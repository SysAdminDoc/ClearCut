package com.novacut.editor.engine

import com.novacut.editor.model.ExportConfig
import com.novacut.editor.model.Track
import com.novacut.editor.model.TrackType
import com.novacut.editor.model.VideoCodec

/**
 * Pure pre-flight analyzer for export color and HDR confidence.
 *
 * The export pipeline still performs the authoritative Media3 negotiation at
 * render time. This class keeps the user-facing forecast deterministic and
 * easy to unit-test before any Android codec APIs are involved.
 */
object ExportColorConfidenceEngine {

    enum class Tone { GOOD, INFO, WARNING }

    data class HdrEncodeSupport(
        val supportedFormats: Set<String> = emptySet(),
        val maxWidth: Int = 0,
        val maxHeight: Int = 0,
        val maxBitrate: Int = 0
    ) {
        val hasAnyHdr: Boolean get() = supportedFormats.isNotEmpty()
    }

    data class SourceHdrSummary(
        val supportedFormats: Set<String> = emptySet(),
        val inspectedSourceCount: Int = 0,
        val totalSourceCount: Int = 0,
        val apvSourceCount: Int = 0
    ) {
        val hasHdrSource: Boolean get() = supportedFormats.isNotEmpty()
        val hasApvSource: Boolean get() = apvSourceCount > 0
        val hasUltraHdrGainMap: Boolean
            get() = supportedFormats.any { it.equals("Ultra HDR gain map", ignoreCase = true) }
        val isFullyInspected: Boolean
            get() = totalSourceCount > 0 && inspectedSourceCount >= totalSourceCount
    }

    data class Chip(
        val label: String,
        val detail: String,
        val tone: Tone
    )

    data class Report(
        val chips: List<Chip>,
        val warnings: List<String>
    ) {
        val hasWarnings: Boolean get() = warnings.isNotEmpty()
    }

    fun analyze(
        config: ExportConfig,
        width: Int,
        height: Int,
        hdrSupport: HdrEncodeSupport,
        sourceSummary: SourceHdrSummary = SourceHdrSummary()
    ): Report {
        val chips = mutableListOf<Chip>()
        val warnings = mutableListOf<String>()

        addSourceChips(sourceSummary, chips)

        if (!config.hdr10PlusMetadata) {
            chips += Chip(
                label = "SDR delivery",
                detail = "HDR metadata is off for broad playback compatibility.",
                tone = Tone.GOOD
            )
            chips += Chip(
                label = "Rec.709-safe",
                detail = "Export settings favor the standard SDR social-video path.",
                tone = Tone.INFO
            )
            if (sourceSummary.hasHdrSource) {
                chips += Chip(
                    label = "HDR source",
                    detail = "Detected ${sourceSummary.formatList()} source media; enable Preserve HDR Metadata for HDR delivery.",
                    tone = Tone.INFO
                )
            }
            return Report(chips = chips, warnings = warnings)
        }

        if (!config.codec.canCarryHdr()) {
            chips += Chip(
                label = "HDR unavailable",
                detail = "${config.codec.label} exports are treated as SDR.",
                tone = Tone.WARNING
            )
            warnings += "${config.codec.label} cannot carry HDR in NovaCut exports. Switch to HEVC, AV1, or VP9 before preserving HDR metadata."
            return Report(chips = chips, warnings = warnings)
        }

        if (!hdrSupport.hasAnyHdr) {
            chips += Chip(
                label = "HDR not advertised",
                detail = "No HDR encode profile was found for ${config.codec.label}.",
                tone = Tone.WARNING
            )
            warnings += "This device does not advertise HDR encode support for ${config.codec.label}; Media3 may tone-map or fall back to SDR."
        } else {
            val formats = hdrSupport.supportedFormats.sorted().joinToString(", ")
            chips += Chip(
                label = "HDR keep requested",
                detail = "${config.codec.label} advertises $formats encode support.",
                tone = Tone.GOOD
            )
        }

        val hasHdr10Plus = hdrSupport.supportedFormats.any { it.equals("HDR10+", ignoreCase = true) }
        val hasDolbyVisionProfile10 = hdrSupport.supportedFormats.any {
            it.equals("Dolby Vision Profile 10", ignoreCase = true)
        }

        if (hasDolbyVisionProfile10) {
            chips += Chip(
                label = "Dolby Vision path",
                detail = "Profile 10 is advertised on this device.",
                tone = Tone.GOOD
            )
        }

        if (hdrSupport.hasAnyHdr && !hasHdr10Plus && !hasDolbyVisionProfile10) {
            chips += Chip(
                label = "Static HDR only",
                detail = "Dynamic HDR metadata is not advertised by this encoder.",
                tone = Tone.INFO
            )
            warnings += "The selected encoder advertises HDR, but not HDR10+ or Dolby Vision dynamic metadata."
        } else if (hasHdr10Plus) {
            chips += Chip(
                label = "HDR10+ metadata",
                detail = "Dynamic HDR metadata is supported by the selected encoder.",
                tone = Tone.GOOD
            )
        }

        if (hdrSupport.maxWidth > 0 && hdrSupport.maxHeight > 0 &&
            (width > hdrSupport.maxWidth || height > hdrSupport.maxHeight)
        ) {
            warnings += "${config.codec.label} HDR encode is advertised up to ${hdrSupport.maxWidth}x${hdrSupport.maxHeight}; this export is ${width}x${height}."
        }

        if (hdrSupport.maxBitrate > 0 && config.videoBitrate > hdrSupport.maxBitrate) {
            warnings += "${config.codec.label} HDR bitrate is advertised up to ${hdrSupport.maxBitrate / 1_000_000} Mbps; this export requests ${config.videoBitrate / 1_000_000} Mbps."
        }

        chips += Chip(
            label = "Source checked at render",
            detail = if (sourceSummary.hasHdrSource) {
                "Source metadata was detected during import and Media3 still verifies it at render."
            } else {
                "HDR is preserved only when the input track actually carries HDR."
            },
            tone = Tone.INFO
        )

        return Report(chips = chips, warnings = warnings.distinct())
    }

    fun summarizeSources(tracks: List<Track>): SourceHdrSummary {
        val visualClips = tracks
            .filter { it.type == TrackType.VIDEO || it.type == TrackType.OVERLAY }
            .flatMap { it.clips }
        val clips = (visualClips.ifEmpty { tracks.flatMap { it.clips } })
            .distinctBy { it.sourceUri.toString() }

        val inspected = clips.filter { it.sourceColorMetadata.isInspected }
        val formats = inspected
            .flatMap { clip -> clip.sourceColorMetadata.hdrFormats.map { it.displayName } }
            .toSet()
        val apvSourceCount = inspected.count { clip ->
            clip.sourceColorMetadata.mimeType.isApvMimeType()
        }

        return SourceHdrSummary(
            supportedFormats = formats,
            inspectedSourceCount = inspected.size,
            totalSourceCount = clips.size,
            apvSourceCount = apvSourceCount
        )
    }

    private fun addSourceChips(
        sourceSummary: SourceHdrSummary,
        chips: MutableList<Chip>
    ) {
        if (sourceSummary.hasApvSource) {
            chips += Chip(
                label = "Source is APV",
                detail = "APV pro intra-frame media was detected; expect very large source files.",
                tone = Tone.WARNING
            )
        }
        if (sourceSummary.hasUltraHdrGainMap) {
            chips += Chip(
                label = "Ultra HDR source",
                detail = "Gain-map HDR was detected during import.",
                tone = Tone.GOOD
            )
        } else if (sourceSummary.hasHdrSource) {
            chips += Chip(
                label = "HDR source",
                detail = "Detected ${sourceSummary.formatList()} source media during import.",
                tone = Tone.GOOD
            )
        } else if (sourceSummary.isFullyInspected) {
            chips += Chip(
                label = "SDR source",
                detail = "No HDR source metadata was found during import.",
                tone = Tone.INFO
            )
        } else if (sourceSummary.totalSourceCount > 0) {
            chips += Chip(
                label = "Source HDR unknown",
                detail = "Some clips were created before source HDR inspection was added.",
                tone = Tone.INFO
            )
        }
    }

    private fun SourceHdrSummary.formatList(): String =
        supportedFormats.sorted().joinToString(", ").ifBlank { "HDR" }

    private fun String?.isApvMimeType(): Boolean =
        this?.equals(EncoderCapabilityProbe.MIME_APV, ignoreCase = true) == true

    private fun VideoCodec.canCarryHdr(): Boolean = this != VideoCodec.H264
}

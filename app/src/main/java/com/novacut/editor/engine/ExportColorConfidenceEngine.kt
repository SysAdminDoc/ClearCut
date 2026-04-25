package com.novacut.editor.engine

import com.novacut.editor.model.ExportConfig
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
        hdrSupport: HdrEncodeSupport
    ): Report {
        val chips = mutableListOf<Chip>()
        val warnings = mutableListOf<String>()

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

        if (hdrSupport.hasAnyHdr && hdrSupport.supportedFormats.none { it.equals("HDR10+", ignoreCase = true) }) {
            chips += Chip(
                label = "Static HDR only",
                detail = "Dynamic HDR10+ metadata is not advertised by this encoder.",
                tone = Tone.INFO
            )
            warnings += "The selected encoder advertises HDR, but not HDR10+ dynamic metadata."
        } else if (hdrSupport.supportedFormats.any { it.equals("HDR10+", ignoreCase = true) }) {
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
            detail = "HDR is preserved only when the input track actually carries HDR.",
            tone = Tone.INFO
        )

        return Report(chips = chips, warnings = warnings.distinct())
    }

    private fun VideoCodec.canCarryHdr(): Boolean = this != VideoCodec.H264
}

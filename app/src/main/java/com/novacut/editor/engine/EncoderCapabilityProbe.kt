package com.novacut.editor.engine

import android.media.MediaCodecInfo
import android.media.MediaCodecInfo.CodecProfileLevel
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import android.util.Log
import com.novacut.editor.model.VideoCodec

/**
 * Device-aware encoder capability probe. Answers the question
 * "will this device *actually* encode at the requested spec?"
 * beyond the coarser "is the codec present at all?" check that
 * `ExportConfig.getAvailableCodecs` already performs.
 *
 * The goal is to surface a warning in the pre-flight report when
 * the user has picked a combination the encoder advertises it can't
 * handle (e.g. 4K HEVC on a phone whose HEVC encoder only goes to
 * 1080p), so the user can dial it back before burning 40 minutes of
 * render time and hitting a Transformer error mid-export.
 *
 * Queries are narrow on purpose: we only look at advertised
 * capabilities, not runtime probes. Real-world encoders sometimes
 * refuse configurations they claim to support, and that's left to
 * Media3 Transformer's own retry logic. This probe is an *early
 * warning*, not a guarantee.
 */
object EncoderCapabilityProbe {

    private const val TAG = "EncoderCapabilityProbe"
    private const val MIME_DOLBY_VISION = "video/dolby-vision"

    enum class HdrExportFormat(val displayName: String) {
        HDR10("HDR10"),
        HDR10_PLUS("HDR10+"),
        DOLBY_VISION_PROFILE_10("Dolby Vision Profile 10")
    }

    enum class DeviceEncodingTier(val displayName: String) {
        STANDARD("Standard"),
        ADVANCED("Advanced"),
        PREMIUM("Premium")
    }

    data class HdrProfileSupport(
        val codec: VideoCodec,
        val supportedFormats: Set<HdrExportFormat>,
        val maxWidth: Int = 0,
        val maxHeight: Int = 0,
        val maxBitrate: Int = 0,
        val encoderNames: Set<String> = emptySet()
    ) {
        val hasAnyHdr: Boolean get() = supportedFormats.isNotEmpty()
        val hasHdr10Plus: Boolean get() = HdrExportFormat.HDR10_PLUS in supportedFormats
        val hasDolbyVisionProfile10: Boolean
            get() = HdrExportFormat.DOLBY_VISION_PROFILE_10 in supportedFormats
    }

    data class DeviceEncodingTierHint(
        val tier: DeviceEncodingTier,
        val detail: String,
        val availableCodecs: Set<VideoCodec>,
        val hdrFormats: Set<HdrExportFormat>,
        val hasHardwareHevc: Boolean,
        val hasHardwareAv1: Boolean,
        val hasHardwareVp9: Boolean
    )

    /**
     * Per-codec capability snapshot. `supported = false` means no
     * encoder on this device accepts the (width, height, framerate,
     * bitrate) tuple. Reasons are human-readable for toast display.
     */
    data class Capability(
        val supported: Boolean,
        val reason: String? = null
    )

    /**
     * Check whether any of the device's advertised encoders for
     * `codec` can handle the requested (w, h, fps, bitrate). Walks
     * every matching encoder so a device with multiple (e.g.
     * Qualcomm + Google-software) HEVC encoders is correctly
     * reported as supporting the higher of the two's capabilities.
     */
    fun check(
        codec: VideoCodec,
        width: Int,
        height: Int,
        framerate: Int,
        bitrate: Int
    ): Capability {
        if (width <= 0 || height <= 0 || framerate <= 0 || bitrate <= 0) {
            return Capability(false, "Invalid export dimensions")
        }
        val mimeType = codec.mimeType
        val codecInfos = try {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
                .filter { it.isEncoder }
                .filter { it.supportedTypes.any { t -> t.equals(mimeType, ignoreCase = true) } }
        } catch (e: Exception) {
            Log.w(TAG, "MediaCodecList lookup failed for ${codec.label}", e)
            return Capability(true, null)  // Don't warn on a probe failure.
        }
        if (codecInfos.isEmpty()) {
            return Capability(
                false,
                "No ${codec.label} encoder present — falling back to H.264 is safer."
            )
        }

        // Accumulate the reason from the *best* encoder that declines
        // so the warning explains which limit was hit. Many devices
        // expose a software encoder that accepts anything — if any
        // encoder says yes we report supported=true.
        var firstReason: String? = null
        for (info in codecInfos) {
            val caps = try {
                info.getCapabilitiesForType(mimeType)
            } catch (_: IllegalArgumentException) {
                continue
            } ?: continue
            val videoCaps = caps.videoCapabilities ?: continue

            if (!videoCaps.isSizeSupported(width, height)) {
                firstReason = firstReason ?: "${codec.label} on this device tops out at " +
                    "${videoCaps.supportedWidths.upper}×${videoCaps.supportedHeights.upper}"
                continue
            }
            if (!videoCaps.areSizeAndRateSupported(width, height, framerate.toDouble())) {
                firstReason = firstReason ?: "${codec.label} at ${width}×${height} " +
                    "is capped to ${videoCaps.getSupportedFrameRatesFor(width, height).upper.toInt()} fps on this device"
                continue
            }
            val bitrateRange = videoCaps.bitrateRange
            if (bitrate !in bitrateRange.lower..bitrateRange.upper) {
                firstReason = firstReason ?: "${codec.label} bitrate is capped at " +
                    "${bitrateRange.upper / 1_000_000} Mbps on this device"
                continue
            }
            return Capability(true)
        }
        return Capability(false, firstReason ?: "${codec.label} can't encode this configuration")
    }

    /**
     * Returns the HDR profiles the device advertises for the selected export codec.
     *
     * Dolby Vision Profile 10 is AV1-based on Android (`dav1.10`), so NovaCut
     * reports it with AV1 rather than HEVC. This is still an advisory: Media3
     * and the platform encoder perform the authoritative negotiation at export
     * time.
     */
    fun queryHdrProfiles(codec: VideoCodec): HdrProfileSupport {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return HdrProfileSupport(codec = codec, supportedFormats = emptySet())
        }

        val mimeTypes = buildSet {
            add(codec.mimeType)
            if (codec == VideoCodec.AV1) add(MIME_DOLBY_VISION)
        }
        val formats = mutableSetOf<HdrExportFormat>()
        val encoders = mutableSetOf<String>()
        var maxWidth = 0
        var maxHeight = 0
        var maxBitrate = 0

        for ((info, mimeType) in matchingEncoderEntries(mimeTypes)) {
            val caps = try {
                info.getCapabilitiesForType(mimeType)
            } catch (_: IllegalArgumentException) {
                continue
            } catch (t: Throwable) {
                Log.w(TAG, "Capability lookup failed for ${info.name} / $mimeType", t)
                continue
            }

            val discovered = caps.profileLevels
                ?.mapNotNull { classifyHdrProfile(mimeType, it) }
                ?.toSet()
                .orEmpty()
            if (discovered.isEmpty()) continue

            formats += discovered
            encoders += info.name
            caps.videoCapabilities?.let { videoCaps ->
                maxWidth = maxOf(maxWidth, videoCaps.supportedWidths.upper)
                maxHeight = maxOf(maxHeight, videoCaps.supportedHeights.upper)
                maxBitrate = maxOf(maxBitrate, videoCaps.bitrateRange.upper)
            }
        }

        return HdrProfileSupport(
            codec = codec,
            supportedFormats = formats,
            maxWidth = maxWidth,
            maxHeight = maxHeight,
            maxBitrate = maxBitrate,
            encoderNames = encoders
        )
    }

    /**
     * Coarse user-facing device tier. It is intentionally derived from actual
     * advertised encoders instead of model names so Tensor, Snapdragon, Exynos,
     * and future devices all get the same treatment.
     */
    fun deviceTierHint(): DeviceEncodingTierHint {
        val availableCodecs = VideoCodec.entries
            .filter { matchingEncoderEntries(setOf(it.mimeType)).isNotEmpty() }
            .toSet()
        val hdrFormats = VideoCodec.entries
            .flatMap { queryHdrProfiles(it).supportedFormats }
            .toSet()
        val hasHardwareHevc = hasHardwareEncoder(VideoCodec.HEVC.mimeType)
        val hasHardwareAv1 = hasHardwareEncoder(VideoCodec.AV1.mimeType)
        val hasHardwareVp9 = hasHardwareEncoder(VideoCodec.VP9.mimeType)

        val tier = when {
            hasHardwareAv1 && hasHardwareVp9 -> DeviceEncodingTier.PREMIUM
            hasHardwareHevc || hasHardwareAv1 || hasHardwareVp9 || hdrFormats.isNotEmpty() ->
                DeviceEncodingTier.ADVANCED
            else -> DeviceEncodingTier.STANDARD
        }
        val detail = when (tier) {
            DeviceEncodingTier.PREMIUM -> {
                val hdr = formatHdrList(hdrFormats)
                if (hdr.isNotBlank()) {
                    "Hardware AV1 and VP9 encoders detected with $hdr HDR profile support."
                } else {
                    "Hardware AV1 and VP9 encoders detected for efficient modern exports."
                }
            }
            DeviceEncodingTier.ADVANCED -> {
                val codecs = availableCodecs
                    .filter { it != VideoCodec.H264 }
                    .joinToString(", ") { it.label }
                    .ifBlank { "modern codec" }
                val hdr = formatHdrList(hdrFormats)
                if (hdr.isNotBlank()) {
                    "$codecs encode is available with $hdr HDR profile support."
                } else {
                    "$codecs encode is available. HDR support depends on the selected codec and source."
                }
            }
            DeviceEncodingTier.STANDARD ->
                "Baseline H.264 export path detected. Choose conservative settings for long renders."
        }

        return DeviceEncodingTierHint(
            tier = tier,
            detail = detail,
            availableCodecs = availableCodecs,
            hdrFormats = hdrFormats,
            hasHardwareHevc = hasHardwareHevc,
            hasHardwareAv1 = hasHardwareAv1,
            hasHardwareVp9 = hasHardwareVp9
        )
    }

    private fun matchingEncoderEntries(mimeTypes: Set<String>): List<Pair<MediaCodecInfo, String>> {
        return try {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
                .filter { it.isEncoder }
                .flatMap { info ->
                    info.supportedTypes
                        .filter { supported ->
                            mimeTypes.any { wanted -> supported.equals(wanted, ignoreCase = true) }
                        }
                        .map { supported -> info to supported }
                }
        } catch (t: Throwable) {
            Log.w(TAG, "MediaCodecList lookup failed", t)
            emptyList()
        }
    }

    private fun hasHardwareEncoder(mimeType: String): Boolean {
        return matchingEncoderEntries(setOf(mimeType)).any { (info, _) -> info.isHardwareAcceleratedCompat() }
    }

    private fun MediaCodecInfo.isHardwareAcceleratedCompat(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            isHardwareAccelerated
        } else {
            val lower = name.lowercase()
            !lower.startsWith("omx.google.") &&
                !lower.startsWith("c2.android.") &&
                !lower.contains(".sw.") &&
                !lower.contains("software")
        }
    }

    private fun classifyHdrProfile(
        mimeType: String,
        profileLevel: CodecProfileLevel
    ): HdrExportFormat? {
        return when {
            mimeType.equals(MediaFormat.MIMETYPE_VIDEO_HEVC, ignoreCase = true) -> when (profileLevel.profile) {
                CodecProfileLevel.HEVCProfileMain10HDR10 -> HdrExportFormat.HDR10
                CodecProfileLevel.HEVCProfileMain10HDR10Plus -> HdrExportFormat.HDR10_PLUS
                else -> null
            }
            mimeType.equals(MediaFormat.MIMETYPE_VIDEO_AV1, ignoreCase = true) -> when (profileLevel.profile) {
                CodecProfileLevel.AV1ProfileMain10HDR10 -> HdrExportFormat.HDR10
                CodecProfileLevel.AV1ProfileMain10HDR10Plus -> HdrExportFormat.HDR10_PLUS
                else -> null
            }
            mimeType.equals(MediaFormat.MIMETYPE_VIDEO_VP9, ignoreCase = true) -> when (profileLevel.profile) {
                CodecProfileLevel.VP9Profile2HDR,
                CodecProfileLevel.VP9Profile3HDR -> HdrExportFormat.HDR10
                CodecProfileLevel.VP9Profile2HDR10Plus,
                CodecProfileLevel.VP9Profile3HDR10Plus -> HdrExportFormat.HDR10_PLUS
                else -> null
            }
            mimeType.equals(MIME_DOLBY_VISION, ignoreCase = true) -> when (profileLevel.profile) {
                CodecProfileLevel.DolbyVisionProfileDvav110 -> HdrExportFormat.DOLBY_VISION_PROFILE_10
                else -> null
            }
            else -> null
        }
    }

    private fun formatHdrList(formats: Set<HdrExportFormat>): String {
        return formats
            .map { it.displayName }
            .sorted()
            .joinToString(", ")
    }
}

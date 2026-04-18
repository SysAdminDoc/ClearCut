package com.novacut.editor.engine

import android.media.MediaCodecInfo
import android.media.MediaCodecList
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
}

package com.novacut.editor.engine

import android.media.MediaCodecInfo.CodecProfileLevel
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Advisory probe for HDR10 / Dolby Vision encode support. See ROADMAP.md Tier C.9.
 *
 * Complements v3.55's [EncoderCapabilityProbe] with HDR-profile awareness.
 * Android 14+ exposes HDR-capable MediaCodec profiles; the probe walks the codec
 * list and returns which HDR pipelines this device can actually encode.
 *
 * Consumed by ExportSheet to surface an HDR toggle only when the codec + device
 * combination supports it, preventing the confusing "toggle set, output is SDR
 * anyway" footgun.
 */
@Singleton
class HdrCapabilityProbe @Inject constructor() {

    enum class HdrFormat(val displayName: String) {
        HDR10(displayName = "HDR10"),
        HDR10_PLUS(displayName = "HDR10+"),
        DOLBY_VISION(displayName = "Dolby Vision"),
        HLG(displayName = "HLG")
    }

    data class HdrSupport(
        val supportedFormats: Set<HdrFormat>,
        val mimeType: String,
        val maxWidth: Int = 0,
        val maxHeight: Int = 0,
        val maxBitrate: Int = 0
    ) {
        val hasAnyHdr: Boolean get() = supportedFormats.isNotEmpty()
    }

    /**
     * Probe HDR encode support for a given codec MIME type.
     * Returns empty set when Android version is below HDR-aware encode (< Android 13).
     */
    fun probe(mimeType: String): HdrSupport {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return HdrSupport(emptySet(), mimeType)
        }
        val formats = mutableSetOf<HdrFormat>()
        var maxWidth = 0
        var maxHeight = 0
        var maxBitrate = 0
        try {
            val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            for (info in list.codecInfos) {
                if (!info.isEncoder) continue
                if (mimeType !in info.supportedTypes) continue
                val caps = try { info.getCapabilitiesForType(mimeType) } catch (_: Throwable) { continue }
                val videoCaps = caps.videoCapabilities ?: continue
                maxWidth = maxOf(maxWidth, videoCaps.supportedWidths.upper)
                maxHeight = maxOf(maxHeight, videoCaps.supportedHeights.upper)
                maxBitrate = maxOf(maxBitrate, videoCaps.bitrateRange.upper)
                // profileLevels can be null on some OEM / non-standard codec implementations.
                val profileLevels = caps.profileLevels ?: continue
                for (pl in profileLevels) {
                    formats += classifyProfile(mimeType, pl) ?: continue
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "probe failed for $mimeType", t)
        }
        return HdrSupport(formats, mimeType, maxWidth, maxHeight, maxBitrate)
    }

    private fun classifyProfile(mimeType: String, pl: CodecProfileLevel): HdrFormat? {
        return when (mimeType) {
            MediaFormat.MIMETYPE_VIDEO_HEVC -> when (pl.profile) {
                CodecProfileLevel.HEVCProfileMain10HDR10 -> HdrFormat.HDR10
                CodecProfileLevel.HEVCProfileMain10HDR10Plus -> HdrFormat.HDR10_PLUS
                else -> null
            }
            MediaFormat.MIMETYPE_VIDEO_AV1 -> when (pl.profile) {
                CodecProfileLevel.AV1ProfileMain10HDR10 -> HdrFormat.HDR10
                CodecProfileLevel.AV1ProfileMain10HDR10Plus -> HdrFormat.HDR10_PLUS
                else -> null
            }
            MediaFormat.MIMETYPE_VIDEO_VP9 -> when (pl.profile) {
                CodecProfileLevel.VP9Profile2HDR,
                CodecProfileLevel.VP9Profile3HDR -> HdrFormat.HDR10
                CodecProfileLevel.VP9Profile2HDR10Plus,
                CodecProfileLevel.VP9Profile3HDR10Plus -> HdrFormat.HDR10_PLUS
                else -> null
            }
            "video/dolby-vision" -> HdrFormat.DOLBY_VISION
            else -> null
        }
    }

    /**
     * Create a MediaFormat configured for HDR encode. The export pipeline must also
     * supply 10-bit input (P010 / YUV420_10bit surfaces). Returns null when [format]
     * is unsupported for [mimeType] on this device.
     */
    fun buildHdrMediaFormat(
        mimeType: String,
        format: HdrFormat,
        width: Int,
        height: Int,
        bitrate: Int,
        frameRate: Int
    ): MediaFormat? {
        val support = probe(mimeType)
        if (format !in support.supportedFormats) return null
        return MediaFormat.createVideoFormat(mimeType, width, height).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            setInteger(MediaFormat.KEY_COLOR_STANDARD, MediaFormat.COLOR_STANDARD_BT2020)
            setInteger(MediaFormat.KEY_COLOR_RANGE, MediaFormat.COLOR_RANGE_LIMITED)
            setInteger(
                MediaFormat.KEY_COLOR_TRANSFER,
                if (format == HdrFormat.HLG) MediaFormat.COLOR_TRANSFER_HLG
                else MediaFormat.COLOR_TRANSFER_ST2084
            )
        }
    }

    companion object {
        private const val TAG = "HdrProbe"
    }
}

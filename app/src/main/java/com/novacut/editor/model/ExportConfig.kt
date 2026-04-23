package com.novacut.editor.model

import androidx.compose.runtime.Immutable
import java.util.UUID

@Immutable
data class ExportConfig(
    val resolution: Resolution = Resolution.FHD_1080P,
    val frameRate: Int = 30,
    val codec: VideoCodec = VideoCodec.H264,
    val quality: ExportQuality = ExportQuality.HIGH,
    val audioCodec: AudioCodec = AudioCodec.AAC,
    val audioBitrate: Int = 256_000,
    val aspectRatio: AspectRatio = AspectRatio.RATIO_16_9,
    val bitrateMode: BitrateMode = BitrateMode.VBR,
    val platformPreset: PlatformPreset? = null,
    val exportAudioOnly: Boolean = false,
    val exportStemsOnly: Boolean = false,
    val includeChapterMarkers: Boolean = false,
    val chapters: List<ChapterMarker> = emptyList(),
    val subtitleFormat: SubtitleFormat? = null,
    val transparentBackground: Boolean = false,
    val exportAsGif: Boolean = false,
    val gifFrameRate: Int = 15,
    val gifMaxWidth: Int = 480,
    val captureFrameOnly: Boolean = false,
    val captureFormat: FrameCaptureFormat = FrameCaptureFormat.PNG,
    val targetSizeBytes: Long? = null,
    val bitrateOverride: Int? = null,
    val filenameTemplate: String = "{name}",
    val exportAsContactSheet: Boolean = false,
    val contactSheetColumns: Int = 4,
    val watermark: Watermark? = null,
    // v3.69 placeholder — reserved for the HDR10+ dynamic-metadata integration.
    // The field is declared here so callers can opt-in ahead of the encoder
    // wire-up; no export path consumes it yet. When the MediaCodec hook lands
    // (HEVC/AV1 on devices advertising HDR10+ profile support), this flag will
    // gate `MediaFormat.KEY_HDR10_PLUS_INFO` attachment.
    val hdr10PlusMetadata: Boolean = false,
    // v3.69 placeholder — gate for the LosslessCut-style stream-copy export
    // path. Eligibility detection already ships (StreamCopyExportEngine); the
    // actual mux uses FFmpeg's `-c copy` path and wakes up with the FFmpegX
    // dependency (Tier A.9). Default true so the feature turns on automatically
    // the moment the dependency is present.
    val allowStreamCopy: Boolean = true
) {
    init {
        require(videoBitrate > 0) { "Bitrate must be positive" }
        require(audioBitrate > 0) { "Audio bitrate must be positive" }
    }

    /**
     * Resolve target-size constraint into a concrete bitrate override for the given
     * timeline duration. Returns a copy of this config with `bitrateOverride` set so
     * the encoder produces a file roughly matching `targetSizeBytes`. No-op if no
     * target size is configured or duration is unusable.
     */
    fun resolveTargetSize(totalDurationMs: Long): ExportConfig {
        val target = targetSizeBytes ?: return this
        // A zero or negative duration means there's no renderable timeline
        // yet. Falling back to the default quality-based bitrate would blow
        // past the user's declared target the moment a clip is added. Pin to
        // the 500 kbps floor so the resolved bitrate always respects the
        // target-size promise; the export is expected to re-resolve once a
        // real duration is known.
        if (totalDurationMs <= 0L) return copy(bitrateOverride = 500_000)
        // Reserve 2% for container overhead (mp4 atoms, moov box) then subtract audio.
        val usableBytes = (target * 0.98).toLong()
        val videoBytes = usableBytes - (audioBitrate.toLong() * totalDurationMs / 8000L)
        if (videoBytes <= 0L) return copy(bitrateOverride = 500_000)
        val bitsPerSec = (videoBytes * 8L * 1000L) / totalDurationMs
        val clamped = bitsPerSec.coerceIn(500_000L, 150_000_000L).toInt()
        return copy(bitrateOverride = clamped)
    }

    companion object {
        fun youtube1080() = ExportConfig(
            resolution = Resolution.FHD_1080P, frameRate = 30, quality = ExportQuality.HIGH,
            aspectRatio = AspectRatio.RATIO_16_9, codec = VideoCodec.H264,
            platformPreset = PlatformPreset.YOUTUBE_1080
        )
        fun youtube4k() = ExportConfig(
            resolution = Resolution.UHD_4K, frameRate = 30, quality = ExportQuality.HIGH,
            aspectRatio = AspectRatio.RATIO_16_9, codec = VideoCodec.HEVC,
            platformPreset = PlatformPreset.YOUTUBE_4K
        )
        fun tiktok() = ExportConfig(
            resolution = Resolution.FHD_1080P, frameRate = 30, quality = ExportQuality.HIGH,
            aspectRatio = AspectRatio.RATIO_9_16, codec = VideoCodec.H264,
            platformPreset = PlatformPreset.TIKTOK
        )
        fun instagram() = ExportConfig(
            resolution = Resolution.FHD_1080P, frameRate = 30, quality = ExportQuality.MEDIUM,
            aspectRatio = AspectRatio.RATIO_9_16, codec = VideoCodec.H264,
            platformPreset = PlatformPreset.INSTAGRAM_REEL
        )
        fun instagramSquare() = ExportConfig(
            resolution = Resolution.FHD_1080P, frameRate = 30, quality = ExportQuality.MEDIUM,
            aspectRatio = AspectRatio.RATIO_1_1, codec = VideoCodec.H264,
            platformPreset = PlatformPreset.INSTAGRAM_FEED
        )
        fun threads() = ExportConfig(
            resolution = Resolution.FHD_1080P, frameRate = 30, quality = ExportQuality.HIGH,
            aspectRatio = AspectRatio.RATIO_9_16, codec = VideoCodec.H264,
            platformPreset = PlatformPreset.THREADS
        )

        /**
         * Query the device's hardware encoder support and return available video codecs.
         * H.264 is always included (guaranteed on all Android devices).
         * HEVC, AV1, and VP9 are included only if a hardware encoder is present.
         */
        fun getAvailableCodecs(): List<VideoCodec> {
            val list = mutableListOf(VideoCodec.H264) // Always available
            val codecList = android.media.MediaCodecList(android.media.MediaCodecList.REGULAR_CODECS)
            codecList.codecInfos.filter { it.isEncoder }.forEach { info ->
                info.supportedTypes.forEach { type ->
                    when (type.lowercase()) {
                        "video/hevc" -> if (VideoCodec.HEVC !in list) list.add(VideoCodec.HEVC)
                        "video/av01" -> if (VideoCodec.AV1 !in list) list.add(VideoCodec.AV1)
                        "video/x-vnd.on2.vp9" -> if (VideoCodec.VP9 !in list) list.add(VideoCodec.VP9)
                    }
                }
            }
            return list
        }
    }

    val videoBitrate: Int get() = bitrateOverride ?: defaultVideoBitrate

    private val defaultVideoBitrate: Int get() = when (resolution) {
        Resolution.SD_480P -> when (quality) {
            ExportQuality.LOW -> 2_000_000
            ExportQuality.MEDIUM -> 4_000_000
            ExportQuality.HIGH -> 6_000_000
        }
        Resolution.HD_720P -> when (quality) {
            ExportQuality.LOW -> 4_000_000
            ExportQuality.MEDIUM -> 6_000_000
            ExportQuality.HIGH -> 10_000_000
        }
        Resolution.FHD_1080P -> when (quality) {
            ExportQuality.LOW -> 6_000_000
            ExportQuality.MEDIUM -> 12_000_000
            ExportQuality.HIGH -> 20_000_000
        }
        Resolution.QHD_1440P -> when (quality) {
            ExportQuality.LOW -> 12_000_000
            ExportQuality.MEDIUM -> 25_000_000
            ExportQuality.HIGH -> 40_000_000
        }
        Resolution.UHD_4K -> when (quality) {
            ExportQuality.LOW -> 25_000_000
            ExportQuality.MEDIUM -> 50_000_000
            ExportQuality.HIGH -> 80_000_000
        }
    }
}

enum class VideoCodec(val mimeType: String, val label: String) {
    H264("video/avc", "H.264"),
    HEVC("video/hevc", "H.265/HEVC"),
    AV1("video/av01", "AV1"),
    VP9("video/x-vnd.on2.vp9", "VP9")
}

enum class AudioCodec(val mimeType: String, val label: String) {
    AAC("audio/mp4a-latm", "AAC"),
    OPUS("audio/opus", "Opus"),
    FLAC("audio/flac", "FLAC")
}

enum class ExportQuality(val label: String) {
    LOW("Small File"),
    MEDIUM("Balanced"),
    HIGH("Best Quality")
}

enum class BitrateMode(val label: String) {
    CBR("Constant"),
    VBR("Variable"),
    CQ("Constant Quality")
}

enum class PlatformPreset(
    val displayName: String,
    val resolution: Resolution,
    val aspectRatio: AspectRatio,
    val frameRate: Int,
    val codec: VideoCodec
) {
    YOUTUBE_1080(
        "YouTube 1080p", Resolution.FHD_1080P, AspectRatio.RATIO_16_9, 30, VideoCodec.H264
    ),
    YOUTUBE_4K(
        "YouTube 4K", Resolution.UHD_4K, AspectRatio.RATIO_16_9, 30, VideoCodec.HEVC
    ),
    TIKTOK(
        "TikTok", Resolution.FHD_1080P, AspectRatio.RATIO_9_16, 30, VideoCodec.H264
    ),
    INSTAGRAM_FEED(
        "Instagram Feed", Resolution.FHD_1080P, AspectRatio.RATIO_1_1, 30, VideoCodec.H264
    ),
    INSTAGRAM_REEL(
        "Instagram Reels", Resolution.FHD_1080P, AspectRatio.RATIO_9_16, 30, VideoCodec.H264
    ),
    INSTAGRAM_STORY(
        "Instagram Story", Resolution.FHD_1080P, AspectRatio.RATIO_9_16, 30, VideoCodec.H264
    ),
    TWITTER(
        "Twitter/X", Resolution.FHD_1080P, AspectRatio.RATIO_16_9, 30, VideoCodec.H264
    ),
    LINKEDIN(
        "LinkedIn", Resolution.FHD_1080P, AspectRatio.RATIO_16_9, 30, VideoCodec.H264
    ),
    THREADS(
        "Threads", Resolution.FHD_1080P, AspectRatio.RATIO_9_16, 30, VideoCodec.H264
    )
}

@Immutable
data class ChapterMarker(
    val timeMs: Long,
    val title: String
)

enum class SubtitleFormat(val extension: String, val displayName: String) {
    SRT("srt", "SubRip (.srt)"),
    VTT("vtt", "WebVTT (.vtt)"),
    ASS("ass", "Advanced SubStation (.ass)")
}

enum class TargetSizePreset(
    val displayName: String,
    val sizeBytes: Long
) {
    DISCORD_8("Discord (8 MB)", 8L * 1024 * 1024),
    DISCORD_25("Discord Nitro (25 MB)", 25L * 1024 * 1024),
    DISCORD_100("Discord Boosted (100 MB)", 100L * 1024 * 1024),
    GMAIL_25("Gmail Attachment (25 MB)", 25L * 1024 * 1024),
    TELEGRAM_50("Telegram (50 MB)", 50L * 1024 * 1024),
    WHATSAPP_16("WhatsApp (16 MB)", 16L * 1024 * 1024),
    TWITTER_512("Twitter/X (512 MB)", 512L * 1024 * 1024);
}

enum class FrameCaptureFormat(val extension: String, val displayName: String) {
    PNG("png", "PNG"),
    JPEG("jpg", "JPEG (smaller)")
}

@Immutable
data class BatchExportItem(
    val id: String = UUID.randomUUID().toString(),
    val config: ExportConfig,
    val outputName: String,
    val status: BatchExportStatus = BatchExportStatus.QUEUED,
    val progress: Float = 0f
)

enum class BatchExportStatus { QUEUED, IN_PROGRESS, COMPLETED, FAILED, CANCELLED }

/**
 * Burn-in watermark applied across every video frame during export. `null`
 * on `ExportConfig.watermark` means no watermark — no cost paid in the
 * encoder pipeline. When non-null the export pipeline decodes the URI once,
 * wraps it as a Media3 `BitmapOverlay`, and passes it alongside text
 * overlays so the watermark is composited on-GPU.
 *
 * `sourceUri` must resolve to a decodable image (PNG with transparency is
 * the typical brand-asset format, but JPEG and WebP are also accepted).
 * `opacity` is multiplied against the bitmap's own alpha channel; 1.0 keeps
 * the bitmap's authored transparency, values below dim the whole overlay.
 */
@Immutable
data class Watermark(
    val sourceUri: android.net.Uri,
    val position: WatermarkPosition = WatermarkPosition.BOTTOM_RIGHT,
    val opacity: Float = 0.9f,
    val scalePercent: Int = 15
) {
    init {
        require(opacity in 0f..1f) { "opacity must be in [0, 1]" }
        require(scalePercent in 5..50) { "scalePercent must be in [5, 50]" }
    }
}

enum class WatermarkPosition(val displayName: String) {
    TOP_LEFT("Top Left"),
    TOP_RIGHT("Top Right"),
    BOTTOM_LEFT("Bottom Left"),
    BOTTOM_RIGHT("Bottom Right"),
    CENTER("Center")
}

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
    val subtitleFormat: SubtitleFormat? = null
) {
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

    val videoBitrate: Int get() = when (resolution) {
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

@Immutable
data class BatchExportItem(
    val id: String = UUID.randomUUID().toString(),
    val config: ExportConfig,
    val outputName: String,
    val status: BatchExportStatus = BatchExportStatus.QUEUED,
    val progress: Float = 0f
)

enum class BatchExportStatus { QUEUED, IN_PROGRESS, COMPLETED, FAILED, CANCELLED }

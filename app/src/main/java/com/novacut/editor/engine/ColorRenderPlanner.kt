package com.novacut.editor.engine

import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import android.view.Surface
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.UnstableApi
import com.novacut.editor.R
import com.novacut.editor.model.Clip
import com.novacut.editor.model.ExportConfig
import com.novacut.editor.model.ProjectColorPolicy
import com.novacut.editor.model.SourceHdrFormat
import com.novacut.editor.model.Track
import com.novacut.editor.model.VideoCodec
import java.util.Locale

/** The color an output file carries. */
enum class DeliveredColor {
    SDR,
    HLG,
    PQ;

    val isHdr: Boolean get() = this != SDR
}

/** The Media3 HDR modes ClearCut renders with. CompositionBuilder maps them to Composition constants. */
enum class ColorHdrMode { KEEP_HDR, TONE_MAP_TO_SDR, TONE_MAP_IN_DECODER, INTERPRET_HDR_AS_SDR }

/**
 * What this device's graphics stack can do with HDR video. Media3 samples HDR frames in
 * OpenGL only through GL_EXT_YUV_target, both to keep HDR and to tone-map it; without the
 * extension the render fails. Some decoders on Android 12 and newer tone-map to SDR
 * themselves; anything else can only read HDR as SDR.
 */
enum class HdrDeviceSupport(val sdrMode: ColorHdrMode) {
    OPEN_GL(ColorHdrMode.TONE_MAP_TO_SDR),
    DECODER_TONE_MAP_ONLY(ColorHdrMode.TONE_MAP_IN_DECODER),
    NONE(ColorHdrMode.INTERPRET_HDR_AS_SDR);

    companion object {
        /** Probed once per process: a throwaway EGL context, and at most one HEVC decoder. */
        val current: HdrDeviceSupport by lazy {
            @OptIn(UnstableApi::class)
            val glSamplesHdr = runCatching { GlUtil.isYuvTargetExtensionSupported() }.getOrDefault(false)
            when {
                glSamplesHdr -> OPEN_GL
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && hevcDecoderToneMaps() -> DECODER_TONE_MAP_ONLY
                else -> NONE
            }
        }

        /**
         * Phones record HDR in HEVC, so that decoder decides the route. This is Media3's own
         * check: configure with an SDR transfer request and see whether the decoder kept it.
         */
        @RequiresApi(Build.VERSION_CODES.S)
        private fun hevcDecoderToneMaps(): Boolean = runCatching {
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_HEVC, 1280, 720).apply {
                setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10)
                setInteger(MediaFormat.KEY_COLOR_STANDARD, MediaFormat.COLOR_STANDARD_BT2020)
                setInteger(MediaFormat.KEY_COLOR_TRANSFER, MediaFormat.COLOR_TRANSFER_HLG)
                setInteger(MediaFormat.KEY_COLOR_RANGE, MediaFormat.COLOR_RANGE_LIMITED)
                setInteger(MediaFormat.KEY_COLOR_TRANSFER_REQUEST, MediaFormat.COLOR_TRANSFER_SDR_VIDEO)
            }
            val name = MediaCodecList(MediaCodecList.REGULAR_CODECS).findDecoderForFormat(format)
                ?: return@runCatching false
            val texture = SurfaceTexture(false)
            val surface = Surface(texture)
            val codec = MediaCodec.createByCodecName(name)
            try {
                codec.configure(format, surface, null, 0)
                codec.inputFormat.getInteger(MediaFormat.KEY_COLOR_TRANSFER_REQUEST, 0) ==
                    MediaFormat.COLOR_TRANSFER_SDR_VIDEO
            } finally {
                codec.release()
                surface.release()
                texture.release()
            }
        }.getOrDefault(false)
    }
}

enum class ColorPlanIssue(val blocking: Boolean) {
    /** Keep HDR is set but no clip is HDR, so the output is SDR. */
    NO_HDR_CLIPS(false),

    /** HLG and PQ clips share the timeline; Media3 converts later ones to the first clip's transfer. */
    MIXED_HDR_TRANSFERS(false),

    /** Some clips predate color inspection, so the plan can't vouch for them. */
    UNINSPECTED_CLIPS(false),

    /** HDR tags are ignored by request; HDR clips look washed out. */
    HDR_READ_AS_SDR(false),

    /**
     * An SDR clip, still or gap shares the track with HDR footage. The output takes the first
     * item's color, and Media3 refuses SDR input into an HDR output, so the render would fail.
     */
    SDR_MIXED_WITH_HDR(true),

    /** HDR footage on stacked video tracks: Media3's compositor rejects HDR layers. */
    STACKED_HDR(true),

    /** The export codec can't carry HDR, and Media3 would silently switch codec or tone-map. */
    CODEC_CANNOT_CARRY_HDR(true),

    /** Overlays that render only in SDR sit on an HDR export. */
    OVERLAYS_NEED_SDR(true),

    /** The graphics driver can't sample HDR frames, so nothing can stay HDR on this device. */
    DEVICE_CANNOT_PROCESS_HDR(true),

    /** Caption burn-in re-encodes the finished file with FFmpeg's H.264 or MPEG-4 encoder, which drops HDR. */
    SUBTITLE_BURN_IN_IS_SDR(true),
}

data class ColorRenderPlan(
    val policy: ProjectColorPolicy,
    val hdrMode: ColorHdrMode,
    /** The color the output carries when the render follows this plan. */
    val expected: DeliveredColor,
    val issues: Set<ColorPlanIssue> = emptySet(),
    /** How this device tone-maps to SDR; a blocked plan previews with it. */
    val sdrFallbackMode: ColorHdrMode = ColorHdrMode.TONE_MAP_TO_SDR,
) {
    val blockers: List<ColorPlanIssue> get() = issues.filter { it.blocking }
    val warnings: List<ColorPlanIssue> get() = issues.filterNot { it.blocking }
    val isBlocked: Boolean get() = issues.any { it.blocking }

    /**
     * A blocked plan never renders an export, so the preview tone-maps instead of
     * handing Media3 a mix it rejects. Everything that can export previews as planned.
     */
    val previewHdrMode: ColorHdrMode
        get() = if (isBlocked) sdrFallbackMode else hdrMode
}

@get:StringRes
val ColorPlanIssue.messageRes: Int
    get() = when (this) {
        ColorPlanIssue.NO_HDR_CLIPS -> R.string.color_plan_no_hdr_clips
        ColorPlanIssue.MIXED_HDR_TRANSFERS -> R.string.color_plan_mixed_hdr_transfers
        ColorPlanIssue.UNINSPECTED_CLIPS -> R.string.color_plan_uninspected_clips
        ColorPlanIssue.HDR_READ_AS_SDR -> R.string.color_plan_hdr_read_as_sdr
        ColorPlanIssue.SDR_MIXED_WITH_HDR -> R.string.color_plan_sdr_mixed_with_hdr
        ColorPlanIssue.STACKED_HDR -> R.string.color_plan_stacked_hdr
        ColorPlanIssue.CODEC_CANNOT_CARRY_HDR -> R.string.color_plan_codec_cannot_carry_hdr
        ColorPlanIssue.OVERLAYS_NEED_SDR -> R.string.color_plan_overlays_need_sdr
        ColorPlanIssue.DEVICE_CANNOT_PROCESS_HDR -> R.string.color_plan_device_cannot_process_hdr
        ColorPlanIssue.SUBTITLE_BURN_IN_IS_SDR -> R.string.color_plan_subtitle_burn_in_is_sdr
    }

/**
 * The one place a project color policy becomes a Media3 HDR mode. Preview and export
 * both call it with the visual tracks they build sequences from, so they get the same
 * decision. The export adds its codec and overlays, which only add blockers: an export
 * stops before render rather than delivering something the preview didn't show.
 */
/** The codec the render encodes with: a transparent background always renders VP9. */
val ExportConfig.renderCodec: VideoCodec
    get() = if (transparentBackground) VideoCodec.VP9 else codec

object ColorRenderPlanner {

    fun plan(
        policy: ProjectColorPolicy,
        visualTracks: List<Track>,
        totalDurationMs: Long,
        codec: VideoCodec? = null,
        overlays: HdrOverlaySummary? = null,
        device: HdrDeviceSupport = HdrDeviceSupport.OPEN_GL,
        isStill: (Clip) -> Boolean = ::looksLikeStill,
    ): ColorRenderPlan {
        val normalized = policy.normalized()
        val issues = linkedSetOf<ColorPlanIssue>()
        val videoClips = visualTracks
            .flatMap { shiftedTimelineClips(it.clips, it.timelineOffsetMs) }
            .filterNot(isStill)
        val hdrTransfers = videoClips.mapNotNull { it.hdrTransfer() }
        // SDR footage renders the same in any mode, so only HDR footage takes the device's route.
        val sdrMode = if (hdrTransfers.isEmpty()) ColorHdrMode.TONE_MAP_TO_SDR else device.sdrMode

        if (normalized.input == ProjectColorPolicy.InputColor.INTERPRET_HDR_AS_SDR) {
            if (hdrTransfers.isNotEmpty()) issues += ColorPlanIssue.HDR_READ_AS_SDR
            return ColorRenderPlan(
                normalized, ColorHdrMode.INTERPRET_HDR_AS_SDR, DeliveredColor.SDR, issues, ColorHdrMode.INTERPRET_HDR_AS_SDR,
            )
        }
        if (!normalized.keepsHdr) {
            if (sdrMode == ColorHdrMode.INTERPRET_HDR_AS_SDR && hdrTransfers.isNotEmpty()) {
                issues += ColorPlanIssue.HDR_READ_AS_SDR
            }
            return ColorRenderPlan(normalized, sdrMode, DeliveredColor.SDR, issues, sdrMode)
        }

        if (videoClips.any { !it.sourceColorMetadata.isInspected }) issues += ColorPlanIssue.UNINSPECTED_CLIPS
        if (hdrTransfers.isEmpty()) {
            issues += ColorPlanIssue.NO_HDR_CLIPS
            return ColorRenderPlan(normalized, ColorHdrMode.KEEP_HDR, DeliveredColor.SDR, issues, sdrMode)
        }
        if (device != HdrDeviceSupport.OPEN_GL) issues += ColorPlanIssue.DEVICE_CANNOT_PROCESS_HDR
        if (visualTracks.size > 1) issues += ColorPlanIssue.STACKED_HDR
        if (hdrTransfers.distinct().size > 1) issues += ColorPlanIssue.MIXED_HDR_TRANSFERS

        val sequenceItems = visualTracks.flatMap { track ->
            buildTimelineSequenceSteps(track.clips, totalDurationMs, track.timelineOffsetMs)
        }
        val sdrItemPresent = sequenceItems.any { step ->
            when (step) {
                is TimelineSequenceStep.GapStep -> true
                is TimelineSequenceStep.ClipStep -> isStill(step.clip) ||
                    (step.clip.sourceColorMetadata.isInspected && step.clip.hdrTransfer() == null)
            }
        }
        if (sdrItemPresent) issues += ColorPlanIssue.SDR_MIXED_WITH_HDR
        if (codec == VideoCodec.H264) issues += ColorPlanIssue.CODEC_CANNOT_CARRY_HDR
        if (overlays?.hasUnsafeBitmapOverlays == true) issues += ColorPlanIssue.OVERLAYS_NEED_SDR

        return ColorRenderPlan(normalized, ColorHdrMode.KEEP_HDR, hdrTransfers.first(), issues, sdrMode)
    }

    /** The export plan for a whole timeline, as the export sheet and the export preflight both check it. */
    fun planExport(
        config: ExportConfig,
        tracks: List<Track>,
        overlayEndsMs: List<Long> = emptyList(),
        overlays: HdrOverlaySummary? = null,
        device: HdrDeviceSupport = HdrDeviceSupport.OPEN_GL,
    ): ColorRenderPlan {
        val composition = CompositionPlanBuilder.build(tracks = tracks, additionalDurationsMs = overlayEndsMs)
        val plan = plan(
            policy = config.colorPolicy,
            visualTracks = composition.visualTracks,
            totalDurationMs = composition.durationMs,
            codec = config.renderCodec,
            overlays = overlays,
            device = device,
        )
        val burnsCaptions = config.burnSubtitles && tracks.any { track -> track.clips.any { it.captions.isNotEmpty() } }
        return if (burnsCaptions && plan.hdrMode == ColorHdrMode.KEEP_HDR && plan.expected.isHdr) {
            plan.copy(issues = plan.issues + ColorPlanIssue.SUBTITLE_BURN_IN_IS_SDR)
        } else {
            plan
        }
    }

    /**
     * Whether copying this clip's bitstream untouched delivers what the policy asks for.
     * Stream copy and trim optimization skip the tone-mapper, so an HDR clip under an SDR
     * policy, or a clip whose color was never inspected, has to be rendered.
     */
    fun copyHonorsPolicy(policy: ProjectColorPolicy, clip: Clip): Boolean {
        val normalized = policy.normalized()
        if (normalized.input == ProjectColorPolicy.InputColor.INTERPRET_HDR_AS_SDR) return false
        if (!clip.sourceColorMetadata.isInspected) return false
        return clip.hdrTransfer() == null || normalized.keepsHdr
    }

    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "bmp", "gif", "heic", "heif", "avif")

    fun looksLikeStill(clip: Clip): Boolean {
        clip.sourceColorMetadata.mimeType?.let { return it.startsWith("image/") }
        val extension = clip.sourceUri.toString()
            .substringBefore('?')
            .substringAfterLast('.', missingDelimiterValue = "")
            .lowercase(Locale.US)
        return extension in IMAGE_EXTENSIONS
    }
}

/** The HDR transfer a clip's import inspection recorded, or null for SDR and unknown. */
internal fun Clip.hdrTransfer(): DeliveredColor? {
    val metadata = sourceColorMetadata
    return when {
        metadata.colorTransfer == "HLG" -> DeliveredColor.HLG
        metadata.colorTransfer == "ST 2084" -> DeliveredColor.PQ
        SourceHdrFormat.HLG in metadata.hdrFormats -> DeliveredColor.HLG
        metadata.hdrFormats.any {
            it == SourceHdrFormat.HDR10 || it == SourceHdrFormat.HDR10_PLUS || it == SourceHdrFormat.DOLBY_VISION
        } -> DeliveredColor.PQ
        else -> null
    }
}

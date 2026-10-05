package com.novacut.editor.ui.editor

import android.content.Context
import com.novacut.editor.engine.ColorHdrMode
import com.novacut.editor.engine.ColorPlanIssue
import com.novacut.editor.engine.ColorRenderPlanner
import com.novacut.editor.engine.EncoderCapabilityProbe
import com.novacut.editor.engine.HdrDeviceSupport
import com.novacut.editor.engine.HdrOverlayAssetInspector
import com.novacut.editor.engine.HdrOverlayPolicy
import com.novacut.editor.engine.MediaImportEngine
import com.novacut.editor.engine.messageRes
import com.novacut.editor.engine.renderCodec
import com.novacut.editor.model.Clip
import com.novacut.editor.model.ExportConfig
import com.novacut.editor.model.SourceColorMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class ExportColorPreflight(
    val blockers: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
)

internal fun EditorState.overlayEndsMs(): List<Long> = listOf(
    textOverlays.maxOfOrNull { it.endTimeMs } ?: 0L,
    imageOverlays.maxOfOrNull { it.endTimeMs } ?: 0L,
)

private val ExportConfig.rendersTaggedVideo: Boolean
    get() = !exportAudioOnly && !exportStemsOnly && !exportAsGif && !captureFrameOnly && !exportAsContactSheet

/**
 * Clips imported before color inspection, and batch source cuts, get inspected here the
 * way import does, so the color plan and stream-copy eligibility see what the files are.
 * A file that can't be read comes back inspected as SDR, which Keep HDR then refuses.
 */
internal suspend fun EditorState.withSourceColorInspected(context: Context): EditorState {
    fun Clip.needsInspection(): Boolean =
        !sourceColorMetadata.isInspected || compoundClips.any { it.needsInspection() }
    if (!exportConfig.rendersTaggedVideo || tracks.none { track -> track.clips.any { it.needsInspection() } }) {
        return this
    }
    val engine = MediaImportEngine(context)
    val inspected = mutableMapOf<String, SourceColorMetadata>()
    fun Clip.inspected(): Clip = copy(
        sourceColorMetadata = sourceColorMetadata.takeIf { it.isInspected }
            ?: inspected.getOrPut(sourceUri.toString()) { engine.inspectSourceColor(sourceUri) },
        compoundClips = compoundClips.map { it.inspected() },
    )
    return withContext(Dispatchers.IO) {
        copy(tracks = tracks.map { track -> track.copy(clips = track.clips.map { it.inspected() }) })
    }
}

/**
 * The project color plan, overlay color and HDR encoder support, checked before a render
 * starts. VideoEngine plans again at render time from the same inputs and refuses the same
 * blockers, so nothing reaches Media3 that the preview didn't plan for.
 */
internal suspend fun exportColorPreflight(context: Context, state: EditorState): ExportColorPreflight {
    val config = state.exportConfig
    if (!config.rendersTaggedVideo) return ExportColorPreflight()
    val overlays = withContext(Dispatchers.IO) {
        HdrOverlayAssetInspector.inspect(
            context = context,
            textOverlays = state.textOverlays,
            imageOverlays = state.imageOverlays,
            watermark = config.watermark,
        )
    }
    val plan = ColorRenderPlanner.planExport(
        config = config,
        tracks = state.tracks,
        overlayEndsMs = state.overlayEndsMs(),
        overlays = overlays,
        device = HdrDeviceSupport.current,
    )
    val blockers = plan.blockers.mapTo(mutableListOf()) { context.getString(it.messageRes) }
    if (plan.hdrMode == ColorHdrMode.KEEP_HDR && plan.expected.isHdr) {
        val support = withContext(Dispatchers.IO) { EncoderCapabilityProbe.queryHdrProfiles(config.renderCodec) }
        if (!support.canPreserveHdr) blockers += support.featureFailureReason()
        HdrOverlayPolicy.evaluate(hdrRequested = true, codec = config.renderCodec, overlays = overlays)
            .takeIf { it.samplerBudgetExceeded }
            ?.disclosure
            ?.let(blockers::add)
    }
    return ExportColorPreflight(
        blockers = blockers,
        // Keep HDR with no HDR clip, or HDR read as SDR, is what the user chose; the sheet
        // shows those. Mixed transfers and unchecked clips change the file, so they need consent.
        warnings = plan.warnings
            .filter { it == ColorPlanIssue.MIXED_HDR_TRANSFERS || it == ColorPlanIssue.UNINSPECTED_CLIPS }
            .map { context.getString(it.messageRes) },
    )
}

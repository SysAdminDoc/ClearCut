package com.novacut.editor.ui.editor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EditorPlaybackContractTest {

    @Test
    fun `timeline rebuild selects the edit point atomically with the new composition`() {
        val engine = locate("app/src/main/java/com/novacut/editor/engine/VideoEngine.kt").readText()
        val viewModel = locate(
            "app/src/main/java/com/novacut/editor/ui/editor/EditorViewModel.kt"
        ).readText()
        val rebuildBlock = viewModel.substring(
            viewModel.indexOf("private fun rebuildPlayerTimeline()"),
            viewModel.indexOf("private fun preloadVisibleWaveforms")
        )

        assertTrue(engine.contains("p.setComposition(composition, startPositionMs.coerceIn"))
        assertTrue(engine.contains("MultipleInputVideoGraph.Factory()"))
        assertTrue(rebuildBlock.contains("startPositionMs = _state.value.playheadMs"))
        assertFalse(rebuildBlock.contains("videoEngine.seekTo"))
    }

    @Test
    fun `play reasserts the timeline edit point instead of inheriting an ended period`() {
        val engine = locate("app/src/main/java/com/novacut/editor/engine/VideoEngine.kt").readText()
        val viewModel = locate(
            "app/src/main/java/com/novacut/editor/ui/editor/EditorViewModel.kt"
        ).readText()
        val playbackBlock = viewModel.substring(
            viewModel.indexOf("fun togglePlayback()"),
            viewModel.indexOf("fun toggleLoop()")
        )

        assertTrue(engine.contains("fun playFromTimelinePosition(positionMs: Long, restartSession: Boolean = false)"))
        assertTrue(engine.contains("playerListener?.let(::addListener)"))
        assertTrue(engine.contains("p.seekTo(positionMs.coerceIn(0L, previewCompositionPlan.durationMs))"))
        assertTrue(engine.contains("playbackSessionNeedsReset("))
        assertTrue(engine.contains("p.stop()"))
        assertTrue(playbackBlock.contains("videoEngine.playFromTimelinePosition(playhead, restartSession)"))
        assertTrue(playbackBlock.contains("videoEngine.isPlaybackRequested()"))
        assertTrue(playbackBlock.contains("!videoEngine.isPlaybackEnded()"))
        assertTrue(playbackBlock.contains("isPlaybackRequested = true"))
        assertTrue(playbackBlock.contains("armPlaybackStartRecovery()"))
        assertFalse(playbackBlock.contains("it.copy(isPlaying = true)"))
    }

    @Test
    fun `manual cut keeps the prepared player timeline`() {
        val delegate = locate(
            "app/src/main/java/com/novacut/editor/ui/editor/ClipEditingDelegate.kt"
        ).readText()
        val splitBlock = delegate.substring(
            delegate.indexOf("fun splitClipAtPlayhead()"),
            delegate.indexOf("// --- Trim ---")
        )

        assertFalse(splitBlock.contains("rebuildPlayerTimeline()"))
        assertTrue(splitBlock.contains("A split only partitions metadata"))
    }

    @Test
    fun `extended trim preview is throttled against the gesture start range`() {
        val delegate = locate(
            "app/src/main/java/com/novacut/editor/ui/editor/ClipEditingDelegate.kt"
        ).readText()
        val viewModel = locate(
            "app/src/main/java/com/novacut/editor/ui/editor/EditorViewModel.kt"
        ).readText()
        val trimBlock = delegate.substring(
            delegate.indexOf("fun beginTrim()"),
            delegate.indexOf("// --- Speed ---")
        )
        val refreshBlock = viewModel.substring(
            viewModel.indexOf("private fun refreshExtendedTrimPreview()"),
            viewModel.indexOf("private fun updatePreview()")
        )
        val endTrimBlock = viewModel.substring(
            viewModel.indexOf("fun endTrim()"),
            viewModel.indexOf("fun beginSpeedChange()")
        )

        assertTrue(trimBlock.contains("trimExtendsPreparedRange(prepared, updatedClip)"))
        assertTrue(trimBlock.contains("if (extendedPreparedRange) refreshExtendedTrimPreview()"))
        assertFalse(trimBlock.contains("preparedTrimRanges = preparedTrimRanges +"))
        assertTrue(refreshBlock.contains("if (extendedTrimPreviewJob?.isActive == true) return"))
        assertTrue(refreshBlock.contains("delay(100L)"))
        assertTrue(refreshBlock.contains("rebuildPlayerTimeline()"))
        assertTrue(endTrimBlock.contains("extendedTrimPreviewJob?.cancel()"))
    }

    @Test
    fun `timeline edit gestures defer undo and route pointer cancellation to rollback`() {
        val delegate = locate(
            "app/src/main/java/com/novacut/editor/ui/editor/ClipEditingDelegate.kt"
        ).readText()
        val viewModel = locate(
            "app/src/main/java/com/novacut/editor/ui/editor/EditorViewModel.kt"
        ).readText()
        val timeline = locate(
            "app/src/main/java/com/novacut/editor/ui/editor/Timeline.kt"
        ).readText()
        val trimBegin = delegate.substring(
            delegate.indexOf("fun beginTrim()"),
            delegate.indexOf("fun trimClip(")
        )
        val gestureBlock = viewModel.substring(
            viewModel.indexOf("fun beginSlipEdit()"),
            viewModel.indexOf("// --- Export ---")
        )
        val cancelBlock = timeline.substring(
            timeline.indexOf("onDragCancel = {", timeline.indexOf("TimelineClipGestureZone.TRIM_LEFT")),
            timeline.indexOf("onDrag = {", timeline.indexOf("onDragCancel = {", timeline.indexOf("TimelineClipGestureZone.TRIM_LEFT")))
        )

        assertFalse(trimBegin.contains("saveUndoState("))
        assertFalse(gestureBlock.substringBefore("fun slipClip(").contains("saveUndoState("))
        assertTrue(gestureBlock.contains("markTimelineGestureMutation(\"Slip edit\")"))
        assertTrue(gestureBlock.contains("markTimelineGestureMutation(\"Slide edit\")"))
        assertTrue(cancelBlock.contains("onTrimDragCanceled()"))
        assertTrue(cancelBlock.contains("onSlipEditCanceled()"))
        assertTrue(cancelBlock.contains("onSlideEditCanceled()"))
        assertFalse(cancelBlock.contains("onTrimDragEnded()"))
    }

    @Test
    fun `live preview excludes single input transition shaders`() {
        val engine = locate("app/src/main/java/com/novacut/editor/engine/VideoEngine.kt").readText()
        assertTrue(engine.contains("if (!previewMode) {\n                clip.headTransition"))
        assertTrue(engine.contains("previewMode = true"))
        assertTrue(engine.contains("EffectBuilder.buildTransitionEffect(it)"))
    }

    @Test
    fun `composition preview owns gaps images and processed audio`() {
        val engine = locate("app/src/main/java/com/novacut/editor/engine/VideoEngine.kt").readText()
        val viewModel = locate(
            "app/src/main/java/com/novacut/editor/ui/editor/EditorViewModel.kt"
        ).readText()
        val previewPanel = locate(
            "app/src/main/java/com/novacut/editor/ui/editor/PreviewPanel.kt"
        ).readText()

        assertTrue(engine.contains("allowAudioTransmux = false"))
        assertTrue(engine.contains("addGap(durationMsToUs(compositionDurationMs))"))
        assertTrue(engine.contains("applyClipSpeed(itemBuilder, clip)"))
        assertTrue(engine.contains("setDurationUs(durationMsToUs(clip.sourceDurationMs.coerceAtLeast(1L)))"))
        assertFalse(viewModel.contains("startGapPlayback"))
        assertFalse(previewPanel.contains("currentClipIsStillImage"))
    }

    private fun locate(relativePath: String): File {
        return listOf(File(relativePath), File("../$relativePath"))
            .firstOrNull(File::exists)
            ?: error("$relativePath not found")
    }
}

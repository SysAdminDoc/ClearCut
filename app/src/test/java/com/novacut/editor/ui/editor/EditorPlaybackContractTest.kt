package com.novacut.editor.ui.editor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EditorPlaybackContractTest {

    @Test
    fun `timeline rebuild selects the edit point atomically with the new playlist`() {
        val engine = locate("app/src/main/java/com/novacut/editor/engine/VideoEngine.kt").readText()
        val viewModel = locate(
            "app/src/main/java/com/novacut/editor/ui/editor/EditorViewModel.kt"
        ).readText()
        val rebuildBlock = viewModel.substring(
            viewModel.indexOf("private fun rebuildPlayerTimeline()"),
            viewModel.indexOf("private fun preloadVisibleWaveforms")
        )

        assertTrue(engine.contains("val startTarget = requireNotNull(resolvePreviewSeekTarget(startPositionMs))"))
        assertTrue(engine.contains("startTarget.mediaItemIndex,\n            startTarget.mediaPositionMs"))
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
        assertTrue(engine.contains("p.seekTo(it.mediaItemIndex, it.mediaPositionMs)"))
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
    fun `live preview excludes single input transition shaders`() {
        val engine = locate("app/src/main/java/com/novacut/editor/engine/VideoEngine.kt").readText()
        val previewEffectsBlock = engine.substring(
            engine.indexOf("private fun buildPreviewEffectsForClip("),
            engine.indexOf("fun setPreviewVolume(")
        )

        assertFalse(previewEffectsBlock.contains("headTransition"))
        assertFalse(previewEffectsBlock.contains("buildTransitionEffect"))
        assertFalse(previewEffectsBlock.contains("buildTransitionOutEffect"))
        assertTrue(engine.contains("Timeline transitions are intentionally excluded here"))
        assertTrue(engine.contains("EffectBuilder.buildTransitionEffect(it)"))
    }

    private fun locate(relativePath: String): File {
        return listOf(File(relativePath), File("../$relativePath"))
            .firstOrNull(File::exists)
            ?: error("$relativePath not found")
    }
}

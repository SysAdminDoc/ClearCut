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

        assertTrue(engine.contains("fun playFromTimelinePosition(positionMs: Long)"))
        assertTrue(engine.contains("playerListener?.let(::addListener)"))
        assertTrue(engine.contains("p.seekTo(target.mediaItemIndex, target.mediaPositionMs)"))
        assertTrue(engine.contains("if (p.playbackState == Player.STATE_IDLE)"))
        assertTrue(playbackBlock.contains("videoEngine.playFromTimelinePosition(playhead)"))
        assertTrue(playbackBlock.contains("videoEngine.isPlaybackRequested()"))
        assertTrue(playbackBlock.contains("!videoEngine.isPlaybackEnded()"))
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

    private fun locate(relativePath: String): File {
        return listOf(File(relativePath), File("../$relativePath"))
            .firstOrNull(File::exists)
            ?: error("$relativePath not found")
    }
}

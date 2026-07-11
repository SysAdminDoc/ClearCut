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

    private fun locate(relativePath: String): File {
        return listOf(File(relativePath), File("../$relativePath"))
            .firstOrNull(File::exists)
            ?: error("$relativePath not found")
    }
}

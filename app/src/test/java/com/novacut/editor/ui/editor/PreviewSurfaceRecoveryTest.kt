package com.novacut.editor.ui.editor

import androidx.media3.exoplayer.ExoTimeoutException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PreviewSurfaceRecoveryTest {

    @Test
    fun detachSurfaceTimeoutIsNotMisreportedAsAClipDecodeFailure() {
        val detachTimeout = ExoTimeoutException(
            ExoTimeoutException.TIMEOUT_OPERATION_DETACH_SURFACE
        )

        assertTrue(isPreviewSurfaceDetachTimeout(RuntimeException("player failed", detachTimeout)))
        assertFalse(
            isPreviewSurfaceDetachTimeout(
                ExoTimeoutException(ExoTimeoutException.TIMEOUT_OPERATION_RELEASE)
            )
        )
        assertFalse(isPreviewSurfaceDetachTimeout(IllegalStateException("decoder failed")))
    }

    @Test
    fun stuckPlayerAndSurfaceTimeoutsAreRecoverableRuntimeFailures() {
        assertTrue(
            isRecoverablePreviewRuntimeFailure(
                RuntimeException("player failed", StuckPlayerException())
            )
        )
        assertTrue(
            isRecoverablePreviewRuntimeFailure(
                ExoTimeoutException(ExoTimeoutException.TIMEOUT_OPERATION_DETACH_SURFACE)
            )
        )
        assertFalse(isRecoverablePreviewRuntimeFailure(IllegalStateException("bad media")))
    }

    @Test
    fun playbackWatchdogRequiresRealPositionProgress() {
        assertFalse(hasPreviewPlaybackAdvanced(1_000L, 1_249L))
        assertTrue(hasPreviewPlaybackAdvanced(1_000L, 1_250L))
        assertTrue("loop wrap counts as progress", hasPreviewPlaybackAdvanced(9_500L, 200L))
    }

    @Test
    fun stuckPlayerAtTimelineEndIsNormalCompletion() {
        assertFalse(isAtPreviewTimelineEnd(9_749L, 10_000L))
        assertTrue(isAtPreviewTimelineEnd(9_750L, 10_000L))
        assertTrue(isPreviewStuckPlayerFailure(RuntimeException(StuckPlayerException())))
        assertFalse(isPreviewStuckPlayerFailure(IllegalStateException("codec")))
    }

    @Test
    fun playerViewRemainsMountedBehindGapStillAndErrorOverlays() {
        val source = locate("app/src/main/java/com/novacut/editor/ui/editor/PreviewPanel.kt").readText()
        val playerViewIndex = source.indexOf("AndroidView(")
        val overlayStateIndex = source.indexOf("hasPlaybackError ->")

        assertTrue("PlayerView must exist before transient overlay branches", playerViewIndex in 0 until overlayStateIndex)
        assertTrue(source.contains("if (playerView.player !== player) playerView.player = player"))
        assertFalse(source.contains("else -> {\n                                AndroidView("))
    }

    private fun locate(relativePath: String): File =
        listOf(File(relativePath), File("../$relativePath"))
            .firstOrNull(File::exists)
            ?: error("$relativePath not found")
}

private class StuckPlayerException : RuntimeException()

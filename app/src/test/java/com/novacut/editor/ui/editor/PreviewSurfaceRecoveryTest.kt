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

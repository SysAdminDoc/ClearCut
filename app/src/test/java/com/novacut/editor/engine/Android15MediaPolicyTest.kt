package com.novacut.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Android15MediaPolicyTest {

    @Test
    fun `api 34 keeps both platform hooks on safe fallback`() {
        assertEquals(
            Android15MediaPolicy.LoudnessIntegration.LEGACY_MEDIA3_RENDERER,
            Android15MediaPolicy.loudnessIntegrationForSdk(34),
        )
        assertFalse(Android15MediaPolicy.supportsDesiredHdrHeadroom(34))
        assertEquals(0.0f, Android15MediaPolicy.desiredHdrHeadroom(34, hasHdrContent = true), 0.0f)
    }

    @Test
    fun `api 35 enables media3 loudness and hdr headroom only for hdr content`() {
        assertEquals(
            Android15MediaPolicy.LoudnessIntegration.MEDIA3_PLATFORM_CONTROLLER,
            Android15MediaPolicy.loudnessIntegrationForSdk(35),
        )
        assertTrue(Android15MediaPolicy.supportsDesiredHdrHeadroom(35))
        assertEquals(
            Android15MediaPolicy.DEFAULT_HDR_HEADROOM_RATIO,
            Android15MediaPolicy.desiredHdrHeadroom(35, hasHdrContent = true),
            0.0f,
        )
        assertEquals(0.0f, Android15MediaPolicy.desiredHdrHeadroom(35, hasHdrContent = false), 0.0f)
    }

    @Test
    fun `production hooks keep media3 loudness and surface headroom at their owners`() {
        val videoEngine = locate(
            "app/src/main/java/com/novacut/editor/engine/VideoEngine.kt"
        ).readText().normalizeLineEndings()
        val previewPanel = locate(
            "app/src/main/java/com/novacut/editor/ui/editor/PreviewPanel.kt"
        ).readText().normalizeLineEndings()
        val windowPolicy = locate(
            "app/src/main/java/com/novacut/editor/ui/editor/Android15HdrHeadroomWindow.kt"
        ).readText().normalizeLineEndings()

        assertTrue(videoEngine.contains("LoudnessCodecController"))
        assertTrue(videoEngine.contains("logAndroid15LoudnessIntegration(\"Export\")"))
        assertTrue(previewPanel.contains("setDesiredHdrHeadroom(desiredPreviewHdrHeadroom)"))
        assertTrue(windowPolicy.contains("window.setDesiredHdrHeadroom(desiredHeadroom)"))
        assertTrue(windowPolicy.contains("ActivityInfo.COLOR_MODE_HDR"))
    }

    private fun locate(relativePath: String): File =
        listOf(File(relativePath), File("../$relativePath"))
            .firstOrNull(File::exists)
            ?: error("$relativePath not found")

    private fun String.normalizeLineEndings(): String = replace("\r\n", "\n")
}

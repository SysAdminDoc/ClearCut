package com.novacut.editor.ui.editor

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FullscreenPreviewContractTest {

    @Test
    fun `fullscreen preview keeps one surface and exits before editor back navigation`() {
        val screen = locate(
            "app/src/main/java/com/novacut/editor/ui/editor/EditorScreen.kt"
        ).readText().normalizeLineEndings()
        val preview = locate(
            "app/src/main/java/com/novacut/editor/ui/editor/PreviewPanel.kt"
        ).readText().normalizeLineEndings()
        val systemUi = locate(
            "app/src/main/java/com/novacut/editor/ui/editor/ImmersivePreviewSystemUi.kt"
        ).readText().normalizeLineEndings()

        assertTrue(screen.contains("var isImmersivePreview by rememberSaveable"))
        assertTrue(screen.contains("enabled = isImmersivePreview ||"))
        assertTrue(screen.contains("isImmersivePreview ->"))
        assertTrue(screen.contains("isFullscreenPreview = isImmersivePreview"))
        assertTrue(screen.contains("if (hasClips || hasOpenPanel || isImmersivePreview) Box"))
        assertTrue(screen.contains("Modifier.fillMaxSize()"))
        assertTrue(preview.contains("Icons.Default.Fullscreen"))
        assertTrue(preview.contains("Icons.Default.FullscreenExit"))
        assertTrue(preview.contains("onToggleFullscreenPreview"))
        assertTrue(systemUi.contains("originalBarsVisible"))
        assertTrue(systemUi.contains("controller.hide(WindowInsetsCompat.Type.systemBars())"))
        assertTrue(systemUi.contains("controller.show(WindowInsetsCompat.Type.systemBars())"))
        assertTrue(screen.contains("if (!isImmersivePreview) EditorPrimaryPanelHost"))
    }

    private fun locate(relativePath: String): File =
        listOf(File(relativePath), File("../$relativePath"))
            .firstOrNull(File::exists)
            ?: error("$relativePath not found")

    private fun String.normalizeLineEndings(): String = replace("\r\n", "\n")
}

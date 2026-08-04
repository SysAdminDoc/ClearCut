package com.novacut.editor.ui.editor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PredictiveBackContractTest {

    @Test
    fun `editor consumes predictive back with cancellation-safe preview state`() {
        val screen = locate(
            "app/src/main/java/com/novacut/editor/ui/editor/EditorScreen.kt"
        ).readText().normalizeLineEndings()
        val breadcrumb = locate(
            "app/src/main/java/com/novacut/editor/ui/editor/CompoundNavBreadcrumb.kt"
        ).readText().normalizeLineEndings()

        assertTrue(screen.contains("PredictiveBackHandler(enabled = canConsumeEditorBack)"))
        assertTrue(screen.contains("progress.collect { event ->"))
        assertTrue(screen.contains("predictiveBackProgress = event.progress.coerceIn(0f, 1f)"))
        assertTrue(screen.contains("catch (_: CancellationException)"))
        assertTrue(screen.contains("finally {"))
        assertTrue(screen.contains("predictiveBackProgress = 0f"))
        assertTrue(screen.contains(".graphicsLayer {"))
        assertTrue(screen.contains("BackEventCompat.EDGE_RIGHT"))
        assertFalse(screen.contains("import androidx.activity.compose.BackHandler"))
        assertTrue(breadcrumb.contains("`PredictiveBackHandler`"))
    }

    @Test
    fun `nested back precedence remains stable until the gesture commits`() {
        val screen = locate(
            "app/src/main/java/com/novacut/editor/ui/editor/EditorScreen.kt"
        ).readText().normalizeLineEndings()
        val action = screen.substring(
            screen.indexOf("fun consumeEditorBack()"),
            screen.indexOf("PredictiveBackHandler(enabled = canConsumeEditorBack)")
        )

        assertTrue(action.indexOf("isImmersivePreview ->") < action.indexOf("hasOpenPanel ->"))
        assertTrue(action.indexOf("hasOpenPanel ->") < action.indexOf("currentTool != EditorTool.NONE"))
        assertTrue(action.indexOf("currentTool != EditorTool.NONE") < action.indexOf("selectedClipIds.size > 1"))
        assertTrue(action.indexOf("selectedClipIds.size > 1") < action.indexOf("selectedClipId != null"))
        assertTrue(action.indexOf("selectedClipId != null") < action.indexOf("compoundNavDepth > 0"))
        assertTrue(
            screen.indexOf(
                "consumeEditorBack()",
                screen.indexOf("PredictiveBackHandler(enabled = canConsumeEditorBack)")
            ) >= 0
        )
    }

    private fun locate(relativePath: String): File =
        listOf(File(relativePath), File("../$relativePath"))
            .firstOrNull(File::exists)
            ?: error("$relativePath not found")

    private fun String.normalizeLineEndings(): String = replace("\r\n", "\n")
}

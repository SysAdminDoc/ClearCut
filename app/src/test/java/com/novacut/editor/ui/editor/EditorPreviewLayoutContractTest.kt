package com.novacut.editor.ui.editor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EditorPreviewLayoutContractTest {

    @Test
    fun `preview owns flexible editor height while timeline remains bounded`() {
        val source = locate("app/src/main/java/com/novacut/editor/ui/editor/EditorScreen.kt").readText()
        val previewBlock = source.substring(
            source.indexOf("// Preview panel with long-press radial menu"),
            source.indexOf("// Multi-select action bar")
        )
        val timelineBlock = source.substring(
            source.indexOf("// Timeline "),
            source.indexOf("BottomToolArea(")
        )

        assertTrue(previewBlock.contains(".weight(1f)"))
        assertTrue(previewBlock.contains(".heightIn(min = previewMinHeight)"))
        assertTrue(
            timelineBlock.contains(
                ".heightIn(min = timelineMinHeight, max = timelineMaxHeight)"
            )
        )
        assertFalse(timelineBlock.contains(".weight(1f)"))
    }

    private fun locate(relativePath: String): File {
        return listOf(File(relativePath), File("../$relativePath"))
            .firstOrNull(File::exists)
            ?: error("$relativePath not found")
    }
}

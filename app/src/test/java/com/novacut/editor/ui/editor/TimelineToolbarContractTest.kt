package com.novacut.editor.ui.editor

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TimelineToolbarContractTest {

    @Test
    fun `selected clips expose a destructive delete toolbar action`() {
        val toolbar = locate("app/src/main/java/com/novacut/editor/ui/editor/TimelineToolbar.kt").readText()
        val chrome = locate("app/src/main/java/com/novacut/editor/ui/editor/TimelineChrome.kt").readText()
        val screen = locate("app/src/main/java/com/novacut/editor/ui/editor/EditorScreen.kt").readText()

        assertTrue(toolbar.contains("if (selectedClipId != null)"))
        assertTrue(toolbar.contains("icon = Icons.Default.Delete"))
        assertTrue(toolbar.contains("destructive = true"))
        assertTrue(toolbar.contains("onClick = onDeleteSelectedClip"))
        assertTrue(chrome.contains("val actionAccent = if (destructive) colors.danger else colors.accent"))
        assertTrue(screen.contains("onDeleteSelectedClip = viewModel::deleteSelectedClip"))
    }

    private fun locate(relativePath: String): File {
        return listOf(File(relativePath), File("../$relativePath"))
            .firstOrNull(File::exists)
            ?: error("$relativePath not found")
    }
}

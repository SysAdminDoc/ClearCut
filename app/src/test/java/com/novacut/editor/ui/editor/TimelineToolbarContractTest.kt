package com.novacut.editor.ui.editor

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TimelineToolbarContractTest {

    @Test
    fun `selected clips expose a destructive delete toolbar action`() {
        val timeline = locate("app/src/main/java/com/novacut/editor/ui/editor/Timeline.kt").readText()
        val chrome = locate("app/src/main/java/com/novacut/editor/ui/editor/TimelineChrome.kt").readText()
        val screen = locate("app/src/main/java/com/novacut/editor/ui/editor/EditorScreen.kt").readText()

        assertTrue(timeline.contains("if (selectedClipId != null)"))
        assertTrue(timeline.contains("icon = Icons.Default.Delete"))
        assertTrue(timeline.contains("destructive = true"))
        assertTrue(timeline.contains("onClick = onDeleteSelectedClip"))
        assertTrue(chrome.contains("val actionAccent = if (destructive) colors.danger else colors.accent"))
        assertTrue(screen.contains("onDeleteSelectedClip = viewModel::deleteSelectedClip"))
    }

    private fun locate(relativePath: String): File {
        return listOf(File(relativePath), File("../$relativePath"))
            .firstOrNull(File::exists)
            ?: error("$relativePath not found")
    }
}

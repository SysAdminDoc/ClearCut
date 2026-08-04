package com.novacut.editor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ShortcutIntentBoundaryTest {

    @Test
    fun exportedProjectShortcutsValidateRoomBeforeOpeningTheEditor() {
        val mainActivity = locate("app/src/main/java/com/novacut/editor/MainActivity.kt").readText()
        val editorViewModel = locate(
            "app/src/main/java/com/novacut/editor/ui/editor/EditorViewModel.kt"
        ).readText()

        assertTrue(mainActivity.contains("private fun validateShortcutProject"))
        assertTrue(mainActivity.contains("projectDao.getProject(projectId) != null"))
        assertTrue(mainActivity.contains("withContext(Dispatchers.IO)"))
        assertTrue(mainActivity.contains("if (projectExists)"))
        assertTrue(mainActivity.contains("pendingEditorOpen = null"))
        assertFalse("The exported shortcut path must not trust a raw ID as an editor route", mainActivity.contains("pendingEditorOpen = pendingShortcutOpen"))

        assertFalse("A missing shortcut project must never be inserted as a blank row", editorViewModel.contains("projectDao.insertProject"))
        assertFalse(
            "A missing shortcut project must never be recreated under the supplied ID",
            editorViewModel.contains("val newProject = _state.value.project.copy(id = projectId)")
        )
        assertTrue("The editor must retain an explicit missing-project state", editorViewModel.contains("projectNotFound"))
    }

    private fun locate(path: String): File {
        var directory = File(System.getProperty("user.dir") ?: error("Could not read user.dir")).absoluteFile
        repeat(8) {
            val candidate = File(directory, path)
            if (candidate.isFile) return candidate
            directory = directory.parentFile ?: error("Could not locate $path")
        }
        error("Could not locate $path")
    }
}

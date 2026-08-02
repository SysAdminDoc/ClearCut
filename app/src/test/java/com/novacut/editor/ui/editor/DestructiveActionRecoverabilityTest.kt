package com.novacut.editor.ui.editor

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Editor undo covers nearly every mutation, which makes the gaps more surprising,
 * not less. Three destructive actions had no undo and no restore; one of them --
 * deleting a user-created checkpoint -- also had no confirmation and no toast, so
 * the work simply vanished.
 *
 * These assertions read source text because the behaviour they guard lives across a
 * ViewModel, a manager and a Compose sheet that no JVM unit test can stand up. They
 * are a ratchet against the specific regression, not a substitute for the device test.
 */
class DestructiveActionRecoverabilityTest {

    @Test
    fun deletingASnapshotIsUndoableAndSaysSo() {
        val body = functionBody(editorViewModel(), "fun deleteSnapshot(")

        assertTrue("deleteSnapshot must push an undo state", body.contains("saveUndoState("))
        assertTrue("deleteSnapshot must tell the user it happened", body.contains("showToast("))
    }

    @Test
    fun clearingTheAiLedgerIsUndoableAndSaysSo() {
        val body = functionBody(editorViewModel(), "fun clearAiUsageLedger(")

        assertTrue("clearAiUsageLedger must push an undo state", body.contains("saveUndoState("))
        assertTrue("clearAiUsageLedger must tell the user it happened", body.contains("showToast("))
    }

    @Test
    fun theUndoSnapshotActuallyCarriesTheCheckpointList() {
        val source = editorViewModel()

        assertTrue(
            "UndoAction must capture projectSnapshots, or deleting one cannot be undone",
            source.contains("val projectSnapshots: List<ProjectSnapshot>")
        )
        assertTrue(
            "captureUndoAction must record the checkpoints",
            source.contains("projectSnapshots = state.projectSnapshots.toList()")
        )
        assertTrue(
            "undo/redo must restore the checkpoints",
            source.contains("projectSnapshots = action.projectSnapshots")
        )
    }

    @Test
    fun deletingAUserTemplateRoutesThroughTrashRatherThanDestroyingTheFile() {
        val manager = locate("app/src/main/java/com/novacut/editor/engine/TemplateManager.kt").readText()

        assertTrue("TemplateManager must expose a restore path", manager.contains("fun restoreTemplate("))
        val deleteBody = functionBody(manager, "fun deleteTemplate(")
        assertTrue(
            "deleteTemplate must move the file aside before any destructive fallback",
            deleteBody.contains("trashFileForId(") && deleteBody.contains("renameTo(")
        )
    }

    @Test
    fun theTemplateSheetOffersTheWayBack() {
        val viewModel = locate("app/src/main/java/com/novacut/editor/ui/projects/ProjectListViewModel.kt").readText()
        val sheet = locate("app/src/main/java/com/novacut/editor/ui/projects/ProjectTemplateSheet.kt").readText()

        assertTrue(viewModel.contains("fun restoreDeletedTemplate()"))
        assertTrue(viewModel.contains("_restorableTemplate.value = RestorableTemplate("))
        assertTrue("the sheet must surface the restore", sheet.contains("onRestoreDeletedTemplate"))
    }

    private fun editorViewModel(): String =
        locate("app/src/main/java/com/novacut/editor/ui/editor/EditorViewModel.kt").readText()

    /** Text from a function's declaration to its closing brace at the same indent. */
    private fun functionBody(source: String, declaration: String): String {
        val start = source.indexOf(declaration)
        require(start >= 0) { "$declaration not found" }
        val indent = source.lastIndexOf('\n', start).let { start - it - 1 }
        val closing = "\n" + " ".repeat(indent) + "}"
        val end = source.indexOf(closing, start)
        require(end > start) { "could not find the end of $declaration" }
        return source.substring(start, end)
    }

    private fun locate(relativePath: String): File =
        listOf(File(relativePath), File("../$relativePath")).first { it.exists() }
}

package com.novacut.editor.ui.editor

import com.novacut.editor.model.ProjectSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Editor undo covers nearly every mutation, which makes the gaps more surprising,
 * not less. Destructive actions must either stay in the document undo model or expose
 * an explicit restore path when they are metadata rather than document edits.
 *
 * These assertions read source text because the behaviour they guard lives across a
 * ViewModel, a manager and a Compose sheet that no JVM unit test can stand up. They
 * are a ratchet against the specific regression, not a substitute for the device test.
 */
class DestructiveActionRecoverabilityTest {

    @Test
    fun deletingASnapshotUsesAnExplicitRestoreOffer() {
        val body = functionBody(editorViewModel(), "fun deleteSnapshot(")

        assertFalse("snapshot metadata must not enter the document undo stack", body.contains("saveUndoState("))
        assertTrue("deleteSnapshot must hold the deleted checkpoint", body.contains("_restorableSnapshot.value = snapshot"))
        assertTrue("deleteSnapshot must tell the user it happened", body.contains("showToast("))
    }

    @Test
    fun clearingTheAiLedgerIsUndoableAndSaysSo() {
        val body = functionBody(editorViewModel(), "fun clearAiUsageLedger(")

        assertTrue("clearAiUsageLedger must push an undo state", body.contains("saveUndoState("))
        assertTrue("clearAiUsageLedger must tell the user it happened", body.contains("showToast("))
    }

    @Test
    fun undoActionsDoNotOwnTheCheckpointList() {
        val source = editorViewModel()
        val undoAction = undoActionBody(source)

        assertFalse("UndoAction must not capture project snapshots", undoAction.contains("projectSnapshots"))
        assertFalse("undo/redo must not restore project snapshots", source.contains("projectSnapshots = action.projectSnapshots"))
        assertTrue("undo/redo must restore through the document-only helper", source.contains("withUndoDocument"))
    }

    @Test
    fun restoringAnEarlierEditLeavesCreatedSnapshotPresent() {
        val snapshot = ProjectSnapshot(projectId = "project", label = "Before export", stateJson = "{}")
        val current = EditorState(projectSnapshots = listOf(snapshot))
        val earlierEdit = UndoAction("Trim clip", tracks = emptyList(), textOverlays = emptyList())

        val restored = current.withUndoDocument(earlierEdit)

        assertEquals(listOf(snapshot), restored.projectSnapshots)
    }

    @Test
    fun redoingAfterAnUndoLeavesSnapshotListPresent() {
        val snapshot = ProjectSnapshot(projectId = "project", label = "Before export", stateJson = "{}")
        val afterUndo = EditorState(projectSnapshots = listOf(snapshot))
        val redoEdit = UndoAction("Redo trim", tracks = emptyList(), textOverlays = emptyList())

        val restored = afterUndo.withUndoDocument(redoEdit)

        assertEquals(listOf(snapshot), restored.projectSnapshots)
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
    fun templateDeletionUsesARealSnackbarUndoAction() {
        val viewModel = locate("app/src/main/java/com/novacut/editor/ui/projects/ProjectListViewModel.kt").readText()
        val sheet = locate("app/src/main/java/com/novacut/editor/ui/projects/ProjectTemplateSheet.kt").readText()
        val screen = locate("app/src/main/java/com/novacut/editor/ui/projects/ProjectListScreen.kt").readText()
        val snackbar = locate("app/src/main/java/com/novacut/editor/ui/editor/PremiumSnackbar.kt").readText()

        assertTrue(viewModel.contains("fun restoreDeletedTemplate()"))
        assertTrue(viewModel.contains("RestorableTemplate(id = id"))
        assertFalse("the sheet must not open a second restore dialog", sheet.contains("RestoreUserTemplateBanner"))
        assertTrue("the project screen must put restore on the snackbar", screen.contains("project_template_restore_action"))
        assertTrue("the shared snackbar must support an action", snackbar.contains("actionLabel: String?"))
    }

    @Test
    fun theEditorOffersTheWayBackForADeletedSnapshot() {
        val viewModel = editorViewModel()
        val host = locate("app/src/main/java/com/novacut/editor/ui/editor/EditorUtilityPanelHost.kt").readText()

        assertTrue(viewModel.contains("fun restoreDeletedSnapshot()"))
        assertTrue(viewModel.contains("private val _restorableSnapshot = MutableStateFlow<ProjectSnapshot?>(null)"))
        assertTrue("the editor host must surface the restore", host.contains("onClick = viewModel::restoreDeletedSnapshot"))
    }

    private fun editorViewModel(): String =
        locate("app/src/main/java/com/novacut/editor/ui/editor/EditorViewModel.kt").readText()

    private fun undoActionBody(source: String): String {
        val start = source.indexOf("data class UndoAction(")
        require(start >= 0) { "UndoAction not found" }
        val end = source.indexOf("\n)\n\n/**", start)
        require(end > start) { "could not find the end of UndoAction" }
        return source.substring(start, end)
    }

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

package com.novacut.editor.ui.editor

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.novacut.editor.engine.AiUsageLedger
import com.novacut.editor.engine.TemplateManager
import com.novacut.editor.model.ProjectSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Execute recoverability transitions against the state helpers and managers. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DestructiveActionRecoverabilityTest {

    @Test
    fun deletingASnapshotReturnsItForAOneShotRestore() {
        val first = ProjectSnapshot(projectId = "project", label = "First", stateJson = "{}")
        val second = ProjectSnapshot(projectId = "project", label = "Second", stateJson = "{}")

        val deletion = requireNotNull(
            deleteSnapshotFromState(
                state = EditorState(projectSnapshots = listOf(first, second)),
                snapshotId = first.id,
            )
        )

        assertEquals(listOf(second), deletion.state.projectSnapshots)
        assertEquals(first, deletion.deleted)
        assertFalse(deleteSnapshotFromState(deletion.state, first.id) != null)
    }

    @Test
    fun undoRestoresDocumentButKeepsSnapshotMetadata() {
        val snapshot = ProjectSnapshot(projectId = "project", label = "Before export", stateJson = "{}")
        val restored = EditorState(projectSnapshots = listOf(snapshot)).withUndoDocument(
            UndoAction("Trim clip", tracks = emptyList(), textOverlays = emptyList())
        )

        assertEquals(listOf(snapshot), restored.projectSnapshots)
    }

    @Test
    fun aiUsageLedgerRoundTripsThroughTheUndoDocument() {
        val entry = AiUsageLedger.Entry(
            clipId = "clip",
            effectKind = AiUsageLedger.EffectKind.AUTO_EDIT_LOCAL,
            modelName = "test-model",
            rangeStartMs = 0L,
            rangeEndMs = 1_000L,
            recordedAtEpochMs = 1L,
        )
        val action = UndoAction(
            description = "Clear AI usage ledger",
            tracks = emptyList(),
            textOverlays = emptyList(),
            aiUsageLedger = listOf(entry),
        )

        val restored = EditorState().withUndoDocument(action)

        assertEquals(listOf(entry), restored.aiUsageLedger)
    }

    @Test
    fun templateDeletionMovesARealFileToTrashAndRestoresIt() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = TemplateManager(context)
        val templateId = "recoverable-template"
        val templateFile = File(context.filesDir, "templates/$templateId.json")
        templateFile.parentFile?.mkdirs()
        templateFile.writeText("{}")

        try {
            assertTrue(manager.deleteTemplate(templateId))
            assertFalse(templateFile.exists())
            assertTrue(manager.isTemplateRestorable(templateId))
            assertTrue(manager.restoreTemplate(templateId))
            assertTrue(templateFile.isFile)
            assertFalse(manager.isTemplateRestorable(templateId))
        } finally {
            templateFile.delete()
            File(context.filesDir, "templates/trash/$templateId.json").delete()
        }
    }
}

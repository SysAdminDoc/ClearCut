package com.novacut.editor.ui.editor

import com.novacut.editor.engine.AutoSaveState
import com.novacut.editor.engine.ProjectAutoSave
import com.novacut.editor.engine.ProjectDocumentApplicator
import com.novacut.editor.engine.ProjectPersistenceCoordinator
import com.novacut.editor.model.Project
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorDocumentCoordinatorTest {

    @Test
    fun openLoadsRoomBeforeRecoveryAndReportsMissingProjects() = runBlocking {
        val calls = mutableListOf<String>()
        val coordinator = EditorDocumentCoordinator(
            loadProject = {
                calls += "project"
                null
            },
            loadRecovery = {
                calls += "recovery"
                ProjectAutoSave.LoadOutcome.NotFound
            },
            saveDocument = { _, _ -> error("not used") },
            saveDatabase = { error("not used") },
        )

        val result = coordinator.open(projectId = "missing", recoveryId = "missing")

        assertTrue(result.projectNotFound)
        assertEquals(null, result.recovery)
        assertEquals(listOf("project"), calls)
    }

    @Test
    fun openReturnsRecoveryOutcomeOnlyAfterTheProjectIsLoaded() = runBlocking {
        val calls = mutableListOf<String>()
        val project = Project(id = "project", name = "Test")
        val coordinator = EditorDocumentCoordinator(
            loadProject = {
                calls += "project"
                project
            },
            loadRecovery = {
                calls += "recovery"
                ProjectAutoSave.LoadOutcome.NotFound
            },
            saveDocument = { _, _ -> error("not used") },
            saveDatabase = { error("not used") },
        )

        val result = coordinator.open(projectId = "project", recoveryId = "project")

        assertFalse(result.projectNotFound)
        assertEquals(project, result.project)
        assertEquals(ProjectAutoSave.LoadOutcome.NotFound, result.recovery)
        assertEquals(listOf("project", "recovery"), calls)
    }

    @Test
    fun saveSerializesDocumentWritesAndExposesThePersistenceResult() = runBlocking {
        val calls = mutableListOf<String>()
        val expected = ProjectPersistenceCoordinator.SaveResult(
            databaseSaved = true,
            autoSaveAttempted = true,
            autoSaveSaved = true,
        )
        val coordinator = EditorDocumentCoordinator(
            loadProject = { error("not used") },
            loadRecovery = { error("not used") },
            saveDocument = { _, enabled ->
                calls += "save:$enabled"
                expected
            },
            saveDatabase = {
                calls += "database"
            },
        )

        val result = coordinator.save(document(), autoSaveEnabled = true)
        coordinator.saveDatabase(document())

        assertEquals(expected, result)
        assertEquals(listOf("save:true", "database"), calls)
    }

    private fun document() = ProjectDocumentApplicator.capture(
        project = Project(id = "project"),
        state = AutoSaveState(projectId = "project"),
    )
}

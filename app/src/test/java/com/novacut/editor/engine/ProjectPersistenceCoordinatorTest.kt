package com.novacut.editor.engine

import android.net.FakeUri
import com.novacut.editor.model.Clip
import com.novacut.editor.model.Project
import com.novacut.editor.model.Track
import com.novacut.editor.model.TrackType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectPersistenceCoordinatorTest {

    @Test
    fun enabledSaveWritesDatabaseThenAutosaveAndReportsSuccess() = runBlocking {
        val writes = mutableListOf<String>()
        val coordinator = ProjectPersistenceCoordinator(
            databaseWriter = { document ->
                assertEquals("project", document.project.id)
                writes += "database"
            },
            autoSaveWriter = {
                writes += "autosave"
                true
            },
        )

        val result = coordinator.save(document(), persistenceAllowed = true)

        assertEquals(listOf("database", "autosave"), writes)
        assertTrue(result.databaseSaved)
        assertTrue(result.autoSaveAttempted)
        assertTrue(result.autoSaveSaved)
        assertTrue(result.succeeded)
    }

    @Test
    fun blockedPersistenceTouchesNeitherDatabaseNorAutosave() = runBlocking {
        var databaseCalls = 0
        var autoSaveCalls = 0
        val coordinator = ProjectPersistenceCoordinator(
            databaseWriter = { databaseCalls++ },
            autoSaveWriter = {
                autoSaveCalls++
                true
            },
        )

        val result = coordinator.save(document(), persistenceAllowed = false)

        assertFalse(result.databaseSaved)
        assertFalse(result.autoSaveAttempted)
        assertFalse(result.autoSaveSaved)
        assertFalse(result.succeeded)
        assertEquals(0, databaseCalls)
        assertEquals(0, autoSaveCalls)
    }

    @Test
    fun failedAutosaveIsDistinguishedFromAStoreWriteFailure() = runBlocking {
        val coordinator = ProjectPersistenceCoordinator(
            databaseWriter = {},
            autoSaveWriter = { false },
        )

        val result = coordinator.save(document(), persistenceAllowed = true)

        assertTrue(result.databaseSaved)
        assertTrue(result.autoSaveAttempted)
        assertFalse(result.autoSaveSaved)
        assertFalse(result.succeeded)
    }

    private fun document() = ProjectDocumentApplicator.capture(
        project = Project(id = "project", name = "Test project"),
        state = AutoSaveState(
            projectId = "project",
            tracks = listOf(
                Track(
                    type = TrackType.VIDEO,
                    index = 0,
                    clips = listOf(
                        Clip(
                            sourceUri = FakeUri,
                            sourceDurationMs = 1_000L,
                            timelineStartMs = 0L,
                            trimEndMs = 1_000L,
                        )
                    )
                )
            ),
        ),
    )
}

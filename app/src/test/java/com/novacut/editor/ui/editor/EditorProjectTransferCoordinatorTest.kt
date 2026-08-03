package com.novacut.editor.ui.editor

import android.net.FakeUri
import com.novacut.editor.engine.AutoSaveState
import com.novacut.editor.engine.ProjectArchive
import com.novacut.editor.engine.ProjectDocumentApplicator
import com.novacut.editor.model.Project
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.UUID

class EditorProjectTransferCoordinatorTest {

    @Test
    fun backupExportUsesAStagedFileAndAlwaysCleansItUp() = runBlocking {
        val root = File(System.getProperty("java.io.tmpdir"), "clearcut-transfer-${UUID.randomUUID()}")
        val stagedFiles = mutableListOf<File>()
        try {
            val coordinator = EditorProjectTransferCoordinator(
                backupTempDirectory = root,
                archiveDirectory = root,
                importRootDirectory = root,
                nowMs = { 1L },
                estimateArchiveSize = { 0L },
                exportArchive = { _, output ->
                    stagedFiles += output
                    output.writeText("archive")
                    true
                },
                copyToDownloads = { source, name ->
                    assertTrue(source.exists())
                    assertEquals("archive", source.readText())
                    name
                },
                existingProjectIds = { emptySet() },
                importArchive = { _, _, _ -> error("not used") },
            )

            val result = coordinator.exportBackup(document(), "portable.clearcut")

            assertEquals("portable.clearcut", result)
            assertNotNull(stagedFiles.singleOrNull())
            assertTrue(stagedFiles.single().parentFile == root)
            assertTrue(stagedFiles.single().exists().not())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun importPassesTheCurrentProjectIdsToTheArchiveImporter() = runBlocking {
        val root = File(System.getProperty("java.io.tmpdir"), "clearcut-transfer-${UUID.randomUUID()}")
        var receivedIds: Set<String>? = null
        try {
            val expected = ProjectArchive.ImportResult(
                state = AutoSaveState(projectId = "imported"),
                report = ProjectArchive.ImportReport(
                    schemaVersion = AutoSaveState.FORMAT_VERSION,
                    schemaTooNew = false,
                    originalProjectId = "original",
                    effectiveProjectId = "imported",
                    projectIdCollided = true,
                    idCollisionPolicy = ProjectArchive.IdCollisionPolicy.REGENERATE,
                    mediaTotal = 0,
                    mediaResolved = 0,
                    unresolvedMediaUris = emptyList(),
                    warnings = emptyList(),
                    targetDirCreated = true,
                ),
            )
            val coordinator = EditorProjectTransferCoordinator(
                backupTempDirectory = root,
                archiveDirectory = root,
                importRootDirectory = root,
                nowMs = { 42L },
                estimateArchiveSize = { 0L },
                exportArchive = { _, _ -> false },
                copyToDownloads = { _, _ -> error("not used") },
                existingProjectIds = { setOf("existing") },
                importArchive = { _, target, ids ->
                    assertEquals(File(root, "imported_42").absolutePath, target.absolutePath)
                    receivedIds = ids
                    expected
                },
            )

            val result = coordinator.importBackup(FakeUri)

            assertEquals(expected, result)
            assertEquals(setOf("existing"), receivedIds)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun document() = ProjectDocumentApplicator.capture(
        project = Project(id = "project"),
        state = AutoSaveState(projectId = "project"),
    )
}

package com.novacut.editor.engine

import com.novacut.editor.model.Project
import com.novacut.editor.model.TrackType
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
class ProjectAutoSaveReadOnlyRecoveryTest {

    private val context = RuntimeEnvironment.getApplication()
    private val autoSaveDir = File(context.filesDir, "autosave")
    private val projectIds = listOf(
        "recovery-partial-read-only",
        "recovery-corrupt-read-only",
        "recovery-future-read-only",
        "recovery-diagnostic-read-only",
        "recovery-backup-only",
        "recovery-explicit-commit",
    )

    @Before
    fun setUp() {
        autoSaveDir.mkdirs()
        clearTestArtifacts()
    }

    @After
    fun tearDown() {
        clearTestArtifacts()
    }

    @Test
    fun partialOpenPreservesPrimaryBackupAndTemp() = runBlocking {
        val projectId = projectIds[0]
        val files = writeArtifacts(
            projectId = projectId,
            primaryContents = partialRecoveryJson(projectId),
            backupContents = validDocumentJson(projectId),
        )
        val before = files.associateWith(::snapshot)

        val outcome = ProjectAutoSave(context).loadRecoveryDataWithOutcome(projectId)

        assertTrue(outcome is ProjectAutoSave.LoadOutcome.Loaded && outcome.report.isPartial)
        assertArtifactsUnchanged(before)
    }

    @Test
    fun corruptOpenPreservesPrimaryBackupAndTemp() = runBlocking {
        val projectId = projectIds[1]
        val files = writeArtifacts(
            projectId = projectId,
            primaryContents = "{not-json",
            backupContents = validDocumentJson(projectId),
        )
        val before = files.associateWith(::snapshot)

        val outcome = ProjectAutoSave(context).loadRecoveryDataWithOutcome(projectId)

        assertTrue(outcome is ProjectAutoSave.LoadOutcome.Corrupt)
        assertArtifactsUnchanged(before)
    }

    @Test
    fun futureSchemaOpenPreservesPrimaryBackupAndTemp() = runBlocking {
        val projectId = projectIds[2]
        val futureVersion = AutoSaveState.FORMAT_VERSION + 1
        val files = writeArtifacts(
            projectId = projectId,
            primaryContents = JSONObject().apply {
                put("version", futureVersion)
                put("schemaVersion", futureVersion)
                put("projectId", projectId)
            }.toString(),
            backupContents = validDocumentJson(projectId),
        )
        val before = files.associateWith(::snapshot)

        val outcome = ProjectAutoSave(context).loadRecoveryDataWithOutcome(projectId)

        assertTrue(outcome is ProjectAutoSave.LoadOutcome.FutureSchema)
        assertArtifactsUnchanged(before)
    }

    @Test
    fun legacyDiagnosticReadPreservesEveryArtifact() = runBlocking {
        val projectId = projectIds[3]
        val files = writeArtifacts(
            projectId = projectId,
            primaryContents = validDocumentJson(projectId),
            backupContents = validDocumentJson(projectId),
        )
        val before = files.associateWith(::snapshot)

        val state = ProjectAutoSave(context).loadRecoveryData(projectId)

        assertNotNull(state)
        assertArtifactsUnchanged(before)
    }

    @Test
    fun backupOnlyOpenReadsWithoutPromotingOrDeletingIt() = runBlocking {
        val projectId = projectIds[4]
        val primary = artifact(projectId, "json")
        val backup = artifact(projectId, "bak").apply { writeText(validDocumentJson(projectId)) }
        val backupBefore = snapshot(backup)

        val outcome = ProjectAutoSave(context).loadRecoveryDataWithOutcome(projectId)

        assertTrue(outcome is ProjectAutoSave.LoadOutcome.Loaded)
        assertFalse(primary.exists())
        assertArtifactsUnchanged(mapOf(backup to backupBefore))
    }

    @Test
    fun explicitRecoveredCommitReplacesPrimaryThenCleansBackup() = runBlocking {
        val projectId = projectIds[5]
        val primary = artifact(projectId, "json").apply { writeText(partialRecoveryJson(projectId)) }
        val backup = artifact(projectId, "bak").apply { writeText(validDocumentJson(projectId)) }
        artifact(projectId, "tmp").writeText("interrupted")
        val document = ProjectDocumentApplicator.capture(
            project = Project(id = projectId, name = "Recovered"),
            state = AutoSaveState(projectId = projectId),
        )

        val saved = ProjectAutoSave(context).saveNow(document)
        val outcome = ProjectAutoSave(context).loadRecoveryDataWithOutcome(projectId)

        assertTrue(saved)
        assertTrue(primary.isFile)
        assertFalse(backup.exists())
        assertTrue(outcome is ProjectAutoSave.LoadOutcome.Loaded && !outcome.report.isPartial)
    }

    private fun writeArtifacts(
        projectId: String,
        primaryContents: String,
        backupContents: String,
    ): List<File> = listOf(
        artifact(projectId, "json").apply { writeText(primaryContents) },
        artifact(projectId, "bak").apply { writeText(backupContents) },
        artifact(projectId, "tmp").apply { writeText("interrupted-temp") },
    )

    private fun validDocumentJson(projectId: String): String = ProjectDocumentApplicator.encode(
        ProjectDocumentApplicator.capture(
            project = Project(id = projectId, name = "Valid"),
            state = AutoSaveState(projectId = projectId),
        )
    )

    private fun partialRecoveryJson(projectId: String): String = JSONObject().apply {
        put("version", AutoSaveState.FORMAT_VERSION)
        put("schemaVersion", AutoSaveState.FORMAT_VERSION)
        put("projectId", projectId)
        put("tracks", JSONArray().put(JSONObject().apply {
            put("type", TrackType.VIDEO.name)
            put("index", 0)
            put("clips", JSONArray().put(JSONObject().apply {
                put("id", "broken-clip")
                put("sourceDurationMs", 5_000L)
                put("trimStartMs", 0L)
                put("trimEndMs", 5_000L)
                put("effects", JSONArray())
                put("keyframes", JSONArray())
            }))
        }))
    }.toString()

    private fun artifact(projectId: String, extension: String): File =
        File(autoSaveDir, "${autoSaveFileStem(projectId)}.$extension")

    private fun clearTestArtifacts() {
        projectIds.forEach { projectId ->
            listOf("json", "bak", "tmp").forEach { extension ->
                artifact(projectId, extension).delete()
            }
        }
    }

    private data class ArtifactSnapshot(
        val exists: Boolean,
        val bytes: ByteArray,
    )

    private fun snapshot(file: File): ArtifactSnapshot = ArtifactSnapshot(
        exists = file.exists(),
        bytes = if (file.exists()) file.readBytes() else byteArrayOf(),
    )

    private fun assertArtifactsUnchanged(before: Map<File, ArtifactSnapshot>) {
        before.forEach { (file, expected) ->
            assertTrue("Existence changed for ${file.name}", file.exists() == expected.exists)
            if (expected.exists) {
                assertArrayEquals("Contents changed for ${file.name}", expected.bytes, file.readBytes())
            }
        }
    }
}

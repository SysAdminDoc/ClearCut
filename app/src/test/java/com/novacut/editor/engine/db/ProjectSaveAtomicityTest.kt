package com.novacut.editor.engine.db

import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import com.novacut.editor.model.Project
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Project saves write a parent row and a media manifest. `insertProject` uses
 * `INSERT OR REPLACE`, so the parent row is deleted before it is re-inserted and
 * `project_media_assets` cascades away with it. Doing that outside a transaction
 * leaves a window where the project exists with no manifest.
 *
 * These tests pin the transactional contract of [ProjectDao.saveProjectWithMediaAssets].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProjectSaveAtomicityTest {

    private lateinit var db: ProjectDatabase
    private lateinit var dao: ProjectDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            ProjectDatabase::class.java
        ).setDriver(AndroidSQLiteDriver()).build()
        dao = db.projectDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun saveWritesParentAndManifestTogether() = runBlocking {
        val project = project(name = "First")
        dao.saveProjectWithMediaAssets(project, listOf(asset(PROJECT_ID, "a1"), asset(PROJECT_ID, "a2")))

        assertEquals("First", dao.getProject(PROJECT_ID)?.name)
        assertEquals(listOf("a1", "a2"), dao.getProjectMediaAssetEntities(PROJECT_ID).map { it.assetId })
    }

    @Test
    fun resaveReplacesTheWholeManifest() = runBlocking {
        dao.saveProjectWithMediaAssets(project(name = "First"), listOf(asset(PROJECT_ID, "a1"), asset(PROJECT_ID, "a2")))
        dao.saveProjectWithMediaAssets(project(name = "Second"), listOf(asset(PROJECT_ID, "a3")))

        assertEquals("Second", dao.getProject(PROJECT_ID)?.name)
        assertEquals(listOf("a3"), dao.getProjectMediaAssetEntities(PROJECT_ID).map { it.assetId })
    }

    @Test
    fun saveWithNoAssetsClearsTheManifestButKeepsTheProject() = runBlocking {
        dao.saveProjectWithMediaAssets(project(name = "First"), listOf(asset(PROJECT_ID, "a1")))
        dao.saveProjectWithMediaAssets(project(name = "Second"), emptyList())

        assertEquals("Second", dao.getProject(PROJECT_ID)?.name)
        assertTrue(dao.getProjectMediaAssetEntities(PROJECT_ID).isEmpty())
    }

    /**
     * Fault injection at the child-write step: an asset row pointing at a
     * nonexistent parent violates the foreign key, which aborts the write after
     * the parent has already been replaced. The whole save must roll back to the
     * previous consistent (project, manifest) pair — not leave the new project
     * name with an erased manifest.
     */
    @Test
    fun failureDuringManifestWriteRollsBackTheParentReplace() = runBlocking {
        dao.saveProjectWithMediaAssets(project(name = "First"), listOf(asset(PROJECT_ID, "a1"), asset(PROJECT_ID, "a2")))

        val failed = runCatching {
            dao.saveProjectWithMediaAssets(
                project(name = "Second"),
                listOf(asset(PROJECT_ID, "a3"), asset("missing-project", "a4"))
            )
        }.isFailure

        assertTrue("Foreign key violation should abort the save", failed)
        assertEquals("First", dao.getProject(PROJECT_ID)?.name)
        assertEquals(listOf("a1", "a2"), dao.getProjectMediaAssetEntities(PROJECT_ID).map { it.assetId })
    }

    /**
     * Documents the hazard the transactional save exists to prevent: replacing the
     * parent on its own cascades the manifest away.
     */
    @Test
    fun bareParentInsertStillCascadesTheManifestAway() = runBlocking {
        dao.saveProjectWithMediaAssets(project(name = "First"), listOf(asset(PROJECT_ID, "a1")))

        dao.insertProject(project(name = "Second"))

        assertEquals("Second", dao.getProject(PROJECT_ID)?.name)
        assertTrue(
            "Bare parent REPLACE must be treated as manifest-destroying",
            dao.getProjectMediaAssetEntities(PROJECT_ID).isEmpty()
        )
    }

    private fun project(name: String) = Project(
        id = PROJECT_ID,
        name = name,
        createdAt = 1_000L,
        updatedAt = 2_000L
    )

    private fun asset(projectId: String, assetId: String) = ProjectMediaAssetEntity(
        projectId = projectId,
        assetId = assetId,
        managedUri = "file:///managed/$assetId.mp4",
        originalUri = "content://media/$assetId",
        displayName = "$assetId.mp4",
        mediaType = "video",
        mimeType = "video/mp4",
        sizeBytes = 1_024L,
        durationMs = 5_000L,
        width = 1920,
        height = 1080,
        quickFingerprint = "fp-$assetId",
        importStatus = "IMPORTED",
        lastVerifiedAtEpochMs = 3_000L
    )

    private companion object {
        const val PROJECT_ID = "project-atomicity"
    }
}

package com.novacut.editor.engine.db

import androidx.room3.testing.MigrationTestHelper
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Locks ClearCut's committed Room schema chain.
 *
 * Versions 1-3 are reconstructed from the original entity and migration DDL.
 * Every hop is validated separately so a legacy row is checked immediately
 * after each migration rather than only after the final upgrade.
 */
@RunWith(AndroidJUnit4::class)
class ProjectDatabaseMigrationTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val targetContext = instrumentation.targetContext

    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation = instrumentation,
        databaseClass = ProjectDatabase::class,
        driver = AndroidSQLiteDriver(),
        file = targetContext.getDatabasePath(TEST_DB)
    )

    @Test
    fun committedSchemaVersionsMigrateToCurrentWithoutProjectLoss() = runBlocking {
        for (startVersion in FIRST_SCHEMA_VERSION until CURRENT_SCHEMA_VERSION) {
            targetContext.deleteDatabase(TEST_DB)
            helper.createDatabase(startVersion).use { db ->
                insertProject(db, startVersion)
                assertProjectRowAtVersion(db, startVersion, startVersion)
            }

            for (targetVersion in (startVersion + 1)..CURRENT_SCHEMA_VERSION) {
                val sourceVersion = targetVersion - 1
                helper.runMigrationsAndValidate(
                    targetVersion,
                    listOf(ProjectDatabase.ALL_MIGRATIONS[sourceVersion - FIRST_SCHEMA_VERSION])
                ).use { db ->
                    assertProjectRowAtVersion(db, startVersion, targetVersion)
                }
            }
        }
    }

    private fun insertProject(db: SQLiteConnection, version: Int) {
        val columns = mutableListOf(
            "id",
            "name",
            "aspectRatio",
            "frameRate",
            "resolution",
            "createdAt",
            "updatedAt",
            "durationMs"
        )
        val values = mutableListOf<Any?>(
            projectId(version),
            "Migrated v$version",
            "RATIO_16_9",
            24,
            "FHD_1080P",
            1_000L,
            2_000L,
            3_000L
        )
        if (version >= 2) {
            columns += "templateId"
            values += null
            columns += "proxyEnabled"
            values += 1
        }
        if (version >= 3) {
            columns += "version"
            values += 1
        }
        if (version >= 4) {
            columns += "thumbnailUri"
            values += null
        }
        if (version >= 6) {
            columns += "notes"
            values += expectedNotes(version)
        }
        if (version >= 7) {
            columns += "deletedAtEpochMs"
            values += DELETED_AT
        }
        if (version >= 9) {
            columns += "frameRateNumerator"
            values += 24
            columns += "frameRateDenominator"
            values += 1
        }

        db.prepare(
            "INSERT INTO projects (${columns.joinToString(", ")}) " +
                "VALUES (${values.joinToString(", ") { "?" }})"
        ).use { statement ->
            values.forEachIndexed { index, value -> bindValue(statement, index + 1, value) }
            statement.step()
        }
    }

    private fun assertProjectRowAtVersion(
        db: SQLiteConnection,
        sourceVersion: Int,
        schemaVersion: Int
    ) {
        db.prepare("SELECT name, frameRate FROM projects WHERE id = ?").use { statement ->
            statement.bindText(1, projectId(sourceVersion))
            assertTrue("Project row from v$sourceVersion should survive v$schemaVersion", statement.step())
            assertEquals("Migrated v$sourceVersion", statement.getText(0))
            assertEquals(24, statement.getInt(1))
            assertFalse(statement.step())
        }

        if (schemaVersion >= 2) {
            db.prepare("SELECT templateId, proxyEnabled FROM projects WHERE id = ?").use { statement ->
                statement.bindText(1, projectId(sourceVersion))
                assertTrue(statement.step())
                assertTrue(statement.isNull(0))
                assertEquals(if (sourceVersion >= 2) 1 else 0, statement.getInt(1))
            }
        }

        if (schemaVersion >= 3) {
            db.prepare("SELECT version FROM projects WHERE id = ?").use { statement ->
                statement.bindText(1, projectId(sourceVersion))
                assertTrue(statement.step())
                assertEquals(1, statement.getInt(0))
            }
        }

        if (schemaVersion >= 4) {
            db.prepare("SELECT thumbnailUri FROM projects WHERE id = ?").use { statement ->
                statement.bindText(1, projectId(sourceVersion))
                assertTrue(statement.step())
                assertTrue(statement.isNull(0))
            }
        }

        if (schemaVersion >= 5) {
            db.prepare("SELECT name FROM sqlite_master WHERE type = 'index' AND name = ?").use { statement ->
                statement.bindText(1, "index_projects_updatedAt")
                assertTrue("updatedAt index should exist after v5", statement.step())
            }
        }

        if (schemaVersion >= 6) {
            db.prepare("SELECT notes FROM projects WHERE id = ?").use { statement ->
                statement.bindText(1, projectId(sourceVersion))
                assertTrue(statement.step())
                assertEquals(expectedNotes(sourceVersion), statement.getText(0))
            }
        }

        if (schemaVersion >= 7) {
            db.prepare("SELECT deletedAtEpochMs FROM projects WHERE id = ?").use { statement ->
                statement.bindText(1, projectId(sourceVersion))
                assertTrue(statement.step())
                if (sourceVersion >= 7) {
                    assertEquals(DELETED_AT, statement.getLong(0))
                } else {
                    assertTrue(statement.isNull(0))
                }
            }
        }

        if (schemaVersion >= 8) {
            assertProjectMediaAssetsTableReady(db)
        }

        if (schemaVersion >= 9) {
            db.prepare("SELECT frameRateNumerator, frameRateDenominator FROM projects WHERE id = ?").use { statement ->
                statement.bindText(1, projectId(sourceVersion))
                assertTrue(statement.step())
                assertEquals(24, statement.getInt(0))
                assertEquals(1, statement.getInt(1))
            }
        }

        if (schemaVersion >= 10) {
            val columns = db.prepare("PRAGMA table_info(project_media_assets)").use { statement ->
                buildSet {
                    while (statement.step()) add(statement.getText(1))
                }
            }
            assertTrue("project_media_assets.notes missing after v10", "notes" in columns)
            assertTrue("project_media_assets.tagsJson missing after v10", "tagsJson" in columns)
        }
    }

    private fun assertProjectMediaAssetsTableReady(db: SQLiteConnection) {
        val columns = db.prepare("PRAGMA table_info(project_media_assets)").use { statement ->
            buildSet {
                while (statement.step()) add(statement.getText(1))
            }
        }
        assertTrue("project_media_assets.projectId missing", "projectId" in columns)
        assertTrue("project_media_assets.assetId missing", "assetId" in columns)
        assertTrue("project_media_assets.managedUri missing", "managedUri" in columns)
        assertTrue("project_media_assets.originalUri missing", "originalUri" in columns)

        db.prepare("SELECT name FROM sqlite_master WHERE type = 'index' AND name = ?").use { statement ->
            statement.bindText(1, "index_project_media_assets_projectId_managedUri")
            assertTrue("Managed URI lookup index should exist after migration", statement.step())
        }
    }

    private fun bindValue(statement: androidx.sqlite.SQLiteStatement, index: Int, value: Any?) {
        when (value) {
            null -> statement.bindNull(index)
            is Int -> statement.bindInt(index, value)
            is Long -> statement.bindLong(index, value)
            is String -> statement.bindText(index, value)
            else -> error("Unsupported migration fixture value: ${value::class}")
        }
    }

    private fun projectId(version: Int) = "project-v$version"

    private fun expectedNotes(version: Int) = if (version >= 6) "notes-v$version" else ""

    companion object {
        private const val FIRST_SCHEMA_VERSION = 1
        private const val CURRENT_SCHEMA_VERSION = 10
        private const val DELETED_AT = 12_345L
        private const val TEST_DB = "clearcut-migration-test"
    }
}

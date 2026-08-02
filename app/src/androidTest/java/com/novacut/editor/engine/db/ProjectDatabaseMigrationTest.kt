package com.novacut.editor.engine.db

import android.database.Cursor
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ProjectDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun committedSchemaVersionsMigrateToCurrentWithoutProjectLoss() {
        for (startVersion in FIRST_SCHEMA_VERSION until CURRENT_SCHEMA_VERSION) {
            val dbName = "clearcut-migration-$startVersion"
            helper.createDatabase(dbName, startVersion).use { db ->
                insertProject(db, startVersion)
                assertProjectRowAtVersion(db, startVersion, startVersion)
            }

            for (targetVersion in (startVersion + 1)..CURRENT_SCHEMA_VERSION) {
                val sourceVersion = targetVersion - 1
                helper.runMigrationsAndValidate(
                    dbName,
                    targetVersion,
                    true,
                    ProjectDatabase.ALL_MIGRATIONS[sourceVersion - FIRST_SCHEMA_VERSION]
                ).use { db ->
                    assertProjectRowAtVersion(db, startVersion, targetVersion)
                }
            }
        }
    }

    private fun insertProject(db: SupportSQLiteDatabase, version: Int) {
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

        db.execSQL(
            "INSERT INTO projects (${columns.joinToString(", ")}) " +
                "VALUES (${values.joinToString(", ") { "?" }})",
            values.toTypedArray()
        )
    }

    private fun assertProjectRowAtVersion(
        db: SupportSQLiteDatabase,
        sourceVersion: Int,
        schemaVersion: Int
    ) {
        db.query(
            "SELECT name, frameRate FROM projects WHERE id = ?",
            arrayOf(projectId(sourceVersion))
        ).use { cursor ->
            assertTrue("Project row from v$sourceVersion should survive v$schemaVersion", cursor.moveToFirst())
            assertEquals("Migrated v$sourceVersion", cursor.getString(0))
            assertEquals(24, cursor.getInt(1))
            assertFalse(cursor.moveToNext())
        }

        if (schemaVersion >= 2) {
            db.query(
                "SELECT templateId, proxyEnabled FROM projects WHERE id = ?",
                arrayOf(projectId(sourceVersion))
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertTrue(cursor.isNull(0))
                assertEquals(if (sourceVersion >= 2) 1 else 0, cursor.getInt(1))
            }
        }

        if (schemaVersion >= 3) {
            db.query(
                "SELECT version FROM projects WHERE id = ?",
                arrayOf(projectId(sourceVersion))
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
        }

        if (schemaVersion >= 4) {
            db.query(
                "SELECT thumbnailUri FROM projects WHERE id = ?",
                arrayOf(projectId(sourceVersion))
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertTrue(cursor.isNull(0))
            }
        }

        if (schemaVersion >= 5) {
            db.query(
                "SELECT name FROM sqlite_master WHERE type = 'index' AND name = ?",
                arrayOf("index_projects_updatedAt")
            ).use { cursor ->
                assertTrue("updatedAt index should exist after v5", cursor.moveToFirst())
            }
        }

        if (schemaVersion >= 6) {
            db.query(
                "SELECT notes FROM projects WHERE id = ?",
                arrayOf(projectId(sourceVersion))
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(expectedNotes(sourceVersion), cursor.getString(0))
            }
        }

        if (schemaVersion >= 7) {
            db.query(
                "SELECT deletedAtEpochMs FROM projects WHERE id = ?",
                arrayOf(projectId(sourceVersion))
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                if (sourceVersion >= 7) {
                    assertEquals(DELETED_AT, cursor.getLong(0))
                } else {
                    assertTrue(cursor.isNull(0))
                }
            }
        }

        if (schemaVersion >= 8) {
            assertProjectMediaAssetsTableReady(db)
        }

        if (schemaVersion >= 9) {
            db.query(
                "SELECT frameRateNumerator, frameRateDenominator FROM projects WHERE id = ?",
                arrayOf(projectId(sourceVersion))
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(24, cursor.getInt(0))
                assertEquals(1, cursor.getInt(1))
            }
        }
    }

    private fun assertProjectMediaAssetsTableReady(db: SupportSQLiteDatabase) {
        db.query("PRAGMA table_info(project_media_assets)").use { cursor ->
            val columns = generateSequence { if (cursor.moveToNext()) cursor.getString(1) else null }
                .toSet()
            assertTrue("project_media_assets.projectId missing", "projectId" in columns)
            assertTrue("project_media_assets.assetId missing", "assetId" in columns)
            assertTrue("project_media_assets.managedUri missing", "managedUri" in columns)
            assertTrue("project_media_assets.originalUri missing", "originalUri" in columns)
        }

        db.query(
            "SELECT name FROM sqlite_master WHERE type = 'index' AND name = ?",
            arrayOf("index_project_media_assets_projectId_managedUri")
        ).use { cursor ->
            assertTrue("Managed URI lookup index should exist after migration", cursor.moveToFirst())
        }
    }

    private fun projectId(version: Int) = "project-v$version"

    private fun expectedNotes(version: Int) = if (version >= 6) "notes-v$version" else ""

    companion object {
        private const val FIRST_SCHEMA_VERSION = 1
        private const val CURRENT_SCHEMA_VERSION = 9
        private const val DELETED_AT = 12_345L
    }
}

private inline fun <T : Cursor, R> T.use(block: (T) -> R): R {
    try {
        return block(this)
    } finally {
        close()
    }
}

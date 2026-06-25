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
 * Historical schemas are only present from version 4 onward, so versions 1-3
 * cannot be validated by MigrationTestHelper until those JSON snapshots are
 * recovered or recreated from a tagged build.
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
        for (startVersion in COMMITTED_SCHEMA_START_VERSION until CURRENT_SCHEMA_VERSION) {
            val dbName = "clearcut-migration-$startVersion"
            helper.createDatabase(dbName, startVersion).use { db ->
                insertProject(db, startVersion)
            }

            helper.runMigrationsAndValidate(
                dbName,
                CURRENT_SCHEMA_VERSION,
                true,
                *ProjectDatabase.ALL_MIGRATIONS
            ).use { db ->
                db.query(
                    "SELECT name, notes, deletedAtEpochMs FROM projects WHERE id = ?",
                    arrayOf(projectId(startVersion))
                ).use { cursor ->
                    assertTrue("Project row from v$startVersion should survive", cursor.moveToFirst())
                    assertEquals("Migrated v$startVersion", cursor.getString(0))
                    assertEquals(expectedNotes(startVersion), cursor.getString(1))
                    if (startVersion >= 7) {
                        assertEquals(DELETED_AT, cursor.getLong(2))
                    } else {
                        assertTrue(cursor.isNull(2))
                    }
                    assertFalse(cursor.moveToNext())
                }

                assertProjectMediaAssetsTableReady(db)
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
            "durationMs",
            "thumbnailUri",
            "templateId",
            "proxyEnabled",
            "version"
        )
        val values = mutableListOf<Any?>(
            projectId(version),
            "Migrated v$version",
            "RATIO_16_9",
            30,
            "FHD_1080P",
            1_000L,
            2_000L,
            3_000L,
            null,
            null,
            1,
            1
        )
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
        private const val COMMITTED_SCHEMA_START_VERSION = 4
        private const val CURRENT_SCHEMA_VERSION = 8
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

package com.novacut.editor.engine.db

import com.novacut.editor.engine.AppLog
import androidx.room3.*
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.novacut.editor.engine.ProjectMediaAsset
import com.novacut.editor.engine.decodeMediaAssetTags
import com.novacut.editor.engine.encodeMediaAssetTags
import com.novacut.editor.engine.normalizeMediaAssetNotes
import com.novacut.editor.model.Project
import com.novacut.editor.model.AspectRatio
import com.novacut.editor.model.Resolution
import kotlinx.coroutines.flow.Flow

@Database(entities = [Project::class, ProjectMediaAssetEntity::class], version = 10, exportSchema = true)
@ColumnTypeConverters(Converters::class)
abstract class ProjectDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE projects ADD COLUMN templateId TEXT DEFAULT NULL")
                connection.execSQL("ALTER TABLE projects ADD COLUMN proxyEnabled INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE projects ADD COLUMN version INTEGER NOT NULL DEFAULT 1")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override suspend fun migrate(connection: SQLiteConnection) {
                // Version 3's entity already declared thumbnailUri, but the original
                // 1->3 upgrade path never added it. Fresh v3 databases therefore have
                // the column while legacy databases upgraded from v1 do not.
                var hasThumbnailUri = false
                connection.prepare("PRAGMA table_info(`projects`)").use { statement ->
                    while (statement.step()) {
                        if (statement.getText(1) == "thumbnailUri") {
                            hasThumbnailUri = true
                            break
                        }
                    }
                }
                if (!hasThumbnailUri) {
                    connection.execSQL("ALTER TABLE projects ADD COLUMN thumbnailUri TEXT DEFAULT NULL")
                }
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL("CREATE INDEX IF NOT EXISTS index_projects_updatedAt ON projects (updatedAt)")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE projects ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE projects ADD COLUMN deletedAtEpochMs INTEGER DEFAULT NULL")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `project_media_assets` (
                        `projectId` TEXT NOT NULL,
                        `assetId` TEXT NOT NULL,
                        `managedUri` TEXT NOT NULL,
                        `originalUri` TEXT NOT NULL,
                        `displayName` TEXT,
                        `mediaType` TEXT NOT NULL,
                        `mimeType` TEXT,
                        `sizeBytes` INTEGER NOT NULL,
                        `durationMs` INTEGER,
                        `width` INTEGER,
                        `height` INTEGER,
                        `quickFingerprint` TEXT,
                        `importStatus` TEXT NOT NULL,
                        `lastVerifiedAtEpochMs` INTEGER NOT NULL,
                        PRIMARY KEY(`projectId`, `assetId`),
                        FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                connection.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_project_media_assets_projectId` ON `project_media_assets` (`projectId`)"
                )
                connection.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_project_media_assets_projectId_managedUri` ON `project_media_assets` (`projectId`, `managedUri`)"
                )
                connection.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_project_media_assets_projectId_originalUri` ON `project_media_assets` (`projectId`, `originalUri`)"
                )
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE projects ADD COLUMN frameRateNumerator INTEGER NOT NULL DEFAULT 30")
                connection.execSQL("ALTER TABLE projects ADD COLUMN frameRateDenominator INTEGER NOT NULL DEFAULT 1")
                connection.execSQL("UPDATE projects SET frameRateNumerator = frameRate")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE project_media_assets ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
                connection.execSQL("ALTER TABLE project_media_assets ADD COLUMN tagsJson TEXT NOT NULL DEFAULT '[]'")
            }
        }

        val ALL_MIGRATIONS = arrayOf(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_7_8,
            MIGRATION_8_9,
            MIGRATION_9_10
        )
    }
}

@Entity(
    tableName = "project_media_assets",
    primaryKeys = ["projectId", "assetId"],
    foreignKeys = [
        ForeignKey(
            entity = Project::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("projectId"),
        Index(value = ["projectId", "managedUri"]),
        Index(value = ["projectId", "originalUri"])
    ]
)
data class ProjectMediaAssetEntity(
    val projectId: String,
    val assetId: String,
    val managedUri: String,
    val originalUri: String,
    val displayName: String?,
    val mediaType: String,
    val mimeType: String?,
    val sizeBytes: Long,
    val durationMs: Long?,
    val width: Int?,
    val height: Int?,
    val quickFingerprint: String?,
    val importStatus: String,
    val lastVerifiedAtEpochMs: Long,
    val notes: String = "",
    val tagsJson: String = "[]"
)

fun ProjectMediaAsset.toProjectMediaAssetEntity(projectId: String): ProjectMediaAssetEntity {
    return ProjectMediaAssetEntity(
        projectId = projectId,
        assetId = assetId,
        managedUri = managedUri,
        originalUri = originalUri,
        displayName = displayName,
        mediaType = mediaType,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        durationMs = durationMs,
        width = width,
        height = height,
        quickFingerprint = quickFingerprint,
        importStatus = importStatus,
        lastVerifiedAtEpochMs = lastVerifiedAtEpochMs,
        notes = normalizeMediaAssetNotes(notes),
        tagsJson = encodeMediaAssetTags(tags)
    )
}

fun ProjectMediaAssetEntity.toProjectMediaAsset(): ProjectMediaAsset {
    return ProjectMediaAsset(
        assetId = assetId,
        managedUri = managedUri,
        originalUri = originalUri,
        displayName = displayName,
        mediaType = mediaType,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        durationMs = durationMs,
        width = width,
        height = height,
        quickFingerprint = quickFingerprint,
        importStatus = importStatus,
        lastVerifiedAtEpochMs = lastVerifiedAtEpochMs,
        notes = normalizeMediaAssetNotes(notes),
        tags = decodeMediaAssetTags(tagsJson)
    )
}

fun List<ProjectMediaAsset>.toProjectMediaAssetEntities(projectId: String): List<ProjectMediaAssetEntity> {
    return map { it.toProjectMediaAssetEntity(projectId) }
}

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects WHERE deletedAtEpochMs IS NULL ORDER BY updatedAt DESC")
    fun getAllProjects(): Flow<List<Project>>

    @Query("SELECT * FROM projects WHERE deletedAtEpochMs IS NULL ORDER BY updatedAt DESC")
    suspend fun getAllProjectsSnapshot(): List<Project>

    @Query("SELECT * FROM projects WHERE deletedAtEpochMs IS NOT NULL ORDER BY deletedAtEpochMs DESC")
    fun getTrashedProjects(): Flow<List<Project>>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getProject(id: String): Project?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: Project)

    @Update
    suspend fun updateProject(project: Project)

    @Delete
    suspend fun deleteProject(project: Project)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE projects SET deletedAtEpochMs = :epochMs WHERE id = :id")
    suspend fun softDelete(id: String, epochMs: Long)

    @Query("UPDATE projects SET deletedAtEpochMs = NULL WHERE id = :id")
    suspend fun restoreProject(id: String)

    @Query("SELECT id FROM projects WHERE deletedAtEpochMs IS NOT NULL AND deletedAtEpochMs < :cutoffEpochMs")
    suspend fun getTrashedIdsOlderThan(cutoffEpochMs: Long): List<String>

    @Query("DELETE FROM projects WHERE deletedAtEpochMs IS NOT NULL AND deletedAtEpochMs < :cutoffEpochMs")
    suspend fun purgeTrashedOlderThan(cutoffEpochMs: Long): Int

    @Query("SELECT * FROM project_media_assets WHERE projectId = :projectId ORDER BY assetId")
    suspend fun getProjectMediaAssetEntities(projectId: String): List<ProjectMediaAssetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProjectMediaAssets(assets: List<ProjectMediaAssetEntity>)

    @Query("DELETE FROM project_media_assets WHERE projectId = :projectId")
    suspend fun deleteProjectMediaAssets(projectId: String)

    @Transaction
    suspend fun replaceProjectMediaAssets(projectId: String, assets: List<ProjectMediaAssetEntity>) {
        deleteProjectMediaAssets(projectId)
        if (assets.isNotEmpty()) {
            insertProjectMediaAssets(assets)
        }
    }

    /**
     * Canonical project save. [insertProject] uses SQLite `INSERT OR REPLACE`, which
     * deletes the parent row and therefore cascades away every `project_media_assets`
     * child before re-inserting the parent. Writing the parent and the media manifest
     * as two separate suspend calls leaves a process-death window in which the project
     * survives with an empty or stale manifest.
     *
     * Both writes happen inside one Room transaction here, so a kill at any internal
     * point rolls back to the previous consistent (project, manifest) pair.
     */
    @Transaction
    suspend fun saveProjectWithMediaAssets(project: Project, assets: List<ProjectMediaAssetEntity>) {
        insertProject(project)
        deleteProjectMediaAssets(project.id)
        if (assets.isNotEmpty()) {
            insertProjectMediaAssets(assets)
        }
    }
}

class Converters {
    @ColumnTypeConverter
    fun fromAspectRatio(value: AspectRatio): String = value.name

    @ColumnTypeConverter
    fun toAspectRatio(value: String): AspectRatio = try {
        AspectRatio.valueOf(value)
    } catch (_: IllegalArgumentException) {
        AppLog.w("Converters", "Unknown aspect ratio '$value', falling back to RATIO_16_9")
        AspectRatio.RATIO_16_9
    }

    @ColumnTypeConverter
    fun fromResolution(value: Resolution): String = value.name

    @ColumnTypeConverter
    fun toResolution(value: String): Resolution = try {
        Resolution.valueOf(value)
    } catch (_: IllegalArgumentException) {
        AppLog.w("Converters", "Unknown resolution '$value', falling back to FHD_1080P")
        Resolution.FHD_1080P
    }
}

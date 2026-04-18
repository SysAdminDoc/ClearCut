package com.novacut.editor.engine.db

import android.util.Log
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.novacut.editor.model.Project
import com.novacut.editor.model.AspectRatio
import com.novacut.editor.model.Resolution
import kotlinx.coroutines.flow.Flow

@Database(entities = [Project::class], version = 6, exportSchema = true)
@TypeConverters(Converters::class)
abstract class ProjectDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao

    companion object {
        // v1→v2: Added templateId and proxyEnabled columns
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE projects ADD COLUMN templateId TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE projects ADD COLUMN proxyEnabled INTEGER NOT NULL DEFAULT 0")
            }
        }

        // v2→v3: Added version column for project snapshots
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE projects ADD COLUMN version INTEGER NOT NULL DEFAULT 1")
            }
        }

        // v3→v4: Schema freeze — establish proper migration baseline
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // No schema changes — version bump to establish migration chain
            }
        }

        // v4→v5: Add index on updatedAt for project list sort performance
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_projects_updatedAt ON projects (updatedAt)")
            }
        }

        // v5→v6: Add scratchpad notes column
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE projects ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
            }
        }

        val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
    }
}

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun getAllProjects(): Flow<List<Project>>

    /**
     * One-shot snapshot used when callers need a freshly-read list (e.g.
     * uniqueness checks during duplicate-project naming) rather than the
     * debounced StateFlow snapshot the UI observes.
     */
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    suspend fun getAllProjectsSnapshot(): List<Project>

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
}

class Converters {
    @TypeConverter
    fun fromAspectRatio(value: AspectRatio): String = value.name

    @TypeConverter
    fun toAspectRatio(value: String): AspectRatio = try {
        AspectRatio.valueOf(value)
    } catch (_: IllegalArgumentException) {
        Log.w("Converters", "Unknown aspect ratio '$value', falling back to RATIO_16_9")
        AspectRatio.RATIO_16_9
    }

    @TypeConverter
    fun fromResolution(value: Resolution): String = value.name

    @TypeConverter
    fun toResolution(value: String): Resolution = try {
        Resolution.valueOf(value)
    } catch (_: IllegalArgumentException) {
        Log.w("Converters", "Unknown resolution '$value', falling back to FHD_1080P")
        Resolution.FHD_1080P
    }
}

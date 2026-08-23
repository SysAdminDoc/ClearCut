package com.novacut.editor.engine

import com.novacut.editor.engine.db.ProjectDao
import com.novacut.editor.engine.db.toProjectMediaAssetEntities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single write owner for the canonical project document.
 *
 * Room and the recovery file intentionally remain separate stores, but callers
 * no longer need to know how a [ProjectDocument] is fanned out to both. The
 * editor supplies the recovery gate; this class owns the ordered database and
 * autosave writes and exposes their outcome as one typed result.
 */
@Singleton
class ProjectPersistenceCoordinator internal constructor(
    private val databaseWriter: suspend (ProjectDocument) -> Unit,
    private val autoSaveWriter: suspend (ProjectDocument) -> Boolean,
) {

    @Inject
    constructor(
        projectDao: ProjectDao,
        autoSave: ProjectAutoSave,
    ) : this(
        databaseWriter = { document: ProjectDocument ->
            projectDao.saveProjectWithMediaAssets(
                document.project,
                document.state.mediaAssets.toProjectMediaAssetEntities(document.project.id),
            )
        },
        autoSaveWriter = { document: ProjectDocument -> autoSave.saveNow(document) },
    )

    data class SaveResult(
        val databaseSaved: Boolean,
        val autoSaveAttempted: Boolean,
        val autoSaveSaved: Boolean,
    ) {
        val succeeded: Boolean
            get() = databaseSaved && autoSaveAttempted && autoSaveSaved
    }

    suspend fun save(
        document: ProjectDocument,
        persistenceAllowed: Boolean,
    ): SaveResult = withContext(Dispatchers.IO) {
        if (!persistenceAllowed) {
            return@withContext SaveResult(
                databaseSaved = false,
                autoSaveAttempted = false,
                autoSaveSaved = false,
            )
        }

        saveDatabase(document)
        SaveResult(
            databaseSaved = true,
            autoSaveAttempted = true,
            autoSaveSaved = autoSaveWriter(document),
        )
    }

    suspend fun saveDatabase(document: ProjectDocument) = withContext(Dispatchers.IO) {
        databaseWriter(document)
    }
}

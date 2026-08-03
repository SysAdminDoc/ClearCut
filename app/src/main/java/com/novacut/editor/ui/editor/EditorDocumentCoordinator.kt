package com.novacut.editor.ui.editor

import com.novacut.editor.engine.AutoSaveRequest
import com.novacut.editor.engine.ProjectAutoSave
import com.novacut.editor.engine.ProjectDocument
import com.novacut.editor.engine.ProjectPersistenceCoordinator
import com.novacut.editor.engine.db.ProjectDao
import com.novacut.editor.model.Project
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the editor's document boundary.
 *
 * The ViewModel still decides how a loaded document is projected into visible
 * editor state, but it no longer reaches into Room, the recovery file, or the
 * persistence coordinator itself. Keeping the load and save gates here also
 * makes it impossible for a periodic save to interleave with a user-initiated
 * save.
 */
@Singleton
class EditorDocumentCoordinator internal constructor(
    private val loadProject: suspend (String) -> Project?,
    private val loadRecovery: suspend (String) -> ProjectAutoSave.LoadOutcome,
    private val saveDocument: suspend (ProjectDocument, Boolean) -> ProjectPersistenceCoordinator.SaveResult,
    private val saveDatabase: suspend (ProjectDocument) -> Unit,
    private val loadBackup: suspend (String) -> ProjectAutoSave.LoadOutcome = {
        error("Backup recovery is not configured")
    },
    private val startAutoSave: (
        String,
        Long,
        (Boolean, AutoSaveRequest?) -> Unit,
        () -> AutoSaveRequest,
    ) -> Unit = { _, _, _, _ -> error("Auto-save is not configured") },
    private val stopAutoSave: () -> Unit = {},
    private val storageInfo: (String) -> ProjectAutoSave.StorageInfo = {
        error("Auto-save storage is not configured")
    },
) {

    @Inject
    constructor(
        projectDao: ProjectDao,
        autoSave: ProjectAutoSave,
        persistence: ProjectPersistenceCoordinator,
    ) : this(
        loadProject = { projectDao.getProject(it) },
        loadRecovery = { autoSave.loadRecoveryDataWithOutcome(it) },
        saveDocument = { document, autoSaveEnabled ->
            persistence.save(document, autoSaveEnabled)
        },
        saveDatabase = { document -> persistence.saveDatabase(document) },
        loadBackup = { autoSave.loadBackupWithOutcome(it) },
        startAutoSave = { projectId, intervalMs, onSaveResult, getRequest ->
            autoSave.startAutoSave(projectId, intervalMs, onSaveResult, getRequest)
        },
        stopAutoSave = { autoSave.stop() },
        storageInfo = { autoSave.getStorageInfo(it) },
    )

    private val saveMutex = Mutex()

    data class OpenResult(
        val project: Project?,
        val recovery: ProjectAutoSave.LoadOutcome?,
        val projectNotFound: Boolean,
    )

    suspend fun open(projectId: String?, recoveryId: String): OpenResult {
        val project = if (projectId == null) null else loadProject(projectId)
        if (projectId != null && project == null) {
            return OpenResult(
                project = null,
                recovery = null,
                projectNotFound = true,
            )
        }
        return OpenResult(
            project = project,
            recovery = loadRecovery(recoveryId),
            projectNotFound = false,
        )
    }

    suspend fun save(
        document: ProjectDocument,
        autoSaveEnabled: Boolean,
    ): ProjectPersistenceCoordinator.SaveResult = saveMutex.withLock {
        saveDocument(document, autoSaveEnabled)
    }

    suspend fun saveDatabase(document: ProjectDocument) = saveMutex.withLock {
        saveDatabase.invoke(document)
    }

    suspend fun loadBackupWithOutcome(projectId: String): ProjectAutoSave.LoadOutcome =
        loadBackup(projectId)

    fun startAutoSave(
        projectId: String,
        intervalMs: Long,
        onSaveResult: (Boolean, AutoSaveRequest?) -> Unit,
        getRequest: () -> AutoSaveRequest,
    ) = startAutoSave.invoke(projectId, intervalMs, onSaveResult, getRequest)

    fun stopAutoSave() = stopAutoSave.invoke()

    fun getStorageInfo(projectId: String): ProjectAutoSave.StorageInfo = storageInfo(projectId)
}

package com.novacut.editor.ui.projects

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import com.novacut.editor.engine.AppLog
import androidx.core.content.FileProvider
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.novacut.editor.MainActivity
import com.novacut.editor.engine.ProjectShortcutPlanner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novacut.editor.R
import com.novacut.editor.engine.AutoSaveState
import com.novacut.editor.engine.IncomingDocumentImportPreview
import com.novacut.editor.engine.IncomingDocumentImportRouter
import com.novacut.editor.engine.IncomingDocumentItem
import com.novacut.editor.engine.IncomingMediaItem
import com.novacut.editor.engine.IncomingMediaKind
import com.novacut.editor.engine.ProjectAutoSave
import com.novacut.editor.engine.ProjectDocumentApplicator
import com.novacut.editor.engine.SettingsRepository
import com.novacut.editor.engine.TemplateImportFailure
import com.novacut.editor.engine.TemplateImportResult
import com.novacut.editor.engine.TemplateManager
import com.novacut.editor.engine.MediaImportEngine
import com.novacut.editor.engine.UserTemplate
import com.novacut.editor.engine.VideoEngine
import com.novacut.editor.engine.attachMediaAssetIdsToTracks
import com.novacut.editor.engine.buildProjectMediaAssets
import com.novacut.editor.engine.deleteManagedMediaUri
import com.novacut.editor.engine.importUriToManagedMedia
import com.novacut.editor.engine.resolveMediaDisplayName
import com.novacut.editor.engine.sanitizeFileName
import com.novacut.editor.engine.sweepUnreferencedArchiveImports
import com.novacut.editor.engine.sweepUnreferencedManagedMedia
import com.novacut.editor.engine.db.ProjectDao
import com.novacut.editor.engine.db.toProjectMediaAssetEntities
import com.novacut.editor.model.AspectRatio
import com.novacut.editor.model.Clip
import com.novacut.editor.model.Project
import com.novacut.editor.model.Resolution
import com.novacut.editor.model.ProjectFilterMode
import com.novacut.editor.model.SortMode
import com.novacut.editor.model.SourceColorMetadata
import com.novacut.editor.model.Track
import com.novacut.editor.model.TrackType
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject

data class ProjectListOperationState(
    val id: Long = SystemClock.uptimeMillis(),
    val title: String,
    val description: String
)

@HiltViewModel
class ProjectListViewModel @Inject constructor(
    private val projectDao: ProjectDao,
    private val autoSave: ProjectAutoSave,
    private val templateManager: TemplateManager,
    private val videoEngine: VideoEngine,
    private val settingsRepo: SettingsRepository,
    private val mediaImportEngine: MediaImportEngine,
    private val incomingDocumentImportRouter: IncomingDocumentImportRouter,
    @ApplicationContext private val appContext: Context
) : ViewModel() {
    private companion object {
        private const val MAX_PROJECT_NAME_CHARS = 80
        private const val TAG = "ProjectListViewModel"
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortMode = MutableStateFlow(SortMode.DATE_DESC)
    val sortMode: StateFlow<SortMode> = _sortMode.asStateFlow()

    private val _filterMode = MutableStateFlow(ProjectFilterMode.ALL)
    val filterMode: StateFlow<ProjectFilterMode> = _filterMode.asStateFlow()

    private val _userTemplates = MutableStateFlow<List<UserTemplate>>(emptyList())
    val userTemplates: StateFlow<List<UserTemplate>> = _userTemplates.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    private val _operationState = MutableStateFlow<ProjectListOperationState?>(null)
    val operationState: StateFlow<ProjectListOperationState?> = _operationState.asStateFlow()

    private val _documentImportPreview = MutableStateFlow<IncomingDocumentImportPreview?>(null)
    val documentImportPreview: StateFlow<IncomingDocumentImportPreview?> = _documentImportPreview.asStateFlow()

    private var toastDismissJob: Job? = null

    private val allProjects: StateFlow<List<Project>> = projectDao.getAllProjects()
        // Room re-emits on any table write even when the query result is identical; collapse
        // those duplicates so the filtered/sorted StateFlow below doesn't force the grid to
        // recompose on every unrelated project update (e.g. auto-save bumping updatedAt).
        .distinctUntilChanged()
        .onEach { _isLoading.value = false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isLoading = MutableStateFlow(true)

    /**
     * True until Room first emits. The list seeded straight to an empty list, so the
     * first-run empty state ("No projects yet") flashed on every cold start before the
     * user's projects arrived -- the app told returning users they had nothing.
     */
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val projectTotalCount: StateFlow<Int> = allProjects
        .map { it.size }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val projects: StateFlow<List<Project>> = combine(
        allProjects, _searchQuery, _sortMode, _filterMode
    ) { projects, query, sort, filter ->
        val searched = if (query.isBlank()) projects
        else projects.filter { it.name.contains(query, ignoreCase = true) }

        // Apply the chip filter after the free-text search so users can
        // search within a subset (e.g. "Under 10 s" + search "intro").
        val now = System.currentTimeMillis()
        val filtered = when (filter) {
            ProjectFilterMode.ALL -> searched
            ProjectFilterMode.RECENT_7D -> {
                val weekAgo = now - 7L * 24L * 60L * 60L * 1000L
                searched.filter { it.updatedAt >= weekAgo }
            }
            ProjectFilterMode.LONG -> searched.filter { it.durationMs >= 60_000L }
            ProjectFilterMode.SHORT -> searched.filter {
                it.durationMs in 1L..9_999L
            }
            ProjectFilterMode.EMPTY -> searched.filter { it.durationMs <= 0L }
        }

        when (sort) {
            SortMode.DATE_DESC -> filtered.sortedByDescending { it.updatedAt }
            SortMode.DATE_ASC -> filtered.sortedBy { it.updatedAt }
            SortMode.NAME_ASC -> filtered.sortedBy { it.name.lowercase() }
            SortMode.NAME_DESC -> filtered.sortedByDescending { it.name.lowercase() }
            SortMode.DURATION_DESC -> filtered.sortedByDescending { it.durationMs }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val trashedProjects = projectDao.getTrashedProjects()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        refreshUserTemplates()
        viewModelScope.launch(Dispatchers.IO) {
            val cutoffMs = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
            val purged = purgeTrashedProjects(cutoffMs)
            if (purged > 0) {
                AppLog.d("ProjectListVM", "Auto-purged $purged trashed projects older than 30 days")
                sweepManagedMediaAfterDeletion()
            }
        }
        viewModelScope.launch {
            allProjects.collect { projects ->
                refreshDynamicShortcuts(projects)
            }
        }
    }

    /**
     * Compute and push the dynamic launcher shortcut list. Side-effect only —
     * `ProjectShortcutPlanner.planDynamic(state)` is the pure decision; this
     * function does the Android-side mapping to `ShortcutInfoCompat` + the
     * platform call.
     */
    private suspend fun refreshDynamicShortcuts(projects: List<Project>) {
        val last = projects.maxByOrNull { it.updatedAt }
        val hasRecovery = last?.id?.let { autoSave.hasRecoveryData(it) } ?: false
        val state = ProjectShortcutPlanner.State(
            lastProjectId = last?.id,
            lastProjectName = last?.name,
            hasRecoveryForLast = hasRecovery,
        )
        val planned = ProjectShortcutPlanner.planDynamic(state)
        val shortcuts = planned.map { it.toShortcutInfoCompat(appContext) }
        try {
            ShortcutManagerCompat.setDynamicShortcuts(appContext, shortcuts)
        } catch (e: Exception) {
            // Some launchers (OEM forks) reject excess shortcuts or refuse
            // updates from a backgrounded process. The shortcut list is a
            // pure affordance — losing it is never worth a crash.
            AppLog.w(TAG, "Failed to set dynamic shortcuts (${planned.size} entries)", e)
        }
    }

    private fun ProjectShortcutPlanner.DynamicShortcut.toShortcutInfoCompat(
        ctx: Context,
    ): ShortcutInfoCompat {
        val intent = Intent(action).apply {
            // Launch the existing MainActivity entry point. The action string
            // is the routing key handleIncomingIntent reads.
            setClassName(ctx, MainActivity::class.java.name)
            // Qualify the receiver: bare `extras` inside this Intent.apply{} block
            // resolves to Intent.extras (a non-iterable Bundle?), not the shortcut's
            // Map<String, String>. Use the outer extension receiver explicitly.
            for ((key, value) in this@toShortcutInfoCompat.extras) putExtra(key, value)
        }
        return ShortcutInfoCompat.Builder(ctx, shortcutId)
            .setShortLabel(shortLabel)
            .setLongLabel(longLabel)
            .setRank(rank)
            .setIntent(intent)
            // Reuse the launcher mipmap so a dedicated raster isn't required
            // up-front. The Resume / Open distinction is in the label, not
            // the icon, which is the most accessible default.
            .setIcon(IconCompat.createWithResource(ctx, R.mipmap.ic_launcher))
            .build()
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSortMode(mode: SortMode) {
        _sortMode.value = mode
    }

    fun setFilterMode(mode: ProjectFilterMode) {
        _filterMode.value = mode
    }

    /**
     * Create a project. A null [aspectRatio], [frameRate] or [resolution] means the
     * caller has no opinion, so the user's Settings defaults apply -- which is what
     * makes those Settings controls do anything. A template that exists to produce a
     * specific format (a 9:16 short, say) passes its own values and keeps them.
     */
    fun createProject(
        name: String = "",
        aspectRatio: AspectRatio? = null,
        frameRate: Int? = null,
        resolution: Resolution? = null,
        templateId: String? = null,
        trackTypes: List<TrackType> = listOf(TrackType.VIDEO, TrackType.AUDIO),
        onCreated: (String) -> Unit = {}
    ) {
        val normalizedName = normalizeProjectName(name)

        viewModelScope.launch {
            val settings = settingsRepo.settings.first()
            val project = Project(
                name = normalizedName,
                aspectRatio = aspectRatio ?: settings.defaultAspectRatio,
                frameRate = frameRate ?: settings.defaultFrameRate,
                resolution = resolution ?: settings.defaultResolution,
                templateId = templateId
            )
            val initialTracks = buildTracks(trackTypes, settings.defaultTrackHeight)
            val operation = beginOperation(
                title = appContext.getString(R.string.projects_operation_create_title),
                description = appContext.getString(R.string.projects_operation_create_body)
            )
            try {
                val created = withContext(Dispatchers.IO) {
                    createProjectWithInitialState(
                        project = project,
                        initialState = AutoSaveState(
                            projectId = project.id,
                            tracks = initialTracks,
                            textOverlays = emptyList()
                        )
                    )
                }
                if (created) {
                    onCreated(project.id)
                } else {
                    showToast(appContext.getString(R.string.project_create_failed))
                }
            } finally {
                endOperation(operation)
            }
        }
    }

    fun deleteProject(project: Project) {
        viewModelScope.launch {
            val operation = beginOperation(
                title = appContext.getString(R.string.projects_operation_delete_title),
                description = appContext.getString(R.string.projects_operation_delete_body, project.name)
            )
            try {
                withContext(Dispatchers.IO) {
                    projectDao.softDelete(project.id, System.currentTimeMillis())
                }
                showToast(appContext.getString(R.string.project_delete_success, project.name))
            } catch (e: Exception) {
                AppLog.w("ProjectListVM", "Failed to soft-delete project ${project.id}", e)
                showToast(appContext.getString(R.string.project_delete_failed))
            } finally {
                endOperation(operation)
            }
        }
    }

    fun restoreProject(project: Project) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    projectDao.restoreProject(project.id)
                }
                showToast(appContext.getString(R.string.project_restore_success, project.name))
            } catch (e: Exception) {
                AppLog.w("ProjectListVM", "Failed to restore project ${project.id}", e)
                showToast(appContext.getString(R.string.project_restore_failed))
            }
        }
    }

    fun deleteProjectForever(project: Project) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    deleteProjectAndCleanup(project)
                }
                showToast(appContext.getString(R.string.project_delete_forever_success, project.name))
            } catch (e: Exception) {
                AppLog.w("ProjectListVM", "Failed to permanently delete ${project.id}", e)
                showToast(appContext.getString(R.string.project_delete_forever_failed))
            }
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            try {
                val count = withContext(Dispatchers.IO) {
                    purgeTrashedProjects(Long.MAX_VALUE)
                }
                if (count > 0) sweepManagedMediaAfterDeletion()
                showToast(
                    appContext.resources.getQuantityString(
                        R.plurals.trash_empty_success,
                        count,
                        count
                    )
                )
            } catch (e: Exception) {
                AppLog.w("ProjectListVM", "Failed to empty trash", e)
                showToast(appContext.getString(R.string.trash_empty_failed))
            }
        }
    }

    fun renameProject(project: Project, newName: String) {
        val normalizedName = normalizeProjectName(newName)
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // Re-read the row so a rename never clobbers columns another
                    // writer updated after this list item was rendered.
                    val current = projectDao.getProject(project.id) ?: project
                    projectDao.updateProject(
                        current.copy(name = normalizedName, updatedAt = System.currentTimeMillis())
                    )
                }
            } catch (e: Exception) {
                AppLog.w("ProjectListVM", "Failed to rename project ${project.id}", e)
                showToast(appContext.getString(R.string.project_rename_failed))
            }
        }
    }

    fun deleteUserTemplate(id: String) {
        viewModelScope.launch {
            val operation = beginOperation(
                title = appContext.getString(R.string.projects_operation_template_delete_title),
                description = appContext.getString(R.string.projects_operation_template_delete_body)
            )
            try {
                val deleteResult = withContext(Dispatchers.IO) {
                    val template = templateManager.getTemplate(id)
                    template?.name to templateManager.deleteTemplate(id)
                }
                loadUserTemplates()
                val restoreOffer = if (deleteResult.second) {
                    // The template went to the trash, not to nothing. Keep its id with
                    // the snackbar action so authored work can be restored without
                    // opening a second modal after the confirmation dialog.
                    RestorableTemplate(id = id, name = deleteResult.first)
                } else {
                    null
                }
                showToast(
                    if (deleteResult.second) {
                        deleteResult.first?.let { templateName ->
                            appContext.getString(R.string.project_template_delete_success, templateName)
                        } ?: appContext.getString(R.string.project_template_delete_success_generic)
                    } else {
                        appContext.getString(R.string.project_template_delete_failed)
                    },
                    restoreTemplate = restoreOffer
                )
            } catch (e: Exception) {
                AppLog.w("ProjectListVM", "Template delete failed", e)
                showToast(appContext.getString(R.string.project_template_delete_failed))
            } finally {
                endOperation(operation)
            }
        }
    }

    /** A template sitting in the trash, still restorable from the templates sheet. */
    data class RestorableTemplate(val id: String, val name: String?)

    private val _restorableTemplate = MutableStateFlow<RestorableTemplate?>(null)
    val restorableTemplate: StateFlow<RestorableTemplate?> = _restorableTemplate.asStateFlow()

    /** Put the most recently deleted template back where it was. */
    fun restoreDeletedTemplate() {
        val pending = _restorableTemplate.value ?: return
        _restorableTemplate.value = null
        viewModelScope.launch {
            val restored = withContext(Dispatchers.IO) { templateManager.restoreTemplate(pending.id) }
            loadUserTemplates()
            showToast(
                if (restored) {
                    pending.name?.let { appContext.getString(R.string.project_template_restore_success, it) }
                        ?: appContext.getString(R.string.project_template_restore_success_generic)
                } else {
                    appContext.getString(R.string.project_template_restore_failed)
                }
            )
        }
    }

    fun importTemplate(uri: Uri) {
        viewModelScope.launch {
            val operation = beginOperation(
                title = appContext.getString(R.string.projects_operation_template_import_title),
                description = appContext.getString(R.string.projects_operation_template_import_body)
            )
            try {
                val importResult = withContext(Dispatchers.IO) {
                    templateManager.importTemplateFromUriDetailed(uri)
                }
                loadUserTemplates()

                val template = importResult.template
                if (template != null) {
                    val warning = importResult.restoreReport.takeIf { it.isPartial }
                        ?.let {
                            appContext.getString(
                                R.string.project_template_import_partial_warning,
                                it.summary()
                            )
                        }
                    showToast(
                        listOfNotNull(
                            appContext.getString(R.string.project_template_import_success, template.name),
                            warning
                        ).joinToString(" ")
                    )
                } else {
                    showToast(templateImportFailureMessage(importResult))
                }
            } catch (e: Exception) {
                AppLog.w("ProjectListVM", "Template import failed", e)
                showToast(appContext.getString(R.string.project_template_import_failed))
            } finally {
                endOperation(operation)
            }
        }
    }

    fun previewIncomingDocuments(items: List<IncomingDocumentItem>) {
        val documents = items.ifEmpty { return }
        viewModelScope.launch {
            val operation = beginOperation(
                title = appContext.getString(R.string.projects_operation_document_import_title),
                description = appContext.getString(R.string.projects_operation_document_import_body)
            )
            try {
                val preview = withContext(Dispatchers.IO) {
                    incomingDocumentImportRouter.preview(documents.first())
                }.let { result ->
                    if (documents.size > 1) {
                        result.copy(
                            warnings = result.warnings +
                                appContext.getString(R.string.project_document_preview_multiple_warning)
                        )
                    } else {
                        result
                    }
                }
                _documentImportPreview.value = preview
            } catch (e: Exception) {
                AppLog.w("ProjectListVM", "Document import preview failed", e)
                showToast(appContext.getString(R.string.project_document_import_failed))
            } finally {
                endOperation(operation)
            }
        }
    }

    fun importPreviewedDocument() {
        val preview = _documentImportPreview.value ?: return
        viewModelScope.launch {
            val operation = beginOperation(
                title = appContext.getString(R.string.projects_operation_document_import_title),
                description = appContext.getString(R.string.projects_operation_document_import_body)
            )
            try {
                val imported = withContext(Dispatchers.IO) {
                    incomingDocumentImportRouter.commit(preview.item)
                }
                _documentImportPreview.value = imported
                loadUserTemplates()
                showToast(imported.title)
            } catch (e: Exception) {
                AppLog.w("ProjectListVM", "Document import failed", e)
                showToast(appContext.getString(R.string.project_document_import_failed))
            } finally {
                endOperation(operation)
            }
        }
    }

    fun dismissDocumentImportPreview() {
        _documentImportPreview.value = null
    }

    private fun templateImportFailureMessage(result: TemplateImportResult): String {
        return when (result.failure) {
            TemplateImportFailure.INCOMPATIBLE -> appContext.getString(R.string.project_template_import_incompatible)
            TemplateImportFailure.OVERSIZED_FILE -> appContext.getString(R.string.project_template_import_too_large)
            TemplateImportFailure.INVALID_JSON,
            TemplateImportFailure.INVALID_STATE -> appContext.getString(R.string.project_template_import_invalid)
            TemplateImportFailure.UNREADABLE_FILE,
            TemplateImportFailure.WRITE_FAILED,
            TemplateImportFailure.NONE -> appContext.getString(R.string.project_template_import_failed)
        }
    }

    fun shareTemplate(templateId: String) {
        viewModelScope.launch {
            val operation = beginOperation(
                title = appContext.getString(R.string.projects_operation_template_share_title),
                description = appContext.getString(R.string.projects_operation_template_share_body)
            )
            try {
                val shareUri = withContext(Dispatchers.IO) {
                    val template = templateManager.getTemplate(templateId) ?: return@withContext null
                    val dir = File(appContext.getExternalFilesDir(null) ?: appContext.filesDir, "archives/templates").apply { mkdirs() }
                    val sanitized = sanitizeFileName(template.name, fallback = "template")
                    val outputFile = File(dir, "$sanitized.clearcut-template")
                    val success = templateManager.exportTemplateToFile(template.id, outputFile)
                    if (!success) return@withContext null

                    FileProvider.getUriForFile(
                        appContext,
                        "${appContext.packageName}.fileprovider",
                        outputFile
                    )
                }

                if (shareUri == null) {
                    showToast(appContext.getString(R.string.project_template_export_failed))
                    return@launch
                }

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_STREAM, shareUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                appContext.startActivity(
                    Intent.createChooser(
                        shareIntent,
                        appContext.getString(R.string.project_template_share_chooser)
                    )
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (e: Exception) {
                showToast(appContext.getString(R.string.project_template_export_failed))
            } finally {
                endOperation(operation)
            }
        }
    }

    fun createFromTemplate(template: UserTemplate, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val operation = beginOperation(
                title = appContext.getString(R.string.projects_operation_template_create_title),
                description = appContext.getString(R.string.projects_operation_template_create_body)
            )
            try {
                val loaded = withContext(Dispatchers.IO) {
                    templateManager.loadTemplateState(template)
                }
                if (loaded == null) {
                    showToast(appContext.getString(R.string.project_template_open_failed))
                    return@launch
                }
                val tracks = loaded.tracks
                val textOverlays = loaded.textOverlays
                val project = Project(
                    name = normalizeProjectName(template.name),
                    aspectRatio = template.aspectRatio,
                    frameRate = template.frameRate,
                    frameRateNumerator = template.frameRateNumerator,
                    frameRateDenominator = template.frameRateDenominator,
                    resolution = template.resolution,
                    templateId = template.id
                )
                val created = withContext(Dispatchers.IO) {
                    createProjectWithInitialState(
                        project = project,
                        initialState = AutoSaveState(
                            projectId = project.id,
                            tracks = tracks.map { track ->
                                track.copy(
                                    clips = if (track.type == TrackType.VIDEO || track.type == TrackType.AUDIO) {
                                        emptyList()
                                    } else {
                                        track.clips
                                    }
                                )
                            },
                            textOverlays = textOverlays
                        )
                    )
                }
                if (created) {
                    onCreated(project.id)
                    if (loaded.restoreReport.isPartial) {
                        showToast(
                            appContext.getString(
                                R.string.project_template_restore_partial_warning,
                                loaded.restoreReport.summary()
                            )
                        )
                    }
                } else {
                    showToast(appContext.getString(R.string.project_create_failed))
                }
            } catch (e: Exception) {
                AppLog.w("ProjectListVM", "Failed to create project from template ${template.id}", e)
                showToast(appContext.getString(R.string.project_create_failed))
            } finally {
                endOperation(operation)
            }
        }
    }

    fun duplicateProject(project: Project) {
        val newId = UUID.randomUUID().toString()
        val baseName = normalizeProjectName(project.name.replace("""\s*\(Copy\s*\d*\)\s*$""".toRegex(), ""))
        viewModelScope.launch {
            val operation = beginOperation(
                title = appContext.getString(R.string.projects_operation_duplicate_title),
                description = appContext.getString(R.string.projects_operation_duplicate_body, project.name)
            )
            try {
                val duplicated = withContext(Dispatchers.IO) {
                    try {
                        // Compute the unique copy name inside the IO coroutine so the
                        // name-uniqueness check reads the freshest DAO snapshot instead
                        // of a potentially stale StateFlow value on the UI thread. This
                        // closes a race where two near-simultaneous duplicate taps could
                        // mint the same "(Copy)" name before either insertion settles.
                        val existingNames = projectDao.getAllProjectsSnapshot().map { it.name }.toSet()
                        var copyName = projectCopyName(baseName, " (Copy)")
                        var counter = 2
                        while (copyName in existingNames) {
                            copyName = projectCopyName(baseName, " (Copy $counter)")
                            counter++
                        }
                        val newProject = project.copy(
                            id = newId,
                            name = copyName,
                            createdAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis()
                        )
                        projectDao.saveProjectWithMediaAssets(
                            newProject,
                            projectDao.getProjectMediaAssetEntities(project.id).map { it.copy(projectId = newId) }
                        )
                        if (autoSave.copyAutoSave(project.id, newId)) {
                            true
                        } else {
                            projectDao.deleteById(newId)
                            false
                        }
                    } catch (e: Exception) {
                        AppLog.w("ProjectListVM", "Failed to duplicate project ${project.id}", e)
                        runCatching { projectDao.deleteById(newId) }
                        false
                    }
                }
                if (duplicated) {
                    showToast(appContext.getString(R.string.project_duplicate_success))
                } else {
                    showToast(appContext.getString(R.string.project_duplicate_failed))
                }
            } finally {
                endOperation(operation)
            }
        }
    }

    fun createProjectFromImport(videoUri: Uri, onCreated: (String) -> Unit) {
        createProjectFromImports(
            listOf(IncomingMediaItem(uri = videoUri, kind = IncomingMediaKind.VIDEO)),
            onCreated = onCreated
        )
    }

    fun createProjectFromImports(items: List<IncomingMediaItem>, onCreated: (String) -> Unit) {
        val incomingItems = items.ifEmpty { return }
        viewModelScope.launch {
            val operation = beginOperation(
                title = appContext.getString(R.string.projects_operation_media_import_title),
                description = appContext.getString(R.string.projects_operation_media_import_body)
            )
            val copiedManagedUris = mutableListOf<Uri>()
            try {
                val imported = withContext(Dispatchers.IO) {
                    importIncomingMediaItems(incomingItems, copiedManagedUris)
                }
                if (imported.isEmpty()) {
                    showToast(appContext.getString(R.string.project_import_invalid_media))
                    return@launch
                }

                val fileName = resolveMediaDisplayName(appContext, incomingItems.first().uri)
                    ?.substringBeforeLast('.')
                    ?.let(::normalizeProjectName)
                    ?: appContext.getString(R.string.project_imported_default_name)
                val visualClips = imported.filter { it.trackType == TrackType.VIDEO }
                val audioClips = imported.filter { it.trackType == TrackType.AUDIO }
                val importedTracks = buildTracks(
                    listOf(TrackType.VIDEO, TrackType.AUDIO),
                    settingsRepo.settings.first().defaultTrackHeight,
                ).map { track ->
                    when (track.type) {
                        TrackType.VIDEO -> track.copy(clips = visualClips.map { it.clip })
                        TrackType.AUDIO -> track.copy(clips = audioClips.map { it.clip })
                        else -> track
                    }
                }
                val durationMs = importedTracks.maxOfOrNull { track ->
                    track.clips.maxOfOrNull { it.timelineEndMs } ?: 0L
                } ?: 0L

                val project = Project(
                    name = fileName,
                    durationMs = durationMs,
                    thumbnailUri = visualClips.firstOrNull { it.kind == IncomingMediaKind.VIDEO }
                        ?.clip
                        ?.sourceUri
                        ?.toString()
                )

                val created = withContext(Dispatchers.IO) {
                    createProjectWithInitialState(
                        project = project,
                        initialState = AutoSaveState(
                            projectId = project.id,
                            tracks = importedTracks,
                            textOverlays = emptyList()
                        )
                    )
                }
                if (created) {
                    if (imported.size < incomingItems.size) {
                        showToast(
                            appContext.getString(
                                R.string.project_import_partial_success,
                                imported.size,
                                incomingItems.size
                            )
                        )
                    }
                    onCreated(project.id)
                } else {
                    copiedManagedUris.forEach { deleteManagedMediaUri(appContext, it) }
                    showToast(appContext.getString(R.string.project_create_failed))
                }
            } catch (e: Exception) {
                AppLog.w("ProjectListVM", "Incoming media import failed", e)
                copiedManagedUris.forEach { deleteManagedMediaUri(appContext, it) }
                showToast(appContext.getString(R.string.project_import_invalid_media))
            } finally {
                endOperation(operation)
            }
        }
    }

    private data class ImportedIncomingMedia(
        val kind: IncomingMediaKind,
        val trackType: TrackType,
        val clip: Clip
    )

    private fun importIncomingMediaItems(
        incomingItems: List<IncomingMediaItem>,
        copiedManagedUris: MutableList<Uri>
    ): List<ImportedIncomingMedia> {
        var nextVisualStartMs = 0L
        var nextAudioStartMs = 0L
        return incomingItems.mapNotNull { item ->
            val managedUri = importUriToManagedMedia(appContext, item.uri, item.kind.mediaType)
                ?: return@mapNotNull null
            if (managedUri.toString() != item.uri.toString()) {
                copiedManagedUris += managedUri
            }

            val readable = runCatching {
                appContext.contentResolver.openAssetFileDescriptor(managedUri, "r")?.use { true } ?: false
            }.getOrDefault(managedUri.scheme == "file")
            if (!readable) {
                deleteManagedMediaUri(appContext, managedUri)
                copiedManagedUris.remove(managedUri)
                return@mapNotNull null
            }

            val hasRequiredTrack = when (item.kind) {
                IncomingMediaKind.VIDEO, IncomingMediaKind.IMAGE -> videoEngine.hasVisualTrack(managedUri)
                IncomingMediaKind.AUDIO -> videoEngine.hasAudioTrack(managedUri)
            }
            if (!hasRequiredTrack) {
                deleteManagedMediaUri(appContext, managedUri)
                copiedManagedUris.remove(managedUri)
                return@mapNotNull null
            }

            val durationMs = videoEngine.getMediaDuration(managedUri).takeIf { it > 0L } ?: run {
                deleteManagedMediaUri(appContext, managedUri)
                copiedManagedUris.remove(managedUri)
                return@mapNotNull null
            }
            val trackType = if (item.kind == IncomingMediaKind.AUDIO) TrackType.AUDIO else TrackType.VIDEO
            val timelineStartMs = if (trackType == TrackType.AUDIO) nextAudioStartMs else nextVisualStartMs
            val sourceColorMetadata = if (trackType == TrackType.VIDEO) {
                mediaImportEngine.inspectSourceColor(managedUri)
            } else {
                SourceColorMetadata()
            }
            val clip = Clip(
                sourceUri = managedUri,
                sourceDurationMs = durationMs,
                timelineStartMs = timelineStartMs,
                trimStartMs = 0L,
                trimEndMs = durationMs,
                sourceColorMetadata = sourceColorMetadata
            )
            if (trackType == TrackType.AUDIO) {
                nextAudioStartMs = clip.timelineEndMs
            } else {
                nextVisualStartMs = clip.timelineEndMs
            }
            ImportedIncomingMedia(kind = item.kind, trackType = trackType, clip = clip)
        }
    }

    private fun refreshUserTemplates() {
        viewModelScope.launch {
            loadUserTemplates()
        }
    }

    private suspend fun loadUserTemplates() {
        val templates = withContext(Dispatchers.IO) {
            templateManager.listTemplates()
        }
        _userTemplates.value = templates
    }

    private fun buildTracks(trackTypes: List<TrackType>, trackHeight: Int): List<Track> {
        val normalizedTypes = trackTypes.ifEmpty { listOf(TrackType.VIDEO, TrackType.AUDIO) }
        return normalizedTypes.mapIndexed { index, type ->
            Track(type = type, index = index, trackHeight = trackHeight.coerceIn(48, 120))
        }
    }

    private fun normalizeProjectName(raw: String): String {
        val normalized = raw
            .map { char -> if (char.isISOControl()) ' ' else char }
            .joinToString("")
            .replace(Regex("""\s+"""), " ")
            .trim()
        val defaultProjectName = appContext.getString(R.string.project_untitled)
        return normalized.ifBlank { defaultProjectName }
            .take(MAX_PROJECT_NAME_CHARS)
            .trim()
            .ifBlank { defaultProjectName }
    }

    private fun projectCopyName(baseName: String, suffix: String): String {
        val maxBaseChars = (MAX_PROJECT_NAME_CHARS - suffix.length).coerceAtLeast(1)
        val defaultProjectName = appContext.getString(R.string.project_untitled)
        val boundedBase = baseName.take(maxBaseChars).trim().ifBlank { defaultProjectName.take(maxBaseChars) }
        return "$boundedBase$suffix"
    }

    private fun beginOperation(title: String, description: String): ProjectListOperationState {
        return ProjectListOperationState(title = title, description = description).also {
            _operationState.value = it
        }
    }

    private fun endOperation(operation: ProjectListOperationState) {
        if (_operationState.value?.id == operation.id) {
            _operationState.value = null
        }
    }

    private suspend fun createProjectWithInitialState(
        project: Project,
        initialState: AutoSaveState
    ): Boolean {
        return try {
            val document = ProjectDocumentApplicator.capture(project, initialState)
            val mediaAssets = buildProjectMediaAssets(appContext, document.state)
            val stateWithAssets = document.state.copy(
                tracks = attachMediaAssetIdsToTracks(document.state.tracks, mediaAssets),
                mediaAssets = mediaAssets
            )
            projectDao.saveProjectWithMediaAssets(
                project,
                stateWithAssets.mediaAssets.toProjectMediaAssetEntities(project.id)
            )
            if (autoSave.saveNow(ProjectDocumentApplicator.capture(project, stateWithAssets))) {
                true
            } else {
                projectDao.deleteById(project.id)
                false
            }
        } catch (e: Exception) {
            AppLog.w("ProjectListVM", "Failed to create project ${project.id}", e)
            runCatching { projectDao.deleteById(project.id) }
            false
        }
    }

    /**
     * Hard-delete trashed projects older than [cutoffEpochMs] AND their
     * recovery JSON. The managed-media sweep builds its keep-set from every
     * autosave file on disk, so leaving a purged project's autosave behind
     * would pin its imported media forever.
     */
    private suspend fun purgeTrashedProjects(cutoffEpochMs: Long): Int {
        val purgedIds = projectDao.getTrashedIdsOlderThan(cutoffEpochMs)
        val count = projectDao.purgeTrashedOlderThan(cutoffEpochMs)
        for (id in purgedIds) {
            runCatching { autoSave.clearRecoveryData(id) }
                .onFailure { error ->
                    AppLog.w("ProjectListVM", "Purged project $id, but recovery cleanup failed", error)
                }
        }
        return count
    }

    private suspend fun deleteProjectAndCleanup(project: Project): Boolean {
        return try {
            projectDao.deleteProject(project)
            runCatching { autoSave.clearRecoveryData(project.id) }
                .onFailure { error ->
                    AppLog.w("ProjectListVM", "Deleted project ${project.id}, but recovery cleanup failed", error)
                }
            sweepManagedMediaAfterDeletion()
            true
        } catch (e: Exception) {
            AppLog.w("ProjectListVM", "Failed to delete project ${project.id}", e)
            false
        }
    }

    private suspend fun sweepManagedMediaAfterDeletion() {
        // Sweep the managed-media dir against the union of sourceUris in every
        // remaining project's auto-save JSON. The 24h min-age buffer inside the
        // sweeper avoids racing a fresh import that has not been auto-saved yet.
        try {
            val referenced = autoSave.collectReferencedSourceUris()
                .map { Uri.parse(it) }
                .toSet()
            val result = sweepUnreferencedManagedMedia(appContext, referenced)
            if (result.filesDeleted > 0) {
                AppLog.d(
                    "ProjectListVM",
                    "Swept ${result.filesDeleted} orphan imports (${result.bytesFreed / 1024} KB)"
                )
            }
            val archiveResult = sweepUnreferencedArchiveImports(appContext, referenced)
            if (archiveResult.filesDeleted > 0) {
                AppLog.d(
                    "ProjectListVM",
                    "Swept ${archiveResult.filesDeleted} orphan archive-import files " +
                        "(${archiveResult.bytesFreed / 1024} KB)"
                )
            }
        } catch (e: Exception) {
            AppLog.w("ProjectListVM", "Managed-media sweep failed", e)
        }
    }

    private fun showToast(message: String, restoreTemplate: RestorableTemplate? = null) {
        toastDismissJob?.cancel()
        _restorableTemplate.value = restoreTemplate
        _toastMessage.value = message
        toastDismissJob = viewModelScope.launch {
            delay(2800L)
            if (_toastMessage.value == message) {
                _toastMessage.value = null
                if (_restorableTemplate.value == restoreTemplate) {
                    _restorableTemplate.value = null
                }
            }
        }
    }

    fun dismissToast() {
        toastDismissJob?.cancel()
        _toastMessage.value = null
        _restorableTemplate.value = null
    }
}

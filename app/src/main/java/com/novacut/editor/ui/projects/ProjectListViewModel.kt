package com.novacut.editor.ui.projects

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novacut.editor.R
import com.novacut.editor.engine.AutoSaveState
import com.novacut.editor.engine.ProjectAutoSave
import com.novacut.editor.engine.TemplateManager
import com.novacut.editor.engine.UserTemplate
import com.novacut.editor.engine.VideoEngine
import com.novacut.editor.engine.deleteManagedMediaUri
import com.novacut.editor.engine.importUriToManagedMedia
import com.novacut.editor.engine.resolveMediaDisplayName
import com.novacut.editor.engine.sanitizeFileName
import com.novacut.editor.engine.db.ProjectDao
import com.novacut.editor.model.AspectRatio
import com.novacut.editor.model.Clip
import com.novacut.editor.model.Project
import com.novacut.editor.model.Resolution
import com.novacut.editor.model.ProjectFilterMode
import com.novacut.editor.model.SortMode
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ProjectListViewModel @Inject constructor(
    private val projectDao: ProjectDao,
    private val autoSave: ProjectAutoSave,
    private val templateManager: TemplateManager,
    private val videoEngine: VideoEngine,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

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

    private var toastDismissJob: Job? = null

    private val allProjects: StateFlow<List<Project>> = projectDao.getAllProjects()
        // Room re-emits on any table write even when the query result is identical; collapse
        // those duplicates so the filtered/sorted StateFlow below doesn't force the grid to
        // recompose on every unrelated project update (e.g. auto-save bumping updatedAt).
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    init {
        refreshUserTemplates()
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

    fun createProject(
        name: String = "Untitled",
        aspectRatio: AspectRatio = AspectRatio.RATIO_16_9,
        frameRate: Int = 30,
        resolution: Resolution = Resolution.FHD_1080P,
        templateId: String? = null,
        trackTypes: List<TrackType> = listOf(TrackType.VIDEO, TrackType.AUDIO),
        onCreated: (String) -> Unit = {}
    ) {
        val normalizedName = normalizeProjectName(name)
        val project = Project(
            name = normalizedName,
            aspectRatio = aspectRatio,
            frameRate = frameRate,
            resolution = resolution,
            templateId = templateId
        )
        val initialTracks = buildTracks(trackTypes)

        viewModelScope.launch {
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
        }
    }

    fun deleteProject(project: Project) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                projectDao.deleteProject(project)
                autoSave.clearRecoveryData(project.id)
                // Sweep the managed-media dir against the union of sourceUris
                // in every *remaining* project's auto-save JSON. Without this
                // the imported source clips accumulate forever — deleting a
                // project used to remove the row + recovery file but leak the
                // imports in `filesDir/media/imports/`. 24h min-age buffer
                // ensures an import from a freshly-created project (not yet
                // auto-saved) doesn't get swept out from under it.
                try {
                    val referenced = autoSave.collectReferencedSourceUris()
                        .map { android.net.Uri.parse(it) }
                        .toSet()
                    val result = com.novacut.editor.engine.sweepUnreferencedManagedMedia(
                        appContext, referenced
                    )
                    if (result.filesDeleted > 0) {
                        Log.d(
                            "ProjectListVM",
                            "Swept ${result.filesDeleted} orphan imports (${result.bytesFreed / 1024} KB)"
                        )
                    }
                } catch (e: Exception) {
                    Log.w("ProjectListVM", "Managed-media sweep failed", e)
                }
            }
        }
    }

    fun renameProject(project: Project, newName: String) {
        val normalizedName = normalizeProjectName(newName)
        viewModelScope.launch {
            projectDao.updateProject(project.copy(name = normalizedName, updatedAt = System.currentTimeMillis()))
        }
    }

    fun deleteUserTemplate(id: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                templateManager.deleteTemplate(id)
            }
            refreshUserTemplates()
        }
    }

    fun importTemplate(uri: Uri) {
        viewModelScope.launch {
            val template = withContext(Dispatchers.IO) {
                templateManager.importTemplateFromUri(uri)
            }
            refreshUserTemplates()

            if (template != null) {
                showToast("Imported template: ${template.name}")
            } else {
                showToast("Failed to import template")
            }
        }
    }

    fun shareTemplate(templateId: String) {
        viewModelScope.launch {
            try {
                val shareUri = withContext(Dispatchers.IO) {
                    val template = templateManager.getTemplate(templateId) ?: return@withContext null
                    val dir = File(appContext.getExternalFilesDir(null) ?: appContext.filesDir, "archives/templates").apply { mkdirs() }
                    val sanitized = sanitizeFileName(template.name, fallback = "template")
                    val outputFile = File(dir, "$sanitized.novacut-template")
                    val success = templateManager.exportTemplateToFile(template.id, outputFile)
                    if (!success) return@withContext null

                    FileProvider.getUriForFile(
                        appContext,
                        "${appContext.packageName}.fileprovider",
                        outputFile
                    )
                }

                if (shareUri == null) {
                    showToast("Template export failed")
                    return@launch
                }

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_STREAM, shareUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                appContext.startActivity(
                    Intent.createChooser(shareIntent, "Share Template")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (e: Exception) {
                showToast("Template export failed: ${e.message}")
            }
        }
    }

    fun createFromTemplate(template: UserTemplate, onCreated: (String) -> Unit) {
        val state = templateManager.loadTemplateState(template)
        if (state == null) {
            showToast("Template could not be opened")
            return
        }
        val (tracks, textOverlays) = state
        val project = Project(
            name = normalizeProjectName(template.name),
            aspectRatio = template.aspectRatio,
            frameRate = template.frameRate,
            resolution = template.resolution,
            templateId = template.id
        )
        viewModelScope.launch {
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
            } else {
                showToast(appContext.getString(R.string.project_create_failed))
            }
        }
    }

    fun duplicateProject(project: Project) {
        val newId = UUID.randomUUID().toString()
        val baseName = project.name.replace("""\s*\(Copy\s*\d*\)\s*$""".toRegex(), "").trim()
        viewModelScope.launch {
            val duplicated = withContext(Dispatchers.IO) {
                try {
                    // Compute the unique copy name inside the IO coroutine so the
                    // name-uniqueness check reads the freshest DAO snapshot instead
                    // of a potentially stale StateFlow value on the UI thread. This
                    // closes a race where two near-simultaneous duplicate taps could
                    // mint the same "(Copy)" name before either insertion settles.
                    val existingNames = projectDao.getAllProjectsSnapshot().map { it.name }.toSet()
                    var copyName = "$baseName (Copy)"
                    var counter = 2
                    while (copyName in existingNames) {
                        copyName = "$baseName (Copy $counter)"
                        counter++
                    }
                    val newProject = project.copy(
                        id = newId,
                        name = copyName,
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                    projectDao.insertProject(newProject)
                    if (autoSave.copyAutoSave(project.id, newId)) {
                        true
                    } else {
                        projectDao.deleteById(newId)
                        false
                    }
                } catch (e: Exception) {
                    Log.w("ProjectListVM", "Failed to duplicate project ${project.id}", e)
                    runCatching { projectDao.deleteById(newId) }
                    false
                }
            }
            if (!duplicated) {
                showToast(appContext.getString(R.string.project_duplicate_failed))
            }
        }
    }

    fun createProjectFromImport(videoUri: Uri, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val importedUri = withContext(Dispatchers.IO) {
                importUriToManagedMedia(appContext, videoUri, "video")
            }
            if (importedUri == null) {
                showToast(appContext.getString(R.string.project_import_copy_failed))
                return@launch
            }
            val copiedForImport = importedUri.toString() != videoUri.toString()
            val importCheck = withContext(Dispatchers.IO) {
                val readable = runCatching {
                    appContext.contentResolver.openAssetFileDescriptor(importedUri, "r")?.use { true } ?: false
                }.getOrDefault(importedUri.scheme == "file")
                readable to videoEngine.hasVisualTrack(importedUri)
            }
            if (!importCheck.first || !importCheck.second) {
                if (copiedForImport) {
                    deleteManagedMediaUri(appContext, importedUri)
                }
                showToast("Couldn't import that video")
                return@launch
            }
            val durationMs = withContext(Dispatchers.IO) {
                videoEngine.getVideoDuration(importedUri).takeIf { it > 0 } ?: 3_000L
            }
            val fileName = resolveMediaDisplayName(appContext, videoUri)
                ?.substringBeforeLast('.')
                ?.let(::normalizeProjectName)
                ?: "Imported"

            val project = Project(
                name = fileName,
                durationMs = durationMs,
                thumbnailUri = importedUri.toString()
            )

            val clip = Clip(
                sourceUri = importedUri,
                sourceDurationMs = durationMs,
                timelineStartMs = 0L,
                trimStartMs = 0L,
                trimEndMs = durationMs
            )
            val importedTracks = buildTracks(listOf(TrackType.VIDEO, TrackType.AUDIO)).map { track ->
                if (track.type == TrackType.VIDEO && track.index == 0) {
                    track.copy(clips = listOf(clip))
                } else {
                    track
                }
            }

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
                onCreated(project.id)
            } else {
                if (copiedForImport) {
                    deleteManagedMediaUri(appContext, importedUri)
                }
                showToast(appContext.getString(R.string.project_create_failed))
            }
        }
    }

    private fun refreshUserTemplates() {
        viewModelScope.launch(Dispatchers.IO) {
            _userTemplates.value = templateManager.listTemplates()
        }
    }

    private fun buildTracks(trackTypes: List<TrackType>): List<Track> {
        val normalizedTypes = trackTypes.ifEmpty { listOf(TrackType.VIDEO, TrackType.AUDIO) }
        return normalizedTypes.mapIndexed { index, type ->
            Track(type = type, index = index)
        }
    }

    private fun normalizeProjectName(raw: String): String {
        return raw.trim().ifBlank { "Untitled" }
    }

    private suspend fun createProjectWithInitialState(
        project: Project,
        initialState: AutoSaveState
    ): Boolean {
        return try {
            projectDao.insertProject(project)
            if (autoSave.saveNow(project.id, initialState)) {
                true
            } else {
                projectDao.deleteById(project.id)
                false
            }
        } catch (e: Exception) {
            Log.w("ProjectListVM", "Failed to create project ${project.id}", e)
            runCatching { projectDao.deleteById(project.id) }
            false
        }
    }

    private fun showToast(message: String) {
        toastDismissJob?.cancel()
        _toastMessage.value = message
        toastDismissJob = viewModelScope.launch {
            delay(2800L)
            if (_toastMessage.value == message) {
                _toastMessage.value = null
            }
        }
    }

    fun dismissToast() {
        toastDismissJob?.cancel()
        _toastMessage.value = null
    }
}

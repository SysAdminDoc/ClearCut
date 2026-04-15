package com.novacut.editor.ui.projects

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novacut.editor.engine.AutoSaveState
import com.novacut.editor.engine.ProjectAutoSave
import com.novacut.editor.engine.TemplateManager
import com.novacut.editor.engine.UserTemplate
import com.novacut.editor.engine.VideoEngine
import com.novacut.editor.engine.sanitizeFileName
import com.novacut.editor.engine.db.ProjectDao
import com.novacut.editor.model.AspectRatio
import com.novacut.editor.model.Clip
import com.novacut.editor.model.Project
import com.novacut.editor.model.Resolution
import com.novacut.editor.model.SortMode
import com.novacut.editor.model.Track
import com.novacut.editor.model.TrackType
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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

    private val _userTemplates = MutableStateFlow<List<UserTemplate>>(emptyList())
    val userTemplates: StateFlow<List<UserTemplate>> = _userTemplates.asStateFlow()

    private val allProjects: StateFlow<List<Project>> = projectDao.getAllProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val projects: StateFlow<List<Project>> = combine(
        allProjects, _searchQuery, _sortMode
    ) { projects, query, sort ->
        val filtered = if (query.isBlank()) projects
        else projects.filter { it.name.contains(query, ignoreCase = true) }

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
            withContext(Dispatchers.IO) {
                projectDao.insertProject(project)
                autoSave.saveNow(
                    project.id,
                    AutoSaveState(
                        projectId = project.id,
                        tracks = initialTracks,
                        textOverlays = emptyList()
                    )
                )
            }
            onCreated(project.id)
        }
    }

    fun deleteProject(project: Project) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                projectDao.deleteProject(project)
                autoSave.clearRecoveryData(project.id)
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
                    val dir = File(appContext.getExternalFilesDir(null), "archives/templates").apply { mkdirs() }
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
            withContext(Dispatchers.IO) {
                projectDao.insertProject(project)
                val autoState = AutoSaveState(
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
                autoSave.saveNow(project.id, autoState)
            }
            onCreated(project.id)
        }
    }

    fun duplicateProject(project: Project) {
        val newId = UUID.randomUUID().toString()
        val existingNames = allProjects.value.map { it.name }.toSet()
        val baseName = project.name.replace("""\s*\(Copy\s*\d*\)\s*$""".toRegex(), "").trim()
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
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                projectDao.insertProject(newProject)
                autoSave.copyAutoSave(project.id, newId)
            }
        }
    }

    fun createProjectFromImport(videoUri: Uri, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val importedUri = withContext(Dispatchers.IO) {
                copyToLocalMedia(videoUri, "video")
            }
            val durationMs = withContext(Dispatchers.IO) {
                videoEngine.getVideoDuration(importedUri).takeIf { it > 0 } ?: 3_000L
            }
            val fileName = resolveDisplayName(videoUri)
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

            withContext(Dispatchers.IO) {
                projectDao.insertProject(project)
                autoSave.saveNow(
                    project.id,
                    AutoSaveState(
                        projectId = project.id,
                        tracks = importedTracks,
                        textOverlays = emptyList()
                    )
                )
            }

            onCreated(project.id)
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

    private fun resolveDisplayName(uri: Uri): String? {
        if (uri.scheme == "file") {
            return uri.lastPathSegment
        }

        return runCatching {
            appContext.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                } else {
                    null
                }
            }
        }.getOrNull() ?: uri.lastPathSegment
    }

    private fun copyToLocalMedia(uri: Uri, mediaType: String): Uri {
        return try {
            if (uri.scheme == "file") {
                val path = uri.path.orEmpty()
                if (path.startsWith(appContext.filesDir.absolutePath) || path.startsWith(appContext.cacheDir.absolutePath)) {
                    return uri
                }
            }

            val mediaDir = File(appContext.filesDir, "media").apply { mkdirs() }
            val ext = resolveFileExtension(uri, mediaType)
            var destFile = File(
                mediaDir,
                "${System.currentTimeMillis()}_${uri.lastPathSegment?.hashCode()?.toUInt() ?: 0}$ext"
            )
            while (destFile.exists()) {
                destFile = File(mediaDir, "${System.currentTimeMillis()}_${(0..9999).random()}$ext")
            }

            val input = appContext.contentResolver.openInputStream(uri) ?: return uri
            input.use { src ->
                destFile.outputStream().use { dst -> src.copyTo(dst) }
            }

            if (destFile.length() == 0L) {
                destFile.delete()
                uri
            } else {
                Uri.fromFile(destFile)
            }
        } catch (e: Exception) {
            uri
        }
    }

    private fun resolveFileExtension(uri: Uri, mediaType: String): String {
        val mimeType = appContext.contentResolver.getType(uri)
        val mimeExt = mimeType?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
        if (!mimeExt.isNullOrBlank()) {
            return ".${mimeExt.lowercase()}"
        }

        val displayName = resolveDisplayName(uri)
        val nameExt = displayName?.substringAfterLast('.', "")
        if (!nameExt.isNullOrBlank()) {
            return ".${nameExt.lowercase()}"
        }

        return when (mediaType) {
            "image" -> ".jpg"
            "audio" -> ".m4a"
            else -> ".mp4"
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()
    }
}

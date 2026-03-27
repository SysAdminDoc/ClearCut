package com.novacut.editor.ui.editor

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.novacut.editor.ai.AiFeatures
import com.novacut.editor.engine.AudioEngine
import com.novacut.editor.engine.AutoSaveState
import com.novacut.editor.engine.ExportState
import com.novacut.editor.engine.ProjectAutoSave
import com.novacut.editor.engine.ProxyEngine
import com.novacut.editor.engine.SettingsRepository
import com.novacut.editor.engine.SmartRenderEngine
import com.novacut.editor.engine.SubtitleExporter
import com.novacut.editor.engine.BeatDetectionEngine
import com.novacut.editor.engine.LoudnessEngine
import com.novacut.editor.engine.NoiseReductionEngine
import com.novacut.editor.engine.FrameInterpolationEngine
import com.novacut.editor.engine.InpaintingEngine
import com.novacut.editor.engine.UpscaleEngine
import com.novacut.editor.engine.VideoMattingEngine
import com.novacut.editor.engine.StabilizationEngine
import com.novacut.editor.engine.StyleTransferEngine
import com.novacut.editor.engine.SmartReframeEngine
import com.novacut.editor.engine.TimelineExchangeEngine
import com.novacut.editor.engine.ProxyWorkflowEngine
import com.novacut.editor.engine.MultiCamEngine
import com.novacut.editor.engine.VideoEngine
import com.novacut.editor.engine.VoiceoverRecorderEngine
import com.novacut.editor.engine.TemplateManager
import com.novacut.editor.engine.db.ProjectDao
import com.novacut.editor.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.novacut.editor.engine.ProxyGenerationWorker
import java.io.File
import java.util.UUID
import javax.inject.Inject

enum class PanelId {
    MEDIA_PICKER, EXPORT_SHEET, EFFECTS, TEXT_EDITOR, TRANSITION_PICKER,
    AUDIO, AI_TOOLS, TRANSFORM, CROP, VOICEOVER_RECORDER,
    COLOR_GRADING, AUDIO_MIXER, KEYFRAME_EDITOR, SPEED_CURVE,
    MASK_EDITOR, BLEND_MODE, BATCH_EXPORT, PIP_PRESETS, CHROMA_KEY,
    SCOPES, CAPTION_EDITOR, CHAPTER_MARKERS, SNAPSHOT_HISTORY,
    TEXT_TEMPLATES, MEDIA_MANAGER, AUDIO_NORM, RENDER_PREVIEW,
    CLOUD_BACKUP, TUTORIAL, UNDO_HISTORY, CAPTION_STYLE_GALLERY,
    BEAT_SYNC, SMART_REFRAME, SPEED_PRESETS, FILLER_REMOVAL,
    AUTO_EDIT, TTS, EFFECT_LIBRARY, NOISE_REDUCTION, STICKER_PICKER
}

data class PanelVisibility(
    val openPanels: Set<PanelId> = emptySet()
) {
    val hasOpenPanel: Boolean get() = openPanels.isNotEmpty()
    fun isOpen(panel: PanelId): Boolean = panel in openPanels
    fun open(panel: PanelId): PanelVisibility = copy(openPanels = setOf(panel))
    fun close(panel: PanelId): PanelVisibility = copy(openPanels = openPanels - panel)
    fun closeAll(): PanelVisibility = copy(openPanels = emptySet())
}

data class EditorState(
    val project: Project = Project(),
    val tracks: List<Track> = listOf(
        Track(type = TrackType.VIDEO, index = 0),
        Track(type = TrackType.AUDIO, index = 1)
    ),
    val selectedClipId: String? = null,
    val selectedTrackId: String? = null,
    val playheadMs: Long = 0L,
    val isPlaying: Boolean = false,
    val zoomLevel: Float = 1f,
    val scrollOffsetMs: Long = 0L,
    val totalDurationMs: Long = 0L,
    val currentTool: EditorTool = EditorTool.NONE,
    val panels: PanelVisibility = PanelVisibility(),
    val exportConfig: ExportConfig = ExportConfig(),
    val exportProgress: Float = 0f,
    val exportState: ExportState = ExportState.IDLE,
    val textOverlays: List<TextOverlay> = emptyList(),
    val imageOverlays: List<ImageOverlay> = emptyList(),
    val timelineMarkers: List<TimelineMarker> = emptyList(),
    val waveforms: Map<String, List<Float>> = emptyMap(),
    val selectedEffectId: String? = null,
    val undoStack: List<UndoAction> = emptyList(),
    val redoStack: List<UndoAction> = emptyList(),
    val toastMessage: String? = null,
    val aiProcessingTool: String? = null,
    val lastExportedFilePath: String? = null,
    val copiedEffects: List<Effect> = emptyList(),
    val exportErrorMessage: String? = null,
    val isRecordingVoiceover: Boolean = false,
    val voiceoverDurationMs: Long = 0L,
    val isLooping: Boolean = false,
    val editingTextOverlayId: String? = null,
    val activeScopeType: com.novacut.editor.ui.editor.ScopeType = com.novacut.editor.ui.editor.ScopeType.HISTOGRAM,
    val exportStartTime: Long = 0L,
    val renderSegments: List<com.novacut.editor.engine.SmartRenderEngine.RenderSegment> = emptyList(),
    val renderSummary: com.novacut.editor.engine.SmartRenderEngine.SmartRenderSummary? = null,
    // Chapter markers
    val chapterMarkers: List<ChapterMarker> = emptyList(),
    // Multi-select
    val selectedClipIds: Set<String> = emptySet(),
    // Mask state
    val selectedMaskId: String? = null,
    // Keyframe state
    val activeKeyframeProperties: Set<KeyframeProperty> = setOf(
        KeyframeProperty.POSITION_X, KeyframeProperty.POSITION_Y,
        KeyframeProperty.SCALE_X, KeyframeProperty.SCALE_Y,
        KeyframeProperty.OPACITY
    ),
    // Audio mixer state
    val vuLevels: Map<String, Pair<Float, Float>> = emptyMap(),
    // Beat markers
    val beatMarkers: List<Long> = emptyList(),
    // Batch export
    val batchExportQueue: List<BatchExportItem> = emptyList(),
    // Project snapshots
    val projectSnapshots: List<ProjectSnapshot> = emptyList(),
    // Proxy
    val proxySettings: ProxySettings = ProxySettings(),
    // Auto-save indicator
    val saveIndicator: com.novacut.editor.model.SaveIndicatorState = com.novacut.editor.model.SaveIndicatorState.HIDDEN,
    // Undo history
    val undoHistoryEntries: List<com.novacut.editor.model.UndoHistoryEntry> = emptyList(),
    // Beat sync
    val isAnalyzingBeats: Boolean = false,
    // Smart reframe
    val isReframing: Boolean = false,
    // Filler removal
    val isAnalyzingFillers: Boolean = false,
    val fillerRegions: List<com.novacut.editor.ai.RemovalRegion> = emptyList(),
    // Auto-edit
    val isAutoEditing: Boolean = false,
    // Editor mode
    val editorMode: EditorMode = EditorMode.PRO,
    // Timeline collapsed
    val isTimelineCollapsed: Boolean = false,
    // TTS
    val isSynthesizingTts: Boolean = false,
    val isTtsAvailable: Boolean = false,
    // Noise reduction
    val isAnalyzingNoise: Boolean = false,
    val noiseAnalysisResult: String? = null,
    // Saved export config (for restoring after quick preview)
    val savedExportConfig: ExportConfig? = null
)

enum class EditorMode(val label: String) {
    EASY("Easy"), PRO("Pro")
}

enum class EditorTool(val displayName: String) {
    NONE(""),
    TRIM("Trim"),
    SPLIT("Split"),
    SPEED("Speed"),
    EFFECTS("Effects"),
    TEXT("Text"),
    AUDIO("Audio"),
    TRANSITION("Transition"),
    TRANSFORM("Transform"),
    CROP("Crop"),
    AI("AI"),
    FREEZE_FRAME("Freeze"),
    EXPORT("Export")
}

data class UndoAction(
    val description: String,
    val tracks: List<Track>,
    val textOverlays: List<TextOverlay>,
    val imageOverlays: List<ImageOverlay> = emptyList(),
    val timelineMarkers: List<TimelineMarker> = emptyList(),
    val chapterMarkers: List<ChapterMarker> = emptyList()
)

@HiltViewModel
class EditorViewModel @Inject constructor(
    private val videoEngine: VideoEngine,
    private val projectDao: ProjectDao,
    private val audioEngine: AudioEngine,
    private val autoSave: ProjectAutoSave,
    private val aiFeatures: AiFeatures,
    private val voiceoverEngine: VoiceoverRecorderEngine,
    private val templateManager: TemplateManager,
    private val proxyEngine: ProxyEngine,
    private val settingsRepo: SettingsRepository,
    private val ttsEngine: com.novacut.editor.engine.TtsEngine,
    private val effectShareEngine: com.novacut.editor.engine.EffectShareEngine,
    private val noiseReductionEngine: NoiseReductionEngine,
    private val beatDetectionEngine: BeatDetectionEngine,
    private val loudnessEngine: LoudnessEngine,
    private val frameInterpolationEngine: FrameInterpolationEngine,
    private val inpaintingEngine: InpaintingEngine,
    private val upscaleEngine: UpscaleEngine,
    private val videoMattingEngine: VideoMattingEngine,
    private val stabilizationEngine: StabilizationEngine,
    private val styleTransferEngine: StyleTransferEngine,
    private val smartReframeEngine: SmartReframeEngine,
    private val timelineExchangeEngine: TimelineExchangeEngine,
    private val proxyWorkflowEngine: ProxyWorkflowEngine,
    private val multiCamEngine: MultiCamEngine,
    @ApplicationContext private val appContext: Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val projectId: String? = savedStateHandle["projectId"]

    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()

    // Fast-path playhead flow — avoids full EditorState copy during playback
    private val _playheadMs = MutableStateFlow(0L)
    val playheadMs: StateFlow<Long> = _playheadMs.asStateFlow()

    val engine get() = videoEngine

    // --- Delegates (extracted to reduce ViewModel size) ---

    val colorGradingDelegate = ColorGradingDelegate(
        stateFlow = _state, appContext = appContext,
        scope = viewModelScope, saveUndoState = ::saveUndoState, showToast = ::showToast,
        pauseIfPlaying = ::pauseIfPlaying, dismissedPanelState = ::dismissedPanelState,
        getSelectedClip = ::getSelectedClip, updatePreview = ::updatePreview
    )

    val audioMixerDelegate = AudioMixerDelegate(
        stateFlow = _state, beatDetectionEngine = beatDetectionEngine,
        loudnessEngine = loudnessEngine, scope = viewModelScope,
        saveUndoState = ::saveUndoState, showToast = ::showToast,
        pauseIfPlaying = ::pauseIfPlaying, dismissedPanelState = ::dismissedPanelState,
        saveProject = ::saveProject
    )

    val exportDelegate = ExportDelegate(
        stateFlow = _state, videoEngine = videoEngine, appContext = appContext,
        scope = viewModelScope, showToast = ::showToast,
        pauseIfPlaying = ::pauseIfPlaying, dismissedPanelState = ::dismissedPanelState,
        showExportSheet = ::showExportSheet
    )

    val aiToolsDelegate = AiToolsDelegate(
        stateFlow = _state, aiFeatures = aiFeatures, templateManager = templateManager,
        frameInterpolationEngine = frameInterpolationEngine, inpaintingEngine = inpaintingEngine,
        upscaleEngine = upscaleEngine, videoMattingEngine = videoMattingEngine,
        stabilizationEngine = stabilizationEngine, styleTransferEngine = styleTransferEngine,
        appContext = appContext, scope = viewModelScope,
        saveUndoState = ::saveUndoState, showToast = ::showToast,
        getSelectedClip = ::getSelectedClip, setClipTransform = { id, px, py, sx, sy, rot ->
            setClipTransform(id, positionX = px, positionY = py, scaleX = sx, scaleY = sy, rotation = rot)
        },
        rebuildPlayerTimeline = ::rebuildPlayerTimeline, saveProject = ::saveProject,
        videoEngine = videoEngine,
        recalculateDuration = ::recalculateDuration
    )

    val clipEditingDelegate = ClipEditingDelegate(
        stateFlow = _state, videoEngine = videoEngine, audioEngine = audioEngine,
        scope = viewModelScope, saveUndoState = ::saveUndoState, showToast = ::showToast,
        rebuildPlayerTimeline = ::rebuildPlayerTimeline, saveProject = ::saveProject,
        updatePreview = ::updatePreview, recalculateDuration = ::recalculateDuration,
        onClipAdded = { clipId, uri ->
            viewModelScope.launch(Dispatchers.IO) {
                val (w, h) = videoEngine.getVideoResolution(uri)
                if (w > 0 && h > 0) {
                    proxyWorkflowEngine.registerMedia(clipId, uri, w, h)
                    if (h > 1080) enqueueProxyGeneration()
                }
            }
        }
    )

    val effectsDelegate = EffectsDelegate(
        stateFlow = _state, saveUndoState = ::saveUndoState, showToast = ::showToast,
        updatePreview = ::updatePreview, saveProject = ::saveProject,
        getSelectedClip = ::getSelectedClip,
        recalculateDuration = ::recalculateDuration
    )

    val overlayDelegate = OverlayDelegate(
        stateFlow = _state, saveUndoState = ::saveUndoState, showToast = ::showToast
    )

    // Whisper model state (exposed via delegate for UI binding)
    val whisperModelState get() = aiToolsDelegate.whisperModelState
    val whisperDownloadProgress get() = aiToolsDelegate.whisperDownloadProgress
    val segmentationModelState get() = aiToolsDelegate.segmentationModelState
    val segmentationDownloadProgress get() = aiToolsDelegate.segmentationDownloadProgress

    // LUT picker state (exposed via delegate)
    val showLutPicker get() = colorGradingDelegate.showLutPicker

    // Stored outside EditorState to avoid recomposition on every resize
    @Volatile
    private var timelineWidthPx: Float = 0f

    fun setTimelineWidth(widthPx: Float) {
        timelineWidthPx = widthPx
    }

    init {
        val autoSaveId = projectId ?: _state.value.project.id

        // Load existing project if projectId provided, then restore auto-save
        viewModelScope.launch {
            if (projectId != null) {
                val project = projectDao.getProject(projectId)
                if (project != null) {
                    _state.update { it.copy(project = project) }
                } else {
                    val newProject = _state.value.project.copy(id = projectId)
                    _state.update { it.copy(project = newProject) }
                    projectDao.insertProject(newProject)
                }
            }

            // Restore auto-save AFTER Room load to avoid race condition
            val recovery = autoSave.loadRecoveryData(autoSaveId)
            if (recovery != null) {
                _state.update {
                    it.copy(
                        tracks = recovery.tracks.ifEmpty { it.tracks },
                        textOverlays = recovery.textOverlays,
                        playheadMs = recovery.playheadMs,
                        chapterMarkers = recovery.chapterMarkers,
                        totalDurationMs = recovery.tracks.maxOfOrNull { t ->
                            t.clips.maxOfOrNull { c -> c.timelineEndMs } ?: 0L
                        } ?: 0L
                    )
                }
                if (recovery.tracks.flatMap { it.clips }.isNotEmpty()) {
                    rebuildPlayerTimeline()
                }
                // Extract waveforms for all recovered clips
                for (track in recovery.tracks) {
                    for (clip in track.clips) {
                        viewModelScope.launch {
                            val waveform = audioEngine.extractWaveform(clip.sourceUri).toList()
                            _state.update { it.copy(waveforms = it.waveforms + (clip.id to waveform)) }
                        }
                    }
                }
            }
        }

        viewModelScope.launch {
            videoEngine.exportProgress.collect { progress ->
                _state.update { it.copy(exportProgress = progress) }
            }
        }
        viewModelScope.launch {
            videoEngine.exportState.collect { exportState ->
                _state.update { it.copy(exportState = exportState) }
                if (exportState == ExportState.CANCELLED) {
                    showToast("Export cancelled")
                }
            }
        }

        // Player.Listener for play state sync — tracked for cleanup
        videoEngine.setPlayerListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                _state.update { it.copy(isPlaying = playing) }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    val totalMs = _state.value.totalDurationMs
                    _playheadMs.value = totalMs
                    _state.update { it.copy(isPlaying = false, playheadMs = totalMs) }
                }
            }
        })

        // Periodic playhead sync (~30fps) with auto-scroll + per-clip speed tracking
        viewModelScope.launch {
            var lastClipIndex = -1
            var frameCount = 0
            while (isActive) {
                delay(33)
                val player = videoEngine.getPlayer() ?: continue
                if (player.isPlaying) {
                    val currentMs = videoEngine.getAbsolutePositionMs()
                    // Fast-path: update dedicated playhead flow every frame
                    _playheadMs.value = currentMs
                    frameCount++
                    // Only sync full state every 5th frame to reduce copies
                    if (frameCount % 5 == 0) {
                        _state.update { s ->
                            var newScroll = s.scrollOffsetMs
                            // Auto-scroll when playhead approaches right edge (>80% of visible area)
                            val widthPx = timelineWidthPx
                            val pixelsPerMs = s.zoomLevel * 0.15f
                            if (widthPx > 0 && pixelsPerMs >= 0.001f) {
                                val visibleMs = (widthPx / pixelsPerMs).toLong()
                                val playheadRelative = currentMs - newScroll
                                if (playheadRelative > visibleMs * 0.8f) {
                                    newScroll = (currentMs - visibleMs / 4).coerceAtLeast(0L)
                                } else if (playheadRelative < 0) {
                                    newScroll = (currentMs - visibleMs / 4).coerceAtLeast(0L)
                                }
                            }
                            s.copy(playheadMs = currentMs, scrollOffsetMs = newScroll)
                        }
                    }
                    // Track clip transitions during playback — update speed/effects for current clip
                    val currentIndex = videoEngine.getCurrentClipIndex()
                    if (currentIndex != lastClipIndex) {
                        lastClipIndex = currentIndex
                        val videoClips = _state.value.tracks
                            .filter { it.type == TrackType.VIDEO }
                            .flatMap { it.clips }
                        val currentClip = videoClips.getOrNull(currentIndex)
                        if (currentClip != null) {
                            videoEngine.setPreviewSpeed(currentClip.speed)
                            videoEngine.applyPreviewEffects(currentClip)
                        }
                    }
                }
            }
        }

        // First-run tutorial: show on first launch
        viewModelScope.launch {
            if (!settingsRepo.isTutorialShown()) {
                delay(500)
                showTutorial()
            }
        }

        // Apply user settings (export defaults + auto-save)
        var appliedDefaults = false
        var lastAutoSaveEnabled: Boolean? = null
        var lastAutoSaveInterval: Int? = null
        viewModelScope.launch {
            settingsRepo.settings.collect { settings ->
                // Apply default export config from settings once on first load
                if (!appliedDefaults) {
                    appliedDefaults = true
                    _state.update { s ->
                        s.copy(exportConfig = s.exportConfig.copy(
                            resolution = settings.defaultResolution,
                            frameRate = settings.defaultFrameRate
                        ))
                    }
                }

                // Only restart auto-save when auto-save settings actually change
                val enabledChanged = settings.autoSaveEnabled != lastAutoSaveEnabled
                val intervalChanged = settings.autoSaveIntervalSec != lastAutoSaveInterval
                if (enabledChanged || intervalChanged) {
                    lastAutoSaveEnabled = settings.autoSaveEnabled
                    lastAutoSaveInterval = settings.autoSaveIntervalSec
                    if (settings.autoSaveEnabled) {
                        autoSave.startAutoSave(
                            projectId ?: _state.value.project.id,
                            intervalMs = settings.autoSaveIntervalSec * 1000L
                        ) {
                            showSaveIndicator(com.novacut.editor.model.SaveIndicatorState.SAVING)
                            val s = _state.value
                            val state = AutoSaveState(
                                projectId = s.project.id,
                                tracks = s.tracks,
                                textOverlays = s.textOverlays,
                                playheadMs = s.playheadMs,
                                chapterMarkers = s.chapterMarkers
                            )
                            viewModelScope.launch {
                                delay(500)
                                showSaveIndicator(com.novacut.editor.model.SaveIndicatorState.SAVED)
                            }
                            state
                        }
                    } else {
                        autoSave.stop()
                    }
                }
            }
        }
    }

    /** Rebuild ExoPlayer timeline from current tracks. Call after any clip mutation. */
    private fun rebuildPlayerTimeline() {
        videoEngine.prepareTimeline(_state.value.tracks)
        updatePreview()
        _state.update { recalculateDuration(it) }
    }

    /** Apply the selected clip's effects and speed to ExoPlayer for live preview. */
    private fun updatePreview() {
        val clip = getSelectedClip()
        videoEngine.applyPreviewEffects(clip)
        val speed = clip?.speed ?: 1f
        videoEngine.setPreviewSpeed(speed)
    }

    /**
     * Enqueue background proxy generation via WorkManager.
     * Called after importing high-res clips when proxy editing is enabled.
     */
    fun enqueueProxyGeneration() {
        val request = OneTimeWorkRequestBuilder<ProxyGenerationWorker>()
            .addTag(ProxyGenerationWorker.TAG)
            .build()
        WorkManager.getInstance(appContext)
            .enqueueUniqueWork(
                ProxyGenerationWorker.WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
    }

    // --- Clip Editing (delegated) ---
    fun addClipToTrack(uri: Uri, trackType: TrackType = TrackType.VIDEO) = clipEditingDelegate.addClipToTrack(uri, trackType)
    fun selectClip(clipId: String?, trackId: String? = null) = clipEditingDelegate.selectClip(clipId, trackId)
    fun deleteSelectedClip() = clipEditingDelegate.deleteSelectedClip()
    fun duplicateSelectedClip() = clipEditingDelegate.duplicateSelectedClip()
    fun mergeWithNextClip() = clipEditingDelegate.mergeWithNextClip()
    fun splitClipAtPlayhead() = clipEditingDelegate.splitClipAtPlayhead()
    fun beginTrim() = clipEditingDelegate.beginTrim()
    fun trimClip(clipId: String, newTrimStartMs: Long? = null, newTrimEndMs: Long? = null) = clipEditingDelegate.trimClip(clipId, newTrimStartMs, newTrimEndMs)
    fun beginSpeedChange() = clipEditingDelegate.beginSpeedChange()
    fun setClipSpeed(clipId: String, speed: Float) = clipEditingDelegate.setClipSpeed(clipId, speed)
    fun setClipReversed(clipId: String, reversed: Boolean) = clipEditingDelegate.setClipReversed(clipId, reversed)
    fun reorderClip(clipId: String, targetIndex: Int) = clipEditingDelegate.reorderClip(clipId, targetIndex)
    fun moveClipToTrack(clipId: String, targetTrackId: String) = clipEditingDelegate.moveClipToTrack(clipId, targetTrackId)

    // --- Effects & Transitions (delegated) ---
    fun addEffect(clipId: String, effect: Effect) = effectsDelegate.addEffect(clipId, effect)
    fun beginEffectAdjust() = effectsDelegate.beginEffectAdjust()
    fun updateEffect(clipId: String, effectId: String, params: Map<String, Float>) = effectsDelegate.updateEffect(clipId, effectId, params)
    fun toggleEffectEnabled(clipId: String, effectId: String) = effectsDelegate.toggleEffectEnabled(clipId, effectId)
    fun removeEffect(clipId: String, effectId: String) = effectsDelegate.removeEffect(clipId, effectId)
    fun copyEffects() = effectsDelegate.copyEffects()
    fun pasteEffects() = effectsDelegate.pasteEffects()
    fun setTransition(clipId: String, transition: Transition?) = effectsDelegate.setTransition(clipId, transition)
    fun beginTransitionDurationChange() = effectsDelegate.beginTransitionDurationChange()
    fun setTransitionDuration(clipId: String, durationMs: Long) = effectsDelegate.setTransitionDuration(clipId, durationMs)

    // --- Overlays & Markers (delegated) ---
    fun addTextOverlay(text: TextOverlay) = overlayDelegate.addTextOverlay(text)
    fun updateTextOverlay(textOverlay: TextOverlay) = overlayDelegate.updateTextOverlay(textOverlay)
    fun removeTextOverlay(id: String) = overlayDelegate.removeTextOverlay(id)
    fun addImageOverlay(uri: Uri, type: ImageOverlayType = ImageOverlayType.STICKER) = overlayDelegate.addImageOverlay(uri, type)
    fun updateImageOverlay(id: String, positionX: Float? = null, positionY: Float? = null, scale: Float? = null, rotation: Float? = null, opacity: Float? = null) = overlayDelegate.updateImageOverlay(id, positionX, positionY, scale, rotation, opacity)
    fun removeImageOverlay(id: String) = overlayDelegate.removeImageOverlay(id)
    fun addTimelineMarker(label: String = "", color: MarkerColor = MarkerColor.BLUE) = overlayDelegate.addTimelineMarker(label, color)
    fun deleteTimelineMarker(id: String) = overlayDelegate.deleteTimelineMarker(id)

    fun jumpToNextMarker() {
        val current = _playheadMs.value
        val next = _state.value.timelineMarkers.firstOrNull { it.timeMs > current + 50 }
        if (next != null) seekTo(next.timeMs) else showToast("No next marker")
    }

    fun jumpToPrevMarker() {
        val current = _playheadMs.value
        val prev = _state.value.timelineMarkers.lastOrNull { it.timeMs < current - 50 }
        if (prev != null) seekTo(prev.timeMs) else showToast("No previous marker")
    }

    fun addTrack(type: TrackType) {
        saveUndoState("Add track")
        _state.update { state ->
            val nextIndex = state.tracks.size
            state.copy(tracks = state.tracks + Track(type = type, index = nextIndex))
        }
    }

    fun toggleTrackMute(trackId: String) {
        _state.update { state ->
            val tracks = state.tracks.map { track ->
                if (track.id == trackId) track.copy(isMuted = !track.isMuted) else track
            }
            state.copy(tracks = tracks)
        }
    }

    fun toggleTrackVisibility(trackId: String) {
        _state.update { state ->
            val tracks = state.tracks.map { track ->
                if (track.id == trackId) track.copy(isVisible = !track.isVisible) else track
            }
            state.copy(tracks = tracks)
        }
    }

    fun toggleTrackLock(trackId: String) {
        _state.update { state ->
            val tracks = state.tracks.map { track ->
                if (track.id == trackId) track.copy(isLocked = !track.isLocked) else track
            }
            state.copy(tracks = tracks)
        }
    }

    // Playback
    fun togglePlayback() {
        if (videoEngine.isPlaying()) {
            videoEngine.pause()
            _state.update { it.copy(isPlaying = false) }
        } else {
            videoEngine.play()
            _state.update { it.copy(isPlaying = true) }
        }
    }

    fun toggleLoop() {
        val newLooping = !_state.value.isLooping
        videoEngine.getPlayer()?.repeatMode = if (newLooping)
            Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
        _state.update { it.copy(isLooping = newLooping) }
    }

    fun seekTo(positionMs: Long) {
        videoEngine.seekTo(positionMs)
        _playheadMs.value = positionMs
        _state.update { it.copy(playheadMs = positionMs) }
        if (_state.value.panels.isOpen(PanelId.SCOPES)) updateScopeFrame()
    }

    /** Enable scrubbing mode during timeline drag for smoother seeking. */
    fun beginScrub() { videoEngine.setScrubbingMode(true) }
    fun endScrub() { videoEngine.setScrubbingMode(false) }

    fun updatePlayheadPosition(positionMs: Long) {
        _playheadMs.value = positionMs
        _state.update { it.copy(playheadMs = positionMs) }
    }

    // Zoom
    fun setZoomLevel(zoom: Float) {
        _state.update { it.copy(zoomLevel = zoom.coerceIn(0.1f, 10f)) }
    }

    fun setScrollOffset(offsetMs: Long) {
        _state.update { it.copy(scrollOffsetMs = offsetMs.coerceAtLeast(0L)) }
    }

    // Tool selection
    fun setTool(tool: EditorTool) {
        // Disable scrubbing mode when leaving trim tool
        if (_state.value.currentTool == EditorTool.TRIM && tool != EditorTool.TRIM) {
            videoEngine.setScrubbingMode(false)
        }
        _state.update { it.copy(currentTool = tool) }
    }

    // Panel mutual exclusion — atomic dismiss-and-show in single state update
    private fun pauseIfPlaying() {
        if (videoEngine.isPlaying()) {
            videoEngine.pause()
            _state.update { it.copy(isPlaying = false) }
        }
    }

    private fun dismissedPanelState(state: EditorState) = state.copy(
        panels = state.panels.closeAll(),
        noiseAnalysisResult = null,
        selectedEffectId = null,
        editingTextOverlayId = null,
        selectedMaskId = null
    )

    fun dismissAllPanels() { _state.update { dismissedPanelState(it) } }

    // --- Clip update helpers ---
    private inline fun updateClipById(clipId: String, crossinline transform: (Clip) -> Clip) {
        _state.update { s ->
            s.copy(tracks = s.tracks.map { track ->
                track.copy(clips = track.clips.map { clip ->
                    if (clip.id == clipId) transform(clip) else clip
                })
            })
        }
    }

    private inline fun updateSelectedClip(crossinline transform: (Clip) -> Clip): Boolean {
        val clipId = _state.value.selectedClipId ?: return false
        updateClipById(clipId, transform)
        return true
    }

    // Generic panel show/hide — standard panels use these directly
    fun showPanel(panel: PanelId) {
        pauseIfPlaying()
        _state.update { dismissedPanelState(it).copy(panels = it.panels.closeAll().open(panel)) }
    }
    fun hidePanel(panel: PanelId) {
        _state.update { it.copy(panels = it.panels.close(panel)) }
    }

    // Standard panel toggles
    fun showMediaPicker() = showPanel(PanelId.MEDIA_PICKER)
    fun hideMediaPicker() = hidePanel(PanelId.MEDIA_PICKER)
    fun showEffectsPanel() = showPanel(PanelId.EFFECTS)
    fun hideEffectsPanel() = hidePanel(PanelId.EFFECTS)
    fun showTransitionPicker() = showPanel(PanelId.TRANSITION_PICKER)
    fun hideTransitionPicker() = hidePanel(PanelId.TRANSITION_PICKER)
    fun showAudioPanel() = showPanel(PanelId.AUDIO)
    fun hideAudioPanel() = hidePanel(PanelId.AUDIO)
    fun showAiToolsPanel() = showPanel(PanelId.AI_TOOLS)
    fun hideAiToolsPanel() = hidePanel(PanelId.AI_TOOLS)
    fun showTransformPanel() = showPanel(PanelId.TRANSFORM)
    fun hideTransformPanel() = hidePanel(PanelId.TRANSFORM)
    fun showCropPanel() = showPanel(PanelId.CROP)
    fun hideCropPanel() = hidePanel(PanelId.CROP)
    fun showVoiceoverPanel() = showPanel(PanelId.VOICEOVER_RECORDER)

    // Non-standard panel methods (side effects beyond show/hide)
    fun showExportSheet() {
        pauseIfPlaying()
        videoEngine.resetExportState()
        _state.update { dismissedPanelState(it).copy(panels = it.panels.closeAll().open(PanelId.EXPORT_SHEET), exportState = ExportState.IDLE, exportProgress = 0f, exportErrorMessage = null) }
    }
    fun hideExportSheet() {
        _state.update { s ->
            val restored = s.savedExportConfig
            s.copy(
                panels = s.panels.close(PanelId.EXPORT_SHEET),
                exportConfig = restored ?: s.exportConfig,
                savedExportConfig = null
            )
        }
    }
    fun showTextEditor() { pauseIfPlaying(); _state.update { dismissedPanelState(it).copy(panels = it.panels.closeAll().open(PanelId.TEXT_EDITOR), editingTextOverlayId = null) } }
    fun editTextOverlay(id: String) { pauseIfPlaying(); _state.update { dismissedPanelState(it).copy(panels = it.panels.closeAll().open(PanelId.TEXT_EDITOR), editingTextOverlayId = id) } }
    fun hideTextEditor() { _state.update { it.copy(panels = it.panels.close(PanelId.TEXT_EDITOR), editingTextOverlayId = null) } }
    fun hideVoiceoverPanel() {
        if (_state.value.isRecordingVoiceover) stopVoiceover()
        voiceoverDurationJob?.cancel()
        hidePanel(PanelId.VOICEOVER_RECORDER)
    }
    fun selectEffect(effectId: String?) { _state.update { it.copy(selectedEffectId = effectId) } }
    fun clearSelectedEffect() { _state.update { it.copy(selectedEffectId = null) } }

    // --- Color Grading (delegated) ---
    fun showColorGrading() = colorGradingDelegate.showColorGrading()
    fun hideColorGrading() = colorGradingDelegate.hideColorGrading()
    fun beginColorGradeAdjust() = colorGradingDelegate.beginColorGradeAdjust()
    fun updateClipColorGrade(colorGrade: ColorGrade) = colorGradingDelegate.updateClipColorGrade(colorGrade)
    // showLutPicker exposed via getter above (line 333)
    fun importLut() = colorGradingDelegate.importLut()
    fun onLutPickerDismissed() = colorGradingDelegate.onLutPickerDismissed()
    fun onLutFileSelected(uri: Uri) = colorGradingDelegate.onLutFileSelected(uri)
    fun setClipLut(lutPath: String) = colorGradingDelegate.setClipLut(lutPath)

    // --- Audio Mixer (delegated) ---
    fun showAudioMixer() = audioMixerDelegate.showAudioMixer()
    fun hideAudioMixer() = audioMixerDelegate.hideAudioMixer()
    fun setTrackVolume(trackId: String, volume: Float) = audioMixerDelegate.setTrackVolume(trackId, volume)
    fun setTrackPan(trackId: String, pan: Float) = audioMixerDelegate.setTrackPan(trackId, pan)
    fun toggleTrackSolo(trackId: String) = audioMixerDelegate.toggleTrackSolo(trackId)
    fun addTrackAudioEffect(trackId: String, type: AudioEffectType) = audioMixerDelegate.addTrackAudioEffect(trackId, type)
    fun removeTrackAudioEffect(trackId: String, effectId: String) = audioMixerDelegate.removeTrackAudioEffect(trackId, effectId)
    fun updateTrackAudioEffectParam(trackId: String, effectId: String, param: String, value: Float) = audioMixerDelegate.updateTrackAudioEffectParam(trackId, effectId, param, value)
    fun detectBeats() = audioMixerDelegate.detectBeats()

    // --- Keyframe Editor ---
    fun showKeyframeEditor() = showPanel(PanelId.KEYFRAME_EDITOR)
    fun hideKeyframeEditor() = hidePanel(PanelId.KEYFRAME_EDITOR)

    fun toggleKeyframeProperty(property: KeyframeProperty) {
        _state.update { s ->
            val current = s.activeKeyframeProperties
            val updated = if (property in current) current - property else current + property
            s.copy(activeKeyframeProperties = updated)
        }
    }

    fun updateClipKeyframes(keyframes: List<Keyframe>) {
        updateSelectedClip { it.copy(keyframes = keyframes) }
    }

    fun addKeyframe(property: KeyframeProperty, timeOffsetMs: Long, value: Float) {
        if (_state.value.selectedClipId == null) return
        saveUndoState("Add keyframe")
        val kf = Keyframe(timeOffsetMs, property, value, interpolation = KeyframeInterpolation.BEZIER)
        updateSelectedClip { it.copy(keyframes = it.keyframes + kf) }
    }

    fun deleteKeyframe(keyframe: Keyframe) {
        if (_state.value.selectedClipId == null) return
        saveUndoState("Delete keyframe")
        updateSelectedClip { clip ->
            clip.copy(keyframes = clip.keyframes.filter {
                !(it.timeOffsetMs == keyframe.timeOffsetMs && it.property == keyframe.property && it.value == keyframe.value)
            })
        }
    }

    // --- Speed Curve ---
    fun showSpeedCurveEditor() = showPanel(PanelId.SPEED_CURVE)
    fun hideSpeedCurveEditor() = hidePanel(PanelId.SPEED_CURVE)

    fun setClipSpeedCurve(speedCurve: SpeedCurve?) {
        if (_state.value.selectedClipId == null) return
        saveUndoState("Speed curve")
        updateSelectedClip { it.copy(speedCurve = speedCurve) }
        rebuildPlayerTimeline()
    }

    // --- Mask Editor ---
    fun showMaskEditor() = showPanel(PanelId.MASK_EDITOR)
    fun hideMaskEditor() { hidePanel(PanelId.MASK_EDITOR); _state.update { it.copy(selectedMaskId = null) } }

    fun selectMask(maskId: String?) {
        _state.update { it.copy(selectedMaskId = maskId) }
    }

    fun addMask(type: MaskType) {
        if (_state.value.selectedClipId == null) return
        saveUndoState("Add mask")
        val defaultPoints = when (type) {
            MaskType.RECTANGLE -> listOf(MaskPoint(0.25f, 0.25f), MaskPoint(0.75f, 0.75f))
            MaskType.ELLIPSE -> listOf(MaskPoint(0.5f, 0.5f), MaskPoint(0.25f, 0.25f))
            MaskType.LINEAR_GRADIENT -> listOf(MaskPoint(0.5f, 0.3f), MaskPoint(0.5f, 0.7f))
            MaskType.RADIAL_GRADIENT -> listOf(MaskPoint(0.5f, 0.5f), MaskPoint(0.3f, 0.3f))
            MaskType.FREEHAND -> emptyList()
        }
        val mask = Mask(type = type, points = defaultPoints)
        updateSelectedClip { it.copy(masks = it.masks + mask) }
        _state.update { it.copy(selectedMaskId = mask.id) }
    }

    fun updateMask(mask: Mask) {
        updateSelectedClip { clip ->
            clip.copy(masks = clip.masks.map { if (it.id == mask.id) mask else it })
        }
    }

    fun deleteMask(maskId: String) {
        if (_state.value.selectedClipId == null) return
        saveUndoState("Delete mask")
        updateSelectedClip { it.copy(masks = it.masks.filter { m -> m.id != maskId }) }
        _state.update { it.copy(selectedMaskId = null) }
    }

    fun updateMaskPoint(maskId: String, pointIndex: Int, x: Float, y: Float) {
        updateSelectedClip { clip ->
            clip.copy(masks = clip.masks.map { mask ->
                if (mask.id == maskId && pointIndex in mask.points.indices) {
                    mask.copy(points = mask.points.toMutableList().apply {
                        set(pointIndex, get(pointIndex).copy(x = x, y = y))
                    })
                } else mask
            })
        }
    }

    fun setFreehandMaskPoints(maskId: String, points: List<MaskPoint>) {
        updateSelectedClip { clip ->
            clip.copy(masks = clip.masks.map { mask ->
                if (mask.id == maskId) mask.copy(points = points) else mask
            })
        }
    }

    // --- Blend Mode ---
    fun showBlendModeSelector() = showPanel(PanelId.BLEND_MODE)
    fun hideBlendModeSelector() = hidePanel(PanelId.BLEND_MODE)

    fun setClipBlendMode(blendMode: BlendMode) {
        if (_state.value.selectedClipId == null) return
        saveUndoState("Blend mode")
        updateSelectedClip { it.copy(blendMode = blendMode) }
        updatePreview()
    }

    fun setTrackBlendMode(trackId: String, blendMode: BlendMode) {
        saveUndoState("Track blend mode")
        _state.update { s ->
            s.copy(tracks = s.tracks.map { track ->
                if (track.id == trackId) track.copy(blendMode = blendMode) else track
            })
        }
    }

    fun setTrackOpacity(trackId: String, opacity: Float) {
        _state.update { s ->
            s.copy(tracks = s.tracks.map { track ->
                if (track.id == trackId) track.copy(opacity = opacity.coerceIn(0f, 1f)) else track
            })
        }
    }

    // --- Batch Export (delegated) ---
    fun showBatchExport() = exportDelegate.showBatchExport()
    fun hideBatchExport() = exportDelegate.hideBatchExport()
    fun addBatchExportItem(config: ExportConfig, name: String) = exportDelegate.addBatchExportItem(config, name)
    fun removeBatchExportItem(id: String) = exportDelegate.removeBatchExportItem(id)
    fun startBatchExport() = exportDelegate.startBatchExport()

    // --- Effect Keyframes ---
    fun addEffectKeyframe(effectId: String, paramName: String, timeOffsetMs: Long, value: Float) {
        if (_state.value.selectedClipId == null) return
        saveUndoState("Effect keyframe")
        updateSelectedClip { clip ->
            clip.copy(effects = clip.effects.map { effect ->
                if (effect.id == effectId) {
                    val kf = EffectKeyframe(timeOffsetMs, paramName, value)
                    effect.copy(keyframes = effect.keyframes + kf)
                } else effect
            })
        }
    }

    // --- Adjustment Layers ---
    fun addAdjustmentLayer() {
        saveUndoState("Add adjustment layer")
        _state.update { s ->
            val newTrack = Track(
                type = TrackType.ADJUSTMENT,
                index = s.tracks.size
            )
            s.copy(tracks = s.tracks + newTrack)
        }
    }

    // --- Captions ---
    fun addCaption(caption: Caption) {
        if (_state.value.selectedClipId == null) return
        saveUndoState("Add caption")
        updateSelectedClip { it.copy(captions = it.captions + caption) }
    }

    fun updateCaption(caption: Caption) {
        updateSelectedClip { clip ->
            clip.copy(captions = clip.captions.map { if (it.id == caption.id) caption else it })
        }
    }

    fun removeCaption(captionId: String) {
        if (_state.value.selectedClipId == null) return
        saveUndoState("Remove caption")
        updateSelectedClip { it.copy(captions = it.captions.filter { c -> c.id != captionId }) }
    }

    // --- Project Snapshots ---
    fun createSnapshot(label: String = "") {
        val s = _state.value
        val autoSaveState = AutoSaveState(projectId = s.project.id, tracks = s.tracks, textOverlays = s.textOverlays, playheadMs = s.playheadMs, chapterMarkers = s.chapterMarkers)
        val json = autoSaveState.serialize()
        val snapshot = ProjectSnapshot(
            projectId = s.project.id,
            timestamp = System.currentTimeMillis(),
            label = label.ifEmpty { "Snapshot ${s.projectSnapshots.size + 1}" },
            stateJson = json
        )
        _state.update { it.copy(projectSnapshots = it.projectSnapshots + snapshot) }
        showToast("Snapshot saved: ${snapshot.label}")
    }

    fun restoreSnapshot(snapshotId: String) {
        val snapshot = _state.value.projectSnapshots.find { it.id == snapshotId } ?: return
        val recovery = AutoSaveState.deserialize(snapshot.stateJson)
        saveUndoState("Restore snapshot")
        _state.update {
            it.copy(
                tracks = recovery.tracks,
                textOverlays = recovery.textOverlays,
                playheadMs = recovery.playheadMs,
                chapterMarkers = recovery.chapterMarkers
            )
        }
        rebuildPlayerTimeline()
        saveProject()
        showToast("Restored: ${snapshot.label}")
    }

    // --- Proxy ---
    fun setProxyEnabled(enabled: Boolean) {
        _state.update { it.copy(proxySettings = it.proxySettings.copy(enabled = enabled)) }
        if (enabled) {
            generateProxiesForAllClips()
        } else {
            proxyEngine.clearProxies()
            rebuildPlayerTimeline()
            showToast("Proxy editing disabled")
        }
    }

    private fun generateProxiesForAllClips() {
        val clips = _state.value.tracks.flatMap { it.clips }
        if (clips.isEmpty()) {
            showToast("No clips to generate proxies for")
            return
        }
        showToast("Generating proxies for ${clips.size} clips...")
        viewModelScope.launch {
            var generated = 0
            for (clip in clips) {
                if (!proxyEngine.hasProxy(clip.sourceUri)) {
                    proxyEngine.generateProxy(
                        clip.sourceUri,
                        _state.value.proxySettings.resolution
                    )
                    generated++
                }
            }
            rebuildPlayerTimeline()
            showToast("Proxy editing enabled ($generated proxies generated)")
        }
    }

    // --- Render Preview + Smart Render (delegated) ---
    fun showRenderPreview() = exportDelegate.showRenderPreview()
    fun hideRenderPreview() = exportDelegate.hideRenderPreview()
    fun renderQuickPreview() = exportDelegate.renderQuickPreview()

    // --- Cloud Backup ---
    fun showCloudBackup() = showPanel(PanelId.CLOUD_BACKUP)
    fun hideCloudBackup() = hidePanel(PanelId.CLOUD_BACKUP)

    // --- Tutorial ---
    fun showTutorial() { _state.update { it.copy(panels = it.panels.open(PanelId.TUTORIAL)) } } // no dismiss — overlays other panels
    fun hideTutorial() {
        hidePanel(PanelId.TUTORIAL)
        viewModelScope.launch { settingsRepo.setTutorialShown() }
    }

    // --- Auto-save indicator ---
    @Volatile
    private var saveIndicatorJob: Job? = null
    fun showSaveIndicator(state: com.novacut.editor.model.SaveIndicatorState) {
        saveIndicatorJob?.cancel()
        _state.update { it.copy(saveIndicator = state) }
        if (state == com.novacut.editor.model.SaveIndicatorState.SAVED) {
            saveIndicatorJob = viewModelScope.launch {
                delay(2000)
                _state.update { it.copy(saveIndicator = com.novacut.editor.model.SaveIndicatorState.HIDDEN) }
            }
        }
    }

    // --- Undo History ---
    fun showUndoHistory() {
        pauseIfPlaying()
        val entries = _state.value.undoStack.mapIndexed { i, a ->
            com.novacut.editor.model.UndoHistoryEntry(i, a.description)
        }.reversed()
        _state.update { dismissedPanelState(it).copy(panels = it.panels.closeAll().open(PanelId.UNDO_HISTORY), undoHistoryEntries = entries) }
    }
    fun hideUndoHistory() = hidePanel(PanelId.UNDO_HISTORY)
    fun jumpToUndoState(index: Int) {
        val stack = _state.value.undoStack
        if (index < 0 || index >= stack.size) return
        val target = stack[index]
        _state.update { it.copy(
            tracks = target.tracks,
            textOverlays = target.textOverlays,
            imageOverlays = target.imageOverlays,
            timelineMarkers = target.timelineMarkers,
            chapterMarkers = target.chapterMarkers,
            undoStack = stack.take(index),
            redoStack = listOf(UndoAction(
                "Current",
                it.tracks,
                it.textOverlays,
                imageOverlays = it.imageOverlays.toList(),
                timelineMarkers = it.timelineMarkers.toList(),
                chapterMarkers = it.chapterMarkers.toList()
            )) + stack.drop(index + 1)
        ) }
        rebuildTimeline()
        showToast("Restored: ${target.description}")
    }

    // --- Caption Style Gallery ---
    fun showCaptionStyleGallery() = showPanel(PanelId.CAPTION_STYLE_GALLERY)
    fun hideCaptionStyleGallery() = hidePanel(PanelId.CAPTION_STYLE_GALLERY)
    fun applyCaptionStyle(template: com.novacut.editor.model.CaptionStyleTemplate) {
        hideCaptionStyleGallery()
        saveUndoState("Apply caption style")
        _state.update { s ->
            s.copy(
                tracks = s.tracks.map { track ->
                    track.copy(clips = track.clips.map { clip ->
                        clip.copy(captions = clip.captions.map { caption ->
                            caption.copy(style = caption.style.copy(
                                fontSize = template.fontSize,
                                fontFamily = template.fontFamily,
                                color = template.textColor,
                                backgroundColor = template.backgroundColor
                            ))
                        })
                    })
                }
            )
        }
        showToast("Caption style applied: ${template.type.displayName}")
    }

    // --- Beat Sync ---
    fun showBeatSync() = showPanel(PanelId.BEAT_SYNC)
    fun hideBeatSync() = hidePanel(PanelId.BEAT_SYNC)
    fun analyzeBeats() {
        val audioClip = _state.value.tracks
            .filter { it.type == TrackType.AUDIO }
            .flatMap { it.clips }
            .firstOrNull() ?: run {
            showToast("Add an audio track first")
            return
        }
        _state.update { it.copy(isAnalyzingBeats = true) }
        viewModelScope.launch {
            try {
                // Extract waveform at reasonable resolution for beat detection
                val waveform = audioEngine.extractWaveform(audioClip.sourceUri, 4000)
                val pcm = ShortArray(waveform.size) { (waveform[it] * 32767).toInt().toShort() }
                // Waveform is mono (1 channel) at ~4000 samples, estimate effective sample rate
                val clipDurationSec = audioClip.sourceDurationMs / 1000.0
                val effectiveSampleRate = if (clipDurationSec > 0) (waveform.size / clipDurationSec).toInt().coerceAtLeast(1) else 4000
                val beats = com.novacut.editor.engine.AudioEffectsEngine.detectBeats(pcm, effectiveSampleRate, 1)
                _state.update { it.copy(beatMarkers = beats, isAnalyzingBeats = false) }
                showToast("Detected ${beats.size} beats")
            } catch (e: Exception) {
                _state.update { it.copy(isAnalyzingBeats = false) }
                showToast("Beat detection failed")
            }
        }
    }
    fun applyBeatSync() {
        val beats = _state.value.beatMarkers
        if (beats.isEmpty()) {
            showToast("Detect beats first")
            return
        }
        saveUndoState("Beat sync")
        var splitCount = 0
        for (beat in beats.sortedDescending()) {
            // Re-read clips each iteration since splits modify state
            val currentClips = _state.value.tracks
                .filter { it.type == TrackType.VIDEO }
                .flatMap { it.clips }
            val clip = currentClips.firstOrNull { beat > it.timelineStartMs && beat < it.timelineEndMs }
            if (clip != null) {
                splitClipAt(clip.id, beat)
                splitCount++
            }
        }
        rebuildTimeline()
        showToast("Split $splitCount clips at beat markers")
        hideBeatSync()
    }

    // --- Smart Reframe ---
    fun showSmartReframe() = showPanel(PanelId.SMART_REFRAME)
    fun hideSmartReframe() = hidePanel(PanelId.SMART_REFRAME)
    fun applySmartReframe(targetAspect: AspectRatio) {
        _state.update { it.copy(isReframing = true) }
        viewModelScope.launch {
            try {
                // Analyze video for subject positions
                val firstClip = _state.value.tracks.flatMap { it.clips }.firstOrNull()
                if (firstClip != null) {
                    val config = SmartReframeEngine.ReframeConfig(
                        targetAspectRatio = targetAspect.toFloat()
                    )
                    smartReframeEngine.analyzeForReframe(firstClip.sourceUri, config) { progress ->
                        // Progress tracked via isReframing state
                    }
                }

                val project = _state.value.project.copy(aspectRatio = targetAspect)
                _state.update { it.copy(
                    project = project,
                    exportConfig = it.exportConfig.copy(aspectRatio = targetAspect),
                    isReframing = false
                ) }
                showToast("Reframed to ${targetAspect.label}")
                hideSmartReframe()
            } catch (e: Exception) {
                _state.update { it.copy(isReframing = false) }
                showToast("Reframe failed: ${e.message}")
            }
        }
    }

    // --- Speed Presets ---
    fun showSpeedPresets() = showPanel(PanelId.SPEED_PRESETS)
    fun hideSpeedPresets() = hidePanel(PanelId.SPEED_PRESETS)
    fun applySpeedPreset(curve: SpeedCurve) {
        if (_state.value.selectedClipId == null) return
        saveUndoState("Speed preset")
        updateSelectedClip { it.copy(speedCurve = curve) }
        rebuildTimeline()
        showToast("Speed preset applied")
        hideSpeedPresets()
    }

    // --- Filler/Silence Removal ---
    fun showFillerRemoval() = showPanel(PanelId.FILLER_REMOVAL)
    fun hideFillerRemoval() = hidePanel(PanelId.FILLER_REMOVAL)

    fun analyzeFillers() {
        val clip = getSelectedClip() ?: return
        _state.update { it.copy(isAnalyzingFillers = true) }
        viewModelScope.launch {
            try {
                val regions = aiFeatures.detectFillerAndSilence(clip.sourceUri)
                _state.update { it.copy(
                    isAnalyzingFillers = false,
                    fillerRegions = regions
                ) }
                showToast("Found ${regions.size} filler/silence regions")
            } catch (e: Exception) {
                _state.update { it.copy(isAnalyzingFillers = false) }
                showToast("Analysis failed")
            }
        }
    }

    fun applyFillerRemoval() {
        val regions = _state.value.fillerRegions
        if (regions.isEmpty()) { showToast("No regions to remove"); return }
        val originalClip = getSelectedClip() ?: return
        saveUndoState("Remove fillers")
        // Convert source-relative times to timeline positions and split+remove in reverse
        // Re-read clip state each iteration since splits mutate clip boundaries
        for (region in regions.sortedByDescending { it.startMs }) {
            val currentClip = _state.value.tracks.flatMap { it.clips }
                .find { it.sourceUri == originalClip.sourceUri && region.startMs >= it.trimStartMs && region.startMs < it.trimEndMs }
                ?: continue
            val timelinePos = currentClip.timelineStartMs + ((region.startMs - currentClip.trimStartMs) / currentClip.speed.coerceAtLeast(0.01f)).toLong()
            if (timelinePos <= currentClip.timelineStartMs || timelinePos >= currentClip.timelineEndMs) continue
            splitClipAt(currentClip.id, timelinePos)
            val clips = _state.value.tracks.flatMap { it.clips }
            val fillerClip = clips.find { it.timelineStartMs == timelinePos }
            if (fillerClip != null) {
                _state.update { s ->
                    s.copy(tracks = s.tracks.map { track ->
                        track.copy(clips = track.clips.filter { it.id != fillerClip.id })
                    })
                }
            }
        }
        rebuildTimeline()
        showToast("Removed ${regions.size} filler regions")
        hideFillerRemoval()
    }

    // --- Auto-Edit ---
    fun showAutoEdit() = showPanel(PanelId.AUTO_EDIT)
    fun hideAutoEdit() = hidePanel(PanelId.AUTO_EDIT)

    fun runAutoEdit() {
        val clips = _state.value.tracks
            .filter { it.type == TrackType.VIDEO }
            .flatMap { it.clips }
        if (clips.isEmpty()) { showToast("Add video clips first"); return }

        _state.update { it.copy(isAutoEditing = true) }
        viewModelScope.launch {
            try {
                val autoClips = clips.map { com.novacut.editor.ai.AutoEditClip(it.sourceUri, it.sourceDurationMs) }
                val musicUri = _state.value.tracks
                    .filter { it.type == TrackType.AUDIO }
                    .flatMap { it.clips }
                    .firstOrNull()?.sourceUri
                val targetMs = 60_000L // 1 minute highlight reel

                val result = aiFeatures.generateAutoEdit(autoClips, musicUri, targetMs)

                if (result.segments.isNotEmpty()) {
                    saveUndoState("Auto edit")
                    // Build new video track from auto-edit segments
                    val newClips = result.segments.map { seg ->
                        val sourceClip = clips[seg.clipIndex]
                        sourceClip.copy(
                            id = java.util.UUID.randomUUID().toString(),
                            trimStartMs = seg.trimStartMs,
                            trimEndMs = seg.trimEndMs,
                            timelineStartMs = seg.timelineStartMs
                        )
                    }
                    _state.update { s ->
                        val videoTrack = s.tracks.first { it.type == TrackType.VIDEO }
                        s.copy(tracks = s.tracks.map { track ->
                            if (track.id == videoTrack.id) track.copy(clips = newClips) else track
                        }, isAutoEditing = false)
                    }
                    rebuildTimeline()
                    showToast("Auto-edit created ${result.segments.size} segments")
                } else {
                    _state.update { it.copy(isAutoEditing = false) }
                    showToast("Could not generate auto-edit")
                }
                hideAutoEdit()
            } catch (e: Exception) {
                _state.update { it.copy(isAutoEditing = false) }
                showToast("Auto-edit failed")
            }
        }
    }

    // --- TTS ---
    fun showTts() {
        pauseIfPlaying()
        if (!ttsEngine.isAvailable()) ttsEngine.initialize { _state.update { it.copy(isTtsAvailable = true) } }
        _state.update { dismissedPanelState(it).copy(panels = it.panels.closeAll().open(PanelId.TTS), isTtsAvailable = ttsEngine.isAvailable()) }
    }
    fun hideTts() { ttsEngine.stopPreview(); hidePanel(PanelId.TTS) }

    fun synthesizeTts(text: String, style: com.novacut.editor.engine.TtsEngine.VoiceStyle) {
        _state.update { it.copy(isSynthesizingTts = true) }
        viewModelScope.launch {
            val file = ttsEngine.synthesize(text, style)
            _state.update { it.copy(isSynthesizingTts = false) }
            if (file != null) {
                val uri = android.net.Uri.fromFile(file)
                // Query actual duration from the generated audio file
                val durationMs = videoEngine.getVideoDuration(uri).takeIf { it > 0 } ?: 3000L
                saveUndoState("Add TTS voice")
                addClipToTrack(uri, durationMs, TrackType.AUDIO)
                rebuildTimeline()
                showToast("Voice added to audio track")
                hideTts()
            } else {
                showToast("TTS synthesis failed")
            }
        }
    }

    fun previewTts(text: String, style: com.novacut.editor.engine.TtsEngine.VoiceStyle) {
        ttsEngine.preview(text, style)
    }

    fun stopTtsPreview() { ttsEngine.stopPreview() }

    // --- Effect Library ---
    fun showEffectLibrary() = showPanel(PanelId.EFFECT_LIBRARY)
    fun hideEffectLibrary() = hidePanel(PanelId.EFFECT_LIBRARY)

    fun exportClipEffects(name: String) {
        val clip = getSelectedClip() ?: return
        viewModelScope.launch {
            val file = effectShareEngine.exportEffects(name, clip.effects, clip.colorGrade, clip.audioEffects)
            if (file != null) {
                showToast("Effects exported: ${file.name}")
            } else {
                showToast("Export failed")
            }
        }
    }

    fun importEffects(uri: android.net.Uri) {
        viewModelScope.launch {
            val imported = effectShareEngine.importEffects(uri)
            if (imported != null) {
                if (_state.value.selectedClipId == null) return@launch
                saveUndoState("Import effects")
                updateSelectedClip { clip ->
                    clip.copy(
                        effects = clip.effects + imported.effects,
                        colorGrade = imported.colorGrade ?: clip.colorGrade
                    )
                }
                showToast("Imported: ${imported.name}")
                updatePreview()
            } else {
                showToast("Import failed — invalid .ncfx file")
            }
        }
    }

    // --- Noise Reduction ---
    fun showNoiseReduction() = showPanel(PanelId.NOISE_REDUCTION)
    fun hideNoiseReduction() { hidePanel(PanelId.NOISE_REDUCTION); _state.update { it.copy(noiseAnalysisResult = null) } }

    // --- Sticker Picker ---
    fun showStickerPicker() = showPanel(PanelId.STICKER_PICKER)
    fun hideStickerPicker() = hidePanel(PanelId.STICKER_PICKER)

    fun analyzeAndReduceNoise() {
        val clip = getSelectedClip() ?: return
        _state.update { it.copy(isAnalyzingNoise = true, noiseAnalysisResult = null) }
        viewModelScope.launch {
            try {
                // Step 1: Analyze noise profile using NoiseReductionEngine
                val noiseProfile = noiseReductionEngine.analyzeNoise(clip.sourceUri)
                val analysisText = "Detected ${noiseProfile.type} noise, SNR: ${noiseProfile.estimatedSnrDb.toInt()} dB" +
                    (noiseProfile.dominantFreqHz?.let { " @ ${it.toInt()} Hz" } ?: "")
                _state.update { it.copy(noiseAnalysisResult = analysisText) }
                showToast(analysisText)

                // Step 2: Apply noise reduction via NoiseReductionEngine
                val mode = when {
                    noiseProfile.estimatedSnrDb < 10f -> NoiseReductionEngine.NoiseReductionMode.AGGRESSIVE
                    noiseProfile.estimatedSnrDb < 20f -> NoiseReductionEngine.NoiseReductionMode.MODERATE
                    noiseProfile.estimatedSnrDb < 30f -> NoiseReductionEngine.NoiseReductionMode.LIGHT
                    else -> NoiseReductionEngine.NoiseReductionMode.OFF
                }

                if (mode != NoiseReductionEngine.NoiseReductionMode.OFF) {
                    saveUndoState("Noise reduction")
                    val result = noiseReductionEngine.processAudio(clip.sourceUri, mode)
                    val processedUri = android.net.Uri.fromFile(result.outputFile)

                    _state.update { s ->
                        s.copy(
                            isAnalyzingNoise = false,
                            noiseAnalysisResult = "$analysisText — applied ${mode.displayName} (SNR improved to ${result.processedSnrDb.toInt()} dB)",
                            tracks = s.tracks.map { track ->
                                track.copy(clips = track.clips.map { c ->
                                    if (c.id == clip.id) {
                                        c.copy(sourceUri = processedUri)
                                    } else c
                                })
                            }
                        )
                    }
                    showToast("Applied ${mode.displayName} noise reduction")
                } else {
                    _state.update { it.copy(isAnalyzingNoise = false) }
                    showToast("Audio is clean — no noise reduction needed")
                }
            } catch (e: Exception) {
                _state.update { it.copy(isAnalyzingNoise = false, noiseAnalysisResult = null) }
                showToast("Noise analysis failed")
            }
        }
    }

    // Helper: add clip to a track by type
    private fun addClipToTrack(uri: android.net.Uri, durationMs: Long, trackType: TrackType) {
        val track = _state.value.tracks.firstOrNull { it.type == trackType } ?: return
        val timelineStart = track.clips.maxOfOrNull { it.timelineEndMs } ?: 0L
        val clip = Clip(
            sourceUri = uri,
            sourceDurationMs = durationMs,
            timelineStartMs = timelineStart,
            trimEndMs = durationMs
        )
        _state.update { s ->
            s.copy(tracks = s.tracks.map { t ->
                if (t.id == track.id) t.copy(clips = t.clips + clip) else t
            })
        }
    }

    // --- Editor Mode ---
    fun toggleEditorMode() {
        _state.update { it.copy(
            editorMode = if (it.editorMode == EditorMode.EASY) EditorMode.PRO else EditorMode.EASY
        ) }
    }

    // --- Timeline Collapse ---
    fun toggleTimelineCollapse() {
        _state.update { it.copy(isTimelineCollapsed = !it.isTimelineCollapsed) }
    }

    // Helper for beat sync splitting
    private fun splitClipAt(clipId: String, positionMs: Long) {
        _state.update { s ->
            s.copy(tracks = s.tracks.map { track ->
                val clipIndex = track.clips.indexOfFirst { it.id == clipId }
                if (clipIndex < 0) return@map track
                val clip = track.clips[clipIndex]
                val relativePos = positionMs - clip.timelineStartMs
                val sourcePos = clip.trimStartMs + (relativePos * clip.speed).toLong()
                if (sourcePos <= clip.trimStartMs + 100 || sourcePos >= clip.trimEndMs - 100) return@map track
                val clip1 = clip.copy(trimEndMs = sourcePos)
                val clip2 = clip.copy(
                    id = java.util.UUID.randomUUID().toString(),
                    trimStartMs = sourcePos,
                    timelineStartMs = clip.timelineStartMs + clip1.durationMs,
                    transition = null
                )
                val newClips = track.clips.toMutableList()
                newClips[clipIndex] = clip1
                newClips.add(clipIndex + 1, clip2)
                track.copy(clips = newClips)
            })
        }
    }

    private fun rebuildTimeline() {
        videoEngine.prepareTimeline(_state.value.tracks)
        updatePreview()
        _state.update { s ->
            s.copy(totalDurationMs = s.tracks.maxOfOrNull { t -> t.clips.maxOfOrNull { c -> c.timelineEndMs } ?: 0L } ?: 0L)
        }
    }

    // --- Multi-Cam Sync ---
    fun syncMultiCamClips() {
        val videoClips = _state.value.tracks
            .filter { it.type == TrackType.VIDEO }
            .flatMap { it.clips }
        if (videoClips.size < 2) {
            showToast("Need at least 2 video clips for multi-cam sync")
            return
        }
        viewModelScope.launch {
            showToast("Syncing clips by audio...")
            try {
                val uris = videoClips.map { it.sourceUri }
                val referenceUri = uris.first()
                val otherUris = uris.drop(1)
                val results = withContext(Dispatchers.IO) {
                    multiCamEngine.syncMultipleClips(referenceUri, otherUris)
                }
                if (results.isNotEmpty()) {
                    saveUndoState("Multi-cam sync")
                    // Build offset list: first clip stays at 0, rest get offsets from sync results
                    val offsets = listOf(0L) + results.map { it.offsetMs }
                    _state.update { s ->
                        s.copy(tracks = s.tracks.map { track ->
                            if (track.type == TrackType.VIDEO) {
                                track.copy(clips = track.clips.mapIndexed { idx, clip ->
                                    val offset = offsets.getOrNull(idx) ?: 0L
                                    clip.copy(timelineStartMs = (clip.timelineStartMs + offset).coerceAtLeast(0L))
                                })
                            } else track
                        })
                    }
                    rebuildTimeline()
                    showToast("Synced ${offsets.size} clips by audio")
                } else {
                    showToast("Could not find audio sync points")
                }
            } catch (e: Exception) {
                showToast("Multi-cam sync failed: ${e.message}")
            }
        }
    }

    // --- Slip/Slide Edit ---
    fun slipClip(clipId: String, slipAmountMs: Long) {
        saveUndoState("Slip edit")
        _state.update { s ->
            s.copy(tracks = s.tracks.map { track ->
                track.copy(clips = track.clips.map { clip ->
                    if (clip.id == clipId) {
                        val newTrimStart = (clip.trimStartMs + slipAmountMs).coerceIn(0L, clip.sourceDurationMs - 100)
                        val duration = clip.trimEndMs - clip.trimStartMs
                        val newTrimEnd = (newTrimStart + duration).coerceAtMost(clip.sourceDurationMs)
                        clip.copy(trimStartMs = newTrimStart, trimEndMs = newTrimEnd)
                    } else clip
                })
            })
        }
        rebuildPlayerTimeline()
    }

    fun slideClip(clipId: String, slideAmountMs: Long) {
        saveUndoState("Slide edit")
        _state.update { s ->
            s.copy(tracks = s.tracks.map { track ->
                val clipIndex = track.clips.indexOfFirst { it.id == clipId }
                if (clipIndex < 0) return@map track

                val clip = track.clips[clipIndex]
                val newStart = (clip.timelineStartMs + slideAmountMs).coerceAtLeast(0L)

                // Adjust neighbors
                val updatedClips = track.clips.toMutableList()
                updatedClips[clipIndex] = clip.copy(timelineStartMs = newStart)

                // Ensure no overlap with adjacent clips
                if (clipIndex > 0) {
                    val prevClip = updatedClips[clipIndex - 1]
                    if (newStart < prevClip.timelineEndMs) {
                        val trimAmount = prevClip.timelineEndMs - newStart
                        updatedClips[clipIndex - 1] = prevClip.copy(
                            trimEndMs = (prevClip.trimEndMs - (trimAmount * prevClip.speed).toLong()).coerceAtLeast(prevClip.trimStartMs + 100)
                        )
                    }
                }
                if (clipIndex < updatedClips.size - 1) {
                    val nextClip = updatedClips[clipIndex + 1]
                    val newEnd = newStart + clip.durationMs
                    if (newEnd > nextClip.timelineStartMs) {
                        val overlap = newEnd - nextClip.timelineStartMs
                        updatedClips[clipIndex + 1] = nextClip.copy(
                            timelineStartMs = newEnd,
                            trimStartMs = (nextClip.trimStartMs + (overlap * nextClip.speed).toLong()).coerceAtMost(nextClip.trimEndMs - 100)
                        )
                    }
                }

                track.copy(clips = updatedClips)
            })
        }
        rebuildPlayerTimeline()
    }

    // --- Export ---
    fun cancelExport() = exportDelegate.cancelExport()

    // --- Media Manager ---
    fun showMediaManager() = showPanel(PanelId.MEDIA_MANAGER)
    fun hideMediaManager() = hidePanel(PanelId.MEDIA_MANAGER)

    fun jumpToClip(clipId: String) {
        val clip = _state.value.tracks.flatMap { it.clips }.find { it.id == clipId } ?: return
        val trackId = _state.value.tracks.find { it.clips.any { c -> c.id == clipId } }?.id
        seekTo(clip.timelineStartMs)
        selectClip(clipId, trackId)
        hideMediaManager()
    }

    fun removeUnusedMedia() {
        val usedUris = _state.value.tracks.flatMap { it.clips }.map { it.sourceUri.toString() }.toSet()
        // In NovaCut, all media is referenced by clips — there's no separate media pool.
        // "Unused" means tracks with zero clips. Remove empty non-default tracks.
        val defaultTrackCount = 2 // VIDEO + AUDIO
        val currentTracks = _state.value.tracks
        if (currentTracks.size <= defaultTrackCount) {
            showToast("No unused tracks to remove")
            return
        }
        saveUndoState("Remove unused tracks")
        val kept = currentTracks.filter { it.clips.isNotEmpty() || it.index < defaultTrackCount }
            .mapIndexed { i, t -> t.copy(index = i) }
        _state.update { recalculateDuration(it.copy(tracks = kept)) }
        val removed = currentTracks.size - kept.size
        showToast("Removed $removed empty track${if (removed != 1) "s" else ""}")
        saveProject()
    }

    // --- Audio Normalization (delegated) ---
    fun showAudioNorm() = audioMixerDelegate.showAudioNorm()
    fun hideAudioNorm() = audioMixerDelegate.hideAudioNorm()
    fun normalizeAudio(targetLufs: Float) = audioMixerDelegate.normalizeAudio(targetLufs)

    // --- Color Match ---
    fun colorMatchToReference(referenceClipId: String) {
        val targetClipId = _state.value.selectedClipId ?: return
        val refClip = _state.value.tracks.flatMap { it.clips }.find { it.id == referenceClipId } ?: return
        val targetClip = _state.value.tracks.flatMap { it.clips }.find { it.id == targetClipId } ?: return

        viewModelScope.launch {
            showToast("Analyzing colors...")
            val refStats = com.novacut.editor.engine.ColorMatchEngine.analyzeFrame(
                appContext, refClip.sourceUri, refClip.trimStartMs + refClip.durationMs / 2
            )
            val targetStats = com.novacut.editor.engine.ColorMatchEngine.analyzeFrame(
                appContext, targetClip.sourceUri, targetClip.trimStartMs + targetClip.durationMs / 2
            )

            if (refStats != null && targetStats != null) {
                saveUndoState("Color match")
                val grade = com.novacut.editor.engine.ColorMatchEngine.generateColorMatch(refStats, targetStats)
                updateClipColorGrade(grade)
                showToast("Color matched to reference clip")
            } else {
                showToast("Could not analyze frames")
            }
        }
    }

    // --- Compound Clips ---
    fun createCompoundClip() {
        val selectedIds = _state.value.selectedClipIds
        if (selectedIds.size < 2) {
            showToast("Select at least 2 clips to create compound")
            return
        }
        saveUndoState("Create compound clip")

        _state.update { s ->
            val allClips = s.tracks.flatMap { it.clips }
            val selectedClips = allClips.filter { it.id in selectedIds }.sortedBy { it.timelineStartMs }
            if (selectedClips.isEmpty()) return@update s

            val compoundStart = selectedClips.minOf { it.timelineStartMs }
            val compoundEnd = selectedClips.maxOf { it.timelineEndMs }

            // Create compound clip containing the selected clips
            val compoundDurationMs = compoundEnd - compoundStart
            val firstClip = selectedClips.first()
            val compoundClip = firstClip.copy(
                id = java.util.UUID.randomUUID().toString(),
                timelineStartMs = compoundStart,
                trimStartMs = 0L,
                trimEndMs = compoundDurationMs,
                speed = 1f,
                isCompound = true,
                compoundClips = selectedClips.map { it.copy() }
            )

            // Remove original clips and insert compound
            val tracks = s.tracks.map { track ->
                val remainingClips = track.clips.filter { it.id !in selectedIds }
                val hadSelected = track.clips.any { it.id in selectedIds }
                if (hadSelected) {
                    track.copy(clips = (remainingClips + compoundClip).sortedBy { it.timelineStartMs })
                } else track
            }

            recalculateDuration(s.copy(
                tracks = tracks,
                selectedClipIds = emptySet(),
                selectedClipId = compoundClip.id
            ))
        }
        rebuildPlayerTimeline()
        showToast("Compound clip created")
    }

    // --- Text Templates ---
    fun showTextTemplates() = showPanel(PanelId.TEXT_TEMPLATES)
    fun hideTextTemplates() = hidePanel(PanelId.TEXT_TEMPLATES)

    fun applyTextTemplate(template: com.novacut.editor.model.TextTemplate) {
        saveUndoState("Apply text template")
        val playhead = _state.value.playheadMs
        template.layers.forEachIndexed { index, layer ->
            val overlay = layer.copy(
                id = UUID.randomUUID().toString(),
                startTimeMs = playhead + index * 100L,
                endTimeMs = playhead + template.durationMs + index * 100L
            )
            _state.update { s -> s.copy(textOverlays = s.textOverlays + overlay) }
        }
        hideTextTemplates()
        showToast("Template applied: ${template.name}")
    }

    // --- Project Archive ---
    fun exportProjectArchive() {
        viewModelScope.launch {
            showToast("Exporting project archive...")
            try {
                val s = _state.value
                val dir = java.io.File(appContext.getExternalFilesDir(null), "archives")
                dir.mkdirs()
                val file = java.io.File(dir, "${s.project.name}.novacut")
                val success = com.novacut.editor.engine.ProjectArchive.exportArchive(
                    context = appContext,
                    projectId = s.project.id,
                    tracks = s.tracks,
                    textOverlays = s.textOverlays,
                    playheadMs = s.playheadMs,
                    outputFile = file
                )
                showToast(if (success) "Archive saved: ${file.name}" else "Archive export failed")
            } catch (e: Exception) {
                showToast("Archive export failed: ${e.message}")
            }
        }
    }

    fun exportToOtio() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val s = _state.value
                val otioJson = timelineExchangeEngine.exportToOtio(s.tracks, s.textOverlays, s.project.name)
                val dir = java.io.File(appContext.getExternalFilesDir(null), "exports")
                dir.mkdirs()
                val file = java.io.File(dir, "${s.project.name}.otio")
                file.writeText(otioJson)
                withContext(Dispatchers.Main) { showToast("OTIO exported: ${file.name}") }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { showToast("OTIO export failed: ${e.message}") }
            }
        }
    }

    fun exportToFcpxml() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val s = _state.value
                val xml = timelineExchangeEngine.exportToFcpxml(s.tracks, s.project.name, s.exportConfig.frameRate)
                val dir = java.io.File(appContext.getExternalFilesDir(null), "exports")
                dir.mkdirs()
                val file = java.io.File(dir, "${s.project.name}.fcpxml")
                file.writeText(xml)
                withContext(Dispatchers.Main) { showToast("FCPXML exported: ${file.name}") }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { showToast("FCPXML export failed: ${e.message}") }
            }
        }
    }

    // --- Linked A/V ---
    fun unlinkAudioVideo() {
        if (_state.value.selectedClipId == null) return
        saveUndoState("Unlink A/V")
        updateSelectedClip { it.copy(linkedClipId = null) }
        showToast("Audio/video unlinked")
    }

    // --- Captions ---
    fun showCaptionEditor() = showPanel(PanelId.CAPTION_EDITOR)
    fun hideCaptionEditor() = hidePanel(PanelId.CAPTION_EDITOR)

    fun generateAutoCaption() {
        val clipId = _state.value.selectedClipId ?: return
        viewModelScope.launch {
            showToast("Generating captions...")
            runAiTool("auto_captions")
        }
    }

    // --- Chapter Markers ---
    fun showChapterMarkers() = showPanel(PanelId.CHAPTER_MARKERS)
    fun hideChapterMarkers() = hidePanel(PanelId.CHAPTER_MARKERS)

    fun addChapterMarker(marker: ChapterMarker) {
        saveUndoState("Add chapter")
        val totalDuration = _state.value.totalDurationMs
        val clampedMarker = marker.copy(timeMs = marker.timeMs.coerceIn(0L, totalDuration))
        _state.update { s ->
            val updated = (s.chapterMarkers + clampedMarker).sortedBy { it.timeMs }
            s.copy(chapterMarkers = updated)
        }
        saveProject()
        showToast("Chapter added at ${formatTime(clampedMarker.timeMs)}")
    }

    fun updateChapterMarker(index: Int, marker: ChapterMarker) {
        saveUndoState("Update chapter")
        val totalDuration = _state.value.totalDurationMs
        val clampedMarker = marker.copy(timeMs = marker.timeMs.coerceIn(0L, totalDuration))
        _state.update { s ->
            if (index in s.chapterMarkers.indices) {
                val updated = s.chapterMarkers.toMutableList()
                updated[index] = clampedMarker
                s.copy(chapterMarkers = updated.sortedBy { it.timeMs })
            } else s
        }
        saveProject()
    }

    fun deleteChapterMarker(index: Int) {
        saveUndoState("Delete chapter")
        _state.update { s ->
            if (index in s.chapterMarkers.indices) {
                s.copy(chapterMarkers = s.chapterMarkers.toMutableList().also { it.removeAt(index) })
            } else s
        }
        saveProject()
    }

    private fun formatTime(ms: Long): String {
        val s = ms / 1000
        val m = s / 60
        return "%d:%02d".format(m, s % 60)
    }

    // --- Snapshot History ---
    fun showSnapshotHistory() = showPanel(PanelId.SNAPSHOT_HISTORY)
    fun hideSnapshotHistory() = hidePanel(PanelId.SNAPSHOT_HISTORY)

    fun deleteSnapshot(snapshotId: String) {
        _state.update { it.copy(projectSnapshots = it.projectSnapshots.filter { s -> s.id != snapshotId }) }
    }

    // --- Multi-select ---
    fun toggleClipMultiSelect(clipId: String) {
        _state.update { s ->
            val current = s.selectedClipIds
            val updated = if (clipId in current) current - clipId else current + clipId
            s.copy(selectedClipIds = updated)
        }
    }

    fun clearMultiSelect() {
        _state.update { it.copy(selectedClipIds = emptySet()) }
    }

    fun groupSelectedClips() {
        val ids = _state.value.selectedClipIds
        if (ids.size < 2) { showToast("Select 2+ clips to group"); return }
        val groupId = java.util.UUID.randomUUID().toString()
        saveUndoState("Group clips")
        _state.update { s ->
            s.copy(tracks = s.tracks.map { track ->
                track.copy(clips = track.clips.map { clip ->
                    if (clip.id in ids) clip.copy(groupId = groupId) else clip
                })
            })
        }
        showToast("Grouped ${ids.size} clips")
    }

    fun ungroupSelectedClips() {
        val ids = _state.value.selectedClipIds
        saveUndoState("Ungroup clips")
        _state.update { s ->
            s.copy(tracks = s.tracks.map { track ->
                track.copy(clips = track.clips.map { clip ->
                    if (clip.id in ids) clip.copy(groupId = null) else clip
                })
            })
        }
        showToast("Clips ungrouped")
    }

    fun deleteMultiSelectedClips() {
        val clipIds = _state.value.selectedClipIds
        if (clipIds.isEmpty()) return
        saveUndoState("Delete ${clipIds.size} clips")
        _state.update { s ->
            val tracks = s.tracks.map { track ->
                track.copy(clips = track.clips.filter { it.id !in clipIds })
            }
            recalculateDuration(s.copy(
                tracks = tracks,
                selectedClipIds = emptySet(),
                selectedClipId = null,
                selectedTrackId = null
            ))
        }
        rebuildPlayerTimeline()
        showToast("Deleted ${clipIds.size} clips")
    }

    // --- Subtitle Export ---
    fun exportSubtitles(format: SubtitleFormat) {
        val captions = _state.value.tracks.flatMap { it.clips }.flatMap { it.captions }
        if (captions.isEmpty()) {
            showToast("No captions to export")
            return
        }
        viewModelScope.launch {
            val dir = java.io.File(appContext.getExternalFilesDir(null), "subtitles")
            dir.mkdirs()
            val file = java.io.File(dir, "${_state.value.project.name}.${format.extension}")
            val success = SubtitleExporter.export(captions, format, file)
            if (success) {
                showToast("Exported to ${file.name}")
            } else {
                showToast("Export failed")
            }
        }
    }

    // --- PiP ---
    fun applyPipPreset(preset: com.novacut.editor.ui.editor.PipPreset) {
        if (_state.value.selectedClipId == null) return
        saveUndoState("PiP preset")
        updateSelectedClip { it.copy(positionX = preset.posX, positionY = preset.posY, scaleX = preset.scaleX, scaleY = preset.scaleY) }
    }

    fun showPipPresets() = showPanel(PanelId.PIP_PRESETS)
    fun hidePipPresets() = hidePanel(PanelId.PIP_PRESETS)

    fun showChromaKey() = showPanel(PanelId.CHROMA_KEY)
    fun hideChromaKey() = hidePanel(PanelId.CHROMA_KEY)

    // --- Video Scopes ---
    private val _scopeFrame = MutableStateFlow<android.graphics.Bitmap?>(null)
    val scopeFrame: StateFlow<android.graphics.Bitmap?> = _scopeFrame.asStateFlow()

    fun toggleScopes() {
        val willShow = !_state.value.panels.isOpen(PanelId.SCOPES)
        _state.update { it.copy(panels = if (willShow) it.panels.open(PanelId.SCOPES) else it.panels.close(PanelId.SCOPES)) }
        if (willShow) updateScopeFrame()
    }

    fun updateScopeFrame() {
        val clip = getSelectedClip() ?: _state.value.tracks
            .flatMap { it.clips }.firstOrNull() ?: return
        val relativeOffset = _state.value.playheadMs - clip.timelineStartMs
        val playheadInClip = (clip.trimStartMs + (relativeOffset * clip.speed).toLong())
            .coerceIn(clip.trimStartMs, clip.trimEndMs)
        viewModelScope.launch(Dispatchers.IO) {
            val frame = videoEngine.extractThumbnail(
                clip.sourceUri, playheadInClip * 1000, 256, 144
            )
            _scopeFrame.value = frame
        }
    }

    fun setScopeType(type: com.novacut.editor.ui.editor.ScopeType) {
        _state.update { it.copy(activeScopeType = type) }
    }

    // --- Transform overlay ---
    fun setClipAnchor(x: Float, y: Float) {
        updateSelectedClip { it.copy(anchorX = x, anchorY = y) }
    }

    // --- Auto-ducking ---
    fun autoDuck() {
        val s = _state.value
        val musicTracks = s.tracks.filter { it.type == TrackType.AUDIO }
        val voiceTracks = s.tracks.filter { it.type == TrackType.VIDEO && !it.isMuted }

        if (musicTracks.isEmpty() || voiceTracks.isEmpty()) {
            showToast("Need both voice and music tracks for ducking")
            return
        }

        viewModelScope.launch {
            showToast("Analyzing speech regions...")
            try {
                val voiceClip = voiceTracks.flatMap { it.clips }.firstOrNull() ?: return@launch
                val waveform = withContext(Dispatchers.IO) {
                    audioEngine.extractWaveform(voiceClip.sourceUri, 44100)
                }
                val pcm = waveform.map { (it * 32767).toInt().toShort() }.toShortArray()
                val speechRegions = withContext(Dispatchers.Default) {
                    com.novacut.editor.engine.AudioEffectsEngine.detectSpeechRegions(pcm, 44100, 1)
                }

                if (speechRegions.isEmpty()) {
                    showToast("No speech detected")
                    return@launch
                }

                saveUndoState("Auto duck")

                // Create volume keyframes on music tracks
                _state.update { state ->
                    state.copy(tracks = state.tracks.map { track ->
                        if (track.type == TrackType.AUDIO) {
                            track.copy(clips = track.clips.map { clip ->
                                val duckKeyframes = mutableListOf<com.novacut.editor.model.Keyframe>()
                                for ((start, end) in speechRegions) {
                                    duckKeyframes.addAll(
                                        com.novacut.editor.engine.KeyframeEngine.createVolumeDuck(
                                            startMs = start, endMs = end,
                                            normalVolume = clip.volume, duckVolume = clip.volume * 0.15f
                                        )
                                    )
                                }
                                clip.copy(keyframes = clip.keyframes + duckKeyframes)
                            })
                        } else track
                    })
                }
                showToast("Ducking applied: ${speechRegions.size} regions")
            } catch (e: Exception) {
                showToast("Auto-duck failed: ${e.message ?: "Unknown error"}")
            }
        }
    }

    // Voiceover recording
    @Volatile
    private var voiceoverDurationJob: Job? = null

    fun startVoiceover() {
        val file = voiceoverEngine.startRecording()
        if (file == null) {
            showToast("Microphone access failed")
            return
        }
        _state.update { it.copy(isRecordingVoiceover = true, voiceoverDurationMs = 0L) }
        voiceoverDurationJob = viewModelScope.launch {
            while (isActive) {
                delay(100)
                _state.update { it.copy(voiceoverDurationMs = voiceoverEngine.getRecordingDurationMs()) }
            }
        }
    }

    fun stopVoiceover() {
        voiceoverDurationJob?.cancel()
        val uri = voiceoverEngine.stopRecording()
        _state.update { it.copy(isRecordingVoiceover = false) }
        if (uri != null) {
            addClipToTrack(uri, TrackType.AUDIO)
            showToast("Voiceover added to audio track")
        } else {
            showToast("Voiceover recording failed")
        }
    }

    fun setClipVolume(clipId: String, volume: Float) {
        updateClipById(clipId) { it.copy(volume = volume.coerceIn(0f, 2f)) }
    }

    fun beginVolumeChange() {
        saveUndoState("Change volume")
    }

    fun beginTransformChange() {
        saveUndoState("Transform clip")
    }

    fun setClipTransform(clipId: String, positionX: Float? = null, positionY: Float? = null,
                         scaleX: Float? = null, scaleY: Float? = null, rotation: Float? = null) {
        updateClipById(clipId) { clip ->
            clip.copy(
                positionX = positionX ?: clip.positionX,
                positionY = positionY ?: clip.positionY,
                scaleX = (scaleX ?: clip.scaleX).coerceIn(0.1f, 5f),
                scaleY = (scaleY ?: clip.scaleY).coerceIn(0.1f, 5f),
                rotation = rotation ?: clip.rotation
            )
        }
        updatePreview()
    }

    fun resetClipTransform(clipId: String) {
        saveUndoState("Reset transform")
        updateClipById(clipId) { it.copy(positionX = 0f, positionY = 0f, scaleX = 1f, scaleY = 1f, rotation = 0f) }
        saveProject()
    }

    fun setClipOpacity(clipId: String, opacity: Float) {
        updateClipById(clipId) { it.copy(opacity = opacity.coerceIn(0f, 1f)) }
        updatePreview()
    }

    fun beginFadeAdjust() {
        saveUndoState("Adjust fade")
    }

    fun setClipFadeIn(clipId: String, fadeInMs: Long) {
        updateClipById(clipId) { clip ->
            val maxFade = (clip.durationMs - clip.fadeOutMs).coerceAtLeast(0L)
            clip.copy(fadeInMs = fadeInMs.coerceIn(0L, maxFade))
        }
    }

    fun setClipFadeOut(clipId: String, fadeOutMs: Long) {
        updateClipById(clipId) { clip ->
            val maxFade = (clip.durationMs - clip.fadeInMs).coerceAtLeast(0L)
            clip.copy(fadeOutMs = fadeOutMs.coerceIn(0L, maxFade))
        }
    }

    // Export
    fun updateExportConfig(config: ExportConfig) {
        _state.update { it.copy(exportConfig = config) }
    }

    fun startExport(outputDir: File) = exportDelegate.startExport(outputDir)
    fun getShareIntent(): Intent? = exportDelegate.getShareIntent()
    fun saveToGallery() = exportDelegate.saveToGallery()

    // Undo/Redo
    fun undo() {
        val undoStack = _state.value.undoStack
        if (undoStack.isEmpty()) return

        val action = undoStack.last()
        val currentAction = UndoAction(
            "Redo",
            _state.value.tracks.map { it.copy() },
            _state.value.textOverlays.toList(),
            imageOverlays = _state.value.imageOverlays.toList(),
            timelineMarkers = _state.value.timelineMarkers.toList(),
            chapterMarkers = _state.value.chapterMarkers.toList()
        )

        _state.update {
            val restored = recalculateDuration(it.copy(
                tracks = action.tracks,
                textOverlays = action.textOverlays,
                imageOverlays = action.imageOverlays,
                timelineMarkers = action.timelineMarkers,
                chapterMarkers = action.chapterMarkers,
                undoStack = undoStack.dropLast(1),
                redoStack = it.redoStack + currentAction
            ))
            val clipExists = it.selectedClipId != null &&
                restored.tracks.any { t -> t.clips.any { c -> c.id == it.selectedClipId } }
            dismissedPanelState(restored).copy(
                selectedClipId = if (clipExists) it.selectedClipId else null,
                currentTool = EditorTool.NONE
            )
        }
        rebuildPlayerTimeline()
    }

    fun redo() {
        val redoStack = _state.value.redoStack
        if (redoStack.isEmpty()) return

        val action = redoStack.last()
        val currentAction = UndoAction(
            "Undo",
            _state.value.tracks.map { it.copy() },
            _state.value.textOverlays.toList(),
            imageOverlays = _state.value.imageOverlays.toList(),
            timelineMarkers = _state.value.timelineMarkers.toList(),
            chapterMarkers = _state.value.chapterMarkers.toList()
        )

        _state.update {
            val restored = recalculateDuration(it.copy(
                tracks = action.tracks,
                textOverlays = action.textOverlays,
                imageOverlays = action.imageOverlays,
                timelineMarkers = action.timelineMarkers,
                chapterMarkers = action.chapterMarkers,
                redoStack = redoStack.dropLast(1),
                undoStack = it.undoStack + currentAction
            ))
            val clipExists = it.selectedClipId != null &&
                restored.tracks.any { t -> t.clips.any { c -> c.id == it.selectedClipId } }
            dismissedPanelState(restored).copy(
                selectedClipId = if (clipExists) it.selectedClipId else null,
                currentTool = EditorTool.NONE
            )
        }
        rebuildPlayerTimeline()
    }

    @Volatile
    private var toastJob: Job? = null

    fun showToast(message: String) {
        toastJob?.cancel()
        _state.update { it.copy(toastMessage = message) }
        toastJob = viewModelScope.launch {
            delay(3000)
            _state.update { it.copy(toastMessage = null) }
        }
    }

    // --- Favorite/Recent Effects ---

    fun toggleEffectFavorite(effectType: EffectType) {
        viewModelScope.launch { settingsRepo.toggleFavoriteEffect(effectType.name) }
    }

    fun trackEffectUsage(effectType: EffectType) {
        viewModelScope.launch { settingsRepo.addRecentEffect(effectType.name) }
    }

    fun getSelectedClip(): Clip? {
        val clipId = _state.value.selectedClipId ?: return null
        return _state.value.tracks.flatMap { it.clips }.firstOrNull { it.id == clipId }
    }

    fun getSelectedTrack(): Track? {
        val trackId = _state.value.selectedTrackId ?: return null
        return _state.value.tracks.firstOrNull { it.id == trackId }
    }

    // Project persistence
    fun saveProject() {
        viewModelScope.launch {
            val firstClipUri = _state.value.tracks
                .filter { it.type == TrackType.VIDEO }
                .flatMap { it.clips }
                .firstOrNull()?.sourceUri?.toString()

            val project = _state.value.project.copy(
                updatedAt = System.currentTimeMillis(),
                durationMs = _state.value.totalDurationMs,
                thumbnailUri = firstClipUri
            )
            projectDao.insertProject(project)
            _state.update { it.copy(project = project) }
        }
    }

    fun renameProject(name: String) {
        _state.update { it.copy(project = it.project.copy(name = name)) }
        saveProject()
    }

    fun updateProjectAspect(aspect: AspectRatio) {
        _state.update { it.copy(project = it.project.copy(aspectRatio = aspect)) }
        saveProject()
        showToast("Aspect ratio: ${aspect.label}")
    }

    private fun saveUndoState(description: String) {
        _state.update { state ->
            val action = UndoAction(
                description = description,
                tracks = state.tracks.map { it.copy() },
                textOverlays = state.textOverlays.toList(),
                imageOverlays = state.imageOverlays.toList(),
                timelineMarkers = state.timelineMarkers.toList(),
                chapterMarkers = state.chapterMarkers.toList()
            )
            state.copy(
                undoStack = (state.undoStack + action).takeLast(50),
                redoStack = emptyList()
            )
        }
    }

    // AI Tools
    fun downloadWhisperModel() = aiToolsDelegate.downloadWhisperModel()
    fun deleteWhisperModel() = aiToolsDelegate.deleteWhisperModel()
    fun saveAsTemplate(name: String) = aiToolsDelegate.saveAsTemplate(name)
    fun downloadSegmentationModel() = aiToolsDelegate.downloadSegmentationModel()
    fun deleteSegmentationModel() = aiToolsDelegate.deleteSegmentationModel()

    fun runAiTool(toolId: String) = aiToolsDelegate.runAiTool(toolId)
    fun cancelAiTool() = aiToolsDelegate.cancelAiTool()

    fun insertFreezeFrame() {
        val clip = getSelectedClip() ?: return
        val playheadMs = _state.value.playheadMs
        if (playheadMs < clip.timelineStartMs || playheadMs >= clip.timelineEndMs) {
            showToast("Move playhead over the selected clip")
            return
        }

        val relativeMs = playheadMs - clip.timelineStartMs
        val sourceTimeMs = clip.trimStartMs + (relativeMs * clip.speed).toLong()

        viewModelScope.launch {
            showToast("Extracting frame...")
            val frameFile = withContext(Dispatchers.IO) {
                videoEngine.extractFrameToFile(clip.sourceUri, sourceTimeMs)
            }
            if (frameFile == null) {
                showToast("Failed to extract frame")
                return@launch
            }

            val frameUri = Uri.fromFile(frameFile)
            val freezeDurationMs = 2000L

            saveUndoState("Freeze frame")

            // Split at playhead, then insert freeze frame between halves
            _state.update { s ->
                val tracks = s.tracks.map { track ->
                    val clipIndex = track.clips.indexOfFirst { it.id == clip.id }
                    if (clipIndex < 0) return@map track

                    val c = track.clips[clipIndex]
                    val splitInSource = c.trimStartMs + (relativeMs * c.speed).toLong()

                    val firstHalf = c.copy(trimEndMs = splitInSource)
                    val freezeClip = Clip(
                        id = UUID.randomUUID().toString(),
                        sourceUri = frameUri,
                        sourceDurationMs = freezeDurationMs,
                        timelineStartMs = firstHalf.timelineEndMs,
                        trimStartMs = 0L,
                        trimEndMs = freezeDurationMs
                    )
                    val secondHalf = c.copy(
                        id = UUID.randomUUID().toString(),
                        timelineStartMs = freezeClip.timelineEndMs,
                        trimStartMs = splitInSource
                    )

                    // Shift subsequent clips
                    val shift = freezeDurationMs
                    val newClips = buildList {
                        addAll(track.clips.subList(0, clipIndex))
                        add(firstHalf)
                        add(freezeClip)
                        add(secondHalf)
                        addAll(track.clips.subList(clipIndex + 1, track.clips.size).map { cl ->
                            cl.copy(timelineStartMs = cl.timelineStartMs + shift)
                        })
                    }
                    track.copy(clips = newClips)
                }
                recalculateDuration(s.copy(tracks = tracks))
            }
            rebuildPlayerTimeline()
            saveProject()
            showToast("Freeze frame inserted (2s)")
        }
    }

    private fun recalculateDuration(state: EditorState): EditorState {
        val totalDuration = state.tracks.maxOfOrNull { t ->
            t.clips.maxOfOrNull { it.timelineEndMs } ?: 0L
        } ?: 0L
        return state.copy(totalDurationMs = totalDuration)
    }

    override fun onCleared() {
        super.onCleared()
        saveIndicatorJob?.cancel()
        toastJob?.cancel()
        aiToolsDelegate.cancelAiTool()
        autoSave.stop()
        voiceoverDurationJob?.cancel()
        voiceoverEngine.release()
        ttsEngine.stopPreview()
        videoEngine.removePlayerListener()
        videoEngine.resetExportState()
        audioEngine.clearWaveformCache()
        // DON'T call videoEngine.release() or ttsEngine.release() — they're @Singletons
    }
}

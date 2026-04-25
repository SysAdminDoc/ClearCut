package com.novacut.editor.ui.editor

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.novacut.editor.R
import com.novacut.editor.ai.AiFeatures
import com.novacut.editor.engine.AudioEngine
import com.novacut.editor.engine.AutoSaveState
import com.novacut.editor.engine.ExportState
import com.novacut.editor.engine.ProjectAutoSave
import com.novacut.editor.engine.ProxyEngine
import com.novacut.editor.engine.SettingsRepository
import com.novacut.editor.engine.SmartRenderEngine
import com.novacut.editor.engine.SubtitleExporter
import com.novacut.editor.engine.TextBasedEditEngine
import com.novacut.editor.engine.AutoChapterEngine
import com.novacut.editor.engine.TalkingHeadFramingEngine
import com.novacut.editor.engine.KaraokeCaptionEngine
import com.novacut.editor.engine.StreamCopyExportEngine
import com.novacut.editor.engine.ContentIdEngine
import com.novacut.editor.engine.DirectPublishEngine
import com.novacut.editor.engine.FlashSafetyEngine
import com.novacut.editor.engine.ColorBlindPreviewEngine
import com.novacut.editor.engine.AiThumbnailEngine
import com.novacut.editor.engine.AudioDescriptionEngine
import com.novacut.editor.engine.StylusMidiEngine
import com.novacut.editor.engine.BeatDetectionEngine
import com.novacut.editor.engine.cleanupFrameOutputFiles
import com.novacut.editor.engine.createFrameCaptureOutputFiles
import com.novacut.editor.engine.finalizeFrameOutputFile
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
import com.novacut.editor.engine.sanitizeFileName
import com.novacut.editor.engine.writeUtf8TextAtomically
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
import kotlin.math.roundToLong

private const val TIMELINE_BASE_SCALE = 0.15f
// Min zoom lowered from 0.1 → 0.01 so a ~10-minute video fits the phone viewport
// when the user taps "fit to window" or when the timeline auto-fits on first layout.
// Previously fit-zoom was clamped before it could reach a ratio that actually fit,
// which is why long clips appeared to only show a narrow window of editable content.
private const val MIN_TIMELINE_ZOOM = 0.01f
private const val MAX_TIMELINE_ZOOM = 10f
private const val WAVEFORM_PRELOAD_PADDING_MS = 3_000L
private const val WAVEFORM_FALLBACK_WINDOW_MS = 15_000L

enum class PanelId {
    MEDIA_PICKER, EXPORT_SHEET, EFFECTS, TEXT_EDITOR, TRANSITION_PICKER,
    AUDIO, AI_TOOLS, TRANSFORM, CROP, VOICEOVER_RECORDER,
    COLOR_GRADING, AUDIO_MIXER, KEYFRAME_EDITOR, SPEED_CURVE,
    MASK_EDITOR, BLEND_MODE, BATCH_EXPORT, PIP_PRESETS, CHROMA_KEY,
    SCOPES, CAPTION_EDITOR, CHAPTER_MARKERS, SNAPSHOT_HISTORY,
    TEXT_TEMPLATES, MEDIA_MANAGER, AUDIO_NORM, RENDER_PREVIEW,
    CLOUD_BACKUP, TUTORIAL, UNDO_HISTORY, CAPTION_STYLE_GALLERY,
    BEAT_SYNC, SMART_REFRAME, SPEED_PRESETS, FILLER_REMOVAL,
    AUTO_EDIT, TTS, EFFECT_LIBRARY, NOISE_REDUCTION, STICKER_PICKER,
    DRAWING, MULTI_CAM, MARKER_LIST, SCRATCHPAD, RECOVERY_DIALOG,
    // v3.69 — 15-feature wave (composite hub + drill-downs).
    V369_FEATURES,
    TEXT_BASED_EDIT, AUTO_CHAPTER, TALKING_HEAD, KARAOKE_CAPTIONS,
    CONTENT_ID, DIRECT_PUBLISH, FLASH_SAFETY, COLOR_BLIND_PREVIEW,
    AI_THUMBNAIL, AUDIO_DESCRIPTION
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
    val toastSeverity: ToastSeverity = ToastSeverity.Info,
    // Set when a recent burst of destructive operations (≥3 deletes in 10s)
    // trips the bulk-change guard. The UI layer uses the nonce to render a
    // one-shot action snackbar ("N clips deleted — Undo"). Null when no
    // banner is pending or after the user interacts with it.
    val bulkUndoPrompt: BulkUndoPrompt? = null,
    val aiRequirementPrompt: AiRequirementPrompt? = null,
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
    val savedExportConfig: ExportConfig? = null,
    // Drawing overlay
    val drawingPaths: List<com.novacut.editor.model.DrawingPath> = emptyList(),
    val isDrawingMode: Boolean = false,
    val drawingColor: Long = 0xFFF38BA8L,
    val drawingStrokeWidth: Float = 4f,
    val aiSuggestion: AiSuggestion? = null,
    // v3.69 feature-wave state
    val v369: V369State = V369State()
)

/**
 * State bag for the v3.69 15-feature wave. Lives as a nested block to keep the
 * top-level EditorState from ballooning; individual features pull what they
 * need via `state.v369.xxx`. All fields default to an empty/neutral value so
 * existing code paths keep working when the features are not in use.
 */
@androidx.compose.runtime.Immutable
data class V369State(
    val transcript: com.novacut.editor.model.Transcript? = null,
    val selectedWordIndices: Set<Int> = emptySet(),
    val chapterCandidates: List<com.novacut.editor.engine.AutoChapterEngine.ChapterCandidate> = emptyList(),
    val flashWarnings: List<com.novacut.editor.engine.FlashSafetyEngine.Warning> = emptyList(),
    val thumbnailCandidates: List<com.novacut.editor.engine.AiThumbnailEngine.Candidate> = emptyList(),
    val colorBlindMode: com.novacut.editor.engine.ColorBlindPreviewEngine.Mode =
        com.novacut.editor.engine.ColorBlindPreviewEngine.Mode.OFF,
    val karaokeStyle: com.novacut.editor.engine.KaraokeCaptionEngine.KaraokeStyle =
        com.novacut.editor.engine.KaraokeCaptionEngine.KaraokeStyle.MRBEAST,
    val streamCopyEligibility: com.novacut.editor.engine.StreamCopyExportEngine.Eligibility? = null,
    val contentIdResult: com.novacut.editor.engine.ContentIdEngine.Match? = null,
    val isAnalyzingFlashes: Boolean = false,
    val isScoringThumbnails: Boolean = false,
    val isTrackingFaces: Boolean = false,
    val isGeneratingChapters: Boolean = false
)

data class AiSuggestion(
    val id: String,
    val message: String,
    val actionId: String
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
    val chapterMarkers: List<ChapterMarker> = emptyList(),
    val drawingPaths: List<com.novacut.editor.model.DrawingPath> = emptyList(),
    // Restoring the playhead with the rest of the state prevents a scrub to
    // an orphan timeline position after undoing a clip deletion or merge.
    // Default 0 so callers that don't capture it (e.g. older serialization
    // paths, if any) still construct a valid record.
    val playheadMs: Long = 0L
)

/**
 * One-shot banner data raised when the ClipEditingDelegate bulk-change
 * tracker spots an unusual burst of destructive operations. The UI uses
 * `id` (a nonce) to key an ephemeral Snackbar; re-emitting with a new id
 * re-shows the banner even when `count` and `undoLabel` happen to match a
 * previous event. Null-ing the field on the state clears the banner.
 */
data class BulkUndoPrompt(
    val id: Long,
    val count: Int,
    val windowMs: Long
)

data class AiRequirementPrompt(
    val id: Long = SystemClock.uptimeMillis(),
    val title: String,
    val body: String,
    val modelName: String,
    val estimatedSize: String,
    val actionLabel: String
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
    // v3.69 engines (15-feature wave)
    private val textBasedEditEngine: TextBasedEditEngine,
    private val autoChapterEngine: AutoChapterEngine,
    private val talkingHeadEngine: TalkingHeadFramingEngine,
    private val karaokeCaptionEngine: KaraokeCaptionEngine,
    private val streamCopyEngine: StreamCopyExportEngine,
    private val contentIdEngine: ContentIdEngine,
    private val directPublishEngine: DirectPublishEngine,
    private val flashSafetyEngine: FlashSafetyEngine,
    private val colorBlindEngine: ColorBlindPreviewEngine,
    private val aiThumbnailEngine: AiThumbnailEngine,
    private val audioDescriptionEngine: AudioDescriptionEngine,
    private val stylusMidiEngine: StylusMidiEngine,
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
        getSelectedClip = ::getSelectedClip, updatePreview = ::updatePreview,
        saveProject = ::saveProject
    )

    val audioMixerDelegate = AudioMixerDelegate(
        stateFlow = _state, beatDetectionEngine = beatDetectionEngine,
        loudnessEngine = loudnessEngine, scope = viewModelScope,
        saveUndoState = ::saveUndoState, showToast = ::showToast,
        pauseIfPlaying = ::pauseIfPlaying, dismissedPanelState = ::dismissedPanelState,
        refreshPreview = ::updatePreview,
        saveProject = ::saveProject
    )

    val exportDelegate = ExportDelegate(
        stateFlow = _state, videoEngine = videoEngine, appContext = appContext,
        scope = viewModelScope, showToast = ::showToast,
        pauseIfPlaying = ::pauseIfPlaying, dismissedPanelState = ::dismissedPanelState,
        showExportSheet = ::showExportSheet,
        streamCopyEngine = streamCopyEngine
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
        stateFlow = _state, videoEngine = videoEngine,
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
            // Auto-fit on first clip: when we go from empty→populated, frame the full
            // project so the user immediately sees the whole clip. Matches CapCut /
            // VN UX where importing the first asset fills the editable area.
            requestInitialFitIfNeeded()
        }
    )

    val effectsDelegate = EffectsDelegate(
        stateFlow = _state, saveUndoState = ::saveUndoState, showToast = ::showToast,
        updatePreview = ::updatePreview, rebuildPlayerTimeline = ::rebuildPlayerTimeline,
        saveProject = ::saveProject, getSelectedClip = ::getSelectedClip,
        recalculateDuration = ::recalculateDuration
    )

    val overlayDelegate = OverlayDelegate(
        stateFlow = _state, saveUndoState = ::saveUndoState, showToast = ::showToast,
        saveProject = ::saveProject
    )

    val v369Delegate = V369Delegate(
        stateFlow = _state, scope = viewModelScope, appContext = appContext,
        saveUndoState = ::saveUndoState, showToast = ::showToast,
        saveProject = ::saveProject, rebuildPlayerTimeline = ::rebuildPlayerTimeline,
        recalculateDuration = ::recalculateDuration,
        textBased = textBasedEditEngine, autoChapter = autoChapterEngine,
        talkingHead = talkingHeadEngine, karaoke = karaokeCaptionEngine,
        streamCopy = streamCopyEngine, contentId = contentIdEngine,
        publish = directPublishEngine, flashSafety = flashSafetyEngine,
        colorBlind = colorBlindEngine, thumbnail = aiThumbnailEngine,
        audioDescription = audioDescriptionEngine, stylusMidi = stylusMidiEngine,
        audioEngine = audioEngine, videoEngine = videoEngine
    )

    // Whisper model state (exposed via delegate for UI binding)
    val whisperModelState get() = aiToolsDelegate.whisperModelState
    val whisperDownloadProgress get() = aiToolsDelegate.whisperDownloadProgress
    val segmentationModelState get() = aiToolsDelegate.segmentationModelState
    val segmentationDownloadProgress get() = aiToolsDelegate.segmentationDownloadProgress

    // LUT picker state (exposed via delegate)
    val showLutPicker get() = colorGradingDelegate.showLutPicker

    // Snap-to-beat / snap-to-marker (driven by user settings)
    private val _snapToBeat = MutableStateFlow(false)
    private val _snapToMarker = MutableStateFlow(true)
    val snapToBeat: Boolean get() = _snapToBeat.value
    val snapToMarker: Boolean get() = _snapToMarker.value

    // v3.69 layout-mode inputs surfaced as StateFlows so Compose can observe.
    private val _oneHandedMode = MutableStateFlow(false)
    val oneHandedMode: StateFlow<Boolean> = _oneHandedMode.asStateFlow()
    private val _desktopOverride =
        MutableStateFlow(com.novacut.editor.engine.DesktopOverride.AUTO)
    val desktopOverride: StateFlow<com.novacut.editor.engine.DesktopOverride> =
        _desktopOverride.asStateFlow()

    fun setOneHandedMode(enabled: Boolean) {
        _oneHandedMode.value = enabled
        viewModelScope.launch { settingsRepo.updateOneHandedMode(enabled) }
    }

    fun setDesktopOverride(value: com.novacut.editor.engine.DesktopOverride) {
        _desktopOverride.value = value
        viewModelScope.launch { settingsRepo.updateDesktopOverride(value) }
    }

    // Confirm-before-delete / show-waveforms (driven by user settings)
    private val _confirmBeforeDelete = MutableStateFlow(true)
    private val _showWaveforms = MutableStateFlow(true)
    val confirmBeforeDelete: Boolean get() = _confirmBeforeDelete.value
    val showWaveforms: Boolean get() = _showWaveforms.value

    // Stored outside EditorState to avoid recomposition on every resize
    @Volatile
    private var timelineWidthPx: Float = 0f
    private val waveformLoadJobs = mutableMapOf<String, Job>()
    private var gapPlaybackJob: Job? = null

    private fun visibleTimelineDurationMs(state: EditorState = _state.value): Long? {
        if (timelineWidthPx <= 0f) return null
        val pixelsPerMs = (state.zoomLevel * TIMELINE_BASE_SCALE).coerceAtLeast(0.001f)
        return (timelineWidthPx / pixelsPerMs).roundToLong().coerceAtLeast(1L)
    }

    private fun maxTimelineScrollOffset(state: EditorState = _state.value): Long {
        val totalDurationMs = state.totalDurationMs.coerceAtLeast(0L)
        if (totalDurationMs == 0L) return 0L

        val visibleDurationMs = visibleTimelineDurationMs(state) ?: return totalDurationMs
        val leadOutPaddingMs = (visibleDurationMs / 4L).coerceIn(750L, 6_000L)
        val minVisibleContentMs = (visibleDurationMs - leadOutPaddingMs)
            .coerceAtLeast((visibleDurationMs / 2L).coerceAtLeast(1L))
        return (totalDurationMs - minVisibleContentMs).coerceAtLeast(0L)
    }

    private fun clampTimelineScrollOffset(offsetMs: Long, state: EditorState = _state.value): Long {
        return offsetMs.coerceIn(0L, maxTimelineScrollOffset(state))
    }

    // True until fitTimelineToWindow has been applied at least once for this session.
    // First layout with content should auto-fit so users immediately see the whole
    // project rather than having to pinch-zoom out.
    private var pendingInitialFit: Boolean = true

    fun setTimelineWidth(widthPx: Float) {
        val wasZero = timelineWidthPx <= 0f
        timelineWidthPx = widthPx
        _state.update { state ->
            val clampedScrollOffsetMs = clampTimelineScrollOffset(state.scrollOffsetMs, state)
            if (clampedScrollOffsetMs == state.scrollOffsetMs) {
                state
            } else {
                state.copy(scrollOffsetMs = clampedScrollOffsetMs)
            }
        }
        preloadVisibleWaveforms()
        // Auto-fit on first layout after content is loaded. We defer the fit until we
        // both know the timeline width AND there is actual content to frame. This
        // means opening a project goes: (1) ViewModel boots empty, (2) setTimelineWidth
        // arrives with width>0, (3) Room+autosave restore populates tracks, (4) the
        // NEXT setTimelineWidth call (or the first one if content beat layout) fires
        // the fit. A small deferred launch re-checks after the state write settles.
        if (wasZero && widthPx > 0f && pendingInitialFit && _state.value.totalDurationMs > 0L) {
            pendingInitialFit = false
            fitTimelineToWindow()
        }
    }

    internal fun requestInitialFitIfNeeded() {
        if (pendingInitialFit && timelineWidthPx > 0f && _state.value.totalDurationMs > 0L) {
            pendingInitialFit = false
            fitTimelineToWindow()
        }
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
                val hadContent = recovery.tracks.any { it.clips.isNotEmpty() } ||
                    recovery.textOverlays.isNotEmpty() ||
                    recovery.imageOverlays.isNotEmpty()
                _state.update {
                    it.copy(
                        tracks = recovery.tracks.ifEmpty { it.tracks },
                        textOverlays = recovery.textOverlays,
                        imageOverlays = recovery.imageOverlays,
                        timelineMarkers = recovery.timelineMarkers,
                        drawingPaths = recovery.drawingPaths,
                        playheadMs = recovery.playheadMs,
                        chapterMarkers = recovery.chapterMarkers,
                        beatMarkers = recovery.beatMarkers,
                        v369 = it.v369.copy(transcript = recovery.transcript ?: it.v369.transcript),
                        totalDurationMs = recovery.tracks.maxOfOrNull { t ->
                            t.clips.maxOfOrNull { c -> c.timelineEndMs } ?: 0L
                        } ?: 0L,
                        // Surface a dialog so the user knows auto-save recovered work — they can
                        // either keep the recovered state (default, already applied) or ack and
                        // discard the on-disk recovery file.
                        panels = if (hadContent) it.panels.open(PanelId.RECOVERY_DIALOG) else it.panels
                    )
                }
                _playheadMs.value = recovery.playheadMs
                if (recovery.tracks.flatMap { it.clips }.isNotEmpty()) {
                    rebuildPlayerTimeline()
                }
                preloadVisibleWaveforms(_state.value)
                // Restored content may have arrived AFTER the timeline laid out with
                // zero clips. In that race the first setTimelineWidth call saw an
                // empty project and skipped the fit. Fire now so the user opens a
                // restored project to the whole timeline framed, not a tiny window.
                requestInitialFitIfNeeded()
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

        // Periodic playhead sync (~30fps) with smooth auto-scroll + per-clip speed tracking
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

                    // Compute auto-scroll every frame for smooth following
                    val widthPx = timelineWidthPx
                    val s = _state.value
                    val pixelsPerMs = s.zoomLevel * 0.15f
                    var newScroll = s.scrollOffsetMs
                    if (widthPx > 0 && pixelsPerMs >= 0.001f) {
                        val visibleMs = (widthPx / pixelsPerMs).toLong()
                        val playheadRelative = currentMs - newScroll
                        if (playheadRelative > visibleMs * 0.8f || playheadRelative < 0) {
                            // Smooth scroll: lerp toward target instead of jumping
                            val targetScroll = (currentMs - visibleMs / 4).coerceAtLeast(0L)
                            newScroll = newScroll + ((targetScroll - newScroll) * 0.15f).toLong()
                        }
                    }
                    newScroll = clampTimelineScrollOffset(newScroll, s)

                    // Push `_state` updates only when scroll moved OR playhead has
                    // drifted at least 200ms from the last sync. Consumers of the
                    // flow that care about live playhead (the timeline render) read
                    // `_playheadMs` directly — the dedicated unboxed-Long flow that
                    // we update every tick above. The 200ms threshold still keeps
                    // `state.playheadMs` fresh enough for user-triggered ops like
                    // `splitClipAtPlayhead` and auto-save, while cutting state.copy
                    // broadcasts from ~6/sec to ~5/sec during playback. Previously
                    // a new EditorState was constructed and emitted on every 5th
                    // tick unconditionally, invalidating every Compose subscriber.
                    val playheadDriftMs = kotlin.math.abs(currentMs - s.playheadMs)
                    if (newScroll != s.scrollOffsetMs || playheadDriftMs >= 200L) {
                        _state.update { st ->
                            st.copy(playheadMs = currentMs, scrollOffsetMs = newScroll)
                        }
                    }
                    // Track clip transitions during playback — update speed/effects for current clip
                    val currentIndex = videoEngine.getCurrentClipIndex()
                    if (currentIndex != lastClipIndex) {
                        lastClipIndex = currentIndex
                        updatePreview()
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
                    val quality = when (settings.defaultExportQuality) {
                        "LOW" -> ExportQuality.LOW
                        "MEDIUM" -> ExportQuality.MEDIUM
                        else -> ExportQuality.HIGH
                    }
                    _state.update { s ->
                        s.copy(exportConfig = s.exportConfig.copy(
                            resolution = settings.defaultResolution,
                            frameRate = settings.defaultFrameRate,
                            quality = quality
                        ))
                    }
                }

                // v3.69 layout-mode mirrors. Kept on dedicated StateFlows so
                // Compose doesn't re-read the entire AppSettings snapshot on
                // every unrelated change.
                if (_oneHandedMode.value != settings.oneHandedMode) {
                    _oneHandedMode.value = settings.oneHandedMode
                }
                if (_desktopOverride.value != settings.desktopModeOverride) {
                    _desktopOverride.value = settings.desktopModeOverride
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
                            val state = buildAutoSaveState(s)
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

                // Sync snap settings
                _snapToBeat.value = settings.snapToBeat
                _snapToMarker.value = settings.snapToMarker

                // Sync confirm-before-delete and waveform settings
                _confirmBeforeDelete.value = settings.confirmBeforeDelete
                _showWaveforms.value = settings.showWaveforms
                if (settings.showWaveforms) {
                    preloadVisibleWaveforms(_state.value)
                } else {
                    cancelWaveformLoads()
                }
            }
        }
    }

    /** Rebuild ExoPlayer timeline from current tracks. Call after any clip mutation. */
    private fun rebuildPlayerTimeline() {
        cancelGapPlayback()
        _state.update(::normalizeTimelineState)
        _playheadMs.value = _state.value.playheadMs
        videoEngine.prepareTimeline(_state.value.tracks)
        videoEngine.seekTo(_state.value.playheadMs)
        updatePreview()
        preloadVisibleWaveforms(_state.value)
    }

    private fun preloadVisibleWaveforms(state: EditorState = _state.value) {
        if (!_showWaveforms.value) return
        val loadWindow = visibleWaveformWindow(state)
        state.tracks
            .asSequence()
            .filter { it.type == TrackType.AUDIO && it.showWaveform }
            .flatMap { it.clips.asSequence() }
            .filter { clip ->
                clip.timelineStartMs <= loadWindow.last && clip.timelineEndMs >= loadWindow.first
            }
            .forEach { clip ->
                enqueueWaveformLoad(clip.id, clip.sourceUri)
            }
    }

    private fun visibleWaveformWindow(state: EditorState): LongRange {
        if (timelineWidthPx > 0f) {
            val pixelsPerMs = (state.zoomLevel * TIMELINE_BASE_SCALE).coerceAtLeast(0.001f)
            val visibleDurationMs = (timelineWidthPx / pixelsPerMs).roundToLong().coerceAtLeast(1L)
            val startMs = (state.scrollOffsetMs - WAVEFORM_PRELOAD_PADDING_MS).coerceAtLeast(0L)
            val endMs = state.scrollOffsetMs + visibleDurationMs + WAVEFORM_PRELOAD_PADDING_MS
            return startMs..endMs
        }

        val fallbackCenterMs = maxOf(state.scrollOffsetMs, _playheadMs.value)
        val startMs = (fallbackCenterMs - WAVEFORM_FALLBACK_WINDOW_MS).coerceAtLeast(0L)
        val endMs = fallbackCenterMs + WAVEFORM_FALLBACK_WINDOW_MS
        return startMs..endMs
    }

    private fun enqueueWaveformLoad(clipId: String, sourceUri: Uri) {
        if (_state.value.waveforms.containsKey(clipId)) return
        if (waveformLoadJobs[clipId]?.isActive == true) return

        waveformLoadJobs[clipId] = viewModelScope.launch {
            try {
                val waveform = audioEngine.extractWaveform(sourceUri).toList()
                var shouldRefreshSuggestion = false
                _state.update { state ->
                    val clipStillExists = state.tracks.any { track ->
                        track.clips.any { clip -> clip.id == clipId }
                    }
                    if (!clipStillExists || state.waveforms.containsKey(clipId)) {
                        state
                    } else {
                        shouldRefreshSuggestion = state.selectedClipId == clipId
                        state.copy(waveforms = state.waveforms + (clipId to waveform))
                    }
                }
                if (shouldRefreshSuggestion) {
                    generateAiSuggestion(clipId)
                }
            } catch (e: Exception) {
                Log.w("EditorViewModel", "Waveform extraction failed for $clipId", e)
            } finally {
                waveformLoadJobs.remove(clipId)
            }
        }
    }

    private fun cancelWaveformLoads(clipIds: Set<String>? = null) {
        val iterator = waveformLoadJobs.iterator()
        while (iterator.hasNext()) {
            val (clipId, job) = iterator.next()
            if (clipIds == null || clipId in clipIds) {
                job.cancel()
                iterator.remove()
            }
        }
    }

    /** Apply the current preview segment's effects and playback settings. */
    private fun updatePreview() {
        val clip = videoEngine.getPreviewClipAt(videoEngine.getCurrentClipIndex())
        val track = clip?.let { previewTrackForClip(it.id) }
        val trackVolume = if (track != null && !isTrackAudibleInPreview(track)) {
            0f
        } else {
            safeEditorFloat(track?.volume ?: 1f, 1f, 0f, 2f)
        }
        videoEngine.applyPreviewEffects(clip)
        videoEngine.setPreviewSpeed(safeEditorFloat(clip?.speed ?: 1f, 1f, 0.01f, 100f))
        videoEngine.setPreviewVolume(safeEditorFloat((clip?.volume ?: 1f) * trackVolume, 1f, 0f, 1f))
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
    fun addClipToTrack(uri: Uri, trackType: TrackType = TrackType.VIDEO) {
        setTool(EditorTool.NONE)
        clipEditingDelegate.addClipToTrack(uri, trackType)
    }
    fun relinkMedia(oldUri: Uri, newUri: Uri) = clipEditingDelegate.relinkMedia(oldUri, newUri)
    fun selectClip(clipId: String?, trackId: String? = null) {
        clipEditingDelegate.selectClip(clipId, trackId)
        generateAiSuggestion(clipId)
    }
    fun deleteSelectedClip() = clipEditingDelegate.deleteSelectedClip()
    fun duplicateSelectedClip() = clipEditingDelegate.duplicateSelectedClip()
    fun mergeWithNextClip() = clipEditingDelegate.mergeWithNextClip()
    fun splitClipAtPlayhead() = clipEditingDelegate.splitClipAtPlayhead()
    fun beginTrim() = clipEditingDelegate.beginTrim()
    fun trimClip(clipId: String, newTrimStartMs: Long? = null, newTrimEndMs: Long? = null) = clipEditingDelegate.trimClip(clipId, newTrimStartMs, newTrimEndMs)
    fun endTrim() = clipEditingDelegate.endTrim()
    fun beginSpeedChange() = clipEditingDelegate.beginSpeedChange()
    fun setClipSpeed(clipId: String, speed: Float) = clipEditingDelegate.setClipSpeed(clipId, speed)
    fun endSpeedChange() = clipEditingDelegate.endSpeedChange()
    fun setClipReversed(clipId: String, reversed: Boolean) = clipEditingDelegate.setClipReversed(clipId, reversed)
    fun reorderClip(clipId: String, targetIndex: Int) = clipEditingDelegate.reorderClip(clipId, targetIndex)
    fun moveClipToTrack(clipId: String, targetTrackId: String) = clipEditingDelegate.moveClipToTrack(clipId, targetTrackId)
    fun splitAtPlayhead() = splitClipAtPlayhead()

    fun copyClipEffects() {
        val state = _state.value
        val selectedId = state.selectedClipId ?: return
        val clip = state.tracks.flatMap { it.clips }.find { it.id == selectedId } ?: return
        if (clip.effects.isEmpty()) return
        _state.update { it.copy(copiedEffects = clip.effects) }
    }

    fun pasteClipEffects() {
        val state = _state.value
        val selectedId = state.selectedClipId ?: return
        if (state.copiedEffects.isEmpty()) return
        saveUndoState("Paste effects")
        // Generate new effect IDs OUTSIDE the _state.update {} closure so that a CAS retry
        // doesn't allocate a fresh UUID set on each attempt. Without this, intermediate
        // closure executions would mint different IDs than the final committed state — fine
        // for in-state consistency but bad for any logging/snapshot observer that captures
        // the first attempt.
        val freshEffects = state.copiedEffects.map { it.copy(id = java.util.UUID.randomUUID().toString()) }
        _state.update { s ->
            s.copy(tracks = s.tracks.map { track ->
                track.copy(clips = track.clips.map { clip ->
                    if (clip.id == selectedId) clip.copy(effects = freshEffects)
                    else clip
                })
            })
        }
        saveProject()
    }

    fun setClipLabel(clipId: String, label: ClipLabel) {
        saveUndoState("Change clip label")
        _state.update { state ->
            state.copy(tracks = state.tracks.map { track ->
                track.copy(clips = track.clips.map { clip ->
                    if (clip.id == clipId) clip.copy(clipLabel = label) else clip
                })
            })
        }
        rebuildTimeline()
        saveProject()
    }

    // --- Effects & Transitions (delegated) ---
    fun addEffect(clipId: String, effect: Effect) = effectsDelegate.addEffect(clipId, effect)
    fun beginEffectAdjust() = effectsDelegate.beginEffectAdjust()
    fun endEffectAdjust() = effectsDelegate.endEffectAdjust()
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
        saveProject()
    }

    fun toggleTrackMute(trackId: String) {
        _state.update { state ->
            val tracks = state.tracks.map { track ->
                if (track.id == trackId) track.copy(isMuted = !track.isMuted) else track
            }
            state.copy(tracks = tracks)
        }
        rebuildPlayerTimeline()
        saveProject()
    }

    fun toggleTrackVisibility(trackId: String) {
        _state.update { state ->
            val tracks = state.tracks.map { track ->
                if (track.id == trackId) track.copy(isVisible = !track.isVisible) else track
            }
            state.copy(tracks = tracks)
        }
        rebuildPlayerTimeline()
        saveProject()
    }

    fun toggleTrackLock(trackId: String) {
        _state.update { state ->
            val tracks = state.tracks.map { track ->
                if (track.id == trackId) track.copy(isLocked = !track.isLocked) else track
            }
            state.copy(tracks = tracks)
        }
        saveProject()
    }

    // Playback
    fun togglePlayPause() = togglePlayback()
    fun togglePlayback() {
        if (gapPlaybackJob?.isActive == true) {
            cancelGapPlayback()
            _state.update { it.copy(isPlaying = false) }
        } else if (videoEngine.isPlaying()) {
            videoEngine.pause()
            _state.update { it.copy(isPlaying = false) }
        } else {
            val playhead = _playheadMs.value
            val currentPreviewClip = previewClipAtPosition(playhead)
            if (currentPreviewClip == null && _state.value.totalDurationMs > playhead) {
                startGapPlayback(playhead)
            } else {
                videoEngine.play()
                _state.update { it.copy(isPlaying = true) }
            }
        }
    }

    fun toggleLoop() {
        val newLooping = !_state.value.isLooping
        videoEngine.getPlayer()?.repeatMode = if (newLooping)
            Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
        _state.update { it.copy(isLooping = newLooping) }
    }

    private var isScrubbing = false
    private var scrubSeekJob: kotlinx.coroutines.Job? = null

    fun seekTo(positionMs: Long) {
        cancelGapPlayback()
        val clamped = positionMs.coerceIn(0L, _state.value.totalDurationMs.coerceAtLeast(0L))
        _playheadMs.value = clamped
        if (isScrubbing) {
            // During scrub: debounce ExoPlayer seeks to every 80ms, skip full state copy
            scrubSeekJob?.cancel()
            scrubSeekJob = viewModelScope.launch {
                kotlinx.coroutines.delay(80)
                videoEngine.seekTo(clamped)
            }
            return
        }
        videoEngine.seekTo(clamped)
        _state.update { it.copy(playheadMs = clamped) }
        if (_state.value.panels.isOpen(PanelId.SCOPES)) updateScopeFrame()
    }

    /** Enable scrubbing mode during timeline drag for smoother seeking. */
    fun beginScrub() {
        cancelGapPlayback()
        isScrubbing = true
        videoEngine.setScrubbingMode(true)
    }
    fun endScrub() {
        isScrubbing = false
        scrubSeekJob?.cancel()
        scrubSeekJob = null
        videoEngine.setScrubbingMode(false)
        val pos = _playheadMs.value
        videoEngine.seekTo(pos)
        _state.update { it.copy(playheadMs = pos) }
    }

    fun updatePlayheadPosition(positionMs: Long) {
        _playheadMs.value = positionMs
        if (!isScrubbing) {
            _state.update { it.copy(playheadMs = positionMs) }
        }
    }

    // Zoom
    fun setZoomLevel(zoom: Float) {
        _state.update { state ->
            val updatedState = state.copy(zoomLevel = zoom.coerceIn(MIN_TIMELINE_ZOOM, MAX_TIMELINE_ZOOM))
            updatedState.copy(
                scrollOffsetMs = clampTimelineScrollOffset(updatedState.scrollOffsetMs, updatedState)
            )
        }
        preloadVisibleWaveforms(_state.value)
    }

    fun setScrollOffset(offsetMs: Long) {
        _state.update { state ->
            state.copy(scrollOffsetMs = clampTimelineScrollOffset(offsetMs, state))
        }
        preloadVisibleWaveforms(_state.value)
    }

    /**
     * Compute and apply the zoom level that makes the entire project duration fit
     * inside the current timeline viewport, and reset scroll to zero. Used on first
     * clip add and on project load so the user doesn't open the editor to a timeline
     * that shows only a few seconds of a long video.
     *
     * No-op when the timeline hasn't laid out yet (width=0) or there's no content.
     */
    fun fitTimelineToWindow() {
        val width = timelineWidthPx
        val state = _state.value
        val duration = state.totalDurationMs
        if (width <= 0f || duration <= 0L) return
        // 0.92 leaves ~8% headroom so the last clip doesn't butt up against the edge.
        val fit = (width / duration.toFloat() / TIMELINE_BASE_SCALE * 0.92f)
            .coerceIn(MIN_TIMELINE_ZOOM, MAX_TIMELINE_ZOOM)
        _state.update { s -> s.copy(zoomLevel = fit, scrollOffsetMs = 0L) }
        preloadVisibleWaveforms(_state.value)
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

    private fun normalizeSelectionState(state: EditorState, tracks: List<Track> = state.tracks): EditorState {
        val clipToTrackId = mutableMapOf<String, String>()
        tracks.forEach { track ->
            track.clips.forEach { clip ->
                clipToTrackId[clip.id] = track.id
            }
        }

        val validSelectedIds = state.selectedClipIds.filter { clipToTrackId.containsKey(it) }.toSet()
        val validSelectedClipId = state.selectedClipId?.takeIf { clipToTrackId.containsKey(it) }

        val normalizedSelectedIds = when {
            validSelectedClipId != null && validSelectedIds.isEmpty() -> setOf(validSelectedClipId)
            validSelectedClipId != null && validSelectedIds.size == 1 && validSelectedClipId !in validSelectedIds -> {
                setOf(validSelectedClipId)
            }
            else -> validSelectedIds
        }
        val normalizedSelectedClipId = when {
            validSelectedClipId != null && (normalizedSelectedIds.isEmpty() || validSelectedClipId in normalizedSelectedIds) -> {
                validSelectedClipId
            }
            normalizedSelectedIds.size == 1 -> normalizedSelectedIds.first()
            else -> null
        }
        val normalizedSelectedTrackId = normalizedSelectedClipId?.let { clipToTrackId[it] }

        return if (
            normalizedSelectedIds == state.selectedClipIds &&
            normalizedSelectedClipId == state.selectedClipId &&
            normalizedSelectedTrackId == state.selectedTrackId
        ) {
            state
        } else {
            state.copy(
                selectedClipIds = normalizedSelectedIds,
                selectedClipId = normalizedSelectedClipId,
                selectedTrackId = normalizedSelectedTrackId
            )
        }
    }

    private fun dismissedPanelState(state: EditorState) = normalizeSelectionState(
        state.copy(
            panels = state.panels.closeAll(),
            noiseAnalysisResult = null,
            selectedEffectId = null,
            editingTextOverlayId = null,
            selectedMaskId = null,
            isDrawingMode = false
        )
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
    fun beginVolumeAdjust() = audioMixerDelegate.beginVolumeAdjust()
    fun endVolumeAdjust() = audioMixerDelegate.endVolumeAdjust()
    fun setTrackVolume(trackId: String, volume: Float) = audioMixerDelegate.setTrackVolume(trackId, volume)
    fun beginPanAdjust() = audioMixerDelegate.beginPanAdjust()
    fun endPanAdjust() = audioMixerDelegate.endPanAdjust()
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
        saveProject()
    }

    fun deleteKeyframe(keyframe: Keyframe) {
        if (_state.value.selectedClipId == null) return
        saveUndoState("Delete keyframe")
        updateSelectedClip { clip ->
            clip.copy(keyframes = clip.keyframes.filter {
                !(it.timeOffsetMs == keyframe.timeOffsetMs && it.property == keyframe.property && it.value == keyframe.value)
            })
        }
        saveProject()
    }

    // --- Speed Curve ---
    fun showSpeedCurveEditor() = showPanel(PanelId.SPEED_CURVE)
    fun hideSpeedCurveEditor() = hidePanel(PanelId.SPEED_CURVE)

    fun setClipSpeedCurve(speedCurve: SpeedCurve?) {
        if (_state.value.selectedClipId == null) return
        saveUndoState("Speed curve")
        updateSelectedClip { it.copy(speedCurve = speedCurve) }
        rebuildPlayerTimeline()
        saveProject()
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
        saveProject()
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
        saveProject()
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
        saveProject()
    }

    fun setTrackBlendMode(trackId: String, blendMode: BlendMode) {
        saveUndoState("Track blend mode")
        _state.update { s ->
            s.copy(tracks = s.tracks.map { track ->
                if (track.id == trackId) track.copy(blendMode = blendMode) else track
            })
        }
        rebuildPlayerTimeline()
        saveProject()
    }

    fun setTrackOpacity(trackId: String, opacity: Float) {
        _state.update { s ->
            s.copy(tracks = s.tracks.map { track ->
                if (track.id == trackId) track.copy(opacity = opacity.coerceIn(0f, 1f)) else track
            })
        }
        rebuildPlayerTimeline()
        saveProject()
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
        saveProject()
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
        saveProject()
    }

    // --- Captions ---
    fun addCaption(caption: Caption) {
        if (_state.value.selectedClipId == null) return
        saveUndoState("Add caption")
        updateSelectedClip { it.copy(captions = it.captions + caption) }
        saveProject()
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
        saveProject()
    }

    // --- Project Snapshots ---
    fun createSnapshot(label: String = "") {
        val s = _state.value
        val autoSaveState = buildAutoSaveState(s)
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
        try {
            val recovery = AutoSaveState.deserialize(snapshot.stateJson)
            saveUndoState("Restore snapshot")
            _state.update {
                it.copy(
                    tracks = recovery.tracks,
                    textOverlays = recovery.textOverlays,
                    imageOverlays = recovery.imageOverlays,
                    timelineMarkers = recovery.timelineMarkers,
                    drawingPaths = recovery.drawingPaths,
                    playheadMs = recovery.playheadMs,
                    chapterMarkers = recovery.chapterMarkers
                )
            }
            _playheadMs.value = recovery.playheadMs
            rebuildPlayerTimeline()
            saveProject()
            showToast(
                appContext.getString(
                    R.string.snapshot_restored_success,
                    snapshot.label.ifEmpty { appContext.getString(R.string.panel_snapshot_untitled) }
                )
            )
        } catch (e: Exception) {
            Log.w("EditorViewModel", "Snapshot restore failed for ${snapshot.id}", e)
            showToast(appContext.getString(R.string.snapshot_restore_failed))
        }
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

    // --- Project Backup ---
    fun showCloudBackup() = showPanel(PanelId.CLOUD_BACKUP)
    fun hideCloudBackup() = hidePanel(PanelId.CLOUD_BACKUP)

    private val _backupEstimatedSize = MutableStateFlow(0L)
    val backupEstimatedSize: StateFlow<Long> = _backupEstimatedSize.asStateFlow()
    private val _lastBackupTime = MutableStateFlow<Long?>(null)
    val lastBackupTime: StateFlow<Long?> = _lastBackupTime.asStateFlow()
    private val _isExportingBackup = MutableStateFlow(false)
    val isExportingBackup: StateFlow<Boolean> = _isExportingBackup.asStateFlow()
    private val _isImportingBackup = MutableStateFlow(false)
    val isImportingBackup: StateFlow<Boolean> = _isImportingBackup.asStateFlow()

    fun estimateBackupSize() {
        viewModelScope.launch(Dispatchers.IO) {
            val size = com.novacut.editor.engine.ProjectArchive.estimateArchiveSize(
                appContext,
                buildAutoSaveState(_state.value)
            )
            _backupEstimatedSize.value = size
        }
    }

    private fun writeBackupToDownloads(sourceFile: File, fileName: String): String {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val resolver = appContext.contentResolver
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(android.provider.MediaStore.Downloads.MIME_TYPE, "application/zip")
                put(
                    android.provider.MediaStore.Downloads.RELATIVE_PATH,
                    "${android.os.Environment.DIRECTORY_DOWNLOADS}/NovaCut"
                )
                put(android.provider.MediaStore.Downloads.IS_PENDING, 1)
            }
            val contentUri = resolver.insert(
                android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                values
            ) ?: throw IllegalStateException("Could not create backup destination")
            try {
                resolver.openOutputStream(contentUri)?.use { output ->
                    sourceFile.inputStream().use { input -> input.copyTo(output) }
                } ?: throw IllegalStateException("Could not open backup destination")
                values.clear()
                values.put(android.provider.MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(contentUri, values, null, null)
                fileName
            } catch (e: Exception) {
                resolver.delete(contentUri, null, null)
                throw e
            }
        } else {
            val downloadsRoot = appContext.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
                ?: File(appContext.filesDir, "downloads")
            val backupDir = File(downloadsRoot, "NovaCut").apply { mkdirs() }
            val destination = File(backupDir, fileName)
            sourceFile.copyTo(destination, overwrite = true)
            destination.name
        }
    }

    fun exportProjectBackup() {
        if (_isExportingBackup.value || _isImportingBackup.value) {
            showToast("Backup action already in progress")
            return
        }
        _isExportingBackup.value = true
        viewModelScope.launch {
            try {
                val s = _state.value
                val fileName = "${sanitizedProjectFileStem(s.project.name)}.novacut"
                val savedName = withContext(Dispatchers.IO) {
                    val tempDir = File(appContext.cacheDir, "backup_exports").apply { mkdirs() }
                    val tempFile = File(tempDir, fileName)
                    try {
                        val success = com.novacut.editor.engine.ProjectArchive.exportArchive(
                            context = appContext,
                            state = buildAutoSaveState(s),
                            outputFile = tempFile
                        )
                        if (!success) return@withContext null
                        writeBackupToDownloads(tempFile, fileName)
                    } finally {
                        tempFile.delete()
                    }
                }
                if (savedName != null) {
                    _lastBackupTime.value = System.currentTimeMillis()
                    showToast("Backup saved: $savedName")
                } else {
                    showToast("Backup export failed")
                }
            } catch (e: Exception) {
                showToast("Backup failed: ${e.message}")
            } finally {
                _isExportingBackup.value = false
            }
        }
    }

    fun importProjectBackup(uri: Uri) {
        if (_isExportingBackup.value || _isImportingBackup.value) {
            showToast("Backup action already in progress")
            return
        }
        _isImportingBackup.value = true
        viewModelScope.launch {
            try {
                showToast("Importing backup...")
                val targetDir = File(appContext.filesDir, "imported_${System.currentTimeMillis()}")
                val state = com.novacut.editor.engine.ProjectArchive.importArchive(appContext, uri, targetDir)
                if (state != null) {
                    saveUndoState("Import backup")
                    _state.update { s ->
                        dismissedPanelState(
                            recalculateDuration(
                                s.copy(
                                    tracks = state.tracks,
                                    textOverlays = state.textOverlays,
                                    imageOverlays = state.imageOverlays,
                                    timelineMarkers = state.timelineMarkers,
                                    chapterMarkers = state.chapterMarkers,
                                    drawingPaths = state.drawingPaths,
                                    beatMarkers = state.beatMarkers,
                                    v369 = s.v369.copy(
                                        transcript = state.transcript,
                                        selectedWordIndices = emptySet()
                                    ),
                                    playheadMs = state.playheadMs
                                )
                            )
                        )
                    }
                    _playheadMs.value = _state.value.playheadMs
                    rebuildPlayerTimeline()
                    saveProject()
                    showToast("Backup imported successfully")
                } else {
                    showToast("Failed to import backup")
                }
            } catch (e: Exception) {
                showToast("Import failed: ${e.message}")
            } finally {
                _isImportingBackup.value = false
            }
        }
    }

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
            drawingPaths = target.drawingPaths,
            undoStack = stack.take(index),
            redoStack = listOf(UndoAction(
                "Current",
                it.tracks,
                it.textOverlays,
                imageOverlays = it.imageOverlays.toList(),
                timelineMarkers = it.timelineMarkers.toList(),
                chapterMarkers = it.chapterMarkers.toList(),
                drawingPaths = it.drawingPaths.toList(),
                playheadMs = _playheadMs.value
            )) + stack.drop(index + 1),
            playheadMs = target.playheadMs.coerceIn(0L, it.totalDurationMs.coerceAtLeast(0L))
        ) }
        _playheadMs.value = _state.value.playheadMs
        rebuildTimeline()
        showToast("Restored: ${target.description}")
    }

    // --- Marker List ---
    fun showMarkerList() = showPanel(PanelId.MARKER_LIST)
    fun hideMarkerList() = hidePanel(PanelId.MARKER_LIST)
    fun updateMarkerLabel(markerId: String, label: String) {
        _state.update { state ->
            state.copy(timelineMarkers = state.timelineMarkers.map {
                if (it.id == markerId) it.copy(label = label) else it
            })
        }
        saveProject()
    }

    // --- Track Header Enhancements ---
    fun toggleTrackWaveform(trackId: String) {
        _state.update { state ->
            state.copy(tracks = state.tracks.map {
                if (it.id == trackId) it.copy(showWaveform = !it.showWaveform) else it
            })
        }
        preloadVisibleWaveforms(_state.value)
        saveProject()
    }
    fun setTrackHeight(trackId: String, height: Int) {
        _state.update { state ->
            state.copy(tracks = state.tracks.map {
                if (it.id == trackId) it.copy(trackHeight = height.coerceIn(32, 120)) else it
            })
        }
        saveProject()
    }
    fun toggleTrackCollapsed(trackId: String) {
        _state.update { state ->
            state.copy(tracks = state.tracks.map {
                if (it.id == trackId) it.copy(isCollapsed = !it.isCollapsed) else it
            })
        }
        saveProject()
    }
    fun collapseAllTracks() {
        _state.update { state ->
            state.copy(tracks = state.tracks.map { it.copy(isCollapsed = true) })
        }
        saveProject()
    }
    fun expandAllTracks() {
        _state.update { state ->
            state.copy(tracks = state.tracks.map { it.copy(isCollapsed = false) })
        }
        saveProject()
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
        saveProject()
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
        saveProject()
        showToast("Split $splitCount clips at beat markers")
        hideBeatSync()
    }
    fun tapBeatMarker() {
        val currentMs = _playheadMs.value
        var changed = false
        _state.update { s ->
            val existing = s.beatMarkers
            val tooClose = existing.any { kotlin.math.abs(it - currentMs) < 50L }
            if (tooClose) s else {
                changed = true
                s.copy(beatMarkers = (existing + currentMs).sorted())
            }
        }
        if (changed) saveProject()
    }
    fun clearBeatMarkers() {
        _state.update { it.copy(beatMarkers = emptyList()) }
        saveProject()
    }

    // --- AI Suggestions ---
    fun dismissAiSuggestion() {
        _state.update { it.copy(aiSuggestion = null) }
    }
    private fun generateAiSuggestion(clipId: String?) {
        if (clipId == null) {
            _state.update { it.copy(aiSuggestion = null) }
            return
        }
        val s = _state.value
        val clip = s.tracks.flatMap { it.clips }.firstOrNull { it.id == clipId } ?: return
        val clipHasVisual = clipHasVisual(clip)
        val clipHasAudio = clipHasAudio(clip)
        if (clipHasAudio && !s.waveforms.containsKey(clip.id)) {
            enqueueWaveformLoad(clip.id, clip.sourceUri)
        }
        val suggestion: AiSuggestion? = when {
            // Color-correction suggestion removed per user request — firing an
            // unsolicited "this clip could use color correction" banner every
            // time a long visual clip was selected was noise, not signal.
            // Users can still trigger auto-color from the AI tools panel.
            s.tracks.filter { it.type == TrackType.VIDEO }.flatMap { it.clips }.size > 3 &&
                s.tracks.flatMap { it.clips }.none { it.transition != null } ->
                AiSuggestion(
                    id = "add_transitions_${clip.id}",
                    message = "Add transitions between your clips",
                    actionId = "transition"
                )
            else -> {
                val waveform = s.waveforms[clip.id]
                if (waveform != null && waveform.size > 10) {
                    val peak = waveform.maxOf { kotlin.math.abs(it) }
                    val avg = waveform.map { kotlin.math.abs(it) }.average().toFloat()
                    val variance = waveform.map { val d = kotlin.math.abs(it) - avg; d * d }.average().toFloat()
                    if (clipHasAudio && peak > 0.01f && variance < 0.005f) AiSuggestion(
                        id = "denoise_${clip.id}",
                        message = "Low audio variance detected - try Denoise",
                        actionId = "denoise"
                    ) else null
                } else null
            }
        }
        _state.update { it.copy(aiSuggestion = suggestion) }
    }

    // --- Smart Reframe ---
    fun showSmartReframe() = showPanel(PanelId.SMART_REFRAME)
    fun hideSmartReframe() = hidePanel(PanelId.SMART_REFRAME)
    fun applySmartReframe(targetAspect: AspectRatio) {
        _state.update { it.copy(isReframing = true) }
        viewModelScope.launch {
            try {
                // Analyze video for subject positions
                val reframeSourceClip = getSelectedClip()?.takeIf(::clipHasVisual)
                    ?: _state.value.tracks
                        .flatMap { it.clips }
                        .firstOrNull(::clipHasVisual)
                if (reframeSourceClip != null) {
                    val config = SmartReframeEngine.ReframeConfig(
                        targetAspectRatio = targetAspect.toFloat()
                    )
                    smartReframeEngine.analyzeForReframe(reframeSourceClip.sourceUri, config) { progress ->
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
        saveProject()
        showToast("Speed preset applied")
        hideSpeedPresets()
    }

    // --- Filler/Silence Removal ---
    fun showFillerRemoval() = showPanel(PanelId.FILLER_REMOVAL)
    fun hideFillerRemoval() = hidePanel(PanelId.FILLER_REMOVAL)

    fun analyzeFillers() {
        val clip = getSelectedClip() ?: return
        if (!clipHasAudio(clip)) {
            showToast("Selected clip has no audio to analyze")
            return
        }
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
            val timelineOffset = currentClip.sourceTimeToTimelineOffsetMs(region.startMs, includeBoundaries = false)
                ?: continue
            val timelinePos = currentClip.timelineStartMs + timelineOffset
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
        // Close gaps left by removed filler clips (ripple delete)
        _state.update { s ->
            val tracks = s.tracks.map { track ->
                val sorted = track.clips.sortedBy { it.timelineStartMs }
                var nextStartMs = 0L
                val rippled = sorted.map { clip ->
                    if (clip.timelineStartMs > nextStartMs) {
                        val shifted = clip.copy(timelineStartMs = nextStartMs)
                        nextStartMs += shifted.durationMs
                        shifted
                    } else {
                        nextStartMs = clip.timelineStartMs + clip.durationMs
                        clip
                    }
                }
                track.copy(clips = rippled)
            }
            recalculateDuration(s.copy(tracks = tracks))
        }
        rebuildTimeline()
        saveProject()
        showToast("Removed ${regions.size} filler regions")
        hideFillerRemoval()
    }

    // --- Auto-Edit ---
    fun showAutoEdit() = showPanel(PanelId.AUTO_EDIT)
    fun hideAutoEdit() = hidePanel(PanelId.AUTO_EDIT)

    fun runAutoEdit(script: String? = null) {
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

                val result = aiFeatures.generateAutoEdit(autoClips, musicUri, targetMs, script)

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
                    saveProject()
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
                // Helper now performs rebuildPlayerTimeline() + saveProject() internally.
                addClipToTrack(uri, durationMs, TrackType.AUDIO)
                showToast("Voice added to audio track")
                hideTts()
            } else {
                showToast("TTS synthesis failed")
            }
        }
    }

    fun previewTts(text: String, style: com.novacut.editor.engine.TtsEngine.VoiceStyle) {
        viewModelScope.launch { ttsEngine.preview(text, style) }
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
                saveProject()
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

    // --- Drawing Overlay ---
    fun showDrawingMode() {
        pauseIfPlaying()
        _state.update { dismissedPanelState(it).copy(
            panels = it.panels.closeAll().open(PanelId.DRAWING),
            isDrawingMode = true
        ) }
    }
    fun hideDrawingMode() {
        _state.update { it.copy(panels = it.panels.close(PanelId.DRAWING), isDrawingMode = false) }
    }
    fun addDrawingPath(path: com.novacut.editor.model.DrawingPath) {
        _state.update { it.copy(drawingPaths = it.drawingPaths + path) }
        saveProject()
    }
    fun clearDrawing() {
        saveUndoState("Clear drawing")
        _state.update { it.copy(drawingPaths = emptyList()) }
        saveProject()
    }
    fun undoLastPath() {
        if (_state.value.drawingPaths.isEmpty()) return
        saveUndoState("Undo drawing path")
        _state.update { it.copy(drawingPaths = it.drawingPaths.dropLast(1)) }
        saveProject()
    }
    fun setDrawingColor(color: Long) {
        _state.update { it.copy(drawingColor = color) }
    }
    fun setDrawingStrokeWidth(width: Float) {
        _state.update { it.copy(drawingStrokeWidth = width) }
    }

    // --- Multi-Cam ---
    fun showMultiCam() = showPanel(PanelId.MULTI_CAM)
    fun hideMultiCam() = hidePanel(PanelId.MULTI_CAM)
    fun switchMultiCamAngle(clipId: String) {
        val s = _state.value
        val videoTracks = s.tracks.filter { it.type == TrackType.VIDEO }
        if (videoTracks.isEmpty()) return
        val primaryTrack = videoTracks.first()
        val sourceTrack = videoTracks.find { track -> track.clips.any { it.id == clipId } } ?: return
        if (sourceTrack.id == primaryTrack.id) {
            selectClip(clipId, primaryTrack.id)
            return
        }
        val clip = sourceTrack.clips.find { it.id == clipId } ?: return
        saveUndoState("Switch multi-cam angle")
        _state.update { st ->
            val updatedTracks = st.tracks.map { track ->
                when (track.id) {
                    primaryTrack.id -> track.copy(clips = listOf(clip) + track.clips)
                    sourceTrack.id -> track.copy(clips = track.clips.filter { it.id != clipId })
                    else -> track
                }
            }
            recalculateDuration(
                st.copy(
                    tracks = updatedTracks,
                    selectedClipIds = setOf(clipId),
                    selectedClipId = clipId,
                    selectedTrackId = primaryTrack.id
                )
            )
        }
        rebuildPlayerTimeline()
        saveProject()
    }

    fun analyzeAndReduceNoise() {
        val clip = getSelectedClip() ?: return
        if (!clipHasAudio(clip)) {
            showToast("Selected clip has no audio to analyze")
            return
        }
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
                    saveProject()
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

    // Helper: add clip to a track by type (used by TTS / voiceover).
    private fun addClipToTrack(uri: android.net.Uri, durationMs: Long, trackType: TrackType) {
        // Refuse degenerate inputs that would otherwise violate Clip's `trimEndMs <= sourceDurationMs`
        // invariant the moment the user touched the new clip (e.g., a TTS file reporting 0 ms).
        if (durationMs <= 0L) {
            android.util.Log.w("EditorViewModel", "addClipToTrack ignored: non-positive durationMs=$durationMs for $uri")
            return
        }
        val currentTracks = _state.value.tracks
        val track = currentTracks.firstOrNull { it.type == trackType }
            ?: Track(type = trackType, index = currentTracks.size)
        val timelineStart = track.clips.maxOfOrNull { it.timelineEndMs } ?: 0L
        val clipId = UUID.randomUUID().toString()
        val clip = Clip(
            id = clipId,
            sourceUri = uri,
            sourceDurationMs = durationMs,
            timelineStartMs = timelineStart,
            trimEndMs = durationMs
        )
        _state.update { s ->
            val baseTracks = if (s.tracks.any { it.id == track.id }) {
                s.tracks
            } else {
                s.tracks + track
            }
            s.copy(tracks = baseTracks.map { t ->
                if (t.id == track.id) t.copy(clips = t.clips + clip) else t
            })
        }
        // Rebuild the preview so the new TTS / voiceover clip is audible immediately, and
        // persist so an app crash or quick background-then-kill doesn't lose the clip
        // (auto-save is on a 30s timer; without this call, the user would have to wait
        // for the next tick before the new audio is durable).
        rebuildPlayerTimeline()
        saveProject()
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
        val clipIdsToSplit = linkedClipIds(_state.value.tracks, clipId)
        val newIdsByOldId = clipIdsToSplit.associateWith { java.util.UUID.randomUUID().toString() }
        _state.update { s ->
            s.copy(tracks = s.tracks.map { track ->
                if (track.clips.none { it.id in clipIdsToSplit }) return@map track
                val newClips = buildList {
                    track.clips.forEach { clip ->
                        val newId = newIdsByOldId[clip.id]
                        if (newId == null || !canSplitClipAtPosition(clip, positionMs)) {
                            add(clip)
                        } else {
                            val sourcePos = splitPointInSource(clip, positionMs)
                            val trimRange = (clip.trimEndMs - clip.trimStartMs).coerceAtLeast(0L)
                            val splitFraction = if (trimRange > 0L) {
                                ((sourcePos - clip.trimStartMs).toFloat() / trimRange.toFloat()).coerceIn(0f, 1f)
                            } else {
                                0f
                            }
                            add(clip.copy(
                                trimEndMs = sourcePos,
                                transition = null,
                                speedCurve = clip.speedCurve?.restrictTo(0f, splitFraction, trimRange)
                            ))
                            add(
                                clip.copy(
                                    id = newId,
                                    trimStartMs = sourcePos,
                                    timelineStartMs = positionMs,
                                    transition = null,
                                    speedCurve = clip.speedCurve?.restrictTo(splitFraction, 1f, trimRange),
                                    linkedClipId = clip.linkedClipId?.let { linkedId -> newIdsByOldId[linkedId] }
                                )
                            )
                        }
                    }
                }
                track.copy(clips = newClips)
            })
        }
    }

    private fun rebuildTimeline() {
        rebuildPlayerTimeline()
    }

    // --- Multi-Cam Sync ---
    fun syncMultiCamClips() {
        val syncEligibleClips = _state.value.tracks
            .filter { it.type == TrackType.VIDEO }
            .flatMap { it.clips }
            .filter(::clipSupportsAudioSync)
        if (syncEligibleClips.size < 2) {
            showToast("Need at least 2 video clips with audio for multi-cam sync")
            return
        }
        viewModelScope.launch {
            showToast("Syncing clips by audio...")
            try {
                val uris = syncEligibleClips.map { it.sourceUri }
                val referenceUri = uris.first()
                val otherUris = uris.drop(1)
                val results = withContext(Dispatchers.IO) {
                    multiCamEngine.syncMultipleClips(referenceUri, otherUris)
                }
                if (results.isNotEmpty()) {
                    saveUndoState("Multi-cam sync")
                    // Build offset list: first clip stays at 0, rest get offsets from sync results
                    val offsets = listOf(0L) + results.map { it.offsetMs }
                    // Build clip-id-to-offset map using the same order as syncEligibleClips
                    val clipIds = syncEligibleClips.map { it.id }
                    val offsetMap = clipIds.zip(offsets).toMap()
                    _state.update { s ->
                        s.copy(tracks = s.tracks.map { track ->
                            if (track.type == TrackType.VIDEO) {
                                track.copy(clips = track.clips.map { clip ->
                                    val offset = offsetMap[clip.id] ?: 0L
                                    clip.copy(timelineStartMs = (clip.timelineStartMs + offset).coerceAtLeast(0L))
                                })
                            } else track
                        })
                    }
                    rebuildTimeline()
                    saveProject()
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
    private var isSlipEditActive = false
    private var isSlideEditActive = false

    fun beginSlipEdit() {
        if (isSlipEditActive) return
        isSlipEditActive = true
        saveUndoState("Slip edit")
        // Freeze the player while the user drags so we don't rebuild it on every
        // pixel of motion. `setScrubbingMode(true)` lets ExoPlayer skip the expensive
        // seek+decode work; the actual timeline rebuild happens in endSlipEdit.
        videoEngine.setScrubbingMode(true)
    }

    fun endSlipEdit() {
        if (!isSlipEditActive) return
        isSlipEditActive = false
        videoEngine.setScrubbingMode(false)
        rebuildPlayerTimeline()
        saveProject()
    }

    fun beginSlideEdit() {
        if (isSlideEditActive) return
        isSlideEditActive = true
        saveUndoState("Slide edit")
        videoEngine.setScrubbingMode(true)
    }

    fun endSlideEdit() {
        if (!isSlideEditActive) return
        isSlideEditActive = false
        videoEngine.setScrubbingMode(false)
        rebuildPlayerTimeline()
        saveProject()
    }

    fun slipClip(clipId: String, slipAmountMs: Long) {
        val linkedIds = linkedClipIds(_state.value.tracks, clipId)
        if (_state.value.tracks.any { track ->
                track.isLocked && track.clips.any { it.id in linkedIds }
            }
        ) {
            return
        }
        _state.update { s ->
            s.copy(tracks = s.tracks.map { track ->
                track.copy(clips = track.clips.map { clip ->
                    if (clip.id in linkedIds) {
                        val sourceWindow = (clip.trimEndMs - clip.trimStartMs).coerceAtLeast(100L)
                        val maxTrimStart = (clip.sourceDurationMs - sourceWindow).coerceAtLeast(0L)
                        val newTrimStart = (clip.trimStartMs + slipAmountMs).coerceIn(0L, maxTrimStart)
                        val newTrimEnd = newTrimStart + sourceWindow
                        clip.copy(trimStartMs = newTrimStart, trimEndMs = newTrimEnd)
                    } else clip
                })
            })
        }
        // Intentionally NOT calling rebuildPlayerTimeline() here. Slip-drag fires
        // this method at touch-event rate (60–120 Hz); rebuilding ExoPlayer's
        // MediaItem set on every tick was the root cause of the "clunky" timeline.
        // Rebuild happens once in endSlipEdit() instead. ScrubbingMode in
        // beginSlipEdit() already suppresses intermediate decode work.
    }

    fun slideClip(clipId: String, slideAmountMs: Long) {
        val tracks = _state.value.tracks
        val linkedLocation = tracks.findClipLocation(clipId)?.clip?.linkedClipId
            ?.let { linkedId -> tracks.findClipLocation(linkedId) }
        val primaryLocation = tracks.findClipLocation(clipId) ?: return
        if (primaryLocation.track.isLocked || (linkedLocation?.track?.isLocked == true)) return

        val primaryBounds = calculateSlideBounds(primaryLocation.track, clipId) ?: return
        var minDelta = primaryBounds.minStartMs - primaryBounds.currentStartMs
        var maxDelta = primaryBounds.maxStartMs - primaryBounds.currentStartMs

        linkedLocation?.let { location ->
            val linkedBounds = calculateSlideBounds(location.track, location.clip.id) ?: return
            minDelta = maxOf(minDelta, linkedBounds.minStartMs - linkedBounds.currentStartMs)
            maxDelta = minOf(maxDelta, linkedBounds.maxStartMs - linkedBounds.currentStartMs)
        }

        if (maxDelta < minDelta) return
        val appliedDelta = (primaryBounds.currentStartMs + slideAmountMs)
            .coerceIn(primaryBounds.minStartMs, primaryBounds.maxStartMs) - primaryBounds.currentStartMs
        val synchronizedDelta = appliedDelta.coerceIn(minDelta, maxDelta)
        if (synchronizedDelta == 0L) return

        _state.update { s ->
            s.copy(tracks = s.tracks.map { track ->
                when {
                    track.id == primaryLocation.track.id -> {
                        slideClipOnTrack(
                            track = track,
                            clipId = clipId,
                            newStartMs = primaryBounds.currentStartMs + synchronizedDelta
                        )
                    }
                    linkedLocation != null && track.id == linkedLocation.track.id -> {
                        val linkedBounds = calculateSlideBounds(track, linkedLocation.clip.id) ?: return@map track
                        slideClipOnTrack(
                            track = track,
                            clipId = linkedLocation.clip.id,
                            newStartMs = linkedBounds.currentStartMs + synchronizedDelta
                        )
                    }
                    else -> track
                }
            })
        }
        // Deferred to endSlideEdit() to avoid per-frame player rebuilds during drag.
        // Same perf fix as slipClip — see comment there.
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

        viewModelScope.launch {
            val refClip = _state.value.tracks.flatMap { it.clips }.find { it.id == referenceClipId }
            val targetClip = _state.value.tracks.flatMap { it.clips }.find { it.id == targetClipId }
            if (refClip == null || targetClip == null) {
                showToast("Clip no longer exists")
                return@launch
            }
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
                saveProject()
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
            val selectedTrackIds = s.tracks
                .filter { track -> track.clips.any { it.id in selectedIds } }
                .map { it.id }
            val compoundTrackId = when {
                s.selectedTrackId != null && s.selectedTrackId in selectedTrackIds -> s.selectedTrackId
                else -> s.tracks
                    .filter { it.id in selectedTrackIds }
                    .minByOrNull { it.index }
                    ?.id
            } ?: return@update s

            val compoundStart = selectedClips.minOf { it.timelineStartMs }
            val compoundEnd = selectedClips.maxOf { it.timelineEndMs }

            // Create compound clip containing the selected clips
            val compoundDurationMs = compoundEnd - compoundStart
            val firstClip = selectedClips.first()
            val compoundClip = firstClip.copy(
                id = java.util.UUID.randomUUID().toString(),
                timelineStartMs = compoundStart,
                sourceDurationMs = compoundDurationMs,
                trimStartMs = 0L,
                trimEndMs = compoundDurationMs,
                speed = 1f,
                isCompound = true,
                compoundClips = selectedClips.map { it.copy() }
            )

            // Remove original clips and insert compound
            val tracks = s.tracks.map { track ->
                val remainingClips = track.clips.filter { it.id !in selectedIds }
                if (track.id == compoundTrackId) {
                    track.copy(clips = (remainingClips + compoundClip).sortedBy { it.timelineStartMs })
                } else {
                    track.copy(clips = remainingClips)
                }
            }

            recalculateDuration(s.copy(
                tracks = tracks,
                selectedClipIds = setOf(compoundClip.id),
                selectedClipId = compoundClip.id,
                selectedTrackId = compoundTrackId
            ))
        }
        rebuildPlayerTimeline()
        saveProject()
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
        saveProject()
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
                val file = java.io.File(dir, "${sanitizedProjectFileStem(s.project.name)}.novacut")
                val success = com.novacut.editor.engine.ProjectArchive.exportArchive(
                    context = appContext,
                    state = buildAutoSaveState(s),
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
                val file = java.io.File(dir, "${sanitizedProjectFileStem(s.project.name)}.otio")
                writeUtf8TextAtomically(file, otioJson)
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
                val file = java.io.File(dir, "${sanitizedProjectFileStem(s.project.name)}.fcpxml")
                writeUtf8TextAtomically(file, xml)
                withContext(Dispatchers.Main) { showToast("FCPXML exported: ${file.name}") }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { showToast("FCPXML export failed: ${e.message}") }
            }
        }
    }

    // --- Linked A/V ---
    fun unlinkAudioVideo() {
        val selectedClipId = _state.value.selectedClipId ?: return
        val linkedIds = linkedClipIds(_state.value.tracks, selectedClipId)
        if (_state.value.tracks.any { track ->
                track.isLocked && track.clips.any { it.id in linkedIds }
            }
        ) {
            showToast("Track is locked")
            return
        }
        saveUndoState("Unlink A/V")
        _state.update { state ->
            state.copy(tracks = state.tracks.map { track ->
                track.copy(clips = track.clips.map { clip ->
                    if (clip.id in linkedIds) clip.copy(linkedClipId = null) else clip
                })
            })
        }
        saveProject()
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
            val current = if (s.selectedClipIds.isEmpty()) {
                s.selectedClipId?.let(::setOf) ?: emptySet()
            } else {
                s.selectedClipIds
            }
            val updated = when {
                current.size == 1 && clipId in current -> current
                clipId in current -> current - clipId
                else -> current + clipId
            }
            val soleSelectedClipId = updated.singleOrNull()
            val soleSelectedTrackId = soleSelectedClipId?.let { selectedId ->
                s.tracks.firstOrNull { track -> track.clips.any { clip -> clip.id == selectedId } }?.id
            }
            s.copy(
                selectedClipIds = updated,
                selectedClipId = if (updated.size == 1) soleSelectedClipId else null,
                selectedTrackId = if (updated.size == 1) soleSelectedTrackId else null
            )
        }
        updatePreview()
    }

    fun clearMultiSelect() {
        _state.update { s ->
            val selectedClipEntries = s.tracks.flatMap { track ->
                track.clips
                    .filter { clip -> clip.id in s.selectedClipIds }
                    .map { clip -> track.id to clip }
            }
            val activeSelection = s.selectedClipId
                ?.let { selectedId ->
                    selectedClipEntries.firstOrNull { (_, clip) -> clip.id == selectedId }
                }
                ?: selectedClipEntries.firstOrNull { (_, clip) ->
                    _playheadMs.value in clip.timelineStartMs until clip.timelineEndMs
                }
                ?: selectedClipEntries.minByOrNull { (_, clip) ->
                    kotlin.math.abs(clip.timelineStartMs - _playheadMs.value)
                }
            val activeClipId = activeSelection?.second?.id
            val activeTrackId = activeSelection?.first
            s.copy(
                selectedClipIds = activeClipId?.let(::setOf) ?: emptySet(),
                selectedClipId = activeClipId,
                selectedTrackId = activeTrackId
            )
        }
        updatePreview()
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
        saveProject()
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
        saveProject()
        showToast("Clips ungrouped")
    }

    fun deleteMultiSelectedClips() {
        val clipIds = _state.value.selectedClipIds
        if (clipIds.isEmpty()) return
        saveUndoState("Delete ${clipIds.size} clips")
        _state.update { s ->
            val tracks = s.tracks.map { track ->
                val remaining = track.clips.filter { it.id !in clipIds }
                // Ripple delete: close gaps by recalculating timeline positions
                val sorted = remaining.sortedBy { it.timelineStartMs }
                var nextStartMs = 0L
                val rippled = sorted.map { clip ->
                    if (clip.timelineStartMs > nextStartMs) {
                        val shifted = clip.copy(timelineStartMs = nextStartMs)
                        nextStartMs += shifted.durationMs
                        shifted
                    } else {
                        nextStartMs = clip.timelineStartMs + clip.durationMs
                        clip
                    }
                }
                track.copy(clips = rippled)
            }
            recalculateDuration(s.copy(
                tracks = tracks,
                selectedClipIds = emptySet(),
                selectedClipId = null,
                selectedTrackId = null,
                waveforms = s.waveforms - clipIds
            ))
        }
        rebuildPlayerTimeline()
        saveProject()
        showToast("Deleted ${clipIds.size} clips")
    }

    // --- Subtitle Export ---
    fun exportSubtitles(format: SubtitleFormat) {
        val captions = _state.value.tracks.flatMap { it.clips }.flatMap { clip ->
            clip.captions.map { c -> c.copy(
                startTimeMs = c.startTimeMs + clip.timelineStartMs,
                endTimeMs = c.endTimeMs + clip.timelineStartMs
            ) }
        }
        if (captions.isEmpty()) {
            showToast("No captions to export")
            return
        }
        viewModelScope.launch {
            val dir = java.io.File(appContext.getExternalFilesDir(null), "subtitles")
            dir.mkdirs()
            val file = java.io.File(
                dir,
                "${sanitizedProjectFileStem(_state.value.project.name)}.${format.extension}"
            )
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
        rebuildPlayerTimeline()
        saveProject()
    }

    fun showPipPresets() = showPanel(PanelId.PIP_PRESETS)
    fun hidePipPresets() = hidePanel(PanelId.PIP_PRESETS)

    fun showChromaKey() = showPanel(PanelId.CHROMA_KEY)
    fun hideChromaKey() = hidePanel(PanelId.CHROMA_KEY)

    // --- v3.69 features hub ---
    fun showV369Features() = showPanel(PanelId.V369_FEATURES)
    fun hideV369Features() = hidePanel(PanelId.V369_FEATURES)

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
        val playheadInClip = clip.timelineOffsetToSourceMs(relativeOffset)
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
                val voiceClip = voiceTracks
                    .flatMap { it.clips }
                    .firstOrNull(::clipHasAudio)

                if (voiceClip == null) {
                    showToast("Need a video clip with audio for ducking")
                    return@launch
                }

                val waveform = withContext(Dispatchers.IO) {
                    audioEngine.extractWaveform(voiceClip.sourceUri, 44100)
                }
                val pcm = ShortArray(waveform.size) { (waveform[it] * 32767).toInt().toShort() }
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
                saveProject()
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
        val safeVolume = safeEditorFloat(volume, 1f, 0f, 2f)
        updateClipById(clipId) { it.copy(volume = safeVolume) }
        videoEngine.setPreviewVolume(safeVolume)
        // saveProject() deferred to endVolumeChange() — slider fires this 60 Hz.
    }

    fun beginVolumeChange() {
        saveUndoState("Change volume")
    }

    fun endVolumeChange() {
        saveProject()
    }

    fun beginTransformChange() {
        saveUndoState("Transform clip")
    }

    fun endTransformChange() {
        saveProject()
    }

    fun setClipTransform(clipId: String, positionX: Float? = null, positionY: Float? = null,
                         scaleX: Float? = null, scaleY: Float? = null, rotation: Float? = null) {
        updateClipById(clipId) { clip ->
            clip.copy(
                positionX = safeEditorFloat(positionX ?: clip.positionX, clip.positionX, -10f, 10f),
                positionY = safeEditorFloat(positionY ?: clip.positionY, clip.positionY, -10f, 10f),
                scaleX = safeEditorFloat(scaleX ?: clip.scaleX, clip.scaleX, 0.1f, 5f),
                scaleY = safeEditorFloat(scaleY ?: clip.scaleY, clip.scaleY, 0.1f, 5f),
                rotation = safeEditorFloat(rotation ?: clip.rotation, clip.rotation, -3600f, 3600f)
            )
        }
        updatePreview()
        // saveProject() deferred to endTransformChange() — preview pinch/drag fires
        // this method at touch-event rate. beginTransformChange + endTransformChange
        // bracket the gesture.
    }

    fun resetClipTransform(clipId: String) {
        saveUndoState("Reset transform")
        updateClipById(clipId) { it.copy(positionX = 0f, positionY = 0f, scaleX = 1f, scaleY = 1f, rotation = 0f) }
        saveProject()
    }

    fun beginOpacityChange() {
        saveUndoState("Adjust opacity")
    }

    fun endOpacityChange() {
        saveProject()
    }

    fun setClipOpacity(clipId: String, opacity: Float) {
        updateClipById(clipId) { it.copy(opacity = opacity.coerceIn(0f, 1f)) }
        updatePreview()
        // saveProject() deferred to endOpacityChange() — slider-driven, see above.
    }

    fun beginFadeAdjust() {
        saveUndoState("Adjust fade")
    }

    fun endFadeAdjust() {
        saveProject()
    }

    fun setClipFadeIn(clipId: String, fadeInMs: Long) {
        updateClipById(clipId) { clip ->
            val maxFade = (clip.durationMs - clip.fadeOutMs).coerceAtLeast(0L)
            clip.copy(fadeInMs = fadeInMs.coerceIn(0L, maxFade))
        }
        // saveProject() deferred to endFadeAdjust().
    }

    fun setClipFadeOut(clipId: String, fadeOutMs: Long) {
        updateClipById(clipId) { clip ->
            val maxFade = (clip.durationMs - clip.fadeInMs).coerceAtLeast(0L)
            clip.copy(fadeOutMs = fadeOutMs.coerceIn(0L, maxFade))
        }
        // saveProject() deferred to endFadeAdjust().
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
            chapterMarkers = _state.value.chapterMarkers.toList(),
            drawingPaths = _state.value.drawingPaths.toList(),
            playheadMs = _playheadMs.value
        )

        _state.update {
            val restored = recalculateDuration(it.copy(
                tracks = action.tracks,
                textOverlays = action.textOverlays,
                imageOverlays = action.imageOverlays,
                timelineMarkers = action.timelineMarkers,
                chapterMarkers = action.chapterMarkers,
                drawingPaths = action.drawingPaths,
                undoStack = undoStack.dropLast(1),
                redoStack = it.redoStack + currentAction
            ))
            val clipExists = it.selectedClipId != null &&
                restored.tracks.any { t -> t.clips.any { c -> c.id == it.selectedClipId } }
            // Clamp the restored playhead to the restored timeline duration so
            // undoing a "delete last clip" doesn't leave the playhead dangling
            // past the new timeline end.
            val clampedPlayhead = action.playheadMs
                .coerceIn(0L, restored.totalDurationMs.coerceAtLeast(0L))
            dismissedPanelState(restored).copy(
                selectedClipId = if (clipExists) it.selectedClipId else null,
                currentTool = EditorTool.NONE,
                playheadMs = clampedPlayhead
            )
        }
        _playheadMs.value = _state.value.playheadMs
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
            chapterMarkers = _state.value.chapterMarkers.toList(),
            drawingPaths = _state.value.drawingPaths.toList(),
            playheadMs = _playheadMs.value
        )

        _state.update {
            val restored = recalculateDuration(it.copy(
                tracks = action.tracks,
                textOverlays = action.textOverlays,
                imageOverlays = action.imageOverlays,
                timelineMarkers = action.timelineMarkers,
                chapterMarkers = action.chapterMarkers,
                drawingPaths = action.drawingPaths,
                redoStack = redoStack.dropLast(1),
                undoStack = (it.undoStack + currentAction).takeLast(50)
            ))
            val clipExists = it.selectedClipId != null &&
                restored.tracks.any { t -> t.clips.any { c -> c.id == it.selectedClipId } }
            val clampedPlayhead = action.playheadMs
                .coerceIn(0L, restored.totalDurationMs.coerceAtLeast(0L))
            dismissedPanelState(restored).copy(
                selectedClipId = if (clipExists) it.selectedClipId else null,
                currentTool = EditorTool.NONE,
                playheadMs = clampedPlayhead
            )
        }
        _playheadMs.value = _state.value.playheadMs
        rebuildPlayerTimeline()
    }

    @Volatile
    private var toastJob: Job? = null

    fun showToast(message: String) {
        showToast(message, inferSeverity(message))
    }

    fun showToast(message: String, severity: ToastSeverity) {
        toastJob?.cancel()
        _state.update { it.copy(toastMessage = message, toastSeverity = severity) }
        // Errors deserve more reading time than info; success/warning use the standard window.
        val durationMs = when (severity) {
            ToastSeverity.Error -> 4500L
            ToastSeverity.Warning -> 3500L
            else -> 2800L
        }
        toastJob = viewModelScope.launch {
            delay(durationMs)
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

    private fun clipHasVisual(clip: Clip): Boolean = videoEngine.hasVisualTrack(clip.sourceUri)

    private fun clipHasAudio(clip: Clip): Boolean = videoEngine.hasAudioTrack(clip.sourceUri)

    private fun clipSupportsAudioSync(clip: Clip): Boolean {
        return videoEngine.isMotionVideo(clip.sourceUri) && videoEngine.hasAudioTrack(clip.sourceUri)
    }

    fun getSelectedClip(): Clip? {
        val clipId = _state.value.selectedClipId ?: return null
        return _state.value.tracks.flatMap { it.clips }.firstOrNull { it.id == clipId }
    }

    fun getSelectedTrack(): Track? {
        val trackId = _state.value.selectedTrackId ?: return null
        return _state.value.tracks.firstOrNull { it.id == trackId }
    }

    fun captureFrame() {
        val clip = getSelectedClip() ?: _state.value.tracks.flatMap { it.clips }.firstOrNull() ?: return
        viewModelScope.launch {
            try {
                val config = _state.value.exportConfig
                val format = if (config.captureFormat == FrameCaptureFormat.JPEG)
                    android.graphics.Bitmap.CompressFormat.JPEG else android.graphics.Bitmap.CompressFormat.PNG
                val quality = if (config.captureFormat == FrameCaptureFormat.JPEG) 90 else 100
                val ext = config.captureFormat.extension
                val captureTimeUs = _playheadMs.value * 1000
                val file = withContext(Dispatchers.IO) {
                    val bitmap = videoEngine.extractThumbnail(clip.sourceUri, captureTimeUs)
                        ?: throw IllegalStateException("No frame available at the current timestamp")
                    val outputFiles = createFrameCaptureOutputFiles(appContext, ext)
                    try {
                        outputFiles.partialFile.outputStream().use { output ->
                            if (!bitmap.compress(format, quality, output)) {
                                throw IllegalStateException("Frame encoder returned no data")
                            }
                        }
                        finalizeFrameOutputFile(outputFiles.partialFile, outputFiles.outputFile)
                            ?: throw IllegalStateException("Frame capture output was empty")
                    } catch (e: Exception) {
                        cleanupFrameOutputFiles(outputFiles.partialFile, outputFiles.outputFile)
                        throw e
                    } finally {
                        bitmap.recycle()
                    }
                }
                _state.update {
                    it.copy(
                        lastExportedFilePath = file.absolutePath,
                        exportState = ExportState.COMPLETE,
                        exportErrorMessage = null
                    )
                }
                showToast("Frame saved: ${file.name}")
            } catch (e: Exception) {
                Log.w("EditorVM", "Frame capture failed", e)
                _state.update {
                    it.copy(
                        exportState = ExportState.ERROR,
                        exportErrorMessage = "Frame capture failed. Try another timestamp or source clip."
                    )
                }
                showToast("Frame capture failed")
            }
        }
    }

    // Project persistence
    private fun buildAutoSaveState(
        state: EditorState = _state.value,
        projectId: String = state.project.id
    ): AutoSaveState {
        return AutoSaveState(
            projectId = projectId,
            tracks = state.tracks,
            textOverlays = state.textOverlays,
            imageOverlays = state.imageOverlays,
            timelineMarkers = state.timelineMarkers,
            playheadMs = state.playheadMs,
            chapterMarkers = state.chapterMarkers,
            drawingPaths = state.drawingPaths,
            beatMarkers = state.beatMarkers,
            transcript = state.v369.transcript
        )
    }

    private fun sanitizedProjectFileStem(name: String): String {
        return sanitizeFileName(name, fallback = "NovaCut")
    }

    fun saveProject() {
        viewModelScope.launch {
            val s = _state.value
            val firstClipUri = s.tracks
                .filter { it.type == TrackType.VIDEO }
                .flatMap { it.clips }
                .firstOrNull()?.sourceUri?.toString()

            val project = s.project.copy(
                updatedAt = System.currentTimeMillis(),
                durationMs = s.totalDurationMs,
                thumbnailUri = firstClipUri
            )
            projectDao.insertProject(project)
            _state.update { it.copy(project = project) }

            // Persist track/clip data immediately (don't wait for auto-save timer)
            autoSave.saveNow(project.id, buildAutoSaveState(s, project.id))
        }
    }

    fun renameProject(name: String) {
        val normalizedName = name.trim().ifBlank { "Untitled" }
        _state.update { it.copy(project = it.project.copy(name = normalizedName)) }
        saveProject()
    }

    fun showScratchpad() {
        pauseIfPlaying()
        _state.update { dismissedPanelState(it).copy(panels = it.panels.closeAll().open(PanelId.SCRATCHPAD)) }
    }

    fun hideScratchpad() {
        _state.update { it.copy(panels = it.panels.close(PanelId.SCRATCHPAD)) }
    }

    fun updateProjectNotes(notes: String) {
        _state.update { it.copy(project = it.project.copy(notes = notes)) }
        saveProject()
    }

    /**
     * Clears the bulk-undo prompt after the user interacts with it (taps
     * Undo or dismisses) or after the UI auto-dismiss timer elapses. Safe
     * to call when the prompt is already null.
     */
    fun dismissBulkUndoPrompt() {
        val current = _state.value.bulkUndoPrompt ?: return
        _state.update { if (it.bulkUndoPrompt?.id == current.id) it.copy(bulkUndoPrompt = null) else it }
    }

    fun dismissRecoveryDialog(recover: Boolean) {
        _state.update { it.copy(panels = it.panels.close(PanelId.RECOVERY_DIALOG)) }
        if (!recover) {
            viewModelScope.launch(Dispatchers.IO) {
                val projectId = _state.value.project.id
                if (projectId.isNotBlank()) {
                    try {
                        autoSave.clearRecoveryData(projectId)
                    } catch (e: Exception) {
                        Log.w("EditorViewModel", "Failed to discard recovery data", e)
                    }
                }
            }
        }
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
                chapterMarkers = state.chapterMarkers.toList(),
                drawingPaths = state.drawingPaths.toList(),
                playheadMs = state.playheadMs
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

    fun exportTemplate(templateId: String) {
        viewModelScope.launch {
            try {
                val exportResult = withContext(Dispatchers.IO) {
                    val template = templateManager.getTemplate(templateId) ?: return@withContext null
                    val dir = File(appContext.getExternalFilesDir(null), "templates").apply { mkdirs() }
                    val sanitized = sanitizeFileName(template.name, fallback = "template")
                    val outputFile = File(dir, "$sanitized.novacut-template")
                    val success = templateManager.exportTemplateToFile(template.id, outputFile)
                    if (!success) return@withContext null
                    template to outputFile
                }
                if (exportResult != null) {
                    val (_, outputFile) = exportResult
                    showToast("Template exported: ${outputFile.name}")
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        appContext, "${appContext.packageName}.fileprovider", outputFile
                    )
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/json"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    appContext.startActivity(
                        Intent.createChooser(shareIntent, "Share Template")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } else {
                    showToast("Template export failed")
                }
            } catch (e: Exception) {
                showToast("Template export failed: ${e.message}")
            }
        }
    }

    fun importTemplate(uri: Uri) {
        viewModelScope.launch {
            try {
                val template = templateManager.importTemplateFromUri(uri)
                if (template != null) {
                    showToast("Imported template: ${template.name}")
                } else {
                    showToast("Failed to import template")
                }
            } catch (e: Exception) {
                showToast("Import failed: ${e.message}")
            }
        }
    }

    fun downloadSegmentationModel() = aiToolsDelegate.downloadSegmentationModel()
    fun deleteSegmentationModel() = aiToolsDelegate.deleteSegmentationModel()

    fun runAiTool(toolId: String) = aiToolsDelegate.runAiTool(toolId)
    fun cancelAiTool() = aiToolsDelegate.cancelAiTool()
    fun dismissAiRequirementPrompt() {
        val current = _state.value.aiRequirementPrompt ?: return
        _state.update {
            if (it.aiRequirementPrompt?.id == current.id) {
                it.copy(aiRequirementPrompt = null)
            } else {
                it
            }
        }
    }

    fun insertFreezeFrame() {
        val clip = getSelectedClip() ?: return
        val playheadMs = _state.value.playheadMs
        if (playheadMs < clip.timelineStartMs || playheadMs >= clip.timelineEndMs) {
            showToast("Move playhead over the selected clip")
            return
        }

        val relativeMs = playheadMs - clip.timelineStartMs
        val sourceTimeMs = clip.timelineOffsetToSourceMs(relativeMs)

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

            // Pre-mint UUIDs OUTSIDE the _state.update {} closure so a CAS retry doesn't
            // allocate fresh IDs on every attempt and produce ID drift relative to anything
            // that observed the in-flight intermediate state.
            val freezeClipId = UUID.randomUUID().toString()
            val secondHalfId = UUID.randomUUID().toString()
            // Split at playhead, then insert freeze frame between halves
            _state.update { s ->
                val tracks = s.tracks.map { track ->
                    val clipIndex = track.clips.indexOfFirst { it.id == clip.id }
                    if (clipIndex < 0) return@map track

                    val c = track.clips[clipIndex]
                    val relativeForClip = playheadMs - c.timelineStartMs
                    val splitInSource = c.timelineOffsetToSourceMs(relativeForClip)
                    if (splitInSource <= c.trimStartMs || splitInSource >= c.trimEndMs) return@map track
                    val trimRange = (c.trimEndMs - c.trimStartMs).coerceAtLeast(0L)
                    val splitFraction = if (trimRange > 0L) {
                        ((splitInSource - c.trimStartMs).toFloat() / trimRange.toFloat()).coerceIn(0f, 1f)
                    } else {
                        0f
                    }

                    val firstHalf = c.copy(
                        trimEndMs = splitInSource,
                        speedCurve = c.speedCurve?.restrictTo(0f, splitFraction, trimRange)
                    )
                    val freezeClip = Clip(
                        id = freezeClipId,
                        sourceUri = frameUri,
                        sourceDurationMs = freezeDurationMs,
                        timelineStartMs = firstHalf.timelineEndMs,
                        trimStartMs = 0L,
                        trimEndMs = freezeDurationMs
                    )
                    val secondHalf = c.copy(
                        id = secondHalfId,
                        timelineStartMs = freezeClip.timelineEndMs,
                        trimStartMs = splitInSource,
                        speedCurve = c.speedCurve?.restrictTo(splitFraction, 1f, trimRange)
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
        return normalizeTimelineState(state)
    }

    private fun normalizeTimelineState(state: EditorState): EditorState {
        val normalizedTracks = state.tracks.map { track ->
            track.copy(clips = track.clips.sortedBy { it.timelineStartMs })
        }
        val totalDuration = normalizedTracks.maxOfOrNull { t ->
            t.clips.maxOfOrNull { it.timelineEndMs } ?: 0L
        } ?: 0L
        val normalizedState = normalizeSelectionState(
            state.copy(
                tracks = normalizedTracks,
                totalDurationMs = totalDuration
            ),
            normalizedTracks
        )
        val clampedPlayheadMs = normalizedState.playheadMs.coerceIn(0L, totalDuration)
        val clampedScrollOffsetMs = clampTimelineScrollOffset(
            offsetMs = normalizedState.scrollOffsetMs,
            state = normalizedState
        )
        return normalizedState.copy(
            playheadMs = clampedPlayheadMs,
            scrollOffsetMs = clampedScrollOffsetMs
        )
    }

    private fun previewTrackForClip(clipId: String): Track? {
        return _state.value.tracks.firstOrNull { track -> track.clips.any { it.id == clipId } }
    }

    private fun primaryPreviewTrack(): Track? {
        return _state.value.tracks
            .sortedBy { it.index }
            .firstOrNull {
                (it.type == TrackType.VIDEO || it.type == TrackType.OVERLAY) &&
                    it.isVisible &&
                    it.clips.isNotEmpty()
            }
    }

    private fun previewClipAtPosition(positionMs: Long): Clip? {
        return primaryPreviewTrack()
            ?.clips
            ?.sortedBy { it.timelineStartMs }
            ?.firstOrNull { positionMs in it.timelineStartMs until it.timelineEndMs }
    }

    private fun nextPreviewClipAfter(positionMs: Long): Clip? {
        return primaryPreviewTrack()
            ?.clips
            ?.sortedBy { it.timelineStartMs }
            ?.firstOrNull { it.timelineStartMs > positionMs }
    }

    private fun startGapPlayback(startMs: Long) {
        val targetClip = nextPreviewClipAfter(startMs)
        val gapEndMs = targetClip?.timelineStartMs ?: _state.value.totalDurationMs
        if (gapEndMs <= startMs) {
            if (targetClip != null) {
                seekTo(targetClip.timelineStartMs)
                videoEngine.play()
                _state.update { it.copy(isPlaying = true) }
            }
            return
        }

        cancelGapPlayback()
        videoEngine.pause()
        _playheadMs.value = startMs
        _state.update { it.copy(isPlaying = true, playheadMs = startMs) }

        gapPlaybackJob = viewModelScope.launch {
            val gapPlaybackStartRealtime = SystemClock.elapsedRealtime()
            while (isActive) {
                val elapsedMs = SystemClock.elapsedRealtime() - gapPlaybackStartRealtime
                val positionMs = (startMs + elapsedMs).coerceAtMost(gapEndMs)
                _playheadMs.value = positionMs
                _state.update { it.copy(playheadMs = positionMs, isPlaying = true) }
                if (positionMs >= gapEndMs) {
                    break
                }
                delay(33)
            }

            if (!isActive) {
                return@launch
            }
            gapPlaybackJob = null
            val resumeClip = nextPreviewClipAfter((gapEndMs - 1L).coerceAtLeast(0L))
            if (resumeClip != null) {
                val resumeAtMs = resumeClip.timelineStartMs
                _playheadMs.value = resumeAtMs
                _state.update { it.copy(playheadMs = resumeAtMs, isPlaying = true) }
                videoEngine.seekTo(resumeAtMs)
                videoEngine.play()
            } else {
                val timelineEndMs = _state.value.totalDurationMs
                _playheadMs.value = timelineEndMs
                _state.update { it.copy(playheadMs = timelineEndMs, isPlaying = false) }
            }
        }
    }

    private fun cancelGapPlayback() {
        val wasPlayingGap = gapPlaybackJob?.isActive == true
        gapPlaybackJob?.cancel()
        gapPlaybackJob = null
        if (wasPlayingGap) {
            _state.update { it.copy(isPlaying = false) }
        }
    }

    private fun isTrackAudibleInPreview(track: Track): Boolean {
        val soloTrackIds = _state.value.tracks.filter { it.isSolo }.map { it.id }.toSet()
        return track.isVisible && !track.isMuted && (soloTrackIds.isEmpty() || track.id in soloTrackIds)
    }

    private fun minimumSlideDurationMs(clip: Clip): Long {
        val speed = safeEditorFloat(clip.speed, 1f, 0.01f, 100f)
        return kotlin.math.ceil(100.0 / speed.toDouble()).toLong().coerceAtLeast(1L)
    }

    private fun maximumPreviousDurationMs(clip: Clip): Long {
        val speed = safeEditorFloat(clip.speed, 1f, 0.01f, 100f)
        return kotlin.math.floor((clip.sourceDurationMs - clip.trimStartMs).toDouble() / speed.toDouble())
            .toLong()
            .coerceAtLeast(minimumSlideDurationMs(clip))
    }

    private fun maximumNextDurationMs(clip: Clip): Long {
        val speed = safeEditorFloat(clip.speed, 1f, 0.01f, 100f)
        return kotlin.math.floor(clip.trimEndMs.toDouble() / speed.toDouble())
            .toLong()
            .coerceAtLeast(minimumSlideDurationMs(clip))
    }

    private fun canSplitClipAtPosition(clip: Clip, positionMs: Long): Boolean {
        if (positionMs <= clip.timelineStartMs || positionMs >= clip.timelineEndMs) return false
        val sourcePos = splitPointInSource(clip, positionMs)
        return sourcePos - clip.trimStartMs >= MIN_TIMELINE_CLIP_DURATION_MS &&
            clip.trimEndMs - sourcePos >= MIN_TIMELINE_CLIP_DURATION_MS
    }

    private fun splitPointInSource(clip: Clip, positionMs: Long): Long {
        val relativePos = positionMs - clip.timelineStartMs
        return clip.timelineOffsetToSourceMs(relativePos)
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
        // Guarantee scrubbing-mode is reset regardless of whether a begin-X()
        // had a matching end-X(). If the activity dies mid-trim / mid-scrub (OS
        // kill, uncaught exception in the drag handler), a stale scrubbing flag
        // would otherwise persist on the singleton VideoEngine and affect the
        // next project opened in this process.
        videoEngine.setScrubbingMode(false)
        // Only reset export state if no export is actively running — the ExportService
        // observes the same state flows and needs to see the terminal state to stop itself.
        if (videoEngine.exportState.value != ExportState.EXPORTING) {
            videoEngine.resetExportState()
        }
        cancelWaveformLoads()
        audioEngine.clearWaveformCache()
        // DON'T call videoEngine.release() or ttsEngine.release() — they're @Singletons
    }
}

private fun safeEditorFloat(value: Float, fallback: Float, min: Float, max: Float): Float {
    val safeFallback = if (fallback.isFinite()) fallback.coerceIn(min, max) else min
    return if (value.isFinite()) value.coerceIn(min, max) else safeFallback
}

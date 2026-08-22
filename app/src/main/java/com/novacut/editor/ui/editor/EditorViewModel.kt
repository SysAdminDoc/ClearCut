package com.novacut.editor.ui.editor

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.OpenableColumns
import com.novacut.editor.engine.AppLog
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novacut.editor.R
import com.novacut.editor.ai.AiFeatures
import com.novacut.editor.ai.AutoEditClip
import com.novacut.editor.ai.AutoEditIntent
import com.novacut.editor.ai.AutoEditResult
import com.novacut.editor.engine.AiUsageLedger
import com.novacut.editor.engine.AppSettings
import com.novacut.editor.engine.AudioEngine
import com.novacut.editor.engine.AutoSaveState
import com.novacut.editor.engine.AutoSaveRequest
import com.novacut.editor.engine.projectStateFingerprint
import com.novacut.editor.engine.ExportIncidentStore
import com.novacut.editor.engine.ExportState
import com.novacut.editor.engine.ExportStoragePolicy
import com.novacut.editor.engine.ExportStoragePreflight
import com.novacut.editor.engine.FRAME_CAPTURE_DIR_NAME
import com.novacut.editor.engine.exportStorageFailureMessage
import com.novacut.editor.engine.FontRegistry
import com.novacut.editor.engine.ProjectAutoSave
import com.novacut.editor.engine.ProductHealthLedger
import com.novacut.editor.engine.ProjectArchive
import com.novacut.editor.engine.ProjectDocument
import com.novacut.editor.engine.ProjectDocumentApplicator
import com.novacut.editor.engine.ProjectDocumentReadResult
import com.novacut.editor.engine.AndroidProjectDependencyProbe
import com.novacut.editor.engine.ProjectDependencyEditorInputs
import com.novacut.editor.engine.ProjectDependencyManifest
import com.novacut.editor.engine.SEGMENTATION_MODEL_DEPENDENCY
import com.novacut.editor.engine.ProxyEngine
import com.novacut.editor.engine.SettingsRepository
import com.novacut.editor.engine.SmartRenderEngine
import com.novacut.editor.engine.SpeakerSwitchPlanner
import com.novacut.editor.engine.SubtitleExporter
import com.novacut.editor.engine.CaptionImportEngine
import com.novacut.editor.engine.ConnectivityObserver
import com.novacut.editor.engine.IncomingDocumentImportRouter
import com.novacut.editor.engine.IncomingDocumentIntentParser
import com.novacut.editor.engine.IncomingDocumentItem
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
import com.novacut.editor.engine.C2paExportEngine
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
import com.novacut.editor.engine.TimelineExportCoordinator
import com.novacut.editor.engine.TrackBlendModeCapability
import com.novacut.editor.engine.TimelineExchangeValidator
import com.novacut.editor.engine.SilenceDetectionEngine
import com.novacut.editor.engine.ProxyWorkflowEngine
import com.novacut.editor.engine.MultiCamEngine
import com.novacut.editor.engine.MediaImportEngine
import com.novacut.editor.engine.MediaHealth
import com.novacut.editor.engine.MediaDiagnosticsProbe
import com.novacut.editor.engine.MediaRelinkProbe
import com.novacut.editor.engine.Media3TrimOptimizationPolicy
import com.novacut.editor.engine.MetadataSidecarEngine
import com.novacut.editor.engine.MetadataSidecarExportResult
import com.novacut.editor.engine.MetadataSidecarFormat
import com.novacut.editor.engine.MetadataSidecarTrack
import com.novacut.editor.engine.SyncFrameDirection
import com.novacut.editor.engine.TimelineMediaJobIdentity
import com.novacut.editor.engine.shouldApplyMediaJobResult
import com.novacut.editor.engine.timelineMediaJobIdentity
import com.novacut.editor.engine.OverlayAssetImportResult
import com.novacut.editor.engine.OverlayAssetStore
import com.novacut.editor.engine.ProjectMediaAsset
import com.novacut.editor.engine.normalizeMediaAssetNotes
import com.novacut.editor.engine.normalizeMediaAssetTags
import com.novacut.editor.engine.writeManagedMediaAssetAnnotations
import com.novacut.editor.engine.VideoEngine
import com.novacut.editor.engine.VoiceoverRecorderEngine
import com.novacut.editor.engine.TemplateManager
import com.novacut.editor.engine.attachMediaAssetIdsToTracks
import com.novacut.editor.engine.backfillManagedMediaAssetSidecars
import com.novacut.editor.engine.buildProjectMediaAssets
import com.novacut.editor.engine.sanitizeFileName
import com.novacut.editor.engine.resolveMediaDisplayName
import com.novacut.editor.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.novacut.editor.engine.EditingSuggestionEngine
import com.novacut.editor.engine.EffectPreviewRenderer
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import kotlin.math.roundToLong
import com.novacut.editor.engine.redacted

private const val TIMELINE_BASE_SCALE = 0.15f
// Min zoom lowered from 0.1 → 0.01 so a ~10-minute video fits the phone viewport
// when the user taps "fit to window" or when the timeline auto-fits on first layout.
// Previously fit-zoom was clamped before it could reach a ratio that actually fit,
// which is why long clips appeared to only show a narrow window of editable content.
private const val WAVEFORM_PRELOAD_PADDING_MS = 3_000L
private const val WAVEFORM_FALLBACK_WINDOW_MS = 15_000L
private const val SUGGESTION_SNOOZE_STATE_KEY = "editingSuggestionSnoozeUntil"
private const val SUGGESTION_SNOOZE_MS = 30 * 60 * 1_000L

internal fun shouldShowEditingSuggestion(
    suggestionId: String,
    nowMs: Long,
    snoozedUntilMs: Map<String, Long>
): Boolean = nowMs >= snoozedUntilMs.getOrDefault(suggestionId, 0L)
internal data class RecoveryOpenFeedback(
    val messageResId: Int,
    val messageArgs: List<Any> = emptyList(),
    val severity: ToastSeverity,
)

internal data class MediaRelinkOpenToastPart(
    val count: Int,
    val quantityResId: Int,
)

internal fun recoveryOpenFeedbackFor(
    outcome: ProjectAutoSave.LoadOutcome,
    expectedRecovery: Boolean,
    partialRestoreSummaryText: String? = null,
): RecoveryOpenFeedback? = when (outcome) {
    is ProjectAutoSave.LoadOutcome.Loaded -> if (outcome.report.isPartial) {
        RecoveryOpenFeedback(
            messageResId = R.string.vm_recovery_partial_toast,
            messageArgs = listOf(partialRestoreSummaryText ?: outcome.report.summary()),
            severity = ToastSeverity.Error,
        )
    } else {
        null
    }
    is ProjectAutoSave.LoadOutcome.FutureSchema -> RecoveryOpenFeedback(
        messageResId = R.string.vm_recovery_future_schema_toast,
        severity = ToastSeverity.Error,
    )
    is ProjectAutoSave.LoadOutcome.Corrupt -> RecoveryOpenFeedback(
        messageResId = R.string.vm_recovery_corrupt_toast,
        severity = ToastSeverity.Error,
    )
    ProjectAutoSave.LoadOutcome.NotFound -> if (expectedRecovery) {
        RecoveryOpenFeedback(
            messageResId = R.string.vm_recovery_not_found_toast,
            severity = ToastSeverity.Warning,
        )
    } else {
        null
    }
}

/**
 * Map the Settings "default codec" string onto the enum the export config uses.
 * The setting is stored as a bare token ("H264"/"HEVC"/"AV1"/"VP9"); an unrecognised
 * value falls back to H.264, which every Android device can encode.
 */
internal fun videoCodecForSetting(stored: String): VideoCodec =
    VideoCodec.entries.firstOrNull { it.name.equals(stored, ignoreCase = true) } ?: VideoCodec.H264

internal fun shouldBlockAutoSaveForRecoveryOutcome(outcome: ProjectAutoSave.LoadOutcome): Boolean {
    return outcome is ProjectAutoSave.LoadOutcome.FutureSchema ||
        outcome is ProjectAutoSave.LoadOutcome.Corrupt ||
        // A partial load is the dangerous case precisely because it looks fine: the
        // project opens, the user edits, and the first autosave overwrites the file
        // with the truncated version. Hold the write until they decide.
        (outcome is ProjectAutoSave.LoadOutcome.Loaded && outcome.report.isPartial)
}

internal fun mediaRelinkOpenToast(
    missingCount: Int,
    unknownCount: Int,
    healthBlockingCount: Int = 0,
    healthWarningCount: Int = 0
): List<MediaRelinkOpenToastPart> {
    val total = missingCount + unknownCount + healthBlockingCount + healthWarningCount
    if (total <= 0) return emptyList()
    val parts = buildList {
        if (missingCount > 0) add(MediaRelinkOpenToastPart(missingCount, R.plurals.vm_media_missing_sources))
        if (unknownCount > 0) add(MediaRelinkOpenToastPart(unknownCount, R.plurals.vm_media_unverified_sources))
        if (healthBlockingCount > 0) add(MediaRelinkOpenToastPart(healthBlockingCount, R.plurals.vm_media_repair_items))
        if (healthWarningCount > 0) add(MediaRelinkOpenToastPart(healthWarningCount, R.plurals.vm_media_warnings))
    }
    return parts
}

enum class PanelId {
    MEDIA_PICKER, EXPORT_SHEET, EFFECTS, TEXT_EDITOR, TRANSITION_PICKER,
    AUDIO, AI_TOOLS, TRANSFORM, CROP, VOICEOVER_RECORDER,
    COLOR_GRADING, AUDIO_MIXER, KEYFRAME_EDITOR, SPEED_CURVE,
    MASK_EDITOR, BLEND_MODE, BATCH_EXPORT, PIP_PRESETS, CHROMA_KEY,
    SCOPES, CAPTION_EDITOR, CHAPTER_MARKERS, SNAPSHOT_HISTORY,
    TEXT_TEMPLATES, MEDIA_MANAGER, AUDIO_NORM, RENDER_PREVIEW,
    CLOUD_BACKUP, TUTORIAL, UNDO_HISTORY, CAPTION_STYLE_GALLERY,
    BEAT_SYNC, SMART_REFRAME, SPEED_PRESETS,
    AUTO_EDIT, TTS, EFFECT_LIBRARY, NOISE_REDUCTION, STICKER_PICKER,
    DRAWING, MULTI_CAM, MARKER_LIST, SCRATCHPAD,
    // v3.69 — 15-feature wave (composite hub + drill-downs).
    V369_FEATURES,
    TEXT_BASED_EDIT, AUTO_CHAPTER, TALKING_HEAD, KARAOKE_CAPTIONS,
    CONTENT_ID, DIRECT_PUBLISH, FLASH_SAFETY, COLOR_BLIND_PREVIEW,
    AI_THUMBNAIL, AUDIO_DESCRIPTION,
    COMMAND_PALETTE,
    STORYBOARD,
    PROJECT_INSPECTOR
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

internal fun ensureEditorTracks(tracks: List<Track>): List<Track> {
    if (tracks.any { it.type == TrackType.TEXT }) return tracks
    val nextIndex = (tracks.maxOfOrNull { it.index } ?: -1) + 1
    return tracks + Track(
        type = TrackType.TEXT,
        index = nextIndex,
        trackHeight = 48,
        isCollapsed = true
    )
}

internal fun orderedTimelineTracks(tracks: List<Track>): List<Track> {
    fun priority(type: TrackType): Int = when (type) {
        TrackType.TEXT -> 0
        TrackType.OVERLAY -> 1
        TrackType.ADJUSTMENT -> 2
        TrackType.VIDEO -> 3
        TrackType.AUDIO -> 4
    }
    return tracks.sortedWith(compareBy<Track>({ priority(it.type) }, { it.index }))
}

internal fun autoEditClipFingerprint(clip: Clip): String {
    val canonical = listOf(
        clip.id,
        clip.sourceUri.toString(),
        clip.sourceDurationMs.toString(),
        clip.trimStartMs.toString(),
        clip.trimEndMs.toString()
    ).joinToString("|")
    return MessageDigest.getInstance("SHA-256")
        .digest(canonical.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}

/** Build render-safe excerpts without carrying edits or ownership from source clips. */
internal fun buildAutoEditExcerptClips(
    sourceClips: List<Clip>,
    proposal: AutoEditResult,
    newId: () -> String = { UUID.randomUUID().toString() }
): List<Clip> {
    require(proposal.segments.isNotEmpty()) { "Auto Edit proposal has no segments" }
    val sources = sourceClips.associateBy { it.id }
    return proposal.segments.map { segment ->
        val source = requireNotNull(sources[segment.clipId]) { "Auto Edit source is no longer present" }
        require(autoEditClipFingerprint(source) == segment.clipFingerprint) {
            "Auto Edit source changed after analysis"
        }
        require(segment.trimStartMs >= source.trimStartMs && segment.trimEndMs <= source.trimEndMs) {
            "Auto Edit range is outside the analyzed source"
        }
        require(segment.trimEndMs > segment.trimStartMs && segment.timelineEndMs > segment.timelineStartMs) {
            "Auto Edit segment has an invalid range"
        }
        source.copy(
            id = newId(),
            timelineStartMs = segment.timelineStartMs,
            trimStartMs = segment.trimStartMs,
            trimEndMs = segment.trimEndMs,
            effects = emptyList(),
            headTransition = null,
            tailTransition = null,
            volume = 1f,
            speed = 1f,
            isReversed = false,
            opacity = 1f,
            rotation = 0f,
            scaleX = 1f,
            scaleY = 1f,
            positionX = 0f,
            positionY = 0f,
            anchorX = 0.5f,
            anchorY = 0.5f,
            fadeInMs = 0L,
            fadeOutMs = 0L,
            keyframes = emptyList(),
            blendMode = BlendMode.NORMAL,
            speedCurve = null,
            colorGrade = null,
            masks = emptyList(),
            linkedClipId = null,
            isCompound = false,
            compoundClips = emptyList(),
            audioEffects = emptyList(),
            motionTrackingData = null,
            captions = emptyList(),
            groupId = null
        )
    }
}

data class EditorState(
    val project: Project = Project(),
    val tracks: List<Track> = listOf(
        Track(type = TrackType.TEXT, index = 0, trackHeight = 48, isCollapsed = true),
        Track(type = TrackType.VIDEO, index = 1),
        Track(type = TrackType.AUDIO, index = 2)
    ),
    val selectedClipId: String? = null,
    val selectedTrackId: String? = null,
    val playheadMs: Long = 0L,
    /** Transient project-time selection used by range-based timeline commands. */
    val selectedTimelineRange: TimelineRange? = null,
    val isPlaying: Boolean = false,
    val isPlaybackRequested: Boolean = false,
    val zoomLevel: Float = 1f,
    val scrollOffsetMs: Long = 0L,
    val totalDurationMs: Long = 0L,
    val currentTool: EditorTool = EditorTool.NONE,
    // Storage slices migrated out of the flat state constructor. Read-only
    // compatibility accessors below preserve existing UI reads while mutation
    // call sites move to nested domain state.
    val panel: EditorPanelState = EditorPanelState(),
    val export: EditorExportDomainState = EditorExportDomainState(),
    val textOverlays: List<TextOverlay> = emptyList(),
    val imageOverlays: List<ImageOverlay> = emptyList(),
    val timelineMarkers: List<TimelineMarker> = emptyList(),
    val globalTransitions: List<GlobalTransition> = emptyList(),
    val waveforms: Map<String, List<Float>> = emptyMap(),
    val undoStack: List<UndoAction> = emptyList(),
    val redoStack: List<UndoAction> = emptyList(),
    val toastMessage: String? = null,
    val toastSeverity: ToastSeverity = ToastSeverity.Info,
    // Set when a recent burst of destructive operations (≥3 deletes in 10s)
    // trips the bulk-change guard. The UI layer uses the nonce to render a
    // one-shot action snackbar ("N clips deleted — Undo"). Null when no
    // banner is pending or after the user interacts with it.
    val bulkUndoPrompt: BulkUndoPrompt? = null,
    val ai: EditorAiState = EditorAiState(),
    val caption: EditorCaptionState = EditorCaptionState(),
    val compound: EditorCompoundState = EditorCompoundState(),
    val copiedEffects: List<Effect> = emptyList(),
    val isRecordingVoiceover: Boolean = false,
    val voiceoverDurationMs: Long = 0L,
    val isLooping: Boolean = false,
    val activeScopeType: com.novacut.editor.ui.editor.ScopeType = com.novacut.editor.ui.editor.ScopeType.HISTOGRAM,
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
    // Project snapshots
    val projectSnapshots: List<ProjectSnapshot> = emptyList(),
    // Proxy
    val proxySettings: ProxySettings = ProxySettings(),
    // Auto-save indicator
    val saveIndicator: com.novacut.editor.model.SaveIndicatorState = com.novacut.editor.model.SaveIndicatorState.HIDDEN,
    val isProjectDirty: Boolean = false,
    // Undo history
    val undoHistoryEntries: List<com.novacut.editor.model.UndoHistoryEntry> = emptyList(),
    // Beat sync
    val isAnalyzingBeats: Boolean = false,
    // Editor mode
    val editorMode: EditorMode = EditorMode.PRO,
    // Timeline collapsed
    val isTimelineCollapsed: Boolean = false,
    // Split-screen before/after comparison
    val isSplitPreviewEnabled: Boolean = false,
    // Drawing overlay
    val drawingPaths: List<com.novacut.editor.model.DrawingPath> = emptyList(),
    val isDrawingMode: Boolean = false,
    val drawingColor: Long = 0xFFF38BA8L,
    val drawingStrokeWidth: Float = 4f,
    // v3.69 feature-wave state
    val v369: V369State = V369State(),
    // v3.71 object-aware editing scaffolding. The list lives in editor state so
    // the timeline can light up tracked-subject lanes the moment a tracker
    // populates them; persistence flows through AutoSaveState.trackedObjects.
    val trackedObjects: List<com.novacut.editor.model.TrackedObject> = emptyList(),
    val storyboardCards: List<com.novacut.editor.model.StoryboardCard> = emptyList(),
    /**
     * Set when the project on screen is not the project on disk: the restore dropped
     * elements. Autosave stays blocked while this is non-null, because the next write
     * would make the truncation permanent. Cleared by an explicit user decision.
     */
    val partialRestore: com.novacut.editor.engine.ProjectRestoreReport? = null,
    /**
     * The requested project is not in the database. The editor used to create a blank
     * project under the same id, which read as "your work is gone" with no explanation.
     */
    val projectNotFound: Boolean = false,
    val media: EditorMediaState = EditorMediaState()
) {
    val panels: PanelVisibility get() = panel.panels
    val selectedEffectId: String? get() = panel.selectedEffectId
    val editingTextOverlayId: String? get() = panel.editingTextOverlayId
    val exportConfig: ExportConfig get() = export.config
    val exportProgress: Float get() = export.progress
    val exportState: ExportState get() = export.state
    val lastExportedFilePath: String? get() = export.lastExportedFilePath
    val exportErrorMessage: String? get() = export.errorMessage
    val exportWarningMessage: String? get() = export.warningMessage
    val trimOptimizationDisclosure: Media3TrimOptimizationPolicy.Disclosure?
        get() = export.trimOptimizationDisclosure
    val exportStartTime: Long get() = export.startTime
    val renderSegments: List<SmartRenderEngine.RenderSegment> get() = export.renderSegments
    val renderSummary: SmartRenderEngine.SmartRenderSummary? get() = export.renderSummary
    val batchExportQueue: List<BatchExportItem> get() = export.batchQueue
    val savedExportConfig: ExportConfig? get() = export.savedConfig
    val exportHistory: List<com.novacut.editor.engine.ExportHistoryEntry> get() = export.history
    val aiRequirementPrompt: AiRequirementPrompt? get() = ai.requirementPrompt
    val aiModelRequirement: com.novacut.editor.engine.AiToolRequirements.ToolRequirement?
        get() = ai.modelRequirement
    val aiProcessingTool: String? get() = ai.processingTool
    val aiProcessingProgress: Float get() = ai.processingProgress
    val stabilizationPreview: StabilizationPreview? get() = ai.stabilizationPreview
    val aiSuggestion: AiSuggestion? get() = ai.suggestion
    val aiUsageLedger: List<AiUsageLedger.Entry> get() = ai.usageLedger
    val cutAssistantReview: com.novacut.editor.engine.CutAssistantEngine.ReviewSet?
        get() = ai.cutAssistantReview
    val autoEditProposal: AutoEditResult? get() = ai.autoEditProposal
    val isReframing: Boolean get() = ai.isReframing
    val isAutoEditing: Boolean get() = ai.isAutoEditing
    val isSynthesizingTts: Boolean get() = ai.isSynthesizingTts
    val isTtsAvailable: Boolean get() = ai.isTtsAvailable
    val isAnalyzingNoise: Boolean get() = ai.isAnalyzingNoise
    val noiseAnalysisResult: String? get() = ai.noiseAnalysisResult
    val backupImportFeedback: BackupImportFeedback? get() = media.backupImportFeedback
    val timelineExchangeFeedback: TimelineExchangeFeedback? get() = media.timelineExchangeFeedback
    val mediaRelinkReports: Map<String, MediaRelinkProbe.ClipRelinkReport> get() = media.relinkReports
    val compoundNavDepth: Int get() = compound.depth
    val compoundBreadcrumbText: String get() = compound.breadcrumbText
    val captionTranslationRows: List<com.novacut.editor.engine.CaptionTranslationEngine.EditorRow>
        get() = caption.translationRows
    val captionTranslationSourceLang: String get() = caption.sourceLang
    val captionTranslationTargetLang: String? get() = caption.targetLang
    val captionTranslationQuality: com.novacut.editor.engine.CaptionTranslationEngine.LanguagePairQuality?
        get() = caption.quality
    val captionTranslationVariant: com.novacut.editor.engine.CaptionTranslationEngine.ModelVariant
        get() = caption.variant
    val captionTranslationUnavailable: Boolean get() = caption.translationUnavailable
    val captionTranslationOffline: Boolean get() = caption.translationOffline
    val captionImportPreview: CaptionImportEngine.Preview? get() = caption.captionImportPreview
}

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
    MUTE_RANGE("Mute range"),
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
    val beatMarkers: List<Long> = emptyList(),
    val trackedObjects: List<com.novacut.editor.model.TrackedObject> = emptyList(),
    val globalTransitions: List<GlobalTransition> = emptyList(),
    val storyboardCards: List<StoryboardCard> = emptyList(),
    val transcript: Transcript? = null,
    val playheadMs: Long = 0L,
    val selectedClipId: String? = null,
    val selectedTrackId: String? = null,
    val selectedClipIds: Set<String> = emptySet(),
    val aiUsageLedger: List<AiUsageLedger.Entry> = emptyList()
)

internal data class SnapshotDeletionResult(
    val state: EditorState,
    val deleted: ProjectSnapshot,
)

/** Pure state transition used by the editor and its recoverability tests. */
internal fun deleteSnapshotFromState(
    state: EditorState,
    snapshotId: String,
): SnapshotDeletionResult? {
    val snapshot = state.projectSnapshots.firstOrNull { it.id == snapshotId } ?: return null
    return SnapshotDeletionResult(
        state = state.copy(
            projectSnapshots = state.projectSnapshots.filterNot { it.id == snapshotId }
        ),
        deleted = snapshot,
    )
}

/**
 * Restore only document-edit fields from an undo action. Project snapshots are
 * deliberate metadata owned by the live editor state, so undoing an edit must
 * leave them untouched.
 */
internal fun EditorState.withUndoDocument(action: UndoAction): EditorState = copy(
    tracks = action.tracks,
    textOverlays = action.textOverlays,
    imageOverlays = action.imageOverlays,
    timelineMarkers = action.timelineMarkers,
    chapterMarkers = action.chapterMarkers,
    drawingPaths = action.drawingPaths,
    beatMarkers = action.beatMarkers,
    trackedObjects = action.trackedObjects,
    globalTransitions = action.globalTransitions,
    storyboardCards = action.storyboardCards,
    v369 = v369.copy(transcript = action.transcript),
    selectedClipId = action.selectedClipId,
    selectedTrackId = action.selectedTrackId,
    selectedClipIds = action.selectedClipIds,
    ai = ai.copy(usageLedger = action.aiUsageLedger)
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

data class BackupImportFeedback(
    val succeeded: Boolean,
    val title: String,
    val body: String,
    val report: ProjectArchive.ImportReport,
    val errorMessage: String? = null
)

data class TimelineExchangeFeedback(
    val succeeded: Boolean,
    val title: String,
    val body: String,
    val outputFileName: String?,
    val report: TimelineExchangeValidator.Report
)

@HiltViewModel
class EditorViewModel @Inject constructor(
    private val videoEngine: VideoEngine,
    private val audioEngine: AudioEngine,
    private val aiFeatures: AiFeatures,
    private val voiceoverEngine: VoiceoverRecorderEngine,
    private val templateManager: TemplateManager,
    private val incomingDocumentImportRouter: IncomingDocumentImportRouter,
    private val proxyEngine: ProxyEngine,
    private val settingsRepo: SettingsRepository,
    private val ttsEngine: com.novacut.editor.engine.TtsEngine,
    private val effectShareEngine: com.novacut.editor.engine.EffectShareEngine,
    private val stylePackManager: com.novacut.editor.engine.StylePackManager,
    private val noiseReductionEngine: NoiseReductionEngine,
    private val beatDetectionEngine: BeatDetectionEngine,
    private val loudnessEngine: LoudnessEngine,
    private val audioMasteringEngine: com.novacut.editor.engine.AudioMasteringEngine,
    private val frameInterpolationEngine: FrameInterpolationEngine,
    private val inpaintingEngine: InpaintingEngine,
    private val upscaleEngine: UpscaleEngine,
    private val videoMattingEngine: VideoMattingEngine,
    private val stabilizationEngine: StabilizationEngine,
    private val smartReframeEngine: SmartReframeEngine,
    private val editingSuggestionEngine: EditingSuggestionEngine,
    private val timelineExportCoordinator: TimelineExportCoordinator,
    private val cutAssistantEngine: com.novacut.editor.engine.CutAssistantEngine,
    private val proxyWorkflowEngine: ProxyWorkflowEngine,
    private val multiCamEngine: MultiCamEngine,
    private val mediaImportEngine: MediaImportEngine,
    private val mediaRelinkProbe: MediaRelinkProbe,
    private val mediaDiagnosticsProbe: MediaDiagnosticsProbe,
    private val metadataSidecarEngine: MetadataSidecarEngine,
    private val overlayAssetStore: OverlayAssetStore,
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
    private val c2paExportEngine: C2paExportEngine,
    private val aiThumbnailEngine: AiThumbnailEngine,
    private val audioDescriptionEngine: AudioDescriptionEngine,
    private val stylusMidiEngine: StylusMidiEngine,
    private val captionTranslationEngine: com.novacut.editor.engine.CaptionTranslationEngine,
    private val fontRegistry: FontRegistry,
    private val lutRegistry: com.novacut.editor.engine.LutRegistry,
    private val exportIncidentStore: ExportIncidentStore,
    private val ffmpegEngine: com.novacut.editor.engine.FFmpegEngine,
    private val editorCoordinatorSet: EditorCoordinatorSet,
    @ApplicationContext private val appContext: Context,
    private val savedStateHandle: SavedStateHandle,
    private val productHealthLedger: ProductHealthLedger,
    private val connectivityObserver: ConnectivityObserver,
) : ViewModel() {

    private val documentCoordinator get() = editorCoordinatorSet.document
    private val projectTransferCoordinator get() = editorCoordinatorSet.projectTransfer
    private val backgroundJobCoordinator get() = editorCoordinatorSet.backgroundJobs
    private val playbackCoordinator get() = editorCoordinatorSet.playback

    private val projectId: String? = savedStateHandle["projectId"]
    private val expectRecovery: Boolean = savedStateHandle["expectRecovery"] ?: false
    private val replayTutorial: Boolean = savedStateHandle["replayTutorial"] ?: false
    private var suggestionSnoozedUntilMs: Map<String, Long> =
        savedStateHandle.get<java.util.HashMap<String, Long>>(SUGGESTION_SNOOZE_STATE_KEY)
            ?.toMap()
            ?: emptyMap()
    private var recoveryOpenComplete = false
    private var autoSaveBlockedByRecovery = false
    private var latestSettings: AppSettings? = null
    private var lastAutoSaveRunning: Boolean? = null
    private var lastAutoSaveIntervalSec: Int? = null
    private val savedStateTracker = SavedStateTracker()
    private var mediaRelinkProbeJob: Job? = null
    private var captionTranslationJob: Job? = null
    private var autoEditJob: Job? = null
    private var autoEditGenerationId: Long = 0L
    private var cutAssistantReviewJob: Job? = null
    private val projectMediaManifestCacheLock = Any()
    private var projectMediaManifestCache: CachedProjectMediaManifest? = null

    private val stateStore = EditorStateStore()
    private val _state: MutableStateFlow<EditorState> get() = stateStore.mutable
    val state: StateFlow<EditorState> get() = stateStore.state

    /** The most recently deleted checkpoint remains restorable until dismissed or replaced. */
    private val _restorableSnapshot = MutableStateFlow<ProjectSnapshot?>(null)
    val restorableSnapshot: StateFlow<ProjectSnapshot?> = _restorableSnapshot.asStateFlow()

    // Fast-path playhead flow — avoids full EditorState copy during playback
    private val _playheadMs = MutableStateFlow(0L)
    val playheadMs: StateFlow<Long> = _playheadMs.asStateFlow()

    val engine get() = videoEngine

    /** Live internet state used to gate model downloads and caption translation controls. */
    val networkAvailable: StateFlow<Boolean> = connectivityObserver.isOnline

    private fun text(resId: Int, vararg args: Any): String =
        appContext.getString(resId, *args)

    // --- Delegates (extracted to reduce ViewModel size) ---

    val colorGradingDelegate = ColorGradingDelegate(
        stateFlow = _state, appContext = appContext, lutRegistry = lutRegistry,
        scope = viewModelScope, saveUndoState = ::saveUndoState, showToast = ::showToast,
        pauseIfPlaying = ::pauseIfPlaying, dismissedPanelState = ::dismissedPanelState,
        getSelectedClip = ::getSelectedClip, updatePreview = ::updatePreview,
        saveProject = ::saveProject
    )

    val audioMixerDelegate = AudioMixerDelegate(
        stateFlow = _state, beatDetectionEngine = beatDetectionEngine,
        loudnessEngine = loudnessEngine, audioMasteringEngine = audioMasteringEngine,
        appContext = appContext,
        scope = viewModelScope,
        saveUndoState = ::saveUndoState, showToast = ::showToast,
        pauseIfPlaying = ::pauseIfPlaying, dismissedPanelState = ::dismissedPanelState,
        refreshPreview = ::rebuildPlayerTimeline,
        saveProject = ::saveProject
    )

    val exportDelegate = ExportDelegate(
        stateFlow = _state, videoEngine = videoEngine, appContext = appContext,
        scope = viewModelScope, showToast = ::showToast,
        pauseIfPlaying = ::pauseIfPlaying, dismissedPanelState = ::dismissedPanelState,
        showExportSheet = ::showExportSheet,
        streamCopyEngine = streamCopyEngine,
        c2paExportEngine = c2paExportEngine,
        mediaHealthPreflight = ::analyzeMediaHealthForState,
        projectDependencyManifest = { editorState ->
            val fontFiles = fontRegistry.listImportedFonts().associate { font ->
                fontRegistry.fontFamilyKey(font.fileName) to font.file.absolutePath
            }
            ProjectDependencyManifest.collect(
                state = buildAutoSaveState(editorState),
                editorInputs = ProjectDependencyEditorInputs(
                    watermarkReference = editorState.exportConfig.watermark?.sourceUri?.toString(),
                    customFontReferencesByFamily = fontFiles,
                ),
                probe = AndroidProjectDependencyProbe(appContext) { dependency ->
                    dependency == SEGMENTATION_MODEL_DEPENDENCY &&
                        aiFeatures.segmentationEngine.isReady()
                },
            )
        },
        audioEngine = audioEngine,
        exportIncidentStore = exportIncidentStore,
        appVersion = com.novacut.editor.ClearCutApp.VERSION,
        ffmpegEngine = ffmpegEngine,
        includeDiagnosticRawErrorText = { latestSettings?.includeDiagnosticRawErrorText == true },
        projectFingerprint = ::currentProjectFingerprint,
    )

    val aiToolsDelegate = AiToolsDelegate(
        stateFlow = _state, aiFeatures = aiFeatures, templateManager = templateManager,
        frameInterpolationEngine = frameInterpolationEngine, inpaintingEngine = inpaintingEngine,
        upscaleEngine = upscaleEngine, videoMattingEngine = videoMattingEngine,
        stabilizationEngine = stabilizationEngine,
        appContext = appContext, scope = viewModelScope,
        saveUndoState = ::saveUndoState, showToast = ::showToast,
        getSelectedClip = ::getSelectedClip,
        getSelectedMask = {
            val selectedMaskId = _state.value.selectedMaskId
            getSelectedClip()?.let { clip ->
                clip.masks.firstOrNull { it.id == selectedMaskId }
                    ?: clip.masks.singleOrNull()
            }
        },
        setClipTransform = { id, px, py, sx, sy, rot ->
            setClipTransform(id, positionX = px, positionY = py, scaleX = sx, scaleY = sy, rotation = rot)
        },
        rebuildPlayerTimeline = ::rebuildPlayerTimeline, saveProject = ::saveProject,
        videoEngine = videoEngine,
        recalculateDuration = ::recalculateDuration,
        settingsRepo = settingsRepo,
        recordAiUsage = ::recordAiUsage,
        productHealthLedger = productHealthLedger
    )

    val clipEditingDelegate = ClipEditingDelegate(
        stateFlow = _state, videoEngine = videoEngine,
        mediaImportEngine = mediaImportEngine,
        appContext = appContext,
        scope = viewModelScope, saveUndoState = ::saveUndoState, showToast = ::showToast,
        beginGestureUndo = ::beginTimelineGestureUndo,
        markGestureMutation = ::markTimelineGestureMutation,
        finishGestureUndo = ::finishTimelineGestureUndo,
        rebuildPlayerTimeline = ::rebuildPlayerTimeline, saveProject = ::saveProject,
        refreshExtendedTrimPreview = ::refreshExtendedTrimPreview,
        seekPreviewTo = ::seekTo,
        currentPlayheadMs = { _playheadMs.value },
        updateLivePlayheadMs = { _playheadMs.value = it },
        quantizeTimeMs = ::quantizeProjectTimeMs,
        previousFrameTimeMs = ::previousProjectFrameTimeMs,
        recalculateDuration = ::recalculateDuration,
        onClipAdded = { clipId, uri ->
            val mediaVersion = _state.value.tracks
                .asSequence()
                .flatMap { it.clips.asSequence() }
                .firstOrNull { it.id == clipId }
                ?.timelineMediaJobIdentity()
                ?.version
                ?: "$clipId|$uri"
            viewModelScope.launch(Dispatchers.IO) {
                val (w, h) = videoEngine.getVideoResolution(uri)
                if (w > 0 && h > 0) {
                    proxyWorkflowEngine.registerMedia(
                        clipId = clipId,
                        uri = uri,
                        width = w,
                        height = h,
                        mediaVersion = mediaVersion,
                    )
                    if (h > 1080) enqueueProxyGeneration()
                }
            }
            // Auto-fit on first clip: when we go from empty→populated, frame the full
            // project so the user immediately sees the whole clip. Matches CapCut /
            // VN UX where importing the first asset fills the editable area.
            requestInitialFitIfNeeded()
            refreshMediaRelinkReports(openPanelOnProblems = false)
        }
    )

    val effectsDelegate = EffectsDelegate(
        stateFlow = _state, saveUndoState = ::saveUndoState, showToast = ::showToast,
        updatePreview = ::updatePreview, rebuildPlayerTimeline = ::rebuildPlayerTimeline,
        saveProject = ::saveProject, getSelectedClip = ::getSelectedClip,
        recalculateDuration = ::recalculateDuration,
        appContext = appContext
    )

    val overlayDelegate = OverlayDelegate(
        stateFlow = _state, saveUndoState = ::saveUndoState, showToast = ::showToast,
        saveProject = ::saveProject, quantizeTimeMs = ::quantizeProjectTimeMs,
        appContext = appContext
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
        audioEngine = audioEngine, videoEngine = videoEngine,
        acoustIdApiKey = { latestSettings?.acoustIdApiKey }
    )

    // Whisper model state (exposed via delegate for UI binding)
    val whisperModelState get() = aiToolsDelegate.whisperModelState
    val whisperDownloadProgress get() = aiToolsDelegate.whisperDownloadProgress
    val segmentationModelState get() = aiToolsDelegate.segmentationModelState
    val segmentationDownloadProgress get() = aiToolsDelegate.segmentationDownloadProgress
    val inpaintingModelState get() = aiToolsDelegate.inpaintingModelState
    val inpaintingDownloadProgress get() = aiToolsDelegate.inpaintingDownloadProgress

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

    // Confirm-before-delete / show-waveforms / haptics (driven by user settings)
    private val _confirmBeforeDelete = MutableStateFlow(true)
    private val _showWaveforms = MutableStateFlow(true)
    private val _hapticsEnabled = MutableStateFlow(true)
    val confirmBeforeDelete: Boolean get() = _confirmBeforeDelete.value
    val showWaveforms: Boolean get() = _showWaveforms.value

    /** Settings > Haptic feedback. Timeline gestures consult this before buzzing. */
    val hapticsEnabled: StateFlow<Boolean> = _hapticsEnabled

    // Stored outside EditorState to avoid recomposition on every resize
    @Volatile
    private var timelineWidthPx: Float = 0f
    private data class WaveformLoadHandle(
        val identity: TimelineMediaJobIdentity,
        var job: Job? = null,
    )

    private data class ProxyGenerationRequest(
        val identity: TimelineMediaJobIdentity,
        val sourceUri: Uri,
        val resolution: ProxyResolution,
    )

    private val waveformLoadJobs = java.util.concurrent.ConcurrentHashMap<String, WaveformLoadHandle>()
    private val loadedWaveformIdentities = java.util.concurrent.ConcurrentHashMap<String, TimelineMediaJobIdentity>()
    private var proxyGenerationJob: Job? = null

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
        exportDelegate.loadExportHistory()
        if (replayTutorial) {
            showTutorial()
        }

        // Load existing project if projectId provided, then restore auto-save
        viewModelScope.launch {
            val openResult = documentCoordinator.open(
                projectId = projectId,
                recoveryId = autoSaveId,
            )
            if (openResult.projectNotFound) {
                // Opening a project that no longer exists used to silently create a
                // blank one under the same id -- the user asked for their work and
                // got an empty timeline that looked like it. Report it instead and
                // let the caller route back to the dashboard.
                AppLog.w("EditorViewModel", "Project $projectId no longer exists; refusing to recreate it blank")
                _state.update { it.copy(projectNotFound = true) }
                recoveryOpenComplete = true
                autoSaveBlockedByRecovery = true
                showToast(text(R.string.editor_project_not_found), ToastSeverity.Error)
                return@launch
            }
            openResult.project?.let { project ->
                _state.update { it.copy(project = project) }
            }

            // Restore auto-save AFTER Room load to avoid race condition.
            // Use the schema-aware outcome path so corrupt/future files are
            // surfaced and preserved instead of being silently treated as
            // missing and overwritten by the next autosave tick.
            handleRecoveryOpenOutcome(requireNotNull(openResult.recovery))
            exportDelegate.restoreBatchExportQueue(
                com.novacut.editor.engine.BatchExportPlanContext(
                    projectId = _state.value.project.id,
                    projectFingerprint = currentProjectFingerprint(),
                )
            )
        }

        viewModelScope.launch {
            _state
                .map(::dirtyTrackingKey)
                .distinctUntilChanged()
                .collectLatest {
                    if (!recoveryOpenComplete || autoSaveBlockedByRecovery) return@collectLatest
                    delay(100L)
                    val current = currentProjectFingerprint()
                    applySavedStateStatus(savedStateTracker.contentChanged(current))
                }
        }

        viewModelScope.launch {
            videoEngine.exportProgress.collect { progress ->
                _state.update { it.copyExport { export -> export.copy(progress = progress) } }
            }
        }
        viewModelScope.launch {
            videoEngine.exportState.collect { exportState ->
                _state.update { it.copyExport { export -> export.copy(state = exportState) } }
                if (exportState == ExportState.CANCELLED) {
                    showToast(text(R.string.vm_export_cancelled_toast))
                }
            }
        }
        viewModelScope.launch {
            videoEngine.exportWarningMessage.collect { warningMessage ->
                _state.update {
                    it.copyExport { export -> export.copy(warningMessage = warningMessage) }
                }
                warningMessage?.takeIf { it.isNotBlank() }?.let { message ->
                    showToast(message, ToastSeverity.Warning)
                }
            }
        }
        viewModelScope.launch {
            videoEngine.trimOptimizationDisclosure.collect { disclosure ->
                _state.update {
                    it.copyExport { export -> export.copy(trimOptimizationDisclosure = disclosure) }
                }
            }
        }

        playbackCoordinator.start(
            scope = viewModelScope,
            callbacks = EditorPlaybackCoordinator.Callbacks(
                snapshot = {
                    val state = _state.value
                    EditorPlaybackCoordinator.PlaybackSnapshot(
                        playheadMs = _playheadMs.value,
                        totalDurationMs = state.totalDurationMs,
                        scrollOffsetMs = state.scrollOffsetMs,
                        zoomLevel = state.zoomLevel,
                        timelineWidthPx = timelineWidthPx,
                        maxTimelineScrollOffsetMs = maxTimelineScrollOffset(state),
                        isPlaybackRequested = state.isPlaybackRequested,
                    )
                },
                onPlayingChanged = { playing ->
                    _state.update { it.copy(isPlaying = playing) }
                },
                onPlaybackRequestedChanged = { requested ->
                    _state.update { it.copy(isPlaybackRequested = requested) }
                },
                onPlaybackEnded = { totalMs ->
                    _playheadMs.value = totalMs
                    _state.update {
                        it.copy(
                            isPlaying = false,
                            isPlaybackRequested = false,
                            playheadMs = totalMs,
                        )
                    }
                },
                onSurfaceRecoveryPosition = { recoveryPositionMs ->
                    if (recoveryPositionMs != _playheadMs.value) {
                        _playheadMs.value = recoveryPositionMs
                        _state.update { it.copy(playheadMs = recoveryPositionMs) }
                    }
                    _state.update { it.copy(isPlaybackRequested = true) }
                },
                onPlaybackStartFailed = {
                    _state.update { it.copy(isPlaying = false, isPlaybackRequested = false) }
                    showToast(text(R.string.vm_preview_playback_failed_toast), ToastSeverity.Error)
                },
                onUnrecoverableError = { error ->
                    showToast(text(R.string.vm_preview_playback_failed_toast), ToastSeverity.Error)
                    AppLog.w("EditorViewModel", "Preview playback failed", error)
                },
                onFrame = { frame ->
                    _playheadMs.value = frame.positionMs
                    val state = _state.value
                    val playheadDriftMs = kotlin.math.abs(frame.positionMs - state.playheadMs)
                    if (frame.scrollOffsetMs != state.scrollOffsetMs || playheadDriftMs >= 200L) {
                        _state.update { current ->
                            current.copy(
                                playheadMs = frame.positionMs,
                                scrollOffsetMs = frame.scrollOffsetMs,
                            )
                        }
                    }
                },
            ),
        )

        // Apply user settings (export defaults + auto-save)
        var appliedDefaults = false
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
                        s.copyExport { export ->
                            export.copy(
                                config = s.exportConfig.copy(
                                    resolution = settings.defaultResolution,
                                    frameRate = settings.defaultFrameRate,
                                    quality = quality,
                                    codec = videoCodecForSetting(settings.defaultCodec)
                                )
                            )
                        }
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

                // Only start autosave after the schema-aware recovery open path
                // has completed. Future-schema/corrupt recovery files block
                // writes so the preserved autosave is not overwritten.
                applyAutoSaveSettings(settings)

                // Sync snap settings
                _snapToBeat.value = settings.snapToBeat
                _snapToMarker.value = settings.snapToMarker

                // Sync confirm-before-delete and waveform settings
                _confirmBeforeDelete.value = settings.confirmBeforeDelete
                _showWaveforms.value = settings.showWaveforms
                _hapticsEnabled.value = settings.hapticEnabled
                // The thumbnail cache used to be hard-wired to heap/8, so the size
                // control in Settings changed a stored number and nothing else.
                videoEngine.setThumbnailCacheSizeMb(settings.thumbnailCacheSizeMb)
                if (_state.value.proxySettings.resolution != settings.proxyResolution) {
                    _state.update { state ->
                        state.copy(
                            proxySettings = state.proxySettings.copy(resolution = settings.proxyResolution)
                        )
                    }
                }
                if (settings.showWaveforms) {
                    preloadVisibleWaveforms(_state.value)
                } else {
                    cancelWaveformLoads()
                }
            }
        }
    }

    private fun handleRecoveryOpenOutcome(outcome: ProjectAutoSave.LoadOutcome) {
        if (outcome is ProjectAutoSave.LoadOutcome.Loaded) {
            restoreLoadedRecovery(outcome.state, outcome.document?.project)
            _state.update { it.copy(partialRestore = outcome.report.takeIf { r -> r.isPartial }) }
        }
        autoSaveBlockedByRecovery = shouldBlockAutoSaveForRecoveryOutcome(outcome)
        recoveryOpenComplete = true
        if (autoSaveBlockedByRecovery) {
            showSaveIndicator(com.novacut.editor.model.SaveIndicatorState.ERROR)
        } else {
            applySavedStateStatus(savedStateTracker.establishBaseline(currentProjectFingerprint()))
        }
        val localizedPartialSummary = (outcome as? ProjectAutoSave.LoadOutcome.Loaded)
            ?.report
            ?.takeIf { it.isPartial }
            ?.let { partialRestoreSummary(appContext.resources, it) }
        recoveryOpenFeedbackFor(outcome, expectRecovery, localizedPartialSummary)?.let { feedback ->
            showToast(text(feedback.messageResId, *feedback.messageArgs.toTypedArray()), feedback.severity)
        }
        applyAutoSaveSettings()
        if (outcome is ProjectAutoSave.LoadOutcome.Loaded) {
            refreshMediaRelinkReports(openPanelOnProblems = true)
        }
    }

    /**
     * Accept the partially restored project as-is. Saving resumes, which means the
     * next write drops whatever the restore could not read — so this is only ever
     * reached through an explicit choice, never a timeout or a dismissal.
     */
    fun keepPartialRestore() {
        if (_state.value.partialRestore == null) return
        val lost = _state.value.partialRestore?.let { partialRestoreSummary(appContext.resources, it) }.orEmpty()
        _state.update { it.copy(partialRestore = null) }
        autoSaveBlockedByRecovery = false
        applySavedStateStatus(savedStateTracker.establishBaseline(currentProjectFingerprint()))
        applyAutoSaveSettings()
        saveProject()
        showToast(text(R.string.vm_partial_restore_keep_toast, lost), ToastSeverity.Warning)
    }

    /**
     * Fall back to the previous autosave, which predates the write that produced the
     * partial file and may still hold the dropped elements. Failing that, the project
     * stays exactly as it is and saving stays blocked — never a silent downgrade.
     */
    fun restorePartialFromBackup() {
        if (_state.value.partialRestore == null) return
        val id = projectId ?: _state.value.project.id
        viewModelScope.launch {
            when (val outcome = documentCoordinator.loadBackupWithOutcome(id)) {
                is ProjectAutoSave.LoadOutcome.Loaded -> {
                    restoreLoadedRecovery(outcome.state, outcome.document?.project)
                    if (outcome.report.isPartial) {
                        _state.update { it.copy(partialRestore = outcome.report) }
                        showToast(
                            text(
                                R.string.vm_partial_restore_backup_incomplete,
                                partialRestoreSummary(appContext.resources, outcome.report),
                            ),
                            ToastSeverity.Error,
                        )
                    } else {
                        _state.update { it.copy(partialRestore = null) }
                        autoSaveBlockedByRecovery = false
                        applySavedStateStatus(savedStateTracker.establishBaseline(currentProjectFingerprint()))
                        applyAutoSaveSettings()
                        saveProject()
                        showToast(text(R.string.vm_partial_restore_backup_success), ToastSeverity.Info)
                    }
                }
                else -> showToast(
                    text(R.string.vm_partial_restore_backup_missing),
                    ToastSeverity.Error,
                )
            }
        }
    }

    private fun restoreLoadedRecovery(recovery: AutoSaveState, recoveredProject: Project? = null) {
        val mediaHealthReport = analyzeMediaHealthForRecovery(recovery)
        _state.update { current ->
            current.copy(
                project = recoveredProject?.takeIf { it.id == current.project.id } ?: current.project,
                tracks = ensureEditorTracks(recovery.tracks.ifEmpty { current.tracks }),
                textOverlays = recovery.textOverlays,
                imageOverlays = recovery.imageOverlays,
                timelineMarkers = recovery.timelineMarkers,
                globalTransitions = recovery.globalTransitions,
                drawingPaths = recovery.drawingPaths,
                playheadMs = recovery.playheadMs,
                chapterMarkers = recovery.chapterMarkers,
                beatMarkers = recovery.beatMarkers,
                v369 = current.v369.copy(transcript = recovery.transcript ?: current.v369.transcript),
                trackedObjects = recovery.trackedObjects.ifEmpty { current.trackedObjects },
                storyboardCards = recovery.storyboardCards.ifEmpty { current.storyboardCards },
                ai = current.ai.copy(usageLedger = recovery.aiUsageLedger),
                export = current.export.copy(
                    config = current.export.config.copy(watermark = recovery.exportWatermark)
                ),
                media = current.media.copy(
                    healthReport = mediaHealthReport,
                    mediaAssets = recovery.mediaAssets,
                ),
                totalDurationMs = recovery.tracks.maxOfOrNull { t ->
                    t.clips.maxOfOrNull { c -> t.effectiveTimelineEndMs(c) } ?: 0L
                }?.coerceAtLeast(0L) ?: 0L
            )
        }
        _playheadMs.value = recovery.playheadMs
        if (recovery.tracks.flatMap { it.clips }.isNotEmpty()) {
            rebuildPlayerTimeline()
        }
        preloadVisibleWaveforms(_state.value)
        backfillRecoveredManagedMediaAssets(recovery)
        // Restored content may have arrived AFTER the timeline laid out with
        // zero clips. In that race the first setTimelineWidth call saw an
        // empty project and skipped the fit. Fire now so the user opens a
        // restored project to the whole timeline framed, not a tiny window.
        requestInitialFitIfNeeded()
    }

    private fun backfillRecoveredManagedMediaAssets(recovery: AutoSaveState) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = backfillManagedMediaAssetSidecars(appContext, recovery)
            recovery.mediaAssets.forEach { asset ->
                writeManagedMediaAssetAnnotations(appContext, asset)
            }
            if (result.sidecarsCreated > 0) {
                invalidateProjectMediaManifestCache()
                AppLog.i(
                    "EditorViewModel",
                    "Backfilled ${result.sidecarsCreated} media asset sidecar(s) from ${result.referencesScanned} restored reference(s)"
                )
            }
            enqueueMediaHashJob()
        }
    }

    private fun enqueueMediaHashJob() {
        backgroundJobCoordinator.enqueueMediaHashJob()
    }

    private fun applyAutoSaveSettings(settings: AppSettings? = latestSettings) {
        val current = settings ?: return
        latestSettings = current
        val shouldRun = projectId != null &&
            recoveryOpenComplete &&
            !autoSaveBlockedByRecovery &&
            current.autoSaveEnabled
        val intervalSec = if (shouldRun) current.autoSaveIntervalSec else null
        if (lastAutoSaveRunning == shouldRun && lastAutoSaveIntervalSec == intervalSec) {
            return
        }

        lastAutoSaveRunning = shouldRun
        lastAutoSaveIntervalSec = intervalSec
        if (shouldRun) {
            documentCoordinator.startAutoSave(
                projectId ?: _state.value.project.id,
                intervalMs = current.autoSaveIntervalSec * 1000L,
                onSaveResult = { succeeded, request ->
                    viewModelScope.launch {
                        run {
                            val currentFingerprint = currentProjectFingerprint()
                            val attempt = request?.let {
                                SaveAttempt(it.saveToken, it.documentFingerprint)
                            }
                            if (succeeded && request != null && attempt != null) {
                                try {
                                    if (currentFingerprint == request.documentFingerprint) {
                                        documentCoordinator.saveDatabase(
                                            ProjectDocumentApplicator.capture(
                                                project = request.project,
                                                state = request.state,
                                            )
                                        )
                                        applySavedProjectMetadata(request.project)
                                    }
                                    applySavedStateStatus(
                                        savedStateTracker.saveSucceeded(attempt, currentProjectFingerprint())
                                    )
                                } catch (e: Exception) {
                                    if (e is CancellationException) throw e
                                    AppLog.e("EditorVM", "Periodic project save failed", e)
                                    applySavedStateStatus(
                                        savedStateTracker.saveFailed(attempt, currentProjectFingerprint())
                                    )
                                }
                            } else if (attempt != null) {
                                applySavedStateStatus(
                                    savedStateTracker.saveFailed(attempt, currentProjectFingerprint())
                                )
                            } else {
                                applySavedStateStatus(
                                    savedStateTracker.externalSaveFailed(currentProjectFingerprint())
                                )
                            }
                        }
                    }
                }
            ) {
                val s = _state.value
                val project = projectForSave(s)
                val state = buildAutoSaveState(s, project.id)
                val fingerprint = projectStateFingerprint(project, state)
                val (attempt, _) = savedStateTracker.beginSave(fingerprint)
                showSaveIndicator(com.novacut.editor.model.SaveIndicatorState.SAVING)
                AutoSaveRequest(
                    project = project,
                    state = state,
                    saveToken = attempt.token,
                    documentFingerprint = attempt.fingerprint,
                )
            }
        } else {
            documentCoordinator.stopAutoSave()
        }
    }

    /** Rebuild ExoPlayer timeline from current tracks. Call after any clip mutation. */
    private fun rebuildPlayerTimeline() {
        previewRebuildJob?.cancel()
        previewRebuildJob = null
        val livePlayheadMs = _playheadMs.value
        _state.update { state ->
            normalizeTimelineState(state.copy(playheadMs = livePlayheadMs))
        }
        _playheadMs.value = _state.value.playheadMs
        val missingClipIds = _state.value.media.relinkReports
            .filter { it.value.state == MediaRelinkProbe.RelinkState.MISSING }
            .keys
        videoEngine.prepareTimeline(
            tracks = _state.value.tracks,
            missingClipIds = missingClipIds,
            startPositionMs = _state.value.playheadMs,
            config = _state.value.exportConfig.copy(aspectRatio = _state.value.project.aspectRatio),
            trackedObjects = _state.value.trackedObjects,
        )
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
                enqueueWaveformLoad(clip)
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

    private fun enqueueWaveformLoad(clip: Clip) {
        val clipId = clip.id
        val identity = clip.timelineMediaJobIdentity()
        val currentClip = _state.value.tracks
            .asSequence()
            .flatMap { it.clips.asSequence() }
            .firstOrNull { it.id == clipId }
        if (!shouldApplyMediaJobResult(identity, currentClip?.timelineMediaJobIdentity())) return

        val loadedIdentity = loadedWaveformIdentities[clipId]
        if (_state.value.waveforms.containsKey(clipId) && loadedIdentity == identity) return
        if (_state.value.waveforms.containsKey(clipId) && loadedIdentity != identity) {
            _state.update { state ->
                if (state.waveforms.containsKey(clipId)) {
                    state.copy(waveforms = state.waveforms - clipId)
                } else {
                    state
                }
            }
            loadedWaveformIdentities.remove(clipId)
        }

        val existing = waveformLoadJobs[clipId]
        if (existing?.identity == identity && existing.job?.isActive == true) return
        existing?.job?.cancel()

        val handle = WaveformLoadHandle(identity)
        waveformLoadJobs[clipId] = handle
        handle.job = viewModelScope.launch {
            try {
                val waveform = audioEngine.extractWaveform(identity.sourceUri.toUri()).toList()
                var shouldRefreshSuggestion = false
                var accepted = false
                _state.update { state ->
                    val currentIdentity = state.tracks
                        .asSequence()
                        .flatMap { it.clips.asSequence() }
                        .firstOrNull { it.id == clipId }
                        ?.timelineMediaJobIdentity()
                    if (!shouldApplyMediaJobResult(identity, currentIdentity) ||
                        state.waveforms.containsKey(clipId) && loadedWaveformIdentities[clipId] == identity
                    ) {
                        state
                    } else {
                        accepted = true
                        shouldRefreshSuggestion = state.selectedClipId == clipId
                        loadedWaveformIdentities[clipId] = identity
                        state.copy(waveforms = state.waveforms + (clipId to waveform))
                    }
                }
                if (accepted && shouldRefreshSuggestion) {
                    generateAiSuggestion(clipId)
                } else if (!accepted && waveformLoadJobs[clipId] === handle) {
                    // The timeline changed while extraction was in flight. Queue the
                    // current identity immediately so a trim/relink does not wait for
                    // another viewport event to recover its waveform.
                    preloadVisibleWaveforms(_state.value)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w("EditorViewModel", "Waveform extraction failed for $clipId", e)
            } finally {
                waveformLoadJobs.remove(clipId, handle)
            }
        }
    }

    private fun cancelWaveformLoads(clipIds: Set<String>? = null) {
        waveformLoadJobs.forEach { (clipId, handle) ->
            if (clipIds == null || clipId in clipIds) {
                handle.job?.cancel()
                waveformLoadJobs.remove(clipId, handle)
            }
        }
    }

    private var previewRebuildJob: Job? = null
    private var extendedTrimPreviewJob: Job? = null

    private fun refreshExtendedTrimPreview() {
        if (extendedTrimPreviewJob?.isActive == true) return
        extendedTrimPreviewJob = viewModelScope.launch {
            delay(100L)
            rebuildPlayerTimeline()
            extendedTrimPreviewJob = null
        }
    }

    /** Debounce expensive composition graph replacement during slider and gesture updates. */
    private fun updatePreview() {
        previewRebuildJob?.cancel()
        previewRebuildJob = viewModelScope.launch {
            delay(100L)
            rebuildPlayerTimeline()
        }
    }

    /**
     * Enqueue background proxy generation via WorkManager.
     * Called after importing high-res clips when proxy editing is enabled.
     */
    fun enqueueProxyGeneration() {
        backgroundJobCoordinator.enqueueProxyGeneration()
    }

    // --- Background Media Ingest ---
    fun enqueueMediaIngest(sourceUri: Uri, mediaType: String, displayName: String) {
        backgroundJobCoordinator.enqueueMediaIngest(
            sourceUri = sourceUri,
            mediaType = mediaType,
            displayName = displayName,
            callbacks = EditorBackgroundJobCoordinator.IngestCallbacks(
                onQueued = { pending ->
                    _state.update { state ->
                        state.copyMedia { media ->
                            media.copy(pendingIngests = media.pendingIngests + pending)
                        }
                    }
                },
                onProgress = ::updateIngestProgress,
                onSucceeded = { workId, managedUri, type ->
                    removeIngest(workId)
                    val trackType = when {
                        type.startsWith("audio") -> TrackType.AUDIO
                        type.startsWith("image") -> TrackType.OVERLAY
                        else -> TrackType.VIDEO
                    }
                    addClipToTrack(managedUri, trackType)
                },
                onFailed = { workId, error ->
                    removeIngest(workId)
                    if (error != null) AppLog.w("EditorViewModel", "Media import failed: $error")
                    showToast(text(R.string.editor_import_failed_toast))
                },
                onCancelled = ::removeIngest,
            ),
        )
    }

    fun cancelMediaIngest(workId: String) {
        backgroundJobCoordinator.cancelMediaIngest(workId) {
            removeIngest(workId)
            showToast(text(R.string.vm_import_cancelled_toast))
        }
    }

    private fun updateIngestProgress(workId: String, progress: Float) {
        _state.update { state ->
            state.copyMedia { media ->
                media.copy(
                    pendingIngests = media.pendingIngests.map { ingest ->
                        if (ingest.workId == workId) ingest.copy(progress = progress) else ingest
                    }
                )
            }
        }
    }

    private fun removeIngest(workId: String) {
        _state.update { state ->
            state.copyMedia { media ->
                media.copy(pendingIngests = media.pendingIngests.filter { it.workId != workId })
            }
        }
    }

    // --- Clip Editing (delegated) ---
    fun addClipToTrack(uri: Uri, trackType: TrackType = TrackType.VIDEO) {
        setTool(EditorTool.NONE)
        clipEditingDelegate.addClipToTrack(uri, trackType)
    }
    fun addMediaSequence(items: List<SequenceMediaItem>) {
        if (items.isEmpty()) return
        setTool(EditorTool.NONE)
        clipEditingDelegate.addMediaSequence(items)
    }
    fun relinkMedia(oldUri: Uri, newUri: Uri) = clipEditingDelegate.relinkMedia(oldUri, newUri)
    fun selectClip(clipId: String?, trackId: String? = null) {
        clipEditingDelegate.selectClip(clipId, trackId)
        generateAiSuggestion(clipId)
    }
    fun deleteSelectedClip() = clipEditingDelegate.deleteSelectedClip()
    fun liftSelectedClip() = clipEditingDelegate.liftSelectedClip()
    fun duplicateSelectedClip() = clipEditingDelegate.duplicateSelectedClip()
    fun mergeWithNextClip() = clipEditingDelegate.mergeWithNextClip()
    fun splitClipAtPlayhead() = clipEditingDelegate.splitClipAtPlayhead()
    fun beginTrim() = clipEditingDelegate.beginTrim()
    fun trimClip(clipId: String, newTrimStartMs: Long? = null, newTrimEndMs: Long? = null) = clipEditingDelegate.trimClip(clipId, newTrimStartMs, newTrimEndMs)
    fun endTrim() {
        extendedTrimPreviewJob?.cancel()
        extendedTrimPreviewJob = null
        clipEditingDelegate.endTrim()
    }
    fun cancelTrim() {
        extendedTrimPreviewJob?.cancel()
        extendedTrimPreviewJob = null
        clipEditingDelegate.endTrim(commit = false)
    }
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
                    if (clip.id == selectedId) {
                        val existingTypes = clip.effects.map { it.type }.toSet()
                        val newEffects = freshEffects.filter { it.type !in existingTypes }
                        clip.copy(effects = clip.effects + newEffects)
                    } else clip
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
    fun setTransitionEasing(clipId: String, easing: TransitionEasing) = effectsDelegate.setTransitionEasing(clipId, easing)

    // --- Fonts ---
    fun getImportedFonts(): List<Pair<String, String>> =
        fontRegistry.listImportedFonts().map { fontRegistry.fontFamilyKey(it.fileName) to it.displayName }

    fun importFont(uri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = fontRegistry.importFont(uri)
            if (result != null) {
                showToast(text(R.string.vm_imported_media_toast, result.displayName))
            } else {
                showToast(text(R.string.vm_invalid_font_toast))
            }
        }
    }

    // --- Overlays & Markers (delegated) ---
    fun addTextOverlay(text: TextOverlay) = overlayDelegate.addTextOverlay(text)
    fun updateTextOverlay(textOverlay: TextOverlay) = overlayDelegate.updateTextOverlay(textOverlay)
    fun removeTextOverlay(id: String) = overlayDelegate.removeTextOverlay(id)
    fun addImageOverlay(uri: Uri, type: ImageOverlayType = ImageOverlayType.STICKER) {
        viewModelScope.launch {
            when (val result = overlayAssetStore.importImageOverlay(uri, type)) {
                is OverlayAssetImportResult.Imported -> {
                    overlayDelegate.addImageOverlay(result.uri, result.type)
                    refreshMediaRelinkReports(openPanelOnProblems = false)
                }
                is OverlayAssetImportResult.Rejected -> {
                    showToast(result.userMessage, ToastSeverity.Warning)
                }
            }
        }
    }
    fun updateImageOverlay(id: String, positionX: Float? = null, positionY: Float? = null, scale: Float? = null, rotation: Float? = null, opacity: Float? = null) = overlayDelegate.updateImageOverlay(id, positionX, positionY, scale, rotation, opacity)
    fun removeImageOverlay(id: String) = overlayDelegate.removeImageOverlay(id)
    fun addTimelineMarker(label: String = "", color: MarkerColor = MarkerColor.BLUE) = overlayDelegate.addTimelineMarker(label, color)
    fun deleteTimelineMarker(id: String) = overlayDelegate.deleteTimelineMarker(id)
    fun applyCutList(text: String) = overlayDelegate.applyCutList(text)

    fun jumpToNextMarker() {
        val current = _playheadMs.value
        val next = _state.value.timelineMarkers.firstOrNull { it.timeMs > current + 50 }
        if (next != null) seekTo(next.timeMs) else showToast(text(R.string.vm_no_next_marker_toast))
    }

    fun jumpToPrevMarker() {
        val current = _playheadMs.value
        val prev = _state.value.timelineMarkers.lastOrNull { it.timeMs < current - 50 }
        if (prev != null) seekTo(prev.timeMs) else showToast(text(R.string.vm_no_prev_marker_toast))
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
        if (playbackCoordinator.isPlaybackRequested() && !playbackCoordinator.isPlaybackEnded()) {
            playbackCoordinator.pause()
            _state.update { it.copy(isPlaying = false, isPlaybackRequested = false) }
        } else {
            val missingCount = _state.value.media.relinkReports
                .values.count { it.state == MediaRelinkProbe.RelinkState.MISSING }
            if (missingCount > 0) {
                showToast(
                    if (missingCount == 1) "1 clip source is missing — it will appear as a gap"
                    else "$missingCount clip sources are missing — they will appear as gaps",
                    ToastSeverity.Warning
                )
            }
            val currentPlayheadMs = _playheadMs.value
            val playhead = playbackStartPosition(
                playheadMs = currentPlayheadMs,
                totalDurationMs = _state.value.totalDurationMs
            )
            val restartSession = playhead != currentPlayheadMs
            if (restartSession) {
                _playheadMs.value = playhead
                _state.update { it.copy(playheadMs = playhead) }
            }
            // CompositionPlayer owns the whole absolute timeline, including gaps on
            // one sequence while another visual or audio lane remains active.
            _state.update { it.copy(isPlaybackRequested = true) }
            playbackCoordinator.playFromTimelinePosition(playhead, restartSession)
        }
    }

    fun toggleLoop() {
        val newLooping = !_state.value.isLooping
        playbackCoordinator.setLooping(newLooping)
        _state.update { it.copy(isLooping = newLooping) }
    }

    private var isScrubbing = false
    private var scrubSeekJob: kotlinx.coroutines.Job? = null

    private fun quantizeProjectTimeMs(timeMs: Long): Long =
        _state.value.project.timelineTimebase.snapMs(timeMs)

    private fun quantizeProjectDurationMs(durationMs: Long): Long {
        val sign = if (durationMs < 0L) -1L else 1L
        return sign * _state.value.project.timelineTimebase.snapMs(kotlin.math.abs(durationMs))
    }

    private fun previousProjectFrameTimeMs(boundaryMs: Long): Long {
        val timebase = _state.value.project.timelineTimebase
        val boundaryFrame = timebase.frameIndexAt(boundaryMs)
        return timebase.timeMsAt((boundaryFrame - 1L).coerceAtLeast(0L))
    }

    fun seekTo(positionMs: Long) {
        val clamped = quantizeProjectTimeInRange(positionMs, _state.value.totalDurationMs)
        _playheadMs.value = clamped
        if (isScrubbing) {
            // During scrub: debounce ExoPlayer seeks to every 80ms, skip full state copy
            scrubSeekJob?.cancel()
            scrubSeekJob = viewModelScope.launch {
                kotlinx.coroutines.delay(80)
                playbackCoordinator.seekTo(clamped)
            }
            return
        }
        playbackCoordinator.seekTo(clamped)
        _state.update { it.copy(playheadMs = clamped) }
        if (_state.value.panels.isOpen(PanelId.SCOPES)) updateScopeFrame()
    }

    private fun quantizeProjectTimeInRange(positionMs: Long, maximumMs: Long): Long {
        val timebase = _state.value.project.timelineTimebase
        val maximumFrame = timebase.frameIndexAtOrBefore(maximumMs)
        val requestedFrame = timebase.frameIndexAt(positionMs)
        return timebase.timeMsAt(requestedFrame.coerceIn(0L, maximumFrame))
    }

    /** Enable scrubbing mode during timeline drag for smoother seeking. */
    fun beginScrub() {
        isScrubbing = true
        playbackCoordinator.setScrubbingMode(true)
    }
    fun endScrub() {
        isScrubbing = false
        scrubSeekJob?.cancel()
        scrubSeekJob = null
        playbackCoordinator.setScrubbingMode(false)
        val pos = _playheadMs.value
        playbackCoordinator.seekTo(pos)
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
            val updatedState = state.copy(zoomLevel = TimelineToolbarPolicy.clampZoom(zoom))
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
            .let(TimelineToolbarPolicy::clampZoom)
        _state.update { s -> s.copy(zoomLevel = fit, scrollOffsetMs = 0L) }
        preloadVisibleWaveforms(_state.value)
    }

    // Tool selection
    fun setTool(tool: EditorTool) {
        // Disable scrubbing mode when leaving trim tool
        if (_state.value.currentTool == EditorTool.TRIM && tool != EditorTool.TRIM) {
            playbackCoordinator.setScrubbingMode(false)
        }
        _state.update {
            it.copy(
                currentTool = tool,
                selectedTimelineRange = if (tool == EditorTool.MUTE_RANGE) {
                    it.selectedTimelineRange
                } else {
                    null
                },
            )
        }
    }

    fun beginTimelineRangeSelection() {
        pauseIfPlaying()
        _state.update {
            it.copy(
                currentTool = EditorTool.MUTE_RANGE,
                selectedTimelineRange = null,
            )
        }
    }

    fun clearTimelineRangeSelection() {
        _state.update { it.copy(selectedTimelineRange = null) }
    }

    fun updateTimelineRange(startMs: Long, endMs: Long) {
        if (_state.value.currentTool != EditorTool.MUTE_RANGE) return
        val start = minOf(startMs, endMs).coerceAtLeast(0L)
        val end = maxOf(startMs, endMs).coerceAtMost(_state.value.totalDurationMs)
        if (end <= start) return
        _state.update {
            it.copy(selectedTimelineRange = TimelineRange(start, end))
        }
    }

    /** Apply the selected interval as volume keyframes without changing clip boundaries. */
    fun muteSelectedTimelineRange() {
        val state = _state.value
        val range = state.selectedTimelineRange ?: return
        val audioClipIds = state.tracks.flatMap { track ->
            track.clips.filter { clip ->
                track.type == TrackType.AUDIO ||
                    (track.type == TrackType.VIDEO || track.type == TrackType.OVERLAY) && clipHasAudio(clip)
            }
        }.mapTo(mutableSetOf()) { it.id }
        val result = muteTimelineRange(state.tracks, range, audioClipIds)
        if (result.changedClipCount == 0) {
            showToast(text(R.string.vm_mute_range_no_audio_toast), ToastSeverity.Warning)
            return
        }

        saveUndoState("Mute range")
        _state.update {
            it.copy(
                tracks = result.tracks,
                currentTool = EditorTool.NONE,
                selectedTimelineRange = null,
            )
        }
        rebuildPlayerTimeline()
        saveProject()
        showToast(text(R.string.vm_mute_range_applied_toast, result.changedClipCount))
    }

    // Panel mutual exclusion — atomic dismiss-and-show in single state update
    private fun pauseIfPlaying() {
        if (playbackCoordinator.isPlaybackRequested()) {
            playbackCoordinator.pause()
            _state.update { it.copy(isPlaying = false, isPlaybackRequested = false) }
        }
    }

    @Volatile private var cachedClipToTrackMap: Map<String, String> = emptyMap()
    @Volatile private var cachedClipToTrackTracksIdentity: List<Track>? = null

    private fun clipToTrackMap(tracks: List<Track>): Map<String, String> {
        if (tracks === cachedClipToTrackTracksIdentity) return cachedClipToTrackMap
        val map = mutableMapOf<String, String>()
        tracks.forEach { track -> track.clips.forEach { clip -> map[clip.id] = track.id } }
        cachedClipToTrackMap = map
        cachedClipToTrackTracksIdentity = tracks
        return map
    }

    private fun normalizeSelectionState(state: EditorState, tracks: List<Track> = state.tracks): EditorState {
        val clipToTrackId = clipToTrackMap(tracks)

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
        // When a clip is selected, the track follows it. Otherwise preserve an
        // existing track (header/empty-track) selection as long as that track
        // still exists, so undo/redo restores a track-only selection instead of
        // silently clearing it.
        val normalizedSelectedTrackId = normalizedSelectedClipId?.let { clipToTrackId[it] }
            ?: state.selectedTrackId?.takeIf { trackId -> tracks.any { it.id == trackId } }

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
            ai = state.ai.copy(
                noiseAnalysisResult = null,
                cutAssistantReview = null
            ),
            selectedMaskId = null,
            isDrawingMode = false
        ).copyPanel { panel ->
            panel.copy(
                panels = panel.panels.closeAll(),
                selectedEffectId = null,
                editingTextOverlayId = null
            )
        }
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
        _state.update {
            dismissedPanelState(it).copyPanel { panelState ->
                panelState.copy(panels = panelState.panels.closeAll().open(panel))
            }
        }
    }
    fun hidePanel(panel: PanelId) {
        _state.update {
            it.copyPanel { panelState ->
                panelState.copy(panels = panelState.panels.close(panel))
            }
        }
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
        _state.update { state ->
            val defaultDisclosure = AiUsageLedger.discloseToggleDefaultOn(state.aiUsageLedger)
            val dismissed = dismissedPanelState(state)
            dismissed.copy(
                panel = dismissed.panel.copy(
                    panels = dismissed.panels.closeAll().open(PanelId.EXPORT_SHEET)
                ),
                export = dismissed.export.copy(
                    config = state.exportConfig.copy(
                        discloseAiUse = defaultDisclosure,
                        writeAiUseSidecar = if (defaultDisclosure) true else state.exportConfig.writeAiUseSidecar
                    ),
                    state = ExportState.IDLE,
                    progress = 0f,
                    errorMessage = null,
                    warningMessage = null,
                )
            )
        }
    }
    fun hideExportSheet() {
        _state.update { s ->
            val restored = s.savedExportConfig
            s.copy(
                panel = s.panel.copy(
                    panels = s.panels.close(PanelId.EXPORT_SHEET)
                ),
                export = s.export.copy(
                    config = restored ?: s.exportConfig,
                    savedConfig = null
                )
            )
        }
    }
    fun showTextEditor() {
        pauseIfPlaying()
        _state.update {
            dismissedPanelState(it).copyPanel { panel ->
                panel.copy(
                    panels = panel.panels.closeAll().open(PanelId.TEXT_EDITOR),
                    editingTextOverlayId = null
                )
            }
        }
    }
    fun editTextOverlay(id: String) {
        pauseIfPlaying()
        _state.update {
            dismissedPanelState(it).copyPanel { panel ->
                panel.copy(
                    panels = panel.panels.closeAll().open(PanelId.TEXT_EDITOR),
                    editingTextOverlayId = id
                )
            }
        }
    }
    fun hideTextEditor() {
        _state.update {
            it.copyPanel { panel ->
                panel.copy(
                    panels = panel.panels.close(PanelId.TEXT_EDITOR),
                    editingTextOverlayId = null
                )
            }
        }
    }
    fun hideVoiceoverPanel() {
        if (_state.value.isRecordingVoiceover) stopVoiceover()
        voiceoverDurationJob?.cancel()
        hidePanel(PanelId.VOICEOVER_RECORDER)
    }
    fun selectEffect(effectId: String?) {
        _state.update { it.copyPanel { panel -> panel.copy(selectedEffectId = effectId) } }
    }
    fun clearSelectedEffect() {
        _state.update { it.copyPanel { panel -> panel.copy(selectedEffectId = null) } }
    }

    // --- Color Grading (delegated) ---
    fun showColorGrading() = colorGradingDelegate.showColorGrading()
    fun hideColorGrading() = colorGradingDelegate.hideColorGrading()
    fun beginColorGradeAdjust() = colorGradingDelegate.beginColorGradeAdjust()
    fun endColorGradeAdjust() = colorGradingDelegate.endColorGradeAdjust()
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
        if (_state.value.selectedClipId == null) return
        // One-shot commit path (preset gallery): a Ken Burns/Shake/Spin apply
        // is a real timeline mutation and must be individually undoable.
        saveUndoState("Edit keyframes")
        updateSelectedClip { it.copy(keyframes = keyframes) }
        saveProject()
    }

    // Gesture-scoped keyframe editing: one undo entry per drag, one save at
    // release. Per-pointer-frame undo pushes were evicting the entire
    // 50-entry undo stack in a single handle drag.
    fun beginKeyframeAdjust() {
        if (_state.value.selectedClipId == null) return
        saveUndoState("Move keyframe")
    }

    fun updateClipKeyframesDuringGesture(keyframes: List<Keyframe>) {
        if (_state.value.selectedClipId == null) return
        updateSelectedClip { it.copy(keyframes = keyframes) }
    }

    fun endKeyframeAdjust() {
        saveProject()
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

    // Gesture-scoped speed-curve editing. The canvas drag previously routed
    // every pointer frame through setClipSpeedCurve — an undo push, a full
    // Media3 composition rebuild, and a Room save per frame, violating the
    // "rebuild at gesture end" contract the speed slider already follows.
    fun beginSpeedCurveAdjust() {
        if (_state.value.selectedClipId == null) return
        saveUndoState("Speed curve")
    }

    fun updateClipSpeedCurveDuringGesture(speedCurve: SpeedCurve?) {
        if (_state.value.selectedClipId == null) return
        updateSelectedClip { it.copy(speedCurve = speedCurve) }
    }

    fun endSpeedCurveAdjust() {
        rebuildPlayerTimeline()
        saveProject()
    }

    // --- Mask Editor ---
    fun showMaskEditor() = showPanel(PanelId.MASK_EDITOR)
    fun hideMaskEditor() {
        hidePanel(PanelId.MASK_EDITOR)
        _state.update { it.copy(selectedMaskId = null) }
        // Mask geometry edits (updateMaskPoint / setFreehandMaskPoints / updateMask) are
        // applied per drag-tick without persisting, to avoid disk thrash. Persist once
        // here on panel close so freehand draws and handle drags survive a restart.
        saveProject()
    }

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
        saveProject()
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
        saveProject()
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
        if (!TrackBlendModeCapability.isSupported(blendMode)) {
            showToast(text(R.string.vm_track_blend_unsupported_toast), ToastSeverity.Warning)
            return
        }
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
    fun addBatchExportSourceCut(
        config: ExportConfig,
        sourceRange: com.novacut.editor.model.BatchExportSourceRange,
    ) = exportDelegate.addBatchExportSourceCut(config, sourceRange)
    fun removeBatchExportItem(id: String) = exportDelegate.removeBatchExportItem(id)
    fun moveBatchExportItem(id: String, targetIndex: Int) = exportDelegate.moveBatchExportItem(id, targetIndex)
    fun retryBatchExportItem(id: String) = exportDelegate.retryBatchExportItem(id)
    fun pauseBatchExport() = exportDelegate.pauseBatchExport()
    fun cancelBatchExport() = exportDelegate.cancelBatchExport()
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
        saveUndoState("Edit caption")
        updateSelectedClip { clip ->
            clip.copy(captions = clip.captions.map { if (it.id == caption.id) caption else it })
        }
        saveProject()
    }

    fun removeCaption(captionId: String) {
        if (_state.value.selectedClipId == null) return
        saveUndoState("Remove caption")
        updateSelectedClip { it.copy(captions = it.captions.filter { c -> c.id != captionId }) }
        saveProject()
    }

    /** Open a bounded, non-mutating SRT/WebVTT preview for the selected clip. */
    fun previewCaptionImport(uri: Uri) {
        if (_state.value.selectedClipId == null) {
            showToast(text(R.string.caption_import_select_clip), ToastSeverity.Warning)
            return
        }
        viewModelScope.launch {
            val preview = withContext(Dispatchers.IO) {
                captionImportItem(uri)?.let { item ->
                    incomingDocumentImportRouter.preview(item)
                }
            }
            val analysis = preview?.captionImport
            if (analysis == null) {
                showToast(
                    preview?.body ?: text(R.string.caption_import_unsupported),
                    ToastSeverity.Error,
                )
                return@launch
            }
            _state.update {
                it.copyCaption { caption -> caption.copy(captionImportPreview = analysis) }
            }
        }
    }

    /** Apply the accepted preview as one undoable edit to the selected clip. */
    fun applyCaptionImport() {
        val preview = _state.value.captionImportPreview ?: return
        val clip = getSelectedClip()
        if (clip == null) {
            dismissCaptionImportPreview()
            showToast(text(R.string.caption_import_select_clip), ToastSeverity.Warning)
            return
        }
        if (!preview.isValid) {
            showToast(text(R.string.caption_import_invalid), ToastSeverity.Error)
            return
        }
        val mapping = CaptionImportEngine.mapToClip(
            preview = preview,
            clipDurationMs = clip.durationMs,
            targetOffsetMs = clip.timelineStartMs,
        )
        if (mapping.captions.isEmpty()) {
            dismissCaptionImportPreview()
            showToast(text(R.string.caption_import_no_cues_in_clip), ToastSeverity.Warning)
            return
        }
        saveUndoState("Import captions")
        updateSelectedClip { selected ->
            selected.copy(
                captions = (selected.captions + mapping.captions)
                    .sortedWith(compareBy<Caption> { it.startTimeMs }.thenBy { it.endTimeMs }),
            )
        }
        dismissCaptionImportPreview()
        saveProject()
        if (mapping.clippedCueCount > 0 || mapping.skippedCueCount > 0) {
            showToast(
                appContext.resources.getQuantityString(
                    R.plurals.caption_import_applied_partial,
                    mapping.captions.size,
                    mapping.captions.size,
                    mapping.clippedCueCount + mapping.skippedCueCount,
                ),
                ToastSeverity.Warning,
            )
        } else {
            showToast(
                appContext.resources.getQuantityString(
                    R.plurals.caption_import_applied,
                    mapping.captions.size,
                    mapping.captions.size,
                )
            )
        }
    }

    fun dismissCaptionImportPreview() {
        _state.update {
            it.copyCaption { caption -> caption.copy(captionImportPreview = null) }
        }
    }

    private fun captionImportItem(uri: Uri): IncomingDocumentItem? {
        if (uri.scheme != "content") return null
        val displayName = resolveMediaDisplayName(appContext, uri)
            ?.substringAfterLast('/')
            ?.substringAfterLast('\\')
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment
                ?.substringAfterLast('/')
                ?.substringAfterLast('\\')
                ?.trim()
                ?.takeIf { it.isNotBlank() }
            ?: return null
        val mimeType = runCatching { appContext.contentResolver.getType(uri) }.getOrNull()
        val kind = IncomingDocumentIntentParser.classify(displayName, mimeType)
            ?.takeIf {
                it == com.novacut.editor.engine.IncomingDocumentKind.CAPTION_SRT ||
                    it == com.novacut.editor.engine.IncomingDocumentKind.CAPTION_WEBVTT
            }
            ?: return null
        val sizeBytes = runCatching {
            appContext.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.SIZE),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (index >= 0 && !cursor.isNull(index)) cursor.getLong(index) else null
                } else {
                    null
                }
            }
        }.getOrNull()
        return IncomingDocumentItem(
            uri = uri,
            kind = kind,
            displayName = displayName,
            mimeType = mimeType,
            sizeBytes = sizeBytes,
        )
    }

    // --- Project Snapshots ---
    fun createSnapshot(label: String = "") {
        val s = _state.value
        val json = ProjectDocumentApplicator.encode(buildProjectDocument(s))
        val snapshot = ProjectSnapshot(
            projectId = s.project.id,
            timestamp = System.currentTimeMillis(),
            label = label.ifEmpty { "Snapshot ${s.projectSnapshots.size + 1}" },
            stateJson = json
        )
        _state.update { it.copy(projectSnapshots = it.projectSnapshots + snapshot) }
        showToast(text(R.string.vm_snapshot_saved_toast, snapshot.label))
    }

    fun restoreSnapshot(snapshotId: String) {
        val snapshot = _state.value.projectSnapshots.find { it.id == snapshotId } ?: return
        try {
            val recovery = when (val decoded = ProjectDocumentApplicator.read(snapshot.stateJson)) {
                is ProjectDocumentReadResult.Loaded -> {
                    if (decoded.report.isPartial) {
                        showToast(
                            "Snapshot is incomplete (${partialRestoreSummary(appContext.resources, decoded.report)}) and was not applied.",
                            ToastSeverity.Error,
                        )
                        return
                    }
                    decoded.document.state
                }
                is ProjectDocumentReadResult.FutureSchema -> {
                    showToast(text(R.string.vm_snapshot_future_schema_toast), ToastSeverity.Error)
                    return
                }
                is ProjectDocumentReadResult.Corrupt -> {
                    throw decoded.cause
                }
            }
            saveUndoState("Restore snapshot")
            _state.update {
                it.copy(
                    tracks = recovery.tracks,
                    textOverlays = recovery.textOverlays,
                    imageOverlays = recovery.imageOverlays,
                    timelineMarkers = recovery.timelineMarkers,
                    globalTransitions = recovery.globalTransitions,
                    drawingPaths = recovery.drawingPaths,
                    playheadMs = recovery.playheadMs,
                    chapterMarkers = recovery.chapterMarkers,
                    ai = it.ai.copy(usageLedger = recovery.aiUsageLedger),
                    beatMarkers = recovery.beatMarkers,
                    trackedObjects = recovery.trackedObjects.ifEmpty { it.trackedObjects },
                    storyboardCards = recovery.storyboardCards.ifEmpty { it.storyboardCards },
                    v369 = it.v369.copy(transcript = recovery.transcript ?: it.v369.transcript),
                    export = it.export.copy(
                        config = it.export.config.copy(watermark = recovery.exportWatermark)
                    ),
                    media = it.media.copy(mediaAssets = recovery.mediaAssets),
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
            AppLog.w("EditorViewModel", "Snapshot restore failed for ${snapshot.id}", e)
            showToast(appContext.getString(R.string.snapshot_restore_failed))
        }
    }

    // --- Proxy ---
    fun setProxyEnabled(enabled: Boolean) {
        _state.update { it.copy(proxySettings = it.proxySettings.copy(enabled = enabled)) }
        if (enabled) {
            generateProxiesForAllClips()
        } else {
            proxyGenerationJob?.cancel()
            proxyGenerationJob = null
            proxyEngine.clearProxies()
            _state.update { state ->
                state.copy(tracks = state.tracks.map { track ->
                    track.copy(clips = track.clips.map { clip -> clip.copy(proxyUri = null) })
                })
            }
            rebuildPlayerTimeline()
            saveProject()
            showToast(text(R.string.vm_proxy_disabled_toast))
        }
    }

    private fun generateProxiesForAllClips() {
        val requests = _state.value.tracks
            .flatMap { it.clips }
            .associate { clip ->
                clip.id to ProxyGenerationRequest(
                    identity = clip.timelineMediaJobIdentity(),
                    sourceUri = clip.sourceUri,
                    resolution = _state.value.proxySettings.resolution,
                )
            }
        if (requests.isEmpty()) {
            showToast(text(R.string.vm_no_clips_for_proxy_toast))
            return
        }
        showToast(text(R.string.vm_generating_proxies_toast, requests.size))
        proxyGenerationJob?.cancel()
        lateinit var thisJob: Job
        thisJob = viewModelScope.launch {
            var generated = 0
            try {
                for (request in requests.values) {
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    val currentClip = _state.value.tracks
                        .asSequence()
                        .flatMap { it.clips.asSequence() }
                        .firstOrNull { it.id == request.identity.clipId }
                    if (!_state.value.proxySettings.enabled ||
                        !shouldApplyMediaJobResult(request.identity, currentClip?.timelineMediaJobIdentity())
                    ) {
                        continue
                    }
                    if (!proxyEngine.hasProxy(request.sourceUri)) {
                        val proxyUri = proxyEngine.generateProxy(
                            request.sourceUri,
                            request.resolution,
                        )
                        if (proxyUri != null) generated++
                    }
                }
                _state.update { state ->
                    if (!state.proxySettings.enabled) {
                        state
                    } else {
                        state.copy(tracks = state.tracks.map { track ->
                            track.copy(clips = track.clips.map { clip ->
                                val request = requests[clip.id]
                                if (request != null &&
                                    shouldApplyMediaJobResult(request.identity, clip.timelineMediaJobIdentity())
                                ) {
                                    clip.copy(proxyUri = proxyEngine.getProxyUri(request.sourceUri))
                                } else {
                                    clip
                                }
                            })
                        })
                    }
                }
                rebuildPlayerTimeline()
                saveProject()
                showToast(text(R.string.vm_proxy_enabled_toast, generated))
            } catch (e: CancellationException) {
                throw e
            } finally {
                if (proxyGenerationJob === thisJob) proxyGenerationJob = null
            }
        }
        proxyGenerationJob = thisJob
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
            val size = projectTransferCoordinator.estimateBackupSize(buildAutoSaveState(_state.value))
            _backupEstimatedSize.value = size
        }
    }

    fun exportProjectBackup() {
        if (_isExportingBackup.value || _isImportingBackup.value) {
            showToast(text(R.string.vm_backup_in_progress_toast))
            return
        }
        _isExportingBackup.value = true
        viewModelScope.launch {
            try {
                val s = _state.value
                val fileName = "${sanitizedProjectFileStem(s.project.name)}.clearcut"
                val savedName = projectTransferCoordinator.exportBackup(
                    document = buildProjectDocument(s),
                    fileName = fileName,
                )
                if (savedName != null) {
                    _lastBackupTime.value = System.currentTimeMillis()
                    showToast(text(R.string.vm_backup_saved_toast, savedName))
                } else {
                    showToast(text(R.string.vm_backup_export_failed_toast))
                }
            } catch (e: Exception) {
                AppLog.e("EditorViewModel", "Backup export failed", e)
                showToast(text(R.string.editor_backup_failed_toast))
            } finally {
                _isExportingBackup.value = false
            }
        }
    }

    fun importProjectBackup(uri: Uri) {
        if (_isExportingBackup.value || _isImportingBackup.value) {
            showToast(text(R.string.vm_backup_in_progress_toast))
            return
        }
        _isImportingBackup.value = true
        viewModelScope.launch {
            try {
                showToast(text(R.string.vm_importing_backup_toast))
                val result = projectTransferCoordinator.importBackup(uri)
                val state = result.document?.state ?: result.state
                if (state != null) {
                    saveUndoState("Import backup")
                    _state.update { s ->
                        dismissedPanelState(
                            recalculateDuration(
                                s.copy(
                                    tracks = ensureEditorTracks(state.tracks),
                                    textOverlays = state.textOverlays,
                                    imageOverlays = state.imageOverlays,
                                    timelineMarkers = state.timelineMarkers,
                                    globalTransitions = state.globalTransitions,
                                    chapterMarkers = state.chapterMarkers,
                                    drawingPaths = state.drawingPaths,
                                    beatMarkers = state.beatMarkers,
                                    ai = s.ai.copy(usageLedger = state.aiUsageLedger),
                                    v369 = s.v369.copy(
                                        transcript = state.transcript,
                                        selectedWordIndices = emptySet()
                                    ),
                                    trackedObjects = state.trackedObjects,
                                    storyboardCards = state.storyboardCards,
                                    playheadMs = state.playheadMs,
                                    export = s.export.copy(
                                        config = s.export.config.copy(watermark = state.exportWatermark)
                                    ),
                                    media = s.media.copy(mediaAssets = state.mediaAssets),
                                )
                            )
                        )
                    }
                    _playheadMs.value = _state.value.playheadMs
                    rebuildPlayerTimeline()
                    saveProject()
                    val report = result.report
                    val message = when {
                        report.mediaMissing > 0 ->
                            "Backup imported — ${report.mediaMissing} media file(s) missing; relink before export"
                        report.warnings.isNotEmpty() ->
                            "Backup imported (${report.summary})"
                        else -> "Backup imported successfully"
                    }
                    if (report.mediaMissing > 0 || report.warnings.isNotEmpty() || report.projectIdCollided) {
                        _state.update {
                            it.copyMedia { media ->
                                media.copy(
                                    backupImportFeedback = BackupImportFeedback(
                                        succeeded = true,
                                        title = "Backup imported with notes",
                                        body = "ClearCut restored the timeline, but this archive needs review before you export or hand it off.",
                                        report = report
                                    )
                                )
                            }
                        }
                    }
                    showToast(message)
                } else {
                    val reason = result.errorMessage ?: result.report.summary
                    _state.update {
                        it.copyMedia { media ->
                            media.copy(
                                backupImportFeedback = BackupImportFeedback(
                                    succeeded = false,
                                    title = "Backup import failed",
                                    body = "ClearCut left the current project unchanged.",
                                    report = result.report,
                                    errorMessage = reason
                                )
                            )
                        }
                    }
                    showToast(text(R.string.editor_backup_import_failed_toast))
                }
            } catch (e: Exception) {
                AppLog.e("EditorViewModel", "Backup import failed", e)
                showToast(text(R.string.editor_import_failed_toast))
            } finally {
                _isImportingBackup.value = false
            }
        }
    }
    // --- Tutorial ---
    fun showTutorial() {
        _state.update { it.copyPanel { panel -> panel.copy(panels = panel.panels.open(PanelId.TUTORIAL)) } }
    } // no dismiss — overlays other panels
    fun hideTutorial() {
        hidePanel(PanelId.TUTORIAL)
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
        _state.update {
            dismissedPanelState(it)
                .copy(undoHistoryEntries = entries)
                .copyPanel { panel ->
                    panel.copy(panels = panel.panels.closeAll().open(PanelId.UNDO_HISTORY))
                }
        }
    }
    fun hideUndoHistory() = hidePanel(PanelId.UNDO_HISTORY)
    fun jumpToUndoState(index: Int) {
        val stack = _state.value.undoStack
        if (index < 0 || index >= stack.size) return
        val target = stack[index]
        _state.update { state ->
            val restored = recalculateDuration(state.withUndoDocument(target).copy(
                undoStack = stack.take(index),
                redoStack = listOf(UndoAction(
                    "Current",
                    state.tracks,
                    state.textOverlays,
                    imageOverlays = state.imageOverlays.toList(),
                    timelineMarkers = state.timelineMarkers.toList(),
                    chapterMarkers = state.chapterMarkers.toList(),
                    drawingPaths = state.drawingPaths.toList(),
                    beatMarkers = state.beatMarkers.toList(),
                    trackedObjects = state.trackedObjects.toList(),
                    globalTransitions = state.globalTransitions.toList(),
                    storyboardCards = state.storyboardCards.toList(),
                    transcript = state.v369.transcript,
                    playheadMs = _playheadMs.value,
                    selectedClipId = state.selectedClipId,
                    selectedTrackId = state.selectedTrackId,
                    selectedClipIds = state.selectedClipIds,
                    aiUsageLedger = state.aiUsageLedger
                )) + stack.drop(index + 1)
            ))
            normalizeSelectionState(restored).copy(
                playheadMs = target.playheadMs.coerceIn(0L, restored.totalDurationMs.coerceAtLeast(0L))
            )
        }
        _playheadMs.value = _state.value.playheadMs
        rebuildTimeline()
        saveProject()
        showToast(text(R.string.vm_restored_toast, target.description))
    }

    // --- Command Palette ---
    fun showCommandPalette() = showPanel(PanelId.COMMAND_PALETTE)
    fun hideCommandPalette() = hidePanel(PanelId.COMMAND_PALETTE)

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
    fun setTrackTimelineOffset(trackId: String, offsetMs: Long) {
        val clampedOffsetMs = clampTrackTimelineOffsetMs(offsetMs)
        val currentTrack = _state.value.tracks.firstOrNull { it.id == trackId } ?: return
        if (currentTrack.timelineOffsetMs == clampedOffsetMs) return
        pauseIfPlaying()
        saveUndoState("Change track sync offset")
        _state.update { state ->
            recalculateDuration(
                state.copy(
                    tracks = state.tracks.map { track ->
                        if (track.id == trackId) {
                            track.copy(timelineOffsetMs = clampedOffsetMs)
                        } else {
                            track
                        }
                    }
                )
            )
        }
        rebuildPlayerTimeline()
        saveProject()
    }
    fun setClipAudioSyncOffset(trackId: String, clipId: String, offsetMs: Long) {
        val state = _state.value
        val track = state.tracks.firstOrNull { it.id == trackId } ?: return
        if (track.type != TrackType.AUDIO) return
        val clip = track.clips.firstOrNull { it.id == clipId } ?: return
        val quantizedOffsetMs = quantizeClipAudioSyncOffsetMs(
            value = offsetMs,
            timebase = state.project.timelineTimebase,
        )
        if (clip.audioSyncOffsetMs == quantizedOffsetMs) return
        pauseIfPlaying()
        saveUndoState("Change clip audio sync offset")
        _state.update { current ->
            recalculateDuration(
                current.copy(
                    tracks = current.tracks.map { currentTrack ->
                        if (currentTrack.id != trackId) {
                            currentTrack
                        } else {
                            currentTrack.copy(
                                clips = currentTrack.clips.map { currentClip ->
                                    if (currentClip.id == clipId) {
                                        currentClip.copy(audioSyncOffsetMs = quantizedOffsetMs)
                                    } else {
                                        currentClip
                                    }
                                }
                            )
                        }
                    }
                )
            )
        }
        rebuildPlayerTimeline()
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
    fun installedCaptionStyles(): List<com.novacut.editor.model.CaptionStyleTemplate> =
        stylePackManager.listInstalledStyles()
    fun applyCaptionStyle(template: com.novacut.editor.model.CaptionStyleTemplate) {
        hideCaptionStyleGallery()
        saveUndoState("Apply caption style")
        _state.update { s ->
            s.copy(
                tracks = s.tracks.map { track ->
                    track.copy(clips = track.clips.map { clip ->
                        clip.copy(captions = clip.captions.map { caption ->
                            caption.copy(style = caption.style.copy(
                                type = template.toCaptionStyleType(),
                                fontSize = template.fontSize,
                                fontFamily = template.fontFamily,
                                color = template.textColor,
                                backgroundColor = template.backgroundColor,
                                highlightColor = template.highlightColor,
                                positionY = template.positionY,
                                outline = template.outlineWidth > 0f,
                                outlineColor = template.outlineColor,
                                outlineWidth = template.outlineWidth.coerceAtLeast(0f),
                                shadow = (template.shadowColor ushr 24) > 0
                            ))
                        })
                    })
                }
            )
        }
        saveProject()
        showToast(text(R.string.vm_caption_style_applied_toast, template.type.displayName))
    }

    // --- Beat Sync ---
    fun showBeatSync() = showPanel(PanelId.BEAT_SYNC)
    fun hideBeatSync() = hidePanel(PanelId.BEAT_SYNC)
    fun analyzeBeats() {
        val audioClip = _state.value.tracks
            .filter { it.type == TrackType.AUDIO }
            .flatMap { it.clips }
            .firstOrNull() ?: run {
            showToast(text(R.string.vm_add_audio_track_first_toast))
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
                val sourceBeats = com.novacut.editor.engine.AudioEffectsEngine.detectBeats(pcm, effectiveSampleRate, 1)
                val beats = mapSourceMarkersToTimeline(audioClip, sourceBeats)
                    .map(_state.value.project.timelineTimebase::snapMs)
                    .distinct()
                _state.update { it.copy(beatMarkers = beats, isAnalyzingBeats = false) }
                showToast(text(R.string.vm_beats_detected_toast, beats.size))
            } catch (e: Exception) {
                _state.update { it.copy(isAnalyzingBeats = false) }
                showToast(text(R.string.vm_beat_detection_failed_toast))
            }
        }
    }
    fun applyBeatSync() {
        val beats = _state.value.beatMarkers
        if (beats.isEmpty()) {
            showToast(text(R.string.vm_detect_beats_first_toast))
            return
        }
        val plannedBeats = beats.sortedDescending().filter { beat ->
            val clip = _state.value.tracks
                .filter { it.type == TrackType.VIDEO }
                .flatMap { it.clips }
                .firstOrNull { beat > it.timelineStartMs && beat < it.timelineEndMs }
                ?: return@filter false
            val splitIds = linkedSplitCandidateIds(_state.value.tracks, setOf(clip.id), beat)
            val regroupedIds = regroupedClipIdsForSplit(_state.value.tracks, splitIds, beat)
            splitIds.isNotEmpty() && _state.value.tracks.none { track ->
                track.isLocked && track.clips.any { it.id in splitIds || it.id in regroupedIds }
            }
        }
        if (plannedBeats.isEmpty()) {
            showToast(text(R.string.vm_beat_split_toast, 0))
            return
        }
        saveUndoState("Beat sync")
        var splitCount = 0
        for (beat in plannedBeats) {
            // Re-read clips each iteration since splits modify state
            val currentClips = _state.value.tracks
                .filter { it.type == TrackType.VIDEO }
                .flatMap { it.clips }
            val clip = currentClips.firstOrNull { beat > it.timelineStartMs && beat < it.timelineEndMs }
            if (clip != null) {
                if (splitClipAt(clip.id, beat).isNotEmpty()) {
                    splitCount++
                }
            }
        }
        rebuildTimeline()
        saveProject()
        showToast(text(R.string.vm_beat_split_toast, splitCount))
        hideBeatSync()
    }
    fun tapBeatMarker() {
        val currentMs = quantizeProjectTimeMs(_playheadMs.value)
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
        if (_state.value.beatMarkers.isEmpty()) return
        // Beat markers are captured by the undo snapshot, so make this destructive
        // clear reversible like every other timeline edit instead of wiping silently.
        saveUndoState("Clear beat markers")
        _state.update { it.copy(beatMarkers = emptyList()) }
        saveProject()
    }

    // --- AI Suggestions ---
    fun dismissAiSuggestion() {
        _state.value.aiSuggestion?.id?.let { suggestionId ->
            suggestionSnoozedUntilMs = suggestionSnoozedUntilMs +
                (suggestionId to (System.currentTimeMillis() + SUGGESTION_SNOOZE_MS))
            savedStateHandle[SUGGESTION_SNOOZE_STATE_KEY] =
                java.util.HashMap(suggestionSnoozedUntilMs)
        }
        _state.update { it.copyAi { ai -> ai.copy(suggestion = null) } }
    }

    fun recordAiUsage(entry: AiUsageLedger.Entry) {
        _state.update { state ->
            state.copyAi { ai ->
                ai.copy(
                    usageLedger = AiUsageLedger.mergeOverlaps(
                        state.aiUsageLedger + entry
                    )
                )
            }
        }
        saveProject()
    }

    /**
     * Clear the per-project AI usage ledger. UndoAction already carried the ledger, so
     * this was permanent only because the snapshot was never taken.
     */
    fun clearAiUsageLedger() {
        if (_state.value.aiUsageLedger.isEmpty()) return
        saveUndoState("Clear AI usage ledger")
        _state.update { it.copyAi { ai -> ai.copy(usageLedger = emptyList()) } }
        saveProject()
        showToast(text(R.string.vm_ai_ledger_cleared_toast))
    }

    private fun generateAiSuggestion(clipId: String?) {
        if (clipId == null) {
            _state.update { it.copyAi { ai -> ai.copy(suggestion = null) } }
            return
        }
        val s = _state.value
        val clip = s.tracks.flatMap { it.clips }.firstOrNull { it.id == clipId } ?: return
        if (clipHasAudio(clip) &&
            (!s.waveforms.containsKey(clip.id) || loadedWaveformIdentities[clip.id] != clip.timelineMediaJobIdentity())
        ) {
            enqueueWaveformLoad(clip)
        }
        val engineSuggestion = editingSuggestionEngine.analyze(
            tracks = s.tracks,
            hasTranscript = s.v369.transcript != null,
            hasBeatMarkers = s.beatMarkers.isNotEmpty()
        )
        val nowMs = System.currentTimeMillis()
        val suggestion = engineSuggestion
            ?.takeIf { shouldShowEditingSuggestion(it.id, nowMs, suggestionSnoozedUntilMs) }
            ?.let {
            AiSuggestion(id = it.id, message = it.message, actionId = it.actionId)
        }
        _state.update { it.copyAi { ai -> ai.copy(suggestion = suggestion) } }
    }

    // --- Smart Reframe ---
    fun showSmartReframe() = showPanel(PanelId.SMART_REFRAME)
    fun hideSmartReframe() = hidePanel(PanelId.SMART_REFRAME)
    fun applySmartReframe(targetAspect: AspectRatio) {
        _state.update { it.copyAi { ai -> ai.copy(isReframing = true) } }
        viewModelScope.launch {
            try {
                val reframeSourceClip = getSelectedClip()?.takeIf(::clipHasVisual)
                    ?: _state.value.tracks
                        .flatMap { it.clips }
                        .firstOrNull(::clipHasVisual)

                if (reframeSourceClip != null && smartReframeEngine.isReady()) {
                    val config = SmartReframeEngine.ReframeConfig(
                        targetAspectRatio = targetAspect.toFloat()
                    )
                    val result = smartReframeEngine.analyzeForReframe(
                        reframeSourceClip.sourceUri, config
                    ) { /* progress tracked via isReframing state */ }

                    if (result != null && result.cropWindows.size >= 2) {
                        saveUndoState("Smart reframe")
                        val clipDurationMs = reframeSourceClip.durationMs
                        val intervalMs = clipDurationMs / (result.cropWindows.size - 1).coerceAtLeast(1)
                        val posKeyframes = result.cropWindows.flatMapIndexed { i, window ->
                            val timeMs = (i.toLong() * intervalMs).coerceAtMost(clipDurationMs)
                            listOf(
                                Keyframe(
                                    timeOffsetMs = timeMs,
                                    property = KeyframeProperty.POSITION_X,
                                    value = (window.centerX - 0.5f) * 2f
                                ),
                                Keyframe(
                                    timeOffsetMs = timeMs,
                                    property = KeyframeProperty.POSITION_Y,
                                    value = (0.5f - window.centerY) * 2f
                                )
                            )
                        }
                        val existingKf = reframeSourceClip.keyframes.filter {
                            it.property != KeyframeProperty.POSITION_X &&
                            it.property != KeyframeProperty.POSITION_Y
                        }
                        updateSelectedClip {
                            it.copy(keyframes = existingKf + posKeyframes)
                        }
                    }
                }

                val project = _state.value.project.copy(aspectRatio = targetAspect)
                _state.update { it.copy(
                    project = project,
                    export = it.export.copy(config = it.exportConfig.copy(aspectRatio = targetAspect)),
                    ai = it.ai.copy(isReframing = false)
                ) }
                saveProject()
                showToast(text(R.string.vm_reframed_toast, targetAspect.label))
                hideSmartReframe()
            } catch (e: Exception) {
                _state.update { it.copyAi { ai -> ai.copy(isReframing = false) } }
                AppLog.e("EditorViewModel", "Smart reframe failed", e)
                showToast(text(R.string.editor_reframe_failed_toast))
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
        showToast(text(R.string.vm_speed_preset_applied_toast))
        hideSpeedPresets()
    }

    // --- Auto-Edit ---
    fun showAutoEdit() = showPanel(PanelId.AUTO_EDIT)
    fun hideAutoEdit() {
        cancelAutoEdit()
        hidePanel(PanelId.AUTO_EDIT)
    }

    fun runAutoEdit(
        intent: AutoEditIntent = AutoEditIntent.HIGHLIGHT_REEL,
        targetDurationMs: Long = 60_000L
    ) {
        val clips = _state.value.tracks
            .filter { it.type == TrackType.VIDEO }
            .flatMap { it.clips }
            .sortedWith(compareBy<Clip> { it.timelineStartMs }.thenBy { it.id })
        if (clips.isEmpty()) { showToast(text(R.string.vm_add_video_clips_first_toast)); return }

        autoEditJob?.cancel()
        val generationId = ++autoEditGenerationId
        _state.update {
            it.copyAi { ai -> ai.copy(isAutoEditing = true, autoEditProposal = null) }
        }
        autoEditJob = viewModelScope.launch {
            try {
                val autoClips = clips.mapIndexed { index, clip ->
                    AutoEditClip(
                        clipId = clip.id,
                        clipFingerprint = autoEditClipFingerprint(clip),
                        uri = clip.sourceUri,
                        sourceStartMs = clip.trimStartMs,
                        sourceEndMs = clip.trimEndMs,
                        sourceOrder = index
                    )
                }
                val musicUri = _state.value.tracks
                    .filter { it.type == TrackType.AUDIO }
                    .flatMap { it.clips }
                    .firstOrNull()?.sourceUri

                val result = aiFeatures.generateAutoEdit(
                    clips = autoClips,
                    musicUri = musicUri,
                    targetDurationMs = targetDurationMs,
                    intent = intent
                )

                if (result.segments.isNotEmpty()) {
                    if (generationId == autoEditGenerationId) {
                        _state.update {
                            it.copyAi { ai -> ai.copy(isAutoEditing = false, autoEditProposal = result) }
                        }
                    }
                } else {
                    if (generationId == autoEditGenerationId) {
                        _state.update {
                            it.copyAi { ai -> ai.copy(isAutoEditing = false, autoEditProposal = null) }
                        }
                        showToast(text(R.string.vm_auto_edit_failed_toast))
                    }
                }
            } catch (_: CancellationException) {
                if (generationId == autoEditGenerationId) {
                    _state.update { it.copyAi { ai -> ai.copy(isAutoEditing = false) } }
                }
            } catch (e: Exception) {
                AppLog.e("EditorViewModel", "Auto Edit proposal failed", e)
                if (generationId == autoEditGenerationId) {
                    _state.update {
                        it.copyAi { ai -> ai.copy(isAutoEditing = false, autoEditProposal = null) }
                    }
                    showToast(text(R.string.vm_auto_edit_error_toast))
                }
            } finally {
                if (generationId == autoEditGenerationId) autoEditJob = null
            }
        }
    }

    fun cancelAutoEdit() {
        autoEditGenerationId++
        autoEditJob?.cancel()
        autoEditJob = null
        _state.update {
            it.copyAi { ai -> ai.copy(isAutoEditing = false, autoEditProposal = null) }
        }
    }

    fun applyAutoEditProposal() {
        val snapshot = _state.value
        val proposal = snapshot.autoEditProposal ?: return
        val sourceClips = snapshot.tracks.filter { it.type == TrackType.VIDEO }.flatMap { it.clips }
        val primaryVideoTrack = snapshot.tracks.firstOrNull { it.type == TrackType.VIDEO } ?: return
        val newClips = try {
            buildAutoEditExcerptClips(sourceClips, proposal)
        } catch (e: Exception) {
            AppLog.w("EditorViewModel", "Auto Edit proposal became stale", e)
            _state.update { it.copyAi { ai -> ai.copy(autoEditProposal = null) } }
            showToast(text(R.string.vm_auto_edit_stale_toast))
            return
        }

        val recordedAt = System.currentTimeMillis()
        val usageEntries = newClips.map { clip ->
            AiUsageRecordFactory.forClip(
                clip = clip,
                effectKind = AiUsageLedger.EffectKind.AUTO_EDIT_LOCAL,
                modelName = "ClearCut Auto Edit window scorer",
                recordedAtEpochMs = recordedAt
            )
        }
        saveUndoState("Apply Auto Edit")
        _state.update { state ->
            recalculateDuration(
                state.copy(
                    tracks = state.tracks.map { track ->
                        when {
                            track.id == primaryVideoTrack.id -> track.copy(clips = newClips)
                            track.type == TrackType.VIDEO -> track.copy(clips = emptyList())
                            else -> track
                        }
                    },
                    selectedClipId = null,
                    selectedClipIds = emptySet(),
                    ai = state.ai.copy(
                        autoEditProposal = null,
                        usageLedger = AiUsageLedger.mergeOverlaps(state.aiUsageLedger + usageEntries)
                    )
                )
            )
        }
        rebuildTimeline()
        saveProject()
        showToast(text(R.string.vm_auto_edit_created_toast, newClips.size))
        hidePanel(PanelId.AUTO_EDIT)
    }

    // --- TTS ---
    fun showTts() {
        pauseIfPlaying()
        if (!ttsEngine.isAvailable()) {
            ttsEngine.initialize {
                _state.update { it.copyAi { ai -> ai.copy(isTtsAvailable = true) } }
            }
        }
        _state.update {
            val dismissed = dismissedPanelState(it)
            dismissed.copy(
                panel = dismissed.panel.copy(
                    panels = dismissed.panels.closeAll().open(PanelId.TTS)
                ),
                ai = dismissed.ai.copy(isTtsAvailable = ttsEngine.isAvailable())
            )
        }
    }
    fun hideTts() { ttsEngine.stopPreview(); hidePanel(PanelId.TTS) }

    fun synthesizeTts(text: String, style: com.novacut.editor.engine.TtsEngine.VoiceStyle) {
        _state.update { it.copyAi { ai -> ai.copy(isSynthesizingTts = true) } }
        viewModelScope.launch {
            val file = ttsEngine.synthesize(text, style)
            _state.update { it.copyAi { ai -> ai.copy(isSynthesizingTts = false) } }
            if (file != null) {
                val uri = android.net.Uri.fromFile(file)
                // Query actual duration from the generated audio file
                val durationMs = videoEngine.getVideoDuration(uri).takeIf { it > 0 } ?: 3000L
                saveUndoState("Add TTS voice")
                // Helper now performs rebuildPlayerTimeline() + saveProject() internally.
                addClipToTrack(
                    uri = uri,
                    durationMs = durationMs,
                    trackType = TrackType.AUDIO,
                    aiUsageKind = AiUsageLedger.EffectKind.TTS_LOCAL,
                    aiUsageModelName = "Android TextToSpeech ${style.name.lowercase()}"
                )
                showToast(text(R.string.vm_tts_added_toast))
                hideTts()
            } else {
                showToast(text(R.string.vm_tts_failed_toast))
            }
        }
    }

    fun previewTts(text: String, style: com.novacut.editor.engine.TtsEngine.VoiceStyle) {
        pauseIfPlaying()
        viewModelScope.launch { ttsEngine.preview(text, style) }
    }

    fun stopTtsPreview() { ttsEngine.stopPreview() }

    // --- Effect Previews ---
    private val _effectPreviews = MutableStateFlow<Map<com.novacut.editor.model.EffectType, android.graphics.Bitmap>>(emptyMap())
    val effectPreviews: StateFlow<Map<com.novacut.editor.model.EffectType, android.graphics.Bitmap>> = _effectPreviews.asStateFlow()

    fun generateEffectPreviews() {
        val clip = getSelectedClip() ?: return
        viewModelScope.launch(Dispatchers.Default) {
            val sourceFrame = videoEngine.extractThumbnail(clip.sourceUri, clip.trimStartMs * 1000L, 80, 45)
                ?: return@launch
            val previews = mutableMapOf<com.novacut.editor.model.EffectType, android.graphics.Bitmap>()
            for (effectType in EffectPreviewRenderer.PREVIEWABLE_EFFECTS) {
                val preview = EffectPreviewRenderer.renderPreview(sourceFrame, effectType)
                if (preview != null) previews[effectType] = preview
            }
            _effectPreviews.value = previews
        }
    }

    // --- Effect Library ---
    fun showEffectLibrary() = showPanel(PanelId.EFFECT_LIBRARY)
    fun hideEffectLibrary() = hidePanel(PanelId.EFFECT_LIBRARY)

    fun exportClipEffects(name: String) {
        val clip = getSelectedClip() ?: return
        viewModelScope.launch {
            val file = effectShareEngine.exportEffects(name, clip.effects, clip.colorGrade, clip.audioEffects)
            if (file != null) {
                showToast(text(R.string.vm_effects_exported_toast, file.name))
            } else {
                showToast(text(R.string.vm_effects_export_failed_toast))
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
                showToast(text(R.string.vm_effects_imported_toast, imported.name))
                updatePreview()
            } else {
                showToast(text(R.string.vm_effects_import_invalid_toast))
            }
        }
    }

    // --- Noise Reduction ---
    fun showNoiseReduction() = showPanel(PanelId.NOISE_REDUCTION)
    fun hideNoiseReduction() {
        hidePanel(PanelId.NOISE_REDUCTION)
        _state.update { it.copyAi { ai -> ai.copy(noiseAnalysisResult = null) } }
    }

    // --- Sticker Picker ---
    fun showStickerPicker() = showPanel(PanelId.STICKER_PICKER)
    fun hideStickerPicker() = hidePanel(PanelId.STICKER_PICKER)

    // --- Drawing Overlay ---
    fun showDrawingMode() {
        pauseIfPlaying()
        _state.update {
            dismissedPanelState(it)
                .copy(isDrawingMode = true)
                .copyPanel { panel ->
                    panel.copy(panels = panel.panels.closeAll().open(PanelId.DRAWING))
                }
        }
    }
    fun hideDrawingMode() {
        _state.update {
            it.copy(isDrawingMode = false)
                .copyPanel { panel -> panel.copy(panels = panel.panels.close(PanelId.DRAWING)) }
        }
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
            showToast(text(R.string.vm_no_audio_to_analyze_toast))
            return
        }
        _state.update {
            it.copyAi { ai ->
                ai.copy(
                    isAnalyzingNoise = true,
                    noiseAnalysisResult = null
                )
            }
        }
        viewModelScope.launch {
            try {
                // Step 1: measure the source. A null profile means the audio could
                // not be measured — report that instead of guessing an SNR.
                val noiseProfile = noiseReductionEngine.analyzeNoise(clip.sourceUri)
                if (noiseProfile == null) {
                    _state.update {
                        it.copyAi { ai -> ai.copy(isAnalyzingNoise = false, noiseAnalysisResult = null) }
                    }
                    showToast(text(R.string.vm_noise_analysis_failed_toast))
                    return@launch
                }
                val analysisText = "Detected ${noiseProfile.type} noise, SNR: ${noiseProfile.estimatedSnrDb.toInt()} dB" +
                    (noiseProfile.dominantFreqHz?.let { " @ ${it.toInt()} Hz" } ?: "")
                _state.update { it.copyAi { ai -> ai.copy(noiseAnalysisResult = analysisText) } }
                showToast(analysisText)

                // Step 2: Apply noise reduction via NoiseReductionEngine
                val mode = when {
                    noiseProfile.estimatedSnrDb < 10f -> NoiseReductionEngine.NoiseReductionMode.AGGRESSIVE
                    noiseProfile.estimatedSnrDb < 20f -> NoiseReductionEngine.NoiseReductionMode.MODERATE
                    noiseProfile.estimatedSnrDb < 30f -> NoiseReductionEngine.NoiseReductionMode.LIGHT
                    else -> NoiseReductionEngine.NoiseReductionMode.OFF
                }

                if (mode == NoiseReductionEngine.NoiseReductionMode.OFF) {
                    _state.update { it.copyAi { ai -> ai.copy(isAnalyzingNoise = false) } }
                    showToast(text(R.string.vm_audio_clean_toast))
                    return@launch
                }

                val result = noiseReductionEngine.processAudio(clip.sourceUri, mode)
                val appliedFile = result.outputFile
                    .takeIf { result.outcome == NoiseReductionEngine.NoiseReductionOutcome.APPLIED }
                if (appliedFile == null) {
                    // NO_OP, UNAVAILABLE, or FAILED. The clip keeps its original
                    // source, no undo entry is pushed, and the reported text is
                    // the engine's own account of what happened.
                    _state.update {
                        it.copyAi { ai ->
                            ai.copy(
                                isAnalyzingNoise = false,
                                noiseAnalysisResult = "$analysisText — ${result.detail}"
                            )
                        }
                    }
                    showToast(result.detail)
                    return@launch
                }

                saveUndoState("Noise reduction")
                val processedUri = android.net.Uri.fromFile(appliedFile)
                _state.update { s ->
                    s.copy(
                        ai = s.ai.copy(
                            isAnalyzingNoise = false,
                            noiseAnalysisResult = "$analysisText — applied ${mode.displayName} (${result.detail})"
                        ),
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
                showToast(text(R.string.vm_noise_reduction_applied_toast, mode.displayName))
            } catch (e: Exception) {
                _state.update {
                    it.copyAi { ai ->
                        ai.copy(
                            isAnalyzingNoise = false,
                            noiseAnalysisResult = null
                        )
                    }
                }
                showToast(text(R.string.vm_noise_analysis_failed_toast))
            }
        }
    }

    // Helper: add clip to a track by type (used by TTS / voiceover).
    private fun addClipToTrack(
        uri: android.net.Uri,
        durationMs: Long,
        trackType: TrackType,
        aiUsageKind: AiUsageLedger.EffectKind? = null,
        aiUsageModelName: String? = null
    ) {
        // Refuse degenerate inputs that would otherwise violate Clip's `trimEndMs <= sourceDurationMs`
        // invariant the moment the user touched the new clip (e.g., a TTS file reporting 0 ms).
        if (durationMs <= 0L) {
            com.novacut.editor.engine.AppLog.w("EditorViewModel", "addClipToTrack ignored: non-positive durationMs=$durationMs for ${uri.redacted()}")
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
            s.copy(
                tracks = baseTracks.map { t ->
                    if (t.id == track.id) t.copy(clips = t.clips + clip) else t
                },
                ai = s.ai.copy(
                    usageLedger = if (aiUsageKind != null) {
                        AiUsageLedger.mergeOverlaps(
                            s.aiUsageLedger + AiUsageRecordFactory.forClip(
                                clip = clip,
                                effectKind = aiUsageKind,
                                modelName = aiUsageModelName.orEmpty()
                            )
                        )
                    } else {
                        s.aiUsageLedger
                    }
                )
            )
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

    // --- Split Preview Comparison ---
    fun toggleSplitPreview() {
        _state.update { it.copy(isSplitPreviewEnabled = !it.isSplitPreviewEnabled) }
    }

    // Helper for beat sync splitting
    private fun splitClipAt(clipId: String, positionMs: Long): Map<String, String> {
        val initialState = _state.value
        val splitPositionMs = initialState.project.timelineTimebase.snapMs(positionMs)
        val clipIdsToSplit = linkedSplitCandidateIds(initialState.tracks, setOf(clipId), splitPositionMs)
        if (clipIdsToSplit.isEmpty()) return emptyMap()
        val regroupedClipIds = regroupedClipIdsForSplit(initialState.tracks, clipIdsToSplit, splitPositionMs)
        if (initialState.tracks.any { track ->
                track.isLocked && track.clips.any { it.id in clipIdsToSplit || it.id in regroupedClipIds }
            }
        ) return emptyMap()
        val candidates = clipIdsToSplit.mapNotNull { candidateId ->
            initialState.tracks.findClipLocation(candidateId)?.clip
        }
        if (candidates.size != clipIdsToSplit.size) return emptyMap()

        val newIdsByOldId = candidates.associate { it.id to java.util.UUID.randomUUID().toString() }
        val newGroupIdsByOldId = candidates.mapNotNull { it.groupId }.distinct()
            .associateWith { java.util.UUID.randomUUID().toString() }
        val newTrackedObjectIdsByClipId = newIdsByOldId.mapValues { (oldClipId, _) ->
            initialState.trackedObjects
                .filter { it.sourceClipId == oldClipId }
                .associate { it.id to java.util.UUID.randomUUID().toString() }
        }
        val splitTrackedObjects = newIdsByOldId.flatMap { (oldClipId, newClipId) ->
            val idMap = newTrackedObjectIdsByClipId[oldClipId].orEmpty()
            initialState.trackedObjects
                .filter { it.sourceClipId == oldClipId }
                .map { tracked ->
                    tracked.copy(id = idMap.getValue(tracked.id), sourceClipId = newClipId)
                }
        }

        _state.update { s ->
            val tracks = s.tracks.map { track ->
                val boundaryCorrections = mutableListOf<Pair<String, Long>>()
                track.copy(clips = buildList {
                    track.clips.forEach { clip ->
                        val newId = newIdsByOldId[clip.id]
                        if (newId == null) {
                            val rightGroupId = clip.groupId?.let { newGroupIdsByOldId[it] }
                            add(
                                if (rightGroupId != null && clip.timelineStartMs >= splitPositionMs) {
                                    clip.copy(groupId = rightGroupId)
                                } else clip
                            )
                        } else {
                            val split = splitTimelineClip(
                                clip = clip,
                                playheadMs = splitPositionMs,
                                newClipId = newId,
                                newLinkedClipId = clip.linkedClipId?.let { newIdsByOldId[it] ?: it },
                                rightGroupId = clip.groupId?.let { newGroupIdsByOldId[it] },
                                rightTrackedObjectIds = newTrackedObjectIdsByClipId[clip.id].orEmpty(),
                                idFactory = { java.util.UUID.randomUUID().toString() }
                            )
                            if (split == null) add(clip) else {
                                add(split.left)
                                add(split.right)
                                boundaryCorrections += split.right.id to
                                    (split.right.timelineEndMs - clip.timelineEndMs)
                            }
                        }
                    }
                }).let { updatedTrack ->
                    boundaryCorrections.fold(updatedTrack) { currentTrack, (rightId, correctionMs) ->
                        shiftFollowingClipsPreservingGaps(
                            track = currentTrack,
                            afterClipId = rightId,
                            correctionMs = correctionMs,
                        )
                    }
                }
            }
            val waveforms = newIdsByOldId.entries.fold(s.waveforms) { acc, (oldId, newId) ->
                acc[oldId]?.let { waveform -> acc + (newId to waveform) } ?: acc
            }
            s.copy(
                tracks = tracks,
                waveforms = waveforms,
                trackedObjects = s.trackedObjects + splitTrackedObjects
            )
        }
        return newIdsByOldId
    }

    private fun rebuildTimeline() {
        rebuildPlayerTimeline()
    }

    // --- Cut Assistant (review proposed silences + filler-word cuts) ---

    /**
     * Generate a non-destructive review of silences and filler words across
     * every video/audio clip currently on the timeline. Stores the result in
     * `state.cutAssistantReview` for the UI to render. The timeline is not
     * mutated until [applyAcceptedCuts] is called.
     */
    fun proposeCutsForReview(config: SilenceDetectionEngine.AutoCutConfig = SilenceDetectionEngine.AutoCutConfig()) {
        cutAssistantReviewJob?.cancel()
        val initialAudioClips = _state.value.tracks
            .filter { it.type == TrackType.VIDEO || it.type == TrackType.AUDIO }
            .flatMap { it.clips }
            .filter { it.sourceDurationMs > 0L }
        if (initialAudioClips.isEmpty()) {
            showToast(text(R.string.vm_cut_add_clip_first_toast))
            return
        }
        // Capture only the ids we plan to scan — by the time the IO scan returns
        // some of those clips may have been deleted, trimmed, or replaced. The
        // post-scan filter below re-validates against the live state so we never
        // hand the engine a stale Track snapshot.
        val targetClipIds = initialAudioClips.map { it.id }.toSet()
        cutAssistantReviewJob = viewModelScope.launch {
            showToast(text(R.string.vm_cut_scanning_toast, initialAudioClips.size))
            try {
                val perClipAudio = withContext(Dispatchers.IO) {
                    initialAudioClips.associate { clip ->
                        val targetCount = ((clip.sourceDurationMs / 50L)
                            .coerceIn(200L, 10_000L)).toInt()
                        val waveform = audioEngine.extractWaveform(clip.sourceUri, targetCount)
                        val sampleRate = if (clip.sourceDurationMs > 0L) {
                            (waveform.size * 1000L / clip.sourceDurationMs).coerceAtLeast(1L).toInt()
                        } else 20
                        clip.id to com.novacut.editor.engine.CutAssistantEngine.ClipAudio(
                            clipId = clip.id,
                            waveform = waveform,
                            sampleRate = sampleRate,
                            words = perClipWordsFor(clip.id, _state.value)
                        )
                    }
                }
                // Re-read live tracks AFTER the IO scan completes — clips may have
                // been mutated while we were busy. Filter both sides so the engine
                // only sees clips that still exist in both the scan and the live
                // state (and whose key invariants haven't drifted).
                val liveTracks = _state.value.tracks
                val liveClipIds = liveTracks.flatMap { it.clips }.map { it.id }.toSet()
                val validIds = targetClipIds intersect liveClipIds intersect perClipAudio.keys
                if (validIds.isEmpty()) {
                    showToast(text(R.string.vm_cut_source_missing_toast))
                    return@launch
                }
                val filteredTracks = liveTracks.map { track ->
                    track.copy(clips = track.clips.filter { it.id in validIds })
                }
                val filteredAudio = perClipAudio.filterKeys { it in validIds }
                val review = cutAssistantEngine.review(filteredTracks, filteredAudio, config).acceptAll()
                _state.update {
                    it.copy(ai = it.ai.copy(cutAssistantReview = review))
                        .copyPanel { panel -> panel.copy(panels = panel.panels.closeAll()) }
                }
                showToast(
                    if (review.proposals.isEmpty()) "Cut Assistant: nothing to trim"
                    else "Cut Assistant: ${review.proposals.size} proposed cut(s)"
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e("CutAssistant", "review failed", e)
                showToast(text(R.string.editor_cut_assistant_failed_toast))
            }
        }
    }

    fun toggleCutProposal(id: String) {
        _state.update { s ->
            s.copyAi { ai -> ai.copy(cutAssistantReview = s.cutAssistantReview?.toggle(id)) }
        }
    }

    fun acceptAllCutProposals() {
        _state.update { s ->
            s.copyAi { ai -> ai.copy(cutAssistantReview = s.cutAssistantReview?.acceptAll()) }
        }
    }

    fun rejectAllCutProposals() {
        _state.update { s ->
            s.copyAi { ai -> ai.copy(cutAssistantReview = s.cutAssistantReview?.rejectAll()) }
        }
    }

    fun dismissCutAssistantReview() {
        _state.update { it.copyAi { ai -> ai.copy(cutAssistantReview = null) } }
    }

    /**
     * Apply every accepted proposal as a single undoable batch. Operations are
     * processed latest-first so each split's right-hand neighbours stay at
     * stable positions while we work backwards through the timeline.
     */
    fun applyAcceptedCuts() {
        val review = _state.value.cutAssistantReview ?: return
        val ops = cutAssistantEngine.planAcceptedOperations(review)
        if (ops.isEmpty()) {
            showToast(text(R.string.vm_cut_none_selected_toast))
            return
        }
        val applicableOps = ops.filter { op ->
            when (op) {
                is com.novacut.editor.engine.CutAssistantEngine.CutOperation.RippleDelete -> {
                    val state = _state.value
                    val splitIds = linkedClipIds(state.tracks, op.clipId)
                    val groupedIds = regroupedClipIdsForSplit(state.tracks, splitIds, op.timelineStartMs)
                    canDeleteTimelineRangeAtomically(
                        tracks = state.tracks,
                        clipId = op.clipId,
                        startMs = op.timelineStartMs,
                        endMs = op.timelineEndMs
                    ) && state.tracks.none { track ->
                        track.isLocked && track.clips.any { it.id in splitIds || it.id in groupedIds }
                    }
                }
            }
        }
        if (applicableOps.isEmpty()) {
            showToast(text(R.string.vm_cut_no_change_toast))
            return
        }
        saveUndoState("Apply Cut Assistant")
        var appliedSecondsReclaimed = 0L
        var appliedCount = 0
        applicableOps.forEach { op ->
            when (op) {
                is com.novacut.editor.engine.CutAssistantEngine.CutOperation.RippleDelete -> {
                    val originalClip = _state.value.tracks
                        .flatMap { it.clips }
                        .firstOrNull { it.id == op.clipId } ?: return@forEach
                    val firstSplitIds = splitClipAt(op.clipId, op.timelineStartMs)
                    // The middle+tail slice is the new id created by the first split.
                    val rightHalfId = firstSplitIds[op.clipId]
                        ?: return@forEach
                    val secondSplitIds = splitClipAt(rightHalfId, op.timelineEndMs)
                    val tailId = secondSplitIds[rightHalfId] ?: return@forEach
                    _state.update { s ->
                        val middleClipIds = expandTimelineEditClipIds(s.tracks, setOf(rightHalfId))
                        val markerRippleRanges = s.tracks
                            .firstOrNull { track -> track.clips.any { it.id == rightHalfId } }
                            ?.clips
                            ?.filter { it.id in middleClipIds }
                            ?.map { it.timelineStartMs to it.timelineEndMs }
                            .orEmpty()
                        val deletedSpanMs = s.tracks
                            .flatMap { it.clips }
                            .filter { it.id in middleClipIds }
                            .maxOfOrNull { it.durationMs }
                            ?: return@update s
                        appliedSecondsReclaimed += deletedSpanMs
                        appliedCount++
                        val timebase = s.project.timelineTimebase
                        s.copy(
                            tracks = rippleDeleteClips(s.tracks, middleClipIds, timebase),
                            waveforms = s.waveforms - middleClipIds,
                            trackedObjects = s.trackedObjects.filterNot {
                                it.sourceClipId in middleClipIds
                            },
                            timelineMarkers = s.timelineMarkers.mapNotNull { marker ->
                                rippleTimelinePosition(marker.timeMs, markerRippleRanges, timebase)
                                    ?.let { marker.copy(timeMs = it) }
                            },
                            chapterMarkers = s.chapterMarkers.mapNotNull { marker ->
                                rippleTimelinePosition(marker.timeMs, markerRippleRanges, timebase)
                                    ?.let { marker.copy(timeMs = it) }
                            },
                            beatMarkers = s.beatMarkers.mapNotNull { markerMs ->
                                rippleTimelinePosition(markerMs, markerRippleRanges, timebase)
                            }
                        )
                    }
                    AppLog.d(
                        "CutAssistant",
                        "Applied ${op.reason} cut ${op.timelineStartMs}..${op.timelineEndMs} on ${originalClip.id} (rightHalf=$rightHalfId, tail=$tailId)"
                    )
                }
            }
        }
        _state.update { s ->
            recalculateDuration(s.copyAi { ai -> ai.copy(cutAssistantReview = null) })
        }
        rebuildPlayerTimeline()
        saveProject()
        if (appliedCount == 0) {
            showToast(text(R.string.vm_cut_no_change_toast))
        } else {
            showToast(text(R.string.vm_cut_applied_toast, appliedCount, appliedSecondsReclaimed / 1000))
        }
    }

    private fun perClipWordsFor(
        clipId: String,
        state: EditorState
    ): List<com.novacut.editor.engine.whisper.SherpaAsrEngine.WordTimestamp> {
        val transcript = state.v369.transcript ?: return emptyList()
        if (transcript.clipId != clipId) return emptyList()
        return transcript.words.map { w ->
            com.novacut.editor.engine.whisper.SherpaAsrEngine.WordTimestamp(
                word = w.text,
                startTimeMs = w.startMs,
                endTimeMs = w.endMs,
                confidence = w.confidence
            )
        }
    }

    // --- Tracked objects (object-aware editing scaffold) ---

    /**
     * Insert or update a tracked object. Persisted via AutoSaveState so the
     * track survives app restart even before SAM 2 / MediaPipe are wired up
     * (manual placements still ride this same surface).
     */
    fun upsertTrackedObject(obj: com.novacut.editor.model.TrackedObject) {
        saveUndoState("Update tracked object")
        _state.update { s ->
            val existingIdx = s.trackedObjects.indexOfFirst { it.id == obj.id }
            val nextList = if (existingIdx >= 0) {
                s.trackedObjects.toMutableList().also { it[existingIdx] = obj }
            } else {
                s.trackedObjects + obj
            }
            s.copy(trackedObjects = nextList)
        }
        saveProject()
    }

    fun removeTrackedObject(id: String) {
        if (_state.value.trackedObjects.none { it.id == id }) return
        saveUndoState("Remove tracked object")
        _state.update { s ->
            s.copy(trackedObjects = s.trackedObjects.filterNot { it.id == id })
        }
        saveProject()
    }

    fun setTrackedObjectEnabled(id: String, enabled: Boolean) {
        _state.update { s ->
            s.copy(trackedObjects = s.trackedObjects.map { obj ->
                if (obj.id == id) obj.copy(isEnabled = enabled) else obj
            })
        }
        updatePreview()
        saveProject()
    }

    fun applyTrackedMosaicToObject(trackedObjectId: String) {
        val state = _state.value
        val trackedObject = state.trackedObjects.firstOrNull { it.id == trackedObjectId }
        if (trackedObject == null || !trackedObject.isEnabled || trackedObject.keyframes.isEmpty()) {
            showToast(text(R.string.vm_no_tracked_data_toast))
            return
        }

        val sourceClip = state.tracks
            .asSequence()
            .flatMap { it.clips.asSequence() }
            .firstOrNull { it.id == trackedObject.sourceClipId }
        if (sourceClip == null) {
            showToast(text(R.string.vm_tracked_clip_missing_toast))
            return
        }

        val alreadyApplied = sourceClip.effects.any {
            it.type == EffectType.TRACKED_MOSAIC && it.targetTrackedObjectId == trackedObject.id
        }
        if (alreadyApplied) {
            showToast(text(R.string.vm_tracked_mosaic_exists_toast))
            return
        }

        val effect = Effect(
            type = EffectType.TRACKED_MOSAIC,
            params = EffectType.defaultParams(EffectType.TRACKED_MOSAIC),
            targetTrackedObjectId = trackedObject.id
        )

        saveUndoState("Apply tracked mosaic")
        updateClipById(sourceClip.id) { clip ->
            clip.copy(effects = clip.effects + effect)
        }
        _state.update {
            it.copy(
                selectedClipId = sourceClip.id
            ).copyPanel { panel -> panel.copy(selectedEffectId = effect.id) }
        }
        updatePreview()
        saveProject()
        showToast(text(R.string.vm_tracked_mosaic_applied_toast, trackedObject.label))
    }

    // --- Storyboard ---

    fun showStoryboard() {
        pauseIfPlaying()
        _state.update { it.copyPanel { p -> p.copy(panels = p.panels.open(PanelId.STORYBOARD)) } }
    }

    fun hideStoryboard() {
        _state.update { it.copyPanel { p -> p.copy(panels = p.panels.close(PanelId.STORYBOARD)) } }
    }

    fun addStoryboardCard(shotText: String) {
        saveUndoState("Add storyboard card")
        _state.update { s ->
            val nextOrdinal = (s.storyboardCards.maxOfOrNull { it.ordinal } ?: -1) + 1
            s.copy(storyboardCards = s.storyboardCards + com.novacut.editor.model.StoryboardCard(
                ordinal = nextOrdinal,
                shotText = shotText
            ))
        }
        saveProject()
    }

    fun updateStoryboardCard(cardId: String, shotText: String? = null, status: com.novacut.editor.model.StoryboardCardStatus? = null, targetDurationMs: Long? = null) {
        _state.update { s ->
            s.copy(storyboardCards = s.storyboardCards.map { card ->
                if (card.id == cardId) card.copy(
                    shotText = shotText ?: card.shotText,
                    status = status ?: card.status,
                    targetDurationMs = targetDurationMs ?: card.targetDurationMs
                ) else card
            })
        }
        saveProject()
    }

    fun removeStoryboardCard(cardId: String) {
        if (_state.value.storyboardCards.none { it.id == cardId }) return
        saveUndoState("Remove storyboard card")
        _state.update { s ->
            s.copy(storyboardCards = s.storyboardCards.filterNot { it.id == cardId })
        }
        saveProject()
    }

    fun reorderStoryboardCards(fromIndex: Int, toIndex: Int) {
        _state.update { s ->
            val cards = s.storyboardCards.toMutableList()
            if (fromIndex !in cards.indices || toIndex !in cards.indices) return@update s
            val moved = cards.removeAt(fromIndex)
            cards.add(toIndex, moved)
            s.copy(storyboardCards = cards.mapIndexed { i, c -> c.copy(ordinal = i) })
        }
        saveProject()
    }

    // --- Multi-Cam Sync ---
    fun syncMultiCamClips() {
        val syncEligibleClips = _state.value.tracks
            .filter { it.type == TrackType.VIDEO }
            .flatMap { it.clips }
            .filter(::clipSupportsAudioSync)
        if (syncEligibleClips.size < 2) {
            showToast(text(R.string.vm_need_multicam_clips_toast))
            return
        }
        viewModelScope.launch {
            showToast(text(R.string.vm_syncing_clips_toast))
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
                    showToast(text(R.string.vm_synced_clips_toast, offsets.size))
                } else {
                    showToast(text(R.string.vm_sync_failed_toast))
                }
            } catch (e: Exception) {
                AppLog.e("EditorViewModel", "Multi-cam sync failed", e)
                showToast(text(R.string.editor_multicam_sync_failed_toast))
            }
        }
    }

    fun applySpeakerAutoSwitch(
        speakerTurns: List<SpeakerSwitchPlanner.SpeakerTurn>,
        speakerToAngle: Map<String, Int> = emptyMap(),
        minDwellMs: Long = 2000L
    ) {
        val videoTracks = _state.value.tracks.filter { it.type == TrackType.VIDEO && it.isVisible }
        if (videoTracks.size < 2) {
            showToast(text(R.string.multicam_need_synced_tracks_toast))
            return
        }
        if (speakerTurns.isEmpty()) {
            showToast(text(R.string.multicam_no_speaker_turns_toast))
            return
        }

        val angles = videoTracks.mapIndexed { i, _ ->
            val assignedSpeaker = speakerToAngle.entries.firstOrNull { it.value == i }?.key
            SpeakerSwitchPlanner.Angle(angleIndex = i, assignedSpeakerId = assignedSpeaker)
        }
        val policy = SpeakerSwitchPlanner.SwitchPolicy(minDwellMs = minDwellMs)
        val plan = SpeakerSwitchPlanner.plan(speakerTurns, angles, policy)

        if (plan.cuts.isEmpty()) {
            showToast(text(R.string.multicam_no_switches_toast))
            return
        }

        saveUndoState("Speaker auto-switch")

        val primaryTrack = videoTracks.first()
        val primaryClips = primaryTrack.clips.sortedBy { it.timelineStartMs }
        if (primaryClips.isEmpty()) return

        val newClips = mutableListOf<Clip>()
        for ((i, cut) in plan.cuts.withIndex()) {
            val nextCutMs = if (i + 1 < plan.cuts.size) plan.cuts[i + 1].timelineMs else
                primaryClips.maxOf { it.timelineEndMs }
            val sourceTrack = videoTracks.getOrNull(cut.angleIndex) ?: continue
            val sourceClip = sourceTrack.clips.firstOrNull {
                it.timelineStartMs <= cut.timelineMs && it.timelineEndMs > cut.timelineMs
            } ?: continue

            val trimStart = (cut.timelineMs - sourceClip.timelineStartMs + sourceClip.trimStartMs)
                .coerceIn(sourceClip.trimStartMs, sourceClip.trimEndMs - 100L)
            val trimEnd = ((nextCutMs - sourceClip.timelineStartMs) + sourceClip.trimStartMs)
                .coerceIn(trimStart + 100L, sourceClip.trimEndMs)

            newClips += sourceClip.copy(
                id = java.util.UUID.randomUUID().toString(),
                timelineStartMs = cut.timelineMs,
                trimStartMs = trimStart,
                trimEndMs = trimEnd
            )
        }

        if (newClips.isEmpty()) return

        _state.update { s ->
            val updatedTracks = s.tracks.map { track ->
                if (track.id == primaryTrack.id) {
                    track.copy(clips = newClips.sortedBy { it.timelineStartMs })
                } else track
            }
            recalculateDuration(s.copy(tracks = updatedTracks))
        }
        rebuildPlayerTimeline()
        saveProject()
        showToast(text(R.string.multicam_applied_switches_toast, plan.cuts.size))
    }

    // --- Slip/Slide Edit ---
    private var isSlipEditActive = false
    private var isSlideEditActive = false
    private var slipEditStartTracks: List<Track>? = null
    private var slideEditStartTracks: List<Track>? = null
    private val timelineGestureUndo = GestureUndoTransaction<UndoAction> { initial, current ->
        hasSameClipTiming(initial.tracks, current.tracks)
    }

    fun beginSlipEdit() {
        if (isSlipEditActive) return
        if (!beginTimelineGestureUndo("Slip edit")) return
        isSlipEditActive = true
        slipEditStartTracks = _state.value.tracks
        // Freeze the player while the user drags so we don't rebuild it on every
        // pixel of motion. `setScrubbingMode(true)` lets ExoPlayer skip the expensive
        // seek+decode work; the actual timeline rebuild happens in endSlipEdit.
        playbackCoordinator.setScrubbingMode(true)
    }

    fun endSlipEdit() = finishSlipEdit(commit = true)

    fun cancelSlipEdit() = finishSlipEdit(commit = false)

    private fun finishSlipEdit(commit: Boolean) {
        if (!isSlipEditActive) return
        isSlipEditActive = false
        val finish = finishTimelineGestureUndo("Slip edit", commit)
        slipEditStartTracks = null
        playbackCoordinator.setScrubbingMode(false)
        if (finish.hadMutation) {
            rebuildPlayerTimeline()
            saveProject()
        }
    }

    fun beginSlideEdit() {
        if (isSlideEditActive) return
        if (!beginTimelineGestureUndo("Slide edit")) return
        isSlideEditActive = true
        slideEditStartTracks = _state.value.tracks
        playbackCoordinator.setScrubbingMode(true)
    }

    fun endSlideEdit() = finishSlideEdit(commit = true)

    fun cancelSlideEdit() = finishSlideEdit(commit = false)

    private fun finishSlideEdit(commit: Boolean) {
        if (!isSlideEditActive) return
        isSlideEditActive = false
        val finish = finishTimelineGestureUndo("Slide edit", commit)
        slideEditStartTracks = null
        playbackCoordinator.setScrubbingMode(false)
        if (finish.hadMutation) {
            rebuildPlayerTimeline()
            saveProject()
        }
    }

    fun slipClip(clipId: String, slipAmountMs: Long) {
        if (quantizeProjectDurationMs(slipAmountMs) == 0L) return
        val baseTracks = slipEditStartTracks ?: _state.value.tracks
        val linkedIds = linkedClipIds(baseTracks, clipId)
        if (baseTracks.any { track ->
                track.isLocked && track.clips.any { it.id in linkedIds }
            }
        ) {
            return
        }
        val candidateTracks = slipLinkedClipsOnTimeline(
            baseTracks,
            linkedIds,
            slipAmountMs,
            _state.value.project.timelineTimebase,
        )
        if (hasSameClipTiming(candidateTracks, baseTracks)) return
        markTimelineGestureMutation("Slip edit")
        _state.update { it.copy(tracks = candidateTracks) }
        // Intentionally NOT calling rebuildPlayerTimeline() here. Slip-drag fires
        // this method at touch-event rate (60–120 Hz); rebuilding ExoPlayer's
        // MediaItem set on every tick was the root cause of the "clunky" timeline.
        // Rebuild happens once in endSlipEdit() instead. ScrubbingMode in
        // beginSlipEdit() already suppresses intermediate decode work.
    }

    fun slideClip(clipId: String, slideAmountMs: Long) {
        if (quantizeProjectDurationMs(slideAmountMs) == 0L) return
        val tracks = slideEditStartTracks ?: _state.value.tracks
        val candidateTracks = planSlideTracks(tracks, clipId, slideAmountMs)
        if (hasSameClipTiming(candidateTracks, tracks)) return
        markTimelineGestureMutation("Slide edit")
        _state.update { it.copy(tracks = candidateTracks) }
        // Deferred to endSlideEdit() to avoid per-frame player rebuilds during drag.
        // Same perf fix as slipClip — see comment there.
    }

    private fun planSlideTracks(
        tracks: List<Track>,
        clipId: String,
        slideAmountMs: Long,
    ): List<Track> {
        val linkedLocation = tracks.findClipLocation(clipId)?.clip?.linkedClipId
            ?.let { linkedId -> tracks.findClipLocation(linkedId) }
        val primaryLocation = tracks.findClipLocation(clipId) ?: return tracks
        if (primaryLocation.track.isLocked || (linkedLocation?.track?.isLocked == true)) return tracks

        val primaryBounds = calculateSlideBounds(primaryLocation.track, clipId) ?: return tracks
        val timebase = _state.value.project.timelineTimebase
        val requestedDeltaFrames = if (slideAmountMs < 0L) {
            -timebase.frameIndexAt(kotlin.math.abs(slideAmountMs))
        } else {
            timebase.frameIndexAt(slideAmountMs)
        }
        val primaryFrame = timebase.frameIndexAt(primaryBounds.currentStartMs)
        var minDeltaFrames = timebase.frameIndexAtOrAfter(primaryBounds.minStartMs) - primaryFrame
        var maxDeltaFrames = timebase.frameIndexAtOrBefore(primaryBounds.maxStartMs) - primaryFrame

        linkedLocation?.let { location ->
            val linkedBounds = calculateSlideBounds(location.track, location.clip.id) ?: return tracks
            val linkedFrame = timebase.frameIndexAt(linkedBounds.currentStartMs)
            minDeltaFrames = maxOf(
                minDeltaFrames,
                timebase.frameIndexAtOrAfter(linkedBounds.minStartMs) - linkedFrame,
            )
            maxDeltaFrames = minOf(
                maxDeltaFrames,
                timebase.frameIndexAtOrBefore(linkedBounds.maxStartMs) - linkedFrame,
            )
        }

        if (maxDeltaFrames < minDeltaFrames) return tracks
        val synchronizedDeltaFrames = requestedDeltaFrames.coerceIn(minDeltaFrames, maxDeltaFrames)
        if (synchronizedDeltaFrames == 0L) return tracks

        return tracks.map { track ->
            when {
                track.id == primaryLocation.track.id -> {
                    slideClipOnTrack(
                        track = track,
                        clipId = clipId,
                        newStartMs = timebase.addFrames(
                            primaryBounds.currentStartMs,
                            synchronizedDeltaFrames,
                        )
                    )
                }
                linkedLocation != null && track.id == linkedLocation.track.id -> {
                    val linkedBounds = calculateSlideBounds(track, linkedLocation.clip.id) ?: return@map track
                    slideClipOnTrack(
                        track = track,
                        clipId = linkedLocation.clip.id,
                        newStartMs = timebase.addFrames(
                            linkedBounds.currentStartMs,
                            synchronizedDeltaFrames,
                        )
                    )
                }
                else -> track
            }
        }
    }

    // --- Export ---
    fun cancelExport() = exportDelegate.cancelExport()
    fun resumeExport(entry: com.novacut.editor.engine.ExportHistoryEntry) = exportDelegate.resumeExport(entry)
    fun confirmPendingExport() = exportDelegate.confirmPendingExport()
    fun dismissPendingExport() = exportDelegate.dismissPendingExport()

    // --- Media Manager ---
    fun showMediaManager() {
        _state.update {
            it.copyMedia { media ->
                media.copy(metadataSidecarExport = MetadataSidecarExportUiState())
            }
        }
        refreshMediaRelinkReports(openPanelOnProblems = false)
        showPanel(PanelId.MEDIA_MANAGER)
    }
    fun hideMediaManager() = hidePanel(PanelId.MEDIA_MANAGER)

    fun updateMediaAssetMetadata(
        uri: Uri,
        notes: String,
        tags: List<String>,
    ) {
        val normalizedNotes = normalizeMediaAssetNotes(notes)
        val normalizedTags = normalizeMediaAssetTags(tags)
        var updatedAsset: ProjectMediaAsset? = null
        _state.update { current ->
            val uriString = uri.toString()
            val existing = current.media.mediaAssets.firstOrNull { asset ->
                asset.managedUri == uriString || asset.originalUri == uriString
            }
            val base = existing ?: ProjectMediaAsset(
                assetId = uriString,
                managedUri = uriString,
                originalUri = uriString,
                displayName = uri.lastPathSegment,
                mediaType = "video",
                mimeType = null,
                sizeBytes = 0L,
                durationMs = null,
                width = null,
                height = null,
                quickFingerprint = null,
                importStatus = "external",
                lastVerifiedAtEpochMs = System.currentTimeMillis(),
            )
            val next = base.copy(notes = normalizedNotes, tags = normalizedTags)
            updatedAsset = next
            current.copyMedia { media ->
                media.copy(
                    mediaAssets = (media.mediaAssets.filterNot { asset ->
                        asset.managedUri == uriString || asset.originalUri == uriString
                    } + next).distinctBy { it.assetId }
                )
            }
        }
        updatedAsset?.let { asset ->
            viewModelScope.launch(Dispatchers.IO) {
                writeManagedMediaAssetAnnotations(appContext, asset)
            }
        }
        saveProject()
    }

    fun exportMetadataSidecar(
        uri: Uri,
        track: MetadataSidecarTrack,
        format: MetadataSidecarFormat,
    ) {
        val current = _state.value.media.metadataSidecarExport
        if (current.isExporting) return
        _state.update {
            it.copyMedia { media ->
                media.copy(
                    metadataSidecarExport = MetadataSidecarExportUiState(isExporting = true)
                )
            }
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                metadataSidecarEngine.export(uri, track, format)
            }
            val next = when (result) {
                is MetadataSidecarExportResult.Success -> {
                    val file = result.file
                    MetadataSidecarExportUiState(
                        file = MetadataSidecarExportFile(
                            path = file.absolutePath,
                            fileName = file.name,
                            sizeBytes = file.length(),
                            format = result.format,
                        ),
                        message = appContext.getString(
                            R.string.vm_metadata_sidecar_exported,
                            file.name,
                        ),
                    )
                }
                is MetadataSidecarExportResult.Unsupported ->
                    MetadataSidecarExportUiState(errorMessage = result.reason)
                is MetadataSidecarExportResult.Failed ->
                    MetadataSidecarExportUiState(errorMessage = result.reason)
            }
            _state.update {
                it.copyMedia { media -> media.copy(metadataSidecarExport = next) }
            }
        }
    }

    fun dismissMetadataSidecarExport() {
        _state.update {
            it.copyMedia { media ->
                media.copy(metadataSidecarExport = MetadataSidecarExportUiState())
            }
        }
    }

    fun reportMetadataSidecarShareFailure() {
        _state.update {
            it.copyMedia { media ->
                media.copy(
                    metadataSidecarExport = media.metadataSidecarExport.copy(
                        message = null,
                        errorMessage = appContext.getString(R.string.vm_metadata_sidecar_share_failed),
                    )
                )
            }
        }
    }

    private fun refreshMediaRelinkReports(openPanelOnProblems: Boolean) {
        val tracks = _state.value.tracks
        val imageOverlays = _state.value.imageOverlays
        if (tracks.flatMap { it.clips }.isEmpty() && imageOverlays.isEmpty()) {
            mediaRelinkProbeJob?.cancel()
            val healthReport = analyzeMediaHealthForState(_state.value, emptyList())
            _state.update { state ->
                state.copyMedia { media ->
                    media.copy(
                        relinkReports = emptyMap(),
                        diagnostics = emptyMap(),
                        healthReport = healthReport
                    )
                }
            }
            return
        }

        mediaRelinkProbeJob?.cancel()
        mediaRelinkProbeJob = viewModelScope.launch {
            val previousMissingIds = _state.value.media.relinkReports
                .filterValues { it.state == MediaRelinkProbe.RelinkState.MISSING }
                .keys
            val reports = mediaRelinkProbe.probeClips(tracks) + mediaRelinkProbe.probeImageOverlays(imageOverlays)
            val diagnostics = mediaDiagnosticsProbe.probeTracks(tracks, imageOverlays)
            val healthReport = analyzeMediaHealthForState(_state.value, diagnostics.values)
            val missingCount = reports.values.count { it.state == MediaRelinkProbe.RelinkState.MISSING }
            val unknownCount = reports.values.count { it.state == MediaRelinkProbe.RelinkState.UNKNOWN }
            val healthBlockingCount = healthReport.blockingCount
            val healthWarningCount = healthReport.warningCount
            _state.update { state ->
                state.copy(
                    media = state.media.copy(
                        relinkReports = reports,
                        diagnostics = diagnostics,
                        healthReport = healthReport
                    )
                )
                    .copyPanel { panel ->
                        panel.copy(
                            panels = if (
                                openPanelOnProblems &&
                                missingCount + unknownCount + healthBlockingCount + healthWarningCount > 0
                            ) {
                                panel.panels.open(PanelId.MEDIA_MANAGER)
                            } else {
                                panel.panels
                            }
                        )
                    }
            }
            val missingIds = reports
                .filterValues { it.state == MediaRelinkProbe.RelinkState.MISSING }
                .keys
            if (missingIds != previousMissingIds) {
                rebuildPlayerTimeline()
            }
            if (openPanelOnProblems) {
                val toastParts = mediaRelinkOpenToast(
                    missingCount = missingCount,
                    unknownCount = unknownCount,
                    healthBlockingCount = healthBlockingCount,
                    healthWarningCount = healthWarningCount
                )
                if (toastParts.isNotEmpty()) {
                    val renderedParts = toastParts.map { part ->
                        appContext.resources.getQuantityString(part.quantityResId, part.count, part.count)
                    }
                    val joined = renderedParts.drop(1).withIndex().fold(renderedParts.first()) { current, indexedNext ->
                        val next = indexedNext.value
                        text(
                            if (indexedNext.index == renderedParts.lastIndex - 1) {
                                R.string.vm_media_list_and
                            } else {
                                R.string.vm_media_list_comma
                            },
                            current,
                            next,
                        )
                    }
                    showToast(text(R.string.vm_media_check_toast, joined), ToastSeverity.Warning)
                }
            }
        }
    }

    fun jumpToClip(clipId: String) {
        val clip = _state.value.tracks.flatMap { it.clips }.find { it.id == clipId } ?: return
        val trackId = _state.value.tracks.find { it.clips.any { c -> c.id == clipId } }?.id
        seekTo(clip.timelineStartMs)
        selectClip(clipId, trackId)
        hideMediaManager()
    }

    fun jumpToSyncFrame(clipId: String, direction: SyncFrameDirection) {
        val snapshot = _state.value
        val clip = snapshot.tracks.flatMap { it.clips }.find { it.id == clipId } ?: return
        val trackId = snapshot.tracks.find { it.clips.any { candidate -> candidate.id == clipId } }?.id
        val relativePlayheadMs = (_playheadMs.value - clip.timelineStartMs)
            .coerceIn(0L, clip.durationMs.coerceAtLeast(0L))
        val sourceTargetMs = clip.timelineOffsetToSourceMs(relativePlayheadMs)
        viewModelScope.launch(Dispatchers.IO) {
            val sourceSyncMs = mediaDiagnosticsProbe.findNearestSyncFrame(
                uri = clip.sourceUri,
                targetMs = sourceTargetMs,
                direction = direction,
            )
            if (sourceSyncMs == null) {
                withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {
                    showToast(text(R.string.vm_no_sync_frame_toast), ToastSeverity.Warning)
                }
                return@launch
            }
            val timelineOffsetMs = clip.sourceTimeToTimelineOffsetMs(sourceSyncMs)
            if (timelineOffsetMs == null) {
                withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {
                    showToast(text(R.string.vm_no_sync_frame_toast), ToastSeverity.Warning)
                }
                return@launch
            }
            val timelinePositionMs = clip.timelineStartMs + timelineOffsetMs
            withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {
                seekTo(timelinePositionMs)
                selectClip(clipId, trackId)
                hideMediaManager()
            }
        }
    }

    fun removeUnusedMedia() {
        val usedUris = _state.value.tracks.flatMap { it.clips }.map { it.sourceUri.toString() }.toSet()
        // In ClearCut, all media is referenced by clips — there's no separate media pool.
        // "Unused" means tracks with zero clips. Remove empty non-default tracks.
        val defaultTrackCount = 2 // VIDEO + AUDIO
        val currentTracks = _state.value.tracks
        if (currentTracks.size <= defaultTrackCount) {
            showToast(text(R.string.vm_no_unused_tracks_toast))
            return
        }
        saveUndoState("Remove unused tracks")
        val kept = currentTracks.filter { it.clips.isNotEmpty() || it.index < defaultTrackCount }
            .mapIndexed { i, t -> t.copy(index = i) }
        _state.update { recalculateDuration(it.copy(tracks = kept)) }
        val removed = currentTracks.size - kept.size
        showToast(text(R.string.vm_removed_tracks_toast, removed))
        saveProject()
    }

    fun getMissingSources(): List<Uri> {
        val missingClipIds = _state.value.media.relinkReports
            .filter { it.value.state == MediaRelinkProbe.RelinkState.MISSING }
            .keys
        return _state.value.tracks
            .flatMap { it.clips }
            .filter { it.id in missingClipIds }
            .map { it.sourceUri }
            .distinct()
    }

    // --- Audio Normalization (delegated) ---
    fun showAudioNorm() = audioMixerDelegate.showAudioNorm()
    fun hideAudioNorm() = audioMixerDelegate.hideAudioNorm()
    fun normalizeAudio(targetLufs: Float) = audioMixerDelegate.normalizeAudio(targetLufs)
    fun normalizeAllClips(targetLufs: Float) = audioMixerDelegate.normalizeAllClips(targetLufs)

    // --- Color Match ---
    fun colorMatchToReference(referenceClipId: String) {
        val targetClipId = _state.value.selectedClipId ?: return

        viewModelScope.launch {
            val refClip = _state.value.tracks.flatMap { it.clips }.find { it.id == referenceClipId }
            val targetClip = _state.value.tracks.flatMap { it.clips }.find { it.id == targetClipId }
            if (refClip == null || targetClip == null) {
                showToast(text(R.string.vm_clip_no_longer_exists_toast))
                return@launch
            }
            showToast(text(R.string.vm_analyzing_colors_toast))
            // Frame analysis uses a blocking MediaMetadataRetriever — keep it off the
            // main thread so the UI doesn't ANR on long/large source files.
            val (refStats, targetStats) = withContext(Dispatchers.IO) {
                val ref = com.novacut.editor.engine.ColorMatchEngine.analyzeFrame(
                    appContext, refClip.sourceUri, refClip.trimStartMs + refClip.durationMs / 2
                )
                val target = com.novacut.editor.engine.ColorMatchEngine.analyzeFrame(
                    appContext, targetClip.sourceUri, targetClip.trimStartMs + targetClip.durationMs / 2
                )
                ref to target
            }

            if (refStats != null && targetStats != null) {
                // Apply to the clip captured when the action started, not whatever is
                // selected now — selection can change during the async analysis above.
                if (_state.value.tracks.flatMap { it.clips }.none { it.id == targetClipId }) {
                    showToast(text(R.string.vm_clip_no_longer_exists_toast))
                    return@launch
                }
                saveUndoState("Color match")
                val grade = com.novacut.editor.engine.ColorMatchEngine.generateColorMatch(refStats, targetStats)
                updateClipById(targetClipId) { it.copy(colorGrade = grade) }
                updatePreview()
                saveProject()
                showToast(text(R.string.vm_color_matched_toast))
            } else {
                showToast(text(R.string.vm_color_analysis_failed_toast))
            }
        }
    }

    // --- Compound Clips ---
    fun createCompoundClip() {
        val selectedIds = _state.value.selectedClipIds
        if (selectedIds.size < 2) {
            showToast(text(R.string.vm_compound_select_clips_toast))
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
        showToast(text(R.string.vm_compound_created_toast))
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
        showToast(text(R.string.vm_template_applied_toast, template.name))
    }

    // --- Project Archive ---
    fun exportProjectArchive() {
        viewModelScope.launch {
            showToast(text(R.string.vm_exporting_archive_toast))
            try {
                val s = _state.value
                val file = projectTransferCoordinator.exportArchive(
                    document = buildProjectDocument(s),
                    projectName = s.project.name,
                )
                showToast(
                    if (file != null) {
                        text(R.string.vm_backup_saved_toast, file.name)
                    } else {
                        text(R.string.vm_backup_export_failed_toast)
                    }
                )
            } catch (e: Exception) {
                AppLog.e("EditorViewModel", "Project archive export failed", e)
                showToast(text(R.string.editor_archive_export_failed_toast))
            }
        }
    }

    fun exportToOtio() = exportTimeline(TimelineExportCoordinator.Format.OTIO)

    fun exportToFcpxml() = exportTimeline(TimelineExportCoordinator.Format.FCPXML)

    fun exportToEditDecisionJson() = exportTimeline(TimelineExportCoordinator.Format.EDIT_DECISION_JSON)

    private fun exportTimeline(format: TimelineExportCoordinator.Format) {
        viewModelScope.launch {
            val s = _state.value
            try {
                val result = timelineExportCoordinator.export(
                    TimelineExportCoordinator.Request(
                        format = format,
                        tracks = s.tracks,
                        textOverlays = s.textOverlays,
                        projectName = s.project.name,
                        frameRate = s.exportConfig.frameRate,
                        outputDirectory = java.io.File(
                            appContext.getExternalFilesDir(null),
                            "exports",
                        ),
                        timelineMarkers = s.timelineMarkers,
                        timebase = s.project.timelineTimebase,
                    )
                )
                withContext(Dispatchers.Main) { publishTimelineExportResult(result) }
            } catch (e: Exception) {
                AppLog.e("EditorViewModel", "${format.name} export failed", e)
                withContext(Dispatchers.Main) {
                    showToast(
                        text(
                            when (format) {
                                TimelineExportCoordinator.Format.OTIO -> R.string.editor_otio_export_failed_toast
                                TimelineExportCoordinator.Format.FCPXML -> R.string.editor_fcpxml_export_failed_toast
                                TimelineExportCoordinator.Format.EDIT_DECISION_JSON ->
                                    R.string.editor_edit_decision_json_export_failed_toast
                            }
                        )
                    )
                }
            }
        }
    }

    private fun publishTimelineExportResult(result: TimelineExportCoordinator.Result) {
        val outputFile = result.outputFile
        val formatName = result.format.name
        if (result.blocked) {
            val first = result.report.errors.first()
            _state.update {
                it.copyMedia { media ->
                    media.copy(
                        timelineExchangeFeedback = TimelineExchangeFeedback(
                            succeeded = false,
                            title = "$formatName export blocked",
                            body = "ClearCut found a timeline issue that would make the handoff unreliable.",
                            outputFileName = null,
                            report = result.report,
                        )
                    )
                }
            }
            showToast(
                text(
                    when (result.format) {
                        TimelineExportCoordinator.Format.OTIO -> R.string.vm_otio_blocked_toast
                        TimelineExportCoordinator.Format.FCPXML -> R.string.vm_fcpxml_blocked_toast
                        TimelineExportCoordinator.Format.EDIT_DECISION_JSON ->
                            R.string.vm_edit_decision_json_blocked_toast
                    },
                    first.path,
                    first.message,
                )
            )
            return
        }

        val file = requireNotNull(outputFile)
        if (result.report.issues.isNotEmpty()) {
            _state.update {
                it.copyMedia { media ->
                    media.copy(
                        timelineExchangeFeedback = TimelineExchangeFeedback(
                            succeeded = true,
                            title = "$formatName exported with notes",
                            body = "The file was written, but the receiving editor may need manual cleanup.",
                            outputFileName = file.name,
                            report = result.report,
                        )
                    )
                }
            }
        }
        val tail = if (result.report.warnings.isNotEmpty()) " (${result.report.summary})" else ""
        showToast(
            text(
                when (result.format) {
                    TimelineExportCoordinator.Format.OTIO -> R.string.vm_otio_exported_toast
                    TimelineExportCoordinator.Format.FCPXML -> R.string.vm_fcpxml_exported_toast
                    TimelineExportCoordinator.Format.EDIT_DECISION_JSON ->
                        R.string.vm_edit_decision_json_exported_toast
                },
                file.name,
                tail,
            )
        )
    }

    // --- Linked A/V ---
    fun unlinkAudioVideo() {
        val selectedClipId = _state.value.selectedClipId ?: return
        val linkedIds = linkedClipIds(_state.value.tracks, selectedClipId)
        if (_state.value.tracks.any { track ->
                track.isLocked && track.clips.any { it.id in linkedIds }
            }
        ) {
            showToast(text(R.string.vm_track_locked_toast))
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
        showToast(text(R.string.vm_av_unlinked_toast))
    }

    // --- Captions ---
    fun showCaptionEditor() {
        showPanel(PanelId.CAPTION_EDITOR)
        _state.value.captionTranslationTargetLang?.let(::runCaptionTranslation)
    }
    fun hideCaptionEditor() = hidePanel(PanelId.CAPTION_EDITOR)

    fun generateAutoCaption() {
        val clipId = _state.value.selectedClipId ?: return
        viewModelScope.launch {
            showToast(text(R.string.vm_generating_captions_toast))
            runAiTool("auto_captions")
        }
    }

    // --- Chapter Markers ---
    fun showChapterMarkers() = showPanel(PanelId.CHAPTER_MARKERS)
    fun hideChapterMarkers() = hidePanel(PanelId.CHAPTER_MARKERS)

    fun addChapterMarker(marker: ChapterMarker) {
        saveUndoState("Add chapter")
        val totalDuration = _state.value.totalDurationMs
        val clampedMarker = marker.copy(timeMs = quantizeProjectTimeInRange(marker.timeMs, totalDuration))
        _state.update { s ->
            val updated = (s.chapterMarkers + clampedMarker).sortedBy { it.timeMs }
            s.copy(chapterMarkers = updated)
        }
        saveProject()
        showToast(text(R.string.vm_chapter_added_toast, formatTime(clampedMarker.timeMs)))
    }

    fun updateChapterMarker(index: Int, marker: ChapterMarker) {
        saveUndoState("Update chapter")
        val totalDuration = _state.value.totalDurationMs
        val clampedMarker = marker.copy(timeMs = quantizeProjectTimeInRange(marker.timeMs, totalDuration))
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

    // --- Global Transitions ---

    fun addGlobalTransition(type: GlobalTransitionType) {
        saveUndoState("Add global transition")
        val totalDuration = _state.value.totalDurationMs
        val anchorMs = when (type) {
            GlobalTransitionType.FADE_FROM_BLACK, GlobalTransitionType.FADE_FROM_WHITE -> 0L
            GlobalTransitionType.FADE_TO_BLACK, GlobalTransitionType.FADE_TO_WHITE ->
                (totalDuration - 1000L).coerceAtLeast(0L)
        }
        val transition = GlobalTransition(
            type = type,
            durationMs = 1000L,
            timelineAnchorMs = anchorMs
        )
        _state.update { s ->
            s.copy(globalTransitions = s.globalTransitions + transition)
        }
        saveProject()
        showToast(type.displayName)
    }

    fun removeGlobalTransition(id: String) {
        saveUndoState("Remove global transition")
        _state.update { s ->
            s.copy(globalTransitions = s.globalTransitions.filter { it.id != id })
        }
        saveProject()
    }

    fun updateGlobalTransitionDuration(id: String, durationMs: Long) {
        val clamped = durationMs.coerceIn(100L, 5000L)
        _state.update { s ->
            s.copy(globalTransitions = s.globalTransitions.map {
                if (it.id == id) it.copy(durationMs = clamped) else it
            })
        }
        saveProject()
    }

    // --- Snapshot History ---
    fun showSnapshotHistory() = showPanel(PanelId.SNAPSHOT_HISTORY)
    fun hideSnapshotHistory() = hidePanel(PanelId.SNAPSHOT_HISTORY)

    /** Delete a checkpoint while keeping the latest deletion available for restoration. */
    fun deleteSnapshot(snapshotId: String) {
        val deletion = deleteSnapshotFromState(_state.value, snapshotId) ?: return
        _state.value = deletion.state
        _restorableSnapshot.value = deletion.deleted
        saveProject()
        showToast(text(R.string.vm_snapshot_deleted_toast, deletion.deleted.label))
    }

    /** Restore the latest deleted checkpoint, matching the single-item template restore affordance. */
    fun restoreDeletedSnapshot() {
        val snapshot = _restorableSnapshot.value ?: return
        if (_state.value.projectSnapshots.none { it.id == snapshot.id }) {
            _state.update { it.copy(projectSnapshots = it.projectSnapshots + snapshot) }
            saveProject()
            showToast(text(R.string.vm_snapshot_restored_toast, snapshot.label))
        }
        _restorableSnapshot.value = null
    }

    /** Drop the one-shot restore offer without changing the project. */
    fun dismissSnapshotRestore() {
        _restorableSnapshot.value = null
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
    }

    fun groupSelectedClips() {
        val ids = _state.value.selectedClipIds
        if (ids.size < 2) { showToast(text(R.string.vm_select_clips_to_group_toast)); return }
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
        showToast(text(R.string.vm_clips_grouped_toast, ids.size))
    }

    fun ungroupSelectedClips() {
        val ids = _state.value.selectedClipIds
        val hasGroupedSelection = _state.value.tracks
            .flatMap { it.clips }
            .any { it.id in ids && it.groupId != null }
        if (!hasGroupedSelection) {
            showToast(text(R.string.vm_select_grouped_clips_to_ungroup_toast))
            return
        }
        saveUndoState("Ungroup clips")
        _state.update { s ->
            s.copy(tracks = s.tracks.map { track ->
                track.copy(clips = track.clips.map { clip ->
                    if (clip.id in ids) clip.copy(groupId = null) else clip
                })
            })
        }
        saveProject()
        showToast(text(R.string.vm_clips_ungrouped_toast))
    }

    fun deleteMultiSelectedClips() {
        clipEditingDelegate.deleteSelectedClip()
    }

    fun applyEffectToSelectedClips(effect: Effect) {
        val ids = _state.value.selectedClipIds
        if (ids.isEmpty()) { showToast(text(R.string.vm_no_clips_selected_toast)); return }
        saveUndoState("Apply ${effect.type.name} to ${ids.size} clips")
        _state.update { s ->
            s.copy(tracks = s.tracks.map { track ->
                track.copy(clips = track.clips.map { clip ->
                    if (clip.id in ids && clip.effects.none { it.type == effect.type }) {
                        clip.copy(effects = clip.effects + effect.copy(
                            id = java.util.UUID.randomUUID().toString()
                        ))
                    } else clip
                })
            })
        }
        rebuildPlayerTimeline()
        saveProject()
        showToast(text(R.string.vm_multi_effect_applied_toast, effect.type.name, ids.size))
    }

    fun applySpeedToSelectedClips(speed: Float) {
        val ids = _state.value.selectedClipIds
        if (ids.isEmpty()) { showToast(text(R.string.vm_no_clips_selected_toast)); return }
        val clampedSpeed = speed.coerceIn(0.1f, 100f)
        saveUndoState("Set speed ${clampedSpeed}x on ${ids.size} clips")
        _state.update { s ->
            s.copy(tracks = s.tracks.map { track ->
                track.copy(clips = track.clips.map { clip ->
                    if (clip.id in ids) clip.copy(speed = clampedSpeed) else clip
                })
            })
        }
        rebuildPlayerTimeline()
        saveProject()
        showToast(text(R.string.vm_multi_speed_applied_toast, clampedSpeed, ids.size))
    }

    fun applyVolumeToSelectedClips(volume: Float) {
        val ids = _state.value.selectedClipIds
        if (ids.isEmpty()) { showToast(text(R.string.vm_no_clips_selected_toast)); return }
        val clampedVolume = volume.coerceIn(0f, 3f)
        saveUndoState("Set volume on ${ids.size} clips")
        _state.update { s ->
            s.copy(tracks = s.tracks.map { track ->
                track.copy(clips = track.clips.map { clip ->
                    if (clip.id in ids) clip.copy(volume = clampedVolume) else clip
                })
            })
        }
        saveProject()
        showToast(text(R.string.vm_multi_volume_applied_toast, ids.size))
    }

    fun copyEffectsToSelectedClips() {
        val state = _state.value
        val sourceEffects = state.copiedEffects
        if (sourceEffects.isEmpty()) { showToast(text(R.string.effects_none_copied_toast)); return }
        val ids = state.selectedClipIds
        if (ids.isEmpty()) { showToast(text(R.string.vm_no_clips_selected_toast)); return }
        saveUndoState("Paste effects to ${ids.size} clips")
        _state.update { s ->
            s.copy(tracks = s.tracks.map { track ->
                track.copy(clips = track.clips.map { clip ->
                    if (clip.id in ids) {
                        val existingTypes = clip.effects.map { it.type }.toSet()
                        val newEffects = sourceEffects
                            .filter { it.type !in existingTypes }
                            .map { it.copy(id = java.util.UUID.randomUUID().toString()) }
                        clip.copy(effects = clip.effects + newEffects)
                    } else clip
                })
            })
        }
        rebuildPlayerTimeline()
        saveProject()
        showToast(text(R.string.vm_multi_effects_pasted_toast, ids.size))
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
            showToast(text(R.string.vm_no_captions_to_export_toast))
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
                showToast(text(R.string.vm_subtitle_exported_toast, file.name))
            } else {
                showToast(text(R.string.vm_subtitle_export_failed_toast))
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
        _state.update {
            it.copyPanel { panel ->
                panel.copy(
                    panels = if (willShow) {
                        panel.panels.open(PanelId.SCOPES)
                    } else {
                        panel.panels.close(PanelId.SCOPES)
                    }
                )
            }
        }
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
            showToast(text(R.string.vm_need_voice_music_ducking_toast))
            return
        }

        viewModelScope.launch {
            showToast(text(R.string.vm_analyzing_speech_toast))
            try {
                val voiceClip = voiceTracks
                    .flatMap { it.clips }
                    .firstOrNull(::clipHasAudio)

                if (voiceClip == null) {
                    showToast(text(R.string.vm_need_video_audio_ducking_toast))
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
                    showToast(text(R.string.vm_no_speech_detected_toast))
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
                showToast(text(R.string.vm_ducking_applied_toast, speechRegions.size))
            } catch (e: Exception) {
                AppLog.e("EditorViewModel", "Auto-duck failed", e)
                showToast(text(R.string.editor_auto_duck_failed_toast))
            }
        }
    }

    // Voiceover recording
    @Volatile
    private var voiceoverDurationJob: Job? = null

    fun startVoiceover() {
        pauseIfPlaying()
        ttsEngine.stopPreview()
        val file = voiceoverEngine.startRecording()
        if (file == null) {
            showToast(text(R.string.vm_mic_access_failed_toast))
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
            showToast(text(R.string.vm_voiceover_added_toast))
        } else {
            showToast(text(R.string.vm_voiceover_failed_toast))
        }
    }

    fun setClipVolume(clipId: String, volume: Float) {
        val safeVolume = safeEditorFloat(volume, 1f, 0f, 2f)
        updateClipById(clipId) { it.copy(volume = safeVolume) }
        // saveProject() deferred to endVolumeChange() — slider fires this 60 Hz.
    }

    fun beginVolumeChange() {
        saveUndoState("Change volume")
    }

    fun endVolumeChange() {
        rebuildPlayerTimeline()
        saveProject()
    }

    fun beginTransformChange() {
        saveUndoState("Transform clip")
    }

    fun endTransformChange() {
        rebuildPlayerTimeline()
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

    fun toggleClipFlipHorizontal(clipId: String) {
        toggleClipFlip(clipId, horizontal = true)
    }

    fun toggleClipFlipVertical(clipId: String) {
        toggleClipFlip(clipId, horizontal = false)
    }

    private fun toggleClipFlip(clipId: String, horizontal: Boolean) {
        val clip = _state.value.tracks.asSequence()
            .flatMap { it.clips.asSequence() }
            .firstOrNull { it.id == clipId }
            ?: return
        saveUndoState(if (horizontal) "Flip clip horizontally" else "Flip clip vertically")
        updateClipById(clipId) { current ->
            if (horizontal) {
                current.copy(flipHorizontal = !current.flipHorizontal)
            } else {
                current.copy(flipVertical = !current.flipVertical)
            }
        }
        rebuildPlayerTimeline()
        saveProject()
    }

    fun resetClipTransform(clipId: String) {
        saveUndoState("Reset transform")
        updateClipById(clipId) {
            it.copy(
                positionX = 0f,
                positionY = 0f,
                scaleX = 1f,
                scaleY = 1f,
                flipHorizontal = false,
                flipVertical = false,
                rotation = 0f,
            )
        }
        saveProject()
    }

    fun beginOpacityChange() {
        saveUndoState("Adjust opacity")
    }

    fun endOpacityChange() {
        rebuildPlayerTimeline()
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
        _state.update { it.copyExport { export -> export.copy(config = config) } }
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
            beatMarkers = _state.value.beatMarkers.toList(),
            trackedObjects = _state.value.trackedObjects.toList(),
            globalTransitions = _state.value.globalTransitions.toList(),
            storyboardCards = _state.value.storyboardCards.toList(),
            transcript = _state.value.v369.transcript,
            playheadMs = _playheadMs.value,
            selectedClipId = _state.value.selectedClipId,
            selectedTrackId = _state.value.selectedTrackId,
            selectedClipIds = _state.value.selectedClipIds,
            aiUsageLedger = _state.value.aiUsageLedger
        )

        _state.update {
            val restored = recalculateDuration(it.withUndoDocument(action).copy(
                undoStack = undoStack.dropLast(1),
                redoStack = (it.redoStack + currentAction).takeLast(50)
            ))
            // Clamp the restored playhead to the restored timeline duration so
            // undoing a "delete last clip" doesn't leave the playhead dangling
            // past the new timeline end.
            val clampedPlayhead = action.playheadMs
                .coerceIn(0L, restored.totalDurationMs.coerceAtLeast(0L))
            dismissedPanelState(restored).copy(
                currentTool = EditorTool.NONE,
                playheadMs = clampedPlayhead
            )
        }
        _playheadMs.value = _state.value.playheadMs
        rebuildPlayerTimeline()
        saveProject()
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
            beatMarkers = _state.value.beatMarkers.toList(),
            trackedObjects = _state.value.trackedObjects.toList(),
            globalTransitions = _state.value.globalTransitions.toList(),
            storyboardCards = _state.value.storyboardCards.toList(),
            transcript = _state.value.v369.transcript,
            playheadMs = _playheadMs.value,
            selectedClipId = _state.value.selectedClipId,
            selectedTrackId = _state.value.selectedTrackId,
            selectedClipIds = _state.value.selectedClipIds,
            aiUsageLedger = _state.value.aiUsageLedger
        )

        _state.update {
            val restored = recalculateDuration(it.withUndoDocument(action).copy(
                redoStack = redoStack.dropLast(1),
                undoStack = (it.undoStack + currentAction).takeLast(50)
            ))
            val clampedPlayhead = action.playheadMs
                .coerceIn(0L, restored.totalDurationMs.coerceAtLeast(0L))
            dismissedPanelState(restored).copy(
                currentTool = EditorTool.NONE,
                playheadMs = clampedPlayhead
            )
        }
        _playheadMs.value = _state.value.playheadMs
        rebuildPlayerTimeline()
        saveProject()
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
                val outputDirectory = java.io.File(appContext.filesDir, FRAME_CAPTURE_DIR_NAME)
                val storageReady = ExportStoragePreflight {
                    ExportStoragePolicy.check(
                        request = ExportStoragePolicy.request(
                            durationMs = 0L,
                            config = config.copy(captureFrameOnly = true),
                            tracks = _state.value.tracks,
                        ),
                        outputDirectory = outputDirectory,
                        cacheDirectory = appContext.cacheDir,
                    )
                }.run(
                    onBlocked = { storageCheck ->
                        val message = appContext.exportStorageFailureMessage(
                            requireNotNull(storageCheck.failure)
                        )
                        _state.update {
                            it.copyExport { export ->
                                export.copy(state = ExportState.ERROR, errorMessage = message)
                            }
                        }
                        showToast(message)
                    },
                    onReady = {},
                )
                if (!storageReady) return@launch
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
                    it.copyExport { export ->
                        export.copy(
                            lastExportedFilePath = file.absolutePath,
                            state = ExportState.COMPLETE,
                            errorMessage = null
                        )
                    }
                }
                showToast(text(R.string.vm_frame_saved_toast, file.name))
            } catch (e: Exception) {
                AppLog.w("EditorVM", "Frame capture failed", e)
                _state.update {
                    it.copyExport { export ->
                        export.copy(
                            state = ExportState.ERROR,
                            errorMessage = "Frame capture failed. Try another timestamp or source clip."
                        )
                    }
                }
                showToast(text(R.string.vm_frame_capture_failed_toast))
            }
        }
    }

    // Project persistence
    private fun buildProjectDocument(
        state: EditorState = _state.value,
        project: Project = state.project,
    ): ProjectDocument {
        val mediaAssets = projectMediaAssetsFor(state)
        val tracks = attachMediaAssetIdsToTracks(state.tracks, mediaAssets)
        return ProjectDocumentApplicator.capture(
            project = project,
            state = AutoSaveState(
                projectId = project.id,
                tracks = tracks,
                textOverlays = state.textOverlays,
                imageOverlays = state.imageOverlays,
                timelineMarkers = state.timelineMarkers,
                playheadMs = state.playheadMs,
                chapterMarkers = state.chapterMarkers,
                drawingPaths = state.drawingPaths,
                beatMarkers = state.beatMarkers,
                transcript = state.v369.transcript,
                trackedObjects = state.trackedObjects,
                aiUsageLedger = state.aiUsageLedger,
                mediaAssets = mediaAssets,
                storyboardCards = state.storyboardCards,
                globalTransitions = state.globalTransitions,
                exportWatermark = state.exportConfig.watermark
            )
        )
    }

    private fun buildAutoSaveState(
        state: EditorState = _state.value,
        projectId: String = state.project.id
    ): AutoSaveState = buildProjectDocument(
        state = state,
        project = state.project.copy(id = projectId),
    ).state

    private fun dirtyTrackingKey(state: EditorState): AutoSaveState = AutoSaveState(
        projectId = state.project.id,
        timestamp = 0L,
        tracks = state.tracks,
        textOverlays = state.textOverlays,
        imageOverlays = state.imageOverlays,
        timelineMarkers = state.timelineMarkers,
        playheadMs = 0L,
        chapterMarkers = state.chapterMarkers,
        drawingPaths = state.drawingPaths,
        beatMarkers = state.beatMarkers,
        transcript = state.v369.transcript,
        trackedObjects = state.trackedObjects,
        aiUsageLedger = state.aiUsageLedger,
        mediaAssets = state.media.mediaAssets,
        storyboardCards = state.storyboardCards,
        globalTransitions = state.globalTransitions,
        exportWatermark = state.exportConfig.watermark,
    )

    private fun currentProjectFingerprint(state: EditorState = _state.value): String =
        projectStateFingerprint(state.project, buildAutoSaveState(state))

    private fun applySavedStateStatus(status: SavedStateStatus) {
        _state.update { state ->
            if (state.isProjectDirty == status.isDirty) state
            else state.copy(isProjectDirty = status.isDirty)
        }
        if (_state.value.saveIndicator != status.indicator) {
            showSaveIndicator(status.indicator)
        }
    }

    private fun projectMediaAssetsFor(state: EditorState): List<ProjectMediaAsset> {
        val cacheKey = mediaManifestCacheKey(state.tracks, state.imageOverlays)
        val generated = synchronized(projectMediaManifestCacheLock) {
            val cached = projectMediaManifestCache
            if (cached?.key == cacheKey) {
                cached.mediaAssets
            } else {
                buildProjectMediaAssets(appContext, state.tracks, state.imageOverlays).also { mediaAssets ->
                    projectMediaManifestCache = CachedProjectMediaManifest(
                        key = cacheKey,
                        mediaAssets = mediaAssets
                    )
                }
            }
        }
        val stored = state.media.mediaAssets
        if (stored.isEmpty()) return generated

        val merged = generated.map { current ->
            val saved = stored.firstOrNull { candidate ->
                candidate.assetId == current.assetId ||
                    candidate.managedUri == current.managedUri ||
                    candidate.originalUri == current.originalUri
            }
            if (saved == null) current else current.copy(
                notes = normalizeMediaAssetNotes(saved.notes),
                tags = normalizeMediaAssetTags(saved.tags),
            )
        }
        val unused = stored.filterNot { saved ->
            generated.any { current ->
                current.assetId == saved.assetId ||
                    current.managedUri == saved.managedUri ||
                    current.originalUri == saved.originalUri
            }
        }
        return merged + unused
    }

    private fun invalidateProjectMediaManifestCache() {
        synchronized(projectMediaManifestCacheLock) {
            projectMediaManifestCache = null
        }
    }

    private fun analyzeMediaHealthForState(
        state: EditorState = _state.value,
        diagnostics: Collection<com.novacut.editor.engine.MediaDiagnostic> = state.media.diagnostics.values,
    ) = MediaHealth.analyze(buildAutoSaveState(state), diagnostics.toList())

    private fun analyzeMediaHealthForRecovery(recovery: AutoSaveState) =
        MediaHealth.analyze(recovery)

    private fun sanitizedProjectFileStem(name: String): String {
        return sanitizeFileName(name, fallback = "ClearCut")
    }

    fun saveProject() {
        val snapshot = _state.value
        val project = projectForSave(snapshot)
        val document = buildProjectDocument(snapshot, project)
        val fingerprint = projectStateFingerprint(project, document.state)
        val (attempt, status) = savedStateTracker.beginSave(fingerprint)
        applySavedStateStatus(status)

        viewModelScope.launch {
            try {
                val result = documentCoordinator.save(
                    document = document,
                    autoSaveEnabled = recoveryOpenComplete && !autoSaveBlockedByRecovery,
                )
                applySavedProjectMetadata(project)

                // Persist the exact snapshot fingerprinted above. Capturing inside
                // the coroutine allowed a newer edit to be mislabeled as saved.
                if (result.succeeded) {
                    applySavedStateStatus(
                        savedStateTracker.saveSucceeded(attempt, currentProjectFingerprint())
                    )
                } else {
                    applySavedStateStatus(
                        savedStateTracker.saveFailed(attempt, currentProjectFingerprint())
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                AppLog.e("EditorVM", "Project save failed", e)
                applySavedStateStatus(
                    savedStateTracker.saveFailed(attempt, currentProjectFingerprint())
                )
            }
        }
    }

    private fun projectForSave(state: EditorState): Project {
        val firstClipUri = state.tracks
            .filter { it.type == TrackType.VIDEO }
            .flatMap { it.clips }
            .firstOrNull()?.sourceUri?.toString()
        return state.project.copy(
            updatedAt = System.currentTimeMillis(),
            durationMs = state.totalDurationMs,
            thumbnailUri = firstClipUri,
        )
    }

    private fun applySavedProjectMetadata(project: Project) {
        _state.update { current ->
            current.copy(
                project = current.project.copy(
                    updatedAt = project.updatedAt,
                    durationMs = project.durationMs,
                    thumbnailUri = project.thumbnailUri,
                )
            )
        }
    }

    fun renameProject(name: String) {
        val normalizedName = name.trim().ifBlank { "Untitled" }
        _state.update { it.copy(project = it.project.copy(name = normalizedName)) }
        saveProject()
    }

    fun showScratchpad() {
        pauseIfPlaying()
        _state.update {
            dismissedPanelState(it).copyPanel { panel ->
                panel.copy(panels = panel.panels.closeAll().open(PanelId.SCRATCHPAD))
            }
        }
    }

    fun hideScratchpad() {
        _state.update {
            it.copyPanel { panel -> panel.copy(panels = panel.panels.close(PanelId.SCRATCHPAD)) }
        }
    }

    fun showProjectInspector() = showPanel(PanelId.PROJECT_INSPECTOR)
    fun hideProjectInspector() = hidePanel(PanelId.PROJECT_INSPECTOR)

    fun collectProjectInspectorData(): ProjectInspectorData {
        val s = _state.value
        val allClips = s.tracks.flatMap { it.clips }
        val storageInfo = documentCoordinator.getStorageInfo(s.project.id)
        val missingCount = s.media.relinkReports
            .values.count { it.state == com.novacut.editor.engine.MediaRelinkProbe.RelinkState.MISSING }
        return ProjectInspectorData(
            projectName = s.project.name,
            clipCount = allClips.size,
            videoTrackCount = s.tracks.count { it.type == com.novacut.editor.model.TrackType.VIDEO },
            audioTrackCount = s.tracks.count { it.type == com.novacut.editor.model.TrackType.AUDIO },
            overlayTrackCount = s.tracks.count {
                it.type == com.novacut.editor.model.TrackType.OVERLAY ||
                    it.type == com.novacut.editor.model.TrackType.TEXT
            },
            textOverlayCount = s.textOverlays.size,
            totalDurationMs = s.totalDurationMs,
            autoSaveSizeBytes = storageInfo.autoSaveSizeBytes,
            autoSaveLastModifiedMs = storageInfo.autoSaveLastModifiedMs,
            missingMediaCount = missingCount,
            exportResolution = s.exportConfig.resolution.name,
            exportCodec = s.exportConfig.codec.name,
            exportFrameRate = s.exportConfig.frameRate,
            dbSchemaVersion = 9,
            backupFileCount = storageInfo.backupFileCount,
            effectCount = allClips.sumOf { it.effects.size },
            keyframeCount = allClips.sumOf { it.keyframes.size }
        )
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

    fun updateProjectAspect(aspect: AspectRatio) {
        _state.update { it.copy(project = it.project.copy(aspectRatio = aspect)) }
        saveProject()
        showToast(text(R.string.vm_aspect_ratio_toast, aspect.label))
    }

    private fun captureUndoAction(description: String): UndoAction {
        val state = _state.value
        return UndoAction(
            description = description,
            tracks = state.tracks.map { it.copy() },
            textOverlays = state.textOverlays.toList(),
            imageOverlays = state.imageOverlays.toList(),
            timelineMarkers = state.timelineMarkers.toList(),
            chapterMarkers = state.chapterMarkers.toList(),
            drawingPaths = state.drawingPaths.toList(),
            beatMarkers = state.beatMarkers.toList(),
            trackedObjects = state.trackedObjects.toList(),
            globalTransitions = state.globalTransitions.toList(),
            storyboardCards = state.storyboardCards.toList(),
            transcript = state.v369.transcript,
            playheadMs = _playheadMs.value,
            selectedClipId = state.selectedClipId,
            selectedTrackId = state.selectedTrackId,
            selectedClipIds = state.selectedClipIds,
            aiUsageLedger = state.aiUsageLedger,
        )
    }

    private fun pushUndoAction(action: UndoAction) {
        _state.update { state ->
            state.copy(
                undoStack = (state.undoStack + action).takeLast(50),
                redoStack = emptyList(),
            )
        }
    }

    private fun restoreUndoAction(action: UndoAction) {
        _state.update { state ->
            val restored = recalculateDuration(state.withUndoDocument(action))
            restored.copy(
                playheadMs = action.playheadMs.coerceIn(
                    0L,
                    restored.totalDurationMs.coerceAtLeast(0L),
                )
            )
        }
        _playheadMs.value = _state.value.playheadMs
    }

    private fun beginTimelineGestureUndo(description: String): Boolean =
        timelineGestureUndo.begin(description)

    private fun markTimelineGestureMutation(description: String) {
        timelineGestureUndo.captureBeforeMutation(description) {
            captureUndoAction(description)
        }
    }

    private fun finishTimelineGestureUndo(
        description: String,
        commit: Boolean,
    ): GestureFinishResult = timelineGestureUndo.finish(
        description = description,
        commit = commit,
        current = { captureUndoAction(description) },
        onCommit = ::pushUndoAction,
        onCancel = ::restoreUndoAction,
    )

    private fun saveUndoState(description: String) {
        pushUndoAction(captureUndoAction(description))
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
                    val dir = File(appContext.getExternalFilesDir(null) ?: appContext.filesDir, "templates").apply { mkdirs() }
                    val sanitized = sanitizeFileName(template.name, fallback = "template")
                    val outputFile = File(dir, "$sanitized.clearcut-template")
                    val success = templateManager.exportTemplateToFile(template.id, outputFile)
                    if (!success) return@withContext null
                    template to outputFile
                }
                if (exportResult != null) {
                    val (_, outputFile) = exportResult
                    showToast(text(R.string.vm_template_exported_toast, outputFile.name))
                    val uri = runCatching {
                        androidx.core.content.FileProvider.getUriForFile(
                            appContext,
                            "${appContext.packageName}.fileprovider",
                            outputFile
                        )
                    }.getOrElse { error ->
                        AppLog.w("EditorViewModel", "Template export FileProvider handoff failed", error)
                        showToast(appContext.getString(R.string.editor_share_location_failed))
                        return@launch
                    }
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
                    showToast(text(R.string.vm_template_export_failed_toast))
                }
            } catch (e: Exception) {
                AppLog.e("EditorViewModel", "Template export failed", e)
                showToast(text(R.string.project_template_export_failed))
            }
        }
    }

    fun importTemplate(uri: Uri) {
        viewModelScope.launch {
            try {
                val template = templateManager.importTemplateFromUri(uri)
                if (template != null) {
                    showToast(text(R.string.vm_template_imported_toast, template.name))
                } else {
                    showToast(text(R.string.vm_template_import_failed_toast))
                }
            } catch (e: Exception) {
                AppLog.e("EditorViewModel", "Template import failed", e)
                showToast(text(R.string.editor_import_failed_toast))
            }
        }
    }

    fun downloadSegmentationModel() = aiToolsDelegate.downloadSegmentationModel()
    fun deleteSegmentationModel() = aiToolsDelegate.deleteSegmentationModel()
    fun downloadInpaintingModel() = aiToolsDelegate.downloadInpaintingModel()
    fun deleteInpaintingModel() = aiToolsDelegate.deleteInpaintingModel()

    fun runAiTool(toolId: String) = aiToolsDelegate.runAiTool(toolId)
    fun runAiToolAfterRequirement(toolId: String) = aiToolsDelegate.runAiToolAfterRequirement(toolId)
    fun cancelAiTool() = aiToolsDelegate.cancelAiTool()
    fun applyStabilizationPreview() = aiToolsDelegate.applyStabilizationPreview()
    fun dismissStabilizationPreview() = aiToolsDelegate.dismissStabilizationPreview()
    fun dismissAiRequirementPrompt() {
        val current = _state.value.aiRequirementPrompt ?: return
        _state.update {
            if (it.aiRequirementPrompt?.id == current.id) {
                it.copyAi { ai -> ai.copy(requirementPrompt = null) }
            } else {
                it
            }
        }
    }

    /**
     * Show the registry-backed AI requirement sheet. Silently no-ops when no
     * registry entry exists — `aiRequirementPrompt` remains the generic
     * fallback for those unknown IDs.
     */
    fun showAiModelRequirement(toolId: String) = aiToolsDelegate.showAiModelRequirement(toolId)

    fun dismissAiModelRequirement() {
        _state.update { it.copyAi { ai -> ai.copy(modelRequirement = null) } }
    }

    // --- R5.4a Caption translation orchestrator ---
    //
    // The panel reads `state.captionTranslationRows` / `*TargetLang` /
    // `*Quality` and routes user actions back through these methods. The
    // network-bound `engine.translate(...)` call is invoked through
    // `runCaptionTranslation()` which the host fires when the user picks
    // a target language. Until the model dep lands the engine returns the
    // source text unchanged — the editor surface still works because all
    // four state-mutation helpers are pure.

    fun captionTranslationTargets(): List<String> =
        captionTranslationEngine.getSupportedLanguages(_state.value.captionTranslationVariant)

    fun runCaptionTranslation(targetLang: String) {
        setCaptionTranslationTarget(targetLang)
        if (!networkAvailable.value) {
            captionTranslationJob?.cancel()
            _state.update {
                it.copyCaption { caption ->
                    caption.copy(
                        translationRows = emptyList(),
                        translationUnavailable = true,
                        translationOffline = true,
                    )
                }
            }
            return
        }
        // No translation model is installed yet: never present untranslated
        // captions as a translation. Surface an explicit unavailable state.
        if (!captionTranslationEngine.isModelReady()) {
            captionTranslationJob?.cancel()
            _state.update {
                it.copyCaption { caption ->
                    caption.copy(
                        translationRows = emptyList(),
                        translationUnavailable = true,
                        translationOffline = false,
                    )
                }
            }
            return
        }
        val state = _state.value
        val selectedClipId = state.selectedClipId
        val clip = selectedClipId?.let { id ->
            state.tracks.flatMap { it.clips }.firstOrNull { it.id == id }
        }
        val segments = captionsToTranslationSegments(clip?.captions ?: emptyList())
        if (segments.isEmpty()) {
            captionTranslationJob?.cancel()
            _state.update {
                it.copyCaption { caption ->
                    caption.copy(
                        translationRows = emptyList(),
                        translationUnavailable = false,
                        translationOffline = false,
                    )
                }
            }
            return
        }

        val sourceLang = state.captionTranslationSourceLang
        val variant = state.captionTranslationVariant
        captionTranslationJob?.cancel()
        captionTranslationJob = viewModelScope.launch {
            val translated = try {
                captionTranslationEngine.translate(
                    segments = segments,
                    sourceLang = sourceLang,
                    targetLang = targetLang,
                )
            } catch (e: com.novacut.editor.engine.CaptionTranslationEngine.TranslationUnavailableException) {
                _state.update { current ->
                    current.copyCaption { caption ->
                        caption.copy(
                            translationRows = emptyList(),
                            translationUnavailable = true,
                            translationOffline = false,
                        )
                    }
                }
                return@launch
            }
            val rows = captionTranslationEngine.buildEditorRows(
                segments = translated,
                variant = variant,
                sourceLang = sourceLang,
                targetLang = targetLang,
            )
            _state.update { current ->
                if (current.captionTranslationTargetLang != targetLang) {
                    current
                } else {
                    current.copyCaption { caption ->
                        caption.copy(
                            translationRows = rows,
                            translationUnavailable = false,
                            translationOffline = false,
                            quality = rows.firstOrNull()?.quality ?: current.captionTranslationQuality,
                        )
                    }
                }
            }
        }
    }

    fun setCaptionTranslationTarget(targetLang: String) {
        val variant = _state.value.captionTranslationVariant
        val source = _state.value.captionTranslationSourceLang
        val quality = captionTranslationEngine.pairQuality(variant, source, targetLang)
        _state.update {
            it.copyCaption { caption ->
                caption.copy(
                    targetLang = targetLang,
                    quality = quality,
                )
            }
        }
    }

    /**
     * Replace a translated caption row's target text with the user's edit
     * and flip its state to USER_EDITED. Pure pass-through to the engine
     * helper so the panel doesn't have to import engine types.
     */
    fun applyCaptionTranslationEdit(rowIndex: Int, newTargetText: String) {
        val rows = _state.value.captionTranslationRows
        val updatedSegments = captionTranslationEngine.applyUserEdit(
            segments = rows.map { it.segment },
            index = rowIndex,
            newTargetText = newTargetText,
        )
        _state.update { state ->
            state.copyCaption { caption ->
                caption.copy(
                    translationRows = updatedSegments.mapIndexed { i, seg ->
                        com.novacut.editor.engine.CaptionTranslationEngine.EditorRow(
                            index = i,
                            segment = seg,
                            quality = rows.getOrNull(i)?.quality
                                ?: state.captionTranslationQuality
                                ?: com.novacut.editor.engine.CaptionTranslationEngine.LanguagePairQuality.UNKNOWN,
                        )
                    },
                )
            }
        }
    }

    /**
     * Mark a row REGENERATE_PENDING so the panel shows the spinner.
     * Callers that have an actual translation backend should follow this
     * with `completeCaptionTranslationRegenerate(index, newText)` once the
     * background work finishes; today the stub `translate()` returns the
     * source text so callers wire a self-completing loop.
     */
    fun regenerateCaptionTranslation(rowIndex: Int) {
        if (!networkAvailable.value) {
            _state.update {
                it.copyCaption { caption ->
                    caption.copy(translationUnavailable = true, translationOffline = true)
                }
            }
            return
        }
        // No translation backend is installed: do not mark rows pending or call
        // translate() (which fails fast); surface the unavailable state instead.
        if (!captionTranslationEngine.isModelReady()) {
            _state.update {
                it.copyCaption { caption ->
                    caption.copy(translationUnavailable = true, translationOffline = false)
                }
            }
            return
        }
        val rows = _state.value.captionTranslationRows
        val row = rows.getOrNull(rowIndex) ?: return
        val sourceLang = _state.value.captionTranslationSourceLang
        val targetLang = _state.value.captionTranslationTargetLang ?: return
        val updated = captionTranslationEngine.markRegeneratePending(
            segments = rows.map { it.segment },
            index = rowIndex,
        )
        _state.update { state ->
            state.copyCaption { caption ->
                caption.copy(
                    translationRows = updated.mapIndexed { i, seg ->
                        com.novacut.editor.engine.CaptionTranslationEngine.EditorRow(
                            index = i,
                            segment = seg,
                            quality = rows.getOrNull(i)?.quality
                                ?: state.captionTranslationQuality
                                ?: com.novacut.editor.engine.CaptionTranslationEngine.LanguagePairQuality.UNKNOWN,
                        )
                    },
                )
            }
        }
        viewModelScope.launch {
            val translated = captionTranslationEngine.translate(
                segments = listOf(translatedSegmentToTranscriptionSegment(row.segment)),
                sourceLang = sourceLang,
                targetLang = targetLang,
            ).firstOrNull()
            completeCaptionTranslationRegenerate(
                rowIndex = rowIndex,
                newTargetText = translated?.targetText ?: row.segment.sourceText,
            )
        }
    }

    // --- Tier C.13 compound navigation orchestrator ---
    //
    // Live nav stack is held here as a mutable companion. The immutable
    // EditorState carries the depth + breadcrumb-text signals the UI reads
    // (PredictiveBackHandler predicate + CompoundNavBreadcrumb chip).

    private val compoundNavStack = com.novacut.editor.engine.CompoundNavStack()

    fun openCompoundClip(clipId: String): Boolean {
        val state = _state.value
        val clip = state.tracks
            .flatMap { it.clips }
            .firstOrNull { it.id == clipId } ?: return false
        if (!compoundNavStack.canPush(clip)) return false
        compoundNavStack.push(clip)
        publishCompoundNavState(clearSelection = true)
        return true
    }

    fun exitCompoundLevel() {
        if (compoundNavStack.isAtRoot) return
        compoundNavStack.pop()
        publishCompoundNavState()
    }

    private fun publishCompoundNavState(clearSelection: Boolean = false) {
        val text = compoundNavStack.formatBreadcrumb(
            rootLabel = appContext.getString(R.string.compound_breadcrumb_root),
            separator = " " + appContext.getString(R.string.compound_breadcrumb_separator) + " ",
        )
        _state.update {
            it.copy(
                compound = it.compound.copy(
                    depth = compoundNavStack.depth,
                    breadcrumbText = text,
                ),
                selectedClipId = if (clearSelection) null else it.selectedClipId,
                selectedTrackId = if (clearSelection) null else it.selectedTrackId,
                selectedClipIds = if (clearSelection) emptySet() else it.selectedClipIds,
                currentTool = if (clearSelection) EditorTool.NONE else it.currentTool,
            )
        }
    }

    fun completeCaptionTranslationRegenerate(rowIndex: Int, newTargetText: String) {
        val rows = _state.value.captionTranslationRows
        val updated = captionTranslationEngine.completeRegenerate(
            segments = rows.map { it.segment },
            index = rowIndex,
            newTargetText = newTargetText,
        )
        _state.update { state ->
            state.copyCaption { caption ->
                caption.copy(
                    translationRows = updated.mapIndexed { i, seg ->
                        com.novacut.editor.engine.CaptionTranslationEngine.EditorRow(
                            index = i,
                            segment = seg,
                            quality = rows.getOrNull(i)?.quality
                                ?: state.captionTranslationQuality
                                ?: com.novacut.editor.engine.CaptionTranslationEngine.LanguagePairQuality.UNKNOWN,
                        )
                    },
                )
            }
        }
    }

    fun dismissBackupImportFeedback() {
        _state.update { it.copyMedia { media -> media.copy(backupImportFeedback = null) } }
    }

    fun dismissTimelineExchangeFeedback() {
        _state.update { it.copyMedia { media -> media.copy(timelineExchangeFeedback = null) } }
    }

    fun insertFreezeFrame() {
        val clip = getSelectedClip() ?: return
        val playheadMs = _state.value.playheadMs
        if (playheadMs < clip.timelineStartMs || playheadMs >= clip.timelineEndMs) {
            showToast(text(R.string.vm_move_playhead_toast))
            return
        }

        val relativeMs = playheadMs - clip.timelineStartMs
        val sourceTimeMs = clip.timelineOffsetToSourceMs(relativeMs)
        val captureFormat = _state.value.exportConfig.captureFormat

        viewModelScope.launch {
            showToast(text(R.string.vm_extracting_frame_toast))
            val frameFile = withContext(Dispatchers.IO) {
                videoEngine.extractFrameToFile(clip.sourceUri, sourceTimeMs, captureFormat)
            }
            if (frameFile == null) {
                showToast(text(R.string.vm_frame_extract_failed_toast))
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
            showToast(text(R.string.vm_freeze_frame_inserted_toast))
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
            t.clips.maxOfOrNull { clip -> t.effectiveTimelineEndMs(clip) } ?: 0L
        }?.coerceAtLeast(0L) ?: 0L
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
        playbackCoordinator.stop()
        aiToolsDelegate.cancelAiTool()
        documentCoordinator.stopAutoSave()
        backgroundJobCoordinator.removeObservers()
        voiceoverDurationJob?.cancel()
        voiceoverEngine.release()
        ttsEngine.stopPreview()
        // Guarantee scrubbing-mode is reset regardless of whether a begin-X()
        // had a matching end-X(). If the activity dies mid-trim / mid-scrub (OS
        // kill, uncaught exception in the drag handler), a stale scrubbing flag
        // would otherwise persist on the singleton VideoEngine and affect the
        // next project opened in this process.
        playbackCoordinator.setScrubbingMode(false)
        // Only reset export state if no export is actively running — the ExportService
        // observes the same state flows and needs to see the terminal state to stop itself.
        if (videoEngine.exportState.value != ExportState.EXPORTING) {
            videoEngine.resetExportState()
        }
        cancelWaveformLoads()
        proxyGenerationJob?.cancel()
        proxyGenerationJob = null
        audioEngine.clearWaveformCache()
        // DON'T call videoEngine.release() or ttsEngine.release() — they're @Singletons
    }
}

private fun safeEditorFloat(value: Float, fallback: Float, min: Float, max: Float): Float {
    val safeFallback = if (fallback.isFinite()) fallback.coerceIn(min, max) else min
    return if (value.isFinite()) value.coerceIn(min, max) else safeFallback
}

private data class CachedProjectMediaManifest(
    val key: String,
    val mediaAssets: List<ProjectMediaAsset>
)

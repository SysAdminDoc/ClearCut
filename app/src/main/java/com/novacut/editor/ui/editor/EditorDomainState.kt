package com.novacut.editor.ui.editor

import com.novacut.editor.engine.AiToolRequirements
import com.novacut.editor.engine.AiUsageLedger
import com.novacut.editor.engine.CaptionTranslationEngine
import com.novacut.editor.engine.CutAssistantEngine
import com.novacut.editor.engine.MediaRelinkProbe
import com.novacut.editor.engine.SmartRenderEngine
import com.novacut.editor.engine.ExportState
import com.novacut.editor.model.BatchExportItem
import com.novacut.editor.model.ExportConfig

/**
 * Typed projection of the large editor state bag into domain-owned slices.
 *
 * This is the first decomposition layer: each slice is sealed by
 * [EditorDomainState], can be tested independently, and gives later storage
 * migrations a stable target without forcing every `EditorState.copy(...)`
 * caller to move in one risky change.
 */
sealed interface EditorDomainState {
    val kind: Kind

    enum class Kind {
        PANEL,
        CAPTION,
        COMPOUND,
        EXPORT,
        AI,
        MEDIA
    }
}

data class EditorPanelState(
    val panels: PanelVisibility,
    val selectedEffectId: String?,
    val editingTextOverlayId: String?
) : EditorDomainState {
    override val kind: EditorDomainState.Kind = EditorDomainState.Kind.PANEL
}

data class EditorCaptionState(
    val translationRows: List<CaptionTranslationEngine.EditorRow>,
    val sourceLang: String,
    val targetLang: String?,
    val quality: CaptionTranslationEngine.LanguagePairQuality?,
    val variant: CaptionTranslationEngine.ModelVariant
) : EditorDomainState {
    override val kind: EditorDomainState.Kind = EditorDomainState.Kind.CAPTION
}

data class EditorCompoundState(
    val depth: Int = 0,
    val breadcrumbText: String = ""
) : EditorDomainState {
    override val kind: EditorDomainState.Kind = EditorDomainState.Kind.COMPOUND
}

data class EditorExportDomainState(
    val config: ExportConfig = ExportConfig(),
    val progress: Float = 0f,
    val state: ExportState = ExportState.IDLE,
    val lastExportedFilePath: String? = null,
    val errorMessage: String? = null,
    val startTime: Long = 0L,
    val renderSegments: List<SmartRenderEngine.RenderSegment> = emptyList(),
    val renderSummary: SmartRenderEngine.SmartRenderSummary? = null,
    val batchQueue: List<BatchExportItem> = emptyList(),
    val savedConfig: ExportConfig? = null
) : EditorDomainState {
    override val kind: EditorDomainState.Kind = EditorDomainState.Kind.EXPORT
}

data class EditorAiState(
    val requirementPrompt: AiRequirementPrompt? = null,
    val modelRequirement: AiToolRequirements.ToolRequirement? = null,
    val processingTool: String? = null,
    val suggestion: AiSuggestion? = null,
    val usageLedger: List<AiUsageLedger.Entry> = emptyList(),
    val cutAssistantReview: CutAssistantEngine.ReviewSet? = null,
    val isReframing: Boolean = false,
    val isAutoEditing: Boolean = false,
    val isSynthesizingTts: Boolean = false,
    val isTtsAvailable: Boolean = false,
    val isAnalyzingNoise: Boolean = false,
    val noiseAnalysisResult: String? = null
) : EditorDomainState {
    override val kind: EditorDomainState.Kind = EditorDomainState.Kind.AI
}

data class EditorMediaState(
    val backupImportFeedback: BackupImportFeedback? = null,
    val timelineExchangeFeedback: TimelineExchangeFeedback? = null,
    val relinkReports: Map<String, MediaRelinkProbe.ClipRelinkReport> = emptyMap()
) : EditorDomainState {
    override val kind: EditorDomainState.Kind = EditorDomainState.Kind.MEDIA
}

data class EditorDomainStates(
    val panel: EditorPanelState,
    val caption: EditorCaptionState,
    val compound: EditorCompoundState,
    val export: EditorExportDomainState,
    val ai: EditorAiState,
    val media: EditorMediaState
) {
    fun asList(): List<EditorDomainState> = listOf(
        panel,
        caption,
        compound,
        export,
        ai,
        media
    )
}

val EditorState.domainStates: EditorDomainStates
    get() = EditorDomainStates(
        panel = EditorPanelState(
            panels = panels,
            selectedEffectId = selectedEffectId,
            editingTextOverlayId = editingTextOverlayId
        ),
        caption = EditorCaptionState(
            translationRows = captionTranslationRows,
            sourceLang = captionTranslationSourceLang,
            targetLang = captionTranslationTargetLang,
            quality = captionTranslationQuality,
            variant = captionTranslationVariant
        ),
        compound = compound,
        export = export,
        ai = ai,
        media = media
    )

inline fun EditorState.copyAi(transform: (EditorAiState) -> EditorAiState): EditorState =
    copy(ai = transform(ai))

inline fun EditorState.copyExport(transform: (EditorExportDomainState) -> EditorExportDomainState): EditorState =
    copy(export = transform(export))

inline fun EditorState.copyMedia(transform: (EditorMediaState) -> EditorMediaState): EditorState =
    copy(media = transform(media))

inline fun EditorState.copyCompound(transform: (EditorCompoundState) -> EditorCompoundState): EditorState =
    copy(compound = transform(compound))

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
    val depth: Int,
    val breadcrumbText: String
) : EditorDomainState {
    override val kind: EditorDomainState.Kind = EditorDomainState.Kind.COMPOUND
}

data class EditorExportDomainState(
    val config: ExportConfig,
    val progress: Float,
    val state: ExportState,
    val lastExportedFilePath: String?,
    val errorMessage: String?,
    val startTime: Long,
    val renderSegments: List<SmartRenderEngine.RenderSegment>,
    val renderSummary: SmartRenderEngine.SmartRenderSummary?,
    val batchQueue: List<BatchExportItem>,
    val savedConfig: ExportConfig?
) : EditorDomainState {
    override val kind: EditorDomainState.Kind = EditorDomainState.Kind.EXPORT
}

data class EditorAiState(
    val requirementPrompt: AiRequirementPrompt?,
    val modelRequirement: AiToolRequirements.ToolRequirement?,
    val processingTool: String?,
    val suggestion: AiSuggestion?,
    val usageLedger: List<AiUsageLedger.Entry>,
    val cutAssistantReview: CutAssistantEngine.ReviewSet?,
    val isReframing: Boolean,
    val isAutoEditing: Boolean,
    val isSynthesizingTts: Boolean,
    val isTtsAvailable: Boolean,
    val isAnalyzingNoise: Boolean,
    val noiseAnalysisResult: String?
) : EditorDomainState {
    override val kind: EditorDomainState.Kind = EditorDomainState.Kind.AI
}

data class EditorMediaState(
    val backupImportFeedback: BackupImportFeedback?,
    val timelineExchangeFeedback: TimelineExchangeFeedback?,
    val relinkReports: Map<String, MediaRelinkProbe.ClipRelinkReport>
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
        compound = EditorCompoundState(
            depth = compoundNavDepth,
            breadcrumbText = compoundBreadcrumbText
        ),
        export = EditorExportDomainState(
            config = exportConfig,
            progress = exportProgress,
            state = exportState,
            lastExportedFilePath = lastExportedFilePath,
            errorMessage = exportErrorMessage,
            startTime = exportStartTime,
            renderSegments = renderSegments,
            renderSummary = renderSummary,
            batchQueue = batchExportQueue,
            savedConfig = savedExportConfig
        ),
        ai = EditorAiState(
            requirementPrompt = aiRequirementPrompt,
            modelRequirement = aiModelRequirement,
            processingTool = aiProcessingTool,
            suggestion = aiSuggestion,
            usageLedger = aiUsageLedger,
            cutAssistantReview = cutAssistantReview,
            isReframing = isReframing,
            isAutoEditing = isAutoEditing,
            isSynthesizingTts = isSynthesizingTts,
            isTtsAvailable = isTtsAvailable,
            isAnalyzingNoise = isAnalyzingNoise,
            noiseAnalysisResult = noiseAnalysisResult
        ),
        media = EditorMediaState(
            backupImportFeedback = backupImportFeedback,
            timelineExchangeFeedback = timelineExchangeFeedback,
            relinkReports = mediaRelinkReports
        )
    )

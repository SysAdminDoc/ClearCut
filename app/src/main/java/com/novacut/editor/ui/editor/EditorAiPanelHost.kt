package com.novacut.editor.ui.editor

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.novacut.editor.engine.segmentation.SegmentationModelState
import com.novacut.editor.engine.whisper.WhisperModelState

@Composable
fun BoxScope.EditorAiPanelHost(
    state: EditorState,
    viewModel: EditorViewModel,
    whisperModelState: WhisperModelState,
    whisperDownloadProgress: Float,
    segmentationModelState: SegmentationModelState,
    segmentationDownloadProgress: Float
) {
    BottomSheetSlot(
        visible = state.panels.isOpen(PanelId.AI_TOOLS),
        modifier = Modifier.align(Alignment.BottomCenter)
    ) {
        AiToolsPanel(
            hasSelectedClip = state.selectedClipId != null,
            onToolSelected = { toolId ->
                if (toolId == "cut_assistant") {
                    viewModel.proposeCutsForReview()
                } else {
                    viewModel.runAiTool(toolId)
                }
            },
            onDisabledToolTapped = { toolName -> viewModel.showToast("Select a clip to use $toolName") },
            onCancelProcessing = viewModel::cancelAiTool,
            onClose = viewModel::hideAiToolsPanel,
            processingTool = state.aiProcessingTool,
            whisperModelState = whisperModelState,
            whisperDownloadProgress = whisperDownloadProgress,
            onDownloadWhisper = viewModel::downloadWhisperModel,
            onDeleteWhisper = viewModel::deleteWhisperModel,
            segmentationModelState = segmentationModelState,
            segmentationDownloadProgress = segmentationDownloadProgress,
            onDownloadSegmentation = viewModel::downloadSegmentationModel,
            onDeleteSegmentation = viewModel::deleteSegmentationModel
        )
    }

    BottomSheetSlot(
        visible = state.cutAssistantReview != null,
        modifier = Modifier.align(Alignment.BottomCenter)
    ) {
        state.cutAssistantReview?.let { review ->
            CutAssistantReviewPanel(
                review = review,
                tracks = state.tracks,
                onToggleProposal = viewModel::toggleCutProposal,
                onAcceptAll = viewModel::acceptAllCutProposals,
                onRejectAll = viewModel::rejectAllCutProposals,
                onApply = viewModel::applyAcceptedCuts,
                onClose = viewModel::dismissCutAssistantReview
            )
        }
    }
}

package com.novacut.editor.ui.editor

import com.novacut.editor.ui.theme.ClearCutAccents
import com.novacut.editor.ui.theme.LocalClearCutColors
import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.novacut.editor.R
import com.novacut.editor.model.Caption
import com.novacut.editor.model.Clip
import com.novacut.editor.model.ClipLabel
import com.novacut.editor.model.KeyframeProperty
import com.novacut.editor.ui.theme.ClearCutChromeIconButton
import com.novacut.editor.ui.theme.Radius
import com.novacut.editor.ui.theme.Spacing
import com.novacut.editor.ui.theme.TouchTarget
import kotlinx.coroutines.delay

@Composable
fun BoxScope.EditorOverlayHost(
    state: EditorState,
    viewModel: EditorViewModel,
    selectedClip: Clip?,
    allCaptions: List<Caption>,
    playheadMs: Long,
    scopeFrame: Bitmap?,
    showClipLabelPicker: Boolean,
    onClipLabelPickerDismiss: () -> Unit,
    useEmbeddedExportPane: Boolean,
    embeddedExportPaneWidth: Dp,
    autoSaveTopPadding: Dp,
    isTutorialOpen: Boolean
) {
    val semanticColors = LocalClearCutColors.current
    if (state.isDrawingMode || state.drawingPaths.isNotEmpty()) {
        DrawingCanvas(
            paths = state.drawingPaths,
            isDrawingMode = state.isDrawingMode,
            drawingColor = state.drawingColor,
            drawingStrokeWidth = state.drawingStrokeWidth,
            onPathAdded = viewModel::addDrawingPath,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }

    ExportProgressOverlay(
        exportState = state.exportState,
        exportProgress = state.exportProgress,
        exportStartTime = state.exportStartTime,
        onCancel = viewModel::cancelExport,
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(
                top = 56.dp,
                end = if (useEmbeddedExportPane) embeddedExportPaneWidth + 8.dp else 8.dp
            )
    )

    AnimatedVisibility(
        visible = isTutorialOpen,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        FirstRunTutorial(
            onComplete = viewModel::hideTutorial,
            modifier = Modifier.zIndex(10f)
        )
    }

    AutoSaveIndicator(
        state = state.saveIndicator,
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(top = autoSaveTopPadding, end = 8.dp)
    )

    if (allCaptions.isNotEmpty()) {
        CaptionPreviewOverlay(
            captions = allCaptions,
            currentTimeMs = playheadMs,
            modifier = Modifier.align(Alignment.Center)
        )
    }

    if (state.panels.isOpen(PanelId.KEYFRAME_EDITOR) && state.selectedClipId != null) {
        val clip = selectedClip
        if (clip != null && clip.keyframes.any { it.property == KeyframeProperty.POSITION_X || it.property == KeyframeProperty.POSITION_Y }) {
            MotionPathOverlay(
                keyframes = clip.keyframes,
                clipDurationMs = clip.durationMs,
                currentTimeMs = (playheadMs - clip.timelineStartMs).coerceAtLeast(0L),
                previewWidth = 400f,
                previewHeight = 225f,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }

    AnimatedVisibility(
        visible = state.panels.isOpen(PanelId.SCOPES),
        enter = fadeIn() + slideInHorizontally(initialOffsetX = { it }),
        exit = fadeOut() + slideOutHorizontally(targetOffsetX = { it }),
        modifier = Modifier.align(Alignment.TopEnd)
    ) {
        VideoScopesOverlay(
            frameBitmap = scopeFrame,
            activeScope = state.activeScopeType,
            onScopeChanged = viewModel::setScopeType,
            onClose = viewModel::toggleScopes,
            modifier = Modifier.padding(8.dp)
        )
    }

    AnimatedVisibility(
        visible = showClipLabelPicker && state.selectedClipId != null,
        enter = fadeIn() + slideInVertically { it },
        exit = fadeOut() + slideOutVertically { it },
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .zIndex(20f)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = semanticColors.panel),
            border = BorderStroke(1.dp, semanticColors.cardStrokeStrong.copy(alpha = 0.86f)),
            shape = RoundedCornerShape(topStart = Radius.xxl, topEnd = Radius.xxl),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
        ) {
            Column(
                modifier = Modifier
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                semanticColors.panelHighest.copy(alpha = 0.86f),
                                semanticColors.panel
                            )
                        )
                    )
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.panel_editor_clip_label),
                            color = semanticColors.text,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            stringResource(R.string.clip_label_picker_description),
                            color = semanticColors.subtext,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    ClearCutChromeIconButton(
                        icon = Icons.Default.Close,
                        contentDescription = stringResource(R.string.cd_close_color_grading),
                        onClick = onClipLabelPickerDismiss,
                        size = 44.dp,
                        iconSize = 20.dp
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ClipLabel.entries.forEach { label ->
                        val isSelected = selectedClip?.clipLabel == label
                        val labelName = if (label == ClipLabel.NONE) {
                            stringResource(R.string.clip_label_none)
                        } else {
                            label.displayName
                        }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(TouchTarget.minimum)
                                .clip(CircleShape)
                                .background(
                                    if (label == ClipLabel.NONE) semanticColors.surfaceHigh else Color(label.argb)
                                )
                                .then(
                                    if (isSelected) {
                                        Modifier.border(2.dp, semanticColors.text, CircleShape)
                                    } else {
                                        Modifier.border(1.dp, semanticColors.cardStroke.copy(alpha = 0.7f), CircleShape)
                                    }
                                )
                                .semantics { contentDescription = labelName }
                                .clickable(role = Role.Button) {
                                    state.selectedClipId?.let { viewModel.setClipLabel(it, label) }
                                }
                        ) {
                            if (label == ClipLabel.NONE) {
                                Icon(
                                    Icons.Default.Close,
                                    labelName,
                                    tint = semanticColors.subtext,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            if (isSelected && label != ClipLabel.NONE) {
                                Icon(
                                    Icons.Default.Check,
                                    labelName,
                                    tint = semanticColors.onAccent,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    PremiumSnackbarHost(
        message = state.toastMessage,
        severity = state.toastSeverity,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 120.dp, start = 16.dp, end = 16.dp)
            .zIndex(10f)
    )

    val bulkPrompt = state.bulkUndoPrompt
    if (bulkPrompt != null) {
        LaunchedEffect(bulkPrompt.id) {
            delay(8000)
            viewModel.dismissBulkUndoPrompt()
        }
        Snackbar(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 120.dp, start = 16.dp, end = 16.dp)
                .zIndex(11f),
            containerColor = semanticColors.panelHighest,
            contentColor = semanticColors.text,
            actionContentColor = ClearCutAccents.Peach,
            action = {
                TextButton(onClick = {
                    viewModel.undo()
                    viewModel.dismissBulkUndoPrompt()
                }) {
                    Text(text = stringResource(R.string.bulk_undo_action))
                }
            },
            dismissAction = {
                ClearCutChromeIconButton(
                    icon = Icons.Default.Close,
                    contentDescription = stringResource(R.string.bulk_undo_dismiss_cd),
                    onClick = { viewModel.dismissBulkUndoPrompt() },
                    size = 40.dp
                )
            },
            shape = RoundedCornerShape(Radius.xl)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Restore,
                    contentDescription = null,
                    tint = ClearCutAccents.Peach,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stringResource(R.string.bulk_undo_message, bulkPrompt.count),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

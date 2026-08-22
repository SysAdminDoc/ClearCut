package com.novacut.editor.ui.export

import com.novacut.editor.ui.theme.ClearCutAccents
import com.novacut.editor.ui.theme.LocalClearCutColors
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.novacut.editor.R
import com.novacut.editor.ui.ClearCutTestTags
import com.novacut.editor.model.BatchExportItem
import com.novacut.editor.model.BatchExportSourceRange
import com.novacut.editor.model.BatchExportStatus
import com.novacut.editor.model.ExportConfig
import com.novacut.editor.model.PlatformPreset
import com.novacut.editor.ui.editor.PremiumEditorPanel
import com.novacut.editor.ui.editor.PremiumPanelCard
import com.novacut.editor.ui.editor.PremiumPanelIconButton
import com.novacut.editor.ui.editor.PremiumPanelPill

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BatchExportPanel(
    queue: List<BatchExportItem>,
    defaultConfig: ExportConfig,
    sourceCuts: List<BatchExportSourceRange>,
    onAddItem: (ExportConfig, String) -> Unit,
    onAddSourceCut: (ExportConfig, BatchExportSourceRange) -> Unit,
    onRemoveItem: (String) -> Unit,
    onMoveItem: (String, Int) -> Unit,
    onRetryItem: (String) -> Unit,
    onPauseBatch: () -> Unit,
    onCancelBatch: () -> Unit,
    onStartBatch: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semanticColors = LocalClearCutColors.current
    var showPresetPicker by remember { mutableStateOf(queue.isEmpty()) }
    val audioOnlyLabel = stringResource(R.string.batch_export_audio_only)
    val audioStemsLabel = stringResource(R.string.batch_export_audio_stems)
    val queuedCount = queue.count { it.status == BatchExportStatus.QUEUED }
    val inProgressCount = queue.count { it.status == BatchExportStatus.IN_PROGRESS }
    val completedCount = queue.count { it.status == BatchExportStatus.COMPLETED }
    val failedCount = queue.count { it.status == BatchExportStatus.FAILED }
    val cancelledCount = queue.count { it.status == BatchExportStatus.CANCELLED }
    val pausedCount = queue.count { it.status == BatchExportStatus.PAUSED }
    val interruptedCount = queue.count { it.status == BatchExportStatus.INTERRUPTED }
    val reviewCount = queue.count { it.status == BatchExportStatus.REVIEW_REQUIRED }
    val reviewOrInterruptedCount = reviewCount + interruptedCount
    val activeLabel = when {
        inProgressCount > 0 -> pluralStringResource(R.plurals.batch_export_active, inProgressCount, inProgressCount)
        reviewOrInterruptedCount > 0 -> pluralStringResource(
            R.plurals.batch_export_needs_review,
            reviewOrInterruptedCount,
            reviewOrInterruptedCount
        )
        failedCount > 0 -> pluralStringResource(R.plurals.batch_export_needs_attention, failedCount, failedCount)
        pausedCount > 0 -> stringResource(R.string.batch_export_status_paused)
        completedCount > 0 -> pluralStringResource(R.plurals.batch_export_done, completedCount, completedCount)
        else -> stringResource(R.string.batch_export_status_ready)
    }

    PremiumEditorPanel(
        title = stringResource(R.string.batch_export_title),
        subtitle = stringResource(R.string.batch_export_subtitle),
        icon = Icons.Default.FileUpload,
        accent = ClearCutAccents.Mauve,
        onClose = onClose,
        modifier = modifier,
        scrollable = true,
        closeButtonTestTag = ClearCutTestTags.BATCH_EXPORT_CLOSE,
        headerActions = {
            PremiumPanelIconButton(
                icon = if (showPresetPicker) Icons.Default.Close else Icons.Default.Add,
                contentDescription = if (showPresetPicker) {
                    stringResource(R.string.batch_export_close_cd)
                } else {
                    stringResource(R.string.batch_export_add_cd)
                },
                onClick = { showPresetPicker = !showPresetPicker },
                tint = if (showPresetPicker) ClearCutAccents.Peach else ClearCutAccents.Green
            )
        }
    ) {
        PremiumPanelCard(accent = ClearCutAccents.Mauve) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.batch_export_queue_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = semanticColors.text
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.batch_export_queue_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = semanticColors.subtext
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PremiumPanelPill(
                        text = pluralStringResource(R.plurals.batch_export_total, queue.size, queue.size),
                        accent = ClearCutAccents.Blue
                    )
                    PremiumPanelPill(
                        text = activeLabel,
                        accent = if (failedCount > 0) ClearCutAccents.Red else ClearCutAccents.Mauve
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PremiumPanelPill(
                    text = pluralStringResource(R.plurals.batch_export_queued, queuedCount, queuedCount),
                    accent = ClearCutAccents.Blue
                )
                if (inProgressCount > 0) {
                    PremiumPanelPill(
                        text = pluralStringResource(R.plurals.batch_export_exporting, inProgressCount, inProgressCount),
                        accent = ClearCutAccents.Mauve
                    )
                }
                if (completedCount > 0) {
                    PremiumPanelPill(
                        text = pluralStringResource(R.plurals.batch_export_done, completedCount, completedCount),
                        accent = ClearCutAccents.Green
                    )
                }
                if (failedCount > 0) {
                    PremiumPanelPill(
                        text = pluralStringResource(R.plurals.batch_export_failed, failedCount, failedCount),
                        accent = ClearCutAccents.Red
                    )
                }
                if (cancelledCount > 0) {
                    PremiumPanelPill(
                        text = pluralStringResource(R.plurals.batch_export_cancelled, cancelledCount, cancelledCount),
                        accent = ClearCutAccents.Yellow
                    )
                }
                if (pausedCount > 0) {
                    PremiumPanelPill(
                        text = stringResource(R.string.batch_export_status_paused),
                        accent = ClearCutAccents.Yellow
                    )
                }
                if (interruptedCount > 0) {
                    PremiumPanelPill(
                        text = pluralStringResource(R.plurals.batch_export_interrupted, interruptedCount, interruptedCount),
                        accent = ClearCutAccents.Yellow
                    )
                }
                if (reviewCount > 0) {
                    PremiumPanelPill(
                        text = pluralStringResource(R.plurals.batch_export_review, reviewCount, reviewCount),
                        accent = ClearCutAccents.Red
                    )
                }
            }
            if (inProgressCount > 0) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PremiumPanelIconButton(
                        icon = Icons.Default.Pause,
                        contentDescription = stringResource(R.string.batch_export_pause_cd),
                        onClick = onPauseBatch,
                        tint = ClearCutAccents.Yellow,
                    )
                    PremiumPanelIconButton(
                        icon = Icons.Default.Stop,
                        contentDescription = stringResource(R.string.batch_export_cancel_cd),
                        onClick = onCancelBatch,
                        tint = ClearCutAccents.Red,
                    )
                }
            }
        }

        if (showPresetPicker) {
            Spacer(modifier = Modifier.height(12.dp))

            if (sourceCuts.isNotEmpty()) {
                PremiumPanelCard(accent = ClearCutAccents.Mauve) {
                    Text(
                        text = stringResource(R.string.batch_export_source_cuts_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = semanticColors.text
                    )
                    Text(
                        text = stringResource(R.string.batch_export_source_cuts_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = semanticColors.subtext
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        sourceCuts.forEach { source ->
                            UtilityExportChip(
                                label = stringResource(
                                    R.string.batch_export_source_cut_label,
                                    source.displayName,
                                    formatBatchCutTime(source.startMs),
                                    formatBatchCutTime(source.endMs),
                                ),
                                accent = ClearCutAccents.Mauve,
                                onClick = { onAddSourceCut(defaultConfig, source) },
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            PremiumPanelCard(accent = ClearCutAccents.Blue) {
                Text(
                    text = stringResource(R.string.batch_export_add_platform_preset),
                    style = MaterialTheme.typography.titleMedium,
                    color = semanticColors.text
                )
                Text(
                    text = stringResource(R.string.batch_export_add_targets_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = semanticColors.subtext
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PlatformPreset.entries.forEach { preset ->
                        FilterChip(
                            selected = false,
                            onClick = {
                                val config = ExportConfig().withPlatformPreset(preset)
                                onAddItem(config, preset.displayName)
                                showPresetPicker = false
                            },
                            label = {
                                Text(
                                    text = preset.displayName,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = semanticColors.panelRaised,
                                labelColor = semanticColors.subtext,
                                selectedContainerColor = ClearCutAccents.Blue.copy(alpha = 0.16f),
                                selectedLabelColor = ClearCutAccents.Blue
                            )
                        )
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    UtilityExportChip(
                        label = audioOnlyLabel,
                        accent = ClearCutAccents.Peach,
                        onClick = {
                            onAddItem(
                                ExportConfig(exportAudioOnly = true),
                                audioOnlyLabel
                            )
                            showPresetPicker = false
                        }
                    )
                    UtilityExportChip(
                        label = audioStemsLabel,
                        accent = ClearCutAccents.Yellow,
                        onClick = {
                            onAddItem(
                                ExportConfig(exportStemsOnly = true),
                                audioStemsLabel
                            )
                            showPresetPicker = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        PremiumPanelCard(accent = ClearCutAccents.Green) {
            Text(
                text = stringResource(R.string.batch_export_queued_title),
                style = MaterialTheme.typography.titleMedium,
                color = semanticColors.text
            )
            Text(
                text = if (queue.isEmpty()) {
                    stringResource(R.string.batch_export_empty_queue)
                } else {
                    stringResource(R.string.batch_export_queued_description)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = semanticColors.subtext
            )

            if (queue.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = semanticColors.panelRaised,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, semanticColors.cardStroke)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.batch_export_empty_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = semanticColors.text
                        )
                        Text(
                            text = stringResource(R.string.batch_export_empty_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = semanticColors.subtext
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    queue.forEachIndexed { index, item ->
                        BatchExportItemRow(
                            item = item,
                            onRemove = { onRemoveItem(item.id) },
                            onRetry = { onRetryItem(item.id) },
                            canMoveUp = index > 0 &&
                                item.status != BatchExportStatus.IN_PROGRESS &&
                                item.status != BatchExportStatus.COMPLETED &&
                                queue[index - 1].status != BatchExportStatus.IN_PROGRESS &&
                                queue[index - 1].status != BatchExportStatus.COMPLETED,
                            canMoveDown = index < queue.lastIndex &&
                                item.status != BatchExportStatus.IN_PROGRESS &&
                                item.status != BatchExportStatus.COMPLETED &&
                                queue[index + 1].status != BatchExportStatus.IN_PROGRESS &&
                                queue[index + 1].status != BatchExportStatus.COMPLETED,
                            onMoveUp = { onMoveItem(item.id, index - 1) },
                            onMoveDown = { onMoveItem(item.id, index + 1) },
                        )
                    }
                }
            }
        }

        val runnableCount = queuedCount + pausedCount + interruptedCount
        if (runnableCount > 0) {
            Spacer(modifier = Modifier.height(12.dp))

            PremiumPanelCard(accent = ClearCutAccents.Green) {
                Text(
                    text = stringResource(R.string.batch_export_run_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = semanticColors.text
                )
                Text(
                    text = stringResource(R.string.batch_export_run_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = semanticColors.subtext
                )

                Button(
                    onClick = onStartBatch,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = ClearCutAccents.Mauve),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Default.RocketLaunch,
                        contentDescription = stringResource(R.string.cd_batch_export)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.batch_export_export_all, runnableCount))
                }
            }
        }
    }
}

@Composable
private fun UtilityExportChip(
    label: String,
    accent: Color,
    onClick: () -> Unit
) {
    FilterChip(
        selected = false,
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = accent.copy(alpha = 0.12f),
            labelColor = accent,
            selectedContainerColor = accent.copy(alpha = 0.18f),
            selectedLabelColor = accent
        )
    )
}

@Composable
private fun BatchExportItemRow(
    item: BatchExportItem,
    onRemove: () -> Unit,
    onRetry: () -> Unit,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    val semanticColors = LocalClearCutColors.current
    val accent = when (item.status) {
        BatchExportStatus.QUEUED -> ClearCutAccents.Blue
        BatchExportStatus.IN_PROGRESS -> ClearCutAccents.Mauve
        BatchExportStatus.COMPLETED -> ClearCutAccents.Green
        BatchExportStatus.FAILED -> ClearCutAccents.Red
        BatchExportStatus.CANCELLED -> ClearCutAccents.Yellow
        BatchExportStatus.PAUSED -> ClearCutAccents.Yellow
        BatchExportStatus.INTERRUPTED -> ClearCutAccents.Yellow
        BatchExportStatus.REVIEW_REQUIRED -> ClearCutAccents.Red
    }
    val statusLabel = when (item.status) {
        BatchExportStatus.QUEUED -> stringResource(R.string.batch_export_status_queued)
        BatchExportStatus.IN_PROGRESS -> "${(item.progress * 100).toInt().coerceIn(0, 100)}%"
        BatchExportStatus.COMPLETED -> stringResource(R.string.batch_export_done_cd)
        BatchExportStatus.FAILED -> stringResource(R.string.batch_export_failed_cd)
        BatchExportStatus.CANCELLED -> stringResource(R.string.batch_export_cancelled_cd)
        BatchExportStatus.PAUSED -> stringResource(R.string.batch_export_paused_cd)
        BatchExportStatus.INTERRUPTED -> stringResource(R.string.batch_export_interrupted_cd)
        BatchExportStatus.REVIEW_REQUIRED -> stringResource(R.string.batch_export_review_required_cd)
    }
    val removable = item.status != BatchExportStatus.IN_PROGRESS
    val retryable = item.status in setOf(
        BatchExportStatus.FAILED,
        BatchExportStatus.CANCELLED,
        BatchExportStatus.PAUSED,
        BatchExportStatus.INTERRUPTED,
        BatchExportStatus.REVIEW_REQUIRED,
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (item.status == BatchExportStatus.IN_PROGRESS) accent.copy(alpha = 0.12f) else semanticColors.panelRaised,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            1.dp,
            if (item.status == BatchExportStatus.IN_PROGRESS) accent.copy(alpha = 0.22f) else semanticColors.cardStroke
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.outputName,
                        style = MaterialTheme.typography.titleSmall,
                        color = semanticColors.text,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.config.describeForQueue(),
                        style = MaterialTheme.typography.bodySmall,
                        color = semanticColors.subtext
                    )
                    item.sourceRange?.let { source ->
                        Text(
                            text = stringResource(
                                R.string.batch_export_source_cut_label,
                                source.displayName,
                                formatBatchCutTime(source.startMs),
                                formatBatchCutTime(source.endMs),
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = ClearCutAccents.Mauve,
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PremiumPanelPill(
                        text = statusLabel,
                        accent = accent
                    )

                    if (retryable) {
                        PremiumPanelIconButton(
                            icon = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.batch_export_retry_cd),
                            onClick = onRetry,
                            tint = ClearCutAccents.Green,
                        )
                    }

                    if (canMoveUp) {
                        PremiumPanelIconButton(
                            icon = Icons.Default.ArrowUpward,
                            contentDescription = stringResource(
                                R.string.batch_export_move_up_cd,
                                item.outputName,
                            ),
                            onClick = onMoveUp,
                            tint = ClearCutAccents.Blue,
                        )
                    }
                    if (canMoveDown) {
                        PremiumPanelIconButton(
                            icon = Icons.Default.ArrowDownward,
                            contentDescription = stringResource(
                                R.string.batch_export_move_down_cd,
                                item.outputName,
                            ),
                            onClick = onMoveDown,
                            tint = ClearCutAccents.Blue,
                        )
                    }

                    if (removable) {
                        PremiumPanelIconButton(
                            icon = Icons.Default.Close,
                            contentDescription = stringResource(R.string.batch_export_remove_cd),
                            onClick = onRemove,
                            tint = semanticColors.subtext
                        )
                    } else {
                        CircularProgressIndicator(
                            progress = { item.progress.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .width(24.dp)
                                .height(24.dp),
                            color = ClearCutAccents.Mauve,
                            strokeWidth = 2.5.dp
                        )
                    }
                }
            }

            if (item.status == BatchExportStatus.IN_PROGRESS) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LinearProgressIndicator(
                        progress = { item.progress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .background(semanticColors.surface, RoundedCornerShape(10.dp)),
                        color = ClearCutAccents.Mauve,
                        trackColor = semanticColors.surface
                    )
                    Text(
                        text = stringResource(R.string.batch_export_status_in_progress),
                        style = MaterialTheme.typography.labelMedium,
                        color = semanticColors.subtext
                    )
                }
            }

            item.errorMessage?.takeIf { it.isNotBlank() }?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (item.status == BatchExportStatus.REVIEW_REQUIRED) {
                        ClearCutAccents.Red
                    } else {
                        semanticColors.subtext
                    },
                )
            }
        }
    }
}

@Composable
private fun ExportConfig.describeForQueue(): String = buildString {
    append(platformPreset?.displayName ?: resolution.label)
    append(" • ")
    when {
        exportAudioOnly -> {
            append(stringResource(R.string.batch_export_suffix_audio_only))
            append(" • ")
            append(audioCodec.label)
        }

        exportStemsOnly -> {
            append(stringResource(R.string.batch_export_suffix_stems))
            append(" • ")
            append(audioCodec.label)
        }

        else -> {
            append(aspectRatio.label)
            append(" • ")
            append(codec.label)
            append(" • ")
            append(stringResource(R.string.export_fps_value, frameRate))
            if (forceConstantFrameRate) {
                append(" • ")
                append(stringResource(R.string.export_constant_frame_rate_pill))
            }
        }
    }
}

private fun formatBatchCutTime(timeMs: Long): String {
    val safeMs = timeMs.coerceAtLeast(0L)
    val totalSeconds = safeMs / 1_000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}

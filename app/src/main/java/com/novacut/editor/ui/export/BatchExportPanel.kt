package com.novacut.editor.ui.export

import com.novacut.editor.ui.theme.ClearCutAccents
import com.novacut.editor.ui.theme.LocalClearCutColors
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.RocketLaunch
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.novacut.editor.R
import com.novacut.editor.model.BatchExportItem
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
    onAddItem: (ExportConfig, String) -> Unit,
    onRemoveItem: (String) -> Unit,
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
    val activeLabel = when {
        inProgressCount > 0 -> "$inProgressCount active"
        failedCount > 0 -> "$failedCount needs attention"
        completedCount > 0 -> "$completedCount done"
        else -> stringResource(R.string.batch_export_status_ready)
    }

    PremiumEditorPanel(
        title = stringResource(R.string.batch_export_title),
        subtitle = "Queue multiple delivery variants, social presets, or utility exports and send them out in one run.",
        icon = Icons.Default.FileUpload,
        accent = ClearCutAccents.Mauve,
        onClose = onClose,
        modifier = modifier,
        scrollable = true,
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
                        text = "${queue.size} total",
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
                    text = "$queuedCount queued",
                    accent = ClearCutAccents.Blue
                )
                if (inProgressCount > 0) {
                    PremiumPanelPill(
                        text = "$inProgressCount exporting",
                        accent = ClearCutAccents.Mauve
                    )
                }
                if (completedCount > 0) {
                    PremiumPanelPill(
                        text = "$completedCount done",
                        accent = ClearCutAccents.Green
                    )
                }
                if (failedCount > 0) {
                    PremiumPanelPill(
                        text = "$failedCount failed",
                        accent = ClearCutAccents.Red
                    )
                }
                if (cancelledCount > 0) {
                    PremiumPanelPill(
                        text = "$cancelledCount cancelled",
                        accent = ClearCutAccents.Yellow
                    )
                }
            }
        }

        if (showPresetPicker) {
            Spacer(modifier = Modifier.height(12.dp))

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
                                val config = ExportConfig(
                                    resolution = preset.resolution,
                                    aspectRatio = preset.aspectRatio,
                                    frameRate = preset.frameRate,
                                    codec = preset.codec,
                                    platformPreset = preset
                                )
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
                    shape = RoundedCornerShape(20.dp),
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
                    queue.forEach { item ->
                        BatchExportItemRow(
                            item = item,
                            onRemove = { onRemoveItem(item.id) }
                        )
                    }
                }
            }
        }

        if (queue.isNotEmpty()) {
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
                    shape = RoundedCornerShape(18.dp)
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Default.RocketLaunch,
                        contentDescription = stringResource(R.string.cd_batch_export)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.batch_export_export_all, queue.size))
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
    onRemove: () -> Unit
) {
    val semanticColors = LocalClearCutColors.current
    val accent = when (item.status) {
        BatchExportStatus.QUEUED -> ClearCutAccents.Blue
        BatchExportStatus.IN_PROGRESS -> ClearCutAccents.Mauve
        BatchExportStatus.COMPLETED -> ClearCutAccents.Green
        BatchExportStatus.FAILED -> ClearCutAccents.Red
        BatchExportStatus.CANCELLED -> ClearCutAccents.Yellow
    }
    val statusLabel = when (item.status) {
        BatchExportStatus.QUEUED -> stringResource(R.string.batch_export_status_queued)
        BatchExportStatus.IN_PROGRESS -> "${(item.progress * 100).toInt().coerceIn(0, 100)}%"
        BatchExportStatus.COMPLETED -> stringResource(R.string.batch_export_done_cd)
        BatchExportStatus.FAILED -> stringResource(R.string.batch_export_failed_cd)
        BatchExportStatus.CANCELLED -> stringResource(R.string.batch_export_cancelled_cd)
    }
    val removable = item.status != BatchExportStatus.IN_PROGRESS

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (item.status == BatchExportStatus.IN_PROGRESS) accent.copy(alpha = 0.12f) else semanticColors.panelRaised,
        shape = RoundedCornerShape(20.dp),
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
        }
    }
}

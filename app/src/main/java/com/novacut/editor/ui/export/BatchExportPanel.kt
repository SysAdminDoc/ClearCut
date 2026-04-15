package com.novacut.editor.ui.export

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
import com.novacut.editor.ui.theme.Mocha

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
        accent = Mocha.Mauve,
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
                tint = if (showPresetPicker) Mocha.Peach else Mocha.Green
            )
        }
    ) {
        PremiumPanelCard(accent = Mocha.Mauve) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.batch_export_queue_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = Mocha.Text
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.batch_export_queue_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Mocha.Subtext0
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PremiumPanelPill(
                        text = "${queue.size} total",
                        accent = Mocha.Blue
                    )
                    PremiumPanelPill(
                        text = activeLabel,
                        accent = if (failedCount > 0) Mocha.Red else Mocha.Mauve
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PremiumPanelPill(
                    text = "$queuedCount queued",
                    accent = Mocha.Blue
                )
                if (inProgressCount > 0) {
                    PremiumPanelPill(
                        text = "$inProgressCount exporting",
                        accent = Mocha.Mauve
                    )
                }
                if (completedCount > 0) {
                    PremiumPanelPill(
                        text = "$completedCount done",
                        accent = Mocha.Green
                    )
                }
                if (failedCount > 0) {
                    PremiumPanelPill(
                        text = "$failedCount failed",
                        accent = Mocha.Red
                    )
                }
                if (cancelledCount > 0) {
                    PremiumPanelPill(
                        text = "$cancelledCount cancelled",
                        accent = Mocha.Yellow
                    )
                }
            }
        }

        if (showPresetPicker) {
            Spacer(modifier = Modifier.height(12.dp))

            PremiumPanelCard(accent = Mocha.Blue) {
                Text(
                    text = stringResource(R.string.batch_export_add_platform_preset),
                    style = MaterialTheme.typography.titleMedium,
                    color = Mocha.Text
                )
                Text(
                    text = stringResource(R.string.batch_export_add_targets_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Mocha.Subtext0
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
                            },
                            label = {
                                Text(
                                    text = preset.displayName,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = Mocha.PanelRaised,
                                labelColor = Mocha.Subtext0,
                                selectedContainerColor = Mocha.Blue.copy(alpha = 0.16f),
                                selectedLabelColor = Mocha.Blue
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
                        accent = Mocha.Peach,
                        onClick = {
                            onAddItem(
                                ExportConfig(exportAudioOnly = true),
                                audioOnlyLabel
                            )
                        }
                    )
                    UtilityExportChip(
                        label = audioStemsLabel,
                        accent = Mocha.Yellow,
                        onClick = {
                            onAddItem(
                                ExportConfig(exportStemsOnly = true),
                                audioStemsLabel
                            )
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        PremiumPanelCard(accent = Mocha.Green) {
            Text(
                text = stringResource(R.string.batch_export_queued_title),
                style = MaterialTheme.typography.titleMedium,
                color = Mocha.Text
            )
            Text(
                text = if (queue.isEmpty()) {
                    stringResource(R.string.batch_export_empty_queue)
                } else {
                    stringResource(R.string.batch_export_queued_description)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Mocha.Subtext0
            )

            if (queue.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Mocha.PanelRaised,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, Mocha.CardStroke)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.batch_export_empty_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = Mocha.Text
                        )
                        Text(
                            text = stringResource(R.string.batch_export_empty_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = Mocha.Subtext0
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

            PremiumPanelCard(accent = Mocha.Green) {
                Text(
                    text = stringResource(R.string.batch_export_run_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = Mocha.Text
                )
                Text(
                    text = stringResource(R.string.batch_export_run_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Mocha.Subtext0
                )

                Button(
                    onClick = onStartBatch,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Mocha.Mauve),
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
    val accent = when (item.status) {
        BatchExportStatus.QUEUED -> Mocha.Blue
        BatchExportStatus.IN_PROGRESS -> Mocha.Mauve
        BatchExportStatus.COMPLETED -> Mocha.Green
        BatchExportStatus.FAILED -> Mocha.Red
        BatchExportStatus.CANCELLED -> Mocha.Yellow
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
        color = if (item.status == BatchExportStatus.IN_PROGRESS) accent.copy(alpha = 0.12f) else Mocha.PanelRaised,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(
            1.dp,
            if (item.status == BatchExportStatus.IN_PROGRESS) accent.copy(alpha = 0.22f) else Mocha.CardStroke
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
                        color = Mocha.Text,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.config.describeForQueue(),
                        style = MaterialTheme.typography.bodySmall,
                        color = Mocha.Subtext0
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
                            tint = Mocha.Subtext0
                        )
                    } else {
                        CircularProgressIndicator(
                            progress = { item.progress.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .width(24.dp)
                                .height(24.dp),
                            color = Mocha.Mauve,
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
                            .background(Mocha.Surface1, RoundedCornerShape(999.dp)),
                        color = Mocha.Mauve,
                        trackColor = Mocha.Surface1
                    )
                    Text(
                        text = stringResource(R.string.batch_export_status_in_progress),
                        style = MaterialTheme.typography.labelMedium,
                        color = Mocha.Subtext0
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

package com.novacut.editor.ui.export

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
    val activeLabel = when {
        inProgressCount > 0 -> "$inProgressCount active"
        failedCount > 0 -> "$failedCount needs attention"
        completedCount > 0 -> "$completedCount done"
        else -> "Ready"
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
                        text = "Delivery queue",
                        style = MaterialTheme.typography.titleMedium,
                        color = Mocha.Text
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Build a stack of exports for different platforms and let NovaCut run them back-to-back.",
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PremiumPanelPill(text = "$queuedCount queued", accent = Mocha.Blue)
                if (inProgressCount > 0) {
                    PremiumPanelPill(text = "$inProgressCount exporting", accent = Mocha.Mauve)
                }
                if (completedCount > 0) {
                    PremiumPanelPill(text = "$completedCount done", accent = Mocha.Green)
                }
            }
        }

        if (showPresetPicker) {
            Spacer(modifier = Modifier.height(12.dp))

            PremiumPanelCard(accent = Mocha.Blue) {
                Text(
                    text = "Add export targets",
                    style = MaterialTheme.typography.titleMedium,
                    color = Mocha.Text
                )
                Text(
                    text = "Tap any preset to drop it straight into the queue, then add utility exports for stems or an audio-only master.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Mocha.Subtext0
                )

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                                containerColor = Mocha.PanelRaised,
                                labelColor = Mocha.Subtext0,
                                selectedContainerColor = Mocha.Blue.copy(alpha = 0.16f),
                                selectedLabelColor = Mocha.Blue
                            )
                        )
                    }
                }

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    UtilityExportChip(
                        label = audioOnlyLabel,
                        accent = Mocha.Peach,
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
                        accent = Mocha.Yellow,
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

        PremiumPanelCard(accent = Mocha.Green) {
            Text(
                text = "Queued exports",
                style = MaterialTheme.typography.titleMedium,
                color = Mocha.Text
            )
            Text(
                text = if (queue.isEmpty()) {
                    stringResource(R.string.batch_export_empty_queue)
                } else {
                    "Watch progress here, remove anything you do not need, and launch the full queue when you are ready."
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
                            text = "Nothing queued yet",
                            style = MaterialTheme.typography.titleSmall,
                            color = Mocha.Text
                        )
                        Text(
                            text = "Open the add control to line up YouTube, TikTok, square, or audio-only exports in one place.",
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
                    text = "Run the batch",
                    style = MaterialTheme.typography.titleMedium,
                    color = Mocha.Text
                )
                Text(
                    text = "NovaCut will export each preset in order and keep status here for every item in the stack.",
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
        BatchExportStatus.QUEUED -> "Queued"
        BatchExportStatus.IN_PROGRESS -> "${(item.progress * 100).toInt().coerceIn(0, 100)}%"
        BatchExportStatus.COMPLETED -> "Done"
        BatchExportStatus.FAILED -> "Failed"
        BatchExportStatus.CANCELLED -> "Cancelled"
    }

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

                    when (item.status) {
                        BatchExportStatus.QUEUED -> {
                            PremiumPanelIconButton(
                                icon = Icons.Default.Close,
                                contentDescription = stringResource(R.string.batch_export_remove_cd),
                                onClick = onRemove,
                                tint = Mocha.Subtext0
                            )
                        }

                        BatchExportStatus.IN_PROGRESS -> {
                            CircularProgressIndicator(
                                progress = { item.progress.coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .width(24.dp)
                                    .height(24.dp),
                                color = Mocha.Mauve,
                                strokeWidth = 2.5.dp
                            )
                        }

                        BatchExportStatus.COMPLETED -> {
                            androidx.compose.material3.Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = stringResource(R.string.batch_export_done_cd),
                                tint = Mocha.Green
                            )
                        }

                        BatchExportStatus.FAILED -> {
                            androidx.compose.material3.Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = stringResource(R.string.batch_export_failed_cd),
                                tint = Mocha.Red
                            )
                        }

                        BatchExportStatus.CANCELLED -> {
                            androidx.compose.material3.Icon(
                                imageVector = Icons.Default.Cancel,
                                contentDescription = stringResource(R.string.batch_export_cancelled_cd),
                                tint = Mocha.Yellow
                            )
                        }
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
                        text = "Export in progress",
                        style = MaterialTheme.typography.labelMedium,
                        color = Mocha.Subtext0
                    )
                }
            }
        }
    }
}

private fun ExportConfig.describeForQueue(): String = buildString {
    append(platformPreset?.displayName ?: resolution.label)
    append(" | ")
    when {
        exportAudioOnly -> {
            append("Audio Only | ")
            append(audioCodec.label)
        }

        exportStemsOnly -> {
            append("Stems | ")
            append(audioCodec.label)
        }

        else -> {
            append(aspectRatio.label)
            append(" | ")
            append(codec.label)
        }
    }
}

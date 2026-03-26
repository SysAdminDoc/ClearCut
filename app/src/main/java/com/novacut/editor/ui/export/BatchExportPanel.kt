package com.novacut.editor.ui.export

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novacut.editor.R
import com.novacut.editor.model.*
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
    var showPresetPicker by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Mocha.Crust, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.batch_export_title), color = Mocha.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Row {
                IconButton(onClick = { showPresetPicker = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Add, stringResource(R.string.batch_export_add_cd), tint = Mocha.Green, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, stringResource(R.string.batch_export_close_cd), tint = Mocha.Subtext0, modifier = Modifier.size(18.dp))
                }
            }
        }

        // Platform preset picker
        if (showPresetPicker) {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.batch_export_add_platform_preset), color = Mocha.Subtext0, fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
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
                        label = { Text(preset.displayName, fontSize = 10.sp) },
                        modifier = Modifier.height(28.dp)
                    )
                }
            }

            // Custom options
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = false,
                    onClick = {
                        onAddItem(ExportConfig(exportAudioOnly = true), stringResource(R.string.batch_export_audio_only))
                        showPresetPicker = false
                    },
                    label = { Text(stringResource(R.string.batch_export_audio_only), fontSize = 10.sp) },
                    modifier = Modifier.height(28.dp)
                )
                FilterChip(
                    selected = false,
                    onClick = {
                        onAddItem(ExportConfig(exportStemsOnly = true), stringResource(R.string.batch_export_audio_stems))
                        showPresetPicker = false
                    },
                    label = { Text(stringResource(R.string.batch_export_audio_stems), fontSize = 10.sp) },
                    modifier = Modifier.height(28.dp)
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Queue
        if (queue.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.batch_export_empty_queue), color = Mocha.Subtext0, fontSize = 13.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 250.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(queue, key = { it.id }) { item ->
                    BatchExportItemRow(
                        item = item,
                        onRemove = { onRemoveItem(item.id) }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = onStartBatch,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Mocha.Mauve),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.RocketLaunch, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.batch_export_export_all, queue.size))
            }
        }
    }
}

@Composable
private fun BatchExportItemRow(
    item: BatchExportItem,
    onRemove: () -> Unit
) {
    val suffixAudioOnly = stringResource(R.string.batch_export_suffix_audio_only)
    val suffixStems = stringResource(R.string.batch_export_suffix_stems)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Mocha.Surface0)
            .padding(10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.outputName, color = Mocha.Text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(
                buildString {
                    append(item.config.resolution.label)
                    append(" | ")
                    append(item.config.codec.label)
                    if (item.config.exportAudioOnly) append(suffixAudioOnly)
                    if (item.config.exportStemsOnly) append(suffixStems)
                },
                color = Mocha.Subtext0,
                fontSize = 10.sp
            )
        }

        when (item.status) {
            BatchExportStatus.QUEUED -> {
                IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, stringResource(R.string.batch_export_remove_cd), tint = Mocha.Subtext0, modifier = Modifier.size(16.dp))
                }
            }
            BatchExportStatus.IN_PROGRESS -> {
                CircularProgressIndicator(
                    progress = { item.progress },
                    modifier = Modifier.size(24.dp),
                    color = Mocha.Mauve,
                    strokeWidth = 2.dp
                )
            }
            BatchExportStatus.COMPLETED -> {
                Icon(Icons.Default.CheckCircle, stringResource(R.string.batch_export_done_cd), tint = Mocha.Green, modifier = Modifier.size(24.dp))
            }
            BatchExportStatus.FAILED -> {
                Icon(Icons.Default.Error, stringResource(R.string.batch_export_failed_cd), tint = Mocha.Red, modifier = Modifier.size(24.dp))
            }
            BatchExportStatus.CANCELLED -> {
                Icon(Icons.Default.Cancel, stringResource(R.string.batch_export_cancelled_cd), tint = Mocha.Yellow, modifier = Modifier.size(24.dp))
            }
        }
    }
}

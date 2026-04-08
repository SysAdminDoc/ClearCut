package com.novacut.editor.ui.settings

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novacut.editor.NovaCutApp
import com.novacut.editor.R
import com.novacut.editor.engine.AppSettings
import com.novacut.editor.model.*
import com.novacut.editor.ui.theme.Mocha

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToAiModels: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Mocha.Midnight,
                        Mocha.Base.copy(alpha = 0.98f),
                        Mocha.Midnight
                    )
                )
            )
            .verticalScroll(rememberScrollState())
    ) {
        SettingsHero(
            settings = settings,
            onBack = onBack,
            onManageAiModels = onNavigateToAiModels
        )

        Spacer(Modifier.height(10.dp))

        // Export Defaults
        SettingsSection(stringResource(R.string.settings_export_defaults)) {
            SettingsDropdown(
                label = stringResource(R.string.settings_default_resolution),
                value = settings.defaultResolution.label,
                options = Resolution.entries.map { it.label },
                onSelected = { idx -> viewModel.setResolution(Resolution.entries[idx]) }
            )
            SettingsDropdown(
                label = stringResource(R.string.settings_default_frame_rate),
                value = "${settings.defaultFrameRate}fps",
                options = listOf("24fps", "30fps", "60fps"),
                onSelected = { idx -> viewModel.setFrameRate(listOf(24, 30, 60)[idx]) }
            )
            SettingsDropdown(
                label = stringResource(R.string.settings_default_aspect_ratio),
                value = settings.defaultAspectRatio.label,
                options = AspectRatio.entries.map { it.label },
                onSelected = { idx -> viewModel.setAspectRatio(AspectRatio.entries[idx]) }
            )
            SettingsDropdown(
                label = stringResource(R.string.settings_default_codec),
                value = listOf("H.264", "H.265 (HEVC)", "AV1", "VP9")[
                    listOf("H264", "HEVC", "AV1", "VP9").indexOf(settings.defaultCodec).coerceAtLeast(0)
                ],
                options = listOf("H.264", "H.265 (HEVC)", "AV1", "VP9"),
                onSelected = { viewModel.setDefaultCodec(listOf("H264", "HEVC", "AV1", "VP9")[it]) }
            )
        }

        // Timeline
        SettingsSection(stringResource(R.string.settings_timeline)) {
            SettingsToggle(
                label = stringResource(R.string.settings_auto_save),
                description = stringResource(R.string.settings_auto_save_description),
                checked = settings.autoSaveEnabled,
                onChanged = { viewModel.setAutoSave(it) }
            )
            if (settings.autoSaveEnabled) {
                SettingsSlider(
                    label = stringResource(R.string.settings_auto_save_interval),
                    value = settings.autoSaveIntervalSec.toFloat(),
                    range = 15f..300f,
                    valueLabel = "${settings.autoSaveIntervalSec}s",
                    onChanged = { viewModel.setAutoSaveInterval(it.toInt()) }
                )
            }
            SettingsDropdown(
                label = stringResource(R.string.settings_proxy_resolution),
                value = settings.proxyResolution.label,
                options = ProxyResolution.entries.map { it.label },
                onSelected = { idx -> viewModel.setProxyResolution(ProxyResolution.entries[idx]) }
            )
            SettingsSwitch(
                label = stringResource(R.string.settings_enable_proxy),
                description = stringResource(R.string.settings_enable_proxy_description),
                checked = settings.proxyEnabled,
                onChanged = { viewModel.setProxyEnabled(it) }
            )
            SettingsSwitch(
                label = stringResource(R.string.settings_show_waveforms),
                description = stringResource(R.string.settings_show_waveforms_desc),
                checked = settings.showWaveforms,
                onChanged = { viewModel.setShowWaveforms(it) }
            )
            SettingsSwitch(
                label = stringResource(R.string.settings_snap_beat),
                description = stringResource(R.string.settings_snap_beat_desc),
                checked = settings.snapToBeat,
                onChanged = { viewModel.setSnapToBeat(it) }
            )
            SettingsSwitch(
                label = stringResource(R.string.settings_snap_markers),
                description = stringResource(R.string.settings_snap_markers_desc),
                checked = settings.snapToMarker,
                onChanged = { viewModel.setSnapToMarker(it) }
            )
            // Default Track Height chips
            Text(stringResource(R.string.settings_default_track_height), color = Mocha.Text, fontSize = 14.sp,
                modifier = Modifier.padding(top = 8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(48, 64, 80, 96).forEach { height ->
                    FilterChip(
                        selected = settings.defaultTrackHeight == height,
                        onClick = { viewModel.setDefaultTrackHeight(height) },
                        label = { Text("${height}dp", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Mocha.Mauve,
                            selectedLabelColor = Mocha.Crust,
                            containerColor = Mocha.Surface0,
                            labelColor = Mocha.Text
                        )
                    )
                }
            }
        }

        // AI Models
        SettingsSection(stringResource(R.string.settings_ai_models)) {
            Text(
                stringResource(R.string.settings_ai_models_description),
                color = Mocha.Subtext0,
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Model info rows
            val models = listOf(
                stringResource(R.string.settings_whisper_model) to stringResource(R.string.settings_whisper_size),
                stringResource(R.string.settings_segmentation_model) to stringResource(R.string.settings_segmentation_size),
                stringResource(R.string.settings_piper_model) to stringResource(R.string.settings_piper_size)
            )
            models.forEach { (name, size) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(name, color = Mocha.Text, fontSize = 13.sp)
                        Text(stringResource(R.string.settings_download_size_format, size), color = Mocha.Subtext0, fontSize = 10.sp)
                    }
                    Text(stringResource(R.string.manage), color = Mocha.Mauve, fontSize = 11.sp,
                        modifier = Modifier.clickable { onNavigateToAiModels() })
                }
            }
        }

        // Editor
        SettingsSection(stringResource(R.string.settings_editor)) {
            SettingsDropdown(
                label = stringResource(R.string.settings_default_mode),
                value = settings.editorMode,
                options = listOf(stringResource(R.string.settings_mode_easy), stringResource(R.string.settings_mode_pro)),
                onSelected = { viewModel.setEditorMode(listOf("Easy", "Pro")[it]) }
            )
            SettingsToggle(
                label = stringResource(R.string.settings_haptic_feedback),
                description = stringResource(R.string.settings_haptic_desc),
                checked = settings.hapticEnabled,
                onChanged = { viewModel.setHapticEnabled(it) }
            )
            SettingsSwitch(
                label = stringResource(R.string.settings_confirm_delete),
                description = stringResource(R.string.settings_confirm_delete_desc),
                checked = settings.confirmBeforeDelete,
                onChanged = { viewModel.setConfirmBeforeDelete(it) }
            )
            // Thumbnail Cache chips
            Text(stringResource(R.string.settings_thumbnail_cache), color = Mocha.Text, fontSize = 14.sp,
                modifier = Modifier.padding(top = 8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(64 to "64 MB", 128 to "128 MB", 256 to "256 MB").forEach { (size, label) ->
                    FilterChip(
                        selected = settings.thumbnailCacheSizeMb == size,
                        onClick = { viewModel.setThumbnailCacheSize(size) },
                        label = { Text(label, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Mocha.Mauve,
                            selectedLabelColor = Mocha.Crust,
                            containerColor = Mocha.Surface0,
                            labelColor = Mocha.Text
                        )
                    )
                }
            }
            // Default Export Quality chips
            Text(stringResource(R.string.settings_export_quality), color = Mocha.Text, fontSize = 14.sp,
                modifier = Modifier.padding(top = 8.dp))
            val qualityLabels = listOf(
                "LOW" to stringResource(R.string.settings_quality_small),
                "MEDIUM" to stringResource(R.string.settings_quality_balanced),
                "HIGH" to stringResource(R.string.settings_quality_best)
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                qualityLabels.forEach { (key, label) ->
                    FilterChip(
                        selected = settings.defaultExportQuality == key,
                        onClick = { viewModel.setDefaultExportQuality(key) },
                        label = { Text(label, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Mocha.Mauve,
                            selectedLabelColor = Mocha.Crust,
                            containerColor = Mocha.Surface0,
                            labelColor = Mocha.Text
                        )
                    )
                }
            }
        }

        // Tutorial
        var showResetConfirm by remember { mutableStateOf(false) }
        SettingsSection(stringResource(R.string.settings_tutorial)) {
            OutlinedButton(
                onClick = { showResetConfirm = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.settings_reset_tutorial))
            }
        }
        if (showResetConfirm) {
            AlertDialog(
                onDismissRequest = { showResetConfirm = false },
                title = { Text(stringResource(R.string.settings_reset_tutorial_confirm_title), color = Mocha.Text) },
                text = { Text(stringResource(R.string.settings_reset_tutorial_confirm), color = Mocha.Subtext0) },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.resetTutorial()
                        showResetConfirm = false
                    }) { Text(stringResource(R.string.settings_confirm), color = Mocha.Mauve) }
                },
                dismissButton = {
                    TextButton(onClick = { showResetConfirm = false }) {
                        Text(stringResource(R.string.cancel), color = Mocha.Subtext0)
                    }
                },
                containerColor = Mocha.Mantle
            )
        }

        // About
        SettingsSection(stringResource(R.string.settings_about)) {
            SettingsInfo(stringResource(R.string.settings_version), NovaCutApp.VERSION)
            SettingsInfo(stringResource(R.string.settings_engine), stringResource(R.string.settings_engine_value))
            SettingsInfo(stringResource(R.string.settings_ai_models), stringResource(R.string.settings_ai_models_value))
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SettingsHero(
    settings: AppSettings,
    onBack: () -> Unit,
    onManageAiModels: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Surface(
            color = Mocha.Panel,
            shape = RoundedCornerShape(28.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Mocha.CardStroke.copy(alpha = 0.9f))
        ) {
            Box(
                modifier = Modifier
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Mocha.PanelHighest.copy(alpha = 0.94f),
                                Mocha.Panel.copy(alpha = 0.98f),
                                Mocha.Mantle
                            )
                        )
                    )
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = Mocha.PanelHighest,
                            shape = RoundedCornerShape(18.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Mocha.CardStroke)
                        ) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    stringResource(R.string.back),
                                    tint = Mocha.Text
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.settings_title),
                                color = Mocha.Text,
                                style = MaterialTheme.typography.headlineLarge
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.settings_subtitle),
                                color = Mocha.Subtext0,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SettingsOverviewStat(
                            label = stringResource(R.string.settings_editor),
                            value = settings.editorMode,
                            accent = Mocha.Mauve,
                            modifier = Modifier.weight(1f)
                        )
                        SettingsOverviewStat(
                            label = stringResource(R.string.settings_auto_save),
                            value = if (settings.autoSaveEnabled) "${settings.autoSaveIntervalSec}s" else stringResource(R.string.cancelled),
                            accent = Mocha.Sapphire,
                            modifier = Modifier.weight(1f)
                        )
                        SettingsOverviewStat(
                            label = stringResource(R.string.settings_ai_models),
                            value = stringResource(R.string.manage),
                            accent = Mocha.Rosewater,
                            modifier = Modifier
                                .weight(1f)
                                .clickable(onClick = onManageAiModels)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsOverviewStat(
    label: String,
    value: String,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = accent.copy(alpha = 0.1f),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.18f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                color = accent,
                style = MaterialTheme.typography.labelMedium
            )
            Text(
                text = value,
                color = Mocha.Text,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            title,
            color = Mocha.Rosewater,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = Mocha.Panel),
            shape = RoundedCornerShape(22.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Mocha.CardStroke.copy(alpha = 0.85f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier.background(
                    Brush.verticalGradient(
                        listOf(
                            Mocha.PanelHighest.copy(alpha = 0.78f),
                            Mocha.Panel
                        )
                    )
                )
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    content = content
                )
            }
        }
    }
}

@Composable
private fun SettingsDropdown(
    label: String,
    value: String,
    options: List<String>,
    onSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Surface(
        color = Mocha.PanelHighest,
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Mocha.CardStroke.copy(alpha = 0.9f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = Mocha.Text, style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(value, color = Mocha.Subtext0, style = MaterialTheme.typography.bodyMedium)
                Icon(Icons.Default.ArrowDropDown, stringResource(R.string.cd_dropdown), tint = Mocha.Subtext0, modifier = Modifier.size(18.dp))
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEachIndexed { idx, opt ->
                DropdownMenuItem(
                    text = { Text(opt, style = MaterialTheme.typography.bodyMedium) },
                    onClick = { onSelected(idx); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun SettingsToggle(
    label: String,
    description: String,
    checked: Boolean,
    onChanged: (Boolean) -> Unit
) {
    Surface(
        color = Mocha.PanelHighest,
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Mocha.CardStroke.copy(alpha = 0.9f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, color = Mocha.Text, style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(2.dp))
                Text(description, color = Mocha.Subtext0, style = MaterialTheme.typography.bodySmall)
            }
            Switch(
                checked = checked,
                onCheckedChange = onChanged,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = Mocha.Mauve,
                    checkedThumbColor = Mocha.Crust,
                    uncheckedTrackColor = Mocha.Surface1,
                    uncheckedThumbColor = Mocha.Subtext0
                )
            )
        }
    }
}

@Composable
private fun SettingsSwitch(
    label: String,
    description: String,
    checked: Boolean,
    onChanged: (Boolean) -> Unit
) {
    Surface(
        color = Mocha.PanelHighest,
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Mocha.CardStroke.copy(alpha = 0.9f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, color = Mocha.Text, style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(2.dp))
                Text(description, color = Mocha.Subtext0, style = MaterialTheme.typography.bodySmall)
            }
            Switch(
                checked = checked,
                onCheckedChange = onChanged,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = Mocha.Mauve,
                    checkedThumbColor = Mocha.Crust,
                    uncheckedTrackColor = Mocha.Surface1,
                    uncheckedThumbColor = Mocha.Subtext0
                )
            )
        }
    }
}

@Composable
private fun SettingsSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueLabel: String,
    onChanged: (Float) -> Unit
) {
    Surface(
        color = Mocha.PanelHighest,
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Mocha.CardStroke.copy(alpha = 0.9f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(label, color = Mocha.Text, style = MaterialTheme.typography.titleSmall)
                Text(valueLabel, color = Mocha.Subtext0, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Slider(
                value = value,
                onValueChange = onChanged,
                valueRange = range,
                colors = SliderDefaults.colors(
                    thumbColor = Mocha.Mauve,
                    activeTrackColor = Mocha.Mauve,
                    inactiveTrackColor = Mocha.Surface1
                )
            )
        }
    }
}

@Composable
private fun SettingsInfo(label: String, value: String) {
    Surface(
        color = Mocha.PanelHighest,
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Mocha.CardStroke.copy(alpha = 0.9f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = Mocha.Text, style = MaterialTheme.typography.titleSmall)
            Text(value, color = Mocha.Subtext0, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

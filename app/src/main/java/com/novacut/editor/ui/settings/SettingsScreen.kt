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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novacut.editor.NovaCutApp
import com.novacut.editor.R
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
            .background(Mocha.Base)
            .verticalScroll(rememberScrollState())
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Mocha.Mantle)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = Mocha.Text)
            }
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.settings_title), color = Mocha.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(8.dp))

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
                label = "Show Waveforms",
                description = "Display audio waveforms on timeline clips",
                checked = settings.showWaveforms,
                onChanged = { viewModel.setShowWaveforms(it) }
            )
            SettingsSwitch(
                label = "Snap to Beat",
                description = "Snap clip edges to detected beat markers",
                checked = settings.snapToBeat,
                onChanged = { viewModel.setSnapToBeat(it) }
            )
            SettingsSwitch(
                label = "Snap to Markers",
                description = "Snap clip edges to timeline markers",
                checked = settings.snapToMarker,
                onChanged = { viewModel.setSnapToMarker(it) }
            )
            // Default Track Height chips
            Text("Default Track Height", color = Mocha.Text, fontSize = 14.sp,
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
        SettingsSection("Editor") {
            SettingsDropdown(
                label = "Default Mode",
                value = settings.editorMode,
                options = listOf("Easy", "Pro"),
                onSelected = { viewModel.setEditorMode(listOf("Easy", "Pro")[it]) }
            )
            SettingsToggle(
                label = "Haptic Feedback",
                description = "Vibrate on timeline snap and clip selection",
                checked = settings.hapticEnabled,
                onChanged = { viewModel.setHapticEnabled(it) }
            )
            SettingsSwitch(
                label = "Confirm Before Delete",
                description = "Show confirmation dialog before deleting clips",
                checked = settings.confirmBeforeDelete,
                onChanged = { viewModel.setConfirmBeforeDelete(it) }
            )
            // Thumbnail Cache chips
            Text("Thumbnail Cache", color = Mocha.Text, fontSize = 14.sp,
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
            Text("Default Export Quality", color = Mocha.Text, fontSize = 14.sp,
                modifier = Modifier.padding(top = 8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("LOW" to "Small File", "MEDIUM" to "Balanced", "HIGH" to "Best Quality").forEach { (key, label) ->
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
        SettingsSection(stringResource(R.string.settings_tutorial)) {
            OutlinedButton(
                onClick = { viewModel.resetTutorial() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.settings_reset_tutorial))
            }
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
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            title,
            color = Mocha.Mauve,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = Mocha.Mantle),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), content = content)
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = true }
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Mocha.Text, fontSize = 14.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(value, color = Mocha.Subtext0, fontSize = 13.sp)
            Icon(Icons.Default.ArrowDropDown, null, tint = Mocha.Subtext0, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEachIndexed { idx, opt ->
                DropdownMenuItem(
                    text = { Text(opt, fontSize = 13.sp) },
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = Mocha.Text, fontSize = 14.sp)
            Text(description, color = Mocha.Subtext0, fontSize = 11.sp)
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

@Composable
private fun SettingsSwitch(
    label: String,
    description: String,
    checked: Boolean,
    onChanged: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = Mocha.Text, fontSize = 14.sp)
            Text(description, color = Mocha.Subtext0, fontSize = 11.sp)
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

@Composable
private fun SettingsSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueLabel: String,
    onChanged: (Float) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = Mocha.Text, fontSize = 14.sp)
            Text(valueLabel, color = Mocha.Subtext0, fontSize = 13.sp)
        }
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

@Composable
private fun SettingsInfo(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Mocha.Text, fontSize = 14.sp)
        Text(value, color = Mocha.Subtext0, fontSize = 13.sp)
    }
}

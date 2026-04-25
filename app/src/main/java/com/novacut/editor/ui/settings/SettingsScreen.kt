package com.novacut.editor.ui.settings

import androidx.compose.animation.animateContentSize
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novacut.editor.NovaCutApp
import com.novacut.editor.R
import com.novacut.editor.engine.AppSettings
import com.novacut.editor.model.*
import com.novacut.editor.ui.theme.Mocha
import com.novacut.editor.ui.theme.NovaCutChromeIconButton
import com.novacut.editor.ui.theme.NovaCutHeroCard
import com.novacut.editor.ui.theme.NovaCutMetricPill
import com.novacut.editor.ui.theme.NovaCutPrimaryButton
import com.novacut.editor.ui.theme.NovaCutScreenBackground
import com.novacut.editor.ui.theme.NovaCutSectionHeader
import com.novacut.editor.ui.theme.NovaCutSecondaryButton
import com.novacut.editor.ui.theme.Radius
import com.novacut.editor.ui.theme.Spacing

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToAiModels: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    NovaCutScreenBackground(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
        SettingsHero(
            settings = settings,
            onBack = onBack,
            onManageAiModels = onNavigateToAiModels
        )

        Spacer(Modifier.height(10.dp))

        // Export Defaults
        SettingsSection(
            title = stringResource(R.string.settings_export_defaults),
            description = stringResource(R.string.settings_export_defaults_description)
        ) {
            SettingsDropdown(
                icon = Icons.Default.Movie,
                accent = Mocha.Rosewater,
                label = stringResource(R.string.settings_default_resolution),
                description = stringResource(R.string.settings_default_resolution_description),
                value = settings.defaultResolution.label,
                options = Resolution.entries.map { it.label },
                onSelected = { idx -> viewModel.setResolution(Resolution.entries[idx]) }
            )
            SettingsDropdown(
                icon = Icons.Default.Schedule,
                accent = Mocha.Sapphire,
                label = stringResource(R.string.settings_default_frame_rate),
                description = stringResource(R.string.settings_default_frame_rate_description),
                value = "${settings.defaultFrameRate}fps",
                options = listOf("24fps", "30fps", "60fps"),
                onSelected = { idx -> viewModel.setFrameRate(listOf(24, 30, 60)[idx]) }
            )
            SettingsDropdown(
                icon = Icons.Default.CropSquare,
                accent = Mocha.Mauve,
                label = stringResource(R.string.settings_default_aspect_ratio),
                description = stringResource(R.string.settings_default_aspect_ratio_description),
                value = settings.defaultAspectRatio.label,
                options = AspectRatio.entries.map { it.label },
                onSelected = { idx -> viewModel.setAspectRatio(AspectRatio.entries[idx]) }
            )
            SettingsDropdown(
                icon = Icons.Default.Memory,
                accent = Mocha.Peach,
                label = stringResource(R.string.settings_default_codec),
                description = stringResource(R.string.settings_default_codec_description),
                value = listOf("H.264", "H.265 (HEVC)", "AV1", "VP9")[
                    listOf("H264", "HEVC", "AV1", "VP9").indexOf(settings.defaultCodec).coerceAtLeast(0)
                ],
                options = listOf("H.264", "H.265 (HEVC)", "AV1", "VP9"),
                onSelected = { viewModel.setDefaultCodec(listOf("H264", "HEVC", "AV1", "VP9")[it]) }
            )
        }

        // Timeline
        SettingsSection(
            title = stringResource(R.string.settings_timeline),
            description = stringResource(R.string.settings_timeline_description)
        ) {
            SettingsToggle(
                icon = Icons.Default.Save,
                accent = Mocha.Mauve,
                label = stringResource(R.string.settings_auto_save),
                description = stringResource(R.string.settings_auto_save_description),
                checked = settings.autoSaveEnabled,
                onChanged = { viewModel.setAutoSave(it) }
            )
            if (settings.autoSaveEnabled) {
                SettingsSlider(
                    icon = Icons.Default.Schedule,
                    accent = Mocha.Sapphire,
                    label = stringResource(R.string.settings_auto_save_interval),
                    description = stringResource(R.string.settings_auto_save_description),
                    value = settings.autoSaveIntervalSec.toFloat(),
                    range = 15f..300f,
                    valueLabel = "${settings.autoSaveIntervalSec}s",
                    onChanged = { viewModel.setAutoSaveInterval(it.toInt()) }
                )
            }
            SettingsDropdown(
                icon = Icons.Default.Tune,
                accent = Mocha.Sky,
                label = stringResource(R.string.settings_proxy_resolution),
                description = stringResource(R.string.settings_proxy_resolution_description),
                value = settings.proxyResolution.label,
                options = ProxyResolution.entries.map { it.label },
                onSelected = { idx -> viewModel.setProxyResolution(ProxyResolution.entries[idx]) }
            )
            SettingsSwitch(
                icon = Icons.Default.Layers,
                accent = Mocha.Blue,
                label = stringResource(R.string.settings_enable_proxy),
                description = stringResource(R.string.settings_enable_proxy_description),
                checked = settings.proxyEnabled,
                onChanged = { viewModel.setProxyEnabled(it) }
            )
            SettingsSwitch(
                icon = Icons.Default.GraphicEq,
                accent = Mocha.Green,
                label = stringResource(R.string.settings_show_waveforms),
                description = stringResource(R.string.settings_show_waveforms_desc),
                checked = settings.showWaveforms,
                onChanged = { viewModel.setShowWaveforms(it) }
            )
            SettingsSwitch(
                icon = Icons.Default.MusicNote,
                accent = Mocha.Green,
                label = stringResource(R.string.settings_snap_beat),
                description = stringResource(R.string.settings_snap_beat_desc),
                checked = settings.snapToBeat,
                onChanged = { viewModel.setSnapToBeat(it) }
            )
            SettingsSwitch(
                icon = Icons.Default.Bookmark,
                accent = Mocha.Yellow,
                label = stringResource(R.string.settings_snap_markers),
                description = stringResource(R.string.settings_snap_markers_desc),
                checked = settings.snapToMarker,
                onChanged = { viewModel.setSnapToMarker(it) }
            )
            SettingsChoiceHeader(
                icon = Icons.Default.ViewStream,
                accent = Mocha.Teal,
                label = stringResource(R.string.settings_default_track_height),
                description = stringResource(R.string.settings_default_track_height_description)
            )
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(48, 64, 80, 96).forEach { height ->
                    FilterChip(
                        selected = settings.defaultTrackHeight == height,
                        onClick = { viewModel.setDefaultTrackHeight(height) },
                        label = { Text("${height}dp", style = MaterialTheme.typography.labelMedium) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Mocha.Teal.copy(alpha = 0.18f),
                            selectedLabelColor = Mocha.Teal,
                            containerColor = Mocha.PanelHighest,
                            labelColor = Mocha.Text
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = settings.defaultTrackHeight == height,
                            borderColor = Mocha.CardStroke,
                            selectedBorderColor = Mocha.Teal.copy(alpha = 0.32f)
                        )
                    )
                }
            }
        }

        // AI Models
        SettingsSection(
            title = stringResource(R.string.settings_ai_models),
            description = stringResource(R.string.settings_ai_models_description)
        ) {
            val models = listOf(
                Triple(Icons.Default.AutoAwesome, stringResource(R.string.settings_whisper_model), stringResource(R.string.settings_whisper_size)),
                Triple(Icons.Default.Movie, stringResource(R.string.settings_segmentation_model), stringResource(R.string.settings_segmentation_size)),
                Triple(Icons.Default.Mic, stringResource(R.string.settings_piper_model), stringResource(R.string.settings_piper_size))
            )
            models.forEach { (icon, name, size) ->
                SettingsActionRow(
                    icon = icon,
                    accent = Mocha.Mauve,
                    label = name,
                    description = stringResource(R.string.settings_download_size_format, size),
                    actionLabel = stringResource(R.string.manage),
                    onClick = onNavigateToAiModels
                )
            }
        }

        // Editor
        SettingsSection(
            title = stringResource(R.string.settings_editor),
            description = stringResource(R.string.settings_editor_description)
        ) {
            SettingsDropdown(
                icon = Icons.Default.Tune,
                accent = Mocha.Mauve,
                label = stringResource(R.string.settings_default_mode),
                description = stringResource(R.string.settings_default_mode_description),
                value = settings.editorMode,
                options = listOf(stringResource(R.string.settings_mode_easy), stringResource(R.string.settings_mode_pro)),
                onSelected = { viewModel.setEditorMode(listOf("Easy", "Pro")[it]) }
            )
            SettingsToggle(
                icon = Icons.Default.TouchApp,
                accent = Mocha.Sapphire,
                label = stringResource(R.string.settings_haptic_feedback),
                description = stringResource(R.string.settings_haptic_desc),
                checked = settings.hapticEnabled,
                onChanged = { viewModel.setHapticEnabled(it) }
            )
            SettingsSwitch(
                icon = Icons.Default.Delete,
                accent = Mocha.Red,
                label = stringResource(R.string.settings_confirm_delete),
                description = stringResource(R.string.settings_confirm_delete_desc),
                checked = settings.confirmBeforeDelete,
                onChanged = { viewModel.setConfirmBeforeDelete(it) }
            )
            SettingsChoiceHeader(
                icon = Icons.Default.PhotoLibrary,
                accent = Mocha.Peach,
                label = stringResource(R.string.settings_thumbnail_cache),
                description = stringResource(R.string.settings_thumbnail_cache_description)
            )
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(64 to "64 MB", 128 to "128 MB", 256 to "256 MB").forEach { (size, label) ->
                    FilterChip(
                        selected = settings.thumbnailCacheSizeMb == size,
                        onClick = { viewModel.setThumbnailCacheSize(size) },
                        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Mocha.Peach.copy(alpha = 0.18f),
                            selectedLabelColor = Mocha.Peach,
                            containerColor = Mocha.PanelHighest,
                            labelColor = Mocha.Text
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = settings.thumbnailCacheSizeMb == size,
                            borderColor = Mocha.CardStroke,
                            selectedBorderColor = Mocha.Peach.copy(alpha = 0.32f)
                        )
                    )
                }
            }
            SettingsChoiceHeader(
                icon = Icons.Default.HighQuality,
                accent = Mocha.Yellow,
                label = stringResource(R.string.settings_export_quality),
                description = stringResource(R.string.settings_export_quality_description)
            )
            val qualityLabels = listOf(
                "LOW" to stringResource(R.string.settings_quality_small),
                "MEDIUM" to stringResource(R.string.settings_quality_balanced),
                "HIGH" to stringResource(R.string.settings_quality_best)
            )
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                qualityLabels.forEach { (key, label) ->
                    FilterChip(
                        selected = settings.defaultExportQuality == key,
                        onClick = { viewModel.setDefaultExportQuality(key) },
                        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Mocha.Yellow.copy(alpha = 0.18f),
                            selectedLabelColor = Mocha.Yellow,
                            containerColor = Mocha.PanelHighest,
                            labelColor = Mocha.Text
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = settings.defaultExportQuality == key,
                            borderColor = Mocha.CardStroke,
                            selectedBorderColor = Mocha.Yellow.copy(alpha = 0.32f)
                        )
                    )
                }
            }
        }

        // Tutorial
        var showResetConfirm by remember { mutableStateOf(false) }
        SettingsSection(
            title = stringResource(R.string.settings_tutorial),
            description = stringResource(R.string.settings_tutorial_description)
        ) {
            SettingsTile(
                icon = Icons.Default.School,
                accent = Mocha.Sapphire,
                label = stringResource(R.string.settings_reset_tutorial),
                description = stringResource(R.string.settings_reset_tutorial_row_description),
                onClick = { showResetConfirm = true }
            ) {
                NovaCutMetricPill(
                    text = stringResource(R.string.settings_reset_tutorial_action),
                    accent = Mocha.Sapphire
                )
            }
        }
        if (showResetConfirm) {
            ResetTutorialConfirmDialog(
                onDismissRequest = { showResetConfirm = false },
                onConfirm = {
                    viewModel.resetTutorial()
                    showResetConfirm = false
                }
            )
        }

        // About
        SettingsSection(
            title = stringResource(R.string.settings_about),
            description = stringResource(R.string.settings_about_description)
        ) {
            SettingsInfo(Icons.Default.Info, stringResource(R.string.settings_version), NovaCutApp.VERSION, Mocha.Sapphire)
            SettingsInfo(Icons.Default.Movie, stringResource(R.string.settings_engine), stringResource(R.string.settings_engine_value), Mocha.Peach)
            SettingsInfo(Icons.Default.AutoAwesome, stringResource(R.string.settings_ai_models), stringResource(R.string.settings_ai_models_value), Mocha.Mauve)
        }

        Spacer(Modifier.height(Spacing.xxl))
        }
    }
}

@Composable
private fun ResetTutorialConfirmDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        icon = {
            Surface(
                color = Mocha.Sapphire.copy(alpha = 0.14f),
                shape = RoundedCornerShape(Radius.lg),
                border = androidx.compose.foundation.BorderStroke(1.dp, Mocha.Sapphire.copy(alpha = 0.24f))
            ) {
                Icon(
                    imageVector = Icons.Default.School,
                    contentDescription = null,
                    tint = Mocha.Sapphire,
                    modifier = Modifier
                        .padding(Spacing.md)
                        .size(22.dp)
                )
            }
        },
        title = {
            Text(
                text = stringResource(R.string.settings_reset_tutorial_confirm_title),
                color = Mocha.Text,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Text(
                text = stringResource(R.string.settings_reset_tutorial_confirm),
                color = Mocha.Subtext0,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            NovaCutPrimaryButton(
                text = stringResource(R.string.settings_reset_tutorial_action),
                onClick = onConfirm,
                icon = Icons.Default.Check
            )
        },
        dismissButton = {
            NovaCutSecondaryButton(
                text = stringResource(R.string.cancel),
                onClick = onDismissRequest
            )
        },
        containerColor = Mocha.PanelHighest,
        titleContentColor = Mocha.Text,
        textContentColor = Mocha.Subtext0,
        shape = RoundedCornerShape(Radius.xxl)
    )
}

@OptIn(ExperimentalLayoutApi::class)
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
        NovaCutHeroCard(
            accent = Mocha.Sapphire,
            shape = RoundedCornerShape(Radius.xxl)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NovaCutChromeIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    onClick = onBack
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.settings_title),
                        color = Mocha.Text,
                        style = MaterialTheme.typography.headlineLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.settings_subtitle),
                        color = Mocha.Subtext0,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SettingsOverviewStat(
                    label = stringResource(R.string.settings_editor),
                    value = settings.editorMode,
                    accent = Mocha.Mauve,
                    modifier = Modifier.widthIn(min = 132.dp)
                )
                SettingsOverviewStat(
                    label = stringResource(R.string.settings_auto_save),
                    value = if (settings.autoSaveEnabled) "${settings.autoSaveIntervalSec}s" else stringResource(R.string.settings_off),
                    accent = Mocha.Sapphire,
                    modifier = Modifier.widthIn(min = 132.dp)
                )
                SettingsOverviewStat(
                    label = stringResource(R.string.settings_ai_models),
                    value = stringResource(R.string.manage),
                    accent = Mocha.Rosewater,
                    modifier = Modifier
                        .widthIn(min = 132.dp)
                        .clickable(role = Role.Button, onClick = onManageAiModels)
                )
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
        shape = RoundedCornerShape(Radius.xl),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.18f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                color = accent,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                color = Mocha.Text,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    description: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm)) {
        NovaCutSectionHeader(
            title = title,
            description = description,
            modifier = Modifier.padding(bottom = Spacing.sm)
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = Mocha.Panel),
            shape = RoundedCornerShape(Radius.xl),
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
                    modifier = Modifier
                        .animateContentSize()
                        .padding(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    content = content
                )
            }
        }
    }
}

@Composable
private fun SettingsDropdown(
    icon: ImageVector,
    accent: androidx.compose.ui.graphics.Color,
    label: String,
    description: String? = null,
    value: String,
    options: List<String>,
    onSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        SettingsTile(
            icon = icon,
            accent = accent,
            label = label,
            description = description,
            onClick = { expanded = true }
        ) {
            Text(value, color = Mocha.Subtext0, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Default.ArrowDropDown,
                stringResource(R.string.cd_dropdown),
                tint = Mocha.Subtext0,
                modifier = Modifier.size(18.dp)
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = Mocha.PanelHighest
        ) {
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
    icon: ImageVector,
    accent: androidx.compose.ui.graphics.Color,
    label: String,
    description: String,
    checked: Boolean,
    onChanged: (Boolean) -> Unit
) {
    SettingsSwitchTile(icon, accent, label, description, checked, onChanged)
}

@Composable
private fun SettingsSwitch(
    icon: ImageVector,
    accent: androidx.compose.ui.graphics.Color,
    label: String,
    description: String,
    checked: Boolean,
    onChanged: (Boolean) -> Unit
) {
    SettingsSwitchTile(icon, accent, label, description, checked, onChanged)
}

@Composable
private fun SettingsSlider(
    icon: ImageVector,
    accent: androidx.compose.ui.graphics.Color,
    label: String,
    description: String? = null,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueLabel: String,
    onChanged: (Float) -> Unit
) {
    // Hold a local in-flight slider value so the thumb tracks the drag smoothly without
    // calling the ViewModel (and writing to DataStore) on every tick of the gesture.
    // Only commit the final value on drag end. `value` (the canonical settings value)
    // is the key on remember so external changes still propagate.
    var localValue by remember(value) { mutableStateOf(value) }
    Surface(
        color = Mocha.PanelHighest,
        shape = RoundedCornerShape(Radius.lg),
        border = androidx.compose.foundation.BorderStroke(1.dp, Mocha.CardStroke.copy(alpha = 0.9f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    SettingsTileIcon(icon = icon, accent = accent)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            label,
                            color = Mocha.Text,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        description?.let {
                            Text(
                                it,
                                color = Mocha.Subtext0,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    valueLabel,
                    color = accent,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Slider(
                value = localValue,
                onValueChange = { localValue = it },
                onValueChangeFinished = { onChanged(localValue) },
                valueRange = range,
                colors = SliderDefaults.colors(
                    thumbColor = accent,
                    activeTrackColor = accent,
                    inactiveTrackColor = Mocha.Surface1
                )
            )
        }
    }
}

@Composable
private fun SettingsInfo(
    icon: ImageVector,
    label: String,
    value: String,
    accent: androidx.compose.ui.graphics.Color
) {
    SettingsTile(
        icon = icon,
        accent = accent,
        label = label
    ) {
        Text(
            text = value,
            color = Mocha.Subtext0,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    accent: androidx.compose.ui.graphics.Color,
    label: String,
    description: String,
    actionLabel: String,
    onClick: () -> Unit
) {
    SettingsTile(
        icon = icon,
        accent = accent,
        label = label,
        description = description,
        onClick = onClick
    ) {
        NovaCutMetricPill(
            text = actionLabel,
            accent = accent
        )
    }
}

@Composable
private fun SettingsChoiceHeader(
    icon: ImageVector,
    accent: androidx.compose.ui.graphics.Color,
    label: String,
    description: String
) {
    Row(
        modifier = Modifier.padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.Top
    ) {
        SettingsTileIcon(icon = icon, accent = accent)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                label,
                color = Mocha.Text,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                description,
                color = Mocha.Subtext0,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SettingsSwitchTile(
    icon: ImageVector,
    accent: androidx.compose.ui.graphics.Color,
    label: String,
    description: String,
    checked: Boolean,
    onChanged: (Boolean) -> Unit
) {
    val switchState = stringResource(if (checked) R.string.settings_on else R.string.settings_off)

    SettingsTile(
        icon = icon,
        accent = accent,
        label = label,
        description = description,
        onClick = { onChanged(!checked) }
    ) {
        Switch(
            checked = checked,
            onCheckedChange = onChanged,
            modifier = Modifier.semantics {
                contentDescription = label
                stateDescription = switchState
            },
            colors = SwitchDefaults.colors(
                checkedTrackColor = accent.copy(alpha = 0.8f),
                checkedThumbColor = Mocha.Crust,
                uncheckedTrackColor = Mocha.Surface1,
                uncheckedThumbColor = Mocha.Subtext0
            )
        )
    }
}

@Composable
private fun SettingsTile(
    icon: ImageVector,
    accent: androidx.compose.ui.graphics.Color,
    label: String,
    description: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit
) {
    Surface(
        color = Mocha.PanelHighest,
        shape = RoundedCornerShape(Radius.lg),
        border = androidx.compose.foundation.BorderStroke(1.dp, Mocha.CardStroke.copy(alpha = 0.9f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 72.dp)
                .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
                .padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingsTileIcon(icon = icon, accent = accent)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    label,
                    color = Mocha.Text,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                description?.let {
                    Text(
                        it,
                        color = Mocha.Subtext0,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = trailing
            )
        }
    }
}

@Composable
private fun SettingsTileIcon(
    icon: ImageVector,
    accent: androidx.compose.ui.graphics.Color
) {
    Surface(
        color = accent.copy(alpha = 0.14f),
        shape = RoundedCornerShape(Radius.md),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.2f))
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier
                .padding(10.dp)
                .size(18.dp)
        )
    }
}

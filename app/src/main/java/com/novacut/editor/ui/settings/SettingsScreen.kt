package com.novacut.editor.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import com.novacut.editor.NovaCutApp
import com.novacut.editor.R
import com.novacut.editor.engine.AppearanceMode
import com.novacut.editor.engine.AppSettings
import com.novacut.editor.engine.ProjectColorPolicy
import com.novacut.editor.engine.segmentation.SegmentationModelState
import com.novacut.editor.engine.whisper.WhisperModelState
import com.novacut.editor.model.*
import com.novacut.editor.ui.NovaCutTestTags
import com.novacut.editor.ui.theme.Mocha
import com.novacut.editor.ui.theme.NovaCutChromeIconButton
import com.novacut.editor.ui.theme.NovaCutDialogIcon
import com.novacut.editor.ui.theme.NovaCutFilterChip
import com.novacut.editor.ui.theme.NovaCutHeroCard
import com.novacut.editor.ui.theme.LocalNovaCutColors
import com.novacut.editor.ui.theme.NovaCutMetricPill
import com.novacut.editor.ui.theme.NovaCutPrimaryButton
import com.novacut.editor.ui.theme.NovaCutScreenBackground
import com.novacut.editor.ui.theme.NovaCutSectionHeader
import com.novacut.editor.ui.theme.NovaCutSecondaryButton
import com.novacut.editor.ui.theme.Radius
import com.novacut.editor.ui.theme.Spacing
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

private enum class SettingsAiModelRemovalTarget {
    WHISPER,
    SEGMENTATION
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    var aiModelsScrollY by remember { mutableIntStateOf(0) }
    val bringAiModelsIntoView: () -> Unit = {
        coroutineScope.launch {
            scrollState.animateScrollTo((aiModelsScrollY - 24).coerceAtLeast(0))
        }
    }
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val aiModelStorage by viewModel.aiModelStorage.collectAsStateWithLifecycle()
    val diagnosticExport by viewModel.diagnosticExport.collectAsStateWithLifecycle()
    val settingsResetNotice by viewModel.settingsResetNotice.collectAsStateWithLifecycle()
    val whisperModelState by viewModel.whisperModelState.collectAsStateWithLifecycle()
    val segmentationModelState by viewModel.segmentationModelState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val canRemoveWhisperModel = whisperModelState == WhisperModelState.READY && aiModelStorage.whisperBytes > 0L
    val canRemoveSegmentationModel = segmentationModelState == SegmentationModelState.READY && aiModelStorage.segmentationBytes > 0L
    var pendingAiModelRemoval by remember { mutableStateOf<SettingsAiModelRemovalTarget?>(null) }
    var showPrivacyDashboard by remember { mutableStateOf(false) }
    var notificationStatusRefreshKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        viewModel.refreshAiModelStorage()
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationStatusRefreshKey += 1
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    NovaCutScreenBackground(
        modifier = modifier
            .fillMaxSize()
            .testTag(NovaCutTestTags.SETTINGS_SCREEN)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
        SettingsHero(
            settings = settings,
            onBack = onBack,
            onManageAiModels = bringAiModelsIntoView
        )

        aiModelStorage.feedbackMessage?.let { message ->
            SettingsFeedbackBanner(
                message = message,
                onDismiss = viewModel::dismissAiModelStorageFeedback
            )
        }
        (diagnosticExport.message ?: diagnosticExport.errorMessage)?.let { message ->
            SettingsFeedbackBanner(
                message = message,
                isError = diagnosticExport.errorMessage != null,
                onDismiss = viewModel::dismissDiagnosticExportMessage
            )
        }
        settingsResetNotice?.let {
            SettingsFeedbackBanner(
                message = stringResource(R.string.settings_reset_notice_message),
                accentOverride = Mocha.Peach,
                iconOverride = Icons.Default.Info,
                onDismiss = viewModel::dismissSettingsResetNotice
            )
        }

        Spacer(Modifier.height(10.dp))

        // Export Defaults
        val projectColorPolicy = ProjectColorPolicy.DEFAULT
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
            SettingsTile(
                icon = Icons.Default.Palette,
                accent = Mocha.Teal,
                label = stringResource(R.string.settings_project_color_policy),
                description = stringResource(R.string.settings_project_color_policy_description)
            ) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    SettingsStatusBadge(
                        text = projectColorPolicy.workingColorSpace.displayName,
                        accent = Mocha.Teal
                    )
                    SettingsStatusBadge(
                        text = projectColorPolicy.displayTransform.displayName,
                        accent = Mocha.Sapphire
                    )
                }
            }
        }

        // Export Notifications
        SettingsSection(
            title = stringResource(R.string.settings_export_notifications),
            description = stringResource(R.string.settings_export_notifications_description)
        ) {
            SettingsNotificationPermissionRow(
                context = context,
                refreshKey = notificationStatusRefreshKey
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
                    NovaCutFilterChip(
                        selected = settings.defaultTrackHeight == height,
                        onClick = { viewModel.setDefaultTrackHeight(height) },
                        text = "${height}dp",
                        accent = Mocha.Teal,
                        icon = if (settings.defaultTrackHeight == height) Icons.Default.Check else null
                    )
                }
            }
        }

        // AI Models
        Box(
            modifier = Modifier.onGloballyPositioned { coordinates ->
                aiModelsScrollY = (coordinates.positionInRoot().y + scrollState.value).roundToInt()
            }
        ) {
            SettingsSection(
                title = stringResource(R.string.settings_ai_models),
                description = stringResource(R.string.settings_ai_models_description)
            ) {
                SettingsSwitch(
                    icon = Icons.Default.Wifi,
                    accent = Mocha.Sapphire,
                    label = stringResource(R.string.settings_ai_wifi_only),
                    description = stringResource(R.string.settings_ai_wifi_only_description),
                    checked = settings.aiModelWifiOnly,
                    onChanged = viewModel::setAiModelWifiOnly
                )
                SettingsStorageOverview(
                    totalBytes = aiModelStorage.totalBytes,
                    whisperBytes = aiModelStorage.whisperBytes,
                    segmentationBytes = aiModelStorage.segmentationBytes
                )
                SettingsAiModelRow(
                    icon = Icons.Default.RecordVoiceOver,
                    accent = Mocha.Mauve,
                    label = stringResource(R.string.settings_whisper_model),
                    description = stringResource(R.string.ai_whisper_description),
                    stateLabel = whisperModelState.displayLabel(),
                    storageLabel = modelStorageLabel(aiModelStorage.whisperBytes, stringResource(R.string.settings_whisper_size)),
                    canRemove = canRemoveWhisperModel,
                    isError = whisperModelState == WhisperModelState.ERROR,
                    isBusy = aiModelStorage.isRemovingWhisper || whisperModelState == WhisperModelState.DOWNLOADING,
                    actionLabel = if (canRemoveWhisperModel) {
                        stringResource(R.string.remove)
                    } else {
                        stringResource(R.string.download)
                    },
                    actionIcon = if (canRemoveWhisperModel) {
                        Icons.Default.Delete
                    } else {
                        Icons.Default.Download
                    },
                    onAction = if (canRemoveWhisperModel) {
                        { pendingAiModelRemoval = SettingsAiModelRemovalTarget.WHISPER }
                    } else {
                        viewModel::downloadWhisperModel
                    }
                )
                SettingsAiModelRow(
                    icon = Icons.Default.PersonOff,
                    accent = Mocha.Green,
                    label = stringResource(R.string.settings_segmentation_model),
                    description = stringResource(R.string.ai_segmentation_description),
                    stateLabel = segmentationModelState.displayLabel(),
                    storageLabel = modelStorageLabel(aiModelStorage.segmentationBytes, stringResource(R.string.settings_segmentation_size)),
                    canRemove = canRemoveSegmentationModel,
                    isError = segmentationModelState == SegmentationModelState.ERROR,
                    isBusy = aiModelStorage.isRemovingSegmentation || segmentationModelState == SegmentationModelState.DOWNLOADING,
                    actionLabel = if (canRemoveSegmentationModel) {
                        stringResource(R.string.remove)
                    } else {
                        stringResource(R.string.download)
                    },
                    actionIcon = if (canRemoveSegmentationModel) {
                        Icons.Default.Delete
                    } else {
                        Icons.Default.Download
                    },
                    onAction = if (canRemoveSegmentationModel) {
                        { pendingAiModelRemoval = SettingsAiModelRemovalTarget.SEGMENTATION }
                    } else {
                        viewModel::downloadSegmentationModel
                    }
                )
                SettingsTile(
                    icon = Icons.Default.Mic,
                    accent = Mocha.Peach,
                    label = stringResource(R.string.settings_piper_model),
                    description = stringResource(R.string.settings_piper_system_voice_description)
                ) {
                    SettingsStatusBadge(
                        text = stringResource(R.string.settings_piper_system_voice_status),
                        accent = Mocha.Peach
                    )
                }
            }
        }

        // Appearance
        SettingsSection(
            title = stringResource(R.string.settings_appearance),
            description = stringResource(R.string.settings_appearance_description)
        ) {
            val appearanceModes = AppearanceMode.entries
            SettingsDropdown(
                icon = Icons.Default.Contrast,
                accent = Mocha.Sky,
                label = stringResource(R.string.settings_appearance_mode),
                description = stringResource(R.string.settings_appearance_mode_description),
                value = settings.appearanceMode.displayLabel(),
                options = appearanceModes.map { it.displayLabel() },
                onSelected = { index -> viewModel.setAppearanceMode(appearanceModes[index]) }
            )
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
                    NovaCutFilterChip(
                        selected = settings.thumbnailCacheSizeMb == size,
                        onClick = { viewModel.setThumbnailCacheSize(size) },
                        text = label,
                        accent = Mocha.Peach,
                        icon = if (settings.thumbnailCacheSizeMb == size) Icons.Default.Check else null
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
                    NovaCutFilterChip(
                        selected = settings.defaultExportQuality == key,
                        onClick = { viewModel.setDefaultExportQuality(key) },
                        text = label,
                        accent = Mocha.Yellow,
                        icon = if (settings.defaultExportQuality == key) Icons.Default.Check else null
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

        // Diagnostics
        SettingsSection(
            title = stringResource(R.string.settings_diagnostics),
            description = stringResource(R.string.settings_diagnostics_description)
        ) {
            SettingsSwitch(
                icon = Icons.Default.ViewStream,
                accent = Mocha.Teal,
                label = stringResource(R.string.settings_diagnostic_timeline_shape),
                description = stringResource(R.string.settings_diagnostic_timeline_shape_description),
                checked = settings.includeDiagnosticTimelineShape,
                onChanged = viewModel::setIncludeDiagnosticTimelineShape
            )
            SettingsDiagnosticExportRow(
                state = diagnosticExport,
                onExport = viewModel::exportDiagnosticBundle,
                onShare = { bundle ->
                    shareDiagnosticBundle(
                        context = context,
                        bundle = bundle,
                        onFailure = viewModel::reportDiagnosticShareFailure
                    )
                }
            )
        }

        // Privacy (R5.5c UI) — opens the PrivacyDashboardPanel in a dialog.
        // Engine helpers (groupForDisplay / controlSummary) are pure so the
        // panel re-renders without any view-model state today.
        SettingsSection(
            title = stringResource(R.string.settings_privacy_section_title),
            description = stringResource(R.string.settings_privacy_section_description)
        ) {
            SettingsActionRow(
                icon = Icons.Default.Shield,
                accent = Mocha.Mauve,
                label = stringResource(R.string.settings_privacy_open_label),
                description = stringResource(R.string.settings_privacy_open_description),
                actionLabel = stringResource(R.string.settings_privacy_open_action),
                onClick = { showPrivacyDashboard = true },
                modifier = Modifier.testTag(NovaCutTestTags.SETTINGS_PRIVACY_OPEN)
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

        pendingAiModelRemoval?.let { target ->
            SettingsAiModelRemovalConfirmDialog(
                target = target,
                storageLabel = when (target) {
                    SettingsAiModelRemovalTarget.WHISPER -> formatStorageBytes(aiModelStorage.whisperBytes)
                    SettingsAiModelRemovalTarget.SEGMENTATION -> formatStorageBytes(aiModelStorage.segmentationBytes)
                },
                onDismissRequest = { pendingAiModelRemoval = null },
                onConfirm = {
                    when (target) {
                        SettingsAiModelRemovalTarget.WHISPER -> viewModel.removeWhisperModel()
                        SettingsAiModelRemovalTarget.SEGMENTATION -> viewModel.removeSegmentationModel()
                    }
                    pendingAiModelRemoval = null
                }
            )
        }

        if (showPrivacyDashboard) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { showPrivacyDashboard = false }
            ) {
                androidx.compose.material3.Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(Radius.xxl),
                    color = Mocha.PanelHighest,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 640.dp)
                        .testTag(NovaCutTestTags.SETTINGS_PRIVACY_DASHBOARD)
                ) {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        PrivacyDashboardPanel()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            NovaCutSecondaryButton(
                                text = stringResource(R.string.settings_privacy_close),
                                onClick = { showPrivacyDashboard = false },
                                modifier = Modifier.testTag(NovaCutTestTags.SETTINGS_PRIVACY_CLOSE)
                            )
                        }
                    }
                }
            }
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
            NovaCutDialogIcon(
                icon = Icons.Default.School,
                accent = Mocha.Sapphire
            )
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

@Composable
private fun SettingsAiModelRemovalConfirmDialog(
    target: SettingsAiModelRemovalTarget,
    storageLabel: String,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit
) {
    val title = when (target) {
        SettingsAiModelRemovalTarget.WHISPER -> stringResource(R.string.ai_remove_whisper_title)
        SettingsAiModelRemovalTarget.SEGMENTATION -> stringResource(R.string.ai_remove_segmentation_title)
    }
    val body = when (target) {
        SettingsAiModelRemovalTarget.WHISPER -> stringResource(R.string.settings_remove_whisper_model_message, storageLabel)
        SettingsAiModelRemovalTarget.SEGMENTATION -> stringResource(R.string.settings_remove_segmentation_model_message, storageLabel)
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        icon = {
            NovaCutDialogIcon(
                icon = Icons.Default.Delete,
                accent = Mocha.Red
            )
        },
        title = {
            Text(
                text = title,
                color = Mocha.Text,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Text(
                text = body,
                color = Mocha.Subtext0,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            NovaCutSecondaryButton(
                text = stringResource(R.string.ai_model_remove_confirm),
                onClick = onConfirm,
                icon = Icons.Default.Delete,
                contentColor = Mocha.Red
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
                    onClick = onBack,
                    modifier = Modifier.testTag(NovaCutTestTags.SETTINGS_BACK)
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
private fun SettingsFeedbackBanner(
    message: String,
    isError: Boolean = false,
    accentOverride: androidx.compose.ui.graphics.Color? = null,
    iconOverride: ImageVector? = null,
    onDismiss: () -> Unit
) {
    val accent = accentOverride ?: if (isError) Mocha.Red else Mocha.Green
    val icon = iconOverride ?: if (isError) Icons.Default.Error else Icons.Default.CheckCircle
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.xs)
            .semantics { liveRegion = LiveRegionMode.Polite },
        color = accent.copy(alpha = 0.10f),
        shape = RoundedCornerShape(Radius.lg),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.22f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingsTileIcon(icon = icon, accent = accent)
            Text(
                text = message,
                color = Mocha.Text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.close),
                    tint = Mocha.Subtext0
                )
            }
        }
    }
}

@Composable
private fun SettingsDiagnosticExportRow(
    state: DiagnosticExportUiState,
    onExport: () -> Unit,
    onShare: (DiagnosticExportBundleUi) -> Unit
) {
    SettingsTile(
        icon = Icons.Default.ReportProblem,
        accent = Mocha.Sapphire,
        label = stringResource(R.string.settings_diagnostic_export),
        description = stringResource(R.string.settings_diagnostic_export_description)
    ) {
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            when {
                state.isExporting -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Mocha.Sapphire,
                            strokeWidth = 2.dp
                        )
                        SettingsStatusBadge(
                            text = stringResource(R.string.settings_diagnostic_exporting),
                            accent = Mocha.Sapphire
                        )
                    }
                }
                state.bundle != null -> {
                    SettingsStatusBadge(
                        text = stringResource(R.string.settings_diagnostic_saved),
                        accent = Mocha.Green
                    )
                    Text(
                        text = stringResource(
                            R.string.settings_diagnostic_file_format,
                            state.bundle.fileName,
                            formatStorageBytes(state.bundle.sizeBytes)
                        ),
                        color = Mocha.Subtext0,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.widthIn(max = 190.dp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        NovaCutSecondaryButton(
                            text = stringResource(R.string.settings_diagnostic_share),
                            onClick = { onShare(state.bundle) },
                            icon = Icons.Default.Share,
                            contentColor = Mocha.Green
                        )
                        NovaCutSecondaryButton(
                            text = stringResource(R.string.settings_diagnostic_rebuild),
                            onClick = onExport,
                            icon = Icons.Default.Refresh,
                            enabled = !state.isExporting,
                            contentColor = Mocha.Sapphire
                        )
                    }
                }
                else -> {
                    NovaCutSecondaryButton(
                        text = stringResource(R.string.settings_diagnostic_export_action),
                        onClick = onExport,
                        icon = Icons.Default.Save,
                        contentColor = Mocha.Sapphire
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsNotificationPermissionRow(
    context: Context,
    refreshKey: Int
) {
    val runtimePermissionRequired = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val runtimePermissionGranted = remember(context, refreshKey) {
        !runtimePermissionRequired ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
    }
    val appNotificationsEnabled = remember(context, refreshKey) {
        NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
    val status = when {
        !runtimePermissionRequired -> NotificationPermissionSettingsStatus.NotRequired
        runtimePermissionGranted && appNotificationsEnabled -> NotificationPermissionSettingsStatus.Enabled
        else -> NotificationPermissionSettingsStatus.Off
    }
    val badgeText = stringResource(status.badgeResId)
    val description = stringResource(status.descriptionResId)

    SettingsTile(
        icon = if (status == NotificationPermissionSettingsStatus.Off) {
            Icons.Default.NotificationsOff
        } else {
            Icons.Default.NotificationsActive
        },
        accent = status.accent,
        label = stringResource(R.string.settings_export_notifications_row),
        description = description,
        onClick = { openAppNotificationSettings(context) },
        semanticState = badgeText
    ) {
        SettingsStatusBadge(
            text = badgeText,
            accent = status.accent
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = stringResource(R.string.settings_export_notifications_open),
            tint = Mocha.Subtext0,
            modifier = Modifier.size(18.dp)
        )
    }
}

private enum class NotificationPermissionSettingsStatus(
    val badgeResId: Int,
    val descriptionResId: Int,
    val accent: androidx.compose.ui.graphics.Color
) {
    Enabled(
        badgeResId = R.string.settings_export_notifications_enabled,
        descriptionResId = R.string.settings_export_notifications_enabled_description,
        accent = Mocha.Green
    ),
    Off(
        badgeResId = R.string.settings_export_notifications_off,
        descriptionResId = R.string.settings_export_notifications_off_description,
        accent = Mocha.Yellow
    ),
    NotRequired(
        badgeResId = R.string.settings_export_notifications_not_required,
        descriptionResId = R.string.settings_export_notifications_not_required_description,
        accent = Mocha.Sapphire
    )
}

private fun openAppNotificationSettings(context: Context) {
    val notificationIntent = Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName)
    runCatching {
        context.startActivity(notificationIntent)
    }.onFailure {
        val fallbackIntent = Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.parse("package:${context.packageName}"))
        context.startActivity(fallbackIntent)
    }
}

@Composable
private fun SettingsStorageOverview(
    totalBytes: Long,
    whisperBytes: Long,
    segmentationBytes: Long
) {
    SettingsTile(
        icon = Icons.Default.Storage,
        accent = Mocha.Rosewater,
        label = stringResource(R.string.settings_ai_storage_title),
        description = stringResource(
            R.string.settings_ai_storage_description,
            formatStorageBytes(whisperBytes),
            formatStorageBytes(segmentationBytes)
        )
    ) {
        SettingsStatusBadge(
            text = formatStorageBytes(totalBytes),
            accent = if (totalBytes > 0L) Mocha.Rosewater else Mocha.Overlay0
        )
    }
}

private fun shareDiagnosticBundle(
    context: Context,
    bundle: DiagnosticExportBundleUi,
    onFailure: () -> Unit
) {
    val file = File(bundle.path)
    if (!file.isFile) {
        onFailure()
        return
    }
    runCatching {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(
                shareIntent,
                context.getString(R.string.settings_diagnostic_share_chooser)
            )
        )
    }.onFailure { onFailure() }
}

@Composable
private fun SettingsAiModelRow(
    icon: ImageVector,
    accent: androidx.compose.ui.graphics.Color,
    label: String,
    description: String,
    stateLabel: String,
    storageLabel: String,
    canRemove: Boolean,
    isError: Boolean,
    isBusy: Boolean,
    actionLabel: String,
    actionIcon: ImageVector,
    onAction: () -> Unit
) {
    SettingsTile(
        icon = icon,
        accent = accent,
        label = label,
        description = stringResource(R.string.settings_model_description_with_size, description, storageLabel)
    ) {
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = accent,
                        strokeWidth = 2.dp
                    )
                }
                SettingsStatusBadge(
                    text = stateLabel,
                    accent = when {
                        canRemove -> Mocha.Green
                        isError -> Mocha.Red
                        isBusy -> Mocha.Sapphire
                        else -> Mocha.Overlay1
                    }
                )
            }
            NovaCutSecondaryButton(
                text = actionLabel,
                onClick = onAction,
                enabled = !isBusy,
                contentColor = if (canRemove) Mocha.Red else accent,
                icon = actionIcon
            )
        }
    }
}

@Composable
private fun SettingsStatusBadge(
    text: String,
    accent: androidx.compose.ui.graphics.Color
) {
    Surface(
        color = accent.copy(alpha = 0.12f),
        shape = RoundedCornerShape(Radius.sm),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.22f))
    ) {
        Text(
            text = text,
            color = accent,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun WhisperModelState.displayLabel(): String = when (this) {
    WhisperModelState.READY -> stringResource(R.string.settings_model_installed)
    WhisperModelState.DOWNLOADING -> stringResource(R.string.settings_model_downloading)
    WhisperModelState.ERROR -> stringResource(R.string.settings_model_error)
    WhisperModelState.NOT_DOWNLOADED -> stringResource(R.string.settings_model_not_installed)
}

@Composable
private fun SegmentationModelState.displayLabel(): String = when (this) {
    SegmentationModelState.READY -> stringResource(R.string.settings_model_installed)
    SegmentationModelState.DOWNLOADING -> stringResource(R.string.settings_model_downloading)
    SegmentationModelState.ERROR -> stringResource(R.string.settings_model_error)
    SegmentationModelState.NOT_DOWNLOADED -> stringResource(R.string.settings_model_not_installed)
}

@Composable
private fun AppearanceMode.displayLabel(): String = when (this) {
    AppearanceMode.SYSTEM -> stringResource(R.string.settings_appearance_system)
    AppearanceMode.DARK -> stringResource(R.string.settings_appearance_dark)
    AppearanceMode.HIGH_CONTRAST_DARK -> stringResource(R.string.settings_appearance_high_contrast)
}

@Composable
private fun modelStorageLabel(bytes: Long, downloadSize: String): String {
    return if (bytes > 0L) {
        stringResource(R.string.settings_installed_size_format, formatStorageBytes(bytes))
    } else {
        stringResource(R.string.settings_download_size_format, downloadSize)
    }
}

@Composable
private fun SettingsSection(
    title: String,
    description: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LocalNovaCutColors.current
    Column(modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm)) {
        NovaCutSectionHeader(
            title = title,
            description = description,
            modifier = Modifier.padding(bottom = Spacing.sm)
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = colors.panel),
            shape = RoundedCornerShape(Radius.xl),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (colors.highContrast) colors.cardStrokeStrong else colors.cardStroke.copy(alpha = 0.85f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier.background(
                    Brush.verticalGradient(
                        listOf(
                            colors.panelHighest.copy(alpha = 0.78f),
                            colors.panel
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
    val colors = LocalNovaCutColors.current
    var expanded by remember { mutableStateOf(false) }
    Box {
        SettingsTile(
            icon = icon,
            accent = accent,
            label = label,
            description = description,
            onClick = { expanded = true }
        ) {
            Text(value, color = colors.subtext, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Default.ArrowDropDown,
                stringResource(R.string.cd_dropdown),
                tint = colors.subtext,
                modifier = Modifier.size(18.dp)
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = colors.panelHighest
        ) {
            options.forEachIndexed { idx, opt ->
                DropdownMenuItem(
                    text = { Text(opt, style = MaterialTheme.typography.bodyMedium) },
                    trailingIcon = if (opt == value) {
                        {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Mocha.Rosewater,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else {
                        null
                    },
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    SettingsTile(
        icon = icon,
        accent = accent,
        label = label,
        description = description,
        onClick = onClick,
        modifier = modifier
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
        onClick = { onChanged(!checked) },
        role = Role.Switch,
        semanticState = switchState
    ) {
        Switch(
            checked = checked,
            onCheckedChange = null,
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
    modifier: Modifier = Modifier,
    role: Role = Role.Button,
    semanticState: String? = null,
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
                .then(modifier)
                .defaultMinSize(minHeight = 72.dp)
                .then(if (onClick != null) Modifier.clickable(role = role, onClick = onClick) else Modifier)
                .then(
                    if (semanticState != null) {
                        Modifier.semantics { stateDescription = semanticState }
                    } else {
                        Modifier
                    }
                )
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

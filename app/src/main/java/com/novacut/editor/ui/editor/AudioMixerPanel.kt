package com.novacut.editor.ui.editor

import com.novacut.editor.ui.theme.ClearCutAccents
import com.novacut.editor.ui.theme.LocalClearCutColors
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.novacut.editor.R
import com.novacut.editor.model.AudioEffect
import com.novacut.editor.model.AudioEffectType
import com.novacut.editor.model.Track
import com.novacut.editor.model.TrackType

private const val AUDIO_ROUTING_RENDERER_AVAILABLE = true

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AudioMixerPanel(
    tracks: List<Track>,
    onTrackVolumeChanged: (String, Float) -> Unit,
    onVolumeDragStarted: () -> Unit,
    onVolumeDragEnded: () -> Unit,
    onTrackPanChanged: (String, Float) -> Unit,
    onPanDragStarted: () -> Unit,
    onPanDragEnded: () -> Unit,
    onTrackMuteToggled: (String) -> Unit,
    onTrackSoloToggled: (String) -> Unit,
    onTrackAudioEffectAdded: (String, AudioEffectType) -> Unit,
    onTrackAudioEffectRemoved: (String, String) -> Unit,
    onTrackAudioEffectParamChanged: (String, String, String, Float) -> Unit,
    vuLevels: Map<String, Pair<Float, Float>>,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semanticColors = LocalClearCutColors.current
    var selectedEffectTrack by remember { mutableStateOf<String?>(null) }
    var selectedEffectId by remember { mutableStateOf<String?>(null) }
    val selectedTrack = tracks.find { it.id == selectedEffectTrack }
    val activeEffects = tracks.sumOf { it.audioEffects.size }

    PremiumEditorPanel(
        title = androidx.compose.ui.res.stringResource(R.string.panel_audio_mixer_title),
        subtitle = androidx.compose.ui.res.stringResource(R.string.panel_audio_mixer_subtitle),
        icon = Icons.Default.Tune,
        accent = ClearCutAccents.Sapphire,
        onClose = onClose,
        closeContentDescription = androidx.compose.ui.res.stringResource(R.string.cd_close_audio_panel),
        modifier = modifier,
        scrollable = true
    ) {
        PremiumPanelCard(accent = ClearCutAccents.Sapphire) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = androidx.compose.ui.res.stringResource(R.string.mixer_session_overview),
                        style = MaterialTheme.typography.titleMedium,
                        color = semanticColors.text
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (selectedTrack != null) {
                            androidx.compose.ui.res.stringResource(
                                R.string.mixer_selected_track_description,
                                androidx.compose.ui.res.stringResource(selectedTrack.type.displayNameRes()),
                                selectedTrack.index + 1,
                            )
                        } else {
                            androidx.compose.ui.res.stringResource(R.string.mixer_empty_selection_description)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = semanticColors.subtext
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    selectedTrack?.let { track ->
                        PremiumPanelPill(
                            text = androidx.compose.ui.res.stringResource(
                                R.string.mixer_track_selected,
                                track.trackLabel(),
                            ),
                            accent = track.type.mixerAccent()
                        )
                    }
                    PremiumPanelPill(
                        text = androidx.compose.ui.res.pluralStringResource(
                            R.plurals.mixer_tracks_live,
                            tracks.size,
                            tracks.size,
                        ),
                        accent = ClearCutAccents.Sapphire
                    )
                    if (AUDIO_ROUTING_RENDERER_AVAILABLE) {
                        PremiumPanelPill(
                            text = androidx.compose.ui.res.pluralStringResource(
                                R.plurals.mixer_fx_staged,
                                activeEffects,
                                activeEffects,
                            ),
                            accent = if (activeEffects > 0) ClearCutAccents.Mauve else semanticColors.overlayStrong
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        PremiumPanelCard(
            accent = ClearCutAccents.Blue
        ) {
            Text(
                text = androidx.compose.ui.res.stringResource(R.string.mixer_channel_strips),
                style = MaterialTheme.typography.titleMedium,
                color = semanticColors.text
            )
            Text(
                text = androidx.compose.ui.res.stringResource(R.string.mixer_channel_strips_description),
                style = MaterialTheme.typography.bodyMedium,
                color = semanticColors.subtext
            )

            Spacer(modifier = Modifier.height(12.dp))

            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(404.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(tracks, key = { it.id }) { track ->
                    ChannelStrip(
                        track = track,
                        vuLevel = vuLevels[track.id] ?: (0f to 0f),
                        onVolumeChanged = { onTrackVolumeChanged(track.id, it) },
                        onVolumeDragStarted = onVolumeDragStarted,
                        onVolumeDragEnded = onVolumeDragEnded,
                        onPanChanged = { onTrackPanChanged(track.id, it) },
                        onPanDragStarted = onPanDragStarted,
                        onPanDragEnded = onPanDragEnded,
                        onMuteToggled = { onTrackMuteToggled(track.id) },
                        onSoloToggled = { onTrackSoloToggled(track.id) },
                        onEffectsClicked = {
                            selectedEffectTrack = if (selectedEffectTrack == track.id) null else track.id
                            selectedEffectId = null
                        },
                        isEffectsExpanded = selectedEffectTrack == track.id
                    )
                }

                item {
                    MasterBusStrip()
                }
            }
        }

        AnimatedVisibility(
            visible = AUDIO_ROUTING_RENDERER_AVAILABLE && selectedTrack != null,
            enter = slideInVertically { it / 3 } + fadeIn(),
            exit = slideOutVertically { it / 3 } + fadeOut()
        ) {
            selectedTrack?.let { track ->
                Spacer(modifier = Modifier.height(12.dp))

                PremiumPanelCard(accent = track.type.mixerAccent()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = androidx.compose.ui.res.stringResource(
                                    R.string.mixer_effects_track,
                                    androidx.compose.ui.res.stringResource(track.type.displayNameRes()),
                                    tracks.indexOf(track) + 1
                                ),
                                style = MaterialTheme.typography.titleMedium,
                                color = semanticColors.text
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (track.audioEffects.isEmpty()) {
                                    androidx.compose.ui.res.stringResource(R.string.mixer_build_chain_description)
                                } else {
                                    androidx.compose.ui.res.stringResource(R.string.mixer_edit_chain_description)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = semanticColors.subtext
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))
                        AddEffectButton(
                            onAdd = { type -> onTrackAudioEffectAdded(track.id, type) }
                        )
                    }

                    if (track.audioEffects.isEmpty()) {
                        Surface(
                            color = semanticColors.panelRaised,
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, semanticColors.cardStroke)
                        ) {
                            Text(
                                text = androidx.compose.ui.res.stringResource(R.string.panel_audio_mixer_no_effects),
                                style = MaterialTheme.typography.bodyMedium,
                                color = semanticColors.subtext,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                            )
                        }
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            track.audioEffects.forEach { effect ->
                                AudioEffectChip(
                                    effect = effect,
                                    isSelected = selectedEffectId == effect.id,
                                    onClick = {
                                        selectedEffectId = if (selectedEffectId == effect.id) null else effect.id
                                    },
                                    onRemove = { onTrackAudioEffectRemoved(track.id, effect.id) }
                                )
                            }
                        }
                    }

                    selectedEffectId?.let { effectId ->
                        val effect = track.audioEffects.find { it.id == effectId } ?: return@let
                        AudioEffectParams(
                            effect = effect,
                            onParamChanged = { param, value ->
                                onTrackAudioEffectParamChanged(track.id, effectId, param, value)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AddEffectButton(
    onAdd: (AudioEffectType) -> Unit
) {
    var showAddMenu by remember { mutableStateOf(false) }

    Box {
        Surface(
            color = ClearCutAccents.Green.copy(alpha = 0.14f),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, ClearCutAccents.Green.copy(alpha = 0.24f))
        ) {
            Row(
                modifier = Modifier
                    .clickable { showAddMenu = true }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = androidx.compose.ui.res.stringResource(R.string.cd_mixer_add_effect),
                    tint = ClearCutAccents.Green,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = androidx.compose.ui.res.stringResource(R.string.mixer_add_fx),
                    style = MaterialTheme.typography.labelLarge,
                    color = ClearCutAccents.Green
                )
            }
        }

        DropdownMenu(
            expanded = showAddMenu,
            onDismissRequest = { showAddMenu = false }
        ) {
            AudioEffectType.entries.forEach { type ->
                DropdownMenuItem(
                    text = { Text(androidx.compose.ui.res.stringResource(type.displayNameRes())) },
                    onClick = {
                        onAdd(type)
                        showAddMenu = false
                    }
                )
            }
        }
    }
}

@Composable
private fun ChannelStrip(
    track: Track,
    vuLevel: Pair<Float, Float>,
    onVolumeChanged: (Float) -> Unit,
    onVolumeDragStarted: () -> Unit,
    onVolumeDragEnded: () -> Unit,
    onPanChanged: (Float) -> Unit,
    onPanDragStarted: () -> Unit,
    onPanDragEnded: () -> Unit,
    onMuteToggled: () -> Unit,
    onSoloToggled: () -> Unit,
    onEffectsClicked: () -> Unit,
    isEffectsExpanded: Boolean
) {
    val semanticColors = LocalClearCutColors.current
    val accent = track.type.mixerAccent()
    val panDesc = androidx.compose.ui.res.stringResource(R.string.cd_mixer_pan)
    val muteDesc = androidx.compose.ui.res.stringResource(
        if (track.isMuted) R.string.cd_mixer_unmute else R.string.cd_mixer_mute
    )
    val soloDesc = androidx.compose.ui.res.stringResource(
        if (track.isSolo) R.string.cd_mixer_unsolo else R.string.cd_mixer_solo
    )
    val fxDesc = androidx.compose.ui.res.stringResource(R.string.cd_mixer_audio_effects)

    Surface(
        modifier = Modifier
            .width(132.dp)
            .fillMaxHeight(),
        color = semanticColors.panelHighest,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(
            1.dp,
            if (isEffectsExpanded) accent.copy(alpha = 0.55f) else semanticColors.cardStrokeStrong
        )
    ) {
        Box(
            modifier = Modifier.background(
                Brush.verticalGradient(
                    listOf(
                        accent.copy(alpha = 0.12f),
                        semanticColors.panelHighest,
                        semanticColors.panelRaised
                    )
                )
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PremiumPanelPill(
                        text = track.trackLabel(),
                        accent = accent
                    )
                    Text(
                        text = androidx.compose.ui.res.stringResource(track.type.displayNameRes()),
                        style = MaterialTheme.typography.labelMedium,
                        color = semanticColors.subtext
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    VUMeter(
                        left = vuLevel.first,
                        right = vuLevel.second,
                        modifier = Modifier
                            .width(34.dp)
                            .height(84.dp)
                    )
                    Text(
                        text = formatVolume(track.volume),
                        style = MaterialTheme.typography.titleSmall,
                        color = semanticColors.text
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Volume + pan sliders drive the `begin/end*Adjust` hooks so the
                    // ViewModel can save an undo snapshot on drag-start and persist
                    // the project on drag-release, instead of writing to disk on
                    // every onValueChange event.
                    var volumeDragging by remember { mutableStateOf(false) }
                    MixerControlBlock(
                        label = androidx.compose.ui.res.stringResource(R.string.mixer_level),
                        valueLabel = formatVolume(track.volume),
                        accent = accent
                    ) {
                        Slider(
                            value = track.volume,
                            onValueChange = {
                                if (!volumeDragging) {
                                    volumeDragging = true
                                    onVolumeDragStarted()
                                }
                                onVolumeChanged(it)
                            },
                            onValueChangeFinished = {
                                volumeDragging = false
                                onVolumeDragEnded()
                            },
                            valueRange = 0f..2f,
                            colors = SliderDefaults.colors(
                                thumbColor = accent,
                                activeTrackColor = accent.copy(alpha = 0.65f),
                                inactiveTrackColor = semanticColors.surface
                            )
                        )
                    }

                    if (AUDIO_ROUTING_RENDERER_AVAILABLE) {
                        var panDragging by remember { mutableStateOf(false) }
                        MixerControlBlock(
                            label = androidx.compose.ui.res.stringResource(R.string.mixer_pan),
                            valueLabel = formatPan(track.pan),
                            accent = accent
                        ) {
                            Slider(
                                value = track.pan,
                                onValueChange = {
                                    if (!panDragging) {
                                        panDragging = true
                                        onPanDragStarted()
                                    }
                                    onPanChanged(it)
                                },
                                onValueChangeFinished = {
                                    panDragging = false
                                    onPanDragEnded()
                                },
                                valueRange = -1f..1f,
                                modifier = Modifier.semantics { contentDescription = panDesc },
                                colors = SliderDefaults.colors(
                                    thumbColor = ClearCutAccents.Mauve,
                                    activeTrackColor = ClearCutAccents.Mauve.copy(alpha = 0.65f),
                                    inactiveTrackColor = semanticColors.surface
                                )
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MixerToggleButton(
                        label = androidx.compose.ui.res.stringResource(R.string.panel_audio_mixer_mute),
                        accent = ClearCutAccents.Red,
                        active = track.isMuted,
                        contentDescription = muteDesc,
                        onClick = onMuteToggled,
                        modifier = Modifier.weight(1f)
                    )
                    MixerToggleButton(
                        label = androidx.compose.ui.res.stringResource(R.string.panel_audio_mixer_solo),
                        accent = ClearCutAccents.Yellow,
                        active = track.isSolo,
                        contentDescription = soloDesc,
                        onClick = onSoloToggled,
                        modifier = Modifier.weight(1f)
                    )
                    if (AUDIO_ROUTING_RENDERER_AVAILABLE) {
                        MixerToggleButton(
                            label = androidx.compose.ui.res.stringResource(R.string.panel_audio_mixer_fx),
                            accent = accent,
                            active = isEffectsExpanded || track.audioEffects.isNotEmpty(),
                            contentDescription = fxDesc,
                            onClick = onEffectsClicked,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MixerControlBlock(
    label: String,
    valueLabel: String,
    accent: Color,
    content: @Composable () -> Unit
) {
    val semanticColors = LocalClearCutColors.current
    Surface(
        color = semanticColors.panelRaised.copy(alpha = 0.92f),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.12f))
    ) {
        Column(
            modifier = Modifier
                .width(104.dp)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = semanticColors.subtext
            )
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.labelLarge,
                color = accent
            )
            content()
        }
    }
}

@Composable
private fun MixerToggleButton(
    label: String,
    accent: Color,
    active: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semanticColors = LocalClearCutColors.current
    Surface(
        modifier = modifier.semantics { this.contentDescription = contentDescription },
        color = if (active) accent.copy(alpha = 0.18f) else semanticColors.panelRaised,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (active) accent.copy(alpha = 0.26f) else semanticColors.cardStroke)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (active) accent else semanticColors.subtext,
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun VUMeter(
    left: Float,
    right: Float,
    modifier: Modifier = Modifier
) {
    val semanticColors = LocalClearCutColors.current
    val smoothedLeft by animateFloatAsState(
        targetValue = left.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = if (left > 0f) 50 else 150),
        label = "vuLeft"
    )
    val smoothedRight by animateFloatAsState(
        targetValue = right.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = if (right > 0f) 50 else 150),
        label = "vuRight"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val barWidth = w * 0.34f
        val gap = w * 0.12f

        drawRoundRect(
            color = semanticColors.panel,
            cornerRadius = CornerRadius(20f, 20f)
        )
        drawRoundRect(
            color = semanticColors.cardStrokeStrong,
            cornerRadius = CornerRadius(20f, 20f),
            style = Stroke(width = 1f)
        )

        val leftHeight = h * smoothedLeft
        val leftColor = when {
            smoothedLeft > 0.9f -> ClearCutAccents.Red
            smoothedLeft > 0.7f -> ClearCutAccents.Yellow
            else -> ClearCutAccents.Green
        }
        drawRect(
            color = leftColor,
            topLeft = Offset(gap, h - leftHeight),
            size = Size(barWidth, leftHeight)
        )

        val rightHeight = h * smoothedRight
        val rightColor = when {
            smoothedRight > 0.9f -> ClearCutAccents.Red
            smoothedRight > 0.7f -> ClearCutAccents.Yellow
            else -> ClearCutAccents.Green
        }
        drawRect(
            color = rightColor,
            topLeft = Offset(gap + barWidth + gap, h - rightHeight),
            size = Size(barWidth, rightHeight)
        )

        for (i in 1..4) {
            val y = h * i / 5f
            drawLine(
                color = semanticColors.surfaceHigh.copy(alpha = 0.45f),
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1f
            )
        }
    }
}

@Composable
private fun MasterBusStrip() {
    val semanticColors = LocalClearCutColors.current
    Surface(
        modifier = Modifier
            .width(132.dp)
            .fillMaxHeight(),
        color = semanticColors.panelHighest,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, ClearCutAccents.Mauve.copy(alpha = 0.32f))
    ) {
        Box(
            modifier = Modifier.background(
                Brush.verticalGradient(
                    listOf(
                        ClearCutAccents.Mauve.copy(alpha = 0.16f),
                        semanticColors.panelHighest,
                        semanticColors.panelRaised
                    )
                )
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                PremiumPanelPill(
                    text = androidx.compose.ui.res.stringResource(R.string.panel_audio_mixer_master),
                    accent = ClearCutAccents.Mauve
                )
                Spacer(modifier = Modifier.height(16.dp))
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = androidx.compose.ui.res.stringResource(R.string.cd_mixer_master),
                    tint = ClearCutAccents.Mauve,
                    modifier = Modifier.size(34.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = androidx.compose.ui.res.stringResource(R.string.mixer_master_bus),
                    style = MaterialTheme.typography.titleSmall,
                    color = semanticColors.text
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = androidx.compose.ui.res.stringResource(R.string.mixer_reference_output),
                    style = MaterialTheme.typography.bodySmall,
                    color = semanticColors.subtext
                )
            }
        }
    }
}

@Composable
private fun AudioEffectChip(
    effect: AudioEffect,
    isSelected: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    val semanticColors = LocalClearCutColors.current
    val removeEffectLabel = androidx.compose.ui.res.stringResource(R.string.cd_mixer_remove_effect)
    Surface(
        color = if (isSelected) ClearCutAccents.Mauve.copy(alpha = 0.16f) else semanticColors.panelRaised,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            1.dp,
            if (isSelected) ClearCutAccents.Mauve.copy(alpha = 0.3f) else semanticColors.cardStroke
        )
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        if (effect.enabled) ClearCutAccents.Green else ClearCutAccents.Red,
                        RoundedCornerShape(10.dp)
                    )
            )
            Text(
                text = androidx.compose.ui.res.stringResource(effect.type.displayNameRes()),
                style = MaterialTheme.typography.labelLarge,
                color = if (isSelected) ClearCutAccents.Mauve else semanticColors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .semantics {
                        contentDescription = removeEffectLabel
                    }
                    .clickable(role = Role.Button, onClick = onRemove),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = semanticColors.subtext,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun AudioEffectParams(
    effect: AudioEffect,
    onParamChanged: (String, Float) -> Unit
) {
    val semanticColors = LocalClearCutColors.current
    Surface(
        color = semanticColors.panelRaised,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, semanticColors.cardStrokeStrong)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = androidx.compose.ui.res.stringResource(effect.type.displayNameRes()),
                style = MaterialTheme.typography.titleMedium,
                color = semanticColors.text
            )
            Text(
                text = androidx.compose.ui.res.stringResource(R.string.mixer_adjust_processor_description),
                style = MaterialTheme.typography.bodyMedium,
                color = semanticColors.subtext
            )

            effect.params.toSortedMap().forEach { (param, value) ->
                val range = getParamRange(effect.type, param)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = localizedParamName(param),
                            style = MaterialTheme.typography.labelLarge,
                            color = semanticColors.text
                        )
                        Text(
                            text = formatParamValue(param, value),
                            style = MaterialTheme.typography.labelLarge,
                            color = ClearCutAccents.Mauve
                        )
                    }
                    Slider(
                        value = value,
                        onValueChange = { onParamChanged(param, it) },
                        valueRange = range.first..range.second,
                        colors = SliderDefaults.colors(
                            thumbColor = ClearCutAccents.Mauve,
                            activeTrackColor = ClearCutAccents.Mauve.copy(alpha = 0.6f),
                            inactiveTrackColor = semanticColors.surface
                        )
                    )
                }
            }
        }
    }
}

private fun getParamRange(type: AudioEffectType, param: String): Pair<Float, Float> {
    return when {
        param.endsWith("_freq") || param == "frequency" -> 20f to 20000f
        param.endsWith("_gain") || param == "gain" || param == "makeupGain" -> -24f to 24f
        param.endsWith("_q") || param == "resonance" -> 0.1f to 10f
        param == "threshold" -> -60f to 0f
        param == "ratio" -> 1f to 20f
        param == "attack" -> 0.1f to 200f
        param == "release" -> 10f to 2000f
        param == "knee" -> 0f to 30f
        param == "ceiling" -> -20f to 0f
        param == "roomSize" || param == "damping" || param == "wetDry" || param == "depth" -> 0f to 1f
        param == "feedback" -> 0f to 0.95f
        param == "delayMs" || param == "preDelay" -> 1f to 2000f
        param == "decay" -> 0.1f to 10f
        param == "rate" -> 0.1f to 20f
        param == "semitones" -> -12f to 12f
        param == "cents" -> -100f to 100f
        param == "targetPeakDb" -> -30f to -5f
        param == "hold" -> 1f to 500f
        param == "bandwidth" -> 0.1f to 5f
        param == "mode" -> 0f to 2f
        param == "pingPong" -> 0f to 1f
        else -> 0f to 1f
    }
}

private fun formatParamName(param: String): String {
    return param.replace("_", " ")
        .split(" ")
        .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
}

@Composable
private fun localizedParamName(param: String): String {
    val bandNumber = param.removePrefix("band")
        .takeWhile(Char::isDigit)
        .toIntOrNull()
    if (bandNumber != null) {
        return when {
            param.endsWith("_freq") -> androidx.compose.ui.res.stringResource(
                R.string.mixer_param_band_frequency,
                bandNumber,
            )
            param.endsWith("_gain") -> androidx.compose.ui.res.stringResource(
                R.string.mixer_param_band_gain,
                bandNumber,
            )
            param.endsWith("_q") -> androidx.compose.ui.res.stringResource(
                R.string.mixer_param_band_q,
                bandNumber,
            )
            else -> androidx.compose.ui.res.stringResource(R.string.mixer_param_generic, formatParamName(param))
        }
    }

    val resourceId = when (param) {
        "threshold" -> R.string.mixer_param_threshold
        "ratio" -> R.string.mixer_param_ratio
        "attack" -> R.string.mixer_param_attack
        "release" -> R.string.mixer_param_release
        "knee" -> R.string.mixer_param_knee
        "makeupGain" -> R.string.mixer_param_makeup_gain
        "ceiling" -> R.string.mixer_param_ceiling
        "hold" -> R.string.mixer_param_hold
        "roomSize" -> R.string.mixer_param_room_size
        "damping" -> R.string.mixer_param_damping
        "wetDry" -> R.string.mixer_param_wet_dry
        "preDelay" -> R.string.mixer_param_pre_delay
        "decay" -> R.string.mixer_param_decay
        "delayMs" -> R.string.mixer_param_delay
        "feedback" -> R.string.mixer_param_feedback
        "pingPong" -> R.string.mixer_param_ping_pong
        "frequency" -> R.string.mixer_param_frequency
        "rate" -> R.string.mixer_param_rate
        "depth" -> R.string.mixer_param_depth
        "semitones" -> R.string.mixer_param_semitones
        "cents" -> R.string.mixer_param_cents
        "targetPeakDb" -> R.string.mixer_param_target_peak
        "mode" -> R.string.mixer_param_mode
        "resonance" -> R.string.mixer_param_resonance
        "bandwidth" -> R.string.mixer_param_bandwidth
        else -> null
    }
    return if (resourceId != null) {
        androidx.compose.ui.res.stringResource(resourceId)
    } else {
        androidx.compose.ui.res.stringResource(R.string.mixer_param_generic, formatParamName(param))
    }
}

private fun formatParamValue(param: String, value: Float): String {
    return when {
        param.endsWith("_freq") || param == "frequency" -> "${value.toInt()}Hz"
        param.endsWith("_gain") || param == "gain" || param == "makeupGain" -> "%.1fdB".format(value)
        param == "threshold" || param == "ceiling" || param == "targetPeakDb" -> "%.1fdB".format(value)
        param == "ratio" -> "%.1f:1".format(value)
        param == "attack" || param == "release" || param == "delayMs" || param == "hold" || param == "preDelay" -> "${value.toInt()}ms"
        param == "decay" -> "%.1fs".format(value)
        param == "semitones" -> "${value.toInt()}st"
        param == "cents" -> "${value.toInt()}c"
        param == "rate" -> "%.1fHz".format(value)
        else -> "%.2f".format(value)
    }
}

private fun Track.trackLabel(): String = when (type) {
    TrackType.VIDEO -> "V${index + 1}"
    TrackType.AUDIO -> "A${index + 1}"
    TrackType.OVERLAY -> "OV${index + 1}"
    TrackType.TEXT -> "T${index + 1}"
    TrackType.ADJUSTMENT -> "ADJ"
}

private fun TrackType.displayNameRes(): Int = when (this) {
    TrackType.VIDEO -> R.string.mixer_track_type_video
    TrackType.AUDIO -> R.string.mixer_track_type_audio
    TrackType.OVERLAY -> R.string.mixer_track_type_overlay
    TrackType.TEXT -> R.string.mixer_track_type_text
    TrackType.ADJUSTMENT -> R.string.mixer_track_type_adjustment
}

private fun AudioEffectType.displayNameRes(): Int = when (this) {
    AudioEffectType.PARAMETRIC_EQ -> R.string.mixer_effect_parametric_eq
    AudioEffectType.COMPRESSOR -> R.string.mixer_effect_compressor
    AudioEffectType.LIMITER -> R.string.mixer_effect_limiter
    AudioEffectType.NOISE_GATE -> R.string.mixer_effect_noise_gate
    AudioEffectType.REVERB -> R.string.mixer_effect_reverb
    AudioEffectType.DELAY -> R.string.mixer_effect_delay
    AudioEffectType.DE_ESSER -> R.string.mixer_effect_de_esser
    AudioEffectType.CHORUS -> R.string.mixer_effect_chorus
    AudioEffectType.FLANGER -> R.string.mixer_effect_flanger
    AudioEffectType.PITCH_SHIFT -> R.string.mixer_effect_pitch_shift
    AudioEffectType.NORMALIZER -> R.string.mixer_effect_normalizer
    AudioEffectType.HIGH_PASS -> R.string.mixer_effect_high_pass
    AudioEffectType.LOW_PASS -> R.string.mixer_effect_low_pass
    AudioEffectType.BAND_PASS -> R.string.mixer_effect_band_pass
    AudioEffectType.NOTCH -> R.string.mixer_effect_notch
}

private fun TrackType.mixerAccent(): Color = when (this) {
    TrackType.VIDEO -> ClearCutAccents.Blue
    TrackType.AUDIO -> ClearCutAccents.Green
    TrackType.OVERLAY -> ClearCutAccents.Peach
    TrackType.TEXT -> ClearCutAccents.Mauve
    TrackType.ADJUSTMENT -> ClearCutAccents.Yellow
}

private fun formatVolume(value: Float): String = "${(value * 100).toInt()}%"

private fun formatPan(value: Float): String = when {
    value < -0.1f -> "L${(-value * 100).toInt()}"
    value > 0.1f -> "R${(value * 100).toInt()}"
    else -> "C"
}

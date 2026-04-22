package com.novacut.editor.ui.editor

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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.novacut.editor.R
import com.novacut.editor.model.AudioEffect
import com.novacut.editor.model.AudioEffectType
import com.novacut.editor.model.Track
import com.novacut.editor.model.TrackType
import com.novacut.editor.ui.theme.Mocha

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
    var selectedEffectTrack by remember { mutableStateOf<String?>(null) }
    var selectedEffectId by remember { mutableStateOf<String?>(null) }
    val selectedTrack = tracks.find { it.id == selectedEffectTrack }
    val activeEffects = tracks.sumOf { it.audioEffects.size }

    PremiumEditorPanel(
        title = androidx.compose.ui.res.stringResource(R.string.panel_audio_mixer_title),
        subtitle = "Balance channels, shape stereo placement, and stack live FX from one stage.",
        icon = Icons.Default.Tune,
        accent = Mocha.Sapphire,
        onClose = onClose,
        closeContentDescription = androidx.compose.ui.res.stringResource(R.string.cd_close_audio_panel),
        modifier = modifier,
        scrollable = true
    ) {
        PremiumPanelCard(accent = Mocha.Sapphire) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Session overview",
                        style = MaterialTheme.typography.titleMedium,
                        color = Mocha.Text
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (selectedTrack != null) {
                            "Fine-tune ${selectedTrack.type.displayLabel()} ${selectedTrack.index + 1} with live metering and effect edits below."
                        } else {
                            "Scroll the strips to stage levels, then open FX on any track to dial in the chain."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Mocha.Subtext0
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    selectedTrack?.let { track ->
                        PremiumPanelPill(
                            text = "${track.trackLabel()} selected",
                            accent = track.type.mixerAccent()
                        )
                    }
                    PremiumPanelPill(
                        text = "${tracks.size} tracks live",
                        accent = Mocha.Sapphire
                    )
                    PremiumPanelPill(
                        text = "$activeEffects FX staged",
                        accent = if (activeEffects > 0) Mocha.Mauve else Mocha.Overlay1
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        PremiumPanelCard(
            accent = Mocha.Blue
        ) {
            Text(
                text = "Channel strips",
                style = MaterialTheme.typography.titleMedium,
                color = Mocha.Text
            )
            Text(
                text = "Each strip now exposes real volume and pan control, plus mute, solo, and effect routing.",
                style = MaterialTheme.typography.bodyMedium,
                color = Mocha.Subtext0
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
            visible = selectedTrack != null,
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
                                    track.type.displayLabel(),
                                    tracks.indexOf(track) + 1
                                ),
                                style = MaterialTheme.typography.titleMedium,
                                color = Mocha.Text
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (track.audioEffects.isEmpty()) {
                                    "Build a chain for cleanup, tone shaping, and loudness control."
                                } else {
                                    "Tap a processor to tweak its parameters or remove it from the chain."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = Mocha.Subtext0
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))
                        AddEffectButton(
                            onAdd = { type -> onTrackAudioEffectAdded(track.id, type) }
                        )
                    }

                    if (track.audioEffects.isEmpty()) {
                        Surface(
                            color = Mocha.PanelRaised,
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, Mocha.CardStroke)
                        ) {
                            Text(
                                text = androidx.compose.ui.res.stringResource(R.string.panel_audio_mixer_no_effects),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Mocha.Subtext0,
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
            color = Mocha.Green.copy(alpha = 0.14f),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, Mocha.Green.copy(alpha = 0.24f))
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
                    tint = Mocha.Green,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Add FX",
                    style = MaterialTheme.typography.labelLarge,
                    color = Mocha.Green
                )
            }
        }

        DropdownMenu(
            expanded = showAddMenu,
            onDismissRequest = { showAddMenu = false }
        ) {
            AudioEffectType.entries.forEach { type ->
                DropdownMenuItem(
                    text = { Text(type.displayName) },
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
        color = Mocha.PanelHighest,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(
            1.dp,
            if (isEffectsExpanded) accent.copy(alpha = 0.55f) else Mocha.CardStrokeStrong
        )
    ) {
        Box(
            modifier = Modifier.background(
                Brush.verticalGradient(
                    listOf(
                        accent.copy(alpha = 0.12f),
                        Mocha.PanelHighest,
                        Mocha.PanelRaised
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
                        text = track.type.displayLabel(),
                        style = MaterialTheme.typography.labelMedium,
                        color = Mocha.Subtext0
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
                        color = Mocha.Text
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
                        label = "Level",
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
                                inactiveTrackColor = Mocha.Surface1
                            )
                        )
                    }

                    var panDragging by remember { mutableStateOf(false) }
                    MixerControlBlock(
                        label = "Pan",
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
                                thumbColor = Mocha.Mauve,
                                activeTrackColor = Mocha.Mauve.copy(alpha = 0.65f),
                                inactiveTrackColor = Mocha.Surface1
                            )
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MixerToggleButton(
                        label = androidx.compose.ui.res.stringResource(R.string.panel_audio_mixer_mute),
                        accent = Mocha.Red,
                        active = track.isMuted,
                        contentDescription = muteDesc,
                        onClick = onMuteToggled,
                        modifier = Modifier.weight(1f)
                    )
                    MixerToggleButton(
                        label = androidx.compose.ui.res.stringResource(R.string.panel_audio_mixer_solo),
                        accent = Mocha.Yellow,
                        active = track.isSolo,
                        contentDescription = soloDesc,
                        onClick = onSoloToggled,
                        modifier = Modifier.weight(1f)
                    )
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

@Composable
private fun MixerControlBlock(
    label: String,
    valueLabel: String,
    accent: Color,
    content: @Composable () -> Unit
) {
    Surface(
        color = Mocha.PanelRaised.copy(alpha = 0.92f),
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
                color = Mocha.Subtext0
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
    Surface(
        modifier = modifier.semantics { this.contentDescription = contentDescription },
        color = if (active) accent.copy(alpha = 0.18f) else Mocha.PanelRaised,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (active) accent.copy(alpha = 0.26f) else Mocha.CardStroke)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (active) accent else Mocha.Subtext0,
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
            color = Mocha.Panel,
            cornerRadius = CornerRadius(20f, 20f)
        )
        drawRoundRect(
            color = Mocha.CardStrokeStrong,
            cornerRadius = CornerRadius(20f, 20f),
            style = Stroke(width = 1f)
        )

        val leftHeight = h * smoothedLeft
        val leftColor = when {
            smoothedLeft > 0.9f -> Mocha.Red
            smoothedLeft > 0.7f -> Mocha.Yellow
            else -> Mocha.Green
        }
        drawRect(
            color = leftColor,
            topLeft = Offset(gap, h - leftHeight),
            size = Size(barWidth, leftHeight)
        )

        val rightHeight = h * smoothedRight
        val rightColor = when {
            smoothedRight > 0.9f -> Mocha.Red
            smoothedRight > 0.7f -> Mocha.Yellow
            else -> Mocha.Green
        }
        drawRect(
            color = rightColor,
            topLeft = Offset(gap + barWidth + gap, h - rightHeight),
            size = Size(barWidth, rightHeight)
        )

        for (i in 1..4) {
            val y = h * i / 5f
            drawLine(
                color = Mocha.Surface2.copy(alpha = 0.45f),
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1f
            )
        }
    }
}

@Composable
private fun MasterBusStrip() {
    Surface(
        modifier = Modifier
            .width(132.dp)
            .fillMaxHeight(),
        color = Mocha.PanelHighest,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, Mocha.Mauve.copy(alpha = 0.32f))
    ) {
        Box(
            modifier = Modifier.background(
                Brush.verticalGradient(
                    listOf(
                        Mocha.Mauve.copy(alpha = 0.16f),
                        Mocha.PanelHighest,
                        Mocha.PanelRaised
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
                    accent = Mocha.Mauve
                )
                Spacer(modifier = Modifier.height(16.dp))
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = androidx.compose.ui.res.stringResource(R.string.cd_mixer_master),
                    tint = Mocha.Mauve,
                    modifier = Modifier.size(34.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Master bus",
                    style = MaterialTheme.typography.titleSmall,
                    color = Mocha.Text
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Reference output",
                    style = MaterialTheme.typography.bodySmall,
                    color = Mocha.Subtext0
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
    Surface(
        color = if (isSelected) Mocha.Mauve.copy(alpha = 0.16f) else Mocha.PanelRaised,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            1.dp,
            if (isSelected) Mocha.Mauve.copy(alpha = 0.3f) else Mocha.CardStroke
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
                        if (effect.enabled) Mocha.Green else Mocha.Red,
                        RoundedCornerShape(999.dp)
                    )
            )
            Text(
                text = effect.type.displayName,
                style = MaterialTheme.typography.labelLarge,
                color = if (isSelected) Mocha.Mauve else Mocha.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = androidx.compose.ui.res.stringResource(R.string.cd_mixer_remove_effect),
                tint = Mocha.Subtext0,
                modifier = Modifier
                    .size(16.dp)
                    .clickable(onClick = onRemove)
            )
        }
    }
}

@Composable
private fun AudioEffectParams(
    effect: AudioEffect,
    onParamChanged: (String, Float) -> Unit
) {
    Surface(
        color = Mocha.PanelRaised,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, Mocha.CardStrokeStrong)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = effect.type.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = Mocha.Text
            )
            Text(
                text = "Adjust the selected processor in real time while the preview keeps playing above.",
                style = MaterialTheme.typography.bodyMedium,
                color = Mocha.Subtext0
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
                            text = formatParamName(param),
                            style = MaterialTheme.typography.labelLarge,
                            color = Mocha.Text
                        )
                        Text(
                            text = formatParamValue(param, value),
                            style = MaterialTheme.typography.labelLarge,
                            color = Mocha.Mauve
                        )
                    }
                    Slider(
                        value = value,
                        onValueChange = { onParamChanged(param, it) },
                        valueRange = range.first..range.second,
                        colors = SliderDefaults.colors(
                            thumbColor = Mocha.Mauve,
                            activeTrackColor = Mocha.Mauve.copy(alpha = 0.6f),
                            inactiveTrackColor = Mocha.Surface1
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

private fun TrackType.displayLabel(): String = when (this) {
    TrackType.VIDEO -> "Video"
    TrackType.AUDIO -> "Audio"
    TrackType.OVERLAY -> "Overlay"
    TrackType.TEXT -> "Text"
    TrackType.ADJUSTMENT -> "Adjust"
}

private fun TrackType.mixerAccent(): Color = when (this) {
    TrackType.VIDEO -> Mocha.Blue
    TrackType.AUDIO -> Mocha.Green
    TrackType.OVERLAY -> Mocha.Peach
    TrackType.TEXT -> Mocha.Mauve
    TrackType.ADJUSTMENT -> Mocha.Yellow
}

private fun formatVolume(value: Float): String = "${(value * 100).toInt()}%"

private fun formatPan(value: Float): String = when {
    value < -0.1f -> "L${(-value * 100).toInt()}"
    value > 0.1f -> "R${(value * 100).toInt()}"
    else -> "C"
}

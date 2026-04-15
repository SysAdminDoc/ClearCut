package com.novacut.editor.ui.editor

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novacut.editor.R
import com.novacut.editor.model.Clip
import com.novacut.editor.ui.theme.Mocha

@Composable
fun AudioPanel(
    clip: Clip?,
    waveform: List<Float>?,
    onVolumeChanged: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onVolumeDragStarted: () -> Unit = {},
    onFadeInChanged: (Long) -> Unit,
    onFadeOutChanged: (Long) -> Unit,
    onFadeDragStarted: () -> Unit = {},
    onStartVoiceover: () -> Unit,
    onClose: () -> Unit
) {
    PremiumEditorPanel(
        title = stringResource(R.string.audio_title),
        subtitle = stringResource(R.string.panel_audio_subtitle),
        icon = Icons.Default.GraphicEq,
        accent = Mocha.Green,
        onClose = onClose,
        modifier = modifier
    ) {
        if (clip == null) {
            PremiumPanelCard(accent = Mocha.Sapphire) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Mocha.Panel),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.LibraryMusic,
                            contentDescription = stringResource(R.string.audio_select_clip),
                            tint = Mocha.Sapphire,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Text(
                        text = stringResource(R.string.audio_select_clip),
                        color = Mocha.Text,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
            return@PremiumEditorPanel
        }

        val clipDuration = clip.durationMs.toFloat().coerceAtLeast(100f)
        val fadeOutMs = clip.fadeOutMs.toFloat()
        val fadeInMs = clip.fadeInMs.toFloat()
        val fadeInMax = (clipDuration - fadeOutMs).coerceAtLeast(0f)
        val fadeOutMax = (clipDuration - fadeInMs).coerceAtLeast(0f)

        PremiumPanelCard(accent = Mocha.Green) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PremiumPanelPill(
                    text = stringResource(R.string.audio_clip_duration, formatTimestamp(clip.durationMs)),
                    accent = Mocha.Sapphire
                )
                PremiumPanelPill(
                    text = "${(clip.volume * 100).toInt()}%",
                    accent = Mocha.Rosewater
                )
            }

            if (waveform != null && waveform.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.audio_waveform_label),
                    color = Mocha.Rosewater,
                    style = MaterialTheme.typography.labelLarge
                )
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(88.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Mocha.Panel)
                        .padding(horizontal = 8.dp, vertical = 10.dp)
                ) {
                    drawWaveform(waveform, Mocha.Green)
                    if (clip.fadeInMs > 0 || clip.fadeOutMs > 0) {
                        drawFadeEnvelope(clip.fadeInMs, clip.fadeOutMs, clip.durationMs.coerceAtLeast(1L), Mocha.Mauve)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        PremiumPanelCard(accent = Mocha.Mauve) {
            EffectSlider(
                label = stringResource(R.string.audio_volume),
                value = clip.volume,
                min = 0f,
                max = 2f,
                onDragStarted = onVolumeDragStarted,
                onValueChange = onVolumeChanged
            )
            EffectSlider(
                label = stringResource(R.string.audio_fade_in),
                value = fadeInMs,
                min = 0f,
                max = fadeInMax,
                onDragStarted = onFadeDragStarted,
                onValueChange = { onFadeInChanged(it.toLong()) }
            )
            EffectSlider(
                label = stringResource(R.string.audio_fade_out),
                value = fadeOutMs,
                min = 0f,
                max = fadeOutMax,
                onDragStarted = onFadeDragStarted,
                onValueChange = { onFadeOutChanged(it.toLong()) }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onStartVoiceover,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Mocha.Rosewater,
                contentColor = Mocha.Midnight
            ),
            shape = RoundedCornerShape(18.dp)
        ) {
            Icon(
                Icons.Default.Mic,
                contentDescription = stringResource(R.string.audio_record_voiceover),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.audio_record_voiceover),
                style = MaterialTheme.typography.titleSmall
            )
        }
    }
}

private fun DrawScope.drawFadeEnvelope(fadeInMs: Long, fadeOutMs: Long, durationMs: Long, color: Color) {
    if (durationMs <= 10) return // Guard against tiny durations causing extreme path values
    val path = Path()
    val w = size.width
    val h = size.height

    path.moveTo(0f, h) // bottom-left

    // Fade-in: ramp up
    if (fadeInMs > 0) {
        val fadeInX = (fadeInMs.toFloat() / durationMs) * w
        path.lineTo(0f, h) // start at full fade (bottom = full volume shown inverted for overlay)
        path.lineTo(fadeInX, 0f) // ramp to top
    } else {
        path.lineTo(0f, 0f)
    }

    // Fade-out: ramp down
    if (fadeOutMs > 0) {
        val fadeOutStartX = ((durationMs - fadeOutMs).toFloat() / durationMs) * w
        path.lineTo(fadeOutStartX, 0f)
        path.lineTo(w, h)
    } else {
        path.lineTo(w, 0f)
        path.lineTo(w, h)
    }

    path.close()

    // Draw the envelope line (not filled, just the envelope shape)
    val strokePath = Path()
    if (fadeInMs > 0) {
        val fadeInX = (fadeInMs.toFloat() / durationMs) * w
        strokePath.moveTo(0f, h)
        strokePath.lineTo(fadeInX, 0f)
    }
    if (fadeOutMs > 0) {
        val fadeOutStartX = ((durationMs - fadeOutMs).toFloat() / durationMs) * w
        if (fadeInMs <= 0) strokePath.moveTo(fadeOutStartX, 0f)
        else strokePath.lineTo(fadeOutStartX, 0f)
        strokePath.lineTo(w, h)
    }

    drawPath(
        path = strokePath,
        color = color,
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
    )
    // Dim region outside envelope
    drawPath(
        path = path,
        color = color.copy(alpha = 0.1f)
    )
}

private fun DrawScope.drawWaveform(waveform: List<Float>, color: Color) {
    val centerY = size.height / 2f
    val barWidth = size.width / waveform.size
    val maxBarHeight = size.height * 0.8f

    for (i in waveform.indices) {
        val x = i * barWidth
        val amplitude = waveform[i].coerceIn(0f, 1f)
        val barHeight = amplitude * maxBarHeight
        val top = centerY - barHeight / 2f

        drawRect(
            color = color.copy(alpha = 0.4f + amplitude * 0.6f),
            topLeft = Offset(x, top),
            size = Size(barWidth * 0.7f, barHeight)
        )
    }
}

@Composable
fun VoiceoverRecorder(
    isRecording: Boolean,
    recordingDurationMs: Long,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    PremiumEditorPanel(
        title = stringResource(R.string.audio_voiceover),
        subtitle = stringResource(R.string.panel_voiceover_subtitle),
        icon = Icons.Default.Mic,
        accent = if (isRecording) Mocha.Red else Mocha.Sapphire,
        onClose = onClose,
        modifier = modifier
    ) {
        PremiumPanelCard(accent = if (isRecording) Mocha.Red else Mocha.Sapphire) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                PremiumPanelPill(
                    text = if (isRecording) {
                        stringResource(R.string.audio_status_recording)
                    } else {
                        stringResource(R.string.audio_status_ready)
                    },
                    accent = if (isRecording) Mocha.Red else Mocha.Sapphire
                )

                Text(
                    text = formatTimestamp(recordingDurationMs),
                    color = if (isRecording) Mocha.Rosewater else Mocha.Text,
                    style = MaterialTheme.typography.displayMedium
                )

                Box(
                    modifier = Modifier
                        .size(112.dp)
                        .clip(CircleShape)
                        .background(
                            if (isRecording) Mocha.Red.copy(alpha = 0.14f)
                            else Mocha.Panel
                        )
                        .border(
                            width = 2.dp,
                            color = if (isRecording) Mocha.Red.copy(alpha = 0.7f) else Mocha.CardStrokeStrong,
                            shape = CircleShape
                        )
                        .clickable {
                            if (isRecording) onStopRecording() else onStartRecording()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isRecording) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Mocha.Red)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Mocha.Red)
                        )
                    }
                }

                Text(
                    text = if (isRecording) stringResource(R.string.audio_tap_to_stop) else stringResource(R.string.audio_tap_to_record),
                    color = Mocha.Subtext0,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        TextButton(
            onClick = onClose,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text(
                text = stringResource(R.string.audio_cancel),
                color = Mocha.Subtext0,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

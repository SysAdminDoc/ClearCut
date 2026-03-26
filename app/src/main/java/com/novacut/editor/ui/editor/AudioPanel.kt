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
    waveform: FloatArray?,
    onVolumeChanged: (Float) -> Unit,
    onVolumeDragStarted: () -> Unit = {},
    onFadeInChanged: (Long) -> Unit,
    onFadeOutChanged: (Long) -> Unit,
    onFadeDragStarted: () -> Unit = {},
    onStartVoiceover: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Mocha.Mantle, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.audio_title), color = Mocha.Text, fontSize = 16.sp)
            IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, "Close audio panel", tint = Mocha.Subtext0, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (clip == null) {
            Text(
                stringResource(R.string.audio_select_clip),
                color = Mocha.Subtext0,
                fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 24.dp).fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            return@Column
        }

        // Waveform visualization with fade envelope
        if (waveform != null && waveform.isNotEmpty()) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Mocha.Surface0)
            ) {
                drawWaveform(waveform, Mocha.Green)
                // Draw fade envelope overlay
                val fadeInMs = clip?.fadeInMs ?: 0L
                val fadeOutMs = clip?.fadeOutMs ?: 0L
                val durationMs = clip?.durationMs ?: 1L
                if (fadeInMs > 0 || fadeOutMs > 0) {
                    drawFadeEnvelope(fadeInMs, fadeOutMs, durationMs, Mocha.Mauve)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Volume
        var volumeDragStarted by remember { mutableStateOf(false) }
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(R.string.audio_volume), color = Mocha.Subtext1, fontSize = 12.sp)
                Text("%.2f".format(clip?.volume ?: 1f), color = Mocha.Subtext0, fontSize = 12.sp)
            }
            Slider(
                value = clip?.volume ?: 1f,
                onValueChange = {
                    if (!volumeDragStarted) {
                        volumeDragStarted = true
                        onVolumeDragStarted()
                    }
                    onVolumeChanged(it)
                },
                onValueChangeFinished = { volumeDragStarted = false },
                valueRange = 0f..2f,
                colors = SliderDefaults.colors(
                    thumbColor = Mocha.Mauve,
                    activeTrackColor = Mocha.Mauve,
                    inactiveTrackColor = Mocha.Surface1
                )
            )
        }

        // Fade In (constrained so fade in + fade out <= clip duration)
        val clipDuration = (clip?.durationMs ?: 5000L).toFloat().coerceAtLeast(100f)
        val fadeOutMs = (clip?.fadeOutMs ?: 0L).toFloat()
        val fadeInMs = (clip?.fadeInMs ?: 0L).toFloat()
        val fadeInMax = (clipDuration - fadeOutMs).coerceAtLeast(0f)
        val fadeOutMax = (clipDuration - fadeInMs).coerceAtLeast(0f)
        EffectSlider("Fade In (ms)", fadeInMs, 0f, fadeInMax, onFadeDragStarted) {
            onFadeInChanged(it.toLong())
        }

        // Fade Out
        EffectSlider("Fade Out (ms)", fadeOutMs, 0f, fadeOutMax, onFadeDragStarted) {
            onFadeOutChanged(it.toLong())
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Voiceover button
        OutlinedButton(
            onClick = onStartVoiceover,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Mocha.Red),
            border = BorderStroke(1.dp, Mocha.Red.copy(alpha = 0.5f))
        ) {
            Icon(Icons.Default.Mic, contentDescription = stringResource(R.string.audio_record_voiceover), modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.audio_record_voiceover))
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

private fun DrawScope.drawWaveform(waveform: FloatArray, color: Color) {
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
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Mocha.Mantle, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(stringResource(R.string.audio_voiceover), color = Mocha.Text, fontSize = 18.sp)
        Spacer(modifier = Modifier.height(16.dp))

        // Recording time
        Text(
            formatTimestamp(recordingDurationMs),
            color = if (isRecording) Mocha.Red else Mocha.Subtext0,
            fontSize = 32.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Record button
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(
                    if (isRecording) Mocha.Red.copy(alpha = 0.2f)
                    else Mocha.Surface0
                )
                .border(3.dp, if (isRecording) Mocha.Red else Mocha.Subtext0, CircleShape)
                .clickable {
                    if (isRecording) onStopRecording() else onStartRecording()
                },
            contentAlignment = Alignment.Center
        ) {
            if (isRecording) {
                // Stop icon (square)
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(Mocha.Red, RoundedCornerShape(4.dp))
                )
            } else {
                // Record icon (circle)
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(Mocha.Red, CircleShape)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            if (isRecording) stringResource(R.string.audio_tap_to_stop) else stringResource(R.string.audio_tap_to_record),
            color = Mocha.Subtext0,
            fontSize = 13.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(onClick = onClose) {
            Text(stringResource(R.string.audio_cancel), color = Mocha.Subtext0)
        }
    }
}

package com.novacut.editor.ui.editor

import com.novacut.editor.ui.theme.ClearCutAccents
import com.novacut.editor.ui.theme.LocalClearCutColors
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.novacut.editor.R
import com.novacut.editor.ai.AutoEditIntent
import com.novacut.editor.ai.AutoEditResult

@Composable
fun AutoEditPanel(
    clipCount: Int,
    hasAudio: Boolean,
    isProcessing: Boolean,
    proposal: AutoEditResult?,
    onGenerate: (AutoEditIntent, Long) -> Unit,
    onCancel: () -> Unit,
    onApply: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semanticColors = LocalClearCutColors.current
    var intent by remember { mutableStateOf(AutoEditIntent.HIGHLIGHT_REEL) }
    var targetDurationMs by remember { mutableStateOf(60_000L) }

    PremiumEditorPanel(
        title = stringResource(R.string.auto_edit_title),
        subtitle = stringResource(R.string.auto_edit_subtitle),
        icon = Icons.Default.AutoFixHigh,
        accent = ClearCutAccents.Mauve,
        onClose = onClose,
        modifier = modifier,
        scrollable = true
    ) {
        PremiumPanelCard(accent = ClearCutAccents.Mauve) {
            Text(
                text = stringResource(R.string.auto_edit_source_overview_title),
                style = MaterialTheme.typography.titleMedium,
                color = semanticColors.text
            )
            Text(
                text = stringResource(R.string.auto_edit_description),
                style = MaterialTheme.typography.bodyMedium,
                color = semanticColors.subtext
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AutoEditInfoCard(
                    label = stringResource(R.string.auto_edit_info_clips),
                    value = clipCount.toString(),
                    icon = Icons.Default.Videocam,
                    accent = ClearCutAccents.Blue,
                    modifier = Modifier.weight(1f)
                )
                AutoEditInfoCard(
                    label = stringResource(R.string.auto_edit_info_music),
                    value = if (hasAudio) stringResource(R.string.auto_edit_info_yes) else stringResource(R.string.auto_edit_info_no),
                    icon = Icons.Default.MusicNote,
                    accent = if (hasAudio) ClearCutAccents.Green else semanticColors.overlayStrong,
                    modifier = Modifier.weight(1f)
                )
                AutoEditInfoCard(
                    label = stringResource(R.string.auto_edit_info_target),
                    value = stringResource(R.string.auto_edit_duration_seconds, targetDurationMs / 1_000L),
                    icon = Icons.Default.Timer,
                    accent = ClearCutAccents.Peach,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        PremiumPanelCard(accent = ClearCutAccents.Blue) {
            Text(
                text = stringResource(R.string.auto_edit_intent_title),
                style = MaterialTheme.typography.titleMedium,
                color = semanticColors.text
            )
            Text(
                text = stringResource(R.string.auto_edit_brief_unsupported),
                style = MaterialTheme.typography.bodyMedium,
                color = semanticColors.subtext
            )
            AutoEditIntent.entries.forEach { option ->
                FilterChip(
                    selected = intent == option,
                    onClick = { intent = option },
                    enabled = !isProcessing,
                    label = { Text(stringResource(option.labelResource())) }
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(30_000L, 60_000L, 90_000L).forEach { duration ->
                    FilterChip(
                        selected = targetDurationMs == duration,
                        onClick = { targetDurationMs = duration },
                        enabled = !isProcessing,
                        label = { Text(stringResource(R.string.auto_edit_duration_seconds, duration / 1_000L)) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (proposal != null) {
            PremiumPanelCard(accent = ClearCutAccents.Green) {
                Text(
                    text = stringResource(R.string.auto_edit_review_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = semanticColors.text
                )
                Text(
                    text = stringResource(
                        R.string.auto_edit_review_summary,
                        proposal.segments.size,
                        proposal.plannedDurationMs / 1_000f,
                        proposal.confidence * 100f
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = semanticColors.subtext
                )
                proposal.segments.forEachIndexed { index, segment ->
                    Text(
                        text = stringResource(
                            R.string.auto_edit_review_segment,
                            index + 1,
                            segment.clipIndex + 1,
                            formatAutoEditTime(segment.trimStartMs),
                            formatAutoEditTime(segment.trimEndMs),
                            segment.timelineEndMs - segment.timelineStartMs,
                            segment.score * 100f
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = semanticColors.text
                    )
                    Text(
                        text = segment.rationale.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = semanticColors.subtext
                    )
                    Text(
                        text = stringResource(
                            R.string.auto_edit_review_scores,
                            segment.scoreComponents.visualQuality * 100f,
                            segment.scoreComponents.motion * 100f,
                            segment.scoreComponents.subjectPresence * 100f,
                            segment.scoreComponents.audioEnergy * 100f
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = semanticColors.overlayStrong
                    )
                }
                if (proposal.warnings.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.auto_edit_review_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = ClearCutAccents.Peach
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.auto_edit_discard))
                    }
                    Button(onClick = onApply, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.auto_edit_apply))
                    }
                }
            }
        } else {
        PremiumPanelCard(accent = if (intent == AutoEditIntent.BEAT_SYNC && hasAudio) ClearCutAccents.Green else ClearCutAccents.Peach) {
            Text(
                text = stringResource(R.string.auto_edit_generate_title),
                style = MaterialTheme.typography.titleMedium,
                color = semanticColors.text
            )
            Text(
                text = if (intent == AutoEditIntent.BEAT_SYNC && hasAudio) {
                    stringResource(R.string.auto_edit_generate_with_audio)
                } else if (intent == AutoEditIntent.BEAT_SYNC) {
                    stringResource(R.string.auto_edit_generate_no_audio)
                } else {
                    stringResource(R.string.auto_edit_generate_visual)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = semanticColors.subtext
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PremiumPanelPill(
                    text = stringResource(R.string.auto_edit_clip_count, clipCount),
                    accent = ClearCutAccents.Blue
                )
                PremiumPanelPill(
                    text = if (hasAudio) stringResource(R.string.auto_edit_audio_ready) else stringResource(R.string.auto_edit_no_soundtrack),
                    accent = if (hasAudio) ClearCutAccents.Green else ClearCutAccents.Peach
                )
            }

            Button(
                onClick = { onGenerate(intent, targetDurationMs) },
                enabled = clipCount > 0 && !isProcessing,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ClearCutAccents.Mauve,
                    contentColor = semanticColors.surfaceBase,
                    disabledContainerColor = semanticColors.surface,
                    disabledContentColor = semanticColors.subtext
                ),
                shape = RoundedCornerShape(18.dp)
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = semanticColors.surfaceBase,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.panel_auto_edit_generating))
                } else {
                    Icon(
                        imageVector = Icons.Default.AutoFixHigh,
                        contentDescription = stringResource(R.string.cd_auto_edit_generate)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.auto_edit_start))
                }
            }

            if (intent == AutoEditIntent.BEAT_SYNC && !hasAudio) {
                Text(
                    text = stringResource(R.string.panel_auto_edit_add_music_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = semanticColors.subtext
                )
            }
            if (isProcessing) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.auto_edit_cancel_analysis))
                }
            }
        }
        }
    }
}

private fun AutoEditIntent.labelResource(): Int = when (this) {
    AutoEditIntent.HIGHLIGHT_REEL -> R.string.auto_edit_intent_highlight
    AutoEditIntent.SOURCE_ORDER -> R.string.auto_edit_intent_source_order
    AutoEditIntent.BEAT_SYNC -> R.string.auto_edit_intent_beat_sync
}

private fun formatAutoEditTime(timeMs: Long): String =
    "%d:%02d.%03d".format(timeMs / 60_000L, (timeMs / 1_000L) % 60L, timeMs % 1_000L)

@Composable
private fun AutoEditInfoCard(
    label: String,
    value: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val semanticColors = LocalClearCutColors.current
    PremiumPanelCard(accent = accent, modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = accent
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = semanticColors.text
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = semanticColors.subtext
            )
        }
    }
}

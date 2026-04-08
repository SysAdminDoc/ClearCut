package com.novacut.editor.ui.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.novacut.editor.R
import com.novacut.editor.engine.TtsEngine
import com.novacut.editor.ui.theme.Mocha

@Composable
fun TtsPanel(
    isAvailable: Boolean,
    isSynthesizing: Boolean,
    onSynthesize: (String, TtsEngine.VoiceStyle) -> Unit,
    onPreview: (String, TtsEngine.VoiceStyle) -> Unit,
    onStopPreview: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var text by remember { mutableStateOf("") }
    var selectedStyle by remember { mutableStateOf(TtsEngine.VoiceStyle.NARRATOR) }

    PremiumEditorPanel(
        title = stringResource(R.string.tts_title),
        subtitle = "Turn hooks, explainers, and voiceover notes into a timeline-ready read without leaving the edit.",
        icon = Icons.Default.RecordVoiceOver,
        accent = Mocha.Mauve,
        onClose = {
            onStopPreview()
            onClose()
        },
        modifier = modifier.heightIn(max = 560.dp),
        scrollable = true,
        headerActions = {
            if (text.isNotBlank()) {
                PremiumPanelIconButton(
                    icon = Icons.Default.Clear,
                    contentDescription = stringResource(R.string.tts_clear_cd),
                    onClick = { text = "" },
                    tint = Mocha.Red
                )
            }
        }
    ) {
        if (!isAvailable) {
            PremiumPanelCard(accent = Mocha.Red) {
                Text(
                    text = "Voiceover unavailable",
                    color = Mocha.Text,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.panel_tts_not_available),
                    color = Mocha.Subtext0,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            return@PremiumEditorPanel
        }

        PremiumPanelCard(accent = Mocha.Mauve) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PremiumPanelPill(
                    text = if (isSynthesizing) "Generating" else "On-device",
                    accent = if (isSynthesizing) Mocha.Peach else Mocha.Green
                )
                PremiumPanelPill(
                    text = selectedStyle.displayName,
                    accent = Mocha.Sapphire
                )
                PremiumPanelPill(
                    text = "${text.length} chars",
                    accent = Mocha.Yellow
                )
            }

            Text(
                text = "Script",
                color = Mocha.Rosewater,
                style = MaterialTheme.typography.labelLarge
            )
            Text(
                text = "Draft narration, read a CTA aloud, or rough in guide VO before you record the final take.",
                color = Mocha.Subtext0,
                style = MaterialTheme.typography.bodyMedium
            )

            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = {
                    Text(
                        text = stringResource(R.string.tts_enter_text),
                        color = Mocha.Overlay0
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 180.dp),
                maxLines = 6,
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Mocha.Mauve,
                    unfocusedBorderColor = Mocha.CardStroke,
                    focusedTextColor = Mocha.Text,
                    unfocusedTextColor = Mocha.Text,
                    cursorColor = Mocha.Mauve,
                    focusedContainerColor = Mocha.Panel,
                    unfocusedContainerColor = Mocha.Panel
                )
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        PremiumPanelCard(accent = Mocha.Sapphire) {
            Text(
                text = "Voice direction",
                color = Mocha.Rosewater,
                style = MaterialTheme.typography.labelLarge
            )
            Text(
                text = "Each style shifts cadence and pitch so you can quickly audition different delivery moods.",
                color = Mocha.Subtext0,
                style = MaterialTheme.typography.bodyMedium
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(TtsEngine.VoiceStyle.entries.toList()) { style ->
                    VoiceStyleCard(
                        style = style,
                        selected = style == selectedStyle,
                        onClick = { selectedStyle = style }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        PremiumPanelCard(accent = Mocha.Blue) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PremiumPanelPill(
                    text = stringResource(R.string.tts_speed_format, selectedStyle.rate),
                    accent = Mocha.Sky
                )
                PremiumPanelPill(
                    text = stringResource(R.string.tts_pitch_format, selectedStyle.pitch),
                    accent = Mocha.Pink
                )
            }

            Text(
                text = "Delivery",
                color = Mocha.Rosewater,
                style = MaterialTheme.typography.labelLarge
            )
            Text(
                text = "Preview the pacing first, then generate a voiceover file and drop it onto the timeline as a fresh clip.",
                color = Mocha.Subtext0,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = stringResource(R.string.panel_tts_piper_hint),
                color = Mocha.Overlay1,
                style = MaterialTheme.typography.bodySmall
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { if (text.isNotBlank()) onPreview(text, selectedStyle) },
                    enabled = text.isNotBlank() && !isSynthesizing,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Mocha.Mauve
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (text.isNotBlank()) Mocha.Mauve else Mocha.CardStroke
                    ),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = stringResource(R.string.cd_tts_preview),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.panel_tts_preview),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Button(
                    onClick = { if (text.isNotBlank()) onSynthesize(text, selectedStyle) },
                    enabled = text.isNotBlank() && !isSynthesizing,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Mocha.Mauve,
                        contentColor = Mocha.Base
                    ),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    if (isSynthesizing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Mocha.Base,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.tts_generating))
                    } else {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Default.RecordVoiceOver,
                            contentDescription = stringResource(R.string.tts_generate_icon_cd),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.tts_generate))
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceStyleCard(
    style: TtsEngine.VoiceStyle,
    selected: Boolean,
    onClick: () -> Unit
) {
    val accent = if (selected) Mocha.Mauve else Mocha.Sapphire

    Surface(
        onClick = onClick,
        modifier = Modifier.width(224.dp),
        color = if (selected) accent.copy(alpha = 0.12f) else Mocha.PanelHighest,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(
            1.dp,
            if (selected) accent.copy(alpha = 0.3f) else Mocha.CardStroke
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = style.displayName,
                color = if (selected) accent else Mocha.Text,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = voiceStyleDescription(style),
                color = Mocha.Subtext0,
                style = MaterialTheme.typography.bodySmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PremiumPanelPill(
                    text = stringResource(R.string.tts_speed_format, style.rate),
                    accent = Mocha.Sky
                )
                PremiumPanelPill(
                    text = stringResource(R.string.tts_pitch_format, style.pitch),
                    accent = Mocha.Pink
                )
            }
        }
    }
}

private fun voiceStyleDescription(style: TtsEngine.VoiceStyle): String = when (style) {
    TtsEngine.VoiceStyle.NARRATOR -> "Balanced documentary pacing."
    TtsEngine.VoiceStyle.CASUAL -> "Natural creator-style delivery."
    TtsEngine.VoiceStyle.ENERGETIC -> "Fast promo cadence for hooks."
    TtsEngine.VoiceStyle.DEEP -> "Lower, steadier voiceover tone."
    TtsEngine.VoiceStyle.SOFT -> "Gentle read for reflective moments."
    TtsEngine.VoiceStyle.FAST -> "Quick read for short-form cutdowns."
    TtsEngine.VoiceStyle.SLOW -> "Clearer spacing for explainers and tutorials."
    TtsEngine.VoiceStyle.DRAMATIC -> "Slower cinematic read with more weight."
}

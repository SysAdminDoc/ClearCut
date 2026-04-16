package com.novacut.editor.ui.editor

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.novacut.editor.R
import com.novacut.editor.engine.TtsEngine
import com.novacut.editor.ui.theme.Mocha

@OptIn(ExperimentalLayoutApi::class)
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
    val preparedText = text.trim()
    val hasScript = preparedText.isNotEmpty()

    PremiumEditorPanel(
        title = stringResource(R.string.tts_title),
        subtitle = stringResource(R.string.panel_tts_subtitle),
        icon = Icons.Default.RecordVoiceOver,
        accent = Mocha.Mauve,
        onClose = {
            onStopPreview()
            onClose()
        },
        modifier = modifier.heightIn(max = 560.dp),
        scrollable = true,
        closeContentDescription = stringResource(R.string.tts_close_cd),
        headerActions = {
            if (hasScript) {
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
                    text = stringResource(R.string.panel_tts_unavailable_title),
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
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PremiumPanelPill(
                    text = if (isSynthesizing) {
                        stringResource(R.string.panel_tts_status_generating)
                    } else {
                        stringResource(R.string.panel_tts_status_ready)
                    },
                    accent = if (isSynthesizing) Mocha.Peach else Mocha.Green
                )
                PremiumPanelPill(
                    text = selectedStyle.displayName,
                    accent = Mocha.Sapphire
                )
                PremiumPanelPill(
                    text = stringResource(R.string.panel_tts_chars_format, preparedText.length),
                    accent = Mocha.Yellow
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.panel_tts_script_title),
                color = Mocha.Rosewater,
                style = MaterialTheme.typography.labelLarge
            )
            Text(
                text = stringResource(R.string.panel_tts_script_description),
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
                text = stringResource(R.string.panel_tts_voice_direction_title),
                color = Mocha.Rosewater,
                style = MaterialTheme.typography.labelLarge
            )
            Text(
                text = stringResource(R.string.panel_tts_voice_direction_description),
                color = Mocha.Subtext0,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(12.dp))

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val compactLayout = maxWidth < 360.dp
                val cardWidth = if (compactLayout) maxWidth else (maxWidth - 10.dp) / 2

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TtsEngine.VoiceStyle.entries.forEach { style ->
                        VoiceStyleCard(
                            style = style,
                            selected = style == selectedStyle,
                            onClick = { selectedStyle = style },
                            modifier = Modifier.width(cardWidth)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        PremiumPanelCard(accent = Mocha.Blue) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
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
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.panel_tts_delivery_title),
                color = Mocha.Rosewater,
                style = MaterialTheme.typography.labelLarge
            )
            Text(
                text = stringResource(R.string.panel_tts_delivery_description),
                color = Mocha.Subtext0,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = stringResource(R.string.panel_tts_piper_hint),
                color = Mocha.Overlay1,
                style = MaterialTheme.typography.bodySmall
            )
            if (!hasScript) {
                Text(
                    text = stringResource(R.string.panel_tts_empty_hint),
                    color = Mocha.Subtext0,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(modifier = Modifier.height(10.dp))

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val compactLayout = maxWidth < 420.dp
                if (compactLayout) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        TtsPreviewButton(
                            enabled = hasScript && !isSynthesizing,
                            onClick = { if (hasScript) onPreview(preparedText, selectedStyle) }
                        )
                        TtsGenerateButton(
                            enabled = hasScript && !isSynthesizing,
                            isSynthesizing = isSynthesizing,
                            onClick = { if (hasScript) onSynthesize(preparedText, selectedStyle) }
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TtsPreviewButton(
                            enabled = hasScript && !isSynthesizing,
                            onClick = { if (hasScript) onPreview(preparedText, selectedStyle) },
                            modifier = Modifier.weight(1f)
                        )
                        TtsGenerateButton(
                            enabled = hasScript && !isSynthesizing,
                            isSynthesizing = isSynthesizing,
                            onClick = { if (hasScript) onSynthesize(preparedText, selectedStyle) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VoiceStyleCard(
    style: TtsEngine.VoiceStyle,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = if (selected) Mocha.Mauve else Mocha.Sapphire

    Surface(
        onClick = onClick,
        modifier = modifier,
        color = if (selected) accent.copy(alpha = 0.12f) else Mocha.PanelHighest,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(
            1.dp,
            if (selected) accent.copy(alpha = 0.3f) else Mocha.CardStroke
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = style.displayName,
                color = if (selected) accent else Mocha.Text,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(voiceStyleDescriptionRes(style)),
                color = Mocha.Subtext0,
                style = MaterialTheme.typography.bodySmall
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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

@Composable
private fun TtsPreviewButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = Mocha.Mauve
        ),
        border = BorderStroke(
            1.dp,
            if (enabled) Mocha.Mauve else Mocha.CardStroke
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
}

@Composable
private fun TtsGenerateButton(
    enabled: Boolean,
    isSynthesizing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
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

@StringRes
private fun voiceStyleDescriptionRes(style: TtsEngine.VoiceStyle): Int = when (style) {
    TtsEngine.VoiceStyle.NARRATOR -> R.string.tts_style_narrator_desc
    TtsEngine.VoiceStyle.CASUAL -> R.string.tts_style_casual_desc
    TtsEngine.VoiceStyle.ENERGETIC -> R.string.tts_style_energetic_desc
    TtsEngine.VoiceStyle.DEEP -> R.string.tts_style_deep_desc
    TtsEngine.VoiceStyle.SOFT -> R.string.tts_style_soft_desc
    TtsEngine.VoiceStyle.FAST -> R.string.tts_style_fast_desc
    TtsEngine.VoiceStyle.SLOW -> R.string.tts_style_slow_desc
    TtsEngine.VoiceStyle.DRAMATIC -> R.string.tts_style_dramatic_desc
}

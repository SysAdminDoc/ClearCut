package com.novacut.editor.ui.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.novacut.editor.R
import com.novacut.editor.ui.theme.Mocha

enum class NormalizationMode(val labelResId: Int, val targetLufs: Float) {
    YOUTUBE(R.string.audio_norm_youtube, -14f),
    TIKTOK(R.string.audio_norm_tiktok, -14f),
    PODCAST(R.string.audio_norm_podcast, -16f),
    BROADCAST(R.string.audio_norm_broadcast, -23f),
    CINEMA(R.string.audio_norm_cinema, -24f),
    LOUD(R.string.audio_norm_loud, -9f),
    CUSTOM(R.string.audio_norm_custom, -14f)
}

@Composable
fun AudioNormPanel(
    currentVolume: Float,
    onNormalize: (Float) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedMode by remember { mutableStateOf(NormalizationMode.YOUTUBE) }
    var customLufs by remember { mutableFloatStateOf(-14f) }
    val targetLufs = if (selectedMode == NormalizationMode.CUSTOM) customLufs else selectedMode.targetLufs

    PremiumEditorPanel(
        title = stringResource(R.string.audio_norm_title),
        subtitle = "Match clip loudness to the delivery target before you export or stack more effects.",
        icon = Icons.Default.GraphicEq,
        accent = Mocha.Mauve,
        onClose = onClose,
        modifier = modifier,
        scrollable = true
    ) {
        PremiumPanelCard(accent = Mocha.Mauve) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Loudness target",
                        style = MaterialTheme.typography.titleMedium,
                        color = Mocha.Text
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Choose a delivery profile and NovaCut will rebalance the selected clip around that LUFS target.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Mocha.Subtext0
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PremiumPanelPill(
                        text = "${(currentVolume * 100f).toInt()}% current",
                        accent = Mocha.Blue
                    )
                    PremiumPanelPill(
                        text = formatLufs(targetLufs),
                        accent = Mocha.Mauve
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        PremiumPanelCard(accent = Mocha.Blue) {
            Text(
                text = "Normalization profiles",
                style = MaterialTheme.typography.titleMedium,
                color = Mocha.Text
            )
            Text(
                text = stringResource(R.string.audio_norm_description),
                style = MaterialTheme.typography.bodyMedium,
                color = Mocha.Subtext0
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                NormalizationMode.entries.forEach { mode ->
                    NormalizationModeRow(
                        mode = mode,
                        selected = selectedMode == mode,
                        onSelect = { selectedMode = mode }
                    )
                }
            }
        }

        if (selectedMode == NormalizationMode.CUSTOM) {
            Spacer(modifier = Modifier.height(12.dp))

            PremiumPanelCard(accent = Mocha.Peach) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.audio_norm_target),
                            style = MaterialTheme.typography.titleMedium,
                            color = Mocha.Text
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Dial the exact target when you are matching an existing delivery spec or audio chain.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Mocha.Subtext0
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))
                    PremiumPanelPill(
                        text = formatLufs(customLufs),
                        accent = Mocha.Peach
                    )
                }

                Slider(
                    value = customLufs,
                    onValueChange = { customLufs = it },
                    valueRange = -30f..-5f,
                    colors = SliderDefaults.colors(
                        thumbColor = Mocha.Peach,
                        activeTrackColor = Mocha.Peach.copy(alpha = 0.7f),
                        inactiveTrackColor = Mocha.Surface1
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("-30 LUFS", style = MaterialTheme.typography.labelMedium, color = Mocha.Subtext0)
                    Text("-5 LUFS", style = MaterialTheme.typography.labelMedium, color = Mocha.Subtext0)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        PremiumPanelCard(accent = Mocha.Green) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Apply normalization",
                        style = MaterialTheme.typography.titleMedium,
                        color = Mocha.Text
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "This keeps your level strategy aligned before you export, publish, or mix against music.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Mocha.Subtext0
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))
                PremiumPanelPill(
                    text = stringResource(selectedMode.labelResId),
                    accent = Mocha.Green
                )
            }

            Button(
                onClick = { onNormalize(targetLufs) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Mocha.Mauve),
                shape = RoundedCornerShape(18.dp)
            ) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Default.Equalizer,
                    contentDescription = stringResource(R.string.cd_equalizer)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.audio_norm_normalize_button))
            }
        }
    }
}

@Composable
private fun NormalizationModeRow(
    mode: NormalizationMode,
    selected: Boolean,
    onSelect: () -> Unit
) {
    val accent = when (mode) {
        NormalizationMode.CUSTOM -> Mocha.Peach
        NormalizationMode.LOUD -> Mocha.Red
        NormalizationMode.BROADCAST, NormalizationMode.CINEMA -> Mocha.Blue
        else -> Mocha.Mauve
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (selected) accent.copy(alpha = 0.14f) else Mocha.PanelRaised,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(
            1.dp,
            if (selected) accent.copy(alpha = 0.28f) else Mocha.CardStroke
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onSelect)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RadioButton(
                    selected = selected,
                    onClick = onSelect,
                    colors = RadioButtonDefaults.colors(selectedColor = accent)
                )
                Column {
                    Text(
                        text = stringResource(mode.labelResId),
                        style = MaterialTheme.typography.titleSmall,
                        color = Mocha.Text
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (mode == NormalizationMode.CUSTOM) {
                            "Set a manual loudness target"
                        } else {
                            "Recommended for ${stringResource(mode.labelResId).lowercase()}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Mocha.Subtext0
                    )
                }
            }

            if (mode != NormalizationMode.CUSTOM) {
                PremiumPanelPill(
                    text = formatLufs(mode.targetLufs),
                    accent = accent
                )
            }
        }
    }
}

private fun formatLufs(value: Float): String = "${value.toInt()} LUFS"

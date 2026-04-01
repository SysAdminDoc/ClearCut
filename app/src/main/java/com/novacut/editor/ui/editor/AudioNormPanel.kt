package com.novacut.editor.ui.editor

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Mocha.Crust, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.audio_norm_title), color = Mocha.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, stringResource(R.string.close), tint = Mocha.Subtext0, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.audio_norm_description),
            color = Mocha.Subtext0,
            fontSize = 11.sp
        )

        Spacer(Modifier.height(12.dp))

        // Mode selector
        NormalizationMode.entries.forEach { mode ->
            val selected = selectedMode == mode
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selected) Mocha.Mauve.copy(alpha = 0.15f) else Color.Transparent)
                    .clickable { selectedMode = mode }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RadioButton(
                        selected = selected,
                        onClick = { selectedMode = mode },
                        colors = RadioButtonDefaults.colors(selectedColor = Mocha.Mauve)
                    )
                    Column {
                        Text(
                            stringResource(mode.labelResId),
                            color = if (selected) Mocha.Text else Mocha.Subtext0,
                            fontSize = 13.sp
                        )
                    }
                }
                if (mode != NormalizationMode.CUSTOM) {
                    Text(
                        "${mode.targetLufs} LUFS",
                        color = if (selected) Mocha.Mauve else Mocha.Subtext0,
                        fontSize = 11.sp
                    )
                }
            }
        }

        // Custom LUFS slider
        if (selectedMode == NormalizationMode.CUSTOM) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.audio_norm_target), color = Mocha.Subtext0, fontSize = 11.sp, modifier = Modifier.width(50.dp))
                Slider(
                    value = customLufs,
                    onValueChange = { customLufs = it },
                    valueRange = -30f..-5f,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = Mocha.Mauve,
                        activeTrackColor = Mocha.Mauve.copy(alpha = 0.6f),
                        inactiveTrackColor = Mocha.Surface0
                    )
                )
                Text(
                    "%.0f LUFS".format(customLufs),
                    color = Mocha.Mauve,
                    fontSize = 11.sp,
                    modifier = Modifier.width(60.dp)
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Current level info
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Mocha.Surface0, RoundedCornerShape(8.dp))
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(stringResource(R.string.audio_norm_current_volume), color = Mocha.Subtext0, fontSize = 12.sp)
            Text("%.0f%%".format(currentVolume * 100f), color = Mocha.Text, fontSize = 12.sp)
        }

        Spacer(Modifier.height(8.dp))

        // Apply button
        Button(
            onClick = {
                val targetLufs = if (selectedMode == NormalizationMode.CUSTOM) customLufs else selectedMode.targetLufs
                onNormalize(targetLufs)
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Mocha.Mauve),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(Icons.Default.Equalizer, stringResource(R.string.cd_equalizer), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.audio_norm_normalize_button))
        }
    }
}

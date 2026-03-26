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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novacut.editor.ui.theme.Mocha

data class PipPreset(
    val name: String,
    val posX: Float,
    val posY: Float,
    val scaleX: Float,
    val scaleY: Float
)

val pipPresets = listOf(
    PipPreset("Top Left", -0.55f, -0.55f, 0.35f, 0.35f),
    PipPreset("Top Right", 0.55f, -0.55f, 0.35f, 0.35f),
    PipPreset("Bottom Left", -0.55f, 0.55f, 0.35f, 0.35f),
    PipPreset("Bottom Right", 0.55f, 0.55f, 0.35f, 0.35f),
    PipPreset("Center Small", 0f, 0f, 0.4f, 0.4f),
    PipPreset("Left Half", -0.5f, 0f, 0.5f, 1f),
    PipPreset("Right Half", 0.5f, 0f, 0.5f, 1f),
    PipPreset("Top Half", 0f, -0.5f, 1f, 0.5f),
    PipPreset("Bottom Half", 0f, 0.5f, 1f, 0.5f),
    PipPreset("Full Screen", 0f, 0f, 1f, 1f),
    PipPreset("Lower Third", 0f, 0.6f, 0.8f, 0.25f),
    PipPreset("Circle Cam", 0.6f, -0.6f, 0.25f, 0.25f)
)

@Composable
fun PipPresetsPanel(
    onPresetSelected: (PipPreset) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
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
            Text("Picture-in-Picture", color = Mocha.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, "Close", tint = Mocha.Subtext0, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(Modifier.height(8.dp))

        // Preset grid
        val rows = pipPresets.chunked(4)
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { preset ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Mocha.Surface0)
                            .clickable { onPresetSelected(preset) }
                            .padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Mini preview
                        Canvas(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Mocha.Base)
                        ) {
                            // Main frame
                            drawRect(
                                Mocha.Subtext0.copy(alpha = 0.2f),
                                Offset(2f, 2f),
                                Size(size.width - 4f, size.height - 4f),
                                style = Stroke(1f)
                            )
                            // PiP position
                            val pipW = size.width * preset.scaleX * 0.8f
                            val pipH = size.height * preset.scaleY * 0.8f
                            val pipX = size.width / 2f + preset.posX * size.width / 2f * 0.8f - pipW / 2f
                            val pipY = size.height / 2f + preset.posY * size.height / 2f * 0.8f - pipH / 2f
                            drawRect(
                                Mocha.Mauve.copy(alpha = 0.4f),
                                Offset(pipX, pipY),
                                Size(pipW, pipH)
                            )
                            drawRect(
                                Mocha.Mauve,
                                Offset(pipX, pipY),
                                Size(pipW, pipH),
                                style = Stroke(1f)
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(preset.name, color = Mocha.Subtext0, fontSize = 8.sp, maxLines = 1)
                    }
                }
                // Fill remaining slots
                repeat(4 - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

// --- Chroma Key Refinement Panel ---

@Composable
fun ChromaKeyPanel(
    similarity: Float,
    smoothness: Float,
    spillSuppression: Float,
    keyColorR: Float,
    keyColorG: Float,
    keyColorB: Float,
    onSimilarityChanged: (Float) -> Unit,
    onSmoothnessChanged: (Float) -> Unit,
    onSpillChanged: (Float) -> Unit,
    onKeyColorChanged: (Float, Float, Float) -> Unit,
    onShowAlphaMatte: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
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
            Text("Chroma Key", color = Mocha.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Row {
                IconButton(onClick = onShowAlphaMatte, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Visibility, "Alpha Matte", tint = Mocha.Peach, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, "Close", tint = Mocha.Subtext0, modifier = Modifier.size(18.dp))
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Key color presets
        Text("Key Color", color = Mocha.Subtext0, fontSize = 11.sp)
        Row(
            modifier = Modifier.padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Green screen
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF00FF00))
                    .then(
                        if (keyColorG > 0.8f && keyColorR < 0.3f && keyColorB < 0.3f)
                            Modifier.border(2.dp, Mocha.Mauve, RoundedCornerShape(6.dp))
                        else Modifier
                    )
                    .clickable { onKeyColorChanged(0f, 1f, 0f) },
                contentAlignment = Alignment.Center
            ) {
                Text("G", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            // Blue screen
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF0044FF))
                    .then(
                        if (keyColorB > 0.8f && keyColorR < 0.3f && keyColorG < 0.3f)
                            Modifier.border(2.dp, Mocha.Mauve, RoundedCornerShape(6.dp))
                        else Modifier
                    )
                    .clickable { onKeyColorChanged(0f, 0f, 1f) },
                contentAlignment = Alignment.Center
            ) {
                Text("B", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            // Red screen
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFFFF0000))
                    .clickable { onKeyColorChanged(1f, 0f, 0f) },
                contentAlignment = Alignment.Center
            ) {
                Text("R", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(4.dp))

        // Key color RGB sliders
        ChromaSlider("Red", keyColorR, Color(0xFFF38BA8)) { onKeyColorChanged(it, keyColorG, keyColorB) }
        ChromaSlider("Green", keyColorG, Color(0xFFA6E3A1)) { onKeyColorChanged(keyColorR, it, keyColorB) }
        ChromaSlider("Blue", keyColorB, Color(0xFF89B4FA)) { onKeyColorChanged(keyColorR, keyColorG, it) }

        Spacer(Modifier.height(8.dp))

        // Refinement controls
        Text("Refinement", color = Mocha.Subtext0, fontSize = 11.sp)
        ChromaSlider("Similarity", similarity, Mocha.Mauve, onSimilarityChanged)
        ChromaSlider("Smoothness", smoothness, Mocha.Mauve, onSmoothnessChanged)
        ChromaSlider("Spill Suppress", spillSuppression, Mocha.Mauve, onSpillChanged)
    }
}

@Composable
private fun ChromaSlider(
    label: String,
    value: Float,
    color: Color,
    onChanged: (Float) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Mocha.Subtext0, fontSize = 10.sp, modifier = Modifier.width(80.dp))
        Slider(
            value = value,
            onValueChange = onChanged,
            valueRange = 0f..1f,
            modifier = Modifier
                .weight(1f)
                .height(24.dp),
            colors = SliderDefaults.colors(
                thumbColor = color,
                activeTrackColor = color.copy(alpha = 0.6f),
                inactiveTrackColor = Mocha.Surface0
            )
        )
        Text("%.2f".format(value), color = Mocha.Subtext0, fontSize = 9.sp, modifier = Modifier.width(30.dp))
    }
}

package com.novacut.editor.ui.editor

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.FormatAlignRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novacut.editor.R
import com.novacut.editor.model.*
import com.novacut.editor.ui.theme.Mocha

@Composable
fun TextEditorSheet(
    existingOverlay: TextOverlay? = null,
    playheadMs: Long,
    onSave: (TextOverlay) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val defaultText = stringResource(R.string.text_editor_your_text)
    var text by remember { mutableStateOf(existingOverlay?.text ?: defaultText) }
    var fontSize by remember { mutableFloatStateOf(existingOverlay?.fontSize ?: 48f) }
    var bold by remember { mutableStateOf(existingOverlay?.bold ?: false) }
    var italic by remember { mutableStateOf(existingOverlay?.italic ?: false) }
    var alignment by remember { mutableStateOf(existingOverlay?.alignment ?: TextAlignment.CENTER) }
    var selectedColor by remember { mutableLongStateOf(existingOverlay?.color ?: 0xFFFFFFFF) }
    var animIn by remember { mutableStateOf(existingOverlay?.animationIn ?: TextAnimation.FADE) }
    var animOut by remember { mutableStateOf(existingOverlay?.animationOut ?: TextAnimation.FADE) }
    var fontFamily by remember { mutableStateOf(existingOverlay?.fontFamily ?: "sans-serif") }
    var duration by remember { mutableFloatStateOf((existingOverlay?.let { it.endTimeMs - it.startTimeMs } ?: 3000L).toFloat()) }
    var positionX by remember { mutableFloatStateOf(existingOverlay?.positionX ?: 0.5f) }
    var positionY by remember { mutableFloatStateOf(existingOverlay?.positionY ?: 0.5f) }
    var shadowOffsetX by remember { mutableFloatStateOf(existingOverlay?.shadowOffsetX ?: 0f) }
    var shadowOffsetY by remember { mutableFloatStateOf(existingOverlay?.shadowOffsetY ?: 0f) }
    var shadowBlur by remember { mutableFloatStateOf(existingOverlay?.shadowBlur ?: 0f) }
    var shadowColor by remember { mutableLongStateOf(existingOverlay?.shadowColor ?: 0x80000000) }
    var glowColor by remember { mutableLongStateOf(existingOverlay?.glowColor ?: 0x00000000) }
    var glowRadius by remember { mutableFloatStateOf(existingOverlay?.glowRadius ?: 0f) }
    var letterSpacing by remember { mutableFloatStateOf(existingOverlay?.letterSpacing ?: 0f) }
    var lineHeight by remember { mutableFloatStateOf(existingOverlay?.lineHeight ?: 1.2f) }
    var textRotation by remember { mutableFloatStateOf(existingOverlay?.rotation ?: 0f) }

    val fontFamilies = listOf(
        "sans-serif" to "Sans Serif",
        "serif" to "Serif",
        "monospace" to "Monospace",
        "cursive" to "Cursive",
        "sans-serif-condensed" to "Condensed",
        "sans-serif-medium" to "Medium"
    )

    val colorOptions = listOf(
        0xFFFFFFFF, 0xFF000000, 0xFFF38BA8, 0xFFFAB387,
        0xFFF9E2AF, 0xFFA6E3A1, 0xFF89B4FA, 0xFFCBA6F7,
        0xFFF5C2E7, 0xFF94E2D5, 0xFF89DCEB, 0xFFB4BEFE
    )
    val previewFontFamily = remember(fontFamily) { previewFontFamily(fontFamily) }
    val previewTextAlign = when (alignment) {
        TextAlignment.LEFT -> TextAlign.Start
        TextAlignment.CENTER -> TextAlign.Center
        TextAlignment.RIGHT -> TextAlign.End
    }
    val previewAlignment = when (alignment) {
        TextAlignment.LEFT -> Alignment.CenterStart
        TextAlignment.CENTER -> Alignment.Center
        TextAlignment.RIGHT -> Alignment.CenterEnd
    }

    PremiumEditorPanel(
        title = stringResource(R.string.text_editor_title),
        subtitle = stringResource(R.string.panel_text_editor_subtitle),
        icon = Icons.Default.Title,
        accent = Mocha.Sapphire,
        onClose = onClose,
        modifier = modifier,
        scrollable = true,
        headerActions = {
            Button(
                onClick = {
                    val overlay = TextOverlay(
                        id = existingOverlay?.id ?: java.util.UUID.randomUUID().toString(),
                        text = text,
                        fontSize = fontSize,
                        fontFamily = fontFamily,
                        color = selectedColor,
                        bold = bold,
                        italic = italic,
                        alignment = alignment,
                        strokeWidth = 0f,
                        strokeColor = 0xFF000000,
                        startTimeMs = existingOverlay?.startTimeMs ?: playheadMs,
                        endTimeMs = (existingOverlay?.startTimeMs ?: playheadMs) + duration.toLong(),
                        animationIn = animIn,
                        animationOut = animOut,
                        positionX = positionX,
                        positionY = positionY,
                        rotation = textRotation,
                        shadowColor = shadowColor,
                        shadowOffsetX = shadowOffsetX,
                        shadowOffsetY = shadowOffsetY,
                        shadowBlur = shadowBlur,
                        glowColor = glowColor,
                        glowRadius = glowRadius,
                        letterSpacing = letterSpacing,
                        lineHeight = lineHeight
                    )
                    onSave(overlay)
                },
                enabled = text.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Mocha.Rosewater,
                    contentColor = Mocha.Midnight,
                    disabledContainerColor = Mocha.PanelHighest,
                    disabledContentColor = Mocha.Subtext0
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.text_editor_save),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    ) {
        PremiumPanelCard(accent = Mocha.Sapphire) {
            Text(
                text = stringResource(R.string.panel_text_editor_preview),
                color = Mocha.Rosewater,
                style = MaterialTheme.typography.labelLarge
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Mocha.Panel.copy(alpha = 0.98f),
                                Mocha.PanelHighest,
                                Mocha.Panel
                            )
                        )
                    )
                    .padding(20.dp),
                contentAlignment = previewAlignment
            ) {
                Text(
                    text = text.ifBlank { defaultText },
                    color = Color(selectedColor),
                    fontSize = fontSize.sp,
                    fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
                    fontStyle = if (italic) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal,
                    textAlign = previewTextAlign,
                    fontFamily = previewFontFamily,
                    letterSpacing = letterSpacing.sp,
                    lineHeight = (fontSize * lineHeight).sp,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PremiumPanelPill(
                    text = "${fontSize.toInt()}pt",
                    accent = Mocha.Sapphire
                )
                PremiumPanelPill(
                    text = "${"%.1f".format(duration / 1000f)}s",
                    accent = Mocha.Peach
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        PremiumPanelCard(accent = Mocha.Mauve) {
            Text(
                text = stringResource(R.string.panel_text_editor_style),
                color = Mocha.Rosewater,
                style = MaterialTheme.typography.labelLarge
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.panel_text_editor_text_label)) },
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Mocha.Mauve,
                    unfocusedBorderColor = Mocha.CardStroke,
                    focusedTextColor = Mocha.Text,
                    unfocusedTextColor = Mocha.Text,
                    focusedLabelColor = Mocha.Mauve,
                    unfocusedLabelColor = Mocha.Subtext0,
                    cursorColor = Mocha.Mauve,
                    focusedContainerColor = Mocha.Panel,
                    unfocusedContainerColor = Mocha.Panel
                ),
                maxLines = 3
            )

            EffectSlider(stringResource(R.string.panel_text_editor_font_size), fontSize, 12f, 120f) { fontSize = it }

            Text(stringResource(R.string.text_editor_font), color = Mocha.Subtext1, style = MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(fontFamilies) { (family, label) ->
                    FilterChip(
                        onClick = { fontFamily = family },
                        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                        selected = fontFamily == family,
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = Mocha.Panel,
                            labelColor = Mocha.Text,
                            selectedContainerColor = Mocha.Mauve.copy(alpha = 0.18f),
                            selectedLabelColor = Mocha.Mauve
                        )
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    onClick = { bold = !bold },
                    label = { Text("B", fontWeight = FontWeight.Bold) },
                    selected = bold,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Mocha.Panel,
                        selectedContainerColor = Mocha.Mauve.copy(alpha = 0.18f),
                        selectedLabelColor = Mocha.Mauve
                    )
                )
                FilterChip(
                    onClick = { italic = !italic },
                    label = { Text("I") },
                    selected = italic,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Mocha.Panel,
                        selectedContainerColor = Mocha.Mauve.copy(alpha = 0.18f),
                        selectedLabelColor = Mocha.Mauve
                    )
                )
                FilterChip(
                    onClick = { alignment = TextAlignment.LEFT },
                    label = { Icon(Icons.AutoMirrored.Filled.FormatAlignLeft, stringResource(R.string.cd_align_left), modifier = Modifier.size(16.dp)) },
                    selected = alignment == TextAlignment.LEFT,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Mocha.Panel,
                        selectedContainerColor = Mocha.Mauve.copy(alpha = 0.18f),
                        selectedLabelColor = Mocha.Mauve
                    )
                )
                FilterChip(
                    onClick = { alignment = TextAlignment.CENTER },
                    label = { Icon(Icons.Default.FormatAlignCenter, stringResource(R.string.cd_align_center), modifier = Modifier.size(16.dp)) },
                    selected = alignment == TextAlignment.CENTER,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Mocha.Panel,
                        selectedContainerColor = Mocha.Mauve.copy(alpha = 0.18f),
                        selectedLabelColor = Mocha.Mauve
                    )
                )
                FilterChip(
                    onClick = { alignment = TextAlignment.RIGHT },
                    label = { Icon(Icons.AutoMirrored.Filled.FormatAlignRight, stringResource(R.string.cd_align_right), modifier = Modifier.size(16.dp)) },
                    selected = alignment == TextAlignment.RIGHT,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Mocha.Panel,
                        selectedContainerColor = Mocha.Mauve.copy(alpha = 0.18f),
                        selectedLabelColor = Mocha.Mauve
                    )
                )
            }

            Text(stringResource(R.string.text_editor_color), color = Mocha.Subtext1, style = MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(colorOptions) { color ->
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(color))
                            .then(
                                if (selectedColor == color) Modifier.border(2.dp, Mocha.Mauve, CircleShape)
                                else Modifier.border(1.dp, Mocha.CardStroke, CircleShape)
                            )
                            .clickable { selectedColor = color }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        PremiumPanelCard(accent = Mocha.Peach) {
            Text(
                text = stringResource(R.string.panel_text_editor_timing),
                color = Mocha.Rosewater,
                style = MaterialTheme.typography.labelLarge
            )
            EffectSlider(stringResource(R.string.panel_text_editor_horizontal), positionX, 0f, 1f) { positionX = it }
            EffectSlider(stringResource(R.string.panel_text_editor_vertical), positionY, 0f, 1f) { positionY = it }
            EffectSlider(stringResource(R.string.panel_text_editor_duration_seconds), duration / 1000f, 0.5f, 10f) { duration = it * 1000f }

            Text(stringResource(R.string.text_editor_enter_animation), color = Mocha.Subtext1, style = MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(TextAnimation.entries.toList()) { anim ->
                    FilterChip(
                        onClick = { animIn = anim },
                        label = { Text(anim.displayName, style = MaterialTheme.typography.labelMedium) },
                        selected = animIn == anim,
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = Mocha.Panel,
                            labelColor = Mocha.Text,
                            selectedContainerColor = Mocha.Peach.copy(alpha = 0.18f),
                            selectedLabelColor = Mocha.Peach
                        )
                    )
                }
            }

            Text(stringResource(R.string.text_editor_exit_animation), color = Mocha.Subtext1, style = MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(TextAnimation.entries.toList()) { anim ->
                    FilterChip(
                        onClick = { animOut = anim },
                        label = { Text(anim.displayName, style = MaterialTheme.typography.labelMedium) },
                        selected = animOut == anim,
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = Mocha.Panel,
                            labelColor = Mocha.Text,
                            selectedContainerColor = Mocha.Peach.copy(alpha = 0.18f),
                            selectedLabelColor = Mocha.Peach
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        PremiumPanelCard(accent = Mocha.Green) {
            Text(
                text = stringResource(R.string.panel_text_editor_appearance),
                color = Mocha.Rosewater,
                style = MaterialTheme.typography.labelLarge
            )
            EffectSlider(stringResource(R.string.panel_text_editor_shadow_x), shadowOffsetX, -10f, 10f) { shadowOffsetX = it }
            EffectSlider(stringResource(R.string.panel_text_editor_shadow_y), shadowOffsetY, -10f, 10f) { shadowOffsetY = it }
            EffectSlider(stringResource(R.string.panel_text_editor_shadow_blur), shadowBlur, 0f, 20f) { shadowBlur = it }
            EffectSlider(stringResource(R.string.panel_text_editor_glow_radius), glowRadius, 0f, 30f) { glowRadius = it }

            Text(stringResource(R.string.text_editor_glow_color), color = Mocha.Subtext1, style = MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf(
                    0x00000000L, 0xFFFFFFFF, 0xFFF38BA8, 0xFFFAB387,
                    0xFFF9E2AF, 0xFFA6E3A1, 0xFF89B4FA, 0xFFCBA6F7
                )) { color ->
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(if (color == 0x00000000L) Mocha.Panel else Color(color))
                            .then(
                                if (glowColor == color) Modifier.border(2.dp, Mocha.Green, CircleShape)
                                else Modifier.border(1.dp, Mocha.CardStroke, CircleShape)
                            )
                            .clickable { glowColor = color }
                    ) {
                        if (color == 0x00000000L) {
                            Text(
                                text = stringResource(R.string.text_editor_off),
                                color = Mocha.Subtext0,
                                fontSize = 7.sp,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }
                }
            }

            EffectSlider(stringResource(R.string.panel_text_editor_letter_spacing), letterSpacing, -5f, 20f) { letterSpacing = it }
            EffectSlider(stringResource(R.string.panel_text_editor_line_height), lineHeight, 0.8f, 3f) { lineHeight = it }
            EffectSlider(stringResource(R.string.panel_text_editor_rotation), textRotation, -180f, 180f) { textRotation = it }
        }
    }
}

private fun previewFontFamily(family: String): androidx.compose.ui.text.font.FontFamily = when (family) {
    "serif" -> androidx.compose.ui.text.font.FontFamily.Serif
    "monospace" -> androidx.compose.ui.text.font.FontFamily.Monospace
    "cursive" -> androidx.compose.ui.text.font.FontFamily.Cursive
    else -> androidx.compose.ui.text.font.FontFamily.SansSerif
}

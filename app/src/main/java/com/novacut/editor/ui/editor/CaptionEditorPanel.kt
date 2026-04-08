package com.novacut.editor.ui.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novacut.editor.R
import com.novacut.editor.model.Caption
import com.novacut.editor.model.CaptionStyle
import com.novacut.editor.model.CaptionStyleType
import com.novacut.editor.ui.theme.Mocha
import java.util.Locale

@Composable
fun CaptionEditorPanel(
    captions: List<Caption>,
    playheadMs: Long,
    clipDurationMs: Long,
    onAddCaption: (Caption) -> Unit,
    onUpdateCaption: (Caption) -> Unit,
    onDeleteCaption: (String) -> Unit,
    onGenerateAutoCaption: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var editingCaption by remember { mutableStateOf<Caption?>(null) }
    var selectedStyleType by remember { mutableStateOf(CaptionStyleType.SUBTITLE_BAR) }
    val activeCaptionCount = captions.count { playheadMs in it.startTimeMs..it.endTimeMs }

    fun createCaption() {
        val newCaption = Caption(
            text = "New Caption",
            startTimeMs = playheadMs,
            endTimeMs = (playheadMs + 2_000L).coerceAtMost(clipDurationMs),
            style = CaptionStyle(type = selectedStyleType)
        )
        onAddCaption(newCaption)
        editingCaption = newCaption
    }

    PremiumEditorPanel(
        title = stringResource(R.string.caption_title),
        subtitle = "Write, time, and style captions that feel polished instead of bolted onto the cut.",
        icon = Icons.Default.ClosedCaption,
        accent = Mocha.Yellow,
        onClose = onClose,
        modifier = modifier,
        scrollable = true,
        headerActions = {
            PremiumPanelIconButton(
                icon = Icons.Default.AutoAwesome,
                contentDescription = stringResource(R.string.cd_caption_auto),
                onClick = onGenerateAutoCaption,
                tint = Mocha.Yellow
            )
            PremiumPanelIconButton(
                icon = Icons.Default.Add,
                contentDescription = stringResource(R.string.caption_add_cd),
                onClick = ::createCaption,
                tint = Mocha.Green
            )
        }
    ) {
        PremiumPanelCard(accent = if (activeCaptionCount > 0) Mocha.Green else Mocha.Yellow) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Caption system",
                        style = MaterialTheme.typography.titleMedium,
                        color = Mocha.Text
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Manage timing, coverage, and default styling for every subtitle block on the selected clip.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Mocha.Subtext0
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PremiumPanelPill(text = "${captions.size} total", accent = Mocha.Blue)
                    PremiumPanelPill(
                        text = if (activeCaptionCount > 0) "$activeCaptionCount live" else "Ready",
                        accent = if (activeCaptionCount > 0) Mocha.Green else Mocha.Yellow
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CaptionMetric(
                    title = "Playhead",
                    value = formatSeconds(playheadMs),
                    accent = Mocha.Peach,
                    modifier = Modifier.weight(1f)
                )
                CaptionMetric(
                    title = "Default Style",
                    value = selectedStyleType.displayName,
                    accent = Mocha.Mauve,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        PremiumPanelCard(accent = Mocha.Mauve) {
            Text(
                text = "Default style for new captions",
                style = MaterialTheme.typography.titleMedium,
                color = Mocha.Text
            )
            Text(
                text = "Pick the starting treatment here, then fine-tune any individual caption below.",
                style = MaterialTheme.typography.bodyMedium,
                color = Mocha.Subtext0
            )

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CaptionStyleType.entries.forEach { styleType ->
                    FilterChip(
                        selected = styleType == selectedStyleType,
                        onClick = { selectedStyleType = styleType },
                        label = {
                            Text(
                                text = styleType.displayName,
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Mocha.Mauve.copy(alpha = 0.18f),
                            selectedLabelColor = Mocha.Mauve,
                            containerColor = Mocha.PanelRaised,
                            labelColor = Mocha.Subtext0
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        PremiumPanelCard(accent = Mocha.Blue) {
            Text(
                text = "Caption list",
                style = MaterialTheme.typography.titleMedium,
                color = Mocha.Text
            )
            Text(
                text = if (captions.isEmpty()) {
                    "Generate a pass automatically or write your first caption by hand."
                } else {
                    "Tap a caption to refine its text, timing, and placement."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Mocha.Subtext0
            )

            if (captions.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Mocha.PanelRaised,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, Mocha.CardStroke)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.caption_no_captions),
                            style = MaterialTheme.typography.titleSmall,
                            color = Mocha.Text
                        )
                        Text(
                            text = "Auto-captions are best for a quick first pass. Manual captions are great for hero text and exact pacing.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Mocha.Subtext0
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = onGenerateAutoCaption,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(18.dp),
                                border = BorderStroke(1.dp, Mocha.Yellow.copy(alpha = 0.35f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Mocha.Yellow)
                            ) {
                                androidx.compose.material3.Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = stringResource(R.string.cd_auto_awesome)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = stringResource(R.string.panel_caption_auto_generate))
                            }

                            Button(
                                onClick = ::createCaption,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(18.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Mocha.Mauve)
                            ) {
                                androidx.compose.material3.Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = stringResource(R.string.caption_add_cd)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = stringResource(R.string.caption_add))
                            }
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    captions.sortedBy { it.startTimeMs }.forEach { caption ->
                        CaptionListCard(
                            caption = caption,
                            playheadMs = playheadMs,
                            isEditing = editingCaption?.id == caption.id,
                            onEdit = { editingCaption = caption },
                            onDelete = { onDeleteCaption(caption.id) }
                        )
                    }
                }
            }
        }

        editingCaption?.let { caption ->
            Spacer(modifier = Modifier.height(12.dp))

            PremiumPanelCard(accent = Mocha.Yellow) {
                Text(
                    text = "Edit caption",
                    style = MaterialTheme.typography.titleMedium,
                    color = Mocha.Text
                )
                Text(
                    text = "Refine the line, tighten the timing, and place it exactly where it belongs.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Mocha.Subtext0
                )

                CaptionEditForm(
                    caption = caption,
                    clipDurationMs = clipDurationMs,
                    onUpdate = { updated ->
                        onUpdateCaption(updated)
                        editingCaption = updated
                    },
                    onDone = { editingCaption = null }
                )
            }
        }
    }
}

@Composable
private fun CaptionMetric(
    title: String,
    value: String,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = accent.copy(alpha = 0.12f),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.18f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = Mocha.Subtext0
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                color = accent,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun CaptionListCard(
    caption: Caption,
    playheadMs: Long,
    isEditing: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isActive = playheadMs in caption.startTimeMs..caption.endTimeMs
    val accent = when {
        isEditing -> Mocha.Mauve
        isActive -> Mocha.Green
        else -> Mocha.Blue
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
        color = if (isEditing) accent.copy(alpha = 0.12f) else Mocha.PanelRaised,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(
            1.dp,
            if (isEditing || isActive) accent.copy(alpha = 0.2f) else Mocha.CardStroke
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = caption.text,
                        style = MaterialTheme.typography.titleSmall,
                        color = Mocha.Text,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${formatSeconds(caption.startTimeMs)} - ${formatSeconds(caption.endTimeMs)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Mocha.Subtext0
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                PremiumPanelPill(
                    text = when {
                        isEditing -> "Editing"
                        isActive -> "Live"
                        else -> caption.style.type.displayName
                    },
                    accent = accent
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (caption.words.isNotEmpty()) {
                        stringResource(R.string.caption_word_count, caption.words.size)
                    } else {
                        "Manual caption"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (caption.words.isNotEmpty()) Mocha.Peach else Mocha.Subtext0
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PremiumPanelIconButton(
                        icon = Icons.Default.Edit,
                        contentDescription = stringResource(R.string.cd_caption_edit),
                        onClick = onEdit,
                        tint = Mocha.Blue
                    )
                    PremiumPanelIconButton(
                        icon = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.caption_delete_cd),
                        onClick = onDelete,
                        tint = Mocha.Red
                    )
                }
            }
        }
    }
}

@Composable
private fun CaptionEditForm(
    caption: Caption,
    clipDurationMs: Long,
    onUpdate: (Caption) -> Unit,
    onDone: () -> Unit
) {
    var text by remember(caption.id) { mutableStateOf(caption.text) }
    var startTime by remember(caption.id) { mutableFloatStateOf(caption.startTimeMs / 1000f) }
    var endTime by remember(caption.id) { mutableFloatStateOf(caption.endTimeMs / 1000f) }
    var fontSize by remember(caption.id) { mutableFloatStateOf(caption.style.fontSize) }
    var positionY by remember(caption.id) { mutableFloatStateOf(caption.style.positionY) }
    var styleType by remember(caption.id) { mutableStateOf(caption.style.type) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.caption_text_hint)) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Mocha.Mauve,
                unfocusedBorderColor = Mocha.CardStroke,
                focusedTextColor = Mocha.Text,
                unfocusedTextColor = Mocha.Text,
                cursorColor = Mocha.Mauve,
                focusedLabelColor = Mocha.Mauve,
                unfocusedLabelColor = Mocha.Subtext0
            ),
            maxLines = 3,
            textStyle = TextStyle(fontSize = 15.sp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CaptionMetric(
                title = "Start",
                value = formatSeconds((startTime * 1000f).toLong()),
                accent = Mocha.Blue,
                modifier = Modifier.weight(1f)
            )
            CaptionMetric(
                title = "End",
                value = formatSeconds((endTime * 1000f).toLong()),
                accent = Mocha.Green,
                modifier = Modifier.weight(1f)
            )
        }

        CaptionSlider(
            label = stringResource(R.string.caption_start_time),
            value = startTime,
            valueRange = 0f..(clipDurationMs / 1000f),
            accent = Mocha.Blue,
            onValueChange = { startTime = it.coerceAtMost(endTime) }
        )
        CaptionSlider(
            label = stringResource(R.string.caption_end_time),
            value = endTime,
            valueRange = 0f..(clipDurationMs / 1000f),
            accent = Mocha.Green,
            onValueChange = { endTime = it.coerceAtLeast(startTime) }
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.panel_caption_style_label),
                style = MaterialTheme.typography.labelLarge,
                color = Mocha.Subtext0
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CaptionStyleType.entries.forEach { type ->
                    FilterChip(
                        selected = type == styleType,
                        onClick = { styleType = type },
                        label = { Text(type.displayName) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Mocha.Mauve.copy(alpha = 0.18f),
                            selectedLabelColor = Mocha.Mauve,
                            containerColor = Mocha.PanelRaised,
                            labelColor = Mocha.Subtext0
                        )
                    )
                }
            }
        }

        CaptionSlider(
            label = stringResource(R.string.caption_font_size),
            value = fontSize,
            valueRange = 16f..72f,
            accent = Mocha.Mauve,
            onValueChange = { fontSize = it },
            valueFormatter = { "${it.toInt()} pt" }
        )
        CaptionSlider(
            label = stringResource(R.string.panel_caption_position_y),
            value = positionY,
            valueRange = 0.1f..0.95f,
            accent = Mocha.Yellow,
            onValueChange = { positionY = it },
            valueFormatter = { "%.0f%%".format(it * 100f) }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onDone,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, Mocha.CardStroke),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Mocha.Subtext0)
            ) {
                Text(text = stringResource(R.string.done))
            }

            Button(
                onClick = {
                    onUpdate(
                        caption.copy(
                            text = text,
                            startTimeMs = (startTime * 1000f).toLong(),
                            endTimeMs = (endTime * 1000f).toLong(),
                            style = caption.style.copy(
                                type = styleType,
                                fontSize = fontSize,
                                positionY = positionY
                            )
                        )
                    )
                    onDone()
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Mocha.Mauve),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text(text = stringResource(R.string.panel_caption_save))
            }
        }
    }
}

@Composable
private fun CaptionSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    accent: androidx.compose.ui.graphics.Color,
    onValueChange: (Float) -> Unit,
    valueFormatter: (Float) -> String = { "%.1fs".format(it) }
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = Mocha.Subtext0
            )
            PremiumPanelPill(text = valueFormatter(value), accent = accent)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
                inactiveTrackColor = Mocha.Surface1
            )
        )
    }
}

private fun formatSeconds(ms: Long): String {
    val totalSeconds = (ms / 1000f).coerceAtLeast(0f)
    return if (totalSeconds >= 60f) {
        val minutes = (totalSeconds / 60f).toInt()
        val seconds = totalSeconds % 60f
        String.format(Locale.getDefault(), "%d:%04.1f", minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%.1fs", totalSeconds)
    }
}

package com.novacut.editor.ui.editor

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.novacut.editor.R
import com.novacut.editor.model.*
import com.novacut.editor.ui.theme.Mocha

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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Mocha.Crust, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .padding(12.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.caption_title), color = Mocha.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Row {
                // Auto-generate button
                IconButton(onClick = onGenerateAutoCaption, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.AutoAwesome, "Auto Caption", tint = Mocha.Yellow, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = {
                    val newCaption = Caption(
                        text = "New Caption",
                        startTimeMs = playheadMs,
                        endTimeMs = (playheadMs + 2000L).coerceAtMost(clipDurationMs),
                        style = CaptionStyle(type = selectedStyleType)
                    )
                    onAddCaption(newCaption)
                    editingCaption = newCaption
                }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Add, stringResource(R.string.caption_add_cd), tint = Mocha.Green, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, stringResource(R.string.caption_close_cd), tint = Mocha.Subtext0, modifier = Modifier.size(18.dp))
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Style selector
        Text(stringResource(R.string.panel_caption_style), color = Mocha.Subtext0, fontSize = 11.sp)
        Spacer(Modifier.height(4.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(CaptionStyleType.entries.toList()) { styleType ->
                val selected = styleType == selectedStyleType
                FilterChip(
                    selected = selected,
                    onClick = { selectedStyleType = styleType },
                    label = { Text(styleType.displayName, fontSize = 10.sp) },
                    modifier = Modifier.height(28.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Mocha.Mauve.copy(alpha = 0.2f),
                        selectedLabelColor = Mocha.Mauve,
                        labelColor = Mocha.Subtext0
                    )
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Caption list
        if (captions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.ClosedCaption, null, tint = Mocha.Subtext0.copy(alpha = 0.3f), modifier = Modifier.size(32.dp))
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.caption_no_captions), color = Mocha.Subtext0, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onGenerateAutoCaption,
                        border = BorderStroke(1.dp, Mocha.Yellow.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.AutoAwesome, null, tint = Mocha.Yellow, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.panel_caption_auto_generate), color = Mocha.Yellow, fontSize = 12.sp)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(captions, key = { it.id }) { caption ->
                    CaptionRow(
                        caption = caption,
                        isEditing = editingCaption?.id == caption.id,
                        playheadMs = playheadMs,
                        onEdit = { editingCaption = caption },
                        onDelete = { onDeleteCaption(caption.id) }
                    )
                }
            }
        }

        // Editing panel
        editingCaption?.let { caption ->
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = Mocha.Surface1, thickness = 1.dp)
            Spacer(Modifier.height(8.dp))
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

@Composable
private fun CaptionRow(
    caption: Caption,
    isEditing: Boolean,
    playheadMs: Long,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isActive = playheadMs in caption.startTimeMs..caption.endTimeMs

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    isEditing -> Mocha.Mauve.copy(alpha = 0.15f)
                    isActive -> Mocha.Green.copy(alpha = 0.1f)
                    else -> Mocha.Surface0
                }
            )
            .clickable(onClick = onEdit)
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                caption.text,
                color = Mocha.Text,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "%.1fs - %.1fs".format(caption.startTimeMs / 1000f, caption.endTimeMs / 1000f),
                    color = Mocha.Subtext0,
                    fontSize = 10.sp
                )
                Text(
                    caption.style.type.displayName,
                    color = Mocha.Mauve.copy(alpha = 0.7f),
                    fontSize = 10.sp
                )
                if (caption.words.isNotEmpty()) {
                    Text(
                        "${caption.words.size} words",
                        color = Mocha.Peach.copy(alpha = 0.7f),
                        fontSize = 10.sp
                    )
                }
            }
        }

        Row {
            IconButton(onClick = onEdit, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Edit, "Edit", tint = Mocha.Subtext0, modifier = Modifier.size(14.dp))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Delete, stringResource(R.string.caption_delete_cd), tint = Mocha.Red.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
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

    Column(modifier = Modifier.fillMaxWidth()) {
        // Text input
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.caption_text_hint), fontSize = 12.sp) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Mocha.Mauve,
                unfocusedBorderColor = Mocha.Surface1,
                focusedTextColor = Mocha.Text,
                unfocusedTextColor = Mocha.Text,
                cursorColor = Mocha.Mauve
            ),
            maxLines = 3,
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
        )

        Spacer(Modifier.height(8.dp))

        // Timing
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.caption_start_time), color = Mocha.Subtext0, fontSize = 10.sp)
                Slider(
                    value = startTime,
                    onValueChange = { startTime = it.coerceAtMost(endTime) },
                    valueRange = 0f..(clipDurationMs / 1000f),
                    modifier = Modifier.height(24.dp),
                    colors = SliderDefaults.colors(thumbColor = Mocha.Mauve, activeTrackColor = Mocha.Mauve.copy(alpha = 0.6f))
                )
                Text("%.1fs".format(startTime), color = Mocha.Subtext0, fontSize = 9.sp)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.caption_end_time), color = Mocha.Subtext0, fontSize = 10.sp)
                Slider(
                    value = endTime,
                    onValueChange = { endTime = it.coerceAtLeast(startTime) },
                    valueRange = 0f..(clipDurationMs / 1000f),
                    modifier = Modifier.height(24.dp),
                    colors = SliderDefaults.colors(thumbColor = Mocha.Mauve, activeTrackColor = Mocha.Mauve.copy(alpha = 0.6f))
                )
                Text("%.1fs".format(endTime), color = Mocha.Subtext0, fontSize = 9.sp)
            }
        }

        // Style
        Text(stringResource(R.string.panel_caption_style_label), color = Mocha.Subtext0, fontSize = 10.sp)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            items(CaptionStyleType.entries.toList()) { type ->
                FilterChip(
                    selected = type == styleType,
                    onClick = { styleType = type },
                    label = { Text(type.displayName, fontSize = 9.sp) },
                    modifier = Modifier.height(26.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Mocha.Mauve.copy(alpha = 0.2f),
                        selectedLabelColor = Mocha.Mauve
                    )
                )
            }
        }

        // Size + Position
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.caption_font_size), color = Mocha.Subtext0, fontSize = 10.sp)
                Slider(
                    value = fontSize, onValueChange = { fontSize = it },
                    valueRange = 16f..72f, modifier = Modifier.height(24.dp),
                    colors = SliderDefaults.colors(thumbColor = Mocha.Mauve, activeTrackColor = Mocha.Mauve.copy(alpha = 0.6f))
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.panel_caption_position_y), color = Mocha.Subtext0, fontSize = 10.sp)
                Slider(
                    value = positionY, onValueChange = { positionY = it },
                    valueRange = 0.1f..0.95f, modifier = Modifier.height(24.dp),
                    colors = SliderDefaults.colors(thumbColor = Mocha.Mauve, activeTrackColor = Mocha.Mauve.copy(alpha = 0.6f))
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Save button
        Button(
            onClick = {
                onUpdate(caption.copy(
                    text = text,
                    startTimeMs = (startTime * 1000).toLong(),
                    endTimeMs = (endTime * 1000).toLong(),
                    style = caption.style.copy(
                        type = styleType,
                        fontSize = fontSize,
                        positionY = positionY
                    )
                ))
                onDone()
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Mocha.Mauve),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(stringResource(R.string.panel_caption_save))
        }
    }
}

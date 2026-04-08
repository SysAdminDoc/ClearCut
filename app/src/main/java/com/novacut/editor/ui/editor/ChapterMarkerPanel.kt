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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.novacut.editor.R
import com.novacut.editor.model.ChapterMarker
import com.novacut.editor.ui.theme.Mocha

@Composable
fun ChapterMarkerPanel(
    chapters: List<ChapterMarker>,
    playheadMs: Long,
    onAddChapter: (ChapterMarker) -> Unit,
    onUpdateChapter: (Int, ChapterMarker) -> Unit,
    onDeleteChapter: (Int) -> Unit,
    onJumpTo: (Long) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var editingIndex by remember { mutableIntStateOf(-1) }
    var editingTitle by remember { mutableStateOf("") }
    val sortedChapters = chapters.sortedBy { it.timeMs }
    val nextChapterLabel = stringResource(R.string.chapter_default_name, chapters.size + 1)

    PremiumEditorPanel(
        title = stringResource(R.string.chapter_title),
        subtitle = "Drop navigation points at the playhead so long edits feel structured and easy to skim.",
        icon = Icons.Default.Bookmarks,
        accent = Mocha.Yellow,
        onClose = onClose,
        modifier = modifier,
        scrollable = true,
        headerActions = {
            PremiumPanelIconButton(
                icon = Icons.Default.Add,
                contentDescription = stringResource(R.string.chapter_add_cd),
                onClick = { onAddChapter(ChapterMarker(playheadMs, nextChapterLabel)) },
                tint = Mocha.Green,
                containerColor = Mocha.PanelHighest
            )
        }
    ) {
        PremiumPanelCard(accent = Mocha.Yellow) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Chapter rail",
                        style = MaterialTheme.typography.titleMedium,
                        color = Mocha.Text
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.chapter_description),
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
                        text = "${chapters.size} chapters",
                        accent = Mocha.Yellow
                    )
                    PremiumPanelPill(
                        text = "Playhead ${formatChapterTimestamp(playheadMs)}",
                        accent = Mocha.Blue
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        PremiumPanelCard(accent = Mocha.Blue) {
            if (sortedChapters.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Bookmarks,
                        contentDescription = stringResource(R.string.cd_bookmarks),
                        tint = Mocha.Overlay1,
                        modifier = Modifier.size(30.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.chapter_empty),
                        style = MaterialTheme.typography.titleSmall,
                        color = Mocha.Text
                    )
                    Text(
                        text = "Use the add button to drop a chapter at the current playhead and start shaping the timeline.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Mocha.Subtext0
                    )
                }
            } else {
                Text(
                    text = "Chapter list",
                    style = MaterialTheme.typography.titleMedium,
                    color = Mocha.Text
                )

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(sortedChapters, key = { _, chapter -> chapter.timeMs }) { index, chapter ->
                        val isEditing = editingIndex == index
                        ChapterRow(
                            index = index,
                            chapter = chapter,
                            isEditing = isEditing,
                            editingTitle = editingTitle,
                            onEditingTitleChanged = { editingTitle = it },
                            onJumpTo = { onJumpTo(chapter.timeMs) },
                            onStartEditing = {
                                editingIndex = index
                                editingTitle = chapter.title
                            },
                            onSave = {
                                onUpdateChapter(index, chapter.copy(title = editingTitle.ifBlank { chapter.title }))
                                editingIndex = -1
                            },
                            onDelete = {
                                if (editingIndex == index) {
                                    editingIndex = -1
                                }
                                onDeleteChapter(index)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChapterRow(
    index: Int,
    chapter: ChapterMarker,
    isEditing: Boolean,
    editingTitle: String,
    onEditingTitleChanged: (String) -> Unit,
    onJumpTo: () -> Unit,
    onStartEditing: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (isEditing) Mocha.Yellow.copy(alpha = 0.14f) else Mocha.PanelRaised,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(
            1.dp,
            if (isEditing) Mocha.Yellow.copy(alpha = 0.28f) else Mocha.CardStroke
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onJumpTo)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(Mocha.Yellow.copy(alpha = 0.18f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.labelLarge,
                            color = Mocha.Yellow
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = formatChapterTimestamp(chapter.timeMs),
                            style = MaterialTheme.typography.labelLarge,
                            color = Mocha.Blue
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        if (isEditing) {
                            OutlinedTextField(
                                value = editingTitle,
                                onValueChange = onEditingTitleChanged,
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Mocha.Text),
                                placeholder = {
                                    Text(
                                        text = stringResource(R.string.panel_snapshot_untitled),
                                        color = Mocha.Subtext0
                                    )
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Mocha.Yellow,
                                    unfocusedBorderColor = Mocha.CardStroke,
                                    focusedTextColor = Mocha.Text,
                                    unfocusedTextColor = Mocha.Text,
                                    cursorColor = Mocha.Yellow
                                )
                            )
                        } else {
                            Text(
                                text = chapter.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = Mocha.Text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                PremiumPanelPill(
                    text = if (isEditing) "Editing" else "Jump",
                    accent = if (isEditing) Mocha.Yellow else Mocha.Green
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ChapterAction(
                    icon = if (isEditing) Icons.Default.Check else Icons.Default.Edit,
                    label = if (isEditing) "Save" else "Rename",
                    accent = if (isEditing) Mocha.Green else Mocha.Subtext0,
                    onClick = if (isEditing) onSave else onStartEditing
                )
                Spacer(modifier = Modifier.width(8.dp))
                ChapterAction(
                    icon = Icons.Default.Delete,
                    label = "Delete",
                    accent = Mocha.Red,
                    onClick = onDelete
                )
            }
        }
    }
}

@Composable
private fun ChapterAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    accent: Color,
    onClick: () -> Unit
) {
    Surface(
        color = accent.copy(alpha = 0.12f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.18f))
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = accent,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = accent
            )
        }
    }
}

private fun formatChapterTimestamp(timeMs: Long): String {
    val totalSeconds = timeMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

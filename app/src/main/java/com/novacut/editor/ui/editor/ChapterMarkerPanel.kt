package com.novacut.editor.ui.editor

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Crust, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Chapters", color = TextColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Row {
                // Add chapter at playhead
                IconButton(
                    onClick = {
                        val title = "Chapter ${chapters.size + 1}"
                        onAddChapter(ChapterMarker(playheadMs, title))
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.Add, "Add at Playhead", tint = Green, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, "Close", tint = Subtext, modifier = Modifier.size(18.dp))
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        Text(
            "Chapters are embedded in MP4 exports for YouTube navigation",
            color = Subtext,
            fontSize = 10.sp
        )

        Spacer(Modifier.height(8.dp))

        if (chapters.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Bookmarks, null, tint = Subtext.copy(alpha = 0.3f), modifier = Modifier.size(32.dp))
                    Spacer(Modifier.height(4.dp))
                    Text("No chapters. Tap + to add at playhead.", color = Subtext, fontSize = 12.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 250.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(chapters.indices.toList()) { index ->
                    val chapter = chapters[index]
                    val isEditing = editingIndex == index

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isEditing) Mauve.copy(alpha = 0.15f) else Surface0)
                            .clickable { onJumpTo(chapter.timeMs) }
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            // Chapter number
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(Yellow.copy(alpha = 0.2f), RoundedCornerShape(4.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("${index + 1}", color = Yellow, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            // Time
                            Text(
                                formatTimestamp(chapter.timeMs),
                                color = Subtext,
                                fontSize = 11.sp
                            )

                            // Title
                            if (isEditing) {
                                OutlinedTextField(
                                    value = editingTitle,
                                    onValueChange = { editingTitle = it },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp),
                                    singleLine = true,
                                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = TextColor),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Mauve,
                                        unfocusedBorderColor = Subtext.copy(alpha = 0.3f),
                                        cursorColor = Mauve
                                    )
                                )
                            } else {
                                Text(
                                    chapter.title,
                                    color = TextColor,
                                    fontSize = 13.sp,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Row {
                            if (isEditing) {
                                IconButton(
                                    onClick = {
                                        onUpdateChapter(index, chapter.copy(title = editingTitle))
                                        editingIndex = -1
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Check, "Save", tint = Green, modifier = Modifier.size(14.dp))
                                }
                            } else {
                                IconButton(
                                    onClick = {
                                        editingIndex = index
                                        editingTitle = chapter.title
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Edit, "Edit", tint = Subtext, modifier = Modifier.size(14.dp))
                                }
                            }
                            IconButton(
                                onClick = {
                                    if (editingIndex == index) editingIndex = -1
                                    else if (editingIndex > index) editingIndex--
                                    onDeleteChapter(index)
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Delete, "Delete", tint = Red.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

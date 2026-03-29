package com.novacut.editor.ui.editor

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novacut.editor.model.MarkerColor
import com.novacut.editor.model.TimelineMarker
import com.novacut.editor.ui.theme.Mocha

@Composable
fun MarkerListPanel(
    markers: List<TimelineMarker>,
    onJumpTo: (Long) -> Unit,
    onDelete: (String) -> Unit,
    onUpdateLabel: (String, String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var filterColor by remember { mutableStateOf<MarkerColor?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val filtered = markers
        .filter { filterColor == null || it.color == filterColor }
        .filter { searchQuery.isBlank() || it.label.contains(searchQuery, ignoreCase = true) || it.notes.contains(searchQuery, ignoreCase = true) }
        .sortedBy { it.timeMs }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Mocha.Base)
            .fillMaxWidth()
            .heightIn(max = 400.dp)
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(Mocha.Surface0)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                "Markers (${filtered.size})",
                color = Mocha.Text,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Close, "Close", tint = Mocha.Subtext0, modifier = Modifier.size(16.dp))
            }
        }

        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search markers...", fontSize = 12.sp) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .height(40.dp),
            textStyle = TextStyle(fontSize = 12.sp, color = Mocha.Text),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Mocha.Mauve,
                unfocusedBorderColor = Mocha.Surface1,
                cursorColor = Mocha.Mauve
            ),
            leadingIcon = { Icon(Icons.Default.Search, null, tint = Mocha.Subtext0, modifier = Modifier.size(16.dp)) }
        )

        // Color filter chips
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            FilterChip(
                onClick = { filterColor = null },
                label = { Text("All", fontSize = 10.sp) },
                selected = filterColor == null,
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = Mocha.Surface0,
                    selectedContainerColor = Mocha.Mauve.copy(alpha = 0.3f)
                ),
                modifier = Modifier.height(28.dp)
            )
            MarkerColor.entries.forEach { color ->
                FilterChip(
                    onClick = { filterColor = if (filterColor == color) null else color },
                    label = {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color(color.argb))
                        )
                    },
                    selected = filterColor == color,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Mocha.Surface0,
                        selectedContainerColor = Color(color.argb).copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.height(28.dp)
                )
            }
        }

        // Marker list
        if (filtered.isEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxWidth().padding(24.dp)
            ) {
                Text("No markers", color = Mocha.Overlay0, fontSize = 12.sp)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(filtered, key = { it.id }) { marker ->
                    var editingLabel by remember { mutableStateOf(false) }
                    var labelText by remember(marker.label) { mutableStateOf(marker.label) }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onJumpTo(marker.timeMs) }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        // Color dot
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(marker.color.argb))
                        )
                        Spacer(Modifier.width(8.dp))

                        // Time
                        Text(
                            formatMarkerTime(marker.timeMs),
                            color = Mocha.Mauve,
                            fontSize = 11.sp,
                            modifier = Modifier.width(55.dp)
                        )

                        // Label
                        if (editingLabel) {
                            OutlinedTextField(
                                value = labelText,
                                onValueChange = { labelText = it },
                                modifier = Modifier.weight(1f).height(32.dp),
                                textStyle = TextStyle(fontSize = 11.sp, color = Mocha.Text),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Mocha.Mauve,
                                    unfocusedBorderColor = Mocha.Surface1,
                                    cursorColor = Mocha.Mauve
                                ),
                                trailingIcon = {
                                    IconButton(
                                        onClick = { onUpdateLabel(marker.id, labelText); editingLabel = false },
                                        modifier = Modifier.size(16.dp)
                                    ) {
                                        Icon(Icons.Default.Check, null, tint = Mocha.Green, modifier = Modifier.size(12.dp))
                                    }
                                }
                            )
                        } else {
                            Text(
                                marker.label.ifBlank { "Marker" },
                                color = Mocha.Text,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Edit button
                        IconButton(
                            onClick = { editingLabel = !editingLabel },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                if (editingLabel) Icons.Default.Close else Icons.Default.Edit,
                                null,
                                tint = Mocha.Subtext0,
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        // Delete button
                        IconButton(
                            onClick = { onDelete(marker.id) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Delete, null, tint = Mocha.Red.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }
    }
}

private fun formatMarkerTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val centis = (ms % 1000) / 10
    return "%02d:%02d.%02d".format(minutes, seconds, centis)
}

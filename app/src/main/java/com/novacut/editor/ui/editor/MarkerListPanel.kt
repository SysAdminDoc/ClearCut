package com.novacut.editor.ui.editor

import com.novacut.editor.ui.theme.ClearCutAccents
import com.novacut.editor.ui.theme.LocalClearCutColors
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.novacut.editor.R
import com.novacut.editor.model.MarkerColor
import com.novacut.editor.model.TimelineMarker
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MarkerListPanel(
    markers: List<TimelineMarker>,
    onJumpTo: (Long) -> Unit,
    onDelete: (String) -> Unit,
    onUpdateLabel: (String, String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semanticColors = LocalClearCutColors.current
    var filterColor by remember { mutableStateOf<MarkerColor?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    val filtered = markers
        .filter { filterColor == null || it.color == filterColor }
        .filter {
            searchQuery.isBlank() ||
                it.label.contains(searchQuery, ignoreCase = true) ||
                it.notes.contains(searchQuery, ignoreCase = true)
        }
        .sortedBy { it.timeMs }

    PremiumEditorPanel(
        title = stringResource(R.string.panel_markers_title),
        subtitle = stringResource(R.string.panel_markers_subtitle),
        icon = Icons.Default.BookmarkBorder,
        accent = ClearCutAccents.Blue,
        onClose = onClose,
        closeContentDescription = stringResource(R.string.cd_close_markers),
        modifier = modifier,
        scrollable = true
    ) {
        PremiumPanelCard(accent = ClearCutAccents.Blue) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val isCompactLayout = maxWidth < 420.dp
                if (isCompactLayout) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column {
                            Text(
                                text = stringResource(R.string.panel_marker_header, filtered.size),
                                style = MaterialTheme.typography.titleMedium,
                                color = semanticColors.text
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (markers.isEmpty()) {
                                    stringResource(R.string.panel_marker_empty_description)
                                } else {
                                    stringResource(R.string.panel_marker_browse_description)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = semanticColors.subtext
                            )
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PremiumPanelPill(
                                text = stringResource(R.string.panel_marker_total, markers.size),
                                accent = ClearCutAccents.Blue
                            )
                            PremiumPanelPill(
                                text = filterColor?.name ?: stringResource(R.string.panel_marker_all),
                                accent = filterColor?.let(::markerAccent) ?: ClearCutAccents.Green
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.panel_marker_header, filtered.size),
                                style = MaterialTheme.typography.titleMedium,
                                color = semanticColors.text
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (markers.isEmpty()) {
                                    stringResource(R.string.panel_marker_empty_description)
                                } else {
                                    stringResource(R.string.panel_marker_browse_description)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = semanticColors.subtext
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PremiumPanelPill(
                                text = stringResource(R.string.panel_marker_total, markers.size),
                                accent = ClearCutAccents.Blue
                            )
                            PremiumPanelPill(
                                text = filterColor?.name ?: stringResource(R.string.panel_marker_all),
                                accent = filterColor?.let(::markerAccent) ?: ClearCutAccents.Green
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = {
                    Text(
                        text = stringResource(R.string.panel_marker_search),
                        color = semanticColors.subtext
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = stringResource(R.string.cd_search),
                        tint = semanticColors.subtext
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ClearCutAccents.Blue,
                    unfocusedBorderColor = semanticColors.cardStroke,
                    focusedTextColor = semanticColors.text,
                    unfocusedTextColor = semanticColors.text,
                    cursorColor = ClearCutAccents.Blue
                )
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MarkerFilterChip(
                    label = stringResource(R.string.panel_marker_all),
                    accent = ClearCutAccents.Blue,
                    selected = filterColor == null,
                    onClick = { filterColor = null }
                )
                MarkerColor.entries.forEach { color ->
                    MarkerFilterChip(
                        label = color.name.lowercase().replaceFirstChar { it.uppercase() },
                        accent = markerAccent(color),
                        selected = filterColor == color,
                        onClick = { filterColor = if (filterColor == color) null else color }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        PremiumPanelCard(accent = ClearCutAccents.Green) {
            if (filtered.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.BookmarkBorder,
                        contentDescription = stringResource(R.string.cd_bookmarks),
                        tint = semanticColors.overlayStrong,
                        modifier = Modifier.size(30.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (markers.isEmpty()) {
                            stringResource(R.string.panel_marker_no_markers)
                        } else {
                            stringResource(R.string.panel_marker_no_matches)
                        },
                        style = MaterialTheme.typography.titleSmall,
                        color = semanticColors.text
                    )
                    Text(
                        text = if (markers.isEmpty()) {
                            stringResource(R.string.panel_marker_empty_description)
                        } else {
                            stringResource(R.string.panel_marker_filtered_description)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = semanticColors.subtext
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    filtered.forEach { marker ->
                        MarkerRow(
                            marker = marker,
                            onJumpTo = { onJumpTo(marker.timeMs) },
                            onDelete = { onDelete(marker.id) },
                            onUpdateLabel = { onUpdateLabel(marker.id, it) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MarkerRow(
    marker: TimelineMarker,
    onJumpTo: () -> Unit,
    onDelete: () -> Unit,
    onUpdateLabel: (String) -> Unit
) {
    val semanticColors = LocalClearCutColors.current
    var editingLabel by remember { mutableStateOf(false) }
    var labelText by remember(marker.label) { mutableStateOf(marker.label) }
    val accent = markerAccent(marker.color)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = semanticColors.panelRaised,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, semanticColors.cardStroke)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onJumpTo)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
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
                            .size(12.dp)
                            .background(accent, CircleShape)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = formatMarkerTime(marker.timeMs),
                            style = MaterialTheme.typography.labelLarge,
                            color = accent
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        if (editingLabel) {
                            OutlinedTextField(
                                value = labelText,
                                onValueChange = { labelText = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = semanticColors.text),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = accent,
                                    unfocusedBorderColor = semanticColors.cardStroke,
                                    focusedTextColor = semanticColors.text,
                                    unfocusedTextColor = semanticColors.text,
                                    cursorColor = accent
                                )
                            )
                        } else {
                            Text(
                                text = marker.label.ifBlank { stringResource(R.string.panel_marker_default_name) },
                                style = MaterialTheme.typography.titleSmall,
                                color = semanticColors.text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                PremiumPanelPill(
                    text = marker.color.name.lowercase().replaceFirstChar { it.uppercase() },
                    accent = accent
                )
            }

            if (marker.notes.isNotBlank()) {
                Text(
                    text = marker.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = semanticColors.subtext,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MarkerAction(
                    icon = if (editingLabel) Icons.Default.Check else Icons.Default.Edit,
                    label = if (editingLabel) {
                        stringResource(R.string.panel_marker_save)
                    } else {
                        stringResource(R.string.panel_marker_rename)
                    },
                    accent = if (editingLabel) ClearCutAccents.Green else semanticColors.subtext,
                    onClick = {
                        if (editingLabel) {
                            val updatedLabel = labelText.trim()
                            labelText = updatedLabel
                            onUpdateLabel(updatedLabel)
                        }
                        editingLabel = !editingLabel
                    }
                )
                MarkerAction(
                    icon = Icons.Default.Delete,
                    label = stringResource(R.string.panel_marker_delete),
                    accent = ClearCutAccents.Red,
                    onClick = onDelete
                )
            }
        }
    }
}

@Composable
private fun MarkerFilterChip(
    label: String,
    accent: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    val semanticColors = LocalClearCutColors.current
    Surface(
        color = if (selected) accent.copy(alpha = 0.16f) else semanticColors.panelRaised,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, if (selected) accent.copy(alpha = 0.24f) else semanticColors.cardStroke)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) accent else semanticColors.subtext,
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun MarkerAction(
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

private fun formatMarkerTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val centis = (ms % 1000) / 10
    return String.format(Locale.US, "%02d:%02d.%02d", minutes, seconds, centis)
}

private fun markerAccent(color: MarkerColor): Color = Color(color.argb)

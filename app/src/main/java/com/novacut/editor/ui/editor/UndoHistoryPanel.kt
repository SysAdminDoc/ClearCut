package com.novacut.editor.ui.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.novacut.editor.R
import com.novacut.editor.model.UndoHistoryEntry
import com.novacut.editor.ui.theme.Mocha
import kotlinx.coroutines.delay

@Composable
fun UndoHistoryPanel(
    currentIndex: Int,
    entries: List<UndoHistoryEntry>,
    onJumpTo: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val selectedUndoIndex = (currentIndex - 1).coerceAtLeast(-1)

    LaunchedEffect(Unit) {
        while (true) {
            delay(5_000L)
            now = System.currentTimeMillis()
        }
    }

    PremiumEditorPanel(
        title = stringResource(R.string.undo_history_title),
        subtitle = "Review the recent edit stack and jump back to a known-good state without repeated undo taps.",
        icon = Icons.Default.History,
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
                        text = "History stack",
                        style = MaterialTheme.typography.titleMedium,
                        color = Mocha.Text
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (entries.isEmpty()) {
                            "No undoable actions have been recorded in this session yet."
                        } else {
                            "The highlighted row is the current state. Tap any earlier step to roll the timeline back in one move."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Mocha.Subtext0
                    )
                }

                Spacer(modifier = Modifier.height(0.dp))

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PremiumPanelPill(
                        text = "${entries.size} actions",
                        accent = Mocha.Mauve
                    )
                    PremiumPanelPill(
                        text = if (selectedUndoIndex >= 0) "Step ${selectedUndoIndex + 1}" else "Live state",
                        accent = Mocha.Green
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        PremiumPanelCard(accent = Mocha.Blue) {
            if (entries.isEmpty()) {
                Text(
                    text = stringResource(R.string.undo_history_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Mocha.Subtext0
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(entries, key = { it.index }) { entry ->
                        val isCurrent = entry.index == selectedUndoIndex
                        val isFuture = entry.index > selectedUndoIndex && selectedUndoIndex >= 0
                        UndoHistoryRow(
                            entry = entry,
                            isCurrent = isCurrent,
                            isFuture = isFuture,
                            relativeTime = formatRelativeTime(now, entry.timestamp),
                            onClick = { onJumpTo(entry.index) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UndoHistoryRow(
    entry: UndoHistoryEntry,
    isCurrent: Boolean,
    isFuture: Boolean,
    relativeTime: String,
    onClick: () -> Unit
) {
    val accent = when {
        isCurrent -> Mocha.Mauve
        isFuture -> Mocha.Overlay1
        else -> Mocha.Blue
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (isCurrent) accent.copy(alpha = 0.14f) else Mocha.PanelRaised,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, if (isCurrent) accent.copy(alpha = 0.24f) else Mocha.CardStroke)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    color = accent.copy(alpha = 0.12f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.18f))
                ) {
                    Text(
                        text = "${entry.index + 1}",
                        color = accent,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.description,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (isFuture) Mocha.Subtext0 else Mocha.Text,
                        fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = relativeTime,
                        style = MaterialTheme.typography.bodySmall,
                        color = Mocha.Subtext0
                    )
                }
            }

            PremiumPanelPill(
                text = when {
                    isCurrent -> "Current"
                    isFuture -> "Ahead"
                    else -> "Jump"
                },
                accent = accent
            )
        }
    }
}

private fun formatRelativeTime(now: Long, timestamp: Long): String {
    val diffMs = now - timestamp
    if (diffMs < 0) return "just now"
    val seconds = diffMs / 1000
    return when {
        seconds < 5 -> "just now"
        seconds < 60 -> "${seconds}s ago"
        seconds < 3600 -> "${seconds / 60}m ago"
        seconds < 86400 -> "${seconds / 3600}h ago"
        else -> "${seconds / 86400}d ago"
    }
}

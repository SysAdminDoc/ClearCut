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
import androidx.compose.ui.draw.alpha
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
    val futureCount = remember(entries, selectedUndoIndex) {
        entries.count { it.index > selectedUndoIndex && selectedUndoIndex >= 0 }
    }

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
        closeContentDescription = stringResource(R.string.undo_history_close),
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
                    if (futureCount > 0) {
                        PremiumPanelPill(
                            text = stringResource(R.string.undo_history_newer_count, futureCount),
                            accent = Mocha.Overlay1
                        )
                    }
                    PremiumPanelPill(
                        text = if (selectedUndoIndex >= 0) {
                            stringResource(R.string.undo_history_step_format, selectedUndoIndex + 1)
                        } else {
                            stringResource(R.string.undo_history_live_state)
                        },
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
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (futureCount > 0) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = Mocha.Surface0,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                            border = BorderStroke(1.dp, Mocha.CardStroke)
                        ) {
                            Text(
                                text = stringResource(R.string.undo_history_future_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = Mocha.Subtext0,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        entries.forEach { entry ->
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
}

@Composable
private fun UndoHistoryRow(
    entry: UndoHistoryEntry,
    isCurrent: Boolean,
    isFuture: Boolean,
    relativeTime: String,
    onClick: () -> Unit
) {
    val canRestore = !isCurrent && !isFuture
    val accent = when {
        isCurrent -> Mocha.Mauve
        isFuture -> Mocha.Overlay1
        else -> Mocha.Blue
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (canRestore || isCurrent) 1f else 0.82f),
        color = when {
            isCurrent -> accent.copy(alpha = 0.14f)
            isFuture -> Mocha.PanelHighest
            else -> Mocha.PanelRaised
        },
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        border = BorderStroke(
            1.dp,
            when {
                isCurrent -> accent.copy(alpha = 0.24f)
                isFuture -> Mocha.CardStroke.copy(alpha = 0.72f)
                else -> Mocha.CardStroke
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = canRestore, onClick = onClick)
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
                    isCurrent -> stringResource(R.string.undo_history_status_current)
                    isFuture -> stringResource(R.string.undo_history_status_newer)
                    else -> stringResource(R.string.undo_history_status_restore)
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

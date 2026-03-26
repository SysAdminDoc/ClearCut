package com.novacut.editor.ui.editor

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val listState = rememberLazyListState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(5000L)
            now = System.currentTimeMillis()
        }
    }

    LaunchedEffect(currentIndex) {
        if (entries.isNotEmpty() && currentIndex in entries.indices) {
            listState.animateScrollToItem(currentIndex)
        }
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Mocha.Base)
            .widthIn(min = 220.dp, max = 280.dp)
            .heightIn(max = 300.dp)
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
                text = stringResource(R.string.undo_history_title),
                color = Mocha.Text,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.undo_history_close),
                    tint = Mocha.Subtext0,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        if (entries.isEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = stringResource(R.string.undo_history_empty),
                    color = Mocha.Overlay0,
                    fontSize = 12.sp
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth()
            ) {
                itemsIndexed(entries) { index, entry ->
                    val isCurrent = index == currentIndex
                    val isFuture = index > currentIndex
                    val entryAlpha = if (isFuture) 0.5f else 1f

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(entryAlpha)
                            .clickable { onJumpTo(index) }
                            .then(
                                if (isCurrent) {
                                    Modifier
                                        .background(Mocha.Surface0)
                                        .border(
                                            width = 3.dp,
                                            color = Mocha.Mauve,
                                            shape = RoundedCornerShape(topStart = 3.dp, bottomStart = 3.dp)
                                        )
                                } else {
                                    Modifier
                                }
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "${index + 1}",
                            color = if (isCurrent) Mocha.Mauve else Mocha.Overlay0,
                            fontSize = 10.sp,
                            modifier = Modifier.width(20.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = entry.description,
                                color = if (isCurrent) Mocha.Text else Mocha.Subtext1,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = formatRelativeTime(now, entry.timestamp),
                                color = Mocha.Overlay0,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
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

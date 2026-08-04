package com.novacut.editor.ui.mediapicker

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.novacut.editor.R
import com.novacut.editor.engine.MediaSequenceCandidate
import com.novacut.editor.engine.MediaSequenceOrder
import com.novacut.editor.engine.moveMediaSequenceItem
import com.novacut.editor.engine.orderMediaSequence
import com.novacut.editor.ui.theme.ClearCutAccents
import com.novacut.editor.ui.theme.LocalClearCutColors
import com.novacut.editor.ui.theme.Radius
import com.novacut.editor.ui.theme.Spacing
import com.novacut.editor.ui.theme.TouchTarget
import java.text.DateFormat
import java.util.Date

data class MediaPickerSelection(
    val id: String,
    val uri: android.net.Uri,
    val mediaType: String,
    val displayName: String,
    val captureTimeMs: Long?,
)

internal fun orderedMediaPickerSelections(
    selections: List<MediaPickerSelection>,
    order: MediaSequenceOrder,
): List<MediaPickerSelection> {
    val byId = selections.associateBy { it.id }
    return orderMediaSequence(
        candidates = selections.map { selection ->
            MediaSequenceCandidate(
                key = selection.id,
                displayName = selection.displayName,
                captureTimeMs = selection.captureTimeMs,
            )
        },
        order = order,
    ).mapNotNull { candidate -> byId[candidate.key] }
}

internal fun moveMediaPickerSelection(
    selections: List<MediaPickerSelection>,
    fromIndex: Int,
    toIndex: Int,
): List<MediaPickerSelection> {
    val byId = selections.associateBy { it.id }
    return moveMediaSequenceItem(
        candidates = selections.map { selection ->
            MediaSequenceCandidate(
                key = selection.id,
                displayName = selection.displayName,
                captureTimeMs = selection.captureTimeMs,
            )
        },
        fromIndex = fromIndex,
        toIndex = toIndex,
    ).mapNotNull { candidate -> byId[candidate.key] }
}

@Composable
internal fun MediaSequenceReviewDialog(
    selections: List<MediaPickerSelection>,
    order: MediaSequenceOrder,
    onOrderChange: (MediaSequenceOrder) -> Unit,
    onMove: (itemId: String, targetIndex: Int) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val colors = LocalClearCutColors.current
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = colors.panelHighest,
        titleContentColor = colors.text,
        textContentColor = colors.subtext,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.LibraryAdd,
                    contentDescription = null,
                    tint = ClearCutAccents.Mauve,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(Spacing.sm))
                Text(
                    text = stringResource(R.string.media_sequence_review_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = onCancel,
                    modifier = Modifier.size(TouchTarget.minimum),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.media_sequence_review_close_cd),
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(stringResource(R.string.media_sequence_review_description))
                Text(
                    text = stringResource(R.string.media_sequence_review_reorder_hint),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.overlayStrong,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    MediaSequenceOrder.entries.forEach { candidateOrder ->
                        FilterChip(
                            selected = order == candidateOrder,
                            onClick = { onOrderChange(candidateOrder) },
                            label = {
                                Text(
                                    when (candidateOrder) {
                                        MediaSequenceOrder.CAPTURE_TIME -> stringResource(R.string.media_sequence_review_capture_time)
                                        MediaSequenceOrder.NAME -> stringResource(R.string.media_sequence_review_name)
                                        MediaSequenceOrder.MANUAL -> stringResource(R.string.media_sequence_review_manual)
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ClearCutAccents.Mauve.copy(alpha = 0.22f),
                                selectedLabelColor = colors.text,
                            ),
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 340.dp),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    itemsIndexed(
                        items = selections,
                        key = { _, selection -> selection.id },
                    ) { index, selection ->
                        var dragDistance by remember(selection.id) { mutableFloatStateOf(0f) }
                        var dragIndex by remember(selection.id) { mutableIntStateOf(index) }
                        val rowModifier = Modifier.pointerInput(selection.id) {
                            detectDragGestures(
                                onDragStart = {
                                    dragDistance = 0f
                                    dragIndex = index
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragDistance += amount.y
                                    val slots = (dragDistance / 64.dp.toPx()).toInt()
                                    if (slots != 0) {
                                        val target = (dragIndex + slots).coerceIn(0, selections.lastIndex)
                                        if (target != dragIndex) {
                                            val movement = target - dragIndex
                                            onMove(selection.id, target)
                                            dragIndex = target
                                            dragDistance -= movement * 64.dp.toPx()
                                        }
                                    }
                                },
                                onDragEnd = { dragDistance = 0f },
                                onDragCancel = { dragDistance = 0f },
                            )
                        }
                        Surface(
                            modifier = rowModifier.fillMaxWidth(),
                            color = colors.panel,
                            shape = RoundedCornerShape(Radius.md),
                            border = BorderStroke(1.dp, colors.cardStroke),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = ClearCutAccents.Mauve,
                                    modifier = Modifier.width(24.dp),
                                )
                                Icon(
                                    imageVector = Icons.Default.DragHandle,
                                    contentDescription = stringResource(R.string.media_sequence_review_drag_cd),
                                    tint = colors.overlayStrong,
                                    modifier = Modifier.size(20.dp),
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = selection.displayName,
                                        color = colors.text,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = sequenceSelectionDetail(selection),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.subtext,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                IconButton(
                                    onClick = { onMove(selection.id, index - 1) },
                                    enabled = index > 0,
                                    modifier = Modifier.size(TouchTarget.minimum),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowUpward,
                                        contentDescription = stringResource(
                                            R.string.media_sequence_review_move_up_cd,
                                            selection.displayName,
                                        ),
                                    )
                                }
                                IconButton(
                                    onClick = { onMove(selection.id, index + 1) },
                                    enabled = index < selections.lastIndex,
                                    modifier = Modifier.size(TouchTarget.minimum),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDownward,
                                        contentDescription = stringResource(
                                            R.string.media_sequence_review_move_down_cd,
                                            selection.displayName,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.media_sequence_review_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.media_sequence_review_cancel))
            }
        },
    )
}

@Composable
private fun sequenceSelectionDetail(selection: MediaPickerSelection): String {
    val type = when (selection.mediaType) {
        "audio" -> stringResource(R.string.media_picker_audio)
        "image" -> stringResource(R.string.media_picker_image)
        else -> stringResource(R.string.media_picker_video)
    }
    val captureTime = selection.captureTimeMs?.let { timestamp ->
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(timestamp))
    } ?: stringResource(R.string.media_sequence_review_capture_unknown)
    return stringResource(R.string.media_sequence_review_item_detail, type, captureTime)
}

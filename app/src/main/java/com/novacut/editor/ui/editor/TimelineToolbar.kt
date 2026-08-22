package com.novacut.editor.ui.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.novacut.editor.R
import com.novacut.editor.model.TimelineRange
import com.novacut.editor.ui.ClearCutTestTags

internal const val TIMELINE_TOOLBAR_MIN_ZOOM = 0.01f
internal const val TIMELINE_TOOLBAR_MAX_ZOOM = 10f

internal object TimelineToolbarPolicy {
    fun zoomOut(zoomLevel: Float): Float =
        (zoomLevel * 0.75f).coerceAtLeast(TIMELINE_TOOLBAR_MIN_ZOOM)

    fun zoomIn(zoomLevel: Float): Float =
        (zoomLevel * 1.33f).coerceAtMost(TIMELINE_TOOLBAR_MAX_ZOOM)
}

@Composable
internal fun TimelineToolbarControls(
    compact: Boolean,
    zoomLevel: Float,
    fitZoomLevel: Float,
    canSplitAtPlayhead: Boolean,
    selectedClipId: String?,
    selectedTimelineRange: TimelineRange?,
    isRangeSelectionMode: Boolean,
    onZoomChanged: (Float) -> Unit,
    onScrollChanged: (Long) -> Unit,
    onSplitAtPlayhead: () -> Unit,
    onDeleteSelectedClip: () -> Unit,
    onBeginRangeSelection: () -> Unit,
    onCancelRangeSelection: () -> Unit,
    onMuteTimelineRange: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TimelineToolbarButton(
            icon = Icons.Default.Remove,
            contentDescription = stringResource(R.string.cd_zoom_out),
            compact = compact,
            onClick = { onZoomChanged(TimelineToolbarPolicy.zoomOut(zoomLevel)) },
        )
        TimelineToolbarButton(
            icon = Icons.Default.FitScreen,
            contentDescription = stringResource(R.string.cd_fit_timeline),
            compact = compact,
            onClick = {
                onZoomChanged(fitZoomLevel)
                onScrollChanged(0L)
            },
        )
        TimelineToolbarButton(
            icon = Icons.Default.Add,
            contentDescription = stringResource(R.string.cd_zoom_in),
            compact = compact,
            onClick = { onZoomChanged(TimelineToolbarPolicy.zoomIn(zoomLevel)) },
        )
        TimelineToolbarButton(
            icon = Icons.Default.SelectAll,
            contentDescription = stringResource(
                if (isRangeSelectionMode) {
                    R.string.timeline_cancel_range_selection
                } else {
                    R.string.timeline_select_range
                },
            ),
            compact = compact,
            highlight = isRangeSelectionMode,
            onClick = if (isRangeSelectionMode) onCancelRangeSelection else onBeginRangeSelection,
        )
        if (selectedTimelineRange != null) {
            TimelineToolbarButton(
                icon = Icons.AutoMirrored.Filled.VolumeOff,
                contentDescription = stringResource(R.string.timeline_mute_range),
                compact = compact,
                highlight = true,
                onClick = onMuteTimelineRange,
            )
        }
        TimelineToolbarButton(
            icon = Icons.Default.ContentCut,
            contentDescription = stringResource(R.string.cd_split_at_playhead),
            compact = compact,
            highlight = true,
            enabled = canSplitAtPlayhead,
            testTag = ClearCutTestTags.TIMELINE_SPLIT,
            onClick = onSplitAtPlayhead,
        )
        if (selectedClipId != null) {
            TimelineToolbarButton(
                icon = Icons.Default.Delete,
                contentDescription = stringResource(R.string.timeline_clip_action_delete),
                compact = compact,
                highlight = true,
                destructive = true,
                testTag = ClearCutTestTags.TIMELINE_DELETE,
                onClick = onDeleteSelectedClip,
            )
        }
        TimelineToolbarButton(
            icon = Icons.Default.MoreHoriz,
            contentDescription = stringResource(R.string.editor_more),
            compact = compact,
            onClick = onMoreClick,
        )
    }
}

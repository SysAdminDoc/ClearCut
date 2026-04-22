package com.novacut.editor.ui.editor

import android.graphics.Bitmap
import androidx.compose.foundation.*
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.novacut.editor.R
import com.novacut.editor.engine.VideoEngine
import com.novacut.editor.model.*
import com.novacut.editor.ui.theme.Mocha
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import java.util.Locale

private const val BASE_SCALE = 0.15f // pixels per ms at zoom 1.0

// Minimum zoom — low enough that a ~10-minute video fits on a phone screen. The old
// 0.1f floor meant long videos could never fit the viewport, which combined with no
// auto-fit-on-add made the timeline appear to only show a tiny portion of the media.
// File-private to avoid clashing with the same-named const in EditorViewModel.kt,
// which maintains its own copy so the VM logic doesn't have a cross-file dependency.
private const val MIN_TIMELINE_ZOOM = 0.01f
private const val MAX_TIMELINE_ZOOM = 10f

private enum class ClipGestureZone { TRIM_LEFT, TRIM_RIGHT, SLIDE, SLIP, NONE }

private fun findSnapTarget(positionMs: Long, targets: List<Long>, thresholdMs: Long): Long? {
    return targets.minByOrNull { abs(it - positionMs) }
        ?.takeIf { abs(it - positionMs) <= thresholdMs }
}

private fun Clip.containsTimelinePosition(positionMs: Long): Boolean {
    return positionMs >= timelineStartMs && positionMs < timelineEndMs
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun Timeline(
    tracks: List<Track>,
    playheadMs: Long,
    totalDurationMs: Long,
    zoomLevel: Float,
    scrollOffsetMs: Long,
    selectedClipId: String?,
    modifier: Modifier = Modifier,
    isTrimMode: Boolean = false,
    waveforms: Map<String, List<Float>> = emptyMap(),
    onClipSelected: (String?, String?) -> Unit,
    onPlayheadMoved: (Long) -> Unit,
    onZoomChanged: (Float) -> Unit,
    onScrollChanged: (Long) -> Unit,
    onTrimChanged: (clipId: String, newTrimStartMs: Long?, newTrimEndMs: Long?) -> Unit = { _, _, _ -> },
    onTrimDragStarted: () -> Unit = {},
    onTrimDragEnded: () -> Unit = {},
    onTimelineWidthChanged: (Float) -> Unit = {},
    onToggleTrackMute: (String) -> Unit = {},
    onToggleTrackVisible: (String) -> Unit = {},
    onToggleTrackLock: (String) -> Unit = {},
    beatMarkers: List<Long> = emptyList(),
    selectedClipIds: Set<String> = emptySet(),
    onScrubStart: () -> Unit = {},
    onScrubEnd: () -> Unit = {},
    onClipLongPress: (String) -> Unit = {},
    onSlideClip: (clipId: String, deltaMs: Long) -> Unit = { _, _ -> },
    onSlipClip: (clipId: String, deltaMs: Long) -> Unit = { _, _ -> },
    onSlideEditStarted: () -> Unit = {},
    onSlideEditEnded: () -> Unit = {},
    onSlipEditStarted: () -> Unit = {},
    onSlipEditEnded: () -> Unit = {},
    onToggleTrackCollapsed: (String) -> Unit = {},
    onToggleTrackWaveform: (String) -> Unit = {},
    onCollapseAllTracks: () -> Unit = {},
    onExpandAllTracks: () -> Unit = {},
    onSetTrackHeight: (String, Int) -> Unit = { _, _ -> },
    snapToBeat: Boolean = false,
    snapToMarker: Boolean = true,
    markers: List<TimelineMarker> = emptyList(),
    onAddMarker: () -> Unit = {},
    onMarkerTapped: (TimelineMarker) -> Unit = {},
    onSplitAtPlayhead: () -> Unit = {},
    onDeleteSelectedClip: () -> Unit = {},
    engine: VideoEngine
) {
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val isCompactTimeline = screenWidth < 430.dp
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val rulerHeight = 28.dp
    val pixelsPerMs = zoomLevel * BASE_SCALE
    val coroutineScope = rememberCoroutineScope()
    val textMeasurer = rememberTextMeasurer()
    var timelineWidthPx by remember { mutableFloatStateOf(0f) }
    val selectedTrackId = remember(tracks, selectedClipId) {
        tracks.firstOrNull { track -> track.clips.any { clip -> clip.id == selectedClipId } }?.id
    }
    val totalClipCount = remember(tracks) { tracks.sumOf { it.clips.size } }
    val fitZoomLevel = remember(timelineWidthPx, totalDurationMs) {
        if (timelineWidthPx <= 0f || totalDurationMs <= 0L) {
            1f
        } else {
            ((timelineWidthPx / totalDurationMs.toFloat()) / BASE_SCALE * 0.92f).coerceIn(MIN_TIMELINE_ZOOM, MAX_TIMELINE_ZOOM)
        }
    }
    val visibleDurationMs = remember(timelineWidthPx, pixelsPerMs, totalDurationMs) {
        if (timelineWidthPx <= 0f || pixelsPerMs <= 0.001f) {
            totalDurationMs
        } else {
            (timelineWidthPx / pixelsPerMs).toLong().coerceAtLeast(0L)
        }
    }
    val headerWidth = if (isCompactTimeline) 132.dp else 140.dp
    val chromePadding = if (isCompactTimeline) 12.dp else 16.dp
    val contentPadding = if (isCompactTimeline) 10.dp else 12.dp
    val trimHandleVisualWidth = 14.dp
    val trimHandleTouchWidth = 28.dp
    val videoTrackLabel = stringResource(R.string.editor_video_track)
    val audioTrackLabel = stringResource(R.string.editor_audio_track)
    val overlayTrackLabel = stringResource(R.string.editor_overlay_track)
    val textTrackLabel = stringResource(R.string.editor_text_track)
    val adjustmentTrackLabel = stringResource(R.string.timeline_adjustment_track)
    val compactVideoTrackLabel = stringResource(R.string.timeline_video_track_short)
    val compactAudioTrackLabel = stringResource(R.string.timeline_audio_track_short)
    val compactOverlayTrackLabel = stringResource(R.string.timeline_overlay_track_short)
    val compactTextTrackLabel = stringResource(R.string.timeline_text_track_short)
    val compactAdjustmentTrackLabel = stringResource(R.string.timeline_adjustment_track_short)
    val videoClipLabel = stringResource(R.string.timeline_video_clip)
    val audioClipLabel = stringResource(R.string.timeline_audio_clip)
    val overlayClipLabel = stringResource(R.string.timeline_overlay_clip)
    val textClipLabel = stringResource(R.string.timeline_text_clip)
    val adjustmentClipLabel = stringResource(R.string.timeline_adjustment_clip)
    val totalClipLabel = pluralStringResource(
        R.plurals.timeline_clip_count,
        totalClipCount,
        totalClipCount
    )
    val markerCountLabel = pluralStringResource(
        R.plurals.timeline_marker_count,
        markers.size,
        markers.size
    )
    val lockedShortLabel = stringResource(R.string.timeline_locked_short)
    val mutedShortLabel = stringResource(R.string.timeline_muted_short)
    val hiddenShortLabel = stringResource(R.string.timeline_hidden_short)
    val trackLabelForType: (TrackType) -> String = { trackType ->
        when (trackType) {
            TrackType.VIDEO -> videoTrackLabel
            TrackType.AUDIO -> audioTrackLabel
            TrackType.OVERLAY -> overlayTrackLabel
            TrackType.TEXT -> textTrackLabel
            TrackType.ADJUSTMENT -> adjustmentTrackLabel
        }
    }
    val compactTrackLabelForType: (TrackType) -> String = { trackType ->
        when (trackType) {
            TrackType.VIDEO -> compactVideoTrackLabel
            TrackType.AUDIO -> compactAudioTrackLabel
            TrackType.OVERLAY -> compactOverlayTrackLabel
            TrackType.TEXT -> compactTextTrackLabel
            TrackType.ADJUSTMENT -> compactAdjustmentTrackLabel
        }
    }
    val clipLabelForType: (TrackType) -> String = { trackType ->
        when (trackType) {
            TrackType.VIDEO -> videoClipLabel
            TrackType.AUDIO -> audioClipLabel
            TrackType.OVERLAY -> overlayClipLabel
            TrackType.TEXT -> textClipLabel
            TrackType.ADJUSTMENT -> adjustmentClipLabel
        }
    }

    // Use rememberUpdatedState for values that change frequently so pointerInput
    // blocks always see the latest value without recreating gesture detectors.
    val currentZoomLevel by rememberUpdatedState(zoomLevel)
    val currentScrollOffsetMs by rememberUpdatedState(scrollOffsetMs)
    val currentTotalDurationMs by rememberUpdatedState(totalDurationMs)
    val currentPlayheadMs by rememberUpdatedState(playheadMs)
    val currentMarkers by rememberUpdatedState(markers)
    val currentTracks by rememberUpdatedState(tracks)
    val currentSelectedClipId by rememberUpdatedState(selectedClipId)
    val currentIsTrimMode by rememberUpdatedState(isTrimMode)

    // Thumbnail cache — quantize zoom to prevent unbounded cache growth
    val thumbnails = remember { mutableStateMapOf<String, List<Bitmap>>() }
    val quantizedZoom = (zoomLevel * 4).toInt() / 4f // quantize to 0.25 steps
    val thumbnailSemaphore = remember { kotlinx.coroutines.sync.Semaphore(3) }
    val thumbnailPreloadPaddingMs = remember(visibleDurationMs) {
        (visibleDurationMs / 2).coerceAtLeast(2_000L)
    }
    val thumbnailVisibleStartMs = (scrollOffsetMs - thumbnailPreloadPaddingMs).coerceAtLeast(0L)
    val thumbnailVisibleEndMs = scrollOffsetMs + visibleDurationMs + thumbnailPreloadPaddingMs

    // Load thumbnails for visible clips — evict stale zoom levels to prevent OOM
    LaunchedEffect(tracks, quantizedZoom, thumbnailVisibleStartMs, thumbnailVisibleEndMs) {
        thumbnails.keys.filter { !it.endsWith("_$quantizedZoom") }
            .forEach { thumbnails.remove(it) }
        tracks
            .filter { it.type == TrackType.VIDEO || it.type == TrackType.OVERLAY }
            .forEach { track ->
            track.clips
                .filter { clip ->
                    clip.timelineEndMs >= thumbnailVisibleStartMs && clip.timelineStartMs <= thumbnailVisibleEndMs
                }
                .forEach { clip ->
                val key = "${clip.id}_${quantizedZoom}"
                if (!thumbnails.containsKey(key)) {
                    launch {
                        thumbnailSemaphore.acquire()
                        try {
                            val count = ((clip.durationMs * pixelsPerMs) / 80f).toInt().coerceIn(1, 20)
                            val strip = engine.extractThumbnailStrip(clip.sourceUri, count)
                            thumbnails[key] = strip
                        } finally {
                            thumbnailSemaphore.release()
                        }
                    }
                }
            }
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Mocha.Panel,
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, Mocha.CardStroke.copy(alpha = 0.92f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Mocha.PanelHighest.copy(alpha = 0.96f),
                            Mocha.Panel,
                            Mocha.Mantle
                        )
                    )
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = chromePadding, top = chromePadding, end = chromePadding),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.timeline_title),
                        color = Mocha.Text,
                        style = if (isCompactTimeline) {
                            MaterialTheme.typography.titleMedium
                        } else {
                            MaterialTheme.typography.titleLarge
                        }
                    )
                    Text(
                        text = "${formatTimelineTime(playheadMs)} / ${formatTimelineTime(totalDurationMs)}",
                        color = Mocha.Subtext0,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimelineToolbarButton(
                        icon = Icons.Default.Remove,
                        contentDescription = stringResource(R.string.cd_zoom_out),
                        compact = isCompactTimeline,
                        onClick = { onZoomChanged((zoomLevel * 0.75f).coerceAtLeast(MIN_TIMELINE_ZOOM)) }
                    )
                    TimelineToolbarButton(
                        icon = Icons.Default.FitScreen,
                        contentDescription = stringResource(R.string.cd_fit_timeline),
                        compact = isCompactTimeline,
                        onClick = {
                            onZoomChanged(fitZoomLevel)
                            onScrollChanged(0L)
                        }
                    )
                    TimelineToolbarButton(
                        icon = Icons.Default.Add,
                        contentDescription = stringResource(R.string.cd_zoom_in),
                        compact = isCompactTimeline,
                        onClick = { onZoomChanged((zoomLevel * 1.33f).coerceAtMost(MAX_TIMELINE_ZOOM)) }
                    )
                    TimelineToolbarButton(
                        icon = Icons.Default.ContentCut,
                        contentDescription = stringResource(R.string.cd_split_at_playhead),
                        compact = isCompactTimeline,
                        highlight = true,
                        onClick = onSplitAtPlayhead
                    )
                    TimelineToolbarButton(
                        icon = Icons.Default.DeleteSweep,
                        contentDescription = stringResource(R.string.cd_delete_selected),
                        compact = isCompactTimeline,
                        enabled = selectedClipId != null,
                        onClick = onDeleteSelectedClip
                    )
                    TimelineToolbarButton(
                        icon = Icons.Default.BookmarkAdd,
                        contentDescription = stringResource(R.string.cd_add_marker),
                        compact = isCompactTimeline,
                        onClick = onAddMarker
                    )
                }
            }

            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = chromePadding, vertical = if (isCompactTimeline) 10.dp else 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TimelineInfoChip(
                    text = if (isTrimMode) {
                        stringResource(R.string.timeline_mode_trim)
                    } else {
                        stringResource(R.string.timeline_mode_arrange)
                    },
                    accent = if (isTrimMode) Mocha.Peach else Mocha.Sky,
                    compact = isCompactTimeline
                )
                TimelineInfoChip(
                    text = stringResource(R.string.timeline_zoom_value, (zoomLevel * 100f).roundToInt()),
                    accent = Mocha.Blue,
                    compact = isCompactTimeline
                )
                TimelineInfoChip(
                    text = stringResource(R.string.timeline_visible_value, formatTimelineTime(visibleDurationMs)),
                    accent = Mocha.Lavender,
                    compact = isCompactTimeline
                )
                TimelineInfoChip(
                    text = markerCountLabel,
                    accent = Mocha.Yellow,
                    compact = isCompactTimeline
                )
                if (snapToBeat) {
                    TimelineInfoChip(
                        text = stringResource(R.string.settings_snap_beat),
                        accent = Mocha.Green,
                        compact = isCompactTimeline
                    )
                }
                if (snapToMarker) {
                    TimelineInfoChip(
                        text = stringResource(R.string.settings_snap_markers),
                        accent = Mocha.Mauve,
                        compact = isCompactTimeline
                    )
                }
                TimelineTextActionChip(
                    text = stringResource(R.string.collapse_all_tracks),
                    compact = isCompactTimeline,
                    onClick = onCollapseAllTracks
                )
                TimelineTextActionChip(
                    text = stringResource(R.string.expand_all_tracks),
                    compact = isCompactTimeline,
                    onClick = onExpandAllTracks
                )
            }

            if (isTrimMode && selectedClipId != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = chromePadding)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Mocha.Peach.copy(alpha = 0.12f))
                        .border(1.dp, Mocha.Peach.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(R.string.timeline_trim_mode_hint),
                        color = Mocha.Peach,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = contentPadding, end = contentPadding, top = contentPadding, bottom = contentPadding)
            ) {
                Column(
                    modifier = Modifier
                        .width(headerWidth)
                        .padding(end = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(rulerHeight)
                            .padding(horizontal = if (isCompactTimeline) 4.dp else 8.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(
                                text = stringResource(R.string.timeline_tracks_label),
                                color = Mocha.Text,
                                style = if (isCompactTimeline) {
                                    MaterialTheme.typography.labelMedium
                                } else {
                                    MaterialTheme.typography.labelLarge
                                }
                            )
                            Text(
                                text = totalClipLabel,
                                color = Mocha.Subtext0,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }

                    tracks.forEach { track ->
                        key(track.id) {
                            val currentTrackHeight = if (track.isCollapsed) 28.dp else track.trackHeight.dp
                            val trackColor = trackAccentColor(track.type)
                            var trackMenuExpanded by remember(track.id) { mutableStateOf(false) }
                            val statusBits = buildList {
                                if (track.isLocked) add(lockedShortLabel)
                                if (track.isMuted) add(mutedShortLabel)
                                if (!track.isVisible) add(hiddenShortLabel)
                            }
                            val trackSummary = statusBits.joinToString(" · ").ifEmpty {
                                pluralStringResource(
                                    R.plurals.timeline_clip_count,
                                    track.clips.size,
                                    track.clips.size
                                )
                            }
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(currentTrackHeight)
                                    .padding(bottom = 6.dp),
                                color = Mocha.Crust.copy(alpha = 0.98f),
                                shape = RoundedCornerShape(if (track.isCollapsed) 16.dp else 20.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (track.id == selectedTrackId) {
                                        trackColor.copy(alpha = 0.52f)
                                    } else {
                                        Mocha.CardStroke.copy(alpha = 0.72f)
                                    }
                                )
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.horizontalGradient(
                                                listOf(
                                                    trackColor.copy(alpha = 0.12f),
                                                    Mocha.Crust,
                                                    Mocha.Mantle
                                                )
                                            )
                                        )
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = if (isCompactTimeline) 8.dp else 9.dp, vertical = if (track.isCollapsed) 6.dp else 7.dp),
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Surface(
                                                color = trackColor.copy(alpha = 0.14f),
                                                shape = CircleShape
                                            ) {
                                                Icon(
                                                    imageVector = trackIcon(track.type),
                                                    contentDescription = null,
                                                    tint = trackColor,
                                                    modifier = Modifier
                                                        .padding(if (isCompactTimeline) 6.dp else 7.dp)
                                                        .size(if (isCompactTimeline) 12.dp else 14.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(if (isCompactTimeline) 6.dp else 8.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "${compactTrackLabelForType(track.type)} ${track.index + 1}",
                                                    color = Mocha.Text,
                                                    style = if (isCompactTimeline) {
                                                        MaterialTheme.typography.labelMedium
                                                    } else {
                                                        MaterialTheme.typography.labelLarge
                                                    },
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = trackSummary,
                                                    color = Mocha.Subtext0,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            TimelineMiniIconButton(
                                                icon = if (track.isCollapsed) {
                                                    Icons.AutoMirrored.Filled.KeyboardArrowRight
                                                } else {
                                                    Icons.Default.KeyboardArrowDown
                                                },
                                                contentDescription = stringResource(
                                                    if (track.isCollapsed) R.string.track_expand else R.string.track_collapse
                                                ),
                                                active = true,
                                                accent = trackColor,
                                                compact = true,
                                                onClick = { onToggleTrackCollapsed(track.id) }
                                            )
                                        }

                                        if (!track.isCollapsed) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                if (track.type == TrackType.AUDIO) {
                                                    TimelineMiniIconButton(
                                                        icon = Icons.Default.GraphicEq,
                                                        contentDescription = stringResource(R.string.track_waveform_toggle),
                                                        active = track.showWaveform,
                                                        accent = trackColor,
                                                        compact = true,
                                                        onClick = { onToggleTrackWaveform(track.id) }
                                                    )
                                                } else {
                                                    TimelineMiniIconButton(
                                                        icon = if (track.isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                        contentDescription = stringResource(R.string.timeline_toggle_visibility),
                                                        active = track.isVisible,
                                                        accent = trackColor,
                                                        compact = true,
                                                        onClick = { onToggleTrackVisible(track.id) }
                                                    )
                                                }
                                                TimelineMiniIconButton(
                                                    icon = if (track.isMuted) {
                                                        Icons.AutoMirrored.Filled.VolumeOff
                                                    } else {
                                                        Icons.AutoMirrored.Filled.VolumeUp
                                                    },
                                                    contentDescription = stringResource(R.string.timeline_toggle_mute),
                                                    active = !track.isMuted,
                                                    accent = trackColor,
                                                    compact = true,
                                                    onClick = { onToggleTrackMute(track.id) }
                                                )
                                                TimelineMiniIconButton(
                                                    icon = if (track.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                                    contentDescription = stringResource(R.string.timeline_toggle_lock),
                                                    active = !track.isLocked,
                                                    accent = trackColor,
                                                    compact = true,
                                                    onClick = { onToggleTrackLock(track.id) }
                                                )
                                                Box {
                                                    TimelineMiniIconButton(
                                                        icon = Icons.Default.MoreHoriz,
                                                        contentDescription = stringResource(R.string.timeline_track_more_options),
                                                        active = false,
                                                        accent = trackColor,
                                                        compact = true,
                                                        onClick = { trackMenuExpanded = true }
                                                    )
                                                    DropdownMenu(
                                                        expanded = trackMenuExpanded,
                                                        onDismissRequest = { trackMenuExpanded = false },
                                                        containerColor = Mocha.PanelHighest,
                                                        shape = RoundedCornerShape(18.dp)
                                                    ) {
                                                        DropdownMenuItem(
                                                            text = {
                                                                Text(
                                                                    text = stringResource(R.string.timeline_track_make_smaller),
                                                                    color = Mocha.Text
                                                                )
                                                            },
                                                            leadingIcon = {
                                                                Icon(
                                                                    imageVector = Icons.Default.Remove,
                                                                    contentDescription = null,
                                                                    tint = trackColor
                                                                )
                                                            },
                                                            onClick = {
                                                                trackMenuExpanded = false
                                                                onSetTrackHeight(track.id, track.trackHeight - 16)
                                                            }
                                                        )
                                                        DropdownMenuItem(
                                                            text = {
                                                                Text(
                                                                    text = stringResource(R.string.timeline_track_make_larger),
                                                                    color = Mocha.Text
                                                                )
                                                            },
                                                            leadingIcon = {
                                                                Icon(
                                                                    imageVector = Icons.Default.Add,
                                                                    contentDescription = null,
                                                                    tint = trackColor
                                                                )
                                                            },
                                                            onClick = {
                                                                trackMenuExpanded = false
                                                                onSetTrackHeight(track.id, track.trackHeight + 16)
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Mocha.Mantle.copy(alpha = 0.98f),
                                    Mocha.Base
                                )
                            )
                        )
                        .border(1.dp, Mocha.CardStroke.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
                        .clipToBounds()
                        .onSizeChanged {
                            timelineWidthPx = it.width.toFloat()
                            onTimelineWidthChanged(timelineWidthPx)
                        }
                        .pointerInput(Unit) {
                            detectTransformGestures { centroid, pan, zoom, _ ->
                                // NaN guard: `coerceIn` does not clamp NaN (all NaN
                                // comparisons return false), so a single bad gesture frame
                                // would propagate NaN through scroll offset and permanently
                                // break the timeline until the activity is rebuilt.
                                val safeZoomFactor = if (zoom.isFinite() && zoom > 0f) zoom else 1f
                                val safePan = if (pan.x.isFinite()) pan.x else 0f
                                val safeCentroidX = if (centroid.x.isFinite()) centroid.x else 0f
                                val oldPpm = (currentZoomLevel * BASE_SCALE).coerceAtLeast(0.0001f)
                                val newZoom = (currentZoomLevel * safeZoomFactor).coerceIn(MIN_TIMELINE_ZOOM, MAX_TIMELINE_ZOOM)
                                val newPpm = (newZoom * BASE_SCALE).coerceAtLeast(0.0001f)
                                // Adjust scroll to keep the pinch center point stable
                                val centerMs = currentScrollOffsetMs + (safeCentroidX / oldPpm).toLong()
                                val newScroll = centerMs - (safeCentroidX / newPpm).toLong()
                                onZoomChanged(newZoom)
                                val panMs = (safePan / newPpm).toLong()
                                onScrollChanged((newScroll - panMs).coerceAtLeast(0L))
                            }
                        }
                ) {
                    // Tapped marker tooltip state
                    var tappedMarkerId by remember { mutableStateOf<String?>(null) }

                    Column {
                    // Time ruler — tap and drag to position playhead
                    var rulerDragX by remember { mutableFloatStateOf(0f) }
                    Box {
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(rulerHeight)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            Mocha.Crust,
                                            Mocha.Mantle
                                        )
                                    )
                                )
                                .pointerInput(Unit) {
                                    detectTapGestures { offset ->
                                        // Check if tap is on a marker flag
                                        val ppm = currentZoomLevel * BASE_SCALE
                                        val flagWidthPx = 8.dp.toPx()
                                        val tappedMarker = currentMarkers.firstOrNull { marker ->
                                            val markerX = (marker.timeMs - currentScrollOffsetMs) * ppm
                                            offset.x in (markerX - flagWidthPx / 2)..(markerX + flagWidthPx / 2)
                                        }
                                        if (tappedMarker != null) {
                                            tappedMarkerId = if (tappedMarkerId == tappedMarker.id) null else tappedMarker.id
                                            onMarkerTapped(tappedMarker)
                                        } else {
                                            tappedMarkerId = null
                                            // Move playhead to tap position
                                            if (ppm > 0.001f) {
                                                val tappedMs = currentScrollOffsetMs + (offset.x / ppm).toLong()
                                                onPlayheadMoved(tappedMs.coerceIn(0L, currentTotalDurationMs))
                                            }
                                        }
                                    }
                                }
                                .pointerInput(Unit) {
                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            rulerDragX = offset.x
                                            onScrubStart()
                                            val ppm = currentZoomLevel * BASE_SCALE
                                            if (ppm > 0.001f) {
                                                val tappedMs = currentScrollOffsetMs + (offset.x / ppm).toLong()
                                                onPlayheadMoved(tappedMs.coerceIn(0L, currentTotalDurationMs))
                                            }
                                        },
                                        onDragEnd = { onScrubEnd() },
                                        onDragCancel = { onScrubEnd() },
                                        onDrag = { _, dragAmount ->
                                            rulerDragX += dragAmount.x
                                            val ppm = currentZoomLevel * BASE_SCALE
                                            if (ppm < 0.001f) return@detectDragGestures
                                            val posMs = currentScrollOffsetMs + (rulerDragX / ppm).toLong()
                                            onPlayheadMoved(posMs.coerceIn(0L, currentTotalDurationMs))
                                        }
                                    )
                                }
                        ) {
                            drawTimeRuler(
                                scrollOffsetMs = scrollOffsetMs,
                                pixelsPerMs = pixelsPerMs,
                                width = size.width,
                                height = size.height,
                                textMeasurer = textMeasurer
                            )

                            // Draw timeline marker flags
                            val flagWidthPx = 8.dp.toPx()
                            val flagHeightPx = 12.dp.toPx()
                            markers.forEach { marker ->
                                val markerX = (marker.timeMs - scrollOffsetMs) * pixelsPerMs
                                if (markerX in -flagWidthPx..size.width + flagWidthPx) {
                                    val markerColor = Color(marker.color.argb)
                                    // Draw flag pole
                                    drawLine(
                                        color = markerColor,
                                        start = Offset(markerX, 0f),
                                        end = Offset(markerX, size.height),
                                        strokeWidth = 1.5f
                                    )
                                    // Draw triangular flag
                                    val flagPath = Path().apply {
                                        moveTo(markerX, 0f)
                                        lineTo(markerX + flagWidthPx, flagHeightPx * 0.4f)
                                        lineTo(markerX, flagHeightPx)
                                        close()
                                    }
                                    drawPath(flagPath, markerColor)
                                }
                            }
                        }

                        // Marker label tooltip
                        val tappedMarker = markers.find { it.id == tappedMarkerId }
                        if (tappedMarker != null && tappedMarker.label.isNotEmpty()) {
                            val markerX = (tappedMarker.timeMs - scrollOffsetMs) * pixelsPerMs
                            Box(
                                modifier = Modifier
                                    .offset(
                                        x = with(density) { markerX.toDp() - 40.dp },
                                        y = rulerHeight
                                    )
                                    .background(
                                        Color(tappedMarker.color.argb).copy(alpha = 0.9f),
                                        RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = tappedMarker.label,
                                    color = Mocha.Crust,
                                    fontSize = 9.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Tracks
                    for (track in tracks) {
                    val currentTrackHeight = if (track.isCollapsed) 28.dp else track.trackHeight.dp
                    key(track.id) {
                        val trackColor = trackAccentColor(track.type)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(currentTrackHeight)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            trackColor.copy(alpha = 0.06f),
                                            Mocha.Base,
                                            Mocha.Mantle
                                        )
                                    )
                                )
                                .border(
                                    width = 0.5.dp,
                                    color = Mocha.Surface0.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(0.dp)
                                )
                                .pointerInput(track.id) {
                                    detectTapGestures(
                                        onTap = { offset ->
                                            val ppm = currentZoomLevel * BASE_SCALE
                                            val tappedMs = currentScrollOffsetMs + (offset.x / ppm).toLong()
                                            val trackClips = currentTracks.firstOrNull { it.id == track.id }?.clips ?: return@detectTapGestures
                                            val clip = trackClips.firstOrNull {
                                                it.containsTimelinePosition(tappedMs)
                                            }
                                            if (clip != null) {
                                                onClipSelected(clip.id, track.id)
                                            } else {
                                                onClipSelected(null, null)
                                            }
                                            onPlayheadMoved(tappedMs.coerceIn(0L, currentTotalDurationMs))
                                        },
                                        onLongPress = { offset ->
                                            val ppm = currentZoomLevel * BASE_SCALE
                                            val tappedMs = currentScrollOffsetMs + (offset.x / ppm).toLong()
                                            val trackClips = currentTracks.firstOrNull { it.id == track.id }?.clips ?: return@detectTapGestures
                                            val clip = trackClips.firstOrNull {
                                                it.containsTimelinePosition(tappedMs)
                                            }
                                            if (clip != null) {
                                                onClipLongPress(clip.id)
                                            }
                                        }
                                    )
                                }
                        ) {
                            if (track.isCollapsed) {
                                for (clip in track.clips) {
                                    val clipStartPx = (clip.timelineStartMs - scrollOffsetMs) * pixelsPerMs
                                    val clipWidthPx = clip.durationMs * pixelsPerMs
                                    if (clipStartPx + clipWidthPx > 0 && clipStartPx < timelineWidthPx) {
                                        Box(
                                            modifier = Modifier
                                                .offset(x = with(density) { clipStartPx.toDp() })
                                                .size(width = with(density) { clipWidthPx.toDp() }, height = 16.dp)
                                                .padding(vertical = 3.dp)
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(trackColor.copy(alpha = 0.6f))
                                        )
                                    }
                                }
                            } else {
                            if (track.clips.isEmpty()) {
                                Text(
                                    text = stringResource(
                                        if (isCompactTimeline) {
                                            R.string.timeline_track_empty_compact
                                        } else {
                                            R.string.timeline_track_empty
                                        }
                                    ),
                                    color = Mocha.Subtext0,
                                    style = if (isCompactTimeline) {
                                        MaterialTheme.typography.labelSmall
                                    } else {
                                        MaterialTheme.typography.labelMedium
                                    },
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                )
                            }
                            // Draw clips
                            track.clips.forEachIndexed { clipIdx, clip ->
                                val clipStartPx = ((clip.timelineStartMs - scrollOffsetMs) * pixelsPerMs)
                                val clipWidthPx = (clip.durationMs * pixelsPerMs)
                                val nextClipTransition = track.clips.getOrNull(clipIdx + 1)?.transition

                                if (clipStartPx + clipWidthPx > 0 && clipStartPx < timelineWidthPx) {
                                    val isSelected = clip.id == selectedClipId
                                    val isMultiSelected = clip.id in selectedClipIds
                                    val clipColor = trackColor
                                    val clipFileName = formatTimelineClipName(
                                        rawName = clip.sourceUri.lastPathSegment,
                                        fallback = clipLabelForType(track.type)
                                    )
                                    val showTrackBadge = clipWidthPx > 132f
                                    val showSpeedBadge = clip.speed != 1f && clipWidthPx > 164f
                                    val showEffectsBadge = clip.effects.isNotEmpty() && clipWidthPx > 152f
                                    val showClipName = clipWidthPx > 84f
                                    val showKeyframeBadge = clip.keyframes.isNotEmpty() && clipWidthPx > 152f
                                    val compactClipBadges = clipWidthPx < 150f
                                    val clipContentPaddingHorizontal = if (compactClipBadges) 6.dp else 8.dp
                                    val clipContentPaddingVertical = if (compactClipBadges) 6.dp else 7.dp
                                    val clipContentDescription = stringResource(
                                        R.string.timeline_clip_content_description,
                                        clipFileName,
                                        formatTimelineDurationLabel(clip.durationMs),
                                        formatTimelineTime(clip.timelineStartMs)
                                    )
                                    val selectClipActionLabel = stringResource(R.string.timeline_select_clip_action)

                                    // Hoist the per-clip background brush. Timeline recomposes on every
                                    // playhead tick (~30 Hz during playback); without this, each visible
                                    // clip allocates a fresh List + Brush per frame. Keying on the three
                                    // values that actually drive the gradient lets Compose reuse the same
                                    // Brush instance until selection or track-color state changes.
                                    val clipBackgroundBrush = remember(isSelected, isMultiSelected, clipColor) {
                                        Brush.horizontalGradient(
                                            when {
                                                isSelected -> listOf(
                                                    clipColor.copy(alpha = 0.64f),
                                                    Mocha.PanelHighest.copy(alpha = 0.94f)
                                                )
                                                isMultiSelected -> listOf(
                                                    Mocha.Peach.copy(alpha = 0.58f),
                                                    Mocha.PanelHighest.copy(alpha = 0.9f)
                                                )
                                                else -> listOf(
                                                    clipColor.copy(alpha = 0.44f),
                                                    Mocha.Panel.copy(alpha = 0.92f)
                                                )
                                            }
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .offset(x = with(density) { clipStartPx.toDp() })
                                            .width(with(density) { clipWidthPx.toDp() })
                                            .fillMaxHeight()
                                            .padding(vertical = 4.dp, horizontal = 1.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(clipBackgroundBrush)
                                            .alpha(if (track.isLocked) 0.7f else 1f)
                                            .then(
                                                Modifier.border(
                                                    if (isSelected) 2.dp else 1.dp,
                                                    when {
                                                        isSelected -> clipColor
                                                        isMultiSelected -> Mocha.Peach.copy(alpha = 0.85f)
                                                        else -> clipColor.copy(alpha = 0.25f)
                                                    },
                                                    RoundedCornerShape(12.dp)
                                                )
                                            )
                                            .semantics {
                                                contentDescription = clipContentDescription
                                                role = Role.Button
                                                selected = isSelected || isMultiSelected
                                                onClick(label = selectClipActionLabel) {
                                                    onClipSelected(clip.id, track.id)
                                                    true
                                                }
                                            }
                                            .then(
                                                // UNIFIED clip gesture handler. Replaces the previous tree of three
                                                // competing pointer-inputs (parent body-drag + left-handle drag + right-handle
                                                // drag) with a single `detectDragGestures` that decides the gesture *zone*
                                                // at drag-start based on where the touch landed. This removes the race
                                                // condition where the parent's drag detector was consuming edge-touch
                                                // events before the child handle detectors could react, which is why
                                                // trim-edge dragging was unresponsive on many devices.
                                                if (!track.isLocked) Modifier.pointerInput(clip.id, currentIsTrimMode) {
                                                    val trimHandleWidthPx = trimHandleTouchWidth.toPx()
                                                    var zone: ClipGestureZone = ClipGestureZone.NONE
                                                    detectDragGestures(
                                                        onDragStart = { offset ->
                                                            val ppm = currentZoomLevel * BASE_SCALE
                                                            val currentClip = currentTracks.flatMap { it.clips }
                                                                .firstOrNull { it.id == clip.id }
                                                            val clipWidthLocal = if (currentClip != null && ppm > 0.0001f) {
                                                                currentClip.durationMs * ppm
                                                            } else 0f
                                                            zone = when {
                                                                offset.x < trimHandleWidthPx -> ClipGestureZone.TRIM_LEFT
                                                                offset.x > clipWidthLocal - trimHandleWidthPx -> ClipGestureZone.TRIM_RIGHT
                                                                currentIsTrimMode -> ClipGestureZone.SLIP
                                                                else -> ClipGestureZone.SLIDE
                                                            }
                                                            onClipSelected(clip.id, track.id)
                                                            when (zone) {
                                                                ClipGestureZone.TRIM_LEFT,
                                                                ClipGestureZone.TRIM_RIGHT -> onTrimDragStarted()
                                                                ClipGestureZone.SLIP -> onSlipEditStarted()
                                                                ClipGestureZone.SLIDE -> onSlideEditStarted()
                                                                ClipGestureZone.NONE -> Unit
                                                            }
                                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        },
                                                        onDragEnd = {
                                                            when (zone) {
                                                                ClipGestureZone.TRIM_LEFT,
                                                                ClipGestureZone.TRIM_RIGHT -> onTrimDragEnded()
                                                                ClipGestureZone.SLIP -> onSlipEditEnded()
                                                                ClipGestureZone.SLIDE -> onSlideEditEnded()
                                                                ClipGestureZone.NONE -> Unit
                                                            }
                                                            zone = ClipGestureZone.NONE
                                                        },
                                                        onDragCancel = {
                                                            when (zone) {
                                                                ClipGestureZone.TRIM_LEFT,
                                                                ClipGestureZone.TRIM_RIGHT -> onTrimDragEnded()
                                                                ClipGestureZone.SLIP -> onSlipEditEnded()
                                                                ClipGestureZone.SLIDE -> onSlideEditEnded()
                                                                ClipGestureZone.NONE -> Unit
                                                            }
                                                            zone = ClipGestureZone.NONE
                                                        },
                                                        onDrag = { change, dragAmount ->
                                                            val ppm = currentZoomLevel * BASE_SCALE
                                                            if (ppm < 0.001f) return@detectDragGestures
                                                            val currentClip = currentTracks.flatMap { it.clips }
                                                                .firstOrNull { it.id == clip.id } ?: return@detectDragGestures
                                                            val deltaMs = (dragAmount.x / ppm).toLong()
                                                            when (zone) {
                                                                ClipGestureZone.TRIM_LEFT -> {
                                                                    val newStart = (currentClip.trimStartMs + deltaMs)
                                                                        .coerceAtLeast(0L)
                                                                        .coerceAtMost(currentClip.trimEndMs - 100L)
                                                                    onTrimChanged(clip.id, newStart, null)
                                                                    change.consume()
                                                                }
                                                                ClipGestureZone.TRIM_RIGHT -> {
                                                                    val newEnd = (currentClip.trimEndMs + deltaMs)
                                                                        .coerceIn(
                                                                            currentClip.trimStartMs + 100L,
                                                                            currentClip.sourceDurationMs
                                                                        )
                                                                    onTrimChanged(clip.id, null, newEnd)
                                                                    change.consume()
                                                                }
                                                                ClipGestureZone.SLIP -> {
                                                                    onSlipClip(clip.id, deltaMs)
                                                                    change.consume()
                                                                }
                                                                ClipGestureZone.SLIDE -> {
                                                                    onSlideClip(clip.id, deltaMs)
                                                                    val snapThreshMs = (12.dp.toPx() / ppm).toLong()
                                                                    val snapTargetsLocal = currentTracks
                                                                        .flatMap { t -> t.clips.filter { it.id != clip.id }
                                                                            .flatMap { listOf(it.timelineStartMs, it.timelineEndMs) } }
                                                                        .plus(currentPlayheadMs).plus(0L)
                                                                        .let { if (snapToBeat) it + beatMarkers else it }
                                                                        .let { if (snapToMarker) it + markers.map { m -> m.timeMs } else it }
                                                                    if (findSnapTarget(currentClip.timelineStartMs + deltaMs, snapTargetsLocal, snapThreshMs) != null) {
                                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                                    }
                                                                    change.consume()
                                                                }
                                                                ClipGestureZone.NONE -> Unit
                                                            }
                                                        }
                                                    )
                                                } else Modifier
                                            )
                                    ) {
                                        if (clip.clipLabel != ClipLabel.NONE) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(3.dp)
                                                    .background(Color(clip.clipLabel.argb))
                                            )
                                        }
                                        // Thumbnail strip for video tracks
                                        if (track.type == TrackType.VIDEO) {
                                            val key = "${clip.id}_${quantizedZoom}"
                                            val thumbs = thumbnails[key]
                                            if (thumbs != null && thumbs.isNotEmpty()) {
                                                Row(
                                                    modifier = Modifier.fillMaxSize()
                                                ) {
                                                    thumbs.forEach { bitmap ->
                                                        Image(
                                                            bitmap = bitmap.asImageBitmap(),
                                                            contentDescription = stringResource(R.string.cd_clip_thumbnail),
                                                            modifier = Modifier
                                                                .weight(1f)
                                                                .fillMaxHeight(),
                                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        // Audio waveform + volume envelope
                                        if (track.type == TrackType.AUDIO) {
                                            val waveform = waveforms[clip.id]
                                            val volumeKfs = volumeKeyframesSorted(clip)
                                            Canvas(modifier = Modifier.fillMaxSize()) {
                                                if (track.showWaveform) {
                                                    if (waveform != null && waveform.isNotEmpty()) {
                                                        drawWaveform(waveform, clipColor)
                                                    } else {
                                                        drawWaveformPlaceholder(clipColor)
                                                    }
                                                }
                                                if (volumeKfs.size >= 2 && clip.durationMs > 0) {
                                                    val path = Path()
                                                    val steps = 100
                                                    for (i in 0..steps) {
                                                        val t = i.toFloat() / steps
                                                        val timeMs = (t * clip.durationMs).toLong()
                                                        val vol = com.novacut.editor.engine.KeyframeEngine.getValueAt(
                                                            volumeKfs, KeyframeProperty.VOLUME, timeMs
                                                        ) ?: clip.volume
                                                        val x = t * size.width
                                                        val y = size.height * (1f - (vol / 2f).coerceIn(0f, 1f))
                                                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                                                    }
                                                    drawPath(
                                                        path,
                                                        Color(0xFFF9E2AF), // Yellow
                                                        style = Stroke(width = 1.5f)
                                                    )
                                                    // Draw keyframe dots on envelope (zero-duration guard above protects the divide).
                                                    val durF = clip.durationMs.toFloat()
                                                    volumeKfs.forEach { kf ->
                                                        val x = (kf.timeOffsetMs.toFloat() / durF) * size.width
                                                        val y = size.height * (1f - (kf.value / 2f).coerceIn(0f, 1f))
                                                        drawCircle(Color(0xFFF9E2AF), 3f, Offset(x, y))
                                                    }
                                                }
                                            }
                                        }

                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    Brush.verticalGradient(
                                                        listOf(
                                                            Color.Transparent,
                                                            Mocha.Crust.copy(alpha = 0.18f),
                                                            Mocha.Crust.copy(alpha = 0.42f)
                                                        )
                                                    )
                                                )
                                        )

                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = clipContentPaddingHorizontal, vertical = clipContentPaddingVertical),
                                            verticalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                if (showTrackBadge) {
                                                    TimelineClipBadge(
                                                        text = compactTrackLabelForType(track.type),
                                                        accent = clipColor,
                                                        compact = compactClipBadges
                                                    )
                                                }
                                                if (showSpeedBadge) {
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    TimelineClipBadge(
                                                        text = formatSpeedLabel(clip.speed),
                                                        accent = Mocha.Yellow,
                                                        compact = compactClipBadges
                                                    )
                                                }
                                                Spacer(modifier = Modifier.weight(1f))
                                                if (track.isLocked) {
                                                    Icon(
                                                        imageVector = Icons.Default.Lock,
                                                        contentDescription = null,
                                                        tint = Mocha.Text.copy(alpha = 0.72f),
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                }
                                                if (showEffectsBadge) {
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    TimelineClipBadge(
                                                        text = "FX ${clip.effects.size}",
                                                        accent = Mocha.Mauve,
                                                        compact = compactClipBadges
                                                    )
                                                }
                                            }

                                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                if (showClipName) {
                                                    Text(
                                                        text = clipFileName,
                                                        color = Mocha.Text,
                                                        style = if (compactClipBadges) {
                                                            MaterialTheme.typography.labelMedium
                                                        } else {
                                                            MaterialTheme.typography.labelLarge
                                                        },
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    TimelineClipBadge(
                                                        text = formatTimelineDurationLabel(clip.durationMs),
                                                        accent = Mocha.Sky,
                                                        compact = compactClipBadges
                                                    )
                                                    if (showKeyframeBadge) {
                                                        TimelineClipBadge(
                                                            text = "${clip.keyframes.size} KF",
                                                            accent = Mocha.Rosewater,
                                                            compact = compactClipBadges
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        // Trim-handle visuals. The pointerInput that actually drives edge-drag
                                        // lives on the clip body Box above (see the ClipGestureZone dispatch).
                                        // Keeping these as pure visual layers avoids the old three-way gesture
                                        // race where nested pointerInputs competed with the body drag detector.
                                        // When the clip is selected the handles become noticeably thicker so the
                                        // user has an obvious visual cue of the draggable zone (matches CapCut /
                                        // KineMaster edit UX).
                                        val trimHandleColor = if (isSelected) clipColor else clipColor.copy(alpha = 0.5f)
                                        val handleVisualWidth = if (isSelected) trimHandleVisualWidth + 4.dp else trimHandleVisualWidth

                                        // Left trim handle (visual only)
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.CenterStart)
                                                .width(handleVisualWidth)
                                                .fillMaxHeight()
                                                .background(
                                                    trimHandleColor,
                                                    RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)
                                                )
                                        ) {
                                            if (isSelected) {
                                                // Grip lines for affordance
                                                Canvas(modifier = Modifier.fillMaxSize()) {
                                                    val cx = size.width * 0.5f
                                                    val gap = 3f * density.density
                                                    for (i in -1..1) {
                                                        drawLine(
                                                            color = Mocha.Crust.copy(alpha = 0.85f),
                                                            start = Offset(cx + i * gap, size.height * 0.28f),
                                                            end = Offset(cx + i * gap, size.height * 0.72f),
                                                            strokeWidth = 1.2f * density.density
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        // Right trim handle (visual only)
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.CenterEnd)
                                                .width(handleVisualWidth)
                                                .fillMaxHeight()
                                                .background(
                                                    trimHandleColor,
                                                    RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp)
                                                )
                                        ) {
                                            if (isSelected) {
                                                Canvas(modifier = Modifier.fillMaxSize()) {
                                                    val cx = size.width * 0.5f
                                                    val gap = 3f * density.density
                                                    for (i in -1..1) {
                                                        drawLine(
                                                            color = Mocha.Crust.copy(alpha = 0.85f),
                                                            start = Offset(cx + i * gap, size.height * 0.28f),
                                                            end = Offset(cx + i * gap, size.height * 0.72f),
                                                            strokeWidth = 1.2f * density.density
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        // Transition-in zone overlay
                                        if (clip.transition != null) {
                                            val transWidthPx = clip.transition.durationMs * pixelsPerMs
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.CenterStart)
                                                    .width(with(density) { transWidthPx.coerceAtLeast(8f).toDp() })
                                                    .fillMaxHeight()
                                                    .background(
                                                        Brush.horizontalGradient(
                                                            colors = listOf(
                                                                Mocha.Yellow.copy(alpha = 0.5f),
                                                                Mocha.Yellow.copy(alpha = 0f)
                                                            )
                                                        )
                                                    )
                                            ) {
                                                // Transition type icon
                                                Icon(
                                                    imageVector = Icons.Filled.SwapHoriz,
                                                    contentDescription = null,
                                                    tint = Mocha.Yellow,
                                                    modifier = Modifier
                                                        .align(Alignment.CenterStart)
                                                        .padding(start = 1.dp)
                                                        .size(10.dp)
                                                )
                                            }
                                        }

                                        // Transition-out zone overlay (next clip has a transition)
                                        if (nextClipTransition != null) {
                                            val transOutWidthPx = nextClipTransition.durationMs * pixelsPerMs
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.CenterEnd)
                                                    .width(with(density) { transOutWidthPx.coerceAtLeast(8f).toDp() })
                                                    .fillMaxHeight()
                                                    .background(
                                                        Brush.horizontalGradient(
                                                            colors = listOf(
                                                                Mocha.Yellow.copy(alpha = 0f),
                                                                Mocha.Yellow.copy(alpha = 0.5f)
                                                            )
                                                        )
                                                    )
                                            )
                                        }

                                        // Keyframe dots
                                        if (clip.keyframes.isNotEmpty() && clipWidthPx > 20) {
                                            Canvas(modifier = Modifier.fillMaxSize()) {
                                                val clipDuration = clip.durationMs.toFloat()
                                                if (clipDuration <= 0) return@Canvas
                                                clip.keyframes.distinctBy { it.timeOffsetMs }.forEach { kf ->
                                                    val x = (kf.timeOffsetMs / clipDuration) * size.width
                                                    if (x in 0f..size.width) {
                                                        drawCircle(
                                                            color = Color(0xFFF5C2E7), // Mocha.Pink
                                                            radius = 3f,
                                                            center = Offset(x, size.height - 6f)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            } // end else (not collapsed)

                            // Beat markers
                            beatMarkers.forEach { beatMs ->
                                val beatPx = ((beatMs - scrollOffsetMs) * pixelsPerMs)
                                if (beatPx in 0f..timelineWidthPx) {
                                    Canvas(
                                        modifier = Modifier
                                            .offset(x = with(density) { beatPx.toDp() })
                                            .width(1.dp)
                                            .fillMaxHeight()
                                    ) {
                                        drawRect(
                                            color = Color(0x40F9E2AF), // Yellow semi-transparent
                                            size = Size(1f * density.density, size.height)
                                        )
                                    }
                                }
                            }

                            // Magnetic snap indicator (shows when clip edges align)
                            // Collect snap targets: all clip edges (except selected), playhead, and timeline origin
                            val selectedClipObj = track.clips.find { it.id == selectedClipId }
                            if (selectedClipObj != null) {
                                val snapTargets = track.clips
                                    .filter { it.id != selectedClipId }
                                    .flatMap { listOf(it.timelineStartMs, it.timelineEndMs) }
                                    .distinct()
                                    .plus(playheadMs)
                                    .plus(0L)
                                    .let { if (snapToBeat) it + beatMarkers else it }
                                    .let { if (snapToMarker) it + markers.map { m -> m.timeMs } else it }
                                val snapThresholdPx = with(density) { 8.dp.toPx() }
                                // Floor at 1ms so snapping still works at extreme zoom-in
                                // (where 8dp / pixelsPerMs would round to 0L and disable snap).
                                val snapThresholdMs = (snapThresholdPx / pixelsPerMs).toLong().coerceAtLeast(1L)

                                val startSnap = findSnapTarget(selectedClipObj.timelineStartMs, snapTargets, snapThresholdMs)
                                val endSnap = findSnapTarget(selectedClipObj.timelineEndMs, snapTargets, snapThresholdMs)
                                val snapPositions = listOfNotNull(startSnap, endSnap).distinct()

                                snapPositions.forEach { snapMs ->
                                    val snapPx = ((snapMs - scrollOffsetMs) * pixelsPerMs)
                                    if (snapPx in 0f..timelineWidthPx) {
                                        Canvas(
                                            modifier = Modifier
                                                .offset(x = with(density) { snapPx.toDp() })
                                                .width(2.dp)
                                                .fillMaxHeight()
                                        ) {
                                            drawRect(
                                                color = Color(0xFF89B4FA), // Blue snap line
                                                size = Size(2f * density.density, size.height)
                                            )
                                            // Draw small diamond indicators at top and bottom
                                            val diamondSize = 4f * density.density
                                            val topDiamond = Path().apply {
                                                moveTo(size.width / 2, 0f)
                                                lineTo(size.width / 2 + diamondSize, diamondSize)
                                                lineTo(size.width / 2, diamondSize * 2)
                                                lineTo(size.width / 2 - diamondSize, diamondSize)
                                                close()
                                            }
                                            drawPath(topDiamond, Color(0xFF89B4FA))
                                            val bottomDiamond = Path().apply {
                                                moveTo(size.width / 2, size.height)
                                                lineTo(size.width / 2 + diamondSize, size.height - diamondSize)
                                                lineTo(size.width / 2, size.height - diamondSize * 2)
                                                lineTo(size.width / 2 - diamondSize, size.height - diamondSize)
                                                close()
                                            }
                                            drawPath(bottomDiamond, Color(0xFF89B4FA))
                                        }
                                    }
                                }
                            }

                            // Playhead line
                            val playheadPx = ((playheadMs - scrollOffsetMs) * pixelsPerMs)
                            if (playheadPx in 0f..timelineWidthPx) {
                                Canvas(
                                    modifier = Modifier
                                        .offset(x = with(density) { playheadPx.toDp() })
                                        .width(2.dp)
                                        .fillMaxHeight()
                                ) {
                                    drawRect(
                                        color = Mocha.Sky,
                                        size = Size(2f * density.density, size.height)
                                    )
                                }
                            }
                        }
                    }
                    }

                // Playhead indicator on ruler
                val playheadPx = ((playheadMs - scrollOffsetMs) * pixelsPerMs)
                if (playheadPx >= 0) {
                    Canvas(
                        modifier = Modifier
                            .offset(x = with(density) { (playheadPx - 6).toDp() })
                            .size(12.dp, rulerHeight)
                    ) {
                        // Triangle playhead
                        val path = Path().apply {
                            moveTo(size.width / 2, size.height)
                            lineTo(0f, 0f)
                            lineTo(size.width, 0f)
                            close()
                        }
                        drawPath(path, Mocha.Sky)
                    }
                }
            }
        }
    }

            // Viewport overview / mini-scroll. Full-project-duration strip showing
            // clip footprints + the current viewport window. Tap-to-seek and
            // drag-to-scroll. This is the primary discovery cue for horizontal
            // scrolling now that long clips no longer fill the editable area — users
            // see at a glance "there is more content off-screen" and can jump to any
            // spot. Matches the scroll-strip present in CapCut and VN.
            if (totalDurationMs > 0L) {
                TimelineOverviewBar(
                    totalDurationMs = totalDurationMs,
                    scrollOffsetMs = scrollOffsetMs,
                    visibleDurationMs = visibleDurationMs,
                    playheadMs = playheadMs,
                    tracks = tracks,
                    contentPadding = contentPadding,
                    onScrollTo = { newOffsetMs -> onScrollChanged(newOffsetMs.coerceAtLeast(0L)) }
                )
            }
        }
    }
}

/**
 * Full-duration mini-strip shown below the tracks area. Paints one rectangle per
 * clip scaled to the whole project timeline, plus a highlighted "viewport" window
 * showing what part of the timeline is currently visible. Tap or drag to scroll.
 *
 * Intentionally a single fixed-height row — the point is to make horizontal scrolling
 * *discoverable* and direct-manipulable, not to replicate the full tracks hierarchy.
 */
@Composable
private fun TimelineOverviewBar(
    totalDurationMs: Long,
    scrollOffsetMs: Long,
    visibleDurationMs: Long,
    playheadMs: Long,
    tracks: List<Track>,
    contentPadding: Dp,
    onScrollTo: (Long) -> Unit
) {
    val density = LocalDensity.current
    val overviewHeight = 22.dp
    var widthPx by remember { mutableFloatStateOf(0f) }
    val overviewContentDescription = stringResource(R.string.cd_timeline_overview)

    val currentTotalDurationMs by rememberUpdatedState(totalDurationMs)
    val currentVisibleDurationMs by rememberUpdatedState(visibleDurationMs)

    fun tapXToScrollOffset(xPx: Float): Long {
        if (widthPx <= 0f || currentTotalDurationMs <= 0L) return scrollOffsetMs
        val fraction = (xPx / widthPx).coerceIn(0f, 1f)
        val targetMs = (fraction * currentTotalDurationMs).toLong()
        // Center the viewport on the tapped position so users don't have to aim at
        // the window's leading edge — matches CapCut / KineMaster scrollbar feel.
        return (targetMs - currentVisibleDurationMs / 2).coerceAtLeast(0L)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = contentPadding, vertical = 6.dp)
            .height(overviewHeight)
            .clip(RoundedCornerShape(10.dp))
            .background(Mocha.Crust.copy(alpha = 0.78f))
            .border(1.dp, Mocha.CardStroke.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .onSizeChanged { widthPx = it.width.toFloat() }
            .semantics { contentDescription = overviewContentDescription }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onScrollTo(tapXToScrollOffset(offset.x))
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    onScrollTo(tapXToScrollOffset(change.position.x))
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (currentTotalDurationMs <= 0L) return@Canvas
            val totalF = currentTotalDurationMs.toFloat()

            // Clip footprints
            tracks.forEach { track ->
                val barColor = trackAccentColor(track.type).copy(alpha = 0.55f)
                track.clips.forEach { clip ->
                    val startF = clip.timelineStartMs.toFloat() / totalF
                    val widthF = (clip.durationMs.toFloat() / totalF).coerceAtLeast(0.002f)
                    drawRect(
                        color = barColor,
                        topLeft = Offset(startF * size.width, size.height * 0.22f),
                        size = Size((widthF * size.width).coerceAtLeast(1f), size.height * 0.56f)
                    )
                }
            }

            // Viewport window
            val vStartF = (scrollOffsetMs.toFloat() / totalF).coerceIn(0f, 1f)
            val vWidthF = (currentVisibleDurationMs.toFloat() / totalF).coerceIn(0.01f, 1f)
            drawRect(
                color = Mocha.Sky.copy(alpha = 0.25f),
                topLeft = Offset(vStartF * size.width, 0f),
                size = Size(vWidthF * size.width, size.height)
            )
            drawRect(
                color = Mocha.Sky,
                topLeft = Offset(vStartF * size.width, 0f),
                size = Size(vWidthF * size.width, size.height),
                style = Stroke(width = 1.6f)
            )

            // Playhead
            val phF = (playheadMs.toFloat() / totalF).coerceIn(0f, 1f)
            drawLine(
                color = Mocha.Rosewater,
                start = Offset(phF * size.width, 0f),
                end = Offset(phF * size.width, size.height),
                strokeWidth = 1.4f
            )
        }
    }
}

@Composable
private fun TimelineToolbarButton(
    icon: ImageVector,
    contentDescription: String,
    compact: Boolean = false,
    highlight: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val backgroundColor = when {
        !enabled -> Mocha.PanelHighest.copy(alpha = 0.35f)
        highlight -> Mocha.Peach.copy(alpha = 0.22f)
        else -> Mocha.PanelHighest
    }
    val borderColor = when {
        !enabled -> Mocha.CardStroke.copy(alpha = 0.35f)
        highlight -> Mocha.Peach.copy(alpha = 0.7f)
        else -> Mocha.CardStroke
    }
    val iconTint = when {
        !enabled -> Mocha.Text.copy(alpha = 0.4f)
        highlight -> Mocha.Peach
        else -> Mocha.Text
    }
    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, borderColor)
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(if (compact) 34.dp else 38.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = iconTint,
                modifier = Modifier.size(if (compact) 16.dp else 18.dp)
            )
        }
    }
}

@Composable
private fun TimelineInfoChip(
    text: String,
    accent: Color,
    compact: Boolean = false
) {
    Surface(
        color = accent.copy(alpha = 0.13f),
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.2f))
    ) {
        Text(
            text = text,
            color = accent,
            style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(
                horizontal = if (compact) 8.dp else 10.dp,
                vertical = if (compact) 5.dp else 6.dp
            )
        )
    }
}

@Composable
private fun TimelineTextActionChip(
    text: String,
    compact: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        color = Mocha.PanelHighest,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, Mocha.CardStroke)
    ) {
        Text(
            text = text,
            color = Mocha.Text,
            style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(
                    horizontal = if (compact) 10.dp else 12.dp,
                    vertical = if (compact) 5.dp else 6.dp
                )
        )
    }
}

@Composable
private fun TimelineMiniIconButton(
    icon: ImageVector,
    contentDescription: String,
    active: Boolean,
    accent: Color,
    compact: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        color = if (active) accent.copy(alpha = 0.14f) else Mocha.Surface0.copy(alpha = 0.8f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            1.dp,
            if (active) accent.copy(alpha = 0.26f) else Mocha.CardStroke.copy(alpha = 0.5f)
        )
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(if (compact) 24.dp else 28.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (active) accent else Mocha.Subtext0,
                modifier = Modifier.size(if (compact) 12.dp else 14.dp)
            )
        }
    }
}

@Composable
private fun TimelineClipBadge(
    text: String,
    accent: Color,
    compact: Boolean = false
) {
    Surface(
        color = accent.copy(alpha = 0.18f),
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.24f))
    ) {
        Text(
            text = text,
            color = Mocha.Text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(
                horizontal = if (compact) 6.dp else 8.dp,
                vertical = if (compact) 3.dp else 4.dp
            )
        )
    }
}

private fun trackAccentColor(trackType: TrackType): Color = when (trackType) {
    TrackType.VIDEO -> Mocha.Blue
    TrackType.AUDIO -> Mocha.Green
    TrackType.OVERLAY -> Mocha.Peach
    TrackType.TEXT -> Mocha.Mauve
    TrackType.ADJUSTMENT -> Mocha.Yellow
}

private fun trackIcon(trackType: TrackType): ImageVector = when (trackType) {
    TrackType.VIDEO -> Icons.Default.Videocam
    TrackType.AUDIO -> Icons.Default.MusicNote
    TrackType.OVERLAY -> Icons.Default.Layers
    TrackType.TEXT -> Icons.Default.Title
    TrackType.ADJUSTMENT -> Icons.Default.Tune
}

private fun formatTimelineClipName(
    rawName: String?,
    fallback: String
): String {
    val cleaned = rawName
        ?.substringAfterLast('/')
        ?.substringBeforeLast('.')
        ?.replace("%20", " ")
        ?.replace(Regex("[_-]+"), " ")
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: return fallback

    val looksGenerated = !cleaned.any { it.isLetter() } ||
        Regex("^(img|vid|pxl|mvimg|screenshot|image|video)\\s*\\d[\\w\\s-]*$", RegexOption.IGNORE_CASE)
            .matches(cleaned)

    return if (looksGenerated) fallback else cleaned
}

private fun formatTimelineTime(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L) / 1000L).toInt()
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

private fun formatTimelineDurationLabel(ms: Long): String {
    return if (ms in 1L until 1_000L) {
        "%.1fs".format(Locale.US, (ms / 1000f).coerceAtLeast(0.1f))
    } else {
        formatTimelineTime(ms)
    }
}

private fun formatSpeedLabel(speed: Float): String {
    val rounded = if (abs(speed - speed.roundToInt()) < 0.05f) {
        speed.roundToInt().toString()
    } else {
        "%.1f".format(speed)
    }
    return "${rounded}x"
}

private fun DrawScope.drawTimeRuler(
    scrollOffsetMs: Long,
    pixelsPerMs: Float,
    width: Float,
    height: Float,
    textMeasurer: TextMeasurer
) {
    val intervalMs = when {
        pixelsPerMs > 0.5f -> 1000L
        pixelsPerMs > 0.1f -> 5000L
        pixelsPerMs > 0.02f -> 10000L
        else -> 30000L
    }

    val startMs = (scrollOffsetMs / intervalMs) * intervalMs
    var currentMs = startMs
    val labelStyle = TextStyle(
        color = Color(0xFFA6ADC8), // Mocha.Subtext0
        fontSize = 9.sp
    )

    while (true) {
        val x = (currentMs - scrollOffsetMs) * pixelsPerMs
        if (x > width) break

        if (x >= 0) {
            val isMajor = currentMs % (intervalMs * 5) == 0L
            val tickHeight = if (isMajor) height * 0.4f else height * 0.25f

            drawLine(
                color = if (isMajor) Color(0xFF7F849C) else Color(0xFF45475A),
                start = Offset(x, height - tickHeight),
                end = Offset(x, height),
                strokeWidth = if (isMajor) 1.5f else 0.5f
            )

            // Time labels at major ticks
            if (isMajor) {
                val totalSeconds = (currentMs / 1000).toInt()
                val min = totalSeconds / 60
                val sec = totalSeconds % 60
                val label = if (min > 0) "$min:${"%02d".format(sec)}" else "${sec}s"
                val measured = textMeasurer.measure(label, labelStyle)
                drawText(
                    textLayoutResult = measured,
                    topLeft = Offset(x - measured.size.width / 2f, 1f)
                )
            }
        }
        currentMs += intervalMs
    }
}

private fun DrawScope.drawWaveform(samples: List<Float>, color: Color) {
    if (samples.isEmpty()) return
    val steps = (size.width / 3f).toInt().coerceAtLeast(1)
    val samplesPerStep = samples.size.toFloat() / steps
    val centerY = size.height / 2f
    val maxAmp = size.height * 0.45f

    for (i in 0 until steps) {
        val sampleIndex = (i * samplesPerStep).toInt().coerceIn(0, samples.size - 1)
        val amplitude = samples[sampleIndex].coerceIn(0f, 1f)
        val barH = (amplitude * maxAmp).coerceAtLeast(1f)
        val x = i * 3f

        drawLine(
            color = color.copy(alpha = 0.7f),
            start = Offset(x, centerY - barH),
            end = Offset(x, centerY + barH),
            strokeWidth = 2f
        )
    }
}

/** Extract volume keyframes sorted by time — top-level to avoid deeply nested lambda class names (Windows MAX_PATH). */
private fun volumeKeyframesSorted(clip: com.novacut.editor.model.Clip): List<com.novacut.editor.model.Keyframe> =
    clip.keyframes
        .filter { it.property == KeyframeProperty.VOLUME }
        .sortedBy { it.timeOffsetMs }

private fun DrawScope.drawWaveformPlaceholder(color: Color) {
    // Deterministic pattern to avoid 30fps flicker from Math.random()
    val steps = (size.width / 4f).toInt().coerceAtLeast(1)
    val centerY = size.height / 2f
    for (i in 0 until steps) {
        val x = i * 4f
        val amplitude = (kotlin.math.sin(i * 0.7) * 0.3 + 0.4).toFloat()
        val barH = (amplitude * size.height * 0.45f).coerceAtLeast(1f)
        drawLine(
            color = color.copy(alpha = 0.4f),
            start = Offset(x, centerY - barH),
            end = Offset(x, centerY + barH),
            strokeWidth = 2f
        )
    }
}

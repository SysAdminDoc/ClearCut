package com.novacut.editor.ui.editor

import android.graphics.Bitmap
import androidx.compose.foundation.*
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.novacut.editor.R
import com.novacut.editor.engine.VideoEngine
import com.novacut.editor.model.*
import com.novacut.editor.ui.theme.Mocha
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private const val BASE_SCALE = 0.15f // pixels per ms at zoom 1.0
// Minimum zoom — low enough that a ~10-minute video fits on a phone screen. The old
// 0.1f floor meant long videos could never fit the viewport, which combined with no
// auto-fit-on-add made the timeline appear to only show a tiny portion of the media.
// File-private to avoid clashing with the same-named const in EditorViewModel.kt,
// which maintains its own copy so the VM logic doesn't have a cross-file dependency.
private const val MIN_TIMELINE_ZOOM = 0.01f
private const val MAX_TIMELINE_ZOOM = 10f

// Allocation-free clip lookup for drag handlers — they fire per pointer event
// (~60-120Hz), where the previous flatMap built a throwaway list each event.
private fun findClipInTracks(tracks: List<Track>, clipId: String): Clip? {
    for (track in tracks) {
        for (candidate in track.clips) {
            if (candidate.id == clipId) return candidate
        }
    }
    return null
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
    playheadMsProvider: (() -> Long)? = null,
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
    onOpenCompoundClip: (String) -> Boolean = { false },
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
    missingClipIds: Set<String> = emptySet(),
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
    val scrubBoundaries = remember(tracks, markers, beatMarkers) {
        val edges = mutableSetOf<Long>()
        tracks.forEach { track ->
            track.clips.forEach { clip ->
                edges.add(clip.timelineStartMs)
                edges.add(clip.timelineStartMs + clip.durationMs)
            }
        }
        markers.forEach { edges.add(it.timeMs) }
        if (snapToBeat) beatMarkers.forEach { edges.add(it) }
        edges.sorted()
    }
    var lastScrubBoundaryIdx by remember { mutableIntStateOf(-1) }
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
    val currentOnClipSelected by rememberUpdatedState(onClipSelected)
    val currentOnPlayheadMoved by rememberUpdatedState(onPlayheadMoved)
    val currentOnSplitAtPlayhead by rememberUpdatedState(onSplitAtPlayhead)
    val currentOnDeleteSelectedClip by rememberUpdatedState(onDeleteSelectedClip)
    val currentOnClipLongPress by rememberUpdatedState(onClipLongPress)
    val currentOnOpenCompoundClip by rememberUpdatedState(onOpenCompoundClip)
    val currentOnSlideClip by rememberUpdatedState(onSlideClip)
    val currentOnSlideEditStarted by rememberUpdatedState(onSlideEditStarted)
    val currentOnSlideEditEnded by rememberUpdatedState(onSlideEditEnded)
    val currentOnSlipClip by rememberUpdatedState(onSlipClip)
    val currentOnSlipEditStarted by rememberUpdatedState(onSlipEditStarted)
    val currentOnSlipEditEnded by rememberUpdatedState(onSlipEditEnded)
    val currentSelectedClipId by rememberUpdatedState(selectedClipId)

    // Hoist the vertical gradient overlay applied on top of every clip body. The
    // Timeline recomposes ~30 Hz during playback; without `remember` this brush
    // was being allocated fresh per clip per frame (a 10-clip project = 300
    // Brush + List allocations/sec). Brush contents are static, so one cached
    // instance covers the entire session.
    val clipOverlayBrush = remember {
        Brush.verticalGradient(
            listOf(
                Color.Transparent,
                Mocha.Crust.copy(alpha = 0.18f),
                Mocha.Crust.copy(alpha = 0.42f)
            )
        )
    }
    // 8dp snap threshold in px — constant for the lifetime of the density scope.
    val snapThresholdPx = with(density) { 8.dp.toPx() }
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
                    // When the track stack outgrows the card's external height
                    // cap, the tracks scroll vertically instead of clipping
                    // behind the tool rail.
                    .verticalScroll(rememberScrollState())
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
                                            lastScrubBoundaryIdx = -1
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
                                            val clampedMs = posMs.coerceIn(0L, currentTotalDurationMs)
                                            onPlayheadMoved(clampedMs)
                                            val nearIdx = scrubBoundaries.binarySearch(clampedMs).let { idx ->
                                                if (idx >= 0) idx
                                                else {
                                                    val insertionPoint = -(idx + 1)
                                                    when {
                                                        insertionPoint >= scrubBoundaries.size -> scrubBoundaries.size - 1
                                                        insertionPoint == 0 -> 0
                                                        else -> {
                                                            val before = scrubBoundaries[insertionPoint - 1]
                                                            val after = scrubBoundaries[insertionPoint]
                                                            if (clampedMs - before < after - clampedMs) insertionPoint - 1
                                                            else insertionPoint
                                                        }
                                                    }
                                                }
                                            }
                                            if (nearIdx >= 0 && nearIdx < scrubBoundaries.size && nearIdx != lastScrubBoundaryIdx) {
                                                val boundaryMs = scrubBoundaries[nearIdx]
                                                val distancePx = kotlin.math.abs((clampedMs - boundaryMs) * ppm)
                                                if (distancePx < 4.dp.toPx()) {
                                                    lastScrubBoundaryIdx = nearIdx
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                }
                                            }
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
                                            if (ppm < 0.001f) return@detectTapGestures
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
                                            if (ppm < 0.001f) return@detectTapGestures
                                            val tappedMs = currentScrollOffsetMs + (offset.x / ppm).toLong()
                                            val trackClips = currentTracks.firstOrNull { it.id == track.id }?.clips ?: return@detectTapGestures
                                            val clip = trackClips.firstOrNull {
                                                it.containsTimelinePosition(tappedMs)
                                            }
                                            if (clip != null) {
                                                dispatchTimelineClipLongPress(
                                                    clipId = clip.id,
                                                    isCompound = clip.isCompound,
                                                    onOpenCompoundClip = currentOnOpenCompoundClip,
                                                    onToggleMultiSelect = currentOnClipLongPress,
                                                )
                                            }
                                        }
                                    )
                                }
                        ) {
                            if (track.isCollapsed) {
                                for (clip in track.clips) {
                                    val clipLayout = timelineClipLayout(
                                        clip = clip,
                                        scrollOffsetMs = scrollOffsetMs,
                                        pixelsPerMs = pixelsPerMs
                                    )
                                    if (clipLayout.isVisibleIn(timelineWidthPx)) {
                                        Box(
                                            modifier = Modifier
                                                .offset(x = with(density) { clipLayout.startPx.toDp() })
                                                .size(width = with(density) { clipLayout.widthPx.toDp() }, height = 16.dp)
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
                                val clipLayout = timelineClipLayout(
                                    clip = clip,
                                    scrollOffsetMs = scrollOffsetMs,
                                    pixelsPerMs = pixelsPerMs
                                )
                                val clipStartPx = clipLayout.startPx
                                val clipWidthPx = clipLayout.widthPx
                                val nextClipTransition = clip.tailTransition ?: track.clips.getOrNull(clipIdx + 1)?.headTransition

                                if (clipLayout.isVisibleIn(timelineWidthPx)) {
                                    val isSelected = clip.id == selectedClipId
                                    val isMultiSelected = clip.id in selectedClipIds
                                    val clipColor = trackColor
                                    val clipFileName = formatTimelineClipName(
                                        rawName = clip.sourceUri.lastPathSegment,
                                        fallback = clipLabelForType(track.type)
                                    )
                                    val contentVisibility = timelineClipContentVisibility(clipWidthPx)
                                    val showTrackBadge = contentVisibility.showTrackBadge
                                    val showSpeedBadge = clip.speed != 1f && contentVisibility.showSpeedBadge
                                    val showEffectsBadge = clip.effects.isNotEmpty() && contentVisibility.showEffectsBadge
                                    val showClipName = contentVisibility.showClipName
                                    val showKeyframeBadge = clip.keyframes.isNotEmpty() && contentVisibility.showKeyframeBadge
                                    val compactClipBadges = contentVisibility.compactBadges
                                    val clipContentPaddingHorizontal = if (compactClipBadges) 6.dp else 8.dp
                                    val clipContentPaddingVertical = if (compactClipBadges) 6.dp else 7.dp
                                    val clipTypeLabel = clipLabelForType(track.type)
                                    val trackTypeLabel = trackLabelForType(track.type)
                                    val clipDurationLabel = formatTimelineDurationLabel(clip.durationMs)
                                    val clipStartLabel = formatTimelineTime(clip.timelineStartMs)
                                    val clipContentDescription = stringResource(
                                        R.string.timeline_clip_content_description,
                                        clipFileName,
                                        clipTypeLabel,
                                        trackTypeLabel,
                                        clipDurationLabel,
                                        clipStartLabel
                                    )
                                    val selectClipActionLabel = stringResource(R.string.timeline_select_clip_action)
                                    val lockedClipStateLabel = stringResource(R.string.timeline_clip_state_locked)
                                    val splitClipActionLabel = stringResource(R.string.timeline_clip_action_split)
                                    val deleteClipActionLabel = stringResource(R.string.timeline_clip_action_delete)
                                    val openCompoundActionLabel = stringResource(R.string.timeline_clip_action_open_compound)
                                    val nudgeDurationLabel = formatTimelineDurationLabel(ACCESSIBILITY_NUDGE_MS)
                                    val nudgeEarlierActionLabel = stringResource(
                                        R.string.timeline_clip_action_nudge_earlier,
                                        nudgeDurationLabel
                                    )
                                    val nudgeLaterActionLabel = stringResource(
                                        R.string.timeline_clip_action_nudge_later,
                                        nudgeDurationLabel
                                    )
                                    val clipCustomActions = remember(
                                        clip.id,
                                        track.id,
                                        track.isLocked,
                                        clip.timelineStartMs,
                                        clip.timelineEndMs,
                                        clip.durationMs,
                                        clip.isCompound,
                                        splitClipActionLabel,
                                        deleteClipActionLabel,
                                        openCompoundActionLabel,
                                        nudgeEarlierActionLabel,
                                        nudgeLaterActionLabel
                                    ) {
                                        if (track.isLocked) {
                                            emptyList()
                                        } else {
                                            buildList {
                                                if (clip.durationMs >= MIN_TIMELINE_CLIP_DURATION_MS * 2) {
                                                    add(
                                                        CustomAccessibilityAction(
                                                            label = splitClipActionLabel
                                                        ) {
                                                            val splitPointMs = clip.accessibleSplitPointMs(currentPlayheadMs)
                                                            if (splitPointMs == null) {
                                                                false
                                                            } else {
                                                                currentOnClipSelected(clip.id, track.id)
                                                                currentOnPlayheadMoved(splitPointMs)
                                                                currentOnSplitAtPlayhead()
                                                                true
                                                            }
                                                        }
                                                    )
                                                }
                                                add(
                                                    CustomAccessibilityAction(
                                                        label = deleteClipActionLabel
                                                    ) {
                                                        currentOnClipSelected(clip.id, track.id)
                                                        currentOnDeleteSelectedClip()
                                                        true
                                                    }
                                                )
                                                if (clip.isCompound) {
                                                    add(
                                                        CustomAccessibilityAction(
                                                            label = openCompoundActionLabel
                                                        ) {
                                                            currentOnOpenCompoundClip(clip.id)
                                                        }
                                                    )
                                                }
                                                add(
                                                    CustomAccessibilityAction(
                                                        label = nudgeEarlierActionLabel
                                                    ) {
                                                        currentOnClipSelected(clip.id, track.id)
                                                        currentOnSlideEditStarted()
                                                        currentOnSlideClip(clip.id, -ACCESSIBILITY_NUDGE_MS)
                                                        currentOnSlideEditEnded()
                                                        true
                                                    }
                                                )
                                                add(
                                                    CustomAccessibilityAction(
                                                        label = nudgeLaterActionLabel
                                                    ) {
                                                        currentOnClipSelected(clip.id, track.id)
                                                        currentOnSlideEditStarted()
                                                        currentOnSlideClip(clip.id, ACCESSIBILITY_NUDGE_MS)
                                                        currentOnSlideEditEnded()
                                                        true
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    var isKeyboardFocused by remember(clip.id) { mutableStateOf(false) }
                                    val runKeyboardNudge: (Long) -> Boolean = { deltaMs ->
                                        if (track.isLocked) {
                                            false
                                        } else {
                                            currentOnClipSelected(clip.id, track.id)
                                            if (currentIsTrimMode) {
                                                currentOnSlipEditStarted()
                                                currentOnSlipClip(clip.id, deltaMs)
                                                currentOnSlipEditEnded()
                                            } else {
                                                currentOnSlideEditStarted()
                                                currentOnSlideClip(clip.id, deltaMs)
                                                currentOnSlideEditEnded()
                                            }
                                            true
                                        }
                                    }
                                    val runKeyboardSplit: () -> Boolean = {
                                        if (track.isLocked) {
                                            false
                                        } else {
                                            val splitPointMs = clip.accessibleSplitPointMs(currentPlayheadMs)
                                            if (splitPointMs == null) {
                                                false
                                            } else {
                                                currentOnClipSelected(clip.id, track.id)
                                                currentOnPlayheadMoved(splitPointMs)
                                                currentOnSplitAtPlayhead()
                                                true
                                            }
                                        }
                                    }

                                    // Hoist the per-clip background brush. Timeline recomposes on every
                                    // playhead tick (~30 Hz during playback); without this, each visible
                                    // clip allocates a fresh List + Brush per frame. Keying on the three
                                    // values that actually drive the gradient lets Compose reuse the same
                                    // Brush instance until selection or track-color state changes.
                                    val isClipMissing = clip.id in missingClipIds
                                    val clipBackgroundBrush = remember(isSelected, isMultiSelected, isClipMissing, clipColor) {
                                        Brush.horizontalGradient(
                                            when {
                                                isClipMissing -> listOf(
                                                    Mocha.Red.copy(alpha = 0.38f),
                                                    Mocha.Crust.copy(alpha = 0.85f)
                                                )
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
                                                    if (isClipMissing) 2.dp else if (isSelected) 2.dp else 1.dp,
                                                    when {
                                                        isClipMissing -> Mocha.Red.copy(alpha = 0.85f)
                                                        isSelected -> clipColor
                                                        isKeyboardFocused -> Mocha.Sky.copy(alpha = 0.95f)
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
                                                if (track.isLocked) {
                                                    stateDescription = lockedClipStateLabel
                                                }
                                                onClick(label = selectClipActionLabel) {
                                                    onClipSelected(clip.id, track.id)
                                                    true
                                                }
                                                customActions = clipCustomActions
                                            }
                                            .onFocusChanged { isKeyboardFocused = it.isFocused }
                                            .onPreviewKeyEvent { event ->
                                                if (event.type != KeyEventType.KeyDown) {
                                                    false
                                                } else {
                                                    when (event.key) {
                                                        Key.Enter,
                                                        Key.NumPadEnter,
                                                        Key.DirectionCenter -> {
                                                            currentOnClipSelected(clip.id, track.id)
                                                            true
                                                        }
                                                        Key.DirectionLeft -> {
                                                            runKeyboardNudge(-keyboardNudgeAmountMs(event.isShiftPressed))
                                                        }
                                                        Key.DirectionRight -> {
                                                            runKeyboardNudge(keyboardNudgeAmountMs(event.isShiftPressed))
                                                        }
                                                        Key.S -> runKeyboardSplit()
                                                        Key.Delete,
                                                        Key.Backspace -> {
                                                            if (track.isLocked) {
                                                                false
                                                            } else {
                                                                currentOnClipSelected(clip.id, track.id)
                                                                currentOnDeleteSelectedClip()
                                                                true
                                                            }
                                                        }
                                                        else -> false
                                                    }
                                                }
                                            }
                                            .focusable()
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
                                                    var zone: TimelineClipGestureZone = TimelineClipGestureZone.NONE
                                                    detectDragGestures(
                                                        onDragStart = { offset ->
                                                            val ppm = currentZoomLevel * BASE_SCALE
                                                            val currentClip = findClipInTracks(currentTracks, clip.id)
                                                            val clipWidthLocal = currentClip?.durationMs?.times(ppm) ?: 0f
                                                            zone = resolveTimelineClipGestureZone(
                                                                touchXPx = offset.x,
                                                                clipWidthPx = clipWidthLocal,
                                                                trimHandleWidthPx = trimHandleWidthPx,
                                                                isTrimMode = currentIsTrimMode
                                                            )
                                                            if (zone == TimelineClipGestureZone.NONE) return@detectDragGestures
                                                            onClipSelected(clip.id, track.id)
                                                            when (zone) {
                                                                TimelineClipGestureZone.TRIM_LEFT,
                                                                TimelineClipGestureZone.TRIM_RIGHT -> onTrimDragStarted()
                                                                TimelineClipGestureZone.SLIP -> onSlipEditStarted()
                                                                TimelineClipGestureZone.SLIDE -> onSlideEditStarted()
                                                                TimelineClipGestureZone.NONE -> Unit
                                                            }
                                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        },
                                                        onDragEnd = {
                                                            when (zone) {
                                                                TimelineClipGestureZone.TRIM_LEFT,
                                                                TimelineClipGestureZone.TRIM_RIGHT -> onTrimDragEnded()
                                                                TimelineClipGestureZone.SLIP -> onSlipEditEnded()
                                                                TimelineClipGestureZone.SLIDE -> onSlideEditEnded()
                                                                TimelineClipGestureZone.NONE -> Unit
                                                            }
                                                            zone = TimelineClipGestureZone.NONE
                                                        },
                                                        onDragCancel = {
                                                            when (zone) {
                                                                TimelineClipGestureZone.TRIM_LEFT,
                                                                TimelineClipGestureZone.TRIM_RIGHT -> onTrimDragEnded()
                                                                TimelineClipGestureZone.SLIP -> onSlipEditEnded()
                                                                TimelineClipGestureZone.SLIDE -> onSlideEditEnded()
                                                                TimelineClipGestureZone.NONE -> Unit
                                                            }
                                                            zone = TimelineClipGestureZone.NONE
                                                        },
                                                        onDrag = { change, dragAmount ->
                                                            val ppm = currentZoomLevel * BASE_SCALE
                                                            if (ppm < 0.001f) return@detectDragGestures
                                                            val currentClip = findClipInTracks(currentTracks, clip.id)
                                                                ?: return@detectDragGestures
                                                            when (
                                                                val action = resolveTimelineClipGestureAction(
                                                                    zone = zone,
                                                                    clip = currentClip,
                                                                    deltaXPx = dragAmount.x,
                                                                    pixelsPerMs = ppm
                                                                )
                                                            ) {
                                                                is TimelineClipGestureAction.TrimLeft -> {
                                                                    onTrimChanged(clip.id, action.trimStartMs, null)
                                                                    change.consume()
                                                                }
                                                                is TimelineClipGestureAction.TrimRight -> {
                                                                    onTrimChanged(clip.id, null, action.trimEndMs)
                                                                    change.consume()
                                                                }
                                                                is TimelineClipGestureAction.Slip -> {
                                                                    onSlipClip(clip.id, action.deltaMs)
                                                                    change.consume()
                                                                }
                                                                is TimelineClipGestureAction.Slide -> {
                                                                    onSlideClip(clip.id, action.deltaMs)
                                                                    val snapThreshMs = (12.dp.toPx() / ppm).toLong()
                                                                    val snapTargetsLocal = timelineSlideSnapTargets(
                                                                        tracks = currentTracks,
                                                                        draggedClipId = clip.id,
                                                                        playheadMs = currentPlayheadMs,
                                                                        beatMarkers = beatMarkers,
                                                                        markers = markers,
                                                                        snapToBeat = snapToBeat,
                                                                        snapToMarker = snapToMarker
                                                                    )
                                                                    if (
                                                                        shouldTriggerTimelineSlideSnapHaptic(
                                                                            currentStartMs = currentClip.timelineStartMs,
                                                                            deltaMs = action.deltaMs,
                                                                            snapTargets = snapTargetsLocal,
                                                                            snapThresholdMs = snapThreshMs
                                                                        )
                                                                    ) {
                                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                                    }
                                                                    change.consume()
                                                                }
                                                                null -> Unit
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
                                            // Sort is O(n log n); Timeline recomposes ~30 Hz during
                                            // playback. Key on clip.keyframes (identity-stable
                                            // inside a single undo snapshot) so we only re-sort
                                            // when the actual keyframe list changes.
                                            val volumeKfs = remember(clip.keyframes) {
                                                volumeKeyframesSorted(clip)
                                            }
                                            Canvas(modifier = Modifier.fillMaxSize()) {
                                                if (track.showWaveform) {
                                                    if (waveform != null && waveform.isNotEmpty()) {
                                                        drawTimelineWaveform(waveform, clipColor)
                                                    } else {
                                                        drawTimelineWaveformPlaceholder(clipColor)
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

                                        // Hoisted Brush — see the `clipOverlayBrush` remember at the
                                        // top of Timeline(). Previously allocated per clip per frame.
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(clipOverlayBrush)
                                        )

                                        if (isClipMissing) {
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Canvas(modifier = Modifier.fillMaxSize()) {
                                                    val step = 12f * density.density
                                                    val stroke = 1.2f * density.density
                                                    val lineColor = Mocha.Red.copy(alpha = 0.25f)
                                                    var x = -size.height
                                                    while (x < size.width + size.height) {
                                                        drawLine(
                                                            color = lineColor,
                                                            start = Offset(x, size.height),
                                                            end = Offset(x + size.height, 0f),
                                                            strokeWidth = stroke
                                                        )
                                                        x += step
                                                    }
                                                }
                                                Icon(
                                                    imageVector = Icons.Default.BrokenImage,
                                                    contentDescription = stringResource(R.string.cd_clip_source_missing),
                                                    tint = Mocha.Red.copy(alpha = 0.7f),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }

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
                                                        contentDescription = stringResource(R.string.cd_clip_locked),
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
                                        if (clip.headTransition != null) {
                                            val transWidthPx = clip.headTransition.durationMs * pixelsPerMs
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

                            // Magnetic snap indicator (shows when clip edges align).
                            // Snap-target computation is memoized via `remember` keyed on the
                            // static inputs (track clips, selection, beat/marker state). Without
                            // this, the full flatMap+filter+distinct+let chain reran on every
                            // playhead tick during playback (~30 Hz), allocating 5-7 Lists per
                            // tick for a computation whose inputs hadn't changed.
                            val selectedClipObj = track.clips.find { it.id == selectedClipId }
                            if (selectedClipObj != null) {
                                val snapTargets = remember(
                                    track.clips,
                                    selectedClipId,
                                    playheadMs,
                                    beatMarkers,
                                    markers,
                                    snapToBeat,
                                    snapToMarker
                                ) {
                                    track.clips
                                        .filter { it.id != selectedClipId }
                                        .flatMap { listOf(it.timelineStartMs, it.timelineEndMs) }
                                        .distinct()
                                        .plus(playheadMs)
                                        .plus(0L)
                                        .let { if (snapToBeat) it + beatMarkers else it }
                                        .let { if (snapToMarker) it + markers.map { m -> m.timeMs } else it }
                                }
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

                            // Playhead line — use provider to defer recomposition
                            val deferredPlayheadMs = playheadMsProvider?.invoke() ?: playheadMs
                            val playheadPx = ((deferredPlayheadMs - scrollOffsetMs) * pixelsPerMs)
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

                // Playhead indicator on ruler — use provider to defer recomposition
                val rulerPlayheadMs = playheadMsProvider?.invoke() ?: playheadMs
                val playheadPx = ((rulerPlayheadMs - scrollOffsetMs) * pixelsPerMs)
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

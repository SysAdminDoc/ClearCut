package com.novacut.editor.ui.editor

import android.graphics.Bitmap
import androidx.compose.foundation.*
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.novacut.editor.R
import com.novacut.editor.engine.VideoEngine
import com.novacut.editor.model.*
import com.novacut.editor.ui.theme.Mocha
import kotlin.math.abs
import kotlinx.coroutines.launch

private const val BASE_SCALE = 0.15f // pixels per ms at zoom 1.0

private fun findSnapTarget(positionMs: Long, targets: List<Long>, thresholdMs: Long): Long? {
    return targets.minByOrNull { abs(it - positionMs) }
        ?.takeIf { abs(it - positionMs) <= thresholdMs }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Timeline(
    tracks: List<Track>,
    playheadMs: Long,
    totalDurationMs: Long,
    zoomLevel: Float,
    scrollOffsetMs: Long,
    selectedClipId: String?,
    isTrimMode: Boolean = false,
    waveforms: Map<String, List<Float>> = emptyMap(),
    onClipSelected: (String, String) -> Unit,
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
    engine: VideoEngine,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val rulerHeight = 28.dp
    val pixelsPerMs = zoomLevel * BASE_SCALE
    val coroutineScope = rememberCoroutineScope()
    val textMeasurer = rememberTextMeasurer()

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

    // Load thumbnails for visible clips — evict stale zoom levels to prevent OOM
    LaunchedEffect(tracks, quantizedZoom) {
        thumbnails.keys.filter { !it.endsWith("_$quantizedZoom") }
            .forEach { thumbnails.remove(it) }
        tracks.forEach { track ->
            track.clips.forEach { clip ->
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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Mocha.Mantle)
    ) {
        // Trim mode indicator
        if (isTrimMode && selectedClipId != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Mocha.Peach.copy(alpha = 0.15f))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "TRIM MODE — Drag clip edges to adjust",
                    color = Mocha.Peach,
                    fontSize = 11.sp
                )
            }
        }

        // Zoom controls + Add Marker
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Collapse/Expand all tracks
            IconButton(
                onClick = {
                    if (tracks.any { !it.isCollapsed }) onCollapseAllTracks()
                    else onExpandAllTracks()
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    if (tracks.any { !it.isCollapsed }) Icons.Default.UnfoldLess else Icons.Default.UnfoldMore,
                    contentDescription = if (tracks.any { !it.isCollapsed }) "Collapse all" else "Expand all",
                    tint = Mocha.Subtext0,
                    modifier = Modifier.size(18.dp)
                )
            }
            // Add Marker button
            IconButton(
                onClick = onAddMarker,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Default.Flag, "Add Marker", tint = Mocha.Blue, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                "${(zoomLevel * 100).toInt()}%",
                color = Mocha.Subtext0,
                fontSize = 10.sp,
                modifier = Modifier.padding(end = 4.dp)
            )
            IconButton(
                onClick = { onZoomChanged((zoomLevel * 0.75f).coerceAtLeast(0.1f)) },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Default.ZoomOut, "Zoom Out", tint = Mocha.Subtext0, modifier = Modifier.size(18.dp))
            }
            IconButton(
                onClick = { onZoomChanged(1f) },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Default.FitScreen, "Fit", tint = Mocha.Subtext0, modifier = Modifier.size(18.dp))
            }
            IconButton(
                onClick = { onZoomChanged((zoomLevel * 1.33f).coerceAtMost(10f)) },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Default.ZoomIn, "Zoom In", tint = Mocha.Subtext0, modifier = Modifier.size(18.dp))
            }
        }

        // Track headers + timeline content
        Row(modifier = Modifier.fillMaxWidth()) {
            // Track headers
            Column(
                modifier = Modifier
                    .width(44.dp)
                    .background(Mocha.Crust)
            ) {
                // Ruler spacer
                Spacer(modifier = Modifier.height(rulerHeight))

                for (track in tracks) {
                    key(track.id) {
                        val currentTrackHeight = if (track.isCollapsed) 24.dp else track.trackHeight.dp
                        val trackColor = when (track.type) {
                            TrackType.VIDEO -> Mocha.Blue
                            TrackType.AUDIO -> Mocha.Green
                            TrackType.OVERLAY -> Mocha.Peach
                            TrackType.TEXT -> Mocha.Mauve
                            TrackType.ADJUSTMENT -> Mocha.Yellow
                        }
                        Column(
                            modifier = Modifier
                                .height(currentTrackHeight)
                                .fillMaxWidth()
                                .background(Mocha.Crust)
                                .border(0.5.dp, Mocha.Surface0),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                if (track.isCollapsed) Icons.Default.ChevronRight else Icons.Default.ExpandMore,
                                contentDescription = if (track.isCollapsed) "Expand" else "Collapse",
                                tint = Mocha.Subtext0,
                                modifier = Modifier.size(11.dp).clickable { onToggleTrackCollapsed(track.id) }
                            )
                            Icon(
                                imageVector = when (track.type) {
                                    TrackType.VIDEO -> Icons.Default.Videocam
                                    TrackType.AUDIO -> Icons.Default.MusicNote
                                    TrackType.OVERLAY -> Icons.Default.Layers
                                    TrackType.TEXT -> Icons.Default.Title
                                    TrackType.ADJUSTMENT -> Icons.Default.Tune
                                },
                                contentDescription = track.type.name,
                                tint = if (track.isVisible) trackColor else Mocha.Surface2,
                                modifier = Modifier
                                    .size(14.dp)
                                    .combinedClickable(
                                        onClick = {},
                                        onLongClick = {
                                            val nextHeight = when (track.trackHeight) {
                                                48 -> 64
                                                64 -> 80
                                                80 -> 96
                                                else -> 48
                                            }
                                            onSetTrackHeight(track.id, nextHeight)
                                        }
                                    )
                            )
                            Row(
                                modifier = Modifier.padding(top = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(1.dp)
                            ) {
                                Icon(
                                    if (track.isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = if (track.isMuted) "Unmute track" else "Mute track",
                                    tint = if (track.isMuted) Mocha.Red.copy(alpha = 0.7f) else Mocha.Surface2,
                                    modifier = Modifier.size(11.dp).clickable { onToggleTrackMute(track.id) }
                                )
                                Icon(
                                    if (track.isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (track.isVisible) "Hide track" else "Show track",
                                    tint = if (!track.isVisible) Mocha.Red.copy(alpha = 0.7f) else Mocha.Surface2,
                                    modifier = Modifier.size(11.dp).clickable { onToggleTrackVisible(track.id) }
                                )
                                Icon(
                                    if (track.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                    contentDescription = if (track.isLocked) "Unlock track" else "Lock track",
                                    tint = if (track.isLocked) Mocha.Peach.copy(alpha = 0.7f) else Mocha.Surface2,
                                    modifier = Modifier.size(11.dp).clickable { onToggleTrackLock(track.id) }
                                )
                                if (track.type == TrackType.AUDIO || track.type == TrackType.VIDEO) {
                                    Icon(
                                        Icons.Default.GraphicEq,
                                        contentDescription = stringResource(R.string.cd_toggle_waveform),
                                        tint = if (track.showWaveform) Mocha.Teal else Mocha.Surface2,
                                        modifier = Modifier.size(11.dp).clickable { onToggleTrackWaveform(track.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Scrollable timeline area
            var timelineWidthPx by remember { mutableFloatStateOf(0f) }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clipToBounds()
                    .onSizeChanged {
                        timelineWidthPx = it.width.toFloat()
                        onTimelineWidthChanged(timelineWidthPx)
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            val oldPpm = currentZoomLevel * BASE_SCALE
                            val newZoom = (currentZoomLevel * zoom).coerceIn(0.1f, 10f)
                            val newPpm = newZoom * BASE_SCALE
                            // Adjust scroll to keep the pinch center point stable
                            val centerMs = currentScrollOffsetMs + (centroid.x / oldPpm).toLong()
                            val newScroll = centerMs - (centroid.x / newPpm).toLong()
                            onZoomChanged(newZoom)
                            val panMs = (pan.x / newPpm).toLong()
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
                                .background(Mocha.Crust)
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
                    val currentTrackHeight = if (track.isCollapsed) 24.dp else track.trackHeight.dp
                    key(track.id) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(currentTrackHeight)
                                .background(Mocha.Base)
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
                                                tappedMs in it.timelineStartMs..it.timelineEndMs
                                            }
                                            if (clip != null) {
                                                onClipSelected(clip.id, track.id)
                                            }
                                            onPlayheadMoved(tappedMs.coerceIn(0L, currentTotalDurationMs))
                                        },
                                        onLongPress = { offset ->
                                            val ppm = currentZoomLevel * BASE_SCALE
                                            val tappedMs = currentScrollOffsetMs + (offset.x / ppm).toLong()
                                            val trackClips = currentTracks.firstOrNull { it.id == track.id }?.clips ?: return@detectTapGestures
                                            val clip = trackClips.firstOrNull {
                                                tappedMs in it.timelineStartMs..it.timelineEndMs
                                            }
                                            if (clip != null) {
                                                onClipLongPress(clip.id)
                                            }
                                        }
                                    )
                                }
                        ) {
                            if (track.isCollapsed) {
                                // Show minimal collapsed indicator
                                val trackColor = when (track.type) {
                                    TrackType.VIDEO -> Mocha.Blue
                                    TrackType.AUDIO -> Mocha.Green
                                    TrackType.OVERLAY -> Mocha.Peach
                                    TrackType.TEXT -> Mocha.Mauve
                                    TrackType.ADJUSTMENT -> Mocha.Yellow
                                }
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
                            // Draw clips
                            track.clips.forEach { clip ->
                                val clipStartPx = ((clip.timelineStartMs - scrollOffsetMs) * pixelsPerMs)
                                val clipWidthPx = (clip.durationMs * pixelsPerMs)

                                if (clipStartPx + clipWidthPx > 0 && clipStartPx < timelineWidthPx) {
                                    val isSelected = clip.id == selectedClipId
                                    val isMultiSelected = clip.id in selectedClipIds
                                    val clipColor = when (track.type) {
                                        TrackType.VIDEO -> Mocha.Blue
                                        TrackType.AUDIO -> Mocha.Green
                                        TrackType.OVERLAY -> Mocha.Peach
                                        TrackType.TEXT -> Mocha.Mauve
                                        TrackType.ADJUSTMENT -> Mocha.Yellow
                                    }

                                    Box(
                                        modifier = Modifier
                                            .offset(x = with(density) { clipStartPx.toDp() })
                                            .width(with(density) { clipWidthPx.toDp() })
                                            .fillMaxHeight()
                                            .padding(vertical = 2.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(
                                                when {
                                                    isSelected -> clipColor.copy(alpha = 0.5f)
                                                    isMultiSelected -> Mocha.Peach.copy(alpha = 0.4f)
                                                    else -> clipColor.copy(alpha = 0.3f)
                                                }
                                            )
                                            .then(
                                                if (isSelected) Modifier.border(
                                                    2.dp,
                                                    clipColor,
                                                    RoundedCornerShape(6.dp)
                                                ) else Modifier
                                            )
                                            .then(
                                                if (isSelected) Modifier.pointerInput(clip.id) {
                                                    val trimHandleWidthPx = 12.dp.toPx()
                                                    detectDragGestures(
                                                        onDragStart = { },
                                                        onDragEnd = {},
                                                        onDragCancel = {},
                                                        onDrag = { change, dragAmount ->
                                                            val ppm = currentZoomLevel * BASE_SCALE
                                                            if (ppm < 0.001f) return@detectDragGestures
                                                            val currentClip = currentTracks.flatMap { it.clips }.firstOrNull { it.id == clip.id } ?: return@detectDragGestures
                                                            val clipWidthPxLocal = currentClip.durationMs * ppm
                                                            val dragStartX = change.position.x - dragAmount.x
                                                            val isOnLeftHandle = dragStartX < trimHandleWidthPx
                                                            val isOnRightHandle = dragStartX > (clipWidthPxLocal - trimHandleWidthPx)

                                                            if (isOnLeftHandle || isOnRightHandle) return@detectDragGestures

                                                            val deltaMs = (dragAmount.x / ppm).toLong()
                                                            if (currentIsTrimMode) {
                                                                onSlipClip(clip.id, deltaMs)
                                                            } else {
                                                                onSlideClip(clip.id, deltaMs)
                                                                val snapThreshMs = (12.dp.toPx() / ppm).toLong()
                                                                val snapTargetsLocal = currentTracks.flatMap { t -> t.clips.filter { it.id != clip.id }.flatMap { listOf(it.timelineStartMs, it.timelineEndMs) } }
                                                                    .plus(currentPlayheadMs).plus(0L)
                                                                    .let { if (snapToBeat) it + beatMarkers else it }
                                                                    .let { if (snapToMarker) it + markers.map { m -> m.timeMs } else it }
                                                                if (findSnapTarget(currentClip.timelineStartMs + deltaMs, snapTargetsLocal, snapThreshMs) != null) {
                                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                                }
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
                                            Canvas(modifier = Modifier.fillMaxSize()) {
                                                if (track.showWaveform) {
                                                    if (waveform != null && waveform.isNotEmpty()) {
                                                        drawWaveform(waveform, clipColor)
                                                    } else {
                                                        drawWaveformPlaceholder(clipColor)
                                                    }
                                                }

                                                // Volume envelope (keyframe-based volume line)
                                                val volumeKfs = clip.keyframes.filter {
                                                    it.property == KeyframeProperty.VOLUME
                                                }.sortedBy { it.timeOffsetMs }
                                                if (volumeKfs.size >= 2) {
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
                                                    // Draw keyframe dots on envelope
                                                    volumeKfs.forEach { kf ->
                                                        val x = (kf.timeOffsetMs.toFloat() / clip.durationMs) * size.width
                                                        val y = size.height * (1f - (kf.value / 2f).coerceIn(0f, 1f))
                                                        drawCircle(Color(0xFFF9E2AF), 3f, Offset(x, y))
                                                    }
                                                }
                                            }
                                        }

                                        // Trim handles
                                        if (isSelected) {
                                            // Left trim handle
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.CenterStart)
                                                    .width(12.dp)
                                                    .fillMaxHeight()
                                                    .background(
                                                        clipColor,
                                                        RoundedCornerShape(
                                                            topStart = 6.dp,
                                                            bottomStart = 6.dp
                                                        )
                                                    )
                                                    .pointerInput(clip.id) {
                                                        detectHorizontalDragGestures(
                                                            onDragStart = {
                                                                onTrimDragStarted()
                                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            },
                                                            onDragEnd = { onTrimDragEnded() },
                                                            onDragCancel = { onTrimDragEnded() },
                                                            onHorizontalDrag = { _, dragAmount ->
                                                                val ppm = currentZoomLevel * BASE_SCALE
                                                                if (ppm < 0.001f) return@detectHorizontalDragGestures
                                                                val currentClip = currentTracks.flatMap { it.clips }.firstOrNull { it.id == clip.id } ?: return@detectHorizontalDragGestures
                                                                val deltaMs = (dragAmount / ppm).toLong()
                                                                val newStart = (currentClip.trimStartMs + deltaMs)
                                                                    .coerceAtLeast(0L)
                                                                    .coerceAtMost(currentClip.trimEndMs - 100L)
                                                                onTrimChanged(clip.id, newStart, null)
                                                            }
                                                        )
                                                    }
                                            )
                                            // Right trim handle
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.CenterEnd)
                                                    .width(12.dp)
                                                    .fillMaxHeight()
                                                    .background(
                                                        clipColor,
                                                        RoundedCornerShape(
                                                            topEnd = 6.dp,
                                                            bottomEnd = 6.dp
                                                        )
                                                    )
                                                    .pointerInput(clip.id) {
                                                        detectHorizontalDragGestures(
                                                            onDragStart = {
                                                                onTrimDragStarted()
                                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            },
                                                            onDragEnd = { onTrimDragEnded() },
                                                            onDragCancel = { onTrimDragEnded() },
                                                            onHorizontalDrag = { _, dragAmount ->
                                                                val ppm = currentZoomLevel * BASE_SCALE
                                                                if (ppm < 0.001f) return@detectHorizontalDragGestures
                                                                val currentClip = currentTracks.flatMap { it.clips }.firstOrNull { it.id == clip.id } ?: return@detectHorizontalDragGestures
                                                                val deltaMs = (dragAmount / ppm).toLong()
                                                                val newEnd = (currentClip.trimEndMs + deltaMs)
                                                                    .coerceIn(currentClip.trimStartMs + 100L, currentClip.sourceDurationMs)
                                                                onTrimChanged(clip.id, null, newEnd)
                                                            }
                                                        )
                                                    }
                                            )
                                        }

                                        // Transition indicator
                                        if (clip.transition != null) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopStart)
                                                    .padding(2.dp)
                                                    .size(12.dp)
                                                    .background(
                                                        Mocha.Yellow,
                                                        RoundedCornerShape(2.dp)
                                                    )
                                            )
                                        }

                                        // Clip filename label
                                        if (clipWidthPx > 60) {
                                            val fileName = clip.sourceUri.lastPathSegment
                                                ?.substringAfterLast('/')
                                                ?.substringBeforeLast('.') ?: ""
                                            if (fileName.isNotEmpty()) {
                                                Text(
                                                    text = fileName,
                                                    color = Mocha.Text.copy(alpha = 0.7f),
                                                    fontSize = 8.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier
                                                        .align(Alignment.BottomStart)
                                                        .padding(start = 4.dp, bottom = 2.dp)
                                                        .background(
                                                            Mocha.Crust.copy(alpha = 0.6f),
                                                            RoundedCornerShape(2.dp)
                                                        )
                                                        .padding(horizontal = 2.dp)
                                                )
                                            }
                                        }

                                        // Effects count badge
                                        if (clip.effects.isNotEmpty()) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(2.dp)
                                                    .background(
                                                        Mocha.Mauve.copy(alpha = 0.8f),
                                                        RoundedCornerShape(4.dp)
                                                    )
                                                    .padding(horizontal = 3.dp, vertical = 1.dp)
                                            ) {
                                                Text(
                                                    "FX${clip.effects.size}",
                                                    color = Mocha.Crust,
                                                    fontSize = 7.sp,
                                                    lineHeight = 8.sp
                                                )
                                            }
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
                                val snapThresholdMs = (snapThresholdPx / pixelsPerMs).toLong()

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
                                        color = Mocha.Red,
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
                        drawPath(path, Mocha.Red)
                    }
                }
            }
        }
    }
    }
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

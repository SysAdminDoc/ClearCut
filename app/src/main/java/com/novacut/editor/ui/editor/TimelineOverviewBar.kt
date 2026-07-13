package com.novacut.editor.ui.editor

import com.novacut.editor.ui.theme.ClearCutAccents
import com.novacut.editor.ui.theme.LocalClearCutColors
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.novacut.editor.R
import com.novacut.editor.model.Track

/**
 * Full-duration mini-strip shown below the tracks area. Paints one rectangle per
 * clip scaled to the whole project timeline, plus the highlighted viewport.
 */
@Composable
internal fun TimelineOverviewBar(
    totalDurationMs: Long,
    scrollOffsetMs: Long,
    visibleDurationMs: Long,
    playheadMs: Long,
    tracks: List<Track>,
    contentPadding: Dp,
    onScrollTo: (Long) -> Unit
) {
    val semanticColors = LocalClearCutColors.current
    val overviewHeight = 22.dp
    var widthPx by remember { mutableFloatStateOf(0f) }
    val overviewContentDescription = stringResource(R.string.cd_timeline_overview)

    val currentTotalDurationMs by rememberUpdatedState(totalDurationMs)
    val currentVisibleDurationMs by rememberUpdatedState(visibleDurationMs)
    val currentScrollOffsetMs by rememberUpdatedState(scrollOffsetMs)

    fun tapXToScrollOffset(xPx: Float): Long {
        return timelineOverviewScrollOffsetForTap(
            xPx = xPx,
            widthPx = widthPx,
            totalDurationMs = currentTotalDurationMs,
            visibleDurationMs = currentVisibleDurationMs,
            currentScrollOffsetMs = currentScrollOffsetMs
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = contentPadding, vertical = 6.dp)
            .height(overviewHeight)
            .clip(RoundedCornerShape(10.dp))
            .background(semanticColors.onAccent.copy(alpha = 0.78f))
            .border(1.dp, semanticColors.cardStroke.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
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

            val vStartF = (scrollOffsetMs.toFloat() / totalF).coerceIn(0f, 1f)
            val vWidthF = (currentVisibleDurationMs.toFloat() / totalF).coerceIn(0.01f, 1f)
            drawRect(
                color = ClearCutAccents.Sky.copy(alpha = 0.25f),
                topLeft = Offset(vStartF * size.width, 0f),
                size = Size(vWidthF * size.width, size.height)
            )
            drawRect(
                color = ClearCutAccents.Sky,
                topLeft = Offset(vStartF * size.width, 0f),
                size = Size(vWidthF * size.width, size.height),
                style = Stroke(width = 1.6f)
            )

            val playheadF = (playheadMs.toFloat() / totalF).coerceIn(0f, 1f)
            drawLine(
                color = ClearCutAccents.Rosewater,
                start = Offset(playheadF * size.width, 0f),
                end = Offset(playheadF * size.width, size.height),
                strokeWidth = 1.4f
            )
        }
    }
}

internal fun timelineOverviewScrollOffsetForTap(
    xPx: Float,
    widthPx: Float,
    totalDurationMs: Long,
    visibleDurationMs: Long,
    currentScrollOffsetMs: Long
): Long {
    if (widthPx <= 0f || totalDurationMs <= 0L) return currentScrollOffsetMs
    val fraction = (xPx / widthPx).coerceIn(0f, 1f)
    val targetMs = (fraction * totalDurationMs).toLong()
    return (targetMs - visibleDurationMs / 2).coerceAtLeast(0L)
}

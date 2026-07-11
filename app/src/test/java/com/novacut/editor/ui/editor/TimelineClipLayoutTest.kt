package com.novacut.editor.ui.editor

import android.net.FakeUri
import com.novacut.editor.model.Clip
import com.novacut.editor.model.TimelineMarker
import com.novacut.editor.model.Track
import com.novacut.editor.model.TrackType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineClipLayoutTest {

    @Test
    fun `clip layout maps timeline position into visible pixel bounds`() {
        val clip = clip(
            id = "clip",
            timelineStartMs = 2_000L,
            trimEndMs = 1_000L,
            sourceDurationMs = 1_000L
        )

        val layout = timelineClipLayout(
            clip = clip,
            scrollOffsetMs = 1_500L,
            pixelsPerMs = 0.2f
        )

        assertEquals(100f, layout.startPx, 0.001f)
        assertEquals(200f, layout.widthPx, 0.001f)
        assertTrue(layout.isVisibleIn(180f))
        assertFalse(layout.isVisibleIn(80f))
    }

    @Test
    fun `clip content visibility keeps badge thresholds stable`() {
        val compactVisibility = timelineClipContentVisibility(140f)
        val wideVisibility = timelineClipContentVisibility(170f)

        assertTrue(compactVisibility.showTrackBadge)
        assertFalse(compactVisibility.showSpeedBadge)
        assertFalse(compactVisibility.showEffectsBadge)
        assertTrue(compactVisibility.showClipName)
        assertFalse(compactVisibility.showKeyframeBadge)
        assertTrue(compactVisibility.compactBadges)

        assertTrue(wideVisibility.showTrackBadge)
        assertTrue(wideVisibility.showSpeedBadge)
        assertTrue(wideVisibility.showEffectsBadge)
        assertTrue(wideVisibility.showClipName)
        assertTrue(wideVisibility.showKeyframeBadge)
        assertFalse(wideVisibility.compactBadges)
    }

    @Test
    fun `gesture zone resolves trim handles before body modes`() {
        assertEquals(
            TimelineClipGestureZone.TRIM_LEFT,
            resolveTimelineClipGestureZone(
                touchXPx = 10f,
                clipWidthPx = 200f,
                trimHandleWidthPx = 28f,
                isTrimMode = true
            )
        )
        assertEquals(
            TimelineClipGestureZone.TRIM_RIGHT,
            resolveTimelineClipGestureZone(
                touchXPx = 180f,
                clipWidthPx = 200f,
                trimHandleWidthPx = 28f,
                isTrimMode = true
            )
        )
        assertEquals(
            TimelineClipGestureZone.SLIP,
            resolveTimelineClipGestureZone(
                touchXPx = 100f,
                clipWidthPx = 200f,
                trimHandleWidthPx = 28f,
                isTrimMode = true
            )
        )
        assertEquals(
            TimelineClipGestureZone.SLIDE,
            resolveTimelineClipGestureZone(
                touchXPx = 100f,
                clipWidthPx = 200f,
                trimHandleWidthPx = 28f,
                isTrimMode = false
            )
        )
        assertEquals(
            TimelineClipGestureZone.NONE,
            resolveTimelineClipGestureZone(
                touchXPx = Float.NaN,
                clipWidthPx = 200f,
                trimHandleWidthPx = 28f,
                isTrimMode = false
            )
        )
    }

    @Test
    fun `gesture actions clamp trim edits and keep body deltas in timeline milliseconds`() {
        val clip = clip(
            id = "clip",
            timelineStartMs = 0L,
            trimStartMs = 200L,
            trimEndMs = 800L,
            sourceDurationMs = 1_000L
        )

        assertEquals(
            TimelineClipGestureAction.TrimLeft(trimStartMs = 0L),
            resolveTimelineClipGestureAction(
                zone = TimelineClipGestureZone.TRIM_LEFT,
                clip = clip,
                deltaXPx = -1_000f,
                pixelsPerMs = 1f
            )
        )
        assertEquals(
            TimelineClipGestureAction.TrimLeft(trimStartMs = 700L),
            resolveTimelineClipGestureAction(
                zone = TimelineClipGestureZone.TRIM_LEFT,
                clip = clip,
                deltaXPx = 1_000f,
                pixelsPerMs = 1f
            )
        )
        assertEquals(
            TimelineClipGestureAction.TrimRight(trimEndMs = 300L),
            resolveTimelineClipGestureAction(
                zone = TimelineClipGestureZone.TRIM_RIGHT,
                clip = clip,
                deltaXPx = -1_000f,
                pixelsPerMs = 1f
            )
        )
        assertEquals(
            TimelineClipGestureAction.TrimRight(trimEndMs = 1_000L),
            resolveTimelineClipGestureAction(
                zone = TimelineClipGestureZone.TRIM_RIGHT,
                clip = clip,
                deltaXPx = 1_000f,
                pixelsPerMs = 1f
            )
        )
        assertEquals(
            TimelineClipGestureAction.Slip(deltaMs = 100L),
            resolveTimelineClipGestureAction(
                zone = TimelineClipGestureZone.SLIP,
                clip = clip,
                deltaXPx = 50f,
                pixelsPerMs = 0.5f
            )
        )
        assertEquals(
            TimelineClipGestureAction.Slide(deltaMs = -100L),
            resolveTimelineClipGestureAction(
                zone = TimelineClipGestureZone.SLIDE,
                clip = clip,
                deltaXPx = -50f,
                pixelsPerMs = 0.5f
            )
        )
    }

    @Test
    fun `gesture action ignores invalid ranges and inactive zones`() {
        val shortClip = clip(
            id = "short",
            timelineStartMs = 0L,
            trimEndMs = 80L,
            sourceDurationMs = 80L
        )

        assertNull(
            resolveTimelineClipGestureAction(
                zone = TimelineClipGestureZone.TRIM_LEFT,
                clip = shortClip,
                deltaXPx = 10f,
                pixelsPerMs = 1f
            )
        )
        assertNull(
            resolveTimelineClipGestureAction(
                zone = TimelineClipGestureZone.NONE,
                clip = shortClip,
                deltaXPx = 10f,
                pixelsPerMs = 1f
            )
        )
        assertNull(
            resolveTimelineClipGestureAction(
                zone = TimelineClipGestureZone.SLIDE,
                clip = shortClip,
                deltaXPx = 10f,
                pixelsPerMs = 0.0001f
            )
        )
    }

    @Test
    fun `slide snap targets exclude dragged clip and honor optional beat and marker toggles`() {
        val target = clip(
            id = "target",
            timelineStartMs = 500L,
            trimEndMs = 500L,
            sourceDurationMs = 500L
        )
        val first = clip(
            id = "first",
            timelineStartMs = 0L,
            trimEndMs = 500L,
            sourceDurationMs = 500L
        )
        val other = clip(
            id = "other",
            timelineStartMs = 2_000L,
            trimEndMs = 300L,
            sourceDurationMs = 300L
        )
        val tracks = listOf(
            Track(type = TrackType.VIDEO, index = 0, clips = listOf(first, target)),
            Track(type = TrackType.AUDIO, index = 1, clips = listOf(other))
        )

        assertEquals(
            listOf(0L, 500L, 2_000L, 2_300L, 750L, 0L, 250L),
            timelineSlideSnapTargets(
                tracks = tracks,
                draggedClipId = "target",
                playheadMs = 750L,
                beatMarkers = listOf(250L),
                markers = listOf(TimelineMarker(id = "marker", timeMs = 1_500L)),
                snapToBeat = true,
                snapToMarker = false
            )
        )
        assertEquals(
            listOf(0L, 500L, 2_000L, 2_300L, 750L, 0L, 1_500L),
            timelineSlideSnapTargets(
                tracks = tracks,
                draggedClipId = "target",
                playheadMs = 750L,
                beatMarkers = listOf(250L),
                markers = listOf(TimelineMarker(id = "marker", timeMs = 1_500L)),
                snapToBeat = false,
                snapToMarker = true
            )
        )
    }

    @Test
    fun `slide snap haptic policy only triggers inside threshold`() {
        val targets = listOf(0L, 1_500L, 2_000L)

        assertTrue(
            shouldTriggerTimelineSlideSnapHaptic(
                currentStartMs = 500L,
                deltaMs = 995L,
                snapTargets = targets,
                snapThresholdMs = 10L
            )
        )
        assertFalse(
            shouldTriggerTimelineSlideSnapHaptic(
                currentStartMs = 500L,
                deltaMs = 989L,
                snapTargets = targets,
                snapThresholdMs = 10L
            )
        )
    }

    @Test
    fun `slide snap corrects both leading and trailing clip edges`() {
        val leading = resolveTimelineSlideSnap(
            currentStartMs = 1_000L,
            clipDurationMs = 500L,
            deltaMs = 495L,
            snapTargets = listOf(1_500L),
            snapThresholdMs = 10L
        )
        val trailing = resolveTimelineSlideSnap(
            currentStartMs = 1_000L,
            clipDurationMs = 500L,
            deltaMs = 490L,
            snapTargets = listOf(2_000L),
            snapThresholdMs = 15L
        )

        assertEquals(500L, leading.deltaMs)
        assertEquals(1_500L, leading.targetMs)
        assertEquals(500L, trailing.deltaMs)
        assertEquals(2_000L, trailing.targetMs)
    }

    private fun clip(
        id: String,
        timelineStartMs: Long,
        trimStartMs: Long = 0L,
        trimEndMs: Long,
        sourceDurationMs: Long
    ): Clip {
        return Clip(
            id = id,
            sourceUri = FakeUri,
            sourceDurationMs = sourceDurationMs,
            timelineStartMs = timelineStartMs,
            trimStartMs = trimStartMs,
            trimEndMs = trimEndMs
        )
    }
}

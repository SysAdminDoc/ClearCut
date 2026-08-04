package com.novacut.editor.engine

import android.net.Uri
import android.net.FakeUri
import com.novacut.editor.model.Caption
import com.novacut.editor.model.CaptionWord
import com.novacut.editor.model.ChapterMarker
import com.novacut.editor.model.Clip
import com.novacut.editor.model.Effect
import com.novacut.editor.model.EffectKeyframe
import com.novacut.editor.model.EffectType
import com.novacut.editor.model.GlobalTransition
import com.novacut.editor.model.GlobalTransitionType
import com.novacut.editor.model.ImageOverlay
import com.novacut.editor.model.Keyframe
import com.novacut.editor.model.KeyframeProperty
import com.novacut.editor.model.MotionTrackPoint
import com.novacut.editor.model.MotionTrackingData
import com.novacut.editor.model.ResolvedTimelineExportRange
import com.novacut.editor.model.TimelineExportRange
import com.novacut.editor.model.TimelineTimebase
import com.novacut.editor.model.TextOverlay
import com.novacut.editor.model.Track
import com.novacut.editor.model.TrackType
import com.novacut.editor.model.TrackedObject
import com.novacut.editor.model.TrackedObjectKeyframe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineRangeExportEngineTest {
    private val sourceUri: Uri = FakeUri
    private val range = TimelineExportRange(60L, 210L)
        .resolve(TimelineTimebase(30), 8_000L)!!

    @Test
    fun slicesTracksAndRebasesClipRelativeMediaState() {
        val clip = Clip(
            id = "clip-1",
            sourceUri = sourceUri,
            sourceDurationMs = 12_000L,
            timelineStartMs = 1_000L,
            trimStartMs = 0L,
            trimEndMs = 12_000L,
            fadeInMs = 1_500L,
            fadeOutMs = 1_500L,
            keyframes = listOf(
                Keyframe(0L, KeyframeProperty.OPACITY, 0f),
                Keyframe(4_000L, KeyframeProperty.OPACITY, 0.5f),
                Keyframe(12_000L, KeyframeProperty.OPACITY, 1f),
            ),
            effects = listOf(
                Effect(
                    type = EffectType.BRIGHTNESS,
                    params = EffectType.defaultParams(EffectType.BRIGHTNESS),
                    keyframes = listOf(
                        EffectKeyframe(0L, "amount", 0f),
                        EffectKeyframe(12_000L, "amount", 1f),
                    ),
                )
            ),
            motionTrackingData = MotionTrackingData(
                trackPoints = listOf(
                    MotionTrackPoint(0L, 0f, 0f),
                    MotionTrackPoint(12_000L, 1f, 1f),
                ),
                isActive = true,
            ),
            captions = listOf(
                Caption(
                    text = "hello",
                    startTimeMs = 1_500L,
                    endTimeMs = 4_500L,
                    words = listOf(CaptionWord("hello", 2_000L, 3_000L)),
                )
            ),
        )
        val source = Track(type = TrackType.VIDEO, index = 0, clips = listOf(clip))

        val result = TimelineRangeExportEngine.slice(listOf(source), range)
        val sliced = result.tracks.single().clips.single()

        assertEquals(0L, sliced.timelineStartMs)
        assertEquals(1_000L, sliced.trimStartMs)
        assertEquals(6_000L, sliced.trimEndMs)
        assertEquals(0L, sliced.keyframes.first().timeOffsetMs)
        assertEquals(5_000L, sliced.keyframes.last().timeOffsetMs)
        assertEquals(0L, sliced.effects.single().keyframes.first().timeOffsetMs)
        assertEquals(5_000L, sliced.effects.single().keyframes.last().timeOffsetMs)
        assertEquals(500L, sliced.fadeInMs)
        assertEquals(0L, sliced.fadeOutMs)
        assertEquals(500L, sliced.captions.single().startTimeMs)
        assertEquals(3_500L, sliced.captions.single().endTimeMs)
        assertEquals(1_000L, sliced.captions.single().words.single().startTimeMs)
        assertEquals(0L, sliced.motionTrackingData?.trackPoints?.first()?.timeOffsetMs)
        assertEquals(5_000L, sliced.motionTrackingData?.trackPoints?.last()?.timeOffsetMs)
        assertEquals(0L, clip.trimStartMs)
        assertNotEquals(sliced, clip)
    }

    @Test
    fun slicesOverlaysTransitionsMarkersAndTrackedObjectsWithoutMutatingInputs() {
        val clip = Clip(
            id = "clip-1",
            sourceUri = sourceUri,
            sourceDurationMs = 12_000L,
            timelineStartMs = 1_000L,
            trimEndMs = 12_000L,
        )
        val tracked = TrackedObject(
            label = "Subject",
            sourceClipId = clip.id,
            keyframes = listOf(
                TrackedObjectKeyframe(0L, 0.1f, 0.2f, 0.2f, 0.2f),
                TrackedObjectKeyframe(6_000L, 0.5f, 0.5f, 0.3f, 0.3f),
                TrackedObjectKeyframe(12_000L, 0.8f, 0.8f, 0.4f, 0.4f),
            ),
        )
        val result = TimelineRangeExportEngine.slice(
            tracks = listOf(Track(type = TrackType.VIDEO, index = 0, clips = listOf(clip))),
            range = range,
            textOverlays = listOf(
                TextOverlay(text = "in", startTimeMs = 1_000L, endTimeMs = 4_000L),
                TextOverlay(text = "out", startTimeMs = 7_500L, endTimeMs = 9_000L),
            ),
            imageOverlays = listOf(
                ImageOverlay(sourceUri = sourceUri, startTimeMs = 2_000L, endTimeMs = 5_000L),
            ),
            trackedObjects = listOf(tracked),
            globalTransitions = listOf(
                GlobalTransition(
                    type = GlobalTransitionType.FADE_TO_BLACK,
                    timelineAnchorMs = 2_000L,
                    durationMs = 5_000L,
                )
            ),
            chapters = listOf(
                ChapterMarker(1_000L, "before"),
                ChapterMarker(4_000L, "inside"),
                ChapterMarker(8_000L, "after"),
            ),
        )

        assertEquals(1, result.textOverlays.size)
        assertEquals(0L, result.textOverlays.single().startTimeMs)
        assertEquals(2_000L, result.textOverlays.single().endTimeMs)
        assertEquals(1, result.imageOverlays.size)
        assertEquals(0L, result.imageOverlays.single().startTimeMs)
        assertEquals(3_000L, result.imageOverlays.single().endTimeMs)
        assertEquals(1, result.globalTransitions.size)
        assertEquals(0L, result.globalTransitions.single().timelineAnchorMs)
        assertEquals(5_000L, result.globalTransitions.single().durationMs)
        assertEquals(listOf("inside"), result.chapters.map { it.title })
        assertEquals(1_000L, result.trackedObjects.single().keyframes.first().clipTimeMs)
        assertEquals(6_000L, result.trackedObjects.single().keyframes.last().clipTimeMs)
        assertEquals(0L, clip.trimStartMs)
        assertTrue(tracked.keyframes.first().clipTimeMs == 0L)
        assertFalse(result.tracks.single().clips.isEmpty())
    }
}

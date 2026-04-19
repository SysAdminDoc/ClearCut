package com.novacut.editor.ui.editor

import android.net.FakeUri
import com.novacut.editor.model.Clip
import com.novacut.editor.model.SpeedCurve
import com.novacut.editor.model.Track
import com.novacut.editor.model.TrackType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineEditingTest {

    @Test
    fun `leading trim moves the clip start on the timeline`() {
        val clip = clip(
            id = "clip",
            timelineStartMs = 0L,
            trimStartMs = 0L,
            trimEndMs = 1_000L,
            sourceDurationMs = 1_000L
        )
        val track = Track(type = TrackType.VIDEO, index = 0, clips = listOf(clip))

        val trimmedTrack = trimClipOnTrack(
            track = track,
            clipId = clip.id,
            requestedTrimStartMs = 200L
        )
        val trimmedClip = trimmedTrack.clips.single()

        assertEquals(200L, trimmedClip.timelineStartMs)
        assertEquals(200L, trimmedClip.trimStartMs)
        assertEquals(1_000L, trimmedClip.timelineEndMs)
    }

    @Test
    fun `leading trim respects the previous clip boundary`() {
        val previous = clip(
            id = "prev",
            timelineStartMs = 0L,
            trimStartMs = 0L,
            trimEndMs = 400L,
            sourceDurationMs = 400L
        )
        val target = clip(
            id = "target",
            timelineStartMs = 400L,
            trimStartMs = 200L,
            trimEndMs = 800L,
            sourceDurationMs = 1_000L
        )
        val track = Track(type = TrackType.VIDEO, index = 0, clips = listOf(previous, target))

        val trimmedTrack = trimClipOnTrack(
            track = track,
            clipId = target.id,
            requestedTrimStartMs = 0L
        )
        val trimmedClip = trimmedTrack.clips.last()

        assertEquals(400L, trimmedClip.timelineStartMs)
        assertEquals(200L, trimmedClip.trimStartMs)
        assertEquals(1_000L, trimmedClip.timelineEndMs)
    }

    @Test
    fun `slide edit keeps neighboring clips connected`() {
        val first = clip(
            id = "a",
            timelineStartMs = 0L,
            trimStartMs = 0L,
            trimEndMs = 500L,
            sourceDurationMs = 800L
        )
        val middle = clip(
            id = "b",
            timelineStartMs = 500L,
            trimStartMs = 0L,
            trimEndMs = 500L,
            sourceDurationMs = 500L
        )
        val last = clip(
            id = "c",
            timelineStartMs = 1_000L,
            trimStartMs = 0L,
            trimEndMs = 500L,
            sourceDurationMs = 800L
        )
        val track = Track(type = TrackType.VIDEO, index = 0, clips = listOf(first, middle, last))

        val shiftedTrack = slideClipOnTrack(
            track = track,
            clipId = middle.id,
            newStartMs = 600L
        )

        assertEquals(600L, shiftedTrack.clips[1].timelineStartMs)
        assertEquals(600L, shiftedTrack.clips[0].timelineEndMs)
        assertEquals(1_100L, shiftedTrack.clips[2].timelineStartMs)
        assertEquals(100L, shiftedTrack.clips[2].trimStartMs)
    }

    @Test
    fun `slide edit honors speed curve timing when extending previous clip`() {
        val first = clip(
            id = "a",
            timelineStartMs = 0L,
            trimStartMs = 0L,
            trimEndMs = 1_000L,
            sourceDurationMs = 2_000L,
            speedCurve = SpeedCurve.constant(2f)
        )
        val second = clip(
            id = "b",
            timelineStartMs = first.timelineEndMs,
            trimStartMs = 0L,
            trimEndMs = 500L,
            sourceDurationMs = 500L
        )
        val track = Track(type = TrackType.VIDEO, index = 0, clips = listOf(first, second))

        val shiftedTrack = slideClipOnTrack(
            track = track,
            clipId = second.id,
            newStartMs = 600L
        )

        assertWithin(1_200L, shiftedTrack.clips[0].trimEndMs, toleranceMs = 4L)
        assertEquals(600L, shiftedTrack.clips[1].timelineStartMs)
    }

    @Test
    fun `preferred audio track skips overlapping lanes`() {
        val overlappingAudio = Track(
            type = TrackType.AUDIO,
            index = 0,
            clips = listOf(
                clip(
                    id = "busy",
                    timelineStartMs = 0L,
                    trimStartMs = 0L,
                    trimEndMs = 1_000L,
                    sourceDurationMs = 1_000L
                )
            )
        )
        val openAudio = Track(type = TrackType.AUDIO, index = 1)

        val audioTrackIndex = preferredAudioTrackIndex(
            tracks = listOf(overlappingAudio, openAudio),
            startMs = 200L,
            endMs = 800L
        )

        assertEquals(1, audioTrackIndex)
    }

    @Test
    fun `preferred audio track returns null when all tracks overlap`() {
        val busyAudio = Track(
            type = TrackType.AUDIO,
            index = 0,
            clips = listOf(
                clip(
                    id = "busy",
                    timelineStartMs = 0L,
                    trimStartMs = 0L,
                    trimEndMs = 1_000L,
                    sourceDurationMs = 1_000L
                )
            )
        )

        val audioTrackIndex = preferredAudioTrackIndex(
            tracks = listOf(busyAudio),
            startMs = 200L,
            endMs = 800L
        )

        assertNull(audioTrackIndex)
    }

    @Test
    fun `merge predicate accepts clips that touch in source and timeline`() {
        val first = clip(
            id = "first",
            timelineStartMs = 0L,
            trimStartMs = 0L,
            trimEndMs = 500L,
            sourceDurationMs = 1_000L
        )
        val second = clip(
            id = "second",
            timelineStartMs = 500L,
            trimStartMs = 500L,
            trimEndMs = 1_000L,
            sourceDurationMs = 1_000L
        )

        assertTrue(canMergeAdjacentClips(first, second))
    }

    @Test
    fun `merge predicate rejects clips separated by a timeline gap`() {
        val first = clip(
            id = "first",
            timelineStartMs = 0L,
            trimStartMs = 0L,
            trimEndMs = 500L,
            sourceDurationMs = 1_000L
        )
        val second = clip(
            id = "second",
            timelineStartMs = 700L,
            trimStartMs = 500L,
            trimEndMs = 1_000L,
            sourceDurationMs = 1_000L
        )

        assertFalse(canMergeAdjacentClips(first, second))
    }

    private fun clip(
        id: String,
        timelineStartMs: Long,
        trimStartMs: Long,
        trimEndMs: Long,
        sourceDurationMs: Long,
        speedCurve: SpeedCurve? = null
    ): Clip {
        return Clip(
            id = id,
            sourceUri = FakeUri,
            sourceDurationMs = sourceDurationMs,
            timelineStartMs = timelineStartMs,
            trimStartMs = trimStartMs,
            trimEndMs = trimEndMs,
            speedCurve = speedCurve
        )
    }

    private fun assertWithin(expected: Long, actual: Long, toleranceMs: Long) {
        assertTrue(
            "expected $actual to be within ${toleranceMs}ms of $expected",
            kotlin.math.abs(actual - expected) <= toleranceMs
        )
    }
}

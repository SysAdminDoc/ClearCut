package com.novacut.editor.ui.editor

import android.net.FakeUri
import com.novacut.editor.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineEditingTest {

    @Test
    fun `playback restarts from zero after reaching the edited timeline end`() {
        assertEquals(0L, playbackStartPosition(playheadMs = 4_000L, totalDurationMs = 4_000L))
        assertEquals(0L, playbackStartPosition(playheadMs = 5_000L, totalDurationMs = 4_000L))
        assertEquals(2_500L, playbackStartPosition(playheadMs = 2_500L, totalDurationMs = 4_000L))
        assertEquals(0L, playbackStartPosition(playheadMs = 0L, totalDurationMs = 0L))
    }

    @Test
    fun `long press opens compound clips through ViewModel callback`() {
        var openedClipId: String? = null
        var toggledClipId: String? = null

        val result = dispatchTimelineClipLongPress(
            clipId = "compound",
            isCompound = true,
            onOpenCompoundClip = { clipId ->
                openedClipId = clipId
                true
            },
            onToggleMultiSelect = { clipId -> toggledClipId = clipId }
        )

        assertEquals(TimelineClipLongPressResult.OPENED_COMPOUND, result)
        assertEquals("compound", openedClipId)
        assertNull(toggledClipId)
    }

    @Test
    fun `long press keeps multi-select fallback for regular clips and rejected compound opens`() {
        var regularToggledClipId: String? = null
        val regularResult = dispatchTimelineClipLongPress(
            clipId = "regular",
            isCompound = false,
            onOpenCompoundClip = { true },
            onToggleMultiSelect = { clipId -> regularToggledClipId = clipId }
        )

        var rejectedToggledClipId: String? = null
        val rejectedResult = dispatchTimelineClipLongPress(
            clipId = "compound",
            isCompound = true,
            onOpenCompoundClip = { false },
            onToggleMultiSelect = { clipId -> rejectedToggledClipId = clipId }
        )

        assertEquals(TimelineClipLongPressResult.TOGGLED_MULTI_SELECT, regularResult)
        assertEquals("regular", regularToggledClipId)
        assertEquals(TimelineClipLongPressResult.TOGGLED_MULTI_SELECT, rejectedResult)
        assertEquals("compound", rejectedToggledClipId)
    }

    @Test
    fun `snap target returns nearest position within threshold`() {
        val targets = listOf(0L, 1_000L, 1_900L, 4_000L)

        assertEquals(1_900L, findSnapTarget(positionMs = 2_020L, targets = targets, thresholdMs = 150L))
        assertNull(findSnapTarget(positionMs = 2_020L, targets = targets, thresholdMs = 100L))
    }

    @Test
    fun `timeline position hit test includes clip start and excludes clip end`() {
        val clip = clip(
            id = "clip",
            timelineStartMs = 1_000L,
            trimStartMs = 0L,
            trimEndMs = 500L,
            sourceDurationMs = 500L
        )

        assertFalse(clip.containsTimelinePosition(999L))
        assertTrue(clip.containsTimelinePosition(1_000L))
        assertTrue(clip.containsTimelinePosition(1_499L))
        assertFalse(clip.containsTimelinePosition(1_500L))
    }

    @Test
    fun `accessible split point uses playhead inside safe range and midpoint otherwise`() {
        val clip = clip(
            id = "clip",
            timelineStartMs = 1_000L,
            trimStartMs = 0L,
            trimEndMs = 1_000L,
            sourceDurationMs = 1_000L
        )

        assertEquals(1_250L, clip.accessibleSplitPointMs(1_250L))
        assertEquals(1_500L, clip.accessibleSplitPointMs(5_000L))
    }

    @Test
    fun `accessible split point is unavailable for sub minimum clips`() {
        val clip = clip(
            id = "short",
            timelineStartMs = 0L,
            trimStartMs = 0L,
            trimEndMs = 150L,
            sourceDurationMs = 150L
        )

        assertNull(clip.accessibleSplitPointMs(50L))
    }

    @Test
    fun `keyboard nudge switches to coarse steps when shift is held`() {
        assertEquals(100L, keyboardNudgeAmountMs(isShiftPressed = false))
        assertEquals(1_000L, keyboardNudgeAmountMs(isShiftPressed = true))
    }

    @Test
    fun `overview tap centers viewport on tapped timeline fraction`() {
        assertEquals(
            4_000L,
            timelineOverviewScrollOffsetForTap(
                xPx = 50f,
                widthPx = 100f,
                totalDurationMs = 10_000L,
                visibleDurationMs = 2_000L,
                currentScrollOffsetMs = 250L
            )
        )
        assertEquals(
            250L,
            timelineOverviewScrollOffsetForTap(
                xPx = 50f,
                widthPx = 0f,
                totalDurationMs = 10_000L,
                visibleDurationMs = 2_000L,
                currentScrollOffsetMs = 250L
            )
        )
    }

    @Test
    fun `timeline clip names clean file-like source labels and keep fallbacks for generated names`() {
        assertEquals("Vacation opening take", formatTimelineClipName("Vacation_opening%20take.mov", fallback = "Video"))
        assertEquals("Video", formatTimelineClipName("IMG_20240604_120000.mp4", fallback = "Video"))
        assertEquals("Video", formatTimelineClipName("20240604120000.mp4", fallback = "Video"))
        assertEquals("Video", formatTimelineClipName(null, fallback = "Video"))
    }

    @Test
    fun `timeline formatters keep compact editor labels stable`() {
        assertEquals("0:00", formatTimelineTime(-1_000L))
        assertEquals("1:01", formatTimelineTime(61_000L))
        assertEquals("1:01:01", formatTimelineTime(3_661_000L))
        assertEquals("0.5s", formatTimelineDurationLabel(500L))
        assertEquals("1:01", formatTimelineDurationLabel(61_000L))
        assertEquals("2x", formatSpeedLabel(2.04f))
        assertEquals("1.3x", formatSpeedLabel(1.25f))
    }

    @Test
    fun `volume keyframes are filtered and sorted for envelope rendering`() {
        val sourceClip = clip(
            id = "audio",
            timelineStartMs = 0L,
            trimStartMs = 0L,
            trimEndMs = 1_000L,
            sourceDurationMs = 1_000L
        ).copy(
            keyframes = listOf(
                Keyframe(timeOffsetMs = 300L, property = KeyframeProperty.VOLUME, value = 0.9f),
                Keyframe(timeOffsetMs = 10L, property = KeyframeProperty.OPACITY, value = 0.5f),
                Keyframe(timeOffsetMs = 100L, property = KeyframeProperty.VOLUME, value = 0.4f)
            )
        )

        assertEquals(listOf(100L, 300L), volumeKeyframesSorted(sourceClip).map { it.timeOffsetMs })
    }

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
    fun `linked leading trim uses the most restrictive neighboring boundary`() {
        val video = clip("video", 500L, 200L, 1_000L, 1_000L)
        val audio = clip("audio", 500L, 200L, 1_000L, 1_000L)
        val videoTrack = Track(
            type = TrackType.VIDEO,
            index = 0,
            clips = listOf(clip("video-prev", 0L, 0L, 400L, 400L), video)
        )
        val audioTrack = Track(
            type = TrackType.AUDIO,
            index = 1,
            clips = listOf(clip("audio-prev", 0L, 0L, 450L, 450L), audio)
        )

        val trimmedTracks = trimLinkedClipsOnTimeline(
            tracks = listOf(videoTrack, audioTrack),
            anchorClipId = video.id,
            targetClipIds = setOf(video.id, audio.id),
            requestedTrimStartMs = 0L
        )

        val trimmedVideo = trimmedTracks[0].clips.last()
        val trimmedAudio = trimmedTracks[1].clips.last()
        assertEquals(450L, trimmedVideo.timelineStartMs)
        assertEquals(450L, trimmedAudio.timelineStartMs)
        assertEquals(150L, trimmedVideo.trimStartMs)
        assertEquals(150L, trimmedAudio.trimStartMs)
        assertEquals(trimmedVideo.timelineEndMs, trimmedAudio.timelineEndMs)
    }

    @Test
    fun `linked trailing trim uses the most restrictive next clip boundary`() {
        val video = clip("video", 0L, 0L, 600L, 1_000L)
        val audio = clip("audio", 0L, 0L, 600L, 1_000L)
        val videoTrack = Track(
            type = TrackType.VIDEO,
            index = 0,
            clips = listOf(video, clip("video-next", 900L, 0L, 100L, 100L))
        )
        val audioTrack = Track(
            type = TrackType.AUDIO,
            index = 1,
            clips = listOf(audio, clip("audio-next", 700L, 0L, 100L, 100L))
        )

        val trimmedTracks = trimLinkedClipsOnTimeline(
            tracks = listOf(videoTrack, audioTrack),
            anchorClipId = video.id,
            targetClipIds = setOf(video.id, audio.id),
            requestedTrimEndMs = 1_000L
        )

        val trimmedVideo = trimmedTracks[0].clips.first()
        val trimmedAudio = trimmedTracks[1].clips.first()
        assertEquals(700L, trimmedVideo.timelineEndMs)
        assertEquals(700L, trimmedAudio.timelineEndMs)
        assertEquals(700L, trimmedVideo.trimEndMs)
        assertEquals(700L, trimmedAudio.trimEndMs)
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

    @Test
    fun `edit target expansion includes linked clips and every member of selected groups`() {
        val groupedVideo = clip("video", 0L, 0L, 500L, 500L).copy(
            linkedClipId = "audio",
            groupId = "scene"
        )
        val linkedAudio = clip("audio", 0L, 0L, 500L, 500L).copy(linkedClipId = "video")
        val groupedOverlay = clip("overlay", 700L, 0L, 300L, 300L).copy(groupId = "scene")
        val tracks = listOf(
            Track(type = TrackType.VIDEO, index = 0, clips = listOf(groupedVideo)),
            Track(type = TrackType.AUDIO, index = 1, clips = listOf(linkedAudio)),
            Track(type = TrackType.OVERLAY, index = 2, clips = listOf(groupedOverlay))
        )

        assertEquals(
            setOf("video", "audio", "overlay"),
            expandTimelineEditClipIds(tracks, setOf("video"))
        )
    }

    @Test
    fun `linked split rejects the entire pair when one member is too short`() {
        val video = clip("video", 0L, 0L, 1_000L, 1_000L).copy(linkedClipId = "audio")
        val audio = clip("audio", 0L, 0L, 550L, 550L).copy(linkedClipId = "video")
        val tracks = listOf(
            Track(type = TrackType.VIDEO, index = 0, clips = listOf(video)),
            Track(type = TrackType.AUDIO, index = 1, clips = listOf(audio))
        )

        assertTrue(linkedSplitCandidateIds(tracks, setOf(video.id), 500L).isEmpty())
        assertEquals(
            setOf("video", "audio"),
            linkedSplitCandidateIds(tracks, setOf(video.id), 300L)
        )
    }

    @Test
    fun `source beat markers map through trim speed and timeline start`() {
        val retimed = clip(
            id = "audio",
            timelineStartMs = 5_000L,
            trimStartMs = 1_000L,
            trimEndMs = 3_000L,
            sourceDurationMs = 4_000L
        ).copy(speed = 2f)

        assertEquals(
            listOf(5_250L, 5_750L),
            mapSourceMarkersToTimeline(retimed, listOf(500L, 1_500L, 2_500L, 3_500L))
        )
    }

    @Test
    fun `ripple delete preserves existing gaps and leaves untouched tracks unchanged`() {
        val first = clip("first", 1_000L, 0L, 500L, 500L)
        val deleted = clip("deleted", 2_000L, 0L, 500L, 500L)
        val last = clip("last", 3_000L, 0L, 400L, 400L)
        val untouched = clip("untouched", 5_000L, 0L, 300L, 300L)
        val tracks = listOf(
            Track(type = TrackType.VIDEO, index = 0, clips = listOf(first, deleted, last)),
            Track(type = TrackType.AUDIO, index = 1, clips = listOf(untouched))
        )

        val result = rippleDeleteClips(tracks, setOf("deleted"))

        assertEquals(listOf("first", "last"), result[0].clips.map { it.id })
        assertEquals(listOf(1_000L, 2_500L), result[0].clips.map { it.timelineStartMs })
        assertEquals(5_000L, result[1].clips.single().timelineStartMs)
    }

    @Test
    fun `timeline markers inside deleted media are removed and later markers ripple`() {
        val ranges = listOf(1_000L to 1_500L, 2_000L to 2_250L)

        assertEquals(900L, rippleTimelinePosition(900L, ranges))
        assertNull(rippleTimelinePosition(1_200L, ranges))
        assertEquals(1_300L, rippleTimelinePosition(1_800L, ranges))
        assertNull(rippleTimelinePosition(2_100L, ranges))
        assertEquals(1_750L, rippleTimelinePosition(2_500L, ranges))
    }

    @Test
    fun `preview playhead follows ripple deletes and lands at a removed clip boundary`() {
        val ranges = listOf(1_000L to 1_500L, 2_000L to 2_250L)

        assertEquals(900L, ripplePlaybackPosition(900L, ranges))
        assertEquals(1_000L, ripplePlaybackPosition(1_200L, ranges))
        assertEquals(1_300L, ripplePlaybackPosition(1_800L, ranges))
        assertEquals(1_500L, ripplePlaybackPosition(2_100L, ranges))
        assertEquals(1_750L, ripplePlaybackPosition(2_500L, ranges))
    }

    @Test
    fun `preview playhead counts overlapping delete ranges only once`() {
        val ranges = listOf(1_000L to 1_600L, 1_400L to 1_800L)

        assertEquals(1_000L, ripplePlaybackPosition(1_500L, ranges))
        assertEquals(1_200L, ripplePlaybackPosition(2_000L, ranges))
    }

    @Test
    fun `assistant range validation rejects a cut with an unsafe trailing fragment`() {
        val source = clip("source", 0L, 0L, 1_000L, 1_000L)
        val tracks = listOf(Track(type = TrackType.VIDEO, index = 0, clips = listOf(source)))

        assertTrue(canDeleteTimelineRangeAtomically(tracks, source.id, 200L, 700L))
        assertFalse(canDeleteTimelineRangeAtomically(tracks, source.id, 200L, 950L))
    }

    @Test
    fun `split preserves absolute animation and caption timing with fresh right-side identities`() {
        val sourceClip = clip("source", 1_000L, 0L, 1_000L, 1_000L).copy(
            groupId = "left-group",
            fadeInMs = 100L,
            fadeOutMs = 200L,
            keyframes = listOf(
                Keyframe(0L, KeyframeProperty.POSITION_X, 0f, interpolation = KeyframeInterpolation.LINEAR),
                Keyframe(1_000L, KeyframeProperty.POSITION_X, 10f, interpolation = KeyframeInterpolation.LINEAR)
            ),
            effects = listOf(
                Effect(
                    id = "effect",
                    type = EffectType.BRIGHTNESS,
                    keyframes = listOf(
                        EffectKeyframe(0L, "amount", 0f),
                        EffectKeyframe(1_000L, "amount", 1f)
                    )
                )
            ),
            masks = listOf(
                Mask(
                    id = "mask",
                    type = MaskType.RECTANGLE,
                    points = listOf(MaskPoint(0f, 0f)),
                    keyframes = listOf(
                        MaskKeyframe(0L, listOf(MaskPoint(0f, 0f))),
                        MaskKeyframe(1_000L, listOf(MaskPoint(1f, 1f)))
                    )
                )
            ),
            captions = listOf(
                Caption(
                    id = "caption",
                    text = "crosses cut",
                    startTimeMs = 400L,
                    endTimeMs = 700L,
                    words = listOf(CaptionWord("cut", 420L, 680L))
                )
            ),
            motionTrackingData = MotionTrackingData(
                id = "motion",
                trackPoints = listOf(
                    MotionTrackPoint(0L, 0f, 0f),
                    MotionTrackPoint(1_000L, 1f, 1f)
                )
            ),
            audioEffects = listOf(AudioEffect(id = "audio-fx", type = AudioEffectType.COMPRESSOR))
        )
        var generatedId = 0

        val split = splitTimelineClip(
            clip = sourceClip,
            playheadMs = 1_500L,
            newClipId = "right",
            newLinkedClipId = null,
            rightGroupId = "right-group",
            idFactory = { "generated-${generatedId++}" }
        ) ?: error("expected split")

        assertEquals(500L, split.left.durationMs)
        assertEquals(500L, split.right.durationMs)
        assertEquals(5f, split.left.keyframes.last().value, 0.01f)
        assertEquals(500L, split.left.keyframes.last().timeOffsetMs)
        assertEquals(5f, split.right.keyframes.first().value, 0.01f)
        assertEquals(0L, split.right.keyframes.first().timeOffsetMs)
        assertEquals(0.5f, split.right.effects.single().keyframes.first().value, 0.01f)
        assertFalse(split.right.effects.single().id == "effect")
        assertFalse(split.right.masks.single().id == "mask")
        assertEquals(400L, split.left.captions.single().startTimeMs)
        assertEquals(500L, split.left.captions.single().endTimeMs)
        assertEquals(0L, split.right.captions.single().startTimeMs)
        assertEquals(200L, split.right.captions.single().endTimeMs)
        assertEquals(0L, split.right.captions.single().words.single().startTimeMs)
        assertEquals(100L, split.left.fadeInMs)
        assertEquals(0L, split.left.fadeOutMs)
        assertEquals(0L, split.right.fadeInMs)
        assertEquals(200L, split.right.fadeOutMs)
        assertEquals("left-group", split.left.groupId)
        assertEquals("right-group", split.right.groupId)
        assertFalse(split.right.audioEffects.single().id == "audio-fx")
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

package com.novacut.editor.ui.editor

import android.net.FakeUri
import com.novacut.editor.model.Clip
import com.novacut.editor.model.KeyframeProperty
import com.novacut.editor.model.TimelineRange
import com.novacut.editor.model.Track
import com.novacut.editor.model.TrackType
import com.novacut.editor.engine.KeyframeEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineMuteRangeTest {

    @Test
    fun projectRangeIsMappedToClipTimeAndHonorsTrackOffset() {
        val audio = Clip(
            id = "audio",
            sourceUri = FakeUri,
            sourceDurationMs = 1_000L,
            trimStartMs = 0L,
            trimEndMs = 1_000L,
            timelineStartMs = 300L,
            volume = 0.75f,
        )
        val text = Clip(
            id = "text",
            sourceUri = FakeUri,
            sourceDurationMs = 1_000L,
            trimStartMs = 0L,
            trimEndMs = 1_000L,
            timelineStartMs = 300L,
        )
        val tracks = listOf(
            Track(
                type = TrackType.AUDIO,
                index = 0,
                timelineOffsetMs = 100L,
                clips = listOf(audio),
            ),
            Track(type = TrackType.TEXT, index = 1, clips = listOf(text)),
        )

        val result = muteTimelineRange(
            tracks = tracks,
            range = TimelineRange(500L, 900L),
            audioClipIds = setOf("audio"),
        )
        val muted = result.tracks.first().clips.single()

        assertEquals(1, result.changedClipCount)
        assertEquals(0f, KeyframeEngine.getValueAt(muted.keyframes, KeyframeProperty.VOLUME, 250L))
        assertEquals(0.75f, KeyframeEngine.getValueAt(muted.keyframes, KeyframeProperty.VOLUME, 500L))
        assertEquals(0, result.tracks[1].clips.single().keyframes.size)
        assertTrue(muted.keyframes.all { it.property == KeyframeProperty.VOLUME })
    }
}

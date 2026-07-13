package com.novacut.editor.engine

import android.net.Uri
import androidx.media3.common.C
import com.novacut.editor.model.Clip
import com.novacut.editor.model.Track
import com.novacut.editor.model.TrackType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PreviewCompositionPlanTest {
    @Test
    fun `orders every visible visual lane and keeps the upper active clip authoritative`() {
        val base = track("base", TrackType.VIDEO, 0, clip("base-clip", 0L, 4_000L))
        val hidden = track("hidden", TrackType.OVERLAY, 1, clip("hidden-clip", 0L, 4_000L), visible = false)
        val upper = track("upper", TrackType.OVERLAY, 2, clip("upper-clip", 1_000L, 2_000L))

        val plan = PreviewCompositionPlan.create(listOf(upper, hidden, base))

        assertEquals(listOf("upper", "base"), plan.visualTracks.map { it.id })
        assertEquals("base-clip", plan.primaryClipAt(500L)?.id)
        assertEquals("upper-clip", plan.primaryClipAt(1_500L)?.id)
        assertEquals("base-clip", plan.primaryClipAt(3_500L)?.id)
    }

    @Test
    fun `audio lane matrix follows export mute solo visibility and volume policy`() {
        val music = track("music", TrackType.AUDIO, 2, clip("music-clip", 500L, 3_000L), volume = 2f)
        val mutedSolo = track(
            "muted-solo", TrackType.AUDIO, 3, clip("muted", 0L, 4_000L), muted = true, solo = true
        )
        val voiceSolo = track(
            "voice-solo", TrackType.AUDIO, 4, clip("voice", 0L, 4_000L), solo = true, volume = 0.5f
        )
        val hiddenSolo = track(
            "hidden-solo", TrackType.AUDIO, 5, clip("hidden", 0L, 4_000L), visible = false, solo = true
        )

        val plan = PreviewCompositionPlan.create(listOf(music, mutedSolo, voiceSolo, hiddenSolo))

        assertEquals(setOf("muted-solo", "voice-solo", "hidden-solo"), plan.soloTrackIds)
        assertEquals(listOf("voice-solo"), plan.audioTracks.map { it.id })
        assertEquals(0.5f, plan.audioTracks.single().volume)
        assertEquals(4_000L, plan.durationMs)
    }

    @Test
    fun `preserves gaps when no visual lane is active`() {
        val lower = track("lower", TrackType.VIDEO, 0, clip("late", 2_000L, 1_000L))
        val upper = track("upper", TrackType.OVERLAY, 1, clip("early", 0L, 500L))
        val plan = PreviewCompositionPlan.create(listOf(lower, upper))

        assertEquals("early", plan.primaryClipAt(250L)?.id)
        assertNull(plan.primaryClipAt(1_000L))
        assertEquals("late", plan.primaryClipAt(2_500L)?.id)
    }

    @Test
    fun `speed curves publish increasing sampled change points`() {
        assertEquals(10_000L, nextSampledSpeedChangeTimeUs(0L, 35_000L))
        assertEquals(20_000L, nextSampledSpeedChangeTimeUs(10_000L, 35_000L))
        assertEquals(30_000L, nextSampledSpeedChangeTimeUs(29_999L, 35_000L))
        assertEquals(C.TIME_UNSET, nextSampledSpeedChangeTimeUs(30_000L, 35_000L))
        assertEquals(C.TIME_UNSET, nextSampledSpeedChangeTimeUs(-1L, 35_000L))
    }

    private fun track(
        id: String,
        type: TrackType,
        index: Int,
        clip: Clip,
        visible: Boolean = true,
        muted: Boolean = false,
        solo: Boolean = false,
        volume: Float = 1f,
    ) = Track(
        id = id,
        type = type,
        index = index,
        clips = listOf(clip),
        isVisible = visible,
        isMuted = muted,
        isSolo = solo,
        volume = volume,
    )

    private fun clip(id: String, startMs: Long, durationMs: Long) = Clip(
        id = id,
        sourceUri = Uri.parse("file:///$id.mp4"),
        sourceDurationMs = durationMs,
        timelineStartMs = startMs,
        trimEndMs = durationMs,
    )
}

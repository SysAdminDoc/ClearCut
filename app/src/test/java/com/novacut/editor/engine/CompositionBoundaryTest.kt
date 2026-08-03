package com.novacut.editor.engine

import android.net.FakeUri
import androidx.media3.common.C
import androidx.media3.transformer.EditedMediaItemSequence
import com.novacut.editor.model.Clip
import com.novacut.editor.model.Track
import com.novacut.editor.model.TrackType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompositionBoundaryTest {

    @Test
    fun compositionPlanOwnsLayerOrderingSoloFilteringAndOverlayDuration() {
        val base = track("base", TrackType.VIDEO, 0, 1_000L)
        val overlay = track("overlay", TrackType.OVERLAY, 3, 2_000L)
        val hidden = track("hidden", TrackType.VIDEO, 4, 5_000L, visible = false)
        val music = track("music", TrackType.AUDIO, 2, 1_500L, solo = true)
        val plan = CompositionPlanBuilder.build(
            tracks = listOf(music, hidden, base, overlay),
            additionalDurationsMs = listOf(7_500L),
        )

        assertEquals(listOf("overlay", "base"), plan.visualTracks.map { it.id })
        assertEquals(listOf("music"), plan.audioTracks.map { it.id })
        assertEquals(setOf("music"), plan.soloTrackIds)
        assertEquals(7_500L, plan.durationMs)
    }

    @Test
    fun compositionBuilderKeepsSequenceCountAndRejectsTransmuxWhenAudioIsExplicit() {
        val sequence = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_VIDEO))
            .addGap(1_000L)
            .build()
        val composition = CompositionBuilder.build(
            CompositionBuildRequest(
                sequences = listOf(sequence),
                hasAudioTracks = true,
                hasEmbeddedVisualAudio = true,
                targetWidth = 1280,
                targetHeight = 720,
                allowAudioTransmux = true,
            )
        )

        assertEquals(1, composition.sequences.size)
        assertFalse(composition.transmuxAudio)
        assertTrue(composition.sequences.single() === sequence)
    }

    private fun track(
        id: String,
        type: TrackType,
        index: Int,
        durationMs: Long,
        visible: Boolean = true,
        solo: Boolean = false,
    ) = Track(
        id = id,
        type = type,
        index = index,
        isVisible = visible,
        isSolo = solo,
        clips = listOf(
            Clip(
                id = "$id-clip",
                sourceUri = FakeUri,
                sourceDurationMs = durationMs,
                timelineStartMs = 0L,
                trimEndMs = durationMs,
            )
        ),
    )
}

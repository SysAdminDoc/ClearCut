package com.novacut.editor.ui.editor

import androidx.media3.common.Player
import com.novacut.editor.engine.PreviewRenderPolicy
import com.novacut.editor.engine.buildTimelineSequenceSteps
import com.novacut.editor.engine.playbackSessionNeedsReset
import com.novacut.editor.model.Clip
import com.novacut.editor.model.EffectType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import android.net.FakeUri

class EditorPlaybackBehaviorTest {
    @Test
    fun livePreviewOmitsTransitionsButRetainsSupportedClipEffects() {
        assertFalse(PreviewRenderPolicy.includesTransitions(previewMode = true))
        assertTrue(PreviewRenderPolicy.includesTransitions(previewMode = false))
        assertFalse(PreviewRenderPolicy.includesEffect(EffectType.BG_REMOVAL, previewMode = true))
        assertTrue(PreviewRenderPolicy.includesEffect(EffectType.GAUSSIAN_BLUR, previewMode = true))
    }

    @Test
    fun previewSequenceRepresentsGapsAsPlayableTimelineSteps() {
        val clip = Clip(
            id = "clip",
            sourceUri = FakeUri,
            sourceDurationMs = 1_000L,
            timelineStartMs = 500L,
            trimEndMs = 1_000L,
        )

        val steps = buildTimelineSequenceSteps(listOf(clip), totalDurationMs = 2_000L)

        assertEquals(
            listOf(
                TimelineSequenceStepDescription.Gap(0L, 500L),
                TimelineSequenceStepDescription.Clip("clip", 500L, 1_000L),
                TimelineSequenceStepDescription.Gap(1_500L, 500L),
            ),
            steps.map {
                when (it) {
                    is com.novacut.editor.engine.TimelineSequenceStep.GapStep ->
                        TimelineSequenceStepDescription.Gap(it.timelineStartMs, it.durationMs)
                    is com.novacut.editor.engine.TimelineSequenceStep.ClipStep ->
                        TimelineSequenceStepDescription.Clip(it.clip.id, it.timelineStartMs, it.durationMs)
                }
            },
        )
    }

    @Test
    fun trimOutsidePreparedRangeIsRecognizedForExtendedPreview() {
        val prepared = PreparedTrimRange(startMs = 200L, endMs = 800L)
        val clip = Clip(
            id = "clip",
            sourceUri = FakeUri,
            sourceDurationMs = 2_000L,
            timelineStartMs = 0L,
            trimStartMs = 150L,
            trimEndMs = 800L,
        )

        assertTrue(trimExtendsPreparedRange(prepared, clip))
        assertFalse(trimExtendsPreparedRange(prepared, clip.copy(trimStartMs = 200L)))
    }

    @Test
    fun endedOrFailedPlaybackAlwaysRequiresAFreshPlayerSession() {
        assertTrue(
            playbackSessionNeedsReset(
                forceRestart = false,
                playbackState = Player.STATE_ENDED,
                hasPlayerError = false,
            )
        )
        assertTrue(
            playbackSessionNeedsReset(
                forceRestart = false,
                playbackState = Player.STATE_READY,
                hasPlayerError = true,
            )
        )
        assertFalse(
            playbackSessionNeedsReset(
                forceRestart = false,
                playbackState = Player.STATE_READY,
                hasPlayerError = false,
            )
        )
    }

    private sealed interface TimelineSequenceStepDescription {
        data class Gap(val startMs: Long, val durationMs: Long) : TimelineSequenceStepDescription
        data class Clip(val id: String, val startMs: Long, val durationMs: Long) : TimelineSequenceStepDescription
    }
}

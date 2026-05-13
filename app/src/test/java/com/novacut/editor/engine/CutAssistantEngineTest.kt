package com.novacut.editor.engine

import android.net.FakeUri
import com.novacut.editor.model.Clip
import com.novacut.editor.model.Track
import com.novacut.editor.model.TrackType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression coverage for [CutAssistantEngine]. The engine orchestrates silence
 * + filler-word detection across the whole timeline and is the source of truth
 * for what the "Review proposed cuts" sheet shows, so any drift here silently
 * cuts the wrong content.
 */
class CutAssistantEngineTest {

    private val silenceEngine = SilenceDetectionEngine()
    private val engine = CutAssistantEngine(silenceEngine)

    private fun audioClip(
        id: String,
        timelineStartMs: Long,
        sourceDurationMs: Long,
        trimStartMs: Long = 0L,
        trimEndMs: Long = sourceDurationMs,
        speed: Float = 1f
    ) = Clip(
        id = id,
        sourceUri = FakeUri,
        sourceDurationMs = sourceDurationMs,
        trimStartMs = trimStartMs,
        trimEndMs = trimEndMs,
        timelineStartMs = timelineStartMs,
        speed = speed
    )

    @Test
    fun `review with no audio returns empty review set`() {
        val tracks = listOf(
            Track(id = "t1", type = TrackType.AUDIO, index = 0, clips = listOf(audioClip("c1", 0L, 5000L)))
        )
        // No perClipAudio entry — engine should gracefully skip.
        val review = engine.review(tracks, emptyMap())
        assertTrue(review.proposals.isEmpty())
        assertTrue(review.accepted.isEmpty())
    }

    @Test
    fun `silence inside clip projects to timeline coordinates`() {
        val clip = audioClip("c1", timelineStartMs = 10_000L, sourceDurationMs = 5000L)
        val tracks = listOf(Track(id = "t1", type = TrackType.AUDIO, index = 0, clips = listOf(clip)))
        val waveform = FloatArray(5000) { 0f } // all silence, 1 sample/ms
        val audio = mapOf(
            clip.id to CutAssistantEngine.ClipAudio(
                clipId = clip.id,
                waveform = waveform,
                sampleRate = 1000,
                words = emptyList()
            )
        )
        // Use paddingMs=0 so the projected range matches the clip extents exactly
        // and we can assert precise endpoints without coupling to the silence
        // engine's default safety margin.
        val review = engine.review(
            tracks,
            audio,
            SilenceDetectionEngine.AutoCutConfig(minSilenceMs = 500L, paddingMs = 0L)
        )
        assertEquals(1, review.proposals.size)
        val p = review.proposals[0]
        assertEquals(clip.id, p.clipId)
        assertEquals(10_000L, p.timelineStartMs)
        assertEquals(15_000L, p.timelineEndMs)
    }

    @Test
    fun `acceptAll then rejectAll round-trips`() {
        val clip = audioClip("c1", 0L, 4000L)
        val tracks = listOf(Track(id = "t1", type = TrackType.AUDIO, index = 0, clips = listOf(clip)))
        val audio = mapOf(
            clip.id to CutAssistantEngine.ClipAudio(
                clipId = clip.id,
                waveform = FloatArray(4000) { 0f },
                sampleRate = 1000
            )
        )
        val review = engine.review(tracks, audio)
        assertTrue(review.proposals.isNotEmpty())
        assertEquals(0, review.accepted.size)
        val accepted = review.acceptAll()
        assertEquals(review.proposals.size, accepted.accepted.size)
        val cleared = accepted.rejectAll()
        assertTrue(cleared.accepted.isEmpty())
    }

    @Test
    fun `planAcceptedOperations orders latest-first to keep right neighbours stable`() {
        val proposals = listOf(
            CutAssistantEngine.ReviewProposal(
                id = "p1",
                clipId = "c1",
                timelineStartMs = 1000L,
                timelineEndMs = 1500L,
                reason = SilenceDetectionEngine.CutProposal.Reason.SILENCE
            ),
            CutAssistantEngine.ReviewProposal(
                id = "p2",
                clipId = "c1",
                timelineStartMs = 4000L,
                timelineEndMs = 4500L,
                reason = SilenceDetectionEngine.CutProposal.Reason.FILLER_WORD
            ),
            CutAssistantEngine.ReviewProposal(
                id = "p3",
                clipId = "c1",
                timelineStartMs = 2500L,
                timelineEndMs = 3000L,
                reason = SilenceDetectionEngine.CutProposal.Reason.SILENCE
            )
        )
        val set = CutAssistantEngine.ReviewSet(proposals = proposals, accepted = proposals.map { it.id }.toSet())
        val ops = engine.planAcceptedOperations(set)
        assertEquals(3, ops.size)
        // Highest timelineStartMs must come first so applying the cuts left-to-
        // right keeps subsequent indices valid as we walk the timeline.
        val starts = ops.map {
            (it as CutAssistantEngine.CutOperation.RippleDelete).timelineStartMs
        }
        assertEquals(listOf(4000L, 2500L, 1000L), starts)
    }

    @Test
    fun `proposal outside trim range is dropped`() {
        // Clip's source is 5 s but user trimmed off the first 2 s. A silence at
        // source ms 0..1000 must NOT be projected — it's already trimmed out.
        val clip = audioClip(
            id = "c1",
            timelineStartMs = 0L,
            sourceDurationMs = 5000L,
            trimStartMs = 2000L,
            trimEndMs = 5000L
        )
        val tracks = listOf(Track(id = "t1", type = TrackType.AUDIO, index = 0, clips = listOf(clip)))
        // Synthesize a waveform that is silent only in 0..1000ms, loud elsewhere.
        val waveform = FloatArray(5000) { idx ->
            if (idx < 1000) 0f else 0.5f
        }
        val audio = mapOf(
            clip.id to CutAssistantEngine.ClipAudio(
                clipId = clip.id,
                waveform = waveform,
                sampleRate = 1000
            )
        )
        val review = engine.review(tracks, audio)
        // The trimmed-out silence shouldn't appear.
        val firstHalfProposal = review.proposals.firstOrNull { it.timelineStartMs < clip.timelineStartMs + 500L }
        assertNull(
            "Silence outside trim range was incorrectly projected: ${review.proposals}",
            firstHalfProposal
        )
    }

    @Test
    fun `merge collapses abutting silences within tolerance`() {
        // Two silences separated by <250 ms (the engine's merge tolerance)
        // should fuse into a single ReviewProposal for the UI.
        val waveform = FloatArray(5000) { idx ->
            // Silent 500..1500ms, loud 1500..1600ms (100ms gap), silent 1600..2600ms.
            when {
                idx in 500 until 1500 -> 0f
                idx in 1600 until 2600 -> 0f
                else -> 0.5f
            }
        }
        val clip = audioClip("c1", 0L, 5000L)
        val tracks = listOf(Track(id = "t1", type = TrackType.AUDIO, index = 0, clips = listOf(clip)))
        val audio = mapOf(
            clip.id to CutAssistantEngine.ClipAudio(
                clipId = clip.id,
                waveform = waveform,
                sampleRate = 1000
            )
        )
        val review = engine.review(
            tracks,
            audio,
            SilenceDetectionEngine.AutoCutConfig(minSilenceMs = 400L, paddingMs = 0L)
        )
        // After merging the gap-100ms pair we expect exactly ONE proposal.
        assertEquals(1, review.proposals.size)
        val p = review.proposals[0]
        assertTrue("Merged proposal should span both silences", p.durationMs >= 2000L)
    }

    @Test
    fun `ReviewProposal init rejects start equal to end`() {
        try {
            CutAssistantEngine.ReviewProposal(
                id = "p",
                clipId = "c",
                timelineStartMs = 100L,
                timelineEndMs = 100L,
                reason = SilenceDetectionEngine.CutProposal.Reason.SILENCE
            )
            error("Expected IllegalArgumentException for zero-duration proposal")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }

    @Test
    fun `totalReclaimMs sums accepted proposal durations only`() {
        val proposals = listOf(
            CutAssistantEngine.ReviewProposal(
                id = "p1",
                clipId = "c1",
                timelineStartMs = 0L,
                timelineEndMs = 1000L,
                reason = SilenceDetectionEngine.CutProposal.Reason.SILENCE
            ),
            CutAssistantEngine.ReviewProposal(
                id = "p2",
                clipId = "c1",
                timelineStartMs = 2000L,
                timelineEndMs = 2500L,
                reason = SilenceDetectionEngine.CutProposal.Reason.FILLER_WORD
            )
        )
        val partial = CutAssistantEngine.ReviewSet(proposals = proposals, accepted = setOf("p1"))
        assertEquals(1000L, partial.totalReclaimMs)
        val all = partial.acceptAll()
        assertEquals(1500L, all.totalReclaimMs)
    }
}

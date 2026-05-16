package com.novacut.editor.engine

import com.novacut.editor.engine.whisper.SherpaAsrEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SilenceDetectionEngineTest {

    private val engine = SilenceDetectionEngine()

    @Test
    fun detectSilences_emptyWaveform_returnsEmptyList() {
        val result = engine.detectSilences(FloatArray(0), 44100)
        assertTrue(result.isEmpty())
    }

    @Test
    fun detectSilences_zeroSampleRate_returnsEmptyList() {
        val result = engine.detectSilences(FloatArray(44100), 0)
        assertTrue(result.isEmpty())
    }

    @Test
    fun detectSilences_allSilent_producesOneRange() {
        val wave = FloatArray(44100) { 0f } // 1s @ 44.1 kHz all silence
        val result = engine.detectSilences(
            wave, 44100,
            SilenceDetectionEngine.AutoCutConfig(minSilenceMs = 500L, paddingMs = 0L)
        )
        assertEquals(1, result.size)
        assertEquals(SilenceDetectionEngine.CutProposal.Reason.SILENCE, result[0].reason)
        assertEquals(0L, result[0].startMs)
        assertEquals(1000L, result[0].endMs)
    }

    @Test
    fun detectSilences_loudAudio_producesEmpty() {
        val wave = FloatArray(44100) { 0.5f } // loud throughout
        val result = engine.detectSilences(wave, 44100)
        assertTrue(result.isEmpty())
    }

    @Test
    fun detectSilences_shortGap_belowThreshold_ignored() {
        // 50 ms silence -- below default 500 ms minimum
        val sampleRate = 44100
        val wave = FloatArray(sampleRate).apply {
            fill(0.5f) // loud
            for (i in 0 until sampleRate * 50 / 1000) this[i + 1000] = 0f
        }
        val result = engine.detectSilences(wave, sampleRate)
        assertTrue(result.isEmpty())
    }

    @Test
    fun detectSilences_paddingLargerThanRun_skipsProposal() {
        // 200 ms silence, 500 ms padding -- padding eats the whole range
        val sampleRate = 44100
        val wave = FloatArray(sampleRate).apply { fill(0.5f) }
        for (i in 0 until sampleRate * 200 / 1000) wave[i + 5000] = 0f
        val result = engine.detectSilences(
            wave, sampleRate,
            SilenceDetectionEngine.AutoCutConfig(minSilenceMs = 100L, paddingMs = 500L)
        )
        // padding > silence -- no valid proposal
        assertTrue(result.isEmpty())
    }

    @Test
    fun detectSilences_doesNotOverflowOnLargeMinSilence() {
        // With 48kHz * huge minSilenceMs, naive Int math would overflow.
        // The engine must stay in Long space and clamp to waveform bounds.
        val wave = FloatArray(1000) { 0f }
        val result = engine.detectSilences(
            wave, 48000,
            SilenceDetectionEngine.AutoCutConfig(
                minSilenceMs = 60_000L * 60 * 24 * 30, // 30 days
                paddingMs = 0L
            )
        )
        // minSilenceSamples is clamped to waveform.size, so a 1000-sample all-silent
        // run meets its own clamped threshold and yields one proposal.
        assertEquals(1, result.size)
    }

    @Test
    fun detectFillerWords_disabledConfig_returnsEmpty() {
        val words = listOf(
            SherpaAsrEngine.WordTimestamp("um", 100, 200)
        )
        val result = engine.detectFillerWords(
            words,
            SilenceDetectionEngine.AutoCutConfig(cutFillerWords = false)
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun detectFillerWords_matchesCaseInsensitive() {
        val words = listOf(
            SherpaAsrEngine.WordTimestamp("Um,", 100, 200),
            SherpaAsrEngine.WordTimestamp("hello", 200, 500),
            SherpaAsrEngine.WordTimestamp("UH", 500, 600)
        )
        val result = engine.detectFillerWords(words)
        assertEquals(2, result.size)
        assertEquals("um", result[0].matchedText)
        assertEquals("uh", result[1].matchedText)
    }

    @Test
    fun detectFillerWords_paddingClampedAtZero() {
        val words = listOf(SherpaAsrEngine.WordTimestamp("um", 50, 80))
        val result = engine.detectFillerWords(
            words,
            SilenceDetectionEngine.AutoCutConfig(paddingMs = 200L)
        )
        assertEquals(1, result.size)
        // startMs - paddingMs would be negative; coerced to 0.
        assertEquals(0L, result[0].startMs)
    }

    @Test
    fun defaultFillers_containNoMultiWordEntries() {
        // Whisper emits word-by-word; multi-word fillers can never match the
        // single-token matcher in detectFillerWords. Regression guard against
        // anyone adding "you know" back to the default set.
        SilenceDetectionEngine.DEFAULT_FILLERS.forEach { filler ->
            assertTrue(
                "Default filler '$filler' must be a single token -- multi-word fillers " +
                    "would silently never match Whisper word output.",
                !filler.contains(' ')
            )
        }
    }

    // --- C.2 follow-up: multi-word fillers ---

    private fun word(text: String, startMs: Long, endMs: Long) =
        SherpaAsrEngine.WordTimestamp(text, startMs, endMs)

    @Test
    fun detectMultiWordFillers_matchesYouKnow() {
        val words = listOf(
            word("So", 0, 200),
            word("you", 200, 350),
            word("know", 350, 550),
            word("the", 550, 700),
        )
        val out = engine.detectMultiWordFillers(words)
        assertEquals(1, out.size)
        val p = out.first()
        assertEquals("you know", p.matchedText)
        assertEquals(SilenceDetectionEngine.CutProposal.Reason.FILLER_WORD, p.reason)
    }

    @Test
    fun detectMultiWordFillers_longestMatchWinsOverShorter() {
        // "kind of" is in DEFAULT_MULTI_WORD_FILLERS; "a lot of" is too —
        // verify the longest one (4 tokens) wins over a 2-token prefix.
        val words = listOf(
            word("That", 0, 200),
            word("was", 200, 400),
            word("at", 400, 500),
            word("the", 500, 600),
            word("end", 600, 700),
            word("of", 700, 800),
            word("the", 800, 900),
            word("day", 900, 1100),
            word(".", 1100, 1200),
        )
        val out = engine.detectMultiWordFillers(words)
        assertEquals(1, out.size)
        assertEquals("at the end of the day", out.first().matchedText)
    }

    @Test
    fun detectMultiWordFillers_caseAndPunctuationInsensitive() {
        val words = listOf(
            word("I", 0, 100),
            word("Mean,", 100, 400),
        )
        val out = engine.detectMultiWordFillers(words)
        assertEquals(1, out.size)
        assertEquals("i mean", out.first().matchedText)
    }

    @Test
    fun detectMultiWordFillers_doesNotDoubleCountOverlappingMatches() {
        val words = listOf(
            word("you", 0, 200),
            word("know", 200, 400),
            word("you", 400, 600),
            word("know", 600, 800),
        )
        val out = engine.detectMultiWordFillers(words)
        // Two non-overlapping occurrences of "you know".
        assertEquals(2, out.size)
    }

    @Test
    fun detectMultiWordFillers_emptyInputReturnsEmpty() {
        assertTrue(engine.detectMultiWordFillers(emptyList()).isEmpty())
    }

    @Test
    fun detectMultiWordFillers_respectsDisabledFlag() {
        val config = SilenceDetectionEngine.AutoCutConfig(cutFillerWords = false)
        val out = engine.detectMultiWordFillers(
            words = listOf(word("you", 0, 200), word("know", 200, 400)),
            config = config,
        )
        assertTrue(out.isEmpty())
    }

    // --- C.2 follow-up: mergeProposals ---

    private fun cut(start: Long, end: Long, reason: SilenceDetectionEngine.CutProposal.Reason, text: String? = null) =
        SilenceDetectionEngine.CutProposal(start, end, reason, text)

    @Test
    fun mergeProposals_emptyReturnsEmpty() {
        assertTrue(engine.mergeProposals(emptyList()).isEmpty())
    }

    @Test
    fun mergeProposals_nonOverlappingPreserved() {
        val cuts = listOf(
            cut(0, 500, SilenceDetectionEngine.CutProposal.Reason.SILENCE),
            cut(2_000, 2_500, SilenceDetectionEngine.CutProposal.Reason.FILLER_WORD, "um"),
        )
        val merged = engine.mergeProposals(cuts, mergeGapMs = 80)
        assertEquals(2, merged.size)
    }

    @Test
    fun mergeProposals_overlappingMerged() {
        val cuts = listOf(
            cut(0, 1_000, SilenceDetectionEngine.CutProposal.Reason.SILENCE),
            cut(900, 1_500, SilenceDetectionEngine.CutProposal.Reason.SILENCE),
        )
        val merged = engine.mergeProposals(cuts, mergeGapMs = 0)
        assertEquals(1, merged.size)
        assertEquals(0L, merged.first().startMs)
        assertEquals(1_500L, merged.first().endMs)
    }

    @Test
    fun mergeProposals_smallGapMerged() {
        val cuts = listOf(
            cut(0, 1_000, SilenceDetectionEngine.CutProposal.Reason.SILENCE),
            cut(1_050, 1_500, SilenceDetectionEngine.CutProposal.Reason.SILENCE),
        )
        // Gap of 50 ms, mergeGap 80 ms → merge.
        val merged = engine.mergeProposals(cuts, mergeGapMs = 80)
        assertEquals(1, merged.size)
        assertEquals(1_500L, merged.first().endMs)
    }

    @Test
    fun mergeProposals_mixedReasonsCollapseToSilence() {
        val cuts = listOf(
            cut(0, 1_000, SilenceDetectionEngine.CutProposal.Reason.SILENCE),
            cut(1_010, 1_200, SilenceDetectionEngine.CutProposal.Reason.FILLER_WORD, "um"),
        )
        val merged = engine.mergeProposals(cuts, mergeGapMs = 80)
        assertEquals(1, merged.size)
        assertEquals(
            SilenceDetectionEngine.CutProposal.Reason.SILENCE,
            merged.first().reason,
        )
        assertEquals("um", merged.first().matchedText)
    }

    @Test
    fun mergeProposals_sortsOutOfOrderInput() {
        val cuts = listOf(
            cut(2_000, 2_500, SilenceDetectionEngine.CutProposal.Reason.FILLER_WORD, "uh"),
            cut(0, 500, SilenceDetectionEngine.CutProposal.Reason.SILENCE),
        )
        val merged = engine.mergeProposals(cuts)
        assertEquals(2, merged.size)
        assertEquals(0L, merged[0].startMs)
        assertEquals(2_000L, merged[1].startMs)
    }
}

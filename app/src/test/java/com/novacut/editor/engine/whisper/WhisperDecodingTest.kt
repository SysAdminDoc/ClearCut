package com.novacut.editor.engine.whisper

import com.novacut.editor.engine.whisper.WhisperDecoding.EOT
import com.novacut.editor.engine.whisper.WhisperDecoding.NO_SPEECH
import com.novacut.editor.engine.whisper.WhisperDecoding.NO_TIMESTAMPS
import com.novacut.editor.engine.whisper.WhisperDecoding.SOT
import com.novacut.editor.engine.whisper.WhisperDecoding.TIMESTAMP_BEGIN
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sin

class WhisperDecodingTest {

    private val vocabSize = TIMESTAMP_BEGIN + 1501
    private val the = 383
    private val quick = 2068

    private fun logits(vararg scores: Pair<Int, Float>) = FloatArray(vocabSize) { -100f }.also { array ->
        scores.forEach { (token, score) -> array[token] = score }
    }

    private fun ts(seconds: Double) = TIMESTAMP_BEGIN + (seconds * 50).roundToInt()

    @Test
    fun theFirstTokenIsATimestampWithinOneSecondEvenWhenPlainTextScoresHigher() {
        // This is what tiny.en really does with a bare <|startoftranscript|> prompt.
        val scores = logits(NO_TIMESTAMPS to 10f, the to 9f, ts(1.2) to 8f, ts(0.06) to 5f)

        assertEquals(ts(0.06), WhisperDecoding.nextToken(scores, emptyList()))
    }

    @Test
    fun noCaptionsIsNeverGeneratedSoDecodingStillOpensWithATimestamp() {
        val scores = logits(NO_SPEECH to 10f, ts(0.0) to 9f)

        assertEquals(ts(0.0), WhisperDecoding.nextToken(scores, emptyList()))
    }

    @Test
    fun specialTokensAreSuppressedMidSequence() {
        val englishLanguageToken = SOT + 1
        val scores = logits(SOT to 20f, englishLanguageToken to 19f, NO_SPEECH to 18f, quick to 3f)

        assertEquals(quick, WhisperDecoding.nextToken(scores, listOf(ts(0.0), the)))
    }

    @Test
    fun bracketsAndOtherNonSpeechSymbolsAreNeverWritten() {
        val openBracket = 58
        val spaceOpenBracket = 685
        val scores = logits(openBracket to 20f, spaceOpenBracket to 19f, the to 3f)

        assertEquals(the, WhisperDecoding.nextToken(scores, listOf(ts(0.0))))
    }

    @Test
    fun noSpeechProbabilityComesFromTheUntouchedFirstStep() {
        // e^10 / (e^10 + e^9), with everything else far below.
        val probability = WhisperDecoding.noSpeechProbability(logits(NO_SPEECH to 10f, ts(0.0) to 9f))

        assertEquals(1.0 / (1.0 + exp(-1.0)), probability, 1e-6)
    }

    @Test
    fun aChunkIsSilentOnlyWhenNoSpeechIsLikelyAndTheDecodeIsUnsure() {
        // Three tokens plus EOT averaging -0.5: confident quiet speech keeps its captions.
        assertFalse(WhisperDecoding.isSilence(noSpeechProbability = 0.7, sumLogProbability = -2.0, generatedCount = 3))
        // No-speech on top at 30% used to drop the chunk; the reference keeps it.
        assertFalse(WhisperDecoding.isSilence(noSpeechProbability = 0.3, sumLogProbability = -8.0, generatedCount = 3))
        assertTrue(WhisperDecoding.isSilence(noSpeechProbability = 0.7, sumLogProbability = -8.0, generatedCount = 3))
        // Exactly -1 per token still counts as unsure, as in the reference.
        assertTrue(WhisperDecoding.isSilence(noSpeechProbability = 0.61, sumLogProbability = -4.0, generatedCount = 3))
    }

    @Test
    fun onlyAudioBelowTheSpeechFloorCountsAsTooQuiet() {
        val silence = FloatArray(16_000)
        val roomTone = FloatArray(16_000) { if (it % 2 == 0) 0.0005f else -0.0005f }
        val whisper = FloatArray(16_000) { (0.01 * sin(it * 0.1)).toFloat() }
        val speechThenPadding = FloatArray(32_000).also { whisper.copyInto(it) }

        assertTrue(WhisperDecoding.isTooQuietForSpeech(silence))
        assertTrue(WhisperDecoding.isTooQuietForSpeech(roomTone))
        assertFalse(WhisperDecoding.isTooQuietForSpeech(whisper))
        // Only the samples that came from the clip count, not the zero padding after them.
        assertFalse(WhisperDecoding.isTooQuietForSpeech(speechThenPadding, length = 16_000))
        // Half a second of quiet speech in a silent 30 second chunk still counts as speech.
        val shortPhrase = FloatArray(480_000).also { whisper.copyInto(it, destinationOffset = 200_000, endIndex = 8_000) }
        assertFalse(WhisperDecoding.isTooQuietForSpeech(shortPhrase))
        assertTrue(WhisperDecoding.isTooQuietForSpeech(whisper, length = 0))
    }

    @Test
    fun noSpeechMustBeAboveSixtyPercentNotAtIt() {
        assertFalse(WhisperDecoding.isSilence(noSpeechProbability = 0.6, sumLogProbability = -8.0, generatedCount = 3))
    }

    @Test
    fun aChunkScoresItsTokensAfterFilteringSoSuppressedTokensDontCount() {
        // No-speech is 79% of the raw first step. Filtered, the two allowed opening
        // timestamps share the step at ln(0.5); scored on the raw logits the chosen one
        // would be -2.24 and the chunk would read as silence.
        val decode = WhisperDecoding.ChunkDecode()
        val first = decode.next(logits(NO_SPEECH to 12f, ts(0.0) to 10f, ts(0.02) to 10f), emptyList())

        assertTrue(first == ts(0.0) || first == ts(0.02))
        assertFalse(decode.isSilence(generatedCount = 1))
    }

    @Test
    fun aChunkTakesNoSpeechFromTheFirstStepBeforeFiltering() {
        // Ten opening timestamps tie, so the decode averages ln(0.1) / 2 = -1.15, and
        // no-speech is 85% of the raw step. Read after filtering it would be zero.
        val decode = WhisperDecoding.ChunkDecode()
        val tied = (0 until 10).map { ts(it * 0.02) to 10f }.toTypedArray()
        decode.next(logits(NO_SPEECH to 14f, *tied), emptyList())

        assertTrue(decode.isSilence(generatedCount = 1))
    }

    @Test
    fun logProbabilityIsTakenOverTheFilteredLogits() {
        val scores = logits(the to 1f, quick to 1f)

        assertEquals(ln(0.5), WhisperDecoding.logProbability(scores, the), 1e-3)
    }

    @Test
    fun anOpeningTimestampIsFollowedByText() {
        val scores = logits(ts(0.2) to 9f, the to 5f)

        assertEquals(the, WhisperDecoding.nextToken(scores, listOf(ts(0.0))))
    }

    @Test
    fun aTimestampNeverGoesBackwards() {
        val generated = listOf(ts(1.0), the)

        assertEquals(quick, WhisperDecoding.nextToken(logits(ts(0.2) to 20f, ts(1.2) to 1f, quick to 3f), generated))
        assertEquals(ts(1.2), WhisperDecoding.nextToken(logits(ts(0.2) to 20f, ts(1.2) to 5f, quick to 3f), generated))
    }

    @Test
    fun aClosingTimestampIsFollowedByATimestampOrTheEnd() {
        val scores = logits(the to 10f, ts(2.0) to 2f, EOT to 1f)

        assertEquals(ts(2.0), WhisperDecoding.nextToken(scores, listOf(ts(0.0), the, ts(2.0))))
    }

    @Test
    fun manyLikelyTimestampsTogetherOutweighTheBestTextToken() {
        // Ten timestamps at 0 sum to ln(10), about 2.30, which beats a text token at 2.0.
        val scores = logits(quick to 2f, *Array(10) { ts(2.0) + it to 0f })

        assertEquals(ts(2.0), WhisperDecoding.nextToken(scores, listOf(ts(0.0), the)))
    }

    @Test
    fun segmentsRunBetweenTimestampsAndTrailingTextRunsToTheChunkEnd() {
        val words = mapOf(the to " The", quick to " quick", 100 to " fox")
        val generated = listOf(ts(0.0), the, ts(1.4), ts(1.4), quick, ts(2.8), 100)

        val segments = WhisperDecoding.segments(generated, chunkOffsetMs = 30_000L, chunkDurationMs = 3_685L) { ids ->
            ids.joinToString("") { words.getValue(it) }
        }

        assertEquals(
            listOf(
                WhisperSegment(30_000L, 31_400L, "The"),
                WhisperSegment(31_400L, 32_800L, "quick"),
                WhisperSegment(32_800L, 33_685L, "fox"),
            ),
            segments,
        )
    }
}

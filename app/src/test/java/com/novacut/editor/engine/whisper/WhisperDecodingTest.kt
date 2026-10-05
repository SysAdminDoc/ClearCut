package com.novacut.editor.engine.whisper

import com.novacut.editor.engine.whisper.WhisperDecoding.EOT
import com.novacut.editor.engine.whisper.WhisperDecoding.NO_SPEECH
import com.novacut.editor.engine.whisper.WhisperDecoding.NO_TIMESTAMPS
import com.novacut.editor.engine.whisper.WhisperDecoding.TIMESTAMP_BEGIN
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.roundToInt

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
    fun noCaptionsAsTheTopFirstChoiceStopsDecoding() {
        val scores = logits(NO_SPEECH to 10f, ts(0.0) to 9f)

        assertEquals(EOT, WhisperDecoding.nextToken(scores, emptyList()))
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

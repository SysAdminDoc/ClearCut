package com.novacut.editor.engine.whisper

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Greedy decoding for Whisper's timestamp mode, after the reference decoder's
 * timestamp rules, and the step that turns generated tokens into timed segments.
 * Pure functions, so they can be checked without a model.
 *
 * Token IDs are the English-only tiny.en vocabulary's, from the pinned revision's
 * added_tokens.json and generation_config.json. The multilingual models number
 * everything past <|startoftranscript|> one higher.
 */
internal object WhisperDecoding {
    const val EOT = 50256             // <|endoftext|>
    const val SOT = 50257             // <|startoftranscript|>
    const val NO_SPEECH = 50361       // <|nocaptions|>
    const val NO_TIMESTAMPS = 50362   // <|notimestamps|>
    const val TIMESTAMP_BEGIN = 50363 // <|0.00|>; each step is 20 ms

    private const val MS_PER_TIMESTAMP = 20L
    private const val MAX_INITIAL_TIMESTAMP_STEPS = 50 // the first segment starts by 1.00 s

    /**
     * Non-speech symbols the reference never lets the model write (brackets, quotes,
     * music notes and the like), the text half of suppress_tokens in the pinned
     * revision's generation_config.json. Without them silence comes out as "[BLANK_AUDIO]".
     */
    private val NON_SPEECH_TOKENS = intArrayOf(
        1, 2, 7, 8, 9, 10, 14, 25, 26, 27, 28, 29, 31, 58, 59, 60, 61, 62, 63, 90, 91, 92, 93,
        357, 366, 438, 532, 685, 705, 796, 930, 1058, 1220, 1267, 1279, 1303, 1343, 1377, 1391,
        1635, 1782, 1875, 2162, 2361, 2488, 3467, 4008, 4211, 4600, 4808, 5299, 5855, 6329, 7203,
        9609, 9959, 10563, 10786, 11420, 11709, 11907, 13163, 13697, 13700, 14808, 15306, 16410,
        16791, 17992, 19203, 19510, 20724, 22305, 22935, 27007, 30109, 30420, 33409, 34949,
        40283, 40493, 40549, 47282, 49146,
    )

    // The reference transcribe() defaults: no_speech_threshold and logprob_threshold.
    private const val NO_SPEECH_THRESHOLD = 0.6
    private const val LOG_PROBABILITY_THRESHOLD = -1.0

    // -60 dBFS RMS. Whispered speech recorded on a phone sits around -40 dBFS.
    private const val SPEECH_FLOOR_RMS = 0.001
    private const val SPEECH_WINDOW_SAMPLES = 1_600 // 100 ms at 16 kHz

    /**
     * Picks the next token from the last position's [logits], given the tokens
     * generated after the prompt so far. Returns [EOT] to stop. Overwrites
     * suppressed entries of [logits].
     */
    fun nextToken(logits: FloatArray, generated: List<Int>): Int {
        if (logits.size <= TIMESTAMP_BEGIN) return argmax(logits)

        // Special tokens and non-speech symbols never appear in a transcript:
        // <|startoftranscript|>, the language and task tokens, <|startofprev|>,
        // <|nocaptions|>, brackets and music notes. Left alone,
        // tiny.en also answers <|notimestamps|> and plain text, which gives no
        // segment boundaries at all.
        suppress(logits, SOT, TIMESTAMP_BEGIN)
        for (token in NON_SPEECH_TOKENS) logits[token] = Float.NEGATIVE_INFINITY
        if (generated.isEmpty()) {
            suppress(logits, 0, TIMESTAMP_BEGIN)
            suppress(logits, TIMESTAMP_BEGIN + MAX_INITIAL_TIMESTAMP_STEPS + 1, logits.size)
            return argmax(logits)
        }

        val lastWasTimestamp = generated.last() >= TIMESTAMP_BEGIN
        val penultimateWasTimestamp = generated.size < 2 || generated[generated.size - 2] >= TIMESTAMP_BEGIN
        if (lastWasTimestamp) {
            if (penultimateWasTimestamp) {
                // An opening timestamp: text comes next.
                suppress(logits, TIMESTAMP_BEGIN, logits.size)
            } else {
                // A closing timestamp: the next segment opens, or decoding ends.
                suppress(logits, 0, EOT)
            }
        }
        val lastTimestamp = generated.lastOrNull { it >= TIMESTAMP_BEGIN }
        if (lastTimestamp != null) {
            // Timestamps never go backwards, and a segment never has zero length.
            val floor = if (lastWasTimestamp && !penultimateWasTimestamp) lastTimestamp else lastTimestamp + 1
            suppress(logits, TIMESTAMP_BEGIN, floor.coerceAtMost(logits.size))
        }
        // When all timestamps together outweigh the likeliest single other token,
        // the segment ends here.
        if (timestampMassWins(logits)) suppress(logits, 0, TIMESTAMP_BEGIN)
        return argmax(logits)
    }

    /** The model's no-speech probability, from the first step's logits before anything is suppressed. */
    fun noSpeechProbability(logits: FloatArray): Double {
        if (logits.size <= NO_SPEECH) return 0.0
        val total = logSumExp(logits)
        return if (total.isFinite()) exp(logits[NO_SPEECH] - total) else 0.0
    }

    /** Log probability of [token] under logits that [nextToken] already filtered. */
    fun logProbability(logits: FloatArray, token: Int): Double = logits[token] - logSumExp(logits)

    /**
     * The reference decoder's silence check. A chunk is dropped only when the model
     * puts no-speech above 60% and the tokens it decoded, the closing EOT included,
     * average under -1 nats. Quiet speech the model is sure of keeps its captions.
     */
    fun isSilence(noSpeechProbability: Double, sumLogProbability: Double, generatedCount: Int): Boolean =
        noSpeechProbability > NO_SPEECH_THRESHOLD &&
            sumLogProbability / (generatedCount + 1) <= LOG_PROBABILITY_THRESHOLD

    /**
     * One chunk's decode steps, in the reference order: the no-speech probability
     * from the first step's untouched logits, then the token, then its log
     * probability under the logits [nextToken] filtered.
     */
    class ChunkDecode {
        private var noSpeechProbability = 0.0
        private var sumLogProbability = 0.0

        fun next(logits: FloatArray, generated: List<Int>): Int {
            if (generated.isEmpty()) noSpeechProbability = noSpeechProbability(logits)
            val token = nextToken(logits, generated)
            sumLogProbability += logProbability(logits, token)
            return token
        }

        fun isSilence(generatedCount: Int): Boolean =
            isSilence(noSpeechProbability, sumLogProbability, generatedCount)
    }

    /**
     * Whether the first [length] samples of [audio] are too quiet to contain speech: no
     * 100 ms window reaches the speech floor, so a short quiet phrase in an otherwise
     * silent chunk still counts.
     */
    fun isTooQuietForSpeech(audio: FloatArray, length: Int = audio.size): Boolean {
        val count = length.coerceIn(0, audio.size)
        var start = 0
        while (start < count) {
            val end = minOf(start + SPEECH_WINDOW_SAMPLES, count)
            var sumSquares = 0.0
            for (i in start until end) sumSquares += audio[i].toDouble() * audio[i]
            if (sqrt(sumSquares / (end - start)) >= SPEECH_FLOOR_RMS) return false
            start = end
        }
        return true
    }

    /**
     * Text between an opening and a closing timestamp becomes one segment. Text the
     * model ends without a closing timestamp runs to [chunkDurationMs].
     */
    fun segments(
        generated: List<Int>,
        chunkOffsetMs: Long,
        chunkDurationMs: Long,
        decode: (List<Int>) -> String,
    ): List<WhisperSegment> {
        val segments = mutableListOf<WhisperSegment>()
        val text = mutableListOf<Int>()
        var startMs = 0L
        fun flush(endMs: Long) {
            if (text.isEmpty()) return
            val decoded = decode(text).trim()
            if (decoded.isNotBlank()) {
                segments += WhisperSegment(
                    startMs = chunkOffsetMs + startMs,
                    endMs = chunkOffsetMs + maxOf(endMs, startMs),
                    text = decoded,
                )
            }
            text.clear()
        }
        for (token in generated) {
            when {
                token >= TIMESTAMP_BEGIN -> {
                    val ms = (token - TIMESTAMP_BEGIN) * MS_PER_TIMESTAMP
                    flush(ms)
                    startMs = ms
                }
                token < EOT -> text += token
            }
        }
        flush(chunkDurationMs)
        return segments
    }

    private fun timestampMassWins(logits: FloatArray): Boolean {
        var maxOther = Float.NEGATIVE_INFINITY
        for (i in 0 until TIMESTAMP_BEGIN) if (logits[i] > maxOther) maxOther = logits[i]
        var maxTimestamp = Float.NEGATIVE_INFINITY
        for (i in TIMESTAMP_BEGIN until logits.size) if (logits[i] > maxTimestamp) maxTimestamp = logits[i]
        if (maxTimestamp == Float.NEGATIVE_INFINITY) return false
        if (maxOther == Float.NEGATIVE_INFINITY) return true
        var sum = 0.0
        for (i in TIMESTAMP_BEGIN until logits.size) sum += exp((logits[i] - maxTimestamp).toDouble())
        return maxTimestamp + ln(sum) > maxOther
    }

    private fun logSumExp(logits: FloatArray): Double {
        var max = Float.NEGATIVE_INFINITY
        for (value in logits) if (value > max) max = value
        if (max == Float.NEGATIVE_INFINITY) return Double.NEGATIVE_INFINITY
        var sum = 0.0
        for (value in logits) sum += exp((value - max).toDouble())
        return max + ln(sum)
    }

    private fun suppress(logits: FloatArray, from: Int, until: Int) {
        for (i in from until until) logits[i] = Float.NEGATIVE_INFINITY
    }

    private fun argmax(logits: FloatArray): Int {
        var best = 0
        var bestLogit = Float.NEGATIVE_INFINITY
        for (i in logits.indices) {
            if (logits[i] > bestLogit) {
                bestLogit = logits[i]
                best = i
            }
        }
        return best
    }
}

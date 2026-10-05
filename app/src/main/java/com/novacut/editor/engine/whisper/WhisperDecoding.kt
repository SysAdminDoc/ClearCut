package com.novacut.editor.engine.whisper

import kotlin.math.exp
import kotlin.math.ln

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
     * Picks the next token from the last position's [logits], given the tokens
     * generated after the prompt so far. Returns [EOT] to stop. Overwrites
     * suppressed entries of [logits].
     */
    fun nextToken(logits: FloatArray, generated: List<Int>): Int {
        if (logits.size <= TIMESTAMP_BEGIN) return argmax(logits)
        // The model's own no-speech verdict, taken before anything is suppressed.
        if (generated.isEmpty() && argmax(logits) == NO_SPEECH) return EOT

        // Left alone, tiny.en answers <|notimestamps|> and plain text, which gives
        // no segment boundaries at all.
        logits[NO_TIMESTAMPS] = Float.NEGATIVE_INFINITY
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

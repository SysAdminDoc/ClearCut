package com.novacut.editor.engine

import android.util.Log
import com.novacut.editor.engine.whisper.SherpaAsrEngine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Descript-style auto-cut proposer. See ROADMAP.md Tier C.2.
 *
 * Two detection passes:
 *  1. Silence: consecutive samples with |amplitude| below [silenceThreshold] for
 *     longer than [minSilenceMs]. Uses RMS from the existing waveform.
 *  2. Filler words: scans Whisper word-level timestamps for filler tokens
 *     (um, uh, like, you know, etc.) and proposes a cut range per occurrence.
 *
 * Returns cut proposals; UI confirms before the ViewModel applies them as
 * back-to-back `splitClipAt` + `deleteClip` commands so the result is fully
 * undo-able.
 */
@Singleton
class SilenceDetectionEngine @Inject constructor() {

    data class CutProposal(
        val startMs: Long,
        val endMs: Long,
        val reason: Reason,
        val matchedText: String? = null
    ) {
        enum class Reason { SILENCE, FILLER_WORD }

        val durationMs: Long get() = endMs - startMs
    }

    data class AutoCutConfig(
        /** RMS threshold below which a sample is considered silent. [0, 1]. */
        val silenceThreshold: Float = 0.02f,
        /** Minimum silence duration to propose a cut. Shorter silences are ignored. */
        val minSilenceMs: Long = 500L,
        /** Safety margin kept at both ends of a silence range (don't clip actual speech). */
        val paddingMs: Long = 80L,
        /** If true, propose cuts for filler words found in the transcript. */
        val cutFillerWords: Boolean = true,
        /** Filler tokens to match. Case-insensitive exact word match on Whisper output. */
        val fillerWords: Set<String> = DEFAULT_FILLERS
    ) {
        init {
            require(silenceThreshold in 0f..1f) { "silenceThreshold must be in [0, 1]" }
            require(minSilenceMs >= 50L) { "minSilenceMs must be >= 50" }
            require(paddingMs >= 0L) { "paddingMs must be >= 0" }
        }
    }

    /**
     * Scan an audio waveform for silent ranges.
     *
     * @param waveform Normalised amplitudes in [-1, 1].
     * @param sampleRate Hz.
     * @param config Thresholds and padding.
     */
    fun detectSilences(
        waveform: FloatArray,
        sampleRate: Int,
        config: AutoCutConfig = AutoCutConfig()
    ): List<CutProposal> {
        if (waveform.isEmpty() || sampleRate <= 0) return emptyList()
        // Stay in Long space until we know the value fits, so pathological thresholds
        // (e.g. a user-entered minSilenceMs in the hours at 48 kHz) can't silently
        // wrap Int.MAX_VALUE and surface as a negative-length run.
        val minSilenceSamplesLong = (config.minSilenceMs * sampleRate.toLong() / 1000L)
            .coerceIn(1L, waveform.size.toLong())
        val minSilenceSamples = minSilenceSamplesLong.toInt()
        val paddingSamples = (config.paddingMs * sampleRate.toLong() / 1000L)
            .coerceIn(0L, waveform.size.toLong())
            .toInt()
        val out = mutableListOf<CutProposal>()
        var runStart = -1
        var i = 0
        while (i < waveform.size) {
            val isSilent = kotlin.math.abs(waveform[i]) < config.silenceThreshold
            if (isSilent && runStart < 0) {
                runStart = i
            } else if (!isSilent && runStart >= 0) {
                val runLen = i - runStart
                if (runLen >= minSilenceSamples) {
                    val startSample = (runStart + paddingSamples).coerceAtMost(i)
                    val endSample = (i - paddingSamples).coerceAtLeast(startSample)
                    if (endSample > startSample) {
                        out += CutProposal(
                            startMs = startSample.toLong() * 1000L / sampleRate,
                            endMs = endSample.toLong() * 1000L / sampleRate,
                            reason = CutProposal.Reason.SILENCE
                        )
                    }
                }
                runStart = -1
            }
            i++
        }
        if (runStart >= 0 && waveform.size - runStart >= minSilenceSamples) {
            val startSample = (runStart + paddingSamples).coerceAtMost(waveform.size)
            val endSample = (waveform.size - paddingSamples).coerceAtLeast(startSample)
            if (endSample > startSample) {
                out += CutProposal(
                    startMs = startSample.toLong() * 1000L / sampleRate,
                    endMs = endSample.toLong() * 1000L / sampleRate,
                    reason = CutProposal.Reason.SILENCE
                )
            }
        }
        Log.d(TAG, "detectSilences: ${out.size} silences proposed")
        return out
    }

    /**
     * Scan Whisper word timestamps for filler tokens.
     *
     * Whisper emits punctuation and capitalisation; we lowercase and strip punctuation
     * before matching so "Um," / "um." / "UM" all hit the "um" entry in [config.fillerWords].
     */
    fun detectFillerWords(
        words: List<SherpaAsrEngine.WordTimestamp>,
        config: AutoCutConfig = AutoCutConfig()
    ): List<CutProposal> {
        if (!config.cutFillerWords) return emptyList()
        return words.mapNotNull { w ->
            val token = w.word.lowercase().trim { it.isWhitespace() || it in PUNCTUATION }
            if (token in config.fillerWords) {
                CutProposal(
                    startMs = (w.startTimeMs - config.paddingMs).coerceAtLeast(0L),
                    endMs = w.endTimeMs + config.paddingMs,
                    reason = CutProposal.Reason.FILLER_WORD,
                    matchedText = token
                )
            } else null
        }.also { Log.d(TAG, "detectFillerWords: ${it.size} fillers proposed") }
    }

    /**
     * Scan Whisper word timestamps for multi-word filler patterns ("you know",
     * "i mean", "sort of"). Uses a sliding window over adjacent
     * [SherpaAsrEngine.WordTimestamp] entries; each match collapses the
     * full window into a single [CutProposal] whose `matchedText` is the
     * lowercased joined phrase.
     */
    fun detectMultiWordFillers(
        words: List<SherpaAsrEngine.WordTimestamp>,
        config: AutoCutConfig = AutoCutConfig(),
        phrases: Set<String> = DEFAULT_MULTI_WORD_FILLERS,
    ): List<CutProposal> {
        if (!config.cutFillerWords || phrases.isEmpty() || words.isEmpty()) return emptyList()
        val normalizedPhrases = phrases.map { it.lowercase().split(" ").filter { t -> t.isNotEmpty() } }
        val maxLen = normalizedPhrases.maxOf { it.size }
        val out = mutableListOf<CutProposal>()
        val matched = BooleanArray(words.size)
        var i = 0
        while (i < words.size) {
            if (matched[i]) { i++; continue }
            var hit: List<String>? = null
            var hitLen = 0
            for (len in maxLen downTo 2) {
                if (i + len > words.size) continue
                val slice = (0 until len).map { offset ->
                    words[i + offset].word.lowercase().trim { it.isWhitespace() || it in PUNCTUATION }
                }
                val match = normalizedPhrases.firstOrNull { it == slice }
                if (match != null) {
                    hit = match
                    hitLen = len
                    break
                }
            }
            if (hit != null) {
                val first = words[i]
                val last = words[i + hitLen - 1]
                out += CutProposal(
                    startMs = (first.startTimeMs - config.paddingMs).coerceAtLeast(0L),
                    endMs = last.endTimeMs + config.paddingMs,
                    reason = CutProposal.Reason.FILLER_WORD,
                    matchedText = hit.joinToString(" "),
                )
                for (k in 0 until hitLen) matched[i + k] = true
                i += hitLen
            } else {
                i++
            }
        }
        Log.d(TAG, "detectMultiWordFillers: ${out.size} multi-word fillers proposed")
        return out
    }

    /**
     * Merge overlapping or near-adjacent cut proposals into a single deduped
     * list. Useful when [detectSilences], [detectFillerWords], and
     * [detectMultiWordFillers] all fire on the same range — the user should
     * see one combined cut, not three stacked.
     *
     * @param mergeGapMs proposals separated by less than this gap are fused
     *   into a single proposal. Default is the same 80 ms padding the
     *   AutoCutConfig uses, so cuts that were already conservatively
     *   over-padded merge cleanly.
     */
    fun mergeProposals(
        proposals: List<CutProposal>,
        mergeGapMs: Long = 80L,
    ): List<CutProposal> {
        require(mergeGapMs >= 0L) { "mergeGapMs must be >= 0: $mergeGapMs" }
        if (proposals.isEmpty()) return emptyList()
        val sorted = proposals.sortedBy { it.startMs }
        val out = mutableListOf<CutProposal>()
        var current = sorted.first()
        for (next in sorted.drop(1)) {
            if (next.startMs - current.endMs <= mergeGapMs) {
                // Merge — keep the earliest start and latest end; collapse
                // the reason to SILENCE when mixed (silence has the clearer
                // UX) and join matched text.
                val mergedReason = if (current.reason == next.reason)
                    current.reason
                else
                    CutProposal.Reason.SILENCE
                val mergedText = listOfNotNull(current.matchedText, next.matchedText)
                    .filter { it.isNotBlank() }
                    .joinToString(", ")
                    .ifBlank { null }
                current = CutProposal(
                    startMs = current.startMs,
                    endMs = maxOf(current.endMs, next.endMs),
                    reason = mergedReason,
                    matchedText = mergedText,
                )
            } else {
                out += current
                current = next
            }
        }
        out += current
        return out
    }

    companion object {
        private const val TAG = "SilenceDetect"
        private const val PUNCTUATION = ".,!?;:-\"'()"

        /**
         * Single-token filler set.
         */
        val DEFAULT_FILLERS: Set<String> = setOf(
            "um", "uh", "er", "ah", "hmm", "mhm", "like",
            "basically", "literally", "right", "so", "well", "actually"
        )

        /**
         * Multi-word filler phrase set consumed by [detectMultiWordFillers].
         * All entries must be lowercased and space-separated. Order within
         * the set doesn't matter — the matcher checks the longest-first.
         */
        val DEFAULT_MULTI_WORD_FILLERS: Set<String> = setOf(
            "you know",
            "i mean",
            "sort of",
            "kind of",
            "a lot of",
            "at the end of the day",
        )
    }
}

package com.novacut.editor.engine

import android.util.Log
import com.novacut.editor.model.Clip
import com.novacut.editor.model.WordTimestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Descript/CapCut-style text-based editing.
 *
 * Translates a user-selected set of transcript word indices into source-time
 * cut ranges on the target clip. All work is pure: the engine does not mutate
 * clips or the timeline — callers apply the ranges via the clip-editing path
 * (V369Delegate ripples them onto the track).
 *
 * Filler detection supports both single-word ("um", "like") and multi-word
 * phrases ("you know", "i mean") — Whisper tokenises "you know" as two
 * separate `WordTimestamp`s, so a single-token match alone would miss the
 * majority of real filler phrases.
 */
@Singleton
class TextBasedEditEngine @Inject constructor() {

    data class CutRange(val startSrcMs: Long, val endSrcMs: Long)

    suspend fun computeCutRanges(
        clip: Clip,
        words: List<WordTimestamp>,
        removedIndices: Set<Int>
    ): List<CutRange> = withContext(Dispatchers.Default) {
        if (removedIndices.isEmpty() || words.isEmpty()) return@withContext emptyList()
        val trimStart = clip.trimStartMs
        val trimEnd = clip.trimEndMs
        if (trimEnd <= trimStart) return@withContext emptyList()

        val raw = removedIndices
            .asSequence()
            .mapNotNull { idx -> words.getOrNull(idx) }
            .map { w ->
                val s = w.startMs.coerceIn(trimStart, trimEnd)
                val e = w.endMs.coerceIn(trimStart, trimEnd)
                s to e
            }
            .filter { (s, e) -> e > s }
            .sortedBy { it.first }
            .toList()

        // Coalesce contiguous or near-contiguous (<120 ms gap) removals so we
        // do not spam the undo stack with one split per word.
        val merged = mutableListOf<Pair<Long, Long>>()
        for ((s, e) in raw) {
            val last = merged.lastOrNull()
            if (last != null && s - last.second <= COALESCE_GAP_MS) {
                merged[merged.size - 1] = last.first to maxOf(last.second, e)
            } else {
                merged += s to e
            }
        }
        merged.map { (s, e) -> CutRange(s, e) }
    }

    /**
     * Default threshold-based filler detection. Returns every word index that
     * is a single-token filler OR the first word of a matched bigram filler
     * (in which case the bigram's second word index is also returned, so the
     * whole phrase is selected for deletion).
     */
    fun fillerWordIndices(words: List<WordTimestamp>): Set<Int> {
        val out = HashSet<Int>()
        words.forEachIndexed { i, w ->
            if (w.text.normalised() in UNIGRAMS) out += i
        }
        for (i in 0 until words.size - 1) {
            val bigram = "${words[i].text.normalised()} ${words[i + 1].text.normalised()}"
            if (bigram in BIGRAMS) { out += i; out += i + 1 }
        }
        Log.d(TAG, "detected ${out.size} filler indices")
        return out
    }

    private fun String.normalised(): String =
        lowercase().trim().trim { it in PUNCT }

    companion object {
        private const val TAG = "TextBasedEditEngine"
        private const val COALESCE_GAP_MS = 120L
        private val PUNCT = ",.!?;:\"'".toCharArray().toSet()
        private val UNIGRAMS = setOf(
            "um", "uh", "er", "ah", "hmm", "mm", "mhm",
            "like", "literally", "basically", "actually", "honestly",
            "anyway", "anyways", "okay", "ok", "right", "so"
        )
        private val BIGRAMS = setOf(
            "you know", "i mean", "sort of", "kind of", "and stuff",
            "or something", "or whatever"
        )
    }
}

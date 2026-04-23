package com.novacut.editor.engine

import android.content.Context
import android.net.Uri
import android.util.Log
import com.novacut.editor.model.ChapterMarker
import com.novacut.editor.model.WordTimestamp
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Auto-chapter generation from a Whisper transcript using a TextTiling-lite
 * heuristic.
 *
 * Algorithm:
 *   1. Slide a 24-word window over the transcript in 6-word steps.
 *   2. For every position, score the cosine similarity of the bag-of-words
 *      between the current window and the next window.
 *   3. Local minima of that similarity signal mark topic shifts.
 *   4. Enforce a minimum-gap constraint so we do not emit chapters every few
 *      seconds on high-information transcripts.
 *
 * Ships without any on-device language model — a sentence-BERT upgrade
 * (all-MiniLM-L6-v2 ONNX, ~23 MB) plugs into the same interface when we are
 * ready to trade install-size for accuracy. The `useSemanticEmbeddings` flag
 * is reserved for that future path.
 */
@Singleton
class AutoChapterEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {

    data class ChapterCandidate(
        val timeMs: Long,
        val title: String,
        val thumbnailUri: Uri? = null
    )

    suspend fun detect(
        words: List<WordTimestamp>,
        minChapterMs: Long = DEFAULT_MIN_CHAPTER_MS,
        maxChapters: Int = DEFAULT_MAX_CHAPTERS
    ): List<ChapterCandidate> = withContext(Dispatchers.Default) {
        if (words.size < MIN_WORDS) return@withContext emptyList()
        val windowSize = WINDOW_SIZE
        val step = STEP_SIZE
        val maxSignalIdx = (words.size - windowSize * 2) / step
        if (maxSignalIdx < 1) return@withContext emptyList()
        val signal = FloatArray(maxSignalIdx)
        for (i in signal.indices) {
            val a = bag(words, i * step, i * step + windowSize)
            val b = bag(words, i * step + windowSize, i * step + windowSize * 2)
            signal[i] = cosine(a, b)
        }
        val lows = localMinima(signal, threshold = LOCAL_MIN_THRESHOLD)
            .map { (it * step + windowSize).coerceAtMost(words.lastIndex) }

        val out = mutableListOf<ChapterCandidate>()
        val usedTitles = HashSet<String>()
        for (idx in lows) {
            val word = words.getOrNull(idx) ?: continue
            if (out.isNotEmpty() && word.startMs - out.last().timeMs < minChapterMs) continue
            val rawTitle = words
                .drop(idx)
                .take(TITLE_WORDS)
                .joinToString(" ") { it.text }
                .trim()
                .take(TITLE_MAX_CHARS)
            val title = rawTitle.ifBlank { "Chapter ${out.size + 1}" }
            // Skip duplicate titles — they arise on repetitive transcripts
            // and look embarrassing in a YouTube description.
            if (title.lowercase() in usedTitles) continue
            usedTitles += title.lowercase()
            out += ChapterCandidate(word.startMs, title)
            if (out.size >= maxChapters) break
        }
        out.also { Log.d(TAG, "detected ${it.size} chapters from ${words.size} words") }
    }

    /** Format chapters as a YouTube-description-ready clipboard block. */
    fun formatYouTubeClipboard(chapters: List<ChapterMarker>): String = buildString {
        appendLine("00:00 Intro")
        for (c in chapters) appendLine("${formatTs(c.timeMs)} ${c.title}")
    }.trimEnd()

    private fun bag(words: List<WordTimestamp>, from: Int, to: Int): Map<String, Int> {
        val map = HashMap<String, Int>()
        val end = to.coerceAtMost(words.size)
        val start = from.coerceAtLeast(0)
        for (i in start until end) {
            val t = words[i].text.lowercase().trim().trimEnd(',', '.', '!', '?')
            if (t.length < 3) continue
            map.merge(t, 1) { x, y -> x + y }
        }
        return map
    }

    private fun cosine(a: Map<String, Int>, b: Map<String, Int>): Float {
        if (a.isEmpty() || b.isEmpty()) return 0f
        var dot = 0.0; var na = 0.0; var nb = 0.0
        for ((k, v) in a) { dot += v * (b[k] ?: 0); na += v.toDouble() * v }
        for (v in b.values) nb += v.toDouble() * v
        val denom = sqrt(na) * sqrt(nb)
        return if (denom > 0.0) (dot / denom).toFloat() else 0f
    }

    private fun localMinima(sig: FloatArray, threshold: Float): List<Int> {
        if (sig.size < 3) return emptyList()
        val out = mutableListOf<Int>()
        for (i in 1 until sig.size - 1) {
            if (sig[i] < sig[i - 1] && sig[i] < sig[i + 1] && sig[i] < threshold) out += i
        }
        return out
    }

    private fun formatTs(ms: Long): String {
        val s = max(0L, ms) / 1000
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
    }

    companion object {
        private const val TAG = "AutoChapterEngine"
        private const val WINDOW_SIZE = 24
        private const val STEP_SIZE = 6
        private const val MIN_WORDS = 20
        private const val LOCAL_MIN_THRESHOLD = 0.35f
        private const val TITLE_WORDS = 8
        private const val TITLE_MAX_CHARS = 48
        private const val DEFAULT_MIN_CHAPTER_MS = 20_000L
        private const val DEFAULT_MAX_CHAPTERS = 12
    }
}

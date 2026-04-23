package com.novacut.editor.engine

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.PriorityQueue
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * AI-ranked thumbnail picker for export (YouTube "Auto Cover" equivalent).
 *
 * Scoring heuristic (normalised to 0..1 each, weighted sum):
 *   0.35 × Laplacian-variance sharpness — avoids motion-blurred frames
 *   0.25 × rule-of-thirds alignment of the salient-edge centroid
 *   0.40 × skin-tone coverage as a face-prominence proxy
 *
 * Implementation notes:
 *   * A bounded min-heap keeps only the current top-N candidates in memory.
 *     Every frame that is evicted from the heap has its bitmap recycled on
 *     the IO dispatcher, so memory stays O(topN) regardless of clip length.
 *   * Cooperative cancellation via `ensureActive()` between samples — the
 *     ViewModel can cancel the job by cancelling its scope without waiting
 *     for the whole clip.
 */
@Singleton
class AiThumbnailEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {

    data class Candidate(
        val timeMs: Long,
        val score: Float,
        val bitmap: Bitmap? = null
    )

    suspend fun score(
        uri: Uri,
        durationMs: Long,
        stepMs: Long = 1000L,
        topN: Int = 5
    ): List<Candidate> = withContext(Dispatchers.IO) {
        if (durationMs <= 0L || topN <= 0) return@withContext emptyList()
        val retriever = MediaMetadataRetriever()
        // Min-heap so the lowest-scored candidate is always at head; when a
        // better frame arrives we poll() the head, recycle its bitmap, offer
        // the new one. Capacity topN guarantees bounded memory.
        val heap = PriorityQueue<Candidate>(topN) { a, b -> a.score.compareTo(b.score) }
        try {
            retriever.setDataSource(context, uri)
            var t = 0L
            coroutineScope {
                while (t < durationMs) {
                    ensureActive()
                    val frame = try {
                        retriever.getFrameAtTime(t * 1000L, MediaMetadataRetriever.OPTION_CLOSEST)
                    } catch (e: Exception) { Log.w(TAG, "getFrameAtTime@${t}ms", e); null }
                    if (frame != null) {
                        val s = scoreFrame(frame)
                        if (heap.size < topN) {
                            heap.offer(Candidate(t, s, frame))
                        } else {
                            val head = heap.peek()
                            if (head != null && s > head.score) {
                                val evicted = heap.poll()
                                evicted?.bitmap?.recycleSafely()
                                heap.offer(Candidate(t, s, frame))
                            } else {
                                // Not good enough — release immediately.
                                frame.recycleSafely()
                            }
                        }
                    }
                    t += stepMs
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "thumbnail scoring failed", e)
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
        heap.toList().sortedByDescending { it.score }
    }

    /**
     * Write the candidate bitmap as a JPEG to `outputFile`. The bitmap is
     * **not** recycled — the caller retains ownership. The earlier auto-recycle
     * behaviour was a latent crash: the same Bitmap instance is referenced
     * by `Candidate.bitmap` and rendered by the v3.69 panel via
     * `bmp.asImageBitmap()`, so recycling here would crash the Compose renderer
     * on the next frame.
     */
    suspend fun saveThumbnail(bitmap: Bitmap, outputFile: File, quality: Int = 92): Boolean =
        withContext(Dispatchers.IO) {
            if (bitmap.isRecycled) {
                Log.w(TAG, "saveThumbnail called with already-recycled bitmap")
                return@withContext false
            }
            try {
                outputFile.parentFile?.mkdirs()
                FileOutputStream(outputFile).use { os ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(1, 100), os)
                }
                true
            } catch (e: Exception) {
                Log.w(TAG, "saveThumbnail failed", e); false
            }
        }

    /**
     * Explicitly dispose of a list of candidates when the caller is done
     * rendering them. Safe to call multiple times — already-recycled bitmaps
     * are ignored.
     */
    fun disposeCandidates(candidates: List<Candidate>) {
        for (c in candidates) c.bitmap?.recycleSafely()
    }

    private fun scoreFrame(bmp: Bitmap): Float {
        val w = (bmp.width / 6).coerceAtLeast(32)
        val h = (bmp.height / 6).coerceAtLeast(18)
        val scaled = if (w == bmp.width && h == bmp.height) bmp
            else Bitmap.createScaledBitmap(bmp, w, h, true)
        val px = IntArray(w * h)
        scaled.getPixels(px, 0, w, 0, 0, w, h)
        if (scaled !== bmp) scaled.recycleSafely()

        // Single pass: grayscale, skin-tone fraction, edge centroid.
        val gray = FloatArray(px.size)
        var skin = 0
        var cx = 0.0; var cy = 0.0; var cw = 0.0
        for (i in px.indices) {
            val p = px[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            gray[i] = 0.2126f * r + 0.7152f * g + 0.0722f * b
            if (isSkin(r, g, b)) skin++
            val ix = i % w; val iy = i / w
            val edge = abs(r - g) + abs(g - b) + abs(r - b)
            cx += ix.toDouble() * edge
            cy += iy.toDouble() * edge
            cw += edge
        }
        val n = px.size.toFloat()
        val skinFrac = skin / n

        // Laplacian variance → sharpness. Two-pass mean/variance keeps the
        // numbers stable even on flat frames.
        var mean = 0.0; var count = 0; var sumSq = 0.0
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val idx = y * w + x
                val lap = -4 * gray[idx] + gray[idx - 1] + gray[idx + 1] +
                    gray[idx - w] + gray[idx + w]
                mean += lap.toDouble(); count++
            }
        }
        val m = if (count > 0) mean / count else 0.0
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val idx = y * w + x
                val lap = -4 * gray[idx] + gray[idx - 1] + gray[idx + 1] +
                    gray[idx - w] + gray[idx + w]
                val d = lap - m; sumSq += d * d
            }
        }
        val variance = if (count > 0) (sumSq / count).toFloat() else 0f
        val sharpness = (variance / 800f).coerceIn(0f, 1f)

        // Rule-of-thirds: reward salient-mass centroids near 1/3 or 2/3.
        val salientX = if (cw > 0) (cx / cw).toFloat() / w.toFloat() else 0.5f
        val salientY = if (cw > 0) (cy / cw).toFloat() / h.toFloat() else 0.5f
        val thirdsX = 1f - minOf(abs(salientX - 0.33f), abs(salientX - 0.66f)) * 2f
        val thirdsY = 1f - minOf(abs(salientY - 0.33f), abs(salientY - 0.66f)) * 2f
        val thirds = (thirdsX.coerceIn(0f, 1f) + thirdsY.coerceIn(0f, 1f)) * 0.5f

        return 0.35f * sharpness + 0.25f * thirds + 0.40f * skinFrac.coerceIn(0f, 1f)
    }

    private fun isSkin(r: Int, g: Int, b: Int): Boolean {
        if (r < 95 || g < 40 || b < 20) return false
        if (abs(r - g) < 15) return false
        return r > g && r > b
    }

    private fun Bitmap.recycleSafely() {
        try { if (!isRecycled) recycle() } catch (_: Exception) { /* already gone */ }
    }

    companion object { private const val TAG = "AiThumbnailEngine" }
}

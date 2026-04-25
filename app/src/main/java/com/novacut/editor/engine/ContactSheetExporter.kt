package com.novacut.editor.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.net.Uri
import android.util.Log
import com.novacut.editor.model.Clip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Renders a "contact sheet" PNG — one labeled thumbnail per clip, arranged in a grid.
 * Useful for review/approval workflows (editors send one PNG instead of a 2 GB mp4)
 * and social-media teasers. Thumbnails come from each clip's midpoint via
 * `VideoEngine.extractThumbnail`, so the existing thumbnail cache accelerates
 * repeated contact-sheet exports for the same project.
 *
 * Layout:
 *   - Grid is columns-wide, ceil(clips/columns) rows tall.
 *   - Each cell: thumbnail on top, two-line caption below ("clip 3", "0:04").
 *   - 16 px outer margin, 12 px inter-cell gap, 28 px caption strip per cell.
 *   - Dark Catppuccin-Mocha background (#1E1E2E) with Text colour for captions.
 *
 * Intentionally single-file PNG — no multi-page, no custom layouts. Those can be
 * layered on if users ask.
 */
object ContactSheetExporter {

    private const val TAG = "ContactSheetExporter"
    private const val THUMB_W = 320
    private const val THUMB_H = 180
    private const val OUTER_PAD = 16
    private const val GAP = 12
    private const val CAPTION_H = 28
    private const val CAPTION_PAD = 4
    private const val BG_COLOR = 0xFF1E1E2E.toInt()
    private const val TEXT_COLOR = 0xFFCDD6F4.toInt()
    private const val SUBTEXT_COLOR = 0xFFA6ADC8.toInt()

    /**
     * Write a contact-sheet PNG for the given clips to `outputFile`.
     * Returns true on success.
     *
     * `extractThumb` is injected so the caller can wire in VideoEngine without
     * this module having to depend on it directly.
     */
    suspend fun export(
        clips: List<Clip>,
        columns: Int,
        outputFile: File,
        extractThumb: (Uri, Long, Int, Int) -> Bitmap?,
        onProgress: (Float) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        if (clips.isEmpty()) {
            Log.w(TAG, "No clips supplied")
            return@withContext false
        }
        val cols = columns.coerceIn(1, 8)
        val rows = (clips.size + cols - 1) / cols
        val cellW = THUMB_W
        val cellH = THUMB_H + CAPTION_H
        val sheetW = OUTER_PAD * 2 + cols * cellW + (cols - 1) * GAP
        val sheetH = OUTER_PAD * 2 + rows * cellH + (rows - 1) * GAP

        val sheet = try {
            Bitmap.createBitmap(sheetW, sheetH, Bitmap.Config.ARGB_8888)
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OOM allocating ${sheetW}x${sheetH} contact sheet", e)
            return@withContext false
        }

        var partialFile: File? = null

        // Canvas and Paint objects are initialised inside the try block so that any
        // OOM thrown by their native allocations is covered by the finally that recycles
        // `sheet`.  Leaving them outside (as was the case before) meant a native OOM
        // during Paint construction leaked the bitmap before the finally scope began.
        try {
            val canvas = Canvas(sheet)
            canvas.drawColor(BG_COLOR)

            val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = TEXT_COLOR
                textSize = 13f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            val durationPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = SUBTEXT_COLOR
                textSize = 11f
                typeface = Typeface.SANS_SERIF
            }
            val placeholderPaint = Paint().apply { color = 0xFF313244.toInt() }

            clips.forEachIndexed { index, clip ->
                ensureActive()
                val col = index % cols
                val row = index / cols
                val cellX = OUTER_PAD + col * (cellW + GAP)
                val cellY = OUTER_PAD + row * (cellH + GAP)

                // Thumbnail from the clip's timeline midpoint mapped back to source
                // time. Using `clip.timelineOffsetToSourceMs(durationMs/2)` respects
                // speedCurve — e.g. a ramp from 0.5x→2x puts the visual midpoint
                // nowhere near the arithmetic trim midpoint, so naive
                // `trimStart + trimRange/2` would grab a misleading frame.
                //
                // Note: VideoEngine.extractThumbnail caches the returned bitmap in a
                // bounded LRU and returns the cached instance. We DO NOT recycle it
                // here — doing so would invalidate the cache for subsequent calls.
                // The cache owns the bitmap's lifecycle.
                val timelineDurationMs = clip.durationMs.coerceAtLeast(1L)
                val midSourceMs = clip.timelineOffsetToSourceMs(timelineDurationMs / 2)
                val thumb = try {
                    extractThumb(clip.sourceUri, midSourceMs * 1000, THUMB_W, THUMB_H)
                } catch (e: Exception) {
                    Log.w(TAG, "Thumbnail extraction failed for clip $index", e)
                    null
                }

                val thumbRect = Rect(cellX, cellY, cellX + cellW, cellY + THUMB_H)
                if (thumb != null && !thumb.isRecycled) {
                    canvas.drawBitmap(thumb, null, thumbRect, null)
                } else {
                    canvas.drawRect(thumbRect, placeholderPaint)
                }

                // Caption: short label + duration, left-aligned inside the caption strip.
                val label = clipLabel(clip, index)
                val duration = formatDuration(clip.durationMs)
                val textX = cellX.toFloat() + CAPTION_PAD
                val labelY = cellY + THUMB_H + 14f
                val durationY = cellY + THUMB_H + 26f
                canvas.drawText(label, textX, labelY, captionPaint)
                canvas.drawText(duration, textX, durationY, durationPaint)

                onProgress((index + 1).toFloat() / clips.size * 0.9f)
            }

            val outputFiles = createStillImageOutputFiles(outputFile)
            partialFile = outputFiles.partialFile
            partialFile.outputStream().buffered().use { out ->
                if (!sheet.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                    throw IllegalStateException("Contact sheet encoder returned no data")
                }
            }
            finalizeStillImageOutputFile(partialFile, outputFiles.outputFile)
                ?: throw IllegalStateException("Contact sheet output was empty")
            onProgress(1f)
            true
        } catch (t: Throwable) {
            // Catch Throwable (not just Exception) so that an OutOfMemoryError raised by
            // `sheet.compress` on a very large grid -- the PNG encoder allocates its own
            // native buffers -- still removes the in-progress partial file. The final PNG
            // is only replaced after a non-empty partial is complete, so a failed export
            // cannot destroy an earlier valid contact sheet at the same path. Coroutine
            // cancellation must still propagate -- rethrow CancellationException before logging.
            if (t is kotlinx.coroutines.CancellationException) {
                cleanupStillImageOutputFile(partialFile)
                throw t
            }
            Log.e(TAG, "Contact sheet render failed", t)
            cleanupStillImageOutputFile(partialFile)
            false
        } finally {
            if (!sheet.isRecycled) sheet.recycle()
        }
    }

    private fun clipLabel(clip: Clip, index: Int): String {
        // Prefer the clip's source filename (last path segment) when available,
        // fall back to a numeric label. Truncate at 24 chars so the caption fits
        // within the thumbnail width at 13 sp.
        val raw = clip.sourceUri.lastPathSegment
            ?.substringAfterLast('/')
            ?.substringBeforeLast('.')
            ?.takeIf { it.isNotBlank() }
            ?: "Clip ${index + 1}"
        return if (raw.length > 24) raw.take(23) + "…" else raw
    }

    private fun formatDuration(ms: Long): String {
        val totalSec = (ms / 1000).coerceAtLeast(0L)
        val m = totalSec / 60
        val s = totalSec % 60
        return "%d:%02d".format(m, s)
    }

}

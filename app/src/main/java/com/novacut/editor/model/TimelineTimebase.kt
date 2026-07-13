package com.novacut.editor.model

import java.util.Locale

data class TimelineTimebase(
    val numerator: Int,
    val denominator: Int = 1,
) {
    init {
        require(numerator in 1..240_000) { "Frame-rate numerator must be positive" }
        require(denominator in 1..10_000) { "Frame-rate denominator must be positive" }
    }

    val nominalFramesPerSecond: Int
        get() = ((numerator + denominator / 2) / denominator).coerceAtLeast(1)

    val frameRateLabel: String
        get() {
            if (denominator == 1) return "$numerator fps"
            val rate = numerator.toDouble() / denominator.toDouble()
            return String.format(Locale.US, "%.3f", rate).trimEnd('0').trimEnd('.') + " fps"
        }

    fun frameIndexAt(timeMs: Long): Long {
        val safeMs = timeMs.coerceAtLeast(0L)
        return divideRounded(safeMs * numerator.toLong(), 1_000L * denominator)
    }

    fun frameIndexAtOrBefore(timeMs: Long): Long {
        val safeMs = timeMs.coerceAtLeast(0L)
        return (safeMs * numerator.toLong()) / (1_000L * denominator)
    }

    fun frameIndexAtOrAfter(timeMs: Long): Long {
        val safeMs = timeMs.coerceAtLeast(0L)
        val divisor = 1_000L * denominator
        return (safeMs * numerator.toLong() + divisor - 1L) / divisor
    }

    fun timeMsAt(frameIndex: Long): Long {
        val safeFrame = frameIndex.coerceAtLeast(0L)
        return divideRounded(safeFrame * 1_000L * denominator, numerator.toLong())
    }

    fun snapMs(timeMs: Long): Long = timeMsAt(frameIndexAt(timeMs))

    fun addFrames(timeMs: Long, deltaFrames: Long): Long =
        timeMsAt((frameIndexAt(timeMs) + deltaFrames).coerceAtLeast(0L))

    fun formatTimecode(timeMs: Long): String {
        val totalFrames = frameIndexAt(timeMs)
        val fps = nominalFramesPerSecond.toLong()
        val totalSeconds = totalFrames / fps
        val frames = totalFrames % fps
        val seconds = totalSeconds % 60L
        val minutes = (totalSeconds / 60L) % 60L
        val hours = totalSeconds / 3_600L
        return String.format(Locale.US, "%02d:%02d:%02d:%02d", hours, minutes, seconds, frames)
    }

    private fun divideRounded(numerator: Long, denominator: Long): Long =
        (numerator + denominator / 2L) / denominator

    companion object {
        val NTSC_23_976 = TimelineTimebase(24_000, 1_001)
        val NTSC_29_97 = TimelineTimebase(30_000, 1_001)
        val NTSC_59_94 = TimelineTimebase(60_000, 1_001)
    }
}

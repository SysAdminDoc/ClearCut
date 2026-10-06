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

    fun frameIndexAt(timeMs: Long): Long = framesAt(timeMs, roundingOffset = 500L * denominator)

    fun frameIndexAtOrBefore(timeMs: Long): Long = framesAt(timeMs, roundingOffset = 0L)

    fun frameIndexAtOrAfter(timeMs: Long): Long = framesAt(timeMs, roundingOffset = 1_000L * denominator - 1L)

    /**
     * (timeMs × numerator + roundingOffset) / (1000 × denominator), without forming the
     * product: callers pass Long.MAX_VALUE as "no limit", and the product wrapped negative.
     * The whole-divisor part scales exactly on its own; past Long's range it saturates.
     */
    private fun framesAt(timeMs: Long, roundingOffset: Long): Long {
        val safeMs = timeMs.coerceAtLeast(0L)
        val divisor = 1_000L * denominator
        val whole = safeMs / divisor
        val rest = safeMs % divisor
        if (whole > (Long.MAX_VALUE - numerator) / numerator) return Long.MAX_VALUE
        return whole * numerator + (rest * numerator + roundingOffset) / divisor
    }

    fun timeMsAt(frameIndex: Long): Long {
        val safeFrame = frameIndex.coerceAtLeast(0L)
        return divideRounded(safeFrame * 1_000L * denominator, numerator.toLong())
    }

    fun snapMs(timeMs: Long): Long = timeMsAt(frameIndexAt(timeMs))

    fun addFrames(timeMs: Long, deltaFrames: Long): Long =
        timeMsAt((frameIndexAt(timeMs) + deltaFrames).coerceAtLeast(0L))

    fun formatTimecode(timeMs: Long): String {
        // Derive HH:MM:SS from real elapsed time, not from frame-count / rounded
        // nominal fps. For fractional rates (29.97 = 30000/1001) counting seconds
        // as totalFrames/30 drifts from wall clock (~3.6s per hour). Using real
        // time keeps the clock accurate; the frame field is the frame index
        // within the current second. Integer rates are unaffected.
        val safeMs = timeMs.coerceAtLeast(0L)
        val totalFrames = frameIndexAt(safeMs)
        val fps = nominalFramesPerSecond.toLong()
        val totalSeconds = safeMs / 1_000L
        val seconds = totalSeconds % 60L
        val minutes = (totalSeconds / 60L) % 60L
        val hours = totalSeconds / 3_600L
        val framesAtSecondStart = frameIndexAt(totalSeconds * 1_000L)
        val frames = (totalFrames - framesAtSecondStart).coerceIn(0L, fps - 1L)
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

package com.novacut.editor.model

import androidx.compose.runtime.Immutable

/**
 * A user-selected, frame-quantized export interval. The end frame is
 * exclusive, which makes adjacent review ranges lossless and avoids exporting
 * the boundary frame twice.
 */
@Immutable
data class TimelineExportRange(
    val startFrame: Long? = null,
    val endFrameExclusive: Long? = null,
) {
    init {
        require(startFrame == null || startFrame >= 0L) {
            "startFrame must be non-negative"
        }
        require(endFrameExclusive == null || endFrameExclusive >= 0L) {
            "endFrameExclusive must be non-negative"
        }
    }

    /** Resolve both frame bounds against the project's authoritative timebase. */
    fun resolve(
        timebase: TimelineTimebase,
        totalDurationMs: Long,
    ): ResolvedTimelineExportRange? {
        val start = startFrame ?: return null
        val end = endFrameExclusive ?: return null
        val safeDuration = totalDurationMs.coerceAtLeast(0L)
        if (safeDuration <= 0L || end <= start) return null

        val maxFrameExclusive = timebase.frameIndexAtOrAfter(safeDuration)
        if (start >= maxFrameExclusive || end > maxFrameExclusive) return null

        val startMs = timebase.timeMsAt(start)
        val endMs = timebase.timeMsAt(end).coerceAtMost(safeDuration)
        if (endMs <= startMs) return null

        return ResolvedTimelineExportRange(
            startFrame = start,
            endFrameExclusive = end,
            startMs = startMs,
            endMs = endMs,
            durationMs = endMs - startMs,
        )
    }
}

@Immutable
data class ResolvedTimelineExportRange(
    val startFrame: Long,
    val endFrameExclusive: Long,
    val startMs: Long,
    val endMs: Long,
    val durationMs: Long,
) {
    init {
        require(startFrame >= 0L) { "startFrame must be non-negative" }
        require(endFrameExclusive > startFrame) { "end frame must follow start frame" }
        require(startMs >= 0L) { "startMs must be non-negative" }
        require(endMs > startMs) { "endMs must follow startMs" }
        require(durationMs == endMs - startMs) { "durationMs must match the resolved bounds" }
    }
}

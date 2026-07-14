package com.novacut.editor.engine

/**
 * Shared guard against hostile or malformed media duration metadata.
 *
 * `MediaMetadataRetriever` returns attacker-influenced duration values.
 * Non-positive, overflowed, and implausibly large durations must never size an
 * array or drive an unbounded analysis loop, or a single crafted file can OOM
 * the app or hang it for hours (the class of failures Shotcut hardened against).
 *
 * All arithmetic here stays in `Long` and clamps before any `toInt()`, so it is
 * safe for `Long.MAX_VALUE` and conversion-overflow inputs. Pure Kotlin so it is
 * fully unit-testable on the JVM.
 */
object MediaDurationPolicy {

    /** Longest duration ClearCut treats as plausible for proportional analysis. */
    const val MAX_PLAUSIBLE_DURATION_MS: Long = 24L * 60L * 60L * 1000L // 24 hours

    /** True for a positive, non-overflowed, <= 24h duration. */
    fun isPlausible(durationMs: Long): Boolean =
        durationMs in 1..MAX_PLAUSIBLE_DURATION_MS

    /**
     * Duration usable for proportional (per-time) analysis. Returns 0 for
     * non-positive, overflowed, or implausibly long (> 24h) metadata so callers
     * skip proportional work without rejecting otherwise-playable media.
     */
    fun analyzableDurationMs(durationMs: Long): Long =
        if (isPlausible(durationMs)) durationMs else 0L

    /**
     * Count of evenly-spaced samples across [durationMs] at [intervalMs]
     * spacing, clamped to [maxSamples]. Returns 0 for non-positive/implausible
     * durations or non-positive parameters. Never overflows and never returns a
     * negative count.
     */
    fun boundedSampleCount(durationMs: Long, intervalMs: Long, maxSamples: Int): Int {
        if (intervalMs <= 0L || maxSamples <= 0) return 0
        val analyzable = analyzableDurationMs(durationMs)
        if (analyzable <= 0L) return 0
        val count = analyzable / intervalMs // Long division: cannot overflow
        return count.coerceIn(0L, maxSamples.toLong()).toInt()
    }
}

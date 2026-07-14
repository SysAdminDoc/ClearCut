package com.novacut.editor.engine

/**
 * Shared ceiling for in-memory PCM accumulation during audio decode.
 *
 * `AudioEngine.decodeToPCM`, `MultiCamEngine.extractMonoPcm`, and the Whisper
 * decode path buffer the whole decoded track before use. Without a bound, a
 * long or crafted file accumulates hundreds of MB and OOMs the app. This caps
 * accumulation and lets callers fail closed (stop decoding) instead of crashing.
 *
 * Pure Kotlin so the overflow-safe budget check is unit-testable on the JVM.
 */
object AudioDecodeBudget {

    /**
     * Maximum decoded 16-bit samples kept in memory (~192 MB of `ShortArray`,
     * or ~384 MB as boxed floats). Covers roughly 16 minutes of 48 kHz stereo;
     * longer inputs stop decoding rather than exhaust the heap.
     */
    const val MAX_PCM_SAMPLES: Int = 96_000_000

    /**
     * True when appending [incoming] samples to [current] would exceed [cap]
     * (or overflow `Int`). Overflow-safe: never evaluates `current + incoming`.
     */
    fun exceedsBudget(current: Int, incoming: Int, cap: Int = MAX_PCM_SAMPLES): Boolean {
        if (incoming <= 0) return false
        if (current >= cap) return true
        return current > cap - incoming
    }
}

package com.novacut.editor.engine

import com.novacut.editor.engine.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Content-ID / copyright pre-check using an audio fingerprint + AcoustID lookup.
 *
 * The Kotlin fingerprint is an energy-envelope hash — RMS over ~50 ms windows
 * quantised to a 16-bit bucket per window. It is intentionally simple: it is
 * not a drop-in replacement for Chromaprint's acoustic fingerprint, but it
 * gives a stable, collision-resistant hash that lets users deduplicate their
 * own exports and gives AcoustID enough envelope similarity for rough match
 * checking if/when the Chromaprint NDK path is wired.
 *
 * The AcoustID HTTP path is intentionally a stub (no network call) until the
 * Chromaprint-compatible fingerprint lands — the previous implementation made
 * a real HTTP GET that never carried the fingerprint payload, so it was both
 * misleading and wasteful. This version returns the local hash reliably and
 * documents the integration hook.
 *
 * Every result carries a [LookupOutcome] because the stub and a genuine "nothing
 * found" are indistinguishable from the returned fields alone, and a user told
 * their audio is clear by a check that never ran may publish on that answer.
 */
@Singleton
class ContentIdEngine @Inject constructor() {

    /**
     * What actually happened during [analyze]. "The service said no" and "the service
     * was never asked" produce identical `matchedTitle == null` results, and telling a
     * user their audio is clear when nothing was checked is the worst kind of wrong
     * answer this engine can give. Callers must branch on this, never on nullity.
     */
    enum class LookupOutcome {
        /** The service was queried and returned a match. */
        MATCHED,

        /** The service was queried and returned nothing. Only this means "clear". */
        NO_MATCH,

        /** No lookup was attempted: the caller supplied no AcoustID API key. */
        NOT_CHECKED_NO_API_KEY,

        /**
         * No lookup was attempted: AcoustID needs a Chromaprint fingerprint and the
         * `libchromaprint` NDK path is not linked, so the query would be meaningless.
         */
        NOT_CHECKED_NO_FINGERPRINT_BACKEND,
        ;

        /** True when a completed lookup backs the result. */
        val wasLookedUp: Boolean get() = this == MATCHED || this == NO_MATCH
    }

    data class Match(
        val matchedTitle: String?,
        val matchedArtist: String?,
        val confidence: Float,
        val hash: String,
        val lookup: LookupOutcome
    )

    /**
     * Whether a Chromaprint-compatible fingerprint can be produced. Until the NDK
     * library is linked this is false, and [analyze] reports that it did not look
     * anything up rather than reporting a clear result it never obtained.
     */
    fun isChromaprintAvailable(): Boolean = false

    /**
     * Compute the fingerprint and (optionally) look it up via AcoustID.
     * `pcm` is expected to be 16-bit signed PCM, mono or stereo — AudioEngine
     * produces this shape from `decodeToPCM`.
     */
    suspend fun analyze(pcm: ShortArray, apiKey: String?): Match = withContext(Dispatchers.IO) {
        val fp = fingerprint(pcm)
        val hash = fp.toHexString()
        if (apiKey.isNullOrBlank()) {
            AppLog.d(TAG, "no AcoustID key — returning hash-only result")
            return@withContext Match(null, null, 0f, hash, LookupOutcome.NOT_CHECKED_NO_API_KEY)
        }
        // AcoustID lookup currently requires a Chromaprint fingerprint format,
        // which depends on the `libchromaprint` NDK library. Until that lands
        // we do not pretend to query the service. Integration hook: send `fp` as
        // Chromaprint-encoded `fingerprint` query param to
        // https://api.acoustid.org/v2/lookup and parse the JSON `results`, then
        // return MATCHED or NO_MATCH from the response.
        if (!isChromaprintAvailable()) {
            AppLog.i(TAG, "AcoustID lookup not wired — Chromaprint dependency pending")
            return@withContext Match(null, null, 0f, hash, LookupOutcome.NOT_CHECKED_NO_FINGERPRINT_BACKEND)
        }
        Match(null, null, 0f, hash, LookupOutcome.NO_MATCH)
    }

    /** Energy-envelope hash: RMS per 2048-sample window, clamped to 16-bit. */
    private fun fingerprint(pcm: ShortArray): IntArray {
        if (pcm.isEmpty()) return IntArray(0)
        val win = 2048
        val blocks = pcm.size / win
        if (blocks == 0) return IntArray(0)
        val out = IntArray(blocks)
        for (b in 0 until blocks) {
            var sum = 0.0
            val off = b * win
            for (i in 0 until win) {
                val s = pcm[off + i].toDouble()
                sum += s * s
            }
            // `coerceIn` guards against the theoretical int overflow that
            // would only happen on a pathological buffer of all 32767/-32768
            // samples — still cheap and future-proof.
            out[b] = sqrt(sum / win).toInt().coerceIn(0, 65535)
        }
        return out
    }

    /** Local similarity helper for offline dedup (no API required). */
    fun similarity(a: IntArray, b: IntArray): Float {
        val n = minOf(a.size, b.size)
        if (n == 0) return 0f
        var diff = 0.0
        for (i in 0 until n) diff += abs(a[i] - b[i]).toDouble()
        val scale = 1.0 / (n * 32768.0)
        return (1.0 - (diff * scale)).coerceIn(0.0, 1.0).toFloat()
    }

    private fun IntArray.toHexString(): String {
        if (isEmpty()) return ""
        val sb = StringBuilder(size * 4)
        for (v in this) sb.append(Integer.toHexString(v and 0xFFFF).padStart(4, '0'))
        return sb.toString()
    }

    companion object { private const val TAG = "ContentIdEngine" }
}

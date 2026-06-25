package com.novacut.editor.engine

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.net.Uri
import android.util.Log
import com.novacut.editor.model.Clip
import com.novacut.editor.model.ImageOverlay
import com.novacut.editor.model.Track
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Probe every clip's `sourceUri` for current accessibility and report dangling
 * references back to the UI so a "Tap to relink" affordance can render on the
 * timeline.
 *
 * The Android `content://` per-URI grant model is the recommended way for
 * ClearCut to access user media — but those grants can disappear at any time
 * (user clears the system Photo Picker grant, the source content provider
 * revokes, the underlying file is moved, the app process restarts after the
 * one-time grant lapses). When that happens ClearCut today silently fails to
 * decode the clip. This probe is the static-analysis half of fixing that:
 * surface "missing" before the user hits play and gets a black frame.
 *
 * The probe is intentionally split into two halves:
 *
 *  - [check] is a pure decision helper that takes a `UriOpener` and any
 *    candidate URI string. Unit-testable on the JVM without Android because
 *    every Android-flavour I/O goes through the opener.
 *  - [probe] / [probeClips] are the Android-bound wrappers that resolve the
 *    Hilt-injected [Context] and delegate to [check].
 *
 * **What this class does not do today:** mutate the project, persist the
 * report, or update the autosave. Those land in the Timeline UI follow-up
 * commit. This commit is the engine + tests only.
 */
@Singleton
class MediaRelinkProbe @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    enum class RelinkState {
        /** URI was opened and reports non-zero length. */
        OK,

        /** URI is unreachable, throws SecurityException/IOException, or reports zero length. */
        MISSING,

        /** Pre-flight signal: URI is null/blank, malformed, or scheme is not supported for probing. */
        UNKNOWN,
    }

    data class ClipRelinkReport(
        val clipId: String,
        val sourceUri: String,
        val state: RelinkState,
        val reason: String? = null,
    ) {
        val isMissing: Boolean get() = state == RelinkState.MISSING

        /**
         * UI hint copy. Pure — no string resources here so engine tests don't
         * need to spin up an Android resource loader. The UI layer is free to
         * wrap this in its own localizable variant.
         */
        val userMessage: String
            get() = when (state) {
                RelinkState.OK -> "Source available"
                RelinkState.MISSING -> reason?.let { "Source missing — $it" } ?: "Source missing"
                RelinkState.UNKNOWN -> reason?.let { "Source unverified — $it" } ?: "Source unverified"
            }
    }

    /**
     * I/O seam for [check]. Production binding wraps Android's
     * `ContentResolver.openAssetFileDescriptor`. Tests inject a fake to
     * exercise every branch without an Android runtime.
     */
    fun interface UriOpener {
        /**
         * Return a [ProbeResult] for the given URI string.
         * - `length > 0` → OK
         * - `length == 0` → MISSING (file exists but is empty / not yet written)
         * - `length < 0` → UNKNOWN length (treat as OK; some providers report -1)
         * - throws → MISSING with the throwable's message
         * - returns null → MISSING ("provider returned null descriptor")
         */
        @Throws(Throwable::class)
        fun open(uri: String): ProbeResult?
    }

    data class ProbeResult(val length: Long)

    /**
     * Pure decision helper — JVM-testable, no Android. Given a URI string and
     * an opener, return the [ClipRelinkReport] without any Android coupling.
     *
     * Decision table:
     *   - null/blank URI → UNKNOWN
     *   - unsupported scheme (anything other than content/file/asset/http/https) → UNKNOWN
     *   - opener throws → MISSING (carries the exception message in `reason`)
     *   - opener returns null → MISSING ("provider returned null")
     *   - length == 0 → MISSING ("empty source")
     *   - length < 0 → OK (some content providers report -1 for unknown length;
     *     treating as missing would cause false-positives across the OS)
     *   - length > 0 → OK
     */
    fun check(clipId: String, uri: String?, opener: UriOpener): ClipRelinkReport {
        if (uri.isNullOrBlank()) {
            return ClipRelinkReport(clipId, uri ?: "", RelinkState.UNKNOWN, "blank source URI")
        }
        val scheme = parseScheme(uri)
        if (scheme !in SUPPORTED_SCHEMES) {
            return ClipRelinkReport(
                clipId, uri, RelinkState.UNKNOWN,
                "scheme '${scheme ?: "<none>"}' not probeable"
            )
        }
        return try {
            val result = opener.open(uri)
            when {
                result == null -> ClipRelinkReport(
                    clipId, uri, RelinkState.MISSING, "provider returned null descriptor"
                )
                result.length == 0L -> ClipRelinkReport(
                    clipId, uri, RelinkState.MISSING, "empty source"
                )
                else -> ClipRelinkReport(clipId, uri, RelinkState.OK)
            }
        } catch (t: Throwable) {
            ClipRelinkReport(
                clipId, uri, RelinkState.MISSING,
                t.message?.take(120) ?: t::class.java.simpleName
            )
        }
    }

    fun checkImageOverlay(
        overlayId: String,
        uri: String?,
        opener: UriOpener,
    ): ClipRelinkReport = check(overlayId, uri, opener)

    /**
     * Production probe. Runs the URI-accessibility check on Dispatchers.IO
     * for every clip referenced by the tracks. Returns a map keyed by clip ID
     * so the Timeline overlay layer can render the hatch + tap action without
     * walking the report list per-frame.
     *
     * Compound clips are recursed in-place so a missing source nested two
     * levels deep is reported under the inner clip's ID, not the wrapping
     * compound clip's ID.
     */
    suspend fun probeClips(tracks: List<Track>): Map<String, ClipRelinkReport> =
        withContext(Dispatchers.IO) {
            val opener = androidOpener()
            val out = mutableMapOf<String, ClipRelinkReport>()
            tracks.forEach { track -> probeTrack(track.clips, opener, out) }
            out
        }

    suspend fun probeImageOverlays(imageOverlays: List<ImageOverlay>): Map<String, ClipRelinkReport> =
        withContext(Dispatchers.IO) {
            val opener = androidOpener()
            imageOverlays.associate { overlay ->
                overlay.id to checkImageOverlay(overlay.id, overlay.sourceUri.toString(), opener)
            }
        }

    private fun probeTrack(
        clips: List<Clip>,
        opener: UriOpener,
        out: MutableMap<String, ClipRelinkReport>,
    ) {
        for (clip in clips) {
            if (clip.isCompound) {
                probeTrack(clip.compoundClips, opener, out)
                continue
            }
            val report = check(clip.id, clip.sourceUri.toString(), opener)
            out[clip.id] = report
        }
    }

    /**
     * Single-URI convenience used by editor screens that need an immediate
     * answer (e.g. about to seek to a clip and want to short-circuit decode
     * errors with a relink prompt).
     */
    suspend fun probe(clipId: String, sourceUri: Uri?): ClipRelinkReport =
        withContext(Dispatchers.IO) {
            check(clipId, sourceUri?.toString(), androidOpener())
        }

    private fun androidOpener(): UriOpener = UriOpener { uri ->
        val parsed = try { Uri.parse(uri) } catch (e: Exception) {
            Log.w(TAG, "Cannot parse URI for relink probe: $uri", e)
            return@UriOpener null
        }
        val descriptor: AssetFileDescriptor? = try {
            context.contentResolver.openAssetFileDescriptor(parsed, "r")
        } catch (se: SecurityException) {
            // Re-throw so check() records "Permission denied" in the reason.
            throw se
        } catch (io: java.io.IOException) {
            throw io
        }
        descriptor?.use { fd -> ProbeResult(fd.length) }
    }

    private fun parseScheme(uri: String): String? {
        val idx = uri.indexOf(':')
        if (idx <= 0) return null
        return uri.substring(0, idx).lowercase()
    }

    companion object {
        private const val TAG = "MediaRelinkProbe"
        private val SUPPORTED_SCHEMES = setOf("content", "file", "asset", "http", "https")
    }
}

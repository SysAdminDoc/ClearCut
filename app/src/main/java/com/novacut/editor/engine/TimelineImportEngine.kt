package com.novacut.editor.engine

import android.content.Context
import android.net.Uri
import android.util.Log
import com.novacut.editor.model.Project
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stub engine -- NLE round-trip import. See ROADMAP.md Tier C.14.
 *
 * Closes the export-only gap on [TimelineExchangeEngine]: parses FCPXML,
 * OpenTimelineIO, and CMX 3600 EDL into a NovaCut [Project] so users can polish
 * on mobile projects started in DaVinci Resolve / Premiere Pro / Final Cut Pro.
 *
 * Lossy conversions (NLE-specific metadata that NovaCut can't represent) are
 * collected into [ImportResult.warnings] and surfaced to the user so they know
 * what was dropped.
 */
@Singleton
class TimelineImportEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {

    enum class Format(val extension: String, val displayName: String) {
        FCPXML("fcpxml", "Final Cut Pro XML"),
        OTIO("otio", "OpenTimelineIO JSON"),
        EDL("edl", "CMX 3600 EDL")
    }

    data class ImportResult(
        val project: Project?,
        val warnings: List<String>,
        val droppedEffects: Int,
        val unresolvedMediaUris: List<String>
    ) {
        val success: Boolean get() = project != null
    }

    fun detectFormat(uri: Uri): Format? {
        val name = uri.lastPathSegment?.lowercase() ?: return null
        return Format.values().firstOrNull { name.endsWith(".${it.extension}") }
    }

    /**
     * Round-trip fidelity hint for a (source NLE, format) pair. Pure
     * function the import UI can call before parsing to set user
     * expectations for what's preserved.
     */
    fun roundTripFidelity(format: Format): RoundTripFidelity = when (format) {
        Format.FCPXML -> RoundTripFidelity.GOOD     // Most clip/effect data
        Format.OTIO -> RoundTripFidelity.EXCELLENT  // Native ASWF interchange
        Format.EDL -> RoundTripFidelity.LIMITED     // Cut decisions only; effects dropped
    }

    enum class RoundTripFidelity(val displayName: String, val warningCopy: String) {
        EXCELLENT("Excellent", "Most timeline data will be preserved."),
        GOOD("Good", "Clip + effect data preserved; provider-specific metadata may be dropped."),
        LIMITED("Limited", "Cut decisions only. Effects, transitions, and overlays will not be imported."),
    }

    suspend fun import(
        uri: Uri,
        format: Format? = null,
        mediaRelocation: Map<String, Uri> = emptyMap()
    ): ImportResult = withContext(Dispatchers.IO) {
        val detected = format ?: detectFormat(uri) ?: return@withContext ImportResult(
            project = null,
            warnings = listOf("Unknown file format"),
            droppedEffects = 0,
            unresolvedMediaUris = emptyList()
        )
        Log.d(TAG, "import: stub -- ${detected.displayName} parser not implemented")
        ImportResult(
            project = null,
            warnings = listOf("${detected.displayName} import is not yet implemented."),
            droppedEffects = 0,
            unresolvedMediaUris = emptyList()
        )
    }

    companion object {
        private const val TAG = "TimelineImport"
    }
}

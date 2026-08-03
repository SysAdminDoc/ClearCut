package com.novacut.editor.engine

import android.content.Context
import android.net.Uri
import com.novacut.editor.engine.TimelineExchangeEngine.TimelineExchangeFormat
import com.novacut.editor.model.Project
import com.novacut.editor.model.Clip
import com.novacut.editor.model.TimelineTimebase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Safe, preview-first timeline import boundary.
 *
 * Parsing produces an immutable [ImportResult]. Nothing is persisted by this
 * class. A caller must inspect the fidelity/media report and explicitly call
 * [commit] to receive one canonical [ProjectDocument] for the existing atomic
 * save boundary.
 */
@Singleton
class TimelineImportEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val timelineExchangeEngine: TimelineExchangeEngine,
    private val timelineExchangeValidator: TimelineExchangeValidator,
    private val mediaRelinkProbe: MediaRelinkProbe,
) {

    enum class Format(val extension: String, val displayName: String) {
        FCPXML("fcpxml", "Final Cut Pro XML"),
        OTIO("otio", "OpenTimelineIO JSON"),
        EDL("edl", "CMX 3600 EDL")
    }

    data class ImportResult(
        /** Kept for callers that already expect a persisted project payload. */
        val project: Project? = null,
        val warnings: List<String> = emptyList(),
        val droppedEffects: Int = 0,
        val unresolvedMediaUris: List<String> = emptyList(),
        val exchangeResult: TimelineExchangeEngine.ExchangeResult? = null,
        val fidelityReport: TimelineExchangeValidator.Report? = null,
        val mediaRelinkReports: List<MediaRelinkProbe.ClipRelinkReport> = emptyList(),
    ) {
        /** Parsing succeeded, but unresolved media or loss errors still gate commit. */
        val success: Boolean
            get() = project != null ||
                (exchangeResult != null && fidelityReport?.canProceed == true)

        val readyForAtomicCommit: Boolean
            get() = exchangeResult != null && fidelityReport?.canProceed == true

        fun toProjectDocument(targetProject: Project, playheadMs: Long = 0L): ProjectDocument? {
            if (!readyForAtomicCommit) return null
            return exchangeResult?.toProjectDocument(targetProject, playheadMs)
        }
    }

    fun detectFormat(uri: Uri): Format? {
        val name = uri.lastPathSegment?.lowercase() ?: return null
        return Format.values().firstOrNull { name.endsWith(".${it.extension}") }
    }

    /**
     * Round-trip fidelity hint for a (source NLE, format) pair. The value is a
     * preview label, not permission to skip the measured issue report.
     */
    fun roundTripFidelity(format: Format): RoundTripFidelity = when (format) {
        Format.FCPXML -> RoundTripFidelity.GOOD
        Format.OTIO -> RoundTripFidelity.EXCELLENT
        Format.EDL -> RoundTripFidelity.LIMITED
    }

    enum class RoundTripFidelity(val displayName: String, val warningCopy: String) {
        EXCELLENT("Excellent", "Most timeline data will be preserved."),
        GOOD("Good", "Clip + timing data are preserved; provider-specific metadata may be dropped."),
        LIMITED("Limited", "Cut decisions only. Effects, transitions, and overlays may not be imported."),
    }

    /** Read, parse, probe media, and produce a non-mutating import preview. */
    suspend fun import(
        uri: Uri,
        format: Format? = null,
        mediaRelocation: Map<String, Uri> = emptyMap(),
    ): ImportResult = withContext(Dispatchers.IO) {
        val detected = format ?: detectFormat(uri) ?: return@withContext ImportResult(
            warnings = listOf("Unknown file format"),
        )
        val raw = readUtf8(uri, detected.maxBytes)
            ?: return@withContext ImportResult(
                warnings = listOf("${detected.displayName} file could not be read within the import limit."),
            )
        importText(
            raw = raw,
            format = detected,
            mediaRelocation = mediaRelocation,
            probeMedia = true,
        )
    }

    /** JVM/instrumentation-friendly parser entry point with optional media probing. */
    suspend fun importText(
        raw: String,
        format: Format,
        mediaRelocation: Map<String, Uri> = emptyMap(),
        probeMedia: Boolean = false,
        uriParser: (String) -> Uri? = Uri::parse,
    ): ImportResult {
        val parsed = when (format) {
            Format.OTIO -> timelineExchangeEngine.importFromOtio(raw, uriParser)
            Format.FCPXML -> timelineExchangeEngine.importFromFcpxml(raw, uriParser)
            Format.EDL -> timelineExchangeEngine.importFromEdl(raw, TimelineTimebase(30), uriParser)
        }
        val relocated = applyRelocations(parsed, mediaRelocation)
        val relinkReports = if (probeMedia) {
            mediaRelinkProbe.probeClips(relocated.tracks).values.toList()
        } else {
            emptyList()
        }
        val probedMissing = relinkReports
            .filter { it.isMissing }
            .map { it.sourceUri }
        val unresolved = (relocated.unresolvedMediaUris + probedMissing).distinct()
        val warnings = (relocated.warnings + relinkReports
            .filter { it.state == MediaRelinkProbe.RelinkState.UNKNOWN }
            .map { "${it.sourceUri}: ${it.reason ?: "media could not be verified"}" })
            .distinct()
        val exchangeFormat = format.toExchangeFormat()
        val report = timelineExchangeValidator.validateImport(
            format = exchangeFormat,
            tracks = relocated.tracks,
            textOverlays = relocated.textOverlays,
            unresolvedMediaUris = unresolved,
            droppedEffects = relocated.droppedEffects,
            importerWarnings = warnings,
        )
        return ImportResult(
            warnings = warnings,
            droppedEffects = relocated.droppedEffects,
            unresolvedMediaUris = unresolved,
            exchangeResult = relocated,
            fidelityReport = report,
            mediaRelinkReports = relinkReports,
        )
    }

    /**
     * The only commit operation: turn an accepted preview into the canonical
     * persisted document. The caller owns the actual repository transaction.
     */
    fun commit(
        targetProject: Project,
        result: ImportResult,
        playheadMs: Long = 0L,
    ): ProjectDocument? = result.toProjectDocument(targetProject, playheadMs)

    private fun applyRelocations(
        result: TimelineExchangeEngine.ExchangeResult,
        mediaRelocation: Map<String, Uri>,
    ): TimelineExchangeEngine.ExchangeResult {
        if (mediaRelocation.isEmpty()) return result
        fun rewriteClip(clip: Clip): Clip = clip.copy(
            sourceUri = mediaRelocation[clip.sourceUri.toString()] ?: clip.sourceUri,
            compoundClips = clip.compoundClips.map(::rewriteClip),
        )
        val rewrittenTracks = result.tracks.map { track ->
            track.copy(clips = track.clips.map(::rewriteClip))
        }
        val stillUnresolved = result.unresolvedMediaUris.filterNot(mediaRelocation::containsKey)
        return result.copy(tracks = rewrittenTracks, unresolvedMediaUris = stillUnresolved)
    }

    private suspend fun readUtf8(uri: Uri, maxBytes: Long): String? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > maxBytes) return@withContext null
                    output.write(buffer, 0, read)
                }
                output.toString(Charsets.UTF_8.name())
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun Format.toExchangeFormat(): TimelineExchangeFormat = when (this) {
        Format.OTIO -> TimelineExchangeFormat.OTIO
        Format.FCPXML -> TimelineExchangeFormat.FCPXML
        Format.EDL -> TimelineExchangeFormat.EDL_CMX3600
    }

    private val Format.maxBytes: Long
        get() = when (this) {
            Format.OTIO, Format.FCPXML -> 25_000_000L
            Format.EDL -> 5_000_000L
        }
}

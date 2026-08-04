package com.novacut.editor.engine

import com.novacut.editor.model.TextOverlay
import com.novacut.editor.model.TimelineMarker
import com.novacut.editor.model.TimelineTimebase
import com.novacut.editor.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the non-UI lifecycle of a timeline interchange export.
 *
 * The editor only supplies an immutable snapshot and an output directory. This
 * coordinator validates that snapshot, serializes it, and commits the text
 * file atomically. It never mutates editor state or presents UI copy, which
 * leaves the ViewModel responsible only for translating the typed result into
 * feedback and localized toasts.
 */
@Singleton
class TimelineExportCoordinator @Inject constructor(
    private val timelineExchangeEngine: TimelineExchangeEngine,
    private val timelineExchangeValidator: TimelineExchangeValidator,
) {

    enum class Format(
        val exchangeFormat: TimelineExchangeEngine.TimelineExchangeFormat,
        val extension: String,
    ) {
        OTIO(TimelineExchangeEngine.TimelineExchangeFormat.OTIO, "otio"),
        FCPXML(TimelineExchangeEngine.TimelineExchangeFormat.FCPXML, "fcpxml"),
        EDIT_DECISION_JSON(
            TimelineExchangeEngine.TimelineExchangeFormat.EDIT_DECISION_JSON,
            EditDecisionJsonEngine.FILE_EXTENSION,
        ),
    }

    data class Request(
        val format: Format,
        val tracks: List<Track>,
        val textOverlays: List<TextOverlay>,
        val projectName: String,
        val frameRate: Int,
        val outputDirectory: File,
        val timelineMarkers: List<TimelineMarker> = emptyList(),
        val timebase: TimelineTimebase = TimelineTimebase(30),
    )

    data class Result(
        val format: Format,
        val report: TimelineExchangeValidator.Report,
        val outputFile: File? = null,
    ) {
        val blocked: Boolean get() = !report.canProceed
        val succeeded: Boolean get() = outputFile != null
    }

    suspend fun export(request: Request): Result = withContext(Dispatchers.IO) {
        val report = timelineExchangeValidator.validateExport(
            format = request.format.exchangeFormat,
            tracks = request.tracks,
            textOverlays = request.textOverlays,
            frameRate = request.frameRate,
        )
        if (!report.canProceed) {
            return@withContext Result(format = request.format, report = report)
        }

        val outputFile = File(
            request.outputDirectory,
            "${sanitizeFileName(request.projectName, fallback = "ClearCut")}.${request.format.extension}",
        )
        val contents = when (request.format) {
            Format.OTIO -> timelineExchangeEngine.exportToOtio(
                tracks = request.tracks,
                textOverlays = request.textOverlays,
                projectName = request.projectName,
                frameRate = request.frameRate,
            )
            Format.FCPXML -> timelineExchangeEngine.exportToFcpxml(
                tracks = request.tracks,
                projectName = request.projectName,
                frameRate = request.frameRate,
            )
            Format.EDIT_DECISION_JSON -> timelineExchangeEngine.exportToEditDecisionJson(
                tracks = request.tracks,
                textOverlays = request.textOverlays,
                timelineMarkers = request.timelineMarkers,
                projectName = request.projectName,
                timebase = request.timebase,
            )
        }
        writeUtf8TextAtomically(outputFile, contents)
        Result(format = request.format, report = report, outputFile = outputFile)
    }
}

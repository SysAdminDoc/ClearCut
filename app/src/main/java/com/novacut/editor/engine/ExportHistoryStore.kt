package com.novacut.editor.engine

import android.content.Context
import com.novacut.editor.model.ExportConfig
import com.novacut.editor.model.ResolvedTimelineExportRange
import com.novacut.editor.model.Track
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

private const val EXPORT_HISTORY_DIR = "diagnostics"
private const val EXPORT_HISTORY_FILE = "export-history.json"
private const val DEFAULT_EXPORT_HISTORY_LIMIT = 25
private const val MAX_EXPORT_HISTORY_BYTES = 256L * 1024L

enum class ExportHistoryStatus {
    COMPLETE,
    FAILED,
    CANCELLED,
    BLOCKED,
    /** Android stopped or refused the export service; the timeline itself was fine. */
    INTERRUPTED
}

/** Statuses whose kept partial file can be offered to Transformer.resume. */
val ExportHistoryStatus.keepsResumablePartial: Boolean
    get() = this == ExportHistoryStatus.CANCELLED || this == ExportHistoryStatus.INTERRUPTED

data class ExportHistoryEntry(
    val id: String = UUID.randomUUID().toString(),
    val projectId: String,
    val projectName: String,
    val status: ExportHistoryStatus,
    val startedAtEpochMs: Long,
    val finishedAtEpochMs: Long,
    val elapsedMs: Long,
    val outputPath: String?,
    val outputName: String?,
    val outputBytes: Long?,
    val codecLabel: String,
    val resolutionLabel: String,
    val frameRate: Int,
    val timelineDurationMs: Long,
    val rangeStartFrame: Long? = null,
    val rangeEndFrameExclusive: Long? = null,
    val rangeStartMs: Long? = null,
    val rangeEndMs: Long? = null,
    /** A non-final Media3 MP4 that can be offered to Transformer.resume. */
    val resumePartialPath: String? = null,
    val resumeProjectFingerprint: String? = null,
    val resumeConfigFingerprint: String? = null,
    val errorMessage: String? = null,
    val diagnosticSummary: String? = null,
    val mediaWarningCount: Int = 0,
    val mediaBlockingCount: Int = 0,
    /** What the color plan said the file would carry; null for outputs without a video track. */
    val requestedColor: DeliveredColor? = null,
    /** What the finished file's video track is tagged with, read back from the file. */
    val observedColor: DeliveredColor? = null,
)

class ExportHistoryStore(
    private val historyFile: File,
    private val retainCount: Int = DEFAULT_EXPORT_HISTORY_LIMIT,
    /** Reads a finished file's video color; production passes ExportOutputVerifier.observedColor. */
    private val observeColor: (File) -> DeliveredColor? = { null },
) {
    fun read(): List<ExportHistoryEntry> {
        if (!historyFile.isFile || historyFile.length() <= 0L || historyFile.length() > MAX_EXPORT_HISTORY_BYTES) {
            return emptyList()
        }
        return runCatching {
            val root = JSONArray(historyFile.readText(Charsets.UTF_8))
            buildList {
                for (index in 0 until root.length()) {
                    root.optJSONObject(index)?.let { json ->
                        exportHistoryEntryFromJson(json)?.let(::add)
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    fun append(entry: ExportHistoryEntry): List<ExportHistoryEntry> {
        val updated = (listOf(entry.withObservedColor()) + read().filterNot { it.id == entry.id })
            .take(retainCount.coerceAtLeast(1))
        write(updated)
        return updated
    }

    fun remove(id: String): List<ExportHistoryEntry> {
        val updated = read().filterNot { it.id == id }
        write(updated)
        return updated
    }

    // Callers append on an IO thread, so this is where the output gets read back.
    private fun ExportHistoryEntry.withObservedColor(): ExportHistoryEntry {
        if (status != ExportHistoryStatus.COMPLETE || requestedColor == null || observedColor != null) return this
        val output = outputPath?.let(::File)?.takeIf { it.isFile } ?: return this
        return copy(observedColor = observeColor(output))
    }

    private fun write(entries: List<ExportHistoryEntry>) {
        historyFile.parentFile?.mkdirs()
        writeUtf8TextAtomically(historyFile, exportHistoryToJson(entries).toString(2))
    }

    companion object {
        fun forContext(context: Context): ExportHistoryStore {
            return ExportHistoryStore(
                historyFile = File(File(context.filesDir, EXPORT_HISTORY_DIR), EXPORT_HISTORY_FILE),
                observeColor = ExportOutputVerifier::observedColor,
            )
        }
    }
}

fun buildExportHistoryEntry(
    projectId: String,
    projectName: String,
    status: ExportHistoryStatus,
    startedAtEpochMs: Long,
    finishedAtEpochMs: Long,
    outputFile: File?,
    config: ExportConfig,
    timelineDurationMs: Long,
    resolvedRange: ResolvedTimelineExportRange? = null,
    resumePartialFile: File? = null,
    resumeProjectFingerprint: String? = null,
    resumeConfigFingerprint: String? = null,
    errorMessage: String? = null,
    diagnosticSummary: String? = null,
    mediaWarningCount: Int = 0,
    mediaBlockingCount: Int = 0,
    /** The timeline's tracks, so the record carries the color the plan asked for. */
    tracks: List<Track> = emptyList(),
): ExportHistoryEntry {
    val existingOutput = outputFile?.takeIf { it.isFile && it.length() > 0L }
    return ExportHistoryEntry(
        projectId = projectId,
        projectName = projectName,
        status = status,
        startedAtEpochMs = startedAtEpochMs,
        finishedAtEpochMs = finishedAtEpochMs,
        elapsedMs = (finishedAtEpochMs - startedAtEpochMs).coerceAtLeast(0L),
        outputPath = existingOutput?.absolutePath,
        outputName = existingOutput?.name,
        outputBytes = existingOutput?.length(),
        codecLabel = if (config.exportAudioOnly || config.exportStemsOnly) {
            config.audioCodec.label
        } else {
            config.codec.label
        },
        resolutionLabel = if (config.exportAudioOnly || config.exportStemsOnly) {
            "Audio"
        } else {
            config.resolution.label
        },
        frameRate = config.frameRate,
        timelineDurationMs = timelineDurationMs.coerceAtLeast(0L),
        rangeStartFrame = resolvedRange?.startFrame,
        rangeEndFrameExclusive = resolvedRange?.endFrameExclusive,
        rangeStartMs = resolvedRange?.startMs,
        rangeEndMs = resolvedRange?.endMs,
        resumePartialPath = resumePartialFile?.absolutePath,
        resumeProjectFingerprint = resumeProjectFingerprint?.takeIf { it.isNotBlank() },
        resumeConfigFingerprint = resumeConfigFingerprint?.takeIf { it.isNotBlank() },
        errorMessage = errorMessage?.takeIf { it.isNotBlank() },
        diagnosticSummary = diagnosticSummary?.takeIf { it.isNotBlank() },
        mediaWarningCount = mediaWarningCount.coerceAtLeast(0),
        mediaBlockingCount = mediaBlockingCount.coerceAtLeast(0),
        requestedColor = requestedExportColor(config, tracks),
    )
}

/** Null for exports that carry no tagged video track: audio, stems, GIFs, stills and contact sheets. */
internal fun requestedExportColor(config: ExportConfig, tracks: List<Track>): DeliveredColor? {
    if (tracks.isEmpty() || config.exportAudioOnly || config.exportStemsOnly || config.exportAsGif ||
        config.captureFrameOnly || config.exportAsContactSheet
    ) {
        return null
    }
    val plan = CompositionPlanBuilder.build(tracks)
    return ColorRenderPlanner.plan(config.colorPolicy, plan.visualTracks, plan.durationMs).expected
}

private fun exportHistoryToJson(entries: List<ExportHistoryEntry>): JSONArray {
    return JSONArray().apply {
        entries.forEach { entry ->
            put(JSONObject().apply {
                put("id", entry.id)
                put("projectId", entry.projectId)
                put("projectName", entry.projectName)
                put("status", entry.status.name)
                put("startedAtEpochMs", entry.startedAtEpochMs)
                put("finishedAtEpochMs", entry.finishedAtEpochMs)
                put("elapsedMs", entry.elapsedMs)
                putNullable("outputPath", entry.outputPath)
                putNullable("outputName", entry.outputName)
                putNullable("outputBytes", entry.outputBytes)
                put("codecLabel", entry.codecLabel)
                put("resolutionLabel", entry.resolutionLabel)
                put("frameRate", entry.frameRate)
                put("timelineDurationMs", entry.timelineDurationMs)
                putNullable("rangeStartFrame", entry.rangeStartFrame)
                putNullable("rangeEndFrameExclusive", entry.rangeEndFrameExclusive)
                putNullable("rangeStartMs", entry.rangeStartMs)
                putNullable("rangeEndMs", entry.rangeEndMs)
                putNullable("resumePartialPath", entry.resumePartialPath)
                putNullable("resumeProjectFingerprint", entry.resumeProjectFingerprint)
                putNullable("resumeConfigFingerprint", entry.resumeConfigFingerprint)
                putNullable("errorMessage", entry.errorMessage)
                putNullable("diagnosticSummary", entry.diagnosticSummary)
                put("mediaWarningCount", entry.mediaWarningCount)
                put("mediaBlockingCount", entry.mediaBlockingCount)
                putNullable("requestedColor", entry.requestedColor?.name)
                putNullable("observedColor", entry.observedColor?.name)
            })
        }
    }
}

private fun exportHistoryEntryFromJson(json: JSONObject): ExportHistoryEntry? {
    val id = json.optString("id").takeIf { it.isNotBlank() } ?: return null
    val projectId = json.optString("projectId").takeIf { it.isNotBlank() } ?: return null
    val projectName = json.optString("projectName").takeIf { it.isNotBlank() } ?: "Untitled"
    val status = runCatching {
        ExportHistoryStatus.valueOf(json.optString("status"))
    }.getOrNull() ?: return null
    return ExportHistoryEntry(
        id = id,
        projectId = projectId,
        projectName = projectName,
        status = status,
        startedAtEpochMs = json.optLong("startedAtEpochMs").coerceAtLeast(0L),
        finishedAtEpochMs = json.optLong("finishedAtEpochMs").coerceAtLeast(0L),
        elapsedMs = json.optLong("elapsedMs").coerceAtLeast(0L),
        outputPath = json.optNullableString("outputPath"),
        outputName = json.optNullableString("outputName"),
        outputBytes = json.optNullableLong("outputBytes"),
        codecLabel = json.optString("codecLabel", "Unknown"),
        resolutionLabel = json.optString("resolutionLabel", "Unknown"),
        frameRate = json.optInt("frameRate").coerceAtLeast(0),
        timelineDurationMs = json.optLong("timelineDurationMs").coerceAtLeast(0L),
        rangeStartFrame = json.optNullableLong("rangeStartFrame"),
        rangeEndFrameExclusive = json.optNullableLong("rangeEndFrameExclusive"),
        rangeStartMs = json.optNullableLong("rangeStartMs"),
        rangeEndMs = json.optNullableLong("rangeEndMs"),
        resumePartialPath = json.optNullableString("resumePartialPath"),
        resumeProjectFingerprint = json.optNullableString("resumeProjectFingerprint"),
        resumeConfigFingerprint = json.optNullableString("resumeConfigFingerprint"),
        errorMessage = json.optNullableString("errorMessage"),
        diagnosticSummary = json.optNullableString("diagnosticSummary"),
        mediaWarningCount = json.optInt("mediaWarningCount").coerceAtLeast(0),
        mediaBlockingCount = json.optInt("mediaBlockingCount").coerceAtLeast(0),
        requestedColor = json.optDeliveredColor("requestedColor"),
        observedColor = json.optDeliveredColor("observedColor"),
    )
}

private fun JSONObject.optNullableString(name: String): String? {
    if (!has(name) || isNull(name)) return null
    return optString(name).takeIf { it.isNotBlank() }
}

private fun JSONObject.optDeliveredColor(name: String): DeliveredColor? =
    optNullableString(name)?.let { value -> DeliveredColor.entries.firstOrNull { it.name == value } }

private fun JSONObject.optNullableLong(name: String): Long? {
    if (!has(name) || isNull(name)) return null
    return optLong(name).takeIf { it >= 0L }
}

private fun JSONObject.putNullable(name: String, value: Any?) {
    put(name, value ?: JSONObject.NULL)
}

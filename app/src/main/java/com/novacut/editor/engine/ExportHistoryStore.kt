package com.novacut.editor.engine

import android.content.Context
import com.novacut.editor.model.ExportConfig
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
    BLOCKED
}

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
    val errorMessage: String? = null,
    val diagnosticSummary: String? = null,
    val mediaWarningCount: Int = 0,
    val mediaBlockingCount: Int = 0
)

class ExportHistoryStore(
    private val historyFile: File,
    private val retainCount: Int = DEFAULT_EXPORT_HISTORY_LIMIT
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
        val updated = (listOf(entry) + read().filterNot { it.id == entry.id })
            .take(retainCount.coerceAtLeast(1))
        historyFile.parentFile?.mkdirs()
        writeUtf8TextAtomically(historyFile, exportHistoryToJson(updated).toString(2))
        return updated
    }

    companion object {
        fun forContext(context: Context): ExportHistoryStore {
            return ExportHistoryStore(
                File(File(context.filesDir, EXPORT_HISTORY_DIR), EXPORT_HISTORY_FILE)
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
    errorMessage: String? = null,
    diagnosticSummary: String? = null,
    mediaWarningCount: Int = 0,
    mediaBlockingCount: Int = 0
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
        errorMessage = errorMessage?.takeIf { it.isNotBlank() },
        diagnosticSummary = diagnosticSummary?.takeIf { it.isNotBlank() },
        mediaWarningCount = mediaWarningCount.coerceAtLeast(0),
        mediaBlockingCount = mediaBlockingCount.coerceAtLeast(0)
    )
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
                putNullable("errorMessage", entry.errorMessage)
                putNullable("diagnosticSummary", entry.diagnosticSummary)
                put("mediaWarningCount", entry.mediaWarningCount)
                put("mediaBlockingCount", entry.mediaBlockingCount)
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
        errorMessage = json.optNullableString("errorMessage"),
        diagnosticSummary = json.optNullableString("diagnosticSummary"),
        mediaWarningCount = json.optInt("mediaWarningCount").coerceAtLeast(0),
        mediaBlockingCount = json.optInt("mediaBlockingCount").coerceAtLeast(0)
    )
}

private fun JSONObject.optNullableString(name: String): String? {
    if (!has(name) || isNull(name)) return null
    return optString(name).takeIf { it.isNotBlank() }
}

private fun JSONObject.optNullableLong(name: String): Long? {
    if (!has(name) || isNull(name)) return null
    return optLong(name).takeIf { it >= 0L }
}

private fun JSONObject.putNullable(name: String, value: Any?) {
    put(name, value ?: JSONObject.NULL)
}

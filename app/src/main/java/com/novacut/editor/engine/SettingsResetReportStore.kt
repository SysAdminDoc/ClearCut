package com.novacut.editor.engine

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class SettingsResetReport(
    val recordedAtEpochMs: Long,
    val reason: String,
    val errorType: String,
    val message: String?,
) {
    val id: String = "$recordedAtEpochMs:$errorType"
}

@Singleton
class SettingsResetReportStore internal constructor(
    private val reportFile: File,
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(defaultReportFile(context))

    @Synchronized
    fun recordCorruptionReset(
        error: Throwable,
        nowEpochMs: Long = System.currentTimeMillis(),
        retainCount: Int = DEFAULT_RETAIN_COUNT,
    ) {
        reportFile.parentFile?.mkdirs()
        reportFile.appendText(
            buildRecordJson(error, nowEpochMs).toString() + "\n",
            Charsets.UTF_8,
        )
        pruneOldRecords(retainCount)
    }

    @Synchronized
    fun latestReport(): SettingsResetReport? =
        readReports().maxByOrNull { it.recordedAtEpochMs }

    @Synchronized
    fun buildDiagnosticText(maxRecords: Int = DEFAULT_RETAIN_COUNT): String? {
        if (!reportFile.isFile) return null
        val lines = reportFile.readLines(Charsets.UTF_8).filter { it.isNotBlank() }
        if (lines.isEmpty()) return null
        return lines.takeLast(maxRecords).joinToString(separator = "\n", postfix = "\n")
    }

    @Synchronized
    fun readReports(): List<SettingsResetReport> {
        if (!reportFile.isFile) return emptyList()
        return reportFile.readLines(Charsets.UTF_8)
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                runCatching {
                    val obj = JSONObject(line)
                    SettingsResetReport(
                        recordedAtEpochMs = obj.optLong("recordedAtEpochMs", 0L).coerceAtLeast(0L),
                        reason = obj.optString("reason", DEFAULT_REASON),
                        errorType = obj.optString("errorType", "Unknown"),
                        message = obj.optStringOrNull("message"),
                    )
                }.getOrNull()
            }
    }

    private fun pruneOldRecords(retainCount: Int) {
        if (!reportFile.isFile) return
        val lines = reportFile.readLines(Charsets.UTF_8).filter { it.isNotBlank() }
        if (lines.size <= retainCount) return
        reportFile.writeText(
            lines.takeLast(retainCount).joinToString(separator = "\n", postfix = "\n"),
            Charsets.UTF_8,
        )
    }

    private fun buildRecordJson(error: Throwable, nowEpochMs: Long): JSONObject =
        JSONObject()
            .put("schema", SCHEMA)
            .put("recordedAtEpochMs", nowEpochMs.coerceAtLeast(0L))
            .put("reason", DEFAULT_REASON)
            .put("errorType", sanitizeToken(error.javaClass.simpleName.ifBlank { "Unknown" }))
            .put("message", error.message?.let(::sanitizeMessage) ?: JSONObject.NULL)

    private fun sanitizeToken(raw: String): String =
        raw.filter { it.isLetterOrDigit() || it == '_' || it == '-' }.take(MAX_TOKEN_CHARS)
            .ifBlank { "Unknown" }

    private fun sanitizeMessage(raw: String): String {
        var result = DiagnosticExportEngine.redactSensitive(raw)
        for (pattern in EXTRA_SECRET_PATTERNS) {
            result = pattern.replace(result) { match ->
                val label = match.groupValues.getOrNull(1)?.ifBlank { "credential" } ?: "credential"
                "$label=<redacted>"
            }
        }
        result = WINDOWS_PATH_PATTERN.replace(result, "<redacted>")
        return result
            .replace(Regex("""[\r\n\t]+"""), " ")
            .trim()
            .take(MAX_MESSAGE_CHARS)
    }

    private fun JSONObject.optStringOrNull(name: String): String? {
        if (!has(name) || isNull(name)) return null
        return opt(name)?.toString()
    }

    companion object {
        const val FILE_NAME = "settings-reset-report.jsonl"
        const val BUNDLE_ENTRY = "settings-reset-report.jsonl"
        const val SCHEMA = "com.novacut.settings-reset.v1"
        const val DEFAULT_REASON = "Preferences DataStore corruption recovery"
        const val DEFAULT_RETAIN_COUNT = 16
        private const val MAX_MESSAGE_CHARS = 240
        private const val MAX_TOKEN_CHARS = 80

        private val WINDOWS_PATH_PATTERN = Regex("""[A-Za-z]:\\[^\s)"']+""")
        private val EXTRA_SECRET_PATTERNS = listOf(
            Regex("""(?i)\b(acoustid(?:_api)?_?key)\s*[:=]\s*[^\s,;]+"""),
            Regex("""(?i)\b(api[_-]?key|token|password|secret)\s*[:=]\s*[^\s,;]+"""),
            Regex("""(?i)\b(proxy(?:User|Username|Password|Credential|Credentials)?)\s*[:=]\s*[^\s,;]+"""),
        )

        fun defaultReportFile(context: Context): File =
            File(File(context.filesDir, DiagnosticExportEngine.DIAG_DIR), FILE_NAME)

        internal fun forFile(reportFile: File): SettingsResetReportStore =
            SettingsResetReportStore(reportFile)
    }
}

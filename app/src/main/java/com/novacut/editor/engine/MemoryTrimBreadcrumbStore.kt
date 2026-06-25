package com.novacut.editor.engine

import java.io.File

class MemoryTrimBreadcrumbStore internal constructor(
    private val breadcrumbFile: File,
) {

    @Synchronized
    fun record(
        decision: MemoryTrimDecision,
        results: List<MemoryTrimRegistry.DispatchResult>,
        nowEpochMs: Long = System.currentTimeMillis(),
        retainCount: Int = DEFAULT_RETAIN_COUNT,
    ) {
        breadcrumbFile.parentFile?.mkdirs()
        breadcrumbFile.appendText(
            buildRecordJson(decision, results, nowEpochMs) + "\n",
            Charsets.UTF_8,
        )
        pruneOldRecords(retainCount)
    }

    @Synchronized
    fun buildDiagnosticText(maxRecords: Int = DEFAULT_RETAIN_COUNT): String? {
        if (!breadcrumbFile.isFile) return null
        val lines = breadcrumbFile.readLines(Charsets.UTF_8).filter { it.isNotBlank() }
        if (lines.isEmpty()) return null
        return lines.takeLast(maxRecords).joinToString(separator = "\n", postfix = "\n")
    }

    private fun pruneOldRecords(retainCount: Int) {
        if (!breadcrumbFile.isFile) return
        val lines = breadcrumbFile.readLines(Charsets.UTF_8).filter { it.isNotBlank() }
        if (lines.size <= retainCount) return
        breadcrumbFile.writeText(
            lines.takeLast(retainCount).joinToString(separator = "\n", postfix = "\n"),
            Charsets.UTF_8,
        )
    }

    private fun buildRecordJson(
        decision: MemoryTrimDecision,
        results: List<MemoryTrimRegistry.DispatchResult>,
        nowEpochMs: Long,
    ): String = buildString {
        append("{")
        append("\"schema\":\"").append(SCHEMA).append("\",")
        append("\"recordedAtEpochMs\":").append(nowEpochMs).append(",")
        append("\"level\":").append(decision.level).append(",")
        append("\"levelName\":\"").append(escapeJson(decision.levelName)).append("\",")
        append("\"reason\":\"").append(decision.reason.name).append("\",")
        append("\"requestedActions\":")
        appendStringArray(decision.actions.map { it.name })
        append(",\"targetResults\":[")
        results.forEachIndexed { index, result ->
            if (index > 0) append(",")
            append("{")
            append("\"action\":\"").append(result.action.name).append("\",")
            append("\"targetName\":\"")
                .append(escapeJson(DiagnosticExportEngine.redactSensitive(result.targetName)))
                .append("\",")
            append("\"succeeded\":").append(result.succeeded)
            result.errorType?.let { errorType ->
                append(",\"errorType\":\"").append(escapeJson(errorType)).append("\"")
            }
            append("}")
        }
        append("]}")
    }

    private fun StringBuilder.appendStringArray(values: List<String>) {
        append("[")
        values.forEachIndexed { index, value ->
            if (index > 0) append(",")
            append("\"").append(escapeJson(value)).append("\"")
        }
        append("]")
    }

    private fun escapeJson(raw: String): String = buildString(raw.length + 8) {
        raw.forEach { c ->
            when (c) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (c.code < 0x20) {
                    append("\\u%04x".format(c.code))
                } else {
                    append(c)
                }
            }
        }
    }

    companion object {
        const val FILE_NAME = "memory-trim.jsonl"
        const val BUNDLE_ENTRY = "memory-trim.jsonl"
        const val SCHEMA = "com.clearcut.memory-trim.v1"
        const val DEFAULT_RETAIN_COUNT = 64

        fun forDiagnosticsDir(diagnosticsDir: File): MemoryTrimBreadcrumbStore =
            MemoryTrimBreadcrumbStore(File(diagnosticsDir, FILE_NAME))

        fun forContextFilesDir(filesDir: File): MemoryTrimBreadcrumbStore =
            forDiagnosticsDir(File(filesDir, DiagnosticExportEngine.DIAG_DIR))
    }
}

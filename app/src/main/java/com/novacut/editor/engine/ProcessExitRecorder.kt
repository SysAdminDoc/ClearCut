package com.novacut.editor.engine

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

data class ProcessExitSnapshot(
    val timestampEpochMs: Long,
    val reasonCode: Int,
    val status: Int,
    val pid: Int,
    val processName: String,
    val importance: Int,
    val pssKb: Long,
    val rssKb: Long,
    val description: String? = null,
    val traceExcerpt: String? = null,
)

interface ProcessExitSource {
    val supported: Boolean
    val lowMemoryKillReportSupported: Boolean?
    fun recentExitRecords(maxRecords: Int): List<ProcessExitSnapshot>
}

@Singleton
class ProcessExitRecorder private constructor(
    private val historyFile: File,
    private val source: ProcessExitSource,
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(
        historyFile = defaultHistoryFile(context),
        source = AndroidProcessExitSource(context)
    )

    fun recordStartupExitReasons(
        nowEpochMs: Long = System.currentTimeMillis(),
        maxSourceRecords: Int = DEFAULT_SOURCE_RECORDS,
        retainCount: Int = DEFAULT_RETAIN_COUNT,
    ) {
        val existing = readHistoryRecords()
        val incoming = if (source.supported) {
            runCatching { source.recentExitRecords(maxSourceRecords.coerceAtLeast(0)) }
                .getOrDefault(emptyList())
        } else {
            emptyList()
        }
        val merged = (incoming + existing)
            .distinctBy { recordKey(it) }
            .sortedWith(compareByDescending<ProcessExitSnapshot> { it.timestampEpochMs }.thenByDescending { it.pid })
            .take(retainCount.coerceAtLeast(0))
        writeHistory(
            records = merged,
            capturedAtEpochMs = nowEpochMs.coerceAtLeast(0L),
            unsupportedReason = if (source.supported) null else "ApplicationExitInfo requires Android 11 / API 30 or newer."
        )
    }

    fun buildDiagnosticJson(): String {
        return if (historyFile.exists()) {
            runCatching { JSONObject(historyFile.readText(Charsets.UTF_8)).toString(2) }
                .getOrElse { defaultHistoryJson(capturedAtEpochMs = 0L).toString(2) }
        } else {
            defaultHistoryJson(capturedAtEpochMs = 0L).toString(2)
        }
    }

    private fun writeHistory(
        records: List<ProcessExitSnapshot>,
        capturedAtEpochMs: Long,
        unsupportedReason: String?,
    ) {
        historyFile.parentFile?.mkdirs()
        writeUtf8TextAtomically(
            historyFile,
            buildHistoryJson(
                records = records,
                capturedAtEpochMs = capturedAtEpochMs,
                unsupportedReason = unsupportedReason,
            ).toString(2)
        )
    }

    private fun defaultHistoryJson(capturedAtEpochMs: Long): JSONObject {
        return buildHistoryJson(
            records = emptyList(),
            capturedAtEpochMs = capturedAtEpochMs,
            unsupportedReason = if (source.supported) null else "ApplicationExitInfo requires Android 11 / API 30 or newer."
        )
    }

    private fun buildHistoryJson(
        records: List<ProcessExitSnapshot>,
        capturedAtEpochMs: Long,
        unsupportedReason: String?,
    ): JSONObject {
        val arr = JSONArray()
        records.forEach { snapshot -> arr.put(snapshot.toJson()) }
        return JSONObject()
            .put("schema", SCHEMA)
            .put("supported", source.supported)
            .put("lowMemoryKillReportSupported", source.lowMemoryKillReportSupported ?: JSONObject.NULL)
            .put("capturedAtEpochMs", capturedAtEpochMs)
            .put("unsupportedReason", unsupportedReason ?: JSONObject.NULL)
            .put("recordCount", arr.length())
            .put("records", arr)
    }

    private fun ProcessExitSnapshot.toJson(): JSONObject {
        return JSONObject()
            .put("timestampEpochMs", timestampEpochMs.coerceAtLeast(0L))
            .put("reasonCode", reasonCode)
            .put("reason", reasonName(reasonCode))
            .put("status", status)
            .put("pid", pid)
            .put("processName", sanitizeShort(processName))
            .put("importance", importance)
            .put("importanceName", importanceName(importance))
            .put("pssKb", pssKb.coerceAtLeast(0L))
            .put("rssKb", rssKb.coerceAtLeast(0L))
            .put("description", description?.let(::sanitizeShort) ?: JSONObject.NULL)
            .put("traceExcerpt", traceExcerpt?.let(::sanitizeTraceExcerpt) ?: JSONObject.NULL)
    }

    private fun readHistoryRecords(): List<ProcessExitSnapshot> {
        if (!historyFile.exists()) return emptyList()
        return runCatching {
            val arr = JSONObject(historyFile.readText(Charsets.UTF_8)).optJSONArray("records") ?: return@runCatching emptyList()
            (0 until arr.length()).mapNotNull { index ->
                val obj = arr.optJSONObject(index) ?: return@mapNotNull null
                ProcessExitSnapshot(
                    timestampEpochMs = obj.optLong("timestampEpochMs", -1L),
                    reasonCode = obj.optInt("reasonCode", REASON_UNKNOWN),
                    status = obj.optInt("status", 0),
                    pid = obj.optInt("pid", 0),
                    processName = obj.optString("processName", ""),
                    importance = obj.optInt("importance", 0),
                    pssKb = obj.optLong("pssKb", 0L),
                    rssKb = obj.optLong("rssKb", 0L),
                    description = obj.optStringOrNull("description"),
                    traceExcerpt = obj.optStringOrNull("traceExcerpt"),
                )
            }
        }.getOrDefault(emptyList())
    }

    private class AndroidProcessExitSource(
        private val context: Context,
    ) : ProcessExitSource {
        override val supported: Boolean
            get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

        override val lowMemoryKillReportSupported: Boolean?
            get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ActivityManager.isLowMemoryKillReportSupported()
            } else {
                null
            }

        override fun recentExitRecords(maxRecords: Int): List<ProcessExitSnapshot> {
            if (!supported || maxRecords <= 0) return emptyList()
            val manager = context.getSystemService(ActivityManager::class.java) ?: return emptyList()
            return manager.getHistoricalProcessExitReasons(null, 0, maxRecords)
                .mapNotNull { info -> info.toSnapshot() }
        }

        @RequiresApi(Build.VERSION_CODES.R)
        private fun ApplicationExitInfo.toSnapshot(): ProcessExitSnapshot? {
            return ProcessExitSnapshot(
                timestampEpochMs = timestamp,
                reasonCode = reason,
                status = status,
                pid = pid,
                processName = processName,
                importance = importance,
                pssKb = pss,
                rssKb = rss,
                description = description,
                traceExcerpt = readTraceExcerpt(this),
            )
        }

        @RequiresApi(Build.VERSION_CODES.R)
        private fun readTraceExcerpt(info: ApplicationExitInfo): String? {
            return runCatching {
                info.traceInputStream?.use { stream ->
                    readTraceWithLimit(stream)
                }
            }.getOrNull()
        }
    }

    companion object {
        const val BUNDLE_ENTRY = "process-exit-history.json"
        const val SCHEMA = "com.novacut.process-exit-history.v1"
        const val DEFAULT_SOURCE_RECORDS = 16
        const val DEFAULT_RETAIN_COUNT = 16
        private const val MAX_SHORT_TEXT_CHARS = 180
        private const val MAX_TRACE_BYTES = 16_384L
        private const val MAX_TRACE_LINES = 40
        private const val MAX_TRACE_LINE_CHARS = 220

        internal const val REASON_UNKNOWN = 0
        private const val REASON_EXIT_SELF = 1
        private const val REASON_SIGNALED = 2
        private const val REASON_LOW_MEMORY = 3
        private const val REASON_CRASH = 4
        private const val REASON_CRASH_NATIVE = 5
        private const val REASON_ANR = 6
        private const val REASON_INITIALIZATION_FAILURE = 7
        private const val REASON_PERMISSION_CHANGE = 8
        private const val REASON_EXCESSIVE_RESOURCE_USAGE = 9
        private const val REASON_USER_REQUESTED = 10
        private const val REASON_USER_STOPPED = 11
        private const val REASON_DEPENDENCY_DIED = 12
        private const val REASON_OTHER = 13
        private const val REASON_FREEZER = 14
        private const val REASON_PACKAGE_STATE_CHANGE = 15
        private const val REASON_PACKAGE_UPDATED = 16

        fun defaultHistoryFile(context: Context): File =
            File(File(context.filesDir, DiagnosticExportEngine.DIAG_DIR), BUNDLE_ENTRY)

        internal fun forFile(historyFile: File, source: ProcessExitSource): ProcessExitRecorder =
            ProcessExitRecorder(historyFile, source)

        internal fun reasonName(reason: Int): String = when (reason) {
            REASON_EXIT_SELF -> "EXIT_SELF"
            REASON_SIGNALED -> "SIGNALED"
            REASON_LOW_MEMORY -> "LOW_MEMORY"
            REASON_CRASH -> "CRASH"
            REASON_CRASH_NATIVE -> "CRASH_NATIVE"
            REASON_ANR -> "ANR"
            REASON_INITIALIZATION_FAILURE -> "INITIALIZATION_FAILURE"
            REASON_PERMISSION_CHANGE -> "PERMISSION_CHANGE"
            REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE"
            REASON_USER_REQUESTED -> "USER_REQUESTED"
            REASON_USER_STOPPED -> "USER_STOPPED"
            REASON_DEPENDENCY_DIED -> "DEPENDENCY_DIED"
            REASON_OTHER -> "OTHER"
            REASON_FREEZER -> "FREEZER"
            REASON_PACKAGE_STATE_CHANGE -> "PACKAGE_STATE_CHANGE"
            REASON_PACKAGE_UPDATED -> "PACKAGE_UPDATED"
            else -> "UNKNOWN"
        }

        internal fun importanceName(importance: Int): String = when (importance) {
            100 -> "FOREGROUND"
            125 -> "FOREGROUND_SERVICE"
            150 -> "TOP_SLEEPING"
            200 -> "VISIBLE"
            230 -> "PERCEPTIBLE"
            300 -> "SERVICE"
            350 -> "CANT_SAVE_STATE"
            400 -> "CACHED"
            1000 -> "GONE"
            else -> "UNKNOWN"
        }

        internal fun sanitizeTraceExcerpt(raw: String): String {
            val redacted = DiagnosticExportEngine.redactSensitive(raw)
                .replace(Regex("""(?i)(caption|transcript|projectName|project_name|mediaUri|sourceUri)\s*[:=]\s*[^\r\n]+""")) {
                    "${it.groupValues[1]}=<redacted>"
                }
            return redacted
                .lineSequence()
                .map { sanitizeShort(it).take(MAX_TRACE_LINE_CHARS) }
                .filter { it.isNotBlank() }
                .take(MAX_TRACE_LINES)
                .joinToString("\n")
        }

        internal fun sanitizeShort(raw: String): String {
            return DiagnosticExportEngine.redactSensitive(raw)
                .replace(Regex("""[\r\t]+"""), " ")
                .trim()
                .take(MAX_SHORT_TEXT_CHARS)
        }

        private fun recordKey(snapshot: ProcessExitSnapshot): String =
            "${snapshot.timestampEpochMs}:${snapshot.reasonCode}:${snapshot.pid}"

        private fun readTraceWithLimit(input: InputStream): String =
            readUtf8WithByteLimit(input, MAX_TRACE_BYTES)

        private fun JSONObject.optStringOrNull(name: String): String? {
            if (!has(name) || isNull(name)) return null
            return opt(name)?.toString()
        }
    }
}

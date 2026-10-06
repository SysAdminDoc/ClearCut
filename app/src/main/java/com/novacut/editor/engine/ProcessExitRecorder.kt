package com.novacut.editor.engine

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.os.ProfilingManager
import android.os.ProfilingResult
import android.os.ProfilingTrigger
import androidx.annotation.RequiresApi
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.util.concurrent.Executors
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
    /** For a memory kill: the device's RAM and what the killed run was holding, as JSON. */
    val memoryContext: String? = null,
)

/** The device's memory as Android reports it. */
data class DeviceMemory(
    val totalBytes: Long,
    val memoryClassMb: Int,
    val largeMemoryClassMb: Int,
    val lowRamDevice: Boolean,
)

/** What ProfilingManager handed back for a heap dump trigger. */
data class HeapDumpResult(
    val triggerType: Int,
    val errorCode: Int,
    val errorMessage: String?,
    val filePath: String?,
)

interface ProcessExitSource {
    val supported: Boolean
    val lowMemoryKillReportSupported: Boolean?
    fun recentExitRecords(maxRecords: Int): List<ProcessExitSnapshot>

    /** Null when Android can't say. */
    fun deviceMemory(): DeviceMemory? = null

    /**
     * Asks Android for a heap dump when the app runs out of memory or hits its
     * memory limit. False where that isn't available, which is before Android 17.
     */
    fun registerHeapDumpTriggers(onResult: (HeapDumpResult) -> Unit): Boolean = false
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

    private val diagnosticsDir: File get() = historyFile.absoluteFile.parentFile ?: File(".")
    private val budgetFile: File get() = File(diagnosticsDir, BUDGET_FILE)
    private val heapDumpIndexFile: File get() = File(diagnosticsDir, HEAP_DUMP_INDEX_FILE)

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
        // Android lists the same exits again on every launch; only ones this history
        // hasn't seen happened in the run that just ended, so only they get its budget.
        val known = existing.mapTo(HashSet()) { recordKey(it) }
        val fresh = incoming.filter { recordKey(it) !in known }.distinctBy { recordKey(it) }
        val memoryContext by lazy {
            buildMemoryContext(
                device = runCatching { source.deviceMemory() }.getOrNull(),
                lastBudget = MemoryBudgetLedger.read(budgetFile),
            ).toString()
        }
        val annotated = fresh.map { snapshot ->
            if (isMemoryKill(snapshot)) snapshot.copy(memoryContext = memoryContext) else snapshot
        }
        val merged = (annotated + existing)
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
        val history = if (historyFile.exists()) {
            runCatching { JSONObject(historyFile.readText(Charsets.UTF_8)) }
                .getOrElse { defaultHistoryJson(capturedAtEpochMs = 0L) }
        } else {
            defaultHistoryJson(capturedAtEpochMs = 0L)
        }
        // Where a dump sits on the phone stays out of the bundle; its name and size don't.
        val dumps = JSONArray()
        readHeapDumpIndex().forEach { entry ->
            dumps.put(JSONObject(entry.toString()).apply { remove("path") })
        }
        return history.put("heapDumps", dumps).toString(2)
    }

    /**
     * Starts this run's memory budget ledger and, on Android 17 and later, asks for
     * a heap dump on an out-of-memory error or a memory-limit kill. Call it after
     * [recordStartupExitReasons], which reads what the previous run left.
     */
    fun startMemoryCapture() {
        MemoryBudgetLedger.install(budgetFile)
        val registered = runCatching {
            source.registerHeapDumpTriggers { result -> recordHeapDump(result) }
        }.getOrElse { error ->
            AppLog.w(TAG, "Heap dump triggers could not be registered", error)
            false
        }
        AppLog.d(TAG, "Heap dump triggers ${if (registered) "registered" else "not available on this Android version"}")
    }

    /** Notes a ProfilingManager result and keeps only the newest dump file, since they run to hundreds of megabytes. */
    @Synchronized
    fun recordHeapDump(result: HeapDumpResult, nowEpochMs: Long = System.currentTimeMillis()) {
        val dump = result.filePath
            ?.let(::File)
            ?.takeIf { result.errorCode == PROFILING_ERROR_NONE && it.isFile }
        val previous = readHeapDumpIndex()
        if (dump != null) {
            previous.mapNotNull { it.optStringOrNull("path") }
                .map(::File)
                .filter { it.absolutePath != dump.absolutePath && isInsideAppData(it) }
                .forEach { stale -> runCatching { stale.delete() } }
        }
        val entry = JSONObject()
            .put("receivedAtEpochMs", nowEpochMs.coerceAtLeast(0L))
            .put("trigger", heapDumpTriggerName(result.triggerType))
            .put("result", profilingErrorName(result.errorCode))
            .put("errorMessage", result.errorMessage?.let(::sanitizeShort) ?: JSONObject.NULL)
            .put("fileName", dump?.name?.let(::sanitizeShort) ?: JSONObject.NULL)
            .put("sizeBytes", dump?.length() ?: 0L)
            .put("path", dump?.absolutePath ?: JSONObject.NULL)
        val kept = (listOf(entry) + previous.map { old ->
            old.apply { if (dump != null) put("path", JSONObject.NULL) }
        }).take(MAX_HEAP_DUMP_RECORDS)
        writeUtf8TextAtomically(heapDumpIndexFile, JSONArray(kept).toString(2))
    }

    /** The newest heap dump still on disk, for a diagnostic ZIP the user opted into. */
    fun latestHeapDumpFile(): File? = readHeapDumpIndex()
        .firstNotNullOfOrNull { entry -> entry.optStringOrNull("path")?.let(::File)?.takeIf { it.isFile } }

    private fun readHeapDumpIndex(): List<JSONObject> {
        if (!heapDumpIndexFile.isFile) return emptyList()
        return runCatching {
            val arr = JSONArray(heapDumpIndexFile.readText(Charsets.UTF_8))
            (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
        }.getOrDefault(emptyList())
    }

    // Only files under the app's own data directory are ever deleted.
    private fun isInsideAppData(file: File): Boolean {
        val appData = diagnosticsDir.parentFile?.parentFile ?: return false
        return runCatching { file.canonicalPath.startsWith(appData.canonicalPath + File.separator) }.getOrDefault(false)
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
        val resolvedReason = if (reasonCode == REASON_OTHER && isMemoryLimiterKill(description)) {
            "MEMORY_LIMITER"
        } else {
            reasonName(reasonCode)
        }
        return JSONObject()
            .put("timestampEpochMs", timestampEpochMs.coerceAtLeast(0L))
            .put("reasonCode", reasonCode)
            .put("reason", resolvedReason)
            .put("status", status)
            .put("pid", pid)
            .put("processName", sanitizeShort(processName))
            .put("importance", importance)
            .put("importanceName", importanceName(importance))
            .put("pssKb", pssKb.coerceAtLeast(0L))
            .put("rssKb", rssKb.coerceAtLeast(0L))
            .put("description", description?.let(::sanitizeShort) ?: JSONObject.NULL)
            .put("traceExcerpt", traceExcerpt?.let(::sanitizeTraceExcerpt) ?: JSONObject.NULL)
            .put("memoryContext", memoryContext?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject.NULL)
    }

    /** The saved exit history, newest first. */
    fun readHistoryRecords(): List<ProcessExitSnapshot> {
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
                    memoryContext = obj.optJSONObject("memoryContext")?.toString(),
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

        override fun deviceMemory(): DeviceMemory? {
            val manager = context.getSystemService(ActivityManager::class.java) ?: return null
            val info = ActivityManager.MemoryInfo().also(manager::getMemoryInfo)
            return DeviceMemory(
                totalBytes = info.totalMem,
                memoryClassMb = manager.memoryClass,
                largeMemoryClassMb = manager.largeMemoryClass,
                lowRamDevice = manager.isLowRamDevice,
            )
        }

        override fun registerHeapDumpTriggers(onResult: (HeapDumpResult) -> Unit): Boolean =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN && registerProfilingTriggers(onResult)

        @RequiresApi(Build.VERSION_CODES.CINNAMON_BUN)
        private fun registerProfilingTriggers(onResult: (HeapDumpResult) -> Unit): Boolean {
            val profiling = context.getSystemService(ProfilingManager::class.java) ?: return false
            profiling.registerForAllProfilingResults(Executors.newSingleThreadExecutor()) { result: ProfilingResult ->
                if (result.triggerType in HEAP_DUMP_TRIGGERS) {
                    onResult(HeapDumpResult(result.triggerType, result.errorCode, result.errorMessage, result.resultFilePath))
                }
            }
            profiling.addProfilingTriggers(HEAP_DUMP_TRIGGERS.map { ProfilingTrigger.Builder(it).build() })
            return true
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
        internal const val BUDGET_FILE = "memory-budget.json"
        private const val HEAP_DUMP_INDEX_FILE = "heap-dumps.json"
        private const val MAX_HEAP_DUMP_RECORDS = 8
        private const val TAG = "ProcessExitRecorder"
        private const val BYTES_PER_MB = 1024L * 1024L

        // ProfilingTrigger.TRIGGER_TYPE_OOM and TRIGGER_TYPE_ANOMALY (the memory
        // limiter's heap dump), both API 37; plain ints keep older builds loading.
        internal const val TRIGGER_TYPE_OOM = 7
        internal const val TRIGGER_TYPE_ANOMALY = 8
        private val HEAP_DUMP_TRIGGERS = listOf(TRIGGER_TYPE_OOM, TRIGGER_TYPE_ANOMALY)
        private const val PROFILING_ERROR_NONE = 0
        const val SCHEMA = "com.clearcut.process-exit-history.v1"
        const val DEFAULT_SOURCE_RECORDS = 16
        const val DEFAULT_RETAIN_COUNT = 16
        private const val MAX_SHORT_TEXT_CHARS = 180
        private const val MAX_TRACE_BYTES = 16_384L
        private const val MAX_TRACE_LINES = 40
        private const val MAX_TRACE_LINE_CHARS = 220

        internal const val REASON_UNKNOWN = 0
        private const val REASON_EXIT_SELF = 1
        private const val REASON_SIGNALED = 2
        internal const val REASON_LOW_MEMORY = 3
        internal const val REASON_CRASH = 4
        internal const val REASON_CRASH_NATIVE = 5
        internal const val REASON_ANR = 6
        internal const val REASON_INITIALIZATION_FAILURE = 7
        private const val REASON_PERMISSION_CHANGE = 8
        internal const val REASON_EXCESSIVE_RESOURCE_USAGE = 9
        private const val REASON_USER_REQUESTED = 10
        private const val REASON_USER_STOPPED = 11
        private const val REASON_DEPENDENCY_DIED = 12
        internal const val REASON_OTHER = 13
        private const val REASON_FREEZER = 14
        private const val REASON_PACKAGE_STATE_CHANGE = 15
        private const val REASON_PACKAGE_UPDATED = 16

        fun defaultHistoryFile(context: Context): File =
            File(File(context.filesDir, DiagnosticExportEngine.DIAG_DIR), BUNDLE_ENTRY)

        internal fun forFile(historyFile: File, source: ProcessExitSource): ProcessExitRecorder =
            ProcessExitRecorder(historyFile, source)

        /**
         * Android 17 introduces per-app memory limits on a subset of devices.
         * When the limit is exceeded, the app is killed with REASON_OTHER and
         * the description contains "MemoryLimiter:AnonSwap". Detecting this
         * surfaces memory-pressure kills in diagnostic ZIPs.
         */
        internal fun isMemoryLimiterKill(description: String?): Boolean =
            description?.contains("MemoryLimiter:AnonSwap") == true

        /** A kill for memory: Android's low-memory killer, or the Android 17 memory limiter. */
        internal fun isMemoryKill(snapshot: ProcessExitSnapshot): Boolean =
            snapshot.reasonCode == REASON_LOW_MEMORY ||
                (snapshot.reasonCode == REASON_OTHER && isMemoryLimiterKill(snapshot.description))

        /**
         * The marketed RAM size a device's reported total memory belongs to. The
         * kernel keeps some back, so an 8 GB phone reports about 7.3 GiB.
         */
        internal fun ramTierGb(totalBytes: Long): Int {
            if (totalBytes <= 0L) return 0
            val gib = totalBytes.toDouble() / (1L shl 30)
            return RAM_TIERS_GB.firstOrNull { gib <= it } ?: kotlin.math.ceil(gib).toInt()
        }

        private val RAM_TIERS_GB = listOf(1, 2, 3, 4, 6, 8, 12, 16, 24, 32)

        internal fun buildMemoryContext(device: DeviceMemory?, lastBudget: JSONObject?): JSONObject =
            JSONObject()
                .put("ramTierGb", device?.let { ramTierGb(it.totalBytes) } ?: JSONObject.NULL)
                .put("totalRamMb", device?.let { it.totalBytes / BYTES_PER_MB } ?: JSONObject.NULL)
                .put("memoryClassMb", device?.memoryClassMb ?: JSONObject.NULL)
                .put("largeMemoryClassMb", device?.largeMemoryClassMb ?: JSONObject.NULL)
                .put("lowRamDevice", device?.lowRamDevice ?: JSONObject.NULL)
                // Null when the killed run never noted a budget: no big decode, no model loaded.
                .put("lastBudget", lastBudget ?: JSONObject.NULL)

        internal fun heapDumpTriggerName(trigger: Int): String = when (trigger) {
            TRIGGER_TYPE_OOM -> "OUT_OF_MEMORY_ERROR"
            TRIGGER_TYPE_ANOMALY -> "MEMORY_LIMIT"
            else -> "TRIGGER_$trigger"
        }

        internal fun profilingErrorName(code: Int): String = when (code) {
            PROFILING_ERROR_NONE -> "OK"
            1 -> "RATE_LIMIT_SYSTEM"
            2 -> "RATE_LIMIT_PROCESS"
            3 -> "PROFILING_IN_PROGRESS"
            4 -> "FAILED_EXECUTING"
            5 -> "FAILED_POST_PROCESSING"
            6 -> "NO_DISK_SPACE"
            7 -> "INVALID_REQUEST"
            else -> "UNKNOWN_$code"
        }

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

        internal const val IMPORTANCE_PERCEPTIBLE = 230

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

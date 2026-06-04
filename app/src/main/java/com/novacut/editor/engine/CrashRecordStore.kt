package com.novacut.editor.engine

import android.content.Context
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.Collections
import java.util.IdentityHashMap
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.system.exitProcess

/**
 * Local-only fatal-crash breadcrumb store.
 *
 * The default uncaught-exception handler runs while the process is already
 * failing, so this class does no network work and keeps writes short: one
 * bounded JSON record under filesDir/diagnostics/crashes, then the previous
 * platform handler is invoked.
 */
@Singleton
class CrashRecordStore private constructor(
    private val recordsDir: File,
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(defaultRecordsDir(context))

    fun installGlobalHandler(appVersion: String) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        if (previous is RecordingUncaughtExceptionHandler) return
        Thread.setDefaultUncaughtExceptionHandler(
            RecordingUncaughtExceptionHandler(
                store = this,
                previous = previous,
                appVersion = appVersion,
            )
        )
    }

    fun recordUncaughtException(
        thread: Thread,
        throwable: Throwable,
        appVersion: String,
        nowEpochMs: Long = System.currentTimeMillis(),
        retainCount: Int = DEFAULT_RETAIN_COUNT,
    ): File {
        val safeNow = nowEpochMs.coerceAtLeast(0L)
        val file = File(recordsDir, "crash-$safeNow-${stableThreadId(thread)}.json")
        writeUtf8TextAtomically(
            file,
            buildRecordJson(thread, throwable, appVersion, safeNow).toString(2),
        )
        pruneOldRecords(retainCount)
        return file
    }

    fun buildDiagnosticJson(maxRecords: Int = DEFAULT_RETAIN_COUNT): String? {
        val files = recordFiles()
            .take(maxRecords.coerceAtLeast(0))
            .asReversed()
        if (files.isEmpty()) return null
        val records = JSONArray()
        for (file in files) {
            val record = runCatching { JSONObject(file.readText(Charsets.UTF_8)) }
                .getOrElse {
                    JSONObject()
                        .put("schema", RECORD_SCHEMA)
                        .put("recordStatus", "unreadable")
                        .put("fileName", sanitizeText(file.name, MAX_SHORT_TEXT_CHARS))
                }
            records.put(record)
        }
        return JSONObject()
            .put("schema", BUNDLE_SCHEMA)
            .put("recordCount", records.length())
            .put("records", records)
            .toString(2)
    }

    fun pruneOldRecords(retainCount: Int = DEFAULT_RETAIN_COUNT) {
        recordFiles()
            .drop(retainCount.coerceAtLeast(0))
            .forEach { file -> runCatching { file.delete() } }
    }

    private fun recordFiles(): List<File> {
        return recordsDir
            .listFiles { file -> file.isFile && file.name.endsWith(".json") }
            ?.sortedWith(compareByDescending<File> { it.lastModified() }.thenByDescending { it.name })
            ?: emptyList()
    }

    private fun buildRecordJson(
        thread: Thread,
        throwable: Throwable,
        appVersion: String,
        nowEpochMs: Long,
    ): JSONObject {
        return JSONObject()
            .put("schema", RECORD_SCHEMA)
            .put("recordedAtEpochMs", nowEpochMs)
            .put("appVersion", sanitizeText(appVersion, MAX_SHORT_TEXT_CHARS))
            .put("thread", buildThreadJson(thread))
            .put("device", buildDeviceJson())
            .put("throwable", buildThrowableJson(throwable))
    }

    private fun buildThreadJson(thread: Thread): JSONObject {
        return JSONObject()
            .put("id", stableThreadId(thread))
            .put("name", sanitizeText(thread.name, MAX_SHORT_TEXT_CHARS))
            .put("state", thread.state.name)
    }

    @Suppress("DEPRECATION")
    private fun stableThreadId(thread: Thread): Long = thread.id

    private fun buildDeviceJson(): JSONObject {
        return JSONObject()
            .put("sdkInt", Build.VERSION.SDK_INT)
            .put("release", sanitizeNullableText(Build.VERSION.RELEASE, MAX_SHORT_TEXT_CHARS))
            .put("manufacturer", sanitizeNullableText(Build.MANUFACTURER, MAX_SHORT_TEXT_CHARS))
            .put("model", sanitizeNullableText(Build.MODEL, MAX_SHORT_TEXT_CHARS))
    }

    private fun buildThrowableJson(root: Throwable): JSONObject {
        val seen = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
        var current: Throwable? = root
        var depth = 0
        val chain = JSONArray()
        while (current != null && depth < MAX_CAUSE_DEPTH && seen.add(current)) {
            chain.put(
                JSONObject()
                    .put("className", sanitizeText(current.javaClass.name, MAX_SHORT_TEXT_CHARS))
                    .put("messagePresent", current.message != null)
                    .put("messageSha256", current.message?.let { sha256Hex(it) } ?: JSONObject.NULL)
                    .put("stackTrace", buildStackTraceJson(current.stackTrace))
            )
            current = current.cause
            depth += 1
        }
        return JSONObject()
            .put("rootClassName", sanitizeText(root.javaClass.name, MAX_SHORT_TEXT_CHARS))
            .put("causeDepth", chain.length())
            .put("causes", chain)
    }

    private fun buildStackTraceJson(frames: Array<StackTraceElement>): JSONArray {
        val arr = JSONArray()
        frames.take(MAX_STACK_FRAMES).forEach { frame ->
            arr.put(
                JSONObject()
                    .put("className", sanitizeText(frame.className, MAX_LONG_TEXT_CHARS))
                    .put("methodName", sanitizeText(frame.methodName, MAX_SHORT_TEXT_CHARS))
                    .put("fileName", frame.fileName?.let { sanitizeText(it, MAX_SHORT_TEXT_CHARS) } ?: JSONObject.NULL)
                    .put("lineNumber", frame.lineNumber)
            )
        }
        return arr
    }

    private class RecordingUncaughtExceptionHandler(
        private val store: CrashRecordStore,
        private val previous: Thread.UncaughtExceptionHandler?,
        private val appVersion: String,
    ) : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(thread: Thread, throwable: Throwable) {
            runCatching {
                store.recordUncaughtException(thread, throwable, appVersion)
            }
            val prior = previous
            if (prior != null && prior !== this) {
                prior.uncaughtException(thread, throwable)
            } else {
                android.os.Process.killProcess(android.os.Process.myPid())
                exitProcess(10)
            }
        }
    }

    companion object {
        const val CRASH_SUBDIR = "crashes"
        const val CRASH_BUNDLE_ENTRY = "crash-records.json"
        const val RECORD_SCHEMA = "com.novacut.crash-record.v1"
        const val BUNDLE_SCHEMA = "com.novacut.crash-records.v1"
        const val DEFAULT_RETAIN_COUNT = 8
        private const val MAX_CAUSE_DEPTH = 4
        private const val MAX_STACK_FRAMES = 32
        private const val MAX_SHORT_TEXT_CHARS = 160
        private const val MAX_LONG_TEXT_CHARS = 320

        fun defaultRecordsDir(context: Context): File =
            File(File(context.filesDir, DiagnosticExportEngine.DIAG_DIR), CRASH_SUBDIR)

        internal fun forDirectory(recordsDir: File): CrashRecordStore =
            CrashRecordStore(recordsDir)

        internal fun sanitizeText(raw: String, maxChars: Int): String {
            return DiagnosticExportEngine.redactSensitive(raw)
                .replace(Regex("""[\r\n\t]+"""), " ")
                .trim()
                .take(maxChars.coerceAtLeast(0))
        }

        private fun sanitizeNullableText(raw: String?, maxChars: Int): Any {
            return raw?.let { sanitizeText(it, maxChars) } ?: JSONObject.NULL
        }

        private fun sha256Hex(raw: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(raw.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}

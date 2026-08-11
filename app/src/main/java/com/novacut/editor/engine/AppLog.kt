package com.novacut.editor.engine

import android.util.Log as AndroidLog
import com.novacut.editor.BuildConfig

/**
 * The single application logging boundary.
 *
 * Verbose and debug messages are available in debug builds and are suppressed
 * by default in release builds. A caller may raise the minimum level at
 * runtime (for example, while diagnosing a local build), but release builds
 * can never lower it below INFO. Messages and throwable text pass through the
 * same conservative redaction used by diagnostic exports before reaching
 * logcat.
 */
object AppLog {
    enum class Level(val priority: Int) {
        VERBOSE(AndroidLog.VERBOSE),
        DEBUG(AndroidLog.DEBUG),
        INFO(AndroidLog.INFO),
        WARN(AndroidLog.WARN),
        ERROR(AndroidLog.ERROR),
        ASSERT(AndroidLog.ASSERT),
    }

    private val releaseFloor = if (BuildConfig.DEBUG) Level.VERBOSE else Level.INFO

    @Volatile
    private var configuredMinimum = releaseFloor

    val minimumLevel: Level
        get() = configuredMinimum

    /** Raise the logging threshold without allowing release builds to re-enable debug noise. */
    fun setMinimumLevel(level: Level) {
        configuredMinimum = if (level.ordinal < releaseFloor.ordinal) releaseFloor else level
    }

    fun isLoggable(level: Level): Boolean = level.ordinal >= configuredMinimum.ordinal

    fun v(tag: String, message: String, throwable: Throwable? = null) =
        write(Level.VERBOSE, tag, message, throwable)

    fun d(tag: String, message: String, throwable: Throwable? = null) =
        write(Level.DEBUG, tag, message, throwable)

    fun i(tag: String, message: String, throwable: Throwable? = null) =
        write(Level.INFO, tag, message, throwable)

    fun w(tag: String, message: String, throwable: Throwable? = null) =
        write(Level.WARN, tag, message, throwable)

    fun e(tag: String, message: String, throwable: Throwable? = null) =
        write(Level.ERROR, tag, message, throwable)

    private fun write(level: Level, tag: String, message: String, throwable: Throwable?) {
        if (!isLoggable(level)) return
        val raw = if (throwable == null) {
            message
        } else {
            "$message\n${AndroidLog.getStackTraceString(throwable)}"
        }
        AndroidLog.println(level.priority, tag, DiagnosticExportEngine.redactSensitive(raw))
    }
}

package com.novacut.editor.engine

import android.os.Build
import android.os.StrictMode
import com.novacut.editor.BuildConfig
import java.util.concurrent.Executor

/**
 * Debug-only runtime diagnostics.
 *
 * Thread and VM StrictMode detect-all policies are installed only when the
 * variant is debuggable. On API 28 and newer, violations are sent through
 * [AppLog] so the application logging boundary remains intact. Older devices
 * use StrictMode's platform log penalty because the listener API does not
 * exist. Violations are deliberately log-only: they never show a user dialog,
 * crash the process, block release builds, or become telemetry. A suppression
 * or permit should be narrowly scoped and documented at the violating call
 * site after the cause has been reviewed.
 */
object DebugRuntimePolicy {
    private const val TAG = "StrictMode"
    private val directExecutor = Executor { runnable -> runnable.run() }

    fun install() {
        if (!BuildConfig.DEBUG) return

        val threadPolicy = StrictMode.ThreadPolicy.Builder()
            .detectAll()
            .applyPenalty()
            .build()
        val vmPolicy = StrictMode.VmPolicy.Builder()
            .detectAll()
            .applyPenalty()
            .build()
        StrictMode.setThreadPolicy(threadPolicy)
        StrictMode.setVmPolicy(vmPolicy)
    }

    private fun StrictMode.ThreadPolicy.Builder.applyPenalty(): StrictMode.ThreadPolicy.Builder {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            penaltyListener(directExecutor) { violation ->
                AppLog.w(TAG, "Thread policy violation: ${violation.javaClass.simpleName}", violation)
            }
        } else {
            penaltyLog()
        }
        return this
    }

    private fun StrictMode.VmPolicy.Builder.applyPenalty(): StrictMode.VmPolicy.Builder {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            penaltyListener(directExecutor) { violation ->
                AppLog.w(TAG, "VM policy violation: ${violation.javaClass.simpleName}", violation)
            }
        } else {
            penaltyLog()
        }
        return this
    }
}

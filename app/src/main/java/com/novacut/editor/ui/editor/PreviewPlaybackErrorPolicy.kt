package com.novacut.editor.ui.editor

import androidx.media3.exoplayer.ExoTimeoutException
import kotlin.math.abs

@android.annotation.SuppressLint("UnsafeOptInUsageError")
internal fun isPreviewSurfaceDetachTimeout(error: Throwable): Boolean {
    var cause: Throwable? = error
    val visited = mutableSetOf<Throwable>()
    while (cause != null && visited.add(cause)) {
        if (cause is ExoTimeoutException &&
            cause.timeoutOperation == ExoTimeoutException.TIMEOUT_OPERATION_DETACH_SURFACE
        ) {
            return true
        }
        cause = cause.cause
    }
    return false
}

internal fun isRecoverablePreviewRuntimeFailure(error: Throwable): Boolean {
    var cause: Throwable? = error
    val visited = mutableSetOf<Throwable>()
    while (cause != null && visited.add(cause)) {
        if (isPreviewSurfaceDetachTimeout(cause) ||
            cause.javaClass.simpleName == "StuckPlayerException"
        ) {
            return true
        }
        cause = cause.cause
    }
    return false
}

internal fun isPreviewStuckPlayerFailure(error: Throwable): Boolean {
    var cause: Throwable? = error
    val visited = mutableSetOf<Throwable>()
    while (cause != null && visited.add(cause)) {
        if (cause.javaClass.simpleName == "StuckPlayerException") return true
        cause = cause.cause
    }
    return false
}

internal fun isAtPreviewTimelineEnd(positionMs: Long, totalDurationMs: Long): Boolean =
    totalDurationMs > 0L && positionMs >= totalDurationMs - 250L

internal fun hasPreviewPlaybackAdvanced(startPositionMs: Long, currentPositionMs: Long): Boolean =
    abs(currentPositionMs - startPositionMs) >= 250L

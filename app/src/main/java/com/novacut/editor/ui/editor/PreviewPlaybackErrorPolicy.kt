package com.novacut.editor.ui.editor

import androidx.media3.exoplayer.ExoTimeoutException

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

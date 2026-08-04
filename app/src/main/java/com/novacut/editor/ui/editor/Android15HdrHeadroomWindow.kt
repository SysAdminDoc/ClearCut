package com.novacut.editor.ui.editor

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.os.Build
import android.util.Log
import android.view.Window
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.novacut.editor.engine.Android15MediaPolicy

private const val TAG = "Android15HdrWindow"

/**
 * Requests the Android 15 HDR window policy only while an HDR source is visible in the editor.
 * The original color mode and headroom are restored when the editor leaves HDR content.
 */
@Composable
internal fun Android15HdrHeadroomWindow(hasHdrContent: Boolean) {
    val context = LocalContext.current
    val view = LocalView.current
    val activity = context.findActivityForHdrPolicy()
    val desiredHeadroom = Android15MediaPolicy.desiredHdrHeadroom(
        sdkInt = Build.VERSION.SDK_INT,
        hasHdrContent = hasHdrContent,
    )

    DisposableEffect(activity, view, desiredHeadroom) {
        val window = activity?.window
        if (window == null || desiredHeadroom == 0.0f ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM
        ) {
            onDispose { }
        } else {
            val restore = applyHdrWindowPolicy(window, desiredHeadroom)
            onDispose { restore() }
        }
    }
}

@RequiresApi(35)
private fun applyHdrWindowPolicy(window: Window, desiredHeadroom: Float): () -> Unit {
    val originalColorMode = window.colorMode
    val originalHeadroom = window.desiredHdrHeadroom
    var colorModeChanged = false
    var headroomApplied = false

    try {
        if (originalColorMode != ActivityInfo.COLOR_MODE_HDR) {
            window.setColorMode(ActivityInfo.COLOR_MODE_HDR)
            colorModeChanged = true
        }
        window.setDesiredHdrHeadroom(desiredHeadroom)
        headroomApplied = true
    } catch (error: RuntimeException) {
        Log.w(TAG, "HDR window headroom is unavailable on this display", error)
    }

    return {
        if (headroomApplied) {
            runCatching { window.setDesiredHdrHeadroom(originalHeadroom) }
                .onFailure { error -> Log.w(TAG, "Could not restore HDR window headroom", error) }
        }
        if (colorModeChanged) {
            runCatching { window.setColorMode(originalColorMode) }
                .onFailure { error -> Log.w(TAG, "Could not restore HDR window color mode", error) }
        }
    }
}

private fun Context.findActivityForHdrPolicy(): Activity? {
    var current: Context = this
    while (true) {
        if (current is Activity) return current
        if (current !is ContextWrapper || current.baseContext === current) return null
        current = current.baseContext
    }
}

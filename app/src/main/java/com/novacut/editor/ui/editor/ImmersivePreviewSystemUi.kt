package com.novacut.editor.ui.editor

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Hides the system bars only while the editor preview is immersive.
 *
 * The visibility and transient-bar behavior are captured at entry so leaving
 * the preview restores the host window exactly, including when the caller had
 * already hidden the bars before opening the editor.
 */
@Composable
internal fun ImmersivePreviewSystemUi(isImmersive: Boolean) {
    val context = LocalContext.current
    val view = LocalView.current
    val activity = context.findActivity()

    DisposableEffect(activity, view, isImmersive) {
        val window = activity?.window
        if (window == null) {
            onDispose { }
        } else {
            val controller = WindowCompat.getInsetsController(window, view)
            val originalBehavior = controller.systemBarsBehavior
            val originalBarsVisible = ViewCompat.getRootWindowInsets(view)
                ?.isVisible(WindowInsetsCompat.Type.systemBars())
                ?: true

            if (isImmersive) {
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = originalBehavior
            }

            onDispose {
                if (isImmersive) {
                    if (originalBarsVisible) {
                        controller.show(WindowInsetsCompat.Type.systemBars())
                    } else {
                        controller.hide(WindowInsetsCompat.Type.systemBars())
                    }
                    controller.systemBarsBehavior = originalBehavior
                }
            }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (true) {
        if (current is Activity) return current
        if (current !is ContextWrapper || current.baseContext === current) return null
        current = current.baseContext
    }
}

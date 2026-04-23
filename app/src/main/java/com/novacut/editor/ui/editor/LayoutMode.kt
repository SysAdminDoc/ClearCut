package com.novacut.editor.ui.editor

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.novacut.editor.engine.DesktopOverride

/**
 * v3.69 layout-mode resolver.
 *
 * The editor renders the same screen content regardless of mode — individual
 * components observe [LocalLayoutMode] and choose a compact / thumb-zone /
 * desktop variant where it is meaningful. The resolver keeps the detection
 * logic in one place so UI code never touches `UiModeManager` directly.
 */
enum class LayoutMode {
    /** Phone with the default (two-hand) layout. */
    PHONE,
    /** Phone, user opted into the thumb-zone compact layout. */
    ONE_HANDED,
    /** Samsung DeX, Chromebook, or generic large-screen desktop-class. */
    DESKTOP
}

val LocalLayoutMode = staticCompositionLocalOf { LayoutMode.PHONE }

@Composable
fun rememberLayoutMode(
    oneHandedUserPref: Boolean,
    desktopOverride: DesktopOverride = DesktopOverride.AUTO
): LayoutMode {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(configuration, oneHandedUserPref, desktopOverride) {
        resolveLayoutMode(context, configuration, oneHandedUserPref, desktopOverride)
    }
}

/**
 * Pure resolver. Tested via synthetic configurations in instrumentation; no
 * Compose state is touched so it is safe to call from non-composable paths
 * if we ever need a non-UI consumer (e.g. analytics).
 */
fun resolveLayoutMode(
    context: Context,
    configuration: Configuration,
    oneHandedUserPref: Boolean,
    desktopOverride: DesktopOverride
): LayoutMode {
    val isDesktopLike = when (desktopOverride) {
        DesktopOverride.FORCE_ON -> true
        DesktopOverride.FORCE_OFF -> false
        DesktopOverride.AUTO -> detectDesktop(context, configuration)
    }
    if (isDesktopLike) return LayoutMode.DESKTOP
    if (oneHandedUserPref && configuration.screenWidthDp < 600) return LayoutMode.ONE_HANDED
    return LayoutMode.PHONE
}

private fun detectDesktop(context: Context, configuration: Configuration): Boolean {
    // UI_MODE_TYPE_DESK is set by Samsung DeX and desktop-mode launchers.
    val mgr = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    if (mgr?.currentModeType == Configuration.UI_MODE_TYPE_DESK) return true
    // Fallback: large-screen (>840 dp) plus a mouse/trackpad usually means
    // Chromebook or a tablet in freeform mode. The `mouse` signal avoids
    // classifying ordinary tablets as desktop.
    if (configuration.screenWidthDp >= 840) {
        val hasMouse =
            (configuration.touchscreen == Configuration.TOUCHSCREEN_NOTOUCH) ||
                (configuration.navigation == Configuration.NAVIGATION_TRACKBALL) ||
                (configuration.navigation == Configuration.NAVIGATION_DPAD)
        if (hasMouse) return true
    }
    return false
}

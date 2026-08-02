package com.novacut.editor.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Settings > Haptic feedback, as seen by composables.
 *
 * The platform [LocalHapticFeedback] knows nothing about the app's own preference,
 * so every call site that used it directly buzzed whether or not the user had turned
 * haptics off — the toggle stored a value and changed nothing.
 */
val LocalHapticsEnabled = staticCompositionLocalOf { true }

/**
 * Haptic feedback that honours [LocalHapticsEnabled]. Use this instead of
 * [LocalHapticFeedback] anywhere the feedback is a nicety the user may switch off.
 */
@Composable
fun rememberUserHaptics(): HapticFeedback {
    val platform = LocalHapticFeedback.current
    val enabled = LocalHapticsEnabled.current
    return remember(platform, enabled) {
        object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                if (enabled) platform.performHapticFeedback(hapticFeedbackType)
            }
        }
    }
}

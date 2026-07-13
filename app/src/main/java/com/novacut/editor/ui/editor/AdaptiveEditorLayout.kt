package com.novacut.editor.ui.editor

import com.novacut.editor.ui.theme.LocalClearCutColors
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Adaptive layout wrapper for editor that switches between phone and tablet layouts.
 * - Phone (Compact): Stacked vertical layout (preview -> timeline -> tools)
 * - Tablet (Medium+): Side-by-side layout (preview+tools left, timeline right)
 */
@Composable
fun AdaptiveEditorLayout(
    isWideScreen: Boolean,
    preview: @Composable (Modifier) -> Unit,
    timeline: @Composable (Modifier) -> Unit,
    toolPanel: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier
) {
    val semanticColors = LocalClearCutColors.current
    if (isWideScreen) {
        // Tablet/foldable: horizontal layout
        Row(
            modifier = modifier
                .fillMaxSize()
                .background(semanticColors.surfaceBase)
        ) {
            // Left: Preview + Tools stacked
            Column(
                modifier = Modifier
                    .weight(0.45f)
                    .fillMaxHeight()
            ) {
                preview(Modifier.weight(0.6f))
                toolPanel(Modifier.weight(0.4f))
            }

            // Divider
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(semanticColors.surfaceLow)
            )

            // Right: Full-height timeline
            timeline(
                Modifier
                    .weight(0.55f)
                    .fillMaxHeight()
            )
        }
    } else {
        // Phone: vertical stack (existing layout)
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(semanticColors.surfaceBase)
        ) {
            preview(Modifier.weight(0.3f))
            timeline(Modifier.weight(0.4f))
            toolPanel(Modifier.weight(0.3f))
        }
    }
}

/**
 * Determines if the current window is wide enough for tablet layout.
 * Uses 600dp as the breakpoint (Material Design compact/medium threshold).
 */
@Composable
fun isWideScreen(): Boolean {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    return configuration.screenWidthDp >= 600
}

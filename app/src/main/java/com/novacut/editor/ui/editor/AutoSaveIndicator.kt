package com.novacut.editor.ui.editor

import com.novacut.editor.ui.theme.ClearCutAccents
import com.novacut.editor.ui.theme.LocalClearCutColors
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.novacut.editor.R
import com.novacut.editor.model.SaveIndicatorState
import com.novacut.editor.ui.theme.Elevation
import com.novacut.editor.ui.theme.Motion
import com.novacut.editor.ui.theme.Radius
import com.novacut.editor.ui.theme.Spacing
import kotlinx.coroutines.delay

@Composable
fun AutoSaveIndicator(
    state: SaveIndicatorState,
    modifier: Modifier = Modifier
) {
    val semanticColors = LocalClearCutColors.current
    var visible by remember { mutableStateOf(false) }
    val accent = when (state) {
        SaveIndicatorState.SAVING -> ClearCutAccents.Sapphire
        SaveIndicatorState.SAVED -> ClearCutAccents.Green
        SaveIndicatorState.ERROR -> ClearCutAccents.Red
        SaveIndicatorState.HIDDEN -> semanticColors.subtext
    }
    val label = when (state) {
        SaveIndicatorState.SAVING -> stringResource(R.string.autosave_saving)
        SaveIndicatorState.SAVED -> stringResource(R.string.autosave_saved)
        SaveIndicatorState.ERROR -> stringResource(R.string.autosave_failed)
        SaveIndicatorState.HIDDEN -> ""
    }

    LaunchedEffect(state) {
        visible = state != SaveIndicatorState.HIDDEN
        if (state == SaveIndicatorState.SAVED) {
            delay(2000L)
            visible = false
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            animationSpec = tween(Motion.DurationMedium, easing = Motion.DecelerateEasing),
            initialOffsetY = { -it / 2 }
        ) + fadeIn(tween(Motion.DurationMedium, easing = Motion.DecelerateEasing)),
        exit = slideOutVertically(
            animationSpec = tween(Motion.DurationFast, easing = Motion.AccelerateEasing),
            targetOffsetY = { -it / 2 }
        ) + fadeOut(tween(Motion.DurationFast, easing = Motion.AccelerateEasing)),
        modifier = modifier
    ) {
        Surface(
            color = semanticColors.panelHighest.copy(alpha = 0.96f),
            shape = RoundedCornerShape(Radius.sm),
            border = BorderStroke(1.dp, semanticColors.cardStroke.copy(alpha = 0.9f)),
            shadowElevation = Elevation.toast,
            modifier = Modifier.semantics {
                contentDescription = label
                liveRegion = if (state == SaveIndicatorState.ERROR) {
                    LiveRegionMode.Assertive
                } else {
                    LiveRegionMode.Polite
                }
            }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                accent.copy(alpha = 0.12f),
                                Color.Transparent
                            )
                        )
                    )
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Surface(
                    color = accent.copy(alpha = 0.14f),
                    shape = CircleShape,
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.24f))
                ) {
                    Box(
                        modifier = Modifier.size(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        when (state) {
                            SaveIndicatorState.SAVING -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 1.7.dp,
                                    color = accent,
                                    trackColor = accent.copy(alpha = 0.12f)
                                )
                            }

                            SaveIndicatorState.SAVED -> {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = accent,
                                    modifier = Modifier.size(14.dp)
                                )
                            }

                            SaveIndicatorState.ERROR -> {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = accent,
                                    modifier = Modifier.size(14.dp)
                                )
                            }

                            SaveIndicatorState.HIDDEN -> Unit
                        }
                    }
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (state == SaveIndicatorState.SAVING) semanticColors.text else accent
                )
            }
        }
    }
}

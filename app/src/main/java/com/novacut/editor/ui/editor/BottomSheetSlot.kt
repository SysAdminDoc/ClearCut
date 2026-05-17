package com.novacut.editor.ui.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.tween
import com.novacut.editor.ui.theme.Motion

@Composable
fun BottomSheetSlot(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            animationSpec = tween(Motion.DurationMedium, easing = Motion.DecelerateEasing),
            initialOffsetY = { it / 3 }
        ) + fadeIn(
            animationSpec = tween(Motion.DurationStandard, easing = Motion.DecelerateEasing)
        ),
        exit = slideOutVertically(
            animationSpec = tween(Motion.DurationFast, easing = Motion.AccelerateEasing),
            targetOffsetY = { it / 4 }
        ) + fadeOut(
            animationSpec = tween(Motion.DurationFast, easing = Motion.AccelerateEasing)
        ),
        modifier = modifier
    ) {
        content()
    }
}

@Composable
fun SidePanelSlot(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(
            animationSpec = tween(Motion.DurationMedium, easing = Motion.DecelerateEasing),
            initialOffsetX = { it / 3 }
        ) + fadeIn(
            animationSpec = tween(Motion.DurationStandard, easing = Motion.DecelerateEasing)
        ),
        exit = slideOutHorizontally(
            animationSpec = tween(Motion.DurationFast, easing = Motion.AccelerateEasing),
            targetOffsetX = { it / 4 }
        ) + fadeOut(
            animationSpec = tween(Motion.DurationFast, easing = Motion.AccelerateEasing)
        ),
        modifier = modifier
    ) {
        content()
    }
}

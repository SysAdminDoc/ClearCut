package com.novacut.editor.ui.editor

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.novacut.editor.R
import com.novacut.editor.ui.theme.Mocha
import com.novacut.editor.ui.theme.Motion
import com.novacut.editor.ui.theme.NovaCutPrimaryButton
import com.novacut.editor.ui.theme.NovaCutSecondaryButton
import com.novacut.editor.ui.theme.Radius
import com.novacut.editor.ui.theme.Spacing
import com.novacut.editor.ui.theme.TouchTarget

private data class TutorialStepDef(
    val titleRes: Int,
    val descriptionRes: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val arrowIcon: androidx.compose.ui.graphics.vector.ImageVector
)

private val tutorialStepDefs = listOf(
    TutorialStepDef(
        titleRes = R.string.tutorial_title_add_media,
        descriptionRes = R.string.tutorial_desc_add_media,
        icon = Icons.Default.Add,
        arrowIcon = Icons.Default.KeyboardArrowUp
    ),
    TutorialStepDef(
        titleRes = R.string.tutorial_title_timeline,
        descriptionRes = R.string.tutorial_desc_timeline,
        icon = Icons.Default.ViewTimeline,
        arrowIcon = Icons.Default.KeyboardArrowDown
    ),
    TutorialStepDef(
        titleRes = R.string.tutorial_title_edit,
        descriptionRes = R.string.tutorial_desc_edit,
        icon = Icons.Default.AutoFixHigh,
        arrowIcon = Icons.Default.KeyboardArrowDown
    ),
    TutorialStepDef(
        titleRes = R.string.tutorial_title_export,
        descriptionRes = R.string.tutorial_desc_export,
        icon = Icons.Default.Upload,
        arrowIcon = Icons.Default.KeyboardArrowUp
    )
)

@Composable
fun FirstRunTutorial(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentStep by remember { mutableIntStateOf(0) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent().changes.forEach { it.consume() }
                    }
                }
            }
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to Mocha.Crust.copy(alpha = 0.96f),
                        0.48f to Mocha.Midnight.copy(alpha = 0.96f),
                        1f to Mocha.Crust.copy(alpha = 0.94f)
                    )
                )
            )
    ) {
        // Skip — quiet pill button. Bare text on a translucent backdrop is hard to discover
        // and easy to misclick; a subtle pill treatment gives it a clear affordance without
        // competing with the primary "Next" CTA.
        Surface(
            color = Mocha.Surface0.copy(alpha = 0.6f),
            shape = RoundedCornerShape(Radius.pill),
            border = BorderStroke(1.dp, Mocha.CardStroke.copy(alpha = 0.6f)),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(Spacing.lg)
                .defaultMinSize(minHeight = TouchTarget.minimum)
                .clickable(role = Role.Button, onClick = onComplete)
        ) {
            Text(
                text = stringResource(R.string.tutorial_skip),
                color = Mocha.Subtext1,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }

        // Center card with animated content
        AnimatedContent(
            targetState = currentStep,
            transitionSpec = {
                (fadeIn(tween(Motion.DurationMedium, easing = Motion.DecelerateEasing)) +
                    slideInHorizontally(tween(Motion.DurationMedium, easing = Motion.DecelerateEasing)) { it / 4 })
                    .togetherWith(
                        fadeOut(tween(Motion.DurationFast, easing = Motion.AccelerateEasing)) +
                            slideOutHorizontally(tween(Motion.DurationFast, easing = Motion.AccelerateEasing)) { -it / 4 }
                    )
            },
            modifier = Modifier.align(Alignment.Center),
            label = "tutorial_step"
        ) { step ->
            val tutorialStep = tutorialStepDefs[step]

            Surface(
                modifier = Modifier
                    .padding(horizontal = Spacing.xxl)
                    .widthIn(max = 340.dp),
                color = Mocha.PanelHighest,
                shape = RoundedCornerShape(Radius.xxl),
                border = BorderStroke(1.dp, Mocha.CardStrokeStrong.copy(alpha = 0.85f)),
                shadowElevation = 12.dp
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .background(
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0f to Mocha.Mauve.copy(alpha = 0.08f),
                                    0.6f to Mocha.PanelHighest,
                                    1f to Mocha.PanelHighest
                                )
                            )
                        )
                        .padding(horizontal = Spacing.xxl, vertical = Spacing.xxl)
                ) {
                    val isFirstStep = step == 0
                    val isLastStep = step == tutorialStepDefs.size - 1
                    val stepCounter = stringResource(
                        R.string.tutorial_step_counter,
                        step + 1,
                        tutorialStepDefs.size
                    )

                    // Direction arrow — kept but smaller and quieter so it's a hint, not a focal point.
                    Icon(
                        imageVector = tutorialStep.arrowIcon,
                        contentDescription = null,
                        tint = Mocha.Mauve.copy(alpha = 0.85f),
                        modifier = Modifier.size(24.dp)
                    )

                    Spacer(modifier = Modifier.height(Spacing.md))

                    // Step icon — added a subtle ring border for depth and an inner glow ring.
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Mocha.Mauve.copy(alpha = 0.14f))
                            .border(
                                BorderStroke(1.dp, Mocha.Mauve.copy(alpha = 0.24f)),
                                CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = tutorialStep.icon,
                            contentDescription = null,
                            tint = Mocha.Mauve,
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(Spacing.lg))

                    Text(
                        text = stringResource(tutorialStep.titleRes),
                        color = Mocha.Text,
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(Spacing.sm))

                    Text(
                        text = stringResource(tutorialStep.descriptionRes),
                        color = Mocha.Subtext1,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(Spacing.md))

                    Surface(
                        color = Mocha.Surface0.copy(alpha = 0.72f),
                        shape = RoundedCornerShape(Radius.pill),
                        border = BorderStroke(1.dp, Mocha.CardStroke.copy(alpha = 0.75f))
                    ) {
                        Text(
                            text = stepCounter,
                            color = Mocha.Subtext1,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(Spacing.xl))

                    // Step indicator — connected pill segments. The current step is wider and
                    // accented, which reads as "you are here" much faster than equal-sized dots.
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.semantics {
                            contentDescription = stepCounter
                            progressBarRangeInfo = ProgressBarRangeInfo(
                                current = (step + 1).toFloat(),
                                range = 1f..tutorialStepDefs.size.toFloat(),
                                steps = tutorialStepDefs.size - 2
                            )
                        }
                    ) {
                        repeat(tutorialStepDefs.size) { index ->
                            val width by animateDpAsState(
                                targetValue = if (index == step) 24.dp else 8.dp,
                                animationSpec = tween(Motion.DurationStandard, easing = Motion.StandardEasing),
                                label = "tutorial_dot_width_$index"
                            )
                            Box(
                                modifier = Modifier
                                    .width(width)
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(Radius.pill))
                                    .background(
                                        if (index == step) Mocha.Mauve
                                        else Mocha.Surface1.copy(alpha = 0.7f)
                                    )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(Spacing.xl))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!isFirstStep) {
                            NovaCutSecondaryButton(
                                text = stringResource(R.string.tutorial_back),
                                onClick = { currentStep-- },
                                modifier = Modifier
                                    .weight(0.42f)
                                    .height(TouchTarget.minimum),
                                icon = Icons.AutoMirrored.Filled.ArrowBack
                            )
                        }

                        NovaCutPrimaryButton(
                            text = stringResource(if (isLastStep) R.string.tutorial_get_started else R.string.tutorial_next),
                            onClick = {
                                if (isLastStep) {
                                    onComplete()
                                } else {
                                    currentStep++
                                }
                            },
                            modifier = Modifier
                                .weight(if (isFirstStep) 1f else 0.58f)
                                .height(TouchTarget.minimum),
                            icon = if (isLastStep) Icons.Default.Check else Icons.AutoMirrored.Filled.ArrowForward
                        )
                    }
                }
            }
        }
    }
}

package com.novacut.editor.ui.editor

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.novacut.editor.R
import com.novacut.editor.ui.theme.Mocha

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
            .background(Mocha.Crust.copy(alpha = 0.85f))
    ) {
        // Skip button
        Text(
            text = stringResource(R.string.tutorial_skip),
            color = Mocha.Subtext0,
            fontSize = 14.sp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .clickable { onComplete() }
        )

        // Center card with animated content
        AnimatedContent(
            targetState = currentStep,
            transitionSpec = {
                (fadeIn() + slideInHorizontally { it / 3 })
                    .togetherWith(fadeOut() + slideOutHorizontally { -it / 3 })
            },
            modifier = Modifier.align(Alignment.Center),
            label = "tutorial_step"
        ) { step ->
            val tutorialStep = tutorialStepDefs[step]

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .padding(horizontal = 32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Mocha.Surface0)
                    .padding(24.dp)
                    .widthIn(max = 320.dp)
            ) {
                // Direction arrow
                Icon(
                    imageVector = tutorialStep.arrowIcon,
                    contentDescription = stringResource(R.string.cd_tutorial_direction),
                    tint = Mocha.Mauve,
                    modifier = Modifier.size(32.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Step icon
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Mocha.Mauve.copy(alpha = 0.15f))
                ) {
                    Icon(
                        imageVector = tutorialStep.icon,
                        contentDescription = stringResource(tutorialStep.titleRes),
                        tint = Mocha.Mauve,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Title
                Text(
                    text = stringResource(tutorialStep.titleRes),
                    color = Mocha.Text,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Description
                Text(
                    text = stringResource(tutorialStep.descriptionRes),
                    color = Mocha.Subtext1,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Step indicator dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(tutorialStepDefs.size) { index ->
                        Box(
                            modifier = Modifier
                                .size(if (index == step) 10.dp else 8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (index == step) Mocha.Mauve else Mocha.Surface1
                                )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Next / Get Started button
                val isLastStep = step == tutorialStepDefs.size - 1
                Button(
                    onClick = {
                        if (isLastStep) {
                            onComplete()
                        } else {
                            currentStep++
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Mocha.Mauve,
                        contentColor = Mocha.Crust
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(if (isLastStep) R.string.tutorial_get_started else R.string.tutorial_next),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    if (!isLastStep) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = stringResource(R.string.cd_tutorial_next),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

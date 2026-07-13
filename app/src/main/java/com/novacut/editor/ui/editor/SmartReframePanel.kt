package com.novacut.editor.ui.editor

import com.novacut.editor.ui.theme.ClearCutAccents
import com.novacut.editor.ui.theme.LocalClearCutColors
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.novacut.editor.R
import com.novacut.editor.model.AspectRatio

private data class ReframeOption(
    val ratio: AspectRatio,
    val platformResId: Int
)

private val reframeOptions = listOf(
    ReframeOption(AspectRatio.RATIO_16_9, R.string.smart_reframe_platform_youtube),
    ReframeOption(AspectRatio.RATIO_9_16, R.string.crop_preset_platform_tiktok_reels),
    ReframeOption(AspectRatio.RATIO_1_1, R.string.smart_reframe_platform_instagram),
    ReframeOption(AspectRatio.RATIO_4_5, R.string.smart_reframe_platform_portrait_ads),
    ReframeOption(AspectRatio.RATIO_4_3, R.string.crop_preset_platform_classic)
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SmartReframePanel(
    currentAspect: AspectRatio,
    isProcessing: Boolean,
    onReframe: (AspectRatio) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semanticColors = LocalClearCutColors.current
    val isCompactGrid = LocalConfiguration.current.screenWidthDp < 430
    PremiumEditorPanel(
        title = stringResource(R.string.smart_reframe_title),
        subtitle = stringResource(R.string.smart_reframe_subtitle),
        icon = Icons.Default.Crop,
        accent = ClearCutAccents.Mauve,
        onClose = onClose,
        closeContentDescription = stringResource(R.string.smart_reframe_close_cd),
        modifier = modifier,
        scrollable = true,
        headerActions = {
            if (isProcessing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = ClearCutAccents.Mauve,
                    strokeWidth = 2.dp
                )
            }
        }
    ) {
        PremiumPanelCard(accent = ClearCutAccents.Mauve) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.smart_reframe_current_frame_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = semanticColors.text
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.smart_reframe_current_frame_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = semanticColors.subtext
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PremiumPanelPill(
                        text = currentAspect.label,
                        accent = ClearCutAccents.Mauve
                    )
                    PremiumPanelPill(
                        text = if (isProcessing) stringResource(R.string.smart_reframe_processing) else stringResource(R.string.smart_reframe_targets_count, reframeOptions.size),
                        accent = if (isProcessing) ClearCutAccents.Peach else ClearCutAccents.Blue
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        PremiumPanelCard(accent = ClearCutAccents.Blue) {
            Text(
                text = stringResource(R.string.smart_reframe_destination_title),
                style = MaterialTheme.typography.titleMedium,
                color = semanticColors.text
            )
            Text(
                text = stringResource(R.string.smart_reframe_destination_description),
                style = MaterialTheme.typography.bodyMedium,
                color = semanticColors.subtext
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                reframeOptions.forEach { option ->
                    SmartReframeCard(
                        option = option,
                        isSelected = option.ratio == currentAspect,
                        isProcessing = isProcessing,
                        onClick = { onReframe(option.ratio) },
                        previewMaxSize = if (isCompactGrid) 64.dp else 72.dp,
                        modifier = Modifier.widthIn(min = if (isCompactGrid) 136.dp else 156.dp, max = 220.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SmartReframeCard(
    option: ReframeOption,
    isSelected: Boolean,
    isProcessing: Boolean,
    onClick: () -> Unit,
    previewMaxSize: Dp,
    modifier: Modifier = Modifier
) {
    val semanticColors = LocalClearCutColors.current
    val accent = if (isSelected) ClearCutAccents.Mauve else ClearCutAccents.Blue
    val previewRatio = option.ratio.toFloat()
    val (previewWidth, previewHeight) = computePreviewDimensions(previewRatio, previewMaxSize)

    Surface(
        modifier = modifier,
        color = if (isSelected) accent.copy(alpha = 0.12f) else semanticColors.panelRaised,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (isSelected) accent.copy(alpha = 0.28f) else semanticColors.cardStroke
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !isProcessing, onClick = onClick)
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(previewWidth)
                    .height(previewHeight)
                    .background(
                        color = if (isSelected) accent.copy(alpha = 0.18f) else semanticColors.surfaceBase,
                        shape = RoundedCornerShape(14.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(previewWidth * 0.62f)
                        .height(previewHeight * 0.62f)
                        .background(
                            color = if (isSelected) accent.copy(alpha = 0.28f) else semanticColors.surface,
                            shape = RoundedCornerShape(10.dp)
                        )
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = option.ratio.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isSelected) accent else semanticColors.text,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(option.platformResId),
                    style = MaterialTheme.typography.bodySmall,
                    color = semanticColors.subtext,
                    textAlign = TextAlign.Center
                )
            }

            PremiumPanelPill(
                text = if (isSelected) stringResource(R.string.smart_reframe_current_pill) else stringResource(R.string.smart_reframe_target_pill),
                accent = accent
            )
        }
    }
}

private fun computePreviewDimensions(ratio: Float, maxSize: Dp): Pair<Dp, Dp> {
    return if (ratio >= 1f) {
        val width = maxSize
        val height = maxSize / ratio
        width to height
    } else {
        val height = maxSize
        val width = maxSize * ratio
        width to height
    }
}

package com.novacut.editor.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.novacut.editor.R
import com.novacut.editor.model.AspectRatio
import com.novacut.editor.ui.theme.Mocha

private data class ReframeOption(
    val ratio: AspectRatio,
    val platform: String
)

private val reframeOptions = listOf(
    ReframeOption(AspectRatio.RATIO_16_9, "YouTube"),
    ReframeOption(AspectRatio.RATIO_9_16, "TikTok / Reels"),
    ReframeOption(AspectRatio.RATIO_1_1, "Instagram"),
    ReframeOption(AspectRatio.RATIO_4_5, "IG Portrait"),
    ReframeOption(AspectRatio.RATIO_4_3, "Classic")
)

@Composable
fun SmartReframePanel(
    currentAspect: AspectRatio,
    isProcessing: Boolean,
    onReframe: (AspectRatio) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            .background(Mocha.Mantle)
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Crop,
                contentDescription = stringResource(R.string.cd_smart_reframe),
                tint = Mocha.Mauve,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Smart Reframe",
                color = Mocha.Text,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            if (isProcessing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = Mocha.Mauve
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close smart reframe",
                    tint = Mocha.Subtext0,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Aspect ratio grid — 3 columns top row, 2 columns bottom row
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                reframeOptions.take(3).forEach { option ->
                    AspectRatioCard(
                        option = option,
                        isSelected = option.ratio == currentAspect,
                        isProcessing = isProcessing,
                        onClick = { onReframe(option.ratio) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                reframeOptions.drop(3).forEach { option ->
                    AspectRatioCard(
                        option = option,
                        isSelected = option.ratio == currentAspect,
                        isProcessing = isProcessing,
                        onClick = { onReframe(option.ratio) },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AspectRatioCard(
    option: ReframeOption,
    isSelected: Boolean,
    isProcessing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isSelected) Mocha.Mauve else Mocha.Surface1
    val bgColor = if (isSelected) Mocha.Mauve.copy(alpha = 0.1f) else Mocha.Surface0

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.5.dp, borderColor, RoundedCornerShape(8.dp))
            .background(bgColor)
            .clickable(enabled = !isProcessing) { onClick() }
            .padding(vertical = 10.dp, horizontal = 4.dp)
    ) {
        // Visual aspect ratio rectangle
        val maxPreviewSize = 36.dp
        val ratio = option.ratio.toFloat()
        val (previewW, previewH) = computePreviewDimensions(ratio, maxPreviewSize)

        Box(
            modifier = Modifier
                .width(previewW)
                .height(previewH)
                .border(
                    width = 1.5.dp,
                    color = if (isSelected) Mocha.Mauve else Mocha.Overlay0,
                    shape = RoundedCornerShape(2.dp)
                )
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Ratio label
        Text(
            text = option.ratio.label,
            color = if (isSelected) Mocha.Mauve else Mocha.Text,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )

        // Platform label
        Text(
            text = option.platform,
            color = Mocha.Subtext0,
            fontSize = 9.sp,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

private fun computePreviewDimensions(ratio: Float, maxSize: Dp): Pair<Dp, Dp> {
    return if (ratio >= 1f) {
        val w = maxSize
        val h = maxSize / ratio
        w to h
    } else {
        val h = maxSize
        val w = maxSize * ratio
        w to h
    }
}

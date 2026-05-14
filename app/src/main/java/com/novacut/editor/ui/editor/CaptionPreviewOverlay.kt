package com.novacut.editor.ui.editor

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novacut.editor.model.Caption
import com.novacut.editor.model.CaptionStyle
import com.novacut.editor.model.CaptionStyleType

/**
 * Renders active captions on the video preview during playback.
 * Supports multiple caption styles with word-level highlighting.
 */
@Composable
fun CaptionPreviewOverlay(
    captions: List<Caption>,
    currentTimeMs: Long,
    modifier: Modifier = Modifier
) {
    val activeCaptions = captions.filter {
        currentTimeMs in it.startTimeMs..it.endTimeMs
    }

    Box(modifier = modifier.fillMaxSize()) {
        activeCaptions.forEach { caption ->
            val style = caption.style
            val progress = if (caption.endTimeMs > caption.startTimeMs) {
                ((currentTimeMs - caption.startTimeMs).toFloat() / (caption.endTimeMs - caption.startTimeMs)).coerceIn(0f, 1f)
            } else 0f

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(androidx.compose.ui.BiasAlignment(0f, style.positionY * 2f - 1f))
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                when (style.type) {
                    CaptionStyleType.SUBTITLE_BAR -> SubtitleBarCaption(caption, progress)
                    CaptionStyleType.WORD_BY_WORD -> WordByWordCaption(caption, currentTimeMs)
                    CaptionStyleType.KARAOKE -> KaraokeCaption(caption, currentTimeMs)
                    CaptionStyleType.BOUNCE -> BounceCaption(caption, progress)
                    CaptionStyleType.TYPEWRITER -> TypewriterCaption(caption, progress)
                    CaptionStyleType.MINIMAL -> MinimalCaption(caption, progress)
                }
            }
        }
    }
}

@Composable
private fun SubtitleBarCaption(caption: Caption, progress: Float) {
    val style = caption.style
    Text(
        text = caption.text,
        color = Color(style.color),
        fontSize = style.fontSize.sp,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
        fontFamily = fontFamilyFromName(style.fontFamily),
        style = TextStyle(
            shadow = captionTextShadow(style)
        ),
        modifier = Modifier
            .drawBehind {
                drawRoundRect(
                    Color(style.backgroundColor),
                    cornerRadius = CornerRadius(8f, 8f)
                )
            }
            .padding(horizontal = 12.dp, vertical = 4.dp)
    )
}

@Composable
private fun WordByWordCaption(caption: Caption, currentTimeMs: Long) {
    if (caption.words.isEmpty()) {
        SubtitleBarCaption(caption, 0f)
        return
    }

    val style = caption.style
    val activeWord = caption.words.find {
        currentTimeMs in it.startTimeMs..it.endTimeMs
    }

    Row(
        modifier = Modifier.padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        caption.words.forEach { word ->
            val isActive = word == activeWord
            Text(
                text = word.text + " ",
                color = if (isActive) Color(style.highlightColor) else Color(style.color).copy(alpha = 0.5f),
                fontSize = (if (isActive) style.fontSize * 1.15f else style.fontSize).sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                fontFamily = fontFamilyFromName(style.fontFamily),
                style = TextStyle(shadow = captionTextShadow(style))
            )
        }
    }
}

@Composable
private fun KaraokeCaption(caption: Caption, currentTimeMs: Long) {
    if (caption.words.isEmpty()) {
        SubtitleBarCaption(caption, 0f)
        return
    }

    val style = caption.style
    Row(
        modifier = Modifier
            .drawBehind {
                drawRoundRect(
                    Color(style.backgroundColor),
                    cornerRadius = CornerRadius(8f, 8f)
                )
            }
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        caption.words.forEach { word ->
            val highlighted = currentTimeMs >= word.startTimeMs
            Text(
                text = word.text + " ",
                color = if (highlighted) Color(style.highlightColor) else Color(style.color),
                fontSize = style.fontSize.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = fontFamilyFromName(style.fontFamily),
                style = TextStyle(shadow = captionTextShadow(style))
            )
        }
    }
}

@Composable
private fun BounceCaption(caption: Caption, progress: Float) {
    val style = caption.style
    val bounceOffset = kotlin.math.abs(kotlin.math.sin(progress * kotlin.math.PI.toFloat() * 3f)) * 8f

    Text(
        text = caption.text,
        color = Color(style.color),
        fontSize = style.fontSize.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        fontFamily = fontFamilyFromName(style.fontFamily),
        style = TextStyle(shadow = captionTextShadow(style)),
        modifier = Modifier
            .offset(y = (-bounceOffset).dp)
            .drawBehind {
                drawRoundRect(
                    Color(style.backgroundColor),
                    cornerRadius = CornerRadius(8f, 8f)
                )
            }
            .padding(horizontal = 12.dp, vertical = 4.dp)
    )
}

@Composable
private fun TypewriterCaption(caption: Caption, progress: Float) {
    val style = caption.style
    val visibleChars = (caption.text.length * progress).toInt().coerceIn(0, caption.text.length)
    val displayText = caption.text.take(visibleChars)

    if (displayText.isNotEmpty()) {
        Text(
            text = displayText + if (progress < 1f) "|" else "",
            color = Color(style.color),
            fontSize = style.fontSize.sp,
            fontWeight = FontWeight.Normal,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center,
            style = TextStyle(shadow = captionTextShadow(style)),
            modifier = Modifier
                .drawBehind {
                    drawRoundRect(
                        Color(style.backgroundColor),
                        cornerRadius = CornerRadius(8f, 8f)
                    )
                }
                .padding(horizontal = 12.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun MinimalCaption(caption: Caption, progress: Float) {
    val style = caption.style
    val alpha = when {
        progress < 0.1f -> progress / 0.1f
        progress > 0.9f -> (1f - progress) / 0.1f
        else -> 1f
    }

    Text(
        text = caption.text,
        color = Color(style.color).copy(alpha = alpha),
        fontSize = style.fontSize.sp,
        fontWeight = FontWeight.Normal,
        textAlign = TextAlign.Center,
        fontFamily = fontFamilyFromName(style.fontFamily),
        style = TextStyle(
            shadow = captionTextShadow(style, alpha)
        )
    )
}

private fun captionTextShadow(style: CaptionStyle, alpha: Float = 1f): Shadow? = when {
    style.outline && style.outlineWidth > 0f -> Shadow(
        color = Color(style.outlineColor).copy(alpha = alpha),
        offset = Offset(1f, 1f),
        blurRadius = style.outlineWidth.coerceAtLeast(2f)
    )
    style.shadow -> Shadow(
        color = Color.Black.copy(alpha = 0.75f * alpha),
        offset = Offset(1f, 1f),
        blurRadius = 4f
    )
    else -> null
}

private fun fontFamilyFromName(name: String): FontFamily = when (name) {
    "serif" -> FontFamily.Serif
    "monospace" -> FontFamily.Monospace
    "cursive" -> FontFamily.Cursive
    "sans-serif-medium" -> FontFamily.SansSerif
    "sans-serif-condensed" -> FontFamily.SansSerif
    else -> FontFamily.SansSerif
}

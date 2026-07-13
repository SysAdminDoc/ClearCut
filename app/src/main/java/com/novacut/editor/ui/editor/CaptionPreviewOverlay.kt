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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novacut.editor.model.Caption
import com.novacut.editor.model.CaptionStyle
import com.novacut.editor.model.CaptionStyleType
import com.novacut.editor.engine.BidiTextPolicy
import com.novacut.editor.engine.CaptionFontFallbackPolicy
import com.novacut.editor.engine.CaptionTextLayoutPolicy

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
        fontFamily = fontFamilyFromName(style.fontFamily, caption.text),
        style = TextStyle(
            shadow = captionTextShadow(style),
            textDirection = textDirectionFor(caption.text),
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

    Text(
        text = highlightedWords(caption) { word -> word == activeWord },
        modifier = Modifier.padding(horizontal = 8.dp),
        textAlign = TextAlign.Center,
        fontFamily = fontFamilyFromName(style.fontFamily, caption.text),
        style = TextStyle(
            shadow = captionTextShadow(style),
            textDirection = textDirectionFor(caption.text),
        ),
    )
}

@Composable
private fun KaraokeCaption(caption: Caption, currentTimeMs: Long) {
    if (caption.words.isEmpty()) {
        SubtitleBarCaption(caption, 0f)
        return
    }

    val style = caption.style
    Text(
        text = highlightedWords(caption) { word -> currentTimeMs >= word.startTimeMs },
        modifier = Modifier
            .drawBehind {
                drawRoundRect(
                    Color(style.backgroundColor),
                    cornerRadius = CornerRadius(8f, 8f)
                )
            }
            .padding(horizontal = 12.dp, vertical = 4.dp),
        textAlign = TextAlign.Center,
        fontFamily = fontFamilyFromName(style.fontFamily, caption.text),
        style = TextStyle(
            shadow = captionTextShadow(style),
            textDirection = textDirectionFor(caption.text),
        ),
    )
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
        fontFamily = fontFamilyFromName(style.fontFamily, caption.text),
        style = TextStyle(
            shadow = captionTextShadow(style),
            textDirection = textDirectionFor(caption.text),
        ),
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
    val displayText = CaptionTextLayoutPolicy.visiblePrefix(caption.text, progress)

    if (displayText.isNotEmpty()) {
        Text(
            text = displayText + if (progress < 1f) "|" else "",
            color = Color(style.color),
            fontSize = style.fontSize.sp,
            fontWeight = FontWeight.Normal,
            fontFamily = fontFamilyFromName("monospace", displayText),
            textAlign = TextAlign.Center,
            style = TextStyle(
                shadow = captionTextShadow(style),
                textDirection = textDirectionFor(displayText),
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
        fontFamily = fontFamilyFromName(style.fontFamily, caption.text),
        style = TextStyle(
            shadow = captionTextShadow(style, alpha),
            textDirection = textDirectionFor(caption.text),
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

private fun fontFamilyFromName(name: String, text: String): FontFamily {
    if (CaptionFontFallbackPolicy.fallbackForText(text) !=
        CaptionFontFallbackPolicy.FontFamily.SYSTEM_SANS_SERIF
    ) return FontFamily.SansSerif
    return when (name) {
        "serif" -> FontFamily.Serif
        "monospace" -> FontFamily.Monospace
        "cursive" -> FontFamily.Cursive
        else -> FontFamily.SansSerif
    }
}

private fun textDirectionFor(text: String): TextDirection =
    if (BidiTextPolicy.baseDirection(text) == BidiTextPolicy.Direction.RTL) {
        TextDirection.Rtl
    } else {
        TextDirection.Ltr
    }

private fun highlightedWords(
    caption: Caption,
    highlighted: (com.novacut.editor.model.CaptionWord) -> Boolean,
): AnnotatedString = buildAnnotatedString {
    caption.words.forEachIndexed { index, word ->
        val isHighlighted = highlighted(word)
        withStyle(
            SpanStyle(
                color = if (isHighlighted) {
                    Color(caption.style.highlightColor)
                } else {
                    Color(caption.style.color).copy(alpha = if (caption.style.type == CaptionStyleType.WORD_BY_WORD) 0.5f else 1f)
                },
                fontSize = (if (isHighlighted && caption.style.type == CaptionStyleType.WORD_BY_WORD) {
                    caption.style.fontSize * 1.15f
                } else {
                    caption.style.fontSize
                }).sp,
                fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Normal,
            )
        ) {
            append(word.text)
        }
        if (index < caption.words.lastIndex) append(' ')
    }
}

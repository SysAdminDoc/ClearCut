package com.novacut.editor.model

import androidx.compose.runtime.Immutable
import java.util.UUID

@Immutable
data class Caption(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val words: List<CaptionWord> = emptyList(),
    val style: CaptionStyle = CaptionStyle()
)

@Immutable
data class CaptionWord(
    val text: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val confidence: Float = 1f
)

data class CaptionStyle(
    val type: CaptionStyleType = CaptionStyleType.SUBTITLE_BAR,
    val fontFamily: String = "sans-serif-medium",
    val fontSize: Float = 36f,
    val color: Long = 0xFFFFFFFF,
    val backgroundColor: Long = 0xCC000000,
    val highlightColor: Long = 0xFFFFD700,
    val positionY: Float = 0.85f,
    val outline: Boolean = true,
    val shadow: Boolean = true
)

enum class CaptionStyleType(val displayName: String) {
    SUBTITLE_BAR("Subtitle Bar"),
    WORD_BY_WORD("Word Pop"),
    KARAOKE("Karaoke Highlight"),
    BOUNCE("Bounce"),
    TYPEWRITER("Typewriter"),
    MINIMAL("Minimal")
}

enum class CaptionTemplateType(val displayName: String) {
    CLASSIC("Classic"),
    KARAOKE("Karaoke"),
    WORD_BY_WORD("Word by Word"),
    BOUNCE("Bounce"),
    GLOW("Glow"),
    OUTLINE("Outline"),
    SHADOW_POP("Shadow Pop"),
    GRADIENT("Gradient"),
    TYPEWRITER("Typewriter"),
    NEON("Neon"),
    COMIC("Comic"),
    MINIMAL("Minimal"),
    BOLD_CENTER("Bold Center"),
    LOWER_THIRD("Lower Third"),
    SUBTITLE("Subtitle")
}

data class CaptionStyleTemplate(
    val id: String = UUID.randomUUID().toString(),
    val type: CaptionTemplateType,
    val fontFamily: String = "sans-serif",
    val fontSize: Float = 24f,
    val textColor: Long = 0xFFFFFFFF,
    val backgroundColor: Long = 0x80000000,
    val outlineColor: Long = 0xFF000000,
    val outlineWidth: Float = 0f,
    val shadowColor: Long = 0x80000000,
    val shadowOffsetX: Float = 2f,
    val shadowOffsetY: Float = 2f,
    val positionY: Float = 0.85f,
    val animation: TextAnimation = TextAnimation.FADE,
    val highlightColor: Long = 0xFFFFD700,
    val wordByWord: Boolean = false
)

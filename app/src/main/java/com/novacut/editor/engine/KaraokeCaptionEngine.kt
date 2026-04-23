package com.novacut.editor.engine

import com.novacut.editor.model.TextAnimation
import com.novacut.editor.model.TextOverlay
import com.novacut.editor.model.WordTimestamp
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Word-pop / karaoke caption generator (Submagic / Captions.ai / Opus Clip style).
 *
 * Takes word-level Whisper timestamps and produces a list of `TextOverlay`s
 * with animations keyed to each word. Eight preset styles covering the
 * mainstream short-form aesthetics. Callers pick a style name; the engine
 * emits overlays that the existing export pipeline already renders.
 */
@Singleton
class KaraokeCaptionEngine @Inject constructor() {

    enum class KaraokeStyle(val displayName: String) {
        MRBEAST("MrBeast"),        // Giant white w/ black stroke, bounce in
        SUBWAY("Subway"),          // Yellow w/ black box bg, slide
        HORMOZI("Hormozi"),        // Alt red/green highlight
        TIKTOK_WHITE("TikTok White"),
        POP_SCALE("Pop Scale"),     // Simple scale-in
        TYPEWRITER("Typewriter"),
        NEON("Neon Glow"),
        MINIMAL("Minimal")
    }

    fun generate(
        words: List<WordTimestamp>,
        style: KaraokeStyle,
        wordsPerCue: Int = 3,
        startIndex: Int = 0
    ): List<TextOverlay> {
        if (words.isEmpty()) return emptyList()
        val overlays = mutableListOf<TextOverlay>()
        var i = 0
        while (i < words.size) {
            val group = words.drop(i).take(wordsPerCue)
            val text = group.joinToString(" ") { it.text }
            val s = group.first().startMs
            val e = group.last().endMs
            if (text.isBlank() || e <= s) { i += wordsPerCue; continue }
            overlays += buildOverlay(text, s, e, style, idx = startIndex + overlays.size)
            i += wordsPerCue
        }
        return overlays
    }

    private fun buildOverlay(
        text: String,
        startMs: Long,
        endMs: Long,
        style: KaraokeStyle,
        idx: Int
    ): TextOverlay {
        val spec = when (style) {
            KaraokeStyle.MRBEAST      -> Spec(0xFFFFFFFFL, 0x00000000L, TextAnimation.BOUNCE, 8f)
            KaraokeStyle.SUBWAY       -> Spec(0xFFFFEB3BL, 0xDD000000L, TextAnimation.SLIDE_UP, 0f)
            KaraokeStyle.HORMOZI      -> Spec(0xFF4ADE80L, 0x00000000L, TextAnimation.SCALE, 6f)
            KaraokeStyle.TIKTOK_WHITE -> Spec(0xFFFFFFFFL, 0xCC000000L, TextAnimation.FADE, 0f)
            KaraokeStyle.POP_SCALE    -> Spec(0xFFFFFFFFL, 0x00000000L, TextAnimation.SCALE, 4f)
            KaraokeStyle.TYPEWRITER   -> Spec(0xFFFFFFFFL, 0x00000000L, TextAnimation.TYPEWRITER, 0f)
            KaraokeStyle.NEON         -> Spec(0xFFEC4899L, 0x00000000L, TextAnimation.BLUR_IN, 0f)
            KaraokeStyle.MINIMAL      -> Spec(0xFFFFFFFFL, 0x00000000L, TextAnimation.FADE, 0f)
        }
        return TextOverlay(
            id = "karaoke_${idx}_${startMs}",
            text = text.uppercase(),
            startTimeMs = startMs,
            endTimeMs = endMs,
            positionX = 0.5f,
            positionY = 0.78f,
            fontSize = if (style == KaraokeStyle.MRBEAST) 84f else 56f,
            color = spec.color,
            backgroundColor = spec.bg,
            fontFamily = "sans-serif",
            animationIn = spec.anim,
            animationOut = TextAnimation.FADE,
            strokeWidth = spec.stroke,
            strokeColor = 0xFF000000L
        )
    }

    private data class Spec(val color: Long, val bg: Long, val anim: TextAnimation, val stroke: Float)
}

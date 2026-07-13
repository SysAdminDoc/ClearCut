package com.novacut.editor.engine

import java.text.BreakIterator
import java.util.Locale

object CaptionTextLayoutPolicy {
    data class Plan(
        val direction: BidiTextPolicy.Direction,
        val baseDirection: BidiTextPolicy.Direction,
        val fallbackFamily: CaptionFontFallbackPolicy.FontFamily,
        val graphemeBoundaries: List<Int>,
    )

    fun plan(text: String): Plan = Plan(
        direction = BidiTextPolicy.classify(text),
        baseDirection = BidiTextPolicy.baseDirection(text),
        fallbackFamily = CaptionFontFallbackPolicy.fallbackForText(text),
        graphemeBoundaries = graphemeBoundaries(text),
    )

    fun visiblePrefix(text: String, fraction: Float): String {
        if (text.isEmpty()) return text
        val boundaries = graphemeBoundaries(text)
        val visibleCount = (boundaries.size * fraction.coerceIn(0f, 1f)).toInt()
            .coerceIn(0, boundaries.size)
        val end = if (visibleCount == 0) 0 else boundaries[visibleCount - 1]
        return text.substring(0, end)
    }

    private fun graphemeBoundaries(text: String): List<Int> {
        if (text.isEmpty()) return emptyList()
        val iterator = BreakIterator.getCharacterInstance(Locale.ROOT)
        iterator.setText(text)
        val candidates = mutableListOf<Int>()
        var boundary = iterator.first()
        while (boundary != BreakIterator.DONE) {
            if (boundary > 0) candidates += boundary
            boundary = iterator.next()
        }
        return candidates.filter { candidate ->
            if (candidate >= text.length) return@filter true
            val previous = Character.codePointBefore(text, candidate)
            val next = Character.codePointAt(text, candidate)
            previous != ZERO_WIDTH_JOINER && next != ZERO_WIDTH_JOINER && !isContinuation(next)
        }
    }

    private fun isContinuation(codePoint: Int): Boolean {
        val type = Character.getType(codePoint)
        return type == Character.NON_SPACING_MARK.toInt() ||
            type == Character.COMBINING_SPACING_MARK.toInt() ||
            type == Character.ENCLOSING_MARK.toInt() ||
            codePoint in 0xFE00..0xFE0F ||
            codePoint in 0xE0100..0xE01EF ||
            codePoint in 0x1F3FB..0x1F3FF
    }

    private const val ZERO_WIDTH_JOINER = 0x200D
}

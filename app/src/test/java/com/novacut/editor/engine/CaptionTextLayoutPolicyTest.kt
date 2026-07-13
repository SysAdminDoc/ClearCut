package com.novacut.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptionTextLayoutPolicyTest {

    @Test
    fun planKeepsMixedDirectionAndScriptFallbackTogether() {
        val rtlFirst = CaptionTextLayoutPolicy.plan("\u0645\u0631\u062D\u0628\u0627 ClearCut 2026")
        assertEquals(BidiTextPolicy.Direction.MIXED, rtlFirst.direction)
        assertEquals(BidiTextPolicy.Direction.RTL, rtlFirst.baseDirection)
        assertEquals(
            CaptionFontFallbackPolicy.FontFamily.NOTO_ARABIC,
            rtlFirst.fallbackFamily,
        )
    }

    @Test
    fun typewriterNeverReturnsDanglingSurrogateOrCombiningMark() {
        val text = "A\uD83D\uDC69\u200D\uD83D\uDCBB e\u0301 \u4F60"
        (0..20).forEach { step ->
            val prefix = CaptionTextLayoutPolicy.visiblePrefix(text, step / 20f)
            assertFalse(prefix.lastOrNull()?.isHighSurrogate() == true)
            assertFalse(prefix.endsWith("\u200D"))
            if (prefix.endsWith("e")) {
                assertFalse("Combining sequence was split at step $step", text.startsWith("$prefix\u0301"))
            }
        }
        assertEquals(text, CaptionTextLayoutPolicy.visiblePrefix(text, 1f))
    }

    @Test
    fun graphemePlanIsMonotonic() {
        val text = "\u0645\u0631\u062D\u0628\u0627 \u4F60\u597D \uD83D\uDC4B\uD83C\uDFFD"
        val lengths = (0..20).map { CaptionTextLayoutPolicy.visiblePrefix(text, it / 20f).length }
        assertEquals(lengths.sorted(), lengths)
        assertTrue(lengths.toSet().size > 3)
    }
}

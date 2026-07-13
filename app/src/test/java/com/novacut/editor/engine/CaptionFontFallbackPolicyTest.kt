package com.novacut.editor.engine

import com.novacut.editor.engine.CaptionFontFallbackPolicy.FontFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptionFontFallbackPolicyTest {

    @Test
    fun blankLanguage_returnsSystemDefault() {
        assertEquals(FontFamily.SYSTEM_SANS_SERIF, CaptionFontFallbackPolicy.fallbackFor(""))
        assertEquals(FontFamily.SYSTEM_SANS_SERIF, CaptionFontFallbackPolicy.fallbackFor("   "))
    }

    @Test
    fun latin_european_returnsSystemSans() {
        listOf("en", "en-US", "fr", "es", "de", "pt-BR", "it", "nl", "pl", "ru", "uk", "el").forEach {
            assertEquals(
                "Expected system fallback for $it",
                FontFamily.SYSTEM_SANS_SERIF,
                CaptionFontFallbackPolicy.fallbackFor(it)
            )
        }
    }

    @Test
    fun simplifiedChinese_isSimplifiedCJK() {
        assertEquals(FontFamily.NOTO_CJK_SC, CaptionFontFallbackPolicy.fallbackFor("zh"))
        assertEquals(FontFamily.NOTO_CJK_SC, CaptionFontFallbackPolicy.fallbackFor("zh-CN"))
        assertEquals(FontFamily.NOTO_CJK_SC, CaptionFontFallbackPolicy.fallbackFor("zh-Hans"))
        assertEquals(FontFamily.NOTO_CJK_SC, CaptionFontFallbackPolicy.fallbackFor("zh-Hans-CN"))
    }

    @Test
    fun traditionalChinese_isTraditionalCJK() {
        assertEquals(FontFamily.NOTO_CJK_TC, CaptionFontFallbackPolicy.fallbackFor("zh-Hant"))
        assertEquals(FontFamily.NOTO_CJK_TC, CaptionFontFallbackPolicy.fallbackFor("zh-Hant-TW"))
        assertEquals(FontFamily.NOTO_CJK_TC, CaptionFontFallbackPolicy.fallbackFor("zh-TW"))
        assertEquals(FontFamily.NOTO_CJK_TC, CaptionFontFallbackPolicy.fallbackFor("zh-HK"))
    }

    @Test
    fun japanese_isJP() {
        assertEquals(FontFamily.NOTO_CJK_JP, CaptionFontFallbackPolicy.fallbackFor("ja"))
        assertEquals(FontFamily.NOTO_CJK_JP, CaptionFontFallbackPolicy.fallbackFor("ja-JP"))
    }

    @Test
    fun korean_isKR() {
        assertEquals(FontFamily.NOTO_CJK_KR, CaptionFontFallbackPolicy.fallbackFor("ko"))
        assertEquals(FontFamily.NOTO_CJK_KR, CaptionFontFallbackPolicy.fallbackFor("ko-KR"))
    }

    @Test
    fun arabicScript_familyCoversAr_fa_ur_ps() {
        listOf("ar", "fa", "ur", "ps").forEach {
            assertEquals(FontFamily.NOTO_ARABIC, CaptionFontFallbackPolicy.fallbackFor(it))
        }
    }

    @Test
    fun hebrewScript_familyCoversHeAndYi() {
        assertEquals(FontFamily.NOTO_HEBREW, CaptionFontFallbackPolicy.fallbackFor("he"))
        assertEquals(FontFamily.NOTO_HEBREW, CaptionFontFallbackPolicy.fallbackFor("yi"))
    }

    @Test
    fun devanagariScript_familyCoversHi_mr_sa_ne() {
        listOf("hi", "mr", "sa", "ne").forEach {
            assertEquals(FontFamily.NOTO_DEVANAGARI, CaptionFontFallbackPolicy.fallbackFor(it))
        }
    }

    @Test
    fun bengali_isBengali() {
        assertEquals(FontFamily.NOTO_BENGALI, CaptionFontFallbackPolicy.fallbackFor("bn"))
        assertEquals(FontFamily.NOTO_BENGALI, CaptionFontFallbackPolicy.fallbackFor("as"))
    }

    @Test
    fun tamil_isTamil() {
        assertEquals(FontFamily.NOTO_TAMIL, CaptionFontFallbackPolicy.fallbackFor("ta"))
    }

    @Test
    fun thaiAndLao_isThaiFamily() {
        assertEquals(FontFamily.NOTO_THAI, CaptionFontFallbackPolicy.fallbackFor("th"))
        assertEquals(FontFamily.NOTO_THAI, CaptionFontFallbackPolicy.fallbackFor("lo"))
    }

    @Test
    fun unknownLang_fallsBackToSystem() {
        assertEquals(FontFamily.SYSTEM_SANS_SERIF, CaptionFontFallbackPolicy.fallbackFor("xx"))
        assertEquals(FontFamily.SYSTEM_SANS_SERIF, CaptionFontFallbackPolicy.fallbackFor("zxx"))
    }

    @Test
    fun caseInsensitive() {
        assertEquals(FontFamily.NOTO_CJK_JP, CaptionFontFallbackPolicy.fallbackFor("JA"))
        assertEquals(FontFamily.NOTO_CJK_TC, CaptionFontFallbackPolicy.fallbackFor("ZH-HANT"))
    }

    @Test
    fun totalBundleBytes_singleFamily() {
        assertEquals(
            FontFamily.NOTO_CJK_JP.approxBundleBytes,
            CaptionFontFallbackPolicy.totalBundleBytes(listOf(FontFamily.NOTO_CJK_JP))
        )
    }

    @Test
    fun totalBundleBytes_includesAllByDefault() {
        val total = CaptionFontFallbackPolicy.totalBundleBytes()
        // System has 0 bytes; all others contribute. Spot-check the total
        // includes at least all four CJK subsets.
        val cjkTotal = FontFamily.NOTO_CJK_SC.approxBundleBytes +
            FontFamily.NOTO_CJK_TC.approxBundleBytes +
            FontFamily.NOTO_CJK_JP.approxBundleBytes +
            FontFamily.NOTO_CJK_KR.approxBundleBytes
        assertTrue("Total $total should be at least the CJK 4-pack ($cjkTotal)", total >= cjkTotal)
    }

    @Test
    fun rendersWithSystemFontsOnly_isTrueForLatin() {
        assertTrue(CaptionFontFallbackPolicy.rendersWithSystemFontsOnly("en-US"))
        assertTrue(CaptionFontFallbackPolicy.rendersWithSystemFontsOnly("fr"))
    }

    @Test
    fun rendersWithSystemFontsOnly_isFalseForCJK() {
        assertFalse(CaptionFontFallbackPolicy.rendersWithSystemFontsOnly("ja"))
        assertFalse(CaptionFontFallbackPolicy.rendersWithSystemFontsOnly("ar"))
    }

    @Test
    fun contentInferenceSelectsInstalledAndroidFallbackFamilies() {
        assertEquals(FontFamily.NOTO_CJK_SC, CaptionFontFallbackPolicy.fallbackForText("\u4F60\u597D\u4E16\u754C"))
        assertEquals(FontFamily.NOTO_CJK_JP, CaptionFontFallbackPolicy.fallbackForText("\u65E5\u672C\u8A9E\u30C6\u30B9\u30C8"))
        assertEquals(FontFamily.NOTO_CJK_KR, CaptionFontFallbackPolicy.fallbackForText("\uC548\uB155\uD558\uC138\uC694"))
        assertEquals(FontFamily.NOTO_ARABIC, CaptionFontFallbackPolicy.fallbackForText("\u0645\u0631\u062D\u0628\u0627"))
        assertEquals(FontFamily.NOTO_HEBREW, CaptionFontFallbackPolicy.fallbackForText("\u05E9\u05DC\u05D5\u05DD"))
        assertEquals(FontFamily.SYSTEM_SANS_SERIF, CaptionFontFallbackPolicy.fallbackForText("ClearCut 2026"))
    }

    @Test
    fun complexScriptOverridesUnsupportedGenericFamilyName() {
        assertEquals(
            "Noto Sans CJK SC",
            CaptionFontFallbackPolicy.familyNameForText("serif", "\u4F60\u597D"),
        )
        assertEquals(
            "Noto Sans Arabic",
            CaptionFontFallbackPolicy.familyNameForText("custom:LatinDisplay.ttf", "\u0645\u0631\u062D\u0628\u0627"),
        )
        assertEquals("serif", CaptionFontFallbackPolicy.familyNameForText("serif", "Hello"))
    }
}

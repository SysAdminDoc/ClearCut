package com.novacut.editor.engine

import java.util.Locale

/**
 * R5.4d — Locale-aware caption font fallback policy.
 *
 * Latin captions look fine in the default `sans-serif` family. Once captions
 * carry CJK / Arabic / Devanagari / Thai content, the system default may not
 * have glyph coverage and characters render as tofu (□). This policy maps a
 * language tag (BCP-47 or ISO-639-1) to the recommended Noto subset family
 * ClearCut should bundle and select for that locale's caption rendering.
 *
 * The actual font files are not bundled by this commit — bundling Noto CJK
 * alone is ~20 MB per writing system. The policy lives in code so the
 * caption renderer can pick the right family the moment the asset bundle
 * lands, and so the AI Tools / Settings UI can disclose the per-language
 * font cost ahead of an export.
 */
object CaptionFontFallbackPolicy {

    const val ANDROID_SYSTEM_FONT_DIRECTORY = "/system/fonts"

    /**
     * The Noto subset families ClearCut intends to bundle. Each entry maps to
     * a `<font>` family name the renderer can resolve via `Typeface.create`
     * after the asset bundle is installed. Defaults to system sans-serif for
     * Latin-script languages where the platform already has coverage.
     */
    enum class FontFamily(
        val familyName: String,
        val approxBundleBytes: Long,
        val coversWritingSystems: List<String>,
    ) {
        SYSTEM_SANS_SERIF(
            familyName = "sans-serif",
            approxBundleBytes = 0L,
            coversWritingSystems = listOf("Latin", "Cyrillic", "Greek"),
        ),
        NOTO_CJK_SC(
            familyName = "Noto Sans CJK SC",
            approxBundleBytes = 20_000_000L,
            coversWritingSystems = listOf("Han (Simplified)"),
        ),
        NOTO_CJK_TC(
            familyName = "Noto Sans CJK TC",
            approxBundleBytes = 20_000_000L,
            coversWritingSystems = listOf("Han (Traditional)"),
        ),
        NOTO_CJK_JP(
            familyName = "Noto Sans CJK JP",
            approxBundleBytes = 20_000_000L,
            coversWritingSystems = listOf("Han + Kana + Hiragana"),
        ),
        NOTO_CJK_KR(
            familyName = "Noto Sans CJK KR",
            approxBundleBytes = 20_000_000L,
            coversWritingSystems = listOf("Hangul"),
        ),
        NOTO_ARABIC(
            familyName = "Noto Sans Arabic",
            approxBundleBytes = 1_200_000L,
            coversWritingSystems = listOf("Arabic"),
        ),
        NOTO_HEBREW(
            familyName = "Noto Sans Hebrew",
            approxBundleBytes = 700_000L,
            coversWritingSystems = listOf("Hebrew"),
        ),
        NOTO_DEVANAGARI(
            familyName = "Noto Sans Devanagari",
            approxBundleBytes = 1_000_000L,
            coversWritingSystems = listOf("Devanagari (Hindi, Marathi, Sanskrit)"),
        ),
        NOTO_BENGALI(
            familyName = "Noto Sans Bengali",
            approxBundleBytes = 900_000L,
            coversWritingSystems = listOf("Bengali"),
        ),
        NOTO_TAMIL(
            familyName = "Noto Sans Tamil",
            approxBundleBytes = 600_000L,
            coversWritingSystems = listOf("Tamil"),
        ),
        NOTO_THAI(
            familyName = "Noto Sans Thai",
            approxBundleBytes = 500_000L,
            coversWritingSystems = listOf("Thai"),
        ),
    }

    /**
     * Look up the recommended fallback family for the given BCP-47 / ISO-639-1
     * language tag. Case-insensitive. Locale region suffixes are stripped
     * before mapping ("zh-Hans-CN" → "zh", "zh-Hant" → "zh-Hant").
     *
     * The `zh-Hant` → Traditional Chinese path is the one exception that
     * preserves the script subtag; everything else uses just the language
     * subtag.
     */
    fun fallbackFor(languageTag: String): FontFamily {
        val lower = languageTag.trim().lowercase(Locale.US)
        if (lower.isEmpty()) return FontFamily.SYSTEM_SANS_SERIF

        // Special-case Traditional vs Simplified Chinese on the script subtag.
        if (lower.startsWith("zh-hant") || lower == "zh-tw" || lower == "zh-hk") {
            return FontFamily.NOTO_CJK_TC
        }
        if (lower.startsWith("zh")) return FontFamily.NOTO_CJK_SC

        val lang = lower.substringBefore('-')
        return when (lang) {
            "ja" -> FontFamily.NOTO_CJK_JP
            "ko" -> FontFamily.NOTO_CJK_KR
            "ar", "fa", "ur", "ps" -> FontFamily.NOTO_ARABIC
            "he", "yi" -> FontFamily.NOTO_HEBREW
            "hi", "mr", "sa", "ne" -> FontFamily.NOTO_DEVANAGARI
            "bn", "as" -> FontFamily.NOTO_BENGALI
            "ta" -> FontFamily.NOTO_TAMIL
            "th", "lo" -> FontFamily.NOTO_THAI
            else -> FontFamily.SYSTEM_SANS_SERIF
        }
    }

    /**
     * Total bundle size if all listed families are installed. Useful for the
     * Settings disclosure copy ("Caption fonts bundle: ~64 MB total").
     */
    fun totalBundleBytes(families: Collection<FontFamily> = FontFamily.entries): Long =
        families.sumOf { it.approxBundleBytes }

    /**
     * Whether a given language tag would render with system fonts only (no
     * extra bundle download required). UI can use this to skip the
     * disclosure sheet for Latin-script targets.
     */
    fun rendersWithSystemFontsOnly(languageTag: String): Boolean =
        fallbackFor(languageTag) == FontFamily.SYSTEM_SANS_SERIF

    /** Infer the required system fallback directly from caption content. */
    fun fallbackForText(text: String): FontFamily {
        var sawHan = false
        var index = 0
        while (index < text.length) {
            val codePoint = Character.codePointAt(text, index)
            when (Character.UnicodeScript.of(codePoint)) {
                Character.UnicodeScript.HIRAGANA,
                Character.UnicodeScript.KATAKANA -> return FontFamily.NOTO_CJK_JP
                Character.UnicodeScript.HANGUL -> return FontFamily.NOTO_CJK_KR
                Character.UnicodeScript.HAN -> sawHan = true
                Character.UnicodeScript.ARABIC -> return FontFamily.NOTO_ARABIC
                Character.UnicodeScript.HEBREW -> return FontFamily.NOTO_HEBREW
                Character.UnicodeScript.DEVANAGARI -> return FontFamily.NOTO_DEVANAGARI
                Character.UnicodeScript.BENGALI -> return FontFamily.NOTO_BENGALI
                Character.UnicodeScript.TAMIL -> return FontFamily.NOTO_TAMIL
                Character.UnicodeScript.THAI,
                Character.UnicodeScript.LAO -> return FontFamily.NOTO_THAI
                else -> Unit
            }
            index += Character.charCount(codePoint)
        }
        return if (sawHan) FontFamily.NOTO_CJK_SC else FontFamily.SYSTEM_SANS_SERIF
    }

    fun familyNameForText(requestedFamily: String, text: String): String {
        val fallback = fallbackForText(text)
        return if (fallback == FontFamily.SYSTEM_SANS_SERIF) {
            requestedFamily.takeIf { it.isNotBlank() } ?: FontFamily.SYSTEM_SANS_SERIF.familyName
        } else {
            fallback.familyName
        }
    }
}

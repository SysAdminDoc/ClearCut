package com.novacut.editor.engine

import java.util.Locale

/**
 * R5.4d — Locale-aware caption font fallback policy.
 *
 * Latin captions look fine in the default `sans-serif` family. Once captions
 * carry CJK / Arabic / Devanagari / Thai content, the system default may not
 * have glyph coverage and characters render as tofu (□). This policy maps a
 * language tag (BCP-47 or ISO-639-1) to the recommended Noto subset family
 * NovaCut should bundle and select for that locale's caption rendering.
 *
 * The actual font files are not bundled by this commit — bundling Noto CJK
 * alone is ~20 MB per writing system. The policy lives in code so the
 * caption renderer can pick the right family the moment the asset bundle
 * lands, and so the AI Tools / Settings UI can disclose the per-language
 * font cost ahead of an export.
 */
object CaptionFontFallbackPolicy {

    /**
     * The Noto subset families NovaCut intends to bundle. Each entry maps to
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
            familyName = "noto-sans-sc",
            approxBundleBytes = 20_000_000L,
            coversWritingSystems = listOf("Han (Simplified)"),
        ),
        NOTO_CJK_TC(
            familyName = "noto-sans-tc",
            approxBundleBytes = 20_000_000L,
            coversWritingSystems = listOf("Han (Traditional)"),
        ),
        NOTO_CJK_JP(
            familyName = "noto-sans-jp",
            approxBundleBytes = 20_000_000L,
            coversWritingSystems = listOf("Han + Kana + Hiragana"),
        ),
        NOTO_CJK_KR(
            familyName = "noto-sans-kr",
            approxBundleBytes = 20_000_000L,
            coversWritingSystems = listOf("Hangul"),
        ),
        NOTO_ARABIC(
            familyName = "noto-sans-arabic",
            approxBundleBytes = 1_200_000L,
            coversWritingSystems = listOf("Arabic"),
        ),
        NOTO_HEBREW(
            familyName = "noto-sans-hebrew",
            approxBundleBytes = 700_000L,
            coversWritingSystems = listOf("Hebrew"),
        ),
        NOTO_DEVANAGARI(
            familyName = "noto-sans-devanagari",
            approxBundleBytes = 1_000_000L,
            coversWritingSystems = listOf("Devanagari (Hindi, Marathi, Sanskrit)"),
        ),
        NOTO_BENGALI(
            familyName = "noto-sans-bengali",
            approxBundleBytes = 900_000L,
            coversWritingSystems = listOf("Bengali"),
        ),
        NOTO_TAMIL(
            familyName = "noto-sans-tamil",
            approxBundleBytes = 600_000L,
            coversWritingSystems = listOf("Tamil"),
        ),
        NOTO_THAI(
            familyName = "noto-sans-thai",
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
}

package com.novacut.editor.engine

import com.novacut.editor.engine.CaptionTranslationEngine.LanguagePairQuality
import com.novacut.editor.engine.CaptionTranslationEngine.ModelVariant
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * R5.4a / R6.7 — Caption translation editor surface tests.
 *
 * The engine is a stub today. These tests cover the new pure-Kotlin
 * editor-surface helpers introduced for the side-by-side preview UX:
 * the LanguagePairQuality lookup and the TranslatedSegment editor state
 * value type.
 */
class CaptionTranslationEngineTest {

    private val engine = CaptionTranslationEngine(context = stubContext())

    private fun stubContext(): android.content.Context =
        object : android.content.ContextWrapper(null) {}

    private fun pq(variant: ModelVariant, src: String, tgt: String) =
        engine.pairQuality(variant, src, tgt)

    // --- pairQuality ---

    @Test
    fun pairQuality_identity_isExcellent() {
        assertEquals(LanguagePairQuality.EXCELLENT, pq(ModelVariant.NLLB_600M, "en", "en"))
    }

    @Test
    fun pairQuality_blankLang_returnsUnknown() {
        assertEquals(LanguagePairQuality.UNKNOWN, pq(ModelVariant.NLLB_600M, "", "en"))
        assertEquals(LanguagePairQuality.UNKNOWN, pq(ModelVariant.NLLB_600M, "en", ""))
    }

    @Test
    fun pairQuality_bergamot_europeanPair_isExcellent() {
        assertEquals(LanguagePairQuality.EXCELLENT, pq(ModelVariant.BERGAMOT_PER_PAIR, "en", "de"))
        assertEquals(LanguagePairQuality.EXCELLENT, pq(ModelVariant.BERGAMOT_PER_PAIR, "fr", "es"))
    }

    @Test
    fun pairQuality_madlad_europeanPair_isExcellent() {
        assertEquals(LanguagePairQuality.EXCELLENT, pq(ModelVariant.MADLAD_400_3B, "en", "de"))
    }

    @Test
    fun pairQuality_madlad_eastAsianPair_isGood() {
        assertEquals(LanguagePairQuality.GOOD, pq(ModelVariant.MADLAD_400_3B, "en", "ja"))
        assertEquals(LanguagePairQuality.GOOD, pq(ModelVariant.MADLAD_400_3B, "zh", "en"))
    }

    @Test
    fun pairQuality_nllb600_europeanPair_isGood() {
        assertEquals(LanguagePairQuality.GOOD, pq(ModelVariant.NLLB_600M, "en", "de"))
    }

    @Test
    fun pairQuality_nllb300_anyPair_isFair() {
        assertEquals(LanguagePairQuality.FAIR, pq(ModelVariant.NLLB_300M, "en", "de"))
        assertEquals(LanguagePairQuality.FAIR, pq(ModelVariant.NLLB_300M, "fr", "ja"))
    }

    @Test
    fun pairQuality_uncoveredPair_isExperimental() {
        assertEquals(LanguagePairQuality.EXPERIMENTAL, pq(ModelVariant.MADLAD_400_3B, "yo", "ig"))
        assertEquals(LanguagePairQuality.EXPERIMENTAL, pq(ModelVariant.NLLB_600M, "yo", "ig"))
    }

    @Test
    fun pairQuality_recognizesMacroFromLocale() {
        // "en-US" should be treated as "en" for the lookup.
        assertEquals(LanguagePairQuality.EXCELLENT, pq(ModelVariant.MADLAD_400_3B, "en-US", "de-DE"))
    }

    @Test
    fun pairQuality_isCaseInsensitive() {
        assertEquals(LanguagePairQuality.EXCELLENT, pq(ModelVariant.MADLAD_400_3B, "EN", "DE"))
    }

    // --- TranslatedSegment editorState ---

    @Test
    fun translatedSegment_defaultEditorStateIsTranslated() {
        val seg = CaptionTranslationEngine.TranslatedSegment(
            sourceText = "Hello",
            targetText = "Hola",
            startTimeMs = 0,
            endTimeMs = 1_000,
        )
        assertEquals(CaptionTranslationEngine.EditorRowState.TRANSLATED, seg.editorState)
    }

    @Test
    fun translatedSegment_canMarkUserEdited() {
        val seg = CaptionTranslationEngine.TranslatedSegment(
            sourceText = "Hello",
            targetText = "Hola — adjusted",
            startTimeMs = 0,
            endTimeMs = 1_000,
            editorState = CaptionTranslationEngine.EditorRowState.USER_EDITED,
        )
        assertEquals(CaptionTranslationEngine.EditorRowState.USER_EDITED, seg.editorState)
    }

    @Test
    fun bergamot_modelVariantMetadata() {
        val b = ModelVariant.BERGAMOT_PER_PAIR
        assertEquals("Bergamot (per language pair)", b.displayName)
        assertEquals(100, b.sizeMb)
        // Bergamot is per language pair, so the languageCount field carries 2.
        assertEquals(2, b.languageCount)
    }

    @Test
    fun madlad_languageCountIs419() {
        // R6.7 — MADLAD-400 is the canonical wide-coverage target.
        assertEquals(419, ModelVariant.MADLAD_400_3B.languageCount)
    }
}

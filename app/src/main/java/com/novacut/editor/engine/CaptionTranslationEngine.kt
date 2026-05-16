package com.novacut.editor.engine

import android.content.Context
import android.util.Log
import com.novacut.editor.engine.whisper.SherpaAsrEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stub engine for on-device caption translation. See ROADMAP.md Tier C.5
 * and Round 6 R6.7.
 *
 * Round 6 (R6.7) pivots the recommended target from NLLB-200 to MADLAD-400 +
 * Mozilla Bergamot:
 *  - MADLAD-400 3B Q4: ~1.5 GB, 419 languages including long-tail dialects,
 *    aggressively quantizable for mobile.
 *  - Mozilla Bergamot models: per-language-pair ~100 MB, Firefox's offline
 *    translation models; better quality than NLLB on common European pairs.
 *  - NLLB-200 distilled remains the fallback for languages neither MADLAD
 *    nor Bergamot covers well.
 *
 * Word timings are re-interpolated based on target word count so downstream
 * karaoke rendering keeps working when the target text expands or contracts
 * vs the source.
 *
 * ## R5.4a — In-editor preview UX
 *
 * Beyond the model dependency, the caption-translation editor needs:
 *  - Side-by-side source/target caption rows so the user can compare and
 *    spot-fix on the fly.
 *  - A per-caption "regenerate" action (re-translate one segment without
 *    touching the rest).
 *  - A per-language quality chip surfaced from [LanguagePairQuality].
 *
 * The data model lives on the engine ([TranslatedSegment.editorState] plus
 * [LanguagePairQuality]); panel rendering lands in a follow-up Compose commit.
 */
@Singleton
class CaptionTranslationEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {

    enum class ModelVariant(val displayName: String, val sizeMb: Int, val languageCount: Int) {
        NLLB_300M("NLLB-200 300M distilled", 350, 200),
        NLLB_600M("NLLB-200 600M distilled", 600, 200),
        MADLAD_400_3B("MADLAD-400 3B Q4", 1500, 419),
        BERGAMOT_PER_PAIR("Bergamot (per language pair)", 100, 2),
    }

    /**
     * R5.4a — Source/target/quality state for a single caption row in the
     * translation editor. Marks the row as user-edited or pending regenerate
     * so the panel can show the right affordance.
     */
    enum class EditorRowState {
        TRANSLATED,        // Engine output unedited
        USER_EDITED,       // User has overridden the engine output
        REGENERATE_PENDING // User tapped regenerate; engine is recomputing
    }

    /**
     * R5.4a — Surfaced quality hint for a (source, target) language pair, so
     * the panel can show users when they're about to translate into a
     * known-weak target. Values come from a curated table per model variant;
     * MADLAD-400 has known-good coverage for European + East Asian pairs,
     * narrower coverage for African + Pacific languages.
     */
    enum class LanguagePairQuality(val displayName: String) {
        EXCELLENT("Excellent"),
        GOOD("Good"),
        FAIR("Fair"),
        EXPERIMENTAL("Experimental"),
        UNKNOWN("Unknown"),
    }

    /**
     * Curated quality lookup for a (source, target) pair on a given model.
     * Returns UNKNOWN for unknown pairs. Pure function so the UI can probe
     * before the model is downloaded.
     */
    fun pairQuality(
        variant: ModelVariant,
        sourceLang: String,
        targetLang: String,
    ): LanguagePairQuality {
        val src = sourceLang.lowercase()
        val tgt = targetLang.lowercase()
        if (src.isBlank() || tgt.isBlank()) return LanguagePairQuality.UNKNOWN
        if (src == tgt) return LanguagePairQuality.EXCELLENT
        val srcMacro = src.substringBefore('-')
        val tgtMacro = tgt.substringBefore('-')
        val europeanMajor = setOf("en", "es", "fr", "de", "it", "pt", "nl", "ru", "pl")
        val eastAsianMajor = setOf("zh", "ja", "ko")
        return when {
            variant == ModelVariant.BERGAMOT_PER_PAIR &&
                srcMacro in europeanMajor && tgtMacro in europeanMajor -> LanguagePairQuality.EXCELLENT
            variant == ModelVariant.MADLAD_400_3B &&
                srcMacro in europeanMajor && tgtMacro in europeanMajor -> LanguagePairQuality.EXCELLENT
            variant == ModelVariant.MADLAD_400_3B &&
                (srcMacro in eastAsianMajor || tgtMacro in eastAsianMajor) -> LanguagePairQuality.GOOD
            variant == ModelVariant.NLLB_600M &&
                srcMacro in europeanMajor && tgtMacro in europeanMajor -> LanguagePairQuality.GOOD
            variant == ModelVariant.NLLB_300M -> LanguagePairQuality.FAIR
            else -> LanguagePairQuality.EXPERIMENTAL
        }
    }

    data class TranslatedSegment(
        val sourceText: String,
        val targetText: String,
        val startTimeMs: Long,
        val endTimeMs: Long,
        val words: List<SherpaAsrEngine.WordTimestamp> = emptyList(),
        /** R5.4a — current editor state for the row. */
        val editorState: EditorRowState = EditorRowState.TRANSLATED,
    )

    private val _modelState = MutableStateFlow(ModelState.NOT_DOWNLOADED)
    val modelState: StateFlow<ModelState> = _modelState

    enum class ModelState { NOT_DOWNLOADED, DOWNLOADING, READY, ERROR }

    fun isModelReady(): Boolean = false

    fun getSupportedLanguages(variant: ModelVariant = ModelVariant.NLLB_600M): List<String> =
        when (variant) {
            ModelVariant.NLLB_300M, ModelVariant.NLLB_600M -> NLLB_LANGUAGES
            ModelVariant.MADLAD_400_3B -> MADLAD_LANGUAGES
        }

    suspend fun downloadModel(
        variant: ModelVariant = ModelVariant.NLLB_600M,
        onProgress: (Float) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "downloadModel: stub -- requires ${variant.displayName}")
        false
    }

    /**
     * Translate a list of caption segments. Word timings are re-interpolated
     * based on target word count so downstream karaoke rendering keeps working.
     */
    suspend fun translate(
        segments: List<SherpaAsrEngine.TranscriptionSegment>,
        sourceLang: String,
        targetLang: String,
        onProgress: (Float) -> Unit = {}
    ): List<TranslatedSegment> = withContext(Dispatchers.Default) {
        Log.d(TAG, "translate: stub -- $sourceLang -> $targetLang (${segments.size} segments)")
        segments.map { seg ->
            TranslatedSegment(
                sourceText = seg.text,
                targetText = seg.text,
                startTimeMs = seg.startTimeMs,
                endTimeMs = seg.endTimeMs,
                words = seg.words
            )
        }
    }

    companion object {
        private const val TAG = "CaptionTranslate"

        // Abbreviated: production list is the full 200/400.
        val NLLB_LANGUAGES = listOf(
            "en", "es", "fr", "de", "it", "pt", "nl", "ru", "pl", "uk", "cs", "ro",
            "zh", "ja", "ko", "ar", "he", "tr", "fa", "hi", "bn", "ta", "te", "vi",
            "th", "id", "ms", "tl", "sw", "am", "yo", "zu", "ha", "ig", "af"
        )
        val MADLAD_LANGUAGES = NLLB_LANGUAGES + listOf(
            "co", "la", "eo", "cy", "gd", "ga", "mt", "is", "fo", "lb", "rm"
        )
    }
}

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
            ModelVariant.BERGAMOT_PER_PAIR -> BERGAMOT_LANGUAGES
        }

    suspend fun downloadModel(
        variant: ModelVariant = ModelVariant.NLLB_600M,
        onProgress: (Float) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "downloadModel: stub -- requires ${variant.displayName}")
        false
    }

    /** Raised when [translate] is called before a translation model is ready. */
    class TranslationUnavailableException :
        IllegalStateException("Caption translation model is not installed")

    /**
     * Translate a list of caption segments. Word timings are re-interpolated
     * based on target word count so downstream karaoke rendering keeps working.
     *
     * No translation model is integrated yet, so this fails fast with
     * [TranslationUnavailableException] rather than returning the source text
     * unchanged — returning identity output would silently label untranslated
     * captions as translated. The real translation path lands behind
     * [isModelReady] once a model is integrated.
     */
    suspend fun translate(
        segments: List<SherpaAsrEngine.TranscriptionSegment>,
        sourceLang: String,
        targetLang: String,
        onProgress: (Float) -> Unit = {}
    ): List<TranslatedSegment> = withContext(Dispatchers.Default) {
        if (!isModelReady()) {
            Log.d(TAG, "translate: model unavailable -- $sourceLang -> $targetLang")
            throw TranslationUnavailableException()
        }
        // Real translation backend goes here once a model is integrated. Until
        // then isModelReady() is always false so this line is unreachable.
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

    // --- R5.4a UI-helper layer ---
    //
    // Pure functions consumed by the Compose translation panel. Each helper
    // is small enough to test on the JVM without standing up the panel and
    // without invoking the (still-stubbed) translate() call.

    /**
     * Single editor-row view model: source + target + quality + state. The
     * panel renders one of these per caption row, with chip colour driven by
     * [editorState] and [quality].
     */
    data class EditorRow(
        val index: Int,
        val segment: TranslatedSegment,
        val quality: LanguagePairQuality,
    ) {
        val isPendingRegenerate: Boolean
            get() = segment.editorState == EditorRowState.REGENERATE_PENDING

        val isUserEdited: Boolean
            get() = segment.editorState == EditorRowState.USER_EDITED
    }

    /**
     * Build the editor-row view model list. The [quality] is computed once
     * per pair (not per row) because (variant, src, tgt) is identical
     * across all rows of one translation pass.
     */
    fun buildEditorRows(
        segments: List<TranslatedSegment>,
        variant: ModelVariant,
        sourceLang: String,
        targetLang: String,
    ): List<EditorRow> {
        val quality = pairQuality(variant, sourceLang, targetLang)
        return segments.mapIndexed { index, seg ->
            EditorRow(index = index, segment = seg, quality = quality)
        }
    }

    /**
     * Replace a single row's target text and mark it [EditorRowState.USER_EDITED].
     * Returns a fresh list — the panel rebinds against the result. No-op when
     * [index] is out of bounds.
     */
    fun applyUserEdit(
        segments: List<TranslatedSegment>,
        index: Int,
        newTargetText: String,
    ): List<TranslatedSegment> {
        if (index !in segments.indices) return segments
        return segments.mapIndexed { i, s ->
            if (i == index) s.copy(
                targetText = newTargetText,
                editorState = EditorRowState.USER_EDITED,
            ) else s
        }
    }

    /**
     * Tag a row [EditorRowState.REGENERATE_PENDING] so the panel can render a
     * spinner while the engine reruns translation for that segment only.
     */
    fun markRegeneratePending(
        segments: List<TranslatedSegment>,
        index: Int,
    ): List<TranslatedSegment> {
        if (index !in segments.indices) return segments
        return segments.mapIndexed { i, s ->
            if (i == index) s.copy(editorState = EditorRowState.REGENERATE_PENDING) else s
        }
    }

    /**
     * Replace a pending row with fresh engine output. Sets state back to
     * [EditorRowState.TRANSLATED]. Used by the regenerate completion path.
     */
    fun completeRegenerate(
        segments: List<TranslatedSegment>,
        index: Int,
        newTargetText: String,
        newWords: List<SherpaAsrEngine.WordTimestamp> = emptyList(),
    ): List<TranslatedSegment> {
        if (index !in segments.indices) return segments
        return segments.mapIndexed { i, s ->
            if (i == index) s.copy(
                targetText = newTargetText,
                words = newWords.ifEmpty { s.words },
                editorState = EditorRowState.TRANSLATED,
            ) else s
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
        val BERGAMOT_LANGUAGES = listOf(
            "en", "es", "fr", "de", "it", "pt", "nl", "ru", "pl"
        )
    }
}

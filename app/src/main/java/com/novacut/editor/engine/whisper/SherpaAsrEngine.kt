package com.novacut.editor.engine.whisper

import android.content.Context
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stub engine -- requires the official Sherpa-ONNX Android AAR for the native backend.
 * See ROADMAP.md (Tier A.1, refreshed in Round 6 R6.8).
 *
 * Delegates to built-in [WhisperEngine] (ONNX Runtime) when Sherpa-ONNX is unavailable.
 *
 * Sherpa-ONNX target:
 *   https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.2/sherpa-onnx-1.13.2.aar
 *
 * ## Three-target model policy (R6.8)
 *
 * Models stay explicit downloads. The recommendation order is:
 *
 *   1. Moonshine v2 Tiny EN — ~33 MB, fastest English ASR target.
 *      Default for English content on every device tier.
 *   2. Whisper Tiny multilingual — ~100 MB, default multilingual fallback.
 *      Smallest universal-language footprint suitable for mid-range devices.
 *   3. Whisper Large V3 Turbo — ~800 MB FP16 (4-decoder-layer ONNX).
 *      Premium multilingual target. ONNX Runtime + Arm KleidiAI delivers
 *      ~2.6x speedup on modern Arm Android. **Gated** on the same premium
 *      tier as SAM 2.1: requires `allowPremiumModels = true` AND
 *      `availableRamMb >= MIN_TURBO_RAM_MB`. Falls back to Whisper Tiny
 *      multilingual when the tier check fails.
 *
 * Higher-accuracy English variants (Moonshine v2 Base) are kept in the enum
 * for callers who explicitly opt in via Settings; they are not part of the
 * default `preferredModelFor(language, ...)` recommendation.
 */
@Singleton
class SherpaAsrEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val whisperEngine: WhisperEngine
) {
    enum class AsrBackend { SHERPA_ONNX, BUILTIN_WHISPER }
    enum class ModelVariant(
        val displayName: String,
        val languages: String,
        val sizeMb: Int,
        val modelPackageName: String,
        val isMoonshineV2: Boolean,
        val isMultilingual: Boolean = false,
        /** Premium-tier models require the device-gating rule in [preferredModelFor]. */
        val requiresPremiumTier: Boolean = false,
        /** Minimum available RAM in MB before the model can be recommended. */
        val minimumRamMb: Int = 0,
    ) {
        MOONSHINE_V2_TINY_EN(
            displayName = "Moonshine v2 Tiny",
            languages = "English",
            sizeMb = 33,
            modelPackageName = "moonshine-v2-tiny-en",
            isMoonshineV2 = true
        ),
        MOONSHINE_V2_BASE_EN(
            displayName = "Moonshine v2 Base",
            languages = "English",
            sizeMb = 110,
            modelPackageName = "moonshine-v2-base-en",
            isMoonshineV2 = true
        ),
        WHISPER_TINY_MULTILINGUAL(
            displayName = "Whisper Tiny",
            languages = "99 languages",
            sizeMb = 100,
            modelPackageName = "whisper-tiny-multilingual",
            isMoonshineV2 = false,
            isMultilingual = true
        ),
        WHISPER_BASE_MULTILINGUAL(
            displayName = "Whisper Base",
            languages = "99 languages",
            sizeMb = 200,
            modelPackageName = "whisper-base-multilingual",
            isMoonshineV2 = false,
            isMultilingual = true
        ),
        // R6.8 — Whisper Large V3 Turbo (4-decoder-layer ONNX, FP16). Premium tier:
        // ~800 MB on disk, recommended only when allowPremiumModels and the
        // device meets the RAM floor. ONNX Runtime + Arm KleidiAI delivers ~2.6x
        // speedup on Arm Android over the Tiny baseline.
        WHISPER_LARGE_V3_TURBO_MULTILINGUAL(
            displayName = "Whisper Large V3 Turbo",
            languages = "99 languages",
            sizeMb = 800,
            modelPackageName = "whisper-large-v3-turbo",
            isMoonshineV2 = false,
            isMultilingual = true,
            requiresPremiumTier = true,
            minimumRamMb = 6_144
        ),
    }

    data class TranscriptionSegment(
        val text: String,
        val startTimeMs: Long,
        val endTimeMs: Long,
        val words: List<WordTimestamp> = emptyList()
    )

    data class WordTimestamp(
        val word: String,
        val startTimeMs: Long,
        val endTimeMs: Long,
        val confidence: Float = 1f
    )

    data class TranscriptionResult(
        val segments: List<TranscriptionSegment>,
        val language: String = "en",
        val durationMs: Long = 0L
    )

    private val _modelState = MutableStateFlow(ModelState.NOT_DOWNLOADED)
    val modelState: StateFlow<ModelState> = _modelState

    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress

    private var activeBackend: AsrBackend = AsrBackend.BUILTIN_WHISPER

    enum class ModelState {
        NOT_DOWNLOADED, DOWNLOADING, READY, ERROR
    }

    /**
     * Returns the active ASR backend. Currently always BUILTIN_WHISPER
     * since Sherpa-ONNX dependency is not present.
     */
    fun getActiveBackend(): AsrBackend = activeBackend

    /**
     * Target model policy for the future native Sherpa-ONNX path.
     *
     * The active runtime still falls back to [WhisperEngine] until NovaCut has a
     * deliberate packaging decision for the 50+ MB Android AAR/native payload,
     * but callers and settings surfaces should converge on this model order.
     *
     * Pass [allowPremiumModels] = true and a real [availableRamMb] reading
     * (from `ActivityManager.getMemoryInfo()`) to opt into Whisper Large V3 Turbo
     * for multilingual content on premium devices (R6.8).
     */
    fun getPreferredModel(
        language: String = "en",
        allowPremiumModels: Boolean = false,
        availableRamMb: Int = 0,
    ): ModelVariant = preferredModelFor(language, allowPremiumModels, availableRamMb)

    /**
     * Transcribe audio from a video/audio URI.
     * Delegates to the built-in [WhisperEngine] and converts results.
     */
    suspend fun transcribe(
        uri: Uri,
        language: String = "en",
        onProgress: (Float) -> Unit = {}
    ): TranscriptionResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "transcribe: delegating to built-in WhisperEngine (Sherpa-ONNX not available)")
        val segments = whisperEngine.transcribe(uri, onProgress)
        TranscriptionResult(
            segments = segments.map { seg ->
                TranscriptionSegment(
                    text = seg.text,
                    startTimeMs = seg.startMs,
                    endTimeMs = seg.endMs
                )
            },
            language = language,
            durationMs = segments.lastOrNull()?.endMs ?: 0L
        )
    }

    /**
     * Get list of supported languages for the active model.
     */
    fun getSupportedLanguages(): List<String> {
        return WHISPER_LANGUAGES
    }

    companion object {
        private const val TAG = "SherpaASR"
        const val TARGET_SHERPA_ONNX_VERSION = "1.13.2"
        const val MIN_MOONSHINE_V2_SHERPA_VERSION = "1.12.28"
        const val ANDROID_AAR_ASSET_NAME = "sherpa-onnx-$TARGET_SHERPA_ONNX_VERSION.aar"
        const val ANDROID_AAR_DOWNLOAD_URL =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/v$TARGET_SHERPA_ONNX_VERSION/$ANDROID_AAR_ASSET_NAME"
        val DEFAULT_ENGLISH_MODEL: ModelVariant = ModelVariant.MOONSHINE_V2_TINY_EN
        val MULTILINGUAL_FALLBACK_MODEL: ModelVariant = ModelVariant.WHISPER_TINY_MULTILINGUAL
        val PREMIUM_MULTILINGUAL_MODEL: ModelVariant = ModelVariant.WHISPER_LARGE_V3_TURBO_MULTILINGUAL

        /**
         * Three-target ASR policy (R6.8):
         * - English → Moonshine v2 Tiny (always).
         * - Multilingual on a premium device with premium-models enabled →
         *   Whisper Large V3 Turbo.
         * - Multilingual otherwise → Whisper Tiny multilingual.
         */
        fun preferredModelFor(
            language: String,
            allowPremiumModels: Boolean = false,
            availableRamMb: Int = 0,
        ): ModelVariant {
            val normalized = language.trim().lowercase(Locale.US)
            val isEnglish = normalized == "en" ||
                normalized.startsWith("en-") ||
                normalized == "english"
            if (isEnglish) return DEFAULT_ENGLISH_MODEL
            val premiumOk = allowPremiumModels &&
                availableRamMb >= PREMIUM_MULTILINGUAL_MODEL.minimumRamMb
            return if (premiumOk) PREMIUM_MULTILINGUAL_MODEL else MULTILINGUAL_FALLBACK_MODEL
        }

        // No legacy single-arg `preferredModelFor(String)` overload — the three-target
        // function above accepts defaults for both premium flags, so existing one-arg
        // call sites keep working. Avoids Kotlin overload-resolution ambiguity.

        val WHISPER_LANGUAGES = listOf(
            "en", "zh", "de", "es", "ru", "ko", "fr", "ja", "pt", "tr",
            "pl", "ca", "nl", "ar", "sv", "it", "id", "hi", "fi", "vi",
            "he", "uk", "el", "ms", "cs", "ro", "da", "hu", "ta", "no",
            "th", "ur", "hr", "bg", "lt", "la", "mi", "ml", "cy", "sk",
            "te", "fa", "lv", "bn", "sr", "az", "sl", "kn", "et", "mk"
        )
    }
}

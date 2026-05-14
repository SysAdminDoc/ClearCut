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
 * See ROADMAP.md
 *
 * Delegates to built-in [WhisperEngine] (ONNX Runtime) when Sherpa-ONNX is unavailable.
 *
 * Sherpa-ONNX target:
 *   https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.2/sherpa-onnx-1.13.2.aar
 *
 * Models stay explicit downloads:
 *   - Moonshine v2 Tiny EN: ~33 MB, default English target
 *   - Moonshine v2 Base EN: ~110 MB, higher-quality English target
 *   - Whisper Tiny multilingual: ~100 MB, multilingual fallback
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
        val isMoonshineV2: Boolean
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
            isMoonshineV2 = false
        ),
        WHISPER_BASE_MULTILINGUAL(
            displayName = "Whisper Base",
            languages = "99 languages",
            sizeMb = 200,
            modelPackageName = "whisper-base-multilingual",
            isMoonshineV2 = false
        )
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
     */
    fun getPreferredModel(language: String = "en"): ModelVariant =
        preferredModelFor(language)

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

        fun preferredModelFor(language: String): ModelVariant {
            val normalized = language.trim().lowercase(Locale.US)
            return if (normalized == "en" || normalized.startsWith("en-") || normalized == "english") {
                DEFAULT_ENGLISH_MODEL
            } else {
                MULTILINGUAL_FALLBACK_MODEL
            }
        }

        val WHISPER_LANGUAGES = listOf(
            "en", "zh", "de", "es", "ru", "ko", "fr", "ja", "pt", "tr",
            "pl", "ca", "nl", "ar", "sv", "it", "id", "hi", "fi", "vi",
            "he", "uk", "el", "ms", "cs", "ro", "da", "hu", "ta", "no",
            "th", "ur", "hr", "bg", "lt", "la", "mi", "ml", "cy", "sk",
            "te", "fa", "lv", "bn", "sr", "az", "sl", "kn", "et", "mk"
        )
    }
}

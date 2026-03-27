package com.novacut.editor.engine.whisper

import android.content.Context
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stub engine -- requires com.k2fsa.sherpa:onnx-android for Sherpa-ONNX backend.
 * See ROADMAP.md
 *
 * Delegates to built-in [WhisperEngine] (ONNX Runtime) when Sherpa-ONNX is unavailable.
 *
 * Sherpa-ONNX dependency (add to app/build.gradle.kts when ready):
 *   implementation("com.k2fsa.sherpa:onnx-android:1.10.+")
 *
 * Models (download on first use from HuggingFace):
 *   - Whisper Tiny multilingual: ~100MB, 27 tok/s, 99 languages
 *   - Moonshine Tiny: ~125MB, 42 tok/s, English only
 */
@Singleton
class SherpaAsrEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val whisperEngine: WhisperEngine
) {
    enum class AsrBackend { SHERPA_ONNX, BUILTIN_WHISPER }
    enum class ModelVariant(val displayName: String, val languages: String, val sizeMb: Int) {
        WHISPER_TINY("Whisper Tiny", "99 languages", 100),
        MOONSHINE_TINY("Moonshine Tiny", "English", 125),
        WHISPER_BASE("Whisper Base", "99 languages", 200)
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

        val WHISPER_LANGUAGES = listOf(
            "en", "zh", "de", "es", "ru", "ko", "fr", "ja", "pt", "tr",
            "pl", "ca", "nl", "ar", "sv", "it", "id", "hi", "fi", "vi",
            "he", "uk", "el", "ms", "cs", "ro", "da", "hu", "ta", "no",
            "th", "ur", "hr", "bg", "lt", "la", "mi", "ml", "cy", "sk",
            "te", "fa", "lv", "bn", "sr", "az", "sl", "kn", "et", "mk"
        )
    }
}

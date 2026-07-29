package com.novacut.editor.engine

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
 * Stub engine -- requires Sherpa-ONNX XTTS v2. See ROADMAP.md Tier C.3.
 *
 * Clones a voice from a 6-second enrollment sample and synthesises text in that
 * voice across 16 languages. Pairs with [TtsEngine] as a premium voice source.
 *
 * Shares the Sherpa-ONNX dependency with [com.novacut.editor.engine.whisper.SherpaAsrEngine]
 * and Piper TTS so the artefact size cost is amortised.
 *
 * Model: XTTS v2 quantised ONNX bundle, ~400 MB.
 */
@Singleton
class VoiceCloneEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {

    data class VoiceProfile(
        val id: String,
        val displayName: String,
        val enrollmentUri: Uri,
        val language: String,
        val createdAtMs: Long
    )

    private val _modelState = MutableStateFlow(ModelState.NOT_DOWNLOADED)
    val modelState: StateFlow<ModelState> = _modelState

    private val _profiles = MutableStateFlow<List<VoiceProfile>>(emptyList())
    val profiles: StateFlow<List<VoiceProfile>> = _profiles

    enum class ModelState { NOT_DOWNLOADED, DOWNLOADING, READY, ERROR }

    fun isModelReady(): Boolean = false

    fun getSupportedLanguages(): List<String> = SUPPORTED_LANGUAGES

    /**
     * Enroll a new voice profile from a 6-second audio sample. Shorter samples
     * reduce clone fidelity; longer samples beyond ~12 s offer diminishing returns.
     */
    suspend fun enrollVoice(
        enrollmentUri: Uri,
        displayName: String,
        language: String
    ): VoiceProfile? = withContext(Dispatchers.IO) {
        Log.d(TAG, "enrollVoice: stub -- requires XTTS v2 (${RedactedLog.assetId(displayName)}, $language)")
        null
    }

    /**
     * Synthesise [text] in the enrolled voice and write a WAV file to [outputUri].
     */
    suspend fun synthesize(
        text: String,
        profile: VoiceProfile,
        outputUri: Uri,
        speed: Float = 1f,
        onProgress: (Float) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "synthesize: stub -- requires XTTS v2 (${text.length} chars)")
        false
    }

    companion object {
        private const val TAG = "VoiceClone"

        val SUPPORTED_LANGUAGES = listOf(
            "en", "es", "fr", "de", "it", "pt", "pl", "tr",
            "ru", "nl", "cs", "ar", "zh", "ja", "hu", "ko"
        )
    }
}

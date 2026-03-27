package com.novacut.editor.engine

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * TTS engine with Piper (VITS architecture) via Sherpa-ONNX as future primary backend,
 * and Android system TTS as the current working fallback.
 *
 * Sherpa-ONNX dependency (add to build.gradle.kts when ready):
 *   implementation("com.k2fsa.sherpa:onnx-android:1.10.+")
 *
 * Voice models (~15-65MB each, downloaded from HuggingFace on first use):
 *   - en_US-amy-medium (female, 22kHz, ~30MB)
 *   - en_US-ryan-medium (male, 22kHz, ~30MB)
 *   - en_GB-alba-medium (British female, ~30MB)
 *   - de_DE-thorsten-medium (German male, ~30MB)
 *   - es_ES-davefx-medium (Spanish male, ~30MB)
 */
@Singleton
class PiperTtsEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    data class VoiceProfile(
        val id: String,
        val name: String,
        val language: String,
        val gender: String,
        val sampleRate: Int = 22050,
        val modelSizeMb: Int,
        val isDownloaded: Boolean = false
    )

    private val voicesDir = File(context.filesDir, "piper_voices").also { it.mkdirs() }

    /**
     * Available voice profiles. Models are downloaded on first use.
     */
    fun getAvailableVoices(): List<VoiceProfile> = listOf(
        VoiceProfile("en_US-amy-medium", "Amy (US)", "en", "female", 22050, 30),
        VoiceProfile("en_US-ryan-medium", "Ryan (US)", "en", "male", 22050, 30),
        VoiceProfile("en_GB-alba-medium", "Alba (UK)", "en", "female", 22050, 30),
        VoiceProfile("de_DE-thorsten-medium", "Thorsten (DE)", "de", "male", 22050, 30),
        VoiceProfile("es_ES-davefx-medium", "Dave (ES)", "es", "male", 22050, 30),
        VoiceProfile("fr_FR-siwis-medium", "Siwis (FR)", "fr", "female", 22050, 30),
        VoiceProfile("ja_JP-takumi-medium", "Takumi (JP)", "ja", "male", 22050, 35),
        VoiceProfile("zh_CN-huayan-medium", "Huayan (CN)", "zh", "female", 22050, 35),
        VoiceProfile("ko_KR-sunhi-medium", "Sunhi (KR)", "ko", "female", 22050, 30),
        VoiceProfile("pt_BR-faber-medium", "Faber (BR)", "pt", "male", 22050, 30)
    ).map { it.copy(isDownloaded = File(voicesDir, it.id).exists()) }

    /**
     * Synthesize text to audio file.
     * Currently uses Android system TTS. When Sherpa-ONNX is integrated,
     * Piper voices will be the primary backend.
     *
     * @param text Text to synthesize
     * @param voiceId Voice profile ID
     * @param speed Speech rate multiplier (0.5 = slow, 1.0 = normal, 2.0 = fast)
     * @return Output audio file, or null on failure
     */
    suspend fun synthesize(
        text: String,
        voiceId: String = "en_US-amy-medium",
        speed: Float = 1.0f,
        onProgress: (Float) -> Unit = {}
    ): File? = withContext(Dispatchers.IO) {
        val outputDir = File(context.filesDir, "tts_output").also { it.mkdirs() }
        val outputFile = File(outputDir, "piper_${System.currentTimeMillis()}.wav")

        try {
            onProgress(0.1f)

            // Use Android system TTS (always available)
            Log.d(TAG, "Using Android system TTS (Sherpa-ONNX Piper not yet integrated)")
            val voice = getAvailableVoices().find { it.id == voiceId }
            val locale = voice?.let { Locale(it.language) } ?: Locale.US
            val result = synthesizeWithSystemTts(text, outputFile, locale, speed)
            onProgress(1f)
            if (result) outputFile else null
        } catch (e: Exception) {
            Log.e(TAG, "TTS synthesis failed: ${e.message}", e)
            outputFile.delete()
            null
        }
    }

    /**
     * Synthesize using Android's built-in TextToSpeech engine.
     */
    private suspend fun synthesizeWithSystemTts(
        text: String,
        outputFile: File,
        locale: Locale,
        speed: Float
    ): Boolean = suspendCancellableCoroutine { cont ->
        var tts: TextToSpeech? = null
        tts = TextToSpeech(context) { status ->
            if (status != TextToSpeech.SUCCESS) {
                Log.e(TAG, "System TTS init failed with status: $status")
                if (cont.isActive) cont.resume(false)
                return@TextToSpeech
            }
            tts?.let { engine ->
                engine.language = locale
                engine.setSpeechRate(speed)
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        engine.shutdown()
                        if (cont.isActive) cont.resume(true)
                    }
                    @Deprecated("Deprecated in API")
                    override fun onError(utteranceId: String?) {
                        engine.shutdown()
                        if (cont.isActive) cont.resume(false)
                    }
                })
                engine.synthesizeToFile(text, null, outputFile, "novacut_tts_${System.currentTimeMillis()}")
            }
        }
        cont.invokeOnCancellation { tts?.shutdown() }
    }

    /**
     * Check if a voice model is downloaded and ready.
     */
    fun isVoiceReady(voiceId: String): Boolean {
        val voiceDir = File(voicesDir, voiceId)
        return voiceDir.exists() && File(voiceDir, "model.onnx").exists()
    }

    /**
     * Get total size of downloaded voice models.
     */
    fun getDownloadedSizeMb(): Int {
        return voicesDir.listFiles()?.sumOf { dir ->
            dir.walkTopDown().sumOf { it.length() }
        }?.let { (it / 1_048_576).toInt() } ?: 0
    }

    /**
     * Check if Sherpa-ONNX TTS runtime is available.
     * Currently always false -- dependency not yet added.
     */
    fun isSherpaAvailable(): Boolean {
        // Sherpa-ONNX (com.k2fsa.sherpa:onnx-android) not yet added to dependencies
        return false
    }

    /**
     * Delete a downloaded voice model to free storage.
     */
    fun deleteVoice(voiceId: String): Boolean {
        val voiceDir = File(voicesDir, voiceId)
        return if (voiceDir.exists()) {
            voiceDir.deleteRecursively()
        } else false
    }

    companion object {
        private const val TAG = "PiperTTS"
    }
}

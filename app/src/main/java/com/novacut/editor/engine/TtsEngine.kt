package com.novacut.editor.engine

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Text-to-Speech engine using Android's built-in TTS.
 * Generates audio files from text for use as voiceover clips.
 */
@Singleton
class TtsEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var tts: TextToSpeech? = null
    @Volatile private var isReady = false
    private val outputDir = File(context.filesDir, "tts").also { it.mkdirs() }
    private val mutex = Mutex()

    // Available voice styles mapped to TTS parameters
    enum class VoiceStyle(val displayName: String, val pitch: Float, val rate: Float) {
        NARRATOR("Narrator", 0.9f, 0.9f),
        CASUAL("Casual", 1.0f, 1.1f),
        ENERGETIC("Energetic", 1.1f, 1.3f),
        DEEP("Deep", 0.7f, 0.85f),
        SOFT("Soft", 1.2f, 0.8f),
        FAST("Fast", 1.0f, 1.5f),
        SLOW("Slow & Clear", 1.0f, 0.7f),
        DRAMATIC("Dramatic", 0.8f, 0.75f)
    }

    fun initialize(onReady: () -> Unit = {}) {
        tts = TextToSpeech(context) { status ->
            isReady = status == TextToSpeech.SUCCESS
            if (isReady) {
                tts?.language = Locale.US
                onReady()
            } else {
                Log.e("TtsEngine", "TTS initialization failed with status $status")
            }
        }
    }

    fun isAvailable(): Boolean = isReady

    /**
     * Get list of available locales on this device.
     */
    fun getAvailableLocales(): List<Locale> {
        val engine = tts ?: return emptyList()
        return engine.availableLanguages?.toList() ?: emptyList()
    }

    /**
     * Synthesize text to an audio file.
     * @return File path of generated audio, or null on failure.
     */
    suspend fun synthesize(
        text: String,
        style: VoiceStyle = VoiceStyle.NARRATOR,
        locale: Locale = Locale.US,
        onProgress: (Float) -> Unit = {}
    ): File? = withContext(Dispatchers.Main) {
        val engine = tts ?: return@withContext null
        if (!isReady) return@withContext null
        if (text.isBlank()) return@withContext null

        engine.language = locale
        engine.setPitch(style.pitch)
        engine.setSpeechRate(style.rate)

        val outputFile = File(outputDir, "tts_${UUID.randomUUID()}.wav")
        val utteranceId = UUID.randomUUID().toString()

        try {
            mutex.withLock {
            suspendCancellableCoroutine { cont ->
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) {
                        onProgress(0.1f)
                    }

                    override fun onDone(id: String?) {
                        if (id == utteranceId) {
                            onProgress(1f)
                            if (cont.isActive) cont.resume(outputFile)
                        }
                    }

                    @Deprecated("Deprecated in API")
                    override fun onError(id: String?) {
                        if (id == utteranceId) {
                            Log.e("TtsEngine", "TTS error for utterance $id")
                            outputFile.delete()
                            if (cont.isActive) cont.resume(null)
                        }
                    }

                    override fun onError(id: String?, errorCode: Int) {
                        if (id == utteranceId) {
                            Log.e("TtsEngine", "TTS error code $errorCode for $id")
                            outputFile.delete()
                            if (cont.isActive) cont.resume(null)
                        }
                    }
                })

                val result = engine.synthesizeToFile(text, null, outputFile, utteranceId)
                if (result != TextToSpeech.SUCCESS && cont.isActive) {
                    outputFile.delete()
                    cont.resume(null)
                }

                cont.invokeOnCancellation {
                    engine.stop()
                    outputFile.delete()
                }
            }
            }
        } catch (e: Exception) {
            Log.e("TtsEngine", "Synthesis failed", e)
            outputFile.delete()
            null
        }
    }

    /**
     * Preview text with TTS (plays through speaker, doesn't save file).
     */
    suspend fun preview(text: String, style: VoiceStyle = VoiceStyle.NARRATOR, locale: Locale = Locale.US) {
        val engine = tts ?: return
        if (!isReady) return
        mutex.withLock {
            engine.language = locale
            engine.setPitch(style.pitch)
            engine.setSpeechRate(style.rate)
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "preview")
        }
    }

    fun stopPreview() {
        tts?.stop()
    }

    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
    }

    /**
     * Clean up old TTS files (older than 24 hours).
     */
    fun cleanupOldFiles() {
        val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000
        outputDir.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
    }
}

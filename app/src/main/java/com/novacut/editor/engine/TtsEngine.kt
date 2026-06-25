package com.novacut.editor.engine

import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
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
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        if (focusChange == AudioManager.AUDIOFOCUS_LOSS ||
            focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
        ) {
            stopPreview()
        }
    }
    private var activePreviewFocusRequest: AudioFocusRequest? = null
    @Volatile private var finishActivePreview: (() -> Unit)? = null
    private var tts: TextToSpeech? = null
    @Volatile private var isReady = false
    private val outputDir = File(context.filesDir, TTS_OUTPUT_DIR_NAME).also { it.mkdirs() }
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

        stopPreview()
        engine.language = locale
        engine.setPitch(style.pitch)
        engine.setSpeechRate(style.rate)

        cleanupOldFiles()
        val fileId = UUID.randomUUID().toString()
        val outputFile = File(outputDir, "${TTS_FILE_PREFIX}${fileId}.wav")
        val partialFile = File(outputDir, "${TTS_FILE_PREFIX}${fileId}.partial.wav")
        val utteranceId = UUID.randomUUID().toString()

        try {
            mutex.withLock {
                suspendCancellableCoroutine { cont ->
                    fun cleanupGeneratedFiles() {
                        partialFile.delete()
                        outputFile.delete()
                    }

                    fun finish(result: File?) {
                        try { engine.setOnUtteranceProgressListener(null) } catch (_: Exception) {}
                        if (cont.isActive) cont.resume(result)
                    }

                    fun reportProgress(value: Float) {
                        runCatching { onProgress(value) }
                            .onFailure { Log.w("TtsEngine", "TTS progress callback failed", it) }
                    }

                    engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(id: String?) {
                            if (id == utteranceId) reportProgress(0.1f)
                        }

                        override fun onDone(id: String?) {
                            if (id == utteranceId) {
                                val finalizedFile = runCatching {
                                    finalizeSynthesizedTtsFile(partialFile, outputFile)
                                }.onFailure {
                                    Log.e("TtsEngine", "TTS output finalization failed for $id", it)
                                    cleanupGeneratedFiles()
                                }.getOrNull()
                                if (finalizedFile != null) {
                                    reportProgress(1f)
                                } else {
                                    Log.e("TtsEngine", "TTS finished without a readable audio file for $id")
                                }
                                finish(finalizedFile)
                            }
                        }

                        @Deprecated("Deprecated in API")
                        override fun onError(id: String?) {
                            if (id == utteranceId) {
                                Log.e("TtsEngine", "TTS error for utterance $id")
                                cleanupGeneratedFiles()
                                finish(null)
                            }
                        }

                        override fun onError(id: String?, errorCode: Int) {
                            if (id == utteranceId) {
                                Log.e("TtsEngine", "TTS error code $errorCode for $id")
                                cleanupGeneratedFiles()
                                finish(null)
                            }
                        }
                    })

                    val result = engine.synthesizeToFile(text, null, partialFile, utteranceId)
                    if (result != TextToSpeech.SUCCESS && cont.isActive) {
                        cleanupGeneratedFiles()
                        finish(null)
                    }

                    cont.invokeOnCancellation {
                        // Clear the progress listener before stop() so a stale
                        // `onDone` / `onError` callback from a cancelled job
                        // can't fire into the next synthesis coroutine's
                        // continuation. Without this, the old listener remains
                        // registered on the shared `engine` (the TextToSpeech
                        // instance is a singleton) and would attempt to resume
                        // a continuation that already threw CancellationException.
                        try { engine.setOnUtteranceProgressListener(null) } catch (_: Exception) {}
                        engine.stop()
                        cleanupGeneratedFiles()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("TtsEngine", "Synthesis failed", e)
            partialFile.delete()
            outputFile.delete()
            null
        }
    }

    /**
     * Preview text with TTS (plays through speaker, doesn't save file).
     */
    suspend fun preview(
        text: String,
        style: VoiceStyle = VoiceStyle.NARRATOR,
        locale: Locale = Locale.US
    ) = withContext(Dispatchers.Main) {
        val engine = tts ?: return@withContext
        if (!isReady) return@withContext
        if (text.isBlank()) return@withContext
        stopPreview()
        mutex.withLock {
            engine.language = locale
            engine.setPitch(style.pitch)
            engine.setSpeechRate(style.rate)
            if (!requestPreviewAudioFocus()) return@withLock
            val utteranceId = "${TTS_PREVIEW_UTTERANCE_ID}_${UUID.randomUUID()}"
            suspendCancellableCoroutine<Unit> { cont ->
                var finished = false

                fun finish() {
                    if (finished) return
                    finished = true
                    finishActivePreview = null
                    try { engine.setOnUtteranceProgressListener(null) } catch (_: Exception) {}
                    abandonPreviewAudioFocus()
                    if (cont.isActive) cont.resume(Unit)
                }

                finishActivePreview = ::finish
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) = Unit

                    override fun onDone(id: String?) {
                        if (id == utteranceId) finish()
                    }

                    @Deprecated("Deprecated in API")
                    override fun onError(id: String?) {
                        if (id == utteranceId) finish()
                    }

                    override fun onError(id: String?, errorCode: Int) {
                        if (id == utteranceId) finish()
                    }

                    override fun onStop(id: String?, interrupted: Boolean) {
                        if (id == utteranceId) finish()
                    }
                })
                val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
                if (result != TextToSpeech.SUCCESS) {
                    finish()
                }

                cont.invokeOnCancellation {
                    try { engine.stop() } catch (_: Exception) {}
                    finish()
                }
            }
        }
    }

    fun stopPreview() {
        val finish = finishActivePreview
        if (finish != null) {
            try { tts?.stop() } catch (_: Exception) {}
            finish()
        } else {
            abandonPreviewAudioFocus()
        }
    }

    fun release() {
        stopPreview()
        tts?.shutdown()
        tts = null
        isReady = false
    }

    /**
     * Clean up abandoned partial TTS files. Finished clips are project assets and
     * can be referenced by saved timelines, so age-based deletion is unsafe.
     */
    fun cleanupOldFiles() {
        val cutoff = System.currentTimeMillis() - ABANDONED_PARTIAL_MAX_AGE_MS
        outputDir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(TTS_PARTIAL_SUFFIX) && it.lastModified() < cutoff }
            ?.forEach { it.delete() }
    }

    private fun requestPreviewAudioFocus(): Boolean {
        val manager = audioManager ?: return true
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = ClearCutAudioFocusPolicy.buildFocusRequest(
                gainType = ClearCutAudioFocusPolicy.TTS_PREVIEW_FOCUS_GAIN,
                attributes = ClearCutAudioFocusPolicy.buildSpeechAttributes(),
                listener = focusChangeListener,
            )
            activePreviewFocusRequest = request
            manager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            manager.requestAudioFocus(
                focusChangeListener,
                AudioManager.STREAM_MUSIC,
                ClearCutAudioFocusPolicy.TTS_PREVIEW_FOCUS_GAIN,
            )
        }
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonPreviewAudioFocus() {
        val manager = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activePreviewFocusRequest?.let { manager.abandonAudioFocusRequest(it) }
            activePreviewFocusRequest = null
        } else {
            @Suppress("DEPRECATION")
            manager.abandonAudioFocus(focusChangeListener)
        }
    }
}

internal const val TTS_OUTPUT_DIR_NAME = "tts_output"
private const val TTS_PREVIEW_UTTERANCE_ID = "preview"
private const val TTS_FILE_PREFIX = "tts_"
private const val TTS_PARTIAL_SUFFIX = ".partial.wav"
private const val ABANDONED_PARTIAL_MAX_AGE_MS = 10 * 60 * 1000L

internal fun finalizeSynthesizedTtsFile(partialFile: File, outputFile: File): File? {
    if (!partialFile.isFile || partialFile.length() <= 0L) {
        partialFile.delete()
        outputFile.delete()
        return null
    }
    moveFileReplacing(partialFile, outputFile)
    return if (outputFile.isFile && outputFile.length() > 0L) {
        outputFile
    } else {
        outputFile.delete()
        null
    }
}

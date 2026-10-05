package com.novacut.editor.engine

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.novacut.editor.engine.whisper.WhisperEngine
import com.novacut.editor.engine.whisper.WhisperModelState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Device acceptance for the pinned Whisper tiny.en model on the bundled ONNX
 * Runtime. speech-quick-brown-fox.m4a is a 3.7 second synthesized sentence,
 * "The quick brown fox jumps over the lazy dog."
 */
@RunWith(AndroidJUnit4::class)
class WhisperDeviceAcceptanceTest {

    @Test
    fun theBundledRuntimeTranscribesASpokenSentence() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val engine = WhisperEngine(context, ModelDownloadManager(context))
        assumeTrue(
            "Seed the pinned Whisper tiny.en files through AI Tools before running this acceptance test.",
            engine.refreshModelState() == WhisperModelState.READY,
        )
        val clip = File(context.cacheDir, "whisper-speech-${System.nanoTime()}.m4a")
        instrumentation.context.assets.open("speech-quick-brown-fox.m4a").use { input ->
            clip.outputStream().use(input::copyTo)
        }
        try {
            val transcript = engine.transcribe(Uri.fromFile(clip)).joinToString(" ") { it.text }.lowercase()

            assertTrue(
                "Whisper missed the spoken words: \"$transcript\"",
                listOf("quick", "brown", "fox", "lazy", "dog").all { it in transcript },
            )
        } finally {
            clip.delete()
        }
    }

    @Test
    fun silenceProducesNoCaptions() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val engine = WhisperEngine(context, ModelDownloadManager(context))
        assumeTrue(
            "Seed the pinned Whisper tiny.en files through AI Tools before running this acceptance test.",
            engine.refreshModelState() == WhisperModelState.READY,
        )
        val clip = File(context.cacheDir, "whisper-silence-${System.nanoTime()}.wav")
        clip.writeBytes(silentWav(seconds = 10))
        try {
            val segments = engine.transcribe(Uri.fromFile(clip))

            assertTrue("Whisper captioned silence: $segments", segments.isEmpty())
        } finally {
            clip.delete()
        }
    }

    /** 16 kHz mono 16-bit PCM of digital silence, with a canonical 44-byte header. */
    private fun silentWav(seconds: Int): ByteArray {
        val sampleRate = 16_000
        val dataBytes = sampleRate * 2 * seconds
        return java.nio.ByteBuffer.allocate(44 + dataBytes).order(java.nio.ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + dataBytes); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1)
            putInt(sampleRate); putInt(sampleRate * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(dataBytes)
        }.array()
    }
}

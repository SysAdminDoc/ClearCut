package com.novacut.editor.engine

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.novacut.editor.model.Keyframe
import com.novacut.editor.model.KeyframeProperty
import java.nio.ByteBuffer

/**
 * Audio processor that applies volume scaling and fade in/out envelope.
 * Operates on 16-bit PCM audio samples.
 */
@UnstableApi
internal class VolumeAudioProcessor(
    private val volume: Float,
    private val fadeInMs: Long,
    private val fadeOutMs: Long,
    private val clipDurationMs: Long,
    private val keyframes: List<Keyframe> = emptyList()
) : BaseAudioProcessor() {

    private var processedFrames: Long = 0L

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.sampleRate == 0 ||
            inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val outputBuffer = replaceOutputBuffer(remaining)
        val sampleRate = inputAudioFormat.sampleRate
        val channelCount = inputAudioFormat.channelCount

        while (inputBuffer.hasRemaining()) {
            val sample = inputBuffer.short
            val frameIndex = processedFrames / channelCount
            val timeMs = frameIndex * 1000L / sampleRate

            var gain = if (keyframes.isNotEmpty()) {
                KeyframeEngine.getValueAt(
                    keyframes, KeyframeProperty.VOLUME, timeMs
                ) ?: volume
            } else {
                volume
            }

            if (fadeInMs > 0 && timeMs < fadeInMs) {
                gain *= timeMs.toFloat() / fadeInMs
            }

            if (fadeOutMs > 0 && timeMs > clipDurationMs - fadeOutMs) {
                val rem = (clipDurationMs - timeMs).coerceAtLeast(0L)
                gain *= rem.toFloat() / fadeOutMs
            }

            val scaled = (sample * gain).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            outputBuffer.putShort(scaled.toShort())
            processedFrames++
        }

        outputBuffer.flip()
    }

    override fun onReset() {
        super.onReset()
        processedFrames = 0L
    }
}

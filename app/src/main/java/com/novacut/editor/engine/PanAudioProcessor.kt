package com.novacut.editor.engine

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt

/**
 * Applies a track pan to interleaved 16-bit PCM.
 *
 * Stereo sources keep their channel layout and use an equal-power balance
 * curve. Mono sources are expanded to stereo so a saved pan value is audible
 * instead of being silently discarded by the renderer.
 */
@UnstableApi
internal class PanAudioProcessor(
    pan: Float,
) : BaseAudioProcessor() {

    private val safePan = if (pan.isFinite()) pan.coerceIn(-1f, 1f) else 0f
    private var inputChannelCount = 0
    private var outputChannelCount = 0

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.sampleRate <= 0 ||
            inputAudioFormat.channelCount <= 0 ||
            inputAudioFormat.encoding != C.ENCODING_PCM_16BIT
        ) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        inputChannelCount = inputAudioFormat.channelCount
        outputChannelCount = if (inputChannelCount == 1) 2 else inputChannelCount
        return if (inputChannelCount == 1) {
            AudioProcessor.AudioFormat(
                inputAudioFormat.sampleRate,
                outputChannelCount,
                inputAudioFormat.encoding,
            )
        } else {
            inputAudioFormat
        }
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val inputSampleCount = inputBuffer.remaining() / Short.SIZE_BYTES
        if (inputSampleCount == 0) return

        val frameCount = inputSampleCount / inputChannelCount
        if (frameCount == 0) return
        val outputBuffer = replaceOutputBuffer(frameCount * outputChannelCount * Short.SIZE_BYTES)
        val leftGain = if (safePan > 0f) cos(safePan * PI.toFloat() / 2f) else 1f
        val rightGain = if (safePan < 0f) cos(-safePan * PI.toFloat() / 2f) else 1f

        repeat(frameCount) {
            if (inputChannelCount == 1) {
                val sample = inputBuffer.short
                outputBuffer.putShort(scale(sample, leftGain))
                outputBuffer.putShort(scale(sample, rightGain))
            } else {
                repeat(inputChannelCount) { channel ->
                    val sample = inputBuffer.short
                    val gain = when (channel) {
                        0 -> leftGain
                        1 -> rightGain
                        else -> 1f
                    }
                    outputBuffer.putShort(scale(sample, gain))
                }
            }
        }

        // PCM input is expected to contain complete samples. Consume any
        // malformed trailing byte rather than letting the next buffer inherit
        // a half sample and fail with BufferUnderflowException.
        if (inputBuffer.hasRemaining()) inputBuffer.position(inputBuffer.limit())
        outputBuffer.flip()
    }

    override fun onReset() {
        super.onReset()
        inputChannelCount = 0
        outputChannelCount = 0
    }

    private fun scale(sample: Short, gain: Float): Short {
        return (sample.toFloat() * gain)
            .roundToInt()
            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            .toShort()
    }
}

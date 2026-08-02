package com.novacut.editor.engine

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.novacut.editor.model.AudioEffect
import java.nio.ByteBuffer

/**
 * Bridges the project DSP chain into Media3's preview and Transformer paths.
 * Both paths feed the same bounded PCM processor, so an enabled effect cannot
 * remain a persistence-only setting.
 */
@UnstableApi
internal class AudioEffectsAudioProcessor(
    private val effects: List<AudioEffect>,
) : BaseAudioProcessor() {

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.sampleRate <= 0 ||
            inputAudioFormat.channelCount <= 0 ||
            inputAudioFormat.encoding != C.ENCODING_PCM_16BIT
        ) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val byteCount = inputBuffer.remaining().and(-Short.SIZE_BYTES)
        if (byteCount == 0) {
            if (inputBuffer.hasRemaining()) inputBuffer.position(inputBuffer.limit())
            return
        }

        val samples = ShortArray(byteCount / Short.SIZE_BYTES)
        for (index in samples.indices) samples[index] = inputBuffer.short
        if (inputBuffer.hasRemaining()) inputBuffer.position(inputBuffer.limit())

        val processed = AudioEffectsEngine.processChain(
            pcm = samples,
            sampleRate = inputAudioFormat.sampleRate,
            channels = inputAudioFormat.channelCount,
            effects = effects,
        )
        val outputBuffer = replaceOutputBuffer(processed.size * Short.SIZE_BYTES)
        processed.forEach(outputBuffer::putShort)
        outputBuffer.flip()
    }
}

package com.novacut.editor.engine

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.novacut.editor.model.AudioEffect
import com.novacut.editor.model.AudioEffectType
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Device-side PCM golden coverage for the processor contract shared by preview and export.
 * This test never launches a UI or injects input; it exercises Media3's real Android audio
 * processor implementation entirely in memory.
 */
@RunWith(AndroidJUnit4::class)
class AudioRenderGoldenInstrumentationTest {

    @Test
    fun panAndDspRemainDeterministicAcrossMedia3Buffers() {
        val stereoFormat = AudioProcessor.AudioFormat(48_000, 2, C.ENCODING_PCM_16BIT)
        assertArrayEquals(
            shortArrayOf(0, -8_000),
            process(PanAudioProcessor(1f), stereoFormat, shortArrayOf(12_000, -8_000)),
        )

        val monoFormat = AudioProcessor.AudioFormat(48_000, 1, C.ENCODING_PCM_16BIT)
        assertArrayEquals(
            shortArrayOf(16_000, 0, -16_000, 0),
            process(PanAudioProcessor(-1f), monoFormat, shortArrayOf(16_000, -16_000)),
        )

        val effect = AudioEffect(
            type = AudioEffectType.LOW_PASS,
            params = mapOf("frequency" to 1_200f, "resonance" to 0.7f),
        )
        val input = ShortArray(97) { index ->
            when {
                index == 0 -> 30_000
                index % 3 == 0 -> 18_000
                index % 3 == 1 -> -9_000
                else -> 3_000
            }
        }
        val whole = process(AudioEffectsAudioProcessor(listOf(effect)), monoFormat, input)
        val chunked = processChunks(
            AudioEffectsAudioProcessor(listOf(effect)),
            monoFormat,
            input,
            intArrayOf(1, 7, 2, 19, 5, 31, 3, 29),
        )

        assertEquals(input.size, whole.size)
        assertArrayEquals(whole, chunked)
    }

    private fun process(
        processor: AudioProcessor,
        format: AudioProcessor.AudioFormat,
        samples: ShortArray,
    ): ShortArray {
        processor.configure(format)
        val input = ByteBuffer
            .allocateDirect(samples.size * Short.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
        samples.forEach(input::putShort)
        input.flip()
        processor.queueInput(input)

        val output = processor.output
        val samplesOut = ShortArray(output.remaining() / Short.SIZE_BYTES)
        for (index in samplesOut.indices) samplesOut[index] = output.short
        processor.reset()
        return samplesOut
    }

    private fun processChunks(
        processor: AudioProcessor,
        format: AudioProcessor.AudioFormat,
        samples: ShortArray,
        chunkSizes: IntArray,
    ): ShortArray {
        processor.configure(format)
        val output = ArrayList<Short>(samples.size)
        var inputOffset = 0
        var chunkIndex = 0
        while (inputOffset < samples.size) {
            val chunkSize = chunkSizes[chunkIndex % chunkSizes.size]
                .coerceAtMost(samples.size - inputOffset)
            val input = ByteBuffer
                .allocateDirect(chunkSize * Short.SIZE_BYTES)
                .order(ByteOrder.nativeOrder())
            for (index in 0 until chunkSize) input.putShort(samples[inputOffset + index])
            input.flip()
            processor.queueInput(input)

            val chunkOutput = processor.output
            while (chunkOutput.hasRemaining()) output += chunkOutput.short
            inputOffset += chunkSize
            chunkIndex++
        }
        processor.reset()
        return output.toShortArray()
    }
}

package com.novacut.editor.engine

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import com.novacut.editor.model.AudioEffect
import com.novacut.editor.model.AudioEffectType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioRenderProcessorTest {

    @Test
    fun stereoPanMovesTheSignalWithoutChangingTheFrameShape() {
        val format = AudioProcessor.AudioFormat(48_000, 2, C.ENCODING_PCM_16BIT)
        val output = process(PanAudioProcessor(1f), format, shortArrayOf(12_000, -8_000))

        assertEquals(2, output.size)
        assertEquals(0, output[0].toInt())
        assertEquals(-8_000, output[1].toInt())
    }

    @Test
    fun monoPanExpandsToStereoSoTheSavedValueIsAudible() {
        val format = AudioProcessor.AudioFormat(44_100, 1, C.ENCODING_PCM_16BIT)
        val processor = PanAudioProcessor(-1f)
        val output = process(processor, format, shortArrayOf(16_000, -16_000))

        assertEquals(4, output.size)
        assertEquals(16_000, output[0].toInt())
        assertEquals(0, output[1].toInt())
        assertEquals(-16_000, output[2].toInt())
        assertEquals(0, output[3].toInt())
    }

    @Test
    fun dspProcessorUsesTheSameEngineContractAsAnalysis() {
        val effect = AudioEffect(
            type = AudioEffectType.LIMITER,
            params = mapOf("ceiling" to -12f),
        )
        val format = AudioProcessor.AudioFormat(48_000, 2, C.ENCODING_PCM_16BIT)
        val input = shortArrayOf(30_000, -30_000, 10_000, -10_000)
        val expected = AudioEffectsEngine.processChain(input, 48_000, 2, listOf(effect))
        val actual = process(AudioEffectsAudioProcessor(listOf(effect)), format, input)

        assertEquals(expected.toList(), actual.toList())
        assertTrue(actual.all { it.toInt() in Short.MIN_VALUE..Short.MAX_VALUE })
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
}

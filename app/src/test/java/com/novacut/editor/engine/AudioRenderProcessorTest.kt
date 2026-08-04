package com.novacut.editor.engine

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import com.novacut.editor.model.AudioEffect
import com.novacut.editor.model.AudioEffectType
import com.novacut.editor.model.KeyframeProperty
import org.junit.Assert.assertArrayEquals
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
    fun centerPanPreservesStereoSamples() {
        val format = AudioProcessor.AudioFormat(48_000, 2, C.ENCODING_PCM_16BIT)
        val input = shortArrayOf(12_000, -8_000, 4_000, 2_000)

        assertArrayEquals(input, process(PanAudioProcessor(0f), format, input))
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

    @Test
    fun statefulDspKeepsFilterStateAcrossInputBufferBoundaries() {
        val format = AudioProcessor.AudioFormat(48_000, 1, C.ENCODING_PCM_16BIT)
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

        val whole = process(AudioEffectsAudioProcessor(listOf(effect)), format, input)
        val chunked = processChunks(
            AudioEffectsAudioProcessor(listOf(effect)),
            format,
            input,
            chunkSizes = intArrayOf(1, 7, 2, 19, 5, 31, 3, 29),
        )

        assertArrayEquals(whole, chunked)
    }

    @Test
    fun volumeProcessorSilencesOnlyTheSelectedKeyframeInterval() {
        val format = AudioProcessor.AudioFormat(1_000, 1, C.ENCODING_PCM_16BIT)
        val keyframes = KeyframeEngine.applyVolumeMuteRange(
            keyframes = emptyList(),
            startOffsetMs = 2L,
            endOffsetMs = 5L,
            fallbackVolume = 1f,
        )
        assertEquals(0f, KeyframeEngine.getValueAt(keyframes, KeyframeProperty.VOLUME, 2L))

        val output = process(
            VolumeAudioProcessor(
                volume = 1f,
                fadeInMs = 0L,
                fadeOutMs = 0L,
                clipDurationMs = 8L,
                keyframes = keyframes,
            ),
            format,
            ShortArray(8) { 1_000 },
        )

        assertArrayEquals(
            shortArrayOf(1_000, 1_000, 0, 0, 0, 1_000, 1_000, 1_000),
            output,
        )
        assertTrue(keyframes.any { it.property == KeyframeProperty.VOLUME && it.value == 0f })
    }

    private fun process(
        processor: AudioProcessor,
        format: AudioProcessor.AudioFormat,
        samples: ShortArray,
    ): ShortArray {
        processor.configure(format)
        processor.flush(AudioProcessor.StreamMetadata.DEFAULT)
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
        processor.flush(AudioProcessor.StreamMetadata.DEFAULT)
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

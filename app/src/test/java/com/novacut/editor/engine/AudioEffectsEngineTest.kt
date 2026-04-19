package com.novacut.editor.engine

import com.novacut.editor.model.AudioEffect
import com.novacut.editor.model.AudioEffectType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioEffectsEngineTest {

    @Test
    fun processChain_toleratesInvalidAudioMetadataAndNonFiniteParams() {
        val pcm = shortArrayOf(
            Short.MIN_VALUE,
            -12_000,
            -1_000,
            0,
            1_000,
            12_000,
            Short.MAX_VALUE
        )
        val invalidParams = mapOf(
            "band1_freq" to Float.POSITIVE_INFINITY,
            "band1_gain" to Float.NaN,
            "band1_q" to Float.NEGATIVE_INFINITY,
            "frequency" to Float.NaN,
            "resonance" to Float.NaN,
            "bandwidth" to Float.NaN,
            "threshold" to Float.NaN,
            "ratio" to Float.NaN,
            "attack" to Float.NEGATIVE_INFINITY,
            "hold" to Float.NaN,
            "release" to Float.NaN,
            "knee" to Float.NaN,
            "makeupGain" to Float.POSITIVE_INFINITY,
            "ceiling" to Float.NaN,
            "roomSize" to Float.POSITIVE_INFINITY,
            "damping" to Float.NaN,
            "wetDry" to Float.NaN,
            "decay" to Float.POSITIVE_INFINITY,
            "delayMs" to Float.POSITIVE_INFINITY,
            "feedback" to Float.NaN,
            "rate" to Float.NaN,
            "depth" to Float.NaN,
            "semitones" to Float.NaN,
            "cents" to Float.POSITIVE_INFINITY,
            "targetPeakDb" to Float.NaN
        )
        val effects = AudioEffectType.entries.map { type ->
            AudioEffect(type = type, params = invalidParams)
        }

        val processed = AudioEffectsEngine.processChain(
            pcm = pcm,
            sampleRate = 0,
            channels = 0,
            effects = effects
        )

        assertEquals(pcm.size, processed.size)
        assertTrue(processed.all { it in Short.MIN_VALUE..Short.MAX_VALUE })
    }

    @Test
    fun analysisHelpers_tolerateInvalidAudioMetadata() {
        val pcm = ShortArray(4_096) { index ->
            if (index % 2 == 0) 1_000 else -1_000
        }

        AudioEffectsEngine.detectBeats(pcm, sampleRate = 0, channels = 0)
        AudioEffectsEngine.detectSpeechRegions(pcm, sampleRate = 0, channels = 0)
        val (left, right) = AudioEffectsEngine.computeVULevels(pcm, channels = 0)

        assertTrue(left in 0f..1f)
        assertTrue(right in 0f..1f)
    }
}

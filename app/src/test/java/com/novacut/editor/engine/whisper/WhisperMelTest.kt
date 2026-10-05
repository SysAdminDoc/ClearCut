package com.novacut.editor.engine.whisper

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

/**
 * Expected values come from Whisper's reference recipe (a centered, reflect-padded
 * 400-point STFT, librosa's Slaney mel filters, then (x + 4) / 4) run in NumPy on
 * the same signal: one second of 440 Hz plus a quieter 2500 Hz, then silence.
 */
class WhisperMelTest {

    private val mel by lazy {
        val tones = FloatArray(WhisperMel.SAMPLE_RATE) { n ->
            val t = n.toDouble() / WhisperMel.SAMPLE_RATE
            (0.5 * sin(2 * PI * 440 * t) + 0.25 * sin(2 * PI * 2500 * t)).toFloat()
        }
        WhisperMel.compute(tones)
    }

    private fun at(melBand: Int, frame: Int) = mel[melBand * WhisperMel.N_FRAMES + frame]

    @Test
    fun eachToneLandsInTheMelBandWhisperExpects() {
        assertEquals(1.4382f, at(11, 50), TOLERANCE)  // 440 Hz, the loudest point
        assertEquals(1.11882f, at(49, 50), TOLERANCE) // 2500 Hz
        assertEquals(1.11881f, at(49, 99), TOLERANCE)
    }

    @Test
    fun framesAreCenteredWithReflectedEdges() {
        assertEquals(1.10957f, at(11, 0), TOLERANCE)
        assertEquals(0.22413f, at(79, 0), TOLERANCE)
        // Frame 100 is centered on the sample where the tones stop.
        assertEquals(1.29283f, at(11, 100), TOLERANCE)
    }

    @Test
    fun quietBandsSitOnTheEightUnitFloorScaledLikeWhisper() {
        assertEquals(-0.5618f, at(0, 50), TOLERANCE)
        assertEquals(-0.5618f, at(30, 2000), TOLERANCE)
    }

    @Test
    fun theWholeSpectrogramMatchesTheReferenceOnAverage() {
        assertEquals(WhisperMel.N_MELS * WhisperMel.N_FRAMES, mel.size)
        assertEquals(-0.55443, mel.average(), 1e-3)
    }

    private companion object {
        const val TOLERANCE = 2e-3f
    }
}

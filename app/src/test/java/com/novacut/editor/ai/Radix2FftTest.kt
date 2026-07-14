package com.novacut.editor.ai

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class Radix2FftTest {

    /** Reference O(n^2) DFT to validate the fast transform against. */
    private fun referenceDft(input: FloatArray): Pair<FloatArray, FloatArray> {
        val n = input.size
        val re = FloatArray(n)
        val im = FloatArray(n)
        for (k in 0 until n) {
            var sr = 0f
            var si = 0f
            for (t in 0 until n) {
                val a = (2.0 * PI * k * t / n).toFloat()
                sr += input[t] * cos(a)
                si -= input[t] * sin(a)
            }
            re[k] = sr
            im[k] = si
        }
        return re to im
    }

    @Test
    fun fftMatchesReferenceDftForAMixedSignal() {
        val n = 64
        val signal = FloatArray(n) { i ->
            (sin(2.0 * PI * 3 * i / n) + 0.5 * cos(2.0 * PI * 7 * i / n)).toFloat()
        }
        val (refRe, refIm) = referenceDft(signal)

        val re = signal.copyOf()
        val im = FloatArray(n)
        radix2Fft(re, im)

        for (k in 0 until n) {
            assertEquals("real bin $k", refRe[k], re[k], 1e-2f)
            assertEquals("imag bin $k", refIm[k], im[k], 1e-2f)
        }
    }

    @Test
    fun impulseHasFlatSpectrum() {
        val n = 32
        val re = FloatArray(n).also { it[0] = 1f }
        val im = FloatArray(n)
        radix2Fft(re, im)
        for (k in 0 until n) {
            assertEquals(1f, re[k], 1e-4f)
            assertEquals(0f, im[k], 1e-4f)
        }
    }

    @Test
    fun nonPowerOfTwoIsLeftUnchanged() {
        val re = floatArrayOf(1f, 2f, 3f)
        val im = FloatArray(3)
        radix2Fft(re, im)
        // Guard clause returns without touching the arrays.
        assertEquals(1f, re[0], 0f)
        assertEquals(2f, re[1], 0f)
        assertEquals(3f, re[2], 0f)
    }
}

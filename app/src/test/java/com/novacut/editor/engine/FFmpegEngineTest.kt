package com.novacut.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FFmpegEngineTest {

    @Suppress("DEPRECATION")
    private val engine = FFmpegEngine::class.java
        .getDeclaredConstructor(android.content.Context::class.java)
        .apply { isAccessible = true }
        .newInstance(null as android.content.Context?)

    @Test
    fun msToSeconds_zero() {
        assertEquals("0.000", engine.msToSeconds(0L))
    }

    @Test
    fun msToSeconds_exactSecond() {
        assertEquals("1.000", engine.msToSeconds(1000L))
    }

    @Test
    fun msToSeconds_fractional() {
        assertEquals("1.500", engine.msToSeconds(1500L))
    }

    @Test
    fun msToSeconds_subMillisecondPrecision() {
        assertEquals("0.001", engine.msToSeconds(1L))
    }

    @Test
    fun msToSeconds_largeValue() {
        assertEquals("3600.000", engine.msToSeconds(3600000L))
    }

    @Test
    fun escapeConcatPath_noSpecialChars() {
        assertEquals("video.mp4", engine.escapeConcatPath("video.mp4"))
    }

    @Test
    fun escapeConcatPath_singleQuote() {
        assertEquals("it'\\''s.mp4", engine.escapeConcatPath("it's.mp4"))
    }

    @Test
    fun escapeConcatPath_multipleSingleQuotes() {
        assertEquals("a'\\''b'\\''c", engine.escapeConcatPath("a'b'c"))
    }

    @Test
    fun escapeFilterPath_backslash() {
        assertEquals("C\\\\Users\\\\video.mp4", engine.escapeFilterPath("C\\Users\\video.mp4"))
    }

    @Test
    fun escapeFilterPath_colon() {
        assertEquals("C\\:video.mp4", engine.escapeFilterPath("C:video.mp4"))
    }

    @Test
    fun escapeFilterPath_singleQuote() {
        assertEquals("it\\'s.mp4", engine.escapeFilterPath("it's.mp4"))
    }

    @Test
    fun escapeFilterPath_allSpecial() {
        assertEquals("C\\\\Users\\:it\\'s", engine.escapeFilterPath("C\\Users:it's"))
    }

    @Test
    fun buildAtempoChain_normalSpeed() {
        val chain = engine.buildAtempoChain(1.5f)
        assertTrue(chain.startsWith("atempo="))
        assertFalse(chain.contains(","))
    }

    @Test
    fun buildAtempoChain_doubleSpeed() {
        val chain = engine.buildAtempoChain(2.0f)
        assertTrue(chain.startsWith("atempo=2.0"))
    }

    @Test
    fun buildAtempoChain_fourXSpeed_chainsMultiple() {
        val chain = engine.buildAtempoChain(4.0f)
        val parts = chain.split(",")
        assertTrue("Should chain multiple atempo filters for 4x", parts.size >= 2)
        parts.forEach { assertTrue(it.startsWith("atempo=")) }
    }

    @Test
    fun buildAtempoChain_halfSpeed() {
        val chain = engine.buildAtempoChain(0.5f)
        assertTrue(chain.startsWith("atempo=0.5"))
    }

    @Test
    fun buildAtempoChain_quarterSpeed_chainsMultiple() {
        val chain = engine.buildAtempoChain(0.25f)
        val parts = chain.split(",")
        assertTrue("Should chain multiple atempo filters for 0.25x", parts.size >= 2)
    }

    @Test
    fun buildAtempoChain_extremeHighSpeed_clamped() {
        val chain = engine.buildAtempoChain(100f)
        val parts = chain.split(",")
        parts.forEach {
            val value = it.removePrefix("atempo=").toDouble()
            assertTrue("Each atempo value should be <= 2.0, got $value", value <= 2.0001)
        }
    }

    @Test
    fun buildAtempoChain_extremeLowSpeed_clamped() {
        val chain = engine.buildAtempoChain(0.01f)
        val parts = chain.split(",")
        parts.forEach {
            val value = it.removePrefix("atempo=").toDouble()
            assertTrue("Each atempo value should be >= 0.5, got $value", value >= 0.4999)
        }
    }

    @Test
    fun buildAtempoChain_normalSpeed_valueInRange() {
        val chain = engine.buildAtempoChain(1.0f)
        val value = chain.removePrefix("atempo=").toDouble()
        assertTrue(value >= 0.5 && value <= 2.0)
    }
}

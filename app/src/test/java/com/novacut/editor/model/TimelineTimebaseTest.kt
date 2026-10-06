package com.novacut.editor.model

import java.math.BigInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineTimebaseTest {
    @Test
    fun `ntsc frame conversion uses deterministic rational boundaries`() {
        val timebase = TimelineTimebase.NTSC_29_97

        assertEquals(0L, timebase.timeMsAt(0L))
        assertEquals(33L, timebase.timeMsAt(1L))
        assertEquals(67L, timebase.timeMsAt(2L))
        assertEquals(1_001L, timebase.timeMsAt(30L))
        assertEquals("29.97 fps", timebase.frameRateLabel)
        assertEquals("00:00:01:00", timebase.formatTimecode(1_001L))
    }

    @Test
    fun `snapping is idempotent without cumulative millisecond drift`() {
        val timebase = TimelineTimebase.NTSC_29_97
        val once = timebase.snapMs(10_017L)
        val repeatedlySnapped = generateSequence(once, timebase::snapMs).take(1_000).last()

        assertEquals(10_010L, once)
        assertEquals(once, repeatedlySnapped)
        assertEquals(timebase.frameIndexAt(10_017L), timebase.frameIndexAt(once))
    }

    @Test
    fun `frame floor ceil and addition stay on ntsc boundaries`() {
        val timebase = TimelineTimebase.NTSC_29_97

        assertEquals(1L, timebase.frameIndexAtOrBefore(66L))
        assertEquals(2L, timebase.frameIndexAtOrAfter(66L))
        assertEquals(67L, timebase.addFrames(33L, 1L))
        assertEquals(133L, timebase.addFrames(100L, 1L))
    }

    @Test
    fun `ntsc timecode tracks wall clock instead of drifting`() {
        val timebase = TimelineTimebase.NTSC_29_97
        // One real hour must read 01:00:00:xx, not the ~00:59:56 that
        // non-drop-frame counting at a rounded 30fps produced.
        assertEquals("01:00:00", timebase.formatTimecode(3_600_000L).substringBeforeLast(':'))
        // Real seconds are exact.
        assertEquals("00:00:30", timebase.formatTimecode(30_000L).substringBeforeLast(':'))
        assertEquals("00:10:00", timebase.formatTimecode(600_000L).substringBeforeLast(':'))
    }

    @Test
    fun `integer rate timecode counts frames exactly`() {
        val timebase = TimelineTimebase(30, 1)
        assertEquals("00:00:01:00", timebase.formatTimecode(1_000L))
        assertEquals("00:00:00:15", timebase.formatTimecode(500L))
    }

    @Test
    fun `integer project rate keeps exact nominal frame labels`() {
        val project = Project(frameRate = 24)

        assertEquals(TimelineTimebase(24, 1), project.timelineTimebase)
        assertEquals("24 fps", project.timelineTimebase.frameRateLabel)
        assertEquals(42L, project.timelineTimebase.snapMs(41L))
    }

    @Test
    fun `frame indexes match exact rational math at every rounding`() {
        val timebases = listOf(
            TimelineTimebase.NTSC_23_976,
            TimelineTimebase.NTSC_29_97,
            TimelineTimebase.NTSC_59_94,
            TimelineTimebase(24, 1),
            TimelineTimebase(240_000, 1),
            TimelineTimebase(1, 10_000),
            TimelineTimebase(240_000, 10_000),
        )
        val times = (0L..3_000L) + listOf(
            9_999_999L, 10_000_000L, 10_010_009L, 3_600_000_000L, 86_400_000_000L, 1L shl 40,
        )
        for (timebase in timebases) {
            for (timeMs in times) {
                val label = "${timebase.numerator}/${timebase.denominator} at $timeMs ms"
                assertEquals(label, exactFrames(timebase, timeMs, Rounding.FLOOR), timebase.frameIndexAtOrBefore(timeMs))
                assertEquals(label, exactFrames(timebase, timeMs, Rounding.CEIL), timebase.frameIndexAtOrAfter(timeMs))
                assertEquals(label, exactFrames(timebase, timeMs, Rounding.HALF_UP), timebase.frameIndexAt(timeMs))
            }
        }
    }

    @Test
    fun `an unbounded time stays the latest frame instead of wrapping negative`() {
        // A lone clip's slide range has no right edge, so its bound arrives as Long.MAX_VALUE.
        // The old ms × numerator product wrapped negative there and the slide was dropped.
        for (timebase in listOf(TimelineTimebase.NTSC_29_97, TimelineTimebase(30, 1), TimelineTimebase(240_000, 1))) {
            val label = "${timebase.numerator}/${timebase.denominator}"
            val latest = timebase.frameIndexAtOrBefore(Long.MAX_VALUE)
            assertEquals(label, exactFrames(timebase, Long.MAX_VALUE, Rounding.FLOOR).coerceAtMost(Long.MAX_VALUE.toBigInteger()).toLong(), latest)
            assertTrue(label, latest >= timebase.frameIndexAtOrBefore(Long.MAX_VALUE / 2))
            assertTrue(label, timebase.frameIndexAtOrAfter(Long.MAX_VALUE) >= latest)
            assertTrue(label, timebase.frameIndexAt(Long.MAX_VALUE) >= latest)
        }
        // 29.97 fits Long exactly; 240,000/1 runs past it and saturates.
        assertEquals(Long.MAX_VALUE, TimelineTimebase(240_000, 1).frameIndexAtOrBefore(Long.MAX_VALUE))
        assertTrue(TimelineTimebase.NTSC_29_97.frameIndexAtOrBefore(Long.MAX_VALUE) < Long.MAX_VALUE)
    }

    private enum class Rounding { FLOOR, CEIL, HALF_UP }

    private fun exactFrames(timebase: TimelineTimebase, timeMs: Long, rounding: Rounding): BigInteger {
        val divisor = BigInteger.valueOf(1_000L * timebase.denominator)
        val product = BigInteger.valueOf(timeMs).multiply(BigInteger.valueOf(timebase.numerator.toLong()))
        val offset = when (rounding) {
            Rounding.FLOOR -> BigInteger.ZERO
            Rounding.CEIL -> divisor - BigInteger.ONE
            Rounding.HALF_UP -> BigInteger.valueOf(500L * timebase.denominator)
        }
        return (product + offset).divide(divisor)
    }

    private fun assertEquals(message: String, expected: BigInteger, actual: Long) =
        assertEquals(message, expected.toLong(), actual)
}

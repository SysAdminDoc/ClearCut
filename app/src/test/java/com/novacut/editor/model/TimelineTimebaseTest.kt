package com.novacut.editor.model

import org.junit.Assert.assertEquals
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
    fun `integer project rate keeps exact nominal frame labels`() {
        val project = Project(frameRate = 24)

        assertEquals(TimelineTimebase(24, 1), project.timelineTimebase)
        assertEquals("24 fps", project.timelineTimebase.frameRateLabel)
        assertEquals(42L, project.timelineTimebase.snapMs(41L))
    }
}

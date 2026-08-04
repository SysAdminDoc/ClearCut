package com.novacut.editor.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class TimelineExportRangeTest {
    private val timebase = TimelineTimebase(30)

    @Test
    fun resolvesFrameBoundsToMillisecondBoundaries() {
        val resolved = TimelineExportRange(
            startFrame = 30L,
            endFrameExclusive = 120L,
        ).resolve(timebase, totalDurationMs = 10_000L)

        assertNotNull(resolved)
        assertEquals(1_000L, resolved?.startMs)
        assertEquals(4_000L, resolved?.endMs)
        assertEquals(3_000L, resolved?.durationMs)
    }

    @Test
    fun permitsThePartialTailAtTheProjectDuration() {
        val resolved = TimelineExportRange(
            startFrame = 270L,
            endFrameExclusive = 299L,
        ).resolve(timebase, totalDurationMs = 9_950L)

        assertNotNull(resolved)
        assertEquals(9_000L, resolved?.startMs)
        assertEquals(9_950L, resolved?.endMs)
    }

    @Test
    fun rejectsIncompleteEmptyAndOutOfBoundsRanges() {
        assertNull(TimelineExportRange(startFrame = 30L).resolve(timebase, 10_000L))
        assertNull(TimelineExportRange(endFrameExclusive = 120L).resolve(timebase, 10_000L))
        assertNull(TimelineExportRange(startFrame = 120L, endFrameExclusive = 120L).resolve(timebase, 10_000L))
        assertNull(TimelineExportRange(startFrame = 0L, endFrameExclusive = 301L).resolve(timebase, 10_000L))
        assertNull(TimelineExportRange(startFrame = 0L, endFrameExclusive = 1L).resolve(timebase, 0L))
    }
}

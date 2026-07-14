package com.novacut.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaDurationPolicyTest {

    @Test
    fun implausibleDurationsAreNotPlausible() {
        assertFalse(MediaDurationPolicy.isPlausible(0L))
        assertFalse(MediaDurationPolicy.isPlausible(-1L))
        assertFalse(MediaDurationPolicy.isPlausible(Long.MIN_VALUE))
        assertFalse(MediaDurationPolicy.isPlausible(Long.MAX_VALUE))
        assertFalse(
            "> 24h is implausible",
            MediaDurationPolicy.isPlausible(MediaDurationPolicy.MAX_PLAUSIBLE_DURATION_MS + 1)
        )
    }

    @Test
    fun normalAndBoundaryDurationsArePlausible() {
        assertTrue(MediaDurationPolicy.isPlausible(1L))
        assertTrue(MediaDurationPolicy.isPlausible(90L * 60L * 1000L)) // 90 min film
        assertTrue(MediaDurationPolicy.isPlausible(MediaDurationPolicy.MAX_PLAUSIBLE_DURATION_MS))
    }

    @Test
    fun analyzableDurationZeroesOutHostileMetadata() {
        assertEquals(0L, MediaDurationPolicy.analyzableDurationMs(0L))
        assertEquals(0L, MediaDurationPolicy.analyzableDurationMs(-5L))
        assertEquals(0L, MediaDurationPolicy.analyzableDurationMs(Long.MAX_VALUE))
        assertEquals(0L, MediaDurationPolicy.analyzableDurationMs(MediaDurationPolicy.MAX_PLAUSIBLE_DURATION_MS + 1))
        val normal = 3_600_000L
        assertEquals(normal, MediaDurationPolicy.analyzableDurationMs(normal))
    }

    @Test
    fun boundedSampleCountNeverOverflowsOrGoesNegative() {
        // Long.MAX_VALUE would overflow a naive (durationMs/step).toInt().
        assertEquals(0, MediaDurationPolicy.boundedSampleCount(Long.MAX_VALUE, 100L, 1_000_000))
        assertEquals(0, MediaDurationPolicy.boundedSampleCount(-1L, 100L, 1_000_000))
        assertEquals(0, MediaDurationPolicy.boundedSampleCount(1000L, 0L, 10))
        assertEquals(0, MediaDurationPolicy.boundedSampleCount(1000L, 100L, 0))
    }

    @Test
    fun boundedSampleCountClampsToMaxAndComputesNormalCase() {
        // 10s at 100ms = 100 samples, under the cap.
        assertEquals(100, MediaDurationPolicy.boundedSampleCount(10_000L, 100L, 1_000_000))
        // Cap enforced when the natural count exceeds it.
        assertEquals(50, MediaDurationPolicy.boundedSampleCount(60_000L, 100L, 50))
        // Exactly 24h at 100ms is the plausible boundary and is allowed.
        val dayCount = MediaDurationPolicy.MAX_PLAUSIBLE_DURATION_MS / 100L
        assertEquals(dayCount.toInt(), MediaDurationPolicy.boundedSampleCount(MediaDurationPolicy.MAX_PLAUSIBLE_DURATION_MS, 100L, Int.MAX_VALUE))
    }
}

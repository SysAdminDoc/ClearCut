package com.novacut.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThumbnailCachePolicyTest {
    private val mb = 1024L * 1024L

    @Test
    fun automaticKeepsTheHistoricalHeapEighthBound() {
        assertEquals(64 * mb, ThumbnailCachePolicy.automaticBytes(512 * mb).toLong())
    }

    @Test
    fun explicitBudgetIsCappedAtHeapQuarterOnNormalDevices() {
        assertEquals(
            128 * mb,
            ThumbnailCachePolicy.resolveBytes(256, 512 * mb, isLowRamDevice = false).toLong()
        )
    }

    @Test
    fun lowRamExplicitBudgetCannotExceedTheAutomaticCeiling() {
        assertEquals(
            64 * mb,
            ThumbnailCachePolicy.resolveBytes(256, 512 * mb, isLowRamDevice = true).toLong()
        )
        assertEquals(listOf(32, 64), ThumbnailCachePolicy.availableSettingsSizes(512 * mb, isLowRamDevice = true))
    }

    @Test
    fun lowRamOptionsDoNotOfferASettingAboveTheHeapEighth() {
        val options = ThumbnailCachePolicy.availableSettingsSizes(256 * mb, isLowRamDevice = true)

        assertTrue(options.contains(32))
        assertFalse(options.any { it > 32 })
    }

    @Test
    fun denseCutTimelineStripsStayBelowTheLowRamStrongReferenceBudget() {
        val maxMemoryBytes = 128 * mb
        val stripBytes = 20L * 80L * 45L * 4L
        val entries = (0..50).map { index ->
            ThumbnailStripPolicy.StripEntry("clip-$index", stripBytes)
        }

        val retained = ThumbnailStripPolicy.retainedKeys(
            entriesInInsertionOrder = entries,
            budgetBytes = ThumbnailStripPolicy.budgetBytes(maxMemoryBytes),
        )

        assertTrue(retained.isNotEmpty())
        assertTrue(retained.size < entries.size)
        assertTrue(retained.size * stripBytes <= ThumbnailStripPolicy.budgetBytes(maxMemoryBytes))
    }
}

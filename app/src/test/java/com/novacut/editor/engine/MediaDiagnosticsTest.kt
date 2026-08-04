package com.novacut.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaDiagnosticsTest {

    @Test
    fun timestampRiskFlagsUnreadableOrUnorderedVideoSamples() {
        assertEquals(
            "No readable samples were found.",
            timestampRiskFor(
                MediaTimestampStats(
                    sampleCount = 0,
                    hasNonMonotonicTimestamps = false,
                    hasSyncFrames = false,
                ),
                isVideo = true,
            )
        )
        assertEquals(
            "Sample timestamps are not monotonic.",
            timestampRiskFor(
                MediaTimestampStats(
                    sampleCount = 12,
                    hasNonMonotonicTimestamps = true,
                    hasSyncFrames = true,
                ),
                isVideo = true,
            )
        )
        assertEquals(
            "No video sync frames were reported.",
            timestampRiskFor(
                MediaTimestampStats(
                    sampleCount = 12,
                    hasNonMonotonicTimestamps = false,
                    hasSyncFrames = false,
                ),
                isVideo = true,
            )
        )
        assertNull(
            timestampRiskFor(
                MediaTimestampStats(
                    sampleCount = 12,
                    hasNonMonotonicTimestamps = false,
                    hasSyncFrames = false,
                ),
                isVideo = false,
            )
        )
    }

    @Test
    fun colorRiskOnlyFlagsIncompleteHdrMetadata() {
        assertEquals(
            "HDR was detected but the source does not report complete color metadata.",
            colorRiskFor(hasHdr = true, colorStandard = "BT.2020", colorTransfer = null)
        )
        assertNull(colorRiskFor(hasHdr = true, colorStandard = "BT.2020", colorTransfer = "ST 2084"))
        assertNull(colorRiskFor(hasHdr = false, colorStandard = null, colorTransfer = null))
    }

    @Test
    fun nearestSyncFrameHonorsDirectionAndDropsInvalidTimes() {
        val syncFrames = listOf(1_000L, -5L, 0L, 2_000L, 1_000L)

        assertEquals(
            1_000L,
            nearestSyncFrameMs(syncFrames, 1_500L, SyncFrameDirection.PREVIOUS)
        )
        assertEquals(
            2_000L,
            nearestSyncFrameMs(syncFrames, 1_500L, SyncFrameDirection.NEXT)
        )
        assertEquals(
            0L,
            nearestSyncFrameMs(syncFrames, 0L, SyncFrameDirection.PREVIOUS)
        )
        assertNull(nearestSyncFrameMs(syncFrames, 2_001L, SyncFrameDirection.NEXT))
        assertTrue(nearestSyncFrameMs(emptyList(), 0L, SyncFrameDirection.NEXT) == null)
    }
}

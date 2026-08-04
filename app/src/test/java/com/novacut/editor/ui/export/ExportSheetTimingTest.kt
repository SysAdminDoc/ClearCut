package com.novacut.editor.ui.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExportSheetTimingTest {

    @Test
    fun elapsedTimeAdvancesFromTheExportStartWithoutProgressChanges() {
        assertEquals(7_500L, exportElapsedMs(nowMs = 17_500L, exportStartTimeMs = 10_000L))
        assertEquals(0L, exportElapsedMs(nowMs = 9_000L, exportStartTimeMs = 10_000L))
        assertEquals(0L, exportElapsedMs(nowMs = 17_500L, exportStartTimeMs = 0L))
    }

    @Test
    fun etaRequiresEnoughElapsedTimeAndProgress() {
        assertNull(exportEtaRemainingMs(elapsedMs = 2_000L, exportProgress = 0.5f))
        assertNull(exportEtaRemainingMs(elapsedMs = 5_000L, exportProgress = 0.05f))
        assertEquals(5_000L, exportEtaRemainingMs(elapsedMs = 5_000L, exportProgress = 0.5f))
    }
}

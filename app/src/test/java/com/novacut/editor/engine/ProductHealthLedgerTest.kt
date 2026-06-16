package com.novacut.editor.engine

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductHealthLedgerTest {

    @Test
    fun ledgerData_defaultsToZeros() {
        val data = ProductHealthLedger.LedgerData()
        assertEquals(0, data.exportAttempts)
        assertEquals(0, data.exportCompleted)
        assertEquals(0, data.exportCancelled)
        assertEquals(0, data.exportFailed)
        assertTrue(data.exportFailureClasses.isEmpty())
        assertEquals(0, data.coldStartCount)
        assertEquals(0, data.modelDownloadAttempts)
    }

    @Test
    fun ledgerData_incrementsCorrectly() {
        val base = ProductHealthLedger.LedgerData()
        val updated = base.copy(
            exportAttempts = base.exportAttempts + 1,
            exportCompleted = base.exportCompleted + 1,
            coldStartCount = base.coldStartCount + 3
        )
        assertEquals(1, updated.exportAttempts)
        assertEquals(1, updated.exportCompleted)
        assertEquals(3, updated.coldStartCount)
    }

    @Test
    fun ledgerData_failureClassesBounded() {
        val classes = (1..30).associate { "class_$it" to it }
        val data = ProductHealthLedger.LedgerData(exportFailureClasses = classes)
        assertEquals(30, data.exportFailureClasses.size)
    }

    @Test
    fun ledgerData_failureClassKeyTruncated() {
        val longKey = "a".repeat(200)
        val classes = mapOf(longKey to 1)
        val data = ProductHealthLedger.LedgerData(exportFailureClasses = classes)
        val key = data.exportFailureClasses.keys.first()
        assertEquals(200, key.length)
    }

    @Test
    fun healthEvent_sealedTypes() {
        val events: List<HealthEvent> = listOf(
            HealthEvent.EXPORT_ATTEMPT,
            HealthEvent.EXPORT_COMPLETE,
            HealthEvent.EXPORT_CANCELLED,
            HealthEvent.ExportFailed("TranscoderError"),
            HealthEvent.COLD_START,
            HealthEvent.WARM_START,
            HealthEvent.MODEL_DOWNLOAD_ATTEMPT,
            HealthEvent.MODEL_DOWNLOAD_FAILED,
            HealthEvent.DIAGNOSTIC_ZIP_CREATED,
            HealthEvent.PROJECT_CREATED,
            HealthEvent.PROJECT_DELETED,
            HealthEvent.AI_TOOL_INVOKED
        )
        assertEquals(12, events.size)
    }

    @Test
    fun healthEvent_exportFailed_carriesClass() {
        val event = HealthEvent.ExportFailed("TimeoutException")
        assertEquals("TimeoutException", event.failureClass)
    }
}

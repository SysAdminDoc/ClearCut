package com.novacut.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsResetReportStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun recordCorruptionReset_writesReasonTimestampAndRedactsSecrets() {
        val store = SettingsResetReportStore.forFile(temp.newFile("settings-reset-report.jsonl"))
        val error = IllegalStateException(
            "bad preferences acoustid_api_key=SECRET api_key=SECRET token=SECRET " +
                "proxyPassword=hunter2 content://media/video/123 " +
                "https://api.example.com/reset?key=SECRET C:\\Users\\--\\secret.txt"
        )

        store.recordCorruptionReset(error, nowEpochMs = 1234L)

        val latest = store.latestReport()
        val diagnostic = store.buildDiagnosticText()
        assertNotNull(latest)
        assertNotNull(diagnostic)
        assertEquals(1234L, latest!!.recordedAtEpochMs)
        assertEquals(SettingsResetReportStore.DEFAULT_REASON, latest.reason)
        assertEquals("IllegalStateException", latest.errorType)
        assertTrue(diagnostic!!.contains("\"recordedAtEpochMs\":1234"))
        assertTrue(diagnostic.contains(SettingsResetReportStore.DEFAULT_REASON))
        assertFalse(diagnostic.contains("SECRET"))
        assertFalse(diagnostic.contains("hunter2"))
        assertFalse(diagnostic.contains("content://"))
        assertFalse(diagnostic.contains("api.example.com/reset"))
        assertFalse(diagnostic.contains("C:\\Users"))
    }
}

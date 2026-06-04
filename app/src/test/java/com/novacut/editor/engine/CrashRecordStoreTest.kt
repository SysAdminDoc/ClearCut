package com.novacut.editor.engine

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CrashRecordStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun recordUncaughtException_writesRedactedCrashRecord() {
        val store = CrashRecordStore.forDirectory(temp.newFolder("crashes"))
        val thread = Thread("render-worker content://media/external/video/42")
        val error = IllegalStateException(
            "failed while reading /storage/emulated/0/DCIM/private-project.mp4"
        ).apply {
            stackTrace = arrayOf(
                StackTraceElement(
                    "com.novacut.editor.engine.VideoEngine",
                    "export",
                    "VideoEngine.kt",
                    123,
                )
            )
        }

        val file = store.recordUncaughtException(
            thread = thread,
            throwable = error,
            appVersion = "v-test",
            nowEpochMs = 1234L,
        )

        val raw = file.readText()
        val json = JSONObject(raw)
        assertEquals(CrashRecordStore.RECORD_SCHEMA, json.getString("schema"))
        assertEquals(1234L, json.getLong("recordedAtEpochMs"))
        assertEquals("v-test", json.getString("appVersion"))
        assertTrue(json.getJSONObject("throwable").getJSONArray("causes").length() >= 1)
        assertFalse(raw.contains("content://"))
        assertFalse(raw.contains("/storage/"))
        assertFalse(raw.contains("private-project"))
        assertFalse(raw.contains("failed while reading"))
        assertTrue(raw.contains("messageSha256"))
    }

    @Test
    fun buildDiagnosticJson_wrapsExistingRecords() {
        val store = CrashRecordStore.forDirectory(temp.newFolder("crashes"))
        repeat(2) { index ->
            store.recordUncaughtException(
                thread = Thread("thread-$index"),
                throwable = RuntimeException("boom-$index"),
                appVersion = "v-test",
                nowEpochMs = 2_000L + index,
                retainCount = 8,
            )
        }

        val payload = JSONObject(store.buildDiagnosticJson()!!)

        assertEquals(CrashRecordStore.BUNDLE_SCHEMA, payload.getString("schema"))
        assertEquals(2, payload.getInt("recordCount"))
        assertEquals(2, payload.getJSONArray("records").length())
    }

    @Test
    fun pruneOldRecords_keepsNewestRecordsOnly() {
        val store = CrashRecordStore.forDirectory(temp.newFolder("crashes"))
        repeat(10) { index ->
            val file = store.recordUncaughtException(
                thread = Thread("thread-$index"),
                throwable = RuntimeException("boom-$index"),
                appVersion = "v-test",
                nowEpochMs = 10_000L + index,
                retainCount = 20,
            )
            file.setLastModified(10_000L + index)
        }

        store.pruneOldRecords(retainCount = 3)

        val payload = JSONObject(store.buildDiagnosticJson(maxRecords = 8)!!)
        val records = payload.getJSONArray("records")
        assertEquals(3, payload.getInt("recordCount"))
        assertEquals(3, records.length())
        assertTrue(records.toString().contains("10009"))
        assertFalse(records.toString().contains("10000"))
    }
}

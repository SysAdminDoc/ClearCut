package com.novacut.editor.engine

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProcessExitRecorderTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun unsupportedDevicesWriteExplicitUnsupportedDiagnosticPayload() {
        val recorder = ProcessExitRecorder.forFile(
            historyFile = temp.newFile("process-exit-history.json"),
            source = FakeProcessExitSource(supported = false)
        )

        recorder.recordStartupExitReasons(nowEpochMs = 1_000L)
        val json = JSONObject(recorder.buildDiagnosticJson())

        assertEquals(ProcessExitRecorder.SCHEMA, json.getString("schema"))
        assertEquals(false, json.getBoolean("supported"))
        assertEquals(0, json.getInt("recordCount"))
        assertTrue(json.getString("unsupportedReason").contains("API 30"))
    }

    @Test
    fun recordsAndDeduplicatesLatestExitReasons() {
        val duplicate = snapshot(timestamp = 3_000L, reason = 4, pid = 42, processName = "com.novacut.editor")
        val recorder = ProcessExitRecorder.forFile(
            historyFile = temp.newFile("process-exit-history.json"),
            source = FakeProcessExitSource(
                records = listOf(
                    snapshot(timestamp = 1_000L, reason = 3, pid = 7, processName = "com.novacut.editor:export"),
                    duplicate,
                    duplicate.copy(pssKb = 999L),
                    snapshot(timestamp = 2_000L, reason = 5, pid = 8, processName = "com.novacut.editor")
                )
            )
        )

        recorder.recordStartupExitReasons(nowEpochMs = 4_000L, retainCount = 3)
        val json = JSONObject(recorder.buildDiagnosticJson())
        val records = json.getJSONArray("records")

        assertEquals(true, json.getBoolean("supported"))
        assertEquals(true, json.getBoolean("lowMemoryKillReportSupported"))
        assertEquals(3, json.getInt("recordCount"))
        assertEquals(3_000L, records.getJSONObject(0).getLong("timestampEpochMs"))
        assertEquals("CRASH", records.getJSONObject(0).getString("reason"))
        assertEquals("CRASH_NATIVE", records.getJSONObject(1).getString("reason"))
        assertEquals("LOW_MEMORY", records.getJSONObject(2).getString("reason"))
    }

    @Test
    fun redactsDescriptionsAndTraceExcerpts() {
        val recorder = ProcessExitRecorder.forFile(
            historyFile = temp.newFile("process-exit-history.json"),
            source = FakeProcessExitSource(
                records = listOf(
                    snapshot(
                        timestamp = 5_000L,
                        reason = 6,
                        pid = 99,
                        processName = "com.novacut.editor",
                        description = "ANR while opening /storage/emulated/0/DCIM/private.mov",
                        traceExcerpt = """
                            main waiting on content://media/external/video/12345
                            caption=Secret spoken words
                            projectName=Client Launch Cut
                            sourceUri=/data/data/com.novacut.editor/files/projects/p123/source.mp4
                        """.trimIndent()
                    )
                )
            )
        )

        recorder.recordStartupExitReasons(nowEpochMs = 6_000L)
        val raw = recorder.buildDiagnosticJson()

        assertFalse(raw.contains("content://"))
        assertFalse(raw.contains("/storage/"))
        assertFalse(raw.contains("/data/data/"))
        assertFalse(raw.contains("Secret spoken words"))
        assertFalse(raw.contains("Client Launch Cut"))
        assertTrue(raw.contains("<redacted>"))
    }

    @Test
    fun reasonAndImportanceNamesCoverExpectedAndroidValues() {
        assertEquals("ANR", ProcessExitRecorder.reasonName(6))
        assertEquals("CRASH_NATIVE", ProcessExitRecorder.reasonName(5))
        assertEquals("SIGNALED", ProcessExitRecorder.reasonName(2))
        assertEquals("UNKNOWN", ProcessExitRecorder.reasonName(999))
        assertEquals("FOREGROUND", ProcessExitRecorder.importanceName(100))
        assertEquals("CACHED", ProcessExitRecorder.importanceName(400))
        assertEquals("UNKNOWN", ProcessExitRecorder.importanceName(-1))
    }

    @Test
    fun traceExcerptIsBounded() {
        val longTrace = (0 until 100).joinToString("\n") { index ->
            "line-$index ${"x".repeat(400)}"
        }

        val excerpt = ProcessExitRecorder.sanitizeTraceExcerpt(longTrace)

        val lines = excerpt.lines()
        assertEquals(40, lines.size)
        assertTrue(lines.all { it.length <= 180 })
        assertTrue(lines.first().startsWith("line-0"))
        assertFalse(excerpt.contains("line-41"))
    }

    private fun snapshot(
        timestamp: Long,
        reason: Int,
        pid: Int,
        processName: String,
        description: String? = null,
        traceExcerpt: String? = null,
    ): ProcessExitSnapshot {
        return ProcessExitSnapshot(
            timestampEpochMs = timestamp,
            reasonCode = reason,
            status = if (reason == 2) 9 else 0,
            pid = pid,
            processName = processName,
            importance = 100,
            pssKb = 12_345L,
            rssKb = 23_456L,
            description = description,
            traceExcerpt = traceExcerpt,
        )
    }

    private class FakeProcessExitSource(
        override val supported: Boolean = true,
        override val lowMemoryKillReportSupported: Boolean? = if (supported) true else null,
        private val records: List<ProcessExitSnapshot> = emptyList(),
    ) : ProcessExitSource {
        override fun recentExitRecords(maxRecords: Int): List<ProcessExitSnapshot> {
            return records.take(maxRecords)
        }
    }
}

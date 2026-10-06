package com.novacut.editor.engine

import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ProcessExitRecorderTest {

    @get:Rule
    val temp = TemporaryFolder()

    @After
    fun stopLedger() {
        MemoryBudgetLedger.uninstall()
    }

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
    fun memoryLimiterAnonSwapDetectedAsDistinctReason() {
        val recorder = ProcessExitRecorder.forFile(
            historyFile = temp.newFile("process-exit-history.json"),
            source = FakeProcessExitSource(
                records = listOf(
                    snapshot(
                        timestamp = 7_000L,
                        reason = 13, // REASON_OTHER
                        pid = 50,
                        processName = "com.novacut.editor",
                        description = "MemoryLimiter:AnonSwap limit=2048000 used=2100000"
                    ),
                    snapshot(
                        timestamp = 8_000L,
                        reason = 13, // REASON_OTHER — not MemoryLimiter
                        pid = 51,
                        processName = "com.novacut.editor",
                        description = "Unknown other reason"
                    )
                )
            )
        )

        recorder.recordStartupExitReasons(nowEpochMs = 9_000L)
        val json = JSONObject(recorder.buildDiagnosticJson())
        val records = json.getJSONArray("records")
        val reasonsByTimestamp = (0 until records.length()).associate { index ->
            val record = records.getJSONObject(index)
            record.getLong("timestampEpochMs") to record.getString("reason")
        }

        assertEquals("MEMORY_LIMITER", reasonsByTimestamp[7_000L])
        assertEquals("OTHER", reasonsByTimestamp[8_000L])
    }

    @Test
    fun isMemoryLimiterKillDetectsPattern() {
        assertTrue(ProcessExitRecorder.isMemoryLimiterKill("MemoryLimiter:AnonSwap limit=2048000 used=2100000"))
        assertTrue(ProcessExitRecorder.isMemoryLimiterKill("some prefix MemoryLimiter:AnonSwap suffix"))
        assertFalse(ProcessExitRecorder.isMemoryLimiterKill("Some other reason"))
        assertFalse(ProcessExitRecorder.isMemoryLimiterKill(null))
        assertFalse(ProcessExitRecorder.isMemoryLimiterKill(""))
    }

    @Test
    fun memoryKillsCarryTheDeviceTierAndWhatTheKilledRunHeld() {
        val history = historyFileInAppData()
        // The run that gets killed: half the decode cap buffered, Whisper open twice,
        // and the segmenter opened and closed again.
        ProcessExitRecorder.forFile(history, FakeProcessExitSource()).apply {
            recordStartupExitReasons(nowEpochMs = 1_000L)
            startMemoryCapture()
        }
        assertFalse(AudioDecodeBudget.exceedsBudget(current = 0, incoming = 48_000_000))
        MemoryBudgetLedger.modelLoaded("onnx:whisper-base.onnx", 145_000_000L)
        MemoryBudgetLedger.modelLoaded("onnx:whisper-base.onnx", 145_000_000L)
        MemoryBudgetLedger.modelLoaded("mediapipe:selfie_segmenter.tflite", 250_000L)
        MemoryBudgetLedger.modelReleased("mediapipe:selfie_segmenter.tflite")
        MemoryBudgetLedger.uninstall()

        // The next launch.
        val recorder = ProcessExitRecorder.forFile(
            history,
            FakeProcessExitSource(
                records = listOf(
                    snapshot(timestamp = 7_000L, reason = 13, pid = 50, processName = "com.novacut.editor",
                        description = "MemoryLimiter:AnonSwap limit=2048000 used=2100000"),
                    snapshot(timestamp = 6_000L, reason = 3, pid = 49, processName = "com.novacut.editor"),
                    snapshot(timestamp = 5_000L, reason = 4, pid = 48, processName = "com.novacut.editor"),
                ),
                memory = DeviceMemory(
                    totalBytes = 7_600_000_000L,
                    memoryClassMb = 256,
                    largeMemoryClassMb = 512,
                    lowRamDevice = false,
                ),
            )
        )
        recorder.recordStartupExitReasons(nowEpochMs = 8_000L)
        val records = recordsByTimestamp(recorder)

        val limiter = records.getValue(7_000L)
        assertEquals("MEMORY_LIMITER", limiter.getString("reason"))
        val context = limiter.getJSONObject("memoryContext")
        assertEquals(8, context.getInt("ramTierGb"))
        assertEquals(7_247L, context.getLong("totalRamMb"))
        assertEquals(256, context.getInt("memoryClassMb"))
        assertEquals(512, context.getInt("largeMemoryClassMb"))
        assertEquals(false, context.getBoolean("lowRamDevice"))
        val budget = context.getJSONObject("lastBudget")
        assertEquals(48_000_000L, budget.getLong("pcmSamplesHeld"))
        assertEquals(AudioDecodeBudget.MAX_PCM_SAMPLES.toLong(), budget.getLong("pcmCapSamples"))
        assertEquals(false, budget.getBoolean("pcmCapReached"))
        val models = budget.getJSONArray("modelsLoaded")
        assertEquals(1, models.length())
        assertEquals("onnx:whisper-base.onnx", models.getJSONObject(0).getString("name"))
        assertEquals(145_000_000L, models.getJSONObject(0).getLong("bytes"))
        assertEquals(2, models.getJSONObject(0).getInt("sessions"))

        val lowMemory = records.getValue(6_000L)
        assertEquals("LOW_MEMORY", lowMemory.getString("reason"))
        assertEquals(8, lowMemory.getJSONObject("memoryContext").getInt("ramTierGb"))

        assertEquals("CRASH", records.getValue(5_000L).getString("reason"))
        assertTrue(records.getValue(5_000L).isNull("memoryContext"))
    }

    @Test
    fun aMemoryKillKeepsTheBudgetOfTheRunItEnded() {
        val history = historyFileInAppData()
        val firstKill = snapshot(timestamp = 7_000L, reason = 13, pid = 50, processName = "com.novacut.editor",
            description = "MemoryLimiter:AnonSwap")
        val secondKill = snapshot(timestamp = 9_000L, reason = 13, pid = 60, processName = "com.novacut.editor",
            description = "MemoryLimiter:AnonSwap")
        ProcessExitRecorder.forFile(history, FakeProcessExitSource()).startMemoryCapture()
        AudioDecodeBudget.exceedsBudget(current = 0, incoming = 16_000_000)

        // Android lists the first kill again on every launch after it.
        ProcessExitRecorder.forFile(history, FakeProcessExitSource(records = listOf(firstKill))).apply {
            recordStartupExitReasons(nowEpochMs = 8_000L)
            startMemoryCapture()
        }
        AudioDecodeBudget.exceedsBudget(current = 0, incoming = 80_000_000)
        val recorder = ProcessExitRecorder.forFile(history, FakeProcessExitSource(records = listOf(secondKill, firstKill)))
        recorder.recordStartupExitReasons(nowEpochMs = 10_000L)
        val records = recordsByTimestamp(recorder)

        fun heldBy(timestamp: Long) = records.getValue(timestamp)
            .getJSONObject("memoryContext").getJSONObject("lastBudget").getLong("pcmSamplesHeld")
        assertEquals(16_000_000L, heldBy(7_000L))
        assertEquals(80_000_000L, heldBy(9_000L))
    }

    @Test
    fun aRunThatNotedNoBudgetSaysSoAndAnUnknownDeviceStaysUnknown() {
        val history = historyFileInAppData()
        ProcessExitRecorder.forFile(history, FakeProcessExitSource()).startMemoryCapture()
        MemoryBudgetLedger.uninstall()
        val recorder = ProcessExitRecorder.forFile(
            history,
            FakeProcessExitSource(records = listOf(snapshot(timestamp = 7_000L, reason = 3, pid = 50, processName = "com.novacut.editor")))
        )

        recorder.recordStartupExitReasons(nowEpochMs = 8_000L)
        val context = recordsByTimestamp(recorder).getValue(7_000L).getJSONObject("memoryContext")

        assertTrue(context.isNull("lastBudget"))
        assertTrue(context.isNull("ramTierGb"))
    }

    @Test
    fun ramTiersMatchWhatPhonesAreSoldAs() {
        val gib = 1L shl 30
        assertEquals(0, ProcessExitRecorder.ramTierGb(0L))
        assertEquals(4, ProcessExitRecorder.ramTierGb((3.6 * gib).toLong()))
        assertEquals(6, ProcessExitRecorder.ramTierGb((5.5 * gib).toLong()))
        assertEquals(8, ProcessExitRecorder.ramTierGb((7.3 * gib).toLong()))
        assertEquals(12, ProcessExitRecorder.ramTierGb((11.2 * gib).toLong()))
        assertEquals(16, ProcessExitRecorder.ramTierGb(16 * gib))
        assertEquals(48, ProcessExitRecorder.ramTierGb((47.1 * gib).toLong()))
    }

    @Test
    fun heapDumpsAreListedAndOnlyTheNewestFileIsKept() {
        val history = historyFileInAppData()
        val profiling = File(history.parentFile.parentFile, "profiling").apply { mkdirs() }
        val foreign = File(temp.newFolder("elsewhere"), "foreign.hprof").apply { writeText("not ours") }
        val older = File(profiling, "oom-1.hprof").apply { writeText("older dump") }
        val newer = File(profiling, "anomaly-2.hprof").apply { writeText("newer dump") }
        val recorder = ProcessExitRecorder.forFile(history, FakeProcessExitSource())

        recorder.recordHeapDump(HeapDumpResult(ProcessExitRecorder.TRIGGER_TYPE_OOM, 0, null, foreign.path), nowEpochMs = 1_000L)
        recorder.recordHeapDump(HeapDumpResult(ProcessExitRecorder.TRIGGER_TYPE_OOM, 0, null, older.path), nowEpochMs = 2_000L)
        recorder.recordHeapDump(HeapDumpResult(ProcessExitRecorder.TRIGGER_TYPE_ANOMALY, 0, null, newer.path), nowEpochMs = 3_000L)
        recorder.recordHeapDump(
            HeapDumpResult(ProcessExitRecorder.TRIGGER_TYPE_ANOMALY, 1, "rate limited for /data/user/0/com.novacut.editor/files", null),
            nowEpochMs = 4_000L,
        )

        assertTrue("A file outside the app's data is never deleted", foreign.isFile)
        assertFalse(older.exists())
        assertTrue(newer.isFile)
        assertEquals(newer.absolutePath, recorder.latestHeapDumpFile()?.absolutePath)

        val raw = recorder.buildDiagnosticJson()
        assertFalse(raw.contains("\"path\""))
        assertFalse(raw.contains("/data/user/0"))
        val dumps = JSONObject(raw).getJSONArray("heapDumps")
        assertEquals(4, dumps.length())
        dumps.getJSONObject(0).let { failed ->
            assertEquals("MEMORY_LIMIT", failed.getString("trigger"))
            assertEquals("RATE_LIMIT_SYSTEM", failed.getString("result"))
            assertTrue(failed.isNull("fileName"))
            assertEquals(0L, failed.getLong("sizeBytes"))
            assertTrue(failed.getString("errorMessage").startsWith("rate limited for "))
            assertFalse(failed.getString("errorMessage").contains("/data/user"))
        }
        dumps.getJSONObject(1).let { kept ->
            assertEquals("MEMORY_LIMIT", kept.getString("trigger"))
            assertEquals("OK", kept.getString("result"))
            assertEquals("anomaly-2.hprof", kept.getString("fileName"))
            assertEquals(10L, kept.getLong("sizeBytes"))
        }
        assertEquals("OUT_OF_MEMORY_ERROR", dumps.getJSONObject(2).getString("trigger"))
        assertEquals("oom-1.hprof", dumps.getJSONObject(2).getString("fileName"))

        repeat(10) { index ->
            recorder.recordHeapDump(HeapDumpResult(ProcessExitRecorder.TRIGGER_TYPE_OOM, 2, null, null), nowEpochMs = 5_000L + index)
        }
        assertEquals(8, JSONObject(recorder.buildDiagnosticJson()).getJSONArray("heapDumps").length())
    }

    @Test
    fun memoryCaptureStartsWithoutHeapDumpTriggersAndDeliversDumpsWhereTheyExist() {
        val history = historyFileInAppData()
        val budgetFile = File(history.parentFile, ProcessExitRecorder.BUDGET_FILE)
        // Before Android 17 the source has no triggers at all.
        val olderAndroid = object : ProcessExitSource {
            override val supported = true
            override val lowMemoryKillReportSupported: Boolean? = true
            override fun recentExitRecords(maxRecords: Int) = emptyList<ProcessExitSnapshot>()
        }
        ProcessExitRecorder.forFile(history, olderAndroid).startMemoryCapture()
        AudioDecodeBudget.exceedsBudget(current = 0, incoming = 16_000_000)
        assertEquals(16_000_000L, MemoryBudgetLedger.read(budgetFile)?.getLong("pcmSamplesHeld"))

        // A platform that throws on registration still gets the ledger.
        ProcessExitRecorder.forFile(history, FakeProcessExitSource(onRegister = { error("no ProfilingManager") }))
            .startMemoryCapture()
        assertNull(MemoryBudgetLedger.read(budgetFile))
        AudioDecodeBudget.exceedsBudget(current = 0, incoming = 24_000_000)
        assertEquals(24_000_000L, MemoryBudgetLedger.read(budgetFile)?.getLong("pcmSamplesHeld"))

        val dump = File(history.parentFile.parentFile, "profiling/delivered.hprof").apply {
            parentFile?.mkdirs()
            writeText("dump")
        }
        val recorder = ProcessExitRecorder.forFile(
            history,
            FakeProcessExitSource(onRegister = { deliver ->
                deliver(HeapDumpResult(ProcessExitRecorder.TRIGGER_TYPE_OOM, 0, null, dump.path))
                true
            })
        )
        recorder.startMemoryCapture()
        assertEquals(dump.absolutePath, recorder.latestHeapDumpFile()?.absolutePath)
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

    // Laid out like the app's own data: <data>/files/diagnostics/process-exit-history.json.
    private fun historyFileInAppData(): File =
        File(temp.newFolder("data", "files", "diagnostics"), "process-exit-history.json")

    private fun recordsByTimestamp(recorder: ProcessExitRecorder): Map<Long, JSONObject> {
        val records = JSONObject(recorder.buildDiagnosticJson()).getJSONArray("records")
        return (0 until records.length()).associate { index ->
            val record = records.getJSONObject(index)
            record.getLong("timestampEpochMs") to record
        }
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
        private val memory: DeviceMemory? = null,
        private val onRegister: ((HeapDumpResult) -> Unit) -> Boolean = { false },
    ) : ProcessExitSource {
        override fun recentExitRecords(maxRecords: Int): List<ProcessExitSnapshot> {
            return records.take(maxRecords)
        }

        override fun deviceMemory(): DeviceMemory? = memory

        override fun registerHeapDumpTriggers(onResult: (HeapDumpResult) -> Unit): Boolean = onRegister(onResult)
    }
}

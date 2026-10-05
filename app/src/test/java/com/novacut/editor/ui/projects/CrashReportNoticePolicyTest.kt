package com.novacut.editor.ui.projects

import com.novacut.editor.engine.CrashRecordStore
import com.novacut.editor.engine.ProcessExitRecorder
import com.novacut.editor.engine.ProcessExitSnapshot
import com.novacut.editor.engine.ProcessExitSource
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Which crashes and exits earn a banner on the next launch, and when it goes away for good. */
class CrashReportNoticePolicyTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val now = 1_800_000_000_000L
    private val hour = 60 * 60 * 1000L

    @Test
    fun aRecordedCrashAndTheExitItCausedMakeOneNoticeNamingTheException() {
        val crashes = crashStore()
        crashes.recordUncaughtException(Thread.currentThread(), IllegalStateException("decoder released"), "v3.81.0", now - hour)
        val exits = exitHistory(exit(at = now - hour + 400, reason = ProcessExitRecorder.REASON_CRASH, importance = 100))

        val notice = latest(crashes, exits)

        assertNotNull(notice)
        assertEquals(CrashReportKind.CRASH, notice!!.kind)
        assertEquals("java.lang.IllegalStateException", notice.detail)
        assertEquals("v3.81.0", notice.appVersion)
        assertEquals(now - hour, notice.occurredAtEpochMs)
        // The exit lands after the record, and dismissing has to cover it too.
        assertEquals(now - hour + 400, notice.coversThroughEpochMs)
    }

    @Test
    fun onceSetAsideItStaysGoneUntilTheNextCrash() {
        val crashes = crashStore()
        crashes.recordUncaughtException(Thread.currentThread(), IllegalStateException(), "v3.82.0", now - hour)
        val exits = exitHistory(exit(at = now - hour + 400, reason = ProcessExitRecorder.REASON_CRASH, importance = 100))
        crashes.acknowledgeNoticesThrough(latest(crashes, exits)!!.coversThroughEpochMs)

        // A fresh store reads the acknowledgement back from disk, as the next launch would.
        val relaunched = CrashRecordStore.forDirectory(crashDir)
        assertNull(latest(relaunched, exits))

        relaunched.recordUncaughtException(Thread.currentThread(), OutOfMemoryError(), "v3.82.0", now - 60_000)
        assertEquals("java.lang.OutOfMemoryError", latest(relaunched, exits)?.detail)
    }

    @Test
    fun anOlderAcknowledgementNeverReopensANewerOne() {
        val crashes = crashStore()
        crashes.acknowledgeNoticesThrough(now - hour)
        crashes.acknowledgeNoticesThrough(now - 2 * hour)

        assertEquals(now - hour, crashes.noticeAcknowledgedThroughEpochMs())
    }

    @Test
    fun theAcknowledgementIsNeitherACrashRecordNorPartOfTheBundle() {
        val crashes = crashStore()
        crashes.recordUncaughtException(Thread.currentThread(), IllegalStateException(), "v3.82.0", now - hour)
        crashes.acknowledgeNoticesThrough(now)
        crashes.pruneOldRecords(retainCount = 1)

        assertEquals(1, crashes.recentCrashes().size)
        assertTrue(crashes.buildDiagnosticJson()!!.contains("\"recordCount\": 1"))
        assertEquals(now, crashes.noticeAcknowledgedThroughEpochMs())
    }

    @Test
    fun anUnreadableRecordIsSkipped() {
        val crashes = crashStore()
        File(crashDir, "crash-1-1.json").writeText("{ not json")
        crashes.recordUncaughtException(Thread.currentThread(), IllegalArgumentException(), "v3.82.0", now - hour)

        assertEquals(listOf("java.lang.IllegalArgumentException"), crashes.recentCrashes().map { it.rootClassName })
    }

    @Test
    fun androidsOwnExitReasonsMapToWhatTheUserSaw() {
        fun kind(reason: Int, importance: Int = 100, description: String? = null) =
            CrashReportNoticePolicy.kindOf(exit(at = now, reason = reason, importance = importance, description = description))

        assertEquals(CrashReportKind.CRASH, kind(ProcessExitRecorder.REASON_CRASH_NATIVE))
        assertEquals(CrashReportKind.CRASH, kind(ProcessExitRecorder.REASON_INITIALIZATION_FAILURE))
        assertEquals(CrashReportKind.NOT_RESPONDING, kind(ProcessExitRecorder.REASON_ANR, importance = 400))
        assertEquals(CrashReportKind.RESOURCE_LIMIT, kind(ProcessExitRecorder.REASON_EXCESSIVE_RESOURCE_USAGE, importance = 400))
        assertEquals(CrashReportKind.MEMORY_LIMIT, kind(ProcessExitRecorder.REASON_OTHER, description = "MemoryLimiter:AnonSwap"))
        assertNull(kind(ProcessExitRecorder.REASON_OTHER))
        // Leaving, force-stopping, updating and plain signals aren't faults.
        for (reason in listOf(1, 2, 10, 11, 16)) assertNull("reason $reason", kind(reason))
    }

    @Test
    fun lowMemoryKillsOnlyCountWhileClearCutWasOnScreenOrExporting() {
        fun kind(importance: Int) = CrashReportNoticePolicy.kindOf(
            exit(at = now, reason = ProcessExitRecorder.REASON_LOW_MEMORY, importance = importance)
        )

        assertEquals(CrashReportKind.LOW_MEMORY, kind(100))
        assertEquals(CrashReportKind.LOW_MEMORY, kind(125))
        assertEquals(CrashReportKind.LOW_MEMORY, kind(230))
        assertNull(kind(300))
        assertNull(kind(400))
        assertNull("unknown importance", kind(0))
        assertNull(
            "a memory-limiter kill in the background",
            CrashReportNoticePolicy.kindOf(exit(at = now, reason = ProcessExitRecorder.REASON_OTHER, importance = 400, description = "MemoryLimiter:AnonSwap"))
        )
    }

    @Test
    fun theNewestEventLeadsAndTheNoticeCoversThemAll() {
        val crashes = crashStore()
        crashes.recordUncaughtException(Thread.currentThread(), IllegalStateException(), "v3.82.0", now - 3 * hour)
        val exits = exitHistory(
            exit(at = now - hour, reason = ProcessExitRecorder.REASON_ANR, importance = 100),
            exit(at = now - 2 * hour, reason = ProcessExitRecorder.REASON_CRASH_NATIVE, importance = 100),
            // Newer, but nothing went wrong.
            exit(at = now - 60_000, reason = 10, importance = 100),
        )

        val notice = latest(crashes, exits)!!

        assertEquals(CrashReportKind.NOT_RESPONDING, notice.kind)
        assertEquals("ANR", notice.detail)
        assertEquals(now - hour, notice.coversThroughEpochMs)
    }

    @Test
    fun aCrashExitWithNoRecordBehindItStillShows() {
        // A crash before the handler was installed, or on a version without it.
        val notice = latest(crashStore(), exitHistory(exit(at = now - hour, reason = ProcessExitRecorder.REASON_CRASH, importance = 100)))

        assertEquals("CRASH", notice?.detail)
        assertNull(notice?.appVersion)
    }

    @Test
    fun aCrashExitLongAfterTheRecordIsItsOwnEvent() {
        val crashes = crashStore()
        crashes.recordUncaughtException(Thread.currentThread(), IllegalStateException(), "v3.82.0", now - hour)
        val laterExit = now - hour + CrashReportNoticePolicy.CRASH_EXIT_MATCH_MS + 1

        val notice = latest(crashes, exitHistory(exit(at = laterExit, reason = ProcessExitRecorder.REASON_CRASH, importance = 100)))

        assertEquals("CRASH", notice?.detail)
        assertEquals(laterExit, notice?.occurredAtEpochMs)
    }

    @Test
    fun eventsOlderThanTwoWeeksAreHistory() {
        val crashes = crashStore()
        val edge = now - CrashReportNoticePolicy.LOOKBACK_MS
        crashes.recordUncaughtException(Thread.currentThread(), IllegalStateException(), "v3.82.0", edge - 1)
        val exits = exitHistory(exit(at = edge - 1, reason = ProcessExitRecorder.REASON_ANR, importance = 100))

        assertNull(latest(crashes, exits))
        assertNotNull(latest(crashes, exitHistory(exit(at = edge, reason = ProcessExitRecorder.REASON_ANR, importance = 100))))
    }

    @Test
    fun theIssueBodyCarriesTheVersionDeviceAndroidAndAbi() {
        val notice = CrashReportNotice(
            kind = CrashReportKind.CRASH,
            occurredAtEpochMs = 1_791_244_920_000L, // 2026-10-06 00:02 UTC
            coversThroughEpochMs = 1_791_244_920_000L,
            detail = "java.lang.IllegalStateException",
            appVersion = "v3.81.0",
        )
        val device = CrashReportDevice("v3.82.0", "samsung", "SM-S901U", "16", 36, "arm64-v8a")

        val body = CrashReportNoticePolicy.issueBody(notice, device)

        assertTrue(body, body.contains("ClearCut closed unexpectedly (java.lang.IllegalStateException) at 2026-10-06 00:02 UTC."))
        assertTrue(body, body.contains("- ClearCut: v3.82.0\n- Version it happened in: v3.81.0\n"))
        assertTrue(body, body.contains("- Device: samsung SM-S901U\n"))
        assertTrue(body, body.contains("- Android: 16 (API 36)\n"))
        assertTrue(body, body.contains("- ABI: arm64-v8a\n"))
        assertFalse(
            "same version twice",
            CrashReportNoticePolicy.issueBody(notice.copy(appVersion = "v3.82.0"), device).contains("happened in")
        )
    }

    private val crashDir: File get() = File(temp.root, "crashes")

    private fun crashStore() = CrashRecordStore.forDirectory(crashDir.apply { mkdirs() })

    /** Exits as the next launch reads them: through the saved history file. */
    private fun exitHistory(vararg exits: ProcessExitSnapshot): List<ProcessExitSnapshot> {
        val recorder = ProcessExitRecorder.forFile(
            temp.newFile(),
            object : ProcessExitSource {
                override val supported = true
                override val lowMemoryKillReportSupported = true
                override fun recentExitRecords(maxRecords: Int) = exits.toList()
            }
        )
        recorder.recordStartupExitReasons(nowEpochMs = now)
        return recorder.readHistoryRecords()
    }

    private fun latest(crashes: CrashRecordStore, exits: List<ProcessExitSnapshot>) = CrashReportNoticePolicy.latest(
        crashes = crashes.recentCrashes(),
        exits = exits,
        acknowledgedThroughEpochMs = crashes.noticeAcknowledgedThroughEpochMs(),
        nowEpochMs = now,
    )

    private fun exit(at: Long, reason: Int, importance: Int, description: String? = null) = ProcessExitSnapshot(
        timestampEpochMs = at,
        reasonCode = reason,
        status = 0,
        pid = (at % 30_000).toInt() + reason,
        processName = "com.novacut.editor",
        importance = importance,
        pssKb = 0,
        rssKb = 0,
        description = description,
    )
}

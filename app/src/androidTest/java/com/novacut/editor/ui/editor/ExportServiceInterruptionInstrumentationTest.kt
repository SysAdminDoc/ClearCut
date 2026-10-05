package com.novacut.editor.ui.editor

import android.app.ForegroundServiceStartNotAllowedException
import android.content.ComponentName
import android.content.ContentResolver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.novacut.editor.R
import com.novacut.editor.engine.BatchExportPlanContext
import com.novacut.editor.engine.BatchExportPlanStore
import com.novacut.editor.engine.ExportHistoryEntry
import com.novacut.editor.engine.ExportHistoryStatus
import com.novacut.editor.engine.ExportHistoryStore
import com.novacut.editor.engine.ExportOutputVerifier
import com.novacut.editor.engine.ExportState
import com.novacut.editor.engine.FFmpegEngine
import com.novacut.editor.engine.FontRegistry
import com.novacut.editor.engine.MediaPipeUsageGate
import com.novacut.editor.engine.MemoryTrimRegistry
import com.novacut.editor.engine.ModelDownloadManager
import com.novacut.editor.engine.ProductHealthLedger
import com.novacut.editor.engine.StreamCopyExportEngine
import com.novacut.editor.engine.StreamCopyMuxer
import com.novacut.editor.engine.VideoEngine
import com.novacut.editor.engine.segmentation.SegmentationEngine
import com.novacut.editor.model.BatchExportStatus
import com.novacut.editor.model.Clip
import com.novacut.editor.model.ExportConfig
import com.novacut.editor.model.Project
import com.novacut.editor.model.Resolution
import com.novacut.editor.model.Track
import com.novacut.editor.model.TrackType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The two ways Android ends a foreground-service export from outside: the
 * media-processing timeout (ExportService.onTimeout hands it to
 * VideoEngine.failExportDueToForegroundServiceTimeout, as here) and a refused
 * startForegroundService. Both must leave a durable INTERRUPTED history entry and
 * no pending MediaStore row, and a timed-out resumable export must resume to a
 * complete file.
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 31)
class ExportServiceInterruptionInstrumentationTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val target = instrumentation.targetContext
    private val workDir = File(target.cacheDir, "fgs-interruption-${System.nanoTime()}")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val project = Project(name = "Interruption fixture")
    private val toasts = java.util.concurrent.CopyOnWriteArrayList<String>()
    private lateinit var engine: VideoEngine

    @Before
    fun setUp() {
        workDir.mkdirs()
        engine = buildVideoEngine(target)
    }

    @After
    fun tearDown() {
        runCatching { engine.cancelExport() }
        engine.release()
        scope.cancel()
        // The history and batch plan are the app's real stores; drop this fixture's rows.
        val history = ExportHistoryStore.forContext(target)
        history.read().filter { it.projectId == project.id }.forEach { history.remove(it.id) }
        BatchExportPlanStore.forContext(target)
            .saveFor(BatchExportPlanContext(project.id, FINGERPRINT), emptyList())
        workDir.deleteRecursively()
    }

    @Test
    fun aServiceTimeoutKeepsAResumablePartialAndResumeFinishesTheExport() {
        val (state, delegate, _) = buildDelegate(refuseServiceStart = false)
        val outputDir = File(workDir, "out").apply { mkdirs() }

        instrumentation.runOnMainSync {
            delegate.startExport(outputDir = outputDir, preferredOutputName = "interrupted-export")
        }
        waitUntil(120_000L, "the export to pass 25%") { engine.exportProgress.value >= 0.25f }
        // ExportService.onTimeout runs on the main thread, which Transformer requires.
        var failedActiveExport = false
        instrumentation.runOnMainSync {
            failedActiveExport = engine.failExportDueToForegroundServiceTimeout("Android stopped the export")
        }
        assertTrue("no export was running when the timeout landed", failedActiveExport)

        val interrupted = waitForHistory(state) { it.status == ExportHistoryStatus.INTERRUPTED }
        val partial = File(requireNotNull(interrupted.resumePartialPath) { "no resumable partial was kept" })
        assertTrue("the kept partial is empty", partial.isFile && partial.length() > 0L)
        assertEquals(target.getString(R.string.export_interrupted_resumable_note), interrupted.diagnosticSummary)
        assertEquals(ExportState.ERROR, state.value.exportState)
        assertEquals(0, pendingMediaStoreRows())

        instrumentation.runOnMainSync { delegate.resumeExport(interrupted) }
        val completed = waitForHistory(state, timeoutMs = 240_000L) { it.status == ExportHistoryStatus.COMPLETE }

        val output = File(requireNotNull(state.value.lastExportedFilePath) { "resume finished without an output" })
        val verification = ExportOutputVerifier.verify(
            outputFile = output,
            expectVideo = true,
            expectAudio = true,
            expectedDurationMs = 30_000L,
        )
        assertTrue("resumed output failed verification: ${verification.reason}", verification.valid)
        assertNotEquals(partial.absolutePath, output.absolutePath)
        assertFalse("the interrupted partial outlived the resume", partial.exists())
        assertFalse(
            "the interrupted entry was not superseded",
            state.value.export.history.any { it.id == interrupted.id },
        )
        assertEquals(0, pendingMediaStoreRows())
    }

    @Test
    fun aRefusedServiceStartRecordsAnInterruptionAndLeavesNothingBehind() {
        val (state, delegate, context) = buildDelegate(refuseServiceStart = true)
        val outputDir = File(workDir, "refused").apply { mkdirs() }

        instrumentation.runOnMainSync {
            delegate.startExport(outputDir = outputDir, preferredOutputName = "refused-export")
        }
        val refused = waitForHistory(state) { it.status == ExportHistoryStatus.INTERRUPTED }

        assertEquals(1, context.serviceStarts)
        assertEquals(target.getString(R.string.export_service_start_refused), refused.errorMessage)
        assertNull(refused.resumePartialPath)
        assertEquals(ExportState.ERROR, state.value.exportState)
        assertNotEquals(ExportState.EXPORTING, engine.exportState.value)
        assertEquals(emptyList<String>(), outputDir.list().orEmpty().toList())
        assertEquals(0, pendingMediaStoreRows())
    }

    @Test
    fun aRefusedServiceStartStopsABatchAndKeepsTheRestOfThePlan() {
        val (state, delegate, context) = buildDelegate(refuseServiceStart = true)
        val config = state.value.exportConfig

        instrumentation.runOnMainSync {
            delegate.addBatchExportItem(config, "first")
            delegate.addBatchExportItem(config, "second")
            delegate.startBatchExport()
        }
        val summary = target.getString(R.string.batch_export_interrupted_summary, 0)
        waitUntil(60_000L, "the batch to stop", { "queue ${state.value.batchExportQueue.map { it.status }}" }) {
            summary in toasts
        }

        val queue = state.value.batchExportQueue
        assertEquals(listOf(BatchExportStatus.INTERRUPTED, BatchExportStatus.QUEUED), queue.map { it.status })
        assertEquals(target.getString(R.string.export_service_start_refused), queue.first().errorMessage)
        assertEquals("the batch went on to start the next item", 1, context.serviceStarts)
        assertEquals(
            queue.map { it.status },
            BatchExportPlanStore.forContext(target)
                .readFor(BatchExportPlanContext(project.id, FINGERPRINT))
                .map { it.status },
        )
        assertEquals(0, pendingMediaStoreRows())
    }

    /** Stands in for the system: counts foreground-service starts, and can refuse them. */
    private class ServiceStartContext(base: Context, private val refuse: Boolean) : ContextWrapper(base) {
        @Volatile var serviceStarts = 0

        override fun getApplicationContext(): Context = this

        override fun startForegroundService(service: Intent): ComponentName? {
            serviceStarts++
            if (refuse) {
                throw ForegroundServiceStartNotAllowedException(
                    "startForegroundService() not allowed due to mAllowStartForeground false"
                )
            }
            return service.component
        }
    }

    private fun buildDelegate(
        refuseServiceStart: Boolean,
    ): Triple<MutableStateFlow<EditorState>, ExportDelegate, ServiceStartContext> {
        val source = File(workDir, "export-30s-av.mp4")
        instrumentation.context.assets.open("export-30s-av.mp4").use { input ->
            source.outputStream().use(input::copyTo)
        }
        val clip = Clip(
            sourceUri = Uri.fromFile(source),
            sourceDurationMs = 30_000L,
            timelineStartMs = 0L,
            trimStartMs = 0L,
            trimEndMs = 30_000L,
        )
        val state = MutableStateFlow(
            EditorState(
                project = project,
                tracks = listOf(Track(type = TrackType.VIDEO, index = 0, clips = listOf(clip))),
                totalDurationMs = 30_000L,
                export = EditorExportDomainState(
                    config = ExportConfig(resolution = Resolution.SD_480P, frameRate = 30),
                ),
            )
        )
        val context = ServiceStartContext(target, refuseServiceStart)
        val delegate = ExportDelegate(
            stateFlow = state,
            videoEngine = engine,
            appContext = context,
            scope = scope,
            showToast = { toasts += it },
            pauseIfPlaying = {},
            dismissedPanelState = { it },
            showExportSheet = {},
            // The editor always passes a real fingerprint; a blank one is stored as
            // none, and resume then refuses the mismatch.
            projectFingerprint = { FINGERPRINT },
        )
        return Triple(state, delegate, context)
    }

    private fun waitForHistory(
        state: MutableStateFlow<EditorState>,
        timeoutMs: Long = 60_000L,
        match: (ExportHistoryEntry) -> Boolean,
    ): ExportHistoryEntry {
        // The store is the app's real one, so only this fixture project's rows count.
        val projectId = state.value.project.id
        var found: ExportHistoryEntry? = null
        waitUntil(timeoutMs, "a matching export history entry", { "export error ${state.value.export.errorMessage}" }) {
            found = state.value.export.history.firstOrNull { it.projectId == projectId && match(it) }
            found != null
        }
        return requireNotNull(found)
    }

    private fun waitUntil(
        timeoutMs: Long,
        what: String,
        detail: () -> String = { "" },
        condition: () -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) {
                "Timed out waiting for $what (engine ${engine.exportState.value}, " +
                    "progress ${engine.exportProgress.value}) ${detail()}"
            }
            Thread.sleep(100L)
        }
    }

    /** Video rows this app left pending, which Gallery apps never show. */
    private fun pendingMediaStoreRows(): Int {
        val args = Bundle().apply {
            putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_ONLY)
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, "${MediaStore.MediaColumns.OWNER_PACKAGE_NAME} = ?")
            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, arrayOf(target.packageName))
        }
        return target.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.MediaColumns._ID),
            args,
            null,
        )?.use { it.count } ?: 0
    }

    private companion object {
        const val FINGERPRINT = "interruption-fixture"
    }

    private fun buildVideoEngine(context: Context): VideoEngine {
        val engineScope = CoroutineScope(SupervisorJob())
        val segmentation = SegmentationEngine(
            context,
            ModelDownloadManager(context),
            MediaPipeUsageGate(
                consentVersionFlow = flowOf(0),
                persistConsentVersion = {},
                scope = engineScope,
            ),
        )
        return VideoEngine(
            context = context,
            segmentationEngine = segmentation,
            streamCopyEngine = StreamCopyExportEngine(StreamCopyMuxer(context)),
            ffmpegEngine = FFmpegEngine(context),
            fontRegistry = FontRegistry(context),
            memoryTrimRegistry = MemoryTrimRegistry(),
            productHealthLedger = ProductHealthLedger(context),
        )
    }
}

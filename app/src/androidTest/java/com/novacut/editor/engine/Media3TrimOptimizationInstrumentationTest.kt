package com.novacut.editor.engine

import android.content.Context
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.novacut.editor.engine.segmentation.SegmentationEngine
import com.novacut.editor.model.Clip
import com.novacut.editor.model.ExportConfig
import com.novacut.editor.model.Resolution
import com.novacut.editor.model.Track
import com.novacut.editor.model.TrackType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Device contract for the real Media3 trim-optimization builder path. The
 * policy gate is covered on the JVM; this test proves an eligible MP4 still
 * completes and passes the same output verifier on a hardware codec stack.
 */
@RunWith(AndroidJUnit4::class)
class Media3TrimOptimizationInstrumentationTest {
    @Test
    fun pureTrimOutputPassesTheExistingVerifier() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val source = File(context.cacheDir, "trim-optimization-source-${System.nanoTime()}.mp4")
        val output = File(context.cacheDir, "trim-optimization-output-${System.nanoTime()}.mp4")
        instrumentation.context.assets.open("trim-boundary.mp4").use { input ->
            source.outputStream().use(input::copyTo)
        }

        val engine = buildVideoEngine(context)
        try {
            val clip = Clip(
                sourceUri = Uri.fromFile(source),
                sourceDurationMs = 1_000L,
                timelineStartMs = 0L,
                trimStartMs = 500L,
                trimEndMs = 1_000L,
            )
            val track = Track(type = TrackType.VIDEO, index = 0, clips = listOf(clip))
            var completed = false
            var error: Exception? = null

            runBlocking {
                withTimeout(60_000L) {
                    engine.export(
                        tracks = listOf(track),
                        config = ExportConfig(resolution = Resolution.SD_480P),
                        outputFile = output,
                        onComplete = { completed = true },
                        onError = { error = it },
                    )
                }
            }

            assertNull("trim export reported an error: ${error?.message}", error)
            assertTrue("trim export did not complete", completed)
            val disclosure = engine.trimOptimizationDisclosure.value
            assertNotNull("trim strategy was not published", disclosure)
            assertEquals(Media3TrimOptimizationPolicy.Strategy.SMART_TRIM, disclosure?.strategy)
            assertNotNull("Media3 optimization result was not published", disclosure?.outcome)
            val verification = ExportOutputVerifier.verify(
                outputFile = output,
                expectVideo = true,
                expectedDurationMs = 500L,
            )
            assertTrue("trim output failed verification: ${verification.reason}", verification.valid)
            assertTrue("trim output has no duration", verification.durationMs > 0L)
        } finally {
            engine.release()
            source.delete()
            output.delete()
        }
    }

    private fun buildVideoEngine(context: Context): VideoEngine {
        val scope = CoroutineScope(SupervisorJob())
        val segmentation = SegmentationEngine(
            context,
            ModelDownloadManager(context),
            MediaPipeUsageGate(
                consentVersionFlow = flowOf(0),
                persistConsentVersion = {},
                scope = scope,
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

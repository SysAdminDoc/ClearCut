package com.novacut.editor.engine

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Device acceptance for the offline stabilization path. The test exercises the
 * platform decoder, bounded analysis, shared transform keyframes, and
 * cancellation against the same small video asset used by the other API 37
 * acceptance lanes.
 */
@RunWith(AndroidJUnit4::class)
class StabilizationDeviceAcceptanceTest {

    @Test
    fun analysisProducesReversibleMotionDataAndSharedKeyframes() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val source = File(context.cacheDir, "stabilization-source-${System.nanoTime()}.mp4")
        instrumentation.context.assets.open("trim-boundary.mp4").use { input ->
            source.outputStream().use(input::copyTo)
        }
        val sourceUri = Uri.fromFile(source)
        val sourceBytes = source.readBytes().contentHashCode()
        val engine = StabilizationEngine(context)
        val progress = mutableListOf<Float>()

        try {
            val config = StabilizationEngine.StabilizationConfig(
                analysisIntervalMs = 100L,
                smoothingWindow = 3,
            )
            val capability = engine.capability(sourceUri, config)
            assertTrue("fixture should be analyzable: ${capability.reason}", capability.supported)
            assertTrue("fixture duration was not read", capability.durationMs > 0L)
            assertTrue("fixture dimensions were not read", capability.width > 0 && capability.height > 0)
            assertTrue("analysis interval was not bounded", capability.analysisIntervalMs in 50L..1_000L)

            val motionData = runBlocking {
                withTimeout(120_000L) {
                    engine.analyzeMotion(
                        uri = sourceUri,
                        config = config,
                        onProgress = { progress += it },
                    )
                }
            }
            assertNotNull("offline motion analysis returned no data", motionData)
            val result = checkNotNull(motionData)
            assertTrue("analysis did not decode enough frames", result.frameCount >= 2)
            assertTrue("raw motion samples were missing", result.transforms.isNotEmpty())
            assertTrue("smoothed motion samples were missing", result.smoothedTransforms.isNotEmpty())
            assertTrue("recommended crop was outside its safe bound", result.recommendedCropScale in 1f..1.3f)
            assertEquals(1f, progress.last(), 0.0001f)

            val keyframes = engine.keyframesFor(
                motionData = result,
                sourceToTimelineOffsetMs = { timestampMs ->
                    timestampMs.coerceIn(0L, result.sourceDurationMs)
                },
                clipDurationMs = result.sourceDurationMs,
            )
            assertTrue("analysis did not produce shared transform keyframes", keyframes.isNotEmpty())
            assertTrue(
                "keyframes did not include counter-motion",
                keyframes.any { it.property == com.novacut.editor.model.KeyframeProperty.POSITION_X } &&
                    keyframes.any { it.property == com.novacut.editor.model.KeyframeProperty.POSITION_Y },
            )
            assertEquals(
                "stabilization analysis must preserve the source bytes",
                sourceBytes,
                source.readBytes().contentHashCode(),
            )
        } finally {
            source.delete()
        }
    }

    @Test
    fun analysisCancellationStopsWithoutProducingAResult() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val source = File(context.cacheDir, "stabilization-cancel-${System.nanoTime()}.mp4")
        instrumentation.context.assets.open("trim-boundary.mp4").use { input ->
            source.outputStream().use(input::copyTo)
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val engine = StabilizationEngine(context)

        try {
            val job = scope.launch {
                engine.analyzeMotion(
                    uri = Uri.fromFile(source),
                    config = StabilizationEngine.StabilizationConfig(analysisIntervalMs = 50L),
                )
            }
            runBlocking {
                delay(10L)
                job.cancelAndJoin()
            }
            assertTrue("analysis job did not acknowledge cancellation", job.isCancelled)
            assertFalse("cancelled analysis unexpectedly completed", job.isCompleted && !job.isCancelled)
        } finally {
            scope.cancel()
            source.delete()
        }
    }
}

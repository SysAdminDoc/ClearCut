package com.novacut.editor.engine

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Device contract for the real speed-export frame-rate cap and encoder path. */
@RunWith(AndroidJUnit4::class)
class Media3ExportRobustnessInstrumentationTest {
    @Test
    fun speedExportRemainsValidAndDoesNotAdvertiseBalloonedFrameRate() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val source = File(context.cacheDir, "robustness-source-${System.nanoTime()}.mp4")
        val output = File(context.cacheDir, "robustness-output-${System.nanoTime()}.mp4")
        instrumentation.context.assets.open("trim-boundary.mp4").use { input ->
            source.outputStream().use(input::copyTo)
        }

        val engine = buildVideoEngine(context)
        try {
            val clip = Clip(
                sourceUri = Uri.fromFile(source),
                sourceDurationMs = 1_000L,
                timelineStartMs = 0L,
                trimStartMs = 0L,
                trimEndMs = 1_000L,
                speed = 4f,
            )
            val track = Track(type = TrackType.VIDEO, index = 0, clips = listOf(clip))
            var completed = false
            var error: Exception? = null

            runBlocking {
                withTimeout(60_000L) {
                    engine.export(
                        tracks = listOf(track),
                        config = ExportConfig(
                            resolution = Resolution.SD_480P,
                            frameRate = 30,
                        ),
                        outputFile = output,
                        onComplete = { completed = true },
                        onError = { error = it },
                    )
                }
            }

            assertNull("speed export reported an error: ${error?.message}", error)
            assertTrue("speed export did not complete", completed)
            val verification = ExportOutputVerifier.verify(
                outputFile = output,
                expectVideo = true,
                expectedDurationMs = 250L,
            )
            assertTrue("speed output failed verification: ${verification.reason}", verification.valid)

            val advertisedFrameRate = videoFrameRate(output)
            assertTrue(
                "speed output advertises a ballooned frame rate: $advertisedFrameRate",
                advertisedFrameRate == null || advertisedFrameRate <= 30.5f,
            )
        } finally {
            engine.release()
            source.delete()
            output.delete()
        }
    }

    private fun videoFrameRate(file: File): Float? {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            val format = (0 until extractor.trackCount)
                .map { extractor.getTrackFormat(it) }
                .firstOrNull { it.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true }
                ?: return null
            runCatching { format.getFloat(MediaFormat.KEY_FRAME_RATE) }.getOrNull()
                ?: runCatching { format.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat() }.getOrNull()
        } finally {
            extractor.release()
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
        )
    }
}

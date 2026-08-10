package com.novacut.editor.engine

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.novacut.editor.engine.segmentation.SegmentationEngine
import com.novacut.editor.model.BatchExportSourceRange
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
import java.nio.ByteBuffer
import kotlin.math.abs

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

    @Test
    fun batchSourceRangeExportsOnlyTheQueuedSourceInterval() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val source = File(context.cacheDir, "batch-cut-source-${System.nanoTime()}.mp4")
        val output = File(context.cacheDir, "batch-cut-output-${System.nanoTime()}.mp4")
        instrumentation.context.assets.open("trim-boundary.mp4").use { input ->
            source.outputStream().use(input::copyTo)
        }

        val engine = buildVideoEngine(context)
        try {
            val sourceRange = BatchExportSourceRange(
                clipId = "source-clip",
                sourceUri = Uri.fromFile(source),
                sourceDurationMs = 1_000L,
                startMs = 200L,
                endMs = 700L,
                displayName = "source-cut",
            )
            val track = Track(
                type = TrackType.VIDEO,
                index = 0,
                clips = listOf(sourceRange.toClip("batch-test-clip")),
            )
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

            assertNull("batch source cut reported an error: ${error?.message}", error)
            assertTrue("batch source cut did not complete", completed)
            val verification = ExportOutputVerifier.verify(
                outputFile = output,
                expectVideo = true,
                expectedDurationMs = sourceRange.durationMs,
            )
            assertTrue(
                "batch source cut failed verification: ${verification.reason}",
                verification.valid,
            )
        } finally {
            engine.release()
            source.delete()
            output.delete()
        }
    }

    @Test
    fun constantFrameRateExportNormalizesVariableInputCadence() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val source = File(context.cacheDir, "cfr-source-${System.nanoTime()}.mp4")
        val vfrSource = File(context.cacheDir, "cfr-vfr-source-${System.nanoTime()}.mp4")
        val output = File(context.cacheDir, "cfr-output-${System.nanoTime()}.mp4")
        instrumentation.context.assets.open("trim-boundary.mp4").use { input ->
            source.outputStream().use(input::copyTo)
        }
        createVariableFrameRateCopy(source, vfrSource)
        val inputTimes = videoSampleTimes(vfrSource)
        val durationMs = videoDurationMs(vfrSource)
        assertTrue("test source did not contain an irregular cadence", inputTimes
            .zipWithNext()
            .map { (start, end) -> end - start }
            .distinct()
            .size > 1)

        val engine = buildVideoEngine(context)
        try {
            val clip = Clip(
                sourceUri = Uri.fromFile(vfrSource),
                sourceDurationMs = durationMs,
                timelineStartMs = 0L,
                trimStartMs = 0L,
                trimEndMs = durationMs,
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
                            forceConstantFrameRate = true,
                        ),
                        outputFile = output,
                        onComplete = { completed = true },
                        onError = { error = it },
                    )
                }
            }

            assertNull("CFR export reported an error: ${error?.message}", error)
            assertTrue("CFR export did not complete", completed)
            val verification = ExportOutputVerifier.verify(
                outputFile = output,
                expectVideo = true,
                expectedDurationMs = durationMs,
            )
            assertTrue("CFR output failed verification: ${verification.reason}", verification.valid)

            val outputTimes = videoSampleTimes(output)
            assertTrue("CFR output did not contain enough video samples", outputTimes.size >= 2)
            val targetIntervalUs = 1_000_000L / 30L
            outputTimes.zipWithNext().forEach { (start, end) ->
                assertTrue(
                    "CFR sample interval was ${end - start}us instead of about ${targetIntervalUs}us; samples=$outputTimes",
                    abs((end - start) - targetIntervalUs) <= 2_000L,
                )
            }
            val advertisedFrameRate = videoFrameRate(output)
            assertTrue(
                "CFR output advertises an unexpected frame rate: $advertisedFrameRate",
                advertisedFrameRate == null || abs(advertisedFrameRate - 30f) <= 0.5f,
            )
        } finally {
            engine.release()
            source.delete()
            vfrSource.delete()
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

    private fun videoDurationMs(file: File): Long {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            val format = (0 until extractor.trackCount)
                .map { extractor.getTrackFormat(it) }
                .first { it.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true }
            format.getLong(MediaFormat.KEY_DURATION).coerceAtLeast(1L) / 1_000L
        } finally {
            extractor.release()
        }
    }

    private fun videoSampleTimes(file: File): List<Long> {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            val track = (0 until extractor.trackCount)
                .first { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true }
            extractor.selectTrack(track)
            buildList {
                while (true) {
                    val sampleTime = extractor.sampleTime
                    if (sampleTime < 0L) break
                    add(sampleTime)
                    extractor.advance()
                }
            }.sorted()
        } finally {
            extractor.release()
        }
    }

    private fun createVariableFrameRateCopy(input: File, output: File) {
        val extractor = MediaExtractor()
        val muxer: MediaMuxer
        try {
            extractor.setDataSource(input.absolutePath)
            val track = (0 until extractor.trackCount)
                .first { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true }
            muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val outputTrack = muxer.addTrack(extractor.getTrackFormat(track))
            muxer.start()
            extractor.selectTrack(track)
            val buffer = ByteBuffer.allocate(1 shl 20)
            val info = MediaCodec.BufferInfo()
            var frameIndex = 0
            var presentationTimeUs = 0L
            val durationsUs = longArrayOf(16_667L, 50_000L, 33_333L, 33_333L)
            try {
                while (true) {
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) break
                    info.set(0, size, presentationTimeUs, extractor.sampleFlags)
                    muxer.writeSampleData(outputTrack, buffer, info)
                    presentationTimeUs += durationsUs[frameIndex % durationsUs.size]
                    frameIndex++
                    extractor.advance()
                }
            } finally {
                muxer.stop()
                muxer.release()
            }
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
            productHealthLedger = ProductHealthLedger(context),
        )
    }
}

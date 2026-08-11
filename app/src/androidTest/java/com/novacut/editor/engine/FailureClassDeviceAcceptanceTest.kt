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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

/**
 * Device acceptance for the four failure classes that are easy to miss in JVM
 * policy tests: dense-cut memory pressure, a long scaled render, repeated trims
 * against one source window, and audio/video duration drift. The fixture uses
 * FFmpeg's software MPEG-4 encoder so the result is independent of the API 37
 * emulator's known Media3 H.264 decoder limitation.
 */
@RunWith(AndroidJUnit4::class)
class FailureClassDeviceAcceptanceTest {

    @Test
    fun failureClassFixturesStayWithinDeviceContracts() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val ffmpeg = FFmpegEngine(context)
        val source = file(context, "failure-source")
        val highDefinitionSource = file(context, "failure-1080p-source")
        val scaledOutput = file(context, "failure-720p-output")
        val twoTrimOutput = file(context, "failure-two-trim-output")
        val audioSource = file(context, "failure-3030ms-audio")
        val mismatchOutput = file(context, "failure-duration-mismatch")
        val alignedOutput = file(context, "failure-duration-aligned")
        val shortOutput = file(context, "failure-short-output")
        val emptyOutput = file(context, "failure-empty-output")
        val engine = buildVideoEngine(context)

        try {
            runBlocking {
                withTimeout(300_000L) {
                    assertEquals(
                        "Could not create the software fixture source",
                        0,
                        ffmpeg.execute(
                            "-y -f lavfi -i testsrc2=size=320x240:rate=30 " +
                                "-t 3.000 -c:v mpeg4 -g 1 -b:v 2M -pix_fmt yuv420p " +
                                source.absolutePath,
                        ),
                    )

                    val denseClips = (0 until 51).map { index ->
                        Clip(
                            id = "dense-cut-$index",
                            sourceUri = Uri.fromFile(source),
                            sourceDurationMs = 3_000L,
                            timelineStartMs = index * 50L,
                            trimStartMs = index * 50L,
                            trimEndMs = index * 50L + 50L,
                        )
                    }
                    assertTrue("dense-cut fixture did not contain 50+ clips", denseClips.size > 50)

                    val strip = engine.extractThumbnailStrip(
                        uri = Uri.fromFile(source),
                        count = denseClips.size,
                        width = 80,
                        height = 45,
                    )
                    try {
                        assertEquals(
                            "dense-cut fixture did not yield one thumbnail per segment",
                            denseClips.size,
                            strip.size,
                        )
                        val stripBytes = strip.sumOf { bitmap -> bitmap.byteCount.toLong() }
                        val stripBudget = ThumbnailStripPolicy.budgetBytes(Runtime.getRuntime().maxMemory())
                        assertTrue(
                            "dense-cut thumbnails exceeded the asserted budget: $stripBytes > $stripBudget",
                            stripBytes <= stripBudget,
                        )
                        val retained = ThumbnailStripPolicy.retainedKeys(
                            entriesInInsertionOrder = denseClips.mapIndexed { index, clip ->
                                ThumbnailStripPolicy.StripEntry(
                                    key = clip.id,
                                    bytes = strip[index].byteCount.toLong(),
                                )
                            },
                            budgetBytes = stripBudget,
                        )
                        val retainedBytes = denseClips
                            .filter { it.id in retained }
                            .sumOf { clip ->
                                strip[denseClips.indexOf(clip)].byteCount.toLong()
                            }
                        assertTrue(
                            "retained dense-cut thumbnails exceeded the asserted budget",
                            retainedBytes <= stripBudget,
                        )
                        assertTrue("dense-cut policy retained no thumbnails", retained.isNotEmpty())
                    } finally {
                        engine.clearThumbnailCache()
                        strip.forEach { bitmap -> bitmap.recycle() }
                    }

                    assertEquals(
                        "Could not create the 1080p software fixture source",
                        0,
                        ffmpeg.execute(
                            "-y -f lavfi -i testsrc2=size=1920x1080:rate=30 " +
                                "-t 5.000 -c:v mpeg4 -g 30 -b:v 8M -pix_fmt yuv420p " +
                                highDefinitionSource.absolutePath,
                        ),
                    )
                    assertEquals(
                        "1080p-to-720p export failed",
                        0,
                        ffmpeg.execute(
                            "-y -i ${highDefinitionSource.absolutePath} " +
                                "-vf scale=1280:720 -c:v mpeg4 -g 30 -b:v 4M " +
                                "-pix_fmt yuv420p ${scaledOutput.absolutePath}",
                        ),
                    )
                    val scaledVerification = ExportOutputVerifier.verify(
                        outputFile = scaledOutput,
                        expectVideo = true,
                        expectedDurationMs = 5_000L,
                        durationToleranceMs = 750L,
                        expectedVideoWidth = Resolution.HD_720P.width,
                        expectedVideoHeight = Resolution.HD_720P.height,
                        expectedContainer = ExportContainer.MP4,
                    )
                    assertTrue(
                        "1080p-to-720p output failed verification: ${scaledVerification.reason}",
                        scaledVerification.valid,
                    )

                    val firstTrim = Clip(
                        id = "same-source-first-trim",
                        sourceUri = Uri.fromFile(source),
                        sourceDurationMs = 3_000L,
                        timelineStartMs = 0L,
                        trimStartMs = 250L,
                        trimEndMs = 1_000L,
                    )
                    val secondTrim = firstTrim.copy(
                        id = "same-source-second-trim",
                        timelineStartMs = firstTrim.durationMs,
                        trimStartMs = 1_500L,
                        trimEndMs = 2_250L,
                    )
                    val trimTrack = Track(
                        type = TrackType.VIDEO,
                        index = 0,
                        clips = listOf(firstTrim, secondTrim),
                    )
                    val streamCopy = StreamCopyExportEngine(StreamCopyMuxer(context))
                    val trimEligibility = streamCopy.analyze(
                        tracks = listOf(trimTrack),
                        hasEffectsOrOverlays = false,
                    )
                    assertTrue(
                        "same-source trim fixture was not eligible: ${trimEligibility.reason}",
                        trimEligibility.eligible,
                    )
                    assertEquals(
                        listOf(
                            StreamCopyMuxer.Range(250L, 1_000L),
                            StreamCopyMuxer.Range(1_500L, 2_250L),
                        ),
                        trimEligibility.ranges,
                    )
                    assertTrue(
                        "same-source two-trim export failed",
                        streamCopy.execute(trimEligibility, twoTrimOutput.absolutePath),
                    )
                    val twoTrimVerification = ExportOutputVerifier.verify(
                        outputFile = twoTrimOutput,
                        expectVideo = true,
                        expectedDurationMs = firstTrim.durationMs + secondTrim.durationMs,
                        durationToleranceMs = 200L,
                        expectedContainer = ExportContainer.MP4,
                    )
                    assertTrue(
                        "same-source two-trim output failed verification: ${twoTrimVerification.reason}",
                        twoTrimVerification.valid,
                    )

                    assertEquals(
                        "Could not create the 3030ms audio fixture",
                        0,
                        ffmpeg.execute(
                            "-y -f lavfi -i anullsrc=channel_layout=mono:sample_rate=48000 " +
                                "-t 3.030 -c:a aac -b:a 96k ${audioSource.absolutePath}",
                        ),
                    )
                    assertEquals(
                        "Could not create the mismatched audio/video fixture",
                        0,
                        ffmpeg.execute(
                            "-y -i ${source.absolutePath} -i ${audioSource.absolutePath} " +
                                "-map 0:v:0 -map 1:a:0 -c:v copy -c:a copy -t 3.030 " +
                                mismatchOutput.absolutePath,
                        ),
                    )
                    val mismatchDurations = trackDurationsMs(mismatchOutput)
                    assertTrue("mismatch fixture has no video track", mismatchDurations.videoMs > 0L)
                    assertTrue("mismatch fixture has no audio track", mismatchDurations.audioMs > 0L)
                    assertTrue(
                        "mismatch fixture did not preserve the 30ms drift: $mismatchDurations",
                        abs(mismatchDurations.videoMs - mismatchDurations.audioMs) in 20L..60L,
                    )

                    assertEquals(
                        "Could not align the mismatched streams",
                        0,
                        ffmpeg.execute(
                            "-y -i ${mismatchOutput.absolutePath} -map 0:v:0 -map 0:a:0 " +
                                "-c:v copy -c:a aac -b:a 96k -shortest ${alignedOutput.absolutePath}",
                        ),
                    )
                    val alignedDurations = trackDurationsMs(alignedOutput)
                    assertTrue("aligned output has no video track", alignedDurations.videoMs > 0L)
                    assertTrue("aligned output has no audio track", alignedDurations.audioMs > 0L)
                    assertTrue(
                        "aligned streams ended at different timeline positions: $alignedDurations",
                        abs(alignedDurations.videoMs - alignedDurations.audioMs) <= 40L,
                    )
                    val alignedVerification = ExportOutputVerifier.verify(
                        outputFile = alignedOutput,
                        expectVideo = true,
                        expectAudio = true,
                        expectedDurationMs = 3_000L,
                        durationToleranceMs = 150L,
                        expectedContainer = ExportContainer.MP4,
                    )
                    assertTrue(
                        "aligned output failed verification: ${alignedVerification.reason}",
                        alignedVerification.valid,
                    )

                    assertTrue(
                        "short fixture could not be created",
                        ffmpeg.streamCopyTrim(
                            inputUri = Uri.fromFile(source),
                            startMs = 0L,
                            endMs = 250L,
                            outputPath = shortOutput.absolutePath,
                        ),
                    )
                    val shortVerification = ExportOutputVerifier.verify(
                        outputFile = shortOutput,
                        expectVideo = true,
                        expectedDurationMs = 1_000L,
                        durationToleranceMs = 100L,
                    )
                    assertFalse("short output was accepted as a complete 1s export", shortVerification.valid)

                    var emptyCompleted = false
                    var emptyError: Exception? = null
                    engine.export(
                        tracks = emptyList(),
                        config = ExportConfig(),
                        outputFile = emptyOutput,
                        onComplete = { emptyCompleted = true },
                        onError = { emptyError = it },
                    )
                    assertFalse("empty export reached COMPLETE", emptyCompleted)
                    assertNotNull("empty export did not report an error", emptyError)
                    assertTrue(
                        "empty export state reached COMPLETE",
                        engine.exportState.value != ExportState.COMPLETE,
                    )
                    assertFalse(
                        "empty export left a verifier-valid artifact",
                        ExportOutputVerifier.verify(emptyOutput).valid,
                    )
                }
            }
        } finally {
            engine.release()
            listOf(
                source,
                highDefinitionSource,
                scaledOutput,
                twoTrimOutput,
                audioSource,
                mismatchOutput,
                alignedOutput,
                shortOutput,
                emptyOutput,
            ).forEach(File::delete)
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
            ffmpegEngine = ffmpegEngine(context),
            fontRegistry = FontRegistry(context),
            memoryTrimRegistry = MemoryTrimRegistry(),
            productHealthLedger = ProductHealthLedger(context),
        )
    }

    private fun ffmpegEngine(context: Context): FFmpegEngine = FFmpegEngine(context)

    private fun file(context: Context, prefix: String): File =
        File(context.cacheDir, "$prefix-${System.nanoTime()}.mp4")

    private fun trackDurationsMs(file: File): TrackDurations {
        val extractor = MediaExtractor()
        var videoMs = 0L
        var audioMs = 0L
        try {
            extractor.setDataSource(file.absolutePath)
            repeat(extractor.trackCount) { index ->
                val format = extractor.getTrackFormat(index)
                val durationMs = runCatching {
                    format.getLong(MediaFormat.KEY_DURATION) / 1_000L
                }.getOrDefault(0L)
                when {
                    format.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true -> {
                        videoMs = maxOf(videoMs, durationMs)
                    }
                    format.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true -> {
                        audioMs = maxOf(audioMs, durationMs)
                    }
                }
            }
        } finally {
            extractor.release()
        }
        return TrackDurations(videoMs, audioMs)
    }

    private data class TrackDurations(val videoMs: Long, val audioMs: Long)
}

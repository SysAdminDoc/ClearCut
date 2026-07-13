package com.novacut.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ExportStoragePolicyTest {
    private val output = File("output")
    private val cache = File("cache")

    @Test
    fun exactSharedVolumeFitPassesAndOneByteShortBlocks() {
        val request = videoRequest(reversedDurationMs = 30_000L, burnSubtitles = true)
        val estimate = ExportStoragePolicy.estimate(request)
        val required = estimate.finalOutputBytes + estimate.outputTemporaryBytes +
            estimate.cacheTemporaryBytes + ExportStoragePolicy.FIXED_RESERVE_BYTES
        val exact = ExportStoragePolicy.check(request, output, cache, sharedProbe(required))
        val short = ExportStoragePolicy.check(request, output, cache, sharedProbe(required - 1L))

        assertTrue(exact.canProceed)
        assertFalse(short.canProceed)
        assertTrue(short.failure is ExportStoragePolicy.Failure.InsufficientSpace)
        assertEquals(ExportStoragePolicy.Location.SHARED, short.requirements.single().location)
    }

    @Test
    fun separateVolumesDoNotDoubleChargeButSharedDemandIsMerged() {
        val request = videoRequest(reversedDurationMs = 30_000L, burnSubtitles = true)
        val estimate = ExportStoragePolicy.estimate(request)
        val outputRequired = estimate.finalOutputBytes + estimate.outputTemporaryBytes +
            ExportStoragePolicy.FIXED_RESERVE_BYTES
        val cacheRequired = estimate.cacheTemporaryBytes + ExportStoragePolicy.FIXED_RESERVE_BYTES
        val individuallyEnough = maxOf(outputRequired, cacheRequired)
        val shared = ExportStoragePolicy.check(request, output, cache, sharedProbe(individuallyEnough))
        val separate = ExportStoragePolicy.check(request, output, cache) { path ->
            if (path == output) {
                ExportStoragePolicy.SpaceSnapshot("output-volume", outputRequired)
            } else {
                ExportStoragePolicy.SpaceSnapshot("cache-volume", cacheRequired)
            }
        }

        assertFalse(shared.canProceed)
        assertTrue(separate.canProceed)
    }

    @Test
    fun unknownDurationWithoutTargetIsBlocked() {
        val check = ExportStoragePolicy.check(
            videoRequest(durationMs = -1L), output, cache, sharedProbe(Long.MAX_VALUE)
        )

        assertFalse(check.canProceed)
        assertTrue(check.failure is ExportStoragePolicy.Failure.UnknownSize)
    }

    @Test
    fun reverseScratchUsesHighBitrateCeilingAndKnownSourceSize() {
        val byRate = ExportStoragePolicy.estimate(videoRequest(reversedDurationMs = 30_000L))
        val bySource = ExportStoragePolicy.estimate(
            videoRequest(reversedDurationMs = 30_000L).copy(
                reversedSourceBytes = 2_000_000_000L,
                reversedSourceBytesKnown = true,
            )
        )

        assertTrue(byRate.cacheTemporaryBytes >= 750_000_000L)
        assertEquals(4_000_000_000L, bySource.cacheTemporaryBytes)
    }

    @Test
    fun gifIncludesBoundedFramesAndEncoderOverhead() {
        val estimate = ExportStoragePolicy.estimate(
            videoRequest(mode = ExportStoragePolicy.Mode.GIF, durationMs = Long.MAX_VALUE)
                .copy(width = 640, height = 360, gifFrameRate = 60)
        )
        val indexedPixels = 640L * 360L * 300L

        assertTrue(estimate.finalOutputBytes > indexedPixels * 2L)
    }

    @Test
    fun contactSheetUsesActualGridDimensions() {
        val estimate = ExportStoragePolicy.estimate(
            videoRequest(mode = ExportStoragePolicy.Mode.CONTACT_SHEET)
                .copy(contactSheetClipCount = 9, contactSheetColumns = 4)
        )
        val expectedWidth = 32L + 4L * 320L + 3L * 12L
        val expectedHeight = 32L + 3L * 208L + 2L * 12L

        assertEquals(expectedWidth * expectedHeight * 4L, estimate.finalOutputBytes)
    }

    @Test
    fun audioFlagsBudgetTheCurrentVideoRendererTruthfully() {
        val audio = ExportStoragePolicy.estimate(videoRequest(mode = ExportStoragePolicy.Mode.AUDIO_ONLY))
        val stems = ExportStoragePolicy.estimate(videoRequest(mode = ExportStoragePolicy.Mode.STEMS))
        val video = ExportStoragePolicy.estimate(videoRequest())

        assertEquals(video.finalOutputBytes, audio.finalOutputBytes)
        assertEquals(video.finalOutputBytes, stems.finalOutputBytes)
    }

    @Test
    fun batchSumsPersistentFinalsAndUsesLargestSequentialScratch() {
        val first = videoRequest(reversedDurationMs = 20_000L)
        val second = videoRequest(reversedDurationMs = 40_000L, mixedRender = true)
        val firstEstimate = ExportStoragePolicy.estimate(first)
        val secondEstimate = ExportStoragePolicy.estimate(second)
        val batch = ExportStoragePolicy.checkBatch(
            listOf(first, second), output, cache, sharedProbe(Long.MAX_VALUE)
        )

        assertEquals(
            firstEstimate.finalOutputBytes + secondEstimate.finalOutputBytes,
            batch.estimate.finalOutputBytes,
        )
        assertEquals(secondEstimate.outputTemporaryBytes, batch.estimate.outputTemporaryBytes)
        assertEquals(secondEstimate.cacheTemporaryBytes, batch.estimate.cacheTemporaryBytes)
    }

    @Test
    fun arithmeticSaturatesInsteadOfWrapping() {
        val estimate = ExportStoragePolicy.estimate(
            videoRequest().copy(targetSizeBytes = Long.MAX_VALUE, burnSubtitles = true)
        )
        val check = ExportStoragePolicy.check(
            videoRequest().copy(targetSizeBytes = Long.MAX_VALUE, burnSubtitles = true),
            output,
            cache,
            sharedProbe(Long.MAX_VALUE),
        )

        assertEquals(Long.MAX_VALUE, check.requirements.single().requiredBytes)
        assertTrue(estimate.finalOutputBytes > 0L)
    }

    @Test
    fun dispatchAndRendererBoundariesCheckBeforeCreatingOutput() {
        val delegate = locate("app/src/main/java/com/novacut/editor/ui/editor/ExportDelegate.kt").readText()
        val dispatch = delegate.substringAfter("private suspend fun startExportAsync")
            .substringBefore("private fun aiDisclosureEntries")
        val engine = locate("app/src/main/java/com/novacut/editor/engine/VideoEngine.kt").readText()
        val transformer = engine.substringAfter("private suspend fun startTransformerWithPolling")
            .substringBefore("private fun requireStorageImmediatelyBeforeOutput")
        val viewModel = locate("app/src/main/java/com/novacut/editor/ui/editor/EditorViewModel.kt").readText()
        val frame = viewModel.substringAfter("fun captureFrame()")
            .substringBefore("// Project persistence")

        assertTrue(dispatch.indexOf("ExportStoragePolicy.check(") < dispatch.indexOf("markExportStarted()"))
        assertTrue(transformer.indexOf("requireStorageImmediatelyBeforeOutput") < transformer.indexOf("transformer.start"))
        assertTrue(frame.indexOf("ExportStoragePolicy.check(") < frame.indexOf("createFrameCaptureOutputFiles"))
    }

    private fun sharedProbe(available: Long) = ExportStoragePolicy.SpaceProbe {
        ExportStoragePolicy.SpaceSnapshot("shared-volume", available)
    }

    private fun locate(relative: String): File {
        var current = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        repeat(8) {
            val candidate = File(current, relative)
            if (candidate.isFile) return candidate
            current = current.parentFile ?: return@repeat
        }
        error("Could not locate $relative")
    }

    private fun videoRequest(
        mode: ExportStoragePolicy.Mode = ExportStoragePolicy.Mode.VIDEO,
        durationMs: Long = 60_000L,
        reversedDurationMs: Long = 0L,
        mixedRender: Boolean = false,
        burnSubtitles: Boolean = false,
    ) = ExportStoragePolicy.Request(
        durationMs = durationMs,
        videoBitrate = 20_000_000,
        audioBitrate = 256_000,
        mode = mode,
        reversedDurationMs = reversedDurationMs,
        mixedRender = mixedRender,
        burnSubtitles = burnSubtitles,
    )
}

package com.novacut.editor.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
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

/** Device contracts for issue #54: photo timelines export. */
@RunWith(AndroidJUnit4::class)
class StillImageTimelineInstrumentationTest {

    @Test
    fun aPhotoOnlyTimelineExportsAtItsTimelineLength() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val first = writeStill(context, "export-still-a.png", Color.RED)
        val second = writeStill(context, "export-still-b.png", Color.BLUE)
        val output = File(context.cacheDir, "still-export-${System.nanoTime()}.mp4")
        val engine = buildVideoEngine(context)
        try {
            val track = Track(
                type = TrackType.VIDEO,
                index = 0,
                clips = listOf(
                    stillClip(first, timelineStartMs = 0L, trimEndMs = 3_000L),
                    // A trimmed still must export at its trimmed length, not the 3 s source span.
                    stillClip(second, timelineStartMs = 3_000L, trimEndMs = 1_500L),
                ),
            )
            var completed = false
            var error: Exception? = null

            runBlocking {
                withTimeout(90_000L) {
                    engine.export(
                        tracks = listOf(track),
                        config = ExportConfig(resolution = Resolution.SD_480P, frameRate = 30),
                        outputFile = output,
                        onComplete = { completed = true },
                        onError = { error = it },
                    )
                }
            }

            assertNull("photo export reported an error: ${error?.message}", error)
            assertTrue("photo export did not complete", completed)
            val verification = ExportOutputVerifier.verify(
                outputFile = output,
                expectVideo = true,
                expectedDurationMs = 4_500L,
                durationToleranceMs = 250L,
            )
            assertTrue("photo export failed verification: ${verification.reason}", verification.valid)
        } finally {
            engine.release()
            first.delete()
            second.delete()
            output.delete()
        }
    }

    private fun stillClip(
        file: File,
        timelineStartMs: Long,
        trimEndMs: Long,
        sourceDurationMs: Long = 3_000L,
    ) = Clip(
        sourceUri = Uri.fromFile(file),
        sourceDurationMs = sourceDurationMs,
        timelineStartMs = timelineStartMs,
        trimStartMs = 0L,
        trimEndMs = trimEndMs,
    )

    private fun writeStill(context: Context, name: String, color: Int): File {
        val file = File(context.cacheDir, name)
        val bitmap = Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        try {
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            bitmap.recycle()
        }
        return file
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

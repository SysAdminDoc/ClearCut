package com.novacut.editor.engine

import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.novacut.editor.model.Mask
import com.novacut.editor.model.MaskPoint
import com.novacut.editor.model.MaskType
import java.io.File
import java.nio.ByteBuffer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Device acceptance for the pinned LaMa model and the editor's object-removal paths. */
@RunWith(AndroidJUnit4::class)
class InpaintingDeviceAcceptanceTest {

    @Test
    fun stillAndAudioVideoObjectRemovalProduceUsableOutputs() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val testContext = instrumentation.context
        val modelFile = File(context.filesDir, "models/inpainting/lama_dilated.onnx")
        assumeTrue(
            "Seed the pinned LaMa model through AI Tools before running this acceptance test.",
            modelFile.isFile && modelFile.length() > 90_000_000L,
        )

        val engine = InpaintingEngine(
            context = context,
            modelDownloadManager = ModelDownloadManager(context),
            ffmpegEngine = FFmpegEngine(context),
        )
        assertTrue("LaMa model was not recognized as ready", engine.isModelReady())
        assertTrue(
            "The AI Tools model path did not accept the pinned LaMa checksum",
            engine.downloadModel(),
        )

        val source = Bitmap.createBitmap(160, 120, Bitmap.Config.ARGB_8888)
        val mask = Bitmap.createBitmap(160, 120, Bitmap.Config.ARGB_8888)
        try {
            source.eraseColor(Color.rgb(28, 48, 72))
            mask.eraseColor(Color.TRANSPARENT)
            for (y in 30 until 90) {
                for (x in 45 until 115) {
                    mask.setPixel(x, y, Color.WHITE)
                }
            }

            val still = engine.inpaintFrame(source, mask)
            assertNotNull("LaMa still inference returned no output", still)
            assertEquals(160, still!!.outputBitmap.width)
            assertEquals(120, still.outputBitmap.height)
            still.outputBitmap.recycle()

            val sourceVideo = copyAsset(
                assetContext = testContext,
                outputContext = context,
                assetName = "trim-boundary.mp4",
                outputName = "inpaint-source.mp4",
            )
            val audioVideo = File(context.cacheDir, "inpaint-source-audio.mp4")
            val muxExit = FFmpegEngine(context).execute(
                "-y -i ${sourceVideo.absolutePath} " +
                    "-f lavfi -i anullsrc=channel_layout=mono:sample_rate=48000 " +
                    "-shortest -c:v copy -c:a aac ${audioVideo.absolutePath}",
            )
            assertEquals("Could not create an audio-bearing acceptance source", 0, muxExit)
            assertTrue(trackTypes(audioVideo).containsAll(setOf("video", "audio")))

            val outputVideo = File(context.cacheDir, "inpaint-output.mp4")
            val videoResult = engine.inpaintVideo(
                uri = Uri.fromFile(audioVideo),
                mask = Mask(
                    type = MaskType.RECTANGLE,
                    points = listOf(MaskPoint(0.25f, 0.25f), MaskPoint(0.75f, 0.75f)),
                ),
                outputUri = Uri.fromFile(outputVideo),
            )
            assertNotNull("LaMa video inference returned no output", videoResult)
            assertTrue("No video frames were inpainted", videoResult!!.framesProcessed > 0)
            assertTrue("Inpainted video was not written", outputVideo.isFile && outputVideo.length() > 0L)
            assertTrue(
                "Inpainted video has no usable video samples",
                hasUsableTrack(outputVideo, "video/"),
            )
            assertTrue(
                "Inpainted video lost its source audio track",
                hasUsableTrack(outputVideo, "audio/"),
            )
        } finally {
            source.recycle()
            mask.recycle()
        }
    }

    private fun copyAsset(
        assetContext: android.content.Context,
        outputContext: android.content.Context,
        assetName: String,
        outputName: String,
    ): File {
        val output = File(outputContext.cacheDir, outputName)
        assetContext.assets.open(assetName).use { input ->
            output.outputStream().use { outputStream -> input.copyTo(outputStream) }
        }
        return output
    }

    private fun trackTypes(file: File): Set<String> {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            buildSet {
                repeat(extractor.trackCount) { index ->
                    extractor.getTrackFormat(index)
                        .getString(android.media.MediaFormat.KEY_MIME)
                        ?.substringBefore('/')
                        ?.let(::add)
                }
            }
        } finally {
            extractor.release()
        }
    }

    private fun hasUsableTrack(file: File, mimePrefix: String): Boolean {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                val mime = format
                    .getString(MediaFormat.KEY_MIME)
                    ?: continue
                if (!mime.startsWith(mimePrefix)) continue
                extractor.selectTrack(index)
                val hasSample = extractor.sampleTime >= 0L && runCatching {
                    extractor.readSampleData(ByteBuffer.allocate(64 * 1024), 0) > 0
                }.getOrDefault(false)
                val hasDuration = runCatching {
                    format.getLong(MediaFormat.KEY_DURATION) > 0L
                }.getOrDefault(false)
                return hasSample || hasDuration
            }
            false
        } finally {
            extractor.release()
        }
    }
}

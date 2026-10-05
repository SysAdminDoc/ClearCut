package com.novacut.editor.engine

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.Clock
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.transformer.AssetLoader
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultAssetLoaderFactory
import androidx.media3.transformer.DefaultDecoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Puts a decoder that can't start ahead of every real one, the way an OEM codec that
 * over-claims a format would sit at the top of the list, and transcodes a real clip.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(UnstableApi::class)
class Media3DecoderFallbackInstrumentationTest {

    @Test
    fun anExportSurvivesADecoderThatWontStartAndSaysSo() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val notes = CopyOnWriteArrayList<String>()

        val outcome = transcode(context, Media3DecoderFallback.assetLoaderFactory(context, deadFirst, notes::add))

        assertNull("export failed: ${outcome.error?.errorCodeName}", outcome.error)
        assertTrue(outcome.outputBytes > 0L)
        assertEquals(notes.toString(), 1, notes.size)
        assertTrue(notes.single(), notes.single().contains(DEAD_DECODER))
        assertTrue(notes.single(), notes.single().startsWith("Media3 video decoder fallback applied: "))
    }

    @Test
    fun withoutFallbackTheSameSourceFailsAtTheDeadDecoder() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val noFallback = DefaultAssetLoaderFactory(
            context,
            DefaultDecoderFactory.Builder(context).setMediaCodecSelector(deadFirst).build(),
            Clock.DEFAULT,
            null,
        )

        val outcome = transcode(context, noFallback)

        // MediaCodec.createByCodecName rejects an unknown name with IllegalArgumentException,
        // which Media3's DefaultCodec reports as an unsupported decoding format.
        assertEquals(ExportException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED, outcome.error?.errorCode)
        assertEquals(DEAD_DECODER, outcome.error?.codecInfo?.name)
    }

    private class Outcome(val outputBytes: Long, val error: ExportException?)

    private fun transcode(context: Context, assetLoaderFactory: AssetLoader.Factory): Outcome {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val source = File(context.cacheDir, "decoder-fallback-source-${System.nanoTime()}.mp4")
        val output = File(context.cacheDir, "decoder-fallback-output-${System.nanoTime()}.mp4")
        instrumentation.context.assets.open("trim-boundary.mp4").use { input ->
            source.outputStream().use(input::copyTo)
        }
        val done = CountDownLatch(1)
        val failure = AtomicReference<ExportException?>()
        // The asset is 320x240. Halving it forces a real decode and re-encode; a Presentation
        // at the source height is a no-op that Media3 drops, and the clip is only remuxed.
        val item = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(source)))
            .setRemoveAudio(true)
            .setEffects(Effects(emptyList(), listOf(Presentation.createForHeight(120))))
            .build()
        instrumentation.runOnMainSync {
            Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAssetLoaderFactory(assetLoaderFactory)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        done.countDown()
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException,
                    ) {
                        failure.set(exportException)
                        done.countDown()
                    }
                })
                .build()
                .start(item, output.absolutePath)
        }
        try {
            assertTrue("transcode timed out", done.await(60, TimeUnit.SECONDS))
            return Outcome(output.length(), failure.get())
        } finally {
            source.delete()
            output.delete()
        }
    }

    private companion object {
        const val DEAD_DECODER = "c2.clearcut.unavailable.decoder"

        /** Lists a decoder with real capabilities but no such codec ahead of each real video decoder. */
        val deadFirst = MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
            val real = MediaCodecSelector.DEFAULT.getDecoderInfos(
                mimeType,
                requiresSecureDecoder,
                requiresTunnelingDecoder,
            )
            if (!MimeTypes.isVideo(mimeType)) return@MediaCodecSelector real
            real.flatMap { info ->
                listOf(
                    MediaCodecInfo.newInstance(
                        DEAD_DECODER,
                        info.mimeType,
                        info.codecMimeType,
                        info.capabilities,
                        info.hardwareAccelerated,
                        info.softwareOnly,
                        info.vendor,
                        false,
                        false,
                    ),
                    info,
                )
            }
        }
    }
}

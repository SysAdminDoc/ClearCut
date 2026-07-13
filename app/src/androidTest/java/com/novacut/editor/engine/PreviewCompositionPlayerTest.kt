package com.novacut.editor.engine

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.SurfaceTexture
import android.media.ImageReader
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.view.PixelCopy
import android.view.Surface
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.Size
import androidx.media3.common.util.ExperimentalApi
import androidx.media3.effect.MultipleInputVideoGraph
import androidx.media3.transformer.Composition
import androidx.media3.transformer.CompositionPlayer
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.novacut.editor.model.BlendMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(ExperimentalApi::class)
class PreviewCompositionPlayerTest {
    @Test
    fun trimmedAndExtendedCompositionsDecodeTheirBoundaryFrames() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val source = File(context.cacheDir, "trim-boundary.mp4")
        instrumentation.context.assets.open("trim-boundary.mp4").use { input ->
            source.outputStream().use(input::copyTo)
        }
        val ready = AtomicReference(CountDownLatch(1))
        val failure = AtomicReference<PlaybackException?>()
        val playerRef = AtomicReference<CompositionPlayer>()
        val imageThread = HandlerThread("trim-boundary-frames").apply { start() }
        val imageReader = ImageReader.newInstance(64, 64, PixelFormat.RGBA_8888, 3)
        val latestColor = AtomicReference<Int?>()
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) ready.get().countDown()
            }

            override fun onPlayerError(error: PlaybackException) {
                failure.set(error)
                ready.get().countDown()
            }
        }
        imageReader.setOnImageAvailableListener({ reader ->
            reader.acquireLatestImage()?.use { image ->
                val plane = image.planes.single()
                val offset = 32 * plane.rowStride + 32 * plane.pixelStride
                val buffer = plane.buffer
                latestColor.set(
                    Color.rgb(
                        buffer.get(offset).toInt() and 0xff,
                        buffer.get(offset + 1).toInt() and 0xff,
                        buffer.get(offset + 2).toInt() and 0xff,
                    )
                )
            }
        }, Handler(imageThread.looper))

        try {
            instrumentation.runOnMainSync {
                val player = CompositionPlayer.Builder(context)
                    .setVideoGraphFactory(MultipleInputVideoGraph.Factory())
                    .build()
                player.addListener(listener)
                player.setVideoSurface(imageReader.surface, Size(64, 64))
                player.setComposition(videoComposition(source, 500L, 1_000L))
                player.prepare()
                playerRef.set(player)
            }
            assertTrue("trimmed preview did not reach READY", ready.get().await(20, TimeUnit.SECONDS))
            assertNull("trimmed preview failed: ${failure.get()?.message}", failure.get())
            val trimmedColor = renderLatestColor(instrumentation, playerRef.get(), latestColor)
            assertTrue(
                "trimmed center rgb=${Color.red(trimmedColor)},${Color.green(trimmedColor)},${Color.blue(trimmedColor)}",
                Color.blue(trimmedColor) > 200 && Color.red(trimmedColor) < 40
            )

            val extendedReady = CountDownLatch(1)
            ready.set(extendedReady)
            instrumentation.runOnMainSync {
                playerRef.get().release()
                val player = CompositionPlayer.Builder(context)
                    .setVideoGraphFactory(MultipleInputVideoGraph.Factory())
                    .build()
                player.addListener(listener)
                player.setVideoSurface(imageReader.surface, Size(64, 64))
                player.setComposition(videoComposition(source, 0L, 1_000L), 0L)
                player.prepare()
                playerRef.set(player)
            }
            assertTrue("extended preview did not reach READY", extendedReady.await(20, TimeUnit.SECONDS))
            assertNull("extended preview failed: ${failure.get()?.message}", failure.get())
            val extendedColor = renderLatestColor(instrumentation, playerRef.get(), latestColor)
            assertTrue(
                "extended center rgb=${Color.red(extendedColor)},${Color.green(extendedColor)},${Color.blue(extendedColor)}",
                Color.red(extendedColor) > 200 && Color.blue(extendedColor) < 40
            )
        } finally {
            instrumentation.runOnMainSync { playerRef.get()?.release() }
            imageReader.close()
            imageThread.quitSafely()
            source.delete()
        }
    }

    @Test
    fun gapOnlyCompositionReplacesStaleVisualContent() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val ready = CountDownLatch(1)
        val failure = AtomicReference<PlaybackException?>()
        val playerRef = AtomicReference<CompositionPlayer>()
        val imageReader = ImageReader.newInstance(16, 16, PixelFormat.RGBA_8888, 2)
        try {
            instrumentation.runOnMainSync {
                val player = CompositionPlayer.Builder(context)
                    .setVideoGraphFactory(MultipleInputVideoGraph.Factory())
                    .build()
                player.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) ready.countDown()
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        failure.set(error)
                        ready.countDown()
                    }
                })
                val gap = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_VIDEO))
                    .addGap(500_000L)
                    .build()
                player.setVideoSurface(imageReader.surface, Size(16, 16))
                player.setComposition(Composition.Builder(listOf(gap)).build())
                player.prepare()
                playerRef.set(player)
            }
            assertTrue("gap-only preview did not reach READY", ready.await(10, TimeUnit.SECONDS))
            assertNull("gap-only preview failed: ${failure.get()?.message}", failure.get())
            instrumentation.runOnMainSync { assertEquals(500L, playerRef.get().duration) }
        } finally {
            instrumentation.runOnMainSync { playerRef.get()?.release() }
            imageReader.close()
        }
    }

    @Test
    fun twoVisualSequencesPrepareAndSeekOnOneAbsoluteTimeline() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val lower = createStill(File(context.cacheDir, "preview-lower.png"), Color.BLUE)
        val upper = createStill(File(context.cacheDir, "preview-upper.png"), Color.RED)
        val ready = CountDownLatch(1)
        val failure = AtomicReference<PlaybackException?>()
        val playerRef = AtomicReference<CompositionPlayer>()
        val imageThread = HandlerThread("preview-composition-images").apply { start() }
        val surfaceTexture = SurfaceTexture(false).apply { setDefaultBufferSize(64, 64) }
        val surface = Surface(surfaceTexture)

        try {
            instrumentation.runOnMainSync {
                val player = CompositionPlayer.Builder(context)
                    .setVideoGraphFactory(MultipleInputVideoGraph.Factory())
                    .build()
                player.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) ready.countDown()
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        failure.set(error)
                        ready.countDown()
                    }
                })
                player.setVideoSurface(surface, Size(64, 64))
                player.setComposition(twoLayerComposition(lower, upper))
                player.prepare()
                playerRef.set(player)
            }

            assertTrue("multi-input preview did not reach READY", ready.await(20, TimeUnit.SECONDS))
            assertNull("multi-input preview failed: ${failure.get()?.message}", failure.get())
            instrumentation.runOnMainSync {
                val player = playerRef.get()
                assertEquals(1_000L, player.duration)
                player.play()
            }
            Thread.sleep(300L)
            val copied = CountDownLatch(1)
            val copyResult = AtomicReference<Int>()
            val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
            PixelCopy.request(surface, bitmap, { result ->
                copyResult.set(result)
                copied.countDown()
            }, Handler(imageThread.looper))
            assertTrue("multi-input preview produced no composited frame", copied.await(10, TimeUnit.SECONDS))
            assertEquals(PixelCopy.SUCCESS, copyResult.get())
            val color = bitmap.getPixel(32, 32)
            bitmap.recycle()
            val colorMessage = "center rgb=${Color.red(color)},${Color.green(color)},${Color.blue(color)}"
            assertTrue(colorMessage, Color.red(color) in 110..150)
            assertTrue(colorMessage, Color.green(color) in 0..12)
            assertTrue(colorMessage, Color.blue(color) in 110..150)
            instrumentation.runOnMainSync {
                val player = playerRef.get()
                player.pause()
                player.seekTo(600L)
            }
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                assertTrue(playerRef.get().currentPosition in 550L..650L)
            }
        } finally {
            instrumentation.runOnMainSync { playerRef.get()?.release() }
            surface.release()
            surfaceTexture.release()
            imageThread.quitSafely()
            lower.delete()
            upper.delete()
        }
    }

    private fun twoLayerComposition(lower: File, upper: File): Composition {
        val sequences = listOf(sequence(upper), sequence(lower))
        return Composition.Builder(sequences)
            .setVideoCompositorSettings(
                ClearCutVideoCompositorSettings(
                    outputWidth = 64,
                    outputHeight = 64,
                    layers = listOf(
                        ClearCutCompositorLayer(0, "upper", 1, 0.5f, BlendMode.NORMAL),
                        ClearCutCompositorLayer(1, "lower", 0, 1f, BlendMode.NORMAL),
                    ),
                )
            )
            .build()
    }

    private fun sequence(file: File): EditedMediaItemSequence {
        val mediaItem = MediaItem.Builder()
            .setUri(Uri.fromFile(file))
            .setImageDurationMs(1_000L)
            .build()
        val item = EditedMediaItem.Builder(mediaItem)
            .setDurationUs(1_000_000L)
            .build()
        return EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_VIDEO))
            .addItem(item)
            .build()
    }

    private fun videoComposition(file: File, trimStartMs: Long, trimEndMs: Long): Composition {
        val mediaItem = MediaItem.Builder()
            .setUri(Uri.fromFile(file))
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(trimStartMs)
                    .setEndPositionMs(trimEndMs)
                    .build()
            )
            .build()
        val sequence = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_VIDEO))
            .addItem(
                EditedMediaItem.Builder(mediaItem)
                    .setDurationUs(1_000_000L)
                    .build()
            )
            .build()
        return Composition.Builder(listOf(sequence)).build()
    }

    private fun renderLatestColor(
        instrumentation: android.app.Instrumentation,
        player: CompositionPlayer,
        latestColor: AtomicReference<Int?>,
    ): Int {
        latestColor.set(null)
        instrumentation.runOnMainSync {
            player.seekTo(0L)
            player.play()
        }
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10L)
        while (latestColor.get() == null && System.nanoTime() < deadline) {
            Thread.sleep(20L)
        }
        instrumentation.runOnMainSync { player.pause() }
        return requireNotNull(latestColor.get()) { "preview produced no decoded frame" }
    }

    private fun createStill(file: File, color: Int): File {
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        return try {
            bitmap.eraseColor(color)
            file.outputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
            file
        } finally {
            bitmap.recycle()
        }
    }
}

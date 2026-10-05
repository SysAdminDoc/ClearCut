package com.novacut.editor.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.media.ImageReader
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import androidx.media3.common.AudioAttributes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.Size
import androidx.media3.transformer.CompositionPlayer
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Device contracts for issue #54: photo timelines export, and preview survives its own end. */
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

    @Test
    fun previewPlaysAndScrubsAgainAfterTheTimelineEnds() = withPreviewSession { session ->
        session.prepare(startPositionMs = 0L, play = true)
        session.awaitEnded("first playback")

        // The editor's loop and Play-at-end both restart from the top; each pass
        // needs a player whose compositor has not already seen end of input.
        repeat(3) { pass ->
            session.playFromTop()
            session.awaitEnded("replay ${pass + 1}")
        }

        session.seekTo(500L)
        session.awaitReady("scrub after the end")
    }

    @Test
    fun previewOpenedWithThePlayheadAtTheEndPlaysFromTheTop() = withPreviewSession { session ->
        // A project reopens at its saved playhead. Parked at the end, the sequences
        // signal end of input without the player ever reporting it ended.
        session.prepare(startPositionMs = PREVIEW_TIMELINE_MS, play = false)
        session.awaitReady("prepare at the end")

        session.playFromTop()
        session.awaitEnded("playback from the top")
    }

    private fun withPreviewSession(block: (PreviewSession) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val video = File(context.cacheDir, "replay-source.mp4")
        instrumentation.context.assets.open("trim-boundary.mp4").use { input ->
            video.outputStream().use(input::copyTo)
        }
        val still = writeStill(context, "replay-still.png", Color.GREEN)
        val track = Track(
            type = TrackType.VIDEO,
            index = 0,
            clips = listOf(
                Clip(
                    sourceUri = Uri.fromFile(video),
                    sourceDurationMs = 1_000L,
                    timelineStartMs = 0L,
                ),
                stillClip(still, timelineStartMs = 1_000L, trimEndMs = 1_000L, sourceDurationMs = 1_000L),
            ),
        )
        val session = PreviewSession(buildVideoEngine(context), track)
        try {
            block(session)
        } finally {
            session.release()
            video.delete()
            still.delete()
        }
    }

    /** Drives a [VideoEngine] preview the way the editor does, on the main thread. */
    private class PreviewSession(private val engine: VideoEngine, private val track: Track) {
        private val instrumentation = InstrumentationRegistry.getInstrumentation()
        private val failure = AtomicReference<PlaybackException?>()
        private val ended = AtomicReference(CountDownLatch(1))
        private val ready = AtomicReference(CountDownLatch(1))
        private val imageThread = HandlerThread("still-replay-frames").apply { start() }
        private val imageReader = ImageReader.newInstance(64, 64, PixelFormat.RGBA_8888, 3).apply {
            setOnImageAvailableListener({ reader ->
                reader.acquireLatestImage()?.close()
            }, Handler(imageThread.looper))
        }
        private var boundPlayer: Player? = null

        init {
            val listener = object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) ready.get().countDown()
                    if (playbackState == Player.STATE_ENDED) {
                        ready.get().countDown()
                        ended.get().countDown()
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    failure.set(error)
                    ready.get().countDown()
                    ended.get().countDown()
                }
            }
            instrumentation.runOnMainSync { engine.setPlayerListener(listener) }
        }

        fun prepare(startPositionMs: Long, play: Boolean) = onMain {
            bindSurface()
            engine.prepareTimeline(listOf(track), startPositionMs = startPositionMs)
            if (play) engine.play()
        }

        fun playFromTop() = onMain {
            engine.playFromTimelinePosition(0L, restartSession = true)
            if (bindSurface()) {
                // The replacement asked for audio focus before bindSurface() could
                // turn focus handling off, and the denial dropped its play request.
                engine.pause()
                engine.play()
            }
        }

        fun seekTo(positionMs: Long) = onMain {
            engine.seekTo(positionMs)
            bindSurface()
        }

        fun awaitEnded(step: String) = await(ended, step, "end")

        fun awaitReady(step: String) = await(ready, step, "settle")

        private fun await(latch: AtomicReference<CountDownLatch>, step: String, what: String) {
            assertTrue("$step did not $what", latch.get().await(30, TimeUnit.SECONDS))
            assertNull("$step failed: ${failure.get()?.message}", failure.get())
        }

        private fun onMain(action: () -> Unit) {
            ended.set(CountDownLatch(1))
            ready.set(CountDownLatch(1))
            instrumentation.runOnMainSync(action)
        }

        /** Returns true when the engine handed out a new player instance. */
        private fun bindSurface(): Boolean {
            val player = engine.getPlayer() as CompositionPlayer
            if (player === boundPlayer) return false
            // Android 15 denies audio focus to a process with no foreground
            // activity, which would hold playback forever under instrumentation.
            player.setAudioAttributes(AudioAttributes.DEFAULT, false)
            player.setVideoSurface(imageReader.surface, Size(64, 64))
            boundPlayer = player
            return true
        }

        fun release() {
            instrumentation.runOnMainSync { engine.release() }
            imageReader.close()
            imageThread.quitSafely()
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

    private companion object {
        const val PREVIEW_TIMELINE_MS = 2_000L
    }
}

package com.novacut.editor.engine

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.novacut.editor.model.Caption
import com.novacut.editor.model.SubtitleFormat
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every FFmpeg pass that re-encodes video must hand back a file with that video in it.
 * MediaCodec encoders on some devices (and the x86_64 emulator) can finish a session
 * with zero frames and exit 0, which used to leave an audio-only file in place.
 */
@RunWith(AndroidJUnit4::class)
class FFmpegIntermediateEncodeInstrumentationTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val target = instrumentation.targetContext
    private val workDir = File(target.cacheDir, "ffmpeg-intermediate-${System.nanoTime()}")
    private val ffmpeg = FFmpegEngine(target)
    private lateinit var source: File

    @Before
    fun setUp() {
        assumeTrue("FFmpeg is not packaged for this ABI", ffmpeg.isAvailable())
        workDir.mkdirs()
        source = File(workDir, "export-30s-av.mp4")
        instrumentation.context.assets.open("export-30s-av.mp4").use { input ->
            source.outputStream().use(input::copyTo)
        }
    }

    @After
    fun tearDown() {
        workDir.deleteRecursively()
    }

    @Test
    fun burnedInCaptionsKeepTheVideo() = runBlocking {
        val ass = File(workDir, "captions.ass")
        assertTrue(
            SubtitleExporter.export(
                listOf(Caption(text = "Burned in", startTimeMs = 500L, endTimeMs = 4_000L)),
                SubtitleFormat.ASS,
                ass,
            )
        )
        val output = File(workDir, "burned.mp4")

        assertTrue(ffmpeg.burnSubtitles(source, ass, output))
        assertPlayable(output, expectedDurationMs = 30_000L)
    }

    @Test
    fun aConstantFrameRateIntermediateKeepsTheVideo() = runBlocking {
        val output = File(workDir, "cfr.mp4")

        assertTrue(ffmpeg.normalizeVideoFrameRate(Uri.fromFile(source), output, frameRate = 30))
        assertPlayable(output, expectedDurationMs = 30_000L)
    }

    @Test
    fun aReversedClipKeepsTheVideo() = runBlocking {
        val output = File(workDir, "reversed.mp4")

        assertTrue(ffmpeg.reverseClipToFile(Uri.fromFile(source), output, trimStartMs = 0L, trimEndMs = 5_000L))
        assertPlayable(output, expectedDurationMs = 5_000L)
    }

    @Test
    fun reversingPastTheEndOfTheSoundKeepsTheVideo() = runBlocking {
        // Six seconds of video whose sound stops at two: the reversed range has none,
        // and no encoder can add it, so a retry must not throw the video away.
        val shortAudio = File(workDir, "audio-ends-early.mp4")
        instrumentation.context.assets.open("audio-ends-early.mp4").use { input ->
            shortAudio.outputStream().use(input::copyTo)
        }
        val output = File(workDir, "reversed-silent-range.mp4")

        assertTrue(ffmpeg.reverseClipToFile(Uri.fromFile(shortAudio), output, trimStartMs = 3_000L, trimEndMs = 6_000L, hasAudio = true))
        assertPlayable(output, expectedDurationMs = 3_000L, expectAudio = false)
    }

    private fun assertPlayable(output: File, expectedDurationMs: Long, expectAudio: Boolean = true) {
        val verification = ExportOutputVerifier.verify(
            outputFile = output,
            expectVideo = true,
            expectAudio = expectAudio,
            expectedDurationMs = expectedDurationMs,
        )
        assertTrue("${output.name} failed verification: ${verification.reason}", verification.valid)
    }
}

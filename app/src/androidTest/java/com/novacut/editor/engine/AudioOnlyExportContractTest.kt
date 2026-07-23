package com.novacut.editor.engine

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.novacut.editor.engine.segmentation.SegmentationEngine
import com.novacut.editor.model.Clip
import com.novacut.editor.model.ExportConfig
import com.novacut.editor.model.Track
import com.novacut.editor.model.TrackType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * On-device contract for the audio-only export path. Proves that
 * [VideoEngine.exportAudio] produces a track-verified `.m4a` (an audio track,
 * no video track) from a real encoded source, and that
 * [ExportOutputVerifier] enforces that structure.
 */
@RunWith(AndroidJUnit4::class)
class AudioOnlyExportContractTest {

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

    @Test
    fun audioOnlyExportProducesTrackVerifiedM4a() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = File(context.cacheDir, "audio-src-${System.nanoTime()}.mp4")
        val output = File(context.cacheDir, "audio-out-${System.nanoTime()}.m4a")
        writeAacToneMp4(source, durationMs = 1200L)
        try {
            val clip = Clip(
                sourceUri = Uri.fromFile(source),
                sourceDurationMs = 1200L,
                timelineStartMs = 0L,
                trimStartMs = 0L,
                trimEndMs = 1200L,
            )
            val track = Track(type = TrackType.AUDIO, index = 0, clips = listOf(clip))
            val config = ExportConfig(exportAudioOnly = true)

            var completed = false
            var error: Exception? = null
            runBlocking {
                withTimeout(60_000L) {
                    buildVideoEngine(context).exportAudio(
                        tracks = listOf(track),
                        config = config,
                        outputFile = output,
                        onComplete = { completed = true },
                        onError = { error = it },
                    )
                }
            }

            assertNull("audio export reported an error: ${error?.message}", error)
            assertTrue("audio export did not complete", completed)

            val verification = ExportOutputVerifier.verify(
                outputFile = output,
                expectVideo = false,
                expectAudio = true,
            )
            assertTrue("verifier rejected the m4a: ${verification.reason}", verification.valid)
            assertTrue("m4a has no audio track", verification.hasAudio)
            assertFalse("audio-only export leaked a video track", verification.hasVideo)
            assertTrue("m4a has no duration", verification.durationMs > 0L)
        } finally {
            source.delete()
            output.delete()
        }
    }

    @Test
    fun verifierRejectsVideoOutputWhenAudioOnlyExpected() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val videoFixture = File(context.cacheDir, "video-fixture-${System.nanoTime()}.mp4")
        InstrumentationRegistry.getInstrumentation().context.assets.open("trim-boundary.mp4").use { input ->
            videoFixture.outputStream().use(input::copyTo)
        }
        try {
            val result = ExportOutputVerifier.verify(
                outputFile = videoFixture,
                expectVideo = false,
                expectAudio = true,
            )
            assertFalse("verifier accepted a video file as audio-only", result.valid)
            assertTrue(
                "unexpected rejection reason: ${result.reason}",
                result.reason?.contains("video track") == true,
            )
        } finally {
            videoFixture.delete()
        }
    }

    /**
     * Encode a short mono AAC sine tone into an MP4 so the export has a real
     * decodable audio source. Deterministic — no external asset required.
     */
    private fun writeAacToneMp4(file: File, durationMs: Long) {
        val sampleRate = 44100
        val channelCount = 1
        val format = MediaFormat.createAudioFormat(
            MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channelCount
        ).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 96_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16_384)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        val muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var trackIndex = -1
        var muxerStarted = false

        val totalSamples = (sampleRate * durationMs / 1000L).toInt()
        val pcmBytes = ByteArray(totalSamples * 2)
        var b = 0
        for (i in 0 until totalSamples) {
            val sample = (Math.sin(2.0 * Math.PI * 440.0 * i / sampleRate) * 8000.0).toInt()
            pcmBytes[b++] = (sample and 0xff).toByte()
            pcmBytes[b++] = ((sample shr 8) and 0xff).toByte()
        }

        var inputOffset = 0
        var sawInputEnd = false
        var sawOutputEnd = false
        val bufferInfo = MediaCodec.BufferInfo()
        try {
            while (!sawOutputEnd) {
                if (!sawInputEnd) {
                    val inIndex = codec.dequeueInputBuffer(10_000L)
                    if (inIndex >= 0) {
                        val inBuf = codec.getInputBuffer(inIndex)!!
                        inBuf.clear()
                        val remaining = pcmBytes.size - inputOffset
                        val ptsUs = (inputOffset / 2).toLong() * 1_000_000L / sampleRate
                        if (remaining <= 0) {
                            codec.queueInputBuffer(
                                inIndex, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            sawInputEnd = true
                        } else {
                            val chunk = minOf(inBuf.capacity(), remaining)
                            inBuf.put(pcmBytes, inputOffset, chunk)
                            codec.queueInputBuffer(inIndex, 0, chunk, ptsUs, 0)
                            inputOffset += chunk
                        }
                    }
                }
                val outIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000L)
                if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    trackIndex = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                    muxerStarted = true
                } else if (outIndex >= 0) {
                    val outBuf = codec.getOutputBuffer(outIndex)!!
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        bufferInfo.size = 0
                    }
                    if (bufferInfo.size > 0 && muxerStarted) {
                        outBuf.position(bufferInfo.offset)
                        outBuf.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(trackIndex, outBuf, bufferInfo)
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        sawOutputEnd = true
                    }
                }
            }
        } finally {
            runCatching { codec.stop() }
            codec.release()
            if (muxerStarted) runCatching { muxer.stop() }
            muxer.release()
        }
    }
}

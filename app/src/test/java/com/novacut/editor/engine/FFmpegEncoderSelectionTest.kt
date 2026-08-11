package com.novacut.editor.engine

import android.content.ContextWrapper
import com.novacut.editor.engine.FFmpegEngine.H264Encoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ClearCut's H.264 export is produced by Media3 Transformer through Android
 * MediaCodec and never touches FFmpeg. The FFmpeg-side passes (reverse
 * pre-render, subtitle burn-in, inpainted frame assembly) still need *an*
 * encoder, and which one they get decides whether the shipped artifact carries
 * a GPL dependency.
 *
 * These pin the selection contract so swapping the vendored AAR cannot silently
 * change what those passes encode with.
 */
class FFmpegEncoderSelectionTest {

    private val engine = FFmpegEngine(object : ContextWrapper(null) {})

    @Test
    fun theLicenceNeutralHardwareEncoderIsPreferredOverGpl() {
        val order = H264Encoder.entries.toList()

        assertEquals(
            "MediaCodec must be tried before libx264 so an LGPL build never falls back to GPL",
            H264Encoder.MEDIACODEC,
            order.first()
        )
        assertTrue(order.indexOf(H264Encoder.MEDIACODEC) < order.indexOf(H264Encoder.X264))
    }

    @Test
    fun onlyX264IsMarkedGpl() {
        assertTrue(H264Encoder.X264.isGpl)
        assertFalse(H264Encoder.MEDIACODEC.isGpl)
        assertFalse(H264Encoder.MPEG4.isGpl)
    }

    @Test
    fun everyEncoderHasALicenceNeutralFallbackBelowIt() {
        // MPEG4 is FFmpeg's own LGPL encoder and is present in every build, so
        // dropping libx264 can never leave the intermediate passes with nothing.
        assertEquals(H264Encoder.MPEG4, H264Encoder.entries.last())
        assertFalse(H264Encoder.entries.last().isGpl)
    }

    @Test
    fun crfIsOnlySentToEncodersThatUnderstandIt() {
        // -crf is x264/x265 rate control. MediaCodec and the native MPEG-4
        // encoder ignore it, so sending only -crf would silently yield a
        // default-bitrate (low quality) intermediate on a non-GPL build.
        val x264 = engine.intermediateQualityArgs(H264Encoder.X264)
        assertTrue(x264.containsAll(listOf("-crf", "18")))

        for (encoder in listOf(H264Encoder.MEDIACODEC, H264Encoder.MPEG4)) {
            val args = engine.intermediateQualityArgs(encoder)
            assertFalse("$encoder must not receive -crf", args.contains("-crf"))
            assertTrue("$encoder must receive an explicit bitrate", args.contains("-b:v"))
        }
    }

    @Test
    fun everyEncoderGetsSomeQualitySetting() {
        for (encoder in H264Encoder.entries) {
            assertTrue(
                "$encoder must not fall back to the container default bitrate",
                engine.intermediateQualityArgs(encoder).isNotEmpty()
            )
        }
    }

    @Test
    fun unusableHardwareOutputFallsBackToMpeg4() {
        assertEquals(
            listOf(H264Encoder.MEDIACODEC, H264Encoder.MPEG4),
            engine.encoderAttempts(H264Encoder.MEDIACODEC),
        )
        assertEquals(
            listOf(H264Encoder.MPEG4),
            engine.encoderAttempts(H264Encoder.MPEG4),
        )
    }

    @Test
    fun ffmpegNamesMatchTheEncoderIdsFfmpegActuallyReports() {
        assertEquals("h264_mediacodec", H264Encoder.MEDIACODEC.ffmpegName)
        assertEquals("libx264", H264Encoder.X264.ffmpegName)
        assertEquals("mpeg4", H264Encoder.MPEG4.ffmpegName)
    }
}

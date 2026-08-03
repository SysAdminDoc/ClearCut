package com.novacut.editor.engine

import java.io.File
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeProcessingPolicyTest {

    @Test
    fun validateVideoFile_allowsAviAfterFfmpegUpgrade() {
        val input = File.createTempFile("clearcut-risk-", ".avi").apply {
            writeBytes(byteArrayOf(0x52, 0x49, 0x46, 0x46))
        }

        try {
            assertNull(NativeProcessingPolicy.validateVideoFile(input, "concat"))
        } finally {
            input.delete()
        }
    }

    @Test
    fun validateVideoPath_allowsAviAfterFfmpegUpgrade() {
        assertNull(NativeProcessingPolicy.validateVideoPath(
            "/storage/emulated/0/Movies/source.AVI?token=abc",
            "extractAudioToWav"
        ))
    }

    @Test
    fun validateVideoPath_allowsSupportedNativeVideoAndAudioInputs() {
        assertNull(NativeProcessingPolicy.validateVideoPath("clip.mp4", "streamCopyTrim"))
        assertNull(NativeProcessingPolicy.validateVideoPath("voice.wav", "extractAudioToWav"))
    }

    @Test
    fun validateAudioFile_allowsInternalRawPcmEncodeInput() {
        val input = File.createTempFile("clearcut-clean-", ".pcm").apply {
            writeBytes(byteArrayOf(0, 1, 2, 3))
        }

        try {
            assertNull(NativeProcessingPolicy.validateAudioFile(input, "encodePcm16leToM4a"))
        } finally {
            input.delete()
        }
    }

    @Test
    fun validateVideoPath_rejectsUnknownExtension() {
        val violation = NativeProcessingPolicy.validateVideoPath("clip.movpkg", "streamCopyTrim")

        assertNotNull(violation)
        assertTrue(violation is NativeProcessingPolicy.PolicyViolation.UnsupportedFormat)
    }

    @Test
    fun unsupportedNativeFailure_namesDisabledDecoder() {
        val violation = NativeProcessingPolicy.unsupportedNativeFailure(
            "Unknown decoder 'tdsc' for input stream #0:0",
            "FFmpeg",
        )

        assertNotNull(violation)
        assertTrue(violation!!.userMessage().contains("TDSC/AVI video"))
    }

    @Test
    fun unsupportedNativeFailure_namesDisabledDemuxer() {
        val violation = NativeProcessingPolicy.unsupportedNativeFailure(
            "Unknown input format: 'vobsub'",
            "FFmpeg",
        )

        assertNotNull(violation)
        assertTrue(violation!!.diagnosticMessage().contains("VobSub subtitles"))
    }

    @Test
    fun unsupportedExtension_usesDisabledFormatName() {
        val violation = NativeProcessingPolicy.validateVideoPath("clip.aax", "streamCopyTrim")

        assertNotNull(violation)
        assertTrue(violation!!.userMessage().contains("AAX audio"))
    }

    @Test
    fun unsupportedNativeFailure_ignoresUnrelatedFailures() {
        assertNull(
            NativeProcessingPolicy.unsupportedNativeFailure(
                "Invalid argument while opening output file",
                "FFmpeg",
            )
        )
    }

}

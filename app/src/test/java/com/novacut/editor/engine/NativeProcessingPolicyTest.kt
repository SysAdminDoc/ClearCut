package com.novacut.editor.engine

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeProcessingPolicyTest {

    @Test
    fun validateVideoFile_blocksAviForNativeDecoderRisk() {
        val input = File.createTempFile("clearcut-risk-", ".avi").apply {
            writeBytes(byteArrayOf(0x52, 0x49, 0x46, 0x46))
        }

        try {
            val violation = NativeProcessingPolicy.validateVideoFile(input, "concat")

            assertTrue(violation is NativeProcessingPolicy.PolicyViolation.BlockedNativeDecoderInput)
            val blocked = violation as NativeProcessingPolicy.PolicyViolation.BlockedNativeDecoderInput
            assertEquals(NativeProcessingPolicy.FFMPEG_MAGIC_YUV_ADVISORY, blocked.advisory)
            assertEquals("avi", blocked.detectedExtension)
            assertTrue(blocked.userMessage().contains("Convert it to MP4 or WebM"))
            assertTrue(blocked.diagnosticMessage().contains("CVE-2026-8461"))
            assertTrue(blocked.diagnosticMessage().contains("FFmpeg 8.1.2"))
        } finally {
            input.delete()
        }
    }

    @Test
    fun validateVideoPath_blocksAviBeforeFfmpegExecution() {
        val violation = NativeProcessingPolicy.validateVideoPath(
            "/storage/emulated/0/Movies/source.AVI?token=abc",
            "extractAudioToWav"
        )

        assertTrue(violation is NativeProcessingPolicy.PolicyViolation.BlockedNativeDecoderInput)
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
    fun isBlockedNativeDecoderInput_normalizesMimeAndExtension() {
        assertTrue(NativeProcessingPolicy.isBlockedNativeDecoderInput(" Video/X-MsVideo ; charset=utf-8", null))
        assertTrue(NativeProcessingPolicy.isBlockedNativeDecoderInput(null, ".AVI"))
    }
}

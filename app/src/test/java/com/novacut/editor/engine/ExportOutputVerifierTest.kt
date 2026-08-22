package com.novacut.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.file.Files

class ExportOutputVerifierTest {

    @Test
    fun verifyRejectsNonExistentFile() {
        val result = ExportOutputVerifier.verify(File("/nonexistent/path/output.mp4"))
        assertFalse(result.valid)
        assertEquals("Output file does not exist", result.reason)
    }

    @Test
    fun verifyRejectsEmptyFile() {
        val dir = Files.createTempDirectory("verifier-test-").toFile()
        try {
            val empty = File(dir, "empty.mp4").apply { createNewFile() }
            val result = ExportOutputVerifier.verify(empty)
            assertFalse(result.valid)
            assertEquals("Output file is empty", result.reason)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun verificationResultDefaultsAreCorrect() {
        val result = ExportVerificationResult(valid = true)
        assertTrue(result.valid)
        assertNull(result.reason)
        assertFalse(result.hasVideo)
        assertFalse(result.hasAudio)
        assertEquals(0L, result.durationMs)
        assertEquals(0, result.width)
        assertEquals(0, result.height)
        assertEquals(0, result.trackCount)
        assertFalse(result.playable)
        assertFalse(result.streamSafe)
        assertEquals(ExportDeliveryStatus.INVALID, result.deliveryStatus)
    }

    @Test
    fun failedResultCarriesReasonAndMetadata() {
        val result = ExportVerificationResult(
            valid = false,
            reason = "test failure",
            hasVideo = true,
            hasAudio = false,
            durationMs = 5000L,
            width = 1920,
            height = 1080,
            trackCount = 1
        )
        assertFalse(result.valid)
        assertEquals("test failure", result.reason)
        assertTrue(result.hasVideo)
        assertFalse(result.hasAudio)
        assertEquals(5000L, result.durationMs)
        assertEquals(1920, result.width)
        assertEquals(1080, result.height)
        assertEquals(1, result.trackCount)
    }

    @Test
    fun shortEncodedOutputIsRejectedAgainstTheRequestedTimelineDuration() {
        val reason = outputDurationFailureReason(
            expectedDurationMs = 65L * 60L * 1_000L,
            actualDurationMs = 30L * 1_000L,
            durationToleranceMs = 2_000L,
        )

        assertTrue(reason?.contains("shorter than expected") == true)
    }

    @Test
    fun outputWithinDurationToleranceRemainsValid() {
        assertNull(
            outputDurationFailureReason(
                expectedDurationMs = 3_000L,
                actualDurationMs = 1_500L,
                durationToleranceMs = 2_000L,
            )
        )
    }

    @Test
    fun detectsMp4ContainerAndFastStartLayout() {
        val dir = Files.createTempDirectory("verifier-container-test-").toFile()
        try {
            val fastStart = File(dir, "fast.mp4").apply {
                writeBytes(atom("ftyp") + atom("moov") + atom("mdat", ByteArray(16)))
            }
            val progressive = File(dir, "progressive.mp4").apply {
                writeBytes(atom("ftyp") + atom("mdat", ByteArray(16)) + atom("moov"))
            }

            assertEquals(ExportContainer.MP4, detectExportContainer(fastStart))
            assertTrue(hasFastStartMp4Layout(fastStart))
            assertFalse(hasFastStartMp4Layout(progressive))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun streamSafeContractDistinguishesPlayableOnlyFromProgressiveMp4() {
        assertNull(streamSafeContractFailure(ExportContainer.MP4, fastStart = true, requireFastStart = true))
        assertTrue(
            streamSafeContractFailure(ExportContainer.MP4, fastStart = false, requireFastStart = true)
                ?.contains("playable but not stream-safe") == true
        )
        assertNull(streamSafeContractFailure(ExportContainer.MP4, fastStart = false, requireFastStart = false))
        assertEquals(
            ExportDeliveryStatus.PLAYABLE,
            ExportVerificationResult(valid = true, playable = true, streamSafe = false).deliveryStatus,
        )
        assertEquals(
            ExportDeliveryStatus.STREAM_SAFE,
            ExportVerificationResult(valid = true, playable = true, streamSafe = true).deliveryStatus,
        )
    }

    @Test
    fun detectsWebmContainerFromEbmlHeader() {
        val dir = Files.createTempDirectory("verifier-webm-test-").toFile()
        try {
            val webm = File(dir, "output.webm")
            FileOutputStream(webm).use { it.write(byteArrayOf(0x1a, 0x45.toByte(), 0xdf.toByte(), 0xa3.toByte())) }
            assertEquals(ExportContainer.WEBM, detectExportContainer(webm))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun expectedContainerFollowsOutputExtension() {
        assertEquals(ExportContainer.MP4, expectedContainerForExtension("MP4"))
        assertEquals(ExportContainer.MP4, expectedContainerForExtension("m4a"))
        assertEquals(ExportContainer.WEBM, expectedContainerForExtension("webm"))
        assertEquals(ExportContainer.UNKNOWN, expectedContainerForExtension("gif"))
    }

    private fun atom(type: String, payload: ByteArray = ByteArray(0)): ByteArray {
        return ByteBuffer.allocate(8 + payload.size)
            .putInt(8 + payload.size)
            .put(type.toByteArray(Charsets.US_ASCII))
            .put(payload)
            .array()
    }
}

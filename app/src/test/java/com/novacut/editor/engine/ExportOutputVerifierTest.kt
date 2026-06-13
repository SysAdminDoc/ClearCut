package com.novacut.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
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
}

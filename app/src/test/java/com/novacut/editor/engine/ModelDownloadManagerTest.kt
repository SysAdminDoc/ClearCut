package com.novacut.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ModelDownloadManagerTest {

    @Test
    fun isValidModelFile_requiresFileAtMinimumSize() {
        val dir = Files.createTempDirectory("novacut-model-test").toFile()
        val model = File(dir, "model.onnx")
        try {
            model.writeBytes(ByteArray(16))

            assertTrue(ModelDownloadManager.isValidModelFile(model, minimumBytes = 16))
            assertFalse(ModelDownloadManager.isValidModelFile(model, minimumBytes = 17))
            assertFalse(ModelDownloadManager.isValidModelFile(File(dir, "missing.onnx"), minimumBytes = 1))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun validateDownloadedFile_rejectsEmptyIncompleteAndUndersizedFiles() {
        val dir = Files.createTempDirectory("novacut-model-validation").toFile()
        val model = File(dir, "model.onnx")
        try {
            model.writeBytes(ByteArray(0))
            assertThrows(java.io.IOException::class.java) {
                ModelDownloadManager.validateDownloadedFile(model, 1, null, "test model")
            }

            model.writeBytes(ByteArray(8))
            assertThrows(java.io.IOException::class.java) {
                ModelDownloadManager.validateDownloadedFile(model, 4, 9, "test model")
            }
            assertThrows(java.io.IOException::class.java) {
                ModelDownloadManager.validateDownloadedFile(model, 9, null, "test model")
            }

            ModelDownloadManager.validateDownloadedFile(model, 8, 8, "test model")
        } finally {
            dir.deleteRecursively()
        }
    }

    // --- R5.9b non-bypassable checksum verification ---

    @Test
    fun isValidModelFile_requireChecksumWithNoSha256_returnsFalse() {
        val dir = Files.createTempDirectory("novacut-r59b-no-hash").toFile()
        val model = File(dir, "model.onnx").apply { writeBytes(ByteArray(32)) }
        try {
            // Legacy lenient mode: null sha + minimum met → true.
            assertTrue(
                ModelDownloadManager.isValidModelFile(
                    file = model,
                    minimumBytes = 16,
                    expectedSha256 = null,
                    requireChecksum = false,
                )
            )
            // R5.9b strict mode: null sha + minimum met → false (no integrity proof).
            assertFalse(
                ModelDownloadManager.isValidModelFile(
                    file = model,
                    minimumBytes = 16,
                    expectedSha256 = null,
                    requireChecksum = true,
                )
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun verifyChecksumOrDelete_failsClosedOnMissingHash() {
        val dir = Files.createTempDirectory("novacut-r59b-explicit").toFile()
        val model = File(dir, "model.onnx").apply { writeBytes(ByteArray(64)) }
        try {
            assertFalse(
                ModelDownloadManager.verifyChecksumOrDelete(
                    file = model,
                    minimumBytes = 32,
                    expectedSha256 = null,
                )
            )
            // File is NOT deleted just because the hash was missing — only on
            // a real mismatch. Missing hash = block load, but cached bytes stay
            // so a future SHA-256 fill in docs/models.md can validate without
            // a re-download.
            assertTrue(model.exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun verifyChecksumOrDelete_acceptsMatchingHash() {
        val dir = Files.createTempDirectory("novacut-r59b-match").toFile()
        val model = File(dir, "model.onnx").apply { writeBytes("hello".toByteArray()) }
        // sha256("hello") = 2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824
        try {
            assertTrue(
                ModelDownloadManager.verifyChecksumOrDelete(
                    file = model,
                    minimumBytes = 1,
                    expectedSha256 = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
                )
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun verifyChecksumOrDelete_deletesOnMismatch() {
        val dir = Files.createTempDirectory("novacut-r59b-mismatch").toFile()
        val model = File(dir, "model.onnx").apply { writeBytes("world".toByteArray()) }
        try {
            val ok = ModelDownloadManager.verifyChecksumOrDelete(
                file = model,
                minimumBytes = 1,
                expectedSha256 = "0000000000000000000000000000000000000000000000000000000000000000",
            )
            assertFalse(ok)
            // Mismatched file IS deleted so a subsequent retry re-downloads.
            assertFalse(model.exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun estimateTotalBytes_usesTheLargerOfEstimateAndMinimum() {
        val files = listOf(
            ModelDownloadManager.ModelFile(
                url = "https://example.com/a.onnx",
                targetFile = File("a.onnx"),
                minimumBytes = 100,
                estimatedBytes = 50
            ),
            ModelDownloadManager.ModelFile(
                url = "https://example.com/b.onnx",
                targetFile = File("b.onnx"),
                minimumBytes = 100,
                estimatedBytes = 250
            )
        )

        assertEquals(350, ModelDownloadManager.estimateTotalBytes(files))
    }
}

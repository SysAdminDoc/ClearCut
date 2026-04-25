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

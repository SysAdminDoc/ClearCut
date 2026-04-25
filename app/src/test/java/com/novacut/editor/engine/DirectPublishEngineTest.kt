package com.novacut.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.file.Files

class DirectPublishEngineTest {

    @Test
    fun validatePublishableFile_rejectsMissingFile() {
        val file = File("missing-export-${System.nanoTime()}.mp4")

        assertEquals("Export file not found", validatePublishableFile(file))
    }

    @Test
    fun validatePublishableFile_rejectsDirectories() {
        val dir = Files.createTempDirectory("publish-dir-").toFile()
        try {
            assertEquals("Export path is not a video file", validatePublishableFile(dir))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun validatePublishableFile_rejectsEmptyFiles() {
        val dir = Files.createTempDirectory("publish-empty-").toFile()
        try {
            val file = File(dir, "export.mp4").apply { writeBytes(ByteArray(0)) }

            assertEquals("Export file is empty", validatePublishableFile(file))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun validatePublishableFile_acceptsReadableNonEmptyFiles() {
        val dir = Files.createTempDirectory("publish-valid-").toFile()
        try {
            val file = File(dir, "export.mp4").apply { writeBytes(byteArrayOf(1, 2, 3)) }

            assertNull(validatePublishableFile(file))
        } finally {
            dir.deleteRecursively()
        }
    }
}

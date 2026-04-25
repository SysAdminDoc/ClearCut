package com.novacut.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class StillImageOutputFilesTest {

    @Test
    fun finalizeStillImageOutputFile_promotesReadablePartial() {
        val dir = Files.createTempDirectory("still-image-").toFile()
        try {
            val output = File(dir, "poster.jpg").apply { writeBytes(byteArrayOf(9)) }
            val partial = File(dir, ".poster.jpg.novacut-partial-1").apply {
                writeBytes(byteArrayOf(1, 2, 3, 4))
            }

            val result = finalizeStillImageOutputFile(partial, output)

            assertEquals(output, result)
            assertFalse(partial.exists())
            assertTrue(output.isFile)
            assertEquals(4L, output.length())
            assertEquals(listOf(1, 2, 3, 4), output.readBytes().map { it.toInt() })
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun finalizeStillImageOutputFile_rejectsEmptyPartialWithoutDeletingExistingOutput() {
        val dir = Files.createTempDirectory("still-image-empty-").toFile()
        try {
            val output = File(dir, "poster.jpg").apply { writeBytes(byteArrayOf(9, 8, 7)) }
            val partial = File(dir, ".poster.jpg.novacut-partial-1").apply { writeBytes(ByteArray(0)) }

            val result = finalizeStillImageOutputFile(partial, output)

            assertNull(result)
            assertFalse(partial.exists())
            assertTrue(output.exists())
            assertEquals(listOf(9, 8, 7), output.readBytes().map { it.toInt() })
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun createStillImageOutputFiles_usesHiddenSiblingPartial() {
        val dir = Files.createTempDirectory("still-image-create-").toFile()
        try {
            val output = File(dir, "contact sheet.png")

            val files = createStillImageOutputFiles(output)

            assertEquals(output.absoluteFile, files.outputFile)
            assertEquals(dir.absoluteFile, files.partialFile.parentFile)
            assertTrue(files.partialFile.name.startsWith(".contact sheet.png.novacut-partial-"))
        } finally {
            dir.deleteRecursively()
        }
    }
}

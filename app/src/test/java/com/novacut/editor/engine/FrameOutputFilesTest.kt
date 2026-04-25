package com.novacut.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class FrameOutputFilesTest {

    @Test
    fun finalizeFrameOutputFile_promotesReadablePartial() {
        val dir = Files.createTempDirectory("frame-output-").toFile()
        try {
            val partial = File(dir, "frame_1.partial.png").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
            val output = File(dir, "frame_1.png")

            val result = finalizeFrameOutputFile(partial, output)

            assertEquals(output, result)
            assertFalse(partial.exists())
            assertTrue(output.isFile)
            assertEquals(4L, output.length())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun finalizeFrameOutputFile_rejectsEmptyPartial() {
        val dir = Files.createTempDirectory("frame-output-empty-").toFile()
        try {
            val partial = File(dir, "frame_1.partial.jpg").apply { writeBytes(ByteArray(0)) }
            val output = File(dir, "frame_1.jpg").apply { writeBytes(byteArrayOf(9)) }

            val result = finalizeFrameOutputFile(partial, output)

            assertNull(result)
            assertFalse(partial.exists())
            assertFalse(output.exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun safeFrameOutputExtension_allowsOnlySupportedImageExtensions() {
        assertEquals("jpg", safeFrameOutputExtension(".JPG"))
        assertEquals("jpg", safeFrameOutputExtension("jpeg"))
        assertEquals("png", safeFrameOutputExtension("PNG"))
        assertEquals("png", safeFrameOutputExtension("../webp"))
    }

    @Test
    fun frameOutputDirectoryNames_matchProviderAndBackupRules() {
        assertEquals("frame_captures", FRAME_CAPTURE_DIR_NAME)
        assertEquals("freeze_frames", FREEZE_FRAME_DIR_NAME)
    }
}

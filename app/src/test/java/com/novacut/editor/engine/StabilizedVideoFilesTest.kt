package com.novacut.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class StabilizedVideoFilesTest {

    @Test
    fun finalizeStabilizedVideoFile_promotesReadablePartial() {
        val dir = Files.createTempDirectory("stabilized-video-").toFile()
        try {
            val partial = File(dir, "stabilized_clip_1.partial.mp4").apply {
                writeBytes(byteArrayOf(1, 2, 3, 4, 5, 6))
            }
            val output = File(dir, "stabilized_clip_1.mp4")

            val result = finalizeStabilizedVideoFile(partial, output)

            assertEquals(output, result)
            assertFalse(partial.exists())
            assertTrue(output.isFile)
            assertEquals(6L, output.length())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun finalizeStabilizedVideoFile_rejectsEmptyPartial() {
        val dir = Files.createTempDirectory("stabilized-video-empty-").toFile()
        try {
            val partial = File(dir, "stabilized_clip_1.partial.mp4").apply { writeBytes(ByteArray(0)) }
            val output = File(dir, "stabilized_clip_1.mp4").apply { writeBytes(byteArrayOf(9)) }

            val result = finalizeStabilizedVideoFile(partial, output)

            assertNull(result)
            assertFalse(partial.exists())
            assertFalse(output.exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun safeStabilizedVideoStem_removesUnsafePathCharacters() {
        assertEquals("clip_01_weird_name", safeStabilizedVideoStem("../clip:01/weird name"))
        assertEquals("clip", safeStabilizedVideoStem("..."))
    }
}

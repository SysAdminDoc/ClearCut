package com.novacut.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class NoiseReductionFilesTest {

    @Test
    fun finalizeNoiseReducedAudioFile_promotesReadablePartial() {
        val dir = Files.createTempDirectory("noise-reduced-").toFile()
        try {
            val partial = File(dir, "nr_1.partial.m4a").apply { writeBytes(byteArrayOf(1, 2, 3, 4, 5)) }
            val output = File(dir, "nr_1.m4a")

            val result = finalizeNoiseReducedAudioFile(partial, output)

            assertEquals(output, result)
            assertFalse(partial.exists())
            assertTrue(output.isFile)
            assertEquals(5L, output.length())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun finalizeNoiseReducedAudioFile_rejectsEmptyPartial() {
        val dir = Files.createTempDirectory("noise-reduced-empty-").toFile()
        try {
            val partial = File(dir, "nr_1.partial.m4a").apply { writeBytes(ByteArray(0)) }
            val output = File(dir, "nr_1.m4a").apply { writeBytes(byteArrayOf(9)) }

            val result = finalizeNoiseReducedAudioFile(partial, output)

            assertNull(result)
            assertFalse(partial.exists())
            assertFalse(output.exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun finalizeNoiseReducedAudioFile_rejectsMissingPartial() {
        val dir = Files.createTempDirectory("noise-reduced-missing-").toFile()
        try {
            val partial = File(dir, "nr_1.partial.m4a")
            val output = File(dir, "nr_1.m4a").apply { writeBytes(byteArrayOf(9)) }

            val result = finalizeNoiseReducedAudioFile(partial, output)

            assertNull(result)
            assertFalse(output.exists())
        } finally {
            dir.deleteRecursively()
        }
    }
}

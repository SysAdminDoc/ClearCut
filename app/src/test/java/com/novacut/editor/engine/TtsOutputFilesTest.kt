package com.novacut.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class TtsOutputFilesTest {

    @Test
    fun finalizeSynthesizedTtsFile_promotesReadablePartial() {
        val dir = Files.createTempDirectory("tts-output-").toFile()
        try {
            val partial = File(dir, "tts_1.partial.wav").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val output = File(dir, "tts_1.wav")

            val result = finalizeSynthesizedTtsFile(partial, output)

            assertEquals(output, result)
            assertFalse(partial.exists())
            assertTrue(output.isFile)
            assertEquals(3L, output.length())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun finalizeSynthesizedTtsFile_rejectsEmptyPartial() {
        val dir = Files.createTempDirectory("tts-output-empty-").toFile()
        try {
            val partial = File(dir, "tts_1.partial.wav").apply { writeBytes(ByteArray(0)) }
            val output = File(dir, "tts_1.wav").apply { writeBytes(byteArrayOf(9)) }

            val result = finalizeSynthesizedTtsFile(partial, output)

            assertNull(result)
            assertFalse(partial.exists())
            assertFalse(output.exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun ttsOutputDirectory_matchesBackupAndFileProviderRules() {
        assertEquals("tts_output", TTS_OUTPUT_DIR_NAME)
    }
}

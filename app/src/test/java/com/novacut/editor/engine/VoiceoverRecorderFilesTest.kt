package com.novacut.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class VoiceoverRecorderFilesTest {

    @Test
    fun finalizeRecordedVoiceoverFile_promotesReadablePartial() {
        val dir = Files.createTempDirectory("voiceover-output-").toFile()
        try {
            val partial = File(dir, "voiceover_1.partial.m4a").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
            val output = File(dir, "voiceover_1.m4a")

            val result = finalizeRecordedVoiceoverFile(partial, output)

            assertEquals(output, result)
            assertFalse(partial.exists())
            assertTrue(output.isFile)
            assertEquals(4L, output.length())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun finalizeRecordedVoiceoverFile_rejectsEmptyPartial() {
        val dir = Files.createTempDirectory("voiceover-empty-").toFile()
        try {
            val partial = File(dir, "voiceover_1.partial.m4a").apply { writeBytes(ByteArray(0)) }
            val output = File(dir, "voiceover_1.m4a").apply { writeBytes(byteArrayOf(9)) }

            val result = finalizeRecordedVoiceoverFile(partial, output)

            assertNull(result)
            assertFalse(partial.exists())
            assertFalse(output.exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun finalizeRecordedVoiceoverFile_rejectsMissingRecordingPair() {
        val dir = Files.createTempDirectory("voiceover-missing-").toFile()
        try {
            val output = File(dir, "voiceover_1.m4a").apply { writeBytes(byteArrayOf(9)) }

            val result = finalizeRecordedVoiceoverFile(null, output)

            assertNull(result)
            assertFalse(output.exists())
        } finally {
            dir.deleteRecursively()
        }
    }
}

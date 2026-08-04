package com.novacut.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class FileNamingTest {

    @Test
    fun `sanitizeFileName falls back for blank names`() {
        assertEquals("ClearCut", sanitizeFileName("   "))
        assertEquals("backup", sanitizeFileName("", fallback = "backup"))
    }

    @Test
    fun `sanitizeFileName removes invalid path characters`() {
        assertEquals(
            "Project_Name_Final",
            sanitizeFileName("Project/Name:Final")
        )
    }

    @Test
    fun `sanitizeFileName avoids reserved windows names`() {
        assertEquals("CON_", sanitizeFileName("CON"))
        assertEquals("LPT1_", sanitizeFileName("LPT1"))
    }

    @Test
    fun `sanitizeFileNamePreservingExtension keeps a safe extension`() {
        val sanitized = sanitizeFileNamePreservingExtension("  rough<>cut?.FCPXML  ")

        assertEquals("rough__cut_.fcpxml", sanitized)
        assertFalse(sanitized.endsWith("."))
    }

    @Test
    fun `autoSaveFileStem is deterministic and strips path separators`() {
        val stem = autoSaveFileStem("../CON?.json")

        assertEquals(stem, autoSaveFileStem("../CON?.json"))
        assertNotEquals(stem, autoSaveFileStem("../CON?.json/other"))
        assertFalse(stem.contains("/"))
        assertFalse(stem.contains("\\"))
    }

    @Test
    fun `finalizeFilenameSize renames completed output using rounded megabytes`() {
        val dir = Files.createTempDirectory("filename-size-").toFile()
        try {
            val output = dir.resolve("clip_{sizeMB}.gif").apply {
                writeBytes(ByteArray(600_000))
            }

            val finalized = finalizeFilenameSize(output)

            assertEquals("clip_1MB.gif", finalized.name)
            assertFalse(output.exists())
            assertEquals(600_000L, finalized.length())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `finalizeFilenameSize leaves ordinary names unchanged`() {
        val dir = Files.createTempDirectory("filename-size-noop-").toFile()
        try {
            val output = dir.resolve("clip.gif").apply { writeBytes(byteArrayOf(1)) }

            val finalized = finalizeFilenameSize(output)

            assertSame(output, finalized)
            assertTrue(output.exists())
        } finally {
            dir.deleteRecursively()
        }
    }
}

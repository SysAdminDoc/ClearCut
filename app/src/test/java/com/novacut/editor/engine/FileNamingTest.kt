package com.novacut.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

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
}

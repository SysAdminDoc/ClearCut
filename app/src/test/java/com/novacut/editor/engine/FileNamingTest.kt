package com.novacut.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class FileNamingTest {

    @Test
    fun `sanitizeFileName falls back for blank names`() {
        assertEquals("NovaCut", sanitizeFileName("   "))
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
}

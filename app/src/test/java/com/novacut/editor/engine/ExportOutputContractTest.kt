package com.novacut.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Contract tests for the export filename sanitizer and the output verifier's
 * file-level guards. The verifier's track/duration inspection uses
 * MediaExtractor and is covered by device/instrumentation tests; here we lock
 * the parts that must hold before a byte is written, so a bad custom name or an
 * empty/missing output can never reach the gallery.
 */
class ExportOutputContractTest {

    @get:Rule
    val temp = TemporaryFolder()

    // --- FileNaming.sanitizeFileName ---

    @Test
    fun sanitizeFileName_stripsPathAndReservedCharacters() {
        val cleaned = sanitizeFileName("clip" + Char(7) + "name")
        assertFalse(cleaned.any { it in charArrayOf('/', '\\', ':', '*', '?', '"', '<', '>', '|') })
    }

    @Test
    fun sanitizeFileName_blankFallsBackToDefault() {
        assertEquals("ClearCut", sanitizeFileName("   "))
        // Only dots/spaces -> trimmed to empty -> fallback (path chars like '/'
        // are replaced with '_' and are therefore NOT blank).
        assertEquals("Fallback", sanitizeFileName(". . .", fallback = "Fallback"))
    }

    @Test
    fun sanitizeFileName_reservedWindowsNamesGetSuffixed() {
        assertEquals("CON_", sanitizeFileName("CON"))
        assertEquals("nul_", sanitizeFileName("nul"))
    }

    @Test
    fun sanitizeFileName_trimsTrailingDotsAndSpacesAndCollapsesWhitespace() {
        assertEquals("my clip", sanitizeFileName("  my    clip . . "))
    }

    @Test
    fun sanitizeFileName_boundsLength() {
        val long = "x".repeat(500)
        assertEquals(80, sanitizeFileName(long).length)
        assertEquals(10, sanitizeFileName(long, maxLength = 10).length)
    }

    @Test
    fun sanitizeFileName_controlCharactersBecomeUnderscore() {
        // A bell (U+0007) is a non-whitespace ISO control char and must be replaced.
        val cleaned = sanitizeFileName("clip" + Char(7) + "name")
        assertEquals("clip_name", cleaned)
        assertFalse(cleaned.any { it.isISOControl() })
    }

    // --- FileNaming.sanitizeFileNamePreservingExtension ---

    @Test
    fun preserveExtension_keepsAndNormalizesExtension() {
        assertEquals("clip.mp4", sanitizeFileNamePreservingExtension("clip.MP4"))
        // Reserved chars in the stem are sanitized; extension preserved.
        val out = sanitizeFileNamePreservingExtension("""a/b.mp4""")
        assertTrue(out.endsWith(".mp4"))
        assertFalse(out.substringBeforeLast('.').contains('/'))
    }

    @Test
    fun preserveExtension_noExtensionYieldsSanitizedStem() {
        assertEquals("clip", sanitizeFileNamePreservingExtension("clip"))
    }

    @Test
    fun preserveExtension_lengthBudgetAccountsForExtension() {
        val out = sanitizeFileNamePreservingExtension("x".repeat(500) + ".mp4", maxLength = 20)
        assertTrue("total length within budget", out.length <= 20)
        assertTrue(out.endsWith(".mp4"))
    }

    // --- ExportOutputVerifier file-level guards ---

    @Test
    fun verify_missingFileIsRejectedWithReason() {
        val result = ExportOutputVerifier.verify(temp.root.resolve("nope.mp4"))
        assertFalse(result.valid)
        assertTrue(result.reason?.contains("does not exist") == true)
    }

    @Test
    fun verify_emptyFileIsRejectedWithReason() {
        val empty = temp.newFile("empty.mp4")
        val result = ExportOutputVerifier.verify(empty)
        assertFalse(result.valid)
        assertTrue(result.reason?.contains("empty") == true)
    }
}

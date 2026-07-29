package com.novacut.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * A redacted identifier is only useful if it is (a) stable, so a support report
 * can say "this same clip failed four times", and (b) genuinely free of the
 * original name.
 */
class RedactedLogTest {

    @Test
    fun theSameReferenceAlwaysProducesTheSameId() {
        val reference = "content://media/external/video/media/1234"

        assertEquals(RedactedLog.assetId(reference), RedactedLog.assetId(reference))
    }

    @Test
    fun differentReferencesProduceDifferentIds() {
        assertNotEquals(
            RedactedLog.assetId("content://media/external/video/media/1"),
            RedactedLog.assetId("content://media/external/video/media/2")
        )
    }

    @Test
    fun theIdNeverContainsTheOriginalName() {
        val path = "/storage/emulated/0/DCIM/hospital-visit-2026.mp4"

        val redacted = RedactedLog.path(path)

        assertFalse(redacted.contains("hospital-visit-2026"))
        assertFalse(redacted.contains("DCIM"))
        assertFalse(redacted.contains("storage"))
        assertTrue("shape is kept for triage", redacted.endsWith("(path,.mp4)"))
    }

    @Test
    fun fileRedactionKeepsOnlyTheExtension() {
        val redacted = RedactedLog.file(File("/data/user/0/com.novacut.editor/files/wedding speech.m4a"))

        assertFalse(redacted.contains("wedding"))
        assertFalse(redacted.contains("speech"))
        assertTrue(redacted.endsWith("(file,.m4a)"))
        assertTrue(redacted.startsWith("asset#"))
    }

    @Test
    fun aLongOrOddSuffixIsNotMistakenForAnExtension() {
        val redacted = RedactedLog.path("/tmp/my.private.notes.about.someone")

        assertFalse(redacted.contains("someone"))
        assertTrue(redacted.endsWith("(path)"))
    }

    @Test
    fun missingReferencesAreNamedRatherThanBlank() {
        assertEquals("asset#none", RedactedLog.assetId(null))
        assertEquals("asset#none", RedactedLog.assetId(""))
        assertEquals("asset#none", RedactedLog.file(null))
        assertEquals("asset#none", RedactedLog.path(null))
    }

    @Test
    fun idsAreShortEnoughToReadInALogLine() {
        val id = RedactedLog.assetId("content://media/external/video/media/1234")

        assertEquals("asset#".length + 8, id.length)
    }
}

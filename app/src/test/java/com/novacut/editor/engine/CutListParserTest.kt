package com.novacut.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CutListParserTest {

    // --- Timecode parsing: colon / dot / minute-second variants ---

    @Test
    fun `parses bare seconds with and without fraction`() {
        assertEquals(0L, CutListParser.parseTimecode("0"))
        assertEquals(12_000L, CutListParser.parseTimecode("12"))
        assertEquals(12_500L, CutListParser.parseTimecode("12.5"))
        assertEquals(1_250L, CutListParser.parseTimecode("1.25"))
    }

    @Test
    fun `parses minute-second colon form`() {
        assertEquals(10_000L, CutListParser.parseTimecode("0:10"))
        assertEquals(90_000L, CutListParser.parseTimecode("1:30"))
        assertEquals(83_500L, CutListParser.parseTimecode("1:23.5"))
    }

    @Test
    fun `parses hour-minute-second colon form`() {
        assertEquals(3_600_000L, CutListParser.parseTimecode("1:00:00"))
        assertEquals(3_723_000L, CutListParser.parseTimecode("1:02:03"))
        assertEquals(83_500L, CutListParser.parseTimecode("0:01:23.5"))
    }

    @Test
    fun `rejects out-of-range and malformed timecodes`() {
        assertNull(CutListParser.parseTimecode("1:60"))     // seconds >= 60
        assertNull(CutListParser.parseTimecode("1:60:00"))  // minutes >= 60
        assertNull(CutListParser.parseTimecode("1:2:3:4"))  // too many parts
        assertNull(CutListParser.parseTimecode("abc"))
        assertNull(CutListParser.parseTimecode(""))
        assertNull(CutListParser.parseTimecode("-5"))
    }

    // --- Line parsing: markers vs ranges ---

    @Test
    fun `single timecode becomes a point marker with label`() {
        val result = CutListParser.parse("0:10 Intro")
        assertFalse(result.hasErrors)
        assertEquals(1, result.entries.size)
        val e = result.entries.single()
        assertEquals(10_000L, e.startMs)
        assertNull(e.endMs)
        assertFalse(e.isRange)
        assertEquals("Intro", e.label)
    }

    @Test
    fun `range with dash separator becomes a cut`() {
        val e = CutListParser.parse("0:10 - 0:15 Trim this").entries.single()
        assertEquals(10_000L, e.startMs)
        assertEquals(15_000L, e.endMs)
        assertTrue(e.isRange)
        assertEquals("Trim this", e.label)
    }

    @Test
    fun `range accepts to and dotdot and comma separators`() {
        assertEquals(18_000L, CutListParser.parse("12 to 18").entries.single().endMs)
        assertEquals(18_000L, CutListParser.parse("12..18").entries.single().endMs)
        val comma = CutListParser.parse("90, 95, Outro").entries.single()
        assertEquals(90_000L, comma.startMs)
        assertEquals(95_000L, comma.endMs)
        assertEquals("Outro", comma.label)
    }

    @Test
    fun `label word starting with to is not treated as a separator`() {
        val e = CutListParser.parse("0:10 total runtime").entries.single()
        assertNull(e.endMs)
        assertEquals("total runtime", e.label)
    }

    @Test
    fun `blank lines and comments are skipped without error`() {
        val result = CutListParser.parse(
            """
            # cut list
            0:05 Marker A

            0:10 - 0:12  Cut B
            """.trimIndent()
        )
        assertFalse(result.hasErrors)
        assertEquals(2, result.entries.size)
    }

    @Test
    fun `invalid rows are reported with their line number and nothing is applied`() {
        val result = CutListParser.parse(
            """
            0:05 good
            not-a-time
            0:20 - 0:10 backwards
            """.trimIndent()
        )
        assertEquals(1, result.entries.size)
        assertEquals(2, result.errors.size)
        assertEquals(2, result.errors[0].lineNumber)
        assertEquals(3, result.errors[1].lineNumber)
        assertTrue(result.errors[1].message.contains("after", ignoreCase = true))
    }

    @Test
    fun `end equal to start is rejected as a zero-length range`() {
        val result = CutListParser.parse("0:10 - 0:10")
        assertTrue(result.hasErrors)
        assertTrue(result.entries.isEmpty())
    }

    @Test
    fun `line numbers reflect original position including skipped lines`() {
        val result = CutListParser.parse("# header\n\n0:10 - 0:15 keep\nbad")
        assertEquals(3, result.entries.single().lineNumber)
        assertEquals(4, result.errors.single().lineNumber)
    }

    @Test
    fun `empty input yields empty result`() {
        val result = CutListParser.parse("")
        assertTrue(result.isEmpty)
        assertFalse(result.hasErrors)
    }
}

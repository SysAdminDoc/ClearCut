package com.novacut.editor.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The keyframe precision fields format with a dot separator and must accept
 * both dot and comma input — comma-decimal locales previously dead-locked the
 * field because toDoubleOrNull() rejected every keystroke.
 */
class DecimalInputsTest {

    @Test
    fun `parses dot decimals`() {
        assertEquals(1.5, parseEditorDecimal("1.5")!!, 1e-9)
    }

    @Test
    fun `parses comma decimals`() {
        assertEquals(1.5, parseEditorDecimal("1,5")!!, 1e-9)
        assertEquals(0.25, parseEditorDecimal(",25")!!, 1e-9)
    }

    @Test
    fun `parses integers negatives and whitespace`() {
        assertEquals(42.0, parseEditorDecimal("42")!!, 1e-9)
        assertEquals(-3.25, parseEditorDecimal(" -3,25 ")!!, 1e-9)
    }

    @Test
    fun `rejects garbage and ambiguous input`() {
        assertNull(parseEditorDecimal(""))
        assertNull(parseEditorDecimal("abc"))
        assertNull(parseEditorDecimal("1.2.3"))
        assertNull(parseEditorDecimal("1,2,3"))
        assertNull(parseEditorDecimal("1.2,3"))
        assertNull(parseEditorDecimal("NaN"))
        assertNull(parseEditorDecimal("Infinity"))
    }

    @Test
    fun `format round-trips through parse`() {
        val formatted = formatEditorDecimal(1234.5678, 3)
        assertEquals("1234.568", formatted)
        assertEquals(1234.568, parseEditorDecimal(formatted)!!, 1e-9)
    }
}

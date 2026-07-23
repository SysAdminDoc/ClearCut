package com.novacut.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FontRegistryTest {

    @Test
    fun `accepts ttf and otf ignoring case`() {
        assertEquals("Brand.ttf", FontRegistry.sanitizedFontFileName("Brand.ttf"))
        assertEquals("Brand.otf", FontRegistry.sanitizedFontFileName("Brand.OTF"))
        assertEquals("Brand.ttf", FontRegistry.sanitizedFontFileName("Brand.TtF"))
    }

    @Test
    fun `rejects non-font extensions`() {
        assertNull(FontRegistry.sanitizedFontFileName("payload.exe"))
        assertNull(FontRegistry.sanitizedFontFileName("font.ttf.exe"))
        assertNull(FontRegistry.sanitizedFontFileName("image.png"))
        assertNull(FontRegistry.sanitizedFontFileName("data.woff2"))
    }

    @Test
    fun `rejects names without an extension`() {
        assertNull(FontRegistry.sanitizedFontFileName("Brand"))
        assertNull(FontRegistry.sanitizedFontFileName(""))
        assertNull(FontRegistry.sanitizedFontFileName("   "))
        assertNull(FontRegistry.sanitizedFontFileName(null))
    }

    @Test
    fun `sanitizes unsafe characters in the stem`() {
        assertEquals("my_font_.ttf", FontRegistry.sanitizedFontFileName("my/font!.ttf"))
        assertEquals("a_b_c.otf", FontRegistry.sanitizedFontFileName("a b c.otf"))
    }

    @Test
    fun `blank stem falls back to a stable name`() {
        assertEquals("font.ttf", FontRegistry.sanitizedFontFileName(".ttf"))
    }

    @Test
    fun `long stems are truncated`() {
        val long = "x".repeat(200) + ".ttf"
        val result = FontRegistry.sanitizedFontFileName(long)!!
        assertEquals(64, result.length) // 60-char stem + ".ttf"
    }
}

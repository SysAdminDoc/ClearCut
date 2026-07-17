package com.novacut.editor.engine.whisper

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Contract tests for the GPT-2 byte-level BPE reverse mapping.
 *
 * `bytes_to_unicode` keeps printable bytes (0x21-0x7E, 0xA1-0xAC, 0xAE-0xFF) as
 * themselves and remaps the remaining 68 bytes (0x00-0x20, 0x7F-0xA0, 0xAD) to
 * U+0100+n in order of appearance. The decoder must reverse that table exactly
 * and then UTF-8-decode the byte stream -- the old char arithmetic corrupted
 * curly quotes and every non-ASCII transcription. Escaped literals keep the
 * expectations byte-exact regardless of editor/source encoding.
 */
class Gpt2ByteDecoderTest {

    @Test
    fun asciiPassesThrough() {
        assertEquals("Hello, world!", Gpt2ByteDecoder.decode("Hello, world!"))
    }

    @Test
    fun spacePrefixDecodes() {
        // U+0120 (LATIN CAPITAL G WITH DOT ABOVE) is byte 0x20 -- the GPT-2
        // leading-space marker.
        assertEquals(" Hello", Gpt2ByteDecoder.decode("\u0120Hello"))
    }

    @Test
    fun newlineAndTabDecode() {
        // U+010A = byte 0x0A, U+0109 = byte 0x09 -- subsumed by the full table.
        assertEquals("a\nb\tc", Gpt2ByteDecoder.decode("a\u010Ab\u0109c"))
    }

    @Test
    fun rightSingleQuoteDecodesAsUtf8() {
        // U+2019 is UTF-8 E2 80 99. GPT-2 chars: 0xE2 self-maps (U+00E2), 0x80 is
        // the 35th remapped byte (U+0122), 0x99 the 60th (U+013B). The old
        // code-0x100 arithmetic turned this into mojibake instead of an apostrophe.
        assertEquals("don\u2019t", Gpt2ByteDecoder.decode("don\u00E2\u0122\u013Bt"))
    }

    @Test
    fun latin1AccentDecodesAsUtf8() {
        // U+00E9 is UTF-8 C3 A9; both bytes self-map (U+00C3 and U+00A9).
        assertEquals("caf\u00E9", Gpt2ByteDecoder.decode("caf\u00C3\u00A9"))
    }

    @Test
    fun highControlByteClassMapsCorrectly() {
        // U+0121 is the 34th remapped char -- byte 0x7F (DEL), NOT 0x21 as the old
        // code-0x100 arithmetic produced.
        assertEquals("\u007F", Gpt2ByteDecoder.decode("\u0121"))
    }

    @Test
    fun malformedByteSequenceIsReplacedNotCrashed() {
        // U+0143 maps to byte 0xAD -- a lone UTF-8 continuation byte, which must
        // decode to U+FFFD (replacement) rather than throw.
        assertEquals("\uFFFD", Gpt2ByteDecoder.decode("\u0143"))
    }
}

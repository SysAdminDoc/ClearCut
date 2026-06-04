package com.novacut.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM tests for [MediaRelinkProbe.check] — the Android-bound `probe`
 * surface is exercised by instrumentation. These lock the decision table so
 * the Timeline UI can rely on the report shape regardless of what the content
 * provider reports as length / what it throws.
 *
 * Context is unused by `check()`, so we construct the probe with a minimal
 * ContextWrapper(null). Android-flavour calls in this test would NPE, but
 * none of the asserted paths reach them — every test injects its own
 * [MediaRelinkProbe.UriOpener] so no real ContentResolver is consulted.
 */
class MediaRelinkProbeTest {

    private val probe = MediaRelinkProbe(android.content.ContextWrapper(null))

    @Test
    fun check_returnsOk_whenOpenerReportsPositiveLength() {
        val r = probe.check("c1", "content://media/external/video/123") { _ ->
            MediaRelinkProbe.ProbeResult(length = 1_024_000L)
        }
        assertEquals(MediaRelinkProbe.RelinkState.OK, r.state)
        assertEquals("c1", r.clipId)
    }

    @Test
    fun check_returnsOk_whenOpenerReportsUnknownLengthMinusOne() {
        // Some Android ContentProviders (notably Photo Picker temp grants and
        // SAF documents) report -1 for "unknown length". Treating this as
        // missing would false-positive on a huge fraction of normal imports.
        val r = probe.check("c2", "content://com.android.providers.media.photopicker/x") { _ ->
            MediaRelinkProbe.ProbeResult(length = -1L)
        }
        assertEquals(MediaRelinkProbe.RelinkState.OK, r.state)
    }

    @Test
    fun check_returnsMissing_whenOpenerReportsZeroLength() {
        val r = probe.check("c3", "content://media/external/video/123") { _ ->
            MediaRelinkProbe.ProbeResult(length = 0L)
        }
        assertEquals(MediaRelinkProbe.RelinkState.MISSING, r.state)
        assertTrue("Reason must mention empty source", r.reason?.contains("empty") == true)
    }

    @Test
    fun check_returnsMissing_whenOpenerReturnsNull() {
        val r = probe.check("c4", "content://x/y") { _ -> null }
        assertEquals(MediaRelinkProbe.RelinkState.MISSING, r.state)
        assertTrue(r.reason?.contains("null", ignoreCase = true) == true)
    }

    @Test
    fun check_returnsMissing_whenOpenerThrowsSecurityException() {
        val r = probe.check("c5", "content://x/y") { _ ->
            throw SecurityException("Permission denied")
        }
        assertEquals(MediaRelinkProbe.RelinkState.MISSING, r.state)
        assertTrue(r.reason?.contains("Permission denied") == true)
    }

    @Test
    fun check_returnsMissing_whenOpenerThrowsIOException() {
        val r = probe.check("c6", "file:///nonexistent.mp4") { _ ->
            throw java.io.FileNotFoundException("nonexistent.mp4")
        }
        assertEquals(MediaRelinkProbe.RelinkState.MISSING, r.state)
        assertTrue(r.reason?.contains("nonexistent") == true)
    }

    @Test
    fun check_returnsUnknown_forBlankUri() {
        val blank = probe.check("c7", "", failingOpener())
        val nullUri = probe.check("c8", null, failingOpener())
        assertEquals(MediaRelinkProbe.RelinkState.UNKNOWN, blank.state)
        assertEquals(MediaRelinkProbe.RelinkState.UNKNOWN, nullUri.state)
    }

    @Test
    fun check_returnsUnknown_forUnsupportedScheme() {
        // The Photo Picker never returns these; defending against a corrupt
        // autosave that snuck in via a third-party share intent.
        val ftp = probe.check("c9", "ftp://example.com/x.mp4", failingOpener())
        val data = probe.check("c10", "data:image/png;base64,abc", failingOpener())
        val noScheme = probe.check("c11", "/sdcard/Movies/x.mp4", failingOpener())
        assertEquals(MediaRelinkProbe.RelinkState.UNKNOWN, ftp.state)
        assertEquals(MediaRelinkProbe.RelinkState.UNKNOWN, data.state)
        assertEquals(MediaRelinkProbe.RelinkState.UNKNOWN, noScheme.state)
    }

    @Test
    fun report_userMessage_isReadable() {
        val ok = probe.check("c12", "content://x/y") { _ ->
            MediaRelinkProbe.ProbeResult(length = 1L)
        }
        val missing = probe.check("c13", "content://x/y") { _ ->
            throw SecurityException("Permission denied")
        }
        val unknown = probe.check("c14", "ftp://x/y", failingOpener())
        assertEquals("Source available", ok.userMessage)
        assertTrue(missing.userMessage.startsWith("Source missing"))
        assertTrue(unknown.userMessage.startsWith("Source unverified"))
    }

    @Test
    fun longThrowableMessageIsTruncated() {
        val long = "x".repeat(500)
        val r = probe.check("c15", "content://x/y") { _ -> throw RuntimeException(long) }
        assertEquals(MediaRelinkProbe.RelinkState.MISSING, r.state)
        assertTrue("reason was ${r.reason?.length}", (r.reason?.length ?: 0) <= 120)
    }

    @Test
    fun checkImageOverlay_usesOverlayIdAndClassifiesMissingSources() {
        val r = probe.checkImageOverlay("overlay-1", "file:///missing/sticker.png") { _ ->
            throw java.io.FileNotFoundException("sticker.png")
        }
        assertEquals("overlay-1", r.clipId)
        assertEquals(MediaRelinkProbe.RelinkState.MISSING, r.state)
        assertTrue(r.userMessage.startsWith("Source missing"))
    }

    private fun failingOpener(): MediaRelinkProbe.UriOpener =
        MediaRelinkProbe.UriOpener { _ ->
            error("opener must not be invoked for this case")
        }
}

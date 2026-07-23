package com.novacut.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportFileTypeTest {

    @Test
    fun `exportMimeTypeFor detects still images`() {
        assertEquals("image/png", exportMimeTypeFor("frame.png"))
        assertEquals("image/jpeg", exportMimeTypeFor("frame.JPG"))
        assertEquals("image/gif", exportMimeTypeFor("loop.gif"))
        assertEquals("image/webp", exportMimeTypeFor("poster.webp"))
    }

    @Test
    fun `exportMimeTypeFor detects video outputs`() {
        assertEquals("video/webm", exportMimeTypeFor("alpha.webm"))
        assertEquals("video/mp4", exportMimeTypeFor("master.mp4"))
        assertEquals("video/mp4", exportMimeTypeFor("no-extension"))
    }

    @Test
    fun `exportUsesImageCollection routes stills and gifs to images`() {
        assertTrue(exportUsesImageCollection("frame.png"))
        assertTrue(exportUsesImageCollection("loop.gif"))
        assertFalse(exportUsesImageCollection("master.mp4"))
        assertFalse(exportUsesImageCollection("alpha.webm"))
    }

    @Test
    fun `exportMimeTypeFor detects standalone audio outputs`() {
        assertEquals("audio/mp4", exportMimeTypeFor("mixdown.m4a"))
        assertEquals("audio/mp4", exportMimeTypeFor("MIXDOWN.M4A"))
        assertEquals("audio/aac", exportMimeTypeFor("voice.aac"))
    }

    @Test
    fun `exportUsesAudioCollection routes m4a and aac to the audio collection`() {
        assertTrue(exportUsesAudioCollection("mixdown.m4a"))
        assertTrue(exportUsesAudioCollection("voice.aac"))
        assertFalse(exportUsesAudioCollection("master.mp4"))
        assertFalse(exportUsesAudioCollection("frame.png"))
    }

    @Test
    fun `audio outputs are neither image nor video collections`() {
        assertFalse(exportUsesImageCollection("mixdown.m4a"))
        assertFalse(exportUsesImageCollection("voice.aac"))
    }
}

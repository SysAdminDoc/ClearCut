package com.novacut.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataScrubEngineTest {

    private val engine = MetadataScrubEngine()

    @Test
    fun canScrub_jpeg_returnsTrue() {
        assertTrue(engine.canScrub("image/jpeg"))
    }

    @Test
    fun canScrub_jpg_returnsTrue() {
        assertTrue(engine.canScrub("image/jpg"))
    }

    @Test
    fun canScrub_png_returnsTrue() {
        assertTrue(engine.canScrub("image/png"))
    }

    @Test
    fun canScrub_webp_returnsTrue() {
        assertTrue(engine.canScrub("image/webp"))
    }

    @Test
    fun canScrub_video_returnsFalse() {
        assertFalse(engine.canScrub("video/mp4"))
    }

    @Test
    fun canScrub_null_returnsFalse() {
        assertFalse(engine.canScrub(null))
    }

    @Test
    fun canScrub_caseInsensitive() {
        assertTrue(engine.canScrub("Image/JPEG"))
    }

    @Test
    fun redactUri_returnsOpaqueAssetUri() {
        val redacted = engine.redactUriForManifest("content://media/external/video/1234", "asset-001")
        assertEquals("asset://asset-001", redacted)
        assertFalse(redacted.contains("content://"))
        assertFalse(redacted.contains("1234"))
    }

    @Test
    fun redactUri_fileUri_redacted() {
        val redacted = engine.redactUriForManifest("file:///data/data/com.novacut.editor/files/media/imports/video.mp4", "asset-002")
        assertEquals("asset://asset-002", redacted)
        assertFalse(redacted.contains("file://"))
        assertFalse(redacted.contains("video.mp4"))
    }
}

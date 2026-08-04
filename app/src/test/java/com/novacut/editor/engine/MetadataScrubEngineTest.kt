package com.novacut.editor.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.FileOutputStream

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
    fun canScrub_webpAndTiff_useReencodePaths() {
        assertTrue(engine.canScrub("image/webp"))
        assertTrue(engine.canScrub("image/tiff"))
        assertTrue(engine.canScrub("image/x-tiff; charset=binary"))
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

@RunWith(RobolectricTestRunner::class)
class MetadataScrubEngineReencodeTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun scrubImage_webpReencodesToReadableOutput() = runBlocking {
        val input = temp.newFile("source.webp")
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        try {
            FileOutputStream(input).use { stream ->
                @Suppress("DEPRECATION")
                assertTrue(bitmap.compress(Bitmap.CompressFormat.WEBP, 100, stream))
            }
        } finally {
            bitmap.recycle()
        }

        val output = temp.root.resolve("scrubbed.webp")
        val result = MetadataScrubEngine().scrubImage(input, output)

        assertNotNull(result)
        assertTrue(output.isFile)
        assertTrue(output.length() > 0L)
        assertNotNull(BitmapFactory.decodeFile(output.absolutePath))
    }
}

package com.novacut.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class VideoFrameRatePolicyTest {

    @Test
    fun normalizesFractionalMetadataToTheNearestSupportedInteger() {
        assertEquals(24, normalizedVideoFrameRate(23.976f))
        assertEquals(30, normalizedVideoFrameRate(29.97f))
        assertEquals(60, normalizedVideoFrameRate(59.94f))
        assertEquals(120, normalizedVideoFrameRate(240f))
    }

    @Test
    fun rejectsMissingNonFiniteAndNonPositiveMetadata() {
        assertNull(normalizedVideoFrameRate(null))
        assertNull(normalizedVideoFrameRate(0f))
        assertNull(normalizedVideoFrameRate(-24f))
        assertNull(normalizedVideoFrameRate(Float.NaN))
        assertNull(normalizedVideoFrameRate(Float.POSITIVE_INFINITY))
        assertTrue(normalizedVideoFrameRate(0.4f)!! >= 1)
    }

    @Test
    fun videoRateProbeFallsBackToTheVideoTrackFormatBeforeThirtyFps() {
        val source = locate(
            "app/src/main/java/com/novacut/editor/engine/VideoEngine.kt"
        ).readText()

        assertTrue(source.contains("MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE"))
        assertTrue(source.contains("normalizedVideoFrameRate(readVideoTrackFrameRate(uri))"))
        assertTrue(source.contains("extractor.setDataSource(context, uri, emptyMap())"))
        assertTrue(source.contains("MediaFormat.KEY_FRAME_RATE"))
        assertTrue(source.contains("mime?.startsWith(\"video/\", ignoreCase = true)"))
        assertTrue(source.contains("?: 30"))
    }

    private fun locate(path: String): File {
        var directory = File(System.getProperty("user.dir") ?: error("Could not read user.dir"))
            .absoluteFile
        repeat(8) {
            val candidate = File(directory, path)
            if (candidate.isFile) return candidate
            directory = directory.parentFile ?: error("Could not locate $path")
        }
        error("Could not locate $path")
    }
}

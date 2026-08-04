package com.novacut.editor.ui.mediapicker

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaDropPolicyTest {

    @Test
    fun acceptsMediaAndFileManagerDragDescriptions() {
        listOf(
            "video/mp4",
            "image/jpeg",
            "audio/mpeg",
            "application/octet-stream",
            "text/uri-list",
            "*/*",
        ).forEach { mimeType ->
            assertTrue(mimeType, isSupportedMediaDropMimeType(mimeType))
        }
    }

    @Test
    fun rejectsNonMediaTextAndDocumentDescriptions() {
        listOf("text/plain", "application/pdf", "application/json", "text/html").forEach { mimeType ->
            assertFalse(mimeType, isSupportedMediaDropMimeType(mimeType))
        }
    }

    @Test
    fun matchingIsCaseAndWhitespaceInsensitive() {
        assertTrue(isSupportedMediaDropMimeType("  VIDEO/MP4 "))
        assertTrue(isSupportedMediaDropMimeType(" Text/Uri-List "))
    }
}

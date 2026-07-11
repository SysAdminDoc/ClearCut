package com.novacut.editor.ui.editor

import com.novacut.editor.model.TextOverlay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewOverlayPolicyTest {

    @Test
    fun `text overlays are active through gaps and use an exclusive end boundary`() {
        val title = TextOverlay(
            id = "title",
            text = "Opening title",
            startTimeMs = 1_000L,
            endTimeMs = 2_000L,
        )

        assertTrue(activePreviewTextOverlays(listOf(title), 999L).isEmpty())
        assertEquals(listOf(title), activePreviewTextOverlays(listOf(title), 1_000L))
        assertEquals(listOf(title), activePreviewTextOverlays(listOf(title), 1_999L))
        assertTrue(activePreviewTextOverlays(listOf(title), 2_000L).isEmpty())
    }
}

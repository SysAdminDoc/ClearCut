package com.novacut.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportImageOverlayTest {

    @Test
    fun isActiveAt_includesStartAndEndOfOverlayWindow() {
        assertFalse(ExportImageOverlay.isActiveAt(999L, 1_000L, 2_000L))
        assertTrue(ExportImageOverlay.isActiveAt(1_000L, 1_000L, 2_000L))
        assertTrue(ExportImageOverlay.isActiveAt(2_000L, 1_000L, 2_000L))
        assertFalse(ExportImageOverlay.isActiveAt(2_001L, 1_000L, 2_000L))
    }

    @Test
    fun outputWidthForFrame_treatsScaleAsOutputWidthFraction() {
        assertEquals(576, ExportImageOverlay.outputWidthForFrame(0.3f, 1920))
        assertEquals(1, ExportImageOverlay.outputWidthForFrame(-1f, 100))
        assertEquals(200, ExportImageOverlay.outputWidthForFrame(100f, 100))
    }

    @Test
    fun backgroundAnchorsMatchPreviewCenterOffsetSemantics() {
        assertEquals(0.25f, ExportImageOverlay.backgroundAnchorX(0.25f), 0.0001f)
        assertEquals(-0.25f, ExportImageOverlay.backgroundAnchorY(0.25f), 0.0001f)
        assertEquals(0f, ExportImageOverlay.backgroundAnchorX(Float.POSITIVE_INFINITY), 0.0001f)
        assertEquals(0f, ExportImageOverlay.backgroundAnchorY(Float.NaN), 0.0001f)
    }
}

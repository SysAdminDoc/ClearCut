package com.novacut.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GifExportPolicyTest {

    @Test
    fun aspectPreservingThumbnailSizeHandlesLandscapePortraitAndSquareSources() {
        assertEquals(480 to 270, aspectPreservingThumbnailSize(480, 1920, 1080))
        assertEquals(480 to 853, aspectPreservingThumbnailSize(480, 1080, 1920))
        assertEquals(480 to 480, aspectPreservingThumbnailSize(480, 1000, 1000))
    }

    @Test
    fun invalidSourceDimensionsUseSafeFallbackHeight() {
        assertEquals(480 to 90, aspectPreservingThumbnailSize(480, 0, 0))
        assertEquals(1 to 1, aspectPreservingThumbnailSize(0, 1, 1))
    }

    @Test
    fun paletteOverflowUsesNearestColorInsteadOfIndexZero() {
        val palette = listOf(0x000000, 0xFFFFFF, 0x00FF00)

        assertEquals(1, nearestGifPaletteIndex(0xF0F0F0, palette))
        assertEquals(2, nearestGifPaletteIndex(0x10E820, palette))
        assertTrue(nearestGifPaletteIndex(0x123456, emptyList()) == 0)
    }

    @Test
    fun quantizedKeysCanBeComparedAsRepresentativeRgbColors() {
        assertEquals(0xA0B0C0, quantizedGifRgb(0xA0BC))
    }

    @Test
    fun logicalScreenIncludesPortraitSourcesAndTheConfiguredGapCanvas() {
        assertEquals(
            480 to 853,
            gifLogicalScreenSize(
                targetWidth = 480,
                aspectRatio = 16f / 9f,
                sourceSizes = listOf(1920 to 1080, 1080 to 1920),
            ),
        )
    }

    @Test
    fun logicalScreenFallsBackToSafeDimensionsForInvalidAspectRatio() {
        assertEquals(
            640 to 640,
            gifLogicalScreenSize(
                targetWidth = 640,
                aspectRatio = 0f,
                sourceSizes = emptyList(),
            ),
        )
    }
}

package com.novacut.editor.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReframePanLimitsTest {

    private val eps = 1e-4f

    @Test
    fun sameAspectHasNoPan() {
        val (x, y) = reframePanLimits(16f / 9f, 16f / 9f)
        assertEquals(0f, x, eps)
        assertEquals(0f, y, eps)
    }

    @Test
    fun landscapeToPortraitPansHorizontallyOnly() {
        // 16:9 -> 9:16 fills height; only horizontal headroom.
        val (x, y) = reframePanLimits(16f / 9f, 9f / 16f)
        assertTrue("horizontal pan headroom expected", x > 0.5f)
        assertEquals("no vertical pan when height is filled", 0f, y, eps)
    }

    @Test
    fun portraitToLandscapePansVerticallyOnly() {
        // 9:16 -> 16:9 fills width; only vertical headroom.
        val (x, y) = reframePanLimits(9f / 16f, 16f / 9f)
        assertEquals("no horizontal pan when width is filled", 0f, x, eps)
        assertTrue("vertical pan headroom expected", y > 0.5f)
    }

    @Test
    fun degenerateRatiosAreClampedToZero() {
        assertEquals(0f to 0f, reframePanLimits(0f, 1f))
        assertEquals(0f to 0f, reframePanLimits(1f, -2f))
    }
}

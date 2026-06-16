package com.novacut.editor.ui.editor

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransformOverlayPolicyTest {

    @Test
    fun `overlay geometry uses actual canvas size before preview fallback`() {
        val geometry = transformOverlayGeometry(
            positionX = 0.25f,
            positionY = -0.5f,
            scaleX = 1.2f,
            scaleY = 0.8f,
            actualWidth = 1080f,
            actualHeight = 607.5f,
            previewWidthFallback = 400f,
            previewHeightFallback = 225f
        )

        assertEquals(777.6f, geometry.width, 0.001f)
        assertEquals(291.6f, geometry.height, 0.001f)
        assertEquals(675f, geometry.centerX, 0.001f)
        assertEquals(151.875f, geometry.centerY, 0.001f)
    }

    @Test
    fun `overlay geometry falls back only when canvas has no size yet`() {
        val geometry = transformOverlayGeometry(
            positionX = 0f,
            positionY = 0f,
            scaleX = 1f,
            scaleY = 1f,
            actualWidth = 0f,
            actualHeight = 0f,
            previewWidthFallback = 400f,
            previewHeightFallback = 225f
        )

        assertEquals(240f, geometry.width, 0.001f)
        assertEquals(135f, geometry.height, 0.001f)
        assertEquals(200f, geometry.centerX, 0.001f)
        assertEquals(112.5f, geometry.centerY, 0.001f)
    }

    @Test
    fun `single finger pan is not handled by transform gesture path`() {
        assertFalse(
            shouldHandleTransformGesture(
                pointerCount = 1,
                zoom = 1f,
                rotation = 0f,
                pan = Offset(48f, 12f)
            )
        )
    }

    @Test
    fun `two finger transform gestures are handled`() {
        assertTrue(
            shouldHandleTransformGesture(
                pointerCount = 2,
                zoom = 1.04f,
                rotation = 0f,
                pan = Offset.Zero
            )
        )
        assertTrue(
            shouldHandleTransformGesture(
                pointerCount = 2,
                zoom = 1f,
                rotation = 2f,
                pan = Offset.Zero
            )
        )
    }

    @Test
    fun `pan delta maps pixels to normalized overlay coordinates`() {
        val (dx, dy) = transformOverlayPanToNormalizedDelta(
            pan = Offset(54f, -30f),
            width = 1080f,
            height = 600f
        )

        assertEquals(0.1f, dx, 0.001f)
        assertEquals(-0.1f, dy, 0.001f)
    }
}

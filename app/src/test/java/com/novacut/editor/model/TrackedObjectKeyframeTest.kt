package com.novacut.editor.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression coverage for the bounds guards on [TrackedObjectKeyframe]. The
 * model holds normalised mask coordinates fed into per-frame mosaic / blur
 * shaders — letting NaN or out-of-range values slip in produces giant
 * off-screen rectangles or garbled pixel reads, so we reject them at the
 * model boundary rather than every consumer having to defend itself.
 */
class TrackedObjectKeyframeTest {

    private fun keyframe(
        clipTimeMs: Long = 0L,
        centerX: Float = 0.5f,
        centerY: Float = 0.5f,
        width: Float = 0.5f,
        height: Float = 0.5f,
        confidence: Float = 1f
    ) = TrackedObjectKeyframe(
        clipTimeMs = clipTimeMs,
        centerX = centerX,
        centerY = centerY,
        width = width,
        height = height,
        confidence = confidence
    )

    @Test
    fun `accepts canonical center-and-size in unit square`() {
        val k = keyframe()
        assertEquals(0.5f, k.centerX, 0f)
        assertEquals(0.5f, k.centerY, 0f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects NaN centerX`() {
        keyframe(centerX = Float.NaN)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects NaN centerY`() {
        keyframe(centerY = Float.NaN)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects positive infinity center`() {
        keyframe(centerX = Float.POSITIVE_INFINITY)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects centerX above 1`() {
        keyframe(centerX = 1.5f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects centerX below 0`() {
        keyframe(centerX = -0.001f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects negative clipTimeMs`() {
        keyframe(clipTimeMs = -5L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects zero width`() {
        keyframe(width = 0f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects width above 1`() {
        keyframe(width = 1.0001f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects NaN width`() {
        keyframe(width = Float.NaN)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects confidence below 0`() {
        keyframe(confidence = -0.1f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects confidence above 1`() {
        keyframe(confidence = 1.1f)
    }

    @Test
    fun `accepts boundary values 0 and 1 for center`() {
        // Coordinates at exactly 0 and 1 are valid (object touching edge).
        keyframe(centerX = 0f, centerY = 1f)
    }

    @Test
    fun `keyframeAt picks the closest sample`() {
        val obj = TrackedObject(
            label = "Subject",
            sourceClipId = "clip-1",
            keyframes = listOf(
                keyframe(clipTimeMs = 0L, centerX = 0.2f),
                keyframe(clipTimeMs = 1000L, centerX = 0.5f),
                keyframe(clipTimeMs = 2000L, centerX = 0.8f)
            )
        )
        assertEquals(0.5f, obj.keyframeAt(900L)?.centerX)
        assertEquals(0.8f, obj.keyframeAt(2100L)?.centerX)
    }

    @Test
    fun `keyframeAt returns null on empty track`() {
        val obj = TrackedObject(label = "Subject", sourceClipId = "clip-1")
        assertNull(obj.keyframeAt(500L))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `TrackedObject rejects blank label`() {
        TrackedObject(label = "  ", sourceClipId = "clip-1")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `TrackedObject rejects blank sourceClipId`() {
        TrackedObject(label = "Subject", sourceClipId = "")
    }

    @Test
    fun `mask polygon defaults to empty`() {
        val k = keyframe()
        assertTrue(k.maskPolygon.isEmpty())
    }
}

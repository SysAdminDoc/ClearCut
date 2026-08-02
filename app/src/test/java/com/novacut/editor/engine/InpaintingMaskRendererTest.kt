package com.novacut.editor.engine

import com.novacut.editor.model.Mask
import com.novacut.editor.model.MaskPoint
import com.novacut.editor.model.MaskType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InpaintingMaskRendererTest {

    @Test
    fun rectangleEllipseAndClosedFreehandMasksAreSupported() {
        assertEquals(
            InpaintingMaskRenderer.ShapeSupport.RECTANGLE,
            InpaintingMaskRenderer.supportFor(
                Mask(
                    type = MaskType.RECTANGLE,
                    points = listOf(MaskPoint(0.1f, 0.1f), MaskPoint(0.4f, 0.4f))
                )
            )
        )
        assertEquals(
            InpaintingMaskRenderer.ShapeSupport.ELLIPSE,
            InpaintingMaskRenderer.supportFor(
                Mask(
                    type = MaskType.ELLIPSE,
                    points = listOf(MaskPoint(0.5f, 0.5f), MaskPoint(0.2f, 0.2f))
                )
            )
        )
        assertTrue(
            InpaintingMaskRenderer.supports(
                Mask(
                    type = MaskType.FREEHAND,
                    points = listOf(
                        MaskPoint(0.1f, 0.1f),
                        MaskPoint(0.4f, 0.1f),
                        MaskPoint(0.2f, 0.4f)
                    )
                )
            )
        )
    }

    @Test
    fun gradientsAndIncompleteGeometryAreRejectedBeforeInference() {
        assertFalse(
            InpaintingMaskRenderer.supports(
                Mask(
                    type = MaskType.LINEAR_GRADIENT,
                    points = listOf(MaskPoint(0.1f, 0.1f), MaskPoint(0.9f, 0.9f))
                )
            )
        )
        assertFalse(
            InpaintingMaskRenderer.supports(
                Mask(type = MaskType.FREEHAND, points = listOf(MaskPoint(0.1f, 0.1f)))
            )
        )
        assertEquals(
            InpaintingMaskRenderer.ShapeSupport.UNSUPPORTED,
            InpaintingMaskRenderer.supportFor(
                Mask(type = MaskType.RECTANGLE, points = listOf(MaskPoint(0.1f, 0.1f)))
            )
        )
    }
}

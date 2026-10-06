package com.novacut.editor.ui.editor

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.novacut.editor.model.Mask
import com.novacut.editor.model.MaskPoint
import com.novacut.editor.model.MaskType
import com.novacut.editor.ui.ClearCutTestTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The mask editor is withheld from the editor until compositing can honor masks, so
 * its handle drag is driven on the overlay itself: the drag has to move the grabbed
 * point to where the finger lifted, leave the other point alone, and open and close
 * exactly one gesture for the undo entry the editor records around it.
 */
@RunWith(AndroidJUnit4::class)
class MaskHandleDragInstrumentationTest {
    @get:Rule
    val compose = createComposeRule()

    private val rectangle = Mask(
        type = MaskType.RECTANGLE,
        points = listOf(MaskPoint(0.25f, 0.25f), MaskPoint(0.75f, 0.75f)),
    )
    private var masks by mutableStateOf(listOf(rectangle))
    private var starts = 0
    private var ends = 0
    private val movedIndices = mutableListOf<Int>()

    private fun showOverlay() {
        compose.setContent {
            MaskPreviewOverlay(
                masks = masks,
                selectedMaskId = rectangle.id,
                previewWidth = 1f,
                previewHeight = 1f,
                onMaskPointMoved = { maskId, index, x, y ->
                    movedIndices += index
                    masks = masks.map { mask ->
                        if (mask.id != maskId) return@map mask
                        mask.copy(points = mask.points.toMutableList().apply { set(index, get(index).copy(x = x, y = y)) })
                    }
                },
                onFreehandDraw = { _, _ -> },
                modifier = Modifier.size(320.dp),
                onMaskDragStarted = { starts++ },
                onMaskDragEnded = { ends++ },
            )
        }
    }

    private fun drag(from: (Float, Float) -> Offset, to: (Float, Float) -> Offset) {
        val overlay = compose.onNodeWithTag(ClearCutTestTags.MASK_PREVIEW_OVERLAY)
        val size = overlay.fetchSemanticsNode().size
        val start = from(size.width.toFloat(), size.height.toFloat())
        val end = to(size.width.toFloat(), size.height.toFloat())
        overlay.performTouchInput {
            down(start)
            for (step in 1..16) moveTo(start + (end - start) * (step / 16f))
            up()
        }
        compose.waitForIdle()
    }

    @Test
    fun draggingAHandleMovesThatPointAndOpensAndClosesOneGesture() {
        showOverlay()

        drag(from = { w, h -> Offset(w * 0.75f, h * 0.75f) }, to = { w, h -> Offset(w * 0.4f, h * 0.9f) })

        assertEquals("one gesture opened", 1, starts)
        assertEquals("one gesture closed", 1, ends)
        assertTrue("only the grabbed handle moves", movedIndices.isNotEmpty() && movedIndices.all { it == 1 })
        val (untouched, moved) = masks.single().points
        assertEquals(0.4f, moved.x, 0.01f)
        assertEquals(0.9f, moved.y, 0.01f)
        assertEquals(MaskPoint(0.25f, 0.25f), untouched)
    }

    @Test
    fun aDragThatMissesEveryHandleLeavesTheMaskAndTheUndoStackAlone() {
        showOverlay()

        drag(from = { w, h -> Offset(w * 0.5f, h * 0.5f) }, to = { w, h -> Offset(w * 0.1f, h * 0.1f) })

        assertEquals(0, starts)
        assertEquals(0, ends)
        assertEquals(emptyList<Int>(), movedIndices)
        assertEquals(rectangle.points, masks.single().points)
    }
}

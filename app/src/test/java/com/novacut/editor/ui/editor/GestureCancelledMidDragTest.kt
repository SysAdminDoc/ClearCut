package com.novacut.editor.ui.editor

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.novacut.editor.model.Keyframe
import com.novacut.editor.model.KeyframeProperty
import com.novacut.editor.model.Mask
import com.novacut.editor.model.MaskPoint
import com.novacut.editor.model.MaskType
import com.novacut.editor.ui.ClearCutTestTags
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A handle drag opens one undo gesture in the editor. When the keyframe panel or the
 * mask overlay leaves the screen mid-drag, or another mask is selected (which restarts
 * the overlay's gesture coroutine without onDragCancel), the gesture has to close
 * anyway or the next drag inherits its stale snapshot.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GestureCancelledMidDragTest {
    @get:Rule
    val compose = createComposeRule()

    private var shown by mutableStateOf(true)
    private var starts = 0
    private var ends = 0

    private fun pressAndMove(tag: String, from: (Float, Float) -> Offset) {
        val node = compose.onNodeWithTag(tag)
        val size = node.fetchSemanticsNode().size
        val start = from(size.width.toFloat(), size.height.toFloat())
        node.performTouchInput {
            down(start)
            for (step in 1..8) moveTo(start + Offset(step * 8f, step * 6f))
        }
        compose.waitForIdle()
    }

    private fun liftAfterRemoval() {
        // The finger is still down in the test's input state; let it go so the next
        // drag can press again.
        compose.onRoot().performTouchInput { cancel() }
        compose.waitForIdle()
    }

    private fun dragFully(tag: String, from: (Float, Float) -> Offset) {
        pressAndMove(tag, from)
        compose.onNodeWithTag(tag).performTouchInput { up() }
        compose.waitForIdle()
    }

    @Test
    fun removingTheKeyframeCurveMidDragClosesItsGesture() {
        val keyframe = Keyframe(timeOffsetMs = 5_000L, property = KeyframeProperty.POSITION_X, value = 0f)
        var keyframes by mutableStateOf(listOf(keyframe))
        compose.setContent {
            if (shown) {
                CurveCanvas(
                    keyframes = keyframes,
                    clipDurationMs = 10_000L,
                    playheadMs = 0L,
                    activeProperties = setOf(KeyframeProperty.POSITION_X),
                    selectedKeyframe = null,
                    onKeyframeSelected = {},
                    onKeyframeMoved = { moved, time, value ->
                        keyframes = keyframes.map { if (it == moved) it.copy(timeOffsetMs = time, value = value) else it }
                    },
                    onAddKeyframe = { _, _, _ -> },
                    modifier = Modifier.size(320.dp).testTag(CURVE),
                    onDragStarted = { starts++ },
                    onDragEnded = { ends++ },
                )
            }
        }
        val onKeyframe: (Float, Float) -> Offset = { w, h -> Offset(w / 2f, h / 2f) }

        pressAndMove(CURVE, onKeyframe)
        assertEquals("the drag opened a gesture", 1, starts)
        assertEquals("nothing closed it yet", 0, ends)

        shown = false
        compose.waitForIdle()
        assertEquals("removing the panel closed the gesture", 1, ends)

        liftAfterRemoval()
        assertEquals("letting go afterward closes nothing twice", 1, ends)
        shown = true
        compose.waitForIdle()
        dragFully(CURVE) { w, h ->
            val moved = keyframes.single()
            Offset(w * moved.timeOffsetMs / 10_000f, h * (1f - (moved.value + 1f) / 2f))
        }
        assertEquals("the next drag opens its own gesture", 2, starts)
        assertEquals("and closes it once", 2, ends)
    }

    @Test
    fun removingOrReselectingTheMaskOverlayMidDragClosesItsGesture() {
        val rectangle = Mask(type = MaskType.RECTANGLE, points = listOf(MaskPoint(0.25f, 0.25f), MaskPoint(0.75f, 0.75f)))
        val second = Mask(type = MaskType.RECTANGLE, points = listOf(MaskPoint(0.25f, 0.25f), MaskPoint(0.75f, 0.75f)))
        var selected by mutableStateOf<String?>(rectangle.id)
        compose.setContent {
            if (shown) {
                MaskPreviewOverlay(
                    masks = listOf(rectangle, second),
                    selectedMaskId = selected,
                    previewWidth = 1f,
                    previewHeight = 1f,
                    onMaskPointMoved = { _, _, _, _ -> },
                    onFreehandDraw = { _, _ -> },
                    modifier = Modifier.size(320.dp),
                    onMaskDragStarted = { starts++ },
                    onMaskDragEnded = { ends++ },
                )
            }
        }
        val onHandle: (Float, Float) -> Offset = { w, h -> Offset(w * 0.75f, h * 0.75f) }
        val overlay = ClearCutTestTags.MASK_PREVIEW_OVERLAY

        pressAndMove(overlay, onHandle)
        assertEquals(1, starts)
        shown = false
        compose.waitForIdle()
        assertEquals("removing the overlay closed the gesture", 1, ends)
        liftAfterRemoval()

        shown = true
        compose.waitForIdle()
        pressAndMove(overlay, onHandle)
        assertEquals(2, starts)
        selected = second.id
        compose.waitForIdle()
        assertEquals("switching masks closed the gesture", 2, ends)
        compose.onNodeWithTag(overlay).performTouchInput { up() }
        compose.waitForIdle()
        assertEquals("lifting the finger afterward closes nothing twice", 2, ends)

        dragFully(overlay, onHandle)
        assertEquals(3, starts)
        assertEquals(3, ends)
    }

    private companion object {
        const val CURVE = "gesture-test-curve"
    }
}

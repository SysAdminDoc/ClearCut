package com.novacut.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.novacut.editor.model.Clip
import com.novacut.editor.ui.ClearCutTestTags
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * Drags, swipes and pinches on the real editor, each checked against what the editor
 * saved and against the Version history panel. Zoom and scroll are read back from the
 * clip block's geometry, which the timeline draws straight from them.
 */
@RunWith(AndroidJUnit4::class)
class QaTimelineGestureTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val editor = QaEditorHarness(compose, fixtureName = "qa-gesture-fixture")

    @Before
    fun setUp() = editor.setUp()

    @After
    fun tearDown() = editor.tearDown()

    @Test
    fun draggingAClipAlongItsTrackSlidesItAndRecordsOneUndoEntry() {
        val clip = editor.importFixture()
        val history = editor.undoHistory()
        val block = editor.clipNode(clip)
        val ppm = editor.pixelsPerMs(clip, block)
        val center = block.positionInRoot + Offset(block.size.width / 2f, block.size.height / 2f)
        val dragPx = block.size.width * 0.3f

        editor.dragInRoot(center, center + Offset(dragPx, 0f))

        val slid = editor.awaitClip(clip.id) { it.timelineStartMs != clip.timelineStartMs }
        assertMovedBy(slid.timelineStartMs - clip.timelineStartMs, dragPx, ppm, "slide")
        assertEquals("a slide keeps the source range", clip.trimStartMs, slid.trimStartMs)
        assertEquals("a slide keeps the source range", clip.trimEndMs, slid.trimEndMs)
        assertEquals(listOf("Slide edit") + history, editor.undoHistory())
    }

    @Test
    fun draggingATrimEdgeTrimsTheClipAndRecordsOneUndoEntry() {
        val clip = editor.importFixture()
        val history = editor.undoHistory()
        val block = editor.clipNode(clip)
        val ppm = editor.pixelsPerMs(clip, block)
        val rightEdge = block.positionInRoot +
            Offset(block.size.width - 8f * editor.density, block.size.height / 2f)
        val dragPx = block.size.width * 0.25f

        editor.dragInRoot(rightEdge, rightEdge - Offset(dragPx, 0f))

        val trimmed = editor.awaitClip(clip.id) { it.trimEndMs != clip.trimEndMs }
        assertMovedBy(clip.trimEndMs - trimmed.trimEndMs, dragPx, ppm, "trim")
        assertEquals("an end trim keeps the start", clip.trimStartMs, trimmed.trimStartMs)
        assertEquals("an end trim keeps the position", clip.timelineStartMs, trimmed.timelineStartMs)
        assertEquals(listOf("Trim clip") + history, editor.undoHistory())
    }

    @Test
    fun pinchingTheTimelineZoomsInWithoutAnUndoEntry() {
        val clip = editor.importFixture()
        // Zoom out first. A new project is fitted to fill the timeline, and the clip
        // block is measured against the timeline's width, so it has to stay narrower
        // than the timeline at the end of the pinch for its width to read the zoom.
        val zoomOut = editor.targetContext.getString(R.string.cd_zoom_out)
        repeat(5) {
            compose.onNodeWithContentDescription(zoomOut).performClick()
            compose.waitForIdle()
        }
        val history = editor.undoHistory()
        val before = editor.clipNode(clip)
        val scaleBefore = editor.pixelsPerMs(clip, before)
        val clipRight = before.positionInRoot.x + before.size.width
        val y = before.positionInRoot.y + before.size.height / 2f
        val dp = editor.density

        // Both fingers land on the bare track to the clip's right and spread from
        // 30dp apart to 125dp apart.
        editor.touchInRoot { toLocal ->
            pinch(
                start0 = toLocal(Offset(clipRight + 40f * dp, y)),
                end0 = toLocal(Offset(clipRight + 15f * dp, y)),
                start1 = toLocal(Offset(clipRight + 70f * dp, y)),
                end1 = toLocal(Offset(clipRight + 140f * dp, y)),
                durationMillis = 600L,
            )
        }

        // The spread is about 4.2x; the detector spends the start of it crossing its
        // touch slop, so the timeline zooms in by less than that, never more.
        val zoom = editor.pixelsPerMs(clip) / scaleBefore
        assertTrue("pinching out should zoom in, zoomed ${"%.2f".format(zoom)}x", zoom in 1.5f..4.3f)
        assertEquals("zooming is a view change, not an edit", history, editor.undoHistory())
    }

    @Test
    fun swipingTheTimelineScrollsItWithoutAnUndoEntry() {
        val clip = editor.importFixture()
        val history = editor.undoHistory()
        val block = editor.clipNode(clip)
        val ppm = editor.pixelsPerMs(clip, block)
        // A SemanticsNode reads its position live, so keep the number, not the node.
        val leftBefore = block.positionInRoot.x
        val clipRight = leftBefore + block.size.width
        val y = block.positionInRoot.y + block.size.height / 2f
        val start = Offset(clipRight + 10f * editor.density, y)

        editor.dragInRoot(start, start - Offset(block.size.width * 0.4f, 0f))

        val after = editor.clipNode(clip)
        val scrolledMs = (leftBefore - after.positionInRoot.x) / ppm
        // A three-second project shown whole can scroll about half a second before the
        // editor's lead-out limit stops it.
        assertTrue("swiping left should scroll forward, scrolled ${scrolledMs}ms", scrolledMs in 200f..clip.durationMs.toFloat())
        assertEquals("a scroll keeps the zoom", ppm, editor.pixelsPerMs(clip, after), 0.0005f)
        assertEquals("scrolling is a view change, not an edit", history, editor.undoHistory())
    }

    @Test
    fun draggingAKeyframeHandleMovesItAndRecordsOneUndoEntry() {
        val clip = editor.importFixture()
        openKeyframePanel(clip)

        val canvas = compose.onNodeWithTag(ClearCutTestTags.KEYFRAME_CURVE_CANVAS)
        val size = canvas.fetchSemanticsNode().size
        val grab = Offset(size.width * 0.25f, size.height * 0.5f)
        canvas.performTouchInput { doubleClick(grab) }
        val added = editor.awaitClip(clip.id) { it.keyframes.size == 1 }.keyframes.single()
        assertNear("double-tap time", clip.durationMs * 0.25f, added.timeOffsetMs.toFloat(), clip.durationMs * 0.04f)
        // On a phone the panel covers the top bar, so close it to read the history.
        closeKeyframePanel()
        val history = editor.undoHistory()
        assertEquals("Add keyframe", history.first())

        openKeyframePanel(clip)
        val drop = Offset(size.width * 0.6f, size.height * 0.25f)
        compose.onNodeWithTag(ClearCutTestTags.KEYFRAME_CURVE_CANVAS).performTouchInput {
            down(grab)
            for (step in 1..16) moveTo(grab + (drop - grab) * (step / 16f))
            up()
        }
        compose.waitForIdle()
        closeKeyframePanel()

        val moved = editor.awaitClip(clip.id) { it.keyframes.singleOrNull()?.timeOffsetMs != added.timeOffsetMs }
            .keyframes.single()
        assertEquals(added.property, moved.property)
        assertNear("dragged time", clip.durationMs * 0.6f, moved.timeOffsetMs.toFloat(), clip.durationMs * 0.04f)
        assertTrue("dragging up raises the value (${added.value} -> ${moved.value})", moved.value > added.value)
        assertEquals(listOf("Move keyframe") + history, editor.undoHistory())
    }

    private fun openKeyframePanel(clip: Clip) {
        compose.onNodeWithTag(ClearCutTestTags.TIMELINE_CLIP_PREFIX + clip.id).performClick()
        compose.onNodeWithTag(ClearCutTestTags.EDITOR_TOOL_TAB_PREFIX + "more").performClick()
        editor.waitForTag(ClearCutTestTags.EDITOR_TOOL_ACTION_LIST)
        compose.onNodeWithTag(ClearCutTestTags.EDITOR_TOOL_ACTION_LIST)
            .performScrollToNode(hasTestTag(ClearCutTestTags.EDITOR_TOOL_ACTION_PREFIX + "keyframes"))
        compose.onNodeWithTag(ClearCutTestTags.EDITOR_TOOL_ACTION_PREFIX + "keyframes").performClick()
        editor.waitForTag(ClearCutTestTags.KEYFRAME_CURVE_CANVAS)
    }

    private fun closeKeyframePanel() {
        compose.onNodeWithContentDescription(editor.targetContext.getString(R.string.panel_keyframes_close_cd))
            .performClick()
        compose.waitUntil(timeoutMillis = 5_000L) {
            compose.onAllNodesWithTag(ClearCutTestTags.KEYFRAME_CURVE_CANVAS).fetchSemanticsNodes().isEmpty()
        }
    }

    /**
     * A drag moves its target by the finger's travel less the touch slop the detector
     * keeps, quantized to project frames. Allow the slop and a few frames either way.
     */
    private fun assertMovedBy(movedMs: Long, dragPx: Float, pixelsPerMs: Float, what: String) {
        val expectedMs = (dragPx - editor.touchSlopPx) / pixelsPerMs
        val toleranceMs = editor.touchSlopPx / pixelsPerMs + FRAME_TOLERANCE_MS
        assertNear("$what distance", expectedMs, movedMs.toFloat(), toleranceMs)
    }

    private fun assertNear(what: String, expected: Float, actual: Float, tolerance: Float) {
        assertTrue("$what: expected about $expected, got $actual (±$tolerance)", abs(actual - expected) <= tolerance)
    }

    private companion object {
        const val FRAME_TOLERANCE_MS = 100f
    }
}

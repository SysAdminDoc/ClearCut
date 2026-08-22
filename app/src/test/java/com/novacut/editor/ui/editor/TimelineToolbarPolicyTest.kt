package com.novacut.editor.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineToolbarPolicyTest {

    @Test
    fun zoomStepsStayWithinTheEditorBounds() {
        assertEquals(
            TIMELINE_TOOLBAR_MIN_ZOOM,
            TimelineToolbarPolicy.zoomOut(TIMELINE_TOOLBAR_MIN_ZOOM),
            0f,
        )
        assertEquals(
            TIMELINE_TOOLBAR_MAX_ZOOM,
            TimelineToolbarPolicy.zoomIn(TIMELINE_TOOLBAR_MAX_ZOOM),
            0f,
        )
        assertEquals(0.75f, TimelineToolbarPolicy.zoomOut(1f), 0.0001f)
        assertEquals(1.33f, TimelineToolbarPolicy.zoomIn(1f), 0.0001f)
    }

    @Test
    fun zoomActionsNormalizeOutOfRangeInputs() {
        assertEquals(
            TIMELINE_TOOLBAR_MIN_ZOOM,
            TimelineToolbarPolicy.zoomOut(-2f),
            0f,
        )
        assertEquals(
            TIMELINE_TOOLBAR_MAX_ZOOM,
            TimelineToolbarPolicy.zoomIn(200f),
            0f,
        )
        assertEquals(
            TIMELINE_TOOLBAR_MIN_ZOOM,
            TimelineToolbarPolicy.clampZoom(Float.NaN),
            0f,
        )
    }
}

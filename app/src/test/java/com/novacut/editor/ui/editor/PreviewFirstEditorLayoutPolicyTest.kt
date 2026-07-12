package com.novacut.editor.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewFirstEditorLayoutPolicyTest {

    @Test
    fun compactPhoneTimelineEditReservesUsablePreviewAndLocksToolRail() {
        val decision = PreviewFirstEditorLayoutPolicy.decide(
            paneMode = AdaptiveEditorLayoutPolicy.PaneMode.SINGLE_PANE,
            screenHeightDp = 891,
            compactEditorHeight = true,
            clipSelected = true,
            currentTool = EditorTool.TRIM,
            openPanels = emptySet(),
            selectedEffectActive = false,
            bottomToolPanelExpanded = true,
            timelineEditGestureActive = true
        )

        assertTrue(decision.previewCritical)
        assertTrue(decision.lockBottomToolArea)
        assertEquals(64, decision.bottomToolRailHeightDp)
        assertEquals(280, decision.previewMinHeightDp)
        assertEquals(200, decision.timelineMinHeightDp)
        assertEquals(260, decision.timelineMaxHeightDp)
    }

    @Test
    fun slideGestureIsPreviewCriticalEvenWithoutTrimTool() {
        val decision = PreviewFirstEditorLayoutPolicy.decide(
            paneMode = AdaptiveEditorLayoutPolicy.PaneMode.SINGLE_PANE,
            screenHeightDp = 760,
            compactEditorHeight = true,
            clipSelected = true,
            currentTool = EditorTool.NONE,
            openPanels = emptySet(),
            selectedEffectActive = false,
            bottomToolPanelExpanded = true,
            timelineEditGestureActive = true
        )

        assertTrue(decision.previewCritical)
        assertTrue(decision.lockBottomToolArea)
        assertEquals(280, decision.previewMinHeightDp)
    }

    @Test
    fun speedAndCropPanelsUsePreviewFirstPhoneBudget() {
        val speed = phoneDecision(openPanels = setOf(PanelId.SPEED_CURVE))
        val crop = phoneDecision(openPanels = setOf(PanelId.CROP))

        assertTrue(speed.previewCritical)
        assertTrue(crop.previewCritical)
        assertEquals(280, speed.previewMinHeightDp)
        assertEquals(280, crop.previewMinHeightDp)
        assertEquals(260, speed.timelineMaxHeightDp)
        assertEquals(260, crop.timelineMaxHeightDp)
    }

    @Test
    fun normalClipModeKeepsExpandableToolsAndExistingCompactPreview() {
        val decision = phoneDecision(openPanels = emptySet(), bottomToolPanelExpanded = true)

        assertFalse(decision.previewCritical)
        assertFalse(decision.lockBottomToolArea)
        assertEquals(126, decision.bottomToolRailHeightDp)
        assertEquals(210, decision.previewMinHeightDp)
        assertEquals(220, decision.timelineMinHeightDp)
        assertEquals(260, decision.timelineMaxHeightDp)
    }

    @Test
    fun tabletPreviewCriticalPanelsDoNotCollapseToolRail() {
        val decision = PreviewFirstEditorLayoutPolicy.decide(
            paneMode = AdaptiveEditorLayoutPolicy.PaneMode.TWO_PANE,
            screenHeightDp = 840,
            compactEditorHeight = false,
            clipSelected = true,
            currentTool = EditorTool.NONE,
            openPanels = setOf(PanelId.TRANSFORM),
            selectedEffectActive = false,
            bottomToolPanelExpanded = true,
            timelineEditGestureActive = false
        )

        assertTrue(decision.previewCritical)
        assertFalse(decision.lockBottomToolArea)
        assertEquals(126, decision.bottomToolRailHeightDp)
        assertEquals(200, decision.previewMinHeightDp)
        assertEquals(252, decision.timelineMinHeightDp)
        assertEquals(360, decision.timelineMaxHeightDp)
    }

    private fun phoneDecision(
        openPanels: Set<PanelId>,
        bottomToolPanelExpanded: Boolean = true
    ): PreviewFirstEditorLayoutPolicy.Decision {
        return PreviewFirstEditorLayoutPolicy.decide(
            paneMode = AdaptiveEditorLayoutPolicy.PaneMode.SINGLE_PANE,
            screenHeightDp = 891,
            compactEditorHeight = true,
            clipSelected = true,
            currentTool = EditorTool.NONE,
            openPanels = openPanels,
            selectedEffectActive = false,
            bottomToolPanelExpanded = bottomToolPanelExpanded,
            timelineEditGestureActive = false
        )
    }
}

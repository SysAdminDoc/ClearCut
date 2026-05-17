package com.novacut.editor.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveEditorLayoutPolicyTest {

    @Test
    fun widthBreakpoints_followAndroidLargeScreenThresholds() {
        assertEquals(AdaptiveEditorLayoutPolicy.WidthClass.COMPACT, AdaptiveEditorLayoutPolicy.widthClass(599))
        assertEquals(AdaptiveEditorLayoutPolicy.WidthClass.MEDIUM, AdaptiveEditorLayoutPolicy.widthClass(600))
        assertEquals(AdaptiveEditorLayoutPolicy.WidthClass.MEDIUM, AdaptiveEditorLayoutPolicy.widthClass(839))
        assertEquals(AdaptiveEditorLayoutPolicy.WidthClass.EXPANDED, AdaptiveEditorLayoutPolicy.widthClass(840))
    }

    @Test
    fun decide_mapsCompactMediumExpandedToPaneModes() {
        assertEquals(
            AdaptiveEditorLayoutPolicy.PaneMode.SINGLE_PANE,
            AdaptiveEditorLayoutPolicy.decide(widthDp = 411, heightDp = 891).paneMode
        )
        assertEquals(
            AdaptiveEditorLayoutPolicy.PaneMode.TWO_PANE,
            AdaptiveEditorLayoutPolicy.decide(widthDp = 700, heightDp = 840).paneMode
        )
        val expanded = AdaptiveEditorLayoutPolicy.decide(widthDp = 900, heightDp = 900)
        assertEquals(AdaptiveEditorLayoutPolicy.PaneMode.THREE_PANE, expanded.paneMode)
        assertTrue(expanded.useSidebar)
    }

    @Test
    fun tabletopOverridesMediumAndExpandedPaneMode() {
        assertEquals(
            AdaptiveEditorLayoutPolicy.PaneMode.TABLETOP_SPLIT,
            AdaptiveEditorLayoutPolicy.decide(widthDp = 700, heightDp = 600, isTabletop = true).paneMode
        )
        assertEquals(
            AdaptiveEditorLayoutPolicy.PaneMode.SINGLE_PANE,
            AdaptiveEditorLayoutPolicy.decide(widthDp = 430, heightDp = 600, isTabletop = true).paneMode
        )
    }

    @Test
    fun desktopLikePromotesMediumWidthToThreePane() {
        val decision = AdaptiveEditorLayoutPolicy.decide(widthDp = 700, heightDp = 840, desktopLike = true)

        assertEquals(AdaptiveEditorLayoutPolicy.PaneMode.THREE_PANE, decision.paneMode)
        assertTrue(decision.useSidebar)
        assertFalse(decision.compactTimeline)
    }
}

package com.novacut.editor.engine

import com.novacut.editor.model.GlobalTransition
import com.novacut.editor.model.GlobalTransitionType
import com.novacut.editor.model.TransitionEasing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GlobalTransitionTest {

    @Test
    fun forClip_noOverlap_returnsNull() {
        val gt = GlobalTransition(
            type = GlobalTransitionType.FADE_TO_BLACK,
            durationMs = 1000L,
            timelineAnchorMs = 5000L
        )
        val result = GlobalTransitionEffect.forClip(listOf(gt), clipTimelineStartMs = 0L, clipTimelineEndMs = 3000L)
        assertNull(result)
    }

    @Test
    fun forClip_overlap_returnsEffect() {
        val gt = GlobalTransition(
            type = GlobalTransitionType.FADE_TO_BLACK,
            durationMs = 1000L,
            timelineAnchorMs = 2000L
        )
        val result = GlobalTransitionEffect.forClip(listOf(gt), clipTimelineStartMs = 0L, clipTimelineEndMs = 5000L)
        assertNotNull(result)
    }

    @Test
    fun forClip_transitionAtClipEnd_overlaps() {
        val gt = GlobalTransition(
            type = GlobalTransitionType.FADE_TO_BLACK,
            durationMs = 500L,
            timelineAnchorMs = 4500L
        )
        val result = GlobalTransitionEffect.forClip(listOf(gt), clipTimelineStartMs = 0L, clipTimelineEndMs = 5000L)
        assertNotNull(result)
    }

    @Test
    fun forClip_transitionAfterClip_returnsNull() {
        val gt = GlobalTransition(
            type = GlobalTransitionType.FADE_FROM_BLACK,
            durationMs = 1000L,
            timelineAnchorMs = 6000L
        )
        val result = GlobalTransitionEffect.forClip(listOf(gt), clipTimelineStartMs = 0L, clipTimelineEndMs = 5000L)
        assertNull(result)
    }

    @Test
    fun forClip_emptyList_returnsNull() {
        val result = GlobalTransitionEffect.forClip(emptyList(), clipTimelineStartMs = 0L, clipTimelineEndMs = 5000L)
        assertNull(result)
    }

    @Test
    fun globalTransition_endMs_computed() {
        val gt = GlobalTransition(
            type = GlobalTransitionType.FADE_TO_BLACK,
            durationMs = 1000L,
            timelineAnchorMs = 4000L
        )
        assertEquals(5000L, gt.endMs)
    }

    @Test
    fun autosaveRoundTrip_globalTransitions() {
        val json = """
        {
            "projectId": "test",
            "tracks": [],
            "globalTransitions": [
                {
                    "id": "gt1",
                    "type": "FADE_TO_BLACK",
                    "durationMs": 1500,
                    "timelineAnchorMs": 8000,
                    "easing": "EASE_IN_OUT"
                }
            ]
        }
        """.trimIndent()
        val restored = AutoSaveState.deserialize(json)
        assertEquals(1, restored.globalTransitions.size)
        val gt = restored.globalTransitions[0]
        assertEquals("gt1", gt.id)
        assertEquals(GlobalTransitionType.FADE_TO_BLACK, gt.type)
        assertEquals(1500L, gt.durationMs)
        assertEquals(8000L, gt.timelineAnchorMs)
        assertEquals(TransitionEasing.EASE_IN_OUT, gt.easing)
    }

    @Test
    fun autosaveRoundTrip_emptyGlobalTransitions() {
        val json = """{"projectId": "test", "tracks": []}"""
        val restored = AutoSaveState.deserialize(json)
        assertEquals(0, restored.globalTransitions.size)
    }
}

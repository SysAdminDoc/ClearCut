package com.novacut.editor.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoEditPlannerTest {
    private val planner = AutoEditPlanner()

    @Test
    fun `highlight planning is deterministic and diversifies sources`() {
        val windows = listOf(
            window("a-1", "a", 0, 0L, 2_000L, score = 1f),
            window("a-2", "a", 0, 2_000L, 4_000L, score = 0.95f),
            window("b-1", "b", 1, 1_000L, 3_000L, score = 0.90f)
        )
        val request = AutoEditPlanRequest(windows, 4_000L)

        val forward = planner.plan(request)
        val reversed = planner.plan(request.copy(windows = windows.reversed()))

        assertEquals(forward, reversed)
        assertEquals(listOf("a", "b"), forward.segments.map { it.clipId })
        assertEquals(4_000L, forward.plannedDurationMs)
        assertFalse(forward.segments[0].rationale.isEmpty())
        assertTrue(forward.confidence in 0f..1f)
    }

    @Test
    fun `overlapping windows from one source are not both selected`() {
        val plan = planner.plan(
            AutoEditPlanRequest(
                windows = listOf(
                    window("a-1", "a", 0, 0L, 3_000L, score = 1f),
                    window("a-2", "a", 0, 1_000L, 4_000L, score = 0.99f),
                    window("b-1", "b", 1, 0L, 2_000L, score = 0.5f)
                ),
                targetDurationMs = 5_000L
            )
        )

        assertEquals(listOf("a-1", "b-1"), plan.segments.map { it.windowId })
    }

    @Test
    fun `source order intent preserves source ordering instead of score rank`() {
        val plan = planner.plan(
            AutoEditPlanRequest(
                windows = listOf(
                    window("late-best", "b", 1, 0L, 2_000L, score = 1f),
                    window("early-low", "a", 0, 500L, 2_500L, score = 0.2f)
                ),
                targetDurationMs = 4_000L,
                intent = AutoEditIntent.SOURCE_ORDER
            )
        )

        assertEquals(listOf("early-low", "late-best"), plan.segments.map { it.windowId })
        assertEquals(listOf(500L, 0L), plan.segments.map { it.sourceStartMs })
    }

    @Test
    fun `target is clamped to unique available source material`() {
        val plan = planner.plan(
            AutoEditPlanRequest(
                windows = listOf(
                    window("a-1", "a", 0, 0L, 2_000L),
                    window("a-2", "a", 0, 1_000L, 3_000L)
                ),
                targetDurationMs = 10_000L
            )
        )

        assertTrue(AutoEditPlanWarning.TARGET_CLAMPED in plan.warnings)
        assertTrue(plan.plannedDurationMs <= 3_000L)
    }

    @Test
    fun `beat sync aligns proposal transitions to supported beats`() {
        val plan = planner.plan(
            AutoEditPlanRequest(
                windows = listOf(
                    window("a", "a", 0, 0L, 4_000L, audio = 1f),
                    window("b", "b", 1, 0L, 4_000L, audio = 0.9f),
                    window("c", "c", 2, 0L, 4_000L, audio = 0.8f)
                ),
                targetDurationMs = 6_000L,
                intent = AutoEditIntent.BEAT_SYNC,
                beatPositionsMs = listOf(2_000L, 4_000L)
            )
        )

        assertEquals(AutoEditBeatSupport.ALIGNED, plan.beatSupport)
        assertEquals(listOf(2_000L, 4_000L, 6_000L), plan.segments.map { it.timelineEndMs })
        assertTrue(plan.segments.all { it.beatAligned })
        assertTrue(plan.segments.all { "Cut aligned to detected beat" in it.rationale })
    }

    @Test
    fun `beat sync degrades explicitly when beat evidence is missing`() {
        val plan = planner.plan(
            AutoEditPlanRequest(
                windows = listOf(window("a", "a", 0, 0L, 3_000L)),
                targetDurationMs = 3_000L,
                intent = AutoEditIntent.BEAT_SYNC,
                beatPositionsMs = listOf(1_000L)
            )
        )

        assertEquals(AutoEditBeatSupport.DEGRADED, plan.beatSupport)
        assertTrue(AutoEditPlanWarning.BEAT_DATA_MISSING in plan.warnings)
        assertTrue(plan.segments.none { it.beatAligned })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `clip fingerprints must be stable across windows`() {
        planner.plan(
            AutoEditPlanRequest(
                windows = listOf(
                    window("a-1", "a", 0, 0L, 1_000L, fingerprint = "one"),
                    window("a-2", "a", 0, 1_000L, 2_000L, fingerprint = "two")
                ),
                targetDurationMs = 1_000L
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `window input is bounded`() {
        val windows = (0..AutoEditPlanner.MAX_WINDOWS).map { index ->
            window("w-$index", "c-$index", index, 0L, 1_000L)
        }
        planner.plan(AutoEditPlanRequest(windows, 1_000L))
    }

    @Test
    fun `sampling ranges inspect head middle and tail while staying bounded`() {
        val ranges = autoEditWindowRanges(1_000L, 121_000L, maxWindows = 12)

        assertEquals(12, ranges.size)
        assertEquals(1_000L, ranges.first().first)
        assertEquals(120_999L, ranges.last().last)
        assertTrue(ranges.all { range -> range.last - range.first + 1L <= 4_000L })
        assertTrue(ranges.zipWithNext().all { (left, right) -> left.last < right.first })
    }

    @Test
    fun `zero keyword evidence never claims a keyword match`() {
        val plan = planner.plan(
            AutoEditPlanRequest(
                windows = listOf(
                    AutoEditWindow(
                        id = "plain",
                        clipId = "clip",
                        clipFingerprint = "stable",
                        sourceOrder = 0,
                        sourceStartMs = 0L,
                        sourceEndMs = 1_000L,
                        scores = AutoEditScoreComponents(0f, 0f, 0f, 0f, 0f)
                    )
                ),
                targetDurationMs = 1_000L
            )
        )

        assertEquals(listOf("Usable source window"), plan.segments.single().rationale)
        assertFalse(plan.segments.single().rationale.any { it.contains("keyword", ignoreCase = true) })
    }

    private fun window(
        id: String,
        clipId: String,
        sourceOrder: Int,
        startMs: Long,
        endMs: Long,
        score: Float = 0.7f,
        audio: Float = score,
        fingerprint: String = "sha256-$clipId"
    ) = AutoEditWindow(
        id = id,
        clipId = clipId,
        clipFingerprint = fingerprint,
        sourceOrder = sourceOrder,
        sourceStartMs = startMs,
        sourceEndMs = endMs,
        scores = AutoEditScoreComponents(
            visualQuality = score,
            motion = score,
            subjectPresence = score,
            audioEnergy = audio,
            keywordRelevance = score
        )
    )
}

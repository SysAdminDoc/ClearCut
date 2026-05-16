package com.novacut.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R6.14 — SpeakerSwitchPlanner.
 */
class SpeakerSwitchPlannerTest {

    private fun turn(id: String, start: Long, end: Long) =
        SpeakerSwitchPlanner.SpeakerTurn(speakerId = id, startMs = start, endMs = end)

    private fun angle(idx: Int, assigned: String? = null) =
        SpeakerSwitchPlanner.Angle(angleIndex = idx, assignedSpeakerId = assigned)

    @Test
    fun emptyInputs_emitNoCuts() {
        val plan = SpeakerSwitchPlanner.plan(
            speakerTurns = emptyList(),
            angles = listOf(angle(0)),
        )
        assertTrue(plan.cuts.isEmpty())
        assertTrue(plan.speakerAngleMap.isEmpty())

        val noAngles = SpeakerSwitchPlanner.plan(
            speakerTurns = listOf(turn("A", 0, 1_000)),
            angles = emptyList(),
        )
        assertTrue(noAngles.cuts.isEmpty())
    }

    @Test
    fun singleTurn_emitsOneCutToInitialAngle() {
        val plan = SpeakerSwitchPlanner.plan(
            speakerTurns = listOf(turn("A", 0, 1_000)),
            angles = listOf(angle(0), angle(1)),
        )
        assertEquals(1, plan.cuts.size)
        assertEquals(SpeakerSwitchPlanner.Cut(timelineMs = 0L, angleIndex = 0), plan.cuts.first())
        // Speaker A took the first free angle (0) via round-robin.
        assertEquals(0, plan.speakerAngleMap["A"])
    }

    @Test
    fun twoSpeakers_alternateAcrossTwoAngles() {
        val turns = listOf(
            turn("A", 0, 2_000),
            turn("B", 2_000, 4_000),
            turn("A", 4_000, 6_000),
            turn("B", 6_000, 8_000),
        )
        val plan = SpeakerSwitchPlanner.plan(
            speakerTurns = turns,
            angles = listOf(angle(0), angle(1)),
        )
        // 1 seed cut + 3 switches = 4 cuts total
        assertEquals(4, plan.cuts.size)
        assertEquals(listOf(0L, 2_000L, 4_000L, 6_000L), plan.cuts.map { it.timelineMs })
        assertEquals(listOf(0, 1, 0, 1), plan.cuts.map { it.angleIndex })
        assertEquals(0, plan.speakerAngleMap["A"])
        assertEquals(1, plan.speakerAngleMap["B"])
    }

    @Test
    fun consecutiveSameSpeaker_noRedundantCuts() {
        val turns = listOf(
            turn("A", 0, 1_000),
            turn("A", 1_000, 2_000),
            turn("A", 2_000, 3_000),
        )
        val plan = SpeakerSwitchPlanner.plan(
            speakerTurns = turns,
            angles = listOf(angle(0), angle(1)),
        )
        assertEquals(1, plan.cuts.size)
        assertEquals(0, plan.cuts.first().angleIndex)
    }

    @Test
    fun minDwellPolicy_dropsFlickerCuts() {
        val turns = listOf(
            turn("A", 0, 500),
            // B's turn starts at 500 ms — only 500 ms after the initial cut to A.
            // With minDwellMs = 800 the switch is dropped.
            turn("B", 500, 1_500),
            // A's next turn starts at 1500 ms — 1500 ms after the original cut.
            turn("A", 1_500, 3_000),
        )
        val plan = SpeakerSwitchPlanner.plan(
            speakerTurns = turns,
            angles = listOf(angle(0), angle(1)),
            policy = SpeakerSwitchPlanner.SwitchPolicy(minDwellMs = 800L),
        )
        // Initial cut to angle 0 + the B cut is dropped + the A cut at 1500 is
        // a no-op because A is already active = single cut total.
        assertEquals(1, plan.cuts.size)
        assertEquals(0, plan.cuts.first().angleIndex)
    }

    @Test
    fun explicitAngleAssignmentOverridesRoundRobin() {
        // Angle 0 belongs to B explicitly; A should grab angle 1.
        val plan = SpeakerSwitchPlanner.plan(
            speakerTurns = listOf(
                turn("A", 0, 1_000),
                turn("B", 1_000, 2_000),
            ),
            angles = listOf(angle(0, assigned = "B"), angle(1)),
        )
        assertEquals(1, plan.speakerAngleMap["A"])
        assertEquals(0, plan.speakerAngleMap["B"])
        assertEquals(listOf(1, 0), plan.cuts.map { it.angleIndex })
    }

    @Test
    fun moreSpeakersThanAngles_wrapsViaModulo() {
        val turns = listOf(
            turn("A", 0, 1_000),
            turn("B", 1_000, 2_000),
            turn("C", 2_000, 3_000), // no third angle available
        )
        val plan = SpeakerSwitchPlanner.plan(
            speakerTurns = turns,
            angles = listOf(angle(0), angle(1)),
        )
        // A -> 0, B -> 1, C wraps to mapping.size % anglesSize == 2 % 2 == 0
        assertEquals(0, plan.speakerAngleMap["A"])
        assertEquals(1, plan.speakerAngleMap["B"])
        assertEquals(0, plan.speakerAngleMap["C"])
        assertEquals(listOf(0L, 1_000L, 2_000L), plan.cuts.map { it.timelineMs })
        assertEquals(listOf(0, 1, 0), plan.cuts.map { it.angleIndex })
    }

    @Test
    fun outOfOrderTurnsAreSortedFirst() {
        val plan = SpeakerSwitchPlanner.plan(
            speakerTurns = listOf(
                turn("B", 2_000, 3_000),
                turn("A", 0, 1_000),
                turn("A", 1_000, 2_000),
            ),
            angles = listOf(angle(0), angle(1)),
        )
        assertEquals(listOf(0L, 2_000L), plan.cuts.map { it.timelineMs })
        assertEquals(listOf(0, 1), plan.cuts.map { it.angleIndex })
    }

    @Test
    fun initialAngleIndexFromPolicy_respected() {
        val plan = SpeakerSwitchPlanner.plan(
            speakerTurns = listOf(turn("A", 0, 1_000)),
            angles = listOf(angle(0), angle(1), angle(2)),
            policy = SpeakerSwitchPlanner.SwitchPolicy(initialAngleIndex = 2),
        )
        assertEquals(2, plan.cuts.first().angleIndex)
    }

    @Test
    fun invalidTurn_throws() {
        assertThrows(IllegalArgumentException::class.java) {
            turn("A", start = 1_000, end = 500)
        }
    }

    @Test
    fun invalidPolicy_throws() {
        assertThrows(IllegalArgumentException::class.java) {
            SpeakerSwitchPlanner.SwitchPolicy(minDwellMs = -1)
        }
    }
}

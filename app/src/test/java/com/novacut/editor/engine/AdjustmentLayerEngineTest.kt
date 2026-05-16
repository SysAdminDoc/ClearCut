package com.novacut.editor.engine

import com.novacut.editor.model.Effect
import com.novacut.editor.model.EffectType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdjustmentLayerEngineTest {

    private val engine = AdjustmentLayerEngine()

    private fun dummyEffect(id: String): Effect = Effect(
        id = id,
        type = EffectType.BRIGHTNESS,
        enabled = true
    )

    @Test
    fun effectsForClip_noLayers_returnsEmpty() {
        assertTrue(engine.effectsForClip(0L, 1000L, emptyList()).isEmpty())
    }

    @Test
    fun effectsForClip_disabledLayer_ignored() {
        val layer = AdjustmentLayerEngine.AdjustmentLayer(
            id = "l1",
            startTimeMs = 0L,
            endTimeMs = 2000L,
            effects = listOf(dummyEffect("e1")),
            enabled = false
        )
        assertTrue(engine.effectsForClip(0L, 1000L, listOf(layer)).isEmpty())
    }

    @Test
    fun effectsForClip_nonOverlappingLayer_ignored() {
        val layer = AdjustmentLayerEngine.AdjustmentLayer(
            id = "l1",
            startTimeMs = 2000L,
            endTimeMs = 3000L,
            effects = listOf(dummyEffect("e1"))
        )
        // Clip 0-1000 does not overlap layer 2000-3000
        assertTrue(engine.effectsForClip(0L, 1000L, listOf(layer)).isEmpty())
    }

    @Test
    fun effectsForClip_layerTouchingEdge_notConsideredOverlap() {
        // Layer ends exactly at clip start -- by convention, zero-area touches
        // do not contribute. Same rule on the other edge.
        val layerTouchingStart = AdjustmentLayerEngine.AdjustmentLayer(
            id = "l1", startTimeMs = 0L, endTimeMs = 1000L,
            effects = listOf(dummyEffect("e1"))
        )
        val layerTouchingEnd = AdjustmentLayerEngine.AdjustmentLayer(
            id = "l2", startTimeMs = 2000L, endTimeMs = 3000L,
            effects = listOf(dummyEffect("e2"))
        )
        assertTrue(engine.effectsForClip(1000L, 2000L,
            listOf(layerTouchingStart, layerTouchingEnd)).isEmpty())
    }

    @Test
    fun effectsForClip_overlappingLayers_accumulate() {
        val l1 = AdjustmentLayerEngine.AdjustmentLayer(
            id = "l1", startTimeMs = 0L, endTimeMs = 5000L,
            effects = listOf(dummyEffect("e1"))
        )
        val l2 = AdjustmentLayerEngine.AdjustmentLayer(
            id = "l2", startTimeMs = 1000L, endTimeMs = 4000L,
            effects = listOf(dummyEffect("e2"), dummyEffect("e3"))
        )
        val out = engine.effectsForClip(500L, 4500L, listOf(l1, l2))
        assertEquals(listOf("e1", "e2", "e3"), out.map { it.id })
    }

    @Test
    fun partitionByLayerBoundaries_noLayers_singleRange() {
        val parts = engine.partitionByLayerBoundaries(0L, 1000L, emptyList())
        assertEquals(listOf(0L until 1000L), parts)
    }

    @Test
    fun partitionByLayerBoundaries_invalidRange_returnsEmpty() {
        // Regression guard for clipEndMs <= clipStartMs (fixed in v3.58 audit pass)
        assertTrue(engine.partitionByLayerBoundaries(1000L, 1000L, emptyList()).isEmpty())
        assertTrue(engine.partitionByLayerBoundaries(1500L, 1000L, emptyList()).isEmpty())
    }

    @Test
    fun partitionByLayerBoundaries_layerInsideClip_splitsInto3() {
        val layer = AdjustmentLayerEngine.AdjustmentLayer(
            id = "l1", startTimeMs = 3000L, endTimeMs = 7000L,
            effects = listOf(dummyEffect("e1"))
        )
        val parts = engine.partitionByLayerBoundaries(0L, 10_000L, listOf(layer))
        assertEquals(listOf(0L until 3000L, 3000L until 7000L, 7000L until 10_000L), parts)
    }

    @Test
    fun partitionByLayerBoundaries_layerExtendsBeyondClip_clamped() {
        val layer = AdjustmentLayerEngine.AdjustmentLayer(
            id = "l1", startTimeMs = 0L, endTimeMs = 5000L,
            effects = listOf(dummyEffect("e1"))
        )
        // Layer 0-5000 vs clip 1000-10000: only the end boundary falls inside.
        val parts = engine.partitionByLayerBoundaries(1000L, 10_000L, listOf(layer))
        assertEquals(listOf(1000L until 5000L, 5000L until 10_000L), parts)
    }

    @Test
    fun adjustmentLayer_invalidRange_throws() {
        try {
            AdjustmentLayerEngine.AdjustmentLayer(
                id = "l1", startTimeMs = 500L, endTimeMs = 500L,
                effects = emptyList()
            )
            assert(false) { "Should have thrown on zero-duration layer" }
        } catch (_: IllegalArgumentException) { /* expected */ }
    }

    // --- C.11 planForClip ---

    @Test
    fun planForClip_noLayers_returnsEmpty() {
        assertEquals(
            emptyList<AdjustmentLayerEngine.AdjustmentLayerSegment>(),
            engine.planForClip(0L, 10_000L, emptyList()),
        )
    }

    @Test
    fun planForClip_invalidRange_returnsEmpty() {
        val plan = engine.planForClip(
            1_000L,
            500L,
            listOf(
                AdjustmentLayerEngine.AdjustmentLayer(
                    id = "a",
                    startTimeMs = 0L,
                    endTimeMs = 2_000L,
                    effects = listOf(dummyEffect("e1")),
                )
            )
        )
        assertTrue(plan.isEmpty())
    }

    @Test
    fun planForClip_singleLayerMidClip_producesThreeSegments() {
        val layer = AdjustmentLayerEngine.AdjustmentLayer(
            id = "mid",
            startTimeMs = 3_000L,
            endTimeMs = 7_000L,
            effects = listOf(dummyEffect("e1")),
        )
        val plan = engine.planForClip(0L, 10_000L, listOf(layer))
        assertEquals(3, plan.size)
        assertEquals(0L, plan[0].timelineStartMs)
        assertEquals(3_000L, plan[0].timelineEndMs)
        assertTrue(plan[0].effects.isEmpty())

        assertEquals(3_000L, plan[1].timelineStartMs)
        assertEquals(7_000L, plan[1].timelineEndMs)
        assertEquals(1, plan[1].effects.size)
        assertEquals("e1", plan[1].effects.first().id)

        assertEquals(7_000L, plan[2].timelineStartMs)
        assertEquals(10_000L, plan[2].timelineEndMs)
        assertTrue(plan[2].effects.isEmpty())
    }

    @Test
    fun planForClip_layerCoveringWholeClip_producesOneSegment() {
        val layer = AdjustmentLayerEngine.AdjustmentLayer(
            id = "all",
            startTimeMs = 0L,
            endTimeMs = 10_000L,
            effects = listOf(dummyEffect("e1")),
        )
        val plan = engine.planForClip(0L, 10_000L, listOf(layer))
        assertEquals(1, plan.size)
        assertEquals(0L, plan[0].timelineStartMs)
        assertEquals(10_000L, plan[0].timelineEndMs)
        assertEquals(1, plan[0].effects.size)
    }

    @Test
    fun planForClip_durationSumsToClipRange() {
        val layers = listOf(
            AdjustmentLayerEngine.AdjustmentLayer(
                id = "l1", startTimeMs = 2_000L, endTimeMs = 4_000L,
                effects = listOf(dummyEffect("a")),
            ),
            AdjustmentLayerEngine.AdjustmentLayer(
                id = "l2", startTimeMs = 5_000L, endTimeMs = 8_000L,
                effects = listOf(dummyEffect("b")),
            ),
        )
        val plan = engine.planForClip(0L, 10_000L, layers)
        val totalDuration = plan.sumOf { it.durationMs }
        assertEquals(10_000L, totalDuration)
    }
}

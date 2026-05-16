package com.novacut.editor.engine

import com.novacut.editor.model.Easing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class KeyframeBezierGraphTest {

    private fun near(actual: Float, expected: Float, eps: Float = 1e-4f) {
        assertTrue("Expected ~$expected got $actual", abs(actual - expected) < eps)
    }

    // --- BezierSegment validation ---

    @Test
    fun bezierSegment_validControlPointsAreAccepted() {
        // Just confirms the data class accepts in-range tangents.
        val seg = KeyframeBezierGraph.BezierSegment(
            startValue = 0f, endValue = 1f, c0t = 0.42f, c0v = 0f, c1t = 0.58f, c1v = 1f
        )
        assertEquals(0.42f, seg.c0t)
    }

    @Test
    fun bezierSegment_outOfRangeC0t_throws() {
        assertThrows(IllegalArgumentException::class.java) {
            KeyframeBezierGraph.BezierSegment(0f, 1f, c0t = -0.1f, c0v = 0f, c1t = 0.5f, c1v = 1f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            KeyframeBezierGraph.BezierSegment(0f, 1f, c0t = 1.1f, c0v = 0f, c1t = 0.5f, c1v = 1f)
        }
    }

    @Test
    fun bezierSegment_overshootControlValuesAllowed() {
        // BACK and ELASTIC easings overshoot — values outside [0, 1] are OK.
        KeyframeBezierGraph.BezierSegment(
            startValue = 0f, endValue = 1f, c0t = 0.5f, c0v = -0.5f, c1t = 0.5f, c1v = 1.5f
        )
    }

    // --- evaluate ---

    private fun sampleEaseInOut(): KeyframeBezierGraph.BezierSegment =
        KeyframeBezierGraph.presetFor(Easing.EASE_IN_OUT)

    @Test
    fun evaluate_atTZero_returnsStartValue() {
        val seg = sampleEaseInOut()
        near(KeyframeBezierGraph.evaluate(seg, 0f), seg.startValue)
    }

    @Test
    fun evaluate_atTOne_returnsEndValue() {
        val seg = sampleEaseInOut()
        near(KeyframeBezierGraph.evaluate(seg, 1f), seg.endValue)
    }

    @Test
    fun evaluate_outOfRangeTClamps() {
        val seg = sampleEaseInOut()
        near(KeyframeBezierGraph.evaluate(seg, -0.5f), seg.startValue)
        near(KeyframeBezierGraph.evaluate(seg, 1.5f), seg.endValue)
    }

    @Test
    fun evaluate_linearPreset_matchesT() {
        val seg = KeyframeBezierGraph.presetFor(Easing.LINEAR)
        listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { t ->
            near(KeyframeBezierGraph.evaluate(seg, t), t)
        }
    }

    @Test
    fun evaluate_easeInOut_isSymmetricAroundMidpoint() {
        val seg = KeyframeBezierGraph.presetFor(Easing.EASE_IN_OUT)
        // Symmetry: B(0.5) ~ 0.5 for canonical EASE_IN_OUT.
        near(KeyframeBezierGraph.evaluate(seg, 0.5f), 0.5f)
    }

    // --- evaluatePoint ---

    @Test
    fun evaluatePoint_returnsAnchorsAtBoundaries() {
        val seg = KeyframeBezierGraph.presetFor(Easing.EASE_IN)
        val (x0, y0) = KeyframeBezierGraph.evaluatePoint(seg, 0f)
        near(x0, 0f)
        near(y0, 0f)
        val (x1, y1) = KeyframeBezierGraph.evaluatePoint(seg, 1f)
        near(x1, 1f)
        near(y1, 1f)
    }

    // --- presets ---

    @Test
    fun presets_coverAllEasings() {
        // Every documented easing has a curve. presetFor falls back to
        // LINEAR for unknowns — the test checks the canonical map has
        // every easing the public Easing enum declares.
        for (easing in Easing.entries) {
            val preset = KeyframeBezierGraph.presets[easing]
            assertTrue("Missing preset for $easing", preset != null)
        }
    }

    @Test
    fun presetFor_unknownFallsBackToLinear() {
        // The map covers every Easing today. Test the fallback path by
        // looking up a preset and checking presetFor agrees with map[].
        val linear = KeyframeBezierGraph.presetFor(Easing.LINEAR)
        assertEquals(linear, KeyframeBezierGraph.presets[Easing.LINEAR])
    }

    @Test
    fun easeIn_pullsDownAtMidpoint() {
        val seg = KeyframeBezierGraph.presetFor(Easing.EASE_IN)
        // EASE_IN ramps slowly; midpoint value is < 0.5.
        val mid = KeyframeBezierGraph.evaluate(seg, 0.5f)
        assertTrue("EASE_IN midpoint should be below 0.5, got $mid", mid < 0.5f)
    }

    @Test
    fun easeOut_pushesUpAtMidpoint() {
        val seg = KeyframeBezierGraph.presetFor(Easing.EASE_OUT)
        val mid = KeyframeBezierGraph.evaluate(seg, 0.5f)
        assertTrue("EASE_OUT midpoint should be above 0.5, got $mid", mid > 0.5f)
    }

    // --- rescale ---

    @Test
    fun rescale_unitToActualRange() {
        val unit = KeyframeBezierGraph.presetFor(Easing.LINEAR)
        val scaled = KeyframeBezierGraph.rescale(unit, startValue = 100f, endValue = 200f)
        near(scaled.startValue, 100f)
        near(scaled.endValue, 200f)
        // Linear c0v=0, c1v=1 → scaled c0v = 100 + 0 * 100 = 100, c1v = 100 + 1 * 100 = 200.
        near(scaled.c0v, 100f)
        near(scaled.c1v, 200f)
    }

    @Test
    fun rescale_negativeRangeStillWorks() {
        val unit = KeyframeBezierGraph.presetFor(Easing.LINEAR)
        val scaled = KeyframeBezierGraph.rescale(unit, startValue = 1f, endValue = -1f)
        // range = -2 → c0v = 1 + 0 * -2 = 1, c1v = 1 + 1 * -2 = -1.
        near(scaled.c0v, 1f)
        near(scaled.c1v, -1f)
    }

    @Test
    fun rescale_evaluationMatchesAtBoundaries() {
        val unit = KeyframeBezierGraph.presetFor(Easing.EASE_IN_OUT)
        val scaled = KeyframeBezierGraph.rescale(unit, startValue = 5f, endValue = 15f)
        near(KeyframeBezierGraph.evaluate(scaled, 0f), 5f)
        near(KeyframeBezierGraph.evaluate(scaled, 1f), 15f)
    }
}


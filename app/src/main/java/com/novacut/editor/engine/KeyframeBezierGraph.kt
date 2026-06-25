package com.novacut.editor.engine

import com.novacut.editor.model.Easing

/**
 * C.12 — Keyframe graph editor data model.
 *
 * Adds a visual bezier-curve editor on top of ClearCut's existing
 * [com.novacut.editor.model.Keyframe] / [Easing] system. The model already
 * supports 12 easings; this module adds the per-segment cubic bezier
 * description the visual editor needs (two control points + two value
 * endpoints) plus the math to evaluate the curve at any normalized t.
 *
 * Pure Kotlin: no Compose, no Android. The Composable that draws the curve
 * (KeyframeGraphPanel) consumes [evaluate], [BezierSegment], and the
 * preset table; it lives in a follow-up commit.
 *
 * ## Why a separate module vs extending KeyframeEngine
 *
 * KeyframeEngine handles *runtime evaluation* — given a list of keyframes and
 * a playhead time, produce the current value. That path stays unchanged.
 *
 * The bezier graph is *authoring data*: per-segment tangent handles the user
 * can drag in the visual editor, plus the math to convert a tangent-handle
 * position into the value the runtime expects. Keeping the two concerns
 * separate means the graph editor evolves without touching the export
 * runtime, and the runtime can keep treating curve presets as opaque.
 */
object KeyframeBezierGraph {

    /**
     * A single segment between two keyframes. Stored in *normalized* space —
     * both axes 0..1 — so the same segment description is reusable across
     * any value range (opacity 0..1, scale 0..10, volume 0..2). The graph
     * editor de-normalizes at render time.
     *
     * The four control points define a cubic bezier:
     *  - `(0, startValue)` — anchor at segment start
     *  - `(c0t, c0v)` — outgoing tangent handle from the start anchor
     *  - `(c1t, c1v)` — incoming tangent handle to the end anchor
     *  - `(1, endValue)` — anchor at segment end
     *
     * Constraints:
     *  - c0t, c1t in 0..1 (tangents stay inside the segment in t).
     *  - startValue, endValue, c0v, c1v can be outside 0..1 (overshoot is
     *    a valid effect — BACK / ELASTIC easings do this).
     */
    data class BezierSegment(
        val startValue: Float,
        val endValue: Float,
        val c0t: Float,
        val c0v: Float,
        val c1t: Float,
        val c1v: Float,
    ) {
        init {
            require(c0t in 0f..1f) { "c0t must be in [0, 1]: $c0t" }
            require(c1t in 0f..1f) { "c1t must be in [0, 1]: $c1t" }
        }
    }

    /**
     * Evaluate the cubic bezier value at normalized time `t` in 0..1.
     *
     * For easing, the input `t` is the x-axis time. Cubic bezier curves are
     * parameterized by an internal curve parameter, so first solve x(u) = t,
     * then return y(u). This keeps CSS-style presets linear/ease-in/ease-out
     * compatible with users' expectation that `evaluate(curve, 0.5)` means
     * "value halfway through the segment", not "raw bezier parameter 0.5".
     *
     * Where P0 = (0, startValue), P3 = (1, endValue), P1 = (c0t, c0v),
     * P2 = (c1t, c1v). Returns the y component (the actual interpolated
     * value).
     *
     * For UI rendering with a raw curve parameter, use [evaluatePoint].
     */
    fun evaluate(segment: BezierSegment, t: Float): Float {
        val clamped = t.coerceIn(0f, 1f)
        if (clamped <= 0f) return segment.startValue
        if (clamped >= 1f) return segment.endValue
        val curveT = solveCurveParameterForX(segment, clamped)
        return evaluateY(segment, curveT)
    }

    private fun solveCurveParameterForX(segment: BezierSegment, x: Float): Float {
        var lo = 0f
        var hi = 1f
        repeat(18) {
            val mid = (lo + hi) * 0.5f
            val midX = evaluateX(segment, mid)
            if (midX < x) {
                lo = mid
            } else {
                hi = mid
            }
        }
        return (lo + hi) * 0.5f
    }

    private fun evaluateX(segment: BezierSegment, t: Float): Float {
        val inv = 1f - t
        val inv2 = inv * inv
        val t2 = t * t
        val t3 = t2 * t
        return 3f * inv2 * t * segment.c0t + 3f * inv * t2 * segment.c1t + t3
    }

    private fun evaluateY(segment: BezierSegment, t: Float): Float {
        val inv = 1f - t
        val inv2 = inv * inv
        val inv3 = inv2 * inv
        val t2 = t * t
        val t3 = t2 * t
        return inv3 * segment.startValue +
            3f * inv2 * t * segment.c0v +
            3f * inv * t2 * segment.c1v +
            t3 * segment.endValue
    }

    /**
     * Evaluate both x and y of the bezier curve at parameter `t`. The visual
     * editor needs both to render the curve geometry (x defines horizontal
     * position, y defines value).
     */
    fun evaluatePoint(segment: BezierSegment, t: Float): Pair<Float, Float> {
        val clamped = t.coerceIn(0f, 1f)
        val inv = 1f - clamped
        val inv2 = inv * inv
        val inv3 = inv2 * inv
        val t2 = clamped * clamped
        val t3 = t2 * clamped
        // P0 = (0, startValue), P1 = (c0t, c0v), P2 = (c1t, c1v), P3 = (1, endValue)
        val x = 3f * inv2 * clamped * segment.c0t + 3f * inv * t2 * segment.c1t + t3
        val y = evaluateY(segment, clamped)
        return x to y
    }

    /**
     * Curve presets users can apply with one tap. Maps a preset name to the
     * normalized BezierSegment that defines its shape over the unit square.
     * Values are CSS / Material-style canonical bezier control points.
     *
     * `endValue` is fixed to 1f and `startValue` to 0f so the preset is a
     * unit easing curve; the runtime re-scales to the actual keyframe
     * values when applying.
     */
    val presets: Map<Easing, BezierSegment> = mapOf(
        Easing.LINEAR to unitSegment(0.0f, 0.0f, 1.0f, 1.0f),
        Easing.EASE_IN to unitSegment(0.42f, 0.0f, 1.0f, 1.0f),
        Easing.EASE_OUT to unitSegment(0.0f, 0.0f, 0.58f, 1.0f),
        Easing.EASE_IN_OUT to unitSegment(0.42f, 0.0f, 0.58f, 1.0f),
        Easing.SPRING to unitSegment(0.5f, 1.5f, 0.5f, 1.0f),
        Easing.BOUNCE to unitSegment(0.36f, 0.0f, 0.66f, -0.56f),
        // Material accelerate: slow start, fast end.
        Easing.CUBIC to unitSegment(0.4f, 0.0f, 0.2f, 1.0f),
        // Approximation of expo (close to linear in shape, anchored at 1.0).
        Easing.EXPO to unitSegment(0.7f, 0.0f, 0.84f, 0.0f),
        Easing.SINE to unitSegment(0.39f, 0.575f, 0.565f, 1.0f),
        Easing.CIRCULAR to unitSegment(0.785f, 0.135f, 0.15f, 0.86f),
        Easing.BACK to unitSegment(0.68f, -0.55f, 0.265f, 1.55f),
        Easing.ELASTIC to unitSegment(0.5f, -0.5f, 0.5f, 1.5f),
    )

    /**
     * Return the preset for the given easing if one exists. Linear is the
     * documented fallback when no preset matches.
     */
    fun presetFor(easing: Easing): BezierSegment =
        presets[easing] ?: presets.getValue(Easing.LINEAR)

    private fun unitSegment(c0t: Float, c0v: Float, c1t: Float, c1v: Float): BezierSegment =
        BezierSegment(
            startValue = 0f,
            endValue = 1f,
            c0t = c0t,
            c0v = c0v,
            c1t = c1t,
            c1v = c1v,
        )

    /**
     * Re-scale a unit-segment preset to a real (startValue, endValue) range.
     * The visual editor stores unit segments to keep the data portable; the
     * runtime applies this scaling to drive the actual keyframe value.
     */
    fun rescale(
        segment: BezierSegment,
        startValue: Float,
        endValue: Float,
    ): BezierSegment {
        val range = endValue - startValue
        return BezierSegment(
            startValue = startValue,
            endValue = endValue,
            c0t = segment.c0t,
            c0v = startValue + segment.c0v * range,
            c1t = segment.c1t,
            c1v = startValue + segment.c1v * range,
        )
    }
}

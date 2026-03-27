package com.novacut.editor.model

import androidx.compose.runtime.Immutable

@Immutable
data class ColorGrade(
    val enabled: Boolean = true,
    val liftR: Float = 0f, val liftG: Float = 0f, val liftB: Float = 0f,
    val gammaR: Float = 1f, val gammaG: Float = 1f, val gammaB: Float = 1f,
    val gainR: Float = 1f, val gainG: Float = 1f, val gainB: Float = 1f,
    val offsetR: Float = 0f, val offsetG: Float = 0f, val offsetB: Float = 0f,
    val curves: ColorCurves = ColorCurves(),
    val hslQualifier: HslQualifier? = null,
    val lutPath: String? = null,
    val lutIntensity: Float = 1f,
    val colorMatchRef: String? = null
)

data class ColorCurves(
    val master: List<CurvePoint> = listOf(CurvePoint(0f, 0f), CurvePoint(1f, 1f)),
    val red: List<CurvePoint> = listOf(CurvePoint(0f, 0f), CurvePoint(1f, 1f)),
    val green: List<CurvePoint> = listOf(CurvePoint(0f, 0f), CurvePoint(1f, 1f)),
    val blue: List<CurvePoint> = listOf(CurvePoint(0f, 0f), CurvePoint(1f, 1f))
) {
    fun evaluateCurve(points: List<CurvePoint>, input: Float): Float {
        if (points.size < 2) return input
        val sorted = points.sortedBy { it.x }
        if (input <= sorted.first().x) return sorted.first().y
        if (input >= sorted.last().x) return sorted.last().y

        for (i in 0 until sorted.size - 1) {
            if (input >= sorted[i].x && input <= sorted[i + 1].x) {
                val p0 = sorted[i]
                val p1 = sorted[i + 1]
                val t = (input - p0.x) / (p1.x - p0.x)
                return SpeedCurve.cubicBezierInterpolate(
                    p0.y, p0.handleOutY, p1.handleInY, p1.y, t
                )
            }
        }
        return input
    }
}

data class CurvePoint(
    val x: Float,
    val y: Float,
    val handleInX: Float = x,
    val handleInY: Float = y,
    val handleOutX: Float = x,
    val handleOutY: Float = y
)

data class HslQualifier(
    val hueCenter: Float = 0f,
    val hueWidth: Float = 30f,
    val satMin: Float = 0f,
    val satMax: Float = 1f,
    val lumMin: Float = 0f,
    val lumMax: Float = 1f,
    val softness: Float = 0.1f,
    val adjustHue: Float = 0f,
    val adjustSat: Float = 0f,
    val adjustLum: Float = 0f
)

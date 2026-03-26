package com.novacut.editor.engine

import com.novacut.editor.model.*
import kotlin.math.*

/**
 * Interpolation engine for keyframe-based animation of clip properties.
 * Supports bezier curves, hold interpolation, and effect parameter keyframing.
 */
object KeyframeEngine {

    /**
     * Get the interpolated value for a property at a given time offset within a clip.
     * Returns null if no keyframes exist for this property.
     */
    fun getValueAt(
        keyframes: List<Keyframe>,
        property: KeyframeProperty,
        timeOffsetMs: Long
    ): Float? {
        val relevant = keyframes
            .filter { it.property == property }
            .sortedBy { it.timeOffsetMs }
            .distinctBy { it.timeOffsetMs }

        if (relevant.isEmpty()) return null
        if (relevant.size == 1) return relevant[0].value

        // Before first keyframe
        if (timeOffsetMs <= relevant.first().timeOffsetMs) return relevant.first().value
        // After last keyframe
        if (timeOffsetMs >= relevant.last().timeOffsetMs) return relevant.last().value

        // Find surrounding keyframes
        var prev = relevant.first()
        var next = relevant.last()

        for (i in 0 until relevant.size - 1) {
            if (timeOffsetMs >= relevant[i].timeOffsetMs && timeOffsetMs <= relevant[i + 1].timeOffsetMs) {
                prev = relevant[i]
                next = relevant[i + 1]
                break
            }
        }

        val duration = (next.timeOffsetMs - prev.timeOffsetMs).toFloat()
        if (duration <= 0f) return prev.value

        val t = (timeOffsetMs - prev.timeOffsetMs).toFloat() / duration

        return when (prev.interpolation) {
            KeyframeInterpolation.HOLD -> prev.value
            KeyframeInterpolation.LINEAR -> lerp(prev.value, next.value, t)
            KeyframeInterpolation.BEZIER -> {
                val easedT = evaluateCubicBezierTime(
                    prev.handleOutX, prev.handleOutY,
                    next.handleInX, next.handleInY,
                    t
                )
                lerp(prev.value, next.value, easedT)
            }
        }
    }

    /**
     * Get interpolated value for an effect parameter keyframe at a given time.
     */
    fun getEffectParamAt(
        keyframes: List<EffectKeyframe>,
        paramName: String,
        timeOffsetMs: Long
    ): Float? {
        val relevant = keyframes
            .filter { it.paramName == paramName }
            .sortedBy { it.timeOffsetMs }
            .distinctBy { it.timeOffsetMs }

        if (relevant.isEmpty()) return null
        if (relevant.size == 1) return relevant[0].value

        if (timeOffsetMs <= relevant.first().timeOffsetMs) return relevant.first().value
        if (timeOffsetMs >= relevant.last().timeOffsetMs) return relevant.last().value

        var prev = relevant.first()
        var next = relevant.last()

        for (i in 0 until relevant.size - 1) {
            if (timeOffsetMs >= relevant[i].timeOffsetMs && timeOffsetMs <= relevant[i + 1].timeOffsetMs) {
                prev = relevant[i]
                next = relevant[i + 1]
                break
            }
        }

        val duration = (next.timeOffsetMs - prev.timeOffsetMs).toFloat()
        if (duration <= 0f) return prev.value

        val t = (timeOffsetMs - prev.timeOffsetMs).toFloat() / duration

        val easedT = if (prev.handleOutX != 0f || prev.handleOutY != 0f ||
            next.handleInX != 0f || next.handleInY != 0f
        ) {
            evaluateCubicBezierTime(
                prev.handleOutX, prev.handleOutY,
                next.handleInX, next.handleInY,
                t
            )
        } else {
            applyEasing(t, next.easing)
        }

        return lerp(prev.value, next.value, easedT)
    }

    /**
     * Get all animated effect params at a given time.
     * Returns map of paramName -> interpolated value.
     */
    fun getAnimatedEffectParams(
        effect: Effect,
        timeOffsetMs: Long
    ): Map<String, Float> {
        if (effect.keyframes.isEmpty()) return effect.params

        val result = effect.params.toMutableMap()
        val keyframedParams = effect.keyframes.map { it.paramName }.distinct()

        for (param in keyframedParams) {
            getEffectParamAt(effect.keyframes, param, timeOffsetMs)?.let {
                result[param] = it
            }
        }
        return result
    }

    /**
     * Get all animated property values at a given time.
     */
    fun getAnimatedState(
        keyframes: List<Keyframe>,
        timeOffsetMs: Long
    ): AnimatedState {
        return AnimatedState(
            positionX = getValueAt(keyframes, KeyframeProperty.POSITION_X, timeOffsetMs),
            positionY = getValueAt(keyframes, KeyframeProperty.POSITION_Y, timeOffsetMs),
            scaleX = getValueAt(keyframes, KeyframeProperty.SCALE_X, timeOffsetMs),
            scaleY = getValueAt(keyframes, KeyframeProperty.SCALE_Y, timeOffsetMs),
            rotation = getValueAt(keyframes, KeyframeProperty.ROTATION, timeOffsetMs),
            opacity = getValueAt(keyframes, KeyframeProperty.OPACITY, timeOffsetMs),
            volume = getValueAt(keyframes, KeyframeProperty.VOLUME, timeOffsetMs),
            anchorX = getValueAt(keyframes, KeyframeProperty.ANCHOR_X, timeOffsetMs),
            anchorY = getValueAt(keyframes, KeyframeProperty.ANCHOR_Y, timeOffsetMs)
        )
    }

    /**
     * Evaluate cubic bezier curve for time remapping.
     * Control points define the easing curve shape (like CSS cubic-bezier).
     * Returns the eased t value (0..1).
     */
    private fun evaluateCubicBezierTime(
        cp1x: Float, cp1y: Float,
        cp2x: Float, cp2y: Float,
        t: Float
    ): Float {
        // If no handle offsets, fall back to linear
        if (cp1x == 0f && cp1y == 0f && cp2x == 0f && cp2y == 0f) return t

        // Solve for the bezier parameter that gives us the desired x (time)
        // using Newton-Raphson iteration
        val x1 = cp1x.coerceIn(0f, 1f)
        val x2 = cp2x.coerceIn(0f, 1f)

        var guess = t
        for (i in 0 until 8) {
            val currentX = cubicBezier(x1, x2, guess)
            val currentSlope = cubicBezierDerivative(x1, x2, guess)
            if (abs(currentSlope) < 1e-7f) break
            guess -= (currentX - t) / currentSlope
            guess = guess.coerceIn(0f, 1f)
        }

        return cubicBezier(cp1y.coerceIn(-1f, 2f), cp2y.coerceIn(-1f, 2f), guess)
    }

    private fun cubicBezier(a: Float, b: Float, t: Float): Float {
        // B(t) = 3(1-t)^2*t*a + 3(1-t)*t^2*b + t^3
        val mt = 1f - t
        return 3f * mt * mt * t * a + 3f * mt * t * t * b + t * t * t
    }

    private fun cubicBezierDerivative(a: Float, b: Float, t: Float): Float {
        val mt = 1f - t
        return 3f * mt * mt * a + 6f * mt * t * (b - a) + 3f * t * t * (1f - b)
    }

    /**
     * Apply easing function to normalized time t (0..1).
     */
    fun applyEasing(t: Float, easing: Easing): Float {
        return when (easing) {
            Easing.LINEAR -> t
            Easing.EASE_IN -> t * t
            Easing.EASE_OUT -> 1f - (1f - t) * (1f - t)
            Easing.EASE_IN_OUT -> {
                if (t < 0.5f) 2f * t * t
                else 1f - (-2f * t + 2f).pow(2) / 2f
            }
            Easing.SPRING -> {
                val c4 = (2f * PI / 3f).toFloat()
                val raw = if (t == 0f || t == 1f) t
                    else -(2f.pow(-10f * t)) * sin((t * 10f - 0.75f) * c4) + 1f
                raw.coerceIn(0f, 1f)
            }
            Easing.BOUNCE -> {
                val n1 = 7.5625f; val d1 = 2.75f
                val bt = 1f - t
                1f - when {
                    bt < 1f / d1 -> n1 * bt * bt
                    bt < 2f / d1 -> n1 * (bt - 1.5f / d1).let { it * it } + 0.75f
                    bt < 2.5f / d1 -> n1 * (bt - 2.25f / d1).let { it * it } + 0.9375f
                    else -> n1 * (bt - 2.625f / d1).let { it * it } + 0.984375f
                }
            }
            Easing.ELASTIC -> {
                if (t == 0f || t == 1f) t
                else -(2f.pow(10f * t - 10f)) * sin((t * 10f - 10.75f) * (2f * PI.toFloat() / 3f))
            }
            Easing.BACK -> {
                val c1 = 1.70158f; val c3 = c1 + 1f
                c3 * t * t * t - c1 * t * t
            }
            Easing.CIRCULAR -> 1f - sqrt(1f - t * t)
            Easing.EXPO -> if (t == 0f) 0f else 2f.pow(10f * t - 10f)
            Easing.SINE -> 1f - cos(t * PI.toFloat() / 2f)
            Easing.CUBIC -> t * t * t
        }
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    // --- Mask keyframe interpolation ---

    /**
     * Interpolate mask shape between keyframes at a given time.
     */
    fun interpolateMaskPoints(
        mask: Mask,
        timeOffsetMs: Long
    ): List<MaskPoint> {
        if (mask.keyframes.isEmpty()) return mask.points
        val sorted = mask.keyframes.sortedBy { it.timeOffsetMs }

        if (timeOffsetMs <= sorted.first().timeOffsetMs) return sorted.first().points
        if (timeOffsetMs >= sorted.last().timeOffsetMs) return sorted.last().points

        for (i in 0 until sorted.size - 1) {
            if (timeOffsetMs >= sorted[i].timeOffsetMs && timeOffsetMs <= sorted[i + 1].timeOffsetMs) {
                val prev = sorted[i]
                val next = sorted[i + 1]
                val duration = (next.timeOffsetMs - prev.timeOffsetMs).toFloat()
                if (duration <= 0f) return prev.points
                val t = applyEasing(
                    (timeOffsetMs - prev.timeOffsetMs).toFloat() / duration,
                    next.easing
                )
                return interpolatePointLists(prev.points, next.points, t)
            }
        }
        return mask.points
    }

    private fun interpolatePointLists(
        a: List<MaskPoint>, b: List<MaskPoint>, t: Float
    ): List<MaskPoint> {
        val size = minOf(a.size, b.size)
        return (0 until size).map { i ->
            MaskPoint(
                x = lerp(a[i].x, b[i].x, t),
                y = lerp(a[i].y, b[i].y, t),
                handleInX = lerp(a[i].handleInX, b[i].handleInX, t),
                handleInY = lerp(a[i].handleInY, b[i].handleInY, t),
                handleOutX = lerp(a[i].handleOutX, b[i].handleOutX, t),
                handleOutY = lerp(a[i].handleOutY, b[i].handleOutY, t)
            )
        }
    }

    // --- Preset generators ---

    fun createKenBurnsKeyframes(
        durationMs: Long,
        startScale: Float = 1f,
        endScale: Float = 1.3f,
        startX: Float = 0f,
        startY: Float = 0f,
        endX: Float = 0.1f,
        endY: Float = 0.05f
    ): List<Keyframe> {
        return listOf(
            Keyframe(0L, KeyframeProperty.SCALE_X, startScale, Easing.EASE_IN_OUT,
                interpolation = KeyframeInterpolation.BEZIER, handleOutX = 0.42f, handleOutY = 0f),
            Keyframe(0L, KeyframeProperty.SCALE_Y, startScale, Easing.EASE_IN_OUT,
                interpolation = KeyframeInterpolation.BEZIER, handleOutX = 0.42f, handleOutY = 0f),
            Keyframe(0L, KeyframeProperty.POSITION_X, startX, Easing.EASE_IN_OUT,
                interpolation = KeyframeInterpolation.BEZIER, handleOutX = 0.42f, handleOutY = 0f),
            Keyframe(0L, KeyframeProperty.POSITION_Y, startY, Easing.EASE_IN_OUT,
                interpolation = KeyframeInterpolation.BEZIER, handleOutX = 0.42f, handleOutY = 0f),
            Keyframe(durationMs, KeyframeProperty.SCALE_X, endScale, Easing.EASE_IN_OUT,
                interpolation = KeyframeInterpolation.BEZIER, handleInX = 0.58f, handleInY = 1f),
            Keyframe(durationMs, KeyframeProperty.SCALE_Y, endScale, Easing.EASE_IN_OUT,
                interpolation = KeyframeInterpolation.BEZIER, handleInX = 0.58f, handleInY = 1f),
            Keyframe(durationMs, KeyframeProperty.POSITION_X, endX, Easing.EASE_IN_OUT,
                interpolation = KeyframeInterpolation.BEZIER, handleInX = 0.58f, handleInY = 1f),
            Keyframe(durationMs, KeyframeProperty.POSITION_Y, endY, Easing.EASE_IN_OUT,
                interpolation = KeyframeInterpolation.BEZIER, handleInX = 0.58f, handleInY = 1f),
        )
    }

    fun createFadeIn(durationMs: Long = 500L): List<Keyframe> {
        return listOf(
            Keyframe(0L, KeyframeProperty.OPACITY, 0f, Easing.EASE_OUT),
            Keyframe(durationMs, KeyframeProperty.OPACITY, 1f, Easing.EASE_OUT)
        )
    }

    fun createFadeOut(clipDurationMs: Long, fadeDurationMs: Long = 500L): List<Keyframe> {
        return listOf(
            Keyframe(clipDurationMs - fadeDurationMs, KeyframeProperty.OPACITY, 1f, Easing.EASE_IN),
            Keyframe(clipDurationMs, KeyframeProperty.OPACITY, 0f, Easing.EASE_IN)
        )
    }

    fun createVolumeDuck(
        startMs: Long,
        endMs: Long,
        normalVolume: Float = 1f,
        duckVolume: Float = 0.2f,
        fadeMs: Long = 300L
    ): List<Keyframe> {
        return listOf(
            Keyframe(startMs, KeyframeProperty.VOLUME, normalVolume, Easing.EASE_IN),
            Keyframe(startMs + fadeMs, KeyframeProperty.VOLUME, duckVolume, Easing.EASE_IN),
            Keyframe(endMs - fadeMs, KeyframeProperty.VOLUME, duckVolume, Easing.EASE_OUT),
            Keyframe(endMs, KeyframeProperty.VOLUME, normalVolume, Easing.EASE_OUT)
        )
    }

    fun createPulse(durationMs: Long, scaleMin: Float = 0.95f, scaleMax: Float = 1.05f): List<Keyframe> {
        val mid = durationMs / 2
        return listOf(
            Keyframe(0L, KeyframeProperty.SCALE_X, scaleMin, Easing.EASE_IN_OUT),
            Keyframe(0L, KeyframeProperty.SCALE_Y, scaleMin, Easing.EASE_IN_OUT),
            Keyframe(mid, KeyframeProperty.SCALE_X, scaleMax, Easing.EASE_IN_OUT),
            Keyframe(mid, KeyframeProperty.SCALE_Y, scaleMax, Easing.EASE_IN_OUT),
            Keyframe(durationMs, KeyframeProperty.SCALE_X, scaleMin, Easing.EASE_IN_OUT),
            Keyframe(durationMs, KeyframeProperty.SCALE_Y, scaleMin, Easing.EASE_IN_OUT)
        )
    }

    fun createShake(
        durationMs: Long,
        intensity: Float = 0.02f,
        frequency: Int = 10
    ): List<Keyframe> {
        val keyframes = mutableListOf<Keyframe>()
        val interval = durationMs / frequency
        for (i in 0..frequency) {
            val t = i * interval
            val offsetX = if (i % 2 == 0) intensity else -intensity
            val offsetY = if (i % 3 == 0) intensity else -intensity
            val decay = 1f - (i.toFloat() / frequency)
            keyframes.add(Keyframe(t, KeyframeProperty.POSITION_X, offsetX * decay, Easing.LINEAR))
            keyframes.add(Keyframe(t, KeyframeProperty.POSITION_Y, offsetY * decay, Easing.LINEAR))
        }
        keyframes.add(Keyframe(durationMs, KeyframeProperty.POSITION_X, 0f, Easing.EASE_OUT))
        keyframes.add(Keyframe(durationMs, KeyframeProperty.POSITION_Y, 0f, Easing.EASE_OUT))
        return keyframes
    }

    fun createDrift(
        durationMs: Long,
        startX: Float = -0.1f, startY: Float = 0f,
        endX: Float = 0.1f, endY: Float = 0f
    ): List<Keyframe> {
        return listOf(
            Keyframe(0L, KeyframeProperty.POSITION_X, startX, Easing.LINEAR),
            Keyframe(0L, KeyframeProperty.POSITION_Y, startY, Easing.LINEAR),
            Keyframe(durationMs, KeyframeProperty.POSITION_X, endX, Easing.LINEAR),
            Keyframe(durationMs, KeyframeProperty.POSITION_Y, endY, Easing.LINEAR)
        )
    }

    fun createSpin360(durationMs: Long, clockwise: Boolean = true): List<Keyframe> {
        return listOf(
            Keyframe(0L, KeyframeProperty.ROTATION, 0f, Easing.EASE_IN_OUT),
            Keyframe(durationMs, KeyframeProperty.ROTATION, if (clockwise) 360f else -360f, Easing.EASE_IN_OUT)
        )
    }

    fun createZoomInOut(
        durationMs: Long,
        startScale: Float = 1f,
        peakScale: Float = 1.5f
    ): List<Keyframe> {
        val mid = durationMs / 2
        return listOf(
            Keyframe(0L, KeyframeProperty.SCALE_X, startScale, Easing.EASE_IN_OUT),
            Keyframe(0L, KeyframeProperty.SCALE_Y, startScale, Easing.EASE_IN_OUT),
            Keyframe(mid, KeyframeProperty.SCALE_X, peakScale, Easing.EASE_IN_OUT),
            Keyframe(mid, KeyframeProperty.SCALE_Y, peakScale, Easing.EASE_IN_OUT),
            Keyframe(durationMs, KeyframeProperty.SCALE_X, startScale, Easing.EASE_IN_OUT),
            Keyframe(durationMs, KeyframeProperty.SCALE_Y, startScale, Easing.EASE_IN_OUT)
        )
    }
}

data class AnimatedState(
    val positionX: Float? = null,
    val positionY: Float? = null,
    val scaleX: Float? = null,
    val scaleY: Float? = null,
    val rotation: Float? = null,
    val opacity: Float? = null,
    val volume: Float? = null,
    val anchorX: Float? = null,
    val anchorY: Float? = null
)

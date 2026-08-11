package com.novacut.editor.engine

import kotlin.math.abs
import kotlin.math.hypot

/** Pure policy shared by the Android motion sampler and JVM contract tests. */
internal object StabilizationPolicy {

    data class MotionSample(
        val timestampMs: Long,
        val dx: Float,
        val dy: Float,
        val confidence: Float,
    )

    data class Correction(
        val timestampMs: Long,
        val dx: Float,
        val dy: Float,
        val confidence: Float,
    )

    fun correctionTrajectory(
        samples: List<MotionSample>,
        windowSize: Int,
    ): List<Correction> {
        if (samples.isEmpty()) return emptyList()
        val safeWindow = windowSize.coerceIn(1, 31)
        val cumulative = ArrayList<Pair<Float, Float>>(samples.size)
        var x = 0f
        var y = 0f
        samples.forEach { sample ->
            x += sample.dx
            y += sample.dy
            cumulative += x to y
        }

        val halfWindow = safeWindow / 2
        return samples.indices.map { index ->
            val start = (index - halfWindow).coerceAtLeast(0)
            val end = (index + halfWindow + 1).coerceAtMost(cumulative.size)
            val averageX = cumulative.subList(start, end).map { it.first }.average().toFloat()
            val averageY = cumulative.subList(start, end).map { it.second }.average().toFloat()
            val current = cumulative[index]
            Correction(
                timestampMs = samples[index].timestampMs,
                dx = (averageX - current.first).coerceIn(-1f, 1f),
                dy = (averageY - current.second).coerceIn(-1f, 1f),
                confidence = samples[index].confidence.coerceIn(0f, 1f),
            )
        }
    }

    fun averageShakeMagnitude(samples: List<MotionSample>): Float =
        samples.map { sample -> hypot(sample.dx.toDouble(), sample.dy.toDouble()).toFloat() }
            .average()
            .toFloat()
            .coerceIn(0f, 1f)

    fun maxShakeMagnitude(samples: List<MotionSample>): Float =
        samples.maxOfOrNull { sample -> hypot(sample.dx.toDouble(), sample.dy.toDouble()).toFloat() }
            ?.coerceIn(0f, 1f)
            ?: 0f

    /** Convert a correction envelope into the static crop/zoom applied at render time. */
    fun recommendedCropScale(corrections: List<Correction>, cropPercentage: Float): Float {
        val correctionEnvelope = corrections.maxOfOrNull { correction ->
            maxOf(abs(correction.dx), abs(correction.dy))
        } ?: 0f
        val crop = maxOf(cropPercentage, correctionEnvelope * 1.25f).coerceIn(0f, 0.3f)
        return (1f + crop).coerceIn(1f, 1.3f)
    }

    fun analysisIntervalMs(isLowRamDevice: Boolean, width: Int, height: Int): Long = when {
        isLowRamDevice -> 200L
        width.toLong() * height.toLong() >= 8_000_000L -> 150L
        else -> 100L
    }

    fun maximumDurationMs(isLowRamDevice: Boolean): Long =
        if (isLowRamDevice) 60_000L else 120_000L
}

package com.novacut.editor.engine

import com.novacut.editor.model.ProxyResolution
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Decides how tall a proxy should be for a given source.
 *
 * The proxy is the requested fraction of the height the viewer actually sees, so a portrait
 * phone clip stored as a rotated landscape frame scales from its displayed height, not its
 * coded one. Presentation works on the displayed frame too, which keeps the proxy in the
 * source's orientation.
 *
 * Each setting also names a working tier (1/2 is 540p, 1/4 is 270p, 1/8 is 135p, as fractions
 * of 1080p). A source no taller than that tier already decodes as cheaply as its proxy would,
 * so it is edited directly instead of being re-encoded into something smaller and softer.
 */
object ProxyResolutionPolicy {

    /** The smallest proxy worth editing against. */
    const val MIN_PROXY_HEIGHT = 120

    /** The height the setting tiers are fractions of, and the basis when a source can't be read. */
    private const val REFERENCE_HEIGHT = 1080

    sealed class Plan {
        data class Generate(val targetHeight: Int) : Plan()
        data class UseSource(val reason: String) : Plan()
    }

    /** The displayed height of a frame stored [codedWidth] by [codedHeight] with [rotationDegrees]. */
    fun displayHeight(codedWidth: Int, codedHeight: Int, rotationDegrees: Int): Int {
        if (codedWidth <= 0 || codedHeight <= 0) return 0
        return if (abs(rotationDegrees) % 180 == 90) codedWidth else codedHeight
    }

    /** [sourceDisplayHeight] is 0 or less when the source's dimensions couldn't be read. */
    fun plan(sourceDisplayHeight: Int, resolution: ProxyResolution): Plan {
        val tierHeight = (REFERENCE_HEIGHT * resolution.scale).roundToInt()
        if (sourceDisplayHeight in 1..tierHeight) {
            return Plan.UseSource(
                "source is ${sourceDisplayHeight}px tall, already at or below the ${tierHeight}px " +
                    "${resolution.label} proxy tier, so it is edited directly"
            )
        }
        val basis = if (sourceDisplayHeight > 0) sourceDisplayHeight else REFERENCE_HEIGHT
        val divisor = Media3ExportRobustnessPolicy.ENCODER_DIMENSION_DIVISOR
        val scaled = ((basis * resolution.scale) / divisor).roundToInt() * divisor
        return Plan.Generate(scaled.coerceAtLeast(MIN_PROXY_HEIGHT))
    }
}

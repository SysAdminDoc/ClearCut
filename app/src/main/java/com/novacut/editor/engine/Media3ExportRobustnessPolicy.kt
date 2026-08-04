package com.novacut.editor.engine

import kotlin.math.roundToInt

/** Small, deterministic guards around Media3's encoder-facing export inputs. */
internal object Media3ExportRobustnessPolicy {
    /** H.264/HEVC hardware encoders generally require dimensions divisible by two. */
    const val ENCODER_DIMENSION_DIVISOR = 2

    /** Avoid turning a speed-ramped 30/60 fps timeline into an unbounded frame stream. */
    const val MAX_SPEED_OUTPUT_FRAME_RATE = 60

    /** Batch/imported configurations may request up to the editor's 240 fps ceiling. */
    const val MAX_CONSTANT_OUTPUT_FRAME_RATE = 240

    data class Dimensions(val width: Int, val height: Int)

    /**
     * Rounds both dimensions the same way Media3 rounds a Presentation's computed side.
     * Keeping the operation here also protects exact-width/height presentations, which
     * cannot use Presentation.copyWithUnsetSideRoundedTo after both sides are set.
     */
    fun encoderSafeDimensions(
        width: Int,
        height: Int,
        divisor: Int = ENCODER_DIMENSION_DIVISOR,
    ): Dimensions = Dimensions(
        width = roundToMultiple(width, divisor),
        height = roundToMultiple(height, divisor),
    )

    /**
     * Returns the per-item output target for speed-processed or explicit CFR items.
     * Ordinary exports remain untouched and receive no per-item cap.
     */
    fun speedFrameRateCap(
        outputFrameRate: Int,
        speedChanged: Boolean,
        forceConstantFrameRate: Boolean = false,
    ): Int? {
        if (!speedChanged && !forceConstantFrameRate) return null
        val maximum = if (forceConstantFrameRate) {
            MAX_CONSTANT_OUTPUT_FRAME_RATE
        } else {
            MAX_SPEED_OUTPUT_FRAME_RATE
        }
        return outputFrameRate.coerceIn(1, maximum)
    }

    private fun roundToMultiple(value: Int, divisor: Int): Int {
        require(value > 0) { "Dimension must be positive: $value" }
        require(divisor > 0) { "Dimension divisor must be positive: $divisor" }
        return (value.toDouble() / divisor)
            .roundToInt()
            .coerceAtLeast(1)
            .let { rounded -> rounded * divisor }
    }
}

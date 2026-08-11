package com.novacut.editor.model

/**
 * Non-destructive stabilization data attached to a clip. The source pixels are
 * never replaced; the render path projects these points into a transform at
 * presentation time.
 */
data class StabilizationData(
    val motion: List<StabilizationMotionPoint> = emptyList(),
    val lensProfile: StabilizationLensProfile = StabilizationLensProfile(),
    val syncOffsetMs: Long = 0L,
    val cropScale: Float = 1f,
    val sourceDurationMs: Long = 0L,
) {
    init {
        require(cropScale.isFinite() && cropScale in 1f..1.3f) {
            "Stabilization crop scale must be finite and between 1 and 1.3"
        }
        require(sourceDurationMs >= 0L) { "Stabilization source duration must be non-negative" }
    }

    val isUsable: Boolean get() = motion.size >= 2

    private val orderedMotion: List<StabilizationMotionPoint> by lazy(LazyThreadSafetyMode.NONE) {
        motion.sortedBy { it.timestampMs }
    }

    /** Return the interpolated counter-motion at a source timestamp. */
    fun correctionAtSourceTimeMs(sourceTimeMs: Long): StabilizationMotionPoint? {
        if (motion.isEmpty()) return null
        val adjustedTime = if (syncOffsetMs > 0L && sourceTimeMs > Long.MAX_VALUE - syncOffsetMs) {
            Long.MAX_VALUE
        } else if (syncOffsetMs < 0L && sourceTimeMs < Long.MIN_VALUE - syncOffsetMs) {
            Long.MIN_VALUE
        } else {
            sourceTimeMs + syncOffsetMs
        }
        val ordered = orderedMotion
        if (adjustedTime <= ordered.first().timestampMs) return ordered.first()
        if (adjustedTime >= ordered.last().timestampMs) return ordered.last()

        val nextIndex = ordered.indexOfFirst { it.timestampMs >= adjustedTime }
        if (nextIndex <= 0) return ordered.first()
        val previous = ordered[nextIndex - 1]
        val next = ordered[nextIndex]
        val span = (next.timestampMs - previous.timestampMs).toFloat()
        if (span <= 0f) return previous
        val fraction = ((adjustedTime - previous.timestampMs).toFloat() / span).coerceIn(0f, 1f)
        return StabilizationMotionPoint(
            timestampMs = adjustedTime,
            dx = previous.dx + (next.dx - previous.dx) * fraction,
            dy = previous.dy + (next.dy - previous.dy) * fraction,
            confidence = previous.confidence + (next.confidence - previous.confidence) * fraction,
        )
    }
}

data class StabilizationMotionPoint(
    val timestampMs: Long,
    /** Normalized counter-motion in the same coordinate space as clip transforms. */
    val dx: Float,
    val dy: Float,
    val confidence: Float = 1f,
)

data class StabilizationLensProfile(
    val name: String = "android-identity",
    val focalLengthMm: Float? = null,
    val distortionK1: Float = 0f,
    val distortionK2: Float = 0f,
)

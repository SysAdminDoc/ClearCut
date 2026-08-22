package com.novacut.editor.model

/**
 * Reusable offline stabilization assumptions. This is deliberately data-only:
 * it describes the lens, motion search, crop, and sync choices used for a
 * future analysis, never executable code or a source-media path.
 */
data class StabilizationProfile(
    val id: String = "balanced-handheld",
    val name: String = "Balanced handheld",
    val lens: StabilizationLensProfile = StabilizationLensProfile(),
    val motion: StabilizationMotionProfile = StabilizationMotionProfile(),
    val cropScale: Float = 1.1f,
    val syncOffsetMs: Long = 0L,
) {
    init {
        require(id.isNotBlank() && id.length <= 128) { "Stabilization profile id is invalid" }
        require(name.isNotBlank() && name.length <= 120) { "Stabilization profile name is invalid" }
        require(cropScale.isFinite() && cropScale in 1f..1.3f) {
            "Stabilization profile crop scale must be between 1 and 1.3"
        }
        require(syncOffsetMs in -60_000L..60_000L) {
            "Stabilization profile sync offset must remain within 60 seconds"
        }
    }
}

data class StabilizationMotionProfile(
    val smoothingStrength: Float = 0.5f,
    val cropPercentage: Float = 0.15f,
    val algorithm: String = "LK_OPTICAL_FLOW",
    val maxFeatures: Int = 300,
    val useAffine: Boolean = true,
    val analysisIntervalMs: Long? = null,
    val smoothingWindow: Int = 5,
) {
    init {
        require(smoothingStrength.isFinite() && smoothingStrength in 0f..1f)
        require(cropPercentage.isFinite() && cropPercentage in 0f..0.3f)
        require(maxFeatures in 1..1_000)
        require(analysisIntervalMs == null || analysisIntervalMs in 1L..10_000L)
        require(smoothingWindow in 1..31)
    }
}

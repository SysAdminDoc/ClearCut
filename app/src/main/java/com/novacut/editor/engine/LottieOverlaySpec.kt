package com.novacut.editor.engine

/**
 * Specification for a Lottie animated overlay in the export pipeline.
 * Created by the ViewModel when preparing export with animated title overlays.
 */
data class LottieOverlaySpec(
    val engine: LottieTemplateEngine,
    val composition: com.airbnb.lottie.LottieComposition,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val textReplacements: Map<String, String> = emptyMap()
)

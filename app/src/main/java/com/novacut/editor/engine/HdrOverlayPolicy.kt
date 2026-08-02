package com.novacut.editor.engine

import com.novacut.editor.model.VideoCodec

/**
 * Describes the overlay inputs that currently cannot share Media3's HDR
 * preservation mode. Lottie has a dedicated ClearCut shader path; text,
 * bitmap, animated-image, and watermark overlays still use Media3's
 * OverlayShaderProgram, which is not safe for HDR colors.
 */
data class HdrOverlaySummary(
    val textOverlayCount: Int = 0,
    val imageOverlayCount: Int = 0,
    val watermarkPresent: Boolean = false,
) {
    val hasUnsafeBitmapOverlays: Boolean
        get() = textOverlayCount > 0 || imageOverlayCount > 0 || watermarkPresent

    fun labels(): List<String> = buildList {
        if (textOverlayCount > 0) add("text overlay${if (textOverlayCount == 1) "" else "s"}")
        if (imageOverlayCount > 0) add("image overlay${if (imageOverlayCount == 1) "" else "s"}")
        if (watermarkPresent) add("watermark")
    }
}

data class HdrOverlayDecision(
    val preserveHdr: Boolean,
    val requiresSdrFallback: Boolean,
    val disclosure: String? = null,
)

object HdrOverlayPolicy {
    fun evaluate(
        hdrRequested: Boolean,
        codec: VideoCodec,
        overlays: HdrOverlaySummary = HdrOverlaySummary(),
    ): HdrOverlayDecision {
        val codecCanCarryHdr = codec != VideoCodec.H264
        val fallbackRequired = hdrRequested && codecCanCarryHdr && overlays.hasUnsafeBitmapOverlays
        return HdrOverlayDecision(
            preserveHdr = hdrRequested && codecCanCarryHdr && !fallbackRequired,
            requiresSdrFallback = fallbackRequired,
            disclosure = if (fallbackRequired) {
                val subjects = overlays.labels().joinToString(", ")
                "HDR preservation is unavailable with $subjects. ClearCut will use the SDR " +
                    "overlay path to avoid Media3 HDR overlay color errors."
            } else {
                null
            },
        )
    }
}

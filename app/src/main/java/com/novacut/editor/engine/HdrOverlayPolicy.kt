package com.novacut.editor.engine

import android.os.Build
import com.novacut.editor.model.VideoCodec

/** Media3's OverlayShaderProgram refuses more than this many texture samplers. */
const val MAX_HDR_OVERLAY_SAMPLERS = 15

/**
 * Describes the overlay inputs that are considered while negotiating HDR.
 *
 * Media3 can keep native [TextOverlay] instances on HDR with one sampler. A
 * still [BitmapOverlay] is HDR-safe on API 34+ only when its bitmap carries a
 * gain map, which consumes two samplers. Stroked text and animated images are
 * bitmap paths without a gain map and therefore remain on the disclosed SDR
 * fallback path.
 */
data class HdrOverlaySummary(
    val textOverlayCount: Int = 0,
    val strokedTextOverlayCount: Int = 0,
    /** Still image overlays, excluding animated image sources. */
    val imageOverlayCount: Int = 0,
    val gainMappedImageOverlayCount: Int = 0,
    val animatedImageOverlayCount: Int = 0,
    val watermarkPresent: Boolean = false,
    val watermarkHasGainMap: Boolean = false,
    /** Maximum simultaneous HDR sampler count, when timeline intervals are known. */
    val maxConcurrentHdrSamplerCount: Int? = null,
) {
    private val unsupportedImageOverlayCount: Int
        get() = (imageOverlayCount - gainMappedImageOverlayCount).coerceAtLeast(0)

    fun hasUnsafeBitmapOverlays(apiLevel: Int = Build.VERSION.SDK_INT): Boolean {
        val gainMappedBitmapsSupported = apiLevel >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        return strokedTextOverlayCount > 0 ||
            animatedImageOverlayCount > 0 ||
            unsupportedImageOverlayCount > 0 ||
            (watermarkPresent && (!watermarkHasGainMap || !gainMappedBitmapsSupported))
    }

    val hasUnsafeBitmapOverlays: Boolean
        get() = hasUnsafeBitmapOverlays()

    val hdrSamplerCount: Int
        get() = maxConcurrentHdrSamplerCount ?: (
            textOverlayCount +
                (gainMappedImageOverlayCount * 2) +
                if (watermarkPresent && watermarkHasGainMap) 2 else 0
            )

    fun labels(apiLevel: Int = Build.VERSION.SDK_INT): List<String> = buildList {
        if (strokedTextOverlayCount > 0) {
            add("stroked text overlay${if (strokedTextOverlayCount == 1) "" else "s"}")
        }
        if (animatedImageOverlayCount > 0) {
            add("animated image overlay${if (animatedImageOverlayCount == 1) "" else "s"}")
        }
        if (unsupportedImageOverlayCount > 0 ||
            (imageOverlayCount > 0 && apiLevel < Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        ) {
            add("image overlay${if (unsupportedImageOverlayCount == 1) "" else "s"}")
        }
        if (watermarkPresent && (!watermarkHasGainMap || apiLevel < Build.VERSION_CODES.UPSIDE_DOWN_CAKE)) {
            add("watermark")
        }
    }
}

data class HdrOverlayDecision(
    val preserveHdr: Boolean,
    val requiresSdrFallback: Boolean,
    val disclosure: String? = null,
    val samplerCount: Int = 0,
    val samplerBudgetExceeded: Boolean = false,
)

object HdrOverlayPolicy {
    fun evaluate(
        hdrRequested: Boolean,
        codec: VideoCodec,
        overlays: HdrOverlaySummary = HdrOverlaySummary(),
        apiLevel: Int = Build.VERSION.SDK_INT,
    ): HdrOverlayDecision {
        val codecCanCarryHdr = codec != VideoCodec.H264
        val fallbackRequired = hdrRequested && codecCanCarryHdr && overlays.hasUnsafeBitmapOverlays(apiLevel)
        val samplerCount = overlays.hdrSamplerCount
        val samplerBudgetExceeded = hdrRequested && codecCanCarryHdr &&
            !fallbackRequired && samplerCount > MAX_HDR_OVERLAY_SAMPLERS
        val disclosure = when {
            fallbackRequired -> {
                val subjects = overlays.labels(apiLevel).joinToString(", ")
                "HDR preservation is unavailable with $subjects. ClearCut will use the SDR " +
                    "overlay path to avoid Media3 HDR overlay color errors."
            }
            samplerBudgetExceeded -> {
                "HDR overlay sampler budget exceeded ($samplerCount/$MAX_HDR_OVERLAY_SAMPLERS). " +
                    "Remove some simultaneous overlays or disable HDR before exporting."
            }
            else -> null
        }
        return HdrOverlayDecision(
            preserveHdr = hdrRequested && codecCanCarryHdr &&
                !fallbackRequired && !samplerBudgetExceeded,
            requiresSdrFallback = fallbackRequired,
            disclosure = disclosure,
            samplerCount = samplerCount,
            samplerBudgetExceeded = samplerBudgetExceeded,
        )
    }

    /** Fail before Media3 turns the same condition into an unnamed GL exception. */
    fun throwIfSamplerBudgetExceeded(decision: HdrOverlayDecision) {
        if (!decision.samplerBudgetExceeded) return
        throw ExportStageException(
            stage = "HDR overlay sampler budget",
            subjectId = null,
            message = decision.disclosure ?:
                "HDR overlay sampler budget exceeded (${decision.samplerCount}/$MAX_HDR_OVERLAY_SAMPLERS).",
        )
    }
}

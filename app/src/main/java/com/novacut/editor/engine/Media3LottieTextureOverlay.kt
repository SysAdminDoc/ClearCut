package com.novacut.editor.engine

import android.graphics.Bitmap
import androidx.media3.common.OverlaySettings
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.effect.lottie.LottieOverlay
import com.airbnb.lottie.LottieComposition
import com.airbnb.lottie.LottieDrawable
import com.airbnb.lottie.TextDelegate
import kotlin.math.roundToLong

internal enum class LottieOverlayBackend {
    MEDIA3_LOTTIE,
    CLEARCUT_SHADER
}

internal data class LottieOverlayBackendDecision(
    val backend: LottieOverlayBackend,
    val reason: String
)

internal fun chooseLottieOverlayBackend(
    preserveHdr: Boolean,
    overlayDurationUs: Long,
    compositionDurationUs: Long
): LottieOverlayBackendDecision {
    if (preserveHdr) {
        return LottieOverlayBackendDecision(
            LottieOverlayBackend.CLEARCUT_SHADER,
            "Media3 OverlayEffect treats Lottie bitmaps as Ultra HDR overlays on HDR input; ClearCut shader preserves the existing SDR-over-HDR fallback."
        )
    }
    if (compositionDurationUs <= 0L) {
        return LottieOverlayBackendDecision(
            LottieOverlayBackend.CLEARCUT_SHADER,
            "composition duration is unavailable"
        )
    }
    if (overlayDurationUs > compositionDurationUs) {
        return LottieOverlayBackendDecision(
            LottieOverlayBackend.CLEARCUT_SHADER,
            "Media3 LottieOverlay loops animations; ClearCut shader holds the final frame for over-long title windows."
        )
    }
    return LottieOverlayBackendDecision(
        LottieOverlayBackend.MEDIA3_LOTTIE,
        "official Media3 Lottie overlay has duration, text, and full-frame parity for this overlay"
    )
}

internal fun lottieOverlayAlphaScale(
    presentationTimeUs: Long,
    overlayStartUs: Long,
    overlayDurationUs: Long
): Float {
    val relativeUs = presentationTimeUs - overlayStartUs
    return if (relativeUs in 0..overlayDurationUs) 1f else 0f
}

internal fun lottieCompositionDurationUs(composition: LottieComposition): Long {
    return composition.duration
        .takeIf { it.isFinite() && it > 0f }
        ?.let { (it * 1_000f).roundToLong() }
        ?: 0L
}

internal fun lottieScaleToFrame(
    compositionWidth: Int,
    compositionHeight: Int,
    frameWidth: Int,
    frameHeight: Int
): Pair<Float, Float> {
    val safeCompositionWidth = compositionWidth.coerceAtLeast(1)
    val safeCompositionHeight = compositionHeight.coerceAtLeast(1)
    val safeFrameWidth = frameWidth.coerceAtLeast(1)
    val safeFrameHeight = frameHeight.coerceAtLeast(1)
    return safeFrameWidth.toFloat() / safeCompositionWidth.toFloat() to
        safeFrameHeight.toFloat() / safeCompositionHeight.toFloat()
}

/**
 * Adapts Media3's first-party Lottie renderer to ClearCut's timeline semantics.
 *
 * Media3 owns the Lottie canvas drawing and texture upload path. This wrapper keeps
 * the existing ClearCut behaviors that the export timeline depends on:
 * - animation time is relative to the overlay start, not the clip start;
 * - alpha is zero outside the overlay window;
 * - the Lottie canvas is sized to the current video frame so old full-frame
 *   templates render like the custom shader path;
 * - TextDelegate substitutions are applied to the supplied LottieDrawable.
 */
@UnstableApi
internal class Media3LottieTextureOverlay(
    private val composition: LottieComposition,
    private val overlayStartUs: Long,
    private val overlayDurationUs: Long,
    private val textReplacements: Map<String, String> = emptyMap()
) : BitmapOverlay() {
    private val visibleSettings = StaticOverlaySettings.Builder()
        .setAlphaScale(1f)
        .build()
    private val hiddenSettings = StaticOverlaySettings.Builder()
        .setAlphaScale(0f)
        .build()

    private var delegate: LottieOverlay? = null

    override fun configure(videoSize: Size) {
        super.configure(videoSize)
        delegate?.release()
        delegate = buildDelegate(videoSize).also { it.configure(videoSize) }
    }

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        val activeDelegate = delegate
            ?: throw VideoFrameProcessingException(
                IllegalStateException("Media3 Lottie overlay used before configure()")
            )
        return activeDelegate.getBitmap(animationPresentationTimeUs(presentationTimeUs))
    }

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
        return if (lottieOverlayAlphaScale(presentationTimeUs, overlayStartUs, overlayDurationUs) > 0f) {
            visibleSettings
        } else {
            hiddenSettings
        }
    }

    override fun release() {
        delegate?.release()
        delegate = null
        super.release()
    }

    private fun buildDelegate(videoSize: Size): LottieOverlay {
        val bounds = composition.bounds
        val (scaleX, scaleY) = lottieScaleToFrame(
            compositionWidth = bounds.width(),
            compositionHeight = bounds.height(),
            frameWidth = videoSize.width,
            frameHeight = videoSize.height
        )
        val drawSettings = StaticOverlaySettings.Builder()
            .setScale(scaleX, scaleY)
            .build()
        val drawable = LottieDrawable()
        if (textReplacements.isNotEmpty()) {
            val textDelegate = TextDelegate(drawable)
            textReplacements.forEach { (layerName, text) ->
                textDelegate.setText(layerName, text)
            }
            drawable.setTextDelegate(textDelegate)
        }
        return LottieOverlay.Builder(CompositionProvider(composition))
            .setLottieDrawable(drawable)
            .setOverlaySettings(drawSettings)
            .build()
    }

    private fun animationPresentationTimeUs(presentationTimeUs: Long): Long {
        return (presentationTimeUs - overlayStartUs).coerceAtLeast(0L)
    }

    private class CompositionProvider(
        private val composition: LottieComposition
    ) : LottieOverlay.LottieProvider {
        override fun release() = Unit
        override fun getLottieComposition(): LottieComposition = composition
    }
}

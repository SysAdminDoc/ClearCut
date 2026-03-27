package com.novacut.editor.engine

import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.AlignmentSpan
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import androidx.media3.common.util.UnstableApi
import com.novacut.editor.model.TextAlignment
import com.novacut.editor.model.TextAnimation
import com.novacut.editor.model.TextOverlay

/**
 * Text overlay that renders within a specific time range during export.
 * Converts model TextOverlay properties to Media3 SpannableString styling.
 */
@UnstableApi
internal class ExportTextOverlay(
    private val overlay: TextOverlay,
    private val relStartMs: Long,
    private val relEndMs: Long
) : androidx.media3.effect.TextOverlay() {

    private val animDurationMs = 500L
    private var currentAlpha = 1f

    override fun getText(presentationTimeUs: Long): SpannableString {
        val timeMs = presentationTimeUs / 1000L
        if (timeMs < relStartMs || timeMs > relEndMs) {
            currentAlpha = 0f
            return SpannableString("")
        }

        computeAnimationState(timeMs)

        val fullText = overlay.text
        val displayText = if (overlay.animationIn == TextAnimation.TYPEWRITER) {
            val elapsed = timeMs - relStartMs
            val charCount = ((elapsed.toFloat() / animDurationMs) * fullText.length)
                .toInt().coerceIn(0, fullText.length)
            fullText.substring(0, charCount)
        } else {
            fullText
        }
        val text = SpannableString(displayText)
        if (displayText.isNotEmpty()) {
            val baseColor = overlay.color.toInt()
            val alphaInt = (currentAlpha * 255f).toInt().coerceIn(0, 255)
            val alphaColor = (baseColor and 0x00FFFFFF) or (alphaInt shl 24)
            text.setSpan(
                ForegroundColorSpan(alphaColor),
                0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            text.setSpan(
                AbsoluteSizeSpan(overlay.fontSize.toInt(), true),
                0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            val style = when {
                overlay.bold && overlay.italic -> Typeface.BOLD_ITALIC
                overlay.bold -> Typeface.BOLD
                overlay.italic -> Typeface.ITALIC
                else -> Typeface.NORMAL
            }
            if (style != Typeface.NORMAL) {
                text.setSpan(StyleSpan(style), 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            text.setSpan(
                TypefaceSpan(overlay.fontFamily),
                0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            if (overlay.backgroundColor.toInt() and 0xFF000000.toInt() != 0) {
                val bgAlpha = (currentAlpha * ((overlay.backgroundColor.toInt() ushr 24) and 0xFF)).toInt().coerceIn(0, 255)
                val bgColor = (overlay.backgroundColor.toInt() and 0x00FFFFFF) or (bgAlpha shl 24)
                text.setSpan(
                    BackgroundColorSpan(bgColor),
                    0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            val alignment = when (overlay.alignment) {
                TextAlignment.LEFT -> Layout.Alignment.ALIGN_NORMAL
                TextAlignment.CENTER -> Layout.Alignment.ALIGN_CENTER
                TextAlignment.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
            }
            text.setSpan(
                AlignmentSpan.Standard(alignment),
                0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        return text
    }

    override fun getVertexTransformation(presentationTimeUs: Long): FloatArray {
        val timeMs = presentationTimeUs / 1000L
        if (timeMs < relStartMs || timeMs > relEndMs) {
            return floatArrayOf(
                0f, 0f, 0f, 0f,
                0f, 0f, 0f, 0f,
                0f, 0f, 1f, 0f,
                0f, 0f, 0f, 1f
            )
        }

        computeAnimationState(timeMs)

        val tx = currentOffsetX + (overlay.positionX * 2f - 1f)
        val ty = currentOffsetY - (overlay.positionY * 2f - 1f)
        val sx = currentScale
        val sy = currentScale
        val rad = currentRotation * (kotlin.math.PI.toFloat() / 180f)
        val cos = kotlin.math.cos(rad)
        val sin = kotlin.math.sin(rad)

        return floatArrayOf(
            sx * cos, sx * sin, 0f, 0f,
            -sy * sin, sy * cos, 0f, 0f,
            0f, 0f, 1f, 0f,
            tx, ty, 0f, 1f
        )
    }

    private var lastComputedTimeMs = -1L
    private var currentOffsetX = 0f
    private var currentOffsetY = 0f
    private var currentScale = 1f
    private var currentRotation = 0f

    private fun computeAnimationState(timeMs: Long) {
        if (timeMs == lastComputedTimeMs) return
        lastComputedTimeMs = timeMs

        currentAlpha = 1f
        currentOffsetX = 0f
        currentOffsetY = 0f
        currentScale = 1f
        currentRotation = 0f

        val inProgress = if (overlay.animationIn != TextAnimation.NONE) {
            ((timeMs - relStartMs).toFloat() / animDurationMs).coerceIn(0f, 1f)
        } else 1f

        val outProgress = if (overlay.animationOut != TextAnimation.NONE) {
            ((relEndMs - timeMs).toFloat() / animDurationMs).coerceIn(0f, 1f)
        } else 1f

        when (overlay.animationIn) {
            TextAnimation.FADE -> currentAlpha *= easeOut(inProgress)
            TextAnimation.SLIDE_UP -> currentOffsetY -= (1f - easeOut(inProgress)) * 0.3f
            TextAnimation.SLIDE_DOWN -> currentOffsetY += (1f - easeOut(inProgress)) * 0.3f
            TextAnimation.SLIDE_LEFT -> currentOffsetX -= (1f - easeOut(inProgress)) * 0.3f
            TextAnimation.SLIDE_RIGHT -> currentOffsetX += (1f - easeOut(inProgress)) * 0.3f
            TextAnimation.SCALE -> currentScale *= easeOut(inProgress)
            TextAnimation.SPIN -> currentRotation += (1f - easeOut(inProgress)) * 360f
            TextAnimation.BOUNCE -> {
                val t = easeOut(inProgress)
                currentOffsetY -= (1f - bounceEase(t)) * 0.3f
            }
            TextAnimation.TYPEWRITER -> { /* handled in getText() */ }
            TextAnimation.NONE -> { }
            TextAnimation.BLUR_IN -> currentAlpha *= easeOut(inProgress)
            TextAnimation.GLITCH -> currentOffsetX += (1f - easeOut(inProgress)) * 0.05f * kotlin.math.sin(inProgress * 30f)
            TextAnimation.WAVE -> currentOffsetY -= kotlin.math.sin(inProgress * 6.28f) * 0.05f
            TextAnimation.ELASTIC -> {
                val t = easeOut(inProgress)
                currentScale *= if (t < 1f) (1f + 0.3f * kotlin.math.sin(t * 3.14f * 3f) * (1f - t)) else 1f
            }
            TextAnimation.FLIP -> currentRotation += (1f - easeOut(inProgress)) * 180f
        }

        when (overlay.animationOut) {
            TextAnimation.FADE -> currentAlpha *= easeOut(outProgress)
            TextAnimation.SLIDE_UP -> currentOffsetY += (1f - easeOut(outProgress)) * 0.3f
            TextAnimation.SLIDE_DOWN -> currentOffsetY -= (1f - easeOut(outProgress)) * 0.3f
            TextAnimation.SLIDE_LEFT -> currentOffsetX += (1f - easeOut(outProgress)) * 0.3f
            TextAnimation.SLIDE_RIGHT -> currentOffsetX -= (1f - easeOut(outProgress)) * 0.3f
            TextAnimation.SCALE -> currentScale *= easeOut(outProgress)
            TextAnimation.SPIN -> currentRotation -= (1f - easeOut(outProgress)) * 360f
            TextAnimation.BOUNCE -> {
                val t = easeOut(outProgress)
                currentOffsetY += (1f - bounceEase(t)) * 0.3f
            }
            TextAnimation.TYPEWRITER -> currentAlpha *= outProgress
            TextAnimation.NONE -> { }
            TextAnimation.BLUR_IN -> currentAlpha *= easeOut(outProgress)
            TextAnimation.GLITCH -> currentOffsetX -= (1f - easeOut(outProgress)) * 0.05f * kotlin.math.sin(outProgress * 30f)
            TextAnimation.WAVE -> currentOffsetY += kotlin.math.sin(outProgress * 6.28f) * 0.05f
            TextAnimation.ELASTIC -> currentScale *= easeOut(outProgress)
            TextAnimation.FLIP -> currentRotation -= (1f - easeOut(outProgress)) * 180f
        }
    }

    private fun easeOut(t: Float): Float = 1f - (1f - t) * (1f - t)

    private fun bounceEase(t: Float): Float {
        return when {
            t < 0.3636f -> 7.5625f * t * t
            t < 0.7273f -> 7.5625f * (t - 0.5455f) * (t - 0.5455f) + 0.75f
            t < 0.9091f -> 7.5625f * (t - 0.8182f) * (t - 0.8182f) + 0.9375f
            else -> 7.5625f * (t - 0.9545f) * (t - 0.9545f) + 0.984375f
        }
    }
}

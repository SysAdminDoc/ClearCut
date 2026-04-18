package com.novacut.editor.engine

import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.*
import com.novacut.editor.engine.segmentation.SegmentationEngine
import com.novacut.editor.model.*

/**
 * Pure mapping from NovaCut model types to Media3 Effect instances.
 * Stateless — all dependencies are passed as parameters.
 */
@UnstableApi
internal object EffectBuilder {

    /**
     * Convert a NovaCut Effect to a Media3 Effect.
     * Returns null for effect types handled outside the visual pipeline (speed, reverse).
     */
    fun buildVideoEffect(
        effect: Effect,
        segmentationEngine: SegmentationEngine
    ): androidx.media3.common.Effect? {
        return when (effect.type) {
            EffectType.BRIGHTNESS -> {
                val value = (effect.params["value"] ?: 0f).coerceIn(-1f, 1f)
                RgbMatrix { _, _ ->
                    val b = value
                    floatArrayOf(
                        1f, 0f, 0f, b,
                        0f, 1f, 0f, b,
                        0f, 0f, 1f, b,
                        0f, 0f, 0f, 1f
                    )
                }
            }
            EffectType.CONTRAST -> {
                val value = (effect.params["value"] ?: 1f).coerceIn(0f, 2f)
                Contrast(value - 1f)
            }
            EffectType.SATURATION -> {
                val value = (effect.params["value"] ?: 1f).coerceIn(0f, 3f)
                RgbMatrix { _, _ ->
                    val s = value
                    val sr = (1 - s) * 0.2126f
                    val sg = (1 - s) * 0.7152f
                    val sb = (1 - s) * 0.0722f
                    floatArrayOf(
                        sr + s, sg, sb, 0f,
                        sr, sg + s, sb, 0f,
                        sr, sg, sb + s, 0f,
                        0f, 0f, 0f, 1f
                    )
                }
            }
            EffectType.GRAYSCALE -> {
                RgbMatrix { _, _ ->
                    floatArrayOf(
                        0.2126f, 0.7152f, 0.0722f, 0f,
                        0.2126f, 0.7152f, 0.0722f, 0f,
                        0.2126f, 0.7152f, 0.0722f, 0f,
                        0f, 0f, 0f, 1f
                    )
                }
            }
            EffectType.SEPIA -> {
                RgbMatrix { _, _ ->
                    floatArrayOf(
                        0.393f, 0.769f, 0.189f, 0f,
                        0.349f, 0.686f, 0.168f, 0f,
                        0.272f, 0.534f, 0.131f, 0f,
                        0f, 0f, 0f, 1f
                    )
                }
            }
            EffectType.INVERT -> {
                RgbMatrix { _, _ ->
                    floatArrayOf(
                        -1f, 0f, 0f, 1f,
                        0f, -1f, 0f, 1f,
                        0f, 0f, -1f, 1f,
                        0f, 0f, 0f, 1f
                    )
                }
            }
            EffectType.TEMPERATURE -> {
                val value = (effect.params["value"] ?: 0f).coerceIn(-5f, 5f)
                RgbMatrix { _, _ ->
                    floatArrayOf(
                        1f + value * 0.1f, 0f, 0f, 0f,
                        0f, 1f, 0f, 0f,
                        0f, 0f, 1f - value * 0.1f, 0f,
                        0f, 0f, 0f, 1f
                    )
                }
            }
            EffectType.TINT -> {
                val value = (effect.params["value"] ?: 0f).coerceIn(-1f, 1f)
                RgbMatrix { _, _ ->
                    floatArrayOf(
                        1f, 0f, 0f, 0f,
                        0f, 1f + value * 0.1f, 0f, 0f,
                        0f, 0f, 1f, 0f,
                        0f, 0f, 0f, 1f
                    )
                }
            }
            EffectType.EXPOSURE -> {
                val value = (effect.params["value"] ?: 0f).coerceIn(-2f, 2f)
                val mul = Math.pow(2.0, value.toDouble()).toFloat()
                RgbMatrix { _, _ ->
                    floatArrayOf(
                        mul, 0f, 0f, 0f,
                        0f, mul, 0f, 0f,
                        0f, 0f, mul, 0f,
                        0f, 0f, 0f, 1f
                    )
                }
            }
            EffectType.GAMMA -> {
                val value = (effect.params["value"] ?: 1f).coerceIn(0.2f, 5f)
                val inv = 1f / value
                RgbMatrix { _, _ ->
                    floatArrayOf(
                        inv, 0f, 0f, 0f,
                        0f, inv, 0f, 0f,
                        0f, 0f, inv, 0f,
                        0f, 0f, 0f, 1f
                    )
                }
            }
            EffectType.HIGHLIGHTS -> {
                val value = (effect.params["value"] ?: 0f).coerceIn(-1f, 1f)
                val scale = 1f + value * 0.3f
                RgbMatrix { _, _ ->
                    floatArrayOf(
                        scale, 0f, 0f, 0f,
                        0f, scale, 0f, 0f,
                        0f, 0f, scale, 0f,
                        0f, 0f, 0f, 1f
                    )
                }
            }
            EffectType.SHADOWS -> {
                val value = (effect.params["value"] ?: 0f).coerceIn(-1f, 1f)
                val offset = value * 0.15f
                RgbMatrix { _, _ ->
                    floatArrayOf(
                        1f, 0f, 0f, offset,
                        0f, 1f, 0f, offset,
                        0f, 0f, 1f, offset,
                        0f, 0f, 0f, 1f
                    )
                }
            }
            EffectType.VIBRANCE -> {
                val value = (effect.params["value"] ?: 0f).coerceIn(-1f, 1f)
                val s = 1f + value * 0.5f
                val sr = (1 - s) * 0.2126f
                val sg = (1 - s) * 0.7152f
                val sb = (1 - s) * 0.0722f
                RgbMatrix { _, _ ->
                    floatArrayOf(
                        sr + s, sg, sb, 0f,
                        sr, sg + s, sb, 0f,
                        sr, sg, sb + s, 0f,
                        0f, 0f, 0f, 1f
                    )
                }
            }
            EffectType.POSTERIZE -> {
                val levels = (effect.params["levels"] ?: 6f).coerceIn(2f, 16f)
                val scale = levels / 8f
                RgbMatrix { _, _ ->
                    floatArrayOf(
                        scale, 0f, 0f, (1f - scale) * 0.5f,
                        0f, scale, 0f, (1f - scale) * 0.5f,
                        0f, 0f, scale, (1f - scale) * 0.5f,
                        0f, 0f, 0f, 1f
                    )
                }
            }
            EffectType.COOL_TONE -> {
                val intensity = (effect.params["intensity"] ?: 0.5f).coerceIn(0f, 1f)
                RgbMatrix { _, _ ->
                    floatArrayOf(
                        1f - intensity * 0.1f, 0f, 0f, 0f,
                        0f, 1f, 0f, 0f,
                        0f, 0f, 1f + intensity * 0.15f, intensity * 0.02f,
                        0f, 0f, 0f, 1f
                    )
                }
            }
            EffectType.WARM_TONE -> {
                val intensity = (effect.params["intensity"] ?: 0.5f).coerceIn(0f, 1f)
                RgbMatrix { _, _ ->
                    floatArrayOf(
                        1f + intensity * 0.15f, 0f, 0f, intensity * 0.02f,
                        0f, 1f + intensity * 0.05f, 0f, 0f,
                        0f, 0f, 1f - intensity * 0.1f, 0f,
                        0f, 0f, 0f, 1f
                    )
                }
            }
            EffectType.CYBERPUNK -> {
                val intensity = (effect.params["intensity"] ?: 0.7f).coerceIn(0f, 1f)
                val s = 1f + intensity * 0.3f
                RgbMatrix { _, _ ->
                    floatArrayOf(
                        s, 0f, 0f, intensity * 0.05f,
                        0f, 1f - intensity * 0.1f, 0f, -intensity * 0.02f,
                        0f, 0f, s, intensity * 0.08f,
                        0f, 0f, 0f, 1f
                    )
                }
            }
            EffectType.NOIR -> {
                val intensity = (effect.params["intensity"] ?: 0.7f).coerceIn(0f, 1f)
                val gray = intensity
                val tint = intensity * 0.03f
                val lr = 0.2126f * gray + (1f - gray)
                val lg = 0.7152f * gray
                val lb = 0.0722f * gray
                RgbMatrix { _, _ ->
                    floatArrayOf(
                        lr, lg, lb, tint,
                        0.2126f * gray, 0.7152f * gray + (1f - gray), 0.0722f * gray, 0f,
                        0.2126f * gray, 0.7152f * gray, 0.0722f * gray + (1f - gray), -tint,
                        0f, 0f, 0f, 1f
                    )
                }
            }
            EffectType.VINTAGE -> {
                val intensity = (effect.params["intensity"] ?: 0.7f).coerceIn(0f, 1f)
                val i = intensity
                RgbMatrix { _, _ ->
                    floatArrayOf(
                        1f - i * 0.3f + i * 0.393f * 0.5f, i * 0.769f * 0.5f, i * 0.189f * 0.5f, i * 0.03f,
                        i * 0.349f * 0.5f, 1f - i * 0.2f + i * 0.686f * 0.5f, i * 0.168f * 0.5f, i * 0.01f,
                        i * 0.272f * 0.5f, i * 0.534f * 0.5f, 1f - i * 0.4f + i * 0.131f * 0.5f, 0f,
                        0f, 0f, 0f, 1f
                    )
                }
            }
            EffectType.MIRROR -> {
                ScaleAndRotateTransformation.Builder()
                    .setScale(-1f, 1f)
                    .build()
            }
            EffectType.VIGNETTE -> {
                val intensity = (effect.params["intensity"] ?: 0.5f).coerceIn(0f, 1f)
                val radius = (effect.params["radius"] ?: 0.7f).coerceIn(0f, 1f)
                EffectShaders.vignette(intensity, radius)
            }
            EffectType.SHARPEN -> {
                val strength = (effect.params["strength"] ?: 0.5f).coerceIn(0f, 3f)
                EffectShaders.sharpen(strength)
            }
            EffectType.FILM_GRAIN -> {
                val intensity = (effect.params["intensity"] ?: 0.1f).coerceIn(0f, 1f)
                EffectShaders.filmGrain(intensity)
            }
            EffectType.GAUSSIAN_BLUR -> {
                val radius = (effect.params["radius"] ?: 5f).coerceIn(1f, 25f)
                EffectShaders.gaussianBlur(radius)
            }
            EffectType.RADIAL_BLUR -> {
                val intensity = (effect.params["intensity"] ?: 0.5f).coerceIn(0f, 1f)
                EffectShaders.radialBlur(intensity)
            }
            EffectType.MOTION_BLUR -> {
                val intensity = (effect.params["intensity"] ?: 0.5f).coerceIn(0f, 1f)
                val angle = (effect.params["angle"] ?: 0f).coerceIn(0f, 360f)
                EffectShaders.motionBlur(intensity, angle)
            }
            EffectType.TILT_SHIFT -> {
                val focusY = (effect.params["focusY"] ?: 0.5f).coerceIn(0f, 1f)
                val width = (effect.params["width"] ?: 0.1f).coerceIn(0.01f, 0.5f)
                val blur = (effect.params["blur"] ?: 0.01f).coerceIn(0f, 1f)
                EffectShaders.tiltShift(focusY, width, blur)
            }
            EffectType.MOSAIC -> {
                val size = (effect.params["size"] ?: 15f).coerceIn(2f, 50f)
                EffectShaders.mosaic(size)
            }
            EffectType.FISHEYE -> {
                val intensity = (effect.params["intensity"] ?: 0.5f).coerceIn(0f, 1f)
                EffectShaders.fisheye(intensity)
            }
            EffectType.GLITCH -> {
                val intensity = (effect.params["intensity"] ?: 0.5f).coerceIn(0f, 1f)
                EffectShaders.glitch(intensity)
            }
            EffectType.PIXELATE -> {
                val size = (effect.params["size"] ?: 10f).coerceIn(2f, 50f)
                EffectShaders.pixelate(size)
            }
            EffectType.WAVE -> {
                val amplitude = (effect.params["amplitude"] ?: 0.02f).coerceIn(0f, 0.1f)
                val frequency = (effect.params["frequency"] ?: 10f).coerceIn(1f, 50f)
                EffectShaders.wave(amplitude, frequency)
            }
            EffectType.CHROMATIC_ABERRATION -> {
                val intensity = (effect.params["intensity"] ?: 0.5f).coerceIn(0f, 2f)
                EffectShaders.chromaticAberration(intensity)
            }
            EffectType.CHROMA_KEY -> {
                val similarity = (effect.params["similarity"] ?: 0.4f).coerceIn(0f, 1f)
                val smoothness = (effect.params["smoothness"] ?: 0.1f).coerceIn(0f, 0.5f)
                val keyR = (effect.params["keyR"] ?: 0f).coerceIn(0f, 1f)
                val keyG = (effect.params["keyG"] ?: 1f).coerceIn(0f, 1f)
                val keyB = (effect.params["keyB"] ?: 0f).coerceIn(0f, 1f)
                EffectShaders.chromaKey(keyR, keyG, keyB, similarity, smoothness)
            }
            EffectType.BG_REMOVAL -> {
                val threshold = (effect.params["threshold"] ?: 0.5f).coerceIn(0.1f, 0.9f)
                if (segmentationEngine.isReady()) {
                    segmentationEngine.createExportEffect(threshold)
                } else null
            }
            EffectType.VHS_RETRO -> {
                val intensity = (effect.params["intensity"] ?: 0.5f).coerceIn(0f, 1f)
                EffectShaders.vhsRetro(intensity)
            }
            EffectType.LIGHT_LEAK -> {
                val intensity = (effect.params["intensity"] ?: 0.5f).coerceIn(0f, 1f)
                EffectShaders.lightLeak(intensity)
            }
            EffectType.SPEED, EffectType.REVERSE -> null
        }
    }

    /**
     * Add color grading effects (lift/gamma/gain, HSL qualification, LUT) for a clip.
     */
    fun MutableList<androidx.media3.common.Effect>.addColorGradingEffects(clip: Clip) {
        clip.colorGrade?.let { grade ->
            if (!grade.enabled) return@let
            val hasLGG = grade.liftR != 0f || grade.liftG != 0f || grade.liftB != 0f ||
                grade.gammaR != 1f || grade.gammaG != 1f || grade.gammaB != 1f ||
                grade.gainR != 1f || grade.gainG != 1f || grade.gainB != 1f ||
                grade.offsetR != 0f || grade.offsetG != 0f || grade.offsetB != 0f
            if (hasLGG) {
                add(EffectShaders.colorGrade(
                    grade.liftR, grade.liftG, grade.liftB,
                    grade.gammaR, grade.gammaG, grade.gammaB,
                    grade.gainR, grade.gainG, grade.gainB,
                    grade.offsetR, grade.offsetG, grade.offsetB
                ))
            }
            grade.hslQualifier?.let { hsl ->
                add(EffectShaders.hslQualify(
                    hsl.hueCenter, hsl.hueWidth,
                    hsl.satMin, hsl.satMax,
                    hsl.lumMin, hsl.lumMax,
                    hsl.softness,
                    hsl.adjustHue, hsl.adjustSat, hsl.adjustLum
                ))
            }
            grade.lutPath?.let { path ->
                val lutFile = java.io.File(path)
                if (lutFile.exists()) {
                    val lut = when {
                        path.endsWith(".cube", true) -> LutEngine.parseCube(lutFile)
                        path.endsWith(".3dl", true) -> LutEngine.parse3dl(lutFile)
                        else -> null
                    }
                    lut?.let { add(LutEngine.createLutEffect(it, grade.lutIntensity)) }
                }
            }
        }
    }

    /**
     * Build transition-in effect for a clip.
     */
    fun buildTransitionEffect(transition: Transition): androidx.media3.common.Effect {
        // Clamp durationMs before multiplying to avoid Float overflow at
        // pathological values (anything above ~25 days multiplied by 1000f
        // lands on Float.POSITIVE_INFINITY, which poisons every division
        // in the transition shader). 2_147_000 ms = ~24.8 days — well past
        // any plausible transition length while staying comfortably inside
        // Float's representable range after * 1000.
        val durationUs = transition.durationMs.coerceIn(1L, 2_147_000L) * 1000f
        return when (transition.type) {
            TransitionType.DISSOLVE, TransitionType.FADE_BLACK ->
                EffectShaders.transitionFadeIn(durationUs)
            TransitionType.FADE_WHITE ->
                EffectShaders.transitionFadeIn(durationUs, fadeToWhite = true)
            TransitionType.WIPE_LEFT -> EffectShaders.transitionWipe(durationUs, -1f, 0f)
            TransitionType.WIPE_RIGHT -> EffectShaders.transitionWipe(durationUs, 1f, 0f)
            TransitionType.WIPE_UP -> EffectShaders.transitionWipe(durationUs, 0f, 1f)
            TransitionType.WIPE_DOWN -> EffectShaders.transitionWipe(durationUs, 0f, -1f)
            TransitionType.SLIDE_LEFT -> EffectShaders.transitionSlideIn(durationUs, 1f, 0f)
            TransitionType.SLIDE_RIGHT -> EffectShaders.transitionSlideIn(durationUs, -1f, 0f)
            TransitionType.ZOOM_IN -> EffectShaders.transitionZoomIn(durationUs)
            TransitionType.ZOOM_OUT -> EffectShaders.transitionZoomOut(durationUs)
            TransitionType.SPIN -> EffectShaders.transitionSpin(durationUs)
            TransitionType.FLIP -> EffectShaders.transitionFlip(durationUs)
            TransitionType.CUBE -> EffectShaders.transitionCube(durationUs)
            TransitionType.RIPPLE -> EffectShaders.transitionRipple(durationUs)
            TransitionType.PIXELATE -> EffectShaders.transitionPixelate(durationUs)
            TransitionType.DIRECTIONAL_WARP -> EffectShaders.transitionDirectionalWarp(durationUs)
            TransitionType.WIND -> EffectShaders.transitionWind(durationUs)
            TransitionType.MORPH -> EffectShaders.transitionMorph(durationUs)
            TransitionType.GLITCH -> EffectShaders.transitionGlitch(durationUs)
            TransitionType.CIRCLE_OPEN -> EffectShaders.transitionCircleOpen(durationUs)
            TransitionType.CROSS_ZOOM -> EffectShaders.transitionCrossZoom(durationUs)
            TransitionType.DREAMY -> EffectShaders.transitionDreamy(durationUs)
            TransitionType.HEART -> EffectShaders.transitionHeart(durationUs)
            TransitionType.SWIRL -> EffectShaders.transitionSwirl(durationUs)
            TransitionType.DOOR_OPEN -> EffectShaders.transitionDoorOpen(durationUs)
            TransitionType.BURN -> EffectShaders.transitionBurn(durationUs)
            TransitionType.RADIAL_WIPE -> EffectShaders.transitionRadialWipe(durationUs)
            TransitionType.MOSAIC_REVEAL -> EffectShaders.transitionMosaicReveal(durationUs)
            TransitionType.BOUNCE -> EffectShaders.transitionBounce(durationUs)
            TransitionType.LENS_FLARE -> EffectShaders.transitionLensFlare(durationUs)
            TransitionType.PAGE_CURL -> EffectShaders.transitionPageCurl(durationUs)
            TransitionType.CROSS_WARP -> EffectShaders.transitionCrossWarp(durationUs)
            TransitionType.ANGULAR -> EffectShaders.transitionAngular(durationUs)
            TransitionType.KALEIDOSCOPE -> EffectShaders.transitionKaleidoscope(durationUs)
            TransitionType.SQUARES_WIRE -> EffectShaders.transitionSquaresWire(durationUs)
            TransitionType.COLOR_PHASE -> EffectShaders.transitionColorPhase(durationUs)
        }
    }

    /**
     * Build transition-out effect for the outgoing clip.
     * Activates near the end of the clip to create a matching exit animation
     * for the next clip's incoming transition.
     */
    fun buildTransitionOutEffect(transition: Transition, clipDurationMs: Long): androidx.media3.common.Effect {
        // Clamp durationMs before multiplying to avoid Float overflow at
        // pathological values (anything above ~25 days multiplied by 1000f
        // lands on Float.POSITIVE_INFINITY, which poisons every division
        // in the transition shader). 2_147_000 ms = ~24.8 days — well past
        // any plausible transition length while staying comfortably inside
        // Float's representable range after * 1000.
        val durationUs = transition.durationMs.coerceIn(1L, 2_147_000L) * 1000f
        val clipDurationUs = clipDurationMs * 1000f
        return when (transition.type) {
            TransitionType.DISSOLVE, TransitionType.FADE_BLACK ->
                EffectShaders.transitionFadeOut(durationUs, clipDurationUs)
            TransitionType.FADE_WHITE ->
                EffectShaders.transitionFadeOut(durationUs, clipDurationUs, fadeToWhite = true)
            TransitionType.WIPE_LEFT -> EffectShaders.transitionWipeOut(durationUs, clipDurationUs, -1f, 0f)
            TransitionType.WIPE_RIGHT -> EffectShaders.transitionWipeOut(durationUs, clipDurationUs, 1f, 0f)
            TransitionType.WIPE_UP -> EffectShaders.transitionWipeOut(durationUs, clipDurationUs, 0f, 1f)
            TransitionType.WIPE_DOWN -> EffectShaders.transitionWipeOut(durationUs, clipDurationUs, 0f, -1f)
            TransitionType.SLIDE_LEFT -> EffectShaders.transitionSlideOut(durationUs, clipDurationUs, -1f, 0f)
            TransitionType.SLIDE_RIGHT -> EffectShaders.transitionSlideOut(durationUs, clipDurationUs, 1f, 0f)
            TransitionType.ZOOM_IN, TransitionType.ZOOM_OUT, TransitionType.CROSS_ZOOM ->
                EffectShaders.transitionZoomOutExit(durationUs, clipDurationUs)
            TransitionType.SPIN, TransitionType.FLIP ->
                EffectShaders.transitionSpinOut(durationUs, clipDurationUs)
            TransitionType.CIRCLE_OPEN, TransitionType.RADIAL_WIPE ->
                EffectShaders.transitionCircleClose(durationUs, clipDurationUs)
            // All other exotic transitions: generic fade to black
            else -> EffectShaders.transitionFadeOut(durationUs, clipDurationUs)
        }
    }

    /**
     * Add opacity and transform effects (static or keyframe-animated) for a clip.
     */
    fun MutableList<androidx.media3.common.Effect>.addOpacityAndTransformEffects(clip: Clip) {
        val hasKeyframeOpacity = clip.keyframes.any { it.property == KeyframeProperty.OPACITY }
        if (hasKeyframeOpacity) {
            add(RgbMatrix { presentationTimeUs, _ ->
                val timeMs = presentationTimeUs / 1000L
                val opacity = KeyframeEngine.getValueAt(
                    clip.keyframes, KeyframeProperty.OPACITY, timeMs
                ) ?: 1f
                floatArrayOf(
                    opacity, 0f, 0f, 0f,
                    0f, opacity, 0f, 0f,
                    0f, 0f, opacity, 0f,
                    0f, 0f, 0f, 1f
                )
            })
        } else if (clip.opacity != 1f) {
            val o = clip.opacity.coerceIn(0f, 1f)
            add(RgbMatrix { _, _ ->
                floatArrayOf(
                    o, 0f, 0f, 0f,
                    0f, o, 0f, 0f,
                    0f, 0f, o, 0f,
                    0f, 0f, 0f, 1f
                )
            })
        }
        val hasKfScale = clip.keyframes.any {
            it.property == KeyframeProperty.SCALE_X || it.property == KeyframeProperty.SCALE_Y
        }
        val hasKfRotation = clip.keyframes.any { it.property == KeyframeProperty.ROTATION }
        val hasKfPosition = clip.keyframes.any {
            it.property == KeyframeProperty.POSITION_X || it.property == KeyframeProperty.POSITION_Y
        }
        val hasAnchor = clip.anchorX != 0f || clip.anchorY != 0f
        val needsStaticTransform = clip.rotation != 0f || clip.scaleX != 1f || clip.scaleY != 1f ||
            clip.positionX != 0f || clip.positionY != 0f || hasAnchor
        if (hasKfScale || hasKfRotation || hasKfPosition) {
            val kfs = clip.keyframes
            val staticSx = clip.scaleX; val staticSy = clip.scaleY
            val staticRot = clip.rotation
            val staticPx = clip.positionX; val staticPy = clip.positionY
            val ax = clip.anchorX; val ay = clip.anchorY
            add(MatrixTransformation { presentationTimeUs ->
                val timeMs = presentationTimeUs / 1000L
                val sx = KeyframeEngine.getValueAt(kfs, KeyframeProperty.SCALE_X, timeMs) ?: staticSx
                val sy = KeyframeEngine.getValueAt(kfs, KeyframeProperty.SCALE_Y, timeMs) ?: staticSy
                val rot = KeyframeEngine.getValueAt(kfs, KeyframeProperty.ROTATION, timeMs) ?: staticRot
                val px = KeyframeEngine.getValueAt(kfs, KeyframeProperty.POSITION_X, timeMs) ?: staticPx
                val py = KeyframeEngine.getValueAt(kfs, KeyframeProperty.POSITION_Y, timeMs) ?: staticPy
                android.graphics.Matrix().apply {
                    if (ax != 0f || ay != 0f) postTranslate(-ax, ay)
                    postScale(sx, sy)
                    postRotate(rot)
                    if (ax != 0f || ay != 0f) postTranslate(ax, -ay)
                    postTranslate(px, -py)
                }
            })
        } else if (needsStaticTransform) {
            val m = android.graphics.Matrix().apply {
                if (hasAnchor) postTranslate(-clip.anchorX, clip.anchorY)
                postScale(clip.scaleX, clip.scaleY)
                postRotate(clip.rotation)
                if (hasAnchor) postTranslate(clip.anchorX, -clip.anchorY)
                postTranslate(clip.positionX, -clip.positionY)
            }
            add(MatrixTransformation { m })
        }
    }
}

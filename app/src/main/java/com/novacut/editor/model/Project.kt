package com.novacut.editor.model

import android.net.Uri
import androidx.room.*
import java.util.UUID

@Entity(tableName = "projects")
data class Project(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String = "Untitled",
    val aspectRatio: AspectRatio = AspectRatio.RATIO_16_9,
    val frameRate: Int = 30,
    val resolution: Resolution = Resolution.FHD_1080P,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val durationMs: Long = 0L,
    val thumbnailUri: String? = null,
    val templateId: String? = null,
    val proxyEnabled: Boolean = false,
    val version: Int = 1
)

enum class AspectRatio(val widthRatio: Int, val heightRatio: Int, val label: String) {
    RATIO_16_9(16, 9, "16:9"),
    RATIO_9_16(9, 16, "9:16"),
    RATIO_1_1(1, 1, "1:1"),
    RATIO_4_3(4, 3, "4:3"),
    RATIO_3_4(3, 4, "3:4"),
    RATIO_4_5(4, 5, "4:5"),
    RATIO_21_9(21, 9, "21:9");

    fun toFloat(): Float = widthRatio.toFloat() / heightRatio.toFloat()
}

enum class Resolution(val width: Int, val height: Int, val label: String) {
    SD_480P(854, 480, "480p"),
    HD_720P(1280, 720, "720p"),
    FHD_1080P(1920, 1080, "1080p"),
    QHD_1440P(2560, 1440, "1440p"),
    UHD_4K(3840, 2160, "4K");

    fun forAspect(aspect: AspectRatio): Pair<Int, Int> {
        val ratio = aspect.toFloat()
        return if (ratio >= 1f) {
            val h = height
            val w = (h * ratio).toInt().let { it - (it % 2) }
            w to h
        } else {
            val w = height
            val h = (w / ratio).toInt().let { it - (it % 2) }
            w to h
        }
    }
}

enum class TrackType { VIDEO, AUDIO, OVERLAY, TEXT, ADJUSTMENT }

data class Track(
    val id: String = UUID.randomUUID().toString(),
    val type: TrackType,
    val index: Int,
    val clips: List<Clip> = emptyList(),
    val isLocked: Boolean = false,
    val isVisible: Boolean = true,
    val isMuted: Boolean = false,
    val isSolo: Boolean = false,
    val volume: Float = 1.0f,
    val pan: Float = 0f,
    val opacity: Float = 1.0f,
    val blendMode: BlendMode = BlendMode.NORMAL,
    val audioEffects: List<AudioEffect> = emptyList(),
    val isLinkedAV: Boolean = true
)

data class Clip(
    val id: String = UUID.randomUUID().toString(),
    val sourceUri: Uri,
    val sourceDurationMs: Long,
    val timelineStartMs: Long,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = sourceDurationMs,
    val effects: List<Effect> = emptyList(),
    val transition: Transition? = null,
    val volume: Float = 1.0f,
    val speed: Float = 1.0f,
    val isReversed: Boolean = false,
    val opacity: Float = 1.0f,
    val rotation: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val positionX: Float = 0f,
    val positionY: Float = 0f,
    val anchorX: Float = 0.5f,
    val anchorY: Float = 0.5f,
    val fadeInMs: Long = 0L,
    val fadeOutMs: Long = 0L,
    val keyframes: List<Keyframe> = emptyList(),
    val blendMode: BlendMode = BlendMode.NORMAL,
    val speedCurve: SpeedCurve? = null,
    val colorGrade: ColorGrade? = null,
    val masks: List<Mask> = emptyList(),
    val linkedClipId: String? = null,
    val isCompound: Boolean = false,
    val compoundClips: List<Clip> = emptyList(),
    val audioEffects: List<AudioEffect> = emptyList(),
    val proxyUri: Uri? = null,
    val motionTrackingData: MotionTrackingData? = null,
    val captions: List<Caption> = emptyList(),
    val groupId: String? = null
) {
    val durationMs: Long get() {
        val trimRange = trimEndMs - trimStartMs
        if (trimRange <= 0) return 0L
        val effectiveSpeed = if (speedCurve != null && speedCurve.points.size >= 2) {
            // Average speed across curve sample points
            val samples = 20
            var sumSpeed = 0f
            for (i in 0 until samples) {
                val t = i.toLong() * trimRange / samples
                sumSpeed += speedCurve.getSpeedAt(t, trimRange).coerceAtLeast(0.01f)
            }
            (sumSpeed / samples).coerceAtLeast(0.01f)
        } else {
            speed.coerceAtLeast(0.01f)
        }
        return (trimRange / effectiveSpeed).toLong()
    }
    val timelineEndMs: Long get() = timelineStartMs + durationMs

    fun getEffectiveSpeed(timeOffsetMs: Long): Float {
        return speedCurve?.getSpeedAt(timeOffsetMs, durationMs) ?: speed
    }
}

// --- Blend Modes ---

enum class BlendMode(val displayName: String) {
    NORMAL("Normal"),
    MULTIPLY("Multiply"),
    SCREEN("Screen"),
    OVERLAY("Overlay"),
    DARKEN("Darken"),
    LIGHTEN("Lighten"),
    COLOR_DODGE("Color Dodge"),
    COLOR_BURN("Color Burn"),
    HARD_LIGHT("Hard Light"),
    SOFT_LIGHT("Soft Light"),
    DIFFERENCE("Difference"),
    EXCLUSION("Exclusion"),
    HUE("Hue"),
    SATURATION_BLEND("Saturation"),
    COLOR("Color"),
    LUMINOSITY("Luminosity"),
    ADD("Add"),
    SUBTRACT("Subtract")
}

// --- Speed Curve (Bezier speed ramping) ---

data class SpeedCurve(
    val points: List<SpeedPoint> = listOf(
        SpeedPoint(0f, 1f),
        SpeedPoint(1f, 1f)
    )
) {
    fun getSpeedAt(timeOffsetMs: Long, clipDurationMs: Long): Float {
        if (points.size < 2) return points.firstOrNull()?.speed ?: 1f
        val t = (timeOffsetMs.toFloat() / clipDurationMs.toFloat()).coerceIn(0f, 1f)
        val sorted = points.sortedBy { it.position }

        if (t <= sorted.first().position) return sorted.first().speed
        if (t >= sorted.last().position) return sorted.last().speed

        for (i in 0 until sorted.size - 1) {
            if (t >= sorted[i].position && t <= sorted[i + 1].position) {
                val p0 = sorted[i]
                val p1 = sorted[i + 1]
                val denom = p1.position - p0.position
                if (denom <= 0f) return p0.speed
                val localT = (t - p0.position) / denom
                return cubicBezierInterpolate(
                    p0.speed, p0.handleOutY, p1.handleInY, p1.speed, localT
                )
            }
        }
        return 1f
    }

    companion object {
        fun cubicBezierInterpolate(
            p0: Float, c0: Float, c1: Float, p1: Float, t: Float
        ): Float {
            val mt = 1f - t
            return mt * mt * mt * p0 +
                    3f * mt * mt * t * c0 +
                    3f * mt * t * t * c1 +
                    t * t * t * p1
        }

        fun constant(speed: Float) = SpeedCurve(
            listOf(SpeedPoint(0f, speed), SpeedPoint(1f, speed))
        )

        fun rampUp(from: Float = 0.5f, to: Float = 2f) = SpeedCurve(
            listOf(
                SpeedPoint(0f, from, handleOutY = from + (to - from) * 0.3f),
                SpeedPoint(1f, to, handleInY = to - (to - from) * 0.3f)
            )
        )

        fun rampDown(from: Float = 2f, to: Float = 0.5f) = rampUp(from, to)

        fun pulse(normalSpeed: Float = 1f, peakSpeed: Float = 4f) = SpeedCurve(
            listOf(
                SpeedPoint(0f, normalSpeed),
                SpeedPoint(0.3f, normalSpeed, handleOutY = peakSpeed * 0.5f),
                SpeedPoint(0.5f, peakSpeed, handleInY = peakSpeed * 0.7f, handleOutY = peakSpeed * 0.7f),
                SpeedPoint(0.7f, normalSpeed, handleInY = peakSpeed * 0.5f),
                SpeedPoint(1f, normalSpeed)
            )
        )
    }
}

data class SpeedPoint(
    val position: Float,
    val speed: Float,
    val handleInY: Float = speed,
    val handleOutY: Float = speed
)

// --- Color Grading ---

data class ColorGrade(
    val enabled: Boolean = true,
    val liftR: Float = 0f, val liftG: Float = 0f, val liftB: Float = 0f,
    val gammaR: Float = 1f, val gammaG: Float = 1f, val gammaB: Float = 1f,
    val gainR: Float = 1f, val gainG: Float = 1f, val gainB: Float = 1f,
    val offsetR: Float = 0f, val offsetG: Float = 0f, val offsetB: Float = 0f,
    val curves: ColorCurves = ColorCurves(),
    val hslQualifier: HslQualifier? = null,
    val lutPath: String? = null,
    val lutIntensity: Float = 1f,
    val colorMatchRef: String? = null
)

data class ColorCurves(
    val master: List<CurvePoint> = listOf(CurvePoint(0f, 0f), CurvePoint(1f, 1f)),
    val red: List<CurvePoint> = listOf(CurvePoint(0f, 0f), CurvePoint(1f, 1f)),
    val green: List<CurvePoint> = listOf(CurvePoint(0f, 0f), CurvePoint(1f, 1f)),
    val blue: List<CurvePoint> = listOf(CurvePoint(0f, 0f), CurvePoint(1f, 1f))
) {
    fun evaluateCurve(points: List<CurvePoint>, input: Float): Float {
        if (points.size < 2) return input
        val sorted = points.sortedBy { it.x }
        if (input <= sorted.first().x) return sorted.first().y
        if (input >= sorted.last().x) return sorted.last().y

        for (i in 0 until sorted.size - 1) {
            if (input >= sorted[i].x && input <= sorted[i + 1].x) {
                val p0 = sorted[i]
                val p1 = sorted[i + 1]
                val t = (input - p0.x) / (p1.x - p0.x)
                return SpeedCurve.cubicBezierInterpolate(
                    p0.y, p0.handleOutY, p1.handleInY, p1.y, t
                )
            }
        }
        return input
    }
}

data class CurvePoint(
    val x: Float,
    val y: Float,
    val handleInX: Float = x,
    val handleInY: Float = y,
    val handleOutX: Float = x,
    val handleOutY: Float = y
)

data class HslQualifier(
    val hueCenter: Float = 0f,
    val hueWidth: Float = 30f,
    val satMin: Float = 0f,
    val satMax: Float = 1f,
    val lumMin: Float = 0f,
    val lumMax: Float = 1f,
    val softness: Float = 0.1f,
    val adjustHue: Float = 0f,
    val adjustSat: Float = 0f,
    val adjustLum: Float = 0f
)

// --- Masks ---

data class Mask(
    val id: String = UUID.randomUUID().toString(),
    val type: MaskType,
    val points: List<MaskPoint> = emptyList(),
    val feather: Float = 0f,
    val opacity: Float = 1f,
    val inverted: Boolean = false,
    val expansion: Float = 0f,
    val keyframes: List<MaskKeyframe> = emptyList(),
    val trackToMotion: Boolean = false
)

enum class MaskType(val displayName: String) {
    RECTANGLE("Rectangle"),
    ELLIPSE("Ellipse"),
    FREEHAND("Freehand"),
    LINEAR_GRADIENT("Linear Gradient"),
    RADIAL_GRADIENT("Radial Gradient")
}

data class MaskPoint(
    val x: Float,
    val y: Float,
    val handleInX: Float = x,
    val handleInY: Float = y,
    val handleOutX: Float = x,
    val handleOutY: Float = y
)

data class MaskKeyframe(
    val timeOffsetMs: Long,
    val points: List<MaskPoint>,
    val easing: Easing = Easing.LINEAR
)

// --- Audio Effects ---

data class AudioEffect(
    val id: String = UUID.randomUUID().toString(),
    val type: AudioEffectType,
    val params: Map<String, Float> = emptyMap(),
    val enabled: Boolean = true
)

enum class AudioEffectType(val displayName: String) {
    PARAMETRIC_EQ("Parametric EQ"),
    COMPRESSOR("Compressor"),
    LIMITER("Limiter"),
    NOISE_GATE("Noise Gate"),
    REVERB("Reverb"),
    DELAY("Delay"),
    DE_ESSER("De-esser"),
    CHORUS("Chorus"),
    FLANGER("Flanger"),
    PITCH_SHIFT("Pitch Shift"),
    NORMALIZER("Normalizer"),
    HIGH_PASS("High Pass"),
    LOW_PASS("Low Pass"),
    BAND_PASS("Band Pass"),
    NOTCH("Notch");

    companion object {
        fun defaultParams(type: AudioEffectType): Map<String, Float> = when (type) {
            PARAMETRIC_EQ -> mapOf(
                "band1_freq" to 80f, "band1_gain" to 0f, "band1_q" to 1f,
                "band2_freq" to 250f, "band2_gain" to 0f, "band2_q" to 1f,
                "band3_freq" to 1000f, "band3_gain" to 0f, "band3_q" to 1f,
                "band4_freq" to 4000f, "band4_gain" to 0f, "band4_q" to 1f,
                "band5_freq" to 12000f, "band5_gain" to 0f, "band5_q" to 1f
            )
            COMPRESSOR -> mapOf(
                "threshold" to -20f, "ratio" to 4f, "attack" to 10f,
                "release" to 100f, "knee" to 6f, "makeupGain" to 0f
            )
            LIMITER -> mapOf("ceiling" to -1f, "release" to 50f)
            NOISE_GATE -> mapOf(
                "threshold" to -40f, "attack" to 1f, "hold" to 50f, "release" to 100f
            )
            REVERB -> mapOf(
                "roomSize" to 0.5f, "damping" to 0.5f, "wetDry" to 0.3f,
                "preDelay" to 20f, "decay" to 2f
            )
            DELAY -> mapOf(
                "delayMs" to 250f, "feedback" to 0.3f, "wetDry" to 0.3f, "pingPong" to 0f
            )
            DE_ESSER -> mapOf("frequency" to 6000f, "threshold" to -20f, "ratio" to 3f)
            CHORUS -> mapOf("rate" to 1.5f, "depth" to 0.5f, "wetDry" to 0.3f)
            FLANGER -> mapOf("rate" to 0.5f, "depth" to 0.5f, "feedback" to 0.3f, "wetDry" to 0.3f)
            PITCH_SHIFT -> mapOf("semitones" to 0f, "cents" to 0f)
            NORMALIZER -> mapOf("targetLufs" to -14f, "mode" to 0f)
            HIGH_PASS -> mapOf("frequency" to 80f, "resonance" to 0.7f)
            LOW_PASS -> mapOf("frequency" to 12000f, "resonance" to 0.7f)
            BAND_PASS -> mapOf("frequency" to 1000f, "bandwidth" to 1f)
            NOTCH -> mapOf("frequency" to 1000f, "bandwidth" to 0.5f)
        }
    }
}

// --- Motion Tracking ---

data class MotionTrackingData(
    val id: String = UUID.randomUUID().toString(),
    val trackPoints: List<MotionTrackPoint> = emptyList(),
    val targetType: TrackTargetType = TrackTargetType.POINT,
    val isActive: Boolean = false
)

data class MotionTrackPoint(
    val timeOffsetMs: Long,
    val x: Float,
    val y: Float,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val rotation: Float = 0f,
    val confidence: Float = 1f
)

enum class TrackTargetType { POINT, SURFACE, FACE }

// --- Captions ---

data class Caption(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val words: List<CaptionWord> = emptyList(),
    val style: CaptionStyle = CaptionStyle()
)

data class CaptionWord(
    val text: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val confidence: Float = 1f
)

data class CaptionStyle(
    val type: CaptionStyleType = CaptionStyleType.SUBTITLE_BAR,
    val fontFamily: String = "sans-serif-medium",
    val fontSize: Float = 36f,
    val color: Long = 0xFFFFFFFF,
    val backgroundColor: Long = 0xCC000000,
    val highlightColor: Long = 0xFFFFD700,
    val positionY: Float = 0.85f,
    val outline: Boolean = true,
    val shadow: Boolean = true
)

enum class CaptionStyleType(val displayName: String) {
    SUBTITLE_BAR("Subtitle Bar"),
    WORD_BY_WORD("Word Pop"),
    KARAOKE("Karaoke Highlight"),
    BOUNCE("Bounce"),
    TYPEWRITER("Typewriter"),
    MINIMAL("Minimal")
}

// --- Effects ---

data class Effect(
    val id: String = UUID.randomUUID().toString(),
    val type: EffectType,
    val params: Map<String, Float> = emptyMap(),
    val enabled: Boolean = true,
    val keyframes: List<EffectKeyframe> = emptyList()
)

data class EffectKeyframe(
    val timeOffsetMs: Long,
    val paramName: String,
    val value: Float,
    val easing: Easing = Easing.LINEAR,
    val handleInX: Float = 0f,
    val handleInY: Float = 0f,
    val handleOutX: Float = 0f,
    val handleOutY: Float = 0f
)

enum class EffectType(val displayName: String, val category: EffectCategory) {
    // Color
    BRIGHTNESS("Brightness", EffectCategory.COLOR),
    CONTRAST("Contrast", EffectCategory.COLOR),
    SATURATION("Saturation", EffectCategory.COLOR),
    TEMPERATURE("Temperature", EffectCategory.COLOR),
    TINT("Tint", EffectCategory.COLOR),
    EXPOSURE("Exposure", EffectCategory.COLOR),
    GAMMA("Gamma", EffectCategory.COLOR),
    HIGHLIGHTS("Highlights", EffectCategory.COLOR),
    SHADOWS("Shadows", EffectCategory.COLOR),
    VIBRANCE("Vibrance", EffectCategory.COLOR),

    // Filters
    GRAYSCALE("Grayscale", EffectCategory.FILTER),
    SEPIA("Sepia", EffectCategory.FILTER),
    INVERT("Invert", EffectCategory.FILTER),
    POSTERIZE("Posterize", EffectCategory.FILTER),
    VIGNETTE("Vignette", EffectCategory.FILTER),
    SHARPEN("Sharpen", EffectCategory.FILTER),
    FILM_GRAIN("Film Grain", EffectCategory.FILTER),
    VINTAGE("Vintage", EffectCategory.FILTER),
    COOL_TONE("Cool Tone", EffectCategory.FILTER),
    WARM_TONE("Warm Tone", EffectCategory.FILTER),
    CYBERPUNK("Cyberpunk", EffectCategory.FILTER),
    NOIR("Noir", EffectCategory.FILTER),
    VHS_RETRO("VHS/Retro", EffectCategory.FILTER),
    LIGHT_LEAK("Light Leak", EffectCategory.FILTER),

    // Blur
    GAUSSIAN_BLUR("Gaussian Blur", EffectCategory.BLUR),
    RADIAL_BLUR("Radial Blur", EffectCategory.BLUR),
    MOTION_BLUR("Motion Blur", EffectCategory.BLUR),
    TILT_SHIFT("Tilt Shift", EffectCategory.BLUR),
    MOSAIC("Mosaic", EffectCategory.BLUR),

    // Distortion
    FISHEYE("Fisheye", EffectCategory.DISTORTION),
    MIRROR("Mirror", EffectCategory.DISTORTION),
    GLITCH("Glitch", EffectCategory.DISTORTION),
    PIXELATE("Pixelate", EffectCategory.DISTORTION),
    WAVE("Wave", EffectCategory.DISTORTION),
    CHROMATIC_ABERRATION("Chromatic Aberration", EffectCategory.DISTORTION),

    // Keying
    CHROMA_KEY("Chroma Key", EffectCategory.KEYING),
    BG_REMOVAL("BG Removal", EffectCategory.KEYING),

    // Speed
    SPEED("Speed", EffectCategory.SPEED),
    REVERSE("Reverse", EffectCategory.SPEED);

    companion object {
        fun defaultParams(type: EffectType): Map<String, Float> = when (type) {
            BRIGHTNESS -> mapOf("value" to 0f)
            CONTRAST -> mapOf("value" to 1f)
            SATURATION -> mapOf("value" to 1f)
            TEMPERATURE -> mapOf("value" to 0f)
            TINT -> mapOf("value" to 0f)
            EXPOSURE -> mapOf("value" to 0f)
            GAMMA -> mapOf("value" to 1f)
            HIGHLIGHTS -> mapOf("value" to 0f)
            SHADOWS -> mapOf("value" to 0f)
            VIBRANCE -> mapOf("value" to 0f)
            VIGNETTE -> mapOf("intensity" to 0.5f, "radius" to 0.7f)
            GAUSSIAN_BLUR -> mapOf("radius" to 5f)
            SHARPEN -> mapOf("strength" to 0.5f)
            FILM_GRAIN -> mapOf("intensity" to 0.1f)
            GLITCH -> mapOf("intensity" to 0.5f)
            PIXELATE -> mapOf("size" to 10f)
            CHROMATIC_ABERRATION -> mapOf("intensity" to 0.5f)
            CHROMA_KEY -> mapOf("similarity" to 0.4f, "smoothness" to 0.1f, "spill" to 0.1f)
            BG_REMOVAL -> mapOf("threshold" to 0.5f)
            TILT_SHIFT -> mapOf("blur" to 0.01f, "focusY" to 0.5f, "width" to 0.1f)
            CYBERPUNK, NOIR, VINTAGE -> mapOf("intensity" to 0.7f)
            COOL_TONE, WARM_TONE -> mapOf("intensity" to 0.5f)
            SPEED -> mapOf("value" to 1f)
            MOSAIC -> mapOf("size" to 15f)
            RADIAL_BLUR, MOTION_BLUR, FISHEYE -> mapOf("intensity" to 0.5f)
            WAVE -> mapOf("amplitude" to 0.02f, "frequency" to 10f)
            POSTERIZE -> mapOf("levels" to 6f)
            VHS_RETRO -> mapOf("intensity" to 0.5f)
            LIGHT_LEAK -> mapOf("intensity" to 0.5f)
            GRAYSCALE, SEPIA, INVERT, MIRROR, REVERSE -> emptyMap()
        }
    }
}

enum class EffectCategory(val displayName: String) {
    COLOR("Color"),
    FILTER("Filters"),
    BLUR("Blur"),
    DISTORTION("Distortion"),
    KEYING("Keying"),
    SPEED("Speed")
}

data class Transition(
    val type: TransitionType,
    val durationMs: Long = 500L
)

enum class TransitionType(val displayName: String) {
    DISSOLVE("Dissolve"),
    FADE_BLACK("Fade to Black"),
    FADE_WHITE("Fade to White"),
    WIPE_LEFT("Wipe Left"),
    WIPE_RIGHT("Wipe Right"),
    WIPE_UP("Wipe Up"),
    WIPE_DOWN("Wipe Down"),
    SLIDE_LEFT("Slide Left"),
    SLIDE_RIGHT("Slide Right"),
    ZOOM_IN("Zoom In"),
    ZOOM_OUT("Zoom Out"),
    SPIN("Spin"),
    FLIP("Flip"),
    CUBE("Cube"),
    RIPPLE("Ripple"),
    PIXELATE("Pixelate"),
    DIRECTIONAL_WARP("Directional Warp"),
    WIND("Wind"),
    MORPH("Morph"),
    GLITCH("Glitch"),
    CIRCLE_OPEN("Circle Open"),
    CROSS_ZOOM("Cross Zoom"),
    DREAMY("Dreamy"),
    HEART("Heart"),
    SWIRL("Swirl"),
    DOOR_OPEN("Door Open"),
    BURN("Burn"),
    RADIAL_WIPE("Radial Wipe"),
    MOSAIC_REVEAL("Mosaic Reveal"),
    BOUNCE("Bounce"),
    LENS_FLARE("Lens Flare"),
    PAGE_CURL("Page Curl"),
    CROSS_WARP("Cross Warp"),
    ANGULAR("Angular"),
    KALEIDOSCOPE("Kaleidoscope"),
    SQUARES_WIRE("Squares Wire"),
    COLOR_PHASE("Color Phase")
}

// --- Keyframes ---

data class Keyframe(
    val timeOffsetMs: Long,
    val property: KeyframeProperty,
    val value: Float,
    val easing: Easing = Easing.LINEAR,
    val handleInX: Float = 0f,
    val handleInY: Float = 0f,
    val handleOutX: Float = 0f,
    val handleOutY: Float = 0f,
    val interpolation: KeyframeInterpolation = KeyframeInterpolation.BEZIER
)

enum class KeyframeProperty {
    POSITION_X, POSITION_Y, SCALE_X, SCALE_Y, ROTATION, OPACITY, VOLUME,
    ANCHOR_X, ANCHOR_Y, MASK_FEATHER, MASK_EXPANSION, MASK_OPACITY
}

enum class KeyframeInterpolation { LINEAR, BEZIER, HOLD }

enum class Easing {
    LINEAR, EASE_IN, EASE_OUT, EASE_IN_OUT, SPRING,
    BOUNCE, ELASTIC, BACK, CIRCULAR, EXPO, SINE, CUBIC
}

// --- Text Overlays ---

data class TextOverlay(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val fontFamily: String = "sans-serif",
    val fontSize: Float = 48f,
    val color: Long = 0xFFFFFFFF,
    val backgroundColor: Long = 0x00000000,
    val strokeColor: Long = 0xFF000000,
    val strokeWidth: Float = 0f,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val alignment: TextAlignment = TextAlignment.CENTER,
    val positionX: Float = 0.5f,
    val positionY: Float = 0.5f,
    val startTimeMs: Long = 0L,
    val endTimeMs: Long = 3000L,
    val animationIn: TextAnimation = TextAnimation.NONE,
    val animationOut: TextAnimation = TextAnimation.NONE,
    val rotation: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val shadowColor: Long = 0x80000000,
    val shadowOffsetX: Float = 0f,
    val shadowOffsetY: Float = 0f,
    val shadowBlur: Float = 0f,
    val glowColor: Long = 0x00000000,
    val glowRadius: Float = 0f,
    val letterSpacing: Float = 0f,
    val lineHeight: Float = 1.2f,
    val textPath: TextPath? = null,
    val templateId: String? = null,
    val keyframes: List<Keyframe> = emptyList()
)

// --- Image/Sticker Overlays ---

data class ImageOverlay(
    val id: String = UUID.randomUUID().toString(),
    val sourceUri: Uri,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val positionX: Float = 0f,
    val positionY: Float = 0f,
    val scale: Float = 0.3f,
    val rotation: Float = 0f,
    val opacity: Float = 1.0f,
    val type: ImageOverlayType = ImageOverlayType.STICKER
)

enum class ImageOverlayType { STICKER, GIF, IMAGE }

data class TextPath(
    val type: TextPathType,
    val points: List<MaskPoint> = emptyList(),
    val progress: Float = 1f
)

enum class TextPathType { STRAIGHT, CURVED, CIRCULAR, WAVE }

data class TextTemplate(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val category: TextTemplateCategory,
    val layers: List<TextOverlay>,
    val durationMs: Long = 3000L,
    val thumbnailRes: Int = 0
)

enum class TextTemplateCategory(val displayName: String) {
    LOWER_THIRD("Lower Thirds"),
    TITLE_CARD("Title Cards"),
    END_SCREEN("End Screens"),
    CALL_TO_ACTION("Call to Action"),
    SOCIAL("Social Media"),
    MINIMAL("Minimal")
}

enum class TextAlignment { LEFT, CENTER, RIGHT }

enum class TextAnimation(val displayName: String) {
    NONE("None"),
    FADE("Fade"),
    SLIDE_UP("Slide Up"),
    SLIDE_DOWN("Slide Down"),
    SLIDE_LEFT("Slide Left"),
    SLIDE_RIGHT("Slide Right"),
    SCALE("Scale"),
    TYPEWRITER("Typewriter"),
    BOUNCE("Bounce"),
    SPIN("Spin"),
    BLUR_IN("Blur In"),
    GLITCH("Glitch"),
    WAVE("Wave"),
    ELASTIC("Elastic"),
    FLIP("Flip")
}

// --- Export ---

data class ExportConfig(
    val resolution: Resolution = Resolution.FHD_1080P,
    val frameRate: Int = 30,
    val codec: VideoCodec = VideoCodec.H264,
    val quality: ExportQuality = ExportQuality.HIGH,
    val audioCodec: AudioCodec = AudioCodec.AAC,
    val audioBitrate: Int = 256_000,
    val aspectRatio: AspectRatio = AspectRatio.RATIO_16_9,
    val bitrateMode: BitrateMode = BitrateMode.VBR,
    val platformPreset: PlatformPreset? = null,
    val exportAudioOnly: Boolean = false,
    val exportStemsOnly: Boolean = false,
    val includeChapterMarkers: Boolean = false,
    val chapters: List<ChapterMarker> = emptyList(),
    val subtitleFormat: SubtitleFormat? = null
) {
    companion object {
        fun youtube1080() = ExportConfig(
            resolution = Resolution.FHD_1080P, frameRate = 30, quality = ExportQuality.HIGH,
            aspectRatio = AspectRatio.RATIO_16_9, codec = VideoCodec.H264,
            platformPreset = PlatformPreset.YOUTUBE_1080
        )
        fun youtube4k() = ExportConfig(
            resolution = Resolution.UHD_4K, frameRate = 30, quality = ExportQuality.HIGH,
            aspectRatio = AspectRatio.RATIO_16_9, codec = VideoCodec.HEVC,
            platformPreset = PlatformPreset.YOUTUBE_4K
        )
        fun tiktok() = ExportConfig(
            resolution = Resolution.FHD_1080P, frameRate = 30, quality = ExportQuality.HIGH,
            aspectRatio = AspectRatio.RATIO_9_16, codec = VideoCodec.H264,
            platformPreset = PlatformPreset.TIKTOK
        )
        fun instagram() = ExportConfig(
            resolution = Resolution.FHD_1080P, frameRate = 30, quality = ExportQuality.MEDIUM,
            aspectRatio = AspectRatio.RATIO_9_16, codec = VideoCodec.H264,
            platformPreset = PlatformPreset.INSTAGRAM_REEL
        )
        fun instagramSquare() = ExportConfig(
            resolution = Resolution.FHD_1080P, frameRate = 30, quality = ExportQuality.MEDIUM,
            aspectRatio = AspectRatio.RATIO_1_1, codec = VideoCodec.H264,
            platformPreset = PlatformPreset.INSTAGRAM_FEED
        )
        fun threads() = ExportConfig(
            resolution = Resolution.FHD_1080P, frameRate = 30, quality = ExportQuality.HIGH,
            aspectRatio = AspectRatio.RATIO_9_16, codec = VideoCodec.H264,
            platformPreset = PlatformPreset.THREADS
        )

        /**
         * Query the device's hardware encoder support and return available video codecs.
         * H.264 is always included (guaranteed on all Android devices).
         * HEVC, AV1, and VP9 are included only if a hardware encoder is present.
         */
        fun getAvailableCodecs(): List<VideoCodec> {
            val list = mutableListOf(VideoCodec.H264) // Always available
            val codecList = android.media.MediaCodecList(android.media.MediaCodecList.REGULAR_CODECS)
            codecList.codecInfos.filter { it.isEncoder }.forEach { info ->
                info.supportedTypes.forEach { type ->
                    when (type.lowercase()) {
                        "video/hevc" -> if (VideoCodec.HEVC !in list) list.add(VideoCodec.HEVC)
                        "video/av01" -> if (VideoCodec.AV1 !in list) list.add(VideoCodec.AV1)
                        "video/x-vnd.on2.vp9" -> if (VideoCodec.VP9 !in list) list.add(VideoCodec.VP9)
                    }
                }
            }
            return list
        }
    }

    val videoBitrate: Int get() = when (resolution) {
        Resolution.SD_480P -> when (quality) {
            ExportQuality.LOW -> 2_000_000
            ExportQuality.MEDIUM -> 4_000_000
            ExportQuality.HIGH -> 6_000_000
        }
        Resolution.HD_720P -> when (quality) {
            ExportQuality.LOW -> 4_000_000
            ExportQuality.MEDIUM -> 6_000_000
            ExportQuality.HIGH -> 10_000_000
        }
        Resolution.FHD_1080P -> when (quality) {
            ExportQuality.LOW -> 6_000_000
            ExportQuality.MEDIUM -> 12_000_000
            ExportQuality.HIGH -> 20_000_000
        }
        Resolution.QHD_1440P -> when (quality) {
            ExportQuality.LOW -> 12_000_000
            ExportQuality.MEDIUM -> 25_000_000
            ExportQuality.HIGH -> 40_000_000
        }
        Resolution.UHD_4K -> when (quality) {
            ExportQuality.LOW -> 25_000_000
            ExportQuality.MEDIUM -> 50_000_000
            ExportQuality.HIGH -> 80_000_000
        }
    }
}

enum class VideoCodec(val mimeType: String, val label: String) {
    H264("video/avc", "H.264"),
    HEVC("video/hevc", "H.265/HEVC"),
    AV1("video/av01", "AV1"),
    VP9("video/x-vnd.on2.vp9", "VP9")
}

enum class AudioCodec(val mimeType: String, val label: String) {
    AAC("audio/mp4a-latm", "AAC"),
    OPUS("audio/opus", "Opus"),
    FLAC("audio/flac", "FLAC")
}

enum class ExportQuality(val label: String) {
    LOW("Small File"),
    MEDIUM("Balanced"),
    HIGH("Best Quality")
}

enum class BitrateMode(val label: String) {
    CBR("Constant"),
    VBR("Variable"),
    CQ("Constant Quality")
}

enum class PlatformPreset(
    val displayName: String,
    val resolution: Resolution,
    val aspectRatio: AspectRatio,
    val frameRate: Int,
    val codec: VideoCodec
) {
    YOUTUBE_1080(
        "YouTube 1080p", Resolution.FHD_1080P, AspectRatio.RATIO_16_9, 30, VideoCodec.H264
    ),
    YOUTUBE_4K(
        "YouTube 4K", Resolution.UHD_4K, AspectRatio.RATIO_16_9, 30, VideoCodec.HEVC
    ),
    TIKTOK(
        "TikTok", Resolution.FHD_1080P, AspectRatio.RATIO_9_16, 30, VideoCodec.H264
    ),
    INSTAGRAM_FEED(
        "Instagram Feed", Resolution.FHD_1080P, AspectRatio.RATIO_1_1, 30, VideoCodec.H264
    ),
    INSTAGRAM_REEL(
        "Instagram Reels", Resolution.FHD_1080P, AspectRatio.RATIO_9_16, 30, VideoCodec.H264
    ),
    INSTAGRAM_STORY(
        "Instagram Story", Resolution.FHD_1080P, AspectRatio.RATIO_9_16, 30, VideoCodec.H264
    ),
    TWITTER(
        "Twitter/X", Resolution.FHD_1080P, AspectRatio.RATIO_16_9, 30, VideoCodec.H264
    ),
    LINKEDIN(
        "LinkedIn", Resolution.FHD_1080P, AspectRatio.RATIO_16_9, 30, VideoCodec.H264
    ),
    THREADS(
        "Threads", Resolution.FHD_1080P, AspectRatio.RATIO_9_16, 30, VideoCodec.H264
    )
}

data class ChapterMarker(
    val timeMs: Long,
    val title: String
)

enum class SubtitleFormat(val extension: String, val displayName: String) {
    SRT("srt", "SubRip (.srt)"),
    VTT("vtt", "WebVTT (.vtt)"),
    ASS("ass", "Advanced SubStation (.ass)")
}

// --- Batch Export ---

data class BatchExportItem(
    val id: String = UUID.randomUUID().toString(),
    val config: ExportConfig,
    val outputName: String,
    val status: BatchExportStatus = BatchExportStatus.QUEUED,
    val progress: Float = 0f
)

enum class BatchExportStatus { QUEUED, IN_PROGRESS, COMPLETED, FAILED, CANCELLED }

// --- Project Templates ---

data class ProjectTemplate(
    val id: String,
    val name: String,
    val category: TemplateCategory,
    val description: String,
    val aspectRatio: AspectRatio,
    val tracks: List<Track>,
    val textOverlays: List<TextOverlay> = emptyList(),
    val durationMs: Long
)

enum class TemplateCategory(val displayName: String) {
    VLOG("Vlog"),
    TUTORIAL("Tutorial"),
    SHORT_FORM("Short Form"),
    CINEMATIC("Cinematic"),
    SLIDESHOW("Slideshow"),
    PROMO("Promo"),
    BLANK("Blank")
}

// --- Project Versioning ---

data class ProjectSnapshot(
    val id: String = UUID.randomUUID().toString(),
    val projectId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val label: String = "",
    val stateJson: String
)

// --- Proxy ---

data class ProxySettings(
    val enabled: Boolean = false,
    val resolution: ProxyResolution = ProxyResolution.QUARTER,
    val autoGenerate: Boolean = true
)

enum class ProxyResolution(val scale: Float, val label: String) {
    HALF(0.5f, "1/2"),
    QUARTER(0.25f, "1/4"),
    EIGHTH(0.125f, "1/8")
}

// --- Sort Mode ---

enum class SortMode(val label: String) {
    DATE_DESC("Recent"),
    DATE_ASC("Oldest"),
    NAME_ASC("A-Z"),
    NAME_DESC("Z-A"),
    DURATION_DESC("Longest")
}

// --- Caption Style Templates ---

enum class CaptionTemplateType(val displayName: String) {
    CLASSIC("Classic"),
    KARAOKE("Karaoke"),
    WORD_BY_WORD("Word by Word"),
    BOUNCE("Bounce"),
    GLOW("Glow"),
    OUTLINE("Outline"),
    SHADOW_POP("Shadow Pop"),
    GRADIENT("Gradient"),
    TYPEWRITER("Typewriter"),
    NEON("Neon"),
    COMIC("Comic"),
    MINIMAL("Minimal"),
    BOLD_CENTER("Bold Center"),
    LOWER_THIRD("Lower Third"),
    SUBTITLE("Subtitle")
}

data class CaptionStyleTemplate(
    val id: String = UUID.randomUUID().toString(),
    val type: CaptionTemplateType,
    val fontFamily: String = "sans-serif",
    val fontSize: Float = 24f,
    val textColor: Long = 0xFFFFFFFF,
    val backgroundColor: Long = 0x80000000,
    val outlineColor: Long = 0xFF000000,
    val outlineWidth: Float = 0f,
    val shadowColor: Long = 0x80000000,
    val shadowOffsetX: Float = 2f,
    val shadowOffsetY: Float = 2f,
    val positionY: Float = 0.85f,
    val animation: TextAnimation = TextAnimation.FADE,
    val highlightColor: Long = 0xFFFFD700,
    val wordByWord: Boolean = false
)

// --- Speed Curve Named Presets ---

enum class SpeedPresetType(val displayName: String, val description: String) {
    BULLET_TIME("Bullet Time", "Dramatic slow-mo with speed ramp"),
    HERO_TIME("Hero Time", "Slow entrance, normal exit"),
    MONTAGE("Montage", "Fast cuts with brief pauses"),
    JUMP_CUT("Jump Cut", "Instant speed changes"),
    SMOOTH_RAMP_UP("Smooth Ramp Up", "Gradually accelerate"),
    SMOOTH_RAMP_DOWN("Smooth Ramp Down", "Gradually decelerate"),
    PULSE("Pulse", "Rhythmic speed oscillation"),
    FLASH("Flash", "Brief fast forward"),
    DREAMY("Dreamy", "Slow with gentle waves"),
    REWIND("Rewind", "Fast reverse feel"),
    TIME_FREEZE("Time Freeze", "Freeze at midpoint then resume"),
    FILM_REEL("Film Reel", "Classic 24fps stutter effect"),
    HEARTBEAT("Heartbeat", "Repeating fast-slow-fast pattern"),
    CRESCENDO("Crescendo", "Exponential ramp from slow to fast")
}

// --- Auto-save indicator ---

enum class SaveIndicatorState {
    HIDDEN, SAVING, SAVED, ERROR
}

// --- First-run tutorial ---

data class TutorialStep(
    val id: String,
    val title: String,
    val description: String,
    val highlightArea: TutorialHighlight
)

enum class TutorialHighlight {
    TIMELINE, PREVIEW, TOOL_BAR, ADD_MEDIA, EXPORT, EFFECTS
}

// --- Undo history entry for visual display ---

data class UndoHistoryEntry(
    val index: Int,
    val description: String,
    val timestamp: Long = System.currentTimeMillis()
)

// --- Timeline Markers ---

data class TimelineMarker(
    val id: String = UUID.randomUUID().toString(),
    val timeMs: Long,
    val label: String = "",
    val color: MarkerColor = MarkerColor.BLUE,
    val notes: String = ""
)

enum class MarkerColor(val argb: Long) {
    RED(0xFFE78284), ORANGE(0xFFEF9F76), YELLOW(0xFFE5C890),
    GREEN(0xFFA6D189), BLUE(0xFF8CAAEE), PURPLE(0xFFCA9EE6)
}

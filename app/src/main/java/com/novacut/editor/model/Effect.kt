package com.novacut.editor.model

import androidx.compose.runtime.Immutable
import java.util.UUID

@Immutable
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

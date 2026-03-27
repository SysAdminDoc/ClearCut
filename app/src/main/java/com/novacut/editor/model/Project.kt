package com.novacut.editor.model

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.room.*
import java.util.UUID

@Immutable
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

@Immutable
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
) {
    init {
        require(index >= 0) { "Track index must be non-negative" }
        require(pan in -1f..1f) { "Pan must be between -1 and 1" }
    }
}

@Immutable
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
    init {
        require(speed > 0f) { "Clip speed must be positive" }
        require(trimStartMs >= 0) { "trimStartMs must be non-negative" }
        require(trimEndMs >= trimStartMs) { "trimEndMs must be >= trimStartMs" }
        require(volume in 0f..2f) { "Volume must be between 0 and 2" }
        require(opacity in 0f..1f) { "Opacity must be between 0 and 1" }
        require(trimEndMs <= sourceDurationMs) { "trimEndMs cannot exceed sourceDurationMs" }
    }

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

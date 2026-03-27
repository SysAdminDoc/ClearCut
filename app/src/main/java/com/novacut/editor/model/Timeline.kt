package com.novacut.editor.model

import androidx.compose.runtime.Immutable
import java.util.UUID

@Immutable
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

@Immutable
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

@Immutable
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

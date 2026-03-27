package com.novacut.editor.model

import java.util.UUID

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
) {
    init {
        require(feather >= 0f) { "Feather must be non-negative" }
        require(opacity in 0f..1f) { "Mask opacity must be between 0 and 1" }
    }
}

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

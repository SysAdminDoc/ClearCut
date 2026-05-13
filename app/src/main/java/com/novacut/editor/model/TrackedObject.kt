package com.novacut.editor.model

import androidx.compose.runtime.Immutable
import java.util.UUID

/**
 * A reusable, named subject that can be the target of operations like blur,
 * mosaic, sticker attach, color grade, or audio focus. See ROADMAP.md R4.3
 * (object-aware editing) and the "object-aware release" sequencing.
 *
 * The actual mask + bounding-box data stream lives in [keyframes]: each entry
 * captures the object's position and confidence at a sampled time, populated
 * by whichever engine produced the track (MediaPipe, MobileSAM, SAM 2,
 * manual). Engines surface tracking drift by dropping confidence below 1.0
 * for that keyframe so the review UI can flag low-confidence frames.
 *
 * The model is intentionally engine-agnostic — it stores only what survives
 * persistence (positions, sizes, normalised mask paths) so a project can
 * round-trip through autosave without requiring a particular tracker to be
 * installed at restore time.
 */
@Immutable
data class TrackedObject(
    val id: String = UUID.randomUUID().toString(),
    /** Human-readable label set at creation ("Person", "License plate", "Subject"). */
    val label: String,
    /** Source clip the track was generated against — keyframes are clip-relative ms. */
    val sourceClipId: String,
    val source: TrackedObjectSource = TrackedObjectSource.MANUAL,
    val keyframes: List<TrackedObjectKeyframe> = emptyList(),
    /** Optional category hint (face, person, vehicle, animal, text). */
    val category: TrackedObjectCategory = TrackedObjectCategory.UNKNOWN,
    /** When false the operation panel hides this object even if persisted. */
    val isEnabled: Boolean = true
) {
    init {
        require(label.isNotBlank()) { "TrackedObject label must not be blank" }
        require(sourceClipId.isNotBlank()) { "TrackedObject sourceClipId must not be blank" }
    }

    /**
     * Find the closest keyframe to [clipRelativeMs]. Linear interpolation is
     * the responsibility of the renderer/effect — we deliberately don't bake
     * an interpolator into the model so each consumer (blur shader, sticker
     * compositor, audio focus) can pick the right strategy.
     */
    fun keyframeAt(clipRelativeMs: Long): TrackedObjectKeyframe? {
        if (keyframes.isEmpty()) return null
        return keyframes.minByOrNull { kotlin.math.abs(it.clipTimeMs - clipRelativeMs) }
    }
}

enum class TrackedObjectSource {
    /** Hand-placed bounding box, no automated tracking. */
    MANUAL,

    /** Live MediaPipe Tasks (face/pose/object detector). */
    MEDIAPIPE,

    /** MobileSAM tap-to-segment (single frame, propagated by optical flow). */
    MOBILE_SAM,

    /** SAM 2 video tracker (full-clip propagation). */
    SAM2,

    /** YOLO + ByteTrack pipeline. */
    YOLO_TRACK
}

enum class TrackedObjectCategory(val displayName: String) {
    UNKNOWN("Unknown"),
    PERSON("Person"),
    FACE("Face"),
    VEHICLE("Vehicle"),
    LICENSE_PLATE("License plate"),
    ANIMAL("Animal"),
    TEXT("Text"),
    PRODUCT("Product")
}

/**
 * Single sample on the track. Coordinates are normalised to the source clip's
 * frame ([0, 1] for x/y/width/height) so a project survives a switch from
 * 1080p to 4K source without mask drift.
 */
@Immutable
data class TrackedObjectKeyframe(
    /** Clip-relative time in ms (matches Clip.trim* coordinate space). */
    val clipTimeMs: Long,
    /** Normalised bounding box centre X in [0, 1]. */
    val centerX: Float,
    /** Normalised bounding box centre Y in [0, 1]. */
    val centerY: Float,
    /** Normalised width in (0, 1]. */
    val width: Float,
    /** Normalised height in (0, 1]. */
    val height: Float,
    /** Tracker confidence in [0, 1]. Below ~0.4 the renderer should warn. */
    val confidence: Float = 1f,
    /**
     * Optional polygon mask in normalised coordinates. Empty = use the
     * bounding box. Populated by SAM/SAM 2 / MobileSAM paths.
     */
    val maskPolygon: List<MaskPoint> = emptyList()
) {
    init {
        // NaN bypasses ordering comparisons (NaN > 0 is false), so the (0, 1] and
        // [0, 1] requires below ALREADY reject NaN for width/height/confidence —
        // but `centerX in 0f..1f` would silently accept NaN if we relied only on
        // ranges. Reject all non-finite inputs explicitly so corrupt JSON cannot
        // sneak NaN coordinates into mosaic/blur masks where they would render
        // as gigantic off-screen rectangles. clipTimeMs guard prevents negative
        // ms (legacy saves before v3.71 used 0L for "missing", not a negative).
        require(clipTimeMs >= 0L) { "clipTimeMs must be non-negative, got $clipTimeMs" }
        require(centerX.isFinite() && centerY.isFinite()) {
            "centerX/centerY must be finite, got ($centerX, $centerY)"
        }
        require(centerX in 0f..1f) { "centerX must be in [0, 1], got $centerX" }
        require(centerY in 0f..1f) { "centerY must be in [0, 1], got $centerY" }
        require(width > 0f && width <= 1f) { "width must be in (0, 1], got $width" }
        require(height > 0f && height <= 1f) { "height must be in (0, 1], got $height" }
        require(confidence in 0f..1f) { "confidence must be in [0, 1], got $confidence" }
    }
}

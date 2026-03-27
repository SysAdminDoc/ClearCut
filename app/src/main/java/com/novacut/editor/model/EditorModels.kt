package com.novacut.editor.model

import androidx.compose.runtime.Immutable

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

@Immutable
data class ProjectSnapshot(
    val id: String = java.util.UUID.randomUUID().toString(),
    val projectId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val label: String = "",
    val stateJson: String
)

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

enum class SortMode(val label: String) {
    DATE_DESC("Recent"),
    DATE_ASC("Oldest"),
    NAME_ASC("A-Z"),
    NAME_DESC("Z-A"),
    DURATION_DESC("Longest")
}

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

enum class SaveIndicatorState {
    HIDDEN, SAVING, SAVED, ERROR
}

data class TutorialStep(
    val id: String,
    val title: String,
    val description: String,
    val highlightArea: TutorialHighlight
)

enum class TutorialHighlight {
    TIMELINE, PREVIEW, TOOL_BAR, ADD_MEDIA, EXPORT, EFFECTS
}

data class UndoHistoryEntry(
    val index: Int,
    val description: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Immutable
data class DrawingPath(
    val points: List<Pair<Float, Float>>,
    val color: Long,
    val strokeWidth: Float
)

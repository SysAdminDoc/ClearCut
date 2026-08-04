package com.novacut.editor.ui.editor

import com.novacut.editor.model.Clip
import kotlin.math.abs

internal const val ACCESSIBILITY_NUDGE_MS = 100L

private const val KEYBOARD_FINE_NUDGE_MS = 100L
private const val KEYBOARD_COARSE_NUDGE_MS = 1000L

internal enum class TimelineClipLongPressResult { OPENED_COMPOUND, TOGGLED_MULTI_SELECT }

internal fun dispatchTimelineClipLongPress(
    clipId: String,
    isCompound: Boolean,
    onOpenCompoundClip: (String) -> Boolean,
    onToggleMultiSelect: (String) -> Unit,
): TimelineClipLongPressResult {
    if (isCompound && onOpenCompoundClip(clipId)) {
        return TimelineClipLongPressResult.OPENED_COMPOUND
    }
    onToggleMultiSelect(clipId)
    return TimelineClipLongPressResult.TOGGLED_MULTI_SELECT
}

internal fun findSnapTarget(positionMs: Long, targets: List<Long>, thresholdMs: Long): Long? {
    return targets.minByOrNull { abs(it - positionMs) }
        ?.takeIf { abs(it - positionMs) <= thresholdMs }
}

internal fun Clip.containsTimelinePosition(positionMs: Long, timelineOffsetMs: Long = 0L): Boolean {
    val effectiveStartMs = timelineStartMs + timelineOffsetMs
    val effectiveEndMs = timelineEndMs + timelineOffsetMs
    return positionMs >= effectiveStartMs && positionMs < effectiveEndMs
}

internal fun Clip.accessibleSplitPointMs(playheadMs: Long, timelineOffsetMs: Long = 0L): Long? {
    val earliestEffectiveSplitMs = timelineStartMs + timelineOffsetMs + MIN_TIMELINE_CLIP_DURATION_MS
    val latestEffectiveSplitMs = timelineEndMs + timelineOffsetMs - MIN_TIMELINE_CLIP_DURATION_MS
    if (latestEffectiveSplitMs < earliestEffectiveSplitMs) return null
    val preferredEffectiveSplitMs = if (playheadMs in earliestEffectiveSplitMs..latestEffectiveSplitMs) {
        playheadMs
    } else {
        timelineStartMs + timelineOffsetMs + durationMs / 2
    }
    return (preferredEffectiveSplitMs - timelineOffsetMs).coerceIn(
        timelineStartMs + MIN_TIMELINE_CLIP_DURATION_MS,
        timelineEndMs - MIN_TIMELINE_CLIP_DURATION_MS,
    )
}

internal fun keyboardNudgeAmountMs(isShiftPressed: Boolean): Long =
    if (isShiftPressed) KEYBOARD_COARSE_NUDGE_MS else KEYBOARD_FINE_NUDGE_MS

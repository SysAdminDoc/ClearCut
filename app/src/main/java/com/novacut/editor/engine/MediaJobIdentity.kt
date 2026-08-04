package com.novacut.editor.engine

import com.novacut.editor.model.Clip
import com.novacut.editor.model.SpeedCurve

/**
 * Identity captured when a timeline-bound media job starts.
 *
 * Clip IDs are intentionally not enough: relinking can keep an ID while changing
 * the source, and trimming/splitting/reordering can change the timeline position
 * that a waveform or proxy result belongs to. Keeping the identity pure makes
 * stale-result decisions testable without a decoder, WorkManager, or Compose.
 */
data class TimelineMediaJobIdentity(
    val clipId: String,
    val sourceUri: String,
    val sourceDurationMs: Long,
    val trimStartMs: Long,
    val trimEndMs: Long,
    val timelineStartMs: Long,
    val timelineEndMs: Long,
    val speed: Float,
    val speedCurve: SpeedCurve?,
    val isReversed: Boolean,
) {
    /** Compact version token for job stores that cannot retain the full identity. */
    val version: String
        get() = listOf(
            clipId,
            sourceUri,
            sourceDurationMs,
            trimStartMs,
            trimEndMs,
            timelineStartMs,
            timelineEndMs,
            speed,
            speedCurve,
            isReversed,
        ).joinToString("\u001f")
}

fun Clip.timelineMediaJobIdentity(): TimelineMediaJobIdentity = TimelineMediaJobIdentity(
    clipId = id,
    sourceUri = sourceUri.toString(),
    sourceDurationMs = sourceDurationMs,
    trimStartMs = trimStartMs,
    trimEndMs = trimEndMs,
    timelineStartMs = timelineStartMs,
    timelineEndMs = timelineEndMs,
    speed = speed,
    speedCurve = speedCurve,
    isReversed = isReversed,
)

internal fun shouldApplyMediaJobResult(
    expected: TimelineMediaJobIdentity,
    current: TimelineMediaJobIdentity?,
): Boolean = current == expected

internal fun shouldApplyProxyResult(
    expectedVersion: String,
    currentVersion: String?,
): Boolean = expectedVersion.isNotBlank() && expectedVersion == currentVersion

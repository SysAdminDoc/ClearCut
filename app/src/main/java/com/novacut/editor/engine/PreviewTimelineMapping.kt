package com.novacut.editor.engine

import com.novacut.editor.model.Clip

/** Map an absolute timeline position to ExoPlayer's clip-relative media time. */
internal fun previewMediaPositionForTimelinePosition(clip: Clip, timelinePositionMs: Long): Long {
    val timelineOffsetMs = (timelinePositionMs - clip.timelineStartMs)
        .coerceIn(0L, clip.durationMs.coerceAtLeast(0L))
    val mediaDurationMs = (clip.trimEndMs - clip.trimStartMs).coerceAtLeast(0L)
    if (mediaDurationMs == 0L) return 0L
    return (clip.timelineOffsetToSourceMs(timelineOffsetMs) - clip.trimStartMs)
        .coerceIn(0L, (mediaDurationMs - 1L).coerceAtLeast(0L))
}

/** Map ExoPlayer's clip-relative media time back to the absolute timeline. */
internal fun previewTimelinePositionForMediaPosition(clip: Clip, mediaPositionMs: Long): Long {
    val mediaDurationMs = (clip.trimEndMs - clip.trimStartMs).coerceAtLeast(0L)
    if (mediaDurationMs == 0L) return clip.timelineStartMs
    val sourceTimeMs = clip.trimStartMs + mediaPositionMs.coerceIn(0L, mediaDurationMs)
    val timelineOffsetMs = clip.sourceTimeToTimelineOffsetMs(sourceTimeMs)
        ?: if (sourceTimeMs <= clip.trimStartMs) 0L else clip.durationMs
    return (clip.timelineStartMs + timelineOffsetMs)
        .coerceIn(clip.timelineStartMs, clip.timelineEndMs)
}

/** Resolve the instantaneous playback rate, including speed-curve ramps. */
internal fun previewSpeedForMediaPosition(clip: Clip, mediaPositionMs: Long): Float {
    val mediaDurationMs = (clip.trimEndMs - clip.trimStartMs).coerceAtLeast(0L)
    return clip.getEffectiveSpeed(mediaPositionMs.coerceIn(0L, mediaDurationMs))
}

package com.novacut.editor.engine

import com.novacut.editor.model.*

/**
 * Analyzes the timeline to detect which segments need re-encoding vs pass-through.
 * Unchanged segments (no effects, no color grade, no transform, no speed change)
 * can be muxed directly without re-encoding, dramatically speeding up export.
 */
object SmartRenderEngine {

    data class RenderSegment(
        val clipId: String,
        val startMs: Long,
        val endMs: Long,
        val needsReEncode: Boolean,
        val reason: String
    )

    /**
     * Analyze all clips to determine which need re-encoding.
     * Returns a list of render segments with their encoding requirements.
     */
    fun analyzeTimeline(
        tracks: List<Track>,
        config: ExportConfig,
        textOverlays: List<TextOverlay> = emptyList()
    ): List<RenderSegment> {
        val segments = mutableListOf<RenderSegment>()

        for (track in tracks) {
            if (!track.isVisible) continue

            for (clip in track.clips) {
                val reasons = mutableListOf<String>()

                // Check if clip needs re-encoding
                if (clip.effects.any { it.enabled }) {
                    reasons.add("effects (${clip.effects.count { it.enabled }})")
                }
                if (clip.colorGrade != null && clip.colorGrade.enabled) {
                    reasons.add("color grade")
                }
                if (clip.headTransition != null || clip.tailTransition != null) {
                    reasons.add("transition")
                }
                if (clip.speed != 1f || clip.speedCurve != null) {
                    reasons.add("speed change")
                }
                if (clip.isReversed) {
                    reasons.add("reversed")
                }
                if (clip.rotation != 0f || clip.scaleX != 1f || clip.scaleY != 1f ||
                    clip.positionX != 0f || clip.positionY != 0f
                ) {
                    reasons.add("transform")
                }
                if (clip.opacity != 1f) {
                    reasons.add("opacity")
                }
                if (clip.masks.isNotEmpty()) {
                    reasons.add("masks")
                }
                if (clip.blendMode != BlendMode.NORMAL) {
                    reasons.add("blend mode")
                }
                if (clip.keyframes.isNotEmpty()) {
                    reasons.add("keyframes")
                }
                if (clip.captions.isNotEmpty()) {
                    reasons.add("captions")
                }

                // Check for text overlays on this clip's time range
                val hasOverlay = textOverlays.any { overlay ->
                    overlay.startTimeMs < clip.timelineEndMs && overlay.endTimeMs > clip.timelineStartMs
                }
                if (hasOverlay) {
                    reasons.add("text overlay")
                }

                // Check if resolution/codec change is needed
                // (would need source media info to compare — simplified here)

                segments.add(
                    RenderSegment(
                        clipId = clip.id,
                        startMs = clip.timelineStartMs,
                        endMs = clip.timelineEndMs,
                        needsReEncode = reasons.isNotEmpty(),
                        reason = if (reasons.isEmpty()) "pass-through" else reasons.joinToString(", ")
                    )
                )
            }
        }

        return segments.sortedBy { it.startMs }
    }

    /**
     * Get a summary of the smart render analysis.
     */
    fun getSummary(segments: List<RenderSegment>): SmartRenderSummary {
        val totalMs = segments.sumOf { it.endMs - it.startMs }
        val reEncodeMs = segments.filter { it.needsReEncode }.sumOf { it.endMs - it.startMs }
        val passThroughMs = totalMs - reEncodeMs

        return SmartRenderSummary(
            totalSegments = segments.size,
            reEncodeSegments = segments.count { it.needsReEncode },
            passThroughSegments = segments.count { !it.needsReEncode },
            totalDurationMs = totalMs,
            reEncodeDurationMs = reEncodeMs,
            passThroughDurationMs = passThroughMs,
            estimatedSpeedup = if (reEncodeMs > 0) totalMs.toFloat() / reEncodeMs else Float.MAX_VALUE
        )
    }

    data class SmartRenderSummary(
        val totalSegments: Int,
        val reEncodeSegments: Int,
        val passThroughSegments: Int,
        val totalDurationMs: Long,
        val reEncodeDurationMs: Long,
        val passThroughDurationMs: Long,
        val estimatedSpeedup: Float
    )

    // --- B.5 mixed-segment stitching ---

    /**
     * A contiguous run of segments that share the same encoding decision.
     * Stitching a timeline that mixes pass-through and re-encode segments
     * groups consecutive same-flag clips into runs so each run can be
     * exported by the right engine (StreamCopy for pass-through,
     * Transformer / FFmpeg for re-encode) and the outputs concatenated.
     *
     * @param startMs Timeline start time of the first segment in the run.
     * @param endMs Timeline end time of the last segment in the run.
     * @param needsReEncode Whether this run needs re-encoding.
     * @param clipIds Ordered list of clip ids in this run.
     */
    data class RenderRun(
        val startMs: Long,
        val endMs: Long,
        val needsReEncode: Boolean,
        val clipIds: List<String>,
    ) {
        val durationMs: Long get() = endMs - startMs
    }

    /**
     * Group an ordered per-clip [analyzeTimeline] result into contiguous
     * runs. Two consecutive segments belong to the same run when they share
     * the [RenderSegment.needsReEncode] flag **and** the previous segment ends
     * exactly where the next one starts (no timeline gap).
     *
     * A timeline gap forces a new run even when the flags match — gaps need
     * an explicit black-frame fill which today only the re-encode path can
     * produce, so the gap-bridging step is left to a future bridge pass on
     * the composer side.
     *
     * The composer can stitch run outputs through the source-pinned
     * FFmpegKitNext concat demuxer.
     */
    fun planRuns(segments: List<RenderSegment>): List<RenderRun> {
        if (segments.isEmpty()) return emptyList()
        val ordered = segments.sortedBy { it.startMs }
        val runs = mutableListOf<RenderRun>()
        var runStart = ordered.first().startMs
        var runEnd = ordered.first().endMs
        var runFlag = ordered.first().needsReEncode
        var runClips = mutableListOf(ordered.first().clipId)
        for (i in 1 until ordered.size) {
            val seg = ordered[i]
            val contiguous = seg.startMs == runEnd
            if (seg.needsReEncode == runFlag && contiguous) {
                runEnd = seg.endMs
                runClips += seg.clipId
            } else {
                runs += RenderRun(runStart, runEnd, runFlag, runClips.toList())
                runStart = seg.startMs
                runEnd = seg.endMs
                runFlag = seg.needsReEncode
                runClips = mutableListOf(seg.clipId)
            }
        }
        runs += RenderRun(runStart, runEnd, runFlag, runClips.toList())
        return runs
    }
}

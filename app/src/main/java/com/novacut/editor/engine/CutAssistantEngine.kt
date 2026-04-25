package com.novacut.editor.engine

import com.novacut.editor.engine.SilenceDetectionEngine.AutoCutConfig
import com.novacut.editor.engine.SilenceDetectionEngine.CutProposal
import com.novacut.editor.engine.whisper.SherpaAsrEngine
import com.novacut.editor.model.Clip
import com.novacut.editor.model.Track
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrator for the Cut Assistant ("Review proposed cuts") workflow described
 * in [ROADMAP.md](file:./ROADMAP.md) Tier C.2 / R4.5.
 *
 * Pure planner — never mutates state. Combines per-clip silence detection (RMS
 * over the clip waveform) with optional Whisper word-level filler-token
 * detection, normalises every proposal into timeline coordinates, sorts and
 * merges overlapping ranges, and returns a [ReviewSet] of accept/reject
 * candidates the UI can present non-destructively.
 *
 * Translating accepted proposals into edits is intentionally a separate concern
 * from generating them — see [planAcceptedOperations]. The result is a list of
 * [CutOperation]s any ViewModel-level applier can replay through its existing
 * split/delete primitives so the entire flow stays inside the undo stack.
 */
@Singleton
class CutAssistantEngine @Inject constructor(
    private val silenceDetectionEngine: SilenceDetectionEngine
) {

    /** A single proposal with provenance + a stable [id] for selection state. */
    data class ReviewProposal(
        val id: String,
        val clipId: String,
        val timelineStartMs: Long,
        val timelineEndMs: Long,
        val reason: CutProposal.Reason,
        val matchedText: String? = null
    ) {
        val durationMs: Long get() = timelineEndMs - timelineStartMs

        init {
            require(timelineEndMs > timelineStartMs) {
                "Proposal end ($timelineEndMs) must be after start ($timelineStartMs)"
            }
        }
    }

    /**
     * Container for a Cut Assistant pass over the whole project. Holds the
     * proposals + the user's per-id acceptance set so the UI can flip
     * individual entries without rebuilding the whole pass.
     */
    data class ReviewSet(
        val proposals: List<ReviewProposal>,
        val accepted: Set<String> = emptySet()
    ) {
        fun toggle(id: String): ReviewSet =
            copy(accepted = if (id in accepted) accepted - id else accepted + id)

        fun acceptAll(): ReviewSet =
            copy(accepted = proposals.map { it.id }.toSet())

        fun rejectAll(): ReviewSet = copy(accepted = emptySet())

        val acceptedProposals: List<ReviewProposal>
            get() = proposals.filter { it.id in accepted }

        val totalReclaimMs: Long
            get() = acceptedProposals.sumOf { it.durationMs }
    }

    /**
     * Per-clip waveform input. The waveform is the same `FloatArray` already
     * cached for the timeline ruler — callers should reuse it rather than
     * decoding twice.
     */
    data class ClipAudio(
        val clipId: String,
        val waveform: FloatArray,
        val sampleRate: Int,
        /**
         * Words spoken inside *this clip* with start/end times relative to the
         * clip's source timeline (matching what Whisper returns directly).
         * Empty when no transcript was generated yet.
         */
        val words: List<SherpaAsrEngine.WordTimestamp> = emptyList()
    )

    /**
     * Run the full Cut Assistant pass. Walks every video/audio clip in
     * [tracks], looks up its [ClipAudio] in [perClipAudio], runs silence +
     * filler detection, projects ranges into timeline coordinates, then merges
     * adjacent/overlapping proposals so the UI doesn't list two abutting
     * silences as separate cuts.
     */
    fun review(
        tracks: List<Track>,
        perClipAudio: Map<String, ClipAudio>,
        config: AutoCutConfig = AutoCutConfig()
    ): ReviewSet {
        val raw = mutableListOf<ReviewProposal>()
        var serial = 0
        tracks.forEach { track ->
            track.clips.forEach { clip ->
                val audio = perClipAudio[clip.id] ?: return@forEach
                val silences = silenceDetectionEngine.detectSilences(audio.waveform, audio.sampleRate, config)
                val fillers = silenceDetectionEngine.detectFillerWords(audio.words, config)
                (silences + fillers).forEach { p ->
                    val tl = projectClipRangeToTimeline(clip, p.startMs, p.endMs) ?: return@forEach
                    raw += ReviewProposal(
                        id = "p${serial++}_${clip.id.take(8)}",
                        clipId = clip.id,
                        timelineStartMs = tl.first,
                        timelineEndMs = tl.second,
                        reason = p.reason,
                        matchedText = p.matchedText
                    )
                }
            }
        }
        val merged = mergeOverlapping(raw, gapToleranceMs = MERGE_GAP_TOLERANCE_MS)
        return ReviewSet(proposals = merged)
    }

    /**
     * Convert the user's acceptance set into a sequence of timeline operations
     * the ViewModel can apply through its existing split + delete primitives.
     *
     * Operations are ordered *latest-first* — applying them in reverse-timeline
     * order keeps the indices behind each cut stable, which matters because
     * the UI's split + delete commands shift everything to their right.
     */
    fun planAcceptedOperations(reviewSet: ReviewSet): List<CutOperation> {
        return reviewSet.acceptedProposals
            .sortedByDescending { it.timelineStartMs }
            .map { p ->
                CutOperation.RippleDelete(
                    clipId = p.clipId,
                    timelineStartMs = p.timelineStartMs,
                    timelineEndMs = p.timelineEndMs,
                    reason = p.reason,
                    matchedText = p.matchedText
                )
            }
    }

    /**
     * Operation type emitted by [planAcceptedOperations]. Kept independent from
     * [com.novacut.editor.engine.EditCommand] so this engine can stay free of
     * ViewModel coupling — the applier translates each op into the right pair
     * of split/delete commands at apply time.
     */
    sealed class CutOperation {
        data class RippleDelete(
            val clipId: String,
            val timelineStartMs: Long,
            val timelineEndMs: Long,
            val reason: CutProposal.Reason,
            val matchedText: String?
        ) : CutOperation()
    }

    /**
     * Map clip-source-relative ms (returned by [SilenceDetectionEngine]) into
     * timeline-relative ms by accounting for trim, speed, and timelineStart.
     *
     * Returns null when the proposal falls outside the clip's trimmed range —
     * that can happen if the waveform was captured pre-trim and the user has
     * since pulled the trim handle past the silence.
     */
    private fun projectClipRangeToTimeline(
        clip: Clip,
        clipRelStartMs: Long,
        clipRelEndMs: Long
    ): Pair<Long, Long>? {
        val trimRange = clip.trimEndMs - clip.trimStartMs
        if (trimRange <= 0L) return null
        // The proposal's clip-relative ms came from the waveform, which is in
        // *source* time (untrimmed). Clip the proposal to the trim window
        // before projecting so a silence that straddles the trim handle only
        // contributes the visible portion.
        val sourceStart = clipRelStartMs.coerceIn(clip.trimStartMs, clip.trimEndMs)
        val sourceEnd = clipRelEndMs.coerceIn(clip.trimStartMs, clip.trimEndMs)
        if (sourceEnd - sourceStart < MIN_CONTRIBUTION_MS) return null

        // Effective clip duration uses speed (constant or curve harmonic mean
        // already baked into Clip.durationMs). Timeline span per source ms is
        // therefore durationMs/trimRange — apply that scale to the offsets.
        val durationMs = clip.durationMs
        if (durationMs <= 0L) return null
        val timelineStart = clip.timelineStartMs +
            ((sourceStart - clip.trimStartMs).toDouble() * durationMs / trimRange).toLong()
        val timelineEnd = clip.timelineStartMs +
            ((sourceEnd - clip.trimStartMs).toDouble() * durationMs / trimRange).toLong()
        if (timelineEnd <= timelineStart) return null
        return timelineStart to timelineEnd
    }

    private fun mergeOverlapping(
        proposals: List<ReviewProposal>,
        gapToleranceMs: Long
    ): List<ReviewProposal> {
        if (proposals.isEmpty()) return proposals
        val sorted = proposals.sortedWith(
            compareBy({ it.clipId }, { it.timelineStartMs })
        )
        val out = mutableListOf<ReviewProposal>()
        var current = sorted.first()
        for (i in 1 until sorted.size) {
            val next = sorted[i]
            val sameClip = next.clipId == current.clipId
            val withinTolerance = next.timelineStartMs <= current.timelineEndMs + gapToleranceMs
            if (sameClip && withinTolerance) {
                current = ReviewProposal(
                    id = current.id, // keep the earlier proposal's id so existing UI selection survives
                    clipId = current.clipId,
                    timelineStartMs = current.timelineStartMs,
                    timelineEndMs = maxOf(current.timelineEndMs, next.timelineEndMs),
                    // Once a silence absorbs a filler the merged label trends toward "silence" —
                    // it's the more conservative interpretation when the user reviews the cut.
                    reason = if (current.reason == CutProposal.Reason.SILENCE ||
                        next.reason == CutProposal.Reason.SILENCE) CutProposal.Reason.SILENCE else current.reason,
                    matchedText = current.matchedText ?: next.matchedText
                )
            } else {
                out += current
                current = next
            }
        }
        out += current
        return out
    }

    companion object {
        /**
         * Two abutting proposals separated by less than this gap collapse into
         * one entry. Picked so a "um... uh..." run with ~150 ms between tokens
         * shows up as a single review row instead of three, but a 1 s pause
         * between sentences still gets its own card.
         */
        private const val MERGE_GAP_TOLERANCE_MS = 250L

        /**
         * Skip proposals shorter than this after trim clipping. Below ~80 ms
         * the visual jolt of a cut outweighs the time saved.
         */
        private const val MIN_CONTRIBUTION_MS = 80L
    }
}

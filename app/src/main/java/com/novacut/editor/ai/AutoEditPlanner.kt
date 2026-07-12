package com.novacut.editor.ai

import kotlin.math.min

/** Pure, deterministic proposal planner for pre-scored Auto Edit source windows. */
class AutoEditPlanner {

    fun plan(request: AutoEditPlanRequest): AutoEditPlan {
        validate(request)
        if (request.windows.isEmpty()) {
            return AutoEditPlan(
                requestedDurationMs = request.targetDurationMs,
                plannedDurationMs = 0L,
                intent = request.intent,
                beatSupport = beatSupportWithoutWindows(request.intent),
                warnings = listOf(AutoEditPlanWarning.NO_USABLE_WINDOWS),
                confidence = 0f
            )
        }

        val availableDurationMs = unionDuration(request.windows)
        val targetDurationMs = min(
            request.targetDurationMs,
            min(request.maxOutputDurationMs, availableDurationMs)
        )
        val warnings = mutableListOf<AutoEditPlanWarning>()
        if (targetDurationMs != request.targetDurationMs) {
            warnings += AutoEditPlanWarning.TARGET_CLAMPED
        }

        val selection = when (request.intent) {
            AutoEditIntent.SOURCE_ORDER -> Selection(
                segments = selectInSourceOrder(request.windows, targetDurationMs),
                beatSupport = AutoEditBeatSupport.NOT_REQUESTED
            )

            AutoEditIntent.HIGHLIGHT_REEL -> Selection(
                segments = selectHighlights(request.windows, targetDurationMs, beatDurationsMs = null),
                beatSupport = AutoEditBeatSupport.NOT_REQUESTED
            )

            AutoEditIntent.BEAT_SYNC -> selectBeatSync(request, targetDurationMs, warnings)
        }

        val plannedDurationMs = selection.segments.sumOf { it.durationMs }
        if (plannedDurationMs < targetDurationMs) {
            warnings += AutoEditPlanWarning.TARGET_UNDERFILLED
        }
        val confidence = if (selection.segments.isEmpty() || targetDurationMs == 0L) {
            0f
        } else {
            val score = selection.segments.sumOf { it.confidence.toDouble() * it.durationMs } /
                plannedDurationMs.coerceAtLeast(1L)
            (score * plannedDurationMs / targetDurationMs).toFloat().coerceIn(0f, 1f)
        }

        return AutoEditPlan(
            requestedDurationMs = request.targetDurationMs,
            plannedDurationMs = plannedDurationMs,
            intent = request.intent,
            segments = selection.segments,
            beatSupport = selection.beatSupport,
            warnings = warnings.distinct(),
            confidence = confidence
        )
    }

    private fun validate(request: AutoEditPlanRequest) {
        require(request.targetDurationMs > 0L) { "targetDurationMs must be positive" }
        require(request.maxOutputDurationMs > 0L) { "maxOutputDurationMs must be positive" }
        require(request.windows.size <= MAX_WINDOWS) { "At most $MAX_WINDOWS windows are supported" }
        require(request.windows.map { it.id }.toSet().size == request.windows.size) {
            "Window IDs must be unique"
        }
        request.windows.groupBy { it.clipId }.forEach { (clipId, windows) ->
            require(windows.map { it.clipFingerprint }.distinct().size == 1) {
                "Clip $clipId has inconsistent fingerprints"
            }
        }
    }

    private fun selectBeatSync(
        request: AutoEditPlanRequest,
        targetDurationMs: Long,
        warnings: MutableList<AutoEditPlanWarning>
    ): Selection {
        val beats = request.beatPositionsMs
            .asSequence()
            .filter { it > 0L && it < targetDurationMs }
            .distinct()
            .sorted()
            .toList()
        if (beats.size < MIN_BEAT_MARKERS) {
            warnings += AutoEditPlanWarning.BEAT_DATA_MISSING
            return Selection(
                selectHighlights(request.windows, targetDurationMs, beatDurationsMs = null),
                AutoEditBeatSupport.DEGRADED
            )
        }

        val boundaries = mutableListOf(0L)
        beats.forEach { beat ->
            if (beat - boundaries.last() >= MIN_SEGMENT_DURATION_MS &&
                targetDurationMs - beat >= MIN_SEGMENT_DURATION_MS
            ) {
                boundaries += beat
            }
        }
        boundaries += targetDurationMs
        val durations = boundaries.zipWithNext { start, end -> end - start }
        if (durations.size < 2) {
            warnings += AutoEditPlanWarning.BEAT_DATA_MISSING
            return Selection(
                selectHighlights(request.windows, targetDurationMs, beatDurationsMs = null),
                AutoEditBeatSupport.DEGRADED
            )
        }

        val aligned = selectHighlights(request.windows, targetDurationMs, durations)
        if (aligned.sumOf { it.durationMs } != targetDurationMs) {
            warnings += AutoEditPlanWarning.BEAT_ALIGNMENT_UNSUPPORTED
            return Selection(
                selectHighlights(request.windows, targetDurationMs, beatDurationsMs = null),
                AutoEditBeatSupport.DEGRADED
            )
        }
        return Selection(aligned, AutoEditBeatSupport.ALIGNED)
    }

    private fun selectInSourceOrder(
        windows: List<AutoEditWindow>,
        targetDurationMs: Long
    ): List<AutoEditSegmentProposal> {
        val candidates = windows.sortedWith(
            compareBy<AutoEditWindow> { it.sourceOrder }
                .thenBy { it.sourceStartMs }
                .thenBy { it.id }
        )
        return selectSequential(candidates, targetDurationMs, beatDurationsMs = null)
    }

    private fun selectHighlights(
        windows: List<AutoEditWindow>,
        targetDurationMs: Long,
        beatDurationsMs: List<Long>?
    ): List<AutoEditSegmentProposal> {
        val remaining = windows.toMutableList()
        val selected = mutableListOf<AutoEditSegmentProposal>()
        val clipUseCounts = mutableMapOf<String, Int>()
        var timelineMs = 0L
        var beatIndex = 0

        while (timelineMs < targetDurationMs && remaining.isNotEmpty()) {
            val desiredDurationMs = beatDurationsMs?.getOrNull(beatIndex)
                ?: (targetDurationMs - timelineMs)
            val candidate = remaining
                .asSequence()
                .filter { it.durationMs >= min(desiredDurationMs, MIN_SEGMENT_DURATION_MS) }
                .filter { window -> !overlapsSelection(window, selected) }
                .sortedWith(
                    compareByDescending<AutoEditWindow> {
                        score(it, AutoEditIntent.HIGHLIGHT_REEL) -
                            DIVERSITY_PENALTY * clipUseCounts.getOrDefault(it.clipId, 0)
                    }
                        .thenBy { it.sourceOrder }
                        .thenBy { it.sourceStartMs }
                        .thenBy { it.id }
                )
                .firstOrNull() ?: break

            remaining.remove(candidate)
            val durationMs = min(candidate.durationMs, desiredDurationMs)
            if (durationMs <= 0L) break
            selected += proposal(
                window = candidate,
                timelineStartMs = timelineMs,
                durationMs = durationMs,
                intent = if (beatDurationsMs == null) AutoEditIntent.HIGHLIGHT_REEL else AutoEditIntent.BEAT_SYNC,
                beatAligned = beatDurationsMs != null
            )
            timelineMs += durationMs
            clipUseCounts[candidate.clipId] = clipUseCounts.getOrDefault(candidate.clipId, 0) + 1
            if (beatDurationsMs != null) beatIndex++
        }
        return selected
    }

    private fun selectSequential(
        candidates: List<AutoEditWindow>,
        targetDurationMs: Long,
        beatDurationsMs: List<Long>?
    ): List<AutoEditSegmentProposal> {
        val selected = mutableListOf<AutoEditSegmentProposal>()
        var timelineMs = 0L
        candidates.forEach { candidate ->
            if (timelineMs >= targetDurationMs || overlapsSelection(candidate, selected)) return@forEach
            val desiredMs = beatDurationsMs?.getOrNull(selected.size) ?: (targetDurationMs - timelineMs)
            val durationMs = min(candidate.durationMs, desiredMs)
            if (durationMs <= 0L) return@forEach
            selected += proposal(
                candidate,
                timelineMs,
                durationMs,
                AutoEditIntent.SOURCE_ORDER,
                beatAligned = false
            )
            timelineMs += durationMs
        }
        return selected
    }

    private fun proposal(
        window: AutoEditWindow,
        timelineStartMs: Long,
        durationMs: Long,
        intent: AutoEditIntent,
        beatAligned: Boolean
    ): AutoEditSegmentProposal {
        val score = score(window, intent)
        return AutoEditSegmentProposal(
            windowId = window.id,
            clipId = window.clipId,
            clipFingerprint = window.clipFingerprint,
            sourceStartMs = window.sourceStartMs,
            sourceEndMs = window.sourceStartMs + durationMs,
            timelineStartMs = timelineStartMs,
            timelineEndMs = timelineStartMs + durationMs,
            scoreComponents = window.scores,
            score = score,
            confidence = score.coerceIn(0f, 1f),
            rationale = rationale(window.scores, beatAligned),
            beatAligned = beatAligned
        )
    }

    private fun score(window: AutoEditWindow, intent: AutoEditIntent): Float {
        val scores = window.scores
        return when (intent) {
            AutoEditIntent.SOURCE_ORDER ->
                scores.visualQuality * 0.40f + scores.subjectPresence * 0.25f +
                    scores.motion * 0.15f + scores.audioEnergy * 0.10f +
                    scores.keywordRelevance * 0.10f

            AutoEditIntent.HIGHLIGHT_REEL ->
                scores.visualQuality * 0.35f + scores.motion * 0.25f +
                    scores.subjectPresence * 0.20f + scores.audioEnergy * 0.10f +
                    scores.keywordRelevance * 0.10f

            AutoEditIntent.BEAT_SYNC ->
                scores.visualQuality * 0.30f + scores.motion * 0.25f +
                    scores.subjectPresence * 0.15f + scores.audioEnergy * 0.25f +
                    scores.keywordRelevance * 0.05f
        }
    }

    private fun rationale(scores: AutoEditScoreComponents, beatAligned: Boolean): List<String> {
        val reasons = listOf(
            scores.visualQuality to "Strong visual quality",
            scores.motion to "Engaging motion",
            scores.subjectPresence to "Clear subject presence",
            scores.audioEnergy to "Strong audio energy",
            scores.keywordRelevance to "Matches requested keywords"
        ).filter { it.first > 0f }
            .sortedByDescending { it.first }
            .take(2)
            .map { it.second }
            .toMutableList()
        if (reasons.isEmpty()) reasons += "Usable source window"
        if (beatAligned) reasons += "Cut aligned to detected beat"
        return reasons
    }

    private fun overlapsSelection(
        window: AutoEditWindow,
        selected: List<AutoEditSegmentProposal>
    ): Boolean = selected.any {
        it.clipId == window.clipId && it.clipFingerprint == window.clipFingerprint &&
            window.sourceStartMs < it.sourceEndMs && window.sourceEndMs > it.sourceStartMs
    }

    private fun unionDuration(windows: List<AutoEditWindow>): Long = windows
        .groupBy { it.clipId to it.clipFingerprint }
        .values
        .sumOf { clipWindows ->
            val sorted = clipWindows.sortedBy { it.sourceStartMs }
            var total = 0L
            var start = sorted.first().sourceStartMs
            var end = sorted.first().sourceEndMs
            sorted.drop(1).forEach { window ->
                if (window.sourceStartMs <= end) {
                    end = maxOf(end, window.sourceEndMs)
                } else {
                    total += end - start
                    start = window.sourceStartMs
                    end = window.sourceEndMs
                }
            }
            total + end - start
        }

    private fun beatSupportWithoutWindows(intent: AutoEditIntent): AutoEditBeatSupport =
        if (intent == AutoEditIntent.BEAT_SYNC) AutoEditBeatSupport.DEGRADED
        else AutoEditBeatSupport.NOT_REQUESTED

    private data class Selection(
        val segments: List<AutoEditSegmentProposal>,
        val beatSupport: AutoEditBeatSupport
    )

    companion object {
        const val MAX_WINDOWS = 4_096
        const val DEFAULT_MAX_OUTPUT_DURATION_MS = 10 * 60 * 1_000L
        const val MIN_SEGMENT_DURATION_MS = 750L
        private const val MIN_BEAT_MARKERS = 2
        private const val DIVERSITY_PENALTY = 0.18f
    }
}

enum class AutoEditIntent { HIGHLIGHT_REEL, SOURCE_ORDER, BEAT_SYNC }

enum class AutoEditBeatSupport { NOT_REQUESTED, ALIGNED, DEGRADED }

enum class AutoEditPlanWarning {
    NO_USABLE_WINDOWS,
    TARGET_CLAMPED,
    TARGET_UNDERFILLED,
    BEAT_DATA_MISSING,
    BEAT_ALIGNMENT_UNSUPPORTED
}

data class AutoEditPlanRequest(
    val windows: List<AutoEditWindow>,
    val targetDurationMs: Long,
    val intent: AutoEditIntent = AutoEditIntent.HIGHLIGHT_REEL,
    val beatPositionsMs: List<Long> = emptyList(),
    val maxOutputDurationMs: Long = AutoEditPlanner.DEFAULT_MAX_OUTPUT_DURATION_MS
)

data class AutoEditWindow(
    val id: String,
    val clipId: String,
    val clipFingerprint: String,
    val sourceOrder: Int,
    val sourceStartMs: Long,
    val sourceEndMs: Long,
    val scores: AutoEditScoreComponents
) {
    init {
        require(id.isNotBlank()) { "id must not be blank" }
        require(clipId.isNotBlank()) { "clipId must not be blank" }
        require(clipFingerprint.isNotBlank()) { "clipFingerprint must not be blank" }
        require(sourceOrder >= 0) { "sourceOrder must not be negative" }
        require(sourceStartMs >= 0L) { "sourceStartMs must not be negative" }
        require(sourceEndMs > sourceStartMs) { "sourceEndMs must be after sourceStartMs" }
    }

    val durationMs: Long get() = sourceEndMs - sourceStartMs
}

data class AutoEditScoreComponents(
    val visualQuality: Float,
    val motion: Float,
    val subjectPresence: Float,
    val audioEnergy: Float,
    val keywordRelevance: Float = 0f
) {
    init {
        mapOf(
            "visualQuality" to visualQuality,
            "motion" to motion,
            "subjectPresence" to subjectPresence,
            "audioEnergy" to audioEnergy,
            "keywordRelevance" to keywordRelevance
        ).forEach { (name, value) ->
            require(value.isFinite() && value in 0f..1f) { "$name must be finite and in [0, 1]" }
        }
    }
}

data class AutoEditSegmentProposal(
    val windowId: String,
    val clipId: String,
    val clipFingerprint: String,
    val sourceStartMs: Long,
    val sourceEndMs: Long,
    val timelineStartMs: Long,
    val timelineEndMs: Long,
    val scoreComponents: AutoEditScoreComponents,
    val score: Float,
    val confidence: Float,
    val rationale: List<String>,
    val beatAligned: Boolean
) {
    val durationMs: Long get() = timelineEndMs - timelineStartMs
}

data class AutoEditPlan(
    val requestedDurationMs: Long,
    val plannedDurationMs: Long,
    val intent: AutoEditIntent,
    val segments: List<AutoEditSegmentProposal> = emptyList(),
    val beatSupport: AutoEditBeatSupport,
    val warnings: List<AutoEditPlanWarning> = emptyList(),
    val confidence: Float
)

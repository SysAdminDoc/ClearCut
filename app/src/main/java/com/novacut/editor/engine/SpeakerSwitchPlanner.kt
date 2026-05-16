package com.novacut.editor.engine

/**
 * R6.14 — Multicam SmartSwitch planner.
 *
 * Takes a list of synced multicam angles plus a speaker timeline (typically
 * derived from Whisper word timestamps with diarization metadata, or from
 * voice-activity detection alone) and emits a `cutPlan` — the ordered list
 * of `(timelineMs, angleIndex)` cuts a multicam panel can apply with a
 * single "Auto-switch by speaker" toggle.
 *
 * The planner is **pure** — no Android dependencies, no I/O — so it tests
 * exhaustively on the JVM. Bindings to the live Whisper / MultiCamEngine
 * output happen in the AudioMixerDelegate (or a dedicated MultiCamDelegate)
 * at a later commit.
 *
 * ## Algorithm summary
 *
 *  1. Walk the speaker turns in timeline order.
 *  2. For each turn, look up the angle assigned to that speaker.
 *      - If the speaker has an explicit assignment, use it.
 *      - Otherwise round-robin through angles in order of first-appearance,
 *        so the same speaker keeps the same angle across turns even when
 *        the planner runs without a manual mapping.
 *  3. Coalesce consecutive turns that resolve to the same angle into a
 *     single cut — a cut only emits when the active angle actually changes.
 *  4. Apply [SwitchPolicy.minDwellMs]: if a proposed cut would leave the
 *     previous angle on screen for less than the dwell threshold, the cut
 *     is dropped — too-rapid switches feel like flicker on real video.
 *
 * The output is the cut plan in canonical form; the multicam apply step
 * just iterates the list.
 */
object SpeakerSwitchPlanner {

    /**
     * A speaker turn in the timeline. `speakerId` is opaque — typically the
     * Whisper diarization label ("SPEAKER_00") but any stable string works.
     */
    data class SpeakerTurn(
        val speakerId: String,
        val startMs: Long,
        val endMs: Long,
    ) {
        init {
            require(endMs > startMs) {
                "SpeakerTurn endMs ($endMs) must be > startMs ($startMs)"
            }
        }
    }

    /**
     * A synced multicam angle. `angleIndex` is the position in the
     * MultiCamEngine.syncedTracks list; the planner only consumes the index
     * to stay decoupled from the engine's `Track` shape.
     */
    data class Angle(
        val angleIndex: Int,
        /** Optional pre-assignment: this angle is "owned" by this speaker. */
        val assignedSpeakerId: String? = null,
    )

    /**
     * Policy knobs.
     *
     * @param minDwellMs minimum on-screen time for the previously-active
     *   angle before a new cut is permitted. Cuts that would violate the
     *   floor are dropped (the planner stays on the previous angle).
     * @param initialAngleIndex which angle to start on if the first turn
     *   doesn't already select an angle by speaker mapping.
     */
    data class SwitchPolicy(
        val minDwellMs: Long = 800L,
        val initialAngleIndex: Int = 0,
    ) {
        init {
            require(minDwellMs >= 0) { "minDwellMs must be >= 0" }
        }
    }

    data class Cut(val timelineMs: Long, val angleIndex: Int)

    data class CutPlan(
        val cuts: List<Cut>,
        /**
         * Speaker → angle map the planner used. Surfaced so the UI can
         * pre-fill its "manual override" controls without re-deriving.
         */
        val speakerAngleMap: Map<String, Int>,
    )

    /**
     * Build a cut plan from speaker turns + angles.
     */
    fun plan(
        speakerTurns: List<SpeakerTurn>,
        angles: List<Angle>,
        policy: SwitchPolicy = SwitchPolicy(),
    ): CutPlan {
        if (angles.isEmpty() || speakerTurns.isEmpty()) {
            return CutPlan(cuts = emptyList(), speakerAngleMap = emptyMap())
        }

        val turns = speakerTurns.sortedBy { it.startMs }
        val angleIndices = angles.map { it.angleIndex }
        val initial = policy.initialAngleIndex.takeIf { it in angleIndices }
            ?: angleIndices.first()

        // Seed the speaker → angle map with any explicit assignments.
        val mapping = HashMap<String, Int>()
        for (a in angles) {
            val id = a.assignedSpeakerId ?: continue
            if (id in mapping) continue
            mapping[id] = a.angleIndex
        }

        // Round-robin queue for speakers that don't have an assignment.
        val assignedAngles = mapping.values.toHashSet()
        val freeAngles = angleIndices.filter { it !in assignedAngles }.toMutableList()

        fun angleFor(speakerId: String): Int {
            mapping[speakerId]?.let { return it }
            // First-appearance round-robin from the free pool; fall back to
            // any angle if the pool is exhausted (more speakers than angles).
            val next = if (freeAngles.isNotEmpty()) {
                freeAngles.removeAt(0)
            } else {
                angleIndices[mapping.size % angleIndices.size]
            }
            mapping[speakerId] = next
            return next
        }

        val cuts = mutableListOf<Cut>()
        var activeAngle = initial
        var activeAngleStart = turns.first().startMs
        // Always cut to the initial angle at the first turn's start, so the
        // resulting plan is self-contained and doesn't assume a starting state.
        cuts += Cut(timelineMs = activeAngleStart, angleIndex = activeAngle)

        for (turn in turns) {
            val target = angleFor(turn.speakerId)
            if (target == activeAngle) continue

            val dwell = turn.startMs - activeAngleStart
            if (dwell < policy.minDwellMs) {
                // Would flicker — keep the previous angle.
                continue
            }
            cuts += Cut(timelineMs = turn.startMs, angleIndex = target)
            activeAngle = target
            activeAngleStart = turn.startMs
        }

        return CutPlan(cuts = cuts.toList(), speakerAngleMap = mapping.toMap())
    }
}

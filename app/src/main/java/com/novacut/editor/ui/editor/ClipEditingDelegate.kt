package com.novacut.editor.ui.editor

import android.net.Uri
import com.novacut.editor.engine.VideoEngine
import com.novacut.editor.model.Clip
import com.novacut.editor.model.Track
import com.novacut.editor.model.TrackType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Delegate handling clip editing operations: add, select, delete, duplicate,
 * merge, split, trim, speed, and reverse.
 * Extracted from EditorViewModel to reduce its size.
 */
class ClipEditingDelegate(
    private val stateFlow: MutableStateFlow<EditorState>,
    private val videoEngine: VideoEngine,
    private val scope: CoroutineScope,
    private val saveUndoState: (String) -> Unit,
    private val showToast: (String) -> Unit,
    private val rebuildPlayerTimeline: () -> Unit,
    private val saveProject: () -> Unit,
    private val updatePreview: () -> Unit,
    private val recalculateDuration: (EditorState) -> EditorState,
    private val onClipAdded: ((clipId: String, uri: Uri) -> Unit)? = null
) {
    // Rolling timestamps of recent delete operations for the bulk-change
    // detector. Bounded to the window length so the structure can't grow.
    // Accessed only from the delegate's own methods, which in turn are only
    // called from the Main thread by EditorViewModel — no locking required.
    private val recentDeletesMs = ArrayDeque<Long>()
    private val bulkDeleteWindowMs = 10_000L
    private val bulkDeleteThreshold = 3
    // --- Add Clip ---
    fun addClipToTrack(uri: Uri, trackType: TrackType = TrackType.VIDEO) {
        scope.launch {
            val mediaInfo = try {
                withContext(Dispatchers.IO) {
                    Triple(
                        videoEngine.getMediaDuration(uri),
                        videoEngine.hasVisualTrack(uri),
                        videoEngine.hasAudioTrack(uri)
                    )
                }
            } catch (e: Exception) {
                showToast("Could not read media: ${e.message ?: "Unknown error"}")
                return@launch
            }
            val (duration, hasVisualTrack, hasAudioTrack) = mediaInfo
            if (duration <= 0) {
                showToast("Could not read media file")
                return@launch
            }

            saveUndoState("Add clip")

            // Create clip ID outside state update so follow-up hooks can reference it.
            val clipId = UUID.randomUUID().toString()
            val linkedAudioClipId = if (
                trackType == TrackType.VIDEO &&
                hasVisualTrack &&
                hasAudioTrack
            ) {
                UUID.randomUUID().toString()
            } else {
                null
            }

            stateFlow.update { state ->
                val baseTracks = if (state.tracks.any { it.type == trackType }) {
                    state.tracks
                } else {
                    state.tracks + Track(type = trackType, index = state.tracks.size)
                }
                val trackIndex = baseTracks.indexOfFirst { it.type == trackType }
                val track = baseTracks[trackIndex]
                val timelineStart = track.clips.maxOfOrNull { it.timelineEndMs } ?: 0L

                val clip = Clip(
                    id = clipId,
                    sourceUri = uri,
                    sourceDurationMs = duration,
                    timelineStartMs = timelineStart,
                    trimStartMs = 0L,
                    trimEndMs = duration,
                    linkedClipId = linkedAudioClipId
                )

                var tracks = baseTracks.mapIndexed { i, t ->
                    if (i == trackIndex) t.copy(clips = t.clips + clip) else t
                }

                if (linkedAudioClipId != null) {
                    val linkedAudioClip = Clip(
                        id = linkedAudioClipId,
                        sourceUri = uri,
                        sourceDurationMs = duration,
                        timelineStartMs = timelineStart,
                        trimStartMs = 0L,
                        trimEndMs = duration,
                        linkedClipId = clipId
                    )
                    val clipEndMs = timelineStart + linkedAudioClip.durationMs
                    val audioTrackIndex = preferredAudioTrackIndex(
                        tracks = tracks,
                        startMs = timelineStart,
                        endMs = clipEndMs
                    )
                    tracks = if (audioTrackIndex != null) {
                        tracks.mapIndexed { i, t ->
                            if (i == audioTrackIndex) {
                                t.copy(clips = t.clips + linkedAudioClip)
                            } else {
                                t
                            }
                        }
                    } else {
                        tracks + Track(
                            type = TrackType.AUDIO,
                            index = tracks.size,
                            clips = listOf(linkedAudioClip)
                        )
                    }
                }

                recalculateDuration(state.copy(
                    tracks = tracks,
                    selectedClipId = clip.id,
                    selectedTrackId = track.id,
                    panels = state.panels.close(PanelId.MEDIA_PICKER)
                ))
            }

            // Rebuild through the shared path so preview and normalization stay in sync.
            rebuildPlayerTimeline()
            saveProject()

            // Notify ViewModel for proxy registration
            onClipAdded?.invoke(clipId, uri)
        }
    }

    // --- Select Clip ---
    fun selectClip(clipId: String?, trackId: String? = null) {
        stateFlow.update { s ->
            val newSelectedIds = if (clipId != null) {
                val allClips = s.tracks.flatMap { it.clips }
                val selectedClip = allClips.find { it.id == clipId }
                if (selectedClip?.groupId != null) {
                    allClips
                        .filter { it.groupId == selectedClip.groupId }
                        .map { it.id }
                        .toSet()
                } else {
                    setOf(clipId)
                }
            } else {
                emptySet()
            }
            s.copy(selectedClipId = clipId, selectedTrackId = trackId, selectedClipIds = newSelectedIds)
        }
        updatePreview()
    }

    // --- Delete Clip ---
    fun deleteSelectedClip() {
        val clipId = stateFlow.value.selectedClipId ?: return
        val clipIdsToDelete = linkedClipIds(stateFlow.value.tracks, clipId)
        // Validate clip exists before saving undo state
        val exists = stateFlow.value.tracks.any { it.clips.any { c -> c.id in clipIdsToDelete } }
        if (!exists) return
        if (tracksContainLockedClip(clipIdsToDelete)) {
            showToast("Track is locked")
            return
        }
        saveUndoState("Delete clip")

        stateFlow.update { state ->
            val tracks = state.tracks.map { track ->
                val deletedClips = track.clips
                    .filter { it.id in clipIdsToDelete }
                    .sortedBy { it.timelineStartMs }
                if (deletedClips.isEmpty()) return@map track

                val updatedClips = track.clips
                    .filterNot { it.id in clipIdsToDelete }
                    .map { clip ->
                        val removedDurationBeforeClip = deletedClips
                            .filter { deleted -> deleted.timelineStartMs < clip.timelineStartMs }
                            .sumOf { it.durationMs }
                        if (removedDurationBeforeClip > 0L) {
                            clip.copy(timelineStartMs = clip.timelineStartMs - removedDurationBeforeClip)
                        } else {
                            clip
                        }
                    }
                track.copy(clips = updatedClips)
            }
            recalculateDuration(state.copy(
                tracks = tracks,
                selectedClipId = null,
                selectedTrackId = null,
                selectedClipIds = emptySet(),
                waveforms = state.waveforms - clipIdsToDelete
            ))
        }
        rebuildPlayerTimeline()
        saveProject()
        registerDeleteForBulkWatcher()
    }

    /**
     * Stamp a delete into the rolling window; if the threshold is crossed
     * inside the window, raise a one-shot banner on state so the UI can
     * offer "Undo" without forcing the user to hunt for the overflow menu.
     * Each emission gets a fresh nonce so a second burst (e.g. user keeps
     * deleting past the banner) re-shows instead of being deduped by
     * Compose's structural equality check.
     */
    private fun registerDeleteForBulkWatcher() {
        val now = System.currentTimeMillis()
        val cutoff = now - bulkDeleteWindowMs
        while (recentDeletesMs.isNotEmpty() && recentDeletesMs.first() < cutoff) {
            recentDeletesMs.removeFirst()
        }
        recentDeletesMs.addLast(now)
        if (recentDeletesMs.size >= bulkDeleteThreshold) {
            val count = recentDeletesMs.size
            stateFlow.update { state ->
                state.copy(
                    bulkUndoPrompt = BulkUndoPrompt(
                        id = now,
                        count = count,
                        windowMs = bulkDeleteWindowMs
                    )
                )
            }
            // Clear the window after emitting so we don't re-fire on every
            // subsequent delete; a fresh burst has to rebuild the count from
            // zero, which matches the human intent of "warned, paying attention".
            recentDeletesMs.clear()
        }
    }

    // --- Duplicate Clip ---
    fun duplicateSelectedClip() {
        val clipId = stateFlow.value.selectedClipId ?: return
        val duplicateIds = linkedClipIds(stateFlow.value.tracks, clipId)
        // Validate clip exists before saving undo state
        val exists = stateFlow.value.tracks.any { it.clips.any { c -> c.id in duplicateIds } }
        if (!exists) return
        if (tracksContainLockedClip(duplicateIds)) {
            showToast("Track is locked")
            return
        }
        saveUndoState("Duplicate clip")

        val newIdsByOldId = duplicateIds.associateWith { UUID.randomUUID().toString() }
        val selectedDuplicateId = newIdsByOldId[clipId] ?: return

        stateFlow.update { s ->
            val tracks = s.tracks.map { track ->
                val clipIndex = track.clips.indexOfFirst { it.id in duplicateIds }
                if (clipIndex < 0) return@map track

                val clip = track.clips[clipIndex]
                val newClip = duplicateClip(
                    clip = clip,
                    newId = newIdsByOldId.getValue(clip.id),
                    linkedClipId = clip.linkedClipId?.let { newIdsByOldId[it] }
                )
                val updatedClips = track.clips.toMutableList().apply { add(clipIndex + 1, newClip) }
                val shifted = updatedClips.mapIndexed { i, candidate ->
                    if (i > clipIndex + 1) {
                        candidate.copy(timelineStartMs = candidate.timelineStartMs + newClip.durationMs)
                    } else {
                        candidate
                    }
                }
                track.copy(clips = shifted)
            }
            val selectedTrackId = tracks.firstOrNull { track ->
                track.clips.any { it.id == selectedDuplicateId }
            }?.id
            val waveforms = newIdsByOldId.entries.fold(s.waveforms) { acc, (oldId, newId) ->
                val existing = acc[oldId]
                if (existing != null) {
                    acc + (newId to existing)
                } else {
                    acc
                }
            }
            recalculateDuration(
                s.copy(
                    tracks = tracks,
                    selectedClipId = selectedDuplicateId,
                    selectedTrackId = selectedTrackId,
                    selectedClipIds = setOf(selectedDuplicateId),
                    waveforms = waveforms
                )
            )
        }
        rebuildPlayerTimeline()
        saveProject()
        showToast("Clip duplicated")
    }

    // --- Merge Clips ---
    fun mergeWithNextClip() {
        val clipId = stateFlow.value.selectedClipId ?: return

        // Validate merge is possible before saving undo state
        val state = stateFlow.value
        val primaryLocation = state.tracks.findClipLocation(clipId) ?: return
        val linkedLocation = primaryLocation.clip.linkedClipId?.let { linkedId ->
            state.tracks.findClipLocation(linkedId)
        }
        if (tracksContainLockedClip(linkedClipIds(state.tracks, clipId))) {
            showToast("Track is locked")
            return
        }
        val vTrack = primaryLocation.track
        val vClipIndex = primaryLocation.clipIndex
        if (vClipIndex >= vTrack.clips.lastIndex) {
            showToast("No next clip to merge")
            return
        }
        val vClip = primaryLocation.clip
        val vNextClip = vTrack.clips[vClipIndex + 1]
        if (!canMergeAdjacentClips(vClip, vNextClip)) {
            showToast("Clips must come from the same source and touch end-to-end")
            return
        }

        linkedLocation?.let { linked ->
            if (linked.clipIndex >= linked.track.clips.lastIndex) {
                showToast("Linked audio is not ready to merge")
                return
            }
            val linkedNextClip = linked.track.clips[linked.clipIndex + 1]
            if (vNextClip.linkedClipId != linkedNextClip.id || !canMergeAdjacentClips(linked.clip, linkedNextClip)) {
                showToast("Linked audio is out of sync")
                return
            }
        }

        saveUndoState("Merge clips")

        stateFlow.update { s ->
            val tracks = s.tracks.map { track ->
                when {
                    track.clips.any { it.id == clipId } -> mergeClipWithNext(track, clipId)
                    linkedLocation != null && track.clips.any { it.id == linkedLocation.clip.id } -> {
                        mergeClipWithNext(track, linkedLocation.clip.id)
                    }
                    else -> track
                }
            }
            val removedClipIds = buildSet {
                add(vNextClip.id)
                linkedLocation?.track?.clips?.getOrNull(linkedLocation.clipIndex + 1)?.id?.let(::add)
            }
            recalculateDuration(
                s.copy(
                    tracks = tracks,
                    waveforms = s.waveforms - removedClipIds
                )
            )
        }
        rebuildPlayerTimeline()
        saveProject()
        showToast("Clips merged")
    }

    // --- Split Clip ---
    fun splitClipAtPlayhead() {
        val state = stateFlow.value
        val playhead = state.playheadMs
        val selectedIds = state.selectedClipIds.ifEmpty {
            setOfNotNull(state.selectedClipId ?: clipAtPlayhead(state, playhead))
        }
        if (selectedIds.isEmpty()) return

        val splitIds = selectedIds
            .flatMap { linkedClipIds(state.tracks, it) }
            .toSet()
        if (tracksContainLockedClip(splitIds)) {
            showToast("Track is locked")
            return
        }
        val splitCandidates = splitIds.mapNotNull { candidateId ->
            state.tracks.findClipLocation(candidateId)
        }.filter { location ->
            playhead > location.clip.timelineStartMs &&
                playhead < location.clip.timelineEndMs &&
                canSplitClipAt(location.clip, playhead)
        }
        if (splitCandidates.isEmpty()) {
            showToast("Clip too short to split here")
            return
        }

        saveUndoState("Split clip")
        val newIdsByOldId = splitCandidates.associate { it.clip.id to UUID.randomUUID().toString() }
        val fallbackSelectedId = state.selectedClipId ?: selectedIds.firstOrNull()

        stateFlow.update { s ->
            val tracks = s.tracks.map { track ->
                if (track.clips.none { it.id in newIdsByOldId }) return@map track
                val updatedClips = buildList {
                    track.clips.forEach { clip ->
                        val newId = newIdsByOldId[clip.id]
                        if (newId == null || !canSplitClipAt(clip, playhead)) {
                            add(clip)
                        } else {
                            val splitPointInSource = splitPointInSource(clip, playhead)
                            // Remap the speedCurve (if any) so each half gets the
                            // correct sub-range of the parent curve. Without this
                            // both halves would inherit the full parent curve and
                            // misreport speeds across the new trim ranges.
                            val parentTrimRange = (clip.trimEndMs - clip.trimStartMs)
                                .coerceAtLeast(1L)
                            val splitFraction = ((splitPointInSource - clip.trimStartMs)
                                .toFloat() / parentTrimRange.toFloat())
                                .coerceIn(0f, 1f)
                            val firstHalfCurve = clip.speedCurve?.restrictTo(
                                0f, splitFraction, parentTrimRange
                            )
                            val secondHalfCurve = clip.speedCurve?.restrictTo(
                                splitFraction, 1f, parentTrimRange
                            )
                            add(
                                clip.copy(
                                    trimEndMs = splitPointInSource,
                                    transition = null,
                                    linkedClipId = clip.linkedClipId,
                                    speedCurve = firstHalfCurve
                                )
                            )
                            add(
                                clip.copy(
                                    id = newId,
                                    timelineStartMs = playhead,
                                    trimStartMs = splitPointInSource,
                                    transition = null,
                                    linkedClipId = clip.linkedClipId?.let { linkedId ->
                                        newIdsByOldId[linkedId]
                                    },
                                    speedCurve = secondHalfCurve
                                )
                            )
                        }
                    }
                }
                track.copy(clips = updatedClips)
            }
            val selectedClipId = fallbackSelectedId?.let { originalId ->
                newIdsByOldId[originalId] ?: originalId
            } ?: s.selectedClipId
            val selectedTrackId = selectedClipId?.let { newClipId ->
                tracks.firstOrNull { track -> track.clips.any { it.id == newClipId } }?.id
            }
            recalculateDuration(
                s.copy(
                    tracks = tracks,
                    selectedClipId = selectedClipId,
                    selectedTrackId = selectedTrackId,
                    selectedClipIds = selectedClipId?.let { setOf(it) } ?: s.selectedClipIds
                )
            )
        }
        rebuildPlayerTimeline()
        saveProject()
        showToast(if (splitCandidates.size > 1) "Clips split" else "Clip split")
    }

    // --- Trim ---
    fun beginTrim() {
        val selectedClipId = stateFlow.value.selectedClipId ?: return
        val targetIds = linkedClipIds(stateFlow.value.tracks, selectedClipId)
        if (tracksContainLockedClip(targetIds)) {
            showToast("Track is locked")
            return
        }
        saveUndoState("Trim clip")
        videoEngine.setScrubbingMode(true)
    }

    fun trimClip(clipId: String, newTrimStartMs: Long? = null, newTrimEndMs: Long? = null) {
        val targetIds = linkedClipIds(stateFlow.value.tracks, clipId)
        if (tracksContainLockedClip(targetIds)) return
        stateFlow.update { state ->
            val tracks = state.tracks.map { track ->
                val targetClipId = track.clips.firstOrNull { it.id in targetIds }?.id
                if (targetClipId == null) {
                    track
                } else {
                    trimClipOnTrack(
                        track = track,
                        clipId = targetClipId,
                        requestedTrimStartMs = newTrimStartMs,
                        requestedTrimEndMs = newTrimEndMs
                    )
                }
            }
            recalculateDuration(state.copy(tracks = tracks))
        }
        rebuildPlayerTimeline()
    }

    fun endTrim() {
        videoEngine.setScrubbingMode(false)
        rebuildPlayerTimeline()
        saveProject()
    }

    // --- Speed ---
    fun beginSpeedChange() {
        saveUndoState("Change speed")
    }

    fun setClipSpeed(clipId: String, speed: Float) {
        stateFlow.update { state ->
            val tracks = state.tracks.map { track ->
                track.copy(clips = track.clips.map { clip ->
                    if (clip.id == clipId) clip.copy(speed = speed.coerceIn(0.1f, 100f))
                    else clip
                })
            }
            recalculateDuration(state.copy(tracks = tracks))
        }
        // Apply speed to preview immediately (don't rebuild full timeline for smooth slider)
        videoEngine.setPreviewSpeed(speed.coerceIn(0.1f, 100f))
    }

    fun endSpeedChange() {
        rebuildPlayerTimeline()
        saveProject()
    }

    // --- Reorder ---
    fun reorderClip(clipId: String, targetIndex: Int) {
        saveUndoState("Reorder clip")
        stateFlow.update { state ->
            val tracks = state.tracks.map { track ->
                val clipIndex = track.clips.indexOfFirst { it.id == clipId }
                if (clipIndex < 0) return@map track
                val mutableClips = track.clips.toMutableList()
                val clip = mutableClips.removeAt(clipIndex)
                val insertAt = targetIndex.coerceIn(0, mutableClips.size)
                mutableClips.add(insertAt, clip)
                // Recalculate timeline positions sequentially
                var currentStartMs = 0L
                val repositioned = mutableClips.map { c ->
                    val updated = c.copy(timelineStartMs = currentStartMs)
                    currentStartMs += c.durationMs
                    updated
                }
                track.copy(clips = repositioned)
            }
            recalculateDuration(state.copy(tracks = tracks))
        }
        rebuildPlayerTimeline()
        saveProject()
    }

    private fun tracksContainLockedClip(clipIds: Set<String>): Boolean {
        return stateFlow.value.tracks.any { track ->
            track.isLocked && track.clips.any { it.id in clipIds }
        }
    }

    private fun duplicateClip(
        clip: Clip,
        newId: String,
        linkedClipId: String?
    ): Clip {
        return clip.copy(
            id = newId,
            timelineStartMs = clip.timelineEndMs,
            effects = clip.effects.map { it.copy(id = UUID.randomUUID().toString()) },
            transition = null,
            linkedClipId = linkedClipId
        )
    }

    private fun clipAtPlayhead(state: EditorState, playheadMs: Long): String? {
        val selectedTrackId = state.selectedTrackId
        if (selectedTrackId != null) {
            state.tracks
                .firstOrNull { it.id == selectedTrackId }
                ?.clips
                ?.firstOrNull { playheadMs in it.timelineStartMs until it.timelineEndMs }
                ?.let { return it.id }
        }
        return state.tracks
            .sortedBy { it.index }
            .flatMap { it.clips.sortedBy { clip -> clip.timelineStartMs } }
            .firstOrNull { playheadMs in it.timelineStartMs until it.timelineEndMs }
            ?.id
    }

    private fun canSplitClipAt(clip: Clip, playheadMs: Long): Boolean {
        if (playheadMs <= clip.timelineStartMs || playheadMs >= clip.timelineEndMs) return false
        val splitPoint = splitPointInSource(clip, playheadMs)
        return splitPoint - clip.trimStartMs >= MIN_TIMELINE_CLIP_DURATION_MS &&
            clip.trimEndMs - splitPoint >= MIN_TIMELINE_CLIP_DURATION_MS
    }

    private fun splitPointInSource(clip: Clip, playheadMs: Long): Long {
        // Use the speed-curve-aware reverse mapping so a clip with a ramp
        // (e.g. 0.5x → 2x) splits at the correct source frame instead of at
        // `trimStart + relative * constant_speed`, which would cut at the
        // wrong frame on any non-constant curve.
        val relativePosition = (playheadMs - clip.timelineStartMs).coerceAtLeast(0L)
        return clip.timelineOffsetToSourceMs(relativePosition)
    }

    private fun mergeClipWithNext(track: Track, clipId: String): Track {
        val clipIndex = track.clips.indexOfFirst { it.id == clipId }
        if (clipIndex < 0 || clipIndex >= track.clips.lastIndex) return track
        val clip = track.clips[clipIndex]
        val nextClip = track.clips[clipIndex + 1]
        if (!canMergeAdjacentClips(clip, nextClip)) return track

        val merged = clip.copy(
            trimEndMs = nextClip.trimEndMs,
            effects = clip.effects + nextClip.effects.map { it.copy(id = UUID.randomUUID().toString()) }
        )
        val updatedClips = track.clips.toMutableList().apply {
            removeAt(clipIndex + 1)
            set(clipIndex, merged)
        }
        val shifted = updatedClips.mapIndexed { index, candidate ->
            if (index > clipIndex) {
                candidate.copy(timelineStartMs = candidate.timelineStartMs - nextClip.durationMs)
            } else {
                candidate
            }
        }
        return track.copy(clips = shifted)
    }

    // --- Move Clip to Track ---
    fun moveClipToTrack(clipId: String, targetTrackId: String) {
        val state = stateFlow.value
        val sourceTrack = state.tracks.firstOrNull { track -> track.clips.any { it.id == clipId } }
        val movedClip = sourceTrack?.clips?.firstOrNull { it.id == clipId }
        val targetTrack = state.tracks.firstOrNull { it.id == targetTrackId }

        if (movedClip == null || targetTrack == null) {
            showToast("Could not move clip")
            return
        }

        if (sourceTrack.id == targetTrackId) {
            showToast("Clip is already on that track")
            return
        }

        val clipHasVisual = videoEngine.hasVisualTrack(movedClip.sourceUri)
        val clipHasAudio = videoEngine.hasAudioTrack(movedClip.sourceUri)
        val incompatibilityMessage = when (targetTrack.type) {
            TrackType.AUDIO -> {
                if (!clipHasAudio) {
                    "Only clips with audio can go on audio tracks"
                } else {
                    null
                }
            }
            TrackType.VIDEO, TrackType.OVERLAY -> {
                if (!clipHasVisual) {
                    "Only photo or video clips can go on visual tracks"
                } else {
                    null
                }
            }
            else -> "Clips can't be moved to this track type"
        }

        if (incompatibilityMessage != null) {
            showToast(incompatibilityMessage)
            return
        }

        saveUndoState("Move clip to track")
        stateFlow.update { state ->
            val tracksWithRemoved = state.tracks.map { track ->
                if (track.id == sourceTrack.id) {
                    track.copy(clips = track.clips.filter { it.id != clipId })
                } else track
            }
            val tracks = tracksWithRemoved.map { track ->
                if (track.id == targetTrackId) {
                    val endMs = track.clips.maxOfOrNull { it.timelineEndMs } ?: 0L
                    track.copy(clips = track.clips + movedClip.copy(timelineStartMs = endMs))
                } else track
            }
            recalculateDuration(state.copy(tracks = tracks))
        }
        rebuildPlayerTimeline()
        saveProject()
        showToast("Clip moved to track")
    }

    // --- Reverse ---
    fun setClipReversed(clipId: String, reversed: Boolean) {
        saveUndoState("Reverse clip")
        stateFlow.update { state ->
            val tracks = state.tracks.map { track ->
                track.copy(clips = track.clips.map { clip ->
                    if (clip.id == clipId) clip.copy(isReversed = reversed)
                    else clip
                })
            }
            recalculateDuration(state.copy(tracks = tracks))
        }
        rebuildPlayerTimeline()
        saveProject()
    }

}

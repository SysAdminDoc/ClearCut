package com.novacut.editor.ui.editor

import android.content.Context
import android.net.Uri
import android.util.Log
import com.novacut.editor.R
import com.novacut.editor.engine.MediaImportEngine
import com.novacut.editor.engine.VideoEngine
import com.novacut.editor.model.Clip
import com.novacut.editor.model.SourceColorMetadata
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
    private val mediaImportEngine: MediaImportEngine,
    private val appContext: Context,
    private val scope: CoroutineScope,
    private val saveUndoState: (String) -> Unit,
    private val showToast: (String) -> Unit,
    private val rebuildPlayerTimeline: () -> Unit,
    private val saveProject: () -> Unit,
    private val updatePreview: () -> Unit,
    private val seekPreviewTo: (Long) -> Unit,
    private val currentPlayheadMs: () -> Long,
    private val updateLivePlayheadMs: (Long) -> Unit,
    private val recalculateDuration: (EditorState) -> EditorState,
    private val onClipAdded: ((clipId: String, uri: Uri) -> Unit)? = null
) {
    private fun text(resId: Int, vararg args: Any): String =
        appContext.getString(resId, *args)

    private data class ImportedMediaInfo(
        val durationMs: Long,
        val hasVisualTrack: Boolean,
        val hasAudioTrack: Boolean,
        val sourceColorMetadata: SourceColorMetadata
    )

    // Rolling timestamps of recent delete operations for the bulk-change
    // detector. Bounded to the window length so the structure can't grow.
    // Accessed only from the delegate's own methods, which in turn are only
    // called from the Main thread by EditorViewModel — no locking required.
    private val recentDeletesMs = ArrayDeque<Long>()
    private val bulkDeleteWindowMs = 10_000L
    private val bulkDeleteThreshold = 3
    private var lastTrimPreviewSeekMs: Long? = null
    // --- Add Clip ---
    fun addClipToTrack(uri: Uri, trackType: TrackType = TrackType.VIDEO) {
        scope.launch {
            val mediaInfo = try {
                withContext(Dispatchers.IO) {
                    ImportedMediaInfo(
                        durationMs = videoEngine.getMediaDuration(uri),
                        hasVisualTrack = videoEngine.hasVisualTrack(uri),
                        hasAudioTrack = videoEngine.hasAudioTrack(uri),
                        sourceColorMetadata = mediaImportEngine.inspectSourceColor(uri)
                    )
                }
            } catch (e: Exception) {
                Log.e("ClipEditingDelegate", "Could not read media", e)
                showToast(text(R.string.editor_media_read_failed_toast))
                return@launch
            }
            val (duration, hasVisualTrack, hasAudioTrack, sourceColorMetadata) = mediaInfo
            if (duration <= 0) {
                showToast(text(R.string.editor_media_read_failed_toast))
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
                    linkedClipId = linkedAudioClipId,
                    sourceColorMetadata = sourceColorMetadata
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
                        linkedClipId = clipId,
                        sourceColorMetadata = sourceColorMetadata
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

                recalculateDuration(
                    state.copy(
                        tracks = tracks,
                        selectedClipId = clip.id,
                        selectedTrackId = track.id
                    ).copyPanel { panel ->
                        panel.copy(panels = panel.panels.close(PanelId.MEDIA_PICKER))
                    }
                )
            }

            // Rebuild through the shared path so preview and normalization stay in sync.
            rebuildPlayerTimeline()
            saveProject()

            // Notify ViewModel for proxy registration
            onClipAdded?.invoke(clipId, uri)
        }
    }

    fun relinkMedia(oldUri: Uri, newUri: Uri) {
        scope.launch {
            val mediaInfo = try {
                withContext(Dispatchers.IO) {
                    ImportedMediaInfo(
                        durationMs = videoEngine.getMediaDuration(newUri),
                        hasVisualTrack = videoEngine.hasVisualTrack(newUri),
                        hasAudioTrack = videoEngine.hasAudioTrack(newUri),
                        sourceColorMetadata = mediaImportEngine.inspectSourceColor(newUri)
                    )
                }
            } catch (e: Exception) {
                showToast(appContext.getString(R.string.clip_read_media_failed_toast))
                return@launch
            }
            val (duration, hasVisualTrack, hasAudioTrack, sourceColorMetadata) = mediaInfo
            if (duration <= 0L) {
                showToast(appContext.getString(R.string.clip_read_media_failed_toast))
                return@launch
            }

            val oldUriKey = oldUri.toString()
            val state = stateFlow.value
            val affected = state.tracks.flatMap { track ->
                track.clips
                    .filter { it.sourceUri.toString() == oldUriKey }
                    .map { clip -> track to clip }
            }
            if (affected.isEmpty()) {
                showToast(appContext.getString(R.string.clip_media_unused_toast))
                return@launch
            }

            val affectedClipIds = affected.map { it.second.id }.toSet()
            if (tracksContainLockedClip(affectedClipIds)) {
                showToast(appContext.getString(R.string.clip_track_locked_toast))
                return@launch
            }

            val incompatibilityMessage = affected.firstNotNullOfOrNull { (track, _) ->
                when (track.type) {
                    TrackType.AUDIO -> if (!hasAudioTrack) "Replacement needs an audio track" else null
                    TrackType.VIDEO, TrackType.OVERLAY -> {
                        if (!hasVisualTrack) "Replacement needs a video or image track" else null
                    }
                    else -> "Replacement is not valid for this track type"
                }
            }
            if (incompatibilityMessage != null) {
                showToast(incompatibilityMessage)
                return@launch
            }

            saveUndoState("Relink media")
            stateFlow.update { current ->
                val tracks = current.tracks.map { track ->
                    track.copy(
                        clips = track.clips.map { clip ->
                            if (clip.sourceUri.toString() == oldUriKey) {
                                clip.relinkedTo(newUri, duration, sourceColorMetadata)
                            } else {
                                clip
                            }
                        }
                    )
                }
                recalculateDuration(
                    current.copy(
                        tracks = tracks,
                        waveforms = current.waveforms - affectedClipIds
                    )
                )
            }

            rebuildPlayerTimeline()
            updatePreview()
            saveProject()
            affectedClipIds.forEach { clipId ->
                onClipAdded?.invoke(clipId, newUri)
            }
            showToast(appContext.getString(R.string.clip_relinked_toast, affectedClipIds.size))
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
            val updated = s.copy(selectedClipId = clipId, selectedTrackId = trackId, selectedClipIds = newSelectedIds)
            if (clipId == null) {
                updated.copyPanel { panel ->
                    val clipPanels = setOf(
                        PanelId.EFFECTS, PanelId.TRANSFORM, PanelId.SPEED_CURVE,
                        PanelId.KEYFRAME_EDITOR, PanelId.MASK_EDITOR, PanelId.BLEND_MODE,
                        PanelId.COLOR_GRADING, PanelId.AUDIO, PanelId.TRANSITION_PICKER,
                        PanelId.AI_TOOLS, PanelId.NOISE_REDUCTION, PanelId.CAPTION_EDITOR,
                        PanelId.PIP_PRESETS, PanelId.CHROMA_KEY
                    )
                    panel.copy(
                        panels = PanelVisibility(panel.panels.openPanels - clipPanels),
                        selectedEffectId = null
                    )
                }
            } else updated
        }
        updatePreview()
    }

    // --- Delete Clip ---
    fun deleteSelectedClip() {
        val state = stateFlow.value
        val livePlayheadMs = currentPlayheadMs()
        val selectedIds = state.selectedClipIds.ifEmpty { setOfNotNull(state.selectedClipId) }
        val clipIdsToDelete = expandTimelineEditClipIds(state.tracks, selectedIds)
        if (clipIdsToDelete.isEmpty()) return
        if (tracksContainLockedClip(clipIdsToDelete)) {
            showToast(appContext.getString(R.string.clip_track_locked_toast))
            return
        }
        val markerTrack = state.selectedTrackId
            ?.let { selectedTrackId -> state.tracks.firstOrNull { it.id == selectedTrackId } }
            ?.takeIf { track -> track.clips.any { it.id in clipIdsToDelete } }
            ?: state.tracks.firstOrNull { track -> track.clips.any { it.id in clipIdsToDelete } }
        val markerRippleRanges = markerTrack?.clips
            ?.filter { it.id in clipIdsToDelete }
            ?.map { it.timelineStartMs to it.timelineEndMs }
            .orEmpty()
        val rippledPlayheadMs = ripplePlaybackPosition(livePlayheadMs, markerRippleRanges)
        saveUndoState(
            if (clipIdsToDelete.size == 1) "Delete clip"
            else "Delete ${clipIdsToDelete.size} clips"
        )

        stateFlow.update { state ->
            recalculateDuration(state.copy(
                tracks = rippleDeleteClips(state.tracks, clipIdsToDelete),
                selectedClipId = null,
                selectedTrackId = null,
                selectedClipIds = emptySet(),
                playheadMs = rippledPlayheadMs,
                waveforms = state.waveforms - clipIdsToDelete,
                trackedObjects = state.trackedObjects.filterNot { it.sourceClipId in clipIdsToDelete },
                timelineMarkers = state.timelineMarkers.mapNotNull { marker ->
                    rippleTimelinePosition(marker.timeMs, markerRippleRanges)
                        ?.let { marker.copy(timeMs = it) }
                },
                chapterMarkers = state.chapterMarkers.mapNotNull { marker ->
                    rippleTimelinePosition(marker.timeMs, markerRippleRanges)
                        ?.let { marker.copy(timeMs = it) }
                },
                beatMarkers = state.beatMarkers.mapNotNull { markerMs ->
                    rippleTimelinePosition(markerMs, markerRippleRanges)
                }
            ))
        }
        updateLivePlayheadMs(rippledPlayheadMs)
        rebuildPlayerTimeline()
        saveProject()
        registerDeleteForBulkWatcher()
        if (clipIdsToDelete.size > 1) {
            showToast(text(R.string.vm_multi_deleted_toast, clipIdsToDelete.size))
        }
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
            showToast(appContext.getString(R.string.clip_track_locked_toast))
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
        showToast(appContext.getString(R.string.clip_duplicated_toast))
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
            showToast(appContext.getString(R.string.clip_track_locked_toast))
            return
        }
        val vTrack = primaryLocation.track
        val vClipIndex = primaryLocation.clipIndex
        if (vClipIndex >= vTrack.clips.lastIndex) {
            showToast(appContext.getString(R.string.clip_no_next_merge_toast))
            return
        }
        val vClip = primaryLocation.clip
        val vNextClip = vTrack.clips[vClipIndex + 1]
        if (!canMergeAdjacentClips(vClip, vNextClip)) {
            showToast(appContext.getString(R.string.clip_merge_source_mismatch_toast))
            return
        }

        linkedLocation?.let { linked ->
            if (linked.clipIndex >= linked.track.clips.lastIndex) {
                showToast(appContext.getString(R.string.clip_linked_audio_not_ready_toast))
                return
            }
            val linkedNextClip = linked.track.clips[linked.clipIndex + 1]
            if (vNextClip.linkedClipId != linkedNextClip.id || !canMergeAdjacentClips(linked.clip, linkedNextClip)) {
                showToast(appContext.getString(R.string.clip_linked_audio_out_of_sync_toast))
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
        showToast(appContext.getString(R.string.clip_merged_toast))
    }

    // --- Split Clip ---
    fun splitClipAtPlayhead() {
        val playhead = currentPlayheadMs()
        val state = stateFlow.value
        val selectedIds = state.selectedClipIds.ifEmpty {
            setOfNotNull(state.selectedClipId ?: clipAtPlayhead(state, playhead))
        }
        if (selectedIds.isEmpty()) return

        val splitIds = selectedIds
            .flatMap { linkedClipIds(state.tracks, it) }
            .toSet()
        if (tracksContainLockedClip(splitIds)) {
            showToast(appContext.getString(R.string.clip_track_locked_toast))
            return
        }
        val splitCandidateIds = linkedSplitCandidateIds(state.tracks, selectedIds, playhead)
        val splitCandidates = splitCandidateIds.mapNotNull(state.tracks::findClipLocation)
        if (splitCandidates.isEmpty()) {
            showToast(appContext.getString(R.string.clip_too_short_to_split_toast))
            return
        }
        val regroupedClipIds = regroupedClipIdsForSplit(state.tracks, splitCandidateIds, playhead)
        if (tracksContainLockedClip(splitCandidateIds + regroupedClipIds)) {
            showToast(appContext.getString(R.string.clip_track_locked_toast))
            return
        }

        saveUndoState("Split clip")
        val newIdsByOldId = splitCandidates.associate { it.clip.id to UUID.randomUUID().toString() }
        val newGroupIdsByOldId = splitCandidates
            .mapNotNull { it.clip.groupId }
            .distinct()
            .associateWith { UUID.randomUUID().toString() }
        val newTrackedObjectIdsByClipId = newIdsByOldId.mapValues { (oldClipId, _) ->
            state.trackedObjects
                .filter { it.sourceClipId == oldClipId }
                .associate { it.id to UUID.randomUUID().toString() }
        }
        val splitTrackedObjects = newIdsByOldId.flatMap { (oldClipId, newClipId) ->
            val idMap = newTrackedObjectIdsByClipId[oldClipId].orEmpty()
            state.trackedObjects
                .filter { it.sourceClipId == oldClipId }
                .map { tracked ->
                    tracked.copy(
                        id = idMap.getValue(tracked.id),
                        sourceClipId = newClipId
                    )
                }
        }
        val fallbackSelectedId = state.selectedClipId ?: selectedIds.firstOrNull()

        stateFlow.update { s ->
            val tracks = s.tracks.map { track ->
                val updatedClips = buildList {
                    track.clips.forEach { clip ->
                        val newId = newIdsByOldId[clip.id]
                        if (newId == null) {
                            val rightGroupId = clip.groupId?.let { newGroupIdsByOldId[it] }
                            add(
                                if (rightGroupId != null && clip.timelineStartMs >= playhead) {
                                    clip.copy(groupId = rightGroupId)
                                } else {
                                    clip
                                }
                            )
                        } else {
                            val split = splitTimelineClip(
                                clip = clip,
                                playheadMs = playhead,
                                newClipId = newId,
                                newLinkedClipId = clip.linkedClipId?.let { linkedId ->
                                    newIdsByOldId[linkedId] ?: linkedId
                                },
                                rightGroupId = clip.groupId?.let { newGroupIdsByOldId[it] },
                                rightTrackedObjectIds = newTrackedObjectIdsByClipId[clip.id].orEmpty(),
                                idFactory = { UUID.randomUUID().toString() }
                            )
                            if (split == null) add(clip) else {
                                add(split.left)
                                add(split.right)
                            }
                        }
                    }
                }
                track.copy(clips = updatedClips)
            }
            val selectedClipId = fallbackSelectedId?.let { originalId ->
                newIdsByOldId[originalId] ?: originalId
            } ?: s.selectedClipId
            val selectedClipIds = selectedIds.mapNotNull { originalId ->
                val resolvedId = newIdsByOldId[originalId] ?: originalId
                resolvedId.takeIf { id -> tracks.any { track -> track.clips.any { it.id == id } } }
            }.toSet()
            val selectedTrackId = selectedClipId?.let { newClipId ->
                tracks.firstOrNull { track -> track.clips.any { it.id == newClipId } }?.id
            }
            val waveforms = newIdsByOldId.entries.fold(s.waveforms) { acc, (oldId, newId) ->
                acc[oldId]?.let { waveform -> acc + (newId to waveform) } ?: acc
            }
            recalculateDuration(
                s.copy(
                    tracks = tracks,
                    playheadMs = playhead,
                    selectedClipId = selectedClipId,
                    selectedTrackId = selectedTrackId,
                    selectedClipIds = selectedClipIds,
                    waveforms = waveforms,
                    trackedObjects = s.trackedObjects + splitTrackedObjects
                )
            )
        }
        // A split only partitions metadata; it does not change the rendered
        // source sequence. Keep the already-prepared player timeline so Play
        // can continue immediately instead of rebuffering every new half.
        saveProject()
        showToast(if (splitCandidates.size > 1) "Clips split" else "Clip split")
    }

    // --- Trim ---
    fun beginTrim() {
        val selectedClipId = stateFlow.value.selectedClipId ?: return
        val targetIds = linkedClipIds(stateFlow.value.tracks, selectedClipId)
        if (tracksContainLockedClip(targetIds)) {
            showToast(appContext.getString(R.string.clip_track_locked_toast))
            return
        }
        saveUndoState("Trim clip")
        // Trim drags need live frame feedback at the in/out boundary. ExoPlayer's
        // scrubbing optimization can hold the surface on a loading frame here, so
        // leave normal decode enabled and avoid only the expensive timeline rebuild.
        videoEngine.setScrubbingMode(false)
    }

    fun trimClip(clipId: String, newTrimStartMs: Long? = null, newTrimEndMs: Long? = null) {
        val targetIds = linkedClipIds(stateFlow.value.tracks, clipId)
        if (tracksContainLockedClip(targetIds)) return
        var previewSeekMs: Long? = null
        stateFlow.update { state ->
            val tracks = trimLinkedClipsOnTimeline(
                tracks = state.tracks,
                anchorClipId = clipId,
                targetClipIds = targetIds,
                requestedTrimStartMs = newTrimStartMs,
                requestedTrimEndMs = newTrimEndMs
            )
            val updatedState = recalculateDuration(state.copy(tracks = tracks))
            val previewClip = updatedState.tracks
                .asSequence()
                .flatMap { it.clips.asSequence() }
                .firstOrNull { it.id == clipId }
            val frameStepMs = (1000L / updatedState.project.frameRate.coerceAtLeast(1))
                .coerceAtLeast(1L)
            previewSeekMs = when {
                previewClip == null -> null
                newTrimStartMs != null -> previewClip.timelineStartMs
                newTrimEndMs != null -> (previewClip.timelineEndMs - frameStepMs)
                    .coerceAtLeast(previewClip.timelineStartMs)
                else -> null
            }?.coerceIn(0L, updatedState.totalDurationMs.coerceAtLeast(0L))
            updatedState
        }
        lastTrimPreviewSeekMs = previewSeekMs
        previewSeekMs?.let(seekPreviewTo)
        // rebuildPlayerTimeline() moved to endTrim() — trim fires at touch-event
        // rate during drag, and rebuilding ExoPlayer's MediaItem set on every
        // tick was the primary source of timeline clunkiness. beginTrim already
        // keeps the preview pinned to the in/out frame without rebuilding the
        // player timeline every tick.
    }

    fun endTrim() {
        videoEngine.setScrubbingMode(false)
        rebuildPlayerTimeline()
        lastTrimPreviewSeekMs?.let(seekPreviewTo)
        lastTrimPreviewSeekMs = null
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
            headTransition = null,
            tailTransition = null,
            linkedClipId = linkedClipId
        )
    }

    private fun Clip.relinkedTo(
        newUri: Uri,
        sourceDurationMs: Long,
        sourceColorMetadata: SourceColorMetadata
    ): Clip {
        if (sourceDurationMs <= 0L) return copy(sourceUri = newUri, sourceDurationMs = sourceDurationMs, sourceColorMetadata = sourceColorMetadata)
        val safeTrimStart = trimStartMs.coerceIn(0L, sourceDurationMs - 1L)
        val safeTrimEnd = trimEndMs.coerceIn(safeTrimStart + 1L, sourceDurationMs)
        return copy(
            sourceUri = newUri,
            sourceDurationMs = sourceDurationMs,
            trimStartMs = safeTrimStart,
            trimEndMs = safeTrimEnd,
            proxyUri = null,
            sourceColorMetadata = sourceColorMetadata
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

    private fun splitPointInSource(clip: Clip, playheadMs: Long): Long {
        // Use the speed-curve-aware reverse mapping so a clip with a ramp
        // (e.g. 0.5x → 2x) splits at the correct source frame instead of at
        // `trimStart + relative * constant_speed`, which would cut at the
        // wrong frame on any non-constant curve.
        val relativePosition = (playheadMs - clip.timelineStartMs).coerceAtLeast(0L)
        return clip.timelineOffsetToSourceMs(relativePosition)
    }

    private fun mergeClipWithNext(track: Track, clipId: String): Track {
        return mergeAdjacentClipPair(track, clipId)
    }

    // --- Move Clip to Track ---
    fun moveClipToTrack(clipId: String, targetTrackId: String) {
        val state = stateFlow.value
        val sourceTrack = state.tracks.firstOrNull { track -> track.clips.any { it.id == clipId } }
        val movedClip = sourceTrack?.clips?.firstOrNull { it.id == clipId }
        val targetTrack = state.tracks.firstOrNull { it.id == targetTrackId }

        if (movedClip == null || targetTrack == null) {
            showToast(appContext.getString(R.string.clip_move_failed_toast))
            return
        }

        if (sourceTrack.id == targetTrackId) {
            showToast(appContext.getString(R.string.clip_already_on_track_toast))
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
            val freshClip = state.tracks.flatMap { it.clips }.firstOrNull { it.id == clipId }
                ?: return@update state
            val tracksWithRemoved = state.tracks.map { track ->
                if (track.clips.any { it.id == clipId }) {
                    track.copy(clips = track.clips.filter { it.id != clipId })
                } else track
            }
            val tracks = tracksWithRemoved.map { track ->
                if (track.id == targetTrackId) {
                    val endMs = track.clips.maxOfOrNull { it.timelineEndMs } ?: 0L
                    track.copy(clips = track.clips + freshClip.copy(timelineStartMs = endMs))
                } else track
            }
            recalculateDuration(state.copy(tracks = tracks))
        }
        rebuildPlayerTimeline()
        saveProject()
        showToast(appContext.getString(R.string.clip_moved_toast))
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

internal fun mergeAdjacentClipPair(track: Track, clipId: String): Track {
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
    // The merged clip still occupies both original clips' timeline span, so
    // every later clip keeps its existing start position.
    return track.copy(clips = updatedClips)
}

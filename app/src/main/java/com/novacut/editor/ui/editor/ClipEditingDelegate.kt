package com.novacut.editor.ui.editor

import android.net.Uri
import com.novacut.editor.engine.AudioEngine
import com.novacut.editor.engine.VideoEngine
import com.novacut.editor.model.Clip
import com.novacut.editor.model.TrackType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
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
    private val audioEngine: AudioEngine,
    private val scope: CoroutineScope,
    private val saveUndoState: (String) -> Unit,
    private val showToast: (String) -> Unit,
    private val rebuildPlayerTimeline: () -> Unit,
    private val saveProject: () -> Unit,
    private val updatePreview: () -> Unit,
    private val recalculateDuration: (EditorState) -> EditorState,
    private val onClipAdded: ((clipId: String, uri: Uri) -> Unit)? = null
) {
    // --- Add Clip ---
    fun addClipToTrack(uri: Uri, trackType: TrackType = TrackType.VIDEO) {
        scope.launch {
            val duration = try {
                withContext(Dispatchers.IO) {
                    videoEngine.getVideoDuration(uri)
                }
            } catch (e: Exception) {
                showToast("Could not read media: ${e.message ?: "Unknown error"}")
                return@launch
            }
            if (duration <= 0) {
                showToast("Could not read media file")
                return@launch
            }

            saveUndoState("Add clip")

            // Create clip ID outside state update so we can reference it for waveform
            val clipId = UUID.randomUUID().toString()

            stateFlow.update { state ->
                val trackIndex = state.tracks.indexOfFirst { it.type == trackType }
                if (trackIndex < 0) return@update state

                val track = state.tracks[trackIndex]
                val timelineStart = track.clips.maxOfOrNull { it.timelineEndMs } ?: 0L

                val clip = Clip(
                    id = clipId,
                    sourceUri = uri,
                    sourceDurationMs = duration,
                    timelineStartMs = timelineStart,
                    trimStartMs = 0L,
                    trimEndMs = duration
                )

                val tracks = state.tracks.mapIndexed { i, t ->
                    if (i == trackIndex) t.copy(clips = t.clips + clip) else t
                }

                val totalDuration = tracks.maxOfOrNull { t ->
                    t.clips.maxOfOrNull { it.timelineEndMs } ?: 0L
                } ?: 0L

                state.copy(
                    tracks = tracks,
                    totalDurationMs = totalDuration,
                    selectedClipId = clip.id,
                    selectedTrackId = track.id,
                    panels = state.panels.close(PanelId.MEDIA_PICKER)
                )
            }

            // Rebuild player timeline with all clips
            videoEngine.prepareTimeline(stateFlow.value.tracks)
            saveProject()

            // Notify ViewModel for proxy registration
            onClipAdded?.invoke(clipId, uri)

            // Extract waveform for audio visualization using the known clip ID
            scope.launch {
                val waveform = audioEngine.extractWaveform(uri)
                stateFlow.update { it.copy(waveforms = it.waveforms + (clipId to waveform)) }
            }
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
        // Validate clip exists before saving undo state
        val exists = stateFlow.value.tracks.any { it.clips.any { c -> c.id == clipId } }
        if (!exists) return
        saveUndoState("Delete clip")

        stateFlow.update { state ->
            val tracks = state.tracks.map { track ->
                val clipIndex = track.clips.indexOfFirst { it.id == clipId }
                if (clipIndex < 0) return@map track

                val deletedClip = track.clips[clipIndex]
                val gapMs = deletedClip.durationMs

                // Ripple delete: shift subsequent clips back to close the gap
                val updatedClips = track.clips
                    .filterNot { it.id == clipId }
                    .map { clip ->
                        if (clip.timelineStartMs > deletedClip.timelineStartMs) {
                            clip.copy(timelineStartMs = clip.timelineStartMs - gapMs)
                        } else clip
                    }
                track.copy(clips = updatedClips)
            }
            val totalDuration = tracks.maxOfOrNull { t ->
                t.clips.maxOfOrNull { it.timelineEndMs } ?: 0L
            } ?: 0L

            state.copy(
                tracks = tracks,
                totalDurationMs = totalDuration,
                selectedClipId = null,
                selectedTrackId = null,
                selectedClipIds = emptySet(),
                waveforms = state.waveforms - clipId
            )
        }
        rebuildPlayerTimeline()
        saveProject()
    }

    // --- Duplicate Clip ---
    fun duplicateSelectedClip() {
        val clipId = stateFlow.value.selectedClipId ?: return
        // Validate clip exists before saving undo state
        val exists = stateFlow.value.tracks.any { it.clips.any { c -> c.id == clipId } }
        if (!exists) return
        saveUndoState("Duplicate clip")

        stateFlow.update { s ->
            val trackAndClip = s.tracks.flatMapIndexed { idx, track ->
                track.clips.filter { it.id == clipId }.map { idx to it }
            }.firstOrNull() ?: return@update s

            val (trackIdx, clip) = trackAndClip
            val newClip = clip.copy(
                id = UUID.randomUUID().toString(),
                timelineStartMs = clip.timelineEndMs,
                effects = clip.effects.map { it.copy(id = UUID.randomUUID().toString()) },
                transition = null
            )

            val track = s.tracks[trackIdx]
            val clipIndex = track.clips.indexOfFirst { it.id == clipId }
            val updatedClips = track.clips.toMutableList().apply { add(clipIndex + 1, newClip) }

            // Shift subsequent clips forward
            val shifted = updatedClips.mapIndexed { i, c ->
                if (i > clipIndex + 1) c.copy(timelineStartMs = c.timelineStartMs + newClip.durationMs) else c
            }

            val tracks = s.tracks.mapIndexed { i, t -> if (i == trackIdx) t.copy(clips = shifted) else t }
            recalculateDuration(s.copy(tracks = tracks, selectedClipId = newClip.id))
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
        val trackAndClipInfo = state.tracks.flatMapIndexed { idx, track ->
            track.clips.filter { it.id == clipId }.map { idx to it }
        }.firstOrNull()
        if (trackAndClipInfo == null) return
        val (vTrackIdx, vClip) = trackAndClipInfo
        val vTrack = state.tracks[vTrackIdx]
        val vClipIndex = vTrack.clips.indexOfFirst { it.id == clipId }
        if (vClipIndex >= vTrack.clips.size - 1) {
            showToast("No next clip to merge")
            return
        }
        val vNextClip = vTrack.clips[vClipIndex + 1]
        if (vClip.sourceUri != vNextClip.sourceUri) {
            showToast("Can only merge clips from the same source")
            return
        }
        if (vClip.trimEndMs != vNextClip.trimStartMs) {
            showToast("Clips must have adjacent trim ranges to merge")
            return
        }

        saveUndoState("Merge clips")

        stateFlow.update { s ->
            val trackAndClip = s.tracks.flatMapIndexed { idx, track ->
                track.clips.filter { it.id == clipId }.map { idx to it }
            }.firstOrNull() ?: return@update s

            val (trackIdx, clip) = trackAndClip
            val track = s.tracks[trackIdx]
            val clipIndex = track.clips.indexOfFirst { it.id == clipId }

            if (clipIndex >= track.clips.size - 1) return@update s
            val nextClip = track.clips[clipIndex + 1]
            if (clip.sourceUri != nextClip.sourceUri) return@update s

            val merged = clip.copy(
                trimEndMs = nextClip.trimEndMs,
                effects = clip.effects + nextClip.effects.map { it.copy(id = UUID.randomUUID().toString()) }
            )

            val updatedClips = track.clips.toMutableList().apply {
                removeAt(clipIndex + 1)
                set(clipIndex, merged)
            }

            // Shift subsequent clips back
            val nextDuration = nextClip.durationMs
            val shifted = updatedClips.mapIndexed { i, c ->
                if (i > clipIndex) c.copy(timelineStartMs = c.timelineStartMs - nextDuration) else c
            }

            val tracks = s.tracks.mapIndexed { i, t -> if (i == trackIdx) t.copy(clips = shifted) else t }
            recalculateDuration(s.copy(tracks = tracks))
        }
        rebuildPlayerTimeline()
        saveProject()
        showToast("Clips merged")
    }

    // --- Split Clip ---
    fun splitClipAtPlayhead() {
        val state = stateFlow.value
        val clipId = state.selectedClipId ?: return
        val playhead = state.playheadMs

        // Validate split is possible before saving undo state
        val splitClip = state.tracks.flatMap { it.clips }.firstOrNull { it.id == clipId }
        if (splitClip == null || playhead <= splitClip.timelineStartMs || playhead >= splitClip.timelineEndMs) return
        // Ensure both halves meet minimum duration (100ms)
        val relPos = playhead - splitClip.timelineStartMs
        val srcSplit = splitClip.trimStartMs + (relPos * splitClip.speed).toLong()
        if (srcSplit - splitClip.trimStartMs < 100L || splitClip.trimEndMs - srcSplit < 100L) {
            showToast("Clip too short to split here")
            return
        }

        saveUndoState("Split clip")

        stateFlow.update { s ->
            val tracks = s.tracks.map { track ->
                val clipIndex = track.clips.indexOfFirst { it.id == clipId }
                if (clipIndex < 0) return@map track

                val clip = track.clips[clipIndex]
                if (playhead <= clip.timelineStartMs || playhead >= clip.timelineEndMs) return@map track

                val relativePosition = playhead - clip.timelineStartMs
                val splitPointInSource = clip.trimStartMs + (relativePosition * clip.speed).toLong()

                val firstHalf = clip.copy(
                    trimEndMs = splitPointInSource
                )
                val secondHalf = clip.copy(
                    id = UUID.randomUUID().toString(),
                    timelineStartMs = playhead,
                    trimStartMs = splitPointInSource
                )

                val updatedClips = buildList {
                    addAll(track.clips.subList(0, clipIndex))
                    add(firstHalf)
                    add(secondHalf)
                    addAll(track.clips.subList(clipIndex + 1, track.clips.size))
                }
                track.copy(clips = updatedClips)
            }
            recalculateDuration(s.copy(tracks = tracks))
        }
        rebuildPlayerTimeline()
        saveProject()
        showToast("Clip split")
    }

    // --- Trim ---
    fun beginTrim() {
        saveUndoState("Trim clip")
        videoEngine.setScrubbingMode(true)
    }

    fun trimClip(clipId: String, newTrimStartMs: Long? = null, newTrimEndMs: Long? = null) {
        stateFlow.update { state ->
            val tracks = state.tracks.map { track ->
                track.copy(clips = track.clips.map { clip ->
                    if (clip.id == clipId) {
                        val start = (newTrimStartMs ?: clip.trimStartMs).coerceIn(0L, clip.sourceDurationMs - 100L)
                        val end = (newTrimEndMs ?: clip.trimEndMs).coerceIn(start + 100L, clip.sourceDurationMs)
                        clip.copy(trimStartMs = start, trimEndMs = end)
                    } else clip
                })
            }
            val totalDuration = tracks.maxOfOrNull { t ->
                t.clips.maxOfOrNull { it.timelineEndMs } ?: 0L
            } ?: 0L
            state.copy(tracks = tracks, totalDurationMs = totalDuration)
        }
        rebuildPlayerTimeline()
    }

    // --- Speed ---
    fun beginSpeedChange() {
        saveUndoState("Change speed")
    }

    fun setClipSpeed(clipId: String, speed: Float) {
        stateFlow.update { state ->
            val tracks = state.tracks.map { track ->
                track.copy(clips = track.clips.map { clip ->
                    if (clip.id == clipId) clip.copy(speed = speed.coerceIn(0.1f, 16f))
                    else clip
                })
            }
            recalculateDuration(state.copy(tracks = tracks))
        }
        // Apply speed to preview immediately (don't rebuild full timeline for smooth slider)
        videoEngine.setPreviewSpeed(speed.coerceIn(0.1f, 16f))
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
    }

    // --- Move Clip to Track ---
    fun moveClipToTrack(clipId: String, targetTrackId: String) {
        saveUndoState("Move clip to track")
        stateFlow.update { state ->
            var clipToMove: Clip? = null
            val tracksWithRemoved = state.tracks.map { track ->
                val clip = track.clips.find { it.id == clipId }
                if (clip != null) {
                    clipToMove = clip
                    track.copy(clips = track.clips.filter { it.id != clipId })
                } else track
            }
            val movedClip = clipToMove ?: return@update state
            val tracks = tracksWithRemoved.map { track ->
                if (track.id == targetTrackId) {
                    val endMs = track.clips.maxOfOrNull { it.timelineEndMs } ?: 0L
                    track.copy(clips = track.clips + movedClip.copy(timelineStartMs = endMs))
                } else track
            }
            recalculateDuration(state.copy(tracks = tracks))
        }
        rebuildPlayerTimeline()
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
            state.copy(tracks = tracks)
        }
        rebuildPlayerTimeline()
    }

}

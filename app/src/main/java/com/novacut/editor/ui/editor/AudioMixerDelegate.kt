package com.novacut.editor.ui.editor

import com.novacut.editor.engine.BeatDetectionEngine
import com.novacut.editor.engine.LoudnessEngine
import com.novacut.editor.model.AudioEffect
import com.novacut.editor.model.AudioEffectType
import com.novacut.editor.model.TrackType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Delegate handling audio mixer, track volume/pan/solo, audio effects,
 * beat detection, and audio normalization.
 * Extracted from EditorViewModel to reduce its size.
 */
class AudioMixerDelegate(
    private val stateFlow: MutableStateFlow<EditorState>,
    private val beatDetectionEngine: BeatDetectionEngine,
    private val loudnessEngine: LoudnessEngine,
    private val scope: CoroutineScope,
    private val saveUndoState: (String) -> Unit,
    private val showToast: (String) -> Unit,
    private val pauseIfPlaying: () -> Unit,
    private val dismissedPanelState: (EditorState) -> EditorState,
    private val refreshPreview: () -> Unit,
    private val saveProject: () -> Unit
) {
    // --- Audio Mixer ---
    fun showAudioMixer() {
        pauseIfPlaying()
        stateFlow.update { dismissedPanelState(it).copy(panels = it.panels.closeAll().open(PanelId.AUDIO_MIXER)) }
    }

    fun hideAudioMixer() {
        stateFlow.update { it.copy(panels = it.panels.close(PanelId.AUDIO_MIXER)) }
    }

    fun setTrackVolume(trackId: String, volume: Float) {
        saveUndoState("Change track volume")
        stateFlow.update { s ->
            s.copy(tracks = s.tracks.map { track ->
                if (track.id == trackId) track.copy(volume = volume.coerceIn(0f, 2f)) else track
            })
        }
        refreshPreview()
        saveProject()
    }

    fun setTrackPan(trackId: String, pan: Float) {
        saveUndoState("Change track pan")
        stateFlow.update { s ->
            s.copy(tracks = s.tracks.map { track ->
                if (track.id == trackId) track.copy(pan = pan.coerceIn(-1f, 1f)) else track
            })
        }
        refreshPreview()
        saveProject()
    }

    fun toggleTrackSolo(trackId: String) {
        saveUndoState("Toggle track solo")
        stateFlow.update { s ->
            s.copy(tracks = s.tracks.map { track ->
                if (track.id == trackId) track.copy(isSolo = !track.isSolo) else track
            })
        }
        refreshPreview()
        saveProject()
    }

    fun addTrackAudioEffect(trackId: String, type: AudioEffectType) {
        saveUndoState("Add audio effect")
        stateFlow.update { s ->
            s.copy(tracks = s.tracks.map { track ->
                if (track.id == trackId) {
                    val effect = AudioEffect(
                        type = type,
                        params = AudioEffectType.defaultParams(type)
                    )
                    track.copy(audioEffects = track.audioEffects + effect)
                } else track
            })
        }
        saveProject()
    }

    fun removeTrackAudioEffect(trackId: String, effectId: String) {
        saveUndoState("Remove audio effect")
        stateFlow.update { s ->
            s.copy(tracks = s.tracks.map { track ->
                if (track.id == trackId) {
                    track.copy(audioEffects = track.audioEffects.filter { it.id != effectId })
                } else track
            })
        }
        saveProject()
    }

    fun updateTrackAudioEffectParam(trackId: String, effectId: String, param: String, value: Float) {
        stateFlow.update { s ->
            s.copy(tracks = s.tracks.map { track ->
                if (track.id == trackId) {
                    track.copy(audioEffects = track.audioEffects.map { effect ->
                        if (effect.id == effectId) {
                            effect.copy(params = effect.params + (param to value))
                        } else effect
                    })
                } else track
            })
        }
        saveProject()
    }

    fun detectBeats() {
        val s = stateFlow.value
        val audioClips = s.tracks
            .filter { it.type == TrackType.AUDIO || it.type == TrackType.VIDEO }
            .flatMap { it.clips }
        if (audioClips.isEmpty()) {
            showToast("No audio clips to analyze")
            return
        }
        val sourceUri = audioClips.first().sourceUri
        // Record undo state before the destructive replacement of beatMarkers so users can
        // recover their previous (e.g. manually-tapped) markers if auto-detect gives bad results.
        saveUndoState("Detect beats")
        scope.launch {
            stateFlow.update { it.copy(isAnalyzingBeats = true) }
            showToast("Detecting beats...")
            try {
                val analysis = withContext(Dispatchers.IO) { beatDetectionEngine.detectBeats(sourceUri) }

                // Re-validate clips still exist after async work
                val currentClips = stateFlow.value.tracks
                    .filter { it.type == TrackType.AUDIO || it.type == TrackType.VIDEO }
                    .flatMap { it.clips }
                if (currentClips.isEmpty()) {
                    stateFlow.update { it.copy(isAnalyzingBeats = false) }
                    showToast("Audio clips were deleted during analysis")
                    return@launch
                }

                val beatTimestamps = analysis.beats.map { it.timestampMs }
                stateFlow.update { it.copy(beatMarkers = beatTimestamps, isAnalyzingBeats = false) }
                saveProject()
                val bpmText = if (analysis.bpm > 0f) " (%.0f BPM)".format(analysis.bpm) else ""
                showToast("Found ${analysis.beats.size} beats$bpmText")
            } catch (e: Exception) {
                stateFlow.update { it.copy(isAnalyzingBeats = false) }
                showToast("Beat detection failed: ${e.message ?: "Unknown error"}")
            }
        }
    }

    // --- Audio Normalization ---
    fun showAudioNorm() {
        pauseIfPlaying()
        stateFlow.update { dismissedPanelState(it).copy(panels = it.panels.closeAll().open(PanelId.AUDIO_NORM)) }
    }

    fun hideAudioNorm() {
        stateFlow.update { it.copy(panels = it.panels.close(PanelId.AUDIO_NORM)) }
    }

    fun normalizeAudio(targetLufs: Float) {
        val clipId = stateFlow.value.selectedClipId ?: return
        val clip = stateFlow.value.tracks.flatMap { it.clips }.find { it.id == clipId } ?: return
        scope.launch {
            showToast("Measuring loudness...")
            try {
                val measurement = withContext(Dispatchers.IO) { loudnessEngine.measureLoudness(clip.sourceUri) }

                // Re-validate clip still exists after async work
                val currentClip = stateFlow.value.tracks.flatMap { it.clips }.find { it.id == clipId }
                if (currentClip == null) {
                    showToast("Clip was deleted during analysis")
                    return@launch
                }

                val preset = LoudnessEngine.LoudnessPreset.entries
                    .firstOrNull { it.targetLufs == targetLufs }
                    ?: LoudnessEngine.LoudnessPreset.YOUTUBE
                val gain = loudnessEngine.calculateNormalizationGain(measurement, preset)

                saveUndoState("Normalize audio")
                stateFlow.update { s ->
                    s.copy(tracks = s.tracks.map { track ->
                        track.copy(clips = track.clips.map { c ->
                            if (c.id == clipId) c.copy(volume = (c.volume * gain).coerceIn(0f, 2f)) else c
                        })
                    })
                }
                hideAudioNorm()
                saveProject()
                showToast("Normalized: %.1f \u2192 %.0f LUFS".format(measurement.integratedLufs, targetLufs))
            } catch (e: Exception) {
                showToast("Normalization failed: ${e.message ?: "Unknown error"}")
            }
        }
    }

}

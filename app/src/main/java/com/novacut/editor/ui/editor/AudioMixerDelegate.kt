package com.novacut.editor.ui.editor

import com.novacut.editor.engine.BeatDetectionEngine
import com.novacut.editor.engine.LoudnessEngine
import com.novacut.editor.model.AudioEffect
import com.novacut.editor.model.AudioEffectType
import com.novacut.editor.model.TrackType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

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
    private val dismissedPanelState: (EditorState) -> EditorState
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
        stateFlow.update { s ->
            s.copy(tracks = s.tracks.map { track ->
                if (track.id == trackId) track.copy(volume = volume.coerceIn(0f, 2f)) else track
            })
        }
    }

    fun setTrackPan(trackId: String, pan: Float) {
        stateFlow.update { s ->
            s.copy(tracks = s.tracks.map { track ->
                if (track.id == trackId) track.copy(pan = pan.coerceIn(-1f, 1f)) else track
            })
        }
    }

    fun toggleTrackSolo(trackId: String) {
        stateFlow.update { s ->
            s.copy(tracks = s.tracks.map { track ->
                if (track.id == trackId) track.copy(isSolo = !track.isSolo) else track
            })
        }
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
        scope.launch {
            stateFlow.update { it.copy(isAnalyzingBeats = true) }
            showToast("Detecting beats...")
            try {
                val analysis = beatDetectionEngine.detectBeats(audioClips.first().sourceUri)
                val beatTimestamps = analysis.beats.map { it.timestampMs }
                stateFlow.update { it.copy(beatMarkers = beatTimestamps, isAnalyzingBeats = false) }
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
        saveUndoState("Normalize audio")

        scope.launch {
            showToast("Measuring loudness...")
            try {
                val measurement = loudnessEngine.measureLoudness(clip.sourceUri)
                val preset = LoudnessEngine.LoudnessPreset.entries
                    .firstOrNull { it.targetLufs == targetLufs }
                    ?: LoudnessEngine.LoudnessPreset.YOUTUBE
                val gain = loudnessEngine.calculateNormalizationGain(measurement, preset)

                stateFlow.update { s ->
                    s.copy(tracks = s.tracks.map { track ->
                        track.copy(clips = track.clips.map { c ->
                            if (c.id == clipId) c.copy(volume = (c.volume * gain).coerceIn(0.1f, 3f)) else c
                        })
                    })
                }
                hideAudioNorm()
                showToast("Normalized: %.1f \u2192 %.0f LUFS".format(measurement.integratedLufs, targetLufs))
            } catch (e: Exception) {
                showToast("Normalization failed: ${e.message ?: "Unknown error"}")
            }
        }
    }

}

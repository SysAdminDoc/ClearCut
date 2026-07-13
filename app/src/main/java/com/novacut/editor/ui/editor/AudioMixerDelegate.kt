package com.novacut.editor.ui.editor

import android.content.Context
import com.novacut.editor.R
import com.novacut.editor.engine.AudioMasteringEngine
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
    private val audioMasteringEngine: AudioMasteringEngine,
    private val appContext: Context,
    private val scope: CoroutineScope,
    private val saveUndoState: (String) -> Unit,
    private val showToast: (String) -> Unit,
    private val pauseIfPlaying: () -> Unit,
    private val dismissedPanelState: (EditorState) -> EditorState,
    private val refreshPreview: () -> Unit,
    private val saveProject: () -> Unit
) {
    private fun text(resId: Int, vararg args: Any): String =
        appContext.getString(resId, *args)

    // --- Audio Mixer ---
    fun showAudioMixer() {
        pauseIfPlaying()
        stateFlow.update {
            dismissedPanelState(it).copyPanel { panel ->
                panel.copy(panels = panel.panels.closeAll().open(PanelId.AUDIO_MIXER))
            }
        }
    }

    fun hideAudioMixer() {
        stateFlow.update {
            it.copyPanel { panel -> panel.copy(panels = panel.panels.close(PanelId.AUDIO_MIXER)) }
        }
    }

    // Volume + pan sliders fire onValueChange 60 Hz during a drag; writing an
    // undo state + full project JSON to disk on every tick was hitching the UI.
    // `beginVolumeAdjust` / `endVolumeAdjust` (and the equivalent pan pair) now
    // bracket the drag so the undo snapshot happens once at drag-start and the
    // project save happens once at drag-end. The per-tick `setTrackVolume` call
    // only does the in-memory state mutation + preview refresh, which is cheap.
    fun beginVolumeAdjust() {
        saveUndoState("Change track volume")
    }

    fun endVolumeAdjust() {
        refreshPreview()
        saveProject()
    }

    fun setTrackVolume(trackId: String, volume: Float) {
        stateFlow.update { s ->
            s.copy(tracks = s.tracks.map { track ->
                if (track.id == trackId) track.copy(volume = volume.coerceIn(0f, 2f)) else track
            })
        }
    }

    fun beginPanAdjust() {
        saveUndoState("Change track pan")
    }

    fun endPanAdjust() {
        refreshPreview()
        saveProject()
    }

    fun setTrackPan(trackId: String, pan: Float) {
        stateFlow.update { s ->
            s.copy(tracks = s.tracks.map { track ->
                if (track.id == trackId) track.copy(pan = pan.coerceIn(-1f, 1f)) else track
            })
        }
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

    // --- C.6: Audio mastering presets ---
    //
    // One-tap chains tuned for distribution targets (Podcast / Music / Dialogue
    // / ASMR / Social Loud). See AudioMasteringEngine for the recipe data.
    // Each apply replaces the track's existing audio effect chain with the
    // preset's components so users get a deterministic before/after — undo
    // restores the previous chain.
    //
    // The mastering chain's LUFS / true-peak targets are passed forward via the
    // saveUndoState label so when this preset is paired with the export sheet's
    // EBU R128 normalization (already shipped), the right target is suggested.

    /** Returns the curated preset catalog (id, displayName, description). */
    fun getMasteringPresets(): List<AudioMasteringEngine.MasteringChain> =
        audioMasteringEngine.getPresets()

    /**
     * Apply a curated mastering chain to the given track. The track's audio
     * effect chain is replaced by the preset's components in canonical order:
     *   HighPass → EQ → De-esser → Compressor → Limiter
     *
     * @param trackId Target track id.
     * @param presetId One of the [AudioMasteringEngine] preset ids.
     * @return true if the preset was applied, false if either the preset id is
     *   unknown or the track is missing.
     */
    fun applyMasteringPreset(trackId: String, presetId: String): Boolean {
        val preset = audioMasteringEngine.getPreset(presetId) ?: run {
            showToast(text(R.string.audio_mastering_unknown_preset))
            return false
        }
        val targetTrack = stateFlow.value.tracks.firstOrNull { it.id == trackId } ?: run {
            showToast(text(R.string.audio_track_not_found))
            return false
        }
        // Skip if the target is not an audio-capable track. Video tracks have
        // embedded audio per Composition but mastering chains apply to the
        // explicit audio mix.
        if (targetTrack.type != TrackType.AUDIO && targetTrack.type != TrackType.VIDEO) {
            showToast(text(R.string.audio_mastering_audio_video_only))
            return false
        }
        saveUndoState("Apply ${preset.displayName}")
        val chain = audioMasteringEngine.buildEffectChain(preset)
        stateFlow.update { s ->
            s.copy(tracks = s.tracks.map { track ->
                if (track.id == trackId) track.copy(audioEffects = chain) else track
            })
        }
        refreshPreview()
        saveProject()
        return true
    }

    fun detectBeats() {
        val s = stateFlow.value
        val audioClips = s.tracks
            .filter { it.type == TrackType.AUDIO || it.type == TrackType.VIDEO }
            .flatMap { it.clips }
        if (audioClips.isEmpty()) {
            showToast(text(R.string.audio_no_clips_to_analyze))
            return
        }
        val sourceUri = audioClips.first().sourceUri
        // Record undo state before the destructive replacement of beatMarkers so users can
        // recover their previous (e.g. manually-tapped) markers if auto-detect gives bad results.
        saveUndoState("Detect beats")
        scope.launch {
            stateFlow.update { it.copy(isAnalyzingBeats = true) }
            showToast(text(R.string.audio_detecting_beats))
            try {
                val analysis = withContext(Dispatchers.IO) { beatDetectionEngine.detectBeats(sourceUri) }

                // Re-validate clips still exist after async work
                val currentClips = stateFlow.value.tracks
                    .filter { it.type == TrackType.AUDIO || it.type == TrackType.VIDEO }
                    .flatMap { it.clips }
                if (currentClips.isEmpty()) {
                    stateFlow.update { it.copy(isAnalyzingBeats = false) }
                    showToast(text(R.string.audio_clips_deleted_during_analysis))
                    return@launch
                }

                val timebase = stateFlow.value.project.timelineTimebase
                val beatTimestamps = analysis.beats.map { timebase.snapMs(it.timestampMs) }.distinct()
                stateFlow.update { it.copy(beatMarkers = beatTimestamps, isAnalyzingBeats = false) }
                saveProject()
                val bpmText = if (analysis.bpm > 0f) {
                    text(R.string.audio_beats_bpm_suffix, analysis.bpm)
                } else {
                    ""
                }
                showToast(text(R.string.audio_beats_found, analysis.beats.size, bpmText))
            } catch (e: Exception) {
                stateFlow.update { it.copy(isAnalyzingBeats = false) }
                showToast(text(R.string.audio_beat_detection_failed))
            }
        }
    }

    // --- Audio Normalization ---
    fun showAudioNorm() {
        pauseIfPlaying()
        stateFlow.update {
            dismissedPanelState(it).copyPanel { panel ->
                panel.copy(panels = panel.panels.closeAll().open(PanelId.AUDIO_NORM))
            }
        }
    }

    fun hideAudioNorm() {
        stateFlow.update {
            it.copyPanel { panel -> panel.copy(panels = panel.panels.close(PanelId.AUDIO_NORM)) }
        }
    }

    fun normalizeAudio(targetLufs: Float) {
        val clipId = stateFlow.value.selectedClipId ?: return
        val clip = stateFlow.value.tracks.flatMap { it.clips }.find { it.id == clipId } ?: return
        scope.launch {
            showToast(text(R.string.audio_measuring_loudness))
            try {
                val measurement = withContext(Dispatchers.IO) { loudnessEngine.measureLoudness(clip.sourceUri) }

                // Re-validate clip still exists after async work
                val currentClip = stateFlow.value.tracks.flatMap { it.clips }.find { it.id == clipId }
                if (currentClip == null) {
                    showToast(text(R.string.audio_clip_deleted_during_analysis))
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
                showToast(text(R.string.audio_normalized_clip, measurement.integratedLufs, targetLufs))
            } catch (e: Exception) {
                showToast(text(R.string.audio_normalization_failed))
            }
        }
    }

    fun normalizeAllClips(targetLufs: Float) {
        val state = stateFlow.value
        val audioClips = state.tracks
            .filter { it.type == TrackType.AUDIO || it.type == TrackType.VIDEO }
            .flatMap { t -> t.clips.map { c -> t.id to c } }
        if (audioClips.isEmpty()) {
            showToast(text(R.string.audio_no_clips_to_normalize))
            return
        }
        val preset = LoudnessEngine.LoudnessPreset.entries
            .firstOrNull { it.targetLufs == targetLufs }
            ?: LoudnessEngine.LoudnessPreset.YOUTUBE
        scope.launch {
            showToast(text(R.string.audio_measuring_loudness_all, audioClips.size))
            try {
                val gains = mutableMapOf<String, Float>()
                for ((_, clip) in audioClips) {
                    val measurement = withContext(Dispatchers.IO) {
                        loudnessEngine.measureLoudness(clip.sourceUri)
                    }
                    gains[clip.id] = loudnessEngine.calculateNormalizationGain(measurement, preset)
                }
                saveUndoState("Normalize all clips")
                var changed = 0
                stateFlow.update { s ->
                    s.copy(tracks = s.tracks.map { track ->
                        track.copy(clips = track.clips.map { c ->
                            val gain = gains[c.id]
                            if (gain != null && gain != 1f) {
                                changed++
                                c.copy(volume = (c.volume * gain).coerceIn(0f, 2f))
                            } else c
                        })
                    })
                }
                hideAudioNorm()
                saveProject()
                showToast(text(R.string.audio_normalized_clips, changed, targetLufs))
            } catch (e: Exception) {
                showToast(text(R.string.audio_normalization_failed))
            }
        }
    }

}

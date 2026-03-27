package com.novacut.editor.ui.editor

import com.novacut.editor.model.Effect
import com.novacut.editor.model.Transition
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.UUID

/**
 * Delegate handling effect and transition management: add, update, toggle, remove,
 * copy/paste effects, set/update transitions.
 * Extracted from EditorViewModel to reduce its size.
 */
class EffectsDelegate(
    private val stateFlow: MutableStateFlow<EditorState>,
    private val saveUndoState: (String) -> Unit,
    private val showToast: (String) -> Unit,
    private val updatePreview: () -> Unit,
    private val saveProject: () -> Unit,
    private val getSelectedClip: () -> com.novacut.editor.model.Clip?,
    private val recalculateDuration: (EditorState) -> EditorState
) {
    // --- Effects ---
    fun addEffect(clipId: String, effect: Effect) {
        // Guard against duplicate effect types
        val clip = stateFlow.value.tracks.flatMap { it.clips }.firstOrNull { it.id == clipId }
        if (clip?.effects?.any { it.type == effect.type } == true) {
            showToast("${effect.type.displayName} already applied")
            return
        }
        saveUndoState("Add effect")
        stateFlow.update { state ->
            val tracks = state.tracks.map { track ->
                track.copy(clips = track.clips.map { c ->
                    if (c.id == clipId) c.copy(effects = c.effects + effect)
                    else c
                })
            }
            state.copy(tracks = tracks)
        }
        updatePreview()
        saveProject()
    }

    fun beginEffectAdjust() {
        saveUndoState("Adjust effect")
    }

    fun updateEffect(clipId: String, effectId: String, params: Map<String, Float>) {
        stateFlow.update { state ->
            val tracks = state.tracks.map { track ->
                track.copy(clips = track.clips.map { clip ->
                    if (clip.id == clipId) {
                        clip.copy(effects = clip.effects.map { e ->
                            if (e.id == effectId) e.copy(params = e.params + params)
                            else e
                        })
                    } else clip
                })
            }
            state.copy(tracks = tracks)
        }
        updatePreview()
    }

    fun toggleEffectEnabled(clipId: String, effectId: String) {
        saveUndoState("Toggle effect")
        stateFlow.update { state ->
            val tracks = state.tracks.map { track ->
                track.copy(clips = track.clips.map { clip ->
                    if (clip.id == clipId) {
                        clip.copy(effects = clip.effects.map { e ->
                            if (e.id == effectId) e.copy(enabled = !e.enabled)
                            else e
                        })
                    } else clip
                })
            }
            state.copy(tracks = tracks)
        }
        updatePreview()
        saveProject()
    }

    fun removeEffect(clipId: String, effectId: String) {
        saveUndoState("Remove effect")
        stateFlow.update { state ->
            val tracks = state.tracks.map { track ->
                track.copy(clips = track.clips.map { clip ->
                    if (clip.id == clipId) clip.copy(effects = clip.effects.filterNot { it.id == effectId })
                    else clip
                })
            }
            state.copy(tracks = tracks)
        }
        updatePreview()
        saveProject()
    }

    fun copyEffects() {
        val clip = getSelectedClip() ?: return
        if (clip.effects.isEmpty()) {
            showToast("No effects to copy")
            return
        }
        stateFlow.update { it.copy(copiedEffects = clip.effects) }
        showToast("Copied ${clip.effects.size} effects")
    }

    fun pasteEffects() {
        val clipId = stateFlow.value.selectedClipId ?: return
        val toPaste = stateFlow.value.copiedEffects
        if (toPaste.isEmpty()) {
            showToast("No effects copied")
            return
        }
        val targetClip = stateFlow.value.tracks.flatMap { it.clips }.firstOrNull { it.id == clipId } ?: return
        val existingTypes = targetClip.effects.map { it.type }.toSet()
        val filtered = toPaste.filter { it.type !in existingTypes }
        if (filtered.isEmpty()) {
            showToast("Effects already present on clip")
            return
        }
        saveUndoState("Paste effects")
        stateFlow.update { state ->
            val tracks = state.tracks.map { track ->
                track.copy(clips = track.clips.map { clip ->
                    if (clip.id == clipId) {
                        clip.copy(effects = clip.effects + filtered.map { it.copy(id = UUID.randomUUID().toString()) })
                    } else clip
                })
            }
            state.copy(tracks = tracks)
        }
        showToast("Pasted ${filtered.size} effects")
        updatePreview()
        saveProject()
    }

    // --- Transitions ---
    fun setTransition(clipId: String, transition: Transition?) {
        saveUndoState("Set transition")
        stateFlow.update { state ->
            val tracks = state.tracks.map { track ->
                track.copy(clips = track.clips.map { clip ->
                    if (clip.id == clipId) clip.copy(transition = transition)
                    else clip
                })
            }
            recalculateDuration(state.copy(tracks = tracks))
        }
        updatePreview()
        saveProject()
    }

    fun beginTransitionDurationChange() {
        saveUndoState("Change transition duration")
    }

    fun setTransitionDuration(clipId: String, durationMs: Long) {
        stateFlow.update { state ->
            val tracks = state.tracks.map { track ->
                track.copy(clips = track.clips.map { clip ->
                    if (clip.id == clipId && clip.transition != null) {
                        val clampedMs = durationMs.coerceIn(100L, clip.durationMs / 2)
                        clip.copy(transition = clip.transition.copy(durationMs = clampedMs))
                    } else clip
                })
            }
            recalculateDuration(state.copy(tracks = tracks))
        }
        updatePreview()
        saveProject()
    }

}

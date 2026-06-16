package com.novacut.editor.ui.editor

import android.content.Context
import com.novacut.editor.R
import com.novacut.editor.model.Effect
import com.novacut.editor.model.Transition
import com.novacut.editor.model.TransitionEasing
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.UUID

class EffectsDelegate(
    private val stateFlow: MutableStateFlow<EditorState>,
    private val saveUndoState: (String) -> Unit,
    private val showToast: (String) -> Unit,
    private val updatePreview: () -> Unit,
    private val rebuildPlayerTimeline: () -> Unit,
    private val saveProject: () -> Unit,
    private val getSelectedClip: () -> com.novacut.editor.model.Clip?,
    private val recalculateDuration: (EditorState) -> EditorState,
    private val appContext: Context
) {
    // --- Effects ---
    fun addEffect(clipId: String, effect: Effect) {
        // Guard against duplicate effect types
        val clip = stateFlow.value.tracks.flatMap { it.clips }.firstOrNull { it.id == clipId }
        if (clip?.effects?.any { it.type == effect.type } == true) {
            showToast(appContext.getString(R.string.effects_already_applied_toast, effect.type.displayName))
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

    fun endEffectAdjust() {
        // Persist once when the slider is released. See note in `updateEffect`.
        saveProject()
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
        // saveProject() moved to endEffectAdjust(). Effect sliders fire this method
        // on every onValueChange event (~60 Hz during drag); serializing the whole
        // project to JSON and writing it to disk 60 times/sec during a single
        // slider adjustment was producing noticeable hitching. The beginEffectAdjust/
        // endEffectAdjust pair already wraps the drag, so the saveProject call is
        // deferred to the end-of-drag hook.
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
            showToast(appContext.getString(R.string.effects_none_to_copy_toast))
            return
        }
        stateFlow.update { it.copy(copiedEffects = clip.effects) }
        showToast(appContext.getString(R.string.effects_copied_toast, clip.effects.size))
    }

    fun pasteEffects() {
        val clipId = stateFlow.value.selectedClipId ?: return
        val toPaste = stateFlow.value.copiedEffects
        if (toPaste.isEmpty()) {
            showToast(appContext.getString(R.string.effects_none_copied_toast))
            return
        }
        val targetClip = stateFlow.value.tracks.flatMap { it.clips }.firstOrNull { it.id == clipId } ?: return
        val existingTypes = targetClip.effects.map { it.type }.toSet()
        val filtered = toPaste.filter { it.type !in existingTypes }
        if (filtered.isEmpty()) {
            showToast(appContext.getString(R.string.effects_already_present_toast))
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
        showToast(appContext.getString(R.string.effects_pasted_toast, filtered.size))
        updatePreview()
        saveProject()
    }

    // --- Transitions ---
    fun setTransition(clipId: String, transition: Transition?, edge: TransitionEdge = TransitionEdge.HEAD) {
        saveUndoState("Set transition")
        stateFlow.update { state ->
            val tracks = state.tracks.map { track ->
                track.copy(clips = track.clips.map { clip ->
                    if (clip.id == clipId) {
                        when (edge) {
                            TransitionEdge.HEAD -> clip.copy(headTransition = transition)
                            TransitionEdge.TAIL -> clip.copy(tailTransition = transition)
                        }
                    } else clip
                })
            }
            recalculateDuration(state.copy(tracks = tracks))
        }
        rebuildPlayerTimeline()
        saveProject()
    }

    fun beginTransitionDurationChange() {
        saveUndoState("Change transition duration")
    }

    fun endTransitionDurationChange() {
        rebuildPlayerTimeline()
        saveProject()
    }

    fun setTransitionDuration(clipId: String, durationMs: Long, edge: TransitionEdge = TransitionEdge.HEAD) {
        stateFlow.update { state ->
            val tracks = state.tracks.map { track ->
                track.copy(clips = track.clips.map { clip ->
                    if (clip.id == clipId) {
                        val t = if (edge == TransitionEdge.HEAD) clip.headTransition else clip.tailTransition
                        if (t != null) {
                            val maxDuration = (clip.durationMs / 2).coerceAtLeast(100L)
                            val clampedMs = durationMs.coerceIn(100L, maxDuration)
                            val updated = t.copy(durationMs = clampedMs)
                            if (edge == TransitionEdge.HEAD) clip.copy(headTransition = updated)
                            else clip.copy(tailTransition = updated)
                        } else clip
                    } else clip
                })
            }
            recalculateDuration(state.copy(tracks = tracks))
        }
    }

    fun setTransitionEasing(clipId: String, easing: TransitionEasing, edge: TransitionEdge = TransitionEdge.HEAD) {
        saveUndoState("Change transition easing")
        stateFlow.update { state ->
            val tracks = state.tracks.map { track ->
                track.copy(clips = track.clips.map { clip ->
                    if (clip.id == clipId) {
                        val t = if (edge == TransitionEdge.HEAD) clip.headTransition else clip.tailTransition
                        if (t != null) {
                            val updated = t.copy(easing = easing)
                            if (edge == TransitionEdge.HEAD) clip.copy(headTransition = updated)
                            else clip.copy(tailTransition = updated)
                        } else clip
                    } else clip
                })
            }
            state.copy(tracks = tracks)
        }
        rebuildPlayerTimeline()
        saveProject()
    }

    enum class TransitionEdge { HEAD, TAIL }

}

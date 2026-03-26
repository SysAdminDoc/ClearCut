package com.novacut.editor.engine

import com.novacut.editor.model.*
import com.novacut.editor.ui.editor.EditorState

/**
 * Serializable edit commands for reliable undo/redo.
 * Each timeline operation is encapsulated as a command that can be
 * executed, undone, and serialized for crash recovery.
 *
 * Benefits over current snapshot-based undo:
 * - O(1) memory per operation (vs O(n) for full state snapshots)
 * - Serializable for crash recovery (replay commands from last checkpoint)
 * - Composable (group multiple commands into a compound command)
 *
 * Note: this does not replace the existing undo system — it provides the
 * foundation for a future migration. The current snapshot-based undo still works.
 */
sealed class EditCommand {
    abstract val description: String
    abstract fun execute(state: EditorState): EditorState
    abstract fun undo(state: EditorState): EditorState

    data class AddClip(
        override val description: String = "Add clip",
        val trackId: String,
        val clip: Clip
    ) : EditCommand() {
        override fun execute(state: EditorState): EditorState {
            return state.copy(tracks = state.tracks.map { track ->
                if (track.id == trackId) track.copy(clips = track.clips + clip) else track
            })
        }
        override fun undo(state: EditorState): EditorState {
            return state.copy(tracks = state.tracks.map { track ->
                if (track.id == trackId) track.copy(clips = track.clips.filter { it.id != clip.id }) else track
            })
        }
    }

    data class RemoveClip(
        override val description: String = "Remove clip",
        val trackId: String,
        val clip: Clip
    ) : EditCommand() {
        override fun execute(state: EditorState): EditorState {
            return state.copy(tracks = state.tracks.map { track ->
                if (track.id == trackId) track.copy(clips = track.clips.filter { it.id != clip.id }) else track
            })
        }
        override fun undo(state: EditorState): EditorState {
            return state.copy(tracks = state.tracks.map { track ->
                if (track.id == trackId) track.copy(clips = track.clips + clip) else track
            })
        }
    }

    data class TrimClip(
        override val description: String = "Trim clip",
        val clipId: String,
        val oldTrimStartMs: Long,
        val oldTrimEndMs: Long,
        val newTrimStartMs: Long,
        val newTrimEndMs: Long
    ) : EditCommand() {
        override fun execute(state: EditorState) = updateClip(state, clipId) {
            it.copy(trimStartMs = newTrimStartMs, trimEndMs = newTrimEndMs)
        }
        override fun undo(state: EditorState) = updateClip(state, clipId) {
            it.copy(trimStartMs = oldTrimStartMs, trimEndMs = oldTrimEndMs)
        }
    }

    data class MoveClip(
        override val description: String = "Move clip",
        val clipId: String,
        val oldTimelineStartMs: Long,
        val newTimelineStartMs: Long
    ) : EditCommand() {
        override fun execute(state: EditorState) = updateClip(state, clipId) {
            it.copy(timelineStartMs = newTimelineStartMs)
        }
        override fun undo(state: EditorState) = updateClip(state, clipId) {
            it.copy(timelineStartMs = oldTimelineStartMs)
        }
    }

    data class SetClipSpeed(
        override val description: String = "Change speed",
        val clipId: String,
        val oldSpeed: Float,
        val newSpeed: Float
    ) : EditCommand() {
        override fun execute(state: EditorState) = updateClip(state, clipId) { it.copy(speed = newSpeed) }
        override fun undo(state: EditorState) = updateClip(state, clipId) { it.copy(speed = oldSpeed) }
    }

    data class ApplyEffect(
        override val description: String = "Apply effect",
        val clipId: String,
        val effect: Effect
    ) : EditCommand() {
        override fun execute(state: EditorState) = updateClip(state, clipId) {
            it.copy(effects = it.effects + effect)
        }
        override fun undo(state: EditorState) = updateClip(state, clipId) {
            it.copy(effects = it.effects.filter { e -> e.id != effect.id })
        }
    }

    data class CompoundCommand(
        override val description: String,
        val commands: List<EditCommand>
    ) : EditCommand() {
        override fun execute(state: EditorState): EditorState {
            var s = state
            for (cmd in commands) s = cmd.execute(s)
            return s
        }
        override fun undo(state: EditorState): EditorState {
            var s = state
            for (cmd in commands.reversed()) s = cmd.undo(s)
            return s
        }
    }

    companion object {
        fun updateClip(state: EditorState, clipId: String, transform: (Clip) -> Clip): EditorState {
            return state.copy(tracks = state.tracks.map { track ->
                track.copy(clips = track.clips.map { clip ->
                    if (clip.id == clipId) transform(clip) else clip
                })
            })
        }
    }
}

/**
 * Bridge to existing snapshot-based undo system.
 * Use this to gradually migrate methods from saveUndoState() to EditCommand.
 * Full migration: replace _state.update + saveUndoState with command.execute() + push to command stack.
 */

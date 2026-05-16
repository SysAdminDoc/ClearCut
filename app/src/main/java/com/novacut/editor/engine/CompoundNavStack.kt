package com.novacut.editor.engine

import com.novacut.editor.model.Clip

/**
 * C.13 — Compound clip / nested-sequence navigation stack.
 *
 * NovaCut's [Clip] model already supports `isCompound` + `compoundClips`
 * (a clip can carry an ordered list of child clips that act as its
 * "sub-timeline"). What's missing from the editor UX is the navigation
 * affordance: tap a compound clip → enter its sub-timeline → edit
 * children → exit back to the parent.
 *
 * This object owns the navigation state. The Composable layer reads
 * [currentLevel] to decide whether to render the parent timeline or a
 * child sub-timeline; on enter / exit it calls [push] / [pop] / [reset].
 *
 * Pure Kotlin so the editor's state-restoration path (autosave, recovery
 * dialog) can serialize the breadcrumb list directly.
 *
 * ## Why a separate object vs holding the stack inside EditorViewModel
 *
 * Keeping the nav stack self-contained means the autosave / archive layer
 * has a clear single object to round-trip when restoring an editor
 * session in a sub-timeline (a user who quit while editing a compound
 * clip should resume in that compound clip's sub-timeline, not at the
 * root). The same self-contained shape also tests cleanly on the JVM
 * without spinning up the full EditorViewModel.
 */
class CompoundNavStack {

    /**
     * A single entry on the nav stack. `parentClipId` is the compound
     * clip the user descended into; null at the root level.
     */
    data class Level(
        val parentClipId: String?,
        val parentClipName: String?,
        val depth: Int,
    ) {
        val isRoot: Boolean get() = parentClipId == null
    }

    private val stack: ArrayDeque<Level> = ArrayDeque()

    init {
        // The root level is always present.
        stack.addLast(Level(parentClipId = null, parentClipName = null, depth = 0))
    }

    /** Current (topmost) level the editor is viewing. */
    val currentLevel: Level get() = stack.last()

    /** Full breadcrumb from root to current. */
    val breadcrumb: List<Level> get() = stack.toList()

    val depth: Int get() = stack.size - 1

    val isAtRoot: Boolean get() = depth == 0

    /**
     * Descend into a compound clip. Throws when [clip] is not compound;
     * the UI gate must check `clip.isCompound` first.
     *
     * Returns the new level.
     */
    fun push(clip: Clip): Level {
        require(clip.isCompound) {
            "Cannot push into non-compound clip ${clip.id}"
        }
        // Reject cycles: a compound clip that lists itself (directly or
        // indirectly) as a child must not be re-entered, otherwise the
        // editor walks an infinite tree.
        if (stack.any { it.parentClipId == clip.id }) {
            throw IllegalStateException(
                "Refusing to re-enter compound clip ${clip.id} that is already on the stack"
            )
        }
        // Enforce the MAX_DEPTH defensive cap so a malformed project can't
        // recurse the editor into an unusable depth.
        require(stack.size <= MAX_DEPTH) {
            "Compound clip nesting depth exceeded (max $MAX_DEPTH)"
        }
        val newLevel = Level(
            parentClipId = clip.id,
            parentClipName = clip.name,
            depth = stack.size,
        )
        stack.addLast(newLevel)
        return newLevel
    }

    /**
     * Exit the current sub-timeline. Returns the new (parent) level.
     * Refuses to pop the root level — at root, exit is a no-op.
     */
    fun pop(): Level {
        if (isAtRoot) return currentLevel
        stack.removeLast()
        return currentLevel
    }

    /** Reset to the root level, discarding all sub-timeline state. */
    fun reset() {
        while (!isAtRoot) stack.removeLast()
    }

    /**
     * Serialize the stack to a list of parent-clip-ids suitable for the
     * autosave JSON. Use [restore] on the editor's session-resume path.
     * The root level is implicit (always present) and is not serialized.
     */
    fun toSerializedIds(): List<String> = stack
        .drop(1)  // skip the implicit root
        .mapNotNull { it.parentClipId }

    /**
     * Restore the stack from a serialized id list. The caller resolves
     * each id back to a [Clip] (since the engine has no access to the
     * project model directly) and is responsible for refusing the
     * restore if any id no longer resolves.
     *
     * @param resolvedClips ordered list of compound clips matching the
     *   serialized ids. Length must equal the id list length.
     */
    fun restore(resolvedClips: List<Clip>) {
        reset()
        for (clip in resolvedClips) {
            push(clip)
        }
    }

    companion object {
        /**
         * Defensive cap to prevent runaway recursion in malformed projects.
         * No real workflow needs more than a few levels of nesting.
         */
        const val MAX_DEPTH: Int = 8
    }
}

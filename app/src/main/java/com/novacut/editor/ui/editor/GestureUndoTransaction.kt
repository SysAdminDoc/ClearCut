package com.novacut.editor.ui.editor

data class GestureFinishResult(
    val hadMutation: Boolean,
    val committedChange: Boolean,
)

/** Holds a pre-mutation snapshot off the undo stack until a gesture commits. */
internal class GestureUndoTransaction<T>(
    private val equivalent: (T, T) -> Boolean,
) {
    private var description: String? = null
    private var snapshot: T? = null

    fun begin(description: String): Boolean {
        if (this.description != null) return false
        this.description = description
        snapshot = null
        return true
    }

    fun captureBeforeMutation(description: String, capture: () -> T) {
        check(this.description == description) { "No active $description gesture" }
        if (snapshot == null) snapshot = capture()
    }

    fun finish(
        description: String,
        commit: Boolean,
        current: () -> T,
        onCommit: (T) -> Unit,
        onCancel: (T) -> Unit,
    ): GestureFinishResult {
        if (this.description != description) return GestureFinishResult(false, false)
        val initial = snapshot
        this.description = null
        snapshot = null
        if (initial == null) return GestureFinishResult(false, false)

        if (!commit) {
            onCancel(initial)
            return GestureFinishResult(hadMutation = true, committedChange = false)
        }
        val changed = !equivalent(initial, current())
        if (changed) onCommit(initial)
        return GestureFinishResult(hadMutation = true, committedChange = changed)
    }
}

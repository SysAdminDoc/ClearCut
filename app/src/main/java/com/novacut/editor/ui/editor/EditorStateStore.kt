package com.novacut.editor.ui.editor

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Single mutable owner for editor UI state.
 *
 * Delegates receive the mutable flow for compatibility with their existing
 * update APIs, while screens observe only the read-only projection. Keeping
 * construction and exposure here gives the state boundary a direct JVM seam.
 */
internal class EditorStateStore(
    initialState: EditorState = EditorState(),
) {
    val mutable: MutableStateFlow<EditorState> = MutableStateFlow(initialState)
    val state: StateFlow<EditorState> = mutable.asStateFlow()

    val value: EditorState get() = mutable.value

    fun update(transform: (EditorState) -> EditorState) {
        mutable.update(transform)
    }
}

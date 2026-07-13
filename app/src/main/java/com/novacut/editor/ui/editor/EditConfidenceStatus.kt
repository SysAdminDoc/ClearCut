package com.novacut.editor.ui.editor

import com.novacut.editor.model.SaveIndicatorState

data class EditConfidenceStatus(
    val undoableEdits: Int,
    val redoableEdits: Int,
    val restorePoints: Int,
    val saveIndicator: SaveIndicatorState,
    val isDirty: Boolean = false,
) {
    val hasUndoHistory: Boolean get() = undoableEdits > 0 || redoableEdits > 0
    val hasRestorePoints: Boolean get() = restorePoints > 0
    val saveNeedsAttention: Boolean get() = saveIndicator == SaveIndicatorState.ERROR
}

fun editConfidenceStatusFor(
    undoableEdits: Int,
    redoableEdits: Int,
    restorePoints: Int,
    saveIndicator: SaveIndicatorState,
    isDirty: Boolean = false,
): EditConfidenceStatus {
    return EditConfidenceStatus(
        undoableEdits = undoableEdits.coerceAtLeast(0),
        redoableEdits = redoableEdits.coerceAtLeast(0),
        restorePoints = restorePoints.coerceAtLeast(0),
        saveIndicator = saveIndicator,
        isDirty = isDirty,
    )
}

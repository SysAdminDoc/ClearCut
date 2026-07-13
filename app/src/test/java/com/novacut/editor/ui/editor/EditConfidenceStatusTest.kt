package com.novacut.editor.ui.editor

import com.novacut.editor.model.SaveIndicatorState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditConfidenceStatusTest {

    @Test
    fun statusClampsNegativeCounts() {
        val status = editConfidenceStatusFor(
            undoableEdits = -2,
            redoableEdits = -1,
            restorePoints = -5,
            saveIndicator = SaveIndicatorState.HIDDEN
        )

        assertEquals(0, status.undoableEdits)
        assertEquals(0, status.redoableEdits)
        assertEquals(0, status.restorePoints)
        assertFalse(status.hasUndoHistory)
        assertFalse(status.hasRestorePoints)
    }

    @Test
    fun undoRedoCountsMarkHistoryAvailable() {
        val undoOnly = editConfidenceStatusFor(3, 0, 0, SaveIndicatorState.SAVED)
        val redoOnly = editConfidenceStatusFor(0, 2, 0, SaveIndicatorState.SAVED)

        assertTrue(undoOnly.hasUndoHistory)
        assertTrue(redoOnly.hasUndoHistory)
    }

    @Test
    fun snapshotsMarkRestorePointsAvailable() {
        val status = editConfidenceStatusFor(0, 0, 2, SaveIndicatorState.SAVED)

        assertTrue(status.hasRestorePoints)
    }

    @Test
    fun saveErrorNeedsAttention() {
        val error = editConfidenceStatusFor(1, 0, 1, SaveIndicatorState.ERROR)
        val saving = editConfidenceStatusFor(1, 0, 1, SaveIndicatorState.SAVING)

        assertTrue(error.saveNeedsAttention)
        assertFalse(saving.saveNeedsAttention)
    }

    @Test
    fun dirtyStateIsIndependentFromTransientSaveIndicator() {
        val dirty = editConfidenceStatusFor(1, 0, 1, SaveIndicatorState.HIDDEN, isDirty = true)
        val saving = editConfidenceStatusFor(1, 0, 1, SaveIndicatorState.SAVING, isDirty = true)
        val error = editConfidenceStatusFor(1, 0, 1, SaveIndicatorState.ERROR, isDirty = true)

        assertTrue(dirty.isDirty)
        assertTrue(saving.isDirty)
        assertTrue(error.isDirty)
        assertTrue(error.saveNeedsAttention)
    }
}

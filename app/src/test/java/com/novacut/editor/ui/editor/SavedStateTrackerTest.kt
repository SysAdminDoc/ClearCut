package com.novacut.editor.ui.editor

import com.novacut.editor.model.SaveIndicatorState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedStateTrackerTest {

    @Test
    fun editUndoAndRedoCompareAgainstSavedBaseline() {
        val tracker = SavedStateTracker()

        assertClean(tracker.establishBaseline("saved"))
        assertDirty(tracker.contentChanged("edited"), SaveIndicatorState.HIDDEN)
        assertClean(tracker.contentChanged("saved"))
        assertDirty(tracker.contentChanged("edited"), SaveIndicatorState.HIDDEN)
    }

    @Test
    fun savingCurrentFingerprintEstablishesCleanBaseline() {
        val tracker = SavedStateTracker()
        tracker.establishBaseline("old")
        tracker.contentChanged("new")

        val (attempt, saving) = tracker.beginSave("new")
        assertDirty(saving, SaveIndicatorState.SAVING)

        assertClean(tracker.saveSucceeded(attempt, "new"), SaveIndicatorState.SAVED)
    }

    @Test
    fun editDuringSaveRemainsDirtyAndDoesNotClaimSaved() {
        val tracker = SavedStateTracker()
        tracker.establishBaseline("old")
        val (attempt, _) = tracker.beginSave("snapshot")

        val status = tracker.saveSucceeded(attempt, "newer-edit")

        assertDirty(status, SaveIndicatorState.HIDDEN)
        assertClean(tracker.contentChanged("snapshot"))
    }

    @Test
    fun staleCompletionCannotOverrideLatestSaveAttempt() {
        val tracker = SavedStateTracker()
        tracker.establishBaseline("old")
        val (first, _) = tracker.beginSave("first")
        val (latest, _) = tracker.beginSave("latest")

        assertDirty(tracker.saveFailed(first, "latest"), SaveIndicatorState.SAVING)
        assertClean(tracker.saveSucceeded(latest, "latest"), SaveIndicatorState.SAVED)
    }

    @Test
    fun saveErrorStaysAssertiveUntilSuccessfulRetry() {
        val tracker = SavedStateTracker()
        tracker.establishBaseline("saved")
        val (failed, _) = tracker.beginSave("edited")

        assertDirty(tracker.saveFailed(failed, "edited"), SaveIndicatorState.ERROR)
        assertDirty(tracker.contentChanged("newer-edit"), SaveIndicatorState.ERROR)

        val (retry, _) = tracker.beginSave("newer-edit")
        assertClean(tracker.saveSucceeded(retry, "newer-edit"), SaveIndicatorState.SAVED)
    }

    private fun assertClean(
        status: SavedStateStatus,
        indicator: SaveIndicatorState = SaveIndicatorState.HIDDEN,
    ) {
        assertFalse(status.isDirty)
        assertEquals(indicator, status.indicator)
    }

    private fun assertDirty(status: SavedStateStatus, indicator: SaveIndicatorState) {
        assertTrue(status.isDirty)
        assertEquals(indicator, status.indicator)
    }
}

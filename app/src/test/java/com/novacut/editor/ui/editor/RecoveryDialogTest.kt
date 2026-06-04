package com.novacut.editor.ui.editor

import com.novacut.editor.engine.AutoSaveState
import com.novacut.editor.engine.ProjectAutoSave
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryDialogTest {

    @Test
    fun shouldShowRecoveryDialog_ignoresNormalProjectPersistence() {
        assertFalse(
            shouldShowRecoveryDialog(
                projectUpdatedAtMs = 10_000L,
                recoveryTimestampMs = 12_000L,
                hasRecoveredContent = true
            )
        )
    }

    @Test
    fun shouldShowRecoveryDialog_requiresNewerAutosaveWithContent() {
        assertTrue(
            shouldShowRecoveryDialog(
                projectUpdatedAtMs = 10_000L,
                recoveryTimestampMs = 20_001L,
                hasRecoveredContent = true
            )
        )

        assertFalse(
            shouldShowRecoveryDialog(
                projectUpdatedAtMs = 10_000L,
                recoveryTimestampMs = 20_001L,
                hasRecoveredContent = false
            )
        )
    }

    @Test
    fun recoveryOpenFeedback_blocksWritesForFutureSchemaAndCorruptAutosaves() {
        val future = ProjectAutoSave.LoadOutcome.FutureSchema(
            fileVersion = AutoSaveState.FORMAT_VERSION + 1,
            supportedVersion = AutoSaveState.FORMAT_VERSION
        )
        val corrupt = ProjectAutoSave.LoadOutcome.Corrupt(IllegalStateException("bad json"))

        assertTrue(shouldBlockAutoSaveForRecoveryOutcome(future))
        assertTrue(shouldBlockAutoSaveForRecoveryOutcome(corrupt))
        assertEquals(ToastSeverity.Error, recoveryOpenFeedbackFor(future, expectedRecovery = false)?.severity)
        assertEquals(ToastSeverity.Error, recoveryOpenFeedbackFor(corrupt, expectedRecovery = false)?.severity)
    }

    @Test
    fun recoveryOpenFeedback_onlyShowsNotFoundForExpectedRecoveryOpen() {
        assertNull(recoveryOpenFeedbackFor(ProjectAutoSave.LoadOutcome.NotFound, expectedRecovery = false))

        val feedback = recoveryOpenFeedbackFor(
            ProjectAutoSave.LoadOutcome.NotFound,
            expectedRecovery = true
        )
        assertEquals(ToastSeverity.Warning, feedback?.severity)
        assertFalse(shouldBlockAutoSaveForRecoveryOutcome(ProjectAutoSave.LoadOutcome.NotFound))
    }
}

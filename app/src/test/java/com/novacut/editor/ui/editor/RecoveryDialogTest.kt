package com.novacut.editor.ui.editor

import org.junit.Assert.assertFalse
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
}

package com.novacut.editor.ui.editor

import com.novacut.editor.engine.AutoSaveState
import com.novacut.editor.engine.ProjectAutoSave
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AutoSaveRecoveryTest {

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

    @Test
    fun loadedAutosaves_haveNoBlockingRecoveryDialog() {
        val viewModel = locate("app/src/main/java/com/novacut/editor/ui/editor/EditorViewModel.kt").readText()
        val utilityPanels = locate(
            "app/src/main/java/com/novacut/editor/ui/editor/EditorUtilityPanelHost.kt"
        ).readText()

        assertFalse(viewModel.contains("RECOVERY_DIALOG"))
        assertFalse(viewModel.contains("shouldShowRecoveryDialog"))
        assertFalse(utilityPanels.contains("recovery_title"))
    }

    private fun locate(relativePath: String): File {
        return listOf(File(relativePath), File("../$relativePath"))
            .firstOrNull(File::exists)
            ?: error("$relativePath not found")
    }
}

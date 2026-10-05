package com.novacut.editor.ui.export

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.novacut.editor.R
import com.novacut.editor.engine.ExportHistoryEntry
import com.novacut.editor.engine.ExportHistoryStatus
import com.novacut.editor.engine.buildExportHistoryEntry
import com.novacut.editor.model.ExportConfig
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.text.DateFormat

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExportHistoryRowTest {

    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val temp = TemporaryFolder()

    private val dateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)

    private fun entry(status: ExportHistoryStatus, partial: File? = null) = buildExportHistoryEntry(
        projectId = "project",
        projectName = "Long Render",
        status = status,
        startedAtEpochMs = 100L,
        finishedAtEpochMs = 200L,
        outputFile = partial,
        config = ExportConfig(),
        timelineDurationMs = 5_000L,
        resumePartialFile = partial,
        resumeProjectFingerprint = partial?.let { "project-fingerprint" },
        resumeConfigFingerprint = partial?.let { "config-fingerprint" },
    )

    private fun show(entry: ExportHistoryEntry, resumed: MutableList<ExportHistoryEntry>, restarts: IntArray) {
        compose.setContent {
            ExportHistoryRow(
                entry = entry,
                dateFormat = dateFormat,
                onResumeExport = { resumed += it },
                onStartAgain = { restarts[0]++ },
            )
        }
    }

    @Test
    fun anInterruptedExportWithAKeptPartialOffersResume() {
        val partial = temp.newFile("partial.mp4").apply { writeBytes(ByteArray(16) { 1 }) }
        val interrupted = entry(ExportHistoryStatus.INTERRUPTED, partial)
        val resumed = mutableListOf<ExportHistoryEntry>()
        val restarts = IntArray(1)
        show(interrupted, resumed, restarts)

        compose.onNodeWithText(string(R.string.export_history_status_interrupted)).assertExists()
        assertEquals(0, compose.onAllNodesWithText(string(R.string.export_start_again)).fetchSemanticsNodes().size)
        compose.onNodeWithText(string(R.string.export_resume)).performClick()

        assertEquals(listOf(interrupted), resumed)
        assertEquals(0, restarts[0])
    }

    @Test
    fun anInterruptedExportWithNothingToResumeOffersStartAgain() {
        val resumed = mutableListOf<ExportHistoryEntry>()
        val restarts = IntArray(1)
        show(entry(ExportHistoryStatus.INTERRUPTED), resumed, restarts)

        assertEquals(0, compose.onAllNodesWithText(string(R.string.export_resume)).fetchSemanticsNodes().size)
        compose.onNodeWithText(string(R.string.export_start_again)).performClick()

        assertEquals(1, restarts[0])
        assertEquals(emptyList<ExportHistoryEntry>(), resumed)
    }

    @Test
    fun aFailedExportOffersNeitherWayBack() {
        show(entry(ExportHistoryStatus.FAILED), mutableListOf(), IntArray(1))

        compose.onNodeWithText(string(R.string.export_history_status_failed)).assertExists()
        assertEquals(0, compose.onAllNodesWithText(string(R.string.export_resume)).fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodesWithText(string(R.string.export_start_again)).fetchSemanticsNodes().size)
    }

    private fun string(id: Int): String = ApplicationProvider.getApplicationContext<android.content.Context>().getString(id)
}

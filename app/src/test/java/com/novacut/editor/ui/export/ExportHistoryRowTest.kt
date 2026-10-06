package com.novacut.editor.ui.export

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.test.onNodeWithTag
import com.novacut.editor.R
import com.novacut.editor.engine.ExportContractDisposition
import com.novacut.editor.engine.ExportHistoryEntry
import com.novacut.editor.engine.ExportHistoryStatus
import com.novacut.editor.engine.ExportObservation
import com.novacut.editor.engine.buildExportHistoryEntry
import com.novacut.editor.model.ExportConfig
import com.novacut.editor.model.VideoCodec
import com.novacut.editor.ui.ClearCutTestTags
import androidx.compose.ui.test.assertTextEquals
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

    @Test
    fun anExactFileSaysSoAndListsWhatItHolds() {
        val observed = ExportObservation(
            valid = true,
            container = "MP4",
            videoMimeType = "video/avc",
            audioMimeType = "audio/mp4a-latm",
            width = 1920,
            height = 1080,
            frameRate = 30f,
        )
        fun finished(config: ExportConfig): ExportHistoryEntry =
            buildExportHistoryEntry(
                projectId = "project",
                projectName = "Long Render",
                status = ExportHistoryStatus.COMPLETE,
                startedAtEpochMs = 100L,
                finishedAtEpochMs = 200L,
                outputFile = temp.newFile().apply { writeBytes(ByteArray(16) { 1 }) },
                config = config,
                timelineDurationMs = 5_000L,
            )
        fun verdict(entry: ExportHistoryEntry): String {
            show(entry, mutableListOf(), IntArray(1))
            return compose.onNodeWithTag(ClearCutTestTags.EXPORT_HISTORY_CONTRACT)
                .fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString()
        }
        fun checked(entry: ExportHistoryEntry, observation: ExportObservation) =
            entry.copy(contract = entry.contract!!.evaluate(observation))

        val exact = checked(finished(ExportConfig()), observed)
        assertEquals(ExportContractDisposition.EXACT, exact.contract?.disposition)
        assertEquals(string(R.string.export_contract_exact), verdict(exact))
        compose.onNodeWithText("H.264 · AAC · 1920 × 1080 · " + context().getString(R.string.export_contract_fps, "30") + " · MP4").assertExists()
        assertEquals(0, compose.onAllNodesWithText(string(R.string.export_contract_field_video_codec), substring = true).fetchSemanticsNodes().size)
    }

    @Test
    fun aSilentCodecSwapNamesWhatWasAskedForAndWhatCameOut() {
        val entry = buildExportHistoryEntry(
            projectId = "project",
            projectName = "Long Render",
            status = ExportHistoryStatus.COMPLETE,
            startedAtEpochMs = 100L,
            finishedAtEpochMs = 200L,
            outputFile = temp.newFile("swap.mp4").apply { writeBytes(ByteArray(16) { 1 }) },
            config = ExportConfig(codec = VideoCodec.HEVC),
            timelineDurationMs = 5_000L,
        )
        val swapped = entry.copy(
            contract = entry.contract!!.evaluate(
                ExportObservation(valid = true, container = "MP4", videoMimeType = "video/avc", width = 1920, height = 1080, frameRate = 30f)
            )
        )
        show(swapped, mutableListOf(), IntArray(1))

        compose.onNodeWithTag(ClearCutTestTags.EXPORT_HISTORY_CONTRACT).assertTextEquals(string(R.string.export_contract_degraded))
        compose.onNodeWithText(
            context().getString(
                R.string.export_contract_mismatch,
                string(R.string.export_contract_field_video_codec),
                "H.265/HEVC",
                "H.264",
            )
        ).assertExists()
    }

    @Test
    fun fallbacksAcceptedDifferencesRejectionsAndOldRowsEachSaySo() {
        fun finished(fallback: String? = null, accepted: String? = null) = buildExportHistoryEntry(
            projectId = "project",
            projectName = "Long Render",
            status = ExportHistoryStatus.COMPLETE,
            startedAtEpochMs = 100L,
            finishedAtEpochMs = 200L,
            outputFile = temp.newFile().apply { writeBytes(ByteArray(16) { 1 }) },
            config = ExportConfig(),
            timelineDurationMs = 5_000L,
            fallbackSummary = fallback,
            degradationSummary = accepted,
        )
        val good = ExportObservation(valid = true, container = "MP4", videoMimeType = "video/avc", width = 1920, height = 1080, frameRate = 30f)
        val cases = listOf(
            finished(fallback = "Software encoder used.").let { it.copy(contract = it.contract!!.evaluate(good)) } to
                string(R.string.export_contract_fallback),
            finished(accepted = "User accepted 2 export warning(s).").let { it.copy(contract = it.contract!!.evaluate(good)) } to
                string(R.string.export_contract_degraded_accepted),
            finished().let { it.copy(contract = it.contract!!.evaluate(ExportObservation(valid = false, failure = "No video track"))) } to
                context().getString(R.string.export_contract_rejected, "No video track"),
            finished() to string(R.string.export_contract_unverified),
            finished().copy(contract = null) to string(R.string.export_contract_unverified),
        )
        val shown = androidx.compose.runtime.mutableStateOf(cases.first().first)
        compose.setContent {
            ExportHistoryRow(entry = shown.value, dateFormat = dateFormat, onResumeExport = {})
        }
        for ((entry, expected) in cases) {
            shown.value = entry
            compose.waitForIdle()
            compose.onNodeWithTag(ClearCutTestTags.EXPORT_HISTORY_CONTRACT).assertTextEquals(expected)
        }
    }

    @Test
    fun runsThatMadeNoFileShowNoVerdict() {
        show(entry(ExportHistoryStatus.FAILED), mutableListOf(), IntArray(1))
        assertEquals(0, compose.onAllNodesWithTag(ClearCutTestTags.EXPORT_HISTORY_CONTRACT).fetchSemanticsNodes().size)
    }

    private fun context(): android.content.Context = ApplicationProvider.getApplicationContext()

    private fun string(id: Int): String = ApplicationProvider.getApplicationContext<android.content.Context>().getString(id)
}

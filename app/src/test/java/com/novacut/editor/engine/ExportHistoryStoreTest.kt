package com.novacut.editor.engine

import android.net.FakeUri
import com.novacut.editor.model.Clip
import com.novacut.editor.model.ExportConfig
import com.novacut.editor.model.ProjectColorPolicy
import com.novacut.editor.model.SourceColorMetadata
import com.novacut.editor.model.TimelineExportRange
import com.novacut.editor.model.TimelineTimebase
import com.novacut.editor.model.Track
import com.novacut.editor.model.TrackType
import com.novacut.editor.model.VideoCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ExportHistoryStoreTest {

    @Test
    fun appendKeepsNewestEntriesFirstAndRetainsLimit() {
        val dir = Files.createTempDirectory("export-history-").toFile()
        try {
            val store = ExportHistoryStore(File(dir, "history.json"), retainCount = 2)

            val first = entry("first", startedAt = 100L)
            val second = entry("second", startedAt = 200L)
            val third = entry("third", startedAt = 300L)

            store.append(first)
            store.append(second)
            val retained = store.append(third)

            assertEquals(listOf("third", "second"), retained.map { it.projectId })
            assertEquals(listOf("third", "second"), store.read().map { it.projectId })
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun readReturnsEmptyForMalformedHistoryFile() {
        val dir = Files.createTempDirectory("export-history-malformed-").toFile()
        try {
            val file = File(dir, "history.json").apply {
                writeText("{not valid json", Charsets.UTF_8)
            }
            val store = ExportHistoryStore(file)

            assertTrue(store.read().isEmpty())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun completedEntryCapturesOutputMetadata() {
        val dir = Files.createTempDirectory("export-history-output-").toFile()
        try {
            val output = File(dir, "final.mp4").apply {
                writeBytes(ByteArray(4096) { 1 })
            }

            val entry = buildExportHistoryEntry(
                projectId = "project",
                projectName = "Road Trip",
                status = ExportHistoryStatus.COMPLETE,
                startedAtEpochMs = 100L,
                finishedAtEpochMs = 2600L,
                outputFile = output,
                config = ExportConfig(),
                timelineDurationMs = 10_000L,
                diagnosticSummary = "Video export completed."
            )

            assertEquals(output.absolutePath, entry.outputPath)
            assertEquals("final.mp4", entry.outputName)
            assertEquals(4096L, entry.outputBytes)
            assertEquals(2500L, entry.elapsedMs)
            assertEquals("Road Trip", entry.projectName)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun persistsResolvedTimelineRangeMetadata() {
        val dir = Files.createTempDirectory("export-history-range-").toFile()
        try {
            val range = TimelineExportRange(30L, 90L)
                .resolve(TimelineTimebase(30), 5_000L)
            val store = ExportHistoryStore(File(dir, "history.json"))
            val entry = buildExportHistoryEntry(
                projectId = "project",
                projectName = "Range Review",
                status = ExportHistoryStatus.COMPLETE,
                startedAtEpochMs = 100L,
                finishedAtEpochMs = 200L,
                outputFile = null,
                config = ExportConfig(),
                timelineDurationMs = range!!.durationMs,
                resolvedRange = range,
            )

            store.append(entry)
            val restored = store.read().single()

            assertEquals(30L, restored.rangeStartFrame)
            assertEquals(90L, restored.rangeEndFrameExclusive)
            assertEquals(1_000L, restored.rangeStartMs)
            assertEquals(3_000L, restored.rangeEndMs)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun persistsAndRemovesCancelledResumeMetadata() {
        val dir = Files.createTempDirectory("export-history-resume-").toFile()
        try {
            val partial = File(dir, "partial.mp4").apply { writeBytes(ByteArray(12) { 2 }) }
            val store = ExportHistoryStore(File(dir, "history.json"))
            val entry = buildExportHistoryEntry(
                projectId = "project",
                projectName = "Resume Me",
                status = ExportHistoryStatus.CANCELLED,
                startedAtEpochMs = 100L,
                finishedAtEpochMs = 200L,
                outputFile = partial,
                config = ExportConfig(),
                timelineDurationMs = 5_000L,
                resumePartialFile = partial,
                resumeProjectFingerprint = "project-fingerprint",
                resumeConfigFingerprint = "config-fingerprint",
            )

            store.append(entry)
            val restored = store.read().single()

            assertEquals(partial.absolutePath, restored.resumePartialPath)
            assertEquals("project-fingerprint", restored.resumeProjectFingerprint)
            assertEquals("config-fingerprint", restored.resumeConfigFingerprint)
            assertEquals(emptyList<ExportHistoryEntry>(), store.remove(entry.id))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun interruptedEntriesRoundTripAndOnlyStoppedExportsOfferResume() {
        val dir = Files.createTempDirectory("export-history-interrupted-").toFile()
        try {
            val partial = File(dir, "partial.mp4").apply { writeBytes(ByteArray(12) { 3 }) }
            val store = ExportHistoryStore(File(dir, "history.json"))
            store.append(
                buildExportHistoryEntry(
                    projectId = "project",
                    projectName = "Long Render",
                    status = ExportHistoryStatus.INTERRUPTED,
                    startedAtEpochMs = 100L,
                    finishedAtEpochMs = 200L,
                    outputFile = partial,
                    config = ExportConfig(),
                    timelineDurationMs = 5_000L,
                    resumePartialFile = partial,
                    resumeProjectFingerprint = "project-fingerprint",
                    resumeConfigFingerprint = "config-fingerprint",
                )
            )

            val restored = ExportHistoryStore(File(dir, "history.json")).read().single()

            assertEquals(ExportHistoryStatus.INTERRUPTED, restored.status)
            assertEquals(partial.absolutePath, restored.resumePartialPath)
            assertEquals(
                setOf(ExportHistoryStatus.CANCELLED, ExportHistoryStatus.INTERRUPTED),
                ExportHistoryStatus.entries.filter { it.keepsResumablePartial }.toSet(),
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun completedExportRecordsRequestedColorAndWhatTheFileCarries() {
        val dir = Files.createTempDirectory("export-history-color-").toFile()
        try {
            val output = File(dir, "hdr.mp4").apply { writeBytes(ByteArray(16)) }
            val entry = buildExportHistoryEntry(
                projectId = "project",
                projectName = "Sunset",
                status = ExportHistoryStatus.COMPLETE,
                startedAtEpochMs = 100L,
                finishedAtEpochMs = 200L,
                outputFile = output,
                config = ExportConfig(codec = VideoCodec.HEVC, colorPolicy = ProjectColorPolicy.KEEP_HDR),
                timelineDurationMs = 1_000L,
                tracks = listOf(hlgTrack()),
            )
            assertEquals(DeliveredColor.HLG, entry.requestedColor)
            assertNull(entry.observedColor)

            val read = mutableListOf<File>()
            ExportHistoryStore(File(dir, "history.json"), observeColor = { read += it; DeliveredColor.SDR })
                .append(entry)

            val restored = ExportHistoryStore(File(dir, "history.json")).read().single()
            assertEquals(listOf(output), read)
            assertEquals(DeliveredColor.HLG, restored.requestedColor)
            assertEquals(DeliveredColor.SDR, restored.observedColor)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun colorIsOnlyRecordedForTaggedVideoAndOnlyReadBackFromFinishedFiles() {
        val dir = Files.createTempDirectory("export-history-color-skip-").toFile()
        try {
            val tracks = listOf(hlgTrack())
            assertEquals(DeliveredColor.SDR, requestedExportColor(ExportConfig(), tracks))
            assertNull(requestedExportColor(ExportConfig(colorPolicy = ProjectColorPolicy.KEEP_HDR, exportAudioOnly = true), tracks))
            assertNull(requestedExportColor(ExportConfig(colorPolicy = ProjectColorPolicy.KEEP_HDR, exportAsGif = true), tracks))
            assertNull(requestedExportColor(ExportConfig(), emptyList()))

            val output = File(dir, "failed.mp4").apply { writeBytes(ByteArray(16)) }
            val failed = buildExportHistoryEntry(
                projectId = "project",
                projectName = "Sunset",
                status = ExportHistoryStatus.FAILED,
                startedAtEpochMs = 100L,
                finishedAtEpochMs = 200L,
                outputFile = output,
                config = ExportConfig(colorPolicy = ProjectColorPolicy.KEEP_HDR),
                timelineDurationMs = 1_000L,
                tracks = tracks,
            )
            val store = ExportHistoryStore(File(dir, "history.json"), observeColor = { error("read a failed export") })
            store.append(failed)
            store.append(entry("older-build", 50L))

            val restored = store.read().associateBy { it.projectId }
            assertNull(restored.getValue("project").observedColor)
            assertNull(restored.getValue("older-build").requestedColor)
            assertNull(restored.getValue("older-build").observedColor)
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun hlgTrack() = Track(
        type = TrackType.VIDEO,
        index = 0,
        clips = listOf(
            Clip(
                sourceUri = FakeUri,
                sourceDurationMs = 1_000L,
                timelineStartMs = 0L,
                trimStartMs = 0L,
                trimEndMs = 1_000L,
                sourceColorMetadata = SourceColorMetadata(mimeType = "video/hevc", colorTransfer = "HLG", inspectedAtMs = 1L),
            )
        ),
    )

    private fun entry(projectId: String, startedAt: Long): ExportHistoryEntry {
        return buildExportHistoryEntry(
            projectId = projectId,
            projectName = projectId,
            status = ExportHistoryStatus.COMPLETE,
            startedAtEpochMs = startedAt,
            finishedAtEpochMs = startedAt + 500L,
            outputFile = null,
            config = ExportConfig(),
            timelineDurationMs = 1_000L
        )
    }
}

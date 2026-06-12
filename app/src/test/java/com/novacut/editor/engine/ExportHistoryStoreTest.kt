package com.novacut.editor.engine

import com.novacut.editor.model.ExportConfig
import org.junit.Assert.assertEquals
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

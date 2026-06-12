package com.novacut.editor.ui.editor

import com.novacut.editor.engine.MediaHealthIssue
import com.novacut.editor.engine.MediaHealthIssueType
import com.novacut.editor.engine.MediaHealthReport
import com.novacut.editor.engine.MediaHealthSeverity
import com.novacut.editor.engine.MediaRelinkProbe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportMediaPreflightTest {

    @Test
    fun evaluateBlocksHealthBlockers() {
        val result = ExportMediaPreflight.evaluate(
            healthReport = report(
                MediaHealthIssue(
                    type = MediaHealthIssueType.MISSING_LOCAL_FILE,
                    severity = MediaHealthSeverity.BLOCKING,
                    subjectId = "clip",
                    message = "missing"
                )
            ),
            relinkReports = emptyMap()
        )

        assertFalse(result.canExport)
        assertEquals(1, result.blockingCount)
        assertTrue(result.message.contains("blocked"))
    }

    @Test
    fun evaluateBlocksMissingRelinkReports() {
        val result = ExportMediaPreflight.evaluate(
            healthReport = report(),
            relinkReports = mapOf(
                "clip" to MediaRelinkProbe.ClipRelinkReport(
                    clipId = "clip",
                    sourceUri = "file:///missing.mp4",
                    state = MediaRelinkProbe.RelinkState.MISSING
                )
            )
        )

        assertFalse(result.canExport)
        assertEquals(1, result.blockingCount)
    }

    @Test
    fun evaluateAllowsWarnings() {
        val result = ExportMediaPreflight.evaluate(
            healthReport = report(
                MediaHealthIssue(
                    type = MediaHealthIssueType.EXTERNAL_SOURCE,
                    severity = MediaHealthSeverity.WARNING,
                    subjectId = "clip",
                    message = "external"
                )
            ),
            relinkReports = mapOf(
                "overlay" to MediaRelinkProbe.ClipRelinkReport(
                    clipId = "overlay",
                    sourceUri = "asset:///overlay.png",
                    state = MediaRelinkProbe.RelinkState.UNKNOWN
                )
            )
        )

        assertTrue(result.canExport)
        assertEquals(0, result.blockingCount)
        assertEquals(2, result.warningCount)
    }

    @Test
    fun evaluateAllowsCleanProjects() {
        val result = ExportMediaPreflight.evaluate(
            healthReport = report(),
            relinkReports = emptyMap()
        )

        assertTrue(result.canExport)
        assertEquals(0, result.blockingCount)
        assertEquals("Media ready for export.", result.message)
    }

    private fun report(vararg issues: MediaHealthIssue): MediaHealthReport {
        return MediaHealthReport(
            totalReferences = 1,
            managedAssets = 1,
            localReadyReferences = 1,
            externalReferences = 0,
            issues = issues.toList()
        )
    }
}

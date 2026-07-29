package com.novacut.editor.ui.editor

import com.novacut.editor.engine.MediaHealthIssue
import com.novacut.editor.engine.MediaHealthIssueType
import com.novacut.editor.engine.MediaHealthReport
import com.novacut.editor.engine.MediaHealthSeverity
import com.novacut.editor.engine.MediaRelinkProbe
import com.novacut.editor.engine.ProjectDependency
import com.novacut.editor.engine.ProjectDependencyKind
import com.novacut.editor.engine.ProjectDependencyManifest
import com.novacut.editor.engine.ProjectDependencyRequest
import com.novacut.editor.engine.ProjectDependencyStatus
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

    @Test
    fun evaluateBlocksAndNamesMissingRequiredDependencies() {
        val result = ExportMediaPreflight.evaluate(
            healthReport = report(),
            relinkReports = emptyMap(),
            dependencies = ProjectDependencyManifest(
                listOf(
                    ProjectDependency(
                        request = ProjectDependencyRequest(
                            kind = ProjectDependencyKind.LUT,
                            reference = "/looks/brand.cube",
                            label = "Brand look",
                        ),
                        status = ProjectDependencyStatus.MISSING,
                    )
                )
            ),
        )

        assertFalse(result.canExport)
        assertEquals(1, result.blockingCount)
        assertTrue(result.message.contains("Brand look"))
        assertTrue(result.message.contains("missing"))
    }

    @Test
    fun evaluateAllowsOnlyNamedExplicitDependencyFallbacks() {
        val result = ExportMediaPreflight.evaluate(
            healthReport = report(),
            relinkReports = emptyMap(),
            dependencies = ProjectDependencyManifest(
                listOf(
                    ProjectDependency(
                        request = ProjectDependencyRequest(
                            kind = ProjectDependencyKind.CUSTOM_FONT,
                            reference = "/fonts/missing.ttf",
                            label = "Brand font",
                            fallbackAllowed = true,
                            fallbackName = "sans-serif",
                        ),
                        status = ProjectDependencyStatus.MISSING,
                    )
                )
            ),
        )

        assertTrue(result.canExport)
        assertEquals(1, result.warningCount)
        assertTrue(result.message.contains("Brand font → sans-serif"))
    }

    @Test
    fun evaluateWarnsAboutUnrenderedMixerEdits() {
        val result = ExportMediaPreflight.evaluate(
            healthReport = report(),
            relinkReports = emptyMap(),
            unrenderedMixerEditCount = 2,
        )

        assertTrue(result.canExport)
        assertEquals(0, result.blockingCount)
        assertEquals(2, result.warningCount)
        assertTrue(result.message.contains("pan or audio effects"))
        assertTrue(result.message.contains("2 tracks"))
    }

    @Test
    fun evaluateDoesNotWarnWhenNoUnrenderedMixerEdits() {
        val result = ExportMediaPreflight.evaluate(
            healthReport = report(),
            relinkReports = emptyMap(),
            unrenderedMixerEditCount = 0,
        )

        assertTrue(result.canExport)
        assertEquals(0, result.warningCount)
        assertEquals("Media ready for export.", result.message)
    }

    @Test
    fun everyBlockerAndWarningIsItemizedNotJustCounted() {
        val result = ExportMediaPreflight.evaluate(
            healthReport = report(
                MediaHealthIssue(
                    type = MediaHealthIssueType.MISSING_LOCAL_FILE,
                    severity = MediaHealthSeverity.BLOCKING,
                    subjectId = "clip",
                    message = "clip source is gone"
                ),
                MediaHealthIssue(
                    type = MediaHealthIssueType.EXTERNAL_SOURCE,
                    severity = MediaHealthSeverity.WARNING,
                    subjectId = "clip2",
                    message = "clip2 lives outside the app"
                )
            ),
            relinkReports = emptyMap(),
        )

        assertEquals(listOf("clip source is gone"), result.blockers)
        assertEquals(listOf("clip2 lives outside the app"), result.warnings)
        assertEquals(result.blockers.size, result.blockingCount)
        assertEquals(result.warnings.size, result.warningCount)
    }

    @Test
    fun renderIntentFallbacksBecomeWarningsThatNeedConsent() {
        val fallback = ExportIntentFallback(
            stage = "reverse-render",
            subjectId = "clip-7",
            message = "Clip clip-7 is reversed, but reverse rendering is unavailable on this device."
        )
        val result = ExportMediaPreflight.evaluate(
            healthReport = report(),
            relinkReports = emptyMap(),
            intentFallbacks = listOf(fallback),
        )

        assertTrue(result.canExport)
        assertTrue("A fallback that changes the output must require consent", result.requiresConsent)
        assertEquals(1, result.warningCount)
        assertEquals(listOf(fallback.message), result.warnings)
        assertEquals(listOf(fallback), result.intentFallbacks)
        assertTrue(result.message.contains("cannot be rendered as edited"))
    }

    @Test
    fun cleanProjectNeedsNoConsent() {
        val result = ExportMediaPreflight.evaluate(
            healthReport = report(),
            relinkReports = emptyMap(),
        )

        assertFalse(result.requiresConsent)
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun blockedProjectStillItemizesWarningsForTheReport() {
        val result = ExportMediaPreflight.evaluate(
            healthReport = report(
                MediaHealthIssue(
                    type = MediaHealthIssueType.MISSING_LOCAL_FILE,
                    severity = MediaHealthSeverity.BLOCKING,
                    subjectId = "clip",
                    message = "missing"
                )
            ),
            relinkReports = emptyMap(),
            unrenderedMixerEditCount = 1,
        )

        assertFalse(result.canExport)
        // Blocked exports never reach the consent dialog, so requiresConsent is false
        // even though warnings exist — they still ride along in the report.
        assertFalse(result.requiresConsent)
        assertEquals(1, result.warnings.size)
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

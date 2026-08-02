package com.novacut.editor.engine

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shared diagnostics bundle deliberately omits the raw encoder error text, because
 * a codec error can quote a filename or a caption and generic redaction cannot
 * recognise arbitrary user text. That left a triager with `errorClass` alone, unable to
 * tell two different failures apart — so the omission is now the user's choice, with
 * the default unchanged.
 */
class ExportIncidentConsentTest {

    private val bundle = ExportIncidentBundle(
        id = "incident-1",
        appVersion = "3.77.0",
        deviceModel = "Pixel",
        androidSdk = 36,
        projectId = "project-1",
        projectName = "Holiday cut",
        failedPhase = "encode",
        errorClass = "ExportException",
        errorMessage = "MediaCodec failed on /storage/emulated/0/DCIM/holiday.mp4",
        encoderPath = "hardware",
        codecLabel = "H.264",
        resolutionLabel = "1080p",
        frameRate = 30,
        exportAudioOnly = false,
        hdrRequested = false,
        streamCopyAttempted = false,
        timelineDurationMs = 12_000L,
        elapsedMs = 4_000L,
        progressSamples = listOf(0.1f, 0.4f),
        mediaWarningCount = 0,
        mediaBlockingCount = 0,
        mediaHealthSummary = null,
        timestampEpochMs = 1_770_000_000_000L,
    )

    @Test
    fun theRawErrorTextIsWithheldByDefault() {
        val json = ExportIncidentStore.toDiagnosticJson(bundle, "project-1")

        assertFalse("raw encoder text must not ship without consent", json.has("errorMessage"))
        assertEquals("ExportException", json.optString("errorClass"))
    }

    @Test
    fun consentAddsTheRawErrorTextAndNothingElse() {
        val withoutConsent = ExportIncidentStore.toDiagnosticJson(bundle, "project-1")
        val withConsent = ExportIncidentStore.toDiagnosticJson(bundle, "project-1", includeRawErrorText = true)

        assertTrue(withConsent.has("errorMessage"))
        assertEquals(bundle.errorMessage, withConsent.optString("errorMessage"))
        assertEquals(
            "consent must add exactly one field",
            withoutConsent.length() + 1,
            withConsent.length()
        )
    }

    @Test
    fun consentNeverReintroducesTheProjectName() {
        val json: JSONObject =
            ExportIncidentStore.toDiagnosticJson(bundle, "project-1", includeRawErrorText = true)

        assertFalse("the shared bundle is pseudonymised", json.has("projectName"))
        assertFalse(json.has("projectId"))
        assertEquals("project-1", json.optString("projectPseudonym"))
    }

    @Test
    fun theCopyableReportNamesTheStageAndTheNextStep() {
        val report = bundle.toCopyableReport()

        assertTrue(report.contains("stage: encode"))
        assertTrue(report.contains("ExportException"))
        assertTrue("a report with no next step is not actionable", report.contains("what to try:"))
    }
}

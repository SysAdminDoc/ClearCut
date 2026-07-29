package com.novacut.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Export failed" is not a report. Every failure a user can copy out must name
 * the stage, the codec, the device, the clip (redacted), and what to do next.
 */
class ExportIncidentReportTest {

    @Test
    fun theCopyableReportNamesStageCodecDeviceAssetAndRemediation() {
        val report = bundle(
            failedPhase = "reverse-render",
            subjectAssetId = RedactedLog.assetId("content://media/1"),
        ).toCopyableReport()

        assertTrue(report.contains("stage: reverse-render"))
        assertTrue(report.contains("codec: H.265"))
        assertTrue(report.contains("device: Pixel Test"))
        assertTrue(report.contains("clip: asset#"))
        assertTrue(report.contains("what to try:"))
        assertTrue(report.contains("Shorten the reversed clip"))
    }

    @Test
    fun theReportNeverCarriesARawPath() {
        val report = bundle(
            failedPhase = "encoder",
            subjectAssetId = RedactedLog.path("/storage/emulated/0/DCIM/private.mp4"),
        ).toCopyableReport()

        assertFalse(report.contains("private.mp4"))
        assertFalse(report.contains("DCIM"))
    }

    @Test
    fun everyStageMapsToConcreteGuidance() {
        val phases = listOf("reverse-render", "storage", "subtitle-burn", "audio-encoder", "encoder", "setup")
        for (phase in phases) {
            val guidance = ExportRemediation.forPhase(phase, hdrRequested = false, streamCopyAttempted = false)
            assertTrue("$phase must have specific guidance", guidance.isNotBlank())
            assertFalse("$phase fell through to the generic message", guidance == ExportRemediation.UNKNOWN)
        }
    }

    @Test
    fun encoderGuidanceAdaptsToWhatWasRequested() {
        val hdr = ExportRemediation.forPhase("encoder", hdrRequested = true, streamCopyAttempted = false)
        val streamCopy = ExportRemediation.forPhase("encoder", hdrRequested = false, streamCopyAttempted = true)
        val plain = ExportRemediation.forPhase("encoder", hdrRequested = false, streamCopyAttempted = false)

        assertTrue(hdr.contains("HDR"))
        assertTrue(streamCopy.contains("stream-copy"))
        assertTrue(plain.contains("H.264"))
    }

    @Test
    fun anUnknownStageStillTellsTheUserSomething() {
        val guidance = ExportRemediation.forPhase("something-new", hdrRequested = false, streamCopyAttempted = false)

        assertEquals(ExportRemediation.UNKNOWN, guidance)
        assertTrue(guidance.isNotBlank())
    }

    @Test
    fun aReportWithoutASubjectOmitsTheClipLineRatherThanPrintingNull() {
        val report = bundle(failedPhase = "encoder", subjectAssetId = null).toCopyableReport()

        assertFalse(report.contains("clip:"))
        assertFalse(report.contains("null"))
    }

    private fun bundle(failedPhase: String, subjectAssetId: String?) = ExportIncidentBundle(
        id = "0123456789abcdef",
        appVersion = "3.77.0",
        deviceModel = "Pixel Test",
        androidSdk = 36,
        projectId = "project-1",
        projectName = "Test",
        failedPhase = failedPhase,
        errorClass = "IllegalStateException",
        errorMessage = "encoder rejected the format",
        encoderPath = "hardware: c2.test.hevc",
        codecLabel = "H.265",
        resolutionLabel = "4K",
        frameRate = 30,
        exportAudioOnly = false,
        hdrRequested = false,
        streamCopyAttempted = false,
        timelineDurationMs = 120_000L,
        elapsedMs = 4_200L,
        progressSamples = listOf(0f, 0.2f),
        mediaWarningCount = 1,
        mediaBlockingCount = 0,
        mediaHealthSummary = "3 refs, 1 warnings, 0 blocking",
        timestampEpochMs = 1_753_800_000_000L,
        subjectAssetId = subjectAssetId,
        remediation = ExportRemediation.forPhase(failedPhase, hdrRequested = false, streamCopyAttempted = false),
    )
}

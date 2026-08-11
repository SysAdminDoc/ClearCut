package com.novacut.editor.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * "Analysed and found nothing" and "could not analyse" are different answers. Several
 * AI paths collapsed the second into the first and reported it as good news: a crashed
 * transcription became "No speech detected", a failed motion analysis became "Video is
 * already stable", and a static zoom became "Basic stabilization applied".
 */
class AnalysisOutcomeHonestyTest {

    @Test
    fun aCaptionRunSeparatesEmptyResultsFromFailure() {
        val analyzed = CaptionOutcome.Analyzed(emptyList())
        val failed = CaptionOutcome.Failed("decode failed")

        assertTrue("an empty transcript is still a completed run", analyzed.captions.isEmpty())
        assertTrue("a failure must name why", failed.reason.isNotBlank())
        assertFalse("the two must not be the same value", analyzed == failed)
    }

    @Test
    fun aDefaultStabilizationResultIsNotAMeasurementOfSteadiness() {
        val notRun = StabilizationResult()
        val measured = StabilizationResult(shakeMagnitude = 0f, confidence = 0.9f, analyzed = true)

        assertFalse("a default result must not read as analysed", notRun.analyzed)
        assertTrue("genuinely steady footage is analysed with zero shake", measured.analyzed)
        assertTrue(measured.shakeMagnitude == 0f)
    }

    @Test
    fun captionConfidenceIsAbsentRatherThanInvented() {
        val transcribed = CaptionEntry(startMs = 0, endMs = 500, text = "hello")

        assertNull(
            "the Whisper path stamped a flat 0.95 on every caption; absent beats invented",
            transcribed.confidence
        )
    }

    @Test
    fun theZoomOnlyPathIsNotCalledStabilization() {
        val delegate = locate(
            "app/src/main/java/com/novacut/editor/ui/editor/AiToolsDelegate.kt"
        ).readText()
        val strings = locate("app/src/main/res/values/strings.xml").readText()

        assertFalse(
            "the scaleX/scaleY path applies no counter-motion and must not claim stabilization",
            delegate.contains("ai_basic_stabilization_applied_toast")
        )
        assertTrue("stabilization must expose a reviewable motion result", delegate.contains("StabilizationPreview"))
        assertTrue("stabilization must persist motion data instead of baking a source", delegate.contains("StabilizationData"))
        val copy = strings
            .substringAfter("<plurals name=\"ai_stabilization_preview_body\">")
            .substringBefore("</plurals>")
        assertTrue("the preview copy must say the source is unchanged", copy.contains("unchanged", ignoreCase = true))
    }

    @Test
    fun aFailedDisclosureSidecarIsNotSilent() {
        val delegate = locate(
            "app/src/main/java/com/novacut/editor/ui/editor/ExportDelegate.kt"
        ).readText()

        assertTrue(
            "the sidecar writer must report failure to its caller",
            delegate.contains("): Boolean {") && delegate.contains("reportSidecarOutcome(")
        )
        assertTrue(
            "and the caller must surface it",
            delegate.contains("R.string.export_ai_disclosure_sidecar_failed")
        )
    }

    private fun locate(relativePath: String): File =
        listOf(File(relativePath), File("../$relativePath")).first { it.exists() }
}

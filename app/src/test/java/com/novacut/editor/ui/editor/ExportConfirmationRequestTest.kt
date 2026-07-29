package com.novacut.editor.ui.editor

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * An export the user let through despite preflight warnings must leave a record
 * saying which render intents were traded away, otherwise the produced file
 * silently differs from the timeline.
 */
class ExportConfirmationRequestTest {

    @Test
    fun acceptedFallbackSummaryNamesEveryStageAndSubject() {
        val request = request(
            warnings = listOf("Clip clip-7 would export forward.", "Track 1 pan is not rendered."),
            fallbacks = listOf(
                ExportIntentFallback("reverse-render", "clip-7", "Clip clip-7 would export forward.")
            )
        )

        val summary = request.acceptedFallbackSummary()

        assertTrue(summary.contains("2 export warning"))
        assertTrue(summary.contains("1 render-intent fallback"))
        assertTrue(summary.contains("reverse-render/clip-7"))
        assertTrue(summary.contains("Clip clip-7 would export forward."))
    }

    @Test
    fun acceptedSummaryStillListsWarningsWithoutIntentFallbacks() {
        val request = request(
            warnings = listOf("Audio will be normalized."),
            fallbacks = emptyList()
        )

        val summary = request.acceptedFallbackSummary()

        assertTrue(summary.contains("1 export warning"))
        assertTrue(summary.contains("Audio will be normalized."))
    }

    private fun request(warnings: List<String>, fallbacks: List<ExportIntentFallback>) =
        ExportConfirmationRequest(
            outputDirPath = "/tmp/exports",
            preferredOutputName = "clip",
            summary = "Export can continue with ${warnings.size} warnings.",
            warnings = warnings,
            intentFallbacks = fallbacks,
        )
}

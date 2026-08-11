package com.novacut.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShaderEffectTest {
    @Test
    fun compileFailureUsesFallbackAndProducesTypedDegradationOutcome() {
        val ledger = RenderDegradationLedger()
        val attemptedSources = mutableListOf<String>()

        val result = compileFragmentWithFallback(
            source = "broken shader",
            fallback = "passthrough shader",
            effectName = "VHS_RETRO",
            ledger = ledger,
            compile = { source ->
                attemptedSources += source
                if (source == "broken shader") error("synthetic compile failure")
                "compiled fallback"
            },
        )

        assertEquals("compiled fallback", result)
        assertEquals(listOf("broken shader", "passthrough shader"), attemptedSources)
        val outcome = requireNotNull(ledger.outcome())
        assertEquals(RenderDegradationType.SHADER_COMPILE, outcome.entries.single().type)
        assertEquals("VHS_RETRO", outcome.entries.single().effectName)
        assertEquals(1, outcome.entries.single().count)
        assertTrue(outcome.summary.contains("VHS_RETRO"))
    }

    @Test
    fun segmentationFailuresAccumulateAsFrameCountAndRemainAbsentWhenHealthy() {
        val ledger = RenderDegradationLedger()
        assertNull(ledger.outcome())

        repeat(3) {
            ledger.record(RenderDegradationType.SEGMENTATION_FRAME, "BG_REMOVAL")
        }

        val outcome = requireNotNull(ledger.outcome())
        assertEquals(RenderDegradationType.SEGMENTATION_FRAME, outcome.entries.single().type)
        assertEquals(3, outcome.entries.single().count)
        assertTrue(outcome.summary.contains("3 frame(s)"))
        val exception = RenderDegradationException(outcome)
        assertTrue(exception.message.orEmpty().contains("BG_REMOVAL"))
    }

    @Test
    fun exportPropagationCarriesTheRecordedOutcomeAsATypedFailure() {
        val ledger = RenderDegradationLedger().apply {
            record(RenderDegradationType.SHADER_COMPILE, "VHS_RETRO")
            repeat(4) {
                record(RenderDegradationType.SEGMENTATION_FRAME, "BG_REMOVAL")
            }
        }

        val failure = renderDegradationExceptionOrNull(ledger.outcome())

        assertEquals(
            "VHS_RETRO: shader compile fallback (1 occurrence(s)); " +
                "BG_REMOVAL: neutral mask fallback (4 frame(s))",
            requireNotNull(failure).outcome.summary,
        )
        assertNull(renderDegradationExceptionOrNull(null))
    }
}

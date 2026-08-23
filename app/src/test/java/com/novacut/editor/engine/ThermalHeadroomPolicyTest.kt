package com.novacut.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThermalHeadroomPolicyTest {

    @Test
    fun decide_noneStatusAndLowHeadroom_runsFullSpeed() {
        val decision = decide(status = ThermalHeadroomPolicy.ThermalStatus.NONE, headroom = 0.1f)

        assertEquals(ThermalHeadroomPolicy.ExportAction.FULL_SPEED, decision.action)
        assertFalse(decision.shouldNotifyUser)
    }

    @Test
    fun decide_fallbackLightThreshold_continuesWithTruthfulAdvisory() {
        val decision = decide(status = ThermalHeadroomPolicy.ThermalStatus.NONE, headroom = 0.72f)

        assertEquals(
            ThermalHeadroomPolicy.ExportAction.CONTINUE_WITH_LIGHT_ADVISORY,
            decision.action,
        )
        assertTrue(decision.shouldNotifyUser)
    }

    @Test
    fun decide_fallbackModerateThreshold_continuesWithHeavyAdvisory() {
        val decision = decide(status = ThermalHeadroomPolicy.ThermalStatus.NONE, headroom = 0.9f)

        assertEquals(
            ThermalHeadroomPolicy.ExportAction.CONTINUE_WITH_HEAVY_ADVISORY,
            decision.action,
        )
    }

    @Test
    fun decide_fallbackSevereThreshold_advisesCoolingWithoutClaimingPause() {
        val decision = decide(status = ThermalHeadroomPolicy.ThermalStatus.NONE, headroom = 0.96f)

        assertEquals(
            ThermalHeadroomPolicy.ExportAction.CONTINUE_WITH_COOLING_ADVISORY,
            decision.action,
        )
    }

    @Test
    fun decide_severeThroughEmergencyStatus_advisesCoolingWhileContinuing() {
        listOf(
            ThermalHeadroomPolicy.ThermalStatus.SEVERE,
            ThermalHeadroomPolicy.ThermalStatus.CRITICAL,
            ThermalHeadroomPolicy.ThermalStatus.EMERGENCY,
        ).forEach { status ->
            val decision = decide(status = status, headroom = 0.1f)
            assertEquals(
                "$status should continue with cooling advice",
                ThermalHeadroomPolicy.ExportAction.CONTINUE_WITH_COOLING_ADVISORY,
                decision.action,
            )
        }
    }

    @Test
    fun decide_shutdown_cancels() {
        val decision = decide(status = ThermalHeadroomPolicy.ThermalStatus.SHUTDOWN, headroom = 0.5f)

        assertEquals(ThermalHeadroomPolicy.ExportAction.CANCEL, decision.action)
        assertTrue(decision.shouldNotifyUser)
    }

    @Test
    fun decide_nanHeadroom_fallsBackToCurrentStatus() {
        val decision = decide(status = ThermalHeadroomPolicy.ThermalStatus.LIGHT, headroom = Float.NaN)

        assertEquals(
            ThermalHeadroomPolicy.ExportAction.CONTINUE_WITH_LIGHT_ADVISORY,
            decision.action,
        )
    }

    @Test
    fun decide_deviceThresholdsOverrideFallbackThresholds() {
        val thresholds = mapOf(
            ThermalHeadroomPolicy.ThermalStatus.LIGHT.osValue to 0.40f,
            ThermalHeadroomPolicy.ThermalStatus.MODERATE.osValue to 0.60f,
            ThermalHeadroomPolicy.ThermalStatus.SEVERE.osValue to 0.80f,
        )

        val decision = decide(
            status = ThermalHeadroomPolicy.ThermalStatus.NONE,
            headroom = 0.65f,
            thresholds = thresholds,
        )

        assertEquals(
            ThermalHeadroomPolicy.ExportAction.CONTINUE_WITH_HEAVY_ADVISORY,
            decision.action,
        )
    }

    @Test
    fun decide_invalidDeviceThresholdsUseDocumentedFallbacks() {
        val decision = decide(
            status = ThermalHeadroomPolicy.ThermalStatus.NONE,
            headroom = 0.90f,
            thresholds = mapOf(
                99 to 0.20f,
                ThermalHeadroomPolicy.ThermalStatus.LIGHT.osValue to Float.NaN,
            ),
        )

        assertEquals(
            ThermalHeadroomPolicy.ExportAction.CONTINUE_WITH_HEAVY_ADVISORY,
            decision.action,
        )
    }

    @Test
    fun decide_usesMostConservativeCurrentOrForecastSignal() {
        val decision = decide(status = ThermalHeadroomPolicy.ThermalStatus.MODERATE, headroom = 0.4f)

        assertEquals(
            ThermalHeadroomPolicy.ExportAction.CONTINUE_WITH_HEAVY_ADVISORY,
            decision.action,
        )
    }

    @Test
    fun decide_notificationOnlyFiresWhenActionChanges() {
        val first = decide(
            status = ThermalHeadroomPolicy.ThermalStatus.NONE,
            headroom = 0.72f,
            previousAction = ThermalHeadroomPolicy.ExportAction.FULL_SPEED,
        )
        val second = decide(
            status = ThermalHeadroomPolicy.ThermalStatus.NONE,
            headroom = 0.72f,
            previousAction = ThermalHeadroomPolicy.ExportAction.CONTINUE_WITH_LIGHT_ADVISORY,
        )

        assertTrue(first.shouldNotifyUser)
        assertFalse(second.shouldNotifyUser)
        assertEquals(
            ThermalHeadroomPolicy.UserMessageKey.NONE,
            ThermalHeadroomPolicy.userMessageAfterTransition(
                second,
                ThermalHeadroomPolicy.EngineTransition.CONTINUING_UNCHANGED,
            ),
        )
    }

    @Test
    fun advisoryCopyRequiresConfirmedContinuingEngineState() {
        val decision = decide(status = ThermalHeadroomPolicy.ThermalStatus.SEVERE, headroom = 0.2f)

        assertEquals(
            ThermalHeadroomPolicy.UserMessageKey.NONE,
            ThermalHeadroomPolicy.userMessageAfterTransition(
                decision,
                ThermalHeadroomPolicy.EngineTransition.NOT_APPLIED,
            ),
        )
        assertEquals(
            ThermalHeadroomPolicy.UserMessageKey.ADVISORY_COOLING,
            ThermalHeadroomPolicy.userMessageAfterTransition(
                decision,
                ThermalHeadroomPolicy.EngineTransition.CONTINUING_UNCHANGED,
            ),
        )
    }

    @Test
    fun cancellationCopyRequiresConfirmedCancelledEngineState() {
        val decision = decide(status = ThermalHeadroomPolicy.ThermalStatus.SHUTDOWN, headroom = 0.2f)

        assertEquals(
            ThermalHeadroomPolicy.UserMessageKey.NONE,
            ThermalHeadroomPolicy.userMessageAfterTransition(
                decision,
                ThermalHeadroomPolicy.EngineTransition.NOT_APPLIED,
            ),
        )
        assertEquals(
            ThermalHeadroomPolicy.UserMessageKey.CANCELLED_FOR_SHUTDOWN,
            ThermalHeadroomPolicy.userMessageAfterTransition(
                decision,
                ThermalHeadroomPolicy.EngineTransition.CANCELLED,
            ),
        )
    }

    @Test
    fun forecastPollingFloor_matchesAndroidGuidance() {
        assertEquals(10_000L, ThermalHeadroomPolicy.MIN_FORECAST_POLL_INTERVAL_MS)
    }

    @Test
    fun thermalStatus_fromOsRecognizesValidValuesAndFallsBackToNone() {
        ThermalHeadroomPolicy.ThermalStatus.entries.forEach { status ->
            assertEquals(status, ThermalHeadroomPolicy.ThermalStatus.fromOs(status.osValue))
        }
        assertEquals(
            ThermalHeadroomPolicy.ThermalStatus.NONE,
            ThermalHeadroomPolicy.ThermalStatus.fromOs(99),
        )
    }

    @Test
    fun shouldOfferOvernightSchedule_gatesOnThirtyMinutes() {
        assertFalse(ThermalHeadroomPolicy.shouldOfferOvernightSchedule(0L))
        assertFalse(ThermalHeadroomPolicy.shouldOfferOvernightSchedule(29L * 60L * 1_000L))
        assertTrue(ThermalHeadroomPolicy.shouldOfferOvernightSchedule(30L * 60L * 1_000L))
        assertTrue(ThermalHeadroomPolicy.shouldOfferOvernightSchedule(2L * 3_600L * 1_000L))
    }

    private fun decide(
        status: ThermalHeadroomPolicy.ThermalStatus,
        headroom: Float,
        thresholds: Map<Int, Float> = emptyMap(),
        previousAction: ThermalHeadroomPolicy.ExportAction = ThermalHeadroomPolicy.ExportAction.FULL_SPEED,
    ): ThermalHeadroomPolicy.Decision = ThermalHeadroomPolicy.decide(
        status = status,
        forecastHeadroom = headroom,
        thresholds = thresholds,
        previousAction = previousAction,
    )
}

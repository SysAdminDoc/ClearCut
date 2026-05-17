package com.novacut.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** R8.5 — ThermalHeadroomPolicy contract tests. */
class ThermalHeadroomPolicyTest {

    @Test
    fun decide_noneStatusAndLowHeadroom_runsFullSpeed() {
        val d = ThermalHeadroomPolicy.decide(
            status = ThermalHeadroomPolicy.ThermalStatus.NONE,
            headroom = 0.1f
        )
        assertEquals(ThermalHeadroomPolicy.ExportAction.FULL_SPEED, d.action)
        assertEquals(ThermalHeadroomPolicy.MAX_PARALLEL_PASSES_FULL, d.maxParallelFilterPasses)
        assertFalse(d.useProxyResolution)
        assertFalse(d.shouldNotifyUser)
    }

    @Test
    fun decide_forecastLightThreshold_emitsThrottleLight() {
        val d = ThermalHeadroomPolicy.decide(
            status = ThermalHeadroomPolicy.ThermalStatus.NONE,
            headroom = 0.72f
        )
        assertEquals(ThermalHeadroomPolicy.ExportAction.THROTTLE_LIGHT, d.action)
        assertEquals(ThermalHeadroomPolicy.MAX_PARALLEL_PASSES_LIGHT, d.maxParallelFilterPasses)
        assertFalse(d.useProxyResolution)
        assertTrue(d.shouldNotifyUser)
        assertEquals(ThermalHeadroomPolicy.UserMessageKey.THROTTLE_LIGHT, d.userMessageKey)
    }

    @Test
    fun decide_forecastHeavyThreshold_throttlesHeavyAndUsesProxy() {
        val d = ThermalHeadroomPolicy.decide(
            status = ThermalHeadroomPolicy.ThermalStatus.NONE,
            headroom = 0.9f
        )
        assertEquals(ThermalHeadroomPolicy.ExportAction.THROTTLE_HEAVY, d.action)
        assertEquals(ThermalHeadroomPolicy.MAX_PARALLEL_PASSES_HEAVY, d.maxParallelFilterPasses)
        assertTrue(d.useProxyResolution)
    }

    @Test
    fun decide_forecastPauseThreshold_pauses() {
        val d = ThermalHeadroomPolicy.decide(
            status = ThermalHeadroomPolicy.ThermalStatus.NONE,
            headroom = 0.96f
        )
        assertEquals(ThermalHeadroomPolicy.ExportAction.PAUSE, d.action)
        assertEquals(ThermalHeadroomPolicy.UserMessageKey.PAUSED_UNTIL_COOL, d.userMessageKey)
        assertTrue(d.shouldNotifyUser)
    }

    @Test
    fun decide_severeStatus_pausesEvenIfHeadroomLow() {
        val d = ThermalHeadroomPolicy.decide(
            status = ThermalHeadroomPolicy.ThermalStatus.SEVERE,
            headroom = 0.2f
        )
        assertEquals(ThermalHeadroomPolicy.ExportAction.PAUSE, d.action)
    }

    @Test
    fun decide_emergencyOrCritical_pauses() {
        listOf(
            ThermalHeadroomPolicy.ThermalStatus.EMERGENCY,
            ThermalHeadroomPolicy.ThermalStatus.CRITICAL
        ).forEach { status ->
            val d = ThermalHeadroomPolicy.decide(status = status, headroom = 0.1f)
            assertEquals("$status should PAUSE", ThermalHeadroomPolicy.ExportAction.PAUSE, d.action)
        }
    }

    @Test
    fun decide_shutdown_cancels() {
        val d = ThermalHeadroomPolicy.decide(
            status = ThermalHeadroomPolicy.ThermalStatus.SHUTDOWN,
            headroom = 0.5f
        )
        assertEquals(ThermalHeadroomPolicy.ExportAction.CANCEL, d.action)
        assertEquals(ThermalHeadroomPolicy.UserMessageKey.EMERGENCY_STOP, d.userMessageKey)
    }

    @Test
    fun decide_nanHeadroomFallsBackToStatusOnly() {
        // NaN headroom + LIGHT status — status path picks THROTTLE_LIGHT.
        val d = ThermalHeadroomPolicy.decide(
            status = ThermalHeadroomPolicy.ThermalStatus.LIGHT,
            headroom = Float.NaN
        )
        assertEquals(ThermalHeadroomPolicy.ExportAction.THROTTLE_LIGHT, d.action)
    }

    @Test
    fun decide_picksMostConservativeAcrossForecastAndStatus() {
        // MODERATE status maps to THROTTLE_HEAVY; lighter forecast must not soften it.
        val d = ThermalHeadroomPolicy.decide(
            status = ThermalHeadroomPolicy.ThermalStatus.MODERATE,
            headroom = 0.4f
        )
        assertEquals(ThermalHeadroomPolicy.ExportAction.THROTTLE_HEAVY, d.action)
    }

    @Test
    fun decide_notificationOnlyFiresOnTransition() {
        // Same action twice in a row — no second notification.
        val first = ThermalHeadroomPolicy.decide(
            status = ThermalHeadroomPolicy.ThermalStatus.NONE,
            headroom = 0.72f,
            previousAction = ThermalHeadroomPolicy.ExportAction.FULL_SPEED
        )
        assertTrue(first.shouldNotifyUser)

        val second = ThermalHeadroomPolicy.decide(
            status = ThermalHeadroomPolicy.ThermalStatus.NONE,
            headroom = 0.72f,
            previousAction = ThermalHeadroomPolicy.ExportAction.THROTTLE_LIGHT
        )
        assertFalse(second.shouldNotifyUser)
        assertEquals(ThermalHeadroomPolicy.UserMessageKey.NONE, second.userMessageKey)
    }

    @Test
    fun thermalStatus_fromOsRecognizesValidValuesAndFallsBackToNone() {
        for (s in ThermalHeadroomPolicy.ThermalStatus.entries) {
            assertEquals(s, ThermalHeadroomPolicy.ThermalStatus.fromOs(s.osValue))
        }
        assertEquals(
            ThermalHeadroomPolicy.ThermalStatus.NONE,
            ThermalHeadroomPolicy.ThermalStatus.fromOs(99)
        )
    }

    @Test
    fun shouldOfferOvernightSchedule_gatesOnThirtyMinutes() {
        assertFalse(ThermalHeadroomPolicy.shouldOfferOvernightSchedule(0L))
        assertFalse(ThermalHeadroomPolicy.shouldOfferOvernightSchedule(29L * 60L * 1_000L))
        assertTrue(ThermalHeadroomPolicy.shouldOfferOvernightSchedule(30L * 60L * 1_000L))
        assertTrue(ThermalHeadroomPolicy.shouldOfferOvernightSchedule(2L * 3_600L * 1_000L))
    }
}

package com.novacut.editor.engine

/** Pure Android thermal-signal interpretation for long-running exports. */
object ThermalHeadroomPolicy {

    /** Mirror of Android `PowerManager.THERMAL_STATUS_*` ranks. */
    enum class ThermalStatus(val osValue: Int) {
        NONE(0),
        LIGHT(1),
        MODERATE(2),
        SEVERE(3),
        CRITICAL(4),
        EMERGENCY(5),
        SHUTDOWN(6);

        companion object {
            fun fromOs(value: Int): ThermalStatus = entries.firstOrNull { it.osValue == value } ?: NONE
        }
    }

    /** What the current export actually does after a thermal observation. */
    enum class ExportAction {
        FULL_SPEED,
        CONTINUE_WITH_LIGHT_ADVISORY,
        CONTINUE_WITH_HEAVY_ADVISORY,
        CONTINUE_WITH_COOLING_ADVISORY,
        CANCEL,
    }

    /** Confirmed export-engine state after the service applies a decision. */
    enum class EngineTransition {
        CONTINUING_UNCHANGED,
        CANCELLED,
        NOT_APPLIED,
    }

    /** Localized notification copy selected only after [EngineTransition] is known. */
    enum class UserMessageKey {
        NONE,
        ADVISORY_LIGHT,
        ADVISORY_HEAVY,
        ADVISORY_COOLING,
        CANCELLED_FOR_SHUTDOWN,
    }

    data class Decision(
        val action: ExportAction,
        val shouldNotifyUser: Boolean,
    )

    /** Android's published safe floor for active forecast polling. */
    const val MIN_FORECAST_POLL_INTERVAL_MS = 10_000L

    /** Fallback thresholds for API 30 through 34 or devices without a threshold map. */
    const val FALLBACK_LIGHT_THRESHOLD = 0.70f
    const val FALLBACK_MODERATE_THRESHOLD = 0.85f
    const val FALLBACK_SEVERE_THRESHOLD = 0.95f

    fun decide(
        status: ThermalStatus,
        forecastHeadroom: Float,
        thresholds: Map<Int, Float> = emptyMap(),
        previousAction: ExportAction = ExportAction.FULL_SPEED,
    ): Decision {
        val predictedStatus = predictedStatus(forecastHeadroom, thresholds)
        val effectiveStatus = if (predictedStatus.osValue > status.osValue) predictedStatus else status
        val action = when (effectiveStatus) {
            ThermalStatus.NONE -> ExportAction.FULL_SPEED
            ThermalStatus.LIGHT -> ExportAction.CONTINUE_WITH_LIGHT_ADVISORY
            ThermalStatus.MODERATE -> ExportAction.CONTINUE_WITH_HEAVY_ADVISORY
            ThermalStatus.SEVERE,
            ThermalStatus.CRITICAL,
            ThermalStatus.EMERGENCY -> ExportAction.CONTINUE_WITH_COOLING_ADVISORY
            ThermalStatus.SHUTDOWN -> ExportAction.CANCEL
        }
        return Decision(
            action = action,
            shouldNotifyUser = action != ExportAction.FULL_SPEED && action != previousAction,
        )
    }

    /**
     * Return copy only when it describes the transition the engine actually made.
     * Advisory actions are truthful because they explicitly describe unchanged work.
     */
    fun userMessageAfterTransition(
        decision: Decision,
        transition: EngineTransition,
    ): UserMessageKey {
        if (!decision.shouldNotifyUser) return UserMessageKey.NONE
        return when (decision.action) {
            ExportAction.FULL_SPEED -> UserMessageKey.NONE
            ExportAction.CONTINUE_WITH_LIGHT_ADVISORY ->
                UserMessageKey.ADVISORY_LIGHT.takeIf {
                    transition == EngineTransition.CONTINUING_UNCHANGED
                } ?: UserMessageKey.NONE
            ExportAction.CONTINUE_WITH_HEAVY_ADVISORY ->
                UserMessageKey.ADVISORY_HEAVY.takeIf {
                    transition == EngineTransition.CONTINUING_UNCHANGED
                } ?: UserMessageKey.NONE
            ExportAction.CONTINUE_WITH_COOLING_ADVISORY ->
                UserMessageKey.ADVISORY_COOLING.takeIf {
                    transition == EngineTransition.CONTINUING_UNCHANGED
                } ?: UserMessageKey.NONE
            ExportAction.CANCEL ->
                UserMessageKey.CANCELLED_FOR_SHUTDOWN.takeIf {
                    transition == EngineTransition.CANCELLED
                } ?: UserMessageKey.NONE
        }
    }

    private fun predictedStatus(
        headroom: Float,
        thresholds: Map<Int, Float>,
    ): ThermalStatus {
        if (!headroom.isFinite() || headroom < 0f) return ThermalStatus.NONE

        val deviceThresholds = thresholds.mapNotNull { (statusValue, threshold) ->
            val status = ThermalStatus.entries.firstOrNull { it.osValue == statusValue }
            if (status == null || status == ThermalStatus.NONE || !threshold.isFinite() || threshold < 0f) {
                null
            } else {
                status to threshold
            }
        }
        if (deviceThresholds.isNotEmpty()) {
            return deviceThresholds
                .filter { (_, threshold) -> headroom >= threshold }
                .maxByOrNull { (status, _) -> status.osValue }
                ?.first
                ?: ThermalStatus.NONE
        }

        return when {
            headroom >= FALLBACK_SEVERE_THRESHOLD -> ThermalStatus.SEVERE
            headroom >= FALLBACK_MODERATE_THRESHOLD -> ThermalStatus.MODERATE
            headroom >= FALLBACK_LIGHT_THRESHOLD -> ThermalStatus.LIGHT
            else -> ThermalStatus.NONE
        }
    }

    fun shouldOfferOvernightSchedule(estimatedRenderMs: Long): Boolean {
        return estimatedRenderMs >= OVERNIGHT_OFFER_THRESHOLD_MS
    }

    const val OVERNIGHT_OFFER_THRESHOLD_MS = 30L * 60L * 1_000L
}

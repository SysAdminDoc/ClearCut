package com.novacut.editor.engine

/**
 * R8.5 — Thermal-aware export scheduling policy.
 *
 * `PowerManager.getThermalHeadroom(forecastSeconds)` (API 30+) returns a
 * 0.0-1.0 prediction of how close the SoC is to `THERMAL_STATUS_SEVERE` in
 * the next N seconds, and `addThermalStatusListener` reacts to actual
 * throttling transitions. This object owns the pure decision math an
 * `ExportService` (or any long-running worker) calls to translate raw
 * thermal signals into a [ThrottleDecision] — pause, downgrade encoder
 * parallelism, switch to proxy, etc. — without itself touching
 * `PowerManager`. That lets the policy live in the JVM unit-test surface
 * alongside `AiUsageLedger`, `CutAssistantEngine`, etc.
 *
 * Background:
 * - The skin-temperature sensor underlying `getThermalHeadroom` is
 *   slow-moving; recommended polling is once per second. Calling more
 *   frequently than every ~10 s can return NaN.
 * - `THERMAL_STATUS_*` integers come from `PowerManager`:
 *     0 = NONE, 1 = LIGHT, 2 = MODERATE, 3 = SEVERE,
 *     4 = CRITICAL, 5 = EMERGENCY, 6 = SHUTDOWN.
 *
 * See ROADMAP.md R8.5 for the user-facing wiring plan: foreground service
 * progress chip, "Schedule for overnight" Settings entry, and the
 * resumable-marker handshake for SEVERE pause/resume.
 */
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

    /** What the encoder loop should do this tick. */
    enum class ExportAction {
        /** Encode at full parallelism. */
        FULL_SPEED,
        /** Drop one filter pass / lower bitrate hint. */
        THROTTLE_LIGHT,
        /** Switch to proxy encode + drop to single-pass. */
        THROTTLE_HEAVY,
        /** Stop encoder thread, persist resumable marker, raise notif. */
        PAUSE,
        /** Hard cancel — device is shutting down. */
        CANCEL
    }

    /** Notification copy keys the UI can map to its own localized strings. */
    enum class UserMessageKey {
        NONE,
        THROTTLE_LIGHT,
        THROTTLE_HEAVY,
        PAUSED_UNTIL_COOL,
        EMERGENCY_STOP
    }

    /**
     * Result of one decision tick.
     *
     * @param action What the encoder loop should do now.
     * @param maxParallelFilterPasses Upper bound on simultaneous shader/filter passes.
     *        Reflects an Snapdragon 7/8-class baseline; UI may scale further.
     * @param useProxyResolution If true, switch the active encode to the
     *        proxy/preview resolution instead of the full target. Implies
     *        the user will see a re-encode estimate change in the progress chip.
     * @param shouldNotifyUser Whether to surface a thermal notification this tick.
     *        UI debounces — only flip true on action transitions, not every tick.
     * @param userMessageKey i18n key the UI maps to localized notification copy.
     */
    data class ThrottleDecision(
        val action: ExportAction,
        val maxParallelFilterPasses: Int,
        val useProxyResolution: Boolean,
        val shouldNotifyUser: Boolean,
        val userMessageKey: UserMessageKey
    )

    /** Forecast threshold above which we treat the next tick as concerning. */
    const val HEADROOM_THROTTLE_LIGHT = 0.7f

    /** Forecast threshold for heavy throttle / proxy fallback. */
    const val HEADROOM_THROTTLE_HEAVY = 0.85f

    /** Forecast threshold for pause-and-cool. */
    const val HEADROOM_PAUSE = 0.95f

    /** Maximum parallel filter passes at full speed. Tuned for SD 7/8-class. */
    const val MAX_PARALLEL_PASSES_FULL = 4
    const val MAX_PARALLEL_PASSES_LIGHT = 2
    const val MAX_PARALLEL_PASSES_HEAVY = 1

    /**
     * Decide what to do this tick.
     *
     * @param status Current PowerManager thermal status.
     * @param headroom Result of `getThermalHeadroom(forecastSeconds)`.
     *        NaN means the platform refuses to forecast right now (too soon
     *        after boot, or call rate exceeded); treat as "trust the status
     *        signal only".
     * @param previousAction Last tick's action; used to debounce notifications.
     */
    fun decide(
        status: ThermalStatus,
        headroom: Float,
        previousAction: ExportAction = ExportAction.FULL_SPEED
    ): ThrottleDecision {
        // Hard floors: device is about to throttle itself or shut down.
        if (status == ThermalStatus.SHUTDOWN) {
            return decision(
                ExportAction.CANCEL,
                MAX_PARALLEL_PASSES_HEAVY,
                useProxyResolution = false,
                previousAction = previousAction,
                userMessageKey = UserMessageKey.EMERGENCY_STOP
            )
        }
        if (status == ThermalStatus.EMERGENCY || status == ThermalStatus.CRITICAL) {
            return decision(
                ExportAction.PAUSE,
                MAX_PARALLEL_PASSES_HEAVY,
                useProxyResolution = true,
                previousAction = previousAction,
                userMessageKey = UserMessageKey.PAUSED_UNTIL_COOL
            )
        }

        // Forecast-driven preemptive throttle.
        val safeHeadroom = if (headroom.isNaN()) null else headroom
        val byHeadroom: ExportAction = when {
            safeHeadroom == null -> null
            safeHeadroom >= HEADROOM_PAUSE -> ExportAction.PAUSE
            safeHeadroom >= HEADROOM_THROTTLE_HEAVY -> ExportAction.THROTTLE_HEAVY
            safeHeadroom >= HEADROOM_THROTTLE_LIGHT -> ExportAction.THROTTLE_LIGHT
            else -> ExportAction.FULL_SPEED
        } ?: ExportAction.FULL_SPEED

        val byStatus: ExportAction = when (status) {
            ThermalStatus.SEVERE -> ExportAction.PAUSE
            ThermalStatus.MODERATE -> ExportAction.THROTTLE_HEAVY
            ThermalStatus.LIGHT -> ExportAction.THROTTLE_LIGHT
            ThermalStatus.NONE -> ExportAction.FULL_SPEED
            else -> ExportAction.FULL_SPEED
        }

        // Choose the more conservative action between forecast and status.
        val action = maxAction(byHeadroom, byStatus)
        val (maxPasses, useProxy, msg) = paramsFor(action)
        return decision(action, maxPasses, useProxy, previousAction, msg)
    }

    private fun decision(
        action: ExportAction,
        maxPasses: Int,
        useProxyResolution: Boolean,
        previousAction: ExportAction,
        userMessageKey: UserMessageKey
    ): ThrottleDecision {
        val notify = action != previousAction && userMessageKey != UserMessageKey.NONE
        return ThrottleDecision(
            action = action,
            maxParallelFilterPasses = maxPasses,
            useProxyResolution = useProxyResolution,
            shouldNotifyUser = notify,
            userMessageKey = if (notify) userMessageKey else UserMessageKey.NONE
        )
    }

    private fun paramsFor(action: ExportAction): Triple<Int, Boolean, UserMessageKey> = when (action) {
        ExportAction.FULL_SPEED -> Triple(MAX_PARALLEL_PASSES_FULL, false, UserMessageKey.NONE)
        ExportAction.THROTTLE_LIGHT -> Triple(MAX_PARALLEL_PASSES_LIGHT, false, UserMessageKey.THROTTLE_LIGHT)
        ExportAction.THROTTLE_HEAVY -> Triple(MAX_PARALLEL_PASSES_HEAVY, true, UserMessageKey.THROTTLE_HEAVY)
        ExportAction.PAUSE -> Triple(MAX_PARALLEL_PASSES_HEAVY, true, UserMessageKey.PAUSED_UNTIL_COOL)
        ExportAction.CANCEL -> Triple(MAX_PARALLEL_PASSES_HEAVY, false, UserMessageKey.EMERGENCY_STOP)
    }

    private fun rank(action: ExportAction): Int = when (action) {
        ExportAction.FULL_SPEED -> 0
        ExportAction.THROTTLE_LIGHT -> 1
        ExportAction.THROTTLE_HEAVY -> 2
        ExportAction.PAUSE -> 3
        ExportAction.CANCEL -> 4
    }

    private fun maxAction(a: ExportAction, b: ExportAction): ExportAction =
        if (rank(a) >= rank(b)) a else b

    /**
     * Whether this estimated total render duration is long enough that the
     * UI should offer the "Schedule for overnight" option (WorkManager job
     * gated on BATTERY_NOT_LOW + CHARGING + IDLE).
     */
    fun shouldOfferOvernightSchedule(estimatedRenderMs: Long): Boolean {
        return estimatedRenderMs >= OVERNIGHT_OFFER_THRESHOLD_MS
    }

    /** 30 minutes — chosen to match the typical creator's "I'll wait" tolerance. */
    const val OVERNIGHT_OFFER_THRESHOLD_MS = 30L * 60L * 1_000L
}

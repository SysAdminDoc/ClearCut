package com.novacut.editor.engine

import com.novacut.editor.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton

/**
 * P0 privacy gate for Google MediaPipe Tasks (selfie segmentation + face
 * detection).
 *
 * The bundled `com.google.mediapipe:tasks-vision` graph packages Google Play
 * Services DataTransport (`RemoteLoggingClient`), which uploads anonymous
 * `COREML_ON_DEVICE_SOLUTIONS` performance metrics (app id/version, task/mode,
 * invocation/drop counts, latency/elapsed time, initialization errors) to
 * Google whenever a Tasks object is constructed. Input media (frames/pixels)
 * never leaves the device, but the metrics upload means construction is not
 * consent-free.
 *
 * Contract:
 * - No `ImageSegmenter` or `FaceDetector` may be constructed while
 *   [isConsented] is false. Every construction site must return early when the
 *   gate is not consented.
 * - Denial leaves all non-MediaPipe editing usable — callers degrade to null.
 * - Revocation closes every live Tasks instance (via registered
 *   [registerRevocationHandler] handlers) and blocks recreation until the user
 *   re-consents.
 * - Consent is versioned: bumping [CONSENT_VERSION] after a material disclosure
 *   change forces re-consent because a lower stored version no longer satisfies
 *   the gate.
 *
 * Consent is persisted through [SettingsRepository]
 * (`mediaPipeConsentVersion`). A cached synchronous view is kept so the
 * synchronous `getOrCreate*` paths can consult the gate without suspending; it
 * fails closed (denied) until the first persisted value is observed.
 */
@Singleton
class MediaPipeUsageGate internal constructor(
    private val consentVersionFlow: Flow<Int>,
    private val persistConsentVersion: suspend (Int) -> Unit,
    scope: CoroutineScope,
) {
    @Inject
    constructor(settingsRepository: SettingsRepository) : this(
        consentVersionFlow = settingsRepository.settings.map { it.mediaPipeConsentVersion },
        persistConsentVersion = { settingsRepository.updateMediaPipeConsentVersion(it) },
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    )

    @Volatile private var cachedConsentVersion: Int = 0
    private val revocationHandlers = CopyOnWriteArrayList<() -> Unit>()

    private val _consented = MutableStateFlow(false)
    /** Observable consent state for UI (Settings/privacy dashboard). */
    val consented: StateFlow<Boolean> = _consented.asStateFlow()

    init {
        scope.launch {
            consentVersionFlow.collect { version -> applyConsentVersion(version) }
        }
    }

    /** True when construction of MediaPipe Tasks objects is currently allowed. */
    fun isConsented(): Boolean = cachedConsentVersion >= CONSENT_VERSION

    /**
     * Register a handler invoked when consent is revoked (or dropped below the
     * current [CONSENT_VERSION]). The handler MUST close and clear any live
     * Tasks instance the caller holds so a revoked session cannot keep using a
     * constructed segmenter/detector.
     *
     * Handlers are held for the process lifetime; engines register once.
     */
    fun registerRevocationHandler(handler: () -> Unit) {
        revocationHandlers.add(handler)
        // If we are already un-consented when a handler registers, make sure any
        // instance it owns is torn down immediately.
        if (!isConsented()) runCatching { handler() }
    }

    /** Grant consent and persist the current [CONSENT_VERSION]. */
    suspend fun grantConsent() {
        persistConsentVersion(CONSENT_VERSION)
        applyConsentVersion(CONSENT_VERSION)
    }

    /** Revoke consent, close live instances, and block recreation. */
    suspend fun revokeConsent() {
        persistConsentVersion(0)
        applyConsentVersion(0)
    }

    @Synchronized
    internal fun applyConsentVersion(version: Int) {
        val wasConsented = cachedConsentVersion >= CONSENT_VERSION
        cachedConsentVersion = version.coerceAtLeast(0)
        val nowConsented = cachedConsentVersion >= CONSENT_VERSION
        _consented.value = nowConsented
        if (wasConsented && !nowConsented) {
            for (handler in revocationHandlers) runCatching { handler() }
        }
    }

    /**
     * Immutable disclosure surfaced by the consent sheet, the privacy
     * dashboard, and the privacy policy. Kept here so the displayed text is
     * derived from the actual SDK behaviour rather than hand-maintained copy.
     */
    val disclosure: Disclosure
        get() = Disclosure(
            processor = "Google (MediaPipe Tasks via Google Play Services DataTransport)",
            appId = BuildConfig.APPLICATION_ID,
            appVersion = BuildConfig.VERSION_NAME,
            tasks = listOf(
                "Selfie image segmentation (IMAGE running mode)",
                "Face detection / BlazeFace (IMAGE running mode)",
            ),
            uploadedMetricFields = listOf(
                "App id and version",
                "Task type and running mode",
                "Invocation and drop counts",
                "Latency and elapsed time",
                "Initialization errors",
            ),
            inputMediaStaysOnDevice = true,
        )

    data class Disclosure(
        val processor: String,
        val appId: String,
        val appVersion: String,
        val tasks: List<String>,
        val uploadedMetricFields: List<String>,
        val inputMediaStaysOnDevice: Boolean,
    )

    companion object {
        /**
         * Current consent version. Increment when the MediaPipe disclosure
         * changes materially so a previously-granted lower version no longer
         * satisfies the gate and the user must re-consent.
         */
        const val CONSENT_VERSION = 1
    }
}

package com.novacut.editor.engine

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * R8.2 — C2PA Content Credentials manifest builder for MP4 export.
 *
 * EU AI Act Article 50 (effective 2026-08-02) requires machine-readable
 * labels for AI-generated content; C2PA is the most technically mature
 * implementation of that requirement and Google Pixel 10 already ships
 * hardware-backed signing at C2PA Assurance Level 2.
 *
 * This engine owns the pure manifest construction and availability layer:
 * given the project name, export config, ClearCut version, and per-project
 * [AiUsageLedger], it builds a structured [C2paManifest] that can be passed
 * to c2pa-android's `Builder.fromJson(...)` once a signing library and
 * certificate enrollment path are configured.
 *
 * ClearCut deliberately separates draft manifest generation from verifiable
 * Content Credentials. A `.c2pa-draft-manifest.json` sidecar is useful audit
 * data, but it is not a signed C2PA manifest store and does not create a BMFF
 * hard binding to the exported MP4. [signingAvailability] is the gate the UI
 * and export path use to avoid implying otherwise.
 *
 * ## Activation candidates
 *
 *  - **c2pa-android** (Content Authenticity Initiative, official): Rust
 *    core + JNI; supports MP4 video, StrongBox / Keystore / web-service
 *    signing. https://opensource.contentauthenticity.org/docs/c2pa-android/
 *  - **proofmode / simple-c2pa**: pure-Kotlin convenience layer on top of
 *    the same c2pa-rs core. Smaller surface, MP4-capable.
 *    https://proofmode.org/blog/simple-c2pa
 *
 * Whichever path lands, it must:
 *  - Default to local Android Keystore signing; prefer StrongBox on Pixel 8+.
 *  - Allow the user to opt out per export (default-on only when the
 *    [AiUsageLedger] for the project is non-empty per
 *    [AiUsageLedger.discloseToggleDefaultOn]).
 *  - Never upload media to a remote signing service unless the user
 *    explicitly picks a `WebServiceSigner` destination with its own
 *    consent sheet.
 *  - Pass the R6.1 16 KB alignment gate (c2pa-android is NDK-built).
 */
@Singleton
class C2paExportEngine @Inject constructor() {

    enum class SigningMode {
        /** Software-backed Android Keystore (default; any device). */
        ANDROID_KEYSTORE,
        /** Hardware-isolated StrongBox Keymaster (Pixel 8+, Galaxy S23+). */
        STRONGBOX,
        /** User-supplied PEM keypair + cert chain (advanced). */
        USER_PEM,
        /** Remote signing service (organization deployments). */
        WEB_SERVICE
    }

    /**
     * One assertion that lands in the C2PA manifest definition. Labels mirror
     * C2PA 2.4 and current CAWG assertion labels so [manifestDefinitionToJson]
     * can pass them through to c2pa-android's manifest builder.
     */
    data class Assertion(
        val label: String,
        val data: Map<String, Any?>
    ) {
        init {
            require(label.isNotBlank()) { "Assertion label must not be blank" }
        }
    }

    /**
     * Structured manifest spec the engine builds before handing off to
     * c2pa-android. Stays as a pure value type so the rest of the system
     * can serialize / display / test it without the AAR present.
     */
    data class C2paManifest(
        val claimGenerator: String,
        val claimGeneratorInfoVersion: String,
        val title: String?,
        val signingMode: SigningMode,
        val assertions: List<Assertion>
    )

    enum class AvailabilityStatus {
        /** Reserved for a future build that includes a complete signer bridge. */
        READY,
        LIBRARY_UNAVAILABLE,
        CERTIFICATE_ENROLLMENT_REQUIRED,
        USER_PEM_REQUIRED,
        REMOTE_SIGNER_REQUIRED,
        REMOTE_CONSENT_REQUIRED,
        SIGNER_BRIDGE_UNAVAILABLE
    }

    data class SigningAvailability(
        val status: AvailabilityStatus,
        val canSignEmbeddedManifest: Boolean,
        val canWriteDraftSidecar: Boolean = true,
        val message: String
    )

    /**
     * Convert an [AiUsageLedger] into the canonical C2PA `c2pa.actions.v2`
     * assertion list. Each merged ledger entry becomes one action:
     *
     *  - GENERATIVE_*, INPAINTING_CLOUD, LIP_SYNC_CLOUD → `c2pa.created`
     *    with the IPTC `trainedAlgorithmicMedia` digital-source IRI.
     *  - AUTO_EDIT_LOCAL → `c2pa.edited` with `digitalSourceType =
     *    compositedWithTrainedAlgorithmicMedia`.
     *  - The remaining DISCLOSURE_RECOMMENDED kinds → `c2pa.edited` with
     *    the IPTC `algorithmicMedia` digital-source IRI.
     *  - INTERNAL_ONLY kinds emit no action (not disclosure-bearing per
     *    [AiUsageLedger.defaultSeverity]).
     */
    fun aiActionsAssertion(ledger: List<AiUsageLedger.Entry>): Assertion? {
        val merged = AiUsageLedger.mergeOverlaps(ledger)
        val actions = merged.mapNotNull { entry ->
            val severity = AiUsageLedger.defaultSeverity(entry.effectKind)
            if (severity == AiUsageLedger.Severity.INTERNAL_ONLY) return@mapNotNull null
            mapOf(
                "action" to actionForKind(entry.effectKind),
                "softwareAgent" to mapOf("name" to entry.modelName),
                "when" to c2paTimestamp(entry.recordedAtEpochMs),
                "digitalSourceType" to digitalSourceTypeForKind(entry.effectKind),
                "parameters" to mapOf(
                    "com.clearcut.clipId" to entry.clipId,
                    "com.clearcut.rangeStartMs" to entry.rangeStartMs,
                    "com.clearcut.rangeEndMs" to entry.rangeEndMs
                )
            )
        }
        if (actions.isEmpty()) return null
        return Assertion(
            label = ACTIONS_V2_LABEL,
            data = mapOf("actions" to actions)
        )
    }

    private fun actionForKind(kind: AiUsageLedger.EffectKind): String = when (kind) {
        AiUsageLedger.EffectKind.GENERATIVE_VIDEO_CLOUD,
        AiUsageLedger.EffectKind.LIP_SYNC_CLOUD,
        AiUsageLedger.EffectKind.GENERATIVE_FILL_CLOUD,
        AiUsageLedger.EffectKind.INPAINTING_CLOUD -> "c2pa.created"

        AiUsageLedger.EffectKind.AUTO_EDIT_LOCAL,
        AiUsageLedger.EffectKind.INPAINTING_LOCAL_LARGE,
        AiUsageLedger.EffectKind.STYLE_TRANSFER_LOCAL,
        AiUsageLedger.EffectKind.UPSCALING_LOCAL,
        AiUsageLedger.EffectKind.FRAME_INTERPOLATION_LOCAL,
        AiUsageLedger.EffectKind.VOICE_CLONE_LOCAL -> "c2pa.edited"

        AiUsageLedger.EffectKind.CAPTION_TRANSLATION_LOCAL,
        AiUsageLedger.EffectKind.BACKGROUND_REMOVAL_LOCAL,
        AiUsageLedger.EffectKind.TTS_LOCAL -> "c2pa.opinion"
    }

    /** C2PA 2.4 `digitalSourceType` values are full IPTC IRIs. */
    private fun digitalSourceTypeForKind(kind: AiUsageLedger.EffectKind): String = when (kind) {
        AiUsageLedger.EffectKind.GENERATIVE_VIDEO_CLOUD,
        AiUsageLedger.EffectKind.LIP_SYNC_CLOUD,
        AiUsageLedger.EffectKind.GENERATIVE_FILL_CLOUD,
        AiUsageLedger.EffectKind.INPAINTING_CLOUD ->
            "http://cv.iptc.org/newscodes/digitalsourcetype/trainedAlgorithmicMedia"

        AiUsageLedger.EffectKind.AUTO_EDIT_LOCAL,
        AiUsageLedger.EffectKind.VOICE_CLONE_LOCAL ->
            "http://cv.iptc.org/newscodes/digitalsourcetype/compositedWithTrainedAlgorithmicMedia"

        AiUsageLedger.EffectKind.INPAINTING_LOCAL_LARGE,
        AiUsageLedger.EffectKind.STYLE_TRANSFER_LOCAL,
        AiUsageLedger.EffectKind.UPSCALING_LOCAL,
        AiUsageLedger.EffectKind.FRAME_INTERPOLATION_LOCAL ->
            "http://cv.iptc.org/newscodes/digitalsourcetype/algorithmicMedia"

        AiUsageLedger.EffectKind.CAPTION_TRANSLATION_LOCAL,
        AiUsageLedger.EffectKind.BACKGROUND_REMOVAL_LOCAL,
        AiUsageLedger.EffectKind.TTS_LOCAL ->
            "http://cv.iptc.org/newscodes/digitalsourcetype/minorHumanEdits"
    }

    /**
     * Build the full manifest spec for a project export. The spec is
     * structured but unsigned; the caller picks a [SigningMode] and hands
     * the result to [signAndEmbed] when the c2pa AAR is wired.
     *
     * @param projectTitle Optional human title (`null` when redacted).
     * @param novaCutVersionName Pulled from `BuildConfig.VERSION_NAME`.
     * @param signingMode Preferred signer; falls back to ANDROID_KEYSTORE
     *        if STRONGBOX is unavailable on the runtime device.
     * @param ledger Per-project AI usage records.
     * @param exporterCreationTimeMs Timestamp written into the manifest's
     *        `c2pa.actions` assertion as `whenCompleted`.
     */
    fun buildManifest(
        projectTitle: String?,
        novaCutVersionName: String,
        signingMode: SigningMode,
        ledger: List<AiUsageLedger.Entry>,
        exporterCreationTimeMs: Long
    ): C2paManifest {
        require(novaCutVersionName.isNotBlank()) {
            "novaCutVersionName must not be blank"
        }
        require(exporterCreationTimeMs >= 0L) {
            "exporterCreationTimeMs must be >= 0"
        }
        val actions = buildList {
            // A new export starts from a blank output asset. The draft does
            // not claim input ingredients because it is not signed or bound.
            add(
                mapOf(
                    "action" to "c2pa.created",
                    "softwareAgent" to mapOf(
                        "name" to "ClearCut",
                        "version" to novaCutVersionName,
                        "operating_system" to "Android"
                    ),
                    "when" to c2paTimestamp(exporterCreationTimeMs),
                    "digitalSourceType" to EMPTY_DIGITAL_SOURCE_TYPE
                )
            )
            aiActionsAssertion(ledger)?.let { assertion ->
                @Suppress("UNCHECKED_CAST")
                addAll(assertion.data["actions"] as List<Map<String, Any?>>)
            }
        }
        val assertions = buildList {
            add(
                Assertion(
                    label = "c2pa.thumbnail.claim.jpeg",
                    data = mapOf(
                        "claimGeneratorInfo" to mapOf(
                            "name" to "ClearCut",
                            "version" to novaCutVersionName,
                            "operating_system" to "Android",
                            "specVersion" to C2PA_SPEC_VERSION
                        )
                    )
                )
            )
            add(
                Assertion(
                    label = ACTIONS_V2_LABEL,
                    data = mapOf("actions" to actions)
                )
            )
        }
        return C2paManifest(
            claimGenerator = "ClearCut/$novaCutVersionName",
            claimGeneratorInfoVersion = novaCutVersionName,
            title = projectTitle?.takeIf { it.isNotBlank() },
            signingMode = signingMode,
            assertions = assertions
        )
    }

    fun manifestDefinitionToJson(manifest: C2paManifest): JSONObject {
        return JSONObject().apply {
            // C2PA 2.4 claim-map-v2 uses one generator-info-map object. The
            // old array form is intentionally not emitted.
            put("claim_generator_info", JSONObject().apply {
                put("name", "ClearCut")
                put("version", manifest.claimGeneratorInfoVersion)
                put("operating_system", "Android")
                put("specVersion", C2PA_SPEC_VERSION)
            })
            if (manifest.title != null) put("dc:title", manifest.title)
            put("assertions", JSONArray().apply {
                manifest.assertions.forEach { assertion ->
                    put(JSONObject().apply {
                        put("label", assertion.label)
                        put("data", toJsonValue(assertion.data))
                    })
                }
            })
        }
    }

    fun manifestToJson(manifest: C2paManifest): JSONObject {
        return draftSidecarToJson(
            manifest = manifest,
            availability = signingAvailability(manifest.signingMode),
            exportedFileName = null
        )
    }

    fun draftSidecarToJson(
        manifest: C2paManifest,
        availability: SigningAvailability,
        exportedFileName: String?
    ): JSONObject {
        val effectiveAvailability = availability.takeUnless {
            it.canSignEmbeddedManifest || it.status == AvailabilityStatus.READY
        } ?: SigningAvailability(
            status = AvailabilityStatus.SIGNER_BRIDGE_UNAVAILABLE,
            canSignEmbeddedManifest = false,
            message = UNSIGNED_DRAFT_MESSAGE
        )
        return JSONObject().apply {
            put("schema", "com.clearcut.c2pa-draft-manifest.v2")
            put("c2paSpecification", C2PA_SPEC_VERSION)
            put("format", "video/mp4")
            put("embeddedManifestStore", false)
            put("hardBinding", false)
            put("isUnsignedDraft", true)
            put("isVerifiableContentCredential", false)
            put("contentCredentialsStatus", effectiveAvailability.status.name)
            put("contentCredentialsMessage", effectiveAvailability.message)
            put("exportedFileName", exportedFileName ?: JSONObject.NULL)
            put("title", manifest.title ?: JSONObject.NULL)
            put("signingMode", manifest.signingMode.name)
            put("manifestDefinition", manifestDefinitionToJson(manifest))
        }
    }

    private fun toJsonValue(value: Any?): Any {
        return when (value) {
            null -> JSONObject.NULL
            is JSONObject, is JSONArray, is String, is Boolean, is Number -> value
            is Map<*, *> -> JSONObject().apply {
                value.entries
                    .sortedBy { it.key.toString() }
                    .forEach { (key, nested) ->
                        put(key.toString(), toJsonValue(nested))
                    }
            }
            is Iterable<*> -> JSONArray().apply {
                value.forEach { put(toJsonValue(it)) }
            }
            is Array<*> -> JSONArray().apply {
                value.forEach { put(toJsonValue(it)) }
            }
            else -> value.toString()
        }
    }

    /**
     * Whether a c2pa signing library is wired into the build. Reflection
     * probe mirrors [OutputStreamingEngine.isAvailable]'s pattern so the
     * UI can branch without crashing when the AAR is absent.
     */
    fun isAvailable(): Boolean {
        cachedAvailability?.let { return it }
        val classes = arrayOf(
            "org.contentauth.c2pa.Builder",            // c2pa-android official
            "org.proofmode.simplec2pa.Manifest",       // simple-c2pa community
        )
        val present = classes.any { name ->
            try {
                Class.forName(name); true
            } catch (_: ClassNotFoundException) {
                false
            }
        }
        cachedAvailability = present
        if (!present) Log.d(TAG, "isAvailable: no c2pa library on classpath")
        return present
    }

    @Volatile private var cachedAvailability: Boolean? = null

    fun signingAvailability(
        signingMode: SigningMode,
        libraryAvailable: Boolean = isAvailable(),
        keystoreKeyAvailable: Boolean = false,
        certificateChainAvailable: Boolean = false,
        userPrivateKeyAvailable: Boolean = false,
        remoteSignerConfigured: Boolean = false,
        remoteConsentGranted: Boolean = false
    ): SigningAvailability {
        if (!libraryAvailable) {
            return SigningAvailability(
                status = AvailabilityStatus.LIBRARY_UNAVAILABLE,
                canSignEmbeddedManifest = false,
                message = "Content Credentials are unavailable because no C2PA signing library is bundled."
            )
        }
        return when (signingMode) {
            SigningMode.ANDROID_KEYSTORE,
            SigningMode.STRONGBOX -> {
                if (keystoreKeyAvailable && certificateChainAvailable) {
                    SigningAvailability(
                        status = AvailabilityStatus.SIGNER_BRIDGE_UNAVAILABLE,
                        canSignEmbeddedManifest = false,
                        message = UNSIGNED_DRAFT_MESSAGE
                    )
                } else {
                    SigningAvailability(
                        status = AvailabilityStatus.CERTIFICATE_ENROLLMENT_REQUIRED,
                        canSignEmbeddedManifest = false,
                        message = "Content Credentials are unavailable until a device key and certificate chain are enrolled."
                    )
                }
            }
            SigningMode.USER_PEM -> {
                if (certificateChainAvailable && userPrivateKeyAvailable) {
                    SigningAvailability(
                        status = AvailabilityStatus.SIGNER_BRIDGE_UNAVAILABLE,
                        canSignEmbeddedManifest = false,
                        message = UNSIGNED_DRAFT_MESSAGE
                    )
                } else {
                    SigningAvailability(
                        status = AvailabilityStatus.USER_PEM_REQUIRED,
                        canSignEmbeddedManifest = false,
                        message = "Content Credentials need a user PEM private key and certificate chain."
                    )
                }
            }
            SigningMode.WEB_SERVICE -> {
                if (!remoteSignerConfigured) {
                    SigningAvailability(
                        status = AvailabilityStatus.REMOTE_SIGNER_REQUIRED,
                        canSignEmbeddedManifest = false,
                        message = "Content Credentials need a configured remote signing service."
                    )
                } else if (!remoteConsentGranted) {
                    SigningAvailability(
                        status = AvailabilityStatus.REMOTE_CONSENT_REQUIRED,
                        canSignEmbeddedManifest = false,
                        message = "Remote Content Credentials signing requires explicit per-export consent."
                    )
                } else {
                    SigningAvailability(
                        status = AvailabilityStatus.SIGNER_BRIDGE_UNAVAILABLE,
                        canSignEmbeddedManifest = false,
                        message = UNSIGNED_DRAFT_MESSAGE
                    )
                }
            }
        }
    }

    /**
     * Sign and embed the manifest into the exported MP4 at [outputPath].
     * Returns a [SignResult] describing what happened.
     *
     * Returns `UNAVAILABLE` until both a C2PA library and signer credentials
     * are configured. The signing bridge must embed a manifest store into the
     * MP4 and leave a verifiable BMFF hard binding; a draft sidecar is not
     * enough to return [SignResult.Signed].
     */
    suspend fun signAndEmbed(
        manifest: C2paManifest,
        outputPath: String
    ): SignResult {
        require(outputPath.isNotBlank()) { "outputPath must not be blank" }
        val availability = signingAvailability(manifest.signingMode)
        if (!availability.canSignEmbeddedManifest) {
            Log.d(
                TAG,
                "signAndEmbed unavailable: ${availability.status} (target=$outputPath, mode=${manifest.signingMode})"
            )
            return SignResult.Unavailable(reason = availability.message)
        }
        Log.d(TAG, "signAndEmbed: signer bridge not implemented (target=$outputPath, mode=${manifest.signingMode})")
        return SignResult.Unavailable(
            reason = "C2PA signer bridge is not implemented in this build"
        )
    }

    sealed class SignResult {
        /** Manifest was signed + embedded into the MP4 file. */
        data class Signed(val signedBytes: Long, val manifestId: String) : SignResult()
        /** c2pa library not available on classpath. */
        data class Unavailable(val reason: String) : SignResult()
        /** Signing attempted but failed (key error, file IO, etc.). */
        data class Failed(val reason: String, val cause: Throwable? = null) : SignResult()
    }

    companion object {
        const val C2PA_SPEC_VERSION = "2.4.0"

        private const val ACTIONS_V2_LABEL = "c2pa.actions.v2"
        private const val EMPTY_DIGITAL_SOURCE_TYPE = "http://c2pa.org/digitalsourcetype/empty"
        private const val UNSIGNED_DRAFT_MESSAGE =
            "Unsigned and unverifiable draft: ClearCut does not embed Content Credentials in this build."
        private const val TAG = "C2paExportEngine"

        private fun c2paTimestamp(epochMs: Long): String = Instant.ofEpochMilli(epochMs).toString()
    }
}

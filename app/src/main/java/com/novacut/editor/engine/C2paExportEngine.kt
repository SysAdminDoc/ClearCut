package com.novacut.editor.engine

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
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
 * This engine owns the **pure manifest construction** layer: given the
 * project name, export config, NovaCut version, and the per-project
 * `AiUsageLedger`, it builds a structured [C2paManifest] that the (future)
 * signing path can hand to either `c2pa-android` (official AAR) or
 * `proofmode/simple-c2pa` (community AAR) to actually sign and embed into
 * the exported MP4.
 *
 * **Today this is a stub** — no c2pa AAR is wired. The class exists so the
 * rest of the system can compose against it: export config builders can
 * already attach a manifest spec, the Privacy Dashboard (R5.5c) can render
 * the assertion list, and the export sheet can decide whether to offer a
 * "Content Credentials" toggle. When the AAR lands, only [signAndEmbed]
 * grows a real body.
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
     * One assertion that lands in the C2PA manifest. Names mirror the C2PA
     * v2.4 specification labels so [signAndEmbed] can pass them through
     * verbatim to c2pa-android's manifest builder.
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

    /**
     * Convert an [AiUsageLedger] into the canonical C2PA `c2pa.actions`
     * assertion list. Each merged ledger entry becomes one action:
     *
     *  - GENERATIVE_*, INPAINTING_CLOUD, LIP_SYNC_CLOUD → `c2pa.created`
     *    with `digitalSourceType = trainedAlgorithmicMedia`.
     *  - AUTO_EDIT_LOCAL → `c2pa.edited` with `digitalSourceType =
     *    compositeWithTrainedAlgorithmicMedia`.
     *  - The remaining DISCLOSURE_RECOMMENDED kinds → `c2pa.edited` with
     *    `digitalSourceType = algorithmicMedia`.
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
                "softwareAgent" to entry.modelName,
                "when" to entry.recordedAtEpochMs,
                "digitalSourceType" to digitalSourceTypeForKind(entry.effectKind),
                "parameters" to mapOf(
                    "clipId" to entry.clipId,
                    "rangeStartMs" to entry.rangeStartMs,
                    "rangeEndMs" to entry.rangeEndMs
                )
            )
        }
        if (actions.isEmpty()) return null
        return Assertion(
            label = "c2pa.actions",
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

    /** C2PA v2 `digitalSourceType` IRI suffix; full IRI prepended by signer. */
    private fun digitalSourceTypeForKind(kind: AiUsageLedger.EffectKind): String = when (kind) {
        AiUsageLedger.EffectKind.GENERATIVE_VIDEO_CLOUD,
        AiUsageLedger.EffectKind.LIP_SYNC_CLOUD,
        AiUsageLedger.EffectKind.GENERATIVE_FILL_CLOUD,
        AiUsageLedger.EffectKind.INPAINTING_CLOUD -> "trainedAlgorithmicMedia"

        AiUsageLedger.EffectKind.AUTO_EDIT_LOCAL,
        AiUsageLedger.EffectKind.VOICE_CLONE_LOCAL -> "compositeWithTrainedAlgorithmicMedia"

        AiUsageLedger.EffectKind.INPAINTING_LOCAL_LARGE,
        AiUsageLedger.EffectKind.STYLE_TRANSFER_LOCAL,
        AiUsageLedger.EffectKind.UPSCALING_LOCAL,
        AiUsageLedger.EffectKind.FRAME_INTERPOLATION_LOCAL -> "algorithmicMedia"

        AiUsageLedger.EffectKind.CAPTION_TRANSLATION_LOCAL,
        AiUsageLedger.EffectKind.BACKGROUND_REMOVAL_LOCAL,
        AiUsageLedger.EffectKind.TTS_LOCAL -> "minorHumanEdits"
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
        val assertions = buildList {
            // Always emit the c2pa.training-mining opt-out — NovaCut's
            // privacy posture is that exports can be marked do-not-train.
            add(
                Assertion(
                    label = "c2pa.training-mining",
                    data = mapOf(
                        "entries" to mapOf(
                            "c2pa.ai_generative_training" to mapOf("use" to "notAllowed"),
                            "c2pa.ai_inference" to mapOf("use" to "notAllowed"),
                            "c2pa.ai_training" to mapOf("use" to "notAllowed"),
                            "c2pa.data_mining" to mapOf("use" to "notAllowed")
                        )
                    )
                )
            )
            add(
                Assertion(
                    label = "c2pa.thumbnail.claim.jpeg",
                    data = mapOf(
                        "claimGeneratorInfo" to mapOf(
                            "name" to "NovaCut",
                            "version" to novaCutVersionName
                        )
                    )
                )
            )
            // c2pa.created action for the export itself.
            add(
                Assertion(
                    label = "c2pa.actions.created",
                    data = mapOf(
                        "action" to "c2pa.created",
                        "softwareAgent" to "NovaCut/$novaCutVersionName",
                        "when" to exporterCreationTimeMs
                    )
                )
            )
            // AI usage actions, when any.
            aiActionsAssertion(ledger)?.let { add(it) }
        }
        return C2paManifest(
            claimGenerator = "NovaCut/$novaCutVersionName",
            claimGeneratorInfoVersion = novaCutVersionName,
            title = projectTitle?.takeIf { it.isNotBlank() },
            signingMode = signingMode,
            assertions = assertions
        )
    }

    fun manifestToJson(manifest: C2paManifest): JSONObject {
        return JSONObject().apply {
            put("schema", "com.novacut.c2pa-manifest.v1")
            put("claimGenerator", manifest.claimGenerator)
            put("claimGeneratorInfoVersion", manifest.claimGeneratorInfoVersion)
            put("title", manifest.title ?: JSONObject.NULL)
            put("signingMode", manifest.signingMode.name)
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

    /**
     * Sign and embed the manifest into the exported MP4 at [outputPath].
     * Returns a [SignResult] describing what happened.
     *
     * Today this is a stub that returns `UNAVAILABLE`. When the c2pa AAR
     * lands, this method bridges to its Builder API; the rest of the
     * engine surface (manifest construction, AI-ledger → assertion
     * conversion) does not change.
     */
    suspend fun signAndEmbed(
        manifest: C2paManifest,
        outputPath: String
    ): SignResult {
        require(outputPath.isNotBlank()) { "outputPath must not be blank" }
        Log.d(TAG, "signAndEmbed: stub — no c2pa library wired (target=$outputPath, mode=${manifest.signingMode})")
        return SignResult.Unavailable(
            reason = "c2pa-android is not yet wired in this build"
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
        private const val TAG = "C2paExportEngine"
    }
}

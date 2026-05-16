package com.novacut.editor.engine

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stub engine -- requires app.rive:rive-android. See ROADMAP.md Tier A.13 and R6.16.
 *
 * Rive brings interactive animations with state-machine inputs, 120 fps playback,
 * and tiny vector-based asset sizes vs Lottie's JSON. Pairs with existing
 * [LottieTemplateEngine] as a parallel rendering path.
 *
 * ## Round 6 reassessment (R6.16)
 *
 * Lottie shipped state machines in late 2025 and dotLottie compressed containers
 * (10-15x smaller files than equivalent JSON). When `lottie-compose:7.x` lands
 * with the state-machine API, the Lottie path will reach near-parity with Rive
 * for the *interactive template* use case at zero additional SDK cost.
 *
 * That means **A.13 is downgraded to Under Consideration** in the Forward View:
 * keep the engine + stub so a future "we want Rive specifically for X" decision
 * is one dep flip away, but don't activate by default. The dotLottie path lands
 * inside [LottieTemplateEngine].
 *
 * ## Activation path (if A.13 ever moves back to Now/Next)
 *
 *   1. Add to gradle/libs.versions.toml:
 *        rive = "9.0.0"
 *        rive-android = { group = "app.rive", name = "rive-android",
 *                         version.ref = "rive" }
 *   2. Add `implementation(libs.rive.android)` to app/build.gradle.kts.
 *   3. Bundle the 5 starter `.riv` files under `app/src/main/assets/rive/`.
 *      Each must record SHA-256 in docs/models.md §1 before activation.
 *   4. Replace [renderFrame] with `RiveAnimationView.draw(Canvas)` against a
 *      bitmap-backed canvas at the requested dimensions, then read back.
 *   5. Match the [LottieOverlayEffect] (Media3 GlEffect) integration so Rive
 *      can drive the same export pipeline overlay slot.
 *
 * ## License
 *
 * Rive Android runtime is Apache-2.0. The bundled `.riv` files we ship must
 * each carry redistributable licenses.
 */
@Singleton
class RiveTemplateEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {

    data class RiveTemplate(
        val id: String,
        val displayName: String,
        val assetPath: String,
        val artboardName: String? = null,
        val stateMachineName: String? = null,
        val inputs: List<StateMachineInput> = emptyList()
    )

    data class StateMachineInput(
        val name: String,
        val type: InputType,
        val defaultValue: Any? = null
    ) {
        enum class InputType { BOOLEAN, NUMBER, TRIGGER }
    }

    /** Returns list of built-in Rive templates bundled with the app. */
    fun getBuiltInTemplates(): List<RiveTemplate> = BUILT_IN_TEMPLATES

    /** Whether Rive runtime is available on this device.
     *
     *  Reflection probe so callers can branch on presence without an explicit
     *  feature flag; flips automatically when the Rive AAR is added.
     */
    fun isAvailable(): Boolean {
        cachedAvailability?.let { return it }
        val available = try {
            Class.forName("app.rive.runtime.kotlin.RiveAnimationView")
            true
        } catch (_: ClassNotFoundException) {
            false
        } catch (e: Throwable) {
            Log.w(TAG, "RiveTemplateEngine availability probe threw an unexpected error", e)
            false
        }
        cachedAvailability = available
        if (!available) Log.d(TAG, "isAvailable: Rive dependency not present")
        return available
    }

    @Volatile private var cachedAvailability: Boolean? = null

    /**
     * Render one frame of a Rive template to a bitmap for export-pipeline compositing.
     * Returns null when Rive runtime is unavailable.
     */
    suspend fun renderFrame(
        template: RiveTemplate,
        timeSec: Float,
        widthPx: Int,
        heightPx: Int,
        inputValues: Map<String, Any> = emptyMap()
    ): Bitmap? = withContext(Dispatchers.Default) {
        Log.d(TAG, "renderFrame: stub -- requires Rive runtime (${template.id} @ ${timeSec}s)")
        null
    }

    companion object {
        private const val TAG = "RiveTemplateEngine"
        const val TARGET_RIVE_VERSION = "9.0.0"
        const val TARGET_MAVEN_GROUP = "app.rive"
        const val TARGET_MAVEN_NAME = "rive-android"

        private val BUILT_IN_TEMPLATES = listOf(
            RiveTemplate("rive_lower_third", "Interactive Lower Third", "rive/lower_third.riv"),
            RiveTemplate("rive_subscribe_btn", "Animated Subscribe", "rive/subscribe.riv"),
            RiveTemplate("rive_progress_bar", "Progress Bar", "rive/progress.riv"),
            RiveTemplate("rive_logo_reveal", "Morphing Logo Reveal", "rive/logo_reveal.riv"),
            RiveTemplate("rive_reaction_meter", "Reaction Meter", "rive/reaction_meter.riv")
        )
    }
}

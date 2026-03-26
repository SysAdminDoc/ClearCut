package com.novacut.editor.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Rive interactive template engine for next-gen motion graphics.
 *
 * Rive: github.com/rive-app/rive-android
 * - State machine-driven animations with user-adjustable parameters
 * - 120fps renderer with pristine antialiasing
 * - Binary .riv format (more compact than Lottie JSON)
 * - Built-in audio engine via miniaudio
 *
 * Dependency (add to build.gradle.kts):
 *   implementation("app.rive:rive-android:9.+")
 *
 * Usage flow:
 * 1. Load .riv template from assets
 * 2. Set state machine inputs (text, color, speed)
 * 3. Advance animation per frame
 * 4. Render to bitmap for video export
 */
@Singleton
class RiveTemplateEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    data class RiveTemplate(
        val id: String,
        val name: String,
        val category: String,
        val assetPath: String,
        val durationMs: Long,
        val editableInputs: List<RiveInput> = emptyList()
    )

    data class RiveInput(
        val name: String,
        val type: InputType,
        val defaultValue: Any
    )

    enum class InputType { TEXT, COLOR, NUMBER, BOOLEAN }

    /**
     * Built-in Rive templates (loaded from assets/rive_templates/).
     */
    fun getBuiltInTemplates(): List<RiveTemplate> = listOf(
        RiveTemplate(
            "interactive_lower_third", "Interactive Lower Third", "lower_third",
            "rive_templates/lower_third.riv", 4000L,
            listOf(
                RiveInput("name", InputType.TEXT, "Your Name"),
                RiveInput("title", InputType.TEXT, "Your Title"),
                RiveInput("accentColor", InputType.COLOR, 0xFFCBA6F7)
            )
        ),
        RiveTemplate(
            "animated_counter", "Animated Counter", "full_screen",
            "rive_templates/counter.riv", 3000L,
            listOf(
                RiveInput("targetNumber", InputType.NUMBER, 100),
                RiveInput("suffix", InputType.TEXT, "K")
            )
        ),
        RiveTemplate(
            "progress_bar", "Animated Progress Bar", "lower_third",
            "rive_templates/progress.riv", 2500L,
            listOf(
                RiveInput("progress", InputType.NUMBER, 75),
                RiveInput("label", InputType.TEXT, "Loading...")
            )
        ),
        RiveTemplate(
            "social_follow", "Social Follow CTA", "lower_third",
            "rive_templates/social_follow.riv", 3500L,
            listOf(
                RiveInput("username", InputType.TEXT, "@username"),
                RiveInput("platform", InputType.TEXT, "Instagram")
            )
        ),
        RiveTemplate(
            "particles_title", "Particle Title", "full_screen",
            "rive_templates/particles.riv", 3000L,
            listOf(
                RiveInput("title", InputType.TEXT, "Your Title"),
                RiveInput("particleCount", InputType.NUMBER, 50)
            )
        )
    )

    /**
     * Render a single frame of a Rive animation.
     * When rive-android dependency is added:
     *   val file = RiveFile(context.assets.open(assetPath).readBytes())
     *   val artboard = file.firstArtboard
     *   val smi = artboard.stateMachineInstance(0)
     *   // Set inputs
     *   smi.advance(frameTimeMs / 1000f)
     *   artboard.advance(frameTimeMs / 1000f)
     *   // Render to canvas
     *   val renderer = RiveArtboardRenderer(artboard)
     *   renderer.draw(canvas)
     */
    fun renderFrame(
        templateId: String,
        frameTimeMs: Long,
        width: Int,
        height: Int,
        inputs: Map<String, Any> = emptyMap()
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        // TODO: When Rive dependency is added, render the animation
        // For now, return transparent bitmap
        return bitmap
    }
}

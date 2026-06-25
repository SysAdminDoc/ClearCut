package com.novacut.editor.engine

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * R6.15 — AI Animated Subtitles per-word emphasis animator.
 *
 * Extends ClearCut's existing karaoke caption pipeline with per-word emphasis
 * styles. Whisper word timestamps already drive the karaoke highlight; this
 * animator maps `(animation, wordProgress, baseStyle)` → render-time
 * `WordRenderState` (scale, offsetX, offsetY, alpha, color blend) the caption
 * renderer applies on top of the base typography.
 *
 * Pure Kotlin so it tests on the JVM. The renderer (Canvas / OverlayEffect)
 * calls [emphasisFor] per word per frame and blends the result.
 *
 * ## Performance budget (per the roadmap entry)
 *
 * R6.15b: cap concurrent animating words to 3. The animator itself does not
 * enforce the cap — that lives in the renderer because it depends on the
 * window of currently-spoken words. The cap exists here as
 * [DEFAULT_MAX_CONCURRENT_ANIMATING_WORDS] so the renderer doesn't have to
 * remember the magic number.
 */
object WordEmphasisAnimator {

    /** Recommended hard cap on simultaneously animating words. */
    const val DEFAULT_MAX_CONCURRENT_ANIMATING_WORDS: Int = 3

    /**
     * Available per-word emphasis animations. Names align with the
     * CaptionTemplateType entries the gallery surfaces (Word Pop / Word
     * Bounce / Word Glow / Word Slide-In) so the gallery can map 1:1.
     */
    enum class Animation(val displayName: String) {
        /** No per-word emphasis; render the word at its base style. */
        NONE("None"),

        /** Scale up briefly past the base size, then settle back. */
        POP("Word Pop"),

        /** Vertical spring bounce; settles below base after the peak. */
        BOUNCE("Word Bounce"),

        /** Color shifts toward [WordRenderState.emphasisColor] then back. */
        GLOW("Word Glow"),

        /** Slide in from the right; settles at zero offset by t=1. */
        SLIDE_IN("Word Slide-In"),
    }

    /**
     * Render state for a single word at a single time. Values are deltas /
     * multipliers the renderer applies on top of the base caption style:
     *  - [scale]: 1.0 = no change.
     *  - [offsetXPx] / [offsetYPx]: pixel deltas from base position.
     *  - [alpha]: 1.0 = fully opaque.
     *  - [emphasisMix]: 0..1 — how much of [emphasisColor] to blend with
     *    the base text color. The base color comes from the
     *    CaptionStyleTemplate; the renderer does the actual lerp.
     *  - [emphasisColor]: ARGB packed color the renderer blends toward
     *    when [emphasisMix] > 0. Only meaningful for [Animation.GLOW].
     */
    data class WordRenderState(
        val scale: Float = 1f,
        val offsetXPx: Float = 0f,
        val offsetYPx: Float = 0f,
        val alpha: Float = 1f,
        val emphasisMix: Float = 0f,
        val emphasisColor: Long = 0xFFFFD700L,
    )

    /**
     * Compute the render state for a word.
     *
     * @param animation which emphasis to apply.
     * @param wordProgress 0..1 normalized time within the word's animation
     *   window. The renderer is responsible for picking the window — e.g.
     *   POP / BOUNCE / GLOW peak around the word's spoken-onset and fade
     *   over ~150 ms, while SLIDE_IN unfolds over the entire spoken duration.
     * @param baselineFontSizePx caption font size in pixels; needed so the
     *   POP / BOUNCE deltas scale with typography (a 60 px caption needs a
     *   larger bounce than a 20 px caption to read at the same intensity).
     * @param emphasisColor ARGB color the GLOW animation blends toward.
     */
    fun emphasisFor(
        animation: Animation,
        wordProgress: Float,
        baselineFontSizePx: Float = 24f,
        emphasisColor: Long = 0xFFFFD700L,
    ): WordRenderState {
        val t = wordProgress.coerceIn(0f, 1f)
        return when (animation) {
            Animation.NONE -> WordRenderState()

            Animation.POP -> {
                // Eased pulse: scale up then back. Peak at t=0.5 → scale 1.18.
                val pulse = sin((t * PI).toFloat())
                WordRenderState(scale = 1f + 0.18f * pulse)
            }

            Animation.BOUNCE -> {
                // Damped sine: peak negative y (upward) early, settle.
                val damp = (1f - t)
                val height = baselineFontSizePx * 0.35f
                val offset = -height * damp * sin((t * 2f * PI).toFloat())
                WordRenderState(offsetYPx = offset)
            }

            Animation.GLOW -> {
                // Mix toward emphasisColor, peak at t=0.5.
                val mix = sin((t * PI).toFloat())
                WordRenderState(emphasisMix = mix, emphasisColor = emphasisColor)
            }

            Animation.SLIDE_IN -> {
                // Ease-out: start one font-size to the right, settle at 0.
                val ease = 1f - (1f - t) * (1f - t)  // quadratic ease-out
                val travel = baselineFontSizePx * 1.5f
                WordRenderState(
                    offsetXPx = travel * (1f - ease),
                    alpha = ease.coerceIn(0f, 1f),
                )
            }
        }
    }

    /**
     * Compute progress for a word given the playhead time and the word's
     * (start, end) timestamps. Useful for the renderer to bridge from word
     * timestamps to the 0..1 animation domain without recomputing the
     * window boundaries.
     */
    fun wordProgress(
        playheadMs: Long,
        wordStartMs: Long,
        wordEndMs: Long,
        animationWindowMs: Long = 200L,
    ): Float {
        require(wordEndMs > wordStartMs) {
            "Word window must be non-empty: start=$wordStartMs end=$wordEndMs"
        }
        require(animationWindowMs > 0L) {
            "animationWindowMs must be positive: $animationWindowMs"
        }
        // The animation window is the lesser of the word's spoken duration
        // and the requested window — a 50 ms word can't carry a 200 ms anim.
        val window = minOf(animationWindowMs, wordEndMs - wordStartMs)
        val elapsed = (playheadMs - wordStartMs).coerceAtLeast(0L)
        if (elapsed >= window) return 1f
        return elapsed.toFloat() / window.toFloat()
    }
}

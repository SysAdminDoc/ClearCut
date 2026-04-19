package com.novacut.editor.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.dp

/**
 * NovaCut design tokens.
 *
 * Centralized spacing / radius / motion / elevation values so the editor surfaces have a
 * single, coherent rhythm instead of every panel inventing its own scale. Use these in place
 * of inline `8.dp` / `tween(120)` / `RoundedCornerShape(14.dp)` literals where practical.
 *
 * The scales are deliberately small. Every unit has a purpose.
 */
object Spacing {
    /** 2dp — hairline gaps between tightly coupled elements (icon-on-icon, badges). */
    val xxs = 2.dp

    /** 4dp — micro spacing inside chips, between an icon and its tight label. */
    val xs = 4.dp

    /** 8dp — default gap between sibling controls in a tight row. */
    val sm = 8.dp

    /** 12dp — comfortable gap between distinct controls; default panel-content spacing. */
    val md = 12.dp

    /** 16dp — section padding, default sheet padding, primary card padding. */
    val lg = 16.dp

    /** 20dp — breathing room between major panel sections, dialog padding. */
    val xl = 20.dp

    /** 24dp — outer padding for hero/onboarding surfaces. */
    val xxl = 24.dp

    /** 32dp — page-level top padding above headlines. */
    val xxxl = 32.dp
}

object Radius {
    /** 6dp — tags, micro-pills, single-letter badges. */
    val xs = 6.dp

    /** 10dp — tight buttons, slim chips. */
    val sm = 10.dp

    /** 12dp — text fields, default control surfaces. */
    val md = 12.dp

    /** 16dp — primary buttons, prominent chips. */
    val lg = 16.dp

    /** 20dp — cards inside panels. */
    val xl = 20.dp

    /** 24dp — top-level panel/sheet corners. */
    val xxl = 24.dp

    /** 999dp — fully rounded (capsule, dot). */
    val pill = 999.dp
}

object Elevation {
    /** Background — lowest layer (app scaffold). */
    val flat = 0.dp

    /** Cards on top of panels. */
    val card = 1.dp

    /** Floating panels, elevated chips. */
    val raised = 3.dp

    /** Sheets, dialogs. */
    val sheet = 6.dp

    /** Snackbars, transient floating affordances. */
    val toast = 8.dp
}

/**
 * Motion tokens.
 *
 * Premium UI feels coherent because every transition shares the same easing curves and
 * durations. Use these instead of ad-hoc `tween(150)` / `spring()` calls.
 */
object Motion {
    /** Material 3 emphasized easing (FastOutSlowIn-equivalent, more cinematic). */
    val EmphasizedEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** Decelerate — incoming content (panels showing, toasts entering). */
    val DecelerateEasing = CubicBezierEasing(0f, 0f, 0.2f, 1f)

    /** Accelerate — outgoing content (panels dismissing). */
    val AccelerateEasing = CubicBezierEasing(0.4f, 0f, 1f, 1f)

    /** Standard easing — symmetric for hover/press/selection state changes. */
    val StandardEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** 120 ms — instant feedback (selection indicators, hover/press tints). */
    const val DurationFast = 120

    /** 200 ms — small UI changes (chip expansion, badge pulses, dropdown unfurl). */
    const val DurationStandard = 200

    /** 280 ms — panel + sheet enter/exit, full-section transitions. */
    const val DurationMedium = 280

    /** 400 ms — large reveals (onboarding cards, hero state changes). */
    const val DurationLarge = 400

    fun fast(easing: CubicBezierEasing = StandardEasing) =
        tween<Float>(durationMillis = DurationFast, easing = easing)

    fun standard(easing: CubicBezierEasing = StandardEasing) =
        tween<Float>(durationMillis = DurationStandard, easing = easing)

    fun panelEnter() =
        tween<Float>(durationMillis = DurationMedium, easing = DecelerateEasing)

    fun panelExit() =
        tween<Float>(durationMillis = DurationFast, easing = AccelerateEasing)

    /** Springy bounce — only for delightful confirmations (success ticks, save badges). */
    fun bounceSpring() = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    /** Critical damped spring — primary tactile interactions (chip selection, knob feedback). */
    fun snappySpring() = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
}

/**
 * Touch target tokens. Material 3 spec is 48dp minimum; we provide an extra-comfy variant
 * for the editor's primary-action affordances on phones held in landscape.
 */
object TouchTarget {
    val minimum = 48.dp
    val comfortable = 56.dp
}

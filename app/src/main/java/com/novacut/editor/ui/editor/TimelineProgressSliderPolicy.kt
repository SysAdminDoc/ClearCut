package com.novacut.editor.ui.editor

/**
 * R6.10d decision guard for Media3's Material3 ProgressSlider.
 *
 * The official ProgressSlider is a player-position control. ClearCut's ruler
 * and mini-player are project-timeline controls whose source of truth is
 * playheadMs / totalDurationMs plus editor callbacks.
 */
object TimelineProgressSliderPolicy {
    enum class Requirement {
        EXTERNAL_TIMELINE_VALUE,
        EXTERNAL_SEEK_CALLBACK,
        SCRUB_START_END_CALLBACKS,
        ZOOMED_SCROLL_WINDOW,
        MARKER_AND_SNAP_OVERLAYS,
        CLIP_HIT_TARGETS,
        CLEARCUT_COLORS,
    }

    enum class AdoptionDecision {
        KEEP_CUSTOM_TIMELINE_RULER,
        KEEP_EXTERNAL_MATERIAL_SLIDER,
        ADOPT_MEDIA3_PROGRESS_SLIDER,
    }

    data class ProgressSliderProfile(
        val usesPlayerPosition: Boolean,
        val performsSeekInternally: Boolean,
        val supportsExternalValue: Boolean,
        val supportsExternalSeekCallback: Boolean,
        val supportsScrubLifecycleCallbacks: Boolean,
        val supportsZoomedScrollWindow: Boolean,
        val supportsMarkerAndSnapOverlays: Boolean,
        val supportsClipHitTargets: Boolean,
        val supportsThemeColors: Boolean,
    ) {
        companion object {
            val MEDIA3_1_10_1 = ProgressSliderProfile(
                usesPlayerPosition = true,
                performsSeekInternally = true,
                supportsExternalValue = false,
                supportsExternalSeekCallback = false,
                supportsScrubLifecycleCallbacks = false,
                supportsZoomedScrollWindow = false,
                supportsMarkerAndSnapOverlays = false,
                supportsClipHitTargets = false,
                supportsThemeColors = true,
            )
        }
    }

    data class Evaluation(
        val decision: AdoptionDecision,
        val missingRequirements: Set<Requirement>,
    )

    val timelineRulerRequirements: Set<Requirement> = setOf(
        Requirement.EXTERNAL_TIMELINE_VALUE,
        Requirement.EXTERNAL_SEEK_CALLBACK,
        Requirement.SCRUB_START_END_CALLBACKS,
        Requirement.ZOOMED_SCROLL_WINDOW,
        Requirement.MARKER_AND_SNAP_OVERLAYS,
        Requirement.CLIP_HIT_TARGETS,
        Requirement.CLEARCUT_COLORS,
    )

    val miniPlayerRequirements: Set<Requirement> = setOf(
        Requirement.EXTERNAL_TIMELINE_VALUE,
        Requirement.EXTERNAL_SEEK_CALLBACK,
        Requirement.CLEARCUT_COLORS,
    )

    fun evaluateTimelineRuler(
        profile: ProgressSliderProfile = ProgressSliderProfile.MEDIA3_1_10_1,
    ): Evaluation {
        val missing = timelineRulerRequirements.filterNot { profile.satisfies(it) }.toSet()
        return Evaluation(
            decision = if (missing.isEmpty()) {
                AdoptionDecision.ADOPT_MEDIA3_PROGRESS_SLIDER
            } else {
                AdoptionDecision.KEEP_CUSTOM_TIMELINE_RULER
            },
            missingRequirements = missing,
        )
    }

    fun evaluateMiniPlayer(
        profile: ProgressSliderProfile = ProgressSliderProfile.MEDIA3_1_10_1,
    ): Evaluation {
        val missing = miniPlayerRequirements.filterNot { profile.satisfies(it) }.toSet()
        return Evaluation(
            decision = if (missing.isEmpty()) {
                AdoptionDecision.ADOPT_MEDIA3_PROGRESS_SLIDER
            } else {
                AdoptionDecision.KEEP_EXTERNAL_MATERIAL_SLIDER
            },
            missingRequirements = missing,
        )
    }

    private fun ProgressSliderProfile.satisfies(requirement: Requirement): Boolean = when (requirement) {
        Requirement.EXTERNAL_TIMELINE_VALUE -> supportsExternalValue && !usesPlayerPosition
        Requirement.EXTERNAL_SEEK_CALLBACK -> supportsExternalSeekCallback && !performsSeekInternally
        Requirement.SCRUB_START_END_CALLBACKS -> supportsScrubLifecycleCallbacks
        Requirement.ZOOMED_SCROLL_WINDOW -> supportsZoomedScrollWindow
        Requirement.MARKER_AND_SNAP_OVERLAYS -> supportsMarkerAndSnapOverlays
        Requirement.CLIP_HIT_TARGETS -> supportsClipHitTargets
        Requirement.CLEARCUT_COLORS -> supportsThemeColors
    }
}

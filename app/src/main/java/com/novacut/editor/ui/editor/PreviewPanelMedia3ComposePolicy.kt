package com.novacut.editor.ui.editor

/**
 * R6.10b decision guard for Media3's Material3 Compose player widgets.
 *
 * Keep this Android-free so future Media3 evaluations can be tested without a
 * Compose runtime. The default profile mirrors the inspected Media3 1.10.1
 * `media3-ui-compose-material3` surface.
 */
object PreviewPanelMedia3ComposePolicy {
    enum class Requirement {
        PLAYER_SURFACE,
        PLAYBACK_CONTROLS,
        TIMELINE_RELATIVE_SEEKING,
        GAP_RECOVERY,
        STILL_IMAGE_FALLBACK,
        TRANSFORM_GESTURES,
        SCOPES_TOGGLE,
        NOVACUT_CHROME
    }

    enum class AdoptionDecision {
        ADOPT_FULL_PLAYER,
        KEEP_PREVIEW_PANEL,
        ADOPT_TARGETED_CONTROLS_ONLY
    }

    data class Media3ComposeProfile(
        val hasPlayerComposable: Boolean,
        val hasMaterial3PlaybackControls: Boolean,
        val hasContentFrameSurface: Boolean,
        val progressSliderUsesPlayerTimeline: Boolean,
        val progressSliderSeeksInternally: Boolean,
        val progressSliderSupportsExternalTimelineValue: Boolean,
        val supportsGapRecovery: Boolean,
        val supportsStillImageFallback: Boolean,
        val supportsTransformGestures: Boolean,
        val supportsScopesToggle: Boolean,
        val supportsNovaCutChrome: Boolean,
    ) {
        companion object {
            val MEDIA3_1_10_1 = Media3ComposeProfile(
                hasPlayerComposable = true,
                hasMaterial3PlaybackControls = true,
                hasContentFrameSurface = true,
                progressSliderUsesPlayerTimeline = true,
                progressSliderSeeksInternally = true,
                progressSliderSupportsExternalTimelineValue = false,
                supportsGapRecovery = false,
                supportsStillImageFallback = false,
                supportsTransformGestures = false,
                supportsScopesToggle = false,
                supportsNovaCutChrome = false,
            )
        }
    }

    data class Evaluation(
        val decision: AdoptionDecision,
        val missingRequirements: Set<Requirement>,
        val progressSliderCanReplaceTimelineSeek: Boolean,
        val targetedControlsWorthRevisiting: Boolean,
    )

    val previewPanelRequirements: Set<Requirement> = setOf(
        Requirement.PLAYER_SURFACE,
        Requirement.PLAYBACK_CONTROLS,
        Requirement.TIMELINE_RELATIVE_SEEKING,
        Requirement.GAP_RECOVERY,
        Requirement.STILL_IMAGE_FALLBACK,
        Requirement.TRANSFORM_GESTURES,
        Requirement.SCOPES_TOGGLE,
        Requirement.NOVACUT_CHROME,
    )

    fun evaluate(
        requirements: Set<Requirement> = previewPanelRequirements,
        profile: Media3ComposeProfile = Media3ComposeProfile.MEDIA3_1_10_1,
    ): Evaluation {
        val missing = requirements.filterNot { profile.satisfies(it) }.toSet()
        val progressSliderCanReplaceTimelineSeek =
            profile.progressSliderSupportsExternalTimelineValue &&
                !profile.progressSliderSeeksInternally &&
                !profile.progressSliderUsesPlayerTimeline
        val targetedControlsWorthRevisiting =
            profile.hasMaterial3PlaybackControls && profile.hasContentFrameSurface
        val decision = when {
            missing.isEmpty() && progressSliderCanReplaceTimelineSeek -> AdoptionDecision.ADOPT_FULL_PLAYER
            missing.all { it == Requirement.NOVACUT_CHROME } && targetedControlsWorthRevisiting ->
                AdoptionDecision.ADOPT_TARGETED_CONTROLS_ONLY
            else -> AdoptionDecision.KEEP_PREVIEW_PANEL
        }

        return Evaluation(
            decision = decision,
            missingRequirements = missing,
            progressSliderCanReplaceTimelineSeek = progressSliderCanReplaceTimelineSeek,
            targetedControlsWorthRevisiting = targetedControlsWorthRevisiting,
        )
    }

    private fun Media3ComposeProfile.satisfies(requirement: Requirement): Boolean = when (requirement) {
        Requirement.PLAYER_SURFACE -> hasPlayerComposable || hasContentFrameSurface
        Requirement.PLAYBACK_CONTROLS -> hasMaterial3PlaybackControls
        Requirement.TIMELINE_RELATIVE_SEEKING ->
            progressSliderSupportsExternalTimelineValue &&
                !progressSliderSeeksInternally &&
                !progressSliderUsesPlayerTimeline
        Requirement.GAP_RECOVERY -> supportsGapRecovery
        Requirement.STILL_IMAGE_FALLBACK -> supportsStillImageFallback
        Requirement.TRANSFORM_GESTURES -> supportsTransformGestures
        Requirement.SCOPES_TOGGLE -> supportsScopesToggle
        Requirement.NOVACUT_CHROME -> supportsNovaCutChrome
    }
}

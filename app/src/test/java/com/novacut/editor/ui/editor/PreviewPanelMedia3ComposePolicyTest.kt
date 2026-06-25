package com.novacut.editor.ui.editor

import com.novacut.editor.ui.editor.PreviewPanelMedia3ComposePolicy.AdoptionDecision
import com.novacut.editor.ui.editor.PreviewPanelMedia3ComposePolicy.Requirement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewPanelMedia3ComposePolicyTest {
    @Test
    fun defaultMedia3ProfileKeepsClearCutPreviewPanel() {
        val evaluation = PreviewPanelMedia3ComposePolicy.evaluate()

        assertEquals(AdoptionDecision.KEEP_PREVIEW_PANEL, evaluation.decision)
        assertTrue(Requirement.TIMELINE_RELATIVE_SEEKING in evaluation.missingRequirements)
        assertTrue(Requirement.GAP_RECOVERY in evaluation.missingRequirements)
        assertTrue(Requirement.STILL_IMAGE_FALLBACK in evaluation.missingRequirements)
        assertTrue(Requirement.TRANSFORM_GESTURES in evaluation.missingRequirements)
        assertTrue(Requirement.SCOPES_TOGGLE in evaluation.missingRequirements)
    }

    @Test
    fun media3ProgressSliderDoesNotReplaceTimelineSeekContract() {
        val evaluation = PreviewPanelMedia3ComposePolicy.evaluate()

        assertFalse(evaluation.progressSliderCanReplaceTimelineSeek)
    }

    @Test
    fun targetedControlsRemainWorthRevisitingWithoutFullPlayerReplacement() {
        val evaluation = PreviewPanelMedia3ComposePolicy.evaluate()

        assertTrue(evaluation.targetedControlsWorthRevisiting)
        assertEquals(AdoptionDecision.KEEP_PREVIEW_PANEL, evaluation.decision)
    }

    @Test
    fun fullyCompatibleFutureProfileCanAdoptFullPlayer() {
        val futureProfile = PreviewPanelMedia3ComposePolicy.Media3ComposeProfile(
            hasPlayerComposable = true,
            hasMaterial3PlaybackControls = true,
            hasContentFrameSurface = true,
            progressSliderUsesPlayerTimeline = false,
            progressSliderSeeksInternally = false,
            progressSliderSupportsExternalTimelineValue = true,
            supportsGapRecovery = true,
            supportsStillImageFallback = true,
            supportsTransformGestures = true,
            supportsScopesToggle = true,
            supportsClearCutChrome = true,
        )

        val evaluation = PreviewPanelMedia3ComposePolicy.evaluate(profile = futureProfile)

        assertEquals(AdoptionDecision.ADOPT_FULL_PLAYER, evaluation.decision)
        assertTrue(evaluation.missingRequirements.isEmpty())
        assertTrue(evaluation.progressSliderCanReplaceTimelineSeek)
    }
}

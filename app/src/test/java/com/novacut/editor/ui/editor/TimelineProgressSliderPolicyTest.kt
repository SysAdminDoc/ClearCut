package com.novacut.editor.ui.editor

import com.novacut.editor.ui.editor.TimelineProgressSliderPolicy.AdoptionDecision
import com.novacut.editor.ui.editor.TimelineProgressSliderPolicy.Requirement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineProgressSliderPolicyTest {
    @Test
    fun media3ProgressSliderDoesNotReplaceTimelineRuler() {
        val evaluation = TimelineProgressSliderPolicy.evaluateTimelineRuler()

        assertEquals(AdoptionDecision.KEEP_CUSTOM_TIMELINE_RULER, evaluation.decision)
        assertTrue(Requirement.EXTERNAL_TIMELINE_VALUE in evaluation.missingRequirements)
        assertTrue(Requirement.EXTERNAL_SEEK_CALLBACK in evaluation.missingRequirements)
        assertTrue(Requirement.ZOOMED_SCROLL_WINDOW in evaluation.missingRequirements)
        assertTrue(Requirement.MARKER_AND_SNAP_OVERLAYS in evaluation.missingRequirements)
        assertTrue(Requirement.CLIP_HIT_TARGETS in evaluation.missingRequirements)
    }

    @Test
    fun miniPlayerKeepsExternalMaterialSliderForProjectTimelineSeek() {
        val evaluation = TimelineProgressSliderPolicy.evaluateMiniPlayer()

        assertEquals(AdoptionDecision.KEEP_EXTERNAL_MATERIAL_SLIDER, evaluation.decision)
        assertTrue(Requirement.EXTERNAL_TIMELINE_VALUE in evaluation.missingRequirements)
        assertTrue(Requirement.EXTERNAL_SEEK_CALLBACK in evaluation.missingRequirements)
    }

    @Test
    fun futureExternallyControlledProgressSliderCanReplaceMiniPlayerSlider() {
        val futureProfile = TimelineProgressSliderPolicy.ProgressSliderProfile(
            usesPlayerPosition = false,
            performsSeekInternally = false,
            supportsExternalValue = true,
            supportsExternalSeekCallback = true,
            supportsScrubLifecycleCallbacks = true,
            supportsZoomedScrollWindow = false,
            supportsMarkerAndSnapOverlays = false,
            supportsClipHitTargets = false,
            supportsThemeColors = true,
        )

        val evaluation = TimelineProgressSliderPolicy.evaluateMiniPlayer(profile = futureProfile)

        assertEquals(AdoptionDecision.ADOPT_MEDIA3_PROGRESS_SLIDER, evaluation.decision)
        assertTrue(evaluation.missingRequirements.isEmpty())
    }

    @Test
    fun futureTimelineAwareProgressSliderCanReplaceRulerOnlyWithFullEditorRequirements() {
        val futureProfile = TimelineProgressSliderPolicy.ProgressSliderProfile(
            usesPlayerPosition = false,
            performsSeekInternally = false,
            supportsExternalValue = true,
            supportsExternalSeekCallback = true,
            supportsScrubLifecycleCallbacks = true,
            supportsZoomedScrollWindow = true,
            supportsMarkerAndSnapOverlays = true,
            supportsClipHitTargets = true,
            supportsThemeColors = true,
        )

        val evaluation = TimelineProgressSliderPolicy.evaluateTimelineRuler(profile = futureProfile)

        assertEquals(AdoptionDecision.ADOPT_MEDIA3_PROGRESS_SLIDER, evaluation.decision)
        assertTrue(evaluation.missingRequirements.isEmpty())
    }
}

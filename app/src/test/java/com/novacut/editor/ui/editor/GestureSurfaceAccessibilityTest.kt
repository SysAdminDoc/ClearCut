package com.novacut.editor.ui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import com.novacut.editor.model.CurvePoint
import com.novacut.editor.model.Keyframe
import com.novacut.editor.model.KeyframeProperty
import com.novacut.editor.ui.theme.ClearCutTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GestureSurfaceAccessibilityTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun transform_surface_exposes_state_and_every_transform_axis() {
        compose.setContent {
            ClearCutTheme {
                TransformOverlay(
                    positionX = 0f,
                    positionY = 0f,
                    scaleX = 1f,
                    scaleY = 1f,
                    rotation = 0f,
                    anchorX = 0.5f,
                    anchorY = 0.5f,
                    opacity = 1f,
                    previewWidth = 320f,
                    previewHeight = 180f,
                    onPositionChanged = { _, _ -> },
                    onScaleChanged = { _, _ -> },
                    onRotationChanged = {},
                    onAnchorChanged = { _, _ -> },
                    onTransformStarted = {},
                    modifier = Modifier.size(320.dp),
                )
            }
        }

        val node = compose.onNode(stateDescriptionStartsWith("Transform:"))
        node.assertExists()
        val labels = node.customActionLabels()
        assertTrue(labels.containsAll(listOf("Move left", "Move right", "Move up", "Move down")))
        assertTrue(labels.containsAll(listOf("Increase scale", "Decrease scale", "Widen", "Narrow")))
        assertTrue(labels.containsAll(listOf("Rotate clockwise", "Rotate counterclockwise")))
        assertTrue(labels.containsAll(listOf("Move anchor left", "Move anchor right")))
    }

    @Test
    fun color_curve_surface_exposes_point_nudges_and_add_action() {
        compose.setContent {
            ClearCutTheme {
                CurveEditor(
                    points = listOf(CurvePoint(0f, 0f), CurvePoint(1f, 1f)),
                    color = androidx.compose.ui.graphics.Color.White,
                    accessibilityLabel = "Master",
                    onPointsChanged = {},
                    modifier = Modifier.size(320.dp),
                )
            }
        }

        val node = compose.onNode(stateDescriptionStartsWith("Master curve:"))
        node.assertExists()
        val labels = node.customActionLabels()
        assertTrue(labels.containsAll(listOf("Move left", "Move right", "Move up", "Move down")))
        assertTrue(labels.contains("Add curve point at center"))
    }

    @Test
    fun preview_surface_exposes_pan_zoom_and_rotation_actions() {
        compose.setContent {
            ClearCutTheme {
                Box(
                    modifier = Modifier.previewTransformAccessibilityModifier(
                        canTransformPreview = true,
                        positionX = 0f,
                        positionY = 0f,
                        scaleX = 1f,
                        scaleY = 1f,
                        rotation = 0f,
                        onPreviewTransformStarted = {},
                        onPreviewTransformEnded = {},
                        onPreviewTransformChanged = { _, _, _, _ -> },
                    ).size(320.dp)
                )
            }
        }

        val node = compose.onNode(stateDescriptionStartsWith("Preview transform:"))
        node.assertExists()
        val labels = node.customActionLabels()
        assertTrue(labels.containsAll(listOf("Move left", "Move right", "Move up", "Move down")))
        assertTrue(labels.containsAll(listOf("Zoom in", "Zoom out", "Rotate clockwise", "Rotate counterclockwise")))
    }

    @Test
    fun keyframe_curve_surface_exposes_timing_and_value_nudges() {
        val keyframe = Keyframe(1_000L, KeyframeProperty.POSITION_X, 0f)
        compose.setContent {
            ClearCutTheme {
                CurveCanvas(
                    keyframes = listOf(keyframe),
                    clipDurationMs = 10_000L,
                    playheadMs = 1_000L,
                    activeProperties = setOf(KeyframeProperty.POSITION_X),
                    selectedKeyframe = keyframe,
                    onKeyframeSelected = {},
                    onKeyframeMoved = { _, _, _ -> },
                    onAddKeyframe = { _, _, _ -> },
                    modifier = Modifier.size(320.dp),
                )
            }
        }

        val node = compose.onNode(stateDescriptionStartsWith("Keyframe curve:"))
        node.assertExists()
        val labels = node.customActionLabels()
        assertTrue(labels.containsAll(listOf("Move keyframe earlier", "Move keyframe later")))
        assertTrue(labels.containsAll(listOf("Increase keyframe value", "Decrease keyframe value")))
        assertTrue(labels.contains("Add keyframe at playhead"))
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.customActionLabels(): List<String> {
        val node = fetchSemanticsNode()
        val actions = if (node.config.contains(SemanticsActions.CustomActions)) {
            node.config[SemanticsActions.CustomActions]
        } else {
            emptyList()
        }
        assertEquals(actions.size, actions.map { it.label }.toSet().size)
        return actions.map { it.label }
    }

    private fun stateDescriptionStartsWith(prefix: String): SemanticsMatcher = SemanticsMatcher(
        description = "state description starts with '$prefix'"
    ) { node ->
        node.config.contains(SemanticsProperties.StateDescription) &&
            node.config[SemanticsProperties.StateDescription].startsWith(prefix)
    }
}

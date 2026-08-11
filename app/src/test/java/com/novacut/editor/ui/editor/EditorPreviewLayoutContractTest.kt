package com.novacut.editor.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EditorPreviewLayoutContractTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun previewOwnsFlexibleHeightWhileTimelineIsBounded() {
        compose.setContent {
            Column(Modifier.size(400.dp)) {
                Box(
                    modifier = editorPreviewModifier(
                        immersivePreview = false,
                        previewMinHeight = 120.dp,
                    )
                        .background(Color.Black)
                        .testTag("preview")
                )
                Box(
                    modifier = Modifier
                        .editorTimelineModifier(100.dp, 160.dp)
                        .height(400.dp)
                        .background(Color.DarkGray)
                        .testTag("timeline")
                )
                Box(Modifier.height(40.dp).fillMaxSize().testTag("tools"))
            }
        }

        compose.onNodeWithTag("preview").assertHeightIsAtLeast(120.dp)
        compose.onNodeWithTag("timeline").assertHeightIsEqualTo(160.dp)

        val previewHeight = compose.onNodeWithTag("preview").fetchSemanticsNode().boundsInRoot.height
        val timelineHeight = compose.onNodeWithTag("timeline").fetchSemanticsNode().boundsInRoot.height
        assertTrue("preview should receive the flexible remainder", previewHeight > timelineHeight)
    }
}

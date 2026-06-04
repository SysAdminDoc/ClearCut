package com.novacut.editor

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.novacut.editor.ui.NovaCutTestTags
import org.junit.Rule
import org.junit.Test

class NovaCutSmokeTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun projectEditorExportAndSettingsSurfacesOpen() {
        compose.onNodeWithTag(NovaCutTestTags.PROJECTS_SCREEN).assertIsDisplayed()

        compose.onNodeWithTag(NovaCutTestTags.PROJECTS_CREATE_PROJECT).performClick()
        compose.onNodeWithTag(NovaCutTestTags.TEMPLATE_SHEET).assertIsDisplayed()
        compose.onNodeWithTag(NovaCutTestTags.TEMPLATE_BLANK)
            .performScrollTo()
            .performClick()

        compose.waitUntilAtLeastOneExists(NovaCutTestTags.EDITOR_SCREEN)
        dismissTutorialIfPresent()

        compose.onNodeWithTag(NovaCutTestTags.EDITOR_EMPTY_ADD_MEDIA).assertIsDisplayed().performClick()
        compose.onNodeWithTag(NovaCutTestTags.MEDIA_PICKER_SHEET).assertIsDisplayed()
        compose.onNodeWithTag(NovaCutTestTags.MEDIA_PICKER_CLOSE).performClick()

        compose.onNodeWithTag(NovaCutTestTags.EDITOR_EXPORT).assertIsDisplayed().performClick()
        compose.onNodeWithTag(NovaCutTestTags.EXPORT_SHEET).assertIsDisplayed()
        compose.onNodeWithTag(NovaCutTestTags.EXPORT_CLOSE).performClick()

        compose.onNodeWithTag(NovaCutTestTags.EDITOR_BACK).performClick()
        compose.waitUntilAtLeastOneExists(NovaCutTestTags.PROJECTS_SCREEN)

        compose.onNodeWithTag(NovaCutTestTags.PROJECTS_SETTINGS).performClick()
        compose.onNodeWithTag(NovaCutTestTags.SETTINGS_SCREEN).assertIsDisplayed()
        compose.onNodeWithTag(NovaCutTestTags.SETTINGS_PRIVACY_OPEN)
            .performScrollTo()
            .performClick()
        compose.onNodeWithTag(NovaCutTestTags.SETTINGS_PRIVACY_DASHBOARD).assertIsDisplayed()
        compose.onNodeWithTag(NovaCutTestTags.SETTINGS_PRIVACY_CLOSE).performClick()
        compose.onNodeWithTag(NovaCutTestTags.SETTINGS_BACK).performClick()

        compose.waitUntilAtLeastOneExists(NovaCutTestTags.PROJECTS_SCREEN)
    }

    private fun dismissTutorialIfPresent() {
        runCatching {
            compose.waitUntil(timeoutMillis = 1_200L) {
                compose.onAllNodesWithTag(NovaCutTestTags.TUTORIAL_SKIP)
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
        }
        val tutorialNodes = compose.onAllNodesWithTag(NovaCutTestTags.TUTORIAL_SKIP).fetchSemanticsNodes()
        if (tutorialNodes.isNotEmpty()) {
            compose.onNodeWithTag(NovaCutTestTags.TUTORIAL_SKIP).performClick()
        }
    }

    private fun AndroidComposeTestRule<*, *>.waitUntilAtLeastOneExists(
        tag: String,
        timeoutMillis: Long = 10_000L
    ) {
        waitUntil(timeoutMillis) {
            onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }
}

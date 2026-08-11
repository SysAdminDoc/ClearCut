package com.novacut.editor

import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckResult
import com.google.android.apps.common.testing.accessibility.framework.integrations.espresso.AccessibilityValidator
import com.github.takahirom.roborazzi.captureRoboImage
import com.novacut.editor.engine.AppearanceMode
import com.novacut.editor.ui.ClearCutTestTags
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = ClearCutApp::class, sdk = [35])
class JvmVisualVerificationTest {
    private val goldenDirectory = File(System.getProperty("user.dir"), "src/test/screenshots")

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>().apply {
        enableAccessibilityChecks(
            AccessibilityValidator().setThrowExceptionFor(
                AccessibilityCheckResult.AccessibilityCheckResultType.WARNING
            )
        )
    }

    @Test
    fun dashboard_editor_export_and_settings_render_in_both_dark_modes() {
        capture("project-dashboard-dark.png")

        compose.onNodeWithTag(ClearCutTestTags.PROJECTS_CREATE_PROJECT).performClick()
        compose.onNodeWithTag(ClearCutTestTags.TEMPLATE_BLANK)
            .performScrollTo()
            .performClick()
        waitUntilAtLeastOneExists(ClearCutTestTags.EDITOR_SCREEN)
        dismissTutorialIfPresent()
        capture("editor-dark.png")

        compose.onNodeWithTag(ClearCutTestTags.EDITOR_EXPORT).performClick()
        waitUntilAtLeastOneExists(ClearCutTestTags.EXPORT_SHEET)
        capture("export-sheet-dark.png")
        compose.onNodeWithTag(ClearCutTestTags.EXPORT_CLOSE).performClick()

        compose.onNodeWithTag(ClearCutTestTags.EDITOR_BACK).performClick()
        waitUntilAtLeastOneExists(ClearCutTestTags.PROJECTS_SCREEN)
        compose.onNodeWithTag(ClearCutTestTags.PROJECTS_SETTINGS).performClick()
        waitUntilAtLeastOneExists(ClearCutTestTags.SETTINGS_SCREEN)
        capture("settings-dark.png")

        updateAppearance(AppearanceMode.HIGH_CONTRAST_DARK)
        capture("settings-high-contrast-dark.png")

        compose.onNodeWithTag(ClearCutTestTags.SETTINGS_BACK).performClick()
        waitUntilAtLeastOneExists(ClearCutTestTags.PROJECTS_SCREEN)
        capture("project-dashboard-high-contrast-dark.png")

        compose.onNodeWithTag(ClearCutTestTags.PROJECTS_CREATE_PROJECT).performClick()
        compose.onNodeWithTag(ClearCutTestTags.TEMPLATE_BLANK)
            .performScrollTo()
            .performClick()
        waitUntilAtLeastOneExists(ClearCutTestTags.EDITOR_SCREEN)
        dismissTutorialIfPresent()
        capture("editor-high-contrast-dark.png")

        compose.onNodeWithTag(ClearCutTestTags.EDITOR_EXPORT).performClick()
        waitUntilAtLeastOneExists(ClearCutTestTags.EXPORT_SHEET)
        capture("export-sheet-high-contrast-dark.png")
    }

    private fun capture(name: String) {
        if (System.getProperty("clearcut.visual.capture") != "true") return
        compose.waitForIdle()
        compose.onRoot().tryPerformAccessibilityChecks()
        compose.onRoot().captureRoboImage(File(goldenDirectory, name))
    }

    private fun updateAppearance(mode: AppearanceMode) {
        runBlocking {
            compose.activity.settingsRepository.updateAppearanceMode(mode)
            compose.activity.settingsRepository.settings.first { it.appearanceMode == mode }
        }
        compose.waitForIdle()
    }

    private fun waitUntilAtLeastOneExists(tag: String, timeoutMillis: Long = 10_000L) {
        compose.waitUntil(timeoutMillis) {
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun dismissTutorialIfPresent() {
        runCatching {
            compose.waitUntil(1_200L) {
                compose.onAllNodesWithTag(ClearCutTestTags.TUTORIAL_SKIP)
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
        }
        if (compose.onAllNodesWithTag(ClearCutTestTags.TUTORIAL_SKIP)
                .fetchSemanticsNodes()
                .isNotEmpty()
        ) {
            compose.onNodeWithTag(ClearCutTestTags.TUTORIAL_SKIP).performClick()
        }
    }
}

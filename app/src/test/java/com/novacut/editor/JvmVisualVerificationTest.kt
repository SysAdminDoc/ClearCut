package com.novacut.editor

import android.net.Uri
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckResult
import com.google.android.apps.common.testing.accessibility.framework.integrations.espresso.AccessibilityValidator
import com.github.takahirom.roborazzi.captureRoboImage
import com.novacut.editor.engine.AutoSaveState
import com.novacut.editor.engine.AppearanceMode
import com.novacut.editor.engine.ProjectAutoSave
import com.novacut.editor.engine.ProjectDocumentApplicator
import com.novacut.editor.model.Clip
import com.novacut.editor.model.TextOverlay
import com.novacut.editor.model.Track
import com.novacut.editor.model.TrackType
import com.novacut.editor.ui.ClearCutTestTags
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = ClearCutApp::class,
    sdk = [35],
    qualifiers = "w360dp-h800dp-xxhdpi",
)
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
    fun primary_workflow_and_supporting_surfaces_render_in_both_dark_modes() {
        capture("project-dashboard-empty-dark.png")

        compose.onNodeWithTag(ClearCutTestTags.PROJECTS_CREATE_PROJECT).performClick()
        waitUntilAtLeastOneExists(ClearCutTestTags.TEMPLATE_SHEET)
        capture("project-template-sheet-dark.png")
        compose.onNodeWithTag(ClearCutTestTags.TEMPLATE_BLANK)
            .performScrollTo()
            .performClick()
        waitUntilAtLeastOneExists(ClearCutTestTags.EDITOR_SCREEN)
        dismissTutorialIfPresent()
        capture("editor-empty-dark.png")

        compose.onNodeWithTag(ClearCutTestTags.EDITOR_EMPTY_ADD_MEDIA).performClick()
        waitUntilAtLeastOneExists(ClearCutTestTags.MEDIA_PICKER_SHEET)
        capture("media-picker-dark.png")
        compose.onNodeWithTag(ClearCutTestTags.MEDIA_PICKER_CLOSE).performClick()

        compose.onNodeWithTag(ClearCutTestTags.EDITOR_BACK).performClick()
        waitUntilAtLeastOneExists(ClearCutTestTags.PROJECTS_SCREEN)
        waitUntilNoExists(ClearCutTestTags.EDITOR_SCREEN)
        val visualProjectId = seedVisualProject()
        waitUntilTextExists(VISUAL_PROJECT_NAME)
        capture("project-dashboard-dark.png")

        compose.onNodeWithTag(ClearCutTestTags.PROJECTS_SETTINGS).performClick()
        waitUntilAtLeastOneExists(ClearCutTestTags.SETTINGS_SCREEN)
        capture("settings-dark.png")

        compose.onNodeWithTag(ClearCutTestTags.SETTINGS_PRIVACY_OPEN)
            .performScrollTo()
            .performClick()
        waitUntilAtLeastOneExists(ClearCutTestTags.SETTINGS_PRIVACY_DASHBOARD)
        capture("privacy-dashboard-dark.png")
        compose.onNodeWithTag(ClearCutTestTags.SETTINGS_PRIVACY_CLOSE).performClick()
        waitUntilNoExists(ClearCutTestTags.SETTINGS_PRIVACY_DASHBOARD)

        compose.onNodeWithTag(ClearCutTestTags.SETTINGS_LICENSES_OPEN)
            .performScrollTo()
            .performClick()
        waitUntilAtLeastOneExists(ClearCutTestTags.SETTINGS_LICENSES_DIALOG)
        capture("open-source-licenses-dark.png")
        compose.onNodeWithTag(ClearCutTestTags.SETTINGS_LICENSES_CLOSE).performClick()
        waitUntilNoExists(ClearCutTestTags.SETTINGS_LICENSES_DIALOG)

        compose.onNodeWithTag(ClearCutTestTags.SETTINGS_BACK).performClick()
        waitUntilAtLeastOneExists(ClearCutTestTags.PROJECTS_SCREEN)

        compose.onNodeWithTag("${ClearCutTestTags.PROJECT_CARD_PREFIX}$visualProjectId").performClick()
        waitUntilAtLeastOneExists(ClearCutTestTags.EDITOR_SCREEN)
        dismissFixtureMediaWarningIfPresent()
        capture("editor-dark.png")

        updateAppearance(AppearanceMode.HIGH_CONTRAST_DARK)
        capture("editor-high-contrast-dark.png")

        compose.onNodeWithTag(ClearCutTestTags.EDITOR_EXPORT).performClick()
        waitUntilAtLeastOneExists(ClearCutTestTags.EXPORT_SHEET)
        capture("export-sheet-high-contrast-dark.png")
        compose.onNodeWithTag(ClearCutTestTags.EXPORT_CLOSE).performClick()

        updateAppearance(AppearanceMode.DARK)
        compose.onNodeWithTag(ClearCutTestTags.EDITOR_EXPORT).performClick()
        waitUntilAtLeastOneExists(ClearCutTestTags.EXPORT_SHEET)
        capture("export-sheet-dark.png")
        compose.onNodeWithTag(ClearCutTestTags.EXPORT_CLOSE).performClick()

        updateAppearance(AppearanceMode.HIGH_CONTRAST_DARK)
        compose.onNodeWithTag(ClearCutTestTags.EDITOR_BACK).performClick()
        waitUntilAtLeastOneExists(ClearCutTestTags.PROJECTS_SCREEN)
        capture("project-dashboard-high-contrast-dark.png")

        compose.onNodeWithTag(ClearCutTestTags.PROJECTS_SETTINGS).performClick()
        waitUntilAtLeastOneExists(ClearCutTestTags.SETTINGS_SCREEN)
        capture("settings-high-contrast-dark.png")

        compose.onNodeWithTag(ClearCutTestTags.SETTINGS_PRIVACY_OPEN)
            .performScrollTo()
            .performClick()
        waitUntilAtLeastOneExists(ClearCutTestTags.SETTINGS_PRIVACY_DASHBOARD)
        capture("privacy-dashboard-high-contrast-dark.png")
        compose.onNodeWithTag(ClearCutTestTags.SETTINGS_PRIVACY_CLOSE).performClick()
        waitUntilNoExists(ClearCutTestTags.SETTINGS_PRIVACY_DASHBOARD)

        compose.onNodeWithTag(ClearCutTestTags.SETTINGS_BACK).performClick()
        waitUntilAtLeastOneExists(ClearCutTestTags.PROJECTS_SCREEN)

        compose.onNodeWithTag(ClearCutTestTags.PROJECTS_CREATE_PROJECT).performClick()
        waitUntilAtLeastOneExists(ClearCutTestTags.TEMPLATE_SHEET)
        capture("project-template-sheet-high-contrast-dark.png")
        compose.onNodeWithTag(ClearCutTestTags.TEMPLATE_BLANK)
            .performScrollTo()
            .performClick()
        waitUntilAtLeastOneExists(ClearCutTestTags.EDITOR_SCREEN)
        dismissTutorialIfPresent()
        capture("editor-empty-high-contrast-dark.png")

        compose.onNodeWithTag(ClearCutTestTags.EDITOR_EMPTY_ADD_MEDIA).performClick()
        waitUntilAtLeastOneExists(ClearCutTestTags.MEDIA_PICKER_SHEET)
        capture("media-picker-high-contrast-dark.png")
        compose.onNodeWithTag(ClearCutTestTags.MEDIA_PICKER_CLOSE).performClick()

        compose.onNodeWithTag(ClearCutTestTags.EDITOR_BACK).performClick()
        waitUntilAtLeastOneExists(ClearCutTestTags.PROJECTS_SCREEN)
        compose.onNodeWithTag(ClearCutTestTags.PROJECTS_SETTINGS).performClick()
        waitUntilAtLeastOneExists(ClearCutTestTags.SETTINGS_SCREEN)
        compose.onNodeWithTag(ClearCutTestTags.SETTINGS_REPLAY_TUTORIAL)
            .performScrollTo()
            .performClick()
        waitUntilAtLeastOneExists(ClearCutTestTags.TUTORIAL_SCREEN)
        capture("tutorial-high-contrast-dark.png")
        updateAppearance(AppearanceMode.DARK)
        capture("tutorial-dark.png")
    }

    private fun capture(name: String) {
        if (System.getProperty("clearcut.visual.capture") != "true") return
        compose.mainClock.advanceTimeBy(VISUAL_SETTLE_TIME_MS)
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

    private fun seedVisualProject(): String = runBlocking {
        val project = requireNotNull(
            compose.activity.projectDao.getAllProjectsSnapshot().maxByOrNull { it.updatedAt }
        )
        val sourceFile = File(compose.activity.cacheDir, "visual-project.mp4")
        compose.activity.assets.open("qa-timeline-fixture.mp4").use { input ->
            sourceFile.outputStream().use(input::copyTo)
        }
        val sourceUri = Uri.fromFile(sourceFile)
        val updatedProject = project.copy(
            name = VISUAL_PROJECT_NAME,
            durationMs = VISUAL_PROJECT_DURATION_MS,
            thumbnailUri = sourceUri.toString(),
            updatedAt = System.currentTimeMillis(),
        )
        val primaryClip = Clip(
            id = "visual-primary-clip",
            sourceUri = sourceUri,
            sourceDurationMs = VISUAL_PROJECT_DURATION_MS,
            timelineStartMs = 0L,
            name = VISUAL_PROJECT_NAME,
        )
        val overlayClip = Clip(
            id = "visual-overlay-clip",
            sourceUri = sourceUri,
            sourceDurationMs = 18_000L,
            timelineStartMs = 12_000L,
            name = "Summit B-roll",
        )
        val audioClip = Clip(
            id = "visual-audio-clip",
            sourceUri = sourceUri,
            sourceDurationMs = VISUAL_PROJECT_DURATION_MS,
            timelineStartMs = 0L,
            name = "Adventure Theme",
        )
        val state = AutoSaveState(
            projectId = updatedProject.id,
            playheadMs = 18_000L,
            tracks = listOf(
                Track(id = "visual-title-track", type = TrackType.TEXT, index = 0),
                Track(
                    id = "visual-video-track",
                    type = TrackType.VIDEO,
                    index = 1,
                    clips = listOf(primaryClip),
                ),
                Track(
                    id = "visual-overlay-track",
                    type = TrackType.OVERLAY,
                    index = 2,
                    clips = listOf(overlayClip),
                ),
                Track(
                    id = "visual-audio-track",
                    type = TrackType.AUDIO,
                    index = 3,
                    clips = listOf(audioClip),
                ),
            ),
            textOverlays = listOf(
                TextOverlay(
                    id = "visual-title",
                    text = "INTO THE WILD",
                    startTimeMs = 0L,
                    endTimeMs = VISUAL_PROJECT_DURATION_MS,
                    bold = true,
                )
            ),
        )

        compose.activity.projectDao.updateProject(updatedProject)
        assertTrue(
            ProjectAutoSave(compose.activity.applicationContext).saveNow(
                ProjectDocumentApplicator.capture(updatedProject, state)
            )
        )
        updatedProject.id
    }

    private fun waitUntilAtLeastOneExists(tag: String, timeoutMillis: Long = 10_000L) {
        compose.waitUntil(timeoutMillis) {
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitUntilNoExists(tag: String, timeoutMillis: Long = 10_000L) {
        compose.waitUntil(timeoutMillis) {
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()
        }
    }

    private fun waitUntilTextExists(text: String, timeoutMillis: Long = 10_000L) {
        compose.waitUntil(timeoutMillis) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun dismissFixtureMediaWarningIfPresent() {
        val managerAppeared = runCatching {
            compose.waitUntil(2_500L) {
                compose.onAllNodesWithTag(ClearCutTestTags.MEDIA_MANAGER_PANEL)
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            true
        }.getOrDefault(false)

        if (managerAppeared) {
            compose.onNodeWithTag(ClearCutTestTags.MEDIA_MANAGER_CLOSE).performClick()
            waitUntilNoExists(ClearCutTestTags.MEDIA_MANAGER_PANEL)
        }

        compose.waitUntil(6_000L) {
            compose.onAllNodesWithText("Preview couldn't decode", substring = true)
                .fetchSemanticsNodes()
                .isEmpty()
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

    private companion object {
        const val VISUAL_PROJECT_NAME = "Mountain Story"
        const val VISUAL_PROJECT_DURATION_MS = 42_000L
        const val VISUAL_SETTLE_TIME_MS = 1_000L
    }
}

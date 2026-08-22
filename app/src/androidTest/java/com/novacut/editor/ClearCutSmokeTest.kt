package com.novacut.editor

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.novacut.editor.ui.ClearCutTestTags
import com.novacut.editor.engine.AppearanceMode
import com.novacut.editor.engine.DesktopOverride
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import androidx.test.filters.SdkSuppress
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class ClearCutSmokeTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>().apply {
        enableAccessibilityChecks()
    }

    @Before
    fun waitForInitialComposeHierarchy() {
        compose.waitForComposeHierarchy()
    }

    @Test
    fun projectEditorExportAndSettingsSurfacesOpen() {
        compose.onNodeWithTag(ClearCutTestTags.PROJECTS_SCREEN).assertIsDisplayed()
        compose.assertAccessibilityChecksPass()

        compose.onNodeWithTag(ClearCutTestTags.PROJECTS_CREATE_PROJECT).performClick()
        compose.onNodeWithTag(ClearCutTestTags.TEMPLATE_SHEET).assertIsDisplayed()
        compose.onNodeWithTag(ClearCutTestTags.TEMPLATE_BLANK)
            .performScrollTo()
            .performClick()

        compose.waitUntilAtLeastOneExists(ClearCutTestTags.EDITOR_SCREEN)
        compose.waitForIdle()
        assertTrue(
            compose.onAllNodesWithTag(ClearCutTestTags.TUTORIAL_SCREEN)
                .fetchSemanticsNodes()
                .isEmpty()
        )
        dismissTutorialIfPresent()
        compose.assertAccessibilityChecksPass()

        compose.onNodeWithTag(ClearCutTestTags.EDITOR_EMPTY_ADD_MEDIA).assertIsDisplayed().performClick()
        compose.onNodeWithTag(ClearCutTestTags.MEDIA_PICKER_SHEET).assertIsDisplayed()
        compose.assertAccessibilityChecksPass()
        compose.onNodeWithTag(ClearCutTestTags.MEDIA_PICKER_CLOSE).performClick()

        compose.onNodeWithTag(ClearCutTestTags.EDITOR_EXPORT).assertIsDisplayed().performClick()
        compose.onNodeWithTag(ClearCutTestTags.EXPORT_SHEET).assertIsDisplayed()
        compose.assertAccessibilityChecksPass()
        compose.onNodeWithTag(ClearCutTestTags.EXPORT_CLOSE).performClick()

        compose.onNodeWithTag(ClearCutTestTags.EDITOR_BACK).performClick()
        compose.waitUntilAtLeastOneExists(ClearCutTestTags.PROJECTS_SCREEN)
        compose.waitUntilNoNodesExist(ClearCutTestTags.EDITOR_SCREEN)

        compose.onNodeWithTag(ClearCutTestTags.PROJECTS_SETTINGS).performClick()
        compose.onNodeWithTag(ClearCutTestTags.SETTINGS_SCREEN).assertIsDisplayed()
        compose.assertAccessibilityChecksPass()
        compose.onNodeWithTag(ClearCutTestTags.SETTINGS_PRIVACY_OPEN)
            .performScrollTo()
            .performClick()
        compose.onNodeWithTag(ClearCutTestTags.SETTINGS_PRIVACY_DASHBOARD).assertIsDisplayed()
        compose.assertAccessibilityChecksPass()
        compose.onNodeWithTag(ClearCutTestTags.SETTINGS_PRIVACY_CLOSE).performClick()
        compose.onNodeWithTag(ClearCutTestTags.SETTINGS_LICENSES_OPEN)
            .performScrollTo()
            .performClick()
        compose.onNodeWithTag(ClearCutTestTags.SETTINGS_LICENSES_DIALOG).assertIsDisplayed()
        compose.assertAccessibilityChecksPass()
        compose.onNodeWithTag(ClearCutTestTags.SETTINGS_LICENSES_CLOSE).performClick()

        compose.onNodeWithTag(ClearCutTestTags.SETTINGS_REPLAY_TUTORIAL)
            .performScrollTo()
            .performClick()
        compose.waitUntilAtLeastOneExists(ClearCutTestTags.EDITOR_SCREEN)
        compose.onNodeWithTag(ClearCutTestTags.TUTORIAL_SCREEN).assertIsDisplayed()
        compose.onNodeWithTag(ClearCutTestTags.TUTORIAL_SKIP).performClick()
        compose.onNodeWithTag(ClearCutTestTags.EDITOR_SCREEN).assertIsDisplayed()
        compose.onNodeWithTag(ClearCutTestTags.EDITOR_BACK).performClick()
        compose.waitUntilAtLeastOneExists(ClearCutTestTags.SETTINGS_SCREEN)
        compose.waitUntilNoNodesExist(ClearCutTestTags.EDITOR_SCREEN)

        compose.onNodeWithTag(ClearCutTestTags.SETTINGS_BACK)
            .performScrollTo()
            .performClick()

        compose.waitUntilAtLeastOneExists(ClearCutTestTags.PROJECTS_SCREEN)
        compose.waitUntilNoNodesExist(ClearCutTestTags.SETTINGS_SCREEN)
    }

    @Test
    @SdkSuppress(minSdkVersion = 33)
    fun pseudoLocalesRenderExpandedAndRtlExportSurfaces() {
        try {
            setApplicationLocale("en-XA")
            compose.waitUntilAtLeastOneExists(ClearCutTestTags.PROJECTS_SCREEN)
            compose.onNodeWithTag(ClearCutTestTags.PROJECTS_SCREEN).assertIsDisplayed()
            compose.onNodeWithTag(ClearCutTestTags.PROJECTS_CREATE_PROJECT).performClick()
            compose.onNodeWithTag(ClearCutTestTags.TEMPLATE_BLANK)
                .performScrollTo()
                .performClick()
            compose.waitUntilAtLeastOneExists(ClearCutTestTags.EDITOR_SCREEN)
            dismissTutorialIfPresent()
            compose.onNodeWithTag(ClearCutTestTags.EDITOR_EXPORT).performClick()
            compose.onNodeWithTag(ClearCutTestTags.EXPORT_SHEET).assertIsDisplayed()
            compose.assertAccessibilityChecksPass()
            assertTrue(compose.onRoot().captureToImage().width > 0)

            setApplicationLocale("ar-XB")
            compose.onNodeWithTag(ClearCutTestTags.EXPORT_SHEET).assertIsDisplayed()
            compose.assertAccessibilityChecksPass()
            assertEquals(
                android.view.View.LAYOUT_DIRECTION_RTL,
                compose.activity.resources.configuration.layoutDirection,
            )
            assertTrue(compose.onRoot().captureToImage().width > 0)
        } finally {
            compose.activity
                .getSystemService(android.app.LocaleManager::class.java)
                .applicationLocales = android.os.LocaleList.getEmptyLocaleList()
        }
    }

    @Test
    fun highContrastPhoneAndDesktopEditorSurfacesRender() {
        try {
            updateAppearance(AppearanceMode.HIGH_CONTRAST_DARK, DesktopOverride.FORCE_OFF)
            compose.waitUntilAtLeastOneExists(ClearCutTestTags.PROJECTS_SCREEN)
            compose.onNodeWithTag(ClearCutTestTags.PROJECTS_CREATE_PROJECT).performClick()
            compose.onNodeWithTag(ClearCutTestTags.TEMPLATE_BLANK)
                .performScrollTo()
                .performClick()
            compose.waitUntilAtLeastOneExists(ClearCutTestTags.EDITOR_SCREEN)
            dismissTutorialIfPresent()
            compose.assertAccessibilityChecksPass()
            assertTrue(compose.onRoot().captureToImage().width > 0)

            compose.onNodeWithTag(ClearCutTestTags.EDITOR_EMPTY_ADD_MEDIA).performClick()
            compose.onNodeWithTag(ClearCutTestTags.MEDIA_PICKER_SHEET).assertIsDisplayed()
            compose.assertAccessibilityChecksPass()
            assertTrue(compose.onRoot().captureToImage().width > 0)
            compose.onNodeWithTag(ClearCutTestTags.MEDIA_PICKER_CLOSE).performClick()

            compose.onNodeWithTag(ClearCutTestTags.EDITOR_EXPORT).performClick()
            compose.onNodeWithTag(ClearCutTestTags.EXPORT_SHEET).assertIsDisplayed()
            compose.assertAccessibilityChecksPass()
            assertTrue(compose.onRoot().captureToImage().width > 0)
            compose.onNodeWithTag(ClearCutTestTags.EXPORT_CLOSE).performClick()

            updateAppearance(AppearanceMode.HIGH_CONTRAST_DARK, DesktopOverride.FORCE_ON)
            compose.waitUntilAtLeastOneExists(ClearCutTestTags.EDITOR_DESKTOP_SIDEBAR)
            compose.onNodeWithTag(ClearCutTestTags.EDITOR_DESKTOP_SIDEBAR).assertIsDisplayed()
            compose.assertAccessibilityChecksPass()
            assertTrue(compose.onRoot().captureToImage().width > 0)
            compose.onNodeWithTag(ClearCutTestTags.EDITOR_EXPORT).performClick()
            compose.onNodeWithTag(ClearCutTestTags.EXPORT_SHEET).assertIsDisplayed()
            compose.assertAccessibilityChecksPass()
            assertTrue(compose.onRoot().captureToImage().width > 0)
        } finally {
            updateAppearance(AppearanceMode.DARK, DesktopOverride.AUTO)
        }
    }

    private fun updateAppearance(appearanceMode: AppearanceMode, desktopOverride: DesktopOverride) {
        runBlocking {
            compose.activity.settingsRepository.updateAppearanceMode(appearanceMode)
            compose.activity.settingsRepository.updateDesktopOverride(desktopOverride)
            compose.activity.settingsRepository.settings.first { settings ->
                settings.appearanceMode == appearanceMode &&
                    settings.desktopModeOverride == desktopOverride
            }
        }
        compose.waitForIdle()
        compose.waitForComposeHierarchy()
    }

    private fun setApplicationLocale(languageTag: String) {
        compose.activity
            .getSystemService(android.app.LocaleManager::class.java)
            .applicationLocales = android.os.LocaleList.forLanguageTags(languageTag)
        compose.waitUntil(timeoutMillis = 20_000L) {
            compose.activity.resources.configuration.locales[0].toLanguageTag() == languageTag
        }
        compose.waitForComposeHierarchy()
    }

    private fun dismissTutorialIfPresent() {
        runCatching {
            compose.waitUntil(timeoutMillis = 1_200L) {
                compose.onAllNodesWithTag(ClearCutTestTags.TUTORIAL_SKIP)
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
        }
        val tutorialNodes = runCatching {
            compose.onAllNodesWithTag(ClearCutTestTags.TUTORIAL_SKIP).fetchSemanticsNodes()
        }.getOrDefault(emptyList())
        if (tutorialNodes.isNotEmpty()) {
            compose.onNodeWithTag(ClearCutTestTags.TUTORIAL_SKIP).performClick()
        }
    }

}

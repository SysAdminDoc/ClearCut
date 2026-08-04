package com.novacut.editor.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TutorialReplayContractTest {

    @Test
    fun replayIsAnExplicitNavigationActionAndOrdinaryEditorsStayQuiet() {
        val mainActivity = locate("app/src/main/java/com/novacut/editor/MainActivity.kt").readText()
        val settings = locate(
            "app/src/main/java/com/novacut/editor/ui/settings/SettingsScreen.kt"
        ).readText()
        val editorViewModel = locate(
            "app/src/main/java/com/novacut/editor/ui/editor/EditorViewModel.kt"
        ).readText()
        val settingsRepository = locate(
            "app/src/main/java/com/novacut/editor/engine/SettingsRepository.kt"
        ).readText()

        assertTrue(mainActivity.contains("onReplayTutorial ="))
        assertTrue(mainActivity.contains("editor/tutorial?replayTutorial=true"))
        assertTrue(mainActivity.contains("replayTutorial=true"))
        assertTrue(settings.contains("onClick = onReplayTutorial"))
        assertTrue(settings.contains("ClearCutTestTags.SETTINGS_REPLAY_TUTORIAL"))
        assertFalse(settings.contains("resetTutorial"))
        assertFalse(settings.contains("ResetTutorialConfirmDialog"))

        assertTrue(editorViewModel.contains("savedStateHandle[\"replayTutorial\"]"))
        assertTrue(editorViewModel.contains("if (replayTutorial)"))
        assertTrue(editorViewModel.contains("val shouldRun = projectId != null"))
        assertFalse(editorViewModel.contains("isTutorialShown"))
        assertFalse(editorViewModel.contains("setTutorialShown"))
        assertFalse(settingsRepository.contains("TUTORIAL_SHOWN"))
    }

    @Test
    fun localizedReplayLabelsExistWithoutTheObsoleteResetLabels() {
        val english = locate("app/src/main/res/values/strings.xml").readText()
        val spanish = locate("app/src/main/res/values-es/strings.xml").readText()

        for (resources in listOf(english, spanish)) {
            assertTrue(resources.contains("settings_replay_tutorial"))
            assertTrue(resources.contains("settings_replay_tutorial_row_description"))
            assertTrue(resources.contains("settings_replay_tutorial_action"))
            assertFalse(resources.contains("settings_reset_tutorial"))
        }
    }

    private fun locate(relativePath: String): File {
        return listOf(File(relativePath), File("../$relativePath"))
            .firstOrNull { it.isFile }
            ?: error("Could not locate $relativePath from ${File(".").absolutePath}")
    }
}

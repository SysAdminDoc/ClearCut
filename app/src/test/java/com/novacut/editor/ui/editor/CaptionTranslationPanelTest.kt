package com.novacut.editor.ui.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.novacut.editor.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CaptionTranslationPanelTest {

    @get:Rule
    val compose = createComposeRule()

    private val targets = listOf("en", "es", "fr")

    @Test
    fun withNoModelThatCanBeInstalledThePanelOffersNoTargets() {
        var translationPossible by mutableStateOf(false)
        val picked = mutableListOf<String>()
        compose.setContent {
            CaptionTranslationPanel(
                rows = emptyList(),
                sourceLang = "en",
                targetLang = null,
                currentQuality = null,
                availableTargets = targets,
                onTargetSelected = { picked += it },
                onUserEdit = { _, _ -> },
                onRegenerate = {},
                unavailable = true,
                translationPossible = translationPossible,
            )
        }

        compose.onNodeWithText(string(R.string.caption_translation_not_installable)).assertExists()
        assertEquals(0, compose.onAllNodesWithText("ES").fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodesWithText("FR").fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().size)

        // An engine that reports a ready model brings the picker back.
        translationPossible = true
        compose.waitForIdle()

        compose.onNodeWithText("ES").assertHasClickAction().performClick()
        assertEquals(listOf("es"), picked)
        assertEquals(0, compose.onAllNodesWithText(string(R.string.caption_translation_not_installable)).fetchSemanticsNodes().size)
    }

    @Test
    fun offlineWithoutAnyInstallableModelStillSaysTranslationCantRun() {
        compose.setContent {
            CaptionTranslationPanel(
                rows = emptyList(),
                sourceLang = "en",
                targetLang = null,
                currentQuality = null,
                availableTargets = targets,
                onTargetSelected = {},
                onUserEdit = { _, _ -> },
                onRegenerate = {},
                offline = true,
                translationPossible = false,
            )
        }

        compose.onNodeWithText(string(R.string.caption_translation_not_installable)).assertExists()
        assertEquals(0, compose.onAllNodesWithText(string(R.string.caption_translation_offline)).fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodesWithText("ES").fetchSemanticsNodes().size)
    }

    private fun string(id: Int): String = ApplicationProvider.getApplicationContext<android.content.Context>().getString(id)
}

package com.novacut.editor.ui.projects

import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performScrollToNode
import com.novacut.editor.ClearCutApp
import com.novacut.editor.MainActivity
import com.novacut.editor.model.Project
import com.novacut.editor.ui.ClearCutTestTags
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Issue #54: with every project in the trash, the dashboard placed the scrolling
 * "ready to start" empty state inside its LazyColumn. A lazy item is measured with
 * an unbounded height, so Compose threw "Vertically scrollable component was
 * measured with an infinity maximum height constraints" on the first frame. The
 * trash survives restarts, so the app crashed on every launch until it was purged.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = ClearCutApp::class,
    sdk = [35],
    qualifiers = "w360dp-h800dp-xxhdpi",
)
class ProjectDashboardTrashOnlyTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun aDashboardWhoseOnlyProjectIsInTheTrashStillRenders() {
        waitUntil { compose.onAllNodesWithTag(ClearCutTestTags.PROJECTS_SCREEN).fetchSemanticsNodes().isNotEmpty() }

        runBlocking {
            compose.activity.projectDao.insertProject(
                Project(name = TRASHED_PROJECT_NAME, deletedAtEpochMs = System.currentTimeMillis())
            )
        }

        // With no active projects the trash auto-expands below the inline empty
        // state. Scrolling the list to the trashed card proves the list measured
        // and that Restore is still reachable.
        waitUntil { compose.onAllNodes(hasScrollToIndexAction()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(TRASHED_PROJECT_NAME))
        waitUntil { compose.onAllNodesWithText(TRASHED_PROJECT_NAME).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun waitUntil(condition: () -> Boolean) {
        compose.waitUntil(10_000L, condition)
    }

    private companion object {
        const val TRASHED_PROJECT_NAME = "Trashed Story"
    }
}

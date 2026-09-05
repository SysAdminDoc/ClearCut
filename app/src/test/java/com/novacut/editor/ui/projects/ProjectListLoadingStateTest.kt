package com.novacut.editor.ui.projects

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The project list seeded straight to an empty list, so "No projects yet" rendered on
 * every cold start until Room's first emission arrived — the app telling a returning
 * user their work was gone. Empty and not-yet-known are different states.
 */
class ProjectListLoadingStateTest {

    @Test
    fun nothingIsEmptyUntilRoomHasAnswered() {
        assertFalse(
            "the first-run empty state must not render before the first emission",
            ProjectListTrashVisibilityPolicy.showsFullScreenEmptyState(
                activeCount = 0,
                trashedCount = 0,
                isLoading = true,
            )
        )
        assertTrue(
            ProjectListTrashVisibilityPolicy.showsLoadingState(
                activeCount = 0,
                trashedCount = 0,
                isLoading = true,
            )
        )
    }

    @Test
    fun theEmptyStateStillRendersOnceTheListIsGenuinelyEmpty() {
        assertTrue(
            ProjectListTrashVisibilityPolicy.showsFullScreenEmptyState(
                activeCount = 0,
                trashedCount = 0,
                isLoading = false,
            )
        )
        assertFalse(
            ProjectListTrashVisibilityPolicy.showsLoadingState(
                activeCount = 0,
                trashedCount = 0,
                isLoading = false,
            )
        )
    }

    @Test
    fun aPopulatedListNeverShowsEitherPlaceholder() {
        listOf(true, false).forEach { loading ->
            assertFalse(
                ProjectListTrashVisibilityPolicy.showsFullScreenEmptyState(3, 0, loading)
            )
            assertFalse(ProjectListTrashVisibilityPolicy.showsLoadingState(3, 0, loading))
        }
    }

    @Test
    fun aQueryFailureNeverRendersAsEmptyOrAsAnEndlessSpinner() {
        // The failure branch is checked first in ProjectListScreen, so what matters is
        // that it claims the screen for the exact state the old code got stuck in:
        // nothing loaded, and the flow terminated.
        assertTrue(
            "a failed project query must offer its own retryable state",
            ProjectListTrashVisibilityPolicy.showsLoadFailureState(
                activeCount = 0,
                trashedCount = 0,
                hasLoadFailure = true,
            )
        )
        // The ViewModel clears isLoading in its catch, so the spinner predicate must be
        // false by then. If it were still true the screen would spin forever.
        assertFalse(
            ProjectListTrashVisibilityPolicy.showsLoadingState(
                activeCount = 0,
                trashedCount = 0,
                isLoading = false,
            )
        )
    }

    @Test
    fun aFailureWithCachedContentKeepsTheListRatherThanBlankingIt() {
        // StateFlow replays the last good list. Losing it to a full-screen error would
        // hide projects the user can still open.
        assertFalse(
            ProjectListTrashVisibilityPolicy.showsLoadFailureState(3, 0, hasLoadFailure = true)
        )
        assertFalse(
            ProjectListTrashVisibilityPolicy.showsLoadFailureState(0, 2, hasLoadFailure = true)
        )
        assertTrue(ProjectListTrashVisibilityPolicy.showsList(activeCount = 3, trashedCount = 0))
    }

    @Test
    fun successNeverShowsTheFailureState() {
        listOf(0, 3).forEach { active ->
            assertFalse(
                ProjectListTrashVisibilityPolicy.showsLoadFailureState(active, 0, hasLoadFailure = false)
            )
        }
    }

    @Test
    fun trashedProjectsKeepTheListRenderingWhileLoading() {
        // Trash is the only undo path for a deleted project; it must not be hidden
        // behind either placeholder.
        assertFalse(ProjectListTrashVisibilityPolicy.showsLoadingState(0, 2, isLoading = true))
        assertTrue(ProjectListTrashVisibilityPolicy.showsList(activeCount = 0, trashedCount = 2))
    }
}

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
    fun trashedProjectsKeepTheListRenderingWhileLoading() {
        // Trash is the only undo path for a deleted project; it must not be hidden
        // behind either placeholder.
        assertFalse(ProjectListTrashVisibilityPolicy.showsLoadingState(0, 2, isLoading = true))
        assertTrue(ProjectListTrashVisibilityPolicy.showsList(activeCount = 0, trashedCount = 2))
    }
}

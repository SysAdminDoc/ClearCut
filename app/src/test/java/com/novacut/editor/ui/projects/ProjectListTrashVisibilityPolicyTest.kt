package com.novacut.editor.ui.projects

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deleting the last active project must not hide the trash. Restore is the only
 * way back from a deletion, and the dashboard used to swap to a bare empty state
 * that never rendered the trash section.
 */
class ProjectListTrashVisibilityPolicyTest {

    @Test
    fun lastDeletionKeepsTheTrashOnScreen() {
        assertFalse(
            "an empty active list with a non-empty trash must still render the list",
            ProjectListTrashVisibilityPolicy.showsFullScreenEmptyState(activeCount = 0, trashedCount = 1)
        )
        assertTrue(ProjectListTrashVisibilityPolicy.showsList(activeCount = 0, trashedCount = 1))
    }

    @Test
    fun trashOpensWithoutASecondTapWhenNothingElseIsOnScreen() {
        assertTrue(ProjectListTrashVisibilityPolicy.autoExpandsTrash(activeCount = 0, trashedCount = 3))
        assertTrue(ProjectListTrashVisibilityPolicy.showsInlineEmptyState(activeCount = 0, trashedCount = 3))
    }

    @Test
    fun trashStaysCollapsedWhileActiveProjectsExist() {
        assertFalse(ProjectListTrashVisibilityPolicy.autoExpandsTrash(activeCount = 2, trashedCount = 3))
        assertFalse(ProjectListTrashVisibilityPolicy.showsInlineEmptyState(activeCount = 2, trashedCount = 3))
    }

    @Test
    fun aTrulyEmptyDashboardStillGetsTheFullScreenEmptyState() {
        assertTrue(ProjectListTrashVisibilityPolicy.showsFullScreenEmptyState(activeCount = 0, trashedCount = 0))
        assertFalse(ProjectListTrashVisibilityPolicy.showsList(activeCount = 0, trashedCount = 0))
        assertFalse(ProjectListTrashVisibilityPolicy.autoExpandsTrash(activeCount = 0, trashedCount = 0))
    }

    /**
     * A search or filter that matches nothing behaves like an empty active list:
     * the empty-state copy renders inline and the trash stays reachable beneath it.
     */
    @Test
    fun aSearchThatMatchesNothingStillShowsTheTrash() {
        assertTrue(ProjectListTrashVisibilityPolicy.showsList(activeCount = 0, trashedCount = 2))
        assertTrue(ProjectListTrashVisibilityPolicy.showsInlineEmptyState(activeCount = 0, trashedCount = 2))
    }

    @Test
    fun negativeCountsAreTreatedAsEmptyRatherThanCrashing() {
        assertTrue(ProjectListTrashVisibilityPolicy.showsFullScreenEmptyState(activeCount = -1, trashedCount = -1))
        assertFalse(ProjectListTrashVisibilityPolicy.autoExpandsTrash(activeCount = -1, trashedCount = 0))
    }
}

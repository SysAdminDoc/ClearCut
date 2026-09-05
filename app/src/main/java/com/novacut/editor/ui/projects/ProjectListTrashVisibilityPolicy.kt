package com.novacut.editor.ui.projects

/**
 * When the project list may collapse to a bare empty state.
 *
 * Trash is the only undo path for a deleted project, so it has to stay
 * reachable in exactly the state where it matters most: no active projects
 * left. The dashboard previously swapped to a full-screen empty state as soon
 * as the active list was empty, which never rendered the trash section and hid
 * Restore behind a restart.
 */
object ProjectListTrashVisibilityPolicy {

    /**
     * True only when there is genuinely nothing to show — no active projects and
     * an empty trash. Otherwise the list renders, carrying the trash section.
     */
    fun showsFullScreenEmptyState(activeCount: Int, trashedCount: Int): Boolean =
        showsFullScreenEmptyState(activeCount, trashedCount, isLoading = false)

    /**
     * The list seeded straight to an empty list before Room's first emission, so
     * "No projects yet" flashed on every cold start -- the app telling a returning
     * user their work was gone. While [isLoading] is true nothing is empty yet: it is
     * simply unknown.
     */
    fun showsFullScreenEmptyState(activeCount: Int, trashedCount: Int, isLoading: Boolean): Boolean =
        !isLoading && activeCount <= 0 && trashedCount <= 0

    /** True while the first Room emission is still outstanding. */
    fun showsLoadingState(activeCount: Int, trashedCount: Int, isLoading: Boolean): Boolean =
        isLoading && activeCount <= 0 && trashedCount <= 0

    /**
     * True when the project query failed and there is nothing to fall back on.
     *
     * A Room exception used to terminate the project flow while the loading flag was
     * still set, so the dashboard spun forever. Failure is a third state: it must be
     * checked before the loading and empty states, or a failure renders as "No projects
     * yet" and tells the user their work is gone.
     */
    fun showsLoadFailureState(activeCount: Int, trashedCount: Int, hasLoadFailure: Boolean): Boolean =
        hasLoadFailure && activeCount <= 0 && trashedCount <= 0

    /** True when the list should render at all (projects, trash, or both). */
    fun showsList(activeCount: Int, trashedCount: Int): Boolean =
        !showsFullScreenEmptyState(activeCount, trashedCount)

    /**
     * True when the trash section should open without a tap. With no active
     * projects the trash is the screen's only content, so Restore and Empty
     * Trash must both be on screen rather than one level down.
     */
    fun autoExpandsTrash(activeCount: Int, trashedCount: Int): Boolean =
        activeCount <= 0 && trashedCount > 0

    /**
     * True when the empty-state block renders inline above the trash section —
     * i.e. nothing matched the current search/filter but the trash is not empty.
     */
    fun showsInlineEmptyState(activeCount: Int, trashedCount: Int): Boolean =
        activeCount <= 0 && trashedCount > 0
}

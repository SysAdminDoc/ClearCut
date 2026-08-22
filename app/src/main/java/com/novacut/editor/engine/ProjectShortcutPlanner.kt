package com.novacut.editor.engine

/**
 * Pure planner for the launcher long-press menu (App Shortcuts) — the
 * dynamic half that complements the static `res/xml/shortcuts.xml` entries.
 *
 * Static shortcuts in `res/xml/shortcuts.xml` cover the two project-agnostic
 * actions ("New Project", "Recent Projects") via `<meta-data android:name=
 * "android.app.shortcuts">`. They're available the moment the app installs,
 * and MainActivity routes them into the same new-project and recent-project
 * flows as the visible dashboard controls.
 *
 * Dynamic shortcuts in this planner adapt to runtime state — they appear
 * only when there's a useful target:
 *  - "Resume recovered draft" appears only when `ProjectAutoSave.hasRecoveryData(...)`
 *    returns true for the last-active project.
 *  - "Open <project name>" appears only when a last-active project exists
 *    and is reachable (Room lookup succeeds).
 *
 * The actual `ShortcutManagerCompat.setDynamicShortcuts(...)` call is an
 * Android-only side effect that belongs in `ProjectListViewModel`. This
 * planner stays pure so the decision table is JVM-testable.
 *
 * Per Google's `ShortcutManagerCompat` docs, dynamic + static + pinned
 * shortcuts together must fit in `getMaxShortcutCountPerActivity()` —
 * typically 4 on most devices, sometimes up to 5. We have 2 static entries
 * (`new_project`, `recent_projects`) and want at most 2 dynamic, totalling
 * 4. The planner enforces that cap so we never push a list the launcher
 * silently drops.
 */
object ProjectShortcutPlanner {

    /** Maximum number of dynamic shortcuts to push so we stay under the per-activity cap. */
    const val MAX_DYNAMIC_SHORTCUTS = 2

    /** Canonical shortcut IDs (must match `res/xml/shortcuts.xml` for the static ones). */
    object ShortcutId {
        const val NEW_PROJECT = "new_project"
        const val RECENT_PROJECTS = "recent_projects"
        const val RESUME_RECOVERED = "resume_recovered"
        const val OPEN_LAST_PROJECT = "open_last_project"
    }

    /**
     * Snapshot of what the planner needs to decide. Pure data — the caller
     * builds this from `ProjectAutoSave.hasRecoveryData()` + DataStore +
     * Room before invoking [planDynamic].
     */
    data class State(
        /** The project the user opened most recently, or null if there is none. */
        val lastProjectId: String?,
        /** Display name of [lastProjectId] when known. Used as the shortcut label. */
        val lastProjectName: String?,
        /** Whether `ProjectAutoSave.hasRecoveryData(lastProjectId)` is true. */
        val hasRecoveryForLast: Boolean,
        /** Whether the user has opted out of dynamic shortcuts in Settings. */
        val userOptedOut: Boolean = false,
    )

    /**
     * One dynamic shortcut entry the orchestrator should push.
     *
     * @property shortcutId canonical ID — match `ShortcutId.*`.
     * @property shortLabel chip text (~10 chars).
     * @property longLabel one-line description (~25 chars).
     * @property action `Intent.action` to fire on tap. The MainActivity
     *   handler routes on this string.
     * @property extras `Intent.putExtra` values to bundle. Today only the
     *   project-id payload, keyed by [EXTRA_PROJECT_ID].
     * @property rank 0 = highest priority; the launcher renders highest
     *   first. Resume-recovered ranks above open-last because losing
     *   in-progress work is the bigger regret.
     */
    data class DynamicShortcut(
        val shortcutId: String,
        val shortLabel: String,
        val longLabel: String,
        val action: String,
        val extras: Map<String, String>,
        val rank: Int,
    )

    /** Bundled extra key for the project ID payload. */
    const val EXTRA_PROJECT_ID = "com.novacut.editor.extra.PROJECT_ID"

    /**
     * Returns the ordered list of dynamic shortcuts the launcher should
     * show right now. May be empty — that is the correct outcome on a
     * fresh install or when the user opts out.
     */
    fun planDynamic(state: State): List<DynamicShortcut> {
        if (state.userOptedOut) return emptyList()
        if (state.lastProjectId.isNullOrBlank()) return emptyList()

        val out = mutableListOf<DynamicShortcut>()

        if (state.hasRecoveryForLast) {
            out += DynamicShortcut(
                shortcutId = ShortcutId.RESUME_RECOVERED,
                shortLabel = "Resume",
                longLabel = "Resume recovered draft",
                action = ACTION_RESUME_RECOVERED,
                extras = mapOf(EXTRA_PROJECT_ID to state.lastProjectId),
                rank = 0,
            )
        }

        val openLabel = state.lastProjectName?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { it.take(OPEN_LABEL_MAX_CHARS) }
            ?: "Last Project"
        out += DynamicShortcut(
            shortcutId = ShortcutId.OPEN_LAST_PROJECT,
            shortLabel = openLabel,
            longLabel = "Open $openLabel",
            action = ACTION_OPEN_LAST_PROJECT,
            extras = mapOf(EXTRA_PROJECT_ID to state.lastProjectId),
            rank = 1,
        )

        // Cap at MAX_DYNAMIC_SHORTCUTS to stay under the launcher's per-
        // activity ceiling. The list is already ranked, so trimming from
        // the tail preserves the most important entries (Resume > Open).
        return out.take(MAX_DYNAMIC_SHORTCUTS)
    }

    const val ACTION_RESUME_RECOVERED = "com.novacut.editor.action.RESUME_RECOVERED"
    const val ACTION_OPEN_LAST_PROJECT = "com.novacut.editor.action.OPEN_LAST_PROJECT"

    private const val OPEN_LABEL_MAX_CHARS = 25
}

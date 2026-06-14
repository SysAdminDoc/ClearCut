package com.novacut.editor.engine

/**
 * Pure decision layer for the passive update check (sideload / GitHub-release
 * installs only).
 *
 * The privacy contract is that **no network request ever fires unless the user
 * has explicitly opted in** and the build actually supports the check. Keeping
 * that gate here — separate from the OkHttp call in [UpdateChecker] and from
 * any Compose UI — means the opt-out behavior is unit-testable without standing
 * up a network stack.
 *
 * `buildSupportsUpdateCheck` mirrors `BuildConfig.UPDATE_CHECK_AVAILABLE`, which
 * a privacy-store fork (for example F-Droid) can flip to `false` to compile the
 * feature out entirely.
 */
object UpdateCheckPolicy {

    sealed interface Decision {
        /** Compiled out for this build flavor — never touch the network. */
        data object Unavailable : Decision

        /** Available, but the user has not opted in — never touch the network. */
        data object Disabled : Decision

        /** Opted in and available — a network version check may run. */
        data object Allowed : Decision
    }

    fun decide(buildSupportsUpdateCheck: Boolean, userEnabled: Boolean): Decision = when {
        !buildSupportsUpdateCheck -> Decision.Unavailable
        !userEnabled -> Decision.Disabled
        else -> Decision.Allowed
    }

    /** Convenience predicate: may the caller make a network request right now? */
    fun mayCheckNetwork(buildSupportsUpdateCheck: Boolean, userEnabled: Boolean): Boolean =
        decide(buildSupportsUpdateCheck, userEnabled) is Decision.Allowed

    /**
     * Given the latest release tag fetched from GitHub and the installed
     * version name, decide whether to surface an update-available notice.
     */
    fun updateAvailable(latestTag: String?, currentVersion: String?): Boolean =
        AppVersion.isNewer(latestTag, currentVersion)
}

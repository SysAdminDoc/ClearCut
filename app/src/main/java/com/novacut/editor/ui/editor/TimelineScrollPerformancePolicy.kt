package com.novacut.editor.ui.editor

/**
 * Stable bounds for timeline work that is allowed to happen around the
 * visible viewport. Keeping these values separate makes the scroll contract
 * testable without constructing a Compose tree.
 */
internal object TimelineScrollPerformancePolicy {
    const val CACHE_WINDOW_AHEAD_DP = 160
    const val CACHE_WINDOW_BEHIND_DP = 80

    fun thumbnailPreloadPaddingMs(visibleDurationMs: Long): Long {
        return (visibleDurationMs / 2).coerceAtLeast(2_000L)
    }

    fun shouldRenderExpensiveContent(isVisible: Boolean): Boolean = isVisible
}

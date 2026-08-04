package com.novacut.editor.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TimelineScrollPerformancePolicyTest {
    @Test
    fun thumbnailPreloadPaddingKeepsAUsefulMinimum() {
        assertEquals(2_000L, TimelineScrollPerformancePolicy.thumbnailPreloadPaddingMs(0L))
        assertEquals(2_000L, TimelineScrollPerformancePolicy.thumbnailPreloadPaddingMs(1_000L))
        assertEquals(5_000L, TimelineScrollPerformancePolicy.thumbnailPreloadPaddingMs(10_000L))
    }

    @Test
    fun expensiveContentOnlyRendersWhenItsClipIsVisible() {
        assertTrue(TimelineScrollPerformancePolicy.shouldRenderExpensiveContent(true))
        assertFalse(TimelineScrollPerformancePolicy.shouldRenderExpensiveContent(false))
    }

    @Test
    fun timelineUsesLazyCacheWindowAndVisibilityGating() {
        val source = locate("app/src/main/java/com/novacut/editor/ui/editor/Timeline.kt").readText()

        assertTrue(source.contains("LazyLayoutCacheWindow"))
        assertTrue(source.contains("LazyRow("))
        assertTrue(source.contains("itemsIndexed("))
        assertTrue(source.contains("rememberLazyListState(cacheWindow = cacheWindow)"))
        assertTrue(source.contains("onVisibilityChanged(minFractionVisible = 0f)"))
        assertTrue(source.contains("TimelineScrollPerformancePolicy.CACHE_WINDOW_AHEAD_DP"))
        assertTrue(source.contains("TimelineScrollPerformancePolicy.shouldRenderExpensiveContent"))
        assertTrue(source.contains("userScrollEnabled = false"))
    }

    @Test
    fun frameTimingBenchmarkStillExercisesTimelineScrubbing() {
        val source = locate(
            "baselineprofile/src/main/java/com/novacut/baselineprofile/StartupAndEditorMacrobenchmark.kt"
        ).readText()

        assertTrue(source.contains("FrameTimingMetric()"))
        assertTrue(source.contains("scrubTimelineViewport()"))
    }

    @Test
    fun cacheWindowKeepsAheadPrefetchLargerThanBehindRetention() {
        assertTrue(
            TimelineScrollPerformancePolicy.CACHE_WINDOW_AHEAD_DP >
                TimelineScrollPerformancePolicy.CACHE_WINDOW_BEHIND_DP
        )
    }

    private fun locate(relativePath: String): File {
        return listOf(File(relativePath), File("../$relativePath"))
            .firstOrNull(File::exists)
            ?: error("$relativePath not found")
    }
}

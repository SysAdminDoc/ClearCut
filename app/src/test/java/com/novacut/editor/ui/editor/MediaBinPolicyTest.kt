package com.novacut.editor.ui.editor

import android.net.FakeUri
import com.novacut.editor.engine.MediaRelinkProbe
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaBinPolicyTest {

    @Test
    fun searchMatchesNamesNotesAndTags() {
        val assets = listOf(
            asset("a", "camera-01.mp4", notes = "interview setup"),
            asset("b", "b-roll.mp4", tags = listOf("outdoor")),
            asset("c", "voice.wav"),
        )

        assertEquals(
            listOf("a"),
            filterAndSortMediaAssets(assets, MediaBinQuery(search = "INTERVIEW")).map { it.assetId }
        )
        assertEquals(
            listOf("b"),
            filterAndSortMediaAssets(assets, MediaBinQuery(search = "outdoor")).map { it.assetId }
        )
    }

    @Test
    fun filtersCoverMissingRelinkUsedUnusedAndTaggedAssets() {
        val assets = listOf(
            asset("missing", "missing.mp4", accessible = false, state = MediaRelinkProbe.RelinkState.MISSING),
            asset("unknown", "unknown.mp4", accessible = false, state = MediaRelinkProbe.RelinkState.UNKNOWN),
            asset("used", "used.mp4", used = listOf("clip-1")),
            asset("unused", "unused.mp4"),
            asset("tagged", "tagged.mp4", tags = listOf("selects")),
        )

        assertEquals(listOf("missing", "unknown"), ids(MediaBinFilter.MISSING, assets))
        assertEquals(listOf("missing", "unknown"), ids(MediaBinFilter.RELINK_NEEDED, assets))
        assertEquals(listOf("used"), ids(MediaBinFilter.USED, assets))
        assertEquals(listOf("missing", "unknown", "tagged", "unused"), ids(MediaBinFilter.UNUSED, assets))
        assertEquals(listOf("tagged"), ids(MediaBinFilter.TAGGED, assets))
    }

    @Test
    fun sortUsesStableAssetIdAsTieBreaker() {
        val assets = listOf(
            asset("b", "Same.mp4", size = 4L),
            asset("a", "same.mp4", size = 4L),
        )

        assertEquals(
            listOf("a", "b"),
            filterAndSortMediaAssets(assets, MediaBinQuery(sort = MediaBinSort.NAME)).map { it.assetId }
        )
        assertEquals(
            listOf("a", "b"),
            filterAndSortMediaAssets(assets, MediaBinQuery(sort = MediaBinSort.SIZE)).map { it.assetId }
        )
    }

    @Test
    fun scanStatesDistinguishEmptyPartialFailureAndCancellation() {
        val empty = MediaScanResult()
        val partial = MediaScanResult(
            issues = listOf(
                MediaScanIssue("provider", "provider.mp4", MediaScanIssueKind.PROVIDER_FAILURE),
                MediaScanIssue("skipped", "skipped.wav", MediaScanIssueKind.SKIPPED),
            ),
        )

        assertEquals(MediaScanStatus.READY, MediaScanState.Ready(empty).status())
        assertEquals(
            MediaScanStatus.READY_WITH_PARTIAL_RESULTS,
            MediaScanState.Ready(partial).status(),
        )
        assertEquals(1, partial.providerFailureCount)
        assertEquals(1, partial.skippedCount)
        assertEquals(MediaScanStatus.FAILED, MediaScanState.Failed(empty).status())
        assertEquals(MediaScanStatus.CANCELLED, MediaScanState.Cancelled(empty).status())
    }

    @Test
    fun retryGenerationAdvancesAndWrapsWithoutReusingAStaleRequest() {
        assertEquals(1L, nextMediaScanGeneration(0L))
        assertEquals(2L, nextMediaScanGeneration(1L))
        assertEquals(1L, nextMediaScanGeneration(Long.MAX_VALUE))
    }

    private fun ids(filter: MediaBinFilter, assets: List<MediaAsset>): List<String> =
        filterAndSortMediaAssets(assets, MediaBinQuery(filter = filter)).map { it.assetId }

    private fun asset(
        id: String,
        name: String,
        accessible: Boolean = true,
        state: MediaRelinkProbe.RelinkState = MediaRelinkProbe.RelinkState.OK,
        used: List<String> = emptyList(),
        tags: List<String> = emptyList(),
        notes: String = "",
        size: Long = 1L,
    ) = MediaAsset(
        assetId = id,
        uri = FakeUri,
        fileName = name,
        fileSize = size,
        durationMs = 1_000L,
        usedInClipIds = used,
        isAccessible = accessible,
        relinkState = state,
        notes = notes,
        tags = tags,
    )
}

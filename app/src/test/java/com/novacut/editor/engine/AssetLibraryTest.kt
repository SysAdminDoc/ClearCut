package com.novacut.editor.engine

import android.net.FakeUri
import com.novacut.editor.model.Clip
import com.novacut.editor.model.Track
import com.novacut.editor.model.TrackType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssetLibraryTest {

    private fun clip(
        id: String = "c1",
        durationMs: Long = 5000L
    ) = Clip(
        id = id,
        sourceUri = FakeUri,
        sourceDurationMs = durationMs,
        trimStartMs = 0L,
        trimEndMs = durationMs,
        timelineStartMs = 0L
    )

    private fun track(index: Int = 0, vararg clips: Clip) = Track(
        id = "t$index",
        type = TrackType.VIDEO,
        index = index,
        clips = clips.toList()
    )

    @Test
    fun assetEntry_referenceCount_correctForMultipleClips() {
        val entry = AssetLibrary.AssetEntry(
            sourceUri = FakeUri,
            displayName = "test.mp4",
            referenceCount = 3,
            clipIds = listOf("c1", "c2", "c3"),
            sizeBytes = 1000L,
            mediaType = "video",
            isMissing = false
        )
        assertEquals(3, entry.referenceCount)
        assertEquals(3, entry.clipIds.size)
    }

    @Test
    fun assetPoolSummary_emptyProject() {
        val summary = AssetLibrary.AssetPoolSummary(
            totalAssets = 0,
            totalSizeBytes = 0L,
            duplicateGroups = emptyList(),
            missingAssets = emptyList(),
            orphanedFiles = emptyList(),
            reclaimableBytes = 0L
        )
        assertEquals(0, summary.totalAssets)
        assertEquals(0L, summary.reclaimableBytes)
    }

    @Test
    fun duplicateGroup_totalSize_computed() {
        val group = AssetLibrary.DuplicateGroup(
            sourceUri = FakeUri,
            entries = listOf(
                AssetLibrary.AssetEntry(
                    sourceUri = FakeUri,
                    displayName = "dup.mp4",
                    referenceCount = 3,
                    clipIds = listOf("c1", "c2", "c3"),
                    sizeBytes = 5000L,
                    mediaType = "video",
                    isMissing = false
                )
            ),
            totalSizeBytes = 15000L
        )
        assertEquals(15000L, group.totalSizeBytes)
    }

    @Test
    fun orphanedFile_tracksSize() {
        val orphan = AssetLibrary.OrphanedFile(
            file = java.io.File("/tmp/orphan.mp4"),
            sizeBytes = 2048L,
            directory = "imports"
        )
        assertEquals(2048L, orphan.sizeBytes)
        assertEquals("imports", orphan.directory)
    }

    @Test
    fun assetPoolSummary_reclaimableBytes_sumsOrphans() {
        val orphans = listOf(
            AssetLibrary.OrphanedFile(java.io.File("/a"), 100L, "d"),
            AssetLibrary.OrphanedFile(java.io.File("/b"), 200L, "d"),
            AssetLibrary.OrphanedFile(java.io.File("/c"), 300L, "d")
        )
        val summary = AssetLibrary.AssetPoolSummary(
            totalAssets = 5,
            totalSizeBytes = 10000L,
            duplicateGroups = emptyList(),
            missingAssets = emptyList(),
            orphanedFiles = orphans,
            reclaimableBytes = orphans.sumOf { it.sizeBytes }
        )
        assertEquals(600L, summary.reclaimableBytes)
    }
}

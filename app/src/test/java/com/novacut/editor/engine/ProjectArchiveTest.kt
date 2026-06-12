package com.novacut.editor.engine

import android.net.TestUri
import com.novacut.editor.model.Clip
import com.novacut.editor.model.Track
import com.novacut.editor.model.TrackType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectArchiveTest {

    @Test
    fun rewriteArchivedMediaUrisForImportKeepsMediaAssetManifestPlayable() {
        val archivedUri = TestUri(
            raw = "file:///old/project/media/imports/clip.mp4",
            schemeValue = "file",
            segment = "clip.mp4"
        )
        val extractedUri = TestUri(
            raw = "file:///new/project/media/0_clip.mp4",
            schemeValue = "file",
            segment = "0_clip.mp4"
        )
        val staleOriginalUri = "content://picker/transient/clip"
        val state = AutoSaveState(
            projectId = "project",
            mediaAssets = listOf(
                ProjectMediaAsset(
                    assetId = "asset-clip",
                    managedUri = archivedUri.toString(),
                    originalUri = staleOriginalUri,
                    displayName = "clip.mp4",
                    mediaType = "video",
                    mimeType = "video/mp4",
                    sizeBytes = 1024L,
                    durationMs = 5_000L,
                    width = 1920,
                    height = 1080,
                    quickFingerprint = "fingerprint",
                    importStatus = "ready",
                    lastVerifiedAtEpochMs = 10L
                )
            ),
            tracks = listOf(
                Track(
                    type = TrackType.VIDEO,
                    index = 0,
                    clips = listOf(
                        Clip(
                            assetId = "asset-clip",
                            sourceUri = archivedUri,
                            sourceDurationMs = 5_000L,
                            timelineStartMs = 0L
                        )
                    )
                )
            )
        )
        val seen = linkedSetOf<String>()
        val unresolved = mutableListOf<String>()

        val rewritten = ProjectArchive.rewriteArchivedMediaUrisForImport(
            state = state,
            manifestEntryMap = mapOf(archivedUri.toString() to "media/0_clip.mp4"),
            extractedFiles = mapOf("media/0_clip.mp4" to extractedUri),
            seenSourceUris = seen,
            unresolvedSink = unresolved
        )

        val clip = rewritten.tracks.single().clips.single()
        val asset = rewritten.mediaAssets.single()
        assertEquals(extractedUri.toString(), clip.sourceUri.toString())
        assertEquals(extractedUri.toString(), asset.managedUri)
        assertEquals(extractedUri.toString(), asset.originalUri)
        assertEquals(setOf(archivedUri.toString()), seen)
        assertTrue(unresolved.isEmpty())
    }
}

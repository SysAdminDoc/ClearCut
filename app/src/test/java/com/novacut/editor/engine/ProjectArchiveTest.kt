package com.novacut.editor.engine

import android.net.TestUri
import com.novacut.editor.model.Clip
import com.novacut.editor.model.ImageOverlay
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

    @Test
    fun rewriteArchivedMediaUrisForImportRewritesNestedClipsAndImageOverlays() {
        val parentUri = testUri("file:///old/project/media/imports/parent.mp4", "parent.mp4")
        val nestedUri = testUri("file:///old/project/media/imports/nested.mp4", "nested.mp4")
        val overlayUri = testUri("file:///old/project/media/imports/sticker.png", "sticker.png")
        val extractedParentUri = testUri("file:///new/project/media/0_parent.mp4", "0_parent.mp4")
        val extractedNestedUri = testUri("file:///new/project/media/1_nested.mp4", "1_nested.mp4")
        val extractedOverlayUri = testUri("file:///new/project/media/2_sticker.png", "2_sticker.png")
        val state = AutoSaveState(
            projectId = "project",
            tracks = listOf(
                Track(
                    type = TrackType.VIDEO,
                    index = 0,
                    clips = listOf(
                        Clip(
                            id = "parent",
                            sourceUri = parentUri,
                            sourceDurationMs = 5_000L,
                            timelineStartMs = 0L,
                            isCompound = true,
                            compoundClips = listOf(
                                Clip(
                                    id = "nested",
                                    sourceUri = nestedUri,
                                    sourceDurationMs = 2_000L,
                                    timelineStartMs = 0L
                                )
                            )
                        )
                    )
                )
            ),
            imageOverlays = listOf(
                ImageOverlay(
                    id = "overlay",
                    sourceUri = overlayUri,
                    startTimeMs = 0L,
                    endTimeMs = 1_000L
                )
            )
        )
        val seen = linkedSetOf<String>()
        val unresolved = mutableListOf<String>()

        val rewritten = ProjectArchive.rewriteArchivedMediaUrisForImport(
            state = state,
            manifestEntryMap = mapOf(
                parentUri.toString() to "media/0_parent.mp4",
                nestedUri.toString() to "media/1_nested.mp4",
                overlayUri.toString() to "media/2_sticker.png"
            ),
            extractedFiles = mapOf(
                "media/0_parent.mp4" to extractedParentUri,
                "media/1_nested.mp4" to extractedNestedUri,
                "media/2_sticker.png" to extractedOverlayUri
            ),
            seenSourceUris = seen,
            unresolvedSink = unresolved
        )

        val parentClip = rewritten.tracks.single().clips.single()
        val nestedClip = parentClip.compoundClips.single()
        val overlay = rewritten.imageOverlays.single()
        assertEquals(extractedParentUri.toString(), parentClip.sourceUri.toString())
        assertEquals(extractedNestedUri.toString(), nestedClip.sourceUri.toString())
        assertEquals(extractedOverlayUri.toString(), overlay.sourceUri.toString())
        assertEquals(setOf(parentUri.toString(), nestedUri.toString(), overlayUri.toString()), seen)
        assertTrue(unresolved.isEmpty())
    }

    @Test
    fun rewriteArchivedMediaUrisForImportReportsUnresolvedMediaOnce() {
        val missingUri = testUri("file:///old/project/media/imports/missing.mp4", "missing.mp4")
        val state = AutoSaveState(
            projectId = "project",
            tracks = listOf(
                Track(
                    type = TrackType.VIDEO,
                    index = 0,
                    clips = listOf(
                        Clip(
                            id = "clip-missing",
                            sourceUri = missingUri,
                            sourceDurationMs = 5_000L,
                            timelineStartMs = 0L
                        ),
                        Clip(
                            id = "clip-missing-duplicate",
                            sourceUri = missingUri,
                            sourceDurationMs = 5_000L,
                            timelineStartMs = 5_000L
                        )
                    )
                )
            )
        )
        val seen = linkedSetOf<String>()
        val unresolved = mutableListOf<String>()

        val rewritten = ProjectArchive.rewriteArchivedMediaUrisForImport(
            state = state,
            manifestEntryMap = mapOf(missingUri.toString() to "media/missing.mp4"),
            extractedFiles = emptyMap(),
            seenSourceUris = seen,
            unresolvedSink = unresolved
        )

        assertEquals(
            listOf(missingUri.toString(), missingUri.toString()),
            rewritten.tracks.single().clips.map { it.sourceUri.toString() }
        )
        assertEquals(setOf(missingUri.toString()), seen)
        assertEquals(listOf(missingUri.toString()), unresolved)
    }

    private fun testUri(raw: String, segment: String): TestUri {
        return TestUri(
            raw = raw,
            schemeValue = raw.substringBefore(':'),
            segment = segment
        )
    }
}

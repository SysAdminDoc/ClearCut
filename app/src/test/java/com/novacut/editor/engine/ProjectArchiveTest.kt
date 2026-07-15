package com.novacut.editor.engine

import android.net.TestUri
import com.novacut.editor.model.Clip
import com.novacut.editor.model.Caption
import com.novacut.editor.model.CaptionStyle
import com.novacut.editor.model.ColorGrade
import com.novacut.editor.model.ImageOverlay
import com.novacut.editor.model.TextOverlay
import com.novacut.editor.model.Track
import com.novacut.editor.model.TrackType
import com.novacut.editor.model.Watermark
import com.novacut.editor.model.WatermarkPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.io.IOException
import java.security.MessageDigest

class ProjectArchiveTest {

    @Test
    fun archiveImportPlanCountsOnlySupportedEntries() {
        val plan = ProjectArchive.planArchiveImport(
            listOf(
                archiveEntry("project.json", size = 1_000L, compressedSize = 500L),
                archiveEntry("media_manifest.json", size = 600L, compressedSize = 300L),
                archiveEntry("media/clip.mp4", size = 8_000L, compressedSize = 4_000L),
                archiveEntry("notes.txt", size = 10_000L, compressedSize = 500L)
            )
        )

        assertEquals(9_600L, plan.expandedBytes)
        assertEquals(3, plan.extractableEntryCount)
    }

    @Test
    fun archiveImportPlanRejectsCompressionBombsAndUnknownSizes() {
        expectPlanFailure("compression-ratio") {
            listOf(
                archiveEntry("project.json", size = 1_000L, compressedSize = 500L),
                archiveEntry(
                    "media/repeated.bin",
                    size = ProjectArchive.MAX_ARCHIVE_COMPRESSION_RATIO + 1L,
                    compressedSize = 1L
                )
            )
        }
        expectPlanFailure("unknown size") {
            listOf(archiveEntry("project.json", size = -1L, compressedSize = -1L))
        }
    }

    @Test
    fun archiveImportPlanRejectsCumulativeExpansionAndUnsafeNames() {
        expectPlanFailure("expanded-size") {
            listOf(
                archiveEntry("project.json", size = 1L, compressedSize = 1L),
                archiveEntry(
                    "media/huge.bin",
                    size = ProjectArchive.MAX_ARCHIVE_TOTAL_BYTES,
                    compressedSize = ProjectArchive.MAX_ARCHIVE_TOTAL_BYTES / 2L
                )
            )
        }
        expectPlanFailure("unsafe entry path") {
            listOf(
                archiveEntry("project.json", size = 1L, compressedSize = 1L),
                archiveEntry("../media/escape.mp4", size = 1L, compressedSize = 1L)
            )
        }
    }

    @Test
    fun mediaManifestV1StillParsesAsOptionalMedia() {
        val raw = """{"version":1,"entries":[{"originalUri":"content://clip","entryName":"media/0.mp4"}]}"""

        val parsed = ProjectArchive.parseMediaManifest(raw)

        assertEquals(1, parsed.version)
        assertEquals(
            ProjectArchive.ArchiveManifestEntry(
                kind = ProjectDependencyKind.MEDIA.name,
                logicalReference = "content://clip",
                entryName = "media/0.mp4",
                byteLength = null,
                sha256 = null,
                archivePolicy = ProjectDependencyArchivePolicy.INCLUDE.name,
                required = false
            ),
            parsed.entries.single()
        )
    }

    @Test
    fun mediaManifestV2RoundTripsTypedIntegrityAndPolicyFields() {
        val entries = listOf(
            ProjectArchive.ArchiveManifestEntry(
                kind = ProjectDependencyKind.LUT.name,
                logicalReference = "/old/look.cube",
                entryName = "luts/0_look.cube",
                byteLength = 42L,
                sha256 = "ab".repeat(32),
                archivePolicy = ProjectDependencyArchivePolicy.INCLUDE.name,
                required = true,
                fallbackName = "Neutral color"
            ),
            ProjectArchive.ArchiveManifestEntry(
                kind = ProjectDependencyKind.MODEL.name,
                logicalReference = SEGMENTATION_MODEL_DEPENDENCY,
                entryName = null,
                byteLength = null,
                sha256 = null,
                archivePolicy = ProjectDependencyArchivePolicy.REFERENCE_ONLY.name,
                required = true
            )
        )

        val parsed = ProjectArchive.parseMediaManifest(ProjectArchive.buildMediaManifestV2(entries))

        assertEquals(2, parsed.version)
        assertEquals(entries, parsed.entries)
    }

    @Test
    fun verifyManifestRejectsTamperedRequiredEntryAndWarnsForOptionalEntry() {
        val file = File.createTempFile("archive-manifest", ".bin").apply { writeText("tampered") }
        val actualHash = MessageDigest.getInstance("SHA-256")
            .digest("tampered".toByteArray())
            .joinToString("") { "%02x".format(it) }
        val badHash = MessageDigest.getInstance("SHA-256")
            .digest("expected".toByteArray())
            .joinToString("") { "%02x".format(it) }
        val required = ProjectArchive.ArchiveManifestEntry(
            kind = ProjectDependencyKind.MEDIA.name,
            logicalReference = "content://clip",
            entryName = "media/0.mp4",
            byteLength = 8L,
            sha256 = actualHash,
            archivePolicy = ProjectDependencyArchivePolicy.INCLUDE.name,
            required = true
        )
        ProjectArchive.verifyManifestFiles(
            ProjectArchive.ParsedArchiveManifest(2, listOf(required)),
            mapOf("media/0.mp4" to file),
            mutableListOf()
        )
        val tamperedManifest = required.copy(sha256 = badHash)
        try {
            ProjectArchive.verifyManifestFiles(
                ProjectArchive.ParsedArchiveManifest(2, listOf(tamperedManifest)),
                mapOf("media/0.mp4" to file),
                mutableListOf()
            )
            fail("Required tampering must reject the archive")
        } catch (_: IOException) {
            // Expected.
        }

        val warnings = mutableListOf<String>()
        val invalidOptionalEntries = ProjectArchive.verifyManifestFiles(
            ProjectArchive.ParsedArchiveManifest(2, listOf(tamperedManifest.copy(required = false))),
            mapOf("media/0.mp4" to file),
            warnings
        )
        assertTrue(warnings.single().contains("integrity check failed"))
        assertEquals(setOf("media/0.mp4"), invalidOptionalEntries)
        file.delete()
    }

    @Test
    fun verifyManifestRejectsUnknownRequiredKindButWarnsForOptionalKind() {
        val unknown = ProjectArchive.ArchiveManifestEntry(
            kind = "PLUGIN_PAYLOAD",
            logicalReference = "example",
            entryName = null,
            byteLength = null,
            sha256 = null,
            archivePolicy = ProjectDependencyArchivePolicy.REFERENCE_ONLY.name,
            required = true
        )
        try {
            ProjectArchive.verifyManifestFiles(
                ProjectArchive.ParsedArchiveManifest(2, listOf(unknown)),
                emptyMap(),
                mutableListOf()
            )
            fail("Unknown required dependency kinds must reject the archive")
        } catch (_: IOException) {
            // Expected.
        }

        val warnings = mutableListOf<String>()
        ProjectArchive.verifyManifestFiles(
            ProjectArchive.ParsedArchiveManifest(2, listOf(unknown.copy(required = false))),
            emptyMap(),
            warnings
        )
        assertTrue(warnings.single().contains("Ignored optional dependency kind"))
    }

    @Test
    fun rewriteDependencyReferencesUpdatesRecursiveLutsAndCustomFonts() {
        val uri = testUri("file:///clip.mp4", "clip.mp4")
        val watermarkUri = testUri("content://brand/logo.png", "logo.png")
        val restoredWatermarkUri = testUri("file:///import/watermarks/logo.png", "logo.png")
        val nested = Clip(
            sourceUri = uri,
            sourceDurationMs = 1_000L,
            timelineStartMs = 0L,
            colorGrade = ColorGrade(lutPath = "/old/look.cube"),
            captions = listOf(Caption(
                text = "caption",
                startTimeMs = 0L,
                endTimeMs = 500L,
                style = CaptionStyle(fontFamily = "custom:caption.ttf")
            ))
        )
        val state = AutoSaveState(
            projectId = "project",
            tracks = listOf(Track(
                type = TrackType.VIDEO,
                index = 0,
                clips = listOf(Clip(
                    sourceUri = uri,
                    sourceDurationMs = 1_000L,
                    timelineStartMs = 0L,
                    isCompound = true,
                    compoundClips = listOf(nested)
                ))
            )),
            textOverlays = listOf(TextOverlay(text = "title", fontFamily = "custom:title.otf")),
            exportWatermark = Watermark(
                sourceUri = watermarkUri,
                position = WatermarkPosition.TOP_LEFT,
                opacity = 0.6f,
                scalePercent = 22
            )
        )

        val rewritten = ProjectArchive.rewriteDependencyReferences(
            state,
            lutPaths = mapOf("/old/look.cube" to "/new/luts/look.cube"),
            installedFonts = mapOf(
                "custom:caption.ttf" to "custom:hash_caption.ttf",
                "custom:title.otf" to "custom:hash_title.otf"
            ),
            watermarkUris = mapOf(watermarkUri.toString() to restoredWatermarkUri)
        )

        val rewrittenNested = rewritten.tracks.single().clips.single().compoundClips.single()
        assertEquals("/new/luts/look.cube", rewrittenNested.colorGrade?.lutPath)
        assertEquals("custom:hash_caption.ttf", rewrittenNested.captions.single().style.fontFamily)
        assertEquals("custom:hash_title.otf", rewritten.textOverlays.single().fontFamily)
        assertEquals(restoredWatermarkUri.toString(), rewritten.exportWatermark?.sourceUri.toString())
        assertEquals(WatermarkPosition.TOP_LEFT, rewritten.exportWatermark?.position)
        assertEquals(0.6f, rewritten.exportWatermark?.opacity)
        assertEquals(22, rewritten.exportWatermark?.scalePercent)
    }

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

    private fun archiveEntry(
        name: String,
        size: Long,
        compressedSize: Long
    ) = ProjectArchive.ArchiveEntryMetadata(
        name = name,
        isDirectory = false,
        size = size,
        compressedSize = compressedSize
    )

    private fun expectPlanFailure(
        messageFragment: String,
        entries: () -> List<ProjectArchive.ArchiveEntryMetadata>
    ) {
        try {
            ProjectArchive.planArchiveImport(entries())
            fail("Expected archive import plan to reject $messageFragment")
        } catch (error: IOException) {
            assertTrue(error.message.orEmpty().contains(messageFragment))
        }
    }
}

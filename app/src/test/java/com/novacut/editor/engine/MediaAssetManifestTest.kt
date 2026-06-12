package com.novacut.editor.engine

import android.net.FakeUri
import android.net.SecondFakeUri
import android.net.TestUri
import com.novacut.editor.model.Clip
import com.novacut.editor.model.ImageOverlay
import com.novacut.editor.model.Track
import com.novacut.editor.model.TrackType
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class MediaAssetManifestTest {

    @Test
    fun sidecarFileUsesSiblingAssetJsonName() {
        val mediaFile = File("/tmp/project/clip.mp4")

        assertEquals(
            File("/tmp/project/clip.mp4.asset.json"),
            mediaAssetSidecarFileFor(mediaFile)
        )
        assertTrue(isMediaAssetSidecar(File("/tmp/project/clip.mp4.asset.json")))
        assertFalse(isMediaAssetSidecar(mediaFile))
        assertEquals(mediaFile, mediaFileForAssetSidecar(File("/tmp/project/clip.mp4.asset.json")))
        assertNull(mediaFileForAssetSidecar(mediaFile))
    }

    @Test
    fun quickFingerprintChangesWhenTailChanges() {
        val dir = Files.createTempDirectory("media-asset-").toFile()
        try {
            val first = File(dir, "first.mp4").apply {
                writeBytes(ByteArray(1024 * 1024 + 8) { index -> (index % 31).toByte() })
            }
            val second = File(dir, "second.mp4").apply {
                writeBytes(first.readBytes())
                appendBytes(byteArrayOf(1, 2, 3, 4))
            }

            assertNotEquals(
                quickMediaAssetFingerprint(first),
                quickMediaAssetFingerprint(second)
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun recordJsonKeepsNullMetadataKeys() {
        val record = MediaAssetRecord(
            assetId = "asset-123",
            managedUri = "file:///managed.mp4",
            originalUri = "content://source/video",
            displayName = null,
            mediaType = "video",
            mimeType = null,
            sizeBytes = 42L,
            durationMs = null,
            width = null,
            height = null,
            quickFingerprint = "abcdef",
            importedAtEpochMs = 100L,
            lastVerifiedAtEpochMs = 200L
        )

        val json = JSONObject(record.toJson().toString())
        assertEquals(1, json.getInt("schemaVersion"))
        assertEquals("asset-123", json.getString("assetId"))
        assertTrue(json.has("displayName"))
        assertTrue(json.has("mimeType"))
        assertTrue(json.has("sha256"))
        assertEquals("pending", json.getString("hashStatus"))
        assertEquals("sha256:size:first-last-1m", json.getString("fingerprintAlgorithm"))
    }

    @Test
    fun collectMediaAssetReferencesIncludesNestedClipsAndImageOverlays() {
        val nestedUri = SecondFakeUri as android.net.Uri
        val overlayUri = TestUri(
            raw = "content://overlay/sticker.png",
            schemeValue = "content",
            segment = "sticker.png"
        )
        val state = AutoSaveState(
            projectId = "project",
            tracks = listOf(
                Track(
                    type = TrackType.VIDEO,
                    index = 0,
                    clips = listOf(
                        Clip(
                            sourceUri = FakeUri,
                            sourceDurationMs = 1000L,
                            timelineStartMs = 0L,
                            isCompound = true,
                            compoundClips = listOf(
                                Clip(
                                    sourceUri = nestedUri,
                                    sourceDurationMs = 500L,
                                    timelineStartMs = 0L
                                )
                            )
                        )
                    )
                )
            ),
            imageOverlays = listOf(
                ImageOverlay(
                    sourceUri = overlayUri,
                    startTimeMs = 0L,
                    endTimeMs = 1000L
                )
            )
        )

        val references = collectMediaAssetReferences(state)

        assertEquals(
            listOf(FakeUri.toString(), nestedUri.toString(), overlayUri.toString()),
            references.map { it.uri.toString() }
        )
        assertEquals(listOf("video", "video", "image"), references.map { it.mediaType })
    }

    @Test
    fun projectMediaAssetFromJsonRejectsBlankIdentityFields() {
        assertNull(projectMediaAssetFromJson(JSONObject().put("managedUri", "file:///clip.mp4")))
        assertNull(projectMediaAssetFromJson(JSONObject().put("assetId", "asset-1")))
    }

    @Test
    fun projectMediaAssetFromJsonDefaultsBlankOriginalUriToManagedUri() {
        val asset = projectMediaAssetFromJson(
            JSONObject()
                .put("assetId", "asset-1")
                .put("managedUri", "file:///clip.mp4")
                .put("originalUri", "")
                .put("mediaType", "video")
        )

        assertEquals("file:///clip.mp4", asset?.originalUri)
    }

    @Test
    fun readProjectMediaAssetSidecarRejectsOversizedJson() {
        val dir = Files.createTempDirectory("media-sidecar-").toFile()
        try {
            val sidecar = File(dir, "clip.mp4.asset.json").apply {
                writeText(" ".repeat(65 * 1024), Charsets.UTF_8)
            }

            assertNull(readProjectMediaAssetSidecar(sidecar))
        } finally {
            dir.deleteRecursively()
        }
    }
}

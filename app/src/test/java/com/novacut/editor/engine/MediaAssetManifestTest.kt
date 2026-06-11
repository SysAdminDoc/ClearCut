package com.novacut.editor.engine

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}

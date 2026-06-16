package com.novacut.editor.engine

import android.net.FakeUri
import com.novacut.editor.model.Clip
import com.novacut.editor.model.Track
import com.novacut.editor.model.TrackType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaLifecycleTest {

    private fun clip(
        id: String = "c1",
        durationMs: Long = 5000L,
        assetId: String? = "asset-001"
    ) = Clip(
        id = id,
        sourceUri = FakeUri,
        sourceDurationMs = durationMs,
        trimStartMs = 0L,
        trimEndMs = durationMs,
        timelineStartMs = 0L,
        assetId = assetId
    )

    private fun track(vararg clips: Clip) = Track(
        id = "t1",
        type = TrackType.VIDEO,
        index = 0,
        clips = clips.toList()
    )

    private fun mediaAsset(
        assetId: String = "asset-001",
        managedUri: String = "file:///data/data/com.novacut.editor/files/media/imports/clip1.mp4",
        originalUri: String = "content://media/external/video/1234",
        sizeBytes: Long = 10_000_000L,
        fingerprint: String? = "abc123"
    ) = ProjectMediaAsset(
        assetId = assetId,
        managedUri = managedUri,
        originalUri = originalUri,
        displayName = "clip1.mp4",
        mediaType = "video",
        mimeType = "video/mp4",
        sizeBytes = sizeBytes,
        durationMs = 5000L,
        width = 1920,
        height = 1080,
        quickFingerprint = fingerprint,
        importStatus = "ready",
        lastVerifiedAtEpochMs = 1000L
    )

    private fun state(
        tracks: List<Track> = emptyList(),
        assets: List<ProjectMediaAsset> = emptyList()
    ) = AutoSaveState(
        projectId = "test-project",
        tracks = tracks,
        mediaAssets = assets
    )

    @Test
    fun autosaveRoundTrip_preservesMediaAssets() {
        val original = state(
            tracks = listOf(track(clip())),
            assets = listOf(mediaAsset())
        )
        val json = original.serialize()
        val restored = AutoSaveState.deserialize(json)

        assertEquals(1, restored.mediaAssets.size)
        val asset = restored.mediaAssets[0]
        assertEquals("asset-001", asset.assetId)
        assertEquals("file:///data/data/com.novacut.editor/files/media/imports/clip1.mp4", asset.managedUri)
        assertEquals("content://media/external/video/1234", asset.originalUri)
        assertEquals("video/mp4", asset.mimeType)
        assertEquals(10_000_000L, asset.sizeBytes)
        assertEquals("abc123", asset.quickFingerprint)
        assertEquals("ready", asset.importStatus)
    }

    @Test
    fun autosaveRoundTrip_preservesClipAssetId_inJson() {
        val original = state(
            tracks = listOf(track(clip(assetId = "asset-002"))),
            assets = listOf(mediaAsset(assetId = "asset-002"))
        )
        val json = original.serialize()
        assertTrue("JSON should contain assetId", json.contains("asset-002"))
    }

    @Test
    fun autosaveRoundTrip_nullAssetId_omittedFromJson() {
        val original = state(tracks = listOf(track(clip(assetId = null))))
        val json = original.serialize()
        val trackJson = org.json.JSONObject(json).getJSONArray("tracks")
            .getJSONObject(0).getJSONArray("clips").getJSONObject(0)
        assertTrue("Null assetId should be absent or null",
            !trackJson.has("assetId") || trackJson.isNull("assetId") || trackJson.optString("assetId") == "")
    }

    @Test
    fun autosaveRoundTrip_emptyMediaAssets_deserializesToEmptyList() {
        val original = state(tracks = listOf(track(clip())))
        val json = original.serialize()
        val restored = AutoSaveState.deserialize(json)

        assertTrue(restored.mediaAssets.isEmpty())
    }

    @Test
    fun autosaveRoundTrip_nullFingerprint_preserved() {
        val original = state(assets = listOf(mediaAsset(fingerprint = null)))
        val json = original.serialize()
        val restored = AutoSaveState.deserialize(json)

        assertEquals(null, restored.mediaAssets[0].quickFingerprint)
    }

    @Test
    fun autosaveRoundTrip_corruptMediaAssetEntry_skippedGracefully() {
        val json = """
        {
            "projectId": "test",
            "tracks": [],
            "mediaAssets": [
                {"assetId": "", "managedUri": ""},
                {"assetId": "good", "managedUri": "file:///good.mp4", "originalUri": "content://ok"}
            ]
        }
        """.trimIndent()
        val restored = AutoSaveState.deserialize(json)

        assertEquals(1, restored.mediaAssets.size)
        assertEquals("good", restored.mediaAssets[0].assetId)
    }

    @Test
    fun autosaveRoundTrip_unknownFutureFields_ignored() {
        val json = """
        {
            "projectId": "test",
            "tracks": [],
            "mediaAssets": [
                {
                    "assetId": "a1",
                    "managedUri": "file:///test.mp4",
                    "originalUri": "content://test",
                    "mediaType": "video",
                    "sizeBytes": 1000,
                    "importStatus": "ready",
                    "lastVerifiedAtEpochMs": 100,
                    "futureField": "unknown",
                    "anotherNewThing": 42
                }
            ]
        }
        """.trimIndent()
        val restored = AutoSaveState.deserialize(json)

        assertEquals(1, restored.mediaAssets.size)
        assertEquals("a1", restored.mediaAssets[0].assetId)
    }

    @Test
    fun autosaveRoundTrip_multipleClipsWithAssets_allPreserved() {
        val clips = (1..5).map { i -> clip(id = "c$i", assetId = "asset-$i") }
        val assets = (1..5).map { i ->
            mediaAsset(
                assetId = "asset-$i",
                managedUri = "file:///media/clip$i.mp4",
                originalUri = "content://video/$i"
            )
        }
        val original = state(
            tracks = listOf(Track(id = "t1", type = TrackType.VIDEO, index = 0, clips = clips)),
            assets = assets
        )
        val json = original.serialize()
        val restored = AutoSaveState.deserialize(json)

        assertEquals(5, restored.mediaAssets.size)
        restored.mediaAssets.forEachIndexed { i, asset ->
            assertEquals("asset-${i + 1}", asset.assetId)
        }
        val assetsArr = org.json.JSONObject(json).getJSONArray("mediaAssets")
        assertEquals(5, assetsArr.length())
    }
}

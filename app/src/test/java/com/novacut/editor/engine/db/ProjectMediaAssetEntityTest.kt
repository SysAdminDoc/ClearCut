package com.novacut.editor.engine.db

import com.novacut.editor.engine.ProjectMediaAsset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectMediaAssetEntityTest {

    @Test
    fun databaseVersionIncludesMediaAssetMigration() {
        assertTrue(ProjectDatabase.ALL_MIGRATIONS.any {
            it.startVersion == 7 && it.endVersion == 8
        })
        assertTrue(ProjectDatabase.ALL_MIGRATIONS.any {
            it.startVersion == 9 && it.endVersion == 10
        })
    }

    @Test
    fun projectMediaAssetEntityRoundTripsManifestFields() {
        val asset = ProjectMediaAsset(
            assetId = "asset-1",
            managedUri = "file:///managed/clip.mp4",
            originalUri = "content://picker/clip",
            displayName = "clip.mp4",
            mediaType = "video",
            mimeType = "video/mp4",
            sizeBytes = 1234L,
            durationMs = 5678L,
            width = 1920,
            height = 1080,
            quickFingerprint = "fingerprint",
            importStatus = "ready",
            lastVerifiedAtEpochMs = 42L,
            notes = "Keep interview selects",
            tags = listOf("interview", "selects")
        )

        val entity = asset.toProjectMediaAssetEntity(projectId = "project")
        val restored = entity.toProjectMediaAsset()

        assertEquals("project", entity.projectId)
        assertEquals(asset, restored)
        assertEquals(listOf(entity), listOf(asset).toProjectMediaAssetEntities("project"))
    }
}

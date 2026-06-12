package com.novacut.editor.engine

import android.net.TestUri
import com.novacut.editor.model.Clip
import com.novacut.editor.model.Track
import com.novacut.editor.model.TrackType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class MediaHealthTest {

    @Test
    fun analyzeReportsReadyForManagedLocalMedia() {
        withTempMedia("clip.mp4", bytes = byteArrayOf(1, 2, 3)) { media ->
            val uri = fileUri(media)
            val state = stateWithClip(
                clipUri = uri,
                asset = asset(assetId = "asset-ready", managedUri = uri.toString())
            )

            val report = MediaHealth.analyze(state)

            assertTrue(report.isReady)
            assertFalse(report.needsAttention)
            assertEquals(1, report.totalReferences)
            assertEquals(1, report.localReadyReferences)
        }
    }

    @Test
    fun analyzeBlocksMissingManagedLocalMedia() {
        val missing = File(Files.createTempDirectory("missing-media-").toFile(), "gone.mp4")
        val uri = fileUri(missing)
        val state = stateWithClip(
            clipUri = uri,
            asset = asset(assetId = "asset-missing", managedUri = uri.toString())
        )

        val report = MediaHealth.analyze(state)

        assertFalse(report.isReady)
        assertTrue(report.issues.any {
            it.type == MediaHealthIssueType.MISSING_LOCAL_FILE &&
                it.severity == MediaHealthSeverity.BLOCKING
        })
    }

    @Test
    fun analyzeBlocksEmptyManagedLocalMedia() {
        withTempMedia("empty.mp4", bytes = ByteArray(0)) { media ->
            val uri = fileUri(media)
            val state = stateWithClip(
                clipUri = uri,
                clipAssetId = "asset-empty",
                asset = asset(assetId = "asset-empty", managedUri = uri.toString())
            )

            val report = MediaHealth.analyze(state)

            assertFalse(report.isReady)
            assertTrue(report.issues.any {
                it.type == MediaHealthIssueType.EMPTY_LOCAL_FILE &&
                    it.severity == MediaHealthSeverity.BLOCKING
            })
        }
    }

    @Test
    fun analyzeBlocksBlankAssetIdentityFields() {
        withTempMedia("clip.mp4", bytes = byteArrayOf(1)) { media ->
            val uri = fileUri(media)
            val state = stateWithClip(
                clipUri = uri,
                clipAssetId = "",
                asset = asset(assetId = "", managedUri = "")
            )

            val report = MediaHealth.analyze(state)

            assertFalse(report.isReady)
            assertTrue(report.issues.any {
                it.type == MediaHealthIssueType.BLANK_ASSET_FIELD &&
                    it.severity == MediaHealthSeverity.BLOCKING
            })
        }
    }

    @Test
    fun analyzeWarnsForExternalMediaWithoutManagedManifest() {
        val state = stateWithClip(
            clipUri = TestUri(
                raw = "content://picker/transient/clip",
                schemeValue = "content",
                segment = "clip"
            ),
            asset = null
        )

        val report = MediaHealth.analyze(state)

        assertTrue(report.isReady)
        assertEquals(1, report.externalReferences)
        assertTrue(report.issues.any { it.type == MediaHealthIssueType.EXTERNAL_SOURCE })
        assertTrue(report.issues.any { it.type == MediaHealthIssueType.MISSING_ASSET_MANIFEST })
    }

    @Test
    fun analyzeBlocksClipAssetIdMissingFromManifest() {
        withTempMedia("clip.mp4", bytes = byteArrayOf(1)) { media ->
            val uri = fileUri(media)
            val state = stateWithClip(
                clipUri = uri,
                clipAssetId = "asset-unknown",
                asset = asset(assetId = "asset-other", managedUri = uri.toString())
            )

            val report = MediaHealth.analyze(state)

            assertFalse(report.isReady)
            assertTrue(report.issues.any { it.type == MediaHealthIssueType.UNKNOWN_ASSET_ID })
        }
    }

    @Test
    fun analyzeBlocksDuplicateAssetIds() {
        withTempMedia("clip.mp4", bytes = byteArrayOf(1)) { media ->
            val uri = fileUri(media)
            val state = stateWithClip(
                clipUri = uri,
                asset = null,
                extraAssets = listOf(
                    asset(assetId = "asset-dup", managedUri = uri.toString()),
                    asset(assetId = "asset-dup", managedUri = uri.toString())
                )
            )

            val report = MediaHealth.analyze(state)

            assertFalse(report.isReady)
            assertTrue(report.issues.any { it.type == MediaHealthIssueType.DUPLICATE_ASSET_ID })
        }
    }

    @Test
    fun analyzeWarnsWhenTimelineUriDoesNotMatchAssetManifest() {
        withTempMedia("timeline.mp4", bytes = byteArrayOf(1)) { timelineMedia ->
            withTempMedia("manifest.mp4", bytes = byteArrayOf(1)) { manifestMedia ->
                val timelineUri = fileUri(timelineMedia)
                val manifestUri = fileUri(manifestMedia)
                val state = stateWithClip(
                    clipUri = timelineUri,
                    clipAssetId = "asset-stale",
                    asset = asset(assetId = "asset-stale", managedUri = manifestUri.toString())
                )

                val report = MediaHealth.analyze(state)

                assertTrue(report.isReady)
                assertTrue(report.issues.any { it.type == MediaHealthIssueType.STALE_REFERENCE_URI })
            }
        }
    }

    @Test
    fun analyzeWarnsButDoesNotBlockWhenProxyFileIsMissing() {
        withTempMedia("clip.mp4", bytes = byteArrayOf(1)) { media ->
            val uri = fileUri(media)
            val missingProxy = TestUri(
                raw = File(media.parentFile, "missing-proxy.mp4").toURI().toString(),
                schemeValue = "file",
                segment = "missing-proxy.mp4"
            )
            val state = stateWithClip(
                clipUri = uri,
                proxyUri = missingProxy,
                asset = asset(assetId = "asset-ready", managedUri = uri.toString())
            )

            val report = MediaHealth.analyze(state)

            assertTrue(report.isReady)
            assertTrue(report.issues.any { it.type == MediaHealthIssueType.MISSING_PROXY_FILE })
        }
    }

    @Test
    fun analyzeWarnsButDoesNotBlockWhenProxyFileIsEmpty() {
        withTempMedia("clip.mp4", bytes = byteArrayOf(1)) { media ->
            withTempMedia("empty-proxy.mp4", bytes = ByteArray(0)) { proxy ->
                val uri = fileUri(media)
                val emptyProxy = fileUri(proxy)
                val state = stateWithClip(
                    clipUri = uri,
                    proxyUri = emptyProxy,
                    asset = asset(assetId = "asset-ready", managedUri = uri.toString())
                )

                val report = MediaHealth.analyze(state)

                assertTrue(report.isReady)
                assertTrue(report.issues.any {
                    it.type == MediaHealthIssueType.EMPTY_PROXY_FILE &&
                        it.severity == MediaHealthSeverity.WARNING
                })
            }
        }
    }

    private fun stateWithClip(
        clipUri: android.net.Uri,
        proxyUri: android.net.Uri? = null,
        clipAssetId: String = "asset-ready",
        asset: ProjectMediaAsset?,
        extraAssets: List<ProjectMediaAsset> = emptyList()
    ): AutoSaveState {
        return AutoSaveState(
            projectId = "project",
            tracks = listOf(
                Track(
                    type = TrackType.VIDEO,
                    index = 0,
                    clips = listOf(
                        Clip(
                            id = "clip-1",
                            assetId = clipAssetId,
                            sourceUri = clipUri,
                            proxyUri = proxyUri,
                            sourceDurationMs = 1_000L,
                            timelineStartMs = 0L
                        )
                    )
                )
            ),
            mediaAssets = listOfNotNull(asset) + extraAssets
        )
    }

    private fun asset(
        assetId: String,
        managedUri: String,
        originalUri: String = managedUri
    ): ProjectMediaAsset {
        return ProjectMediaAsset(
            assetId = assetId,
            managedUri = managedUri,
            originalUri = originalUri,
            displayName = "clip.mp4",
            mediaType = "video",
            mimeType = "video/mp4",
            sizeBytes = 1L,
            durationMs = 1_000L,
            width = 1920,
            height = 1080,
            quickFingerprint = "fingerprint",
            importStatus = "ready",
            lastVerifiedAtEpochMs = 1L
        )
    }

    private fun withTempMedia(name: String, bytes: ByteArray, block: (File) -> Unit) {
        val dir = Files.createTempDirectory("media-health-").toFile()
        try {
            val file = File(dir, name).apply { writeBytes(bytes) }
            block(file)
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun fileUri(file: File): TestUri {
        return TestUri(
            raw = file.toURI().toString(),
            schemeValue = "file",
            segment = file.name
        )
    }
}

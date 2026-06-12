package com.novacut.editor.engine

import android.net.Uri
import com.novacut.editor.model.Clip
import com.novacut.editor.model.Track
import com.novacut.editor.model.TrackType
import java.io.File
import java.net.URI

enum class MediaHealthSeverity {
    INFO,
    WARNING,
    BLOCKING
}

enum class MediaHealthIssueType {
    DUPLICATE_ASSET_ID,
    BLANK_ASSET_FIELD,
    MISSING_LOCAL_FILE,
    EMPTY_LOCAL_FILE,
    MISSING_PROXY_FILE,
    EMPTY_PROXY_FILE,
    EXTERNAL_SOURCE,
    MISSING_ASSET_MANIFEST,
    MISSING_REFERENCE_ASSET_ID,
    UNKNOWN_ASSET_ID,
    STALE_REFERENCE_URI,
    UNPROBEABLE_SOURCE
}

data class MediaHealthIssue(
    val type: MediaHealthIssueType,
    val severity: MediaHealthSeverity,
    val subjectId: String,
    val uri: String? = null,
    val message: String
)

data class MediaHealthReport(
    val totalReferences: Int,
    val managedAssets: Int,
    val localReadyReferences: Int,
    val externalReferences: Int,
    val issues: List<MediaHealthIssue>
) {
    val blockingCount: Int get() = issues.count { it.severity == MediaHealthSeverity.BLOCKING }
    val warningCount: Int get() = issues.count { it.severity == MediaHealthSeverity.WARNING }
    val isReady: Boolean get() = blockingCount == 0
    val needsAttention: Boolean get() = issues.isNotEmpty()
}

object MediaHealth {

    fun analyze(state: AutoSaveState): MediaHealthReport {
        val references = collectProjectReferences(state)
        val duplicateAssetIds = state.mediaAssets
            .groupingBy { it.assetId }
            .eachCount()
            .filterValues { it > 1 }
            .keys
        val assetsById = state.mediaAssets
            .filter { it.assetId.isNotBlank() }
            .distinctBy { it.assetId }
            .associateBy { it.assetId }
        val assetsByKnownUri = state.mediaAssets
            .flatMap { asset ->
                listOf(asset.managedUri, asset.originalUri)
                    .filter { it.isNotBlank() }
                    .map { uri -> uri to asset }
            }
            .toMap()
        val issues = mutableListOf<MediaHealthIssue>()

        duplicateAssetIds.forEach { assetId ->
            issues += MediaHealthIssue(
                type = MediaHealthIssueType.DUPLICATE_ASSET_ID,
                severity = MediaHealthSeverity.BLOCKING,
                subjectId = assetId,
                message = "Multiple media assets share the same asset ID."
            )
        }

        state.mediaAssets.forEach { asset ->
            issues += validateAsset(asset)
        }

        var localReady = 0
        var external = 0
        references.forEach { reference ->
            val scheme = uriScheme(reference.uri)
            val asset = reference.assetId
                ?.takeIf { it.isNotBlank() }
                ?.let { assetsById[it] }
                ?: assetsByKnownUri[reference.uri.toString()]

            when (scheme) {
                "file" -> {
                    val fileState = localFileState(reference.uri.toString())
                    if (fileState == LocalFileState.READY) {
                        localReady++
                    } else {
                        issues += fileIssue(
                            state = fileState,
                            subjectId = reference.id,
                            uri = reference.uri.toString()
                        )
                    }
                }
                "content", "http", "https", "asset" -> {
                    external++
                    if (asset == null) {
                        issues += MediaHealthIssue(
                            type = MediaHealthIssueType.EXTERNAL_SOURCE,
                            severity = MediaHealthSeverity.WARNING,
                            subjectId = reference.id,
                            uri = reference.uri.toString(),
                            message = "Media is provider-backed or remote and is not represented by a managed local asset."
                        )
                    }
                }
                else -> {
                    issues += MediaHealthIssue(
                        type = MediaHealthIssueType.UNPROBEABLE_SOURCE,
                        severity = MediaHealthSeverity.WARNING,
                        subjectId = reference.id,
                        uri = reference.uri.toString(),
                        message = "Media URI scheme is not probeable."
                    )
                }
            }

            if (state.mediaAssets.isNotEmpty()) {
                if (reference.assetId.isNullOrBlank()) {
                    issues += MediaHealthIssue(
                        type = MediaHealthIssueType.MISSING_REFERENCE_ASSET_ID,
                        severity = MediaHealthSeverity.WARNING,
                        subjectId = reference.id,
                        uri = reference.uri.toString(),
                        message = "Timeline media reference has no stable asset ID."
                    )
                } else if (reference.assetId !in assetsById) {
                    issues += MediaHealthIssue(
                        type = MediaHealthIssueType.UNKNOWN_ASSET_ID,
                        severity = MediaHealthSeverity.BLOCKING,
                        subjectId = reference.id,
                        uri = reference.uri.toString(),
                        message = "Timeline media reference points to an asset ID that is not in the project manifest."
                    )
                }
            } else if (references.isNotEmpty()) {
                issues += MediaHealthIssue(
                    type = MediaHealthIssueType.MISSING_ASSET_MANIFEST,
                    severity = MediaHealthSeverity.WARNING,
                    subjectId = state.projectId,
                    message = "Project has media references but no media asset manifest."
                )
            }

            if (asset != null &&
                reference.uri.toString() != asset.managedUri &&
                reference.uri.toString() != asset.originalUri
            ) {
                issues += MediaHealthIssue(
                    type = MediaHealthIssueType.STALE_REFERENCE_URI,
                    severity = MediaHealthSeverity.WARNING,
                    subjectId = reference.id,
                    uri = reference.uri.toString(),
                    message = "Timeline media URI does not match the managed or original URI in its asset manifest entry."
                )
            }

            reference.proxyUri?.let { proxyUri ->
                if (uriScheme(proxyUri) == "file") {
                    when (localFileState(proxyUri.toString())) {
                        LocalFileState.READY -> Unit
                        LocalFileState.EMPTY -> issues += MediaHealthIssue(
                            type = MediaHealthIssueType.EMPTY_PROXY_FILE,
                            severity = MediaHealthSeverity.WARNING,
                            subjectId = reference.id,
                            uri = proxyUri.toString(),
                            message = "Preview proxy exists but is empty; NovaCut should fall back to source media."
                        )
                        LocalFileState.MISSING,
                        LocalFileState.INVALID -> issues += MediaHealthIssue(
                            type = MediaHealthIssueType.MISSING_PROXY_FILE,
                            severity = MediaHealthSeverity.WARNING,
                            subjectId = reference.id,
                            uri = proxyUri.toString(),
                            message = "Preview proxy is missing; NovaCut should fall back to source media."
                        )
                    }
                }
            }
        }

        return MediaHealthReport(
            totalReferences = references.size,
            managedAssets = state.mediaAssets.size,
            localReadyReferences = localReady,
            externalReferences = external,
            issues = issues.distinctBy { listOf(it.type, it.subjectId, it.uri).joinToString("|") }
        )
    }

    private fun validateAsset(asset: ProjectMediaAsset): List<MediaHealthIssue> {
        val issues = mutableListOf<MediaHealthIssue>()
        if (asset.assetId.isBlank() || asset.managedUri.isBlank()) {
            issues += MediaHealthIssue(
                type = MediaHealthIssueType.BLANK_ASSET_FIELD,
                severity = MediaHealthSeverity.BLOCKING,
                subjectId = asset.assetId.ifBlank { "<blank>" },
                uri = asset.managedUri.takeIf { it.isNotBlank() },
                message = "Media asset manifest entry is missing its stable identity or managed URI."
            )
        }
        if (asset.managedUri.isNotBlank() && uriScheme(asset.managedUri) == "file") {
            val state = localFileState(asset.managedUri)
            if (state != LocalFileState.READY) {
                issues += fileIssue(state, asset.assetId, asset.managedUri)
            }
        }
        return issues
    }

    private fun fileIssue(
        state: LocalFileState,
        subjectId: String,
        uri: String
    ): MediaHealthIssue {
        return when (state) {
            LocalFileState.EMPTY -> MediaHealthIssue(
                type = MediaHealthIssueType.EMPTY_LOCAL_FILE,
                severity = MediaHealthSeverity.BLOCKING,
                subjectId = subjectId,
                uri = uri,
                message = "Local media file exists but is empty."
            )
            LocalFileState.MISSING,
            LocalFileState.INVALID -> MediaHealthIssue(
                type = MediaHealthIssueType.MISSING_LOCAL_FILE,
                severity = MediaHealthSeverity.BLOCKING,
                subjectId = subjectId,
                uri = uri,
                message = "Local media file is missing or cannot be resolved."
            )
            LocalFileState.READY -> error("READY does not produce a media health issue")
        }
    }

    private fun collectProjectReferences(state: AutoSaveState): List<ProjectMediaReference> {
        val out = mutableListOf<ProjectMediaReference>()
        state.tracks.forEach { track ->
            track.clips.forEach { clip ->
                collectClipReferences(
                    clip = clip,
                    track = track,
                    mediaType = if (track.type == TrackType.AUDIO) "audio" else "video",
                    out = out
                )
            }
        }
        state.imageOverlays.forEach { overlay ->
            out += ProjectMediaReference(
                id = overlay.id,
                assetId = null,
                uri = overlay.sourceUri,
                mediaType = "image"
            )
        }
        return out.distinctBy { "${it.id}|${it.uri}" }
    }

    private fun collectClipReferences(
        clip: Clip,
        track: Track,
        mediaType: String,
        out: MutableList<ProjectMediaReference>
    ) {
        out += ProjectMediaReference(
            id = clip.id,
            assetId = clip.assetId,
            uri = clip.sourceUri,
            mediaType = mediaType,
            proxyUri = clip.proxyUri
        )
        clip.compoundClips.forEach { child ->
            collectClipReferences(child, track, mediaType, out)
        }
    }

    private data class ProjectMediaReference(
        val id: String,
        val assetId: String?,
        val uri: Uri,
        val mediaType: String,
        val proxyUri: Uri? = null
    )

    private enum class LocalFileState {
        READY,
        MISSING,
        EMPTY,
        INVALID
    }

    private fun localFileState(uri: String): LocalFileState {
        val file = fileFromUri(uri) ?: return LocalFileState.INVALID
        return when {
            !file.isFile -> LocalFileState.MISSING
            file.length() <= 0L -> LocalFileState.EMPTY
            else -> LocalFileState.READY
        }
    }

    private fun fileFromUri(uri: String): File? {
        return runCatching {
            File(URI.create(uri))
        }.getOrElse {
            uri.removePrefix("file://")
                .takeIf { it.isNotBlank() && it != uri }
                ?.let(::File)
        }
    }

    private fun uriScheme(uri: Uri): String? = uriScheme(uri.toString())

    private fun uriScheme(uri: String): String? {
        val index = uri.indexOf(':')
        if (index <= 0) return null
        return uri.substring(0, index).lowercase()
    }
}

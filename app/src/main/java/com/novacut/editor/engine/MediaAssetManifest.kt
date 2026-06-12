package com.novacut.editor.engine

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.webkit.MimeTypeMap
import com.novacut.editor.model.Clip
import com.novacut.editor.model.ImageOverlay
import com.novacut.editor.model.Track
import com.novacut.editor.model.TrackType
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.Locale

private const val MEDIA_ASSET_TAG = "MediaAssetManifest"
private const val MEDIA_ASSET_SCHEMA_VERSION = 1
private const val FINGERPRINT_WINDOW_BYTES = 1024 * 1024
private const val MAX_MEDIA_ASSET_SIDECAR_BYTES = 64L * 1024L

internal data class MediaAssetReference(
    val uri: Uri,
    val mediaType: String
)

internal data class ManagedMediaAssetBackfillResult(
    val referencesScanned: Int,
    val sidecarsCreated: Int
)

data class ProjectMediaAsset(
    val assetId: String,
    val managedUri: String,
    val originalUri: String,
    val displayName: String?,
    val mediaType: String,
    val mimeType: String?,
    val sizeBytes: Long,
    val durationMs: Long?,
    val width: Int?,
    val height: Int?,
    val quickFingerprint: String?,
    val importStatus: String,
    val lastVerifiedAtEpochMs: Long
)

internal data class MediaAssetRecord(
    val assetId: String,
    val managedUri: String,
    val originalUri: String,
    val displayName: String?,
    val mediaType: String,
    val mimeType: String?,
    val sizeBytes: Long,
    val durationMs: Long?,
    val width: Int?,
    val height: Int?,
    val quickFingerprint: String,
    val importedAtEpochMs: Long,
    val lastVerifiedAtEpochMs: Long
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("schemaVersion", MEDIA_ASSET_SCHEMA_VERSION)
            put("assetId", assetId)
            put("managedUri", managedUri)
            put("originalUri", originalUri)
            putNullable("displayName", displayName)
            put("mediaType", mediaType)
            putNullable("mimeType", mimeType)
            put("sizeBytes", sizeBytes)
            putNullable("durationMs", durationMs)
            putNullable("width", width)
            putNullable("height", height)
            put("quickFingerprint", quickFingerprint)
            put("fingerprintAlgorithm", "sha256:size:first-last-1m")
            putNullable("sha256", null)
            put("hashStatus", "pending")
            put("importStatus", "ready")
            put("importedAtEpochMs", importedAtEpochMs)
            put("lastVerifiedAtEpochMs", lastVerifiedAtEpochMs)
        }
    }
}

internal fun writeManagedMediaAssetSidecar(
    context: Context,
    managedUri: Uri,
    originalUri: Uri,
    mediaType: String,
    importedAtEpochMs: Long = System.currentTimeMillis()
): Boolean {
    val managedFile = managedUri.takeIf { it.scheme == "file" }?.path?.let(::File) ?: return false
    val record = buildMediaAssetRecord(
        context = context,
        managedFile = managedFile,
        managedUri = managedUri,
        originalUri = originalUri,
        mediaType = mediaType,
        importedAtEpochMs = importedAtEpochMs
    ) ?: return false
    return runCatching {
        writeUtf8TextAtomically(
            targetFile = mediaAssetSidecarFileFor(managedFile),
            contents = record.toJson().toString(2)
        )
        true
    }.getOrElse { err ->
        android.util.Log.w(MEDIA_ASSET_TAG, "Failed to write media asset sidecar for $managedUri", err)
        false
    }
}

internal fun mediaAssetSidecarFileFor(mediaFile: File): File {
    return File(mediaFile.parentFile, "${mediaFile.name}.asset.json")
}

internal fun isMediaAssetSidecar(file: File): Boolean = file.name.endsWith(".asset.json")

internal fun mediaFileForAssetSidecar(sidecar: File): File? {
    if (!isMediaAssetSidecar(sidecar)) return null
    return File(sidecar.parentFile, sidecar.name.removeSuffix(".asset.json"))
}

internal fun collectMediaAssetReferences(state: AutoSaveState): List<MediaAssetReference> {
    return collectMediaAssetReferences(state.tracks, state.imageOverlays)
}

internal fun collectMediaAssetReferences(
    tracks: List<Track>,
    imageOverlays: List<ImageOverlay>
): List<MediaAssetReference> {
    val references = mutableListOf<MediaAssetReference>()
    tracks.forEach { track ->
        val mediaType = when (track.type) {
            TrackType.AUDIO -> "audio"
            else -> "video"
        }
        track.clips.forEach { clip ->
            collectClipMediaAssetReferences(clip, mediaType, references)
        }
    }
    imageOverlays.forEach { overlay ->
        references += MediaAssetReference(overlay.sourceUri, "image")
    }
    return references.distinctBy { it.uri.toString() }
}

internal fun backfillManagedMediaAssetSidecars(
    context: Context,
    state: AutoSaveState
): ManagedMediaAssetBackfillResult {
    val references = collectMediaAssetReferences(state)
    val managedDir = managedMediaDir(context)
    var created = 0
    references.forEach { reference ->
        val file = managedMediaFileForReference(reference.uri, managedDir) ?: return@forEach
        if (!mediaAssetSidecarFileFor(file).isFile &&
            writeManagedMediaAssetSidecar(context, reference.uri, reference.uri, reference.mediaType)
        ) {
            created++
        }
    }
    return ManagedMediaAssetBackfillResult(
        referencesScanned = references.size,
        sidecarsCreated = created
    )
}

internal fun buildProjectMediaAssets(
    context: Context,
    state: AutoSaveState
): List<ProjectMediaAsset> {
    return buildProjectMediaAssets(context, state.tracks, state.imageOverlays)
}

internal fun buildProjectMediaAssets(
    context: Context,
    tracks: List<Track>,
    imageOverlays: List<ImageOverlay>
): List<ProjectMediaAsset> {
    val managedDir = managedMediaDir(context)
    return collectMediaAssetReferences(tracks, imageOverlays).map { reference ->
        projectMediaAssetForReference(context, managedDir, reference)
    }
}

internal fun attachMediaAssetIdsToTracks(
    tracks: List<Track>,
    mediaAssets: List<ProjectMediaAsset>
): List<Track> {
    if (mediaAssets.isEmpty()) return tracks
    val assetIdsByUri = mediaAssets
        .flatMap { asset -> listOf(asset.managedUri to asset.assetId, asset.originalUri to asset.assetId) }
        .toMap()
    return tracks.map { track ->
        track.copy(clips = track.clips.map { clip -> attachMediaAssetIdToClip(clip, assetIdsByUri) })
    }
}

internal fun quickMediaAssetFingerprint(file: File): String? {
    if (!file.isFile || file.length() <= 0L) return null
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update(file.length().toString().toByteArray(Charsets.UTF_8))
    RandomAccessFile(file, "r").use { randomAccessFile ->
        updateDigestFrom(randomAccessFile, digest, 0L, minOf(file.length(), FINGERPRINT_WINDOW_BYTES.toLong()))
        if (file.length() > FINGERPRINT_WINDOW_BYTES) {
            val tailStart = maxOf(FINGERPRINT_WINDOW_BYTES.toLong(), file.length() - FINGERPRINT_WINDOW_BYTES)
            updateDigestFrom(randomAccessFile, digest, tailStart, file.length() - tailStart)
        }
    }
    return digest.digest().joinToString(separator = "") { "%02x".format(it) }
}

private fun attachMediaAssetIdToClip(
    clip: Clip,
    assetIdsByUri: Map<String, String>
): Clip {
    val assetId = clip.assetId ?: assetIdsByUri[clip.sourceUri.toString()]
    val children = clip.compoundClips.map { child ->
        attachMediaAssetIdToClip(child, assetIdsByUri)
    }
    return clip.copy(assetId = assetId, compoundClips = children)
}

private fun collectClipMediaAssetReferences(
    clip: Clip,
    mediaType: String,
    references: MutableList<MediaAssetReference>
) {
    references += MediaAssetReference(clip.sourceUri, mediaType)
    clip.compoundClips.forEach { child ->
        collectClipMediaAssetReferences(child, mediaType, references)
    }
}

private fun projectMediaAssetForReference(
    context: Context,
    managedDir: File,
    reference: MediaAssetReference
): ProjectMediaAsset {
    val managedFile = managedMediaFileForReference(reference.uri, managedDir)
    if (managedFile != null) {
        val sidecarAsset = readProjectMediaAssetSidecar(mediaAssetSidecarFileFor(managedFile))
        if (sidecarAsset != null) return sidecarAsset

        val record = buildMediaAssetRecord(
            context = context,
            managedFile = managedFile,
            managedUri = reference.uri,
            originalUri = reference.uri,
            mediaType = reference.mediaType,
            importedAtEpochMs = managedFile.lastModified().takeIf { it > 0L } ?: System.currentTimeMillis()
        )
        if (record != null) return record.toProjectMediaAsset()
    }

    return ProjectMediaAsset(
        assetId = assetIdForUri(reference.uri),
        managedUri = reference.uri.toString(),
        originalUri = reference.uri.toString(),
        displayName = reference.uri.lastPathSegment,
        mediaType = reference.mediaType,
        mimeType = null,
        sizeBytes = managedFile?.length() ?: 0L,
        durationMs = null,
        width = null,
        height = null,
        quickFingerprint = null,
        importStatus = if (reference.uri.scheme == "file") "missing" else "external",
        lastVerifiedAtEpochMs = System.currentTimeMillis()
    )
}

internal fun readProjectMediaAssetSidecar(sidecar: File): ProjectMediaAsset? {
    if (!sidecar.isFile || sidecar.length() <= 0L || sidecar.length() > MAX_MEDIA_ASSET_SIDECAR_BYTES) {
        return null
    }
    return runCatching {
        sidecar.inputStream().use { input ->
            projectMediaAssetFromJson(JSONObject(readUtf8WithByteLimit(input, MAX_MEDIA_ASSET_SIDECAR_BYTES)))
        }
    }.getOrNull()
}

internal fun projectMediaAssetFromJson(json: JSONObject): ProjectMediaAsset? {
    val assetId = json.optString("assetId", "").takeIf { it.isNotBlank() } ?: return null
    val managedUri = json.optString("managedUri", "").takeIf { it.isNotBlank() } ?: return null
    val originalUri = json.optString("originalUri", "").takeIf { it.isNotBlank() } ?: managedUri
    return ProjectMediaAsset(
        assetId = assetId,
        managedUri = managedUri,
        originalUri = originalUri,
        displayName = json.optNullableString("displayName"),
        mediaType = json.optString("mediaType", "video"),
        mimeType = json.optNullableString("mimeType"),
        sizeBytes = json.optLong("sizeBytes", 0L).coerceAtLeast(0L),
        durationMs = json.optNullableLong("durationMs"),
        width = json.optNullableInt("width"),
        height = json.optNullableInt("height"),
        quickFingerprint = json.optNullableString("quickFingerprint"),
        importStatus = json.optString("importStatus", "ready"),
        lastVerifiedAtEpochMs = json.optLong("lastVerifiedAtEpochMs", System.currentTimeMillis())
    )
}

private fun MediaAssetRecord.toProjectMediaAsset(): ProjectMediaAsset {
    return ProjectMediaAsset(
        assetId = assetId,
        managedUri = managedUri,
        originalUri = originalUri,
        displayName = displayName,
        mediaType = mediaType,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        durationMs = durationMs,
        width = width,
        height = height,
        quickFingerprint = quickFingerprint,
        importStatus = "ready",
        lastVerifiedAtEpochMs = lastVerifiedAtEpochMs
    )
}

private fun assetIdForUri(uri: Uri): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(uri.toString().toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { "%02x".format(it) }
    return "asset-${digest.take(24)}"
}

private fun managedMediaFileForReference(uri: Uri, managedDir: File): File? {
    if (uri.scheme != "file") return null
    val path = uri.path ?: return null
    val file = runCatching { File(path).canonicalFile }.getOrNull() ?: return null
    val root = runCatching { managedDir.canonicalFile }.getOrNull() ?: return null
    if (!file.isFile || file.length() <= 0L) return null
    if (!file.toPath().startsWith(root.toPath())) return null
    return file
}

private fun JSONObject.optNullableString(name: String): String? {
    if (!has(name) || isNull(name)) return null
    return optString(name).takeIf { it.isNotEmpty() }
}

private fun JSONObject.optNullableLong(name: String): Long? {
    if (!has(name) || isNull(name)) return null
    return optLong(name).takeIf { it >= 0L }
}

private fun JSONObject.optNullableInt(name: String): Int? {
    if (!has(name) || isNull(name)) return null
    return optInt(name).takeIf { it > 0 }
}

private fun buildMediaAssetRecord(
    context: Context,
    managedFile: File,
    managedUri: Uri,
    originalUri: Uri,
    mediaType: String,
    importedAtEpochMs: Long
): MediaAssetRecord? {
    if (!managedFile.isFile || managedFile.length() <= 0L) return null
    val fingerprint = quickMediaAssetFingerprint(managedFile) ?: return null
    val mediaMetadata = readMediaMetadata(context, managedUri)
    val imageMetadata = if (mediaType == "image") readImageMetadata(managedFile) else null
    return MediaAssetRecord(
        assetId = "asset-${fingerprint.take(24)}",
        managedUri = managedUri.toString(),
        originalUri = originalUri.toString(),
        displayName = resolveMediaDisplayName(context, originalUri) ?: managedFile.name,
        mediaType = mediaType,
        mimeType = resolveMimeType(context, originalUri, managedFile),
        sizeBytes = managedFile.length(),
        durationMs = mediaMetadata.durationMs,
        width = imageMetadata?.width ?: mediaMetadata.width,
        height = imageMetadata?.height ?: mediaMetadata.height,
        quickFingerprint = fingerprint,
        importedAtEpochMs = importedAtEpochMs,
        lastVerifiedAtEpochMs = System.currentTimeMillis()
    )
}

private data class MediaDimensions(
    val durationMs: Long? = null,
    val width: Int? = null,
    val height: Int? = null
)

private fun readMediaMetadata(context: Context, uri: Uri): MediaDimensions {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        MediaDimensions(
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.takeIf { it >= 0L },
            width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull()
                ?.takeIf { it > 0 },
            height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull()
                ?.takeIf { it > 0 }
        )
    } catch (_: Exception) {
        MediaDimensions()
    } finally {
        runCatching { retriever.release() }
    }
}

private fun readImageMetadata(file: File): MediaDimensions {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, options)
    return MediaDimensions(
        width = options.outWidth.takeIf { it > 0 },
        height = options.outHeight.takeIf { it > 0 }
    )
}

private fun resolveMimeType(context: Context, originalUri: Uri, managedFile: File): String? {
    return runCatching { context.contentResolver.getType(originalUri) }.getOrNull()
        ?: managedFile.extension
            .takeIf { it.isNotBlank() }
            ?.lowercase(Locale.US)
            ?.let { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it) }
}

private fun updateDigestFrom(
    randomAccessFile: RandomAccessFile,
    digest: MessageDigest,
    start: Long,
    length: Long
) {
    val buffer = ByteArray(64 * 1024)
    var remaining = length
    randomAccessFile.seek(start)
    while (remaining > 0L) {
        val read = randomAccessFile.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
        if (read <= 0) break
        digest.update(buffer, 0, read)
        remaining -= read
    }
}

private fun JSONObject.putNullable(name: String, value: Any?) {
    put(name, value ?: JSONObject.NULL)
}

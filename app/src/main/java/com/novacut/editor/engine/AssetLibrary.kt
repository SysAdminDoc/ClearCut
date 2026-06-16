package com.novacut.editor.engine

import android.content.Context
import android.net.Uri
import com.novacut.editor.model.Clip
import com.novacut.editor.model.ImageOverlay
import com.novacut.editor.model.Track
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AssetLibrary @Inject constructor(
    @ApplicationContext private val context: Context
) {

    data class AssetEntry(
        val sourceUri: Uri,
        val displayName: String,
        val referenceCount: Int,
        val clipIds: List<String>,
        val sizeBytes: Long,
        val mediaType: String,
        val isMissing: Boolean
    )

    data class DuplicateGroup(
        val sourceUri: Uri,
        val entries: List<AssetEntry>,
        val totalSizeBytes: Long
    )

    data class AssetPoolSummary(
        val totalAssets: Int,
        val totalSizeBytes: Long,
        val duplicateGroups: List<DuplicateGroup>,
        val missingAssets: List<AssetEntry>,
        val orphanedFiles: List<OrphanedFile>,
        val reclaimableBytes: Long
    )

    data class OrphanedFile(
        val file: File,
        val sizeBytes: Long,
        val directory: String
    )

    fun analyzeProject(
        tracks: List<Track>,
        imageOverlays: List<ImageOverlay> = emptyList(),
        mediaAssets: List<ProjectMediaAsset> = emptyList()
    ): AssetPoolSummary {
        val sourceMap = mutableMapOf<String, MutableList<Clip>>()
        for (track in tracks) {
            for (clip in track.clips) {
                val key = clip.sourceUri.toString()
                sourceMap.getOrPut(key) { mutableListOf() }.add(clip)
            }
        }

        val entries = mutableListOf<AssetEntry>()
        for ((uriString, clips) in sourceMap) {
            val uri = Uri.parse(uriString)
            val sizeBytes = resolveFileSize(uri, mediaAssets)
            val isMissing = !isAccessible(uri)
            val displayName = resolveDisplayName(uri, mediaAssets)
            entries.add(AssetEntry(
                sourceUri = uri,
                displayName = displayName,
                referenceCount = clips.size,
                clipIds = clips.map { it.id },
                sizeBytes = sizeBytes,
                mediaType = if (clips.any { it.sourceDurationMs > 0L }) "video" else "image",
                isMissing = isMissing
            ))
        }

        val duplicateGroups = entries
            .filter { it.referenceCount > 1 }
            .map { entry ->
                DuplicateGroup(
                    sourceUri = entry.sourceUri,
                    entries = listOf(entry),
                    totalSizeBytes = entry.sizeBytes * entry.referenceCount
                )
            }

        val managedMediaDir = managedMediaDir(context)
        val referencedUris = sourceMap.keys.toSet()
        val orphanedFiles = findOrphanedFiles(managedMediaDir, referencedUris, mediaAssets)

        return AssetPoolSummary(
            totalAssets = entries.size,
            totalSizeBytes = entries.sumOf { it.sizeBytes },
            duplicateGroups = duplicateGroups,
            missingAssets = entries.filter { it.isMissing },
            orphanedFiles = orphanedFiles,
            reclaimableBytes = orphanedFiles.sumOf { it.sizeBytes }
        )
    }

    fun bulkRelink(
        tracks: List<Track>,
        oldUri: Uri,
        newUri: Uri
    ): List<Track> {
        val oldKey = oldUri.toString()
        return tracks.map { track ->
            val updatedClips = track.clips.map { clip ->
                if (clip.sourceUri.toString() == oldKey) {
                    clip.copy(sourceUri = newUri)
                } else clip
            }
            if (updatedClips != track.clips) track.copy(clips = updatedClips) else track
        }
    }

    fun findOrphanedFiles(
        managedMediaDir: File,
        referencedUris: Set<String>,
        mediaAssets: List<ProjectMediaAsset> = emptyList()
    ): List<OrphanedFile> {
        if (!managedMediaDir.isDirectory) return emptyList()
        val assetUris = mediaAssets.map { it.managedUri }.toSet()
        val result = mutableListOf<OrphanedFile>()
        managedMediaDir.listFiles()?.forEach { file ->
            if (!file.isFile) return@forEach
            val fileUri = Uri.fromFile(file).toString()
            if (fileUri !in referencedUris && fileUri !in assetUris) {
                result.add(OrphanedFile(
                    file = file,
                    sizeBytes = file.length(),
                    directory = file.parentFile?.name ?: "unknown"
                ))
            }
        }
        return result
    }

    fun reclaimOrphaned(orphans: List<OrphanedFile>): Long {
        var reclaimed = 0L
        for (orphan in orphans) {
            if (orphan.file.delete()) {
                reclaimed += orphan.sizeBytes
            }
        }
        return reclaimed
    }

    private fun resolveFileSize(uri: Uri, assets: List<ProjectMediaAsset>): Long {
        val asset = assets.firstOrNull { it.managedUri == uri.toString() || it.originalUri == uri.toString() }
        if (asset != null && asset.sizeBytes > 0L) return asset.sizeBytes
        if (uri.scheme == "file") {
            val path = uri.path ?: return 0L
            val file = File(path)
            if (file.isFile) return file.length()
        }
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
        } catch (_: Exception) { 0L }
    }

    private fun resolveDisplayName(uri: Uri, assets: List<ProjectMediaAsset>): String {
        val asset = assets.firstOrNull { it.managedUri == uri.toString() || it.originalUri == uri.toString() }
        if (asset?.displayName != null) return asset.displayName
        return uri.lastPathSegment ?: uri.toString().takeLast(40)
    }

    private fun isAccessible(uri: Uri): Boolean {
        if (uri.scheme == "file") {
            val path = uri.path ?: return false
            return File(path).exists()
        }
        return try {
            context.contentResolver.openInputStream(uri)?.use { true } ?: false
        } catch (_: Exception) { false }
    }
}

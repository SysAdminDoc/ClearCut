package com.novacut.editor.engine

import android.content.Context
import android.net.Uri
import android.util.Log
import com.novacut.editor.model.Clip
import com.novacut.editor.model.ImageOverlay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.util.LinkedHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.json.JSONArray
import org.json.JSONObject

/**
 * Bundles a NovaCut project (state + media files) into a zip archive for backup/transfer.
 */
object ProjectArchive {

    private const val PROJECT_JSON_ENTRY = "project.json"
    private const val MEDIA_MANIFEST_ENTRY = "media_manifest.json"

    private data class ArchivedMediaSource(
        val originalUri: String,
        val uri: Uri,
        val entryName: String
    )

    /**
     * Export a project as a .novacut zip archive.
     * Includes the project JSON + all source media files.
     */
    suspend fun exportArchive(
        context: Context,
        state: AutoSaveState,
        outputFile: File,
        onProgress: (Float) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            outputFile.parentFile?.mkdirs()

            val projectJson = state.serialize()
            val archivedMedia = collectArchivedMedia(state)
            val mediaManifest = buildMediaManifest(archivedMedia)
            val totalFiles = archivedMedia.size + 2 // project.json + media manifest
            var processedFiles = 0

            ZipOutputStream(BufferedOutputStream(FileOutputStream(outputFile))).use { zip ->
                writeTextEntry(zip, PROJECT_JSON_ENTRY, projectJson)
                processedFiles++
                onProgress(processedFiles.toFloat() / totalFiles)

                writeTextEntry(zip, MEDIA_MANIFEST_ENTRY, mediaManifest)
                processedFiles++
                onProgress(processedFiles.toFloat() / totalFiles)

                archivedMedia.forEach { media ->
                    zip.putNextEntry(ZipEntry(media.entryName))
                    context.contentResolver.openInputStream(media.uri)?.use { input ->
                        input.copyTo(zip, bufferSize = 8192)
                    } ?: throw IOException("Cannot read media: ${media.uri}")
                    zip.closeEntry()
                    processedFiles++
                    onProgress(processedFiles.toFloat() / totalFiles)
                }
            }

            true
        } catch (e: Exception) {
            Log.e("ProjectArchive", "Archive export failed", e)
            outputFile.delete()
            false
        }
    }

    /**
     * Import a .novacut zip archive, extracting media files and returning the project state.
     */
    suspend fun importArchive(
        context: Context,
        archiveUri: Uri,
        targetDir: File
    ): AutoSaveState? = withContext(Dispatchers.IO) {
        val canonicalTargetDir = targetDir.canonicalFile
        val targetDirAlreadyExisted = canonicalTargetDir.exists()
        try {
            val inputStream = context.contentResolver.openInputStream(archiveUri) ?: return@withContext null
            if (!canonicalTargetDir.exists() && !canonicalTargetDir.mkdirs()) {
                Log.e("ProjectArchive", "Failed to create import directory: ${canonicalTargetDir.path}")
                return@withContext null
            }

            var projectJson: String? = null
            var mediaManifestJson: String? = null
            val extractedFiles = mutableMapOf<String, Uri>()

            inputStream.use {
                ZipInputStream(it).use { zipInput ->
                    var entry = zipInput.nextEntry
                    while (entry != null) {
                        when {
                            entry.isDirectory -> {
                                // No-op. Files create parents as needed.
                            }
                            entry.name == PROJECT_JSON_ENTRY -> {
                                projectJson = readCurrentEntryText(zipInput)
                            }
                            entry.name == MEDIA_MANIFEST_ENTRY -> {
                                mediaManifestJson = readCurrentEntryText(zipInput)
                            }
                            else -> {
                                val outFile = File(canonicalTargetDir, entry.name).canonicalFile
                                if (!outFile.path.startsWith(canonicalTargetDir.path + File.separator)) {
                                    Log.w("ProjectArchive", "Skipping zip entry with path traversal: ${entry.name}")
                                    zipInput.closeEntry()
                                    entry = zipInput.nextEntry
                                    continue
                                }
                                outFile.parentFile?.mkdirs()
                                outFile.outputStream().use { out ->
                                    zipInput.copyTo(out, bufferSize = 65536)
                                }
                                extractedFiles[entry.name] = Uri.fromFile(outFile)
                            }
                        }
                        zipInput.closeEntry()
                        entry = zipInput.nextEntry
                    }
                }
            }

            val stateJson = projectJson
            if (stateJson == null) {
                Log.e("ProjectArchive", "No $PROJECT_JSON_ENTRY in archive")
                if (!targetDirAlreadyExisted) {
                    canonicalTargetDir.deleteRecursively()
                }
                return@withContext null
            }

            val manifestMap = mediaManifestJson?.let(::parseMediaManifest).orEmpty()
            AutoSaveState.deserialize(stateJson)
                .rewriteArchivedMediaUris(manifestMap, extractedFiles)
        } catch (e: Exception) {
            Log.e("ProjectArchive", "Archive import failed", e)
            if (!targetDirAlreadyExisted) {
                canonicalTargetDir.deleteRecursively()
            }
            null
        }
    }

    /**
     * Get the estimated archive size in bytes.
     */
    suspend fun estimateArchiveSize(
        context: Context,
        state: AutoSaveState
    ): Long = withContext(Dispatchers.IO) {
        var totalSize = 0L
        val mediaUris = collectArchivedMedia(state).map { it.uri }

        for (uri in mediaUris) {
            try {
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { fd ->
                    totalSize += fd.length
                }
            } catch (e: Exception) {
                // Skip unreadable files
            }
        }

        totalSize + 4096 // Add overhead for project JSON
    }

    private fun collectArchivedMedia(state: AutoSaveState): List<ArchivedMediaSource> {
        val uniqueMedia = LinkedHashMap<String, Uri>()

        fun register(uri: Uri) {
            if (uri == Uri.EMPTY) return
            val key = uri.toString()
            if (key.isBlank()) return
            uniqueMedia.putIfAbsent(key, uri)
        }

        fun registerClip(clip: Clip) {
            register(clip.sourceUri)
            clip.compoundClips.forEach(::registerClip)
        }

        state.tracks.forEach { track ->
            track.clips.forEach(::registerClip)
        }
        state.imageOverlays.forEach { overlay: ImageOverlay ->
            register(overlay.sourceUri)
        }

        return uniqueMedia.entries.mapIndexed { index, (originalUri, uri) ->
            val entryName = "media/${index}_${sanitizeFileNamePreservingExtension(
                raw = uri.lastPathSegment ?: "media_$index",
                fallbackStem = "media_$index"
            )}"
            ArchivedMediaSource(
                originalUri = originalUri,
                uri = uri,
                entryName = entryName
            )
        }
    }

    private fun buildMediaManifest(mediaSources: List<ArchivedMediaSource>): String {
        return JSONObject().apply {
            put("version", 1)
            put("entries", JSONArray().apply {
                mediaSources.forEach { media ->
                    put(JSONObject().apply {
                        put("originalUri", media.originalUri)
                        put("entryName", media.entryName)
                    })
                }
            })
        }.toString(2)
    }

    private fun parseMediaManifest(raw: String): Map<String, String> {
        val json = JSONObject(raw)
        val entries = json.optJSONArray("entries") ?: JSONArray()
        return buildMap {
            for (index in 0 until entries.length()) {
                val item = entries.optJSONObject(index) ?: continue
                val originalUri = item.optString("originalUri", "")
                val entryName = item.optString("entryName", "")
                if (originalUri.isNotBlank() && entryName.isNotBlank()) {
                    put(originalUri, entryName)
                }
            }
        }
    }

    private fun writeTextEntry(zip: ZipOutputStream, entryName: String, text: String) {
        zip.putNextEntry(ZipEntry(entryName))
        zip.write(text.toByteArray())
        zip.closeEntry()
    }

    private fun readCurrentEntryText(zipInput: ZipInputStream): String {
        val buffer = ByteArrayOutputStream()
        zipInput.copyTo(buffer, bufferSize = 8192)
        return buffer.toString(Charsets.UTF_8.name())
    }

    private fun AutoSaveState.rewriteArchivedMediaUris(
        manifestEntryMap: Map<String, String>,
        extractedFiles: Map<String, Uri>
    ): AutoSaveState {
        fun resolveArchivedUri(originalUri: Uri): Uri {
            val mappedEntry = manifestEntryMap[originalUri.toString()]
            if (mappedEntry != null) {
                extractedFiles[mappedEntry]?.let { return it }
            }

            return fallbackArchivedUri(originalUri, extractedFiles) ?: originalUri
        }

        fun rewriteClip(clip: Clip): Clip {
            return clip.copy(
                sourceUri = resolveArchivedUri(clip.sourceUri),
                proxyUri = null,
                compoundClips = clip.compoundClips.map(::rewriteClip)
            )
        }

        return copy(
            tracks = tracks.map { track ->
                track.copy(clips = track.clips.map(::rewriteClip))
            },
            imageOverlays = imageOverlays.map { overlay ->
                overlay.copy(sourceUri = resolveArchivedUri(overlay.sourceUri))
            }
        )
    }

    private fun fallbackArchivedUri(
        originalUri: Uri,
        extractedFiles: Map<String, Uri>
    ): Uri? {
        val originalName = originalUri.lastPathSegment?.takeIf { it.isNotBlank() } ?: return null
        val sanitizedOriginalName = sanitizeFileNamePreservingExtension(
            raw = originalName,
            fallbackStem = originalName
        )

        return extractedFiles.entries.firstNotNullOfOrNull { (entryName, uri) ->
            val archivedName = entryName.substringAfterLast('/').substringAfter('_', entryName.substringAfterLast('/'))
            when {
                archivedName == originalName -> uri
                archivedName == sanitizedOriginalName -> uri
                else -> null
            }
        }
    }
}

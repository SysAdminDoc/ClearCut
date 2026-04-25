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
    private const val MAX_ARCHIVE_ENTRY_COUNT = 4_096
    private const val MAX_ARCHIVE_TEXT_ENTRY_BYTES = 5_000_000L
    private const val MAX_ARCHIVE_TOTAL_BYTES = 4L * 1024L * 1024L * 1024L

    /**
     * How to handle the situation where the archive's project ID already exists
     * locally. The default is [REGENERATE] — safest and matches the user
     * expectation of "import as a copy".
     */
    enum class IdCollisionPolicy {
        /** Mint a new UUID for the imported project. */
        REGENERATE,

        /** Keep the original ID, even if it overwrites an existing project. */
        KEEP
    }

    /**
     * Diagnostic detail attached to every import attempt. Surfaced to the user
     * via the post-import sheet — historically the importer dropped media or
     * silently truncated newer-schema archives, so the report exists to make
     * those problems impossible to miss.
     */
    data class ImportReport(
        val schemaVersion: Int,
        val schemaTooNew: Boolean,
        val originalProjectId: String?,
        val effectiveProjectId: String?,
        val projectIdCollided: Boolean,
        val idCollisionPolicy: IdCollisionPolicy,
        val mediaTotal: Int,
        val mediaResolved: Int,
        val unresolvedMediaUris: List<String>,
        val warnings: List<String>,
        val targetDirCreated: Boolean
    ) {
        val mediaMissing: Int get() = mediaTotal - mediaResolved
        val canProceed: Boolean get() = !schemaTooNew
        val summary: String get() = buildString {
            append("schema v$schemaVersion")
            if (schemaTooNew) append(" (too new)")
            if (mediaMissing > 0) append(" · $mediaMissing missing media")
            if (projectIdCollided) append(" · ID collision (${idCollisionPolicy.name.lowercase()})")
            if (warnings.isNotEmpty()) append(" · ${warnings.size} warning(s)")
        }
    }

    /**
     * Outcome of [importArchiveWithReport]. The state is non-null only when the
     * archive was structurally valid; the [report] is populated either way.
     */
    data class ImportResult(
        val state: AutoSaveState?,
        val report: ImportReport,
        val errorMessage: String? = null
    )

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
        val targetFile = outputFile.absoluteFile
        val parentDir = targetFile.parentFile
        val tempFile = try {
            if (parentDir != null && !parentDir.exists() && !parentDir.mkdirs() && !parentDir.exists()) {
                throw IOException("Failed to create archive directory: ${parentDir.absolutePath}")
            }
            File.createTempFile("${targetFile.name}.", ".tmp", parentDir)
        } catch (e: Exception) {
            Log.e("ProjectArchive", "Archive export failed before writing", e)
            return@withContext false
        }

        try {
            val projectJson = state.serialize()
            val archivedMedia = collectArchivedMedia(state)
            val mediaManifest = buildMediaManifest(archivedMedia)
            val totalFiles = archivedMedia.size + 2 // project.json + media manifest
            var processedFiles = 0

            ZipOutputStream(BufferedOutputStream(FileOutputStream(tempFile))).use { zip ->
                writeTextEntry(zip, PROJECT_JSON_ENTRY, projectJson)
                processedFiles++
                onProgress(processedFiles.toFloat() / totalFiles)

                writeTextEntry(zip, MEDIA_MANIFEST_ENTRY, mediaManifest)
                processedFiles++
                onProgress(processedFiles.toFloat() / totalFiles)

                var writtenMediaBytes = 0L
                archivedMedia.forEach { media ->
                    zip.putNextEntry(ZipEntry(media.entryName))
                    context.contentResolver.openInputStream(media.uri)?.use { input ->
                        val remainingBytes = MAX_ARCHIVE_TOTAL_BYTES - writtenMediaBytes
                        if (remainingBytes <= 0L) {
                            throw IOException("Archive exceeds size limit")
                        }
                        writtenMediaBytes += copyWithLimit(input, zip, remainingBytes)
                    } ?: throw IOException("Cannot read media: ${media.uri}")
                    zip.closeEntry()
                    processedFiles++
                    onProgress(processedFiles.toFloat() / totalFiles)
                }
            }
            moveFileReplacing(tempFile, targetFile)

            true
        } catch (e: Exception) {
            Log.e("ProjectArchive", "Archive export failed", e)
            tempFile.delete()
            false
        }
    }

    /**
     * Backwards-compatible thin wrapper around [importArchiveWithReport] for
     * callers that only need the resulting state. New code should prefer the
     * report-returning variant so missing media and schema drift are surfaced.
     */
    suspend fun importArchive(
        context: Context,
        archiveUri: Uri,
        targetDir: File
    ): AutoSaveState? = importArchiveWithReport(
        context = context,
        archiveUri = archiveUri,
        targetDir = targetDir,
        existingProjectIds = emptySet(),
        idCollisionPolicy = IdCollisionPolicy.REGENERATE
    ).state

    /**
     * Import a .novacut zip archive and produce a structured [ImportResult]
     * with diagnostics for the UI.
     *
     * @param existingProjectIds caller-supplied set used to detect ID
     *     collisions. Empty by default — callers that intend to persist the
     *     imported project should query [com.novacut.editor.engine.db.ProjectDao]
     *     and pass the snapshot.
     * @param idCollisionPolicy how to react when the archive's project ID is
     *     already present in [existingProjectIds].
     */
    suspend fun importArchiveWithReport(
        context: Context,
        archiveUri: Uri,
        targetDir: File,
        existingProjectIds: Set<String> = emptySet(),
        idCollisionPolicy: IdCollisionPolicy = IdCollisionPolicy.REGENERATE
    ): ImportResult = withContext(Dispatchers.IO) {
        val canonicalTargetDir = targetDir.canonicalFile
        val targetDirAlreadyExisted = canonicalTargetDir.exists()
        val extractedPaths = mutableListOf<File>()
        val warnings = mutableListOf<String>()

        try {
            if (!canonicalTargetDir.exists() && !canonicalTargetDir.mkdirs()) {
                Log.e("ProjectArchive", "Failed to create import directory: ${canonicalTargetDir.path}")
                return@withContext ImportResult(
                    state = null,
                    report = blankFailureReport(idCollisionPolicy),
                    errorMessage = "Failed to create import directory"
                )
            }

            val inputStream = context.contentResolver.openInputStream(archiveUri)
                ?: return@withContext ImportResult(
                    state = null,
                    report = blankFailureReport(idCollisionPolicy),
                    errorMessage = "Could not open archive"
                )

            var projectJson: String? = null
            var mediaManifestJson: String? = null
            val extractedFiles = mutableMapOf<String, Uri>()
            val seenEntries = hashSetOf<String>()
            val seenOutputPaths = hashSetOf<String>()
            var entryCount = 0
            var extractedBytes = 0L

            inputStream.use {
                ZipInputStream(it).use { zipInput ->
                    var entry = zipInput.nextEntry
                    while (entry != null) {
                        entryCount++
                        if (entryCount > MAX_ARCHIVE_ENTRY_COUNT) {
                            throw IOException("Archive contains too many entries")
                        }
                        if (!seenEntries.add(entry.name)) {
                            throw IOException("Archive contains duplicate entry: ${entry.name}")
                        }
                        when {
                            entry.isDirectory -> {
                                // No-op. Files create parents as needed.
                            }
                            entry.name == PROJECT_JSON_ENTRY -> {
                                projectJson = readCurrentEntryText(zipInput, MAX_ARCHIVE_TEXT_ENTRY_BYTES)
                            }
                            entry.name == MEDIA_MANIFEST_ENTRY -> {
                                mediaManifestJson = readCurrentEntryText(zipInput, MAX_ARCHIVE_TEXT_ENTRY_BYTES)
                            }
                            else -> {
                                if (!isSupportedMediaEntry(entry.name)) {
                                    Log.w("ProjectArchive", "Skipping unsupported archive entry: ${entry.name}")
                                    warnings += "Skipped unsupported entry: ${entry.name}"
                                    zipInput.closeEntry()
                                    entry = zipInput.nextEntry
                                    continue
                                }
                                val outFile = File(canonicalTargetDir, entry.name).canonicalFile
                                val targetPath = canonicalTargetDir.toPath()
                                if (!outFile.toPath().startsWith(targetPath)) {
                                    Log.w("ProjectArchive", "Skipping zip entry with path traversal: ${entry.name}")
                                    warnings += "Skipped path-traversal entry: ${entry.name}"
                                    zipInput.closeEntry()
                                    entry = zipInput.nextEntry
                                    continue
                                }
                                if (!seenOutputPaths.add(outFile.path)) {
                                    throw IOException("Archive maps multiple entries to the same file: ${entry.name}")
                                }
                                outFile.parentFile?.mkdirs()
                                outFile.outputStream().use { out ->
                                    val remainingBytes = MAX_ARCHIVE_TOTAL_BYTES - extractedBytes
                                    if (remainingBytes <= 0L) {
                                        throw IOException("Archive exceeds size limit")
                                    }
                                    extractedBytes += copyWithLimit(zipInput, out, remainingBytes)
                                }
                                extractedPaths += outFile
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
                cleanupPartialImport(canonicalTargetDir, extractedPaths, targetDirAlreadyExisted)
                return@withContext ImportResult(
                    state = null,
                    report = blankFailureReport(idCollisionPolicy),
                    errorMessage = "Archive missing $PROJECT_JSON_ENTRY"
                )
            }

            val schemaVersion = parseSchemaVersion(stateJson)
            val schemaTooNew = schemaVersion > AutoSaveState.FORMAT_VERSION
            if (schemaTooNew) {
                Log.w(
                    "ProjectArchive",
                    "Archive schema v$schemaVersion is newer than supported v${AutoSaveState.FORMAT_VERSION}; refusing best-effort load"
                )
                warnings += "Archive uses schema v$schemaVersion; this build supports up to v${AutoSaveState.FORMAT_VERSION}."
                cleanupPartialImport(canonicalTargetDir, extractedPaths, targetDirAlreadyExisted)
                return@withContext ImportResult(
                    state = null,
                    report = ImportReport(
                        schemaVersion = schemaVersion,
                        schemaTooNew = true,
                        originalProjectId = parseProjectId(stateJson),
                        effectiveProjectId = null,
                        projectIdCollided = false,
                        idCollisionPolicy = idCollisionPolicy,
                        mediaTotal = 0,
                        mediaResolved = 0,
                        unresolvedMediaUris = emptyList(),
                        warnings = warnings,
                        targetDirCreated = !targetDirAlreadyExisted
                    ),
                    errorMessage = "Archive schema is newer than this app supports"
                )
            }
            if (schemaVersion < AutoSaveState.FORMAT_VERSION) {
                warnings += "Archive used schema v$schemaVersion; migrated to v${AutoSaveState.FORMAT_VERSION}."
            }

            val manifestMap = mediaManifestJson?.let(::parseMediaManifest).orEmpty()
            val rawState = AutoSaveState.deserialize(stateJson)
            val originalProjectId = rawState.projectId
            val collided = originalProjectId in existingProjectIds
            val effectiveProjectId = when {
                collided && idCollisionPolicy == IdCollisionPolicy.REGENERATE ->
                    java.util.UUID.randomUUID().toString()
                else -> originalProjectId
            }
            if (collided) {
                warnings += if (idCollisionPolicy == IdCollisionPolicy.REGENERATE) {
                    "Project ID '$originalProjectId' already existed; assigned new ID '$effectiveProjectId'."
                } else {
                    "Project ID '$originalProjectId' overwrites an existing project (kept by policy)."
                }
            }

            val unresolved = mutableListOf<String>()
            val seenSourceUris = LinkedHashSet<String>()
            val rewritten = rawState.copy(projectId = effectiveProjectId)
                .rewriteArchivedMediaUris(manifestMap, extractedFiles, seenSourceUris, unresolved)

            val mediaTotal = seenSourceUris.size
            val mediaResolved = mediaTotal - unresolved.size

            ImportResult(
                state = rewritten,
                report = ImportReport(
                    schemaVersion = schemaVersion,
                    schemaTooNew = false,
                    originalProjectId = originalProjectId,
                    effectiveProjectId = effectiveProjectId,
                    projectIdCollided = collided,
                    idCollisionPolicy = idCollisionPolicy,
                    mediaTotal = mediaTotal,
                    mediaResolved = mediaResolved,
                    unresolvedMediaUris = unresolved,
                    warnings = warnings,
                    targetDirCreated = !targetDirAlreadyExisted
                ),
                errorMessage = null
            )
        } catch (e: Exception) {
            Log.e("ProjectArchive", "Archive import failed", e)
            cleanupPartialImport(canonicalTargetDir, extractedPaths, targetDirAlreadyExisted)
            ImportResult(
                state = null,
                report = blankFailureReport(idCollisionPolicy),
                errorMessage = e.message ?: e.javaClass.simpleName
            )
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
                    if (fd.length > 0L) {
                        totalSize += fd.length
                    }
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

    private fun parseSchemaVersion(raw: String): Int {
        return runCatching { JSONObject(raw).optInt("version", 0) }.getOrDefault(0)
    }

    private fun parseProjectId(raw: String): String? {
        return runCatching { JSONObject(raw).optString("projectId", "") }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
    }

    private fun blankFailureReport(policy: IdCollisionPolicy): ImportReport = ImportReport(
        schemaVersion = 0,
        schemaTooNew = false,
        originalProjectId = null,
        effectiveProjectId = null,
        projectIdCollided = false,
        idCollisionPolicy = policy,
        mediaTotal = 0,
        mediaResolved = 0,
        unresolvedMediaUris = emptyList(),
        warnings = emptyList(),
        targetDirCreated = false
    )

    private fun writeTextEntry(zip: ZipOutputStream, entryName: String, text: String) {
        zip.putNextEntry(ZipEntry(entryName))
        zip.write(text.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun isSupportedMediaEntry(entryName: String): Boolean {
        if (!entryName.startsWith("media/")) return false
        if ('\\' in entryName) return false
        if (entryName.endsWith('/')) return false
        return entryName.substringAfter("media/").isNotBlank()
    }

    private fun readCurrentEntryText(zipInput: ZipInputStream, maxBytes: Long): String {
        return readUtf8WithByteLimit(zipInput, maxBytes)
    }

    private fun AutoSaveState.rewriteArchivedMediaUris(
        manifestEntryMap: Map<String, String>,
        extractedFiles: Map<String, Uri>,
        seenSourceUris: MutableSet<String>,
        unresolvedSink: MutableList<String>
    ): AutoSaveState {
        fun resolveArchivedUri(originalUri: Uri): Uri {
            val key = originalUri.toString()
            val isFresh = key.isNotBlank() && seenSourceUris.add(key)

            val mappedEntry = manifestEntryMap[key]
            if (mappedEntry != null) {
                extractedFiles[mappedEntry]?.let { return it }
            }
            val fallback = fallbackArchivedUri(originalUri, extractedFiles)
            if (fallback != null) return fallback

            if (isFresh) unresolvedSink += key
            return originalUri
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

    private fun cleanupPartialImport(
        canonicalTargetDir: File,
        extractedPaths: List<File>,
        targetDirAlreadyExisted: Boolean
    ) {
        extractedPaths
            .sortedByDescending { it.absolutePath.length }
            .forEach { extracted ->
                runCatching { extracted.delete() }
            }

        if (!targetDirAlreadyExisted) {
            canonicalTargetDir.deleteRecursively()
            return
        }

        extractedPaths
            .mapNotNull { it.parentFile }
            .distinct()
            .sortedByDescending { it.absolutePath.length }
            .forEach { directory ->
                if (directory != canonicalTargetDir && directory.exists() && directory.list().isNullOrEmpty()) {
                    runCatching { directory.delete() }
                }
            }
    }
}

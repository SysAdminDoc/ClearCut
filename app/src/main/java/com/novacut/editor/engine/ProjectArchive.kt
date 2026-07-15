package com.novacut.editor.engine

import android.content.Context
import android.net.Uri
import android.os.StatFs
import android.util.Log
import com.novacut.editor.model.Clip
import com.novacut.editor.model.ImageOverlay
import com.novacut.editor.model.Watermark
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.security.MessageDigest
import java.util.LinkedHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.json.JSONArray
import org.json.JSONObject

/**
 * Bundles a ClearCut project (state + media files) into a zip archive for backup/transfer.
 */
object ProjectArchive {

    private const val PROJECT_JSON_ENTRY = "project.json"
    private const val MEDIA_MANIFEST_ENTRY = "media_manifest.json"
    internal const val MAX_ARCHIVE_ENTRY_COUNT = 4_096
    internal const val MAX_ARCHIVE_TEXT_ENTRY_BYTES = 5_000_000L
    internal const val MAX_ARCHIVE_TOTAL_BYTES = 4L * 1024L * 1024L * 1024L
    internal const val MAX_ARCHIVE_COMPRESSION_RATIO = 200L
    private const val MAX_ARCHIVE_ENTRY_NAME_CHARS = 512
    private const val ARCHIVE_STORAGE_RESERVE_BYTES = 128L * 1024L * 1024L
    private const val STORAGE_RECHECK_INTERVAL_BYTES = 8L * 1024L * 1024L

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

    /** Metadata-only validation result used by incoming-document preview. */
    data class PreviewResult(
        val report: ImportReport,
        val packagedMedia: Int,
        val errorMessage: String? = null
    ) {
        val valid: Boolean get() = errorMessage == null && report.canProceed
    }

    internal data class ArchiveEntryMetadata(
        val name: String,
        val isDirectory: Boolean,
        val size: Long,
        val compressedSize: Long
    )

    internal data class ArchiveImportPlan(
        val expandedBytes: Long,
        val extractableEntryCount: Int
    )

    internal data class ArchiveManifestEntry(
        val kind: String,
        val logicalReference: String,
        val entryName: String?,
        val byteLength: Long?,
        val sha256: String?,
        val archivePolicy: String,
        val required: Boolean,
        val fallbackAllowed: Boolean = false,
        val fallbackName: String? = null
    )

    private data class ArchivedDependencySource(
        val manifest: ArchiveManifestEntry,
        val uri: Uri? = null,
        val file: File? = null
    )

    /**
     * Export a project as a .clearcut zip archive.
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
            val archivedDependencies = collectArchivedDependencies(context, state).map { source ->
                if (source.manifest.archivePolicy != ProjectDependencyArchivePolicy.INCLUDE.name) return@map source
                try {
                    val (byteLength, sha256) = inspectDependency(context, source)
                    source.copy(manifest = source.manifest.copy(byteLength = byteLength, sha256 = sha256))
                } catch (e: Exception) {
                    if (source.manifest.required) throw e
                    source.copy(manifest = source.manifest.copy(
                        entryName = null,
                        archivePolicy = ProjectDependencyArchivePolicy.REFERENCE_ONLY.name,
                        fallbackAllowed = true,
                        fallbackName = "Original reference"
                    ))
                }
            }
            val mediaManifest = buildMediaManifestV2(archivedDependencies.map { it.manifest })
            val includedDependencies = archivedDependencies.filter {
                it.manifest.archivePolicy == ProjectDependencyArchivePolicy.INCLUDE.name
            }
            val totalFiles = includedDependencies.size + 2 // project.json + manifest
            var processedFiles = 0

            ZipOutputStream(BufferedOutputStream(FileOutputStream(tempFile))).use { zip ->
                writeTextEntry(zip, PROJECT_JSON_ENTRY, projectJson)
                processedFiles++
                onProgress(processedFiles.toFloat() / totalFiles)

                writeTextEntry(zip, MEDIA_MANIFEST_ENTRY, mediaManifest)
                processedFiles++
                onProgress(processedFiles.toFloat() / totalFiles)

                var writtenDependencyBytes = 0L
                includedDependencies.forEach { dependency ->
                    val entryName = dependency.manifest.entryName
                        ?: throw IOException("Included dependency has no archive entry")
                    zip.putNextEntry(ZipEntry(entryName))
                    openDependency(context, dependency).use { input ->
                        val remainingBytes = MAX_ARCHIVE_TOTAL_BYTES - writtenDependencyBytes
                        if (remainingBytes <= 0L) {
                            throw IOException("Archive exceeds size limit")
                        }
                        val (copied, sha256) = copyWithLimitAndDigest(input, zip, remainingBytes)
                        if (copied != dependency.manifest.byteLength ||
                            !sha256.equals(dependency.manifest.sha256, ignoreCase = true)
                        ) {
                            throw IOException("Dependency changed while archiving: ${dependency.manifest.logicalReference}")
                        }
                        writtenDependencyBytes += copied
                    }
                    zip.closeEntry()
                    processedFiles++
                    onProgress(processedFiles.toFloat() / totalFiles)
                }
            }
            moveFileReplacing(tempFile, targetFile)

            true
        } catch (e: CancellationException) {
            // Cooperative cancellation must propagate so the caller's scope
            // tears down — swallowing it would leave the UI thinking the export
            // is still in progress while the coroutine has actually died.
            tempFile.delete()
            throw e
        } catch (e: Exception) {
            Log.e("ProjectArchive", "Archive export failed", e)
            tempFile.delete()
            false
        }
    }

    /**
     * Validate only the bounded project/manifest metadata at the front of an
     * archive. This path intentionally stops before the first payload entry:
     * an incoming-document preview must never extract or copy project media.
     */
    suspend fun previewArchive(
        context: Context,
        archiveUri: Uri
    ): PreviewResult = withContext(Dispatchers.IO) {
        val warnings = mutableListOf<String>()
        try {
            val input = context.contentResolver.openInputStream(archiveUri)
                ?: return@withContext PreviewResult(
                    report = blankFailureReport(IdCollisionPolicy.REGENERATE),
                    packagedMedia = 0,
                    errorMessage = "Could not open archive"
                )
            var projectJson: String? = null
            var mediaManifestJson: String? = null
            val seenEntries = hashSetOf<String>()
            var entryCount = 0

            input.use { stream ->
                ZipInputStream(BufferedInputStream(stream)).use { zip ->
                    metadata@ while (true) {
                        val entry = zip.nextEntry ?: break
                        entryCount++
                        if (entryCount > MAX_ARCHIVE_ENTRY_COUNT) {
                            throw IOException("Archive contains too many entries")
                        }
                        validateArchiveEntryName(entry.name)
                        if (!seenEntries.add(entry.name)) {
                            throw IOException("Archive contains duplicate entry: ${entry.name}")
                        }
                        when {
                            entry.isDirectory -> Unit
                            entry.name == PROJECT_JSON_ENTRY -> {
                                rejectKnownOversizedEntry(entry, MAX_ARCHIVE_TEXT_ENTRY_BYTES)
                                projectJson = readCurrentEntryText(zip, MAX_ARCHIVE_TEXT_ENTRY_BYTES)
                            }
                            entry.name == MEDIA_MANIFEST_ENTRY -> {
                                rejectKnownOversizedEntry(entry, MAX_ARCHIVE_TEXT_ENTRY_BYTES)
                                mediaManifestJson = readCurrentEntryText(zip, MAX_ARCHIVE_TEXT_ENTRY_BYTES)
                            }
                            else -> {
                                if (projectJson == null) {
                                    throw IOException("Archive metadata must precede payload entries")
                                }
                                break@metadata
                            }
                        }
                        zip.closeEntry()
                        if (projectJson != null && mediaManifestJson != null) break@metadata
                    }
                }
            }

            val stateJson = projectJson ?: throw IOException("Archive missing $PROJECT_JSON_ENTRY")
            val schemaVersion = parseSchemaVersion(stateJson)
            val originalProjectId = parseProjectId(stateJson)
            if (schemaVersion > AutoSaveState.FORMAT_VERSION) {
                warnings += "Archive uses schema v$schemaVersion; this build supports up to v${AutoSaveState.FORMAT_VERSION}."
                return@withContext PreviewResult(
                    report = ImportReport(
                        schemaVersion = schemaVersion,
                        schemaTooNew = true,
                        originalProjectId = originalProjectId,
                        effectiveProjectId = null,
                        projectIdCollided = false,
                        idCollisionPolicy = IdCollisionPolicy.REGENERATE,
                        mediaTotal = 0,
                        mediaResolved = 0,
                        unresolvedMediaUris = emptyList(),
                        warnings = warnings,
                        targetDirCreated = false
                    ),
                    packagedMedia = 0,
                    errorMessage = "Archive schema is newer than this app supports"
                )
            }
            if (schemaVersion < AutoSaveState.FORMAT_VERSION) {
                warnings += "Archive used schema v$schemaVersion; import will migrate it to v${AutoSaveState.FORMAT_VERSION}."
            }

            val rawState = AutoSaveState.deserialize(stateJson)
            val manifest = mediaManifestJson?.let(::parseMediaManifest)
            if (manifest != null) validateManifestMetadata(manifest, warnings)
            val mediaEntries = manifest?.entries.orEmpty()
                .filter { it.kind == ProjectDependencyKind.MEDIA.name }
            val mediaTotal = if (manifest != null) mediaEntries.size else collectLegacyMedia(rawState).size
            val packagedMedia = if (manifest != null) {
                mediaEntries.count {
                    it.archivePolicy == ProjectDependencyArchivePolicy.INCLUDE.name && it.entryName != null
                }
            } else {
                warnings += "Legacy archive payload inventory will be verified during intentional import."
                0
            }
            warnings += "Payload bytes and checksums are verified only during intentional import."

            PreviewResult(
                report = ImportReport(
                    schemaVersion = schemaVersion,
                    schemaTooNew = false,
                    originalProjectId = rawState.projectId,
                    effectiveProjectId = rawState.projectId,
                    projectIdCollided = false,
                    idCollisionPolicy = IdCollisionPolicy.REGENERATE,
                    mediaTotal = mediaTotal,
                    mediaResolved = packagedMedia.coerceAtMost(mediaTotal),
                    unresolvedMediaUris = emptyList(),
                    warnings = warnings,
                    targetDirCreated = false
                ),
                packagedMedia = packagedMedia,
                errorMessage = null
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("ProjectArchive", "Archive metadata preview failed", e)
            PreviewResult(
                report = blankFailureReport(IdCollisionPolicy.REGENERATE),
                packagedMedia = 0,
                errorMessage = e.message ?: e.javaClass.simpleName
            )
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
     * Import a .clearcut zip archive and produce a structured [ImportResult]
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
        val warnings = mutableListOf<String>()
        var stagedArchive: File? = null
        var extractionStage: File? = null
        var installedTarget = false
        val newlyInstalledFonts = mutableListOf<File>()

        try {
            if (canonicalTargetDir.exists()) {
                throw IOException("Import destination already exists")
            }
            val destinationParent = canonicalTargetDir.parentFile
                ?: throw IOException("Import destination has no parent directory")
            if (!destinationParent.exists() && !destinationParent.mkdirs() && !destinationParent.exists()) {
                throw IOException("Failed to create import parent directory")
            }

            val staged = stageArchiveForImport(context, archiveUri)
            stagedArchive = staged
            ZipFile(staged).use { zip ->
                val plan = planArchiveImport(zip.entries().asSequence().map { entry ->
                    ArchiveEntryMetadata(
                        name = entry.name,
                        isDirectory = entry.isDirectory,
                        size = entry.size,
                        compressedSize = entry.compressedSize
                    )
                }.toList())
                ensureStorageAvailable(
                    directory = destinationParent,
                    payloadBytes = plan.expandedBytes,
                    purpose = "archive import destination"
                )
                extractionStage = createExtractionStagingDir(canonicalTargetDir)
                val extracted = extractArchiveToStage(
                    zip = zip,
                    plan = plan,
                    stagingDir = requireNotNull(extractionStage),
                    warnings = warnings
                )
                val stateJson = extracted.projectJson
                    ?: throw IOException("Archive missing $PROJECT_JSON_ENTRY")

                val schemaVersion = parseSchemaVersion(stateJson)
                val schemaTooNew = schemaVersion > AutoSaveState.FORMAT_VERSION
                if (schemaTooNew) {
                    Log.w(
                        "ProjectArchive",
                        "Archive schema v$schemaVersion is newer than supported v${AutoSaveState.FORMAT_VERSION}; refusing best-effort load"
                    )
                    warnings += "Archive uses schema v$schemaVersion; this build supports up to v${AutoSaveState.FORMAT_VERSION}."
                    extractionStage?.deleteRecursively()
                    extractionStage = null
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
                            targetDirCreated = false
                        ),
                        errorMessage = "Archive schema is newer than this app supports"
                    )
                }
                if (schemaVersion < AutoSaveState.FORMAT_VERSION) {
                    warnings += "Archive used schema v$schemaVersion; migrated to v${AutoSaveState.FORMAT_VERSION}."
                }

                val stagedUris = extracted.files.mapValues { Uri.fromFile(it.value) }.toMutableMap()
                val parsedManifest = extracted.mediaManifestJson?.let(::parseMediaManifest)
                    ?: ParsedArchiveManifest(version = 1, entries = emptyList())
                val invalidOptionalEntries = verifyManifestEntries(parsedManifest, stagedUris, warnings)
                invalidOptionalEntries.forEach { entryName ->
                    stagedUris.remove(entryName)?.path?.let(::File)?.delete()
                }
                val trustedManifestEntries = parsedManifest.entries
                    .filter { it.entryName !in invalidOptionalEntries }
                val manifestMap = trustedManifestEntries
                    .filter { it.kind == ProjectDependencyKind.MEDIA.name && it.entryName != null }
                    .associate { it.logicalReference to requireNotNull(it.entryName) }
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

                moveFileReplacing(requireNotNull(extractionStage), canonicalTargetDir)
                extractionStage = null
                installedTarget = true
                val finalFiles = stagedUris.keys.associateWith { entryName ->
                    Uri.fromFile(File(canonicalTargetDir, entryName).canonicalFile)
                }
                val unresolved = mutableListOf<String>()
                val seenSourceUris = LinkedHashSet<String>()
                val rewritten = rewriteArchivedMediaUrisForImport(
                    state = rawState.copy(projectId = effectiveProjectId),
                    manifestEntryMap = manifestMap,
                    extractedFiles = finalFiles,
                    seenSourceUris = seenSourceUris,
                    unresolvedSink = unresolved,
                    allowFileNameFallback = parsedManifest.version <= 1
                )
                val dependencyRewrite = if (parsedManifest.version >= 2) {
                    rewriteArchivedDependenciesForImport(
                        context,
                        rewritten,
                        trustedManifestEntries,
                        finalFiles
                    )
                } else {
                    DependencyRewriteResult(rewritten, emptyList())
                }
                newlyInstalledFonts += dependencyRewrite.newlyInstalledFonts

                val mediaTotal = seenSourceUris.size
                val mediaResolved = mediaTotal - unresolved.size
                return@withContext ImportResult(
                    state = dependencyRewrite.state,
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
                        targetDirCreated = true
                    ),
                    errorMessage = null
                )
            }
        } catch (e: CancellationException) {
            extractionStage?.deleteRecursively()
            if (installedTarget) canonicalTargetDir.deleteRecursively()
            newlyInstalledFonts.forEach { it.delete() }
            throw e
        } catch (e: Exception) {
            Log.e("ProjectArchive", "Archive import failed", e)
            extractionStage?.deleteRecursively()
            if (installedTarget) canonicalTargetDir.deleteRecursively()
            newlyInstalledFonts.forEach { it.delete() }
            ImportResult(
                state = null,
                report = blankFailureReport(idCollisionPolicy),
                errorMessage = e.message ?: e.javaClass.simpleName
            )
        } finally {
            stagedArchive?.delete()
        }
    }

    private data class ExtractedArchive(
        val projectJson: String?,
        val mediaManifestJson: String?,
        val files: MutableMap<String, File>
    )

    internal fun planArchiveImport(entries: List<ArchiveEntryMetadata>): ArchiveImportPlan {
        if (entries.size > MAX_ARCHIVE_ENTRY_COUNT) {
            throw IOException("Archive contains too many entries")
        }
        val seenNames = hashSetOf<String>()
        var expandedBytes = 0L
        var extractableEntryCount = 0
        var hasProjectJson = false

        entries.forEach { entry ->
            validateArchiveEntryName(entry.name)
            if (!seenNames.add(entry.name)) {
                throw IOException("Archive contains duplicate entry: ${entry.name}")
            }
            if (entry.isDirectory) return@forEach

            val isTextEntry = entry.name == PROJECT_JSON_ENTRY || entry.name == MEDIA_MANIFEST_ENTRY
            val isPayloadEntry = isSupportedMediaEntry(entry.name)
            if (!isTextEntry && !isPayloadEntry) return@forEach
            if (entry.size < 0L || entry.compressedSize < 0L) {
                throw IOException("Archive entry has unknown size: ${entry.name}")
            }
            val entryLimit = if (isTextEntry) MAX_ARCHIVE_TEXT_ENTRY_BYTES else MAX_ARCHIVE_TOTAL_BYTES
            if (entry.size > entryLimit) {
                throw IOException("Archive entry exceeds size limit: ${entry.name}")
            }
            if (entry.size > 0L) {
                if (entry.compressedSize <= 0L ||
                    entry.size > saturatingMultiply(entry.compressedSize, MAX_ARCHIVE_COMPRESSION_RATIO)
                ) {
                    throw IOException("Archive entry exceeds compression-ratio limit: ${entry.name}")
                }
            }
            expandedBytes = saturatingAdd(expandedBytes, entry.size)
            if (expandedBytes > MAX_ARCHIVE_TOTAL_BYTES) {
                throw IOException("Archive exceeds expanded-size limit")
            }
            extractableEntryCount++
            if (entry.name == PROJECT_JSON_ENTRY) hasProjectJson = true
        }
        if (!hasProjectJson) throw IOException("Archive missing $PROJECT_JSON_ENTRY")
        return ArchiveImportPlan(expandedBytes, extractableEntryCount)
    }

    private fun stageArchiveForImport(context: Context, archiveUri: Uri): File {
        val stagingDir = File(context.cacheDir, "project-archive-staging")
        if (!stagingDir.exists() && !stagingDir.mkdirs() && !stagingDir.exists()) {
            throw IOException("Could not create archive staging directory")
        }
        val declaredSize = runCatching {
            context.contentResolver.openAssetFileDescriptor(archiveUri, "r")?.use { descriptor ->
                descriptor.length.takeIf { it >= 0L }
            }
        }.getOrNull()
        if (declaredSize == 0L) throw IOException("Archive is empty")
        if (declaredSize != null && declaredSize > MAX_ARCHIVE_TOTAL_BYTES) {
            throw IOException("Archive exceeds compressed-size limit")
        }
        ensureStorageAvailable(
            directory = stagingDir,
            payloadBytes = declaredSize ?: 0L,
            purpose = "archive staging"
        )

        val staged = File.createTempFile("project-import-", ".clearcut", stagingDir)
        try {
            val input = context.contentResolver.openInputStream(archiveUri)
                ?: throw IOException("Could not open archive")
            input.use { source ->
                BufferedOutputStream(FileOutputStream(staged)).use { output ->
                    copyArchiveWithStorageChecks(source, output, stagingDir)
                }
            }
            if (staged.length() <= 0L) throw IOException("Archive is empty")
            return staged
        } catch (e: Exception) {
            staged.delete()
            throw e
        }
    }

    private fun copyArchiveWithStorageChecks(
        input: InputStream,
        output: OutputStream,
        stagingDir: File
    ): Long {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var copied = 0L
        var nextStorageCheck = STORAGE_RECHECK_INTERVAL_BYTES
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            copied = saturatingAdd(copied, read.toLong())
            if (copied > MAX_ARCHIVE_TOTAL_BYTES) {
                throw IOException("Archive exceeds compressed-size limit")
            }
            output.write(buffer, 0, read)
            if (copied >= nextStorageCheck) {
                ensureStorageAvailable(stagingDir, 0L, "archive staging")
                nextStorageCheck = saturatingAdd(copied, STORAGE_RECHECK_INTERVAL_BYTES)
            }
        }
        return copied
    }

    private fun createExtractionStagingDir(targetDir: File): File {
        val parent = targetDir.parentFile
            ?: throw IOException("Import destination has no parent directory")
        repeat(8) {
            val candidate = File(parent, ".${targetDir.name}.import-${java.util.UUID.randomUUID()}")
            if (candidate.mkdir()) return candidate.canonicalFile
        }
        throw IOException("Could not create import staging directory")
    }

    private fun extractArchiveToStage(
        zip: ZipFile,
        plan: ArchiveImportPlan,
        stagingDir: File,
        warnings: MutableList<String>
    ): ExtractedArchive {
        var projectJson: String? = null
        var mediaManifestJson: String? = null
        val files = mutableMapOf<String, File>()
        var extractedEntryCount = 0

        zip.entries().asSequence().forEach { entry ->
            when {
                entry.isDirectory -> Unit
                entry.name == PROJECT_JSON_ENTRY -> {
                    projectJson = zip.getInputStream(entry).use {
                        readUtf8WithByteLimit(it, MAX_ARCHIVE_TEXT_ENTRY_BYTES)
                    }
                    extractedEntryCount++
                }
                entry.name == MEDIA_MANIFEST_ENTRY -> {
                    mediaManifestJson = zip.getInputStream(entry).use {
                        readUtf8WithByteLimit(it, MAX_ARCHIVE_TEXT_ENTRY_BYTES)
                    }
                    extractedEntryCount++
                }
                isSupportedMediaEntry(entry.name) -> {
                    val outputFile = File(stagingDir, entry.name).canonicalFile
                    if (!outputFile.toPath().startsWith(stagingDir.toPath())) {
                        throw IOException("Unsafe archive output path: ${entry.name}")
                    }
                    val parent = outputFile.parentFile
                    if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.exists()) {
                        throw IOException("Could not create archive output directory")
                    }
                    zip.getInputStream(entry).use { input ->
                        BufferedOutputStream(FileOutputStream(outputFile)).use { output ->
                            val copied = copyEntryWithStorageChecks(
                                input = input,
                                output = output,
                                maxBytes = entry.size,
                                stagingDir = stagingDir
                            )
                            if (copied != entry.size) {
                                throw IOException("Archive entry size changed while extracting: ${entry.name}")
                            }
                        }
                    }
                    files[entry.name] = outputFile
                    extractedEntryCount++
                }
                else -> {
                    Log.w("ProjectArchive", "Skipping unsupported archive entry: ${entry.name}")
                    warnings += "Skipped unsupported entry: ${entry.name}"
                }
            }
        }
        if (extractedEntryCount != plan.extractableEntryCount) {
            throw IOException("Archive entry plan changed during extraction")
        }
        return ExtractedArchive(projectJson, mediaManifestJson, files)
    }

    private fun copyEntryWithStorageChecks(
        input: InputStream,
        output: OutputStream,
        maxBytes: Long,
        stagingDir: File
    ): Long {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var copied = 0L
        var nextStorageCheck = STORAGE_RECHECK_INTERVAL_BYTES
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            copied = saturatingAdd(copied, read.toLong())
            if (copied > maxBytes) throw IOException("Archive entry exceeded declared size")
            output.write(buffer, 0, read)
            if (copied >= nextStorageCheck) {
                ensureStorageAvailable(stagingDir, 0L, "archive extraction")
                nextStorageCheck = saturatingAdd(copied, STORAGE_RECHECK_INTERVAL_BYTES)
            }
        }
        return copied
    }

    private fun ensureStorageAvailable(directory: File, payloadBytes: Long, purpose: String) {
        require(payloadBytes >= 0L) { "payloadBytes must be non-negative" }
        val available = StatFs(directory.absolutePath).availableBytes
        val required = saturatingAdd(payloadBytes, ARCHIVE_STORAGE_RESERVE_BYTES)
        if (available < required) {
            throw IOException(
                "Insufficient storage for $purpose: requires $required bytes including reserve; $available available"
            )
        }
    }

    private fun validateArchiveEntryName(name: String) {
        if (name.isBlank() || name.length > MAX_ARCHIVE_ENTRY_NAME_CHARS) {
            throw IOException("Archive contains an invalid entry name")
        }
        if (name.startsWith('/') || name.startsWith('\\') || '\\' in name ||
            name.matches(Regex("^[A-Za-z]:.*")) || name.any { it.code < 0x20 } ||
            name.split('/').any { it == "." || it == ".." }
        ) {
            throw IOException("Archive contains an unsafe entry path: $name")
        }
    }

    private fun rejectKnownOversizedEntry(entry: ZipEntry, maxBytes: Long) {
        if (entry.size > maxBytes) {
            throw IOException("Archive entry exceeds size limit: ${entry.name}")
        }
    }

    private fun saturatingAdd(left: Long, right: Long): Long {
        if (right > 0L && left > Long.MAX_VALUE - right) return Long.MAX_VALUE
        return left + right
    }

    private fun saturatingMultiply(left: Long, right: Long): Long {
        if (left <= 0L || right <= 0L) return 0L
        if (left > Long.MAX_VALUE / right) return Long.MAX_VALUE
        return left * right
    }

    /**
     * Get the estimated archive size in bytes.
     */
    suspend fun estimateArchiveSize(
        context: Context,
        state: AutoSaveState
    ): Long = withContext(Dispatchers.IO) {
        var totalSize = 0L
        val dependencies = collectArchivedDependencies(context, state)
            .filter { it.manifest.archivePolicy == ProjectDependencyArchivePolicy.INCLUDE.name }

        for (dependency in dependencies) {
            try {
                openDependency(context, dependency).use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        totalSize = (totalSize + read).coerceAtMost(MAX_ARCHIVE_TOTAL_BYTES)
                    }
                }
            } catch (_: Exception) {
                // Skip unreadable files
            }
        }

        totalSize + 4096 // Add overhead for project JSON
    }

    private data class LegacyMediaSource(val originalUri: String, val uri: Uri, val entryName: String)

    private fun collectLegacyMedia(state: AutoSaveState): List<LegacyMediaSource> {
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
            LegacyMediaSource(
                originalUri = originalUri,
                uri = uri,
                entryName = entryName
            )
        }
    }

    private fun collectArchivedDependencies(
        context: Context,
        state: AutoSaveState
    ): List<ArchivedDependencySource> {
        val sources = LinkedHashMap<Pair<ProjectDependencyKind, String>, ArchivedDependencySource>()
        var mediaIndex = 0
        var lutIndex = 0
        var fontIndex = 0
        var watermarkIndex = 0

        fun addMedia(uri: Uri, required: Boolean = true) {
            val reference = uri.toString().takeIf(String::isNotBlank) ?: return
            val key = ProjectDependencyKind.MEDIA to reference
            sources[key]?.let { existing ->
                if (required && !existing.manifest.required) {
                    sources[key] = existing.copy(manifest = existing.manifest.copy(required = true))
                }
                return
            }
            val entry = "media/${mediaIndex++}_${sanitizeFileNamePreservingExtension(
                uri.lastPathSegment ?: "media", "media"
            )}"
            sources[key] = ArchivedDependencySource(
                manifest = ArchiveManifestEntry(
                    kind = ProjectDependencyKind.MEDIA.name,
                    logicalReference = reference,
                    entryName = entry,
                    byteLength = null,
                    sha256 = null,
                    archivePolicy = ProjectDependencyArchivePolicy.INCLUDE.name,
                    required = required
                ),
                uri = uri
            )
        }

        fun addLut(reference: String) {
            if (reference.isBlank()) return
            val key = ProjectDependencyKind.LUT to reference
            if (key in sources) return
            val file = File(reference)
            val entry = "luts/${lutIndex++}_${sanitizeFileNamePreservingExtension(file.name, "lut")}"
            sources[key] = ArchivedDependencySource(
                manifest = ArchiveManifestEntry(
                    kind = ProjectDependencyKind.LUT.name,
                    logicalReference = reference,
                    entryName = entry,
                    byteLength = null,
                    sha256 = null,
                    archivePolicy = ProjectDependencyArchivePolicy.INCLUDE.name,
                    required = true
                ),
                file = file
            )
        }

        fun addFont(family: String) {
            if (!family.startsWith(FontRegistry.CUSTOM_PREFIX)) return
            val fileName = family.removePrefix(FontRegistry.CUSTOM_PREFIX)
            val safeName = sanitizeFileNamePreservingExtension(fileName, "font")
            val fontsDir = File(context.filesDir, "fonts").canonicalFile
            val file = File(fontsDir, fileName).canonicalFile
            require(file.toPath().startsWith(fontsDir.toPath())) { "Unsafe custom font reference" }
            val key = ProjectDependencyKind.CUSTOM_FONT to family
            if (key in sources) return
            sources[key] = ArchivedDependencySource(
                manifest = ArchiveManifestEntry(
                    kind = ProjectDependencyKind.CUSTOM_FONT.name,
                    logicalReference = family,
                    entryName = "fonts/${fontIndex++}_$safeName",
                    byteLength = null,
                    sha256 = null,
                    archivePolicy = ProjectDependencyArchivePolicy.INCLUDE.name,
                    required = true
                ),
                file = file
            )
        }

        fun addWatermark(watermark: Watermark) {
            val reference = watermark.sourceUri.toString().takeIf(String::isNotBlank) ?: return
            val key = ProjectDependencyKind.WATERMARK to reference
            if (key in sources) return
            val entry = "watermarks/${watermarkIndex++}_${sanitizeFileNamePreservingExtension(
                watermark.sourceUri.lastPathSegment ?: "watermark", "watermark"
            )}"
            sources[key] = ArchivedDependencySource(
                manifest = ArchiveManifestEntry(
                    kind = ProjectDependencyKind.WATERMARK.name,
                    logicalReference = reference,
                    entryName = entry,
                    byteLength = null,
                    sha256 = null,
                    archivePolicy = ProjectDependencyArchivePolicy.INCLUDE.name,
                    required = true
                ),
                uri = watermark.sourceUri
            )
        }

        fun visitClip(clip: Clip) {
            addMedia(clip.sourceUri)
            clip.colorGrade?.lutPath?.let(::addLut)
            clip.captions.forEach { addFont(it.style.fontFamily) }
            clip.compoundClips.forEach(::visitClip)
        }

        state.tracks.forEach { track -> track.clips.forEach(::visitClip) }
        state.imageOverlays.forEach { addMedia(it.sourceUri) }
        state.mediaAssets.forEach { asset ->
            asset.managedUri.takeIf(String::isNotBlank)?.let { addMedia(Uri.parse(it)) }
        }
        state.storyboardCards.forEach { card -> card.mediaUri?.let { addMedia(it, required = false) } }
        state.textOverlays.forEach { addFont(it.fontFamily) }
        state.exportWatermark?.let(::addWatermark)

        val needsModel = state.tracks.any { track ->
            track.clips.any(::clipUsesSegmentationModel)
        }
        if (needsModel) {
            sources[ProjectDependencyKind.MODEL to SEGMENTATION_MODEL_DEPENDENCY] = ArchivedDependencySource(
                manifest = ArchiveManifestEntry(
                    kind = ProjectDependencyKind.MODEL.name,
                    logicalReference = SEGMENTATION_MODEL_DEPENDENCY,
                    entryName = null,
                    byteLength = null,
                    sha256 = null,
                    archivePolicy = ProjectDependencyArchivePolicy.REFERENCE_ONLY.name,
                    required = true,
                    fallbackName = null
                )
            )
        }
        return sources.values.toList()
    }

    private fun clipUsesSegmentationModel(clip: Clip): Boolean =
        clip.effects.any { it.enabled && it.type == com.novacut.editor.model.EffectType.BG_REMOVAL } ||
            clip.compoundClips.any(::clipUsesSegmentationModel)

    private fun inspectDependency(context: Context, source: ArchivedDependencySource): Pair<Long, String> {
        val digest = MessageDigest.getInstance("SHA-256")
        var length = 0L
        openDependency(context, source).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                length += read
                if (length > MAX_ARCHIVE_TOTAL_BYTES) throw IOException("Archive dependency exceeds size limit")
                digest.update(buffer, 0, read)
            }
        }
        return length to digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun openDependency(context: Context, source: ArchivedDependencySource): InputStream =
        source.file?.inputStream()
            ?: source.uri?.let { context.contentResolver.openInputStream(it) }
            ?: throw IOException("Cannot read dependency: ${source.manifest.logicalReference}")

    internal fun buildMediaManifestV2(entries: List<ArchiveManifestEntry>): String = JSONObject().apply {
        put("version", 2)
        put("entries", JSONArray().apply {
            entries.forEach { entry ->
                put(JSONObject().apply {
                    put("kind", entry.kind)
                    put("logicalReference", entry.logicalReference)
                    entry.entryName?.let { put("entryName", it) }
                    entry.byteLength?.let { put("byteLength", it) }
                    entry.sha256?.let { put("sha256", it) }
                    put("archivePolicy", entry.archivePolicy)
                    put("required", entry.required)
                    put("fallbackAllowed", entry.fallbackAllowed)
                    entry.fallbackName?.let { put("fallbackName", it) }
                })
            }
        })
    }.toString(2)

    internal data class ParsedArchiveManifest(val version: Int, val entries: List<ArchiveManifestEntry>)

    internal fun parseMediaManifest(raw: String): ParsedArchiveManifest {
        val json = JSONObject(raw)
        val version = json.optInt("version", 1)
        val entries = json.optJSONArray("entries") ?: JSONArray()
        val parsed = buildList {
            for (index in 0 until entries.length()) {
                val item = entries.optJSONObject(index) ?: continue
                if (version <= 1) {
                    val originalUri = item.optString("originalUri", "")
                    val entryName = item.optString("entryName", "")
                    if (originalUri.isNotBlank() && entryName.isNotBlank()) {
                        add(ArchiveManifestEntry(
                            kind = ProjectDependencyKind.MEDIA.name,
                            logicalReference = originalUri,
                            entryName = entryName,
                            byteLength = null,
                            sha256 = null,
                            archivePolicy = ProjectDependencyArchivePolicy.INCLUDE.name,
                            required = false
                        ))
                    }
                } else {
                    val reference = item.optString("logicalReference", "")
                    val kind = item.optString("kind", "")
                    if (reference.isBlank() || kind.isBlank()) continue
                    add(ArchiveManifestEntry(
                        kind = kind,
                        logicalReference = reference,
                        entryName = item.optString("entryName", "").takeIf(String::isNotBlank),
                        byteLength = if (item.has("byteLength")) item.optLong("byteLength") else null,
                        sha256 = item.optString("sha256", "").takeIf(String::isNotBlank),
                        archivePolicy = item.optString("archivePolicy", ProjectDependencyArchivePolicy.INCLUDE.name),
                        required = item.optBoolean("required", true),
                        fallbackAllowed = item.optBoolean("fallbackAllowed", false),
                        fallbackName = item.optString("fallbackName", "").takeIf(String::isNotBlank)
                    ))
                }
            }
        }
        return ParsedArchiveManifest(version, parsed)
    }

    private fun parseSchemaVersion(raw: String): Int {
        // Prefer the canonical `schemaVersion` key (matching peekSchemaVersion),
        // falling back to the legacy `version`. Reading only `version` would make
        // a future build that keeps only `schemaVersion` see version 0 here and
        // treat a genuinely newer archive as older — bypassing the schema-too-new
        // guard and doing a lossy best-effort import instead of refusing it.
        return runCatching {
            val json = JSONObject(raw)
            json.optInt("schemaVersion", json.optInt("version", 0))
        }.getOrDefault(0)
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
        if (listOf("media/", "luts/", "fonts/", "watermarks/").none(entryName::startsWith)) return false
        if ('\\' in entryName) return false
        if (entryName.endsWith('/')) return false
        return entryName.substringAfter('/').isNotBlank()
    }

    private fun validateManifestMetadata(
        manifest: ParsedArchiveManifest,
        warnings: MutableList<String>
    ) {
        if (manifest.version <= 1) return
        val knownKinds = setOf(
            ProjectDependencyKind.MEDIA.name,
            ProjectDependencyKind.LUT.name,
            ProjectDependencyKind.CUSTOM_FONT.name,
            ProjectDependencyKind.WATERMARK.name,
            ProjectDependencyKind.MODEL.name
        )
        val expectedPrefixes = mapOf(
            ProjectDependencyKind.MEDIA.name to "media/",
            ProjectDependencyKind.LUT.name to "luts/",
            ProjectDependencyKind.CUSTOM_FONT.name to "fonts/",
            ProjectDependencyKind.WATERMARK.name to "watermarks/"
        )
        val seenLogical = hashSetOf<Pair<String, String>>()
        manifest.entries.forEach { entry ->
            if (!seenLogical.add(entry.kind to entry.logicalReference)) {
                throw IOException("Manifest contains duplicate dependency: ${entry.logicalReference}")
            }
            if (entry.kind !in knownKinds) {
                if (entry.required) throw IOException("Unknown required dependency kind: ${entry.kind}")
                warnings += "Ignored optional dependency kind: ${entry.kind}"
                return@forEach
            }
            if (entry.archivePolicy == ProjectDependencyArchivePolicy.REFERENCE_ONLY.name) return@forEach
            if (entry.archivePolicy != ProjectDependencyArchivePolicy.INCLUDE.name) {
                if (entry.required) throw IOException("Required dependency has unsupported archive policy")
                warnings += "Ignored optional dependency with archive policy ${entry.archivePolicy}"
                return@forEach
            }
            val archiveName = entry.entryName
            val expectedPrefix = expectedPrefixes[entry.kind]
            if (archiveName == null || expectedPrefix == null || !archiveName.startsWith(expectedPrefix) ||
                !isSupportedMediaEntry(archiveName)
            ) {
                handleInvalidManifestEntry(entry, warnings, "unsafe or missing archive path")
                return@forEach
            }
            validateArchiveEntryName(archiveName)
            val expectedLength = entry.byteLength
            val expectedSha = entry.sha256
            if (expectedLength == null || expectedLength < 0L || expectedLength > MAX_ARCHIVE_TOTAL_BYTES ||
                expectedSha?.matches(Regex("[0-9a-fA-F]{64}")) != true
            ) {
                handleInvalidManifestEntry(entry, warnings, "missing or invalid integrity metadata")
            }
        }
    }

    private fun verifyManifestEntries(
        manifest: ParsedArchiveManifest,
        extractedFiles: Map<String, Uri>,
        warnings: MutableList<String>
    ): Set<String> = verifyManifestFiles(
        manifest,
        extractedFiles.mapNotNull { (name, uri) -> uri.path?.let { name to File(it) } }.toMap(),
        warnings
    )

    internal fun verifyManifestFiles(
        manifest: ParsedArchiveManifest,
        extractedFiles: Map<String, File>,
        warnings: MutableList<String>
    ): Set<String> {
        if (manifest.version <= 1) return emptySet()
        val invalidOptionalEntries = mutableSetOf<String>()
        val knownKinds = setOf(
            ProjectDependencyKind.MEDIA.name,
            ProjectDependencyKind.LUT.name,
            ProjectDependencyKind.CUSTOM_FONT.name,
            ProjectDependencyKind.WATERMARK.name,
            ProjectDependencyKind.MODEL.name
        )
        val expectedPrefixes = mapOf(
            ProjectDependencyKind.MEDIA.name to "media/",
            ProjectDependencyKind.LUT.name to "luts/",
            ProjectDependencyKind.CUSTOM_FONT.name to "fonts/",
            ProjectDependencyKind.WATERMARK.name to "watermarks/"
        )
        val seenLogical = hashSetOf<Pair<String, String>>()
        manifest.entries.forEach { entry ->
            if (!seenLogical.add(entry.kind to entry.logicalReference)) {
                throw IOException("Manifest contains duplicate dependency: ${entry.logicalReference}")
            }
            if (entry.kind !in knownKinds) {
                if (entry.required) throw IOException("Unknown required dependency kind: ${entry.kind}")
                warnings += "Ignored optional dependency kind: ${entry.kind}"
                entry.entryName?.let(invalidOptionalEntries::add)
                return@forEach
            }
            if (entry.archivePolicy == ProjectDependencyArchivePolicy.REFERENCE_ONLY.name) return@forEach
            if (entry.archivePolicy != ProjectDependencyArchivePolicy.INCLUDE.name) {
                if (entry.required) throw IOException("Required dependency has unsupported archive policy")
                warnings += "Ignored optional dependency with archive policy ${entry.archivePolicy}"
                entry.entryName?.let(invalidOptionalEntries::add)
                return@forEach
            }
            val archiveName = entry.entryName
            val expectedPrefix = expectedPrefixes[entry.kind]
            if (archiveName == null || expectedPrefix == null || !archiveName.startsWith(expectedPrefix) ||
                !isSupportedMediaEntry(archiveName)
            ) {
                if (handleInvalidManifestEntry(entry, warnings, "unsafe or missing archive path")) {
                    archiveName?.let(invalidOptionalEntries::add)
                }
                return@forEach
            }
            val file = extractedFiles[archiveName]
            if (file == null) {
                if (handleInvalidManifestEntry(entry, warnings, "missing archive entry")) {
                    invalidOptionalEntries += archiveName
                }
                return@forEach
            }
            if (!file.isFile) {
                if (handleInvalidManifestEntry(entry, warnings, "unreadable archive entry")) {
                    invalidOptionalEntries += archiveName
                }
                return@forEach
            }
            val expectedLength = entry.byteLength
            val expectedSha = entry.sha256
            if (expectedLength == null || expectedLength < 0L || expectedSha?.matches(Regex("[0-9a-fA-F]{64}")) != true) {
                if (handleInvalidManifestEntry(entry, warnings, "missing integrity metadata")) {
                    invalidOptionalEntries += archiveName
                }
                return@forEach
            }
            val actualSha = file.inputStream().use(::sha256)
            if (file.length() != expectedLength || !actualSha.equals(expectedSha, ignoreCase = true)) {
                if (handleInvalidManifestEntry(entry, warnings, "integrity check failed")) {
                    invalidOptionalEntries += archiveName
                }
            }
        }
        return invalidOptionalEntries
    }

    private fun handleInvalidManifestEntry(
        entry: ArchiveManifestEntry,
        warnings: MutableList<String>,
        reason: String
    ): Boolean {
        if (entry.required) throw IOException("Required ${entry.kind} dependency $reason: ${entry.logicalReference}")
        warnings += "Optional ${entry.kind} dependency $reason: ${entry.logicalReference}"
        return true
    }

    private fun sha256(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun copyWithLimitAndDigest(
        input: InputStream,
        output: OutputStream,
        maxBytes: Long
    ): Pair<Long, String> {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var copied = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (copied + read > maxBytes) throw IOException("Archive exceeds size limit")
            output.write(buffer, 0, read)
            digest.update(buffer, 0, read)
            copied += read
        }
        return copied to digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun readCurrentEntryText(zipInput: ZipInputStream, maxBytes: Long): String {
        return readUtf8WithByteLimit(zipInput, maxBytes)
    }

    internal fun rewriteArchivedMediaUrisForImport(
        state: AutoSaveState,
        manifestEntryMap: Map<String, String>,
        extractedFiles: Map<String, Uri>,
        seenSourceUris: MutableSet<String>,
        unresolvedSink: MutableList<String>,
        allowFileNameFallback: Boolean = true
    ): AutoSaveState {
        return state.rewriteArchivedMediaUris(
            manifestEntryMap = manifestEntryMap,
            extractedFiles = extractedFiles,
            seenSourceUris = seenSourceUris,
            unresolvedSink = unresolvedSink,
            allowFileNameFallback = allowFileNameFallback
        )
    }

    private data class DependencyRewriteResult(
        val state: AutoSaveState,
        val newlyInstalledFonts: List<File>
    )

    private data class InstalledFontsResult(
        val mappings: Map<String, String>,
        val newlyInstalledFiles: List<File>
    )

    private fun rewriteArchivedDependenciesForImport(
        context: Context,
        state: AutoSaveState,
        manifestEntries: List<ArchiveManifestEntry>,
        extractedFiles: Map<String, Uri>
    ): DependencyRewriteResult {
        val lutPaths = manifestEntries
            .filter { it.kind == ProjectDependencyKind.LUT.name && it.entryName != null }
            .mapNotNull { entry ->
                extractedFiles[entry.entryName]?.path?.let { entry.logicalReference to it }
            }
            .toMap()
        val installedFonts = installArchivedFonts(context, manifestEntries, extractedFiles)
        val watermarkUris = manifestEntries
            .filter { it.kind == ProjectDependencyKind.WATERMARK.name && it.entryName != null }
            .mapNotNull { entry -> extractedFiles[entry.entryName]?.let { entry.logicalReference to it } }
            .toMap()

        return DependencyRewriteResult(
            state = rewriteDependencyReferences(state, lutPaths, installedFonts.mappings, watermarkUris),
            newlyInstalledFonts = installedFonts.newlyInstalledFiles
        )
    }

    internal fun rewriteDependencyReferences(
        state: AutoSaveState,
        lutPaths: Map<String, String>,
        installedFonts: Map<String, String>,
        watermarkUris: Map<String, Uri> = emptyMap()
    ): AutoSaveState {

        fun rewriteClip(clip: Clip): Clip = clip.copy(
            colorGrade = clip.colorGrade?.let { grade ->
                grade.copy(lutPath = grade.lutPath?.let { lutPaths[it] ?: it })
            },
            captions = clip.captions.map { caption ->
                caption.copy(style = caption.style.copy(
                    fontFamily = installedFonts[caption.style.fontFamily] ?: caption.style.fontFamily
                ))
            },
            compoundClips = clip.compoundClips.map(::rewriteClip)
        )

        return state.copy(
            tracks = state.tracks.map { track -> track.copy(clips = track.clips.map(::rewriteClip)) },
            textOverlays = state.textOverlays.map { overlay ->
                overlay.copy(fontFamily = installedFonts[overlay.fontFamily] ?: overlay.fontFamily)
            },
            exportWatermark = state.exportWatermark?.let { watermark ->
                watermark.copy(sourceUri = watermarkUris[watermark.sourceUri.toString()] ?: watermark.sourceUri)
            }
        )
    }

    private fun installArchivedFonts(
        context: Context,
        manifestEntries: List<ArchiveManifestEntry>,
        extractedFiles: Map<String, Uri>
    ): InstalledFontsResult {
        val fontsDir = File(context.filesDir, "fonts")
        val mappings = mutableMapOf<String, String>()
        val newlyInstalled = mutableListOf<File>()
        try {
            manifestEntries
                .filter { it.kind == ProjectDependencyKind.CUSTOM_FONT.name && it.entryName != null }
                .forEach { entry ->
                    val source = extractedFiles[entry.entryName]?.path?.let(::File) ?: return@forEach
                    val originalName = entry.logicalReference.removePrefix(FontRegistry.CUSTOM_PREFIX)
                    val safeName = sanitizeFileNamePreservingExtension(originalName, "font")
                    val hashPrefix = entry.sha256.orEmpty().ifBlank { "imported" }
                    val installedName = "${hashPrefix}_$safeName"
                    val target = File(fontsDir, installedName).canonicalFile
                    if (!target.toPath().startsWith(fontsDir.canonicalFile.toPath())) {
                        throw IOException("Unsafe font install path")
                    }
                    if (!fontsDir.exists() && !fontsDir.mkdirs() && !fontsDir.exists()) {
                        throw IOException("Cannot create font directory")
                    }
                    val targetMatches = target.isFile &&
                        entry.byteLength == target.length() &&
                        entry.sha256?.equals(target.inputStream().use(::sha256), ignoreCase = true) == true
                    if (!targetMatches) {
                        val targetExisted = target.exists()
                        val partial = File.createTempFile(".$installedName.", ".partial", fontsDir)
                        try {
                            source.inputStream().use { input ->
                                partial.outputStream().use { output ->
                                    val copied = copyWithLimit(
                                        input,
                                        output,
                                        entry.byteLength ?: MAX_ARCHIVE_TOTAL_BYTES
                                    )
                                    if (copied != entry.byteLength) {
                                        throw IOException("Archived font size changed during install")
                                    }
                                }
                            }
                            moveFileReplacing(partial, target)
                            if (!targetExisted) newlyInstalled += target
                        } finally {
                            partial.delete()
                        }
                    }
                    mappings[entry.logicalReference] = FontRegistry.CUSTOM_PREFIX + installedName
                }
            return InstalledFontsResult(mappings, newlyInstalled)
        } catch (e: Exception) {
            newlyInstalled.forEach { it.delete() }
            throw e
        }
    }

    private fun AutoSaveState.rewriteArchivedMediaUris(
        manifestEntryMap: Map<String, String>,
        extractedFiles: Map<String, Uri>,
        seenSourceUris: MutableSet<String>,
        unresolvedSink: MutableList<String>,
        allowFileNameFallback: Boolean
    ): AutoSaveState {
        fun resolveDirectArchivedUri(uriString: String): Uri? {
            val mappedEntry = manifestEntryMap[uriString]
            if (mappedEntry != null) {
                extractedFiles[mappedEntry]?.let { return it }
            }
            return null
        }

        fun resolveMappedArchivedUri(originalUri: Uri): Uri? {
            val key = originalUri.toString()
            return resolveDirectArchivedUri(key) ?: if (allowFileNameFallback) {
                fallbackArchivedUri(originalUri, extractedFiles)
            } else {
                null
            }
        }

        fun resolveArchivedUri(originalUri: Uri): Uri {
            val key = originalUri.toString()
            val isFresh = key.isNotBlank() && seenSourceUris.add(key)
            val resolved = resolveMappedArchivedUri(originalUri)
            if (resolved != null) return resolved
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

        fun rewriteAsset(asset: ProjectMediaAsset): ProjectMediaAsset {
            val resolvedManagedUri = resolveDirectArchivedUri(asset.managedUri)
                ?: resolveDirectArchivedUri(asset.originalUri)
                ?: resolveArchivedUri(Uri.parse(asset.managedUri))
            val resolvedOriginalUri = resolveDirectArchivedUri(asset.originalUri)
                ?: resolvedManagedUri
            return asset.copy(
                managedUri = resolvedManagedUri.toString(),
                originalUri = resolvedOriginalUri.toString()
            )
        }

        return copy(
            tracks = tracks.map { track ->
                track.copy(clips = track.clips.map(::rewriteClip))
            },
            imageOverlays = imageOverlays.map { overlay ->
                overlay.copy(sourceUri = resolveArchivedUri(overlay.sourceUri))
            },
            mediaAssets = mediaAssets.map(::rewriteAsset)
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

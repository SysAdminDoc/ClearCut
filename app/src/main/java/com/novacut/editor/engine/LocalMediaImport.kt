package com.novacut.editor.engine

import android.content.Context
import android.net.Uri
import android.os.StatFs
import android.provider.OpenableColumns
import android.util.Log
import android.webkit.MimeTypeMap
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CancellationException

private const val LOCAL_MEDIA_IMPORT_TAG = "LocalMediaImport"
private const val LOCAL_MEDIA_FALLBACK_STEM = "media"
private const val MAX_MANAGED_MEDIA_EXTENSION_LENGTH = 10
private const val COPY_BUFFER_SIZE = 64 * 1024
private const val FREE_SPACE_MARGIN_BYTES = 50L * 1024L * 1024L

internal fun managedMediaDir(context: Context): File = File(context.filesDir, "media/imports")

internal fun pendingCameraCaptureDir(context: Context): File = File(context.cacheDir, "camera-captures")

internal fun importUriToManagedMedia(
    context: Context,
    uri: Uri,
    mediaType: String
): Uri? {
    if (uri.scheme == "file") {
        val sourceFile = uri.path?.let(::File)
        if (sourceFile != null && sourceFile.exists()) {
            val sourceCanonical = runCatching { sourceFile.canonicalFile }.getOrNull()
            if (sourceCanonical != null &&
                sourceCanonical.isFile &&
                sourceCanonical.length() > 0L &&
                isInsideDirectory(sourceCanonical, managedMediaDir(context))
            ) {
                val managedUri = Uri.fromFile(sourceCanonical)
                if (!mediaAssetSidecarFileFor(sourceCanonical).isFile) {
                    writeManagedMediaAssetSidecar(context, managedUri, uri, mediaType)
                }
                return managedUri
            }
        }
    }

    val destinationDir = managedMediaDir(context)
    if (!destinationDir.exists() && !destinationDir.mkdirs() && !destinationDir.exists()) {
        Log.w(LOCAL_MEDIA_IMPORT_TAG, "Failed to create managed media directory: ${destinationDir.path}")
        return null
    }

    // Opportunistically sweep abandoned `.partial` artifacts left behind by
    // prior imports that crashed mid-copy. Bounded to once-per-import so it
    // doesn't add meaningful latency on the happy path.
    sweepAbandonedPartials(destinationDir)

    val destinationFile = createUniqueManagedMediaFile(
        directory = destinationDir,
        displayName = resolveMediaDisplayName(context, uri),
        extension = resolveManagedMediaExtension(context, uri, mediaType)
    )
    // Write to a sibling `.partial` file and rename on success so an interrupted
    // or crashing copy can never surface to the caller as a truncated-but-valid
    // media file. Without this a clip imported during a crash would be added to
    // the timeline with a 0-byte or partial video that breaks playback later.
    val partialFile = File(destinationFile.parentFile, destinationFile.name + ".partial")

    return try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            partialFile.outputStream().use { output -> input.copyTo(output) }
        } ?: run {
            partialFile.delete()
            Log.w(LOCAL_MEDIA_IMPORT_TAG, "Cannot open input stream for $uri")
            return null
        }

        if (partialFile.length() <= 0L) {
            partialFile.delete()
            Log.w(LOCAL_MEDIA_IMPORT_TAG, "Imported file was empty for $uri")
            return null
        }

        if (!partialFile.renameTo(destinationFile)) {
            // Rename can fail across filesystems or if the dest appeared.
            // Fall back through the same atomic writer used by export paths so
            // a failed fallback never exposes a truncated managed-media file.
            try {
                writeFileAtomically(destinationFile, requireNonEmpty = true) { tempFile ->
                    partialFile.inputStream().use { input ->
                        tempFile.outputStream().use { output -> input.copyTo(output) }
                    }
                }
                partialFile.delete()
            } catch (copyErr: Exception) {
                partialFile.delete()
                destinationFile.delete()
                Log.w(LOCAL_MEDIA_IMPORT_TAG, "Rename + fallback copy both failed for $uri", copyErr)
                return null
            }
        }
        Uri.fromFile(destinationFile).also { managedUri ->
            writeManagedMediaAssetSidecar(context, managedUri, uri, mediaType)
        }
    } catch (e: Exception) {
        partialFile.delete()
        destinationFile.delete()
        Log.w(LOCAL_MEDIA_IMPORT_TAG, "Failed to import media URI $uri", e)
        null
    }
}

/**
 * Delete `.partial` files older than 10 minutes in the managed-media dir.
 * These are only created by `importUriToManagedMedia` and are always expected
 * to be renamed away on success, so anything older than a sane per-clip copy
 * time is an abandoned artifact from a crashed or killed process.
 */
private fun sweepAbandonedPartials(dir: File) {
    val cutoffMs = System.currentTimeMillis() - 10L * 60L * 1000L
    dir.listFiles()?.forEach { f ->
        if (f.isFile && f.name.endsWith(".partial") && f.lastModified() < cutoffMs) {
            runCatching { f.delete() }
        }
    }
}

/**
 * Mark-and-sweep the managed-media dir against a set of URIs that are still
 * referenced by surviving projects. Files not in the keep-set AND older than
 * `minAgeMs` (default 24h, so we never race an import that just finished
 * writing but hasn't been registered into a project's auto-save yet) are
 * deleted. Returns the number of files removed and the bytes reclaimed so
 * callers can surface a toast or telemetry if they want.
 *
 * Called when a project is deleted — the caller hands in every file URI
 * referenced by the remaining projects' auto-save states. Without this the
 * managed-media dir only grows (the old `deleteProject` path removed the DB
 * row and auto-save JSON but left all the imported source clips on disk).
 */
internal data class ManagedMediaSweepResult(val filesDeleted: Int, val bytesFreed: Long)

internal fun sweepUnreferencedManagedMedia(
    context: Context,
    referencedUris: Set<Uri>,
    minAgeMs: Long = 24L * 60L * 60L * 1000L
): ManagedMediaSweepResult {
    val dir = managedMediaDir(context)
    if (!dir.exists()) return ManagedMediaSweepResult(0, 0L)
    val referencedPaths = referencedUris
        .mapNotNull { u ->
            if (u.scheme == "file") u.path else null
        }
        .mapNotNull { runCatching { File(it).canonicalPath }.getOrNull() }
        .toSet()
    val ageCutoff = System.currentTimeMillis() - minAgeMs
    var deleted = 0
    var bytes = 0L
    dir.listFiles()?.forEach { f ->
        if (!f.isFile) return@forEach
        if (f.name.endsWith(".partial")) return@forEach
        if (f.lastModified() > ageCutoff) return@forEach
        if (isMediaAssetSidecar(f)) {
            val mediaFile = mediaFileForAssetSidecar(f)
            if (mediaFile != null && !mediaFile.isFile) {
                val size = f.length()
                if (f.delete()) {
                    deleted++
                    bytes += size
                }
            }
            return@forEach
        }
        val canonical = runCatching { f.canonicalPath }.getOrNull() ?: return@forEach
        if (canonical in referencedPaths) return@forEach
        val size = f.length()
        if (f.delete()) {
            runCatching { mediaAssetSidecarFileFor(f).delete() }
            deleted++
            bytes += size
        }
    }
    return ManagedMediaSweepResult(deleted, bytes)
}

/**
 * Sweep `filesDir/imported_<timestamp>/` directories created by project
 * archive imports. Nothing else deletes them: removing an imported project
 * (or undoing an import) previously stranded the extracted media forever.
 * A directory is kept if ANY file inside it is still referenced by a
 * remaining project's auto-save, or if it is younger than [minAgeMs].
 */
internal fun sweepUnreferencedArchiveImports(
    context: Context,
    referencedUris: Set<Uri>,
    minAgeMs: Long = 24L * 60L * 60L * 1000L
): ManagedMediaSweepResult {
    val referencedPaths = referencedUris
        .mapNotNull { u -> if (u.scheme == "file") u.path else null }
        .mapNotNull { runCatching { File(it).canonicalPath }.getOrNull() }
        .toSet()
    val ageCutoff = System.currentTimeMillis() - minAgeMs
    var deleted = 0
    var bytes = 0L
    context.filesDir.listFiles { f ->
        f.isDirectory && f.name.matches(Regex("imported_\\d+"))
    }?.forEach { dir ->
        val files = dir.walkTopDown().filter { it.isFile }.toList()
        val newestModified = files.maxOfOrNull { it.lastModified() } ?: dir.lastModified()
        if (newestModified > ageCutoff) return@forEach
        val stillReferenced = files.any { f ->
            runCatching { f.canonicalPath }.getOrNull() in referencedPaths
        }
        if (stillReferenced) return@forEach
        val size = files.sumOf { it.length() }
        if (dir.deleteRecursively()) {
            deleted += files.size
            bytes += size
        }
    }
    return ManagedMediaSweepResult(deleted, bytes)
}

internal fun deleteManagedMediaUri(context: Context, uri: Uri): Boolean {
    if (uri.scheme != "file") return false
    val file = uri.path?.let(::File) ?: return false
    val canonical = runCatching { file.canonicalFile }.getOrNull() ?: return false
    if (!isInsideDirectory(canonical, managedMediaDir(context))) return false
    return canonical.isFile && canonical.delete().also { deleted ->
        if (deleted) runCatching { mediaAssetSidecarFileFor(canonical).delete() }
    }
}

internal fun finalizePendingCameraCapture(
    context: Context,
    pendingFile: File,
    mediaType: String
): Uri? {
    val pendingCanonical = runCatching { pendingFile.canonicalFile }.getOrNull()
    if (pendingCanonical == null || !isInsideDirectory(pendingCanonical, pendingCameraCaptureDir(context))) {
        Log.w(LOCAL_MEDIA_IMPORT_TAG, "Rejected pending camera capture outside capture directory: ${pendingFile.path}")
        return null
    }

    if (!pendingCanonical.isFile || pendingCanonical.length() <= 0L) {
        runCatching { pendingCanonical.delete() }
        Log.w(LOCAL_MEDIA_IMPORT_TAG, "Pending camera capture missing or empty: ${pendingCanonical.path}")
        return null
    }

    val destinationDir = managedMediaDir(context)
    if (!destinationDir.exists() && !destinationDir.mkdirs() && !destinationDir.exists()) {
        Log.w(LOCAL_MEDIA_IMPORT_TAG, "Failed to create managed media directory: ${destinationDir.path}")
        return null
    }

    val destinationFile = createUniqueManagedMediaFile(
        directory = destinationDir,
        displayName = pendingFile.name,
        extension = ".${pendingFile.extension}".takeIf { pendingFile.extension.isNotBlank() }
            ?: defaultManagedMediaExtension(mediaType)
    )

    return try {
        moveFileReplacing(pendingCanonical, destinationFile)
        Uri.fromFile(destinationFile).also { managedUri ->
            writeManagedMediaAssetSidecar(context, managedUri, Uri.fromFile(pendingCanonical), mediaType)
        }
    } catch (_: Exception) {
        try {
            writeFileAtomically(destinationFile, requireNonEmpty = true) { tempFile ->
                pendingCanonical.inputStream().use { input ->
                    tempFile.outputStream().use { output -> input.copyTo(output) }
                }
            }
            pendingCanonical.delete()
            Uri.fromFile(destinationFile).also { managedUri ->
                writeManagedMediaAssetSidecar(context, managedUri, Uri.fromFile(pendingCanonical), mediaType)
            }
        } catch (copyError: Exception) {
            destinationFile.delete()
            Log.w(LOCAL_MEDIA_IMPORT_TAG, "Failed to finalize camera capture ${pendingCanonical.path}", copyError)
            null
        }
    }
}

internal fun resolveMediaDisplayName(context: Context, uri: Uri): String? {
    if (uri.scheme == "file") {
        return uri.lastPathSegment
    }

    return runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
            } else {
                null
            }
        }
    }.getOrNull() ?: uri.lastPathSegment
}

internal fun resolveManagedMediaExtension(
    context: Context,
    uri: Uri,
    mediaType: String
): String {
    val mimeType = context.contentResolver.getType(uri)
    val mimeExtension = normalizeManagedMediaExtension(
        mimeType?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
    )
    if (mimeExtension != null) {
        return mimeExtension
    }

    val displayName = resolveMediaDisplayName(context, uri)
    val displayExtension = normalizeManagedMediaExtension(displayName?.substringAfterLast('.', ""))
    if (displayExtension != null) {
        return displayExtension
    }

    return defaultManagedMediaExtension(mediaType)
}

private fun defaultManagedMediaExtension(mediaType: String): String {
    return when (mediaType) {
        "image" -> ".jpg"
        "audio" -> ".m4a"
        else -> ".mp4"
    }
}

private fun normalizeManagedMediaExtension(rawExtension: String?): String? {
    val normalized = rawExtension
        ?.lowercase()
        ?.filter { it.isLetterOrDigit() }
        ?.take(MAX_MANAGED_MEDIA_EXTENSION_LENGTH)
        ?.takeIf { it.isNotBlank() }
        ?: return null
    return ".$normalized"
}

private fun createUniqueManagedMediaFile(
    directory: File,
    displayName: String?,
    extension: String
): File {
    val safeExtension = extension
        .takeIf { it.startsWith('.') && it.length > 1 }
        ?.lowercase()
        ?: ".bin"
    val fallbackName = "$LOCAL_MEDIA_FALLBACK_STEM$safeExtension"
    val preferredName = displayName
        ?.takeIf { it.isNotBlank() }
        ?.let { sanitizeFileNamePreservingExtension(it, fallbackStem = LOCAL_MEDIA_FALLBACK_STEM, maxLength = 72) }
        ?: fallbackName
    val normalizedName = if (preferredName.endsWith(safeExtension)) {
        preferredName
    } else {
        sanitizeFileNamePreservingExtension(
            raw = preferredName.substringBeforeLast('.', preferredName) + safeExtension,
            fallbackStem = LOCAL_MEDIA_FALLBACK_STEM,
            maxLength = 72
        )
    }

    var candidate = File(directory, "${System.currentTimeMillis()}_$normalizedName")
    var index = 2
    while (candidate.exists() || File(candidate.parentFile, candidate.name + ".partial").exists()) {
        candidate = File(directory, "${System.currentTimeMillis()}_${index}_$normalizedName")
        index++
    }
    return candidate
}

internal sealed class IngestResult {
    data class Success(val managedUri: Uri) : IngestResult()
    data class InsufficientSpace(val requiredBytes: Long, val availableBytes: Long) : IngestResult()
    data object Cancelled : IngestResult()
    data class Failed(val reason: String) : IngestResult()
}

internal fun querySourceSize(context: Context, uri: Uri): Long {
    if (uri.scheme == "file") {
        return uri.path?.let(::File)?.length() ?: -1L
    }
    return runCatching {
        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
            ?: context.contentResolver.query(
                uri, arrayOf(OpenableColumns.SIZE), null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (idx >= 0) cursor.getLong(idx) else -1L
                } else -1L
            } ?: -1L
    }.getOrDefault(-1L)
}

internal fun checkFreeSpace(context: Context, requiredBytes: Long): Boolean {
    if (requiredBytes <= 0L) return true
    val statFs = StatFs(context.filesDir.path)
    val available = statFs.availableBytes
    val margin = maxOf(FREE_SPACE_MARGIN_BYTES, requiredBytes / 10)
    return available >= requiredBytes + margin
}

internal fun importUriToManagedMediaWithProgress(
    context: Context,
    uri: Uri,
    mediaType: String,
    onProgress: (Float) -> Unit = {},
    isCancelled: () -> Boolean = { false }
): IngestResult {
    if (uri.scheme == "file") {
        val sourceFile = uri.path?.let(::File)
        if (sourceFile != null && sourceFile.exists()) {
            val sourceCanonical = runCatching { sourceFile.canonicalFile }.getOrNull()
            if (sourceCanonical != null &&
                sourceCanonical.isFile &&
                sourceCanonical.length() > 0L &&
                isInsideDirectory(sourceCanonical, managedMediaDir(context))
            ) {
                val managedUri = Uri.fromFile(sourceCanonical)
                if (!mediaAssetSidecarFileFor(sourceCanonical).isFile) {
                    writeManagedMediaAssetSidecar(context, managedUri, uri, mediaType)
                }
                onProgress(1f)
                return IngestResult.Success(managedUri)
            }
        }
    }

    val sourceSize = querySourceSize(context, uri)
    if (sourceSize > 0L && !checkFreeSpace(context, sourceSize)) {
        val statFs = StatFs(context.filesDir.path)
        return IngestResult.InsufficientSpace(sourceSize, statFs.availableBytes)
    }

    if (isCancelled()) return IngestResult.Cancelled

    val destinationDir = managedMediaDir(context)
    if (!destinationDir.exists() && !destinationDir.mkdirs() && !destinationDir.exists()) {
        return IngestResult.Failed("Failed to create managed media directory")
    }

    sweepAbandonedPartials(destinationDir)

    val destinationFile = createUniqueManagedMediaFile(
        directory = destinationDir,
        displayName = resolveMediaDisplayName(context, uri),
        extension = resolveManagedMediaExtension(context, uri, mediaType)
    )
    val partialFile = File(destinationFile.parentFile, destinationFile.name + ".partial")

    return try {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: return IngestResult.Failed("Cannot open input stream").also { partialFile.delete() }

        inputStream.use { input ->
            partialFile.outputStream().use { output ->
                copyWithProgress(input, output, sourceSize, onProgress, isCancelled)
            }
        }

        if (isCancelled()) {
            partialFile.delete()
            return IngestResult.Cancelled
        }

        if (partialFile.length() <= 0L) {
            partialFile.delete()
            return IngestResult.Failed("Imported file was empty")
        }

        if (!partialFile.renameTo(destinationFile)) {
            try {
                writeFileAtomically(destinationFile, requireNonEmpty = true) { tempFile ->
                    partialFile.inputStream().use { input ->
                        tempFile.outputStream().use { output -> input.copyTo(output) }
                    }
                }
                partialFile.delete()
            } catch (copyErr: Exception) {
                partialFile.delete()
                destinationFile.delete()
                return IngestResult.Failed("Rename and fallback copy both failed: ${copyErr.message}")
            }
        }

        onProgress(1f)
        val managedUri = Uri.fromFile(destinationFile)
        writeManagedMediaAssetSidecar(context, managedUri, uri, mediaType)
        IngestResult.Success(managedUri)
    } catch (e: CancellationException) {
        partialFile.delete()
        IngestResult.Cancelled
    } catch (e: Exception) {
        partialFile.delete()
        destinationFile.delete()
        IngestResult.Failed(e.message ?: "Unknown error")
    }
}

private fun copyWithProgress(
    input: InputStream,
    output: OutputStream,
    totalBytes: Long,
    onProgress: (Float) -> Unit,
    isCancelled: () -> Boolean
) {
    val buffer = ByteArray(COPY_BUFFER_SIZE)
    var bytesCopied = 0L
    var lastProgressReport = 0L
    while (true) {
        if (isCancelled()) throw CancellationException("Import cancelled")
        val bytes = input.read(buffer)
        if (bytes < 0) break
        output.write(buffer, 0, bytes)
        bytesCopied += bytes
        if (totalBytes > 0L && bytesCopied - lastProgressReport >= totalBytes / 100) {
            onProgress((bytesCopied.toFloat() / totalBytes).coerceIn(0f, 0.99f))
            lastProgressReport = bytesCopied
        }
    }
}

private fun isInsideDirectory(file: File, directory: File): Boolean {
    val canonicalFile = runCatching { file.canonicalFile }.getOrNull() ?: return false
    val canonicalDirectory = runCatching { directory.canonicalFile }.getOrNull() ?: return false
    return canonicalFile.toPath().startsWith(canonicalDirectory.toPath())
}

package com.novacut.editor.engine

import android.content.Context
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bounded, validated LUT storage. Raw .cube/.3dl files are treated as legacy
 * declarative assets; the local registry supplies the current hash/provenance
 * and one-step rollback metadata without ever loading executable content.
 */
@Singleton
class LutRegistry @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    data class ImportedLut(
        val fileName: String,
        val file: File,
        val size: Int,
        val contentHash: String,
        val provenanceSource: String = "local import",
        val canRollback: Boolean = false,
    )

    private val lutDir = File(context.filesDir, "luts").also { it.mkdirs() }
    private val rollbackDir = File(lutDir, "rollback").also { it.mkdirs() }

    fun importLut(uri: Uri): ImportedLut? {
        val fileName = resolveFileName(uri) ?: return null
        val targetFile = fileForName(fileName) ?: return null
        val hadExisting = targetFile.isFile
        if (hadExisting && !backupForRollback(targetFile)) return null
        return try {
            writeFileAtomically(targetFile, requireNonEmpty = true) { tempFile ->
                val input = context.contentResolver.openInputStream(uri)
                    ?: throw IOException("Cannot open LUT URI")
                input.use { source ->
                    tempFile.outputStream().use { output ->
                        copyWithLimit(source, output, MAX_LUT_BYTES)
                    }
                }
            }
            val lut = parse(targetFile) ?: run {
                targetFile.delete()
                if (hadExisting) restoreRollback(targetFile)
                return null
            }
            ImportedLut(
                fileName = targetFile.name,
                file = targetFile,
                size = lut.size,
                contentHash = DeclarativePackContract.sha256File(targetFile),
                canRollback = hadExisting,
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to import LUT", e)
            if (!hadExisting) targetFile.delete() else restoreRollback(targetFile)
            null
        }
    }

    fun listImportedLuts(): List<ImportedLut> {
        return lutDir.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in ALLOWED_EXTENSIONS }
            ?.mapNotNull { file ->
                val lut = parse(file) ?: return@mapNotNull null
                ImportedLut(
                    fileName = file.name,
                    file = file,
                    size = lut.size,
                    contentHash = runCatching { DeclarativePackContract.sha256File(file) }.getOrDefault(""),
                    canRollback = rollbackFileForName(file.name)?.isFile == true,
                )
            }
            ?.sortedBy { it.fileName.lowercase() }
            ?: emptyList()
    }

    fun deleteLut(fileName: String): Boolean {
        val file = fileForName(fileName) ?: return false
        if (!file.isFile || !backupForRollback(file)) return false
        return file.delete()
    }

    fun canRollback(fileName: String): Boolean =
        rollbackFileForName(fileName)?.isFile == true

    fun rollbackLut(fileName: String): Boolean {
        val target = fileForName(fileName) ?: return false
        val rollback = rollbackFileForName(fileName) ?: return false
        if (!rollback.isFile) return false
        return try {
            writeFileAtomically(target, requireNonEmpty = true) { tempFile ->
                rollback.inputStream().use { input ->
                    tempFile.outputStream().use { output -> input.copyTo(output) }
                }
            }
            if (parse(target) == null) return false
            rollback.delete()
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to roll back LUT: ${RedactedLog.assetId(fileName)}", e)
            false
        }
    }

    private fun resolveFileName(uri: Uri): String? {
        val raw = uri.lastPathSegment?.substringAfterLast('/')?.trim().orEmpty()
        if (raw.isBlank()) return null
        val extension = raw.substringAfterLast('.', "").lowercase()
        if (extension !in ALLOWED_EXTENSIONS) return null
        val sanitized = sanitizeFileNamePreservingExtension(raw, fallbackStem = "imported", maxLength = 80)
        return sanitized.takeIf { it.substringAfterLast('.', "").lowercase() in ALLOWED_EXTENSIONS }
    }

    private fun fileForName(fileName: String): File? {
        val raw = fileName.trim()
        val sanitized = sanitizeFileNamePreservingExtension(raw, fallbackStem = "", maxLength = 80)
        return sanitized.takeIf { it == raw && it.substringAfterLast('.', "").lowercase() in ALLOWED_EXTENSIONS }
            ?.let { File(lutDir, it) }
    }

    private fun rollbackFileForName(fileName: String): File? =
        fileForName(fileName)?.let { File(rollbackDir, it.name) }

    private fun backupForRollback(file: File): Boolean {
        val rollback = rollbackFileForName(file.name) ?: return false
        return try {
            writeFileAtomically(rollback, requireNonEmpty = true) { tempFile ->
                file.inputStream().use { input ->
                    tempFile.outputStream().use { output -> input.copyTo(output) }
                }
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to preserve LUT for rollback: ${file.redacted()}", e)
            false
        }
    }

    private fun restoreRollback(file: File): Boolean {
        val rollback = rollbackFileForName(file.name) ?: return false
        if (!rollback.isFile) return false
        return try {
            writeFileAtomically(file, requireNonEmpty = true) { tempFile ->
                rollback.inputStream().use { input ->
                    tempFile.outputStream().use { output -> input.copyTo(output) }
                }
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to restore LUT: ${file.redacted()}", e)
            false
        }
    }

    private fun parse(file: File): LutEngine.Lut3D? = when (file.extension.lowercase()) {
        "cube" -> LutEngine.parseCube(file)
        "3dl" -> LutEngine.parse3dl(file)
        else -> null
    }

    private companion object {
        const val TAG = "LutRegistry"
        const val MAX_LUT_BYTES = 32L * 1024L * 1024L
        val ALLOWED_EXTENSIONS = setOf("cube", "3dl")
    }
}

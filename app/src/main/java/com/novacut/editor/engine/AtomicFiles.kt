package com.novacut.editor.engine

import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal fun moveFileReplacing(sourceFile: File, targetFile: File) {
    val parentDir = targetFile.absoluteFile.parentFile
        ?: throw IOException("No parent directory for ${targetFile.absolutePath}")
    if (!parentDir.exists() && !parentDir.mkdirs() && !parentDir.exists()) {
        throw IOException("Failed to create directory ${parentDir.absolutePath}")
    }

    try {
        Files.move(
            sourceFile.toPath(),
            targetFile.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE
        )
    } catch (_: AtomicMoveNotSupportedException) {
        Files.move(
            sourceFile.toPath(),
            targetFile.toPath(),
            StandardCopyOption.REPLACE_EXISTING
        )
    }
}

internal fun writeUtf8TextAtomically(targetFile: File, contents: String) {
    writeFileAtomically(targetFile) { tempFile ->
        tempFile.writeText(contents, Charsets.UTF_8)
    }
}

/**
 * Force the file's data to stable storage before a rename commits it.
 * Rename atomicity does not imply data durability: on power loss the
 * journal can commit the rename while the temp file's blocks were never
 * flushed, yielding an empty/partial target. Android's own AtomicFile
 * syncs before rename for exactly this reason.
 */
internal fun syncFileData(file: File) {
    java.io.RandomAccessFile(file, "rw").use { it.fd.sync() }
}

internal fun writeFileAtomically(
    targetFile: File,
    requireNonEmpty: Boolean = false,
    writeContents: (File) -> Unit
) {
    val parentDir = targetFile.absoluteFile.parentFile
        ?: throw IOException("No parent directory for ${targetFile.absolutePath}")
    if (!parentDir.exists() && !parentDir.mkdirs() && !parentDir.exists()) {
        throw IOException("Failed to create directory ${parentDir.absolutePath}")
    }

    val tempFile = File.createTempFile(atomicTempPrefixFor(targetFile), ".tmp", parentDir)
    try {
        writeContents(tempFile)
        if (requireNonEmpty && (!tempFile.isFile || tempFile.length() <= 0L)) {
            throw IOException("Atomic write produced an empty file for ${targetFile.absolutePath}")
        }
        syncFileData(tempFile)
        moveFileReplacing(tempFile, targetFile)
    } catch (e: Exception) {
        tempFile.delete()
        throw e
    }
}

private fun atomicTempPrefixFor(targetFile: File): String {
    val base = targetFile.name.ifBlank { "output" }.take(48)
    return ".$base."
}

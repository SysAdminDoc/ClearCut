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
    val parentDir = targetFile.absoluteFile.parentFile
        ?: throw IOException("No parent directory for ${targetFile.absolutePath}")
    if (!parentDir.exists() && !parentDir.mkdirs() && !parentDir.exists()) {
        throw IOException("Failed to create directory ${parentDir.absolutePath}")
    }

    val tempFile = File.createTempFile("${targetFile.name}.", ".tmp", parentDir)
    try {
        tempFile.writeText(contents, Charsets.UTF_8)
        moveFileReplacing(tempFile, targetFile)
    } catch (e: Exception) {
        tempFile.delete()
        throw e
    }
}

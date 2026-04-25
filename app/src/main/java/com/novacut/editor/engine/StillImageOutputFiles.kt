package com.novacut.editor.engine

import java.io.File
import java.io.IOException
import java.util.UUID

private const val STILL_IMAGE_PARTIAL_MARKER = ".novacut-partial-"
private const val ABANDONED_STILL_IMAGE_PARTIAL_MAX_AGE_MS = 10 * 60 * 1000L

internal data class StillImageOutputFiles(
    val outputFile: File,
    val partialFile: File
)

internal fun createStillImageOutputFiles(outputFile: File): StillImageOutputFiles {
    val canonicalOutputFile = outputFile.absoluteFile
    val parentDir = canonicalOutputFile.parentFile
        ?: throw IOException("No parent directory for ${canonicalOutputFile.absolutePath}")
    if (!parentDir.exists() && !parentDir.mkdirs() && !parentDir.exists()) {
        throw IOException("Failed to create directory ${parentDir.absolutePath}")
    }
    sweepAbandonedStillImagePartials(parentDir, canonicalOutputFile.name)
    return StillImageOutputFiles(
        outputFile = canonicalOutputFile,
        partialFile = File(
            parentDir,
            ".${canonicalOutputFile.name}$STILL_IMAGE_PARTIAL_MARKER${UUID.randomUUID()}"
        )
    )
}

internal fun finalizeStillImageOutputFile(partialFile: File, outputFile: File): File? {
    if (!partialFile.isFile || partialFile.length() <= 0L) {
        cleanupStillImageOutputFile(partialFile)
        return null
    }
    moveFileReplacing(partialFile, outputFile)
    return if (outputFile.isFile && outputFile.length() > 0L) {
        outputFile
    } else {
        outputFile.delete()
        null
    }
}

internal fun cleanupStillImageOutputFile(partialFile: File?) {
    partialFile?.delete()
}

private fun sweepAbandonedStillImagePartials(dir: File, outputName: String) {
    val partialPrefix = ".$outputName$STILL_IMAGE_PARTIAL_MARKER"
    val cutoff = System.currentTimeMillis() - ABANDONED_STILL_IMAGE_PARTIAL_MAX_AGE_MS
    dir.listFiles()
        ?.filter { it.isFile && it.name.startsWith(partialPrefix) && it.lastModified() < cutoff }
        ?.forEach { it.delete() }
}

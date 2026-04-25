package com.novacut.editor.engine

import android.content.Context
import java.io.File
import java.util.UUID

internal const val STABILIZED_VIDEO_DIR_NAME = "stabilized"
private const val STABILIZED_VIDEO_FILE_PREFIX = "stabilized_"
private const val STABILIZED_VIDEO_PARTIAL_SUFFIX = ".partial.mp4"
private const val ABANDONED_STABILIZED_PARTIAL_MAX_AGE_MS = 10 * 60 * 1000L

internal data class StabilizedVideoOutputFiles(
    val outputFile: File,
    val partialFile: File
)

internal fun createStabilizedVideoOutputFiles(
    context: Context,
    clipId: String
): StabilizedVideoOutputFiles {
    val dir = File(context.filesDir, STABILIZED_VIDEO_DIR_NAME).also { it.mkdirs() }
    sweepAbandonedStabilizedVideoPartials(dir)
    val safeClipId = safeStabilizedVideoStem(clipId)
    val fileId = "${System.currentTimeMillis()}_${UUID.randomUUID()}"
    return StabilizedVideoOutputFiles(
        outputFile = File(dir, "${STABILIZED_VIDEO_FILE_PREFIX}${safeClipId}_$fileId.mp4"),
        partialFile = File(dir, "${STABILIZED_VIDEO_FILE_PREFIX}${safeClipId}_$fileId$STABILIZED_VIDEO_PARTIAL_SUFFIX")
    )
}

internal fun finalizeStabilizedVideoFile(partialFile: File, outputFile: File): File? {
    if (!partialFile.isFile || partialFile.length() <= 0L) {
        cleanupStabilizedVideoFiles(partialFile, outputFile)
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

internal fun cleanupStabilizedVideoFiles(partialFile: File, outputFile: File) {
    partialFile.delete()
    outputFile.delete()
}

internal fun safeStabilizedVideoStem(raw: String): String {
    return raw.replace(Regex("[^A-Za-z0-9_-]"), "_")
        .trim('_')
        .take(64)
        .ifBlank { "clip" }
}

private fun sweepAbandonedStabilizedVideoPartials(dir: File) {
    val cutoff = System.currentTimeMillis() - ABANDONED_STABILIZED_PARTIAL_MAX_AGE_MS
    dir.listFiles()
        ?.filter { it.isFile && it.name.endsWith(STABILIZED_VIDEO_PARTIAL_SUFFIX) && it.lastModified() < cutoff }
        ?.forEach { it.delete() }
}

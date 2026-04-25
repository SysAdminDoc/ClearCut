package com.novacut.editor.engine

import android.content.Context
import java.io.File
import java.util.Locale
import java.util.UUID

internal const val FRAME_CAPTURE_DIR_NAME = "frame_captures"
internal const val FREEZE_FRAME_DIR_NAME = "freeze_frames"
private const val FRAME_CAPTURE_FILE_PREFIX = "frame_"
private const val FREEZE_FRAME_FILE_PREFIX = "freeze_"
private const val FRAME_OUTPUT_PARTIAL_MARKER = ".partial."
private const val ABANDONED_FRAME_OUTPUT_PARTIAL_MAX_AGE_MS = 10 * 60 * 1000L

internal data class FrameOutputFiles(
    val outputFile: File,
    val partialFile: File
)

internal fun createFrameCaptureOutputFiles(
    context: Context,
    extension: String
): FrameOutputFiles {
    val dir = File(context.filesDir, FRAME_CAPTURE_DIR_NAME).also { it.mkdirs() }
    sweepAbandonedFrameOutputPartials(dir)
    val safeExtension = safeFrameOutputExtension(extension)
    val fileId = "${System.currentTimeMillis()}_${UUID.randomUUID()}"
    return FrameOutputFiles(
        outputFile = File(dir, "$FRAME_CAPTURE_FILE_PREFIX$fileId.$safeExtension"),
        partialFile = File(dir, "$FRAME_CAPTURE_FILE_PREFIX$fileId.partial.$safeExtension")
    )
}

internal fun createFreezeFrameOutputFiles(context: Context): FrameOutputFiles {
    val dir = File(context.filesDir, FREEZE_FRAME_DIR_NAME).also { it.mkdirs() }
    sweepAbandonedFrameOutputPartials(dir)
    val fileId = "${System.currentTimeMillis()}_${UUID.randomUUID()}"
    return FrameOutputFiles(
        outputFile = File(dir, "$FREEZE_FRAME_FILE_PREFIX$fileId.jpg"),
        partialFile = File(dir, "$FREEZE_FRAME_FILE_PREFIX$fileId.partial.jpg")
    )
}

internal fun finalizeFrameOutputFile(partialFile: File, outputFile: File): File? {
    if (!partialFile.isFile || partialFile.length() <= 0L) {
        cleanupFrameOutputFiles(partialFile, outputFile)
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

internal fun cleanupFrameOutputFiles(partialFile: File, outputFile: File) {
    partialFile.delete()
    outputFile.delete()
}

internal fun safeFrameOutputExtension(raw: String): String {
    return when (raw.trim().trimStart('.').lowercase(Locale.US)) {
        "jpg", "jpeg" -> "jpg"
        "png" -> "png"
        else -> "png"
    }
}

private fun sweepAbandonedFrameOutputPartials(dir: File) {
    val cutoff = System.currentTimeMillis() - ABANDONED_FRAME_OUTPUT_PARTIAL_MAX_AGE_MS
    dir.listFiles()
        ?.filter { it.isFile && it.name.contains(FRAME_OUTPUT_PARTIAL_MARKER) && it.lastModified() < cutoff }
        ?.forEach { it.delete() }
}

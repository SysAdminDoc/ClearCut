package com.novacut.editor.engine

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.net.Uri
import java.io.File

/** Resolves project dependencies without changing or substituting them. */
class AndroidProjectDependencyProbe(
    private val context: Context,
    private val modelReady: (String) -> Boolean,
) : ProjectDependencyProbe {

    override fun probe(request: ProjectDependencyRequest): ProjectDependencyStatus =
        when (request.kind) {
            ProjectDependencyKind.MEDIA -> probeReadableReference(request.reference)
            ProjectDependencyKind.LUT -> probeLut(request.reference)
            ProjectDependencyKind.CUSTOM_FONT -> probeFont(request.reference)
            ProjectDependencyKind.WATERMARK -> probeWatermark(request.reference)
            ProjectDependencyKind.MODEL -> if (modelReady(request.reference)) {
                ProjectDependencyStatus.AVAILABLE
            } else {
                ProjectDependencyStatus.MISSING
            }
        }

    private fun probeReadableReference(reference: String): ProjectDependencyStatus =
        runCatching {
            openReference(reference)?.use { input ->
                if (input.read() >= 0) ProjectDependencyStatus.AVAILABLE
                else ProjectDependencyStatus.INVALID
            } ?: ProjectDependencyStatus.MISSING
        }.getOrDefault(ProjectDependencyStatus.UNREADABLE)

    private fun probeLut(reference: String): ProjectDependencyStatus {
        val file = File(reference)
        if (!file.isFile) return ProjectDependencyStatus.MISSING
        if (!file.canRead()) return ProjectDependencyStatus.UNREADABLE
        val parsed = when (file.extension.lowercase()) {
            "cube" -> LutEngine.parseCube(file)
            "3dl" -> LutEngine.parse3dl(file)
            else -> return ProjectDependencyStatus.INVALID
        }
        return if (parsed != null) ProjectDependencyStatus.AVAILABLE else ProjectDependencyStatus.INVALID
    }

    private fun probeFont(reference: String): ProjectDependencyStatus {
        val file = File(reference)
        if (!file.isFile) return ProjectDependencyStatus.MISSING
        if (!file.canRead()) return ProjectDependencyStatus.UNREADABLE
        if (file.extension.lowercase() !in setOf("ttf", "otf")) return ProjectDependencyStatus.INVALID
        return runCatching { Typeface.createFromFile(file) }
            .fold(
                onSuccess = { ProjectDependencyStatus.AVAILABLE },
                onFailure = { ProjectDependencyStatus.INVALID },
            )
    }

    private fun probeWatermark(reference: String): ProjectDependencyStatus =
        runCatching {
            openReference(reference)?.use { input ->
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(input, null, options)
                if (options.outWidth > 0 && options.outHeight > 0) {
                    ProjectDependencyStatus.AVAILABLE
                } else {
                    ProjectDependencyStatus.INVALID
                }
            } ?: ProjectDependencyStatus.MISSING
        }.getOrDefault(ProjectDependencyStatus.UNREADABLE)

    private fun openReference(reference: String) = when (val uri = Uri.parse(reference)) {
        Uri.EMPTY -> null
        else -> when (uri.scheme?.lowercase()) {
            null, "" -> File(reference).takeIf(File::isFile)?.inputStream()
            "file" -> uri.path?.let(::File)?.takeIf(File::isFile)?.inputStream()
            "asset" -> uri.path?.trimStart('/')?.takeIf(String::isNotBlank)?.let(context.assets::open)
            else -> context.contentResolver.openInputStream(uri)
        }
    }
}

package com.novacut.editor.engine

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

enum class IncomingDocumentImportStatus {
    READY,
    BLOCKED,
    INVALID,
    IMPORTED,
}

data class IncomingDocumentImportPreview(
    val item: IncomingDocumentItem,
    val status: IncomingDocumentImportStatus,
    val title: String,
    val body: String,
    val details: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val canImportNow: Boolean = false,
)

@Singleton
class IncomingDocumentImportRouter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val templateManager: TemplateManager,
    private val effectShareEngine: EffectShareEngine,
    private val timelineImportEngine: TimelineImportEngine,
    private val stylePackManager: StylePackManager,
) {
    suspend fun preview(item: IncomingDocumentItem): IncomingDocumentImportPreview = withContext(Dispatchers.IO) {
        val readability = validateReadable(item)
        if (readability != null) return@withContext readability

        when (item.kind) {
            IncomingDocumentKind.TEMPLATE -> previewTemplate(item)
            IncomingDocumentKind.EFFECT_PACK -> previewEffectPack(item)
            IncomingDocumentKind.STYLE_PACK -> previewStylePack(item)
            IncomingDocumentKind.LUT_CUBE -> previewLut(item) { LutEngine.parseCube(it) }
            IncomingDocumentKind.LUT_3DL -> previewLut(item) { LutEngine.parse3dl(it) }
            IncomingDocumentKind.OPENFX_DESCRIPTOR -> previewOpenFxDescriptor(item)
            IncomingDocumentKind.PROJECT_ARCHIVE -> previewProjectArchive(item)
            IncomingDocumentKind.TIMELINE_OTIO,
            IncomingDocumentKind.TIMELINE_FCPXML,
            IncomingDocumentKind.TIMELINE_EDL -> previewTimelineImport(item)
        }
    }

    suspend fun importTemplate(item: IncomingDocumentItem): IncomingDocumentImportPreview = withContext(Dispatchers.IO) {
        if (item.kind != IncomingDocumentKind.TEMPLATE) {
            return@withContext invalid(item, "Only NovaCut template files can be imported from this preview.")
        }
        val readability = validateReadable(item)
        if (readability != null) return@withContext readability
        val result = templateManager.importTemplateFromUriDetailed(item.uri)
        val template = result.template
        if (template != null) {
            IncomingDocumentImportPreview(
                item = item,
                status = IncomingDocumentImportStatus.IMPORTED,
                title = "Template imported",
                body = "Saved \"${template.name}\" to Templates.",
                details = listOf(
                    "File kind: ${item.kind.displayName}",
                    "Target action: ${item.kind.targetAction}",
                    "Tracks: ${template.trackTypes.joinToString { it.name.lowercase() }}",
                    "Text overlays: ${template.textOverlayCount}",
                ),
                warnings = result.compatibilityReport?.issues.orEmpty().map { it.message },
                canImportNow = false,
            )
        } else {
            invalid(
                item = item,
                body = templateImportFailureMessage(result.failure),
                warnings = result.compatibilityReport?.issues.orEmpty().map { it.message },
            )
        }
    }

    suspend fun importStylePack(item: IncomingDocumentItem): IncomingDocumentImportPreview = withContext(Dispatchers.IO) {
        if (item.kind != IncomingDocumentKind.STYLE_PACK) {
            return@withContext invalid(item, "Only .ncstyle files can be imported as style packs.")
        }
        val readability = validateReadable(item)
        if (readability != null) return@withContext readability
        val result = stylePackManager.importFromUri(item.uri)
        val pack = result.pack
        if (pack != null) {
            IncomingDocumentImportPreview(
                item = item,
                status = IncomingDocumentImportStatus.IMPORTED,
                title = "Style pack installed",
                body = "\"${pack.name}\" added ${pack.styles.size} styles to the caption gallery.",
                details = listOf(
                    "File kind: ${item.kind.displayName}",
                    "Target action: ${item.kind.targetAction}",
                    "Pack: ${pack.name} v${pack.version}",
                    "Author: ${pack.author.ifBlank { "Unknown" }}",
                    "Styles: ${pack.styles.size}",
                ),
                warnings = result.warnings,
                canImportNow = false,
            )
        } else {
            invalid(
                item = item,
                body = stylePackFailureMessage(result.failure),
                warnings = result.warnings,
            )
        }
    }

    private fun previewStylePack(item: IncomingDocumentItem): IncomingDocumentImportPreview {
        val json = readText(item) ?: return invalid(item, "NovaCut could not read this style-pack file.")
        val result = stylePackManager.importFromJson(json)
        val pack = result.pack
        if (pack == null) {
            return invalid(item, stylePackFailureMessage(result.failure), result.warnings)
        }
        return IncomingDocumentImportPreview(
            item = item,
            status = IncomingDocumentImportStatus.READY,
            title = "Style pack ready to install",
            body = "\"${pack.name}\" contains ${pack.styles.size} caption/text styles.",
            details = baseDetails(item) + listOf(
                "Pack: ${pack.name} v${pack.version}",
                "Author: ${pack.author.ifBlank { "Unknown" }}",
                "License: ${pack.license.ifBlank { "Not specified" }}",
                "Styles: ${pack.styles.size}",
            ),
            warnings = result.warnings.ifEmpty { listOf("No project data was changed during preview.") },
            canImportNow = true,
        )
    }

    private fun previewTemplate(item: IncomingDocumentItem): IncomingDocumentImportPreview {
        return IncomingDocumentImportPreview(
            item = item,
            status = IncomingDocumentImportStatus.READY,
            title = "Template ready for review",
            body = "NovaCut can run the existing template compatibility checks before saving this file to Templates.",
            details = baseDetails(item),
            warnings = emptyList(),
            canImportNow = true,
        )
    }

    private suspend fun previewEffectPack(item: IncomingDocumentItem): IncomingDocumentImportPreview {
        val imported = effectShareEngine.importEffects(item.uri)
            ?: return invalid(item, "This .ncfx file did not match NovaCut's effect-pack schema.")
        return IncomingDocumentImportPreview(
            item = item,
            status = IncomingDocumentImportStatus.READY,
            title = "Effect pack validated",
            body = "Open an editor project, select a clip, and use Effect Library import to apply this pack.",
            details = baseDetails(item) + listOf(
                "Effect pack: ${imported.name}",
                "Video effects: ${imported.effects.size}",
                "Audio effects: ${imported.audioEffects.size}",
                "Color grade: ${if (imported.colorGrade != null) "included" else "not included"}",
            ),
            warnings = listOf("No clip was changed from the Projects screen."),
            canImportNow = false,
        )
    }

    private fun previewLut(
        item: IncomingDocumentItem,
        parse: (File) -> LutEngine.Lut3D?,
    ): IncomingDocumentImportPreview {
        val tempFile = copyToPreviewFile(item) ?: return invalid(item, "NovaCut could not copy this LUT for validation.")
        return try {
            val lut = parse(tempFile)
                ?: return invalid(item, "This LUT is malformed or uses an unsupported table shape.")
            IncomingDocumentImportPreview(
                item = item,
                status = IncomingDocumentImportStatus.READY,
                title = "LUT validated",
                body = "Open Color Grading in an editor project to apply this LUT to a selected clip.",
                details = baseDetails(item) + listOf(
                    "LUT size: ${lut.size}x${lut.size}x${lut.size}",
                    "Entries: ${lut.data.size / 3}",
                ),
                warnings = listOf("No project color grade was changed from the Projects screen."),
                canImportNow = false,
            )
        } finally {
            tempFile.delete()
        }
    }

    private fun previewOpenFxDescriptor(item: IncomingDocumentItem): IncomingDocumentImportPreview {
        val json = readText(item) ?: return invalid(item, "NovaCut could not read this descriptor.")
        val descriptor = OpenFxDescriptor.fromJson(json)
            ?: return invalid(item, "This .ncfxd file did not match NovaCut's OpenFX descriptor schema.")
        return IncomingDocumentImportPreview(
            item = item,
            status = IncomingDocumentImportStatus.READY,
            title = "OpenFX descriptor validated",
            body = "NovaCut can carry this metadata alongside effect packs for future timeline interchange.",
            details = baseDetails(item) + listOf(
                "NovaCut effect: ${descriptor.novaCutEffectId}",
                "OpenFX effect: ${descriptor.openfxId}",
                "Parameters: ${descriptor.parameters.size}",
            ),
            warnings = listOf("Descriptors are metadata only; no runtime effect was installed."),
            canImportNow = false,
        )
    }

    private suspend fun previewProjectArchive(item: IncomingDocumentItem): IncomingDocumentImportPreview {
        val previewDir = File(context.cacheDir, "document-import-preview/archive-${System.currentTimeMillis()}")
        return try {
            val result = ProjectArchive.importArchiveWithReport(
                context = context,
                archiveUri = item.uri,
                targetDir = previewDir,
                existingProjectIds = emptySet(),
                idCollisionPolicy = ProjectArchive.IdCollisionPolicy.REGENERATE,
            )
            val report = result.report
            if (result.state == null) {
                return invalid(
                    item = item,
                    body = result.errorMessage ?: "This archive could not be validated.",
                    warnings = report.warnings,
                )
            }
            IncomingDocumentImportPreview(
                item = item,
                status = IncomingDocumentImportStatus.READY,
                title = "Project archive validated",
                body = "Open an editor project and use Cloud Backup import to restore this archive intentionally.",
                details = baseDetails(item) + listOf(
                    "Archive report: ${report.summary}",
                    "Media: ${report.mediaResolved}/${report.mediaTotal} resolved",
                    "Schema version: ${report.schemaVersion}",
                ),
                warnings = report.warnings + listOf("Preview validation did not replace the current timeline."),
                canImportNow = false,
            )
        } finally {
            previewDir.deleteRecursively()
        }
    }

    private suspend fun previewTimelineImport(item: IncomingDocumentItem): IncomingDocumentImportPreview {
        val format = when (item.kind) {
            IncomingDocumentKind.TIMELINE_OTIO -> TimelineImportEngine.Format.OTIO
            IncomingDocumentKind.TIMELINE_FCPXML -> TimelineImportEngine.Format.FCPXML
            IncomingDocumentKind.TIMELINE_EDL -> TimelineImportEngine.Format.EDL
            else -> null
        } ?: return invalid(item, "Unknown timeline interchange format.")
        val fidelity = timelineImportEngine.roundTripFidelity(format)
        val result = timelineImportEngine.import(item.uri, format = format)
        return IncomingDocumentImportPreview(
            item = item,
            status = IncomingDocumentImportStatus.BLOCKED,
            title = "${format.displayName} import is not active yet",
            body = "NovaCut recognized the file and checked the target parser status without mutating project state.",
            details = baseDetails(item) + listOf(
                "Expected fidelity: ${fidelity.displayName}",
                fidelity.warningCopy,
            ),
            warnings = result.warnings.ifEmpty { listOf("${format.displayName} parser is not yet implemented.") },
            canImportNow = false,
        )
    }

    private fun validateReadable(item: IncomingDocumentItem): IncomingDocumentImportPreview? {
        if (item.uri.scheme != "content") {
            return invalid(item, "Only content:// document grants are accepted.")
        }
        val knownSize = item.sizeBytes
        if (knownSize != null && knownSize > item.kind.maxBytes) {
            return invalid(item, "This file is larger than NovaCut's ${item.kind.displayName} import limit.")
        }
        val readable = runCatching {
            context.contentResolver.openAssetFileDescriptor(item.uri, "r")?.use { descriptor ->
                descriptor.length != 0L
            } ?: false
        }.getOrDefault(false)
        return if (readable) null else invalid(item, "NovaCut could not read this document grant.")
    }

    private fun copyToPreviewFile(item: IncomingDocumentItem): File? {
        val dir = File(context.cacheDir, "document-import-preview").apply { mkdirs() }
        val extension = PluginRegistry.kindForFileName(item.displayName)?.fileExtension
            ?: ".bin"
        val file = File(dir, "preview-${System.currentTimeMillis()}$extension")
        return try {
            context.contentResolver.openInputStream(item.uri)?.use { input ->
                file.outputStream().use { output ->
                    copyWithLimit(input, output, item.kind.maxBytes)
                }
            } ?: return null
            file.takeIf { it.length() > 0L }
        } catch (_: Exception) {
            file.delete()
            null
        }
    }

    private fun readText(item: IncomingDocumentItem): String? {
        return try {
            context.contentResolver.openInputStream(item.uri)?.use { stream ->
                readUtf8WithByteLimit(stream, item.kind.maxBytes)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun baseDetails(item: IncomingDocumentItem): List<String> {
        return listOfNotNull(
            "File: ${item.displayName}",
            "File kind: ${item.kind.displayName}",
            "Target action: ${item.kind.targetAction}",
            item.mimeType?.let { "MIME type: $it" },
            item.sizeBytes?.let { "Size: ${formatBytes(it)}" },
        )
    }

    private fun invalid(
        item: IncomingDocumentItem,
        body: String,
        warnings: List<String> = emptyList(),
    ): IncomingDocumentImportPreview {
        return IncomingDocumentImportPreview(
            item = item,
            status = IncomingDocumentImportStatus.INVALID,
            title = "Document import blocked",
            body = body,
            details = baseDetails(item),
            warnings = warnings.ifEmpty { listOf("No project data was changed.") },
            canImportNow = false,
        )
    }

    private fun blocked(
        item: IncomingDocumentItem,
        body: String,
        warnings: List<String>,
    ): IncomingDocumentImportPreview {
        return IncomingDocumentImportPreview(
            item = item,
            status = IncomingDocumentImportStatus.BLOCKED,
            title = "${item.kind.displayName} support is pending",
            body = body,
            details = baseDetails(item),
            warnings = warnings,
            canImportNow = false,
        )
    }

    private fun stylePackFailureMessage(failure: StylePackFailure): String {
        return when (failure) {
            StylePackFailure.NONE -> "Style pack import failed."
            StylePackFailure.UNREADABLE -> "NovaCut could not read this file."
            StylePackFailure.INVALID_JSON -> "File is not valid JSON."
            StylePackFailure.MISSING_REQUIRED_FIELDS -> "Pack is missing required fields (id, name, or styles)."
            StylePackFailure.INCOMPATIBLE_VERSION -> "Pack requires a newer version of NovaCut."
            StylePackFailure.EMPTY_STYLES -> "Pack contains no styles."
            StylePackFailure.DUPLICATE_ID -> "Pack contains duplicate style IDs."
            StylePackFailure.OVERSIZED -> "Pack file is too large."
            StylePackFailure.WRITE_FAILED -> "Could not save pack to device storage."
        }
    }

    private fun templateImportFailureMessage(failure: TemplateImportFailure): String {
        return when (failure) {
            TemplateImportFailure.INCOMPATIBLE -> "Template needs a newer NovaCut version or unsupported tools."
            TemplateImportFailure.OVERSIZED_FILE -> "Template file is too large."
            TemplateImportFailure.INVALID_JSON,
            TemplateImportFailure.INVALID_STATE -> "Template file is not readable."
            TemplateImportFailure.UNREADABLE_FILE,
            TemplateImportFailure.WRITE_FAILED,
            TemplateImportFailure.NONE -> "Template import failed."
        }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024L) return "$bytes B"
        val kib = bytes / 1024.0
        if (kib < 1024.0) return "%.1f KB".format(kib)
        val mib = kib / 1024.0
        if (mib < 1024.0) return "%.1f MB".format(mib)
        return "%.2f GB".format(mib / 1024.0)
    }
}

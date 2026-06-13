package com.novacut.editor.engine

import android.content.Intent
import android.net.Uri
import android.os.Build
import java.util.Locale

enum class IncomingDocumentKind(
    val displayName: String,
    val targetAction: String,
    val maxBytes: Long,
) {
    TEMPLATE(
        displayName = "Project template",
        targetAction = "Save to Templates",
        maxBytes = 10_000_000L,
    ),
    EFFECT_PACK(
        displayName = "Effect pack",
        targetAction = "Validate for Effect Library",
        maxBytes = 1_000_000L,
    ),
    STYLE_PACK(
        displayName = "Caption / text style pack",
        targetAction = "Install to Caption Gallery",
        maxBytes = 1_000_000L,
    ),
    LUT_CUBE(
        displayName = "LUT (.cube)",
        targetAction = "Validate for Color Grading",
        maxBytes = 5_000_000L,
    ),
    LUT_3DL(
        displayName = "LUT (.3dl)",
        targetAction = "Validate for Color Grading",
        maxBytes = 5_000_000L,
    ),
    OPENFX_DESCRIPTOR(
        displayName = "OpenFX effect descriptor",
        targetAction = "Validate interchange metadata",
        maxBytes = 1_000_000L,
    ),
    PROJECT_ARCHIVE(
        displayName = "NovaCut project archive",
        targetAction = "Validate project archive",
        maxBytes = 4L * 1024L * 1024L * 1024L,
    ),
    TIMELINE_OTIO(
        displayName = "OpenTimelineIO timeline",
        targetAction = "Review timeline-import status",
        maxBytes = 25_000_000L,
    ),
    TIMELINE_FCPXML(
        displayName = "Final Cut Pro XML",
        targetAction = "Review timeline-import status",
        maxBytes = 25_000_000L,
    ),
    TIMELINE_EDL(
        displayName = "CMX 3600 EDL",
        targetAction = "Review timeline-import status",
        maxBytes = 5_000_000L,
    );

    companion object {
        fun fromPluginKind(kind: PluginRegistry.Kind): IncomingDocumentKind = when (kind) {
            PluginRegistry.Kind.TEMPLATE -> TEMPLATE
            PluginRegistry.Kind.EFFECT_PACK -> EFFECT_PACK
            PluginRegistry.Kind.STYLE_PACK -> STYLE_PACK
            PluginRegistry.Kind.LUT_CUBE -> LUT_CUBE
            PluginRegistry.Kind.LUT_3DL -> LUT_3DL
            PluginRegistry.Kind.OPENFX_DESCRIPTOR -> OPENFX_DESCRIPTOR
        }
    }
}

data class IncomingDocumentMetadata(
    val displayName: String?,
    val mimeType: String?,
    val sizeBytes: Long?,
)

data class IncomingDocumentItem(
    val uri: Uri,
    val kind: IncomingDocumentKind,
    val displayName: String,
    val mimeType: String?,
    val sizeBytes: Long?,
)

internal object IncomingDocumentIntentParser {
    private val documentMimeTypes = setOf(
        "application/octet-stream",
        "application/json",
        "application/zip",
        "application/x-zip-compressed",
        "application/xml",
        "text/xml",
        "text/plain",
    )

    fun parse(
        action: String?,
        dataUri: Uri?,
        streamUris: List<Uri>,
        clipDataUris: List<Uri>,
        intentMimeType: String?,
        hasReadGrant: Boolean,
        resolveMetadata: (Uri) -> IncomingDocumentMetadata
    ): List<IncomingDocumentItem> {
        val candidateUris = when (action) {
            Intent.ACTION_VIEW -> listOfNotNull(dataUri) + clipDataUris
            Intent.ACTION_SEND -> streamUris.ifEmpty { listOfNotNull(dataUri) } + clipDataUris
            Intent.ACTION_SEND_MULTIPLE -> streamUris + clipDataUris
            else -> return emptyList()
        }
        if (candidateUris.isEmpty()) return emptyList()
        if (action != Intent.ACTION_VIEW && !hasReadGrant) return emptyList()

        val seen = LinkedHashSet<String>()
        return candidateUris.mapNotNull { uri ->
            if (uri.scheme != "content") return@mapNotNull null
            if (!seen.add(uri.toString())) return@mapNotNull null

            val metadata = resolveMetadata(uri)
            val rawMimeType = metadata.mimeType ?: intentMimeType
            val mimeType = normalizeMimeType(rawMimeType)
            if (rawMimeType != null && mimeType == null) return@mapNotNull null
            val displayName = normalizeDisplayName(metadata.displayName, uri) ?: return@mapNotNull null
            val kind = classify(displayName = displayName, mimeType = mimeType) ?: return@mapNotNull null
            val sizeBytes = metadata.sizeBytes?.takeIf { it >= 0L }
            if (sizeBytes != null && (sizeBytes == 0L || sizeBytes > kind.maxBytes)) {
                return@mapNotNull null
            }
            IncomingDocumentItem(
                uri = uri,
                kind = kind,
                displayName = displayName,
                mimeType = mimeType,
                sizeBytes = sizeBytes,
            )
        }
    }

    fun parse(
        intent: Intent,
        resolveMetadata: (Uri) -> IncomingDocumentMetadata
    ): List<IncomingDocumentItem> {
        val action = intent.action
        val hasReadGrant = action == Intent.ACTION_VIEW ||
            (intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0
        return parse(
            action = action,
            dataUri = intent.data,
            streamUris = intent.streamUris(),
            clipDataUris = intent.clipDataUris(),
            intentMimeType = intent.type,
            hasReadGrant = hasReadGrant,
            resolveMetadata = resolveMetadata
        )
    }

    fun classify(displayName: String, mimeType: String?): IncomingDocumentKind? {
        val normalizedName = displayName.trim()
        PluginRegistry.kindForFileName(normalizedName)?.let { pluginKind ->
            return IncomingDocumentKind.fromPluginKind(pluginKind)
                .takeIf { mimeType == null || mimeType in documentMimeTypes }
        }

        val lower = normalizedName.lowercase(Locale.US)
        return when {
            lower.endsWith(".novacut") -> IncomingDocumentKind.PROJECT_ARCHIVE
            lower.endsWith(".zip") && mimeType.isZipMimeType() -> IncomingDocumentKind.PROJECT_ARCHIVE
            lower.endsWith(".otio") -> IncomingDocumentKind.TIMELINE_OTIO
            lower.endsWith(".fcpxml") -> IncomingDocumentKind.TIMELINE_FCPXML
            lower.endsWith(".edl") -> IncomingDocumentKind.TIMELINE_EDL
            else -> null
        }?.takeIf { mimeType == null || mimeType in documentMimeTypes }
    }

    private fun normalizeDisplayName(rawName: String?, uri: Uri): String? {
        val raw = rawName
            ?.substringAfterLast('/')
            ?.substringAfterLast('\\')
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment
                ?.substringAfterLast('/')
                ?.substringAfterLast('\\')
                ?.trim()
                ?.takeIf { it.isNotBlank() }
        return raw?.take(160)
    }

    private fun normalizeMimeType(raw: String?): String? {
        return raw
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase(Locale.US)
            ?.takeIf { it.isNotBlank() && it != "*/*" }
    }

    private fun String?.isZipMimeType(): Boolean {
        return this == "application/zip" ||
            this == "application/x-zip-compressed" ||
            this == "application/octet-stream"
    }

    private fun Intent.streamUris(): List<Uri> {
        val list = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
        }
        if (!list.isNullOrEmpty()) return list

        val single = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        }
        return listOfNotNull(single)
    }

    private fun Intent.clipDataUris(): List<Uri> {
        val clip = clipData ?: return emptyList()
        return (0 until clip.itemCount).mapNotNull { index ->
            clip.getItemAt(index).uri
        }
    }
}

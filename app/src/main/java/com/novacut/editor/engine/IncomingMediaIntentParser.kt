package com.novacut.editor.engine

import android.content.Intent
import android.net.Uri
import android.os.Build

enum class IncomingMediaKind(val mediaType: String) {
    VIDEO("video"),
    IMAGE("image"),
    AUDIO("audio");

    companion object {
        fun fromMimeType(mimeType: String?): IncomingMediaKind? {
            val normalized = mimeType
                ?.substringBefore(';')
                ?.trim()
                ?.lowercase()
                ?.takeIf { it.isNotBlank() }
                ?: return null
            return when {
                normalized.startsWith("video/") -> VIDEO
                normalized.startsWith("image/") -> IMAGE
                normalized.startsWith("audio/") -> AUDIO
                normalized == "application/ogg" -> AUDIO
                else -> null
            }
        }
    }
}

data class IncomingMediaItem(
    val uri: Uri,
    val kind: IncomingMediaKind
)

internal object IncomingMediaIntentParser {
    fun parse(
        action: String?,
        dataUri: Uri?,
        streamUris: List<Uri>,
        clipDataUris: List<Uri>,
        intentMimeType: String?,
        hasReadGrant: Boolean,
        resolveMimeType: (Uri) -> String?
    ): List<IncomingMediaItem> {
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
            val kind = IncomingMediaKind.fromMimeType(resolveMimeType(uri))
                ?: IncomingMediaKind.fromMimeType(intentMimeType)
                ?: return@mapNotNull null
            IncomingMediaItem(uri = uri, kind = kind)
        }
    }

    fun parse(
        intent: Intent,
        resolveMimeType: (Uri) -> String?
    ): List<IncomingMediaItem> {
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
            resolveMimeType = resolveMimeType
        )
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

package com.novacut.editor.ui.mediapicker

/**
 * MIME types that can identify a media-bearing drag before Android exposes its
 * ClipData. File managers commonly advertise a URI list or a generic file
 * stream, so those forms are accepted and resolved by URI metadata on drop.
 */
internal fun isSupportedMediaDropMimeType(mimeType: String): Boolean {
    val normalized = mimeType.trim().lowercase()
    return normalized == "*/*" ||
        normalized == "application/octet-stream" ||
        normalized == "text/uri-list" ||
        normalized.startsWith("video/") ||
        normalized.startsWith("image/") ||
        normalized.startsWith("audio/")
}

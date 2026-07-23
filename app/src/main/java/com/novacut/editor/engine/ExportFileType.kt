package com.novacut.editor.engine

private val stillImageExtensions = setOf("gif", "png", "jpg", "jpeg", "webp")
private val audioExtensions = setOf("m4a", "aac")

internal fun exportMimeTypeFor(fileName: String): String {
    return when (fileName.substringAfterLast('.', "").lowercase()) {
        "gif" -> "image/gif"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        "webm" -> "video/webm"
        "m4a" -> "audio/mp4"
        "aac" -> "audio/aac"
        else -> "video/mp4"
    }
}

internal fun exportUsesImageCollection(fileName: String): Boolean {
    return fileName.substringAfterLast('.', "").lowercase() in stillImageExtensions
}

internal fun exportUsesAudioCollection(fileName: String): Boolean {
    return fileName.substringAfterLast('.', "").lowercase() in audioExtensions
}

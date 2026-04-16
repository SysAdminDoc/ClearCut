package com.novacut.editor.engine

private val stillImageExtensions = setOf("gif", "png", "jpg", "jpeg", "webp")

internal fun exportMimeTypeFor(fileName: String): String {
    return when (fileName.substringAfterLast('.', "").lowercase()) {
        "gif" -> "image/gif"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        "webm" -> "video/webm"
        else -> "video/mp4"
    }
}

internal fun exportUsesImageCollection(fileName: String): Boolean {
    return fileName.substringAfterLast('.', "").lowercase() in stillImageExtensions
}

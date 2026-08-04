package com.novacut.editor.engine

import java.io.File

private val RESERVED_WINDOWS_FILE_NAMES = setOf(
    "CON", "PRN", "AUX", "NUL",
    "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
    "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
)

private val invalidFileNameChars = Regex("""[\\/:*?"<>|]""")
private val repeatedWhitespace = Regex("""\s+""")
private const val SIZE_MB_FILENAME_TOKEN = "{sizeMB}"

/**
 * Replace the post-export size token after an output has been fully written.
 * Returns the original file when the token is absent or the rename fails.
 */
internal fun finalizeFilenameSize(outputFile: File): File {
    if (!outputFile.name.contains(SIZE_MB_FILENAME_TOKEN)) return outputFile
    val mb = (outputFile.length() + 524_288L) / 1_048_576L
    val renamed = File(
        outputFile.parentFile,
        outputFile.name.replace(SIZE_MB_FILENAME_TOKEN, "${mb}MB")
    )
    return if (outputFile.renameTo(renamed)) renamed else outputFile
}

fun sanitizeFileName(
    raw: String,
    fallback: String = "ClearCut",
    maxLength: Int = 80
): String {
    val fallbackCandidate = fallback.trim().ifBlank { "ClearCut" }
    val normalized = raw
        .trim()
        .replace(invalidFileNameChars, "_")
        .map { ch -> if (ch.isISOControl()) '_' else ch }
        .joinToString("")
        .replace(repeatedWhitespace, " ")
        .trim()
        .trimEnd('.', ' ')

    var candidate = normalized.ifBlank { fallbackCandidate }
    if (candidate.uppercase() in RESERVED_WINDOWS_FILE_NAMES) {
        candidate = "${candidate}_"
    }

    val bounded = if (candidate.length > maxLength) {
        candidate.take(maxLength).trimEnd('.', ' ').ifBlank { fallbackCandidate }
    } else {
        candidate
    }

    return bounded.ifBlank { fallbackCandidate }
}

fun sanitizeFileNamePreservingExtension(
    raw: String,
    fallbackStem: String = "ClearCut",
    maxLength: Int = 80
): String {
    val trimmed = raw.trim()
    val rawExtension = trimmed
        .substringAfterLast('.', missingDelimiterValue = "")
        .takeIf { it.isNotBlank() && trimmed.contains('.') }
    val extension = rawExtension
        ?.lowercase()
        ?.filter(Char::isLetterOrDigit)
        ?.take(10)
        ?.ifBlank { null }

    val maxStemLength = (maxLength - ((extension?.length ?: 0) + if (extension != null) 1 else 0))
        .coerceAtLeast(1)
    val stemSource = if (extension != null) {
        trimmed.removeSuffix(".${rawExtension}")
    } else {
        trimmed
    }
    val stem = sanitizeFileName(
        raw = stemSource,
        fallback = fallbackStem,
        maxLength = maxStemLength
    )

    return if (extension != null) "$stem.$extension" else stem
}

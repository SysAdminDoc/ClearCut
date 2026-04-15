package com.novacut.editor.engine

private val RESERVED_WINDOWS_FILE_NAMES = setOf(
    "CON", "PRN", "AUX", "NUL",
    "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
    "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
)

private val invalidFileNameChars = Regex("""[\\/:*?"<>|]""")
private val repeatedWhitespace = Regex("""\s+""")

fun sanitizeFileName(
    raw: String,
    fallback: String = "NovaCut",
    maxLength: Int = 80
): String {
    val fallbackCandidate = fallback.trim().ifBlank { "NovaCut" }
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
    fallbackStem: String = "NovaCut",
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

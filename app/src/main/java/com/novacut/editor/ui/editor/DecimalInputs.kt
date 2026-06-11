package com.novacut.editor.ui.editor

import java.util.Locale

/**
 * Locale-tolerant numeric parsing for the editor's precision text fields.
 *
 * The fields display values with a dot decimal separator, but users in
 * comma-decimal locales (most of Europe, South America) type commas —
 * `toDoubleOrNull()` alone rejects those, silently dead-locking the field
 * (every keystroke parses to null, so the value never updates).
 */
internal fun parseEditorDecimal(text: String): Double? {
    val normalized = text.trim().replace(',', '.')
    if (normalized.count { it == '.' } > 1) return null
    return normalized.toDoubleOrNull()?.takeIf { it.isFinite() }
}

/** Formats with a dot decimal separator so the text round-trips through [parseEditorDecimal]. */
internal fun formatEditorDecimal(value: Double, decimals: Int): String =
    String.format(Locale.US, "%.${decimals}f", value)

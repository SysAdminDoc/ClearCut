package com.novacut.editor.ui.editor

/**
 * A held-back export. Preflight found warnings — most importantly render intents
 * the pipeline cannot honour — so the export waits here until the user accepts
 * them. Every item in [warnings] is shown verbatim before any work starts, and
 * the accepted set is written to export history when the user confirms.
 */
data class ExportConfirmationRequest(
    val outputDirPath: String,
    val preferredOutputName: String?,
    val summary: String,
    val warnings: List<String>,
    val intentFallbacks: List<ExportIntentFallback>,
) {
    /** One line per accepted item, for the export-history diagnostic record. */
    fun acceptedFallbackSummary(): String {
        if (intentFallbacks.isEmpty()) {
            return "User accepted ${warnings.size} export warning(s): " + warnings.joinToString(" | ")
        }
        val accepted = intentFallbacks.joinToString(" | ") { fallback ->
            "${fallback.stage}${fallback.subjectId?.let { "/$it" } ?: ""}: ${fallback.message}"
        }
        return "User accepted ${warnings.size} export warning(s), including " +
            "${intentFallbacks.size} render-intent fallback(s): $accepted"
    }
}

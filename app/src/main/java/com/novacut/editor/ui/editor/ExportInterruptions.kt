package com.novacut.editor.ui.editor

import android.content.Context
import android.content.Intent
import com.novacut.editor.R
import com.novacut.editor.engine.AppLog
import com.novacut.editor.engine.ExportHistoryEntry
import com.novacut.editor.model.ExportConfig
import java.io.File

/** The resumable export in flight: its output, and the partial it resumed from. */
internal data class ActiveResumeSession(
    val outputFile: File,
    val eligible: Boolean,
    val config: ExportConfig,
    val projectFingerprint: String,
    val configFingerprint: String,
    val sourcePartialFile: File? = null,
    val supersededHistoryId: String? = null,
)

/** An export Android stopped or refused to start, ready for its history row. */
internal data class InterruptedExport(
    val message: String,
    val diagnostic: String,
    val config: ExportConfig,
    val keptPartial: File?,
    val projectFingerprint: String?,
    val configFingerprint: String?,
    val supersededHistoryId: String?,
)

/**
 * Which partial a stopped resumable export keeps: the new output when it holds
 * frames, else the partial this run resumed from. The other one is deleted.
 */
internal fun settleResumePartial(
    session: ActiveResumeSession?,
    preservedOutput: File?,
    deleteOwned: (File) -> Unit,
): File? {
    if (session?.eligible != true) return null
    val keep = preservedOutput?.takeIf { it.isFile && it.length() > 0L }
        ?: session.sourcePartialFile
        ?: preservedOutput
        ?: session.outputFile
    val source = session.sourcePartialFile
    if (source != null) {
        deleteOwned(if (keep.absolutePath != source.absolutePath) source else session.outputFile)
    }
    return keep
}

/**
 * Starts the export service, or returns why Android refused: a start from the
 * background, or a media-processing budget that is already spent. Android 12+
 * throws ForegroundServiceStartNotAllowedException, an IllegalStateException.
 */
internal fun startExportService(context: Context, intent: Intent): RuntimeException? = try {
    context.startForegroundService(intent)
    null
} catch (e: IllegalStateException) {
    e
} catch (e: SecurityException) {
    e
}

/**
 * An export Android refused to start. Nothing was rendered, so the output is
 * removed; a partial this run meant to resume from stays, and the entry points at
 * it so Resume still works once the app is in front.
 */
internal fun serviceStartRefusal(
    context: Context,
    refusal: RuntimeException,
    config: ExportConfig,
    outputFile: File?,
    resumeCandidate: ExportHistoryEntry? = null,
    resumeSourceFile: File? = null,
): InterruptedExport {
    AppLog.w("ExportDelegate", "Export service start refused", refusal)
    outputFile?.takeIf { it.absolutePath != resumeSourceFile?.absolutePath }?.delete()
    val kept = resumeSourceFile?.takeIf { resumeCandidate != null && it.isFile && it.length() > 0L }
    return InterruptedExport(
        message = context.getString(R.string.export_service_start_refused),
        diagnostic = "Android refused to start the export service " +
            "(${refusal::class.java.simpleName}); nothing was rendered.",
        config = config,
        keptPartial = kept,
        projectFingerprint = resumeCandidate?.resumeProjectFingerprint.takeIf { kept != null },
        configFingerprint = resumeCandidate?.resumeConfigFingerprint.takeIf { kept != null },
        supersededHistoryId = resumeCandidate?.id,
    )
}

/**
 * A video export the service timeout stopped. Keeps whichever partial can resume
 * and says which way back the history row offers.
 */
internal fun serviceTimeout(
    context: Context,
    message: String,
    session: ActiveResumeSession?,
    preservedPartial: File?,
    fallbackConfig: ExportConfig,
    outputFile: File,
    deleteOwned: (File) -> Unit,
): InterruptedExport {
    val kept = settleResumePartial(session, preservedPartial, deleteOwned)
        ?.takeIf { it.isFile && it.length() > 0L }
    if (kept?.absolutePath != outputFile.absolutePath) outputFile.delete()
    return InterruptedExport(
        message = message,
        diagnostic = context.getString(
            if (kept != null) R.string.export_interrupted_resumable_note else R.string.export_interrupted_restart_note
        ),
        config = session?.config ?: fallbackConfig,
        keptPartial = kept,
        projectFingerprint = session?.projectFingerprint.takeIf { kept != null },
        configFingerprint = session?.configFingerprint.takeIf { kept != null },
        supersededHistoryId = session?.supersededHistoryId,
    )
}

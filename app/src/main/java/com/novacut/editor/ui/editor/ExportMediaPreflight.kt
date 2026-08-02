package com.novacut.editor.ui.editor

import com.novacut.editor.engine.AudioConformanceReport
import com.novacut.editor.engine.MediaHealthReport
import com.novacut.editor.engine.MediaHealthSeverity
import com.novacut.editor.engine.MediaRelinkProbe
import com.novacut.editor.engine.ProjectDependency
import com.novacut.editor.engine.ProjectDependencyManifest
import com.novacut.editor.engine.ProjectDependencyStatus

/**
 * A render intent the export cannot honour. Accepting one changes what the file
 * contains relative to what the timeline shows, so it needs explicit consent
 * before any work starts — never a log line after the fact.
 */
data class ExportIntentFallback(
    val stage: String,
    val subjectId: String?,
    val message: String,
)

data class ExportMediaPreflightResult(
    val canExport: Boolean,
    val blockingCount: Int,
    val warningCount: Int,
    val message: String,
    val audioConformance: AudioConformanceReport? = null,
    val dependencies: ProjectDependencyManifest = ProjectDependencyManifest(emptyList()),
    /** Every blocker, itemized, in the order the user should read them. */
    val blockers: List<String> = emptyList(),
    /** Every warning, itemized. Non-empty means the export needs consent. */
    val warnings: List<String> = emptyList(),
    /** The subset of [warnings] that silently change render intent. */
    val intentFallbacks: List<ExportIntentFallback> = emptyList(),
) {
    /** True when the user must acknowledge something before work begins. */
    val requiresConsent: Boolean get() = canExport && warnings.isNotEmpty()
}

object ExportMediaPreflight {

    fun evaluate(
        healthReport: MediaHealthReport?,
        relinkReports: Map<String, MediaRelinkProbe.ClipRelinkReport>,
        audioConformance: AudioConformanceReport? = null,
        dependencies: ProjectDependencyManifest = ProjectDependencyManifest(emptyList()),
        // Render intents the export pipeline already knows it cannot honour —
        // currently reversed clips whose backend is unavailable or that exceed
        // the reverse pre-render limit. They would export forward, which is a
        // different video, so they are surfaced here instead of logged mid-render.
        intentFallbacks: List<ExportIntentFallback> = emptyList(),
    ): ExportMediaPreflightResult {
        val blockers = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        healthReport?.issues.orEmpty().forEach { issue ->
            when (issue.severity) {
                MediaHealthSeverity.BLOCKING -> blockers += issue.message
                MediaHealthSeverity.WARNING -> warnings += issue.message
                else -> Unit
            }
        }

        relinkReports.values.forEach { relink ->
            when (relink.state) {
                MediaRelinkProbe.RelinkState.MISSING ->
                    blockers += "Clip ${relink.clipId}: source media is missing."
                MediaRelinkProbe.RelinkState.UNKNOWN ->
                    warnings += "Clip ${relink.clipId}: source media could not be verified."
                else -> Unit
            }
        }

        audioConformance?.let { audio ->
            audio.issues.forEach { issue ->
                if (issue.isBlocking) blockers += issue.message else warnings += issue.message
            }
            // Resampling can be required even when no conformance issue fired —
            // e.g. every clip agrees on 44.1 kHz but the export target is 48 kHz.
            // That still changes the audio, so it needs to be said out loud.
            val alreadyDisclosed = audio.issues.any { it.message.contains("resampl", ignoreCase = true) }
            if (audio.needsResampling && !alreadyDisclosed) {
                warnings += "Audio will be resampled to ${audio.targetSampleRate} Hz / " +
                    "${audio.targetChannelCount}ch on export."
            }
        }

        val dependencyBlockers = dependencies.blockingDependencies
        dependencyBlockers.forEach { dependency ->
            blockers += "${dependency.request.label} is ${dependency.status.name.lowercase()}."
        }
        val dependencyWarnings = dependencies.dependencies.filter {
            it.status != ProjectDependencyStatus.AVAILABLE && !it.blocksRequestedOperation
        }
        dependencyWarnings.forEach { dependency ->
            warnings += "${dependency.request.label} → ${dependency.request.fallbackName ?: "explicit fallback"}"
        }

        intentFallbacks.forEach { fallback -> warnings += fallback.message }

        val blockingCount = blockers.size
        val warningCount = warnings.size

        return when {
            blockingCount > 0 -> ExportMediaPreflightResult(
                canExport = false,
                blockingCount = blockingCount,
                warningCount = warningCount,
                message = blockedMessage(blockingCount, dependencyBlockers),
                audioConformance = audioConformance,
                dependencies = dependencies,
                blockers = blockers,
                warnings = warnings,
                intentFallbacks = intentFallbacks,
            )
            warningCount > 0 -> {
                val audioNote = if (audioConformance?.needsResampling == true) {
                    " Audio will be normalized to ${audioConformance.targetSampleRate} Hz / " +
                        "${audioConformance.targetChannelCount}ch."
                } else ""
                val dependencyNote = if (dependencyWarnings.isNotEmpty()) {
                    val fallbacks = dependencyWarnings.take(3).joinToString { dependency ->
                        "${dependency.request.label} → ${dependency.request.fallbackName ?: "explicit fallback"}"
                    }
                    " Fallbacks: $fallbacks."
                } else ""
                val fallbackNote = if (intentFallbacks.isNotEmpty()) {
                    " ${intentFallbacks.size} clip${if (intentFallbacks.size == 1) "" else "s"} " +
                        "cannot be rendered as edited and would be exported unchanged."
                } else ""
                ExportMediaPreflightResult(
                    canExport = true,
                    blockingCount = 0,
                    warningCount = warningCount,
                    message = if (warningCount == 1) {
                        "Export can continue with 1 warning.$audioNote$dependencyNote$fallbackNote"
                    } else {
                        "Export can continue with $warningCount warnings." +
                            "$audioNote$dependencyNote$fallbackNote"
                    },
                    audioConformance = audioConformance,
                    dependencies = dependencies,
                    blockers = blockers,
                    warnings = warnings,
                    intentFallbacks = intentFallbacks,
                )
            }
            else -> ExportMediaPreflightResult(
                canExport = true,
                blockingCount = 0,
                warningCount = 0,
                message = "Media ready for export.",
                audioConformance = audioConformance,
                dependencies = dependencies,
                blockers = blockers,
                warnings = warnings,
                intentFallbacks = intentFallbacks,
            )
        }
    }

    private fun blockedMessage(
        blockers: Int,
        dependencyBlockers: List<ProjectDependency>,
    ): String {
        if (dependencyBlockers.isNotEmpty()) {
            val names = dependencyBlockers.take(3).joinToString { dependency ->
                "${dependency.request.label} (${dependency.status.name.lowercase()})"
            }
            val remainder = dependencyBlockers.size - 3
            val suffix = if (remainder > 0) " and $remainder more" else ""
            return "Export blocked by $blockers issue${if (blockers == 1) "" else "s"}. " +
                "Required dependencies: $names$suffix. Restore or replace them before export."
        }
        return if (blockers == 1) {
            "Export blocked by 1 media issue. Open Media Manager to relink or repair it."
        } else {
            "Export blocked by $blockers media issues. Open Media Manager to relink or repair them."
        }
    }
}

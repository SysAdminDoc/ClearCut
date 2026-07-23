package com.novacut.editor.ui.editor

import com.novacut.editor.engine.AudioConformanceReport
import com.novacut.editor.engine.MediaHealthReport
import com.novacut.editor.engine.MediaHealthSeverity
import com.novacut.editor.engine.MediaRelinkProbe
import com.novacut.editor.engine.ProjectDependencyManifest
import com.novacut.editor.engine.ProjectDependencyStatus

data class ExportMediaPreflightResult(
    val canExport: Boolean,
    val blockingCount: Int,
    val warningCount: Int,
    val message: String,
    val audioConformance: AudioConformanceReport? = null,
    val dependencies: ProjectDependencyManifest = ProjectDependencyManifest(emptyList()),
)

object ExportMediaPreflight {

    fun evaluate(
        healthReport: MediaHealthReport?,
        relinkReports: Map<String, MediaRelinkProbe.ClipRelinkReport>,
        audioConformance: AudioConformanceReport? = null,
        dependencies: ProjectDependencyManifest = ProjectDependencyManifest(emptyList()),
        // Number of tracks carrying mixer pan or audio-effect edits that the
        // current render path does not yet apply. The pan/DSP mixer controls are
        // withheld from the UI (they are not rendered in preview or export), but
        // a project imported or created before they were withheld can still carry
        // saved values. Surfacing them here keeps those edits from being silently
        // dropped on export.
        unrenderedMixerEditCount: Int = 0,
    ): ExportMediaPreflightResult {
        val healthBlockers = healthReport?.issues
            ?.count { it.severity == MediaHealthSeverity.BLOCKING }
            ?: 0
        val healthWarnings = healthReport?.issues
            ?.count { it.severity == MediaHealthSeverity.WARNING }
            ?: 0
        val missingSources = relinkReports.values.count {
            it.state == MediaRelinkProbe.RelinkState.MISSING
        }
        val unknownSources = relinkReports.values.count {
            it.state == MediaRelinkProbe.RelinkState.UNKNOWN
        }
        val audioBlockers = audioConformance?.blockingCount ?: 0
        val audioWarnings = audioConformance?.warningCount ?: 0
        val dependencyBlockers = dependencies.blockingDependencies
        val dependencyWarnings = dependencies.dependencies.filter {
            it.status != ProjectDependencyStatus.AVAILABLE && !it.blocksRequestedOperation
        }

        val mixerEditWarnings = unrenderedMixerEditCount.coerceAtLeast(0)

        val blockers = healthBlockers + missingSources + audioBlockers + dependencyBlockers.size
        val warnings = healthWarnings + unknownSources + audioWarnings +
            dependencyWarnings.size + mixerEditWarnings
        return when {
            blockers > 0 -> ExportMediaPreflightResult(
                canExport = false,
                blockingCount = blockers,
                warningCount = warnings,
                message = if (dependencyBlockers.isNotEmpty()) {
                    val names = dependencyBlockers.take(3).joinToString { dependency ->
                        "${dependency.request.label} (${dependency.status.name.lowercase()})"
                    }
                    val remainder = dependencyBlockers.size - 3
                    val suffix = if (remainder > 0) " and $remainder more" else ""
                    "Export blocked by $blockers issue${if (blockers == 1) "" else "s"}. " +
                        "Required dependencies: $names$suffix. Restore or replace them before export."
                } else if (blockers == 1) {
                    "Export blocked by 1 media issue. Open Media Manager to relink or repair it."
                } else {
                    "Export blocked by $blockers media issues. Open Media Manager to relink or repair them."
                },
                audioConformance = audioConformance,
                dependencies = dependencies,
            )
            warnings > 0 -> {
                val audioNote = if (audioConformance?.needsResampling == true) {
                    " Audio will be normalized to ${audioConformance.targetSampleRate} Hz / ${audioConformance.targetChannelCount}ch."
                } else ""
                val dependencyNote = if (dependencyWarnings.isNotEmpty()) {
                    val fallbacks = dependencyWarnings.take(3).joinToString { dependency ->
                        "${dependency.request.label} → ${dependency.request.fallbackName ?: "explicit fallback"}"
                    }
                    " Fallbacks: $fallbacks."
                } else ""
                val mixerNote = if (mixerEditWarnings > 0) {
                    " $mixerEditWarnings track${if (mixerEditWarnings == 1) "" else "s"} " +
                        "use pan or audio effects that are not rendered yet and are ignored in this export."
                } else ""
                ExportMediaPreflightResult(
                    canExport = true,
                    blockingCount = 0,
                    warningCount = warnings,
                    message = if (warnings == 1) {
                        "Export can continue with 1 warning.$audioNote$dependencyNote$mixerNote"
                    } else {
                        "Export can continue with $warnings warnings.$audioNote$dependencyNote$mixerNote"
                    },
                    audioConformance = audioConformance,
                    dependencies = dependencies,
                )
            }
            else -> ExportMediaPreflightResult(
                canExport = true,
                blockingCount = 0,
                warningCount = 0,
                message = "Media ready for export.",
                audioConformance = audioConformance,
                dependencies = dependencies,
            )
        }
    }
}

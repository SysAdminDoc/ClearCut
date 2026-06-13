package com.novacut.editor.ui.editor

import com.novacut.editor.engine.AudioConformanceReport
import com.novacut.editor.engine.MediaHealthReport
import com.novacut.editor.engine.MediaHealthSeverity
import com.novacut.editor.engine.MediaRelinkProbe

data class ExportMediaPreflightResult(
    val canExport: Boolean,
    val blockingCount: Int,
    val warningCount: Int,
    val message: String,
    val audioConformance: AudioConformanceReport? = null,
)

object ExportMediaPreflight {

    fun evaluate(
        healthReport: MediaHealthReport?,
        relinkReports: Map<String, MediaRelinkProbe.ClipRelinkReport>,
        audioConformance: AudioConformanceReport? = null,
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

        val blockers = healthBlockers + missingSources + audioBlockers
        val warnings = healthWarnings + unknownSources + audioWarnings
        return when {
            blockers > 0 -> ExportMediaPreflightResult(
                canExport = false,
                blockingCount = blockers,
                warningCount = warnings,
                message = if (blockers == 1) {
                    "Export blocked by 1 media issue. Open Media Manager to relink or repair it."
                } else {
                    "Export blocked by $blockers media issues. Open Media Manager to relink or repair them."
                },
                audioConformance = audioConformance,
            )
            warnings > 0 -> {
                val audioNote = if (audioConformance?.needsResampling == true) {
                    " Audio will be normalized to ${audioConformance.targetSampleRate} Hz / ${audioConformance.targetChannelCount}ch."
                } else ""
                ExportMediaPreflightResult(
                    canExport = true,
                    blockingCount = 0,
                    warningCount = warnings,
                    message = if (warnings == 1) {
                        "Export can continue with 1 media warning.$audioNote"
                    } else {
                        "Export can continue with $warnings media warnings.$audioNote"
                    },
                    audioConformance = audioConformance,
                )
            }
            else -> ExportMediaPreflightResult(
                canExport = true,
                blockingCount = 0,
                warningCount = 0,
                message = "Media ready for export.",
                audioConformance = audioConformance,
            )
        }
    }
}

package com.novacut.editor.ui.editor

import com.novacut.editor.engine.ExportState
import com.novacut.editor.model.BatchExportItem
import com.novacut.editor.model.Track

/**
 * The editor state one batch item renders from. Items keep their own export settings,
 * but color follows the project, the same as the preview: a preset item would otherwise
 * carry the default SDR policy into a Keep HDR project.
 */
internal fun EditorState.forBatchItem(item: BatchExportItem): EditorState {
    val config = item.config.copy(colorPolicy = exportConfig.colorPolicy)
    val sourceRange = item.sourceRange ?: return copyExport { export ->
        export.copy(
            config = config,
            state = ExportState.IDLE,
            progress = 0f,
            errorMessage = null,
            pendingConfirmation = null,
        )
    }
    val sourceClip = sourceRange.toClip("batch-${item.id}-${sourceRange.clipId}")
    val sourceTrack = Track(
        id = "batch-${item.id}-track",
        type = sourceRange.trackType,
        index = 0,
        clips = listOf(sourceClip),
    )
    return copy(
        tracks = listOf(sourceTrack),
        selectedClipId = sourceClip.id,
        selectedTrackId = sourceTrack.id,
        selectedClipIds = setOf(sourceClip.id),
        totalDurationMs = sourceClip.durationMs,
        textOverlays = emptyList(),
        imageOverlays = emptyList(),
        timelineMarkers = emptyList(),
        globalTransitions = emptyList(),
        trackedObjects = emptyList(),
    ).copyExport { export ->
        export.copy(
            config = config.copy(timelineRange = null),
            state = ExportState.IDLE,
            progress = 0f,
            errorMessage = null,
            pendingConfirmation = null,
        )
    }
}

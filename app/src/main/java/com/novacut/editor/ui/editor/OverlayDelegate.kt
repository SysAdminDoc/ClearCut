package com.novacut.editor.ui.editor

import android.content.Context
import android.net.Uri
import com.novacut.editor.R
import com.novacut.editor.engine.CutListParser
import com.novacut.editor.model.ImageOverlay
import com.novacut.editor.model.ImageOverlayType
import com.novacut.editor.model.MarkerColor
import com.novacut.editor.model.TextOverlay
import com.novacut.editor.model.TimelineMarker
import kotlinx.coroutines.flow.MutableStateFlow

class OverlayDelegate(
    private val stateFlow: MutableStateFlow<EditorState>,
    private val saveUndoState: (String) -> Unit,
    private val showToast: (String) -> Unit,
    private val saveProject: () -> Unit,
    private val appContext: Context
) {
    // --- Text Overlays ---

    fun addTextOverlay(text: TextOverlay) {
        if (text.startTimeMs >= text.endTimeMs) { showToast(appContext.getString(R.string.overlay_invalid_duration_toast)); return }
        saveUndoState("Add text")
        stateFlow.update { it.copy(textOverlays = it.textOverlays + text) }
        saveProject()
    }

    fun updateTextOverlay(textOverlay: TextOverlay) {
        if (textOverlay.startTimeMs >= textOverlay.endTimeMs) { showToast(appContext.getString(R.string.overlay_invalid_duration_toast)); return }
        saveUndoState("Edit text")
        stateFlow.update { state ->
            state.copy(
                textOverlays = state.textOverlays.map {
                    if (it.id == textOverlay.id) textOverlay else it
                }
            )
        }
        saveProject()
    }

    fun removeTextOverlay(id: String) {
        saveUndoState("Remove text")
        stateFlow.update { state ->
            state.copy(
                textOverlays = state.textOverlays.filterNot { it.id == id }
            ).copyPanel { panel ->
                panel.copy(
                    editingTextOverlayId = if (panel.editingTextOverlayId == id) null else panel.editingTextOverlayId
                )
            }
        }
        saveProject()
    }

    // --- Image/Sticker Overlays ---

    fun addImageOverlay(uri: Uri, type: ImageOverlayType = ImageOverlayType.STICKER) {
        saveUndoState("Add sticker")
        // Single snapshot read so start/end can't fall out of sync if the user
        // scrubs the playhead between the two `stateFlow.value` accesses.
        val snapshot = stateFlow.value
        val startMs = snapshot.playheadMs
        val endMs = minOf(startMs + 5000L, snapshot.totalDurationMs.coerceAtLeast(startMs + 1000L))
        val overlay = ImageOverlay(
            sourceUri = uri,
            startTimeMs = startMs,
            endTimeMs = endMs,
            type = type
        )
        stateFlow.update { it.copy(imageOverlays = it.imageOverlays + overlay) }
        saveProject()
        showToast(appContext.getString(R.string.overlay_sticker_added_toast))
    }

    fun beginImageOverlayAdjust() {
        saveUndoState("Edit sticker")
    }

    fun updateImageOverlay(id: String, positionX: Float? = null, positionY: Float? = null, scale: Float? = null, rotation: Float? = null, opacity: Float? = null) {
        stateFlow.update { s ->
            s.copy(imageOverlays = s.imageOverlays.map { o ->
                if (o.id == id) o.copy(
                    positionX = positionX.safeOverlayFloat(o.positionX).coerceIn(-5f, 5f),
                    positionY = positionY.safeOverlayFloat(o.positionY).coerceIn(-5f, 5f),
                    scale = scale.safeOverlayFloat(o.scale).coerceIn(0.01f, 100f),
                    rotation = rotation.safeOverlayFloat(o.rotation),
                    opacity = opacity.safeOverlayFloat(o.opacity).coerceIn(0f, 1f)
                ) else o
            })
        }
    }

    fun endImageOverlayAdjust() {
        saveProject()
    }

    fun removeImageOverlay(id: String) {
        saveUndoState("Remove sticker")
        stateFlow.update { it.copy(imageOverlays = it.imageOverlays.filter { o -> o.id != id }) }
        saveProject()
    }

    // --- Timeline Markers ---

    fun addTimelineMarker(label: String = "", color: MarkerColor = MarkerColor.BLUE) {
        saveUndoState("Add marker")
        val marker = TimelineMarker(timeMs = stateFlow.value.playheadMs, label = label, color = color)
        stateFlow.update { it.copy(timelineMarkers = (it.timelineMarkers + marker).sortedBy { m -> m.timeMs }) }
        saveProject()
        showToast(appContext.getString(R.string.overlay_marker_added_toast))
    }

    fun deleteTimelineMarker(id: String) {
        saveUndoState("Delete marker")
        stateFlow.update { state -> state.copy(timelineMarkers = state.timelineMarkers.filter { it.id != id }) }
        saveProject()
    }

    /**
     * Parse a pasted / imported cut list and apply every valid row as timeline
     * markers in a single undoable batch. Point rows become one marker; range
     * rows become a bracketing "in"/"out" marker pair the user can act on with
     * the existing marker + cut tools. Invalid rows are NOT applied — the full
     * [CutListParser.Result] is returned so the caller can surface per-row
     * errors for the user to fix. Nothing is committed when there is no valid
     * row (avoids an empty undo entry).
     */
    fun applyCutList(text: String): CutListParser.Result {
        val result = CutListParser.parse(text)
        if (result.entries.isEmpty()) {
            showToast(
                if (result.hasErrors) {
                    appContext.getString(R.string.cut_list_import_no_valid_rows, result.errors.size)
                } else {
                    appContext.getString(R.string.cut_list_import_empty)
                }
            )
            return result
        }
        saveUndoState("Import cut list")
        val newMarkers = buildList {
            result.entries.forEach { entry ->
                val start = entry.startMs.coerceAtLeast(0L)
                val end = entry.endMs
                if (end == null) {
                    add(TimelineMarker(timeMs = start, label = entry.label, color = MarkerColor.BLUE))
                } else {
                    val base = entry.label.ifBlank { appContext.getString(R.string.cut_list_default_label) }
                    add(
                        TimelineMarker(
                            timeMs = start,
                            label = appContext.getString(R.string.cut_list_range_in, base),
                            color = MarkerColor.ORANGE
                        )
                    )
                    add(
                        TimelineMarker(
                            timeMs = end.coerceAtLeast(0L),
                            label = appContext.getString(R.string.cut_list_range_out, base),
                            color = MarkerColor.ORANGE
                        )
                    )
                }
            }
        }
        stateFlow.update {
            it.copy(timelineMarkers = (it.timelineMarkers + newMarkers).sortedBy { m -> m.timeMs })
        }
        saveProject()
        val markerCount = result.entries.count { !it.isRange }
        val cutCount = result.entries.count { it.isRange }
        showToast(
            appContext.getString(
                R.string.cut_list_imported_summary,
                markerCount,
                cutCount,
                result.errors.size
            )
        )
        return result
    }

}

private fun Float?.safeOverlayFloat(default: Float): Float {
    return if (this != null && isFinite()) this else default
}

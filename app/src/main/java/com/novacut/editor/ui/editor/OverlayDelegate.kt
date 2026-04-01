package com.novacut.editor.ui.editor

import android.net.Uri
import com.novacut.editor.model.ImageOverlay
import com.novacut.editor.model.ImageOverlayType
import com.novacut.editor.model.MarkerColor
import com.novacut.editor.model.TextOverlay
import com.novacut.editor.model.TimelineMarker
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Delegate handling text overlays, image/sticker overlays, and timeline markers.
 * Extracted from EditorViewModel to reduce its size.
 */
class OverlayDelegate(
    private val stateFlow: MutableStateFlow<EditorState>,
    private val saveUndoState: (String) -> Unit,
    private val showToast: (String) -> Unit,
    private val saveProject: () -> Unit
) {
    // --- Text Overlays ---

    fun addTextOverlay(text: TextOverlay) {
        if (text.startTimeMs >= text.endTimeMs) { showToast("Invalid text overlay duration"); return }
        saveUndoState("Add text")
        stateFlow.update { it.copy(textOverlays = it.textOverlays + text) }
        saveProject()
    }

    fun updateTextOverlay(textOverlay: TextOverlay) {
        if (textOverlay.startTimeMs >= textOverlay.endTimeMs) { showToast("Invalid text overlay duration"); return }
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
                textOverlays = state.textOverlays.filterNot { it.id == id },
                editingTextOverlayId = if (state.editingTextOverlayId == id) null else state.editingTextOverlayId
            )
        }
        saveProject()
    }

    // --- Image/Sticker Overlays ---

    fun addImageOverlay(uri: Uri, type: ImageOverlayType = ImageOverlayType.STICKER) {
        saveUndoState("Add sticker")
        val overlay = ImageOverlay(
            sourceUri = uri,
            startTimeMs = stateFlow.value.playheadMs,
            endTimeMs = minOf(stateFlow.value.playheadMs + 5000L, stateFlow.value.totalDurationMs.coerceAtLeast(stateFlow.value.playheadMs + 1000L)),
            type = type
        )
        stateFlow.update { it.copy(imageOverlays = it.imageOverlays + overlay) }
        saveProject()
        showToast("Sticker added")
    }

    fun updateImageOverlay(id: String, positionX: Float? = null, positionY: Float? = null, scale: Float? = null, rotation: Float? = null, opacity: Float? = null) {
        saveUndoState("Edit sticker")
        stateFlow.update { s ->
            s.copy(imageOverlays = s.imageOverlays.map { o ->
                if (o.id == id) o.copy(
                    positionX = positionX ?: o.positionX,
                    positionY = positionY ?: o.positionY,
                    scale = scale ?: o.scale,
                    rotation = rotation ?: o.rotation,
                    opacity = opacity ?: o.opacity
                ) else o
            })
        }
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
        showToast("Marker added")
    }

    fun deleteTimelineMarker(id: String) {
        saveUndoState("Delete marker")
        stateFlow.update { state -> state.copy(timelineMarkers = state.timelineMarkers.filter { it.id != id }) }
        saveProject()
    }

}

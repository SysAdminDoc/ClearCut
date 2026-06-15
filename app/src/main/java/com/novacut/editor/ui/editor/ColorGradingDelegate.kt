package com.novacut.editor.ui.editor

import android.content.Context
import android.net.Uri
import android.util.Log
import com.novacut.editor.R
import com.novacut.editor.engine.copyWithLimit
import com.novacut.editor.engine.sanitizeFileNamePreservingExtension
import com.novacut.editor.engine.writeFileAtomically
import com.novacut.editor.model.ColorGrade
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

private const val MAX_LUT_IMPORT_BYTES = 32L * 1024L * 1024L

/**
 * Delegate handling color grading, LUT import, and scope operations.
 * Extracted from EditorViewModel to reduce its size.
 */
class ColorGradingDelegate(
    private val stateFlow: MutableStateFlow<EditorState>,
    private val appContext: Context,
    private val scope: CoroutineScope,
    private val saveUndoState: (String) -> Unit,
    private val showToast: (String) -> Unit,
    private val pauseIfPlaying: () -> Unit,
    private val dismissedPanelState: (EditorState) -> EditorState,
    private val getSelectedClip: () -> com.novacut.editor.model.Clip?,
    private val updatePreview: () -> Unit,
    private val saveProject: () -> Unit
) {
    private val _showLutPicker = MutableStateFlow(false)
    val showLutPicker: StateFlow<Boolean> = _showLutPicker.asStateFlow()

    private fun text(resId: Int, vararg args: Any): String =
        appContext.getString(resId, *args)

    fun showColorGrading() {
        pauseIfPlaying()
        stateFlow.update {
            dismissedPanelState(it).copyPanel { panel ->
                panel.copy(panels = panel.panels.closeAll().open(PanelId.COLOR_GRADING))
            }
        }
    }

    fun hideColorGrading() {
        stateFlow.update {
            it.copyPanel { panel -> panel.copy(panels = panel.panels.close(PanelId.COLOR_GRADING)) }
        }
    }

    fun beginColorGradeAdjust() {
        saveUndoState("Color grade")
    }

    fun endColorGradeAdjust() {
        saveProject()
    }

    fun updateClipColorGrade(colorGrade: ColorGrade) {
        val clipId = stateFlow.value.selectedClipId ?: return
        stateFlow.update { s ->
            s.copy(tracks = s.tracks.map { track ->
                track.copy(clips = track.clips.map { clip ->
                    if (clip.id == clipId) clip.copy(colorGrade = colorGrade) else clip
                })
            })
        }
        updatePreview()
    }

    fun importLut() {
        _showLutPicker.value = true
    }

    fun onLutPickerDismissed() {
        _showLutPicker.value = false
    }

    fun onLutFileSelected(uri: Uri) {
        _showLutPicker.value = false
        scope.launch(Dispatchers.IO) {
            try {
                val lutDir = File(appContext.filesDir, "luts").also { it.mkdirs() }
                val rawFileName = uri.lastPathSegment?.substringAfterLast('/') ?: "imported.cube"
                val fileName = sanitizeFileNamePreservingExtension(
                    raw = rawFileName,
                    fallbackStem = "imported",
                    maxLength = 80
                ).let { sanitized ->
                    if (sanitized.contains('.')) sanitized else "$sanitized.cube"
                }
                val destFile = File(lutDir, fileName)
                writeFileAtomically(destFile, requireNonEmpty = true) { tempFile ->
                    val inputStream = appContext.contentResolver.openInputStream(uri)
                        ?: throw IOException("Cannot open LUT file")
                    inputStream.use { input ->
                        tempFile.outputStream().use { output ->
                            copyWithLimit(input, output, MAX_LUT_IMPORT_BYTES)
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    setClipLut(destFile.absolutePath)
                    showToast("LUT applied: $fileName")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    val message = if (
                        e is IOException &&
                        e.message?.contains("byte limit", ignoreCase = true) == true
                    ) {
                        text(R.string.color_lut_too_large_toast)
                    } else {
                        Log.e("ColorGradingDelegate", "Failed to import LUT", e)
                        text(R.string.color_lut_import_failed_toast)
                    }
                    showToast(message)
                }
            }
        }
    }

    fun setClipLut(lutPath: String) {
        val clip = getSelectedClip() ?: return
        saveUndoState("Apply LUT")
        val currentGrade = clip.colorGrade ?: ColorGrade()
        updateClipColorGrade(currentGrade.copy(lutPath = lutPath))
    }

}

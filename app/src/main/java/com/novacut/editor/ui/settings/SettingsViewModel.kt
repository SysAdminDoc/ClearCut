package com.novacut.editor.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novacut.editor.engine.AppSettings
import com.novacut.editor.engine.DiagnosticExportEngine
import com.novacut.editor.engine.ModelDownloadManager
import com.novacut.editor.engine.SettingsRepository
import com.novacut.editor.engine.segmentation.SegmentationEngine
import com.novacut.editor.engine.whisper.WhisperEngine
import com.novacut.editor.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class AiModelStorageUiState(
    val whisperBytes: Long = 0L,
    val segmentationBytes: Long = 0L,
    val isRemovingWhisper: Boolean = false,
    val isRemovingSegmentation: Boolean = false,
    val feedbackMessage: String? = null
) {
    val totalBytes: Long get() = whisperBytes + segmentationBytes
}

data class DiagnosticExportBundleUi(
    val path: String,
    val fileName: String,
    val sizeBytes: Long
)

data class DiagnosticExportUiState(
    val isExporting: Boolean = false,
    val bundle: DiagnosticExportBundleUi? = null,
    val message: String? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: SettingsRepository,
    private val whisperEngine: WhisperEngine,
    private val segmentationEngine: SegmentationEngine,
    private val diagnosticExportEngine: DiagnosticExportEngine
) : ViewModel() {

    val settings: StateFlow<AppSettings> = repo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    val whisperModelState = whisperEngine.modelState
    val segmentationModelState = segmentationEngine.modelState

    private val _aiModelStorage = MutableStateFlow(AiModelStorageUiState())
    val aiModelStorage: StateFlow<AiModelStorageUiState> = _aiModelStorage.asStateFlow()

    private val _diagnosticExport = MutableStateFlow(DiagnosticExportUiState())
    val diagnosticExport: StateFlow<DiagnosticExportUiState> = _diagnosticExport.asStateFlow()

    init {
        refreshAiModelStorage()
    }

    fun setResolution(v: Resolution) = viewModelScope.launch { repo.updateResolution(v) }
    fun setFrameRate(v: Int) = viewModelScope.launch { repo.updateFrameRate(v) }
    fun setAspectRatio(v: AspectRatio) = viewModelScope.launch { repo.updateAspectRatio(v) }
    fun setAutoSave(v: Boolean) = viewModelScope.launch { repo.updateAutoSave(v) }
    fun setAutoSaveInterval(v: Int) = viewModelScope.launch { repo.updateAutoSaveInterval(v) }
    fun setProxyResolution(v: ProxyResolution) = viewModelScope.launch { repo.updateProxyResolution(v) }
    fun setDefaultCodec(codec: String) = viewModelScope.launch { repo.updateDefaultCodec(codec) }
    fun setProxyEnabled(enabled: Boolean) = viewModelScope.launch { repo.updateProxyEnabled(enabled) }
    fun resetTutorial() = viewModelScope.launch { repo.setTutorialShown(false) }
    fun setEditorMode(mode: String) = viewModelScope.launch { repo.updateEditorMode(mode) }
    fun setHapticEnabled(enabled: Boolean) = viewModelScope.launch { repo.updateHapticEnabled(enabled) }
    fun setShowWaveforms(v: Boolean) = viewModelScope.launch { repo.updateShowWaveforms(v) }
    fun setDefaultTrackHeight(v: Int) = viewModelScope.launch { repo.updateDefaultTrackHeight(v) }
    fun setSnapToBeat(v: Boolean) = viewModelScope.launch { repo.updateSnapToBeat(v) }
    fun setSnapToMarker(v: Boolean) = viewModelScope.launch { repo.updateSnapToMarker(v) }
    fun setThumbnailCacheSize(v: Int) = viewModelScope.launch { repo.updateThumbnailCacheSize(v) }
    fun setConfirmBeforeDelete(v: Boolean) = viewModelScope.launch { repo.updateConfirmBeforeDelete(v) }
    fun setDefaultExportQuality(v: String) = viewModelScope.launch { repo.updateDefaultExportQuality(v) }
    fun setAiModelWifiOnly(v: Boolean) = viewModelScope.launch { repo.updateAiModelWifiOnly(v) }

    fun refreshAiModelStorage() {
        viewModelScope.launch {
            val (whisperBytes, segmentationBytes) = withContext(Dispatchers.IO) {
                whisperEngine.refreshModelState()
                segmentationEngine.refreshModelState()
                whisperEngine.getModelSizeBytes() to segmentationEngine.getModelSizeBytes()
            }
            _aiModelStorage.update {
                it.copy(
                    whisperBytes = whisperBytes,
                    segmentationBytes = segmentationBytes
                )
            }
        }
    }

    fun dismissAiModelStorageFeedback() {
        _aiModelStorage.update { it.copy(feedbackMessage = null) }
    }

    fun exportDiagnosticBundle() {
        if (_diagnosticExport.value.isExporting) return
        viewModelScope.launch {
            _diagnosticExport.update {
                it.copy(isExporting = true, message = null, errorMessage = null)
            }
            try {
                val modelRegistry = withContext(Dispatchers.IO) {
                    val whisperBytes = whisperEngine.getModelSizeBytes()
                    val segmentationBytes = segmentationEngine.getModelSizeBytes()
                    listOf(
                        DiagnosticExportEngine.ModelSnapshot(
                            id = "whisper-onnx",
                            installed = whisperBytes > 0L,
                            sizeBytes = whisperBytes
                        ),
                        DiagnosticExportEngine.ModelSnapshot(
                            id = "segmentation-mediapipe",
                            installed = segmentationBytes > 0L,
                            sizeBytes = segmentationBytes
                        )
                    )
                }
                val bundle = diagnosticExportEngine.exportDiagnosticBundle(modelRegistry)
                _diagnosticExport.update {
                    it.copy(
                        isExporting = false,
                        bundle = DiagnosticExportBundleUi(
                            path = bundle.absolutePath,
                            fileName = bundle.name,
                            sizeBytes = bundle.length()
                        ),
                        message = "Diagnostic ZIP saved locally. Share it only when you choose.",
                        errorMessage = null
                    )
                }
            } catch (error: CancellationException) {
                _diagnosticExport.update { it.copy(isExporting = false) }
                throw error
            } catch (error: Exception) {
                _diagnosticExport.update {
                    it.copy(
                        isExporting = false,
                        errorMessage = "Diagnostic ZIP could not be created. Try again.",
                        message = null
                    )
                }
            }
        }
    }

    fun dismissDiagnosticExportMessage() {
        _diagnosticExport.update { it.copy(message = null, errorMessage = null) }
    }

    fun reportDiagnosticShareFailure() {
        _diagnosticExport.update {
            it.copy(
                message = null,
                errorMessage = "Diagnostic ZIP could not be shared from this device."
            )
        }
    }

    fun downloadWhisperModel() {
        viewModelScope.launch {
            _aiModelStorage.update { it.copy(feedbackMessage = null) }
            val wifiOnly = repo.settings.first().aiModelWifiOnly
            try {
                val success = whisperEngine.downloadModel(wifiOnly = wifiOnly)
                val bytes = withContext(Dispatchers.IO) { whisperEngine.getModelSizeBytes() }
                _aiModelStorage.update {
                    it.copy(
                        whisperBytes = bytes,
                        feedbackMessage = if (success) {
                            "Whisper installed. Captions can now use local speech-to-text."
                        } else {
                            "Whisper could not be verified. Retry the download before using local speech-to-text."
                        }
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _aiModelStorage.update {
                    it.copy(
                        feedbackMessage = when (error) {
                            is ModelDownloadManager.MeteredNetworkException ->
                                "Wi-Fi-only model downloads are on. Connect to Wi-Fi or change the setting."
                            else ->
                                "Whisper could not be downloaded. Check your connection and try again."
                        }
                    )
                }
            }
        }
    }

    fun downloadSegmentationModel() {
        viewModelScope.launch {
            _aiModelStorage.update { it.copy(feedbackMessage = null) }
            val wifiOnly = repo.settings.first().aiModelWifiOnly
            try {
                val success = segmentationEngine.downloadModel(wifiOnly = wifiOnly)
                val bytes = withContext(Dispatchers.IO) { segmentationEngine.getModelSizeBytes() }
                _aiModelStorage.update {
                    it.copy(
                        segmentationBytes = bytes,
                        feedbackMessage = if (success) {
                            "Segmentation installed. Background tools can now run locally."
                        } else {
                            "Segmentation could not be verified. Retry the download before using AI matte tools."
                        }
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _aiModelStorage.update {
                    it.copy(
                        feedbackMessage = when (error) {
                            is ModelDownloadManager.MeteredNetworkException ->
                                "Wi-Fi-only model downloads are on. Connect to Wi-Fi or change the setting."
                            else ->
                                "Segmentation could not be downloaded. Check your connection and try again."
                        }
                    )
                }
            }
        }
    }

    fun removeWhisperModel() {
        viewModelScope.launch {
            val before = withContext(Dispatchers.IO) { whisperEngine.getModelSizeBytes() }
            _aiModelStorage.update { it.copy(isRemovingWhisper = true, feedbackMessage = null) }
            val success = withContext(Dispatchers.IO) {
                runCatching { whisperEngine.deleteModel() }.isSuccess
            }
            val after = withContext(Dispatchers.IO) { whisperEngine.getModelSizeBytes() }
            _aiModelStorage.update {
                it.copy(
                    whisperBytes = after,
                    isRemovingWhisper = false,
                    feedbackMessage = if (success) {
                        "Whisper removed. Freed ${formatStorageBytes(before - after)}."
                    } else {
                        "Whisper could not be removed. Try again from AI Tools."
                    }
                )
            }
        }
    }

    fun removeSegmentationModel() {
        viewModelScope.launch {
            val before = withContext(Dispatchers.IO) { segmentationEngine.getModelSizeBytes() }
            _aiModelStorage.update { it.copy(isRemovingSegmentation = true, feedbackMessage = null) }
            val success = withContext(Dispatchers.IO) {
                runCatching { segmentationEngine.deleteModel() }.isSuccess
            }
            val after = withContext(Dispatchers.IO) { segmentationEngine.getModelSizeBytes() }
            _aiModelStorage.update {
                it.copy(
                    segmentationBytes = after,
                    isRemovingSegmentation = false,
                    feedbackMessage = if (success) {
                        "Segmentation model removed. Freed ${formatStorageBytes(before - after)}."
                    } else {
                        "Segmentation could not be removed. Try again from AI Tools."
                    }
                )
            }
        }
    }
}

fun formatStorageBytes(bytes: Long): String {
    val safeBytes = bytes.coerceAtLeast(0L)
    return when {
        safeBytes < 1024L -> "$safeBytes B"
        safeBytes < 1024L * 1024L -> "${safeBytes / 1024L} KB"
        safeBytes < 1024L * 1024L * 1024L -> {
            val mb = safeBytes / (1024.0 * 1024.0)
            String.format(java.util.Locale.getDefault(), "%.1f MB", mb)
        }
        else -> {
            val gb = safeBytes / (1024.0 * 1024.0 * 1024.0)
            String.format(java.util.Locale.getDefault(), "%.2f GB", gb)
        }
    }
}

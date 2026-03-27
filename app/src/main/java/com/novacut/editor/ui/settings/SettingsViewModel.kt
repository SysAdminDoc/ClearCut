package com.novacut.editor.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novacut.editor.engine.AppSettings
import com.novacut.editor.engine.SettingsRepository
import com.novacut.editor.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: SettingsRepository
) : ViewModel() {

    val settings: StateFlow<AppSettings> = repo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

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
}

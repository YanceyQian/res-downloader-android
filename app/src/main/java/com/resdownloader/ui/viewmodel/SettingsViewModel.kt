package com.resdownloader.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resdownloader.BuildConfig
import com.resdownloader.data.model.AssetInfo
import com.resdownloader.data.model.VersionInfo
import com.resdownloader.data.preferences.PreferencesManager
import com.resdownloader.data.repository.UpdateRepository
import com.resdownloader.data.repository.UpdateState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val updateRepository: UpdateRepository
) : ViewModel() {

    val downloadPath = preferencesManager.downloadPath
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val proxyPort = preferencesManager.proxyPort
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 8899)

    val language = preferencesManager.language
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "zh")

    val certificateInstalled = preferencesManager.certificateInstalled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val updateState = updateRepository.updateState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UpdateState.Idle)

    val latestVersion = updateRepository.latestVersion
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val currentVersion = BuildConfig.VERSION_NAME

    private val _uiEvent = MutableSharedFlow<UiEvent>()
    val uiEvent: SharedFlow<UiEvent> = _uiEvent.asSharedFlow()

    fun setDownloadPath(path: String) {
        viewModelScope.launch {
            preferencesManager.setDownloadPath(path)
        }
    }

    fun setProxyPort(port: Int) {
        viewModelScope.launch {
            if (port in 1024..65535) {
                preferencesManager.setProxyPort(port)
            } else {
                _uiEvent.emit(UiEvent.Error("端口号无效，请输入 1024-65535 之间的值"))
            }
        }
    }

    fun setLanguage(lang: String) {
        viewModelScope.launch {
            preferencesManager.setLanguage(lang)
            _uiEvent.emit(UiEvent.LanguageChanged(lang))
        }
    }

    fun setCertificateInstalled(installed: Boolean) {
        viewModelScope.launch {
            preferencesManager.setCertificateInstalled(installed)
        }
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            val result = updateRepository.checkForUpdates()
            result.fold(
                onSuccess = { versionInfo ->
                    _uiEvent.emit(UiEvent.UpdateAvailable(versionInfo))
                },
                onFailure = { error ->
                    if (error.message?.contains("Already up to date") == true) {
                        _uiEvent.emit(UiEvent.NoUpdate)
                    } else {
                        _uiEvent.emit(UiEvent.Error(error.message ?: "检查更新失败"))
                    }
                }
            )
        }
    }

    fun downloadUpdate(asset: AssetInfo) {
        viewModelScope.launch {
            updateRepository.downloadUpdate(asset) { progress ->
            }.fold(
                onSuccess = { filePath ->
                    _uiEvent.emit(UiEvent.UpdateDownloaded(filePath))
                },
                onFailure = { error ->
                    _uiEvent.emit(UiEvent.Error(error.message ?: "下载更新失败"))
                }
            )
        }
    }

    fun resetUpdateState() {
        updateRepository.resetState()
    }

    sealed class UiEvent {
        data class Error(val message: String) : UiEvent()
        data class UpdateAvailable(val versionInfo: VersionInfo) : UiEvent()
        data class UpdateDownloaded(val filePath: String) : UiEvent()
        object NoUpdate : UiEvent()
        data class LanguageChanged(val lang: String) : UiEvent()
    }
}

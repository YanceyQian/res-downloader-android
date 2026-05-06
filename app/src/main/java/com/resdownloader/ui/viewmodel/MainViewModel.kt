package com.resdownloader.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resdownloader.data.model.Platform
import com.resdownloader.data.model.ResourceInfo
import com.resdownloader.data.model.ResourceType
import com.resdownloader.data.preferences.PreferencesManager
import com.resdownloader.data.repository.ProxyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val proxyRepository: ProxyRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    val proxyState = proxyRepository.proxyState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val resources = proxyRepository.filteredResources
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentFilter = MutableStateFlow<ResourceType?>(null)

    val downloadPath = preferencesManager.downloadPath
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "/storage/emulated/0/Download/ResDownloader")

    private val _uiEvent = MutableSharedFlow<UiEvent>()
    val uiEvent: SharedFlow<UiEvent> = _uiEvent.asSharedFlow()

    init {
        viewModelScope.launch {
            currentFilter.collect { filter ->
                proxyRepository.setFilter(filter)
            }
        }
    }

    fun toggleProxy() {
        viewModelScope.launch {
            if (proxyState.value) {
                proxyRepository.stopProxy()
            } else {
                proxyRepository.startProxy()
            }
        }
    }

    fun setFilter(type: ResourceType?) {
        currentFilter.value = type
    }

    fun removeResource(id: String) {
        proxyRepository.removeResource(id)
    }

    fun clearResources() {
        proxyRepository.clearResources()
    }

    fun addManualResource(url: String) {
        viewModelScope.launch {
            val resource = proxyRepository.parseUrl(url)
            if (resource != null) {
                proxyRepository.addResource(resource)
                _uiEvent.emit(UiEvent.ResourceAdded(resource))
            } else {
                _uiEvent.emit(UiEvent.Error("无法解析该链接"))
            }
        }
    }

    fun getPlatformDisplayName(platform: Platform): String {
        return when (platform) {
            Platform.WECHAT -> "微信"
            Platform.DOUYIN -> "抖音"
            Platform.KUAISHOU -> "快手"
            Platform.XIAOHONGSHU -> "小红书"
            Platform.KUGOU -> "酷狗"
            Platform.QQMUSIC -> "QQ音乐"
            Platform.OTHER -> "其他"
        }
    }

    fun getResourceTypeDisplayName(type: ResourceType): String {
        return when (type) {
            ResourceType.VIDEO -> "视频"
            ResourceType.AUDIO -> "音频"
            ResourceType.IMAGE -> "图片"
            ResourceType.M3U8 -> "M3U8"
            ResourceType.OTHER -> "其他"
        }
    }

    sealed class UiEvent {
        data class ResourceAdded(val resource: ResourceInfo) : UiEvent()
        data class Error(val message: String) : UiEvent()
    }
}

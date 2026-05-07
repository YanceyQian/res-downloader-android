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

    private val allResources = proxyRepository.filteredResources
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val searchQuery = MutableStateFlow("")

    val currentFilter = MutableStateFlow<ResourceType?>(null)

    // 抓取模式：light = 轻量模式（仅支持平台），full = 全量模式
    val captureMode: StateFlow<String> = preferencesManager.wxAction
        .map { if (it) "full" else "light" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "full")

    val resources = combine(allResources, searchQuery) { resources, query ->
        if (query.isBlank()) {
            resources
        } else {
            resources.filter { it.filename.contains(query, ignoreCase = true) || it.url.contains(query, ignoreCase = true) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun setCaptureMode(mode: String) {
        viewModelScope.launch {
            // mode = "light" 时 wxAction = false，mode = "full" 时 wxAction = true
            preferencesManager.setWxAction(mode == "full")
        }
    }

    fun removeResource(id: String) {
        proxyRepository.removeResource(id)
    }

    fun clearResources() {
        proxyRepository.clearResources()
    }

    fun addManualResource(url: String) {
        viewModelScope.launch {
            val urls = url.lines().filter { it.isNotBlank() }
            var addedCount = 0
            var lastAddedResource: ResourceInfo? = null
            urls.forEach { singleUrl ->
                val trimmedUrl = singleUrl.trim()
                if (trimmedUrl.isNotEmpty()) {
                    try {
                        val resource = parseManualUrl(trimmedUrl)
                        if (resource != null) {
                            proxyRepository.addResource(resource)
                            addedCount++
                            lastAddedResource = resource
                        } else {
                            _uiEvent.emit(UiEvent.Error("无法解析该链接: $trimmedUrl"))
                        }
                    } catch (e: Exception) {
                        _uiEvent.emit(UiEvent.Error("解析失败: ${e.message}"))
                    }
                }
            }
            if (addedCount > 0) {
                lastAddedResource?.let {
                    _uiEvent.emit(UiEvent.ResourceAdded(it))
                }
            }
        }
    }

    /**
     * 解析手动输入的 URL
     */
    private fun parseManualUrl(url: String): ResourceInfo? {
        return try {
            val cleanUrl = url.trim()

            // 检查是否是有效的 URL
            if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
                return null
            }

            // 从 URL 提取文件名
            val fileName = extractFilenameFromUrl(cleanUrl)

            // 检测资源类型
            val resourceType = detectResourceType(cleanUrl, fileName)

            // 检测平台
            val platform = detectPlatform(cleanUrl)

            ResourceInfo(
                id = System.currentTimeMillis().toString() + "_" + cleanUrl.hashCode(),
                url = cleanUrl,
                filename = fileName,
                platform = platform,
                type = resourceType,
                size = 0,
                timestamp = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun extractFilenameFromUrl(url: String): String {
        return try {
            val path = java.net.URL(url).path
            val nameFromPath = path.substringAfterLast("/").substringBefore("?")

            if (nameFromPath.isNotEmpty() && nameFromPath.contains(".")) {
                nameFromPath
            } else {
                // 根据 URL 类型生成文件名
                when {
                    url.contains(".mp4") || url.contains("video") -> "video_${System.currentTimeMillis()}.mp4"
                    url.contains(".mp3") || url.contains("audio") || url.contains("music") -> "audio_${System.currentTimeMillis()}.mp3"
                    url.contains(".jpg") || url.contains(".jpeg") || url.contains("image") -> "image_${System.currentTimeMillis()}.jpg"
                    url.contains(".png") -> "image_${System.currentTimeMillis()}.png"
                    url.contains(".m3u8") -> "playlist_${System.currentTimeMillis()}.m3u8"
                    url.contains(".pdf") -> "document_${System.currentTimeMillis()}.pdf"
                    else -> "resource_${System.currentTimeMillis()}.mp4"
                }
            }
        } catch (e: Exception) {
            "resource_${System.currentTimeMillis()}.mp4"
        }
    }

    private fun detectResourceType(url: String, filename: String): ResourceType {
        val lowerUrl = url.lowercase()
        val lowerFile = filename.lowercase()

        return when {
            lowerUrl.contains(".mp4") || lowerUrl.contains("video") || lowerFile.endsWith(".mp4") -> ResourceType.VIDEO
            lowerUrl.contains(".m3u8") || lowerFile.endsWith(".m3u8") -> ResourceType.M3U8
            lowerUrl.contains(".mp3") || lowerUrl.contains("audio") || lowerUrl.contains("music") || lowerFile.endsWith(".mp3") -> ResourceType.AUDIO
            lowerUrl.contains(".m4a") || lowerFile.endsWith(".m4a") -> ResourceType.AUDIO
            lowerUrl.contains(".jpg") || lowerUrl.contains(".jpeg") || lowerFile.endsWith(".jpg") || lowerFile.endsWith(".jpeg") -> ResourceType.IMAGE
            lowerUrl.contains(".png") || lowerFile.endsWith(".png") -> ResourceType.IMAGE
            lowerUrl.contains(".gif") || lowerFile.endsWith(".gif") -> ResourceType.IMAGE
            lowerUrl.contains(".pdf") || lowerFile.endsWith(".pdf") -> ResourceType.PDF
            lowerUrl.contains(".xlsx") || lowerUrl.contains(".xls") || lowerFile.endsWith(".xlsx") -> ResourceType.XLS
            lowerUrl.contains(".doc") || lowerUrl.contains(".docx") || lowerFile.endsWith(".docx") -> ResourceType.DOC
            lowerUrl.contains(".woff") || lowerUrl.contains(".ttf") || lowerUrl.contains(".otf") -> ResourceType.FONT
            else -> ResourceType.VIDEO
        }
    }

    private fun detectPlatform(url: String): Platform {
        val lowerUrl = url.lowercase()
        return when {
            // 微信（视频号、小程序）
            lowerUrl.contains("mp.weixin") || lowerUrl.contains("channels.weixin") -> Platform.WECHAT
            lowerUrl.contains("weishi.qq") || lowerUrl.contains("qqweishi") -> Platform.QQWEISHI
            // 抖音
            lowerUrl.contains("douyin") || lowerUrl.contains("tiktok") || lowerUrl.contains("bytedance") || lowerUrl.contains("amemv") -> Platform.DOUYIN
            // 快手
            lowerUrl.contains("kuaishou") || lowerUrl.contains("ks") || lowerUrl.contains("gifshow") -> Platform.KUAISHOU
            // 小红书
            lowerUrl.contains("xiaohongshu") || lowerUrl.contains("xhs") || lowerUrl.contains("redbook") || lowerUrl.contains("xhslink") -> Platform.XIAOHONGSHU
            // B站
            lowerUrl.contains("bilibili") || lowerUrl.contains("bili") || lowerUrl.contains("b23.tv") -> Platform.BILIBILI
            // 酷狗音乐
            lowerUrl.contains("kugou") || lowerUrl.contains("kugoustatic") -> Platform.KUGOU
            // QQ音乐
            lowerUrl.contains("qqmusic") || lowerUrl.contains("y.qq") || lowerUrl.contains("imgcache.qq") -> Platform.QQMUSIC
            // YouTube
            lowerUrl.contains("googlevideo") || lowerUrl.contains("youtube") || lowerUrl.contains("youtu.be") -> Platform.YOUTUBE
            // 微信小程序（通过特定路径识别）
            lowerUrl.contains("servicewechat") || lowerUrl.contains("weapp") -> Platform.WECHAT_MINI
            else -> Platform.OTHER
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
            Platform.BILIBILI -> "B站"
            Platform.WECHAT_MINI -> "小程序"
            Platform.QQWEISHI -> "QQ微视"
            Platform.YOUTUBE -> "YouTube"
            Platform.OTHER -> "其他"
        }
    }

    fun getResourceTypeDisplayName(type: ResourceType): String {
        return when (type) {
            ResourceType.VIDEO -> "视频"
            ResourceType.AUDIO -> "音频"
            ResourceType.IMAGE -> "图片"
            ResourceType.M3U8 -> "M3U8"
            ResourceType.LIVE -> "直播流"
            ResourceType.STREAM -> "流数据"
            ResourceType.XLS -> "表格"
            ResourceType.DOC -> "文档"
            ResourceType.PDF -> "PDF"
            ResourceType.FONT -> "字体"
            ResourceType.OTHER -> "其他"
        }
    }

    sealed class UiEvent {
        data class ResourceAdded(val resource: ResourceInfo) : UiEvent()
        data class Error(val message: String) : UiEvent()
    }
}

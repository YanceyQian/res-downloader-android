package com.resdownloader.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resdownloader.data.model.*
import com.resdownloader.data.preferences.PreferencesManager
import com.resdownloader.data.repository.*
import com.resdownloader.network.*
import com.resdownloader.util.WechatVideoDownloader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val proxyRepository: ProxyRepository,
    private val preferencesManager: PreferencesManager,
    private val wechatVideoDownloader: WechatVideoDownloader,
    private val wechatErrorRepository: WechatErrorRepository,
    private val platformErrorRepository: PlatformErrorRepository,
    private val updateRepository: UpdateRepository
) : ViewModel() {

    companion object {
        private const val TAG = "MainViewModel"
    }

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

    // 微信错误相关状态
    val isApiLikelyBroken: StateFlow<Boolean> = wechatErrorRepository.isApiLikelyBroken
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val currentAnnouncement: StateFlow<Announcement?> = wechatErrorRepository.currentAnnouncement
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val lastWechatError: StateFlow<WechatErrorDetail?> = wechatErrorRepository.errorStatistics
        .map { stats -> stats.lastErrorType?.let { type ->
            WechatErrorDetail(
                type = type,
                originalMessage = "Last error: ${type.name}"
            )
        }}
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // ========== 通用平台错误相关状态 ==========
    
    // 当前需要显示降级方案的平台
    val activeFallbackPlatform: StateFlow<Platform?> = platformErrorRepository.activeFallbackPlatform
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // 各平台公告
    val platformAnnouncements: StateFlow<Map<Platform, PlatformAnnouncement>> = platformErrorRepository.platformAnnouncements
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // 平台错误统计
    val platformStatistics: StateFlow<Map<Platform, PlatformErrorStatistics>> = platformErrorRepository.platformStatistics
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // API 警告是否已忽略（按平台）
    private val _apiWarningDismissed = MutableStateFlow<Map<Platform, Boolean>>(emptyMap())
    val apiWarningDismissed: StateFlow<Map<Platform, Boolean>> = _apiWarningDismissed.asStateFlow()

    // 是否显示反馈对话框
    private val _showFeedbackDialog = MutableStateFlow(false)
    val showFeedbackDialog: StateFlow<Boolean> = _showFeedbackDialog.asStateFlow()

    // 反馈对话框对应的平台
    private val _feedbackPlatform = MutableStateFlow<Platform?>(null)
    val feedbackPlatform: StateFlow<Platform?> = _feedbackPlatform.asStateFlow()

    // 更新状态
    val updateState: StateFlow<UpdateState> = updateRepository.updateState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UpdateState.Idle)

    val latestVersion: StateFlow<VersionInfo?> = updateRepository.latestVersion
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // 是否显示更新对话框
    private val _showUpdateDialog = MutableStateFlow(false)
    val showUpdateDialog: StateFlow<Boolean> = _showUpdateDialog.asStateFlow()

    // 是否已忽略当前公告
    private val _announcementDismissed = MutableStateFlow(false)
    val announcementDismissed: StateFlow<Boolean> = _announcementDismissed.asStateFlow()

    // 平台下载器映射
    private val downloaders = mapOf(
        Platform.WECHAT to wechatVideoDownloader,
        Platform.DOUYIN to DouyinDownloader(),
        Platform.KUAISHOU to KuaishouDownloader(),
        Platform.XIAOHONGSHU to XiaohongshuDownloader(),
        Platform.BILIBILI to BilibiliDownloader(),
        Platform.NETEASE to NeteaseDownloader(),
        Platform.GENERAL to GeneralDownloader()
    )

    init {
        // 注册下载器到工厂
        downloaders.forEach { (_, downloader) ->
            DownloaderFactory.register(downloader)
        }

        // 启动时检查公告
        viewModelScope.launch {
            checkAnnouncement()
        }

        // 监听 API 失效状态
        viewModelScope.launch {
            isApiLikelyBroken.collect { isBroken ->
                if (isBroken) {
                    // API 可能失效，显示警告
                    Log.w(TAG, "API appears to be broken, showing warning")
                }
            }
        }
    }

    /**
     * 获取平台下载器
     */
    fun getDownloader(platform: Platform): PlatformDownloader? {
        return downloaders[platform] ?: downloaders[Platform.GENERAL] as PlatformDownloader?
    }

    /**
     * 检测 URL 的平台
     */
    fun detectPlatform(url: String): Platform {
        return DownloaderFactory.detectPlatform(url)
    }

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
     * 添加单个资源（同步版本，供外部调用）
     */
    suspend fun addSingleResource(url: String): ResourceInfo? {
        return try {
            parseManualUrl(url)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 解析手动输入的 URL
     * 对视频号链接调用 API 获取真实信息
     */
    private suspend fun parseManualUrl(url: String): ResourceInfo? {
        return try {
            val cleanUrl = url.trim()

            // 检查是否是有效的 URL
            if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
                return null
            }

            // 检测平台
            val platform = detectPlatform(cleanUrl)

            // 如果是微信视频号，调用 API 获取真实信息
            var finalUrl = cleanUrl
            var filename = extractFilenameFromUrl(cleanUrl)
            var decodeKey: String? = null

            if (platform == Platform.WECHAT && wechatVideoDownloader.isVideoAccountUrl(cleanUrl)) {
                Log.d(TAG, "Detected WeChat video account URL, resolving via API...")

                // 设置错误回调
                wechatVideoDownloader.setErrorCallback { e ->
                    viewModelScope.launch {
                        handleWechatError(wechatErrorRepository.handleException(e))
                    }
                }

                val videoInfo = wechatVideoDownloader.resolveVideo(cleanUrl)
                if (videoInfo != null) {
                    finalUrl = videoInfo.videoUrl
                    filename = videoInfo.title + ".mp4"
                    decodeKey = videoInfo.decodeKey
                    Log.d(TAG, "Video resolved: title=${videoInfo.title}, hasDecodeKey=${decodeKey != null}")

                    // 成功获取视频信息，清除错误计数
                    wechatErrorRepository.clearApiErrorCount()
                } else {
                    Log.w(TAG, "Failed to resolve video, falling back to original URL")
                    // 解析失败时，提示用户可能需要更新
                    viewModelScope.launch {
                        _uiEvent.emit(UiEvent.WechatApiError(
                            com.resdownloader.data.model.WechatErrorType.API_INVALID_RESPONSE
                        ))
                    }
                }
            }

            // 检测资源类型
            val resourceType = detectResourceType(finalUrl, filename)

            ResourceInfo(
                id = System.currentTimeMillis().toString() + "_" + finalUrl.hashCode(),
                url = finalUrl,
                filename = filename,
                platform = platform,
                type = resourceType,
                size = 0,
                timestamp = System.currentTimeMillis(),
                decodeKey = decodeKey
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing URL: $url", e)
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

    fun getPlatformDisplayName(platform: Platform): String {
        return platform.displayName
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

    /**
     * 获取用户友好的错误提示
     */
    fun getWechatErrorMessage(): String? {
        val lastError = lastWechatError.value ?: return null
        return wechatErrorRepository.getUserMessage(lastError)
    }

    /**
     * 检查是否有更新
     */
    fun checkForUpdates() {
        viewModelScope.launch {
            val result = updateRepository.checkForUpdates()
            result.fold(
                onSuccess = { versionInfo ->
                    // 发现新版本
                    Log.i(TAG, "New version available: ${versionInfo.tagName}")
                    _showUpdateDialog.value = true
                },
                onFailure = {
                    // 没有更新或检查失败
                    Log.d(TAG, "No updates available or check failed")
                }
            )
        }
    }

    /**
     * 下载并安装更新
     */
    fun downloadAndInstallUpdate() {
        viewModelScope.launch {
            val versionInfo = latestVersion.value
            val asset = versionInfo?.assets?.firstOrNull { it.name.endsWith(".apk") }

            if (asset != null) {
                updateRepository.downloadUpdate(asset) { progress ->
                    Log.d(TAG, "Update download progress: $progress%")
                }.fold(
                    onSuccess = { filePath ->
                        // TODO: 安装 APK
                        Log.i(TAG, "Update downloaded to: $filePath")
                    },
                    onFailure = { e ->
                        Log.e(TAG, "Failed to download update", e)
                    }
                )
            }
        }
    }

    /**
     * 关闭更新对话框
     */
    fun dismissUpdateDialog() {
        _showUpdateDialog.value = false
    }

    /**
     * 检查公告
     */
    private fun checkAnnouncement() {
        viewModelScope.launch {
            wechatErrorRepository.checkAnnouncement()
        }
    }

    /**
     * 忽略当前公告
     */
    fun dismissAnnouncement() {
        _announcementDismissed.value = true
    }

    /**
     * 重置公告忽略状态（每次启动时重置）
     */
    fun resetAnnouncementDismissed() {
        _announcementDismissed.value = false
    }

    /**
     * 忽略 API 警告
     */
    fun dismissApiWarning() {
        // API 警告通过 wechatErrorRepository 管理，此处留空
    }

    /**
     * 重置 API 警告状态
     */
    fun resetApiWarning() {
        // API 警告通过 wechatErrorRepository 管理，此处留空
    }

    /**
     * 清除错误历史
     */
    fun clearErrorHistory() {
        viewModelScope.launch {
            wechatErrorRepository.clearErrorHistory()
        }
    }

    /**
     * 处理微信错误（供下载器调用）
     */
    fun handleWechatError(errorDetail: WechatErrorDetail) {
        wechatErrorRepository.handleError(errorDetail)

        // 如果是 API 相关错误，发送 UI 事件提示用户
        if (errorDetail.type.isApiRelated) {
            viewModelScope.launch {
                _uiEvent.emit(UiEvent.WechatApiError(errorDetail.type))
            }
        }
    }

    /**
     * 处理微信 API 成功（成功后调用以清除错误计数）
     */
    fun onWechatApiSuccess() {
        wechatErrorRepository.clearApiErrorCount()
    }

    // ========== 通用平台错误处理方法 ==========

    /**
     * 处理平台错误
     */
    fun handlePlatformError(
        platform: Platform,
        errorType: PlatformErrorType,
        originalMessage: String,
        httpCode: Int? = null,
        url: String? = null
    ) {
        val errorDetail = PlatformErrorDetail(
            type = errorType,
            originalMessage = originalMessage,
            platform = platform,
            url = url,
            httpCode = httpCode
        )
        platformErrorRepository.handleError(errorDetail)

        // 发送 UI 事件
        viewModelScope.launch {
            _uiEvent.emit(UiEvent.PlatformApiError(platform, errorType))
        }
    }

    /**
     * 处理平台异常
     */
    fun handlePlatformException(
        platform: Platform,
        e: Exception,
        url: String? = null
    ) {
        val errorDetail = platformErrorRepository.handleException(platform, e, url = url)
        
        viewModelScope.launch {
            _uiEvent.emit(UiEvent.PlatformApiError(platform, errorDetail.type))
        }
    }

    /**
     * 获取平台降级消息
     */
    fun getPlatformFallbackMessage(platform: Platform): String {
        return platformErrorRepository.getFallbackMessage(platform)
    }

    /**
     * 获取平台降级选项
     */
    fun getPlatformFallbackOptions(platform: Platform): List<FallbackOptionUi> {
        return platformErrorRepository.getFallbackOptions(platform)
    }

    /**
     * 显示降级方案对话框
     */
    fun showFallbackDialog(platform: Platform) {
        // 通过 platformErrorRepository 显示降级对话框
    }

    /**
     * 关闭降级方案对话框
     */
    fun dismissFallbackDialog() {
        platformErrorRepository.dismissFallbackDialog()
    }

    /**
     * 清除平台错误计数（成功后调用）
     */
    fun clearPlatformErrorCount(platform: Platform) {
        platformErrorRepository.clearErrorCount(platform)
    }

    /**
     * 显示反馈对话框
     */
    fun showFeedbackDialog(platform: Platform) {
        _feedbackPlatform.value = platform
        _showFeedbackDialog.value = true
    }

    /**
     * 关闭反馈对话框
     */
    fun dismissFeedbackDialog() {
        _showFeedbackDialog.value = false
        _feedbackPlatform.value = null
    }

    /**
     * 生成平台反馈模板
     */
    fun generatePlatformFeedback(platform: Platform, errorType: PlatformErrorType): PlatformUserFeedback {
        val errorDetail = PlatformErrorDetail(
            type = errorType,
            originalMessage = "Error: ${errorType.name}",
            platform = platform
        )
        return platformErrorRepository.generateFeedback(platform, errorDetail)
    }

    /**
     * 提交平台反馈
     */
    fun submitPlatformFeedback(feedback: PlatformUserFeedback) {
        viewModelScope.launch {
            platformErrorRepository.saveFeedback(feedback)
            dismissFeedbackDialog()
            _uiEvent.emit(UiEvent.FeedbackSubmitted)
        }
    }

    /**
     * 忽略平台 API 警告
     */
    fun dismissPlatformApiWarning(platform: Platform) {
        _apiWarningDismissed.value = _apiWarningDismissed.value.toMutableMap().apply {
            put(platform, true)
        }
    }

    /**
     * 检查平台 API 警告是否已忽略
     */
    fun isPlatformApiWarningDismissed(platform: Platform): Boolean {
        return _apiWarningDismissed.value[platform] == true
    }

    /**
     * 清除平台错误历史
     */
    fun clearPlatformErrorHistory(platform: Platform) {
        viewModelScope.launch {
            platformErrorRepository.clearErrorHistory(platform)
            _apiWarningDismissed.value = _apiWarningDismissed.value.toMutableMap().apply {
                remove(platform)
            }
        }
    }

    /**
     * 检查平台公告
     */
    fun checkPlatformAnnouncement(platform: Platform) {
        viewModelScope.launch {
            platformErrorRepository.checkAnnouncement(platform)
        }
    }

    sealed class UiEvent {
        data class ResourceAdded(val resource: ResourceInfo) : UiEvent()
        data class Error(val message: String) : UiEvent()
        data class WechatApiError(val errorType: WechatErrorType) : UiEvent()
        
        // 通用平台错误事件
        data class PlatformApiError(val platform: Platform, val errorType: PlatformErrorType) : UiEvent()
        object FeedbackSubmitted : UiEvent()
        data class FallbackOptionSelected(val platform: Platform, val optionId: String) : UiEvent()
    }
}

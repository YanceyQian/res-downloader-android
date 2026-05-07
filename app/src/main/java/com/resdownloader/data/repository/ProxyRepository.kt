package com.resdownloader.data.repository

import android.content.Context
import android.content.Intent
import com.resdownloader.data.model.ResourceInfo
import com.resdownloader.data.model.ResourceType
import com.resdownloader.data.model.Platform
import com.resdownloader.data.preferences.PreferencesManager
import com.resdownloader.service.ProxyVpnService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProxyRepository @Inject constructor(
    private val context: Context,
    private val preferencesManager: PreferencesManager
) {

    private val repositoryScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    private val _proxyState = MutableStateFlow(false)
    val proxyState: StateFlow<Boolean> = _proxyState.asStateFlow()

    private val _resources = MutableStateFlow<List<ResourceInfo>>(emptyList())
    val resources: StateFlow<List<ResourceInfo>> = _resources.asStateFlow()

    private val _filteredResources = MutableStateFlow<List<ResourceInfo>>(emptyList())
    val filteredResources: StateFlow<List<ResourceInfo>> = _filteredResources.asStateFlow()

    private var currentFilter: ResourceType? = null

    init {
        // 监听服务状态 - 使用更安全的方式访问 companion object
        repositoryScope.launch {
            while (true) {
                _proxyState.value = ProxyVpnService.getRunning()
                kotlinx.coroutines.delay(500)
            }
        }
        
        // 监听服务中的资源
        repositoryScope.launch {
            while (true) {
                _resources.value = ProxyVpnService.getResourcesList()
                applyFilter()
                kotlinx.coroutines.delay(500)
            }
        }
    }

    fun startProxy() {
        val intent = Intent(context, ProxyVpnService::class.java)
        intent.action = ProxyVpnService.ACTION_START
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        _proxyState.value = true
    }

    fun stopProxy() {
        val intent = Intent(context, ProxyVpnService::class.java)
        intent.action = ProxyVpnService.ACTION_STOP
        context.startService(intent)
        _proxyState.value = false
    }

    fun addResource(resource: ResourceInfo) {
        // 首先添加到 ProxyVpnService，确保不会被覆盖
        ProxyVpnService.addResource(resource)
        
        val currentList = _resources.value.toMutableList()
        if (currentList.none { it.id == resource.id }) {
            val insertTail = preferencesManager.getInsertTailSync()
            if (insertTail) {
                currentList.add(resource)
            } else {
                currentList.add(0, resource)
            }
            _resources.value = currentList
            applyFilter()
        }
    }

    fun removeResource(id: String) {
        ProxyVpnService.removeResource(id)
        _resources.value = _resources.value.filter { it.id != id }
        applyFilter()
    }

    fun clearResources() {
        ProxyVpnService.clearResourcesList()
        _resources.value = emptyList()
        _filteredResources.value = emptyList()
    }

    fun setFilter(type: ResourceType?) {
        currentFilter = type
        applyFilter()
    }

    private fun applyFilter() {
        _filteredResources.value = if (currentFilter == null) {
            _resources.value
        } else {
            _resources.value.filter { it.type == currentFilter }
        }
    }

    fun parseUrl(url: String): ResourceInfo? {
        val lowerUrl = url.lowercase()
        
        // 检查是否是有效的URL
        if (!lowerUrl.startsWith("http://") && !lowerUrl.startsWith("https://")) {
            return null
        }
        
        val type = detectResourceType(url)
        val platform = detectPlatform(url)
        
        // 如果无法检测到类型，默认作为视频处理（如果是视频平台）
        val finalType = if (type == ResourceType.OTHER && platform != Platform.OTHER) {
            ResourceType.VIDEO
        } else {
            type
        }

        return ResourceInfo(
            id = System.currentTimeMillis().toString(),
            url = url,
            type = finalType,
            platform = platform,
            filename = extractFilename(url),
            size = 0,
            timestamp = System.currentTimeMillis()
        )
    }

    private fun detectResourceType(url: String): ResourceType {
        val lowerUrl = url.lowercase()
        return when {
            lowerUrl.contains(".m3u8") -> ResourceType.M3U8
            lowerUrl.contains(".mp4") || lowerUrl.contains(".flv") ||
            lowerUrl.contains(".avi") || lowerUrl.contains(".mkv") ||
            lowerUrl.contains(".webm") -> ResourceType.VIDEO
            lowerUrl.contains(".mp3") || lowerUrl.contains(".flac") ||
            lowerUrl.contains(".wav") || lowerUrl.contains(".aac") ||
            lowerUrl.contains(".ogg") || lowerUrl.contains(".m4a") -> ResourceType.AUDIO
            lowerUrl.contains(".jpg") || lowerUrl.contains(".jpeg") ||
            lowerUrl.contains(".png") || lowerUrl.contains(".gif") ||
            lowerUrl.contains(".webp") || lowerUrl.contains(".bmp") -> ResourceType.IMAGE
            else -> ResourceType.OTHER
        }
    }

    private fun detectPlatform(url: String): Platform {
        val lowerUrl = url.lowercase()
        return when {
            lowerUrl.contains("weixin.qq.com") || lowerUrl.contains("wechat.com") ||
            lowerUrl.contains("tmp.video.qq.com") || lowerUrl.contains("wxtingyun.com") -> Platform.WECHAT
            lowerUrl.contains("douyin.com") || lowerUrl.contains("iesdouyin.com") ||
            lowerUrl.contains("amemv.com") -> Platform.DOUYIN
            lowerUrl.contains("kuaishou.com") || lowerUrl.contains("kspkg.com") -> Platform.KUAISHOU
            lowerUrl.contains("xiaohongshu.com") || lowerUrl.contains("xhslink.com") -> Platform.XIAOHONGSHU
            lowerUrl.contains("kugou.com") || lowerUrl.contains("kgimg.com") -> Platform.KUGOU
            lowerUrl.contains("qq.com") && (lowerUrl.contains("music") || lowerUrl.contains("y.qq.com")) -> Platform.QQMUSIC
            lowerUrl.contains("bilibili.com") || lowerUrl.contains("b23.tv") || lowerUrl.contains("bilivideo.com") -> Platform.BILIBILI
            else -> Platform.OTHER
        }
    }

    private fun extractFilename(url: String): String {
        val lowerUrl = url.lowercase()
        
        // 处理B站链接
        if (lowerUrl.contains("bilibili.com") || lowerUrl.contains("b23.tv")) {
            val bvMatch = Regex("bv[0-9a-z]+", RegexOption.IGNORE_CASE).find(url)
            val avMatch = Regex("av[0-9]+", RegexOption.IGNORE_CASE).find(url)
            return when {
                bvMatch != null -> "bilibili_${bvMatch.value}.mp4"
                avMatch != null -> "bilibili_${avMatch.value}.mp4"
                else -> "bilibili_video.mp4"
            }
        }
        
        val path = url.substringBefore("?").substringAfterLast("/")
        return if (path.isNotEmpty() && path.contains(".")) {
            path
        } else {
            // 尝试从域名生成一个更有意义的文件名
            val host = try {
                java.net.URL(url).host.replace("www.", "").replace(".com", "").replace(".cn", "")
            } catch (e: Exception) {
                "download"
            }
            "${host}_video.mp4"
        }
    }
}

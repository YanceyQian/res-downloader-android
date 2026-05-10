package com.resdownloader.network

import android.util.Log
import com.resdownloader.data.model.Platform
import com.resdownloader.data.model.ResourceInfo
import com.resdownloader.data.model.ResourceType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

/**
 * 平台下载器接口
 * 参考原项目 res-downloader 的插件系统设计
 * 
 * 原项目架构:
 * - Plugin Interface: 每个平台有独立的插件
 * - Bridge: 插件与核心通信
 * - MediaInfo: 统一的资源信息
 * 
 * Android 端实现:
 * - PlatformDownloader: 统一下载器接口
 * - 各平台下载器实现特定平台的解析逻辑
 */
interface PlatformDownloader {

    companion object {
        private const val TAG = "PlatformDownloader"
    }

    /**
     * 获取该下载器支持的平台
     */
    val platform: Platform

    /**
     * 检查此下载器是否支持处理该 URL
     */
    fun isSupported(url: String): Boolean = platform.matches(url)

    /**
     * 解析资源信息
     * @param url 分享链接或直链
     * @return 解析结果
     */
    suspend fun resolve(url: String): ResolveResult

    /**
     * 开始下载
     * @param resource 资源信息
     * @param outputDir 输出目录
     * @return 下载结果流
     */
    suspend fun download(resource: ResourceInfo, outputDir: File): Flow<DownloadProgress>

    /**
     * 取消下载
     */
    fun cancel()

    /**
     * 解析结果
     */
    data class ResolveResult(
        val success: Boolean,
        val resource: ResourceInfo? = null,
        val error: String? = null
    )

    /**
     * 下载进度
     */
    data class DownloadProgress(
        val taskId: String,
        val progress: Int,           // 0-100
        val downloadedBytes: Long,
        val totalBytes: Long,
        val status: Status = Status.DOWNLOADING,
        val outputFile: File? = null,
        val error: String? = null
    )

    /**
     * 下载状态
     */
    enum class Status {
        PENDING,
        DOWNLOADING,
        DECRYPTING,
        MERGING,
        COMPLETED,
        FAILED,
        CANCELLED
    }
}

/**
 * 下载器工厂
 * 根据 URL 自动选择合适的下载器
 */
object DownloaderFactory {

    private val downloaders = mutableMapOf<Platform, PlatformDownloader>()

    /**
     * 注册下载器
     */
    fun register(downloader: PlatformDownloader) {
        downloaders[downloader.platform] = downloader
        Log.d("DownloaderFactory", "Registered: ${downloader.platform.displayName}")
    }

    /**
     * 获取合适的下载器
     */
    fun getDownloader(platform: Platform): PlatformDownloader? {
        return downloaders[platform]
    }

    /**
     * 根据 URL 自动选择下载器
     */
    fun getDownloaderForUrl(url: String): PlatformDownloader? {
        val platform = Platform.detect(url)
        return downloaders[platform] ?: downloaders[Platform.GENERAL]
    }

    /**
     * 检测 URL 平台
     */
    fun detectPlatform(url: String): Platform {
        return Platform.detect(url)
    }

    /**
     * 获取所有已注册的下载器
     */
    fun getAllDownloaders(): Map<Platform, PlatformDownloader> {
        return downloaders.toMap()
    }

    /**
     * 获取支持的平台列表
     */
    fun getSupportedPlatforms(): List<Platform> {
        return downloaders.keys.toList()
    }
}

/**
 * 下载器基类
 * 提供通用功能和工具方法
 */
abstract class BaseDownloader(override val platform: Platform) : PlatformDownloader {

    protected val tag = platform.name

    /**
     * 生成任务 ID
     */
    protected fun generateTaskId(): String {
        return "${platform.name}_${System.currentTimeMillis()}"
    }

    /**
     * 清理文件名
     */
    protected fun cleanFilename(filename: String): String {
        return filename
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("\\s+"), "_")
            .trim()
            .take(100)
            .ifEmpty { "video_${System.currentTimeMillis()}" }
    }

    /**
     * 获取文件扩展名
     */
    protected fun getExtension(url: String, defaultExt: String = "mp4"): String {
        val path = url.substringBefore("?").substringAfterLast("/")
        val ext = path.substringAfterLast(".", "").lowercase()
        return if (ext.isNotEmpty() && ext.length <= 5) ext else defaultExt
    }

    /**
     * 创建资源信息
     */
    protected fun createResourceInfo(
        url: String,
        title: String,
        type: ResourceType,
        platform: Platform,
        extraInfo: Map<String, String> = emptyMap(),
        timestamp: Long = System.currentTimeMillis()
    ): ResourceInfo {
        return ResourceInfo(
            id = generateTaskId(),
            url = url,
            filename = cleanFilename(title),
            platform = platform,
            type = type,
            size = 0,
            timestamp = timestamp,
            extraInfo = extraInfo
        )
    }
}

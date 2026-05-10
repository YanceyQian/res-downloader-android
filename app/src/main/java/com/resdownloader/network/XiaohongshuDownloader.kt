package com.resdownloader.network

import android.util.Log
import com.resdownloader.data.model.Platform
import com.resdownloader.data.model.ResourceInfo
import com.resdownloader.data.model.ResourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * 小红书下载器
 * 
 * 参考原项目 res-downloader 的通用资源抓取思路
 * 小红书使用特殊的 API 接口获取内容直链
 * 
 * 支持:
 * - 分享链接解析 (xhslink.com, xiaohongshu.com)
 * - 笔记内容下载 (图片、视频)
 * - 图集批量下载
 * - 视频下载
 */
class XiaohongshuDownloader : BaseDownloader(Platform.XIAOHONGSHU) {

    companion object {
        private const val TAG = "XiaohongshuDownloader"
        
        // 小红书分享链接模式
        private val SHARE_URL_PATTERNS = listOf(
            Regex("(xhslink\\.com/[a-zA-Z0-9]+)"),
            Regex("(xiaohongshu\\.com/explore/[a-zA-Z0-9]+)"),
            Regex("(xiaohongshu\\.com/discovery/item/[a-zA-Z0-9]+)")
        )
        
        // 小红书短链重定向
        private const val REDIRECT_API = "https://www.xiaohongshu.com"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /**
     * 内容类型
     */
    enum class ContentType {
        IMAGE,      // 图片笔记
        VIDEO,      // 视频笔记
        MIXED       // 图文混合
    }

    /**
     * 内容信息
     */
    data class ContentInfo(
        val noteId: String,
        val title: String,
        val type: ContentType,
        val mediaUrls: List<String>,    // 图片或视频 URL 列表
        val coverUrl: String?,
        val author: String?,
        val description: String?
    )

    /**
     * 解析分享链接
     */
    override suspend fun resolve(url: String): PlatformDownloader.ResolveResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Resolving Xiaohongshu URL: $url")
            
            // 1. 获取完整 URL
            val fullUrl = resolveShortUrl(url)
            if (fullUrl == null) {
                return@withContext PlatformDownloader.ResolveResult(
                    success = false,
                    error = "无法解析小红书链接，请检查链接格式"
                )
            }
            
            // 2. 提取笔记 ID
            val noteId = extractNoteId(fullUrl)
            if (noteId == null) {
                return@withContext PlatformDownloader.ResolveResult(
                    success = false,
                    error = "无法提取笔记 ID"
                )
            }
            
            Log.d(TAG, "Extracted note ID: $noteId")
            
            // 3. 获取笔记信息
            val contentInfo = fetchNoteInfo(noteId)
            if (contentInfo == null) {
                return@withContext PlatformDownloader.ResolveResult(
                    success = false,
                    error = "获取笔记信息失败，请稍后重试"
                )
            }
            
            // 4. 根据内容类型创建资源信息
            val resources = mutableListOf<ResourceInfo>()
            
            when (contentInfo.type) {
                ContentType.VIDEO -> {
                    // 视频笔记 - 创建一个视频资源
                    val videoUrl = contentInfo.mediaUrls.firstOrNull() ?: return@withContext PlatformDownloader.ResolveResult(
                        success = false,
                        error = "视频 URL 解析失败"
                    )
                    
                    resources.add(createResourceInfo(
                        url = videoUrl,
                        title = contentInfo.title.ifEmpty { "小红书视频_${noteId}" },
                        type = ResourceType.VIDEO,
                        platform = Platform.XIAOHONGSHU,
                        extraInfo = mapOf(
                            "note_id" to noteId,
                            "author" to (contentInfo.author ?: ""),
                            "cover_url" to (contentInfo.coverUrl ?: ""),
                            "description" to (contentInfo.description ?: "")
                        )
                    ))
                }
                
                ContentType.IMAGE, ContentType.MIXED -> {
                    // 图片笔记或混合内容 - 为每张图片创建资源
                    contentInfo.mediaUrls.forEachIndexed { index, imageUrl ->
                        resources.add(createResourceInfo(
                            url = imageUrl,
                            title = "${contentInfo.title.ifEmpty { noteId }}_${index + 1}",
                            type = ResourceType.IMAGE,
                            platform = Platform.XIAOHONGSHU,
                            extraInfo = mapOf(
                                "note_id" to noteId,
                                "index" to index.toString(),
                                "author" to (contentInfo.author ?: ""),
                                "description" to (contentInfo.description ?: "")
                            )
                        ))
                    }
                }
            }
            
            // 返回第一个资源作为主要结果
            val primaryResource = resources.firstOrNull()
            if (primaryResource == null) {
                return@withContext PlatformDownloader.ResolveResult(
                    success = false,
                    error = "未找到可下载的内容"
                )
            }
            
            Log.d(TAG, "Resolved content: ${contentInfo.title}, type: ${contentInfo.type}, items: ${resources.size}")
            PlatformDownloader.ResolveResult(success = true, resource = primaryResource)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving URL", e)
            PlatformDownloader.ResolveResult(success = false, error = e.message ?: "未知错误")
        }
    }

    /**
     * 下载内容
     */
    override suspend fun download(
        resource: ResourceInfo,
        outputDir: File
    ): Flow<PlatformDownloader.DownloadProgress> = flow {
        val taskId = resource.id
        
        emit(PlatformDownloader.DownloadProgress(
            taskId = taskId,
            progress = 0,
            downloadedBytes = 0,
            totalBytes = 0,
            status = PlatformDownloader.Status.DOWNLOADING
        ))
        
        try {
            // 确保目录存在
            if (!outputDir.exists()) {
                outputDir.mkdirs()
            }
            
            // 生成文件名
            val extension = getExtension(resource.url, "jpg")
            val filename = "${resource.filename}.$extension"
            val outputFile = File(outputDir, filename)
            val tempFile = File(outputDir, "temp_$filename")
            
            // 下载文件
            downloadFile(resource.url, tempFile).let { success ->
                if (!success) {
                    emit(PlatformDownloader.DownloadProgress(
                        taskId = taskId,
                        progress = 0,
                        downloadedBytes = 0,
                        totalBytes = 0,
                        status = PlatformDownloader.Status.FAILED,
                        error = "下载失败"
                    ))
                    return@flow
                }
            }
            
            // 重命名文件
            tempFile.renameTo(outputFile)
            
            emit(PlatformDownloader.DownloadProgress(
                taskId = taskId,
                progress = 100,
                downloadedBytes = outputFile.length(),
                totalBytes = outputFile.length(),
                status = PlatformDownloader.Status.COMPLETED,
                outputFile = outputFile
            ))
            
            Log.d(TAG, "Download completed: ${outputFile.absolutePath}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Download error", e)
            emit(PlatformDownloader.DownloadProgress(
                taskId = taskId,
                progress = 0,
                downloadedBytes = 0,
                totalBytes = 0,
                status = PlatformDownloader.Status.FAILED,
                error = e.message
            ))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 取消下载
     */
    override fun cancel() {
        // TODO: 实现下载取消
    }

    /**
     * 解析短链接
     */
    private fun resolveShortUrl(shortUrl: String): String? {
        return try {
            val request = Request.Builder()
                .url(shortUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml")
                .build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                response.request.url.toString()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving short URL", e)
            shortUrl
        }
    }

    /**
     * 提取笔记 ID
     */
    private fun extractNoteId(url: String): String? {
        // explore/xxx 格式
        val exploreMatch = Regex("explore/([a-zA-Z0-9]+)").find(url)
        if (exploreMatch != null) {
            return exploreMatch.groupValues[1]
        }
        
        // discovery/item/xxx 格式
        val itemMatch = Regex("discovery/item/([a-zA-Z0-9]+)").find(url)
        if (itemMatch != null) {
            return itemMatch.groupValues[1]
        }
        
        // 已经是笔记 ID
        if (url.contains("xiaohongshu.com") && url.length < 50) {
            return url.substringAfterLast("/").substringBefore("?")
        }
        
        return null
    }

    /**
     * 获取笔记信息
     * 使用第三方 API
     */
    private fun fetchNoteInfo(noteId: String): ContentInfo? {
        return try {
            // 方法1: 使用第三方 API
            val apiUrl = "https://api.xiaohongshu.wtf/api?url=https://www.xiaohongshu.com/explore/$noteId"
            
            val request = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36")
                .build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@use fetchNoteInfoFallback(noteId)
                }
                
                val body = response.body?.string() ?: return@use fetchNoteInfoFallback(noteId)
                parseNoteResponse(body)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching note info", e)
            fetchNoteInfoFallback(noteId)
        }
    }

    /**
     * 备用方案：直接访问页面解析
     */
    private fun fetchNoteInfoFallback(noteId: String): ContentInfo? {
        return try {
            val url = "https://www.xiaohongshu.com/explore/$noteId"
            
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                
                val html = response.body?.string() ?: return null
                parseHtmlResponse(html, noteId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fallback fetch error", e)
            null
        }
    }

    /**
     * 解析 API 响应
     */
    private fun parseNoteResponse(json: String): ContentInfo? {
        return try {
            val root = JSONObject(json)
            
            // 提取笔记 ID
            val noteIdRegex = Regex("\"noteId\"\\s*:\\s*\"?([a-zA-Z0-9]+)\"?")
            val noteIdMatch = noteIdRegex.find(json)
            val noteId = noteIdMatch?.groupValues?.get(1) ?: return null
            
            // 提取标题
            val titleRegex = Regex("\"title\"\\s*:\\s*\"?([^\",}]+)\"?")
            val titleMatch = titleRegex.find(json)
            val title = titleMatch?.groupValues?.get(1)?.take(100) ?: ""
            
            // 提取描述
            val descRegex = Regex("\"desc\"\\s*:\\s*\"?([^\"]+)\"?")
            val descMatch = descRegex.find(json)
            val description = descMatch?.groupValues?.get(1)
            
            // 提取作者
            val authorRegex = Regex("\"nickname\"\\s*:\\s*\"?([^\",}]+)\"?")
            val authorMatch = authorRegex.find(json)
            val author = authorMatch?.groupValues?.get(1)
            
            // 提取媒体 URL
            val mediaUrls = mutableListOf<String>()
            
            // 视频 URL
            val videoRegex = Regex("\"masterUrl\"\\s*:\\s*\"([^\"]+)\"")
            val videoMatch = videoRegex.find(json)
            if (videoMatch != null) {
                mediaUrls.add(videoMatch.groupValues[1].replace("\\u002F", "/"))
            }
            
            // 备用视频 URL
            if (mediaUrls.isEmpty()) {
                val altVideoRegex = Regex("\"stream\"\\s*:\\s*\\{[^}]*\"url\"\\s*:\\s*\"([^\"]+)\"")
                val altVideoMatch = altVideoRegex.find(json)
                if (altVideoMatch != null) {
                    mediaUrls.add(altVideoMatch.groupValues[1].replace("\\u002F", "/"))
                }
            }
            
            // 图片 URL
            if (mediaUrls.isEmpty()) {
                val imageRegex = Regex("\"url\"\\s*:\\s*\"(https?://[^\"]+image[^\"]+\\.(?:jpg|jpeg|png|webp))\"")
                val imageMatches = imageRegex.findAll(json)
                for (match in imageMatches) {
                    val url = match.groupValues[1].replace("\\u002F", "/")
                    if (!mediaUrls.contains(url)) {
                        mediaUrls.add(url)
                    }
                }
            }
            
            // 封面 URL
            val coverRegex = Regex("\"cover\"\\s*:\\s*\"([^\"]+)\"")
            val coverMatch = coverRegex.find(json)
            val coverUrl = coverMatch?.groupValues?.get(1)?.replace("\\u002F", "/")
            
            if (mediaUrls.isEmpty()) {
                Log.e(TAG, "No media URLs found in response")
                return null
            }
            
            // 判断内容类型
            val type = when {
                mediaUrls.any { it.contains(".mp4") || it.contains("video") } -> ContentType.VIDEO
                mediaUrls.size > 1 -> ContentType.MIXED
                else -> ContentType.IMAGE
            }
            
            ContentInfo(
                noteId = noteId,
                title = title.replace("\\n", " ").trim(),
                type = type,
                mediaUrls = mediaUrls,
                coverUrl = coverUrl,
                author = author,
                description = description
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing note response", e)
            null
        }
    }

    /**
     * 解析 HTML 响应
     */
    private fun parseHtmlResponse(html: String, noteId: String): ContentInfo? {
        return try {
            // 尝试从 script 标签中提取数据
            val scriptPatterns = listOf(
                "window\\.__INITIAL_STATE__\\s*=\\s*(\\{.*?\\});".toRegex(setOf(RegexOption.DOT_MATCHES_ALL)),
                "window\\.__XHS_DATA__\\s*=\\s*(\\{.*?\\});".toRegex(setOf(RegexOption.DOT_MATCHES_ALL)),
                "type=\"application/json\">(.*?)</script>".toRegex(setOf(RegexOption.DOT_MATCHES_ALL))
            )
            
            for (pattern in scriptPatterns) {
                val match = pattern.find(html)
                if (match != null) {
                    val data = match.groupValues[1]
                    val result = parseNoteResponse(data)
                    if (result != null) return result
                }
            }
            
            // 备用：从页面源码中提取媒体 URL
            val mediaPatterns = listOf(
                "https://[^\"'<>\\s]+\\.(mp4|m3u8)(?:[^<>\"'\\s]*)".toRegex(),
                "https://[^\"'<>\\s]+image[^\"'<>\\s]+\\.(?:jpg|jpeg|png|webp)".toRegex()
            )
            
            val mediaUrls = mutableListOf<String>()
            for (pattern in mediaPatterns) {
                val matches = pattern.findAll(html)
                for (match in matches) {
                    var url = match.value
                    url = url.replace("\\u002F", "/").replace("\\/", "/")
                    if (!mediaUrls.contains(url)) {
                        mediaUrls.add(url)
                    }
                }
            }
            
            if (mediaUrls.isEmpty()) {
                return null
            }
            
            val type = when {
                mediaUrls.any { it.contains(".mp4") || it.contains("video") } -> ContentType.VIDEO
                mediaUrls.size > 1 -> ContentType.MIXED
                else -> ContentType.IMAGE
            }
            
            ContentInfo(
                noteId = noteId,
                title = "",
                type = type,
                mediaUrls = mediaUrls,
                coverUrl = null,
                author = null,
                description = null
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing HTML response", e)
            null
        }
    }

    /**
     * 下载文件
     */
    private fun downloadFile(url: String, outputFile: File): Boolean {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36")
                .header("Referer", "https://www.xiaohongshu.com/")
                .build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Download failed: ${response.code}")
                    return false
                }
                
                val body = response.body ?: return false
                
                FileOutputStream(outputFile).use { output ->
                    body.byteStream().use { input ->
                        input.copyTo(output)
                    }
                }
                
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Download error", e)
            false
        }
    }
}

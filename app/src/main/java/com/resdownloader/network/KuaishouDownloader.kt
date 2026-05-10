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
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * 快手下载器
 * 
 * 参考原项目 res-downloader 的通用资源抓取思路
 * 快手使用特殊的 API 接口获取视频直链
 * 
 * 支持:
 * - 分享链接解析 (ksurl.cn, v.kuaishou.com)
 * - 视频直链获取
 * - 无水印下载
 */
class KuaishouDownloader : BaseDownloader(Platform.KUAISHOU) {

    companion object {
        private const val TAG = "KuaishouDownloader"
        
        // 快手分享链接模式
        private val SHARE_URL_PATTERNS = listOf(
            Regex("(ksurl\\.cn/[a-zA-Z0-9]+)"),
            Regex("(v\\.kuaishou\\.com/[a-zA-Z0-9]+)"),
            Regex("(www\\.kuaishou\\.com/profile/[^\\s]+)")
        )
        
        // 快手短链 API
        private const val SHORT_URL_API = "https://v.kuaishou.com/f"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /**
     * 视频信息
     */
    data class VideoInfo(
        val photoId: String,
        val title: String,
        val videoUrl: String,
        val coverUrl: String?,
        val duration: Long,
        val size: Long,
        val author: String?
    )

    /**
     * 解析分享链接
     */
    override suspend fun resolve(url: String): PlatformDownloader.ResolveResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Resolving Kuaishou URL: $url")
            
            // 1. 提取视频 ID
            val videoId = extractVideoId(url)
            if (videoId == null) {
                return@withContext PlatformDownloader.ResolveResult(
                    success = false,
                    error = "无法解析快手链接，请检查链接格式"
                )
            }
            
            Log.d(TAG, "Extracted video ID: $videoId")
            
            // 2. 获取视频信息
            val videoInfo = fetchVideoInfo(videoId, url)
            if (videoInfo == null) {
                return@withContext PlatformDownloader.ResolveResult(
                    success = false,
                    error = "获取视频信息失败，请稍后重试"
                )
            }
            
            // 3. 创建资源信息
            val resource = createResourceInfo(
                url = videoInfo.videoUrl,
                title = videoInfo.title.ifEmpty { "快手视频_${videoInfo.photoId}" },
                type = ResourceType.VIDEO,
                platform = Platform.KUAISHOU,
                extraInfo = mapOf(
                    "photo_id" to videoInfo.photoId,
                    "author" to (videoInfo.author ?: ""),
                    "cover_url" to (videoInfo.coverUrl ?: "")
                )
            )
            
            Log.d(TAG, "Resolved video: ${videoInfo.title}")
            PlatformDownloader.ResolveResult(success = true, resource = resource)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving URL", e)
            PlatformDownloader.ResolveResult(success = false, error = e.message ?: "未知错误")
        }
    }

    /**
     * 下载视频
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
            val extension = getExtension(resource.url)
            val filename = "${resource.filename}.$extension"
            val outputFile = File(outputDir, filename)
            val tempFile = File(outputDir, "temp_$filename")
            
            // 下载视频
            downloadFile(resource.url, tempFile) { downloaded, total ->
                // 进度更新由 flow 发射
            }.let { success ->
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
     * 取消下载（预留）
     */
    override fun cancel() {
        // TODO: 实现下载取消
    }

    /**
     * 提取视频 ID
     */
    private fun extractVideoId(url: String): String? {
        // 处理短链
        if (url.contains("ksurl.cn") || url.contains("v.kuaishou.com")) {
            return resolveShortUrl(url)
        }
        
        // 直接从 URL 提取
        for (pattern in SHARE_URL_PATTERNS) {
            val match = pattern.find(url)
            if (match != null) {
                val matched = match.groupValues[1]
                // 如果匹配到完整 URL，尝试获取 photoId
                if (matched.contains("profile")) {
                    return extractPhotoIdFromProfile(url)
                }
                return matched
            }
        }
        
        // 从 profile URL 提取
        return extractPhotoIdFromProfile(url)
    }

    /**
     * 从 profile URL 提取 photoId
     */
    private fun extractPhotoIdFromProfile(url: String): String? {
        // ksurl.cn/xxx 格式
        val shortMatch = Regex("ksurl\\.cn/([a-zA-Z0-9]+)").find(url)
        if (shortMatch != null) {
            return shortMatch.groupValues[1]
        }
        
        // v.kuaishou.com/xxx 格式
        val vMatch = Regex("v\\.kuaishou\\.com/([a-zA-Z0-9]+)").find(url)
        if (vMatch != null) {
            return vMatch.groupValues[1]
        }
        
        // profile URL
        val profileMatch = Regex("photoId=([a-zA-Z0-9_]+)").find(url)
        if (profileMatch != null) {
            return profileMatch.groupValues[1]
        }
        
        return null
    }

    /**
     * 解析短链接
     */
    private fun resolveShortUrl(shortUrl: String): String? {
        return try {
            val request = Request.Builder()
                .url(shortUrl)
                .header("User-Agent", "Mozilla/5.0")
                .header("Referer", "https://www.kuaishou.com/")
                .build()
            
            client.newCall(request).execute().use { response ->
                val finalUrl = response.request.url.toString()
                
                // 从重定向 URL 中提取 photoId
                val photoIdMatch = Regex("photoId=([a-zA-Z0-9_]+)").find(finalUrl)
                if (photoIdMatch != null) {
                    return@use photoIdMatch.groupValues[1]
                }
                
                // 如果是完整 URL，返回 URL
                if (finalUrl.contains("kuaishou.com")) {
                    return@use extractPhotoIdFromProfile(finalUrl)
                }
                
                finalUrl
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving short URL", e)
            null
        }
    }

    /**
     * 获取视频信息
     * 使用第三方 API
     */
    private fun fetchVideoInfo(videoId: String, originalUrl: String): VideoInfo? {
        return try {
            // 方法1: 使用第三方 API
            val apiUrl = "https://api.kuaishou.wtf/api?url=$originalUrl"
            
            val request = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36")
                .build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@use fetchVideoInfoFallback(originalUrl)
                }
                
                val body = response.body?.string() ?: return@use fetchVideoInfoFallback(originalUrl)
                parseVideoResponse(body)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching video info", e)
            fetchVideoInfoFallback(originalUrl)
        }
    }

    /**
     * 备用方案：直接访问页面解析
     */
    private fun fetchVideoInfoFallback(url: String): VideoInfo? {
        return try {
            // 先解析短链
            val resolvedUrl = resolveShortUrl(url) ?: url
            val fullUrl = if (resolvedUrl.contains("http")) resolvedUrl else "https://www.kuaishou.com/$resolvedUrl"
            
            val request = Request.Builder()
                .url(fullUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml")
                .build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                
                val html = response.body?.string() ?: return null
                parseHtmlResponse(html)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fallback fetch error", e)
            null
        }
    }

    /**
     * 解析 API 响应
     */
    private fun parseVideoResponse(json: String): VideoInfo? {
        return try {
            // 提取视频 URL
            val videoUrlRegex = listOf(
                Regex("\"play_addr\"\\s*:\\s*\\{[^}]*\"url_list\"\\s*:\\s*\\[\\s*\"([^\"]+)\""),
                Regex("\"srcNoMark\"\\s*:\\s*\"([^\"]+)\""),
                Regex("\"src\"\\s*:\\s*\"(https?://[^\"]+\\.(mp4|m3u8)[^\"]*)\""),
                Regex("https://[^\"'<>\\s]+\\.(mp4|m3u8)(?:[^<>\"'\\s]*)")
            )
            
            var videoUrl: String? = null
            for (regex in videoUrlRegex) {
                val match = regex.find(json)
                if (match != null) {
                    videoUrl = match.groupValues[1]
                        .replace("\\u002F", "/")
                        .replace("\\/", "/")
                    break
                }
            }
            
            if (videoUrl == null) {
                Log.e(TAG, "Video URL not found in response")
                return null
            }
            
            // 提取标题
            val titleRegex = Regex("\"caption\"\\s*:\\s*\"([^\"]+)\"")
            val titleMatch = titleRegex.find(json)
            val title = titleMatch?.groupValues?.get(1)
                ?.replace("\\n", " ")
                ?.trim()
                ?.take(100)
                ?: "快手视频"
            
            // 提取 photoId
            val photoIdRegex = Regex("\"photoId\"\\s*:\\s*\"?([a-zA-Z0-9_]+)\"?")
            val photoIdMatch = photoIdRegex.find(json)
            val photoId = photoIdMatch?.groupValues?.get(1) ?: "unknown"
            
            // 提取封面
            val coverRegex = Regex("\"coverUrl\"\\s*:\\s*\"([^\"]+)\"")
            val coverMatch = coverRegex.find(json)
            val coverUrl = coverMatch?.groupValues?.get(1)?.replace("\\u002F", "/")
            
            // 提取作者
            val authorRegex = Regex("\"author\"\\s*:\\s*\\{[^}]*\"name\"\\s*:\\s*\"([^\"]+)\"")
            val authorMatch = authorRegex.find(json)
            val author = authorMatch?.groupValues?.get(1)
            
            VideoInfo(
                photoId = photoId,
                title = title,
                videoUrl = videoUrl,
                coverUrl = coverUrl,
                duration = 0,
                size = 0,
                author = author
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing video response", e)
            null
        }
    }

    /**
     * 解析 HTML 响应
     */
    private fun parseHtmlResponse(html: String): VideoInfo? {
        return try {
            // 尝试从 script 标签中提取数据
            val scriptPatterns = listOf(
                "window\\.__APOLLO_STATE__\\s*=\\s*(\\{.*?\\});".toRegex(setOf(RegexOption.DOT_MATCHES_ALL)),
                "<script>window\\.__INITIAL_STATE__\\s*=\\s*(.*?)</script>".toRegex(setOf(RegexOption.DOT_MATCHES_ALL)),
                "type=\"application/json\">(.*?)</script>".toRegex(setOf(RegexOption.DOT_MATCHES_ALL))
            )
            
            for (pattern in scriptPatterns) {
                val match = pattern.find(html)
                if (match != null) {
                    val data = match.groupValues[1]
                    val result = parseVideoResponse(data)
                    if (result != null) return result
                }
            }
            
            // 备用：从页面源码中提取视频 URL
            val videoUrlPatterns = listOf(
                "https?://[^\"'<>\\s]+\\.(mp4|m3u8)(?:[^<>\"'\\s]*)".toRegex(),
                "\"play_addr\"\\s*:\\s*\"([^\"]+)\"".toRegex(),
                "\"src\"\\s*:\\s*\"(https?://[^\"]+)\"".toRegex()
            )
            
            for (pattern in videoUrlPatterns) {
                val match = pattern.find(html)
                if (match != null) {
                    var url = match.groupValues[1]
                    url = url.replace("\\u002F", "/").replace("\\/", "/")
                    
                    return VideoInfo(
                        photoId = "unknown",
                        title = "快手视频",
                        videoUrl = url,
                        coverUrl = null,
                        duration = 0,
                        size = 0,
                        author = null
                    )
                }
            }
            
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing HTML response", e)
            null
        }
    }

    /**
     * 下载文件
     */
    private fun downloadFile(url: String, outputFile: File, onProgress: (Long, Long) -> Unit): Boolean {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36")
                .header("Referer", "https://www.kuaishou.com/")
                .build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Download failed: ${response.code}")
                    return false
                }
                
                val body = response.body ?: return false
                val totalBytes = body.contentLength()
                
                FileOutputStream(outputFile).use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var totalRead = 0L
                        
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalRead += bytesRead
                            onProgress(totalRead, totalBytes)
                        }
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

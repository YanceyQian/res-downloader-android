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
import java.net.URL
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * 抖音下载器
 * 
 * 参考原项目 res-downloader 的通用资源抓取思路
 * 抖音使用特殊的 API 接口获取视频直链
 * 
 * 支持:
 * - 分享链接解析 (v.douyin.com/xxx)
 * - 视频直链获取
 * - 无水印下载
 */
class DouyinDownloader : BaseDownloader(Platform.DOUYIN) {

    companion object {
        private const val TAG = "DouyinDownloader"
        
        // 抖音分享链接模式
        private val SHARE_URL_PATTERNS = listOf(
            "(v\\.douyin\\.com/[a-zA-Z0-9]+)".toRegex(),
            "(www\\.douyin\\.com/video/(\\d+))".toRegex()
        )
        
        // 短链重定向 API
        private const val SHORT_URL_API = "https://api.douyin.wtf/api"
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
        val awemeId: String,
        val title: String,
        val videoUrl: String,
        val coverUrl: String?,
        val musicUrl: String?,
        val duration: Long,
        val size: Long,
        val author: String?
    )

    /**
     * 解析分享链接
     */
    override suspend fun resolve(url: String): PlatformDownloader.ResolveResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Resolving Douyin URL: $url")
            
            // 1. 提取视频 ID
            val videoId = extractVideoId(url)
            if (videoId == null) {
                return@withContext PlatformDownloader.ResolveResult(
                    success = false,
                    error = "无法解析抖音链接，请检查链接格式"
                )
            }
            
            Log.d(TAG, "Extracted video ID: $videoId")
            
            // 2. 获取视频信息
            val videoInfo = fetchVideoInfo(videoId)
            if (videoInfo == null) {
                return@withContext PlatformDownloader.ResolveResult(
                    success = false,
                    error = "获取视频信息失败，请稍后重试"
                )
            }
            
            // 3. 创建资源信息
            val resource = createResourceInfo(
                url = videoInfo.videoUrl,
                title = videoInfo.title.ifEmpty { "抖音视频_${videoInfo.awemeId}" },
                type = ResourceType.VIDEO,
                platform = Platform.DOUYIN,
                extraInfo = mapOf(
                    "aweme_id" to videoInfo.awemeId,
                    "author" to (videoInfo.author ?: ""),
                    "cover_url" to (videoInfo.coverUrl ?: ""),
                    "music_url" to (videoInfo.musicUrl ?: "")
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
        if (url.contains("v.douyin.com")) {
            return resolveShortUrl(url)
        }
        
        // 直接从 URL 提取
        for (pattern in SHARE_URL_PATTERNS) {
            val match = pattern.find(url)
            if (match != null) {
                return match.groupValues[2].ifEmpty { match.groupValues[1] }
            }
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
                .build()
            
            client.newCall(request).execute().use { response ->
                val finalUrl = response.request.url.toString()
                
                // 从重定向 URL 中提取视频 ID
                val regex = "video/(\\d+)".toRegex()
                val match = regex.find(finalUrl)
                match?.groupValues?.get(1)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving short URL", e)
            null
        }
    }

    /**
     * 获取视频信息
     * 使用第三方 API (api.douyin.wtf)
     */
    private fun fetchVideoInfo(videoId: String): VideoInfo? {
        return try {
            // 方法1: 使用第三方 API
            val apiUrl = "https://api.douyin.wtf/api?url=$videoId"
            
            val request = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36")
                .build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@use fetchVideoInfoFallback(videoId)
                }
                
                val body = response.body?.string() ?: return@use fetchVideoInfoFallback(videoId)
                parseVideoResponse(body)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching video info", e)
            fetchVideoInfoFallback(videoId)
        }
    }

    /**
     * 备用方案：直接访问页面解析
     */
    private fun fetchVideoInfoFallback(videoId: String): VideoInfo? {
        return try {
            val url = if (videoId.startsWith("http")) videoId else "https://www.douyin.com/video/$videoId"
            
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml")
                .build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                
                val html = response.body?.string() ?: return null
                parseHtmlResponse(html, videoId)
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
            val regex = Regex("\"play_addr\"\\s*:\\s*\\{[^}]*\"url_list\"\\s*:\\s*\\[\\s*\"([^\"]+)\"")
            val urlMatch = regex.find(json)
            
            val videoUrl = urlMatch?.groupValues?.get(1)
                ?: Regex("\"url_list\"\\s*:\\s*\\[\\s*\"([^\"]+)\"").find(json)?.groupValues?.get(1)
                ?: return null
            
            val titleRegex = Regex("\"desc\"\\s*:\\s*\"([^\"]+)\"")
            val titleMatch = titleRegex.find(json)
            val title = titleMatch?.groupValues?.get(1)?.take(100) ?: "抖音视频"
            
            val awemeIdRegex = Regex("\"aweme_id\"\\s*:\\s*\"?(\\d+)\"?")
            val awemeIdMatch = awemeIdRegex.find(json)
            val awemeId = awemeIdMatch?.groupValues?.get(1) ?: ""
            
            VideoInfo(
                awemeId = awemeId,
                title = title.replace("\\n", " ").trim(),
                videoUrl = videoUrl.replace("\\u002F", "/").ifEmpty { "" },
                coverUrl = null,
                musicUrl = null,
                duration = 0,
                size = 0,
                author = null
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing video response", e)
            null
        }
    }

    /**
     * 解析 HTML 响应
     */
    private fun parseHtmlResponse(html: String, videoId: String): VideoInfo? {
        return try {
            // 尝试从 script 标签中提取 RENDER_DATA
            val renderDataRegex = Regex("<script id=\"RENDER_DATA\" type=\"application/json\">(.*?)</script>")
            val renderDataMatch = renderDataRegex.find(html)
            
            if (renderDataMatch != null) {
                val renderData = renderDataMatch.groupValues[1]
                val decodedData = java.net.URLDecoder.decode(renderData, "utf-8")
                return parseVideoResponse(decodedData)
            }
            
            // 备用：从页面源码中提取视频 URL
            val videoUrlPatterns = listOf(
                Regex("\"playAddr\"\\s*:\\s*\"([^\"]+)\""),
                Regex("\"play_addr\"\\s*:\\s*\\{[^}]*\"url_list\"\\s*:\\s*\\[\\s*\"([^\"]+)\""),
                Regex("https?://[^\"'<>\\s]+\\.(mp4|m3u8)(?:[^<>\"'\\s]*)")
            )
            
            for (pattern in videoUrlPatterns) {
                val match = pattern.find(html)
                if (match != null) {
                    var url = match.groupValues[1]
                    url = url.replace("\\u002F", "/")
                    
                    return VideoInfo(
                        awemeId = videoId,
                        title = "抖音视频",
                        videoUrl = url,
                        coverUrl = null,
                        musicUrl = null,
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
                .header("Referer", "https://www.douyin.com/")
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

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

/**
 * 通用下载器
 * 
 * 用于处理直接链接和 M3U8 流媒体
 * 对应原项目 res-downloader 的 DefaultPlugin 通用资源抓取
 * 
 * 支持:
 * - 直接视频链接
 * - 直接音频链接
 * - 直接图片链接
 * - M3U8 播放列表
 * - 通用 HTTP/HTTPS 资源
 */
class GeneralDownloader : BaseDownloader(Platform.GENERAL) {

    companion object {
        private const val TAG = "GeneralDownloader"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    /**
     * 解析 URL
     * 对于通用下载器，直接返回资源信息
     */
    override suspend fun resolve(url: String): PlatformDownloader.ResolveResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Resolving general URL: $url")
            
            // 1. 检测资源类型
            val resourceType = detectResourceType(url)
            
            // 2. 获取文件信息
            val fileInfo = fetchFileInfo(url)
            
            // 3. 创建资源信息
            val resource = createResourceInfo(
                url = fileInfo.url,
                title = fileInfo.filename,
                type = resourceType,
                platform = Platform.GENERAL,
                extraInfo = mapOf(
                    "size" to fileInfo.size.toString(),
                    "content_type" to fileInfo.contentType
                )
            )
            
            Log.d(TAG, "Resolved: ${resource.filename}, type: $resourceType, size: ${fileInfo.size}")
            PlatformDownloader.ResolveResult(success = true, resource = resource)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving URL", e)
            PlatformDownloader.ResolveResult(success = false, error = e.message ?: "未知错误")
        }
    }

    /**
     * 下载资源
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
            
            // 获取文件大小
            val totalBytes = resource.extraInfo["size"]?.toLongOrNull() ?: 0L
            
            // 下载文件
            val success = downloadWithProgress(resource.url, tempFile) { downloaded ->
                // 发射进度更新
            }
            
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
     * 检测资源类型
     */
    private fun detectResourceType(url: String): ResourceType {
        val lowerUrl = url.lowercase()
        
        return when {
            lowerUrl.endsWith(".m3u8") -> ResourceType.M3U8
            lowerUrl.contains(".mp4") || lowerUrl.contains(".webm") || 
            lowerUrl.contains(".mkv") || lowerUrl.contains(".mov") ||
            lowerUrl.contains(".avi") || lowerUrl.contains(".flv") -> ResourceType.VIDEO
            lowerUrl.endsWith(".mp3") || lowerUrl.endsWith(".wav") || 
            lowerUrl.endsWith(".flac") || lowerUrl.endsWith(".aac") ||
            lowerUrl.endsWith(".m4a") || lowerUrl.endsWith(".ogg") -> ResourceType.AUDIO
            lowerUrl.endsWith(".jpg") || lowerUrl.endsWith(".jpeg") || 
            lowerUrl.endsWith(".png") || lowerUrl.endsWith(".gif") ||
            lowerUrl.endsWith(".webp") || lowerUrl.endsWith(".bmp") -> ResourceType.IMAGE
            else -> ResourceType.VIDEO // 默认视为视频
        }
    }

    /**
     * 文件信息
     */
    data class FileInfo(
        val url: String,
        val filename: String,
        val size: Long,
        val contentType: String
    )

    /**
     * 获取文件信息
     */
    private suspend fun fetchFileInfo(url: String): FileInfo = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .method("HEAD", null)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36")
                .build()
            
            client.newCall(request).execute().use { response ->
                val finalUrl = response.request.url.toString()
                val contentType = response.header("Content-Type") ?: "application/octet-stream"
                val contentLength = response.header("Content-Length")?.toLongOrNull() ?: 0L
                
                // 提取文件名
                val filename = extractFilename(finalUrl, contentType)
                
                FileInfo(
                    url = finalUrl,
                    filename = filename,
                    size = contentLength,
                    contentType = contentType
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching file info", e)
            // 返回基本信息
            FileInfo(
                url = url,
                filename = extractFilename(url, ""),
                size = 0,
                contentType = ""
            )
        }
    }

    /**
     * 提取文件名
     */
    private fun extractFilename(url: String, contentType: String): String {
        return try {
            val path = URL(url).path
            var name = path.substringAfterLast("/").substringBefore("?")
            
            if (name.isEmpty() || !name.contains(".")) {
                val ext = when {
                    contentType.contains("video/mp4") -> ".mp4"
                    contentType.contains("video/webm") -> ".webm"
                    contentType.contains("audio/mpeg") || contentType.contains("audio/mp3") -> ".mp3"
                    contentType.contains("audio/x-m4a") -> ".m4a"
                    contentType.contains("image/jpeg") -> ".jpg"
                    contentType.contains("image/png") -> ".png"
                    contentType.contains("image/gif") -> ".gif"
                    url.contains(".mp4") -> ".mp4"
                    url.contains(".webm") -> ".webm"
                    url.contains(".mp3") -> ".mp3"
                    url.contains(".m4a") -> ".m4a"
                    url.contains(".jpg") -> ".jpg"
                    url.contains(".png") -> ".png"
                    else -> ".mp4"
                }
                name = "download_${System.currentTimeMillis()}$ext"
            }
            
            cleanFilename(name)
        } catch (e: Exception) {
            "download_${System.currentTimeMillis()}.mp4"
        }
    }

    /**
     * 带进度下载文件
     */
    private fun downloadWithProgress(
        url: String,
        outputFile: File,
        onProgress: (Long) -> Unit
    ): Boolean {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36")
                .header("Accept", "*/*")
                .build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Download failed: ${response.code}")
                    return false
                }
                
                // 检查是否为 HTML 页面（可能是重定向页面）
                val contentType = response.header("Content-Type") ?: ""
                if (contentType.contains("text/html") && 
                    (response.header("Content-Length")?.toLongOrNull() ?: 0) < 1024 * 1024) {
                    Log.e(TAG, "Received HTML page instead of media")
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
                            onProgress(totalRead)
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

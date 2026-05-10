package com.resdownloader.util

import android.util.Log
import com.resdownloader.data.model.Platform
import com.resdownloader.data.model.ResourceInfo
import com.resdownloader.data.model.ResourceType
import com.resdownloader.network.PlatformDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * 微信视频号下载器
 * 
 * 功能：
 * 1. 从分享链接解析视频信息
 * 2. 下载并处理视频文件
 * 3. 保存到本地文件
 */
class WechatVideoDownloader : PlatformDownloader {

    // PlatformDownloader 接口实现
    override val platform: Platform = Platform.WECHAT

    companion object {
        private const val TAG = "WechatVideoDownloader"
        
        // 视频号相关域名
        private val VIDEO_ACCOUNT_DOMAINS = listOf(
            "channels.weixin.qq.com",
            "mp.weixin.qq.com",
            "res.wx.qq.com",
            "weixin.qq.com"
        )
        
        // 视频号 API 地址
        private const val VIDEO_DETAIL_API = "https://channels.weixin.qq.com/platform/lib/video/detail"
        private const val VIDEO_SEARCH_API = "https://channels.weixin.qq.com/platform/lib/video/search"
        
        // 视频号短链格式
        private val SHORT_URL_PATTERN = Pattern.compile("(https?://(?:wx\\.)?short\\.ws/[a-zA-Z0-9]+)")
        private val VIDEO_ID_PATTERN = Pattern.compile("(?:id|vid)=([a-zA-Z0-9_\\-]+)", Pattern.CASE_INSENSITIVE)
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /**
     * 视频信息
     */
    data class VideoInfo(
        val videoId: String,
        val title: String,
        val videoUrl: String,
        val coverUrl: String?,
        val decodeKey: String?,
        val duration: Long,
        val size: Long
    )

    /**
     * 下载结果
     */
    data class DownloadResult(
        val success: Boolean,
        val file: File? = null,
        val error: String? = null,
        val videoInfo: VideoInfo? = null
    )

    /**
     * 从分享链接解析视频信息
     * 
     * @param shareUrl 视频号分享链接
     * @return 视频信息
     */
    suspend fun resolveVideo(shareUrl: String): VideoInfo? = withContext(Dispatchers.IO) {
        Log.d(TAG, "Resolving video from URL: $shareUrl")
        
        try {
            // 1. 解析视频 ID
            val videoId = extractVideoId(shareUrl)
            if (videoId == null) {
                Log.e(TAG, "Failed to extract video ID from URL")
                // 触发 URL 解析错误
                onResolveError?.invoke(Exception("无法提取视频 ID"))
                return@withContext null
            }
            
            // 2. 调用 API 获取视频信息
            val videoInfo = fetchVideoInfo(videoId)
            if (videoInfo == null) {
                Log.e(TAG, "Failed to fetch video info")
                // API 获取失败，可能需要更新
                onResolveError?.invoke(Exception("API 获取视频信息失败"))
                return@withContext null
            }
            
            Log.d(TAG, "Video resolved: ${videoInfo.title}")
            videoInfo
            
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving video", e)
            onResolveError?.invoke(e)
            null
        }
    }

    /**
     * 错误回调（由调用者设置）
     */
    var onResolveError: ((Exception) -> Unit)? = null

    /**
     * 设置错误回调
     */
    fun setErrorCallback(callback: ((Exception) -> Unit)?) {
        this.onResolveError = callback
    }

    /**
     * 提取视频 ID
     */
    private fun extractVideoId(url: String): String? {
        // 尝试从短链解析
        val shortMatcher = SHORT_URL_PATTERN.matcher(url)
        if (shortMatcher.find()) {
            val shortUrl = shortMatcher.group(1)
            Log.d(TAG, "Found short URL: $shortUrl")
            // 短链需要重定向获取真实 URL
            return resolveShortUrl(shortUrl)
        }
        
        // 直接从 URL 提取
        val matcher = VIDEO_ID_PATTERN.matcher(url)
        if (matcher.find()) {
            return matcher.group(1)
        }
        
        // 从路径提取
        if (url.contains("product/")) {
            val parts = url.split("product/")
            if (parts.size > 1) {
                return parts[1].split("[?&#]".toRegex()).first()
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
                .get()
                .build()
            
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.request.url.toString()
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving short URL", e)
            null
        }
    }

    /**
     * 获取视频信息
     */
    private fun fetchVideoInfo(videoId: String): VideoInfo? {
        return try {
            // 构建请求参数
            val params = mapOf(
                "id" to videoId,
                "biz" to "video",
                "mid" to videoId,
                "idx" to "1",
                "vid" to videoId
            )
            
            val requestBody = params.entries.joinToString("&") { "${it.key}=${it.value}" }
            
            val request = Request.Builder()
                .url(VIDEO_DETAIL_API)
                .post(requestBody.toRequestBody("application/x-www-form-urlencoded"))
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 10; SM-G9750) AppleWebKit/537.36")
                .addHeader("Referer", "https://channels.weixin.qq.com/")
                .build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "API request failed: ${response.code}")
                    return null
                }
                
                val body = response.body?.string() ?: return null
                parseVideoResponse(body, videoId)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching video info", e)
            null
        }
    }

    /**
     * 解析视频响应
     * 
     * 从 API 响应中提取视频信息
     */
    private fun parseVideoResponse(response: String, videoId: String): VideoInfo? {
        return try {
            val json = org.json.JSONObject(response)
            
            // 检查响应状态
            if (json.optInt("ret", -1) != 0) {
                Log.e(TAG, "API returned error: ${json.optString("errmsg")}")
                return null
            }
            
            val data = json.optJSONObject("data") ?: return null
            
            // 提取视频 URL
            val videoUrl = extractVideoUrl(data)
            if (videoUrl == null) {
                Log.e(TAG, "Video URL not found in response")
                return null
            }
            
            // 解析视频信息
            val decodeKey = data.optString("decodeKey", "")
            
            // 提取其他信息
            val title = data.optString("title", "视频_$videoId")
            val coverUrl = data.optString("thumb_url", null)
            val duration = data.optLong("duration", 0)
            val size = data.optLong("size", 0)
            
            VideoInfo(
                videoId = videoId,
                title = cleanFilename(title),
                videoUrl = videoUrl,
                coverUrl = coverUrl,
                decodeKey = decodeKey.ifEmpty { null },
                duration = duration,
                size = size
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing video response", e)
            null
        }
    }

    /**
     * 从响应中提取视频 URL
     * 
     * 支持多种格式：
     * 1. direct_url - 直链
     * 2. urls - URL 数组
     * 3. src - 单个 URL
     * 4. video_url - 视频 URL
     */
    private fun extractVideoUrl(data: org.json.JSONObject): String? {
        // 尝试 direct_url
        var url = data.optString("direct_url", "")
        if (url.isNotEmpty()) return url
        
        // 尝试 urls 数组
        val urls = data.optJSONArray("urls")
        if (urls != null && urls.length() > 0) {
            // 取最高质量的 URL（通常最后一个）
            return urls.getString(urls.length() - 1)
        }
        
        // 尝试 src
        url = data.optString("src", "")
        if (url.isNotEmpty()) return url
        
        // 尝试 video_url
        url = data.optString("video_url", "")
        if (url.isNotEmpty()) return url
        
        // 尝试 media_url
        url = data.optString("media_url", "")
        if (url.isNotEmpty()) return url
        
        // 尝试从 urls 对象中获取
        val urlsObj = data.optJSONObject("urls")
        if (urlsObj != null) {
            val keys = urlsObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = urlsObj.optString(key, "")
                if (value.isNotEmpty()) return value
            }
        }
        
        return null
    }

    /**
     * 下载并处理视频
     * 
     * 执行完整的下载流程：
     * 1. 下载资源文件
     * 2. 处理文件格式
     * 3. 保存到本地
     */
    suspend fun downloadAndDecrypt(
        videoInfo: VideoInfo,
        outputDir: File,
        onProgress: (Int, Long, Long) -> Unit = { _, _, _ -> }
    ): DownloadResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "Downloading video: ${videoInfo.title}")
        
        try {
            // 1. 确保输出目录存在
            if (!outputDir.exists()) {
                outputDir.mkdirs()
            }
            
            // 2. 生成文件名
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val extension = if (videoInfo.decodeKey != null) ".enc.mp4" else ".mp4"
            val filename = "${videoInfo.title}$extension"
            val tempFile = File(outputDir, "temp_$timestamp$extension")
            val outputFile = File(outputDir, filename)
            
            // 3. 下载视频
            onProgress(0, 0, videoInfo.size)
            
            val downloadedSize = downloadFile(videoInfo.videoUrl, tempFile) { bytesRead, totalBytes ->
                onProgress(50, bytesRead, totalBytes)
            }
            
            if (downloadedSize == 0L) {
                return@withContext DownloadResult(false, error = "Download failed")
            }
            
            // 4. 如果需要解密
            val finalFile: File
            if (videoInfo.decodeKey != null) {
                Log.d(TAG, "Decrypting video with key...")
                onProgress(80, downloadedSize, downloadedSize)
                
                // 使用 VideoDecryptor 解密
                val decryptResult = VideoDecryptor.decryptFile(
                    tempFile,
                    outputFile,
                    videoInfo.decodeKey,
                    VideoDecryptor.DecryptMode.WECHAT_VIDEO
                )
                
                if (!decryptResult.success) {
                    // 解密失败，尝试直接使用原文件
                    Log.w(TAG, "Decryption failed, using original file")
                    tempFile.renameTo(outputFile)
                } else {
                    // 修复 MP4 头
                    VideoDecryptor.fixMp4Header(outputFile)
                }
                
                // 删除临时文件
                tempFile.delete()
                finalFile = outputFile
            } else {
                // 不需要解密，直接移动文件
                tempFile.renameTo(outputFile)
                finalFile = outputFile
            }
            
            onProgress(100, finalFile.length(), finalFile.length())
            
            Log.d(TAG, "Video downloaded: ${finalFile.absolutePath}")
            DownloadResult(true, finalFile, videoInfo = videoInfo)
            
        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            DownloadResult(false, error = e.message)
        }
    }

    /**
     * 下载文件
     */
    private fun downloadFile(url: String, outputFile: File, onProgress: (Long, Long) -> Unit): Long {
        return try {
            val request = Request.Builder()
                .url(url)
                .get()
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 10; SM-G9750) AppleWebKit/537.36")
                .addHeader("Referer", "https://channels.weixin.qq.com/")
                .build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Download failed: ${response.code}")
                    return 0L
                }
                
                val body = response.body ?: return 0L
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
                        
                        totalRead
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading file", e)
            0L
        }
    }

    /**
     * 检查 URL 是否为视频号链接
     */
    fun isVideoAccountUrl(url: String): Boolean {
        val lowerUrl = url.lowercase()
        return VIDEO_ACCOUNT_DOMAINS.any { lowerUrl.contains(it) } ||
               lowerUrl.contains("short.ws") ||
               lowerUrl.contains("channels.weixin")
    }

    /**
     * 从 ResourceInfo 创建下载任务
     */
    fun createDownloadTask(resource: ResourceInfo): DownloadResult {
        return try {
            if (resource.platform.name != "WECHAT") {
                return DownloadResult(false, error = "Not a WeChat resource")
            }
            
            val videoId = extractVideoId(resource.url) ?: resource.id
            val title = resource.filename.substringBeforeLast(".")
            
            val videoInfo = VideoInfo(
                videoId = videoId,
                title = title,
                videoUrl = resource.url,
                coverUrl = null,
                decodeKey = resource.decodeKey,
                duration = 0,
                size = resource.size
            )
            
            // 同步下载（简化版本）
            DownloadResult(true, videoInfo = videoInfo)
            
        } catch (e: Exception) {
            DownloadResult(false, error = e.message)
        }
    }

    /**
     * 清理文件名
     */
    private fun cleanFilename(filename: String): String {
        return filename
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("\\s+"), "_")
            .take(100)
    }

    /**
     * MD5 哈希
     */
    fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * 扩展函数：创建 RequestBody（兼容 OkHttp 4.x）
     */
    private fun String.toRequestBody(mediaType: String): okhttp3.RequestBody {
        return okhttp3.RequestBody.create(mediaType.toMediaType(), this)
    }

    // ==================== PlatformDownloader 接口实现 ====================

    /**
     * 解析资源信息（PlatformDownloader 接口）
     */
    override suspend fun resolve(url: String): PlatformDownloader.ResolveResult {
        return try {
            val videoInfo = resolveVideo(url)
            if (videoInfo == null) {
                return PlatformDownloader.ResolveResult(
                    success = false,
                    error = "无法解析微信视频链接，请检查链接格式"
                )
            }

            val resource = ResourceInfo(
                id = "wechat_${System.currentTimeMillis()}",
                url = videoInfo.videoUrl,
                filename = cleanFilename(videoInfo.title),
                platform = Platform.WECHAT,
                type = ResourceType.VIDEO,
                size = videoInfo.size,
                timestamp = System.currentTimeMillis(),
                extraInfo = mapOf(
                    "video_id" to videoInfo.videoId,
                    "decode_key" to (videoInfo.decodeKey ?: ""),
                    "cover_url" to (videoInfo.coverUrl ?: "")
                )
            )

            PlatformDownloader.ResolveResult(success = true, resource = resource)
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving URL", e)
            PlatformDownloader.ResolveResult(success = false, error = e.message ?: "未知错误")
        }
    }

    /**
     * 下载资源（PlatformDownloader 接口）
     */
    override suspend fun download(resource: ResourceInfo, outputDir: File): Flow<PlatformDownloader.DownloadProgress> = flow {
        emit(PlatformDownloader.DownloadProgress(
            taskId = resource.id,
            progress = 0,
            downloadedBytes = 0,
            totalBytes = 0,
            status = PlatformDownloader.Status.DOWNLOADING
        ))

        try {
            val result = downloadAndDecrypt(
                videoInfo = VideoInfo(
                    videoId = resource.id,
                    title = resource.filename,
                    videoUrl = resource.url,
                    coverUrl = null,
                    decodeKey = resource.decodeKey,
                    duration = 0L,
                    size = resource.size
                ),
                outputDir = outputDir
            )

            if (result.success) {
                emit(PlatformDownloader.DownloadProgress(
                    taskId = resource.id,
                    progress = 100,
                    downloadedBytes = result.file?.length() ?: 0,
                    totalBytes = result.file?.length() ?: 0,
                    status = PlatformDownloader.Status.COMPLETED,
                    outputFile = result.file
                ))
            } else {
                emit(PlatformDownloader.DownloadProgress(
                    taskId = resource.id,
                    progress = 0,
                    downloadedBytes = 0,
                    totalBytes = 0,
                    status = PlatformDownloader.Status.FAILED,
                    error = result.error
                ))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Download error", e)
            emit(PlatformDownloader.DownloadProgress(
                taskId = resource.id,
                progress = 0,
                downloadedBytes = 0,
                totalBytes = 0,
                status = PlatformDownloader.Status.FAILED,
                error = e.message
            ))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 取消下载（PlatformDownloader 接口）
     */
    override fun cancel() {
        // 微信下载暂不支持取消
        Log.d(TAG, "Cancel requested but not implemented")
    }
}

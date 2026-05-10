package com.resdownloader.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * HTTPS 资源抓取器
 * 支持多种平台的直接 URL 抓取：
 * - 直接视频链接
 * - M3U8 播放列表
 * - 社交媒体分享链接
 * - 云盘直链
 */
class ResourceFetcher {

    companion object {
        private const val TAG = "ResourceFetcher"
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    /**
     * 抓取结果
     */
    data class FetchResult(
        val success: Boolean,
        val url: String? = null,
        val type: ResourceType = ResourceType.UNKNOWN,
        val filename: String? = null,
        val size: Long = 0,
        val mimeType: String? = null,
        val error: String? = null
    )

    enum class ResourceType {
        VIDEO,
        AUDIO,
        IMAGE,
        M3U8,
        HTML,
        UNKNOWN
    }

    /**
     * 抓取资源
     */
    suspend fun fetch(url: String): FetchResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching: $url")

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36")
                .header("Accept", "*/*")
                .header("Referer", getBaseUrl(url))
                .build()

            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext FetchResult(
                    success = false,
                    error = "HTTP ${response.code}"
                )
            }

            val finalUrl = response.request.url.toString()
            val contentType = response.header("Content-Type") ?: ""
            val contentLength = response.body?.contentLength() ?: 0L

            Log.d(TAG, "Final URL: $finalUrl, Content-Type: $contentType, Size: $contentLength")

            // 检测资源类型
            val type = when {
                contentType.contains("video") -> ResourceType.VIDEO
                contentType.contains("audio") -> ResourceType.AUDIO
                contentType.contains("image") -> ResourceType.IMAGE
                finalUrl.endsWith(".m3u8") -> ResourceType.M3U8
                contentType.contains("text/html") -> ResourceType.HTML
                else -> ResourceType.UNKNOWN
            }

            // 如果是 HTML，尝试解析
            if (type == ResourceType.HTML || contentType.contains("text/html")) {
                val html = response.body?.string() ?: ""
                val parsed = parseHtmlResources(html, finalUrl)
                if (parsed != null) {
                    return@withContext parsed
                }
            }

            val filename = extractFilename(finalUrl, contentType)

            FetchResult(
                success = true,
                url = finalUrl,
                type = type,
                filename = filename,
                size = contentLength,
                mimeType = contentType
            )

        } catch (e: Exception) {
            Log.e(TAG, "Fetch error", e)
            FetchResult(success = false, error = e.message)
        }
    }

    /**
     * 解析 HTML 页面中的资源
     */
    private fun parseHtmlResources(html: String, baseUrl: String): FetchResult? {
        Log.d(TAG, "Parsing HTML page...")

        // 尝试匹配 video 标签
        val videoPatterns = listOf(
            Regex("<video[^>]*src=[\"']([^\"']+)[\"']"),
            Regex("<source[^>]*src=[\"']([^\"']+)[\"']"),
            Regex("\"url\":\\s*[\"']([^\"']+\\.(mp4|webm|m3u8)[^\"']*)[\"']"),
            Regex("\"src\":\\s*[\"']([^\"']+\\.(mp4|webm|m3u8)[^\"']*)[\"']"),
            Regex("https?://[^\"'<>\\s]+\\.(?:mp4|webm|m3u8)(?:[^\"'<>\\s]*)")
        )

        for (pattern in videoPatterns) {
            val matches = pattern.findAll(html)
            for (match in matches) {
                val videoUrl = match.groupValues[1].ifEmpty { match.value }
                if (videoUrl.startsWith("http")) {
                    Log.d(TAG, "Found video URL: $videoUrl")
                    return FetchResult(
                        success = true,
                        url = videoUrl,
                        type = if (videoUrl.endsWith(".m3u8")) ResourceType.M3U8 else ResourceType.VIDEO,
                        filename = extractFilename(videoUrl, "video/mp4")
                    )
                }
            }
        }

        // 尝试匹配 JSON 中的视频 URL
        val jsonPatterns = listOf(
            Regex("\"playUrl\"\\s*:\\s*\"([^\"]+)\""),
            Regex("\"url\"\\s*:\\s*\"([^\"]+)\""),
            Regex("\"src\"\\s*:\\s*\"([^\"]+)\"")
        )

        for (pattern in jsonPatterns) {
            val matches = pattern.findAll(html)
            for (match in matches) {
                val videoUrl = match.groupValues[1]
                if (videoUrl.startsWith("http") && (videoUrl.contains(".mp4") || videoUrl.contains(".m3u8") || videoUrl.contains("video"))) {
                    Log.d(TAG, "Found video URL in JSON: $videoUrl")
                    return FetchResult(
                        success = true,
                        url = videoUrl,
                        type = if (videoUrl.endsWith(".m3u8")) ResourceType.M3U8 else ResourceType.VIDEO,
                        filename = extractFilename(videoUrl, "video/mp4")
                    )
                }
            }
        }

        return null
    }

    /**
     * 从 URL 提取文件名
     */
    private fun extractFilename(url: String, contentType: String): String {
        return try {
            val path = URL(url).path
            val name = path.substringAfterLast("/").substringBefore("?")

            if (name.isNotEmpty() && name.contains(".")) {
                name
            } else {
                val ext = when {
                    contentType.contains("video/mp4") || contentType.contains("video/mpeg") -> "mp4"
                    contentType.contains("video/webm") -> "webm"
                    contentType.contains("audio/mpeg") || contentType.contains("audio/mp3") -> "mp3"
                    contentType.contains("audio/x-m4a") -> "m4a"
                    contentType.contains("image/jpeg") -> "jpg"
                    contentType.contains("image/png") -> "png"
                    url.contains(".mp4") -> "mp4"
                    url.contains(".webm") -> "webm"
                    url.contains(".m3u8") -> "m3u8"
                    url.contains(".mp3") -> "mp3"
                    url.contains(".m4a") -> "m4a"
                    else -> "mp4"
                }
                "${System.currentTimeMillis()}.$ext"
            }
        } catch (e: Exception) {
            "${System.currentTimeMillis()}.mp4"
        }
    }

    /**
     * 获取基础 URL
     */
    private fun getBaseUrl(url: String): String {
        return try {
            val parsed = URL(url)
            "${parsed.protocol}://${parsed.host}/"
        } catch (e: Exception) {
            url
        }
    }

    /**
     * 检测 URL 类型（不下载）
     */
    suspend fun detectType(url: String): ResourceType = withContext(Dispatchers.IO) {
        try {
            // 先检查 URL 后缀
            when {
                url.endsWith(".mp4") || url.endsWith(".webm") || url.endsWith(".mkv") ||
                url.contains("/video/") -> ResourceType.VIDEO
                url.endsWith(".mp3") || url.endsWith(".wav") || url.endsWith(".m4a") ||
                url.contains("/audio/") -> ResourceType.AUDIO
                url.endsWith(".m3u8") -> ResourceType.M3U8
                url.endsWith(".jpg") || url.endsWith(".png") || url.endsWith(".gif") -> ResourceType.IMAGE
                else -> {
                    // HEAD 请求检测
                    val request = Request.Builder()
                        .url(url)
                        .method("HEAD", null)
                        .header("User-Agent", "Mozilla/5.0")
                        .build()

                    val response = okHttpClient.newCall(request).execute()
                    val contentType = response.header("Content-Type") ?: ""

                    when {
                        contentType.contains("video") -> ResourceType.VIDEO
                        contentType.contains("audio") -> ResourceType.AUDIO
                        contentType.contains("image") -> ResourceType.IMAGE
                        else -> ResourceType.UNKNOWN
                    }
                }
            }
        } catch (e: Exception) {
            ResourceType.UNKNOWN
        }
    }

    /**
     * 获取文件大小
     */
    suspend fun getFileSize(url: String): Long = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .method("HEAD", null)
                .build()

            val response = okHttpClient.newCall(request).execute()
            response.header("Content-Length")?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            0L
        }
    }
}

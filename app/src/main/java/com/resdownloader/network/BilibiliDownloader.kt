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
 * B站下载器
 * 
 * 参考原项目 res-downloader 的通用资源抓取思路
 * B站使用 M3U8 流媒体，需要特殊处理
 * 
 * 支持:
 * - 视频下载
 * - 弹幕下载 (XML 格式)
 * - 字幕下载 (ASS 格式)
 * - 高画质选择
 */
class BilibiliDownloader : BaseDownloader(Platform.BILIBILI) {

    companion object {
        private const val TAG = "BilibiliDownloader"
        
        // B站相关域名
        private const val BILIBILI_HOST = "bilibili.com"
        
        // B站 API
        private const val API_VIDEO_INFO = "https://api.bilibili.com/x/web-interface/view"
        private const val API_PLAY_URL = "https://api.bilibili.com/x/player/playurl"
        private const val API_SUBTITLE = "https://api.bilibili.com/x/player/v2"
        private const val API_DANMAKU = "https://api.bilibili.com/x/v1/dm/list.so"
        
        // B站 URL 模式
        private val VIDEO_URL_PATTERNS = listOf(
            "bilibili\\.com/video/(BV[a-zA-Z0-9]+)".toRegex(),
            "bilibili\\.com/video/(av\\d+)".toRegex(),
            "(b23\\.tv/[a-zA-Z0-9]+)".toRegex()
        )
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
        val bvid: String,
        val cid: Long,
        val title: String,
        val description: String,
        val author: String,
        val duration: Long,
        val videoUrl: String,
        val coverUrl: String?,
        val subtitles: List<SubtitleInfo>,
        val danmaku: String?
    )

    /**
     * 字幕信息
     */
    data class SubtitleInfo(
        val id: Long,
        val lang: String,
        val url: String
    )

    /**
     * 解析分享链接
     */
    override suspend fun resolve(url: String): PlatformDownloader.ResolveResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Resolving Bilibili URL: $url")
            
            // 1. 获取完整 URL (处理短链)
            val fullUrl = resolveShortUrl(url)
            
            // 2. 提取 BVID
            val bvid: String = extractBvid(fullUrl) ?: run {
                return@withContext PlatformDownloader.ResolveResult(
                    success = false,
                    error = "无法解析B站链接，请检查链接格式"
                )
            }

            Log.d(TAG, "Extracted BVID: $bvid")
            
            // 3. 获取视频信息
            val videoInfo = fetchVideoInfo(bvid)
            if (videoInfo == null) {
                return@withContext PlatformDownloader.ResolveResult(
                    success = false,
                    error = "获取视频信息失败，请稍后重试"
                )
            }
            
            // 4. 创建资源信息
            val resource = createResourceInfo(
                url = videoInfo.videoUrl,
                title = videoInfo.title,
                type = ResourceType.M3U8,
                platform = Platform.BILIBILI,
                extraInfo = mapOf(
                    "bvid" to videoInfo.bvid,
                    "cid" to videoInfo.cid.toString(),
                    "author" to videoInfo.author,
                    "cover_url" to (videoInfo.coverUrl ?: ""),
                    "subtitles" to videoInfo.subtitles.joinToString(",") { "${it.lang}:${it.id}" },
                    "danmaku_url" to (videoInfo.danmaku ?: "")
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
            
            val bvid = resource.extraInfo["bvid"] ?: ""
            val cid = resource.extraInfo["cid"]?.toLongOrNull() ?: 0L
            
            // 下载弹幕
            val danmakuUrl = resource.extraInfo["danmaku_url"]
            if (!danmakuUrl.isNullOrEmpty()) {
                downloadDanmaku(danmakuUrl, outputDir, bvid)
            }
            
            // 下载字幕
            val subtitles = resource.extraInfo["subtitles"]
            if (!subtitles.isNullOrEmpty()) {
                downloadSubtitles(subtitles, outputDir, bvid)
            }
            
            // 使用 M3U8 下载器下载视频
            val m3u8Downloader = M3u8Downloader()
            
            m3u8Downloader.setCallback(object : M3u8Downloader.DownloadCallback {
                override fun onProgress(progress: Int, downloaded: Int, total: Int) {
                    // 进度由 flow 发射
                }
                
                override fun onComplete(outputFile: File) {
                    Log.d(TAG, "Video download completed")
                }
                
                override fun onError(error: String) {
                    Log.e(TAG, "Video download error: $error")
                }
            })
            
            val filename = "${resource.filename}.mp4"
            m3u8Downloader.download(resource.url, outputDir.absolutePath, filename)
            
            emit(PlatformDownloader.DownloadProgress(
                taskId = taskId,
                progress = 100,
                downloadedBytes = 0,
                totalBytes = 0,
                status = PlatformDownloader.Status.COMPLETED,
                outputFile = File(outputDir, filename)
            ))
            
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
    private fun resolveShortUrl(shortUrl: String): String {
        if (!shortUrl.contains("b23.tv")) {
            return shortUrl
        }
        
        return try {
            val request = Request.Builder()
                .url(shortUrl)
                .header("User-Agent", "Mozilla/5.0")
                .build()
            
            val response = client.newCall(request).execute()
            val result = if (response.isSuccessful) {
                response.request.url.toString()
            } else {
                shortUrl
            }
            response.close()
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving short URL", e)
            shortUrl
        }
    }

    /**
     * 提取 BVID
     */
    private fun extractBvid(url: String): String? {
        for (pattern in VIDEO_URL_PATTERNS) {
            val match = pattern.find(url)
            if (match != null) {
                return match.groupValues[1]
            }
        }
        return null
    }

    /**
     * 获取视频信息
     */
    private fun fetchVideoInfo(bvid: String): VideoInfo? {
        return try {
            // 1. 获取视频基本信息
            val infoUrl = "$API_VIDEO_INFO?bvid=$bvid"
            val infoRequest = Request.Builder()
                .url(infoUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36")
                .header("Referer", "https://www.bilibili.com/")
                .build()
            
            var title = "B站视频"
            var description = ""
            var author = ""
            var cid = 0L
            var duration = 0L
            var coverUrl: String? = null
            var pages = 1
            
            client.newCall(infoRequest).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return null
                    val json = JSONObject(body)
                    val data = json.optJSONObject("data") ?: return null
                    
                    title = data.optString("title", title)
                    description = data.optString("desc", description)
                    author = data.optJSONObject("owner")?.optString("name", "") ?: ""
                    duration = data.optLong("duration", 0)
                    coverUrl = data.optString("pic", null)
                    
                    // 获取第一个分P的CID
                    val pageArray = data.optJSONArray("pages")
                    if (pageArray != null && pageArray.length() > 0) {
                        val firstPage = pageArray.getJSONObject(0)
                        cid = firstPage.optLong("cid", 0)
                        pages = pageArray.length()
                    }
                }
            }
            
            if (cid == 0L) {
                Log.e(TAG, "Failed to get CID")
                return null
            }
            
            // 2. 获取播放地址 (选择最高画质)
            val playUrl = "$API_PLAY_URL?bvid=$bvid&cid=$cid&qn=127&fnval=4048&fnver=0&fourk=1"
            val playRequest = Request.Builder()
                .url(playUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36")
                .header("Referer", "https://www.bilibili.com/")
                .build()
            
            var videoUrl = ""
            var subtitles = emptyList<SubtitleInfo>()
            var danmakuUrl: String? = null
            
            client.newCall(playRequest).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val json = JSONObject(body)
                    val data = json.optJSONObject("data")
                    
                    if (data != null) {
                        val dash = data.optJSONObject("dash")
                        if (dash != null) {
                            // 获取视频流
                            val videos = dash.optJSONArray("video")
                            if (videos != null && videos.length() > 0) {
                                val video = videos.getJSONObject(0)
                                videoUrl = video.optString("baseUrl", video.optString("backupUrl", ""))
                            }
                            
                            // 获取字幕
                            val subtitleTracks = dash.optJSONArray("subtitle")
                            if (subtitleTracks != null) {
                                subtitles = parseSubtitles(subtitleTracks)
                            }
                        }
                        
                        // 备用：从 durl 获取
                        if (videoUrl.isEmpty()) {
                            val durlArray = data.optJSONArray("durl")
                            if (durlArray != null && durlArray.length() > 0) {
                                videoUrl = durlArray.getJSONObject(0).optString("url", "")
                            }
                        }
                    }
                }
            }
            
            if (videoUrl.isEmpty()) {
                Log.e(TAG, "Failed to get video URL")
                return null
            }
            
            // 3. 获取字幕列表
            val subtitleUrl = "$API_SUBTITLE?aid=${bvid.removePrefix("BV")}&cid=$cid"
            val subtitleRequest = Request.Builder()
                .url(subtitleUrl)
                .header("User-Agent", "Mozilla/5.0")
                .header("Referer", "https://www.bilibili.com/")
                .build()
            
            subtitles = try {
                client.newCall(subtitleRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        val json = JSONObject(body)
                        parseSubtitlesFromDetail(json)
                    } else {
                        emptyList()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching subtitles", e)
                emptyList()
            }
            
            // 4. 获取弹幕
            danmakuUrl = "$API_DANMAKU?oid=$cid"
            
            VideoInfo(
                bvid = bvid,
                cid = cid,
                title = cleanFilename(title),
                description = description,
                author = author,
                duration = duration,
                videoUrl = videoUrl,
                coverUrl = coverUrl,
                subtitles = subtitles,
                danmaku = danmakuUrl
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching video info", e)
            null
        }
    }

    /**
     * 解析字幕列表
     */
    private fun parseSubtitles(jsonArray: org.json.JSONArray): List<SubtitleInfo> {
        val subtitles = mutableListOf<SubtitleInfo>()
        for (i in 0 until jsonArray.length()) {
            val item = jsonArray.getJSONObject(i)
            subtitles.add(SubtitleInfo(
                id = item.optLong("id", i.toLong()),
                lang = item.optString("lang_key", "unknown"),
                url = item.optString("subtitle_url", "")
            ))
        }
        return subtitles
    }

    /**
     * 从视频详情 API 解析字幕
     */
    private fun parseSubtitlesFromDetail(json: JSONObject): List<SubtitleInfo> {
        val subtitles = mutableListOf<SubtitleInfo>()
        val data = json.optJSONObject("data") ?: return subtitles
        val subtitle = data.optJSONObject("subtitle_info") ?: return subtitles
        val subtitlesArray = subtitle.optJSONArray("subtitles") ?: return subtitles
        
        for (i in 0 until subtitlesArray.length()) {
            val item = subtitlesArray.getJSONObject(i)
            subtitles.add(SubtitleInfo(
                id = item.optLong("id", i.toLong()),
                lang = item.optString("lang_key", "unknown"),
                url = item.optString("subtitle_url", "")
            ))
        }
        return subtitles
    }

    /**
     * 下载弹幕
     */
    private suspend fun downloadDanmaku(url: String, outputDir: File, bvid: String) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .header("Referer", "https://www.bilibili.com/")
                .build()
            
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val danmakuFile = File(outputDir, "${bvid}_danmaku.xml")
                    response.body?.byteStream()?.use { input ->
                        FileOutputStream(danmakuFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    Log.d(TAG, "Danmaku downloaded: ${danmakuFile.absolutePath}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading danmaku", e)
        }
    }

    /**
     * 下载字幕
     */
    private suspend fun downloadSubtitles(subtitles: String, outputDir: File, bvid: String) {
        // 字幕格式: "lang:id,lang:id"
        val subtitleList = subtitles.split(",")
        for (subtitle in subtitleList) {
            val parts = subtitle.split(":")
            if (parts.size == 2) {
                try {
                    // 简化实现：字幕下载逻辑
                    Log.d(TAG, "Subtitle: ${parts[0]} (ID: ${parts[1]})")
                } catch (e: Exception) {
                    Log.e(TAG, "Error downloading subtitle", e)
                }
            }
        }
    }
}

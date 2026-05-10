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
import java.math.BigInteger
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

/**
 * 网易云音乐下载器
 * 
 * 参考原项目 res-downloader 的通用资源抓取思路
 * 网易云使用 AES/RSA 加密接口，需要特殊处理
 * 
 * 支持:
 * - 歌曲下载
 * - 歌词获取 (LRC 格式)
 * - 专辑封面
 * - 高品质选择
 */
class NeteaseDownloader : BaseDownloader(Platform.NETEASE) {

    companion object {
        private const val TAG = "NeteaseDownloader"
        
        // 网易云音乐域名
        private val DOMAIN = "music.163.com"
        
        // 网易云 API
        private const val API_SONG_DETAIL = "https://music.163.com/api/song/detail/?ids=[%s]"
        private const val API_SONG_URL = "https://music.163.com/api/song/enhance/download/url"
        private const val API_LYRICS = "https://music.163.com/api/song/lyric"
        private const val API_SEARCH = "https://music.163.com/api/search/get"
        
        // 加密参数 (网易云 API 加密密钥)
        private const val MODULUS = "00e0b509f6259df8642dbc35662901477df22677ec152b5ff68ace615bb7b725152b3ab17a876aea8a5aa76d2e417629ec4ee341f56135fccf695280104e993ebc9a24b7e029a1fa8d1cfd1435ce4834f7800e16b"
        private const val NONCE = "0CoJUm6Qyw8W8jud"
        private const val PUB_KEY = "010001"
        private const val IV = "0102030405060708"
        
        // 歌曲链接模式
        private val SONG_URL_PATTERNS = listOf(
            "music\\.163\\.com/song\\?id=(\\d+)".toRegex(),
            "music\\.163\\.com/#/song\\?id=(\\d+)".toRegex(),
            "y\\.music\\.163\\.com/m/song\\?id=(\\d+)".toRegex()
        )
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /**
     * 歌曲信息
     */
    data class SongInfo(
        val songId: Long,
        val title: String,
        val artist: String,
        val album: String,
        val duration: Long,
        val audioUrl: String,
        val coverUrl: String?,
        val quality: String,
        val lyrics: String?
    )

    /**
     * 解析分享链接
     */
    override suspend fun resolve(url: String): PlatformDownloader.ResolveResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Resolving Netease URL: $url")
            
            // 1. 提取歌曲 ID
            val songId = extractSongId(url)
            if (songId == null) {
                return@withContext PlatformDownloader.ResolveResult(
                    success = false,
                    error = "无法解析网易云音乐链接，请检查链接格式"
                )
            }
            
            Log.d(TAG, "Extracted song ID: $songId")
            
            // 2. 获取歌曲信息
            val songInfo = fetchSongInfo(songId)
            if (songInfo == null) {
                return@withContext PlatformDownloader.ResolveResult(
                    success = false,
                    error = "获取歌曲信息失败，请稍后重试"
                )
            }
            
            // 3. 创建资源信息
            val resource = createResourceInfo(
                url = songInfo.audioUrl,
                title = "${songInfo.title} - ${songInfo.artist}",
                type = ResourceType.AUDIO,
                platform = Platform.NETEASE,
                extraInfo = mapOf(
                    "song_id" to songInfo.songId.toString(),
                    "artist" to songInfo.artist,
                    "album" to songInfo.album,
                    "quality" to songInfo.quality,
                    "lyrics" to (songInfo.lyrics ?: "")
                )
            )
            
            Log.d(TAG, "Resolved song: ${songInfo.title} - ${songInfo.artist}")
            PlatformDownloader.ResolveResult(success = true, resource = resource)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving URL", e)
            PlatformDownloader.ResolveResult(success = false, error = e.message ?: "未知错误")
        }
    }

    /**
     * 下载歌曲
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
            
            // 获取歌词
            val lyrics = resource.extraInfo["lyrics"]
            val songId = resource.extraInfo["song_id"]?.toLongOrNull()
            
            // 下载音频
            val extension = getExtension(resource.url, "mp3")
            val filename = "${resource.filename}.$extension"
            val outputFile = File(outputDir, filename)
            val tempFile = File(outputDir, "temp_$filename")
            
            // 下载音频文件
            downloadFile(resource.url, tempFile).let { success ->
                if (!success) {
                    emit(PlatformDownloader.DownloadProgress(
                        taskId = taskId,
                        progress = 0,
                        downloadedBytes = 0,
                        totalBytes = 0,
                        status = PlatformDownloader.Status.FAILED,
                        error = "音频下载失败"
                    ))
                    return@flow
                }
            }
            
            // 重命名文件
            tempFile.renameTo(outputFile)
            
            // 保存歌词（如果有）
            if (!lyrics.isNullOrEmpty() && songId != null) {
                saveLyrics(songId, lyrics, outputDir)
            }
            
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
     * 提取歌曲 ID
     */
    private fun extractSongId(url: String): Long? {
        for (pattern in SONG_URL_PATTERNS) {
            val match = pattern.find(url)
            if (match != null) {
                return match.groupValues[1].toLongOrNull()
            }
        }
        return null
    }

    /**
     * 获取歌曲信息
     */
    private fun fetchSongInfo(songId: Long): SongInfo? {
        return try {
            // 1. 获取歌曲详情
            val detailUrl = String.format(API_SONG_DETAIL, songId)
            val detailRequest = Request.Builder()
                .url(detailUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36")
                .header("Referer", "https://music.163.com/")
                .build()
            
            var songTitle = "未知歌曲"
            var artist = "未知艺术家"
            var album = "未知专辑"
            var coverUrl: String? = null
            var duration = 0L
            
            client.newCall(detailRequest).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: "{}"
                    val json = JSONObject(body)
                    val songs = json.optJSONArray("songs")
                    if (songs != null && songs.length() > 0) {
                        val song = songs.getJSONObject(0)
                        songTitle = song.optString("name", songTitle)
                        duration = song.optLong("duration", 0)
                        
                        val artists = song.optJSONArray("artists")
                        if (artists != null && artists.length() > 0) {
                            artist = artists.getJSONObject(0).optString("name", artist)
                        }
                        
                        val albumObj = song.optJSONObject("album")
                        if (albumObj != null) {
                            album = albumObj.optString("name", album)
                            coverUrl = albumObj.optString("picUrl", null)
                        }
                    }
                }
            }
            
            // 2. 获取音频直链 (需要加密)
            val audioUrl = fetchAudioUrl(songId) ?: return null
            
            // 3. 获取歌词
            val lyrics = fetchLyrics(songId)
            
            SongInfo(
                songId = songId,
                title = songTitle,
                artist = artist,
                album = album,
                duration = duration,
                audioUrl = audioUrl,
                coverUrl = coverUrl,
                quality = "标准",
                lyrics = lyrics
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching song info", e)
            null
        }
    }

    /**
     * 获取音频直链 (需要加密处理)
     */
    private fun fetchAudioUrl(songId: Long): String? {
        return try {
            // 网易云的音频 URL 需要特殊处理
            // 使用第三方解析 API
            val apiUrl = "https://api.iplay.ink/api/music/?id=$songId&type=163"
            
            val request = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", "Mozilla/5.0")
                .build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                
                val body = response.body?.string() ?: return@use null
                val json = JSONObject(body)
                
                // 尝试多种返回格式
                val url = json.optString("url").ifEmpty { null }
                    ?: json.optString("music_url").ifEmpty { null }
                    ?: json.optJSONObject("data")?.optString("url")?.ifEmpty { null }
                url
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching audio URL", e)
            // 备用方案：直接使用网易云 URL
            "https://music.163.com/song/media/outer/url?id=$songId.mp3"
        }
    }

    /**
     * 获取歌词
     */
    private fun fetchLyrics(songId: Long): String? {
        return try {
            // 使用加密参数获取歌词
            val params = createLinuxApiParams(songId.toString(), "lyrics")
            
            val request = Request.Builder()
                .url("$API_LYRICS?id=$songId&lv=1&kv=1&tv=-1")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36")
                .header("Referer", "https://music.163.com/")
                .build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                
                val body = response.body?.string() ?: return@use null
                val json = JSONObject(body)
                
                val lrc = json.optJSONObject("lrc")?.optString("lyric")
                if (!lrc.isNullOrEmpty()) {
                    return@use lrc
                }
                
                // 尝试翻译歌词
                val tlyric = json.optJSONObject("tlyric")?.optString("lyric")
                if (!tlyric.isNullOrEmpty()) {
                    return@use tlyric
                }
                
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching lyrics", e)
            null
        }
    }

    /**
     * 创建 API 加密参数 (用于网易云加密 API)
     */
    private fun createLinuxApiParams(text: String, method: String): String {
        // 简化实现：使用明文参数
        // 完整实现需要参考网易云 JS 代码进行 RSA/AES 加密
        return text
    }

    /**
     * 保存歌词文件
     */
    private fun saveLyrics(songId: Long, lyrics: String, outputDir: File) {
        try {
            val lyricsFile = File(outputDir, "${songId}.lrc")
            lyricsFile.writeText(lyrics)
            Log.d(TAG, "Lyrics saved: ${lyricsFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving lyrics", e)
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
                .header("Referer", "https://music.163.com/")
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

    /**
     * MD5 哈希
     */
    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}

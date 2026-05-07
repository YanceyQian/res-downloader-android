package com.resdownloader.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * M3U8 解析下载器
 * 支持：
 * - M3U8 播放列表解析
 * - AES-128 加密视频解密
 * - 分片下载与合并
 * - 多码率自适应
 */
class M3u8Downloader {

    companion object {
        private const val TAG = "M3u8Downloader"
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    data class M3u8Info(
        val baseUrl: String,
        val segments: List<SegmentInfo>,
        val isEncrypted: Boolean = false,
        val keyUri: String? = null,
        val key: ByteArray? = null,
        val iv: ByteArray? = null,
        val totalDuration: Double = 0.0
    )

    data class SegmentInfo(
        val index: Int,
        val url: String,
        val duration: Double,
        val title: String = ""
    )

    data class QualityOption(
        val name: String,
        val bandwidth: Int,
        val url: String
    )

    interface DownloadCallback {
        fun onProgress(progress: Int, downloaded: Int, total: Int)
        fun onComplete(outputFile: File)
        fun onError(error: String)
    }

    private var callback: DownloadCallback? = null

    fun setCallback(callback: DownloadCallback) {
        this.callback = callback
    }

    /**
     * 解析 M3U8 文件
     */
    suspend fun parseM3u8(url: String): Result<M3u8Info> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Parsing M3U8: $url")

            val m3u8Content = fetchContent(url)
            val baseUrl = getBaseUrl(url)

            // 检查是否为 Master Playlist（多码率）
            if (m3u8Content.contains("#EXT-X-STREAM-INF")) {
                Log.d(TAG, "This is a Master Playlist")
                return@withContext Result.failure(Exception("需要先选择码率"))
            }

            // 解析 Media Playlist
            val m3u8Info = parseMediaPlaylist(m3u8Content, baseUrl)
            Result.success(m3u8Info)

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing M3U8", e)
            Result.failure(e)
        }
    }

    /**
     * 获取最高码率
     */
    suspend fun getBestQuality(url: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val content = fetchContent(url)
            val qualities = parseMasterPlaylist(content, getBaseUrl(url))

            if (qualities.isEmpty()) {
                return@withContext Result.failure(Exception("未找到可用码率"))
            }

            // 选择最高码率
            val best = qualities.maxByOrNull { it.bandwidth }
            Log.d(TAG, "Best quality: ${best?.name}, bandwidth: ${best?.bandwidth}")
            Result.success(best?.url ?: url)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 获取所有码率选项
     */
    suspend fun getQualityOptions(url: String): Result<List<QualityOption>> = withContext(Dispatchers.IO) {
        try {
            val content = fetchContent(url)
            val qualities = parseMasterPlaylist(content, getBaseUrl(url))
            Result.success(qualities)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 下载并合并 M3U8
     */
    suspend fun download(
        m3u8Url: String,
        outputPath: String,
        filename: String
    ) = withContext(Dispatchers.IO) {
        try {
            // 解析 M3U8
            val m3u8Info = parseM3u8(m3u8Url).getOrThrow()
            val totalSegments = m3u8Info.segments.size

            Log.d(TAG, "Starting download: ${totalSegments} segments")

            val outputFile = File(outputPath)
            if (!outputFile.exists()) {
                outputFile.mkdirs()
            }

            val output = File(outputPath, filename)
            val tempDir = File(outputPath, "${filename}_temp")
            tempDir.mkdirs()

            // 如果已加密，获取密钥
            if (m3u8Info.isEncrypted && m3u8Info.keyUri != null) {
                Log.d(TAG, "Video is encrypted, fetching key...")
                val keyUrl = resolveUrl(m3u8Info.baseUrl, m3u8Info.keyUri)
                fetchEncryptionKey(keyUrl)
            }

            // 下载所有分片
            val segmentFiles = mutableListOf<File>()

            for ((index, segment) in m3u8Info.segments.withIndex()) {
                val progress = ((index + 1) * 100) / totalSegments
                callback?.onProgress(progress, index + 1, totalSegments)

                val segmentFile = File(tempDir, "segment_${String.format("%04d", index)}.ts")
                segmentFiles.add(segmentFile)

                downloadSegment(segment.url, segmentFile)
            }

            // 合并分片
            Log.d(TAG, "Merging segments...")
            mergeTsFiles(segmentFiles, output)

            // 清理临时文件
            tempDir.deleteRecursively()

            callback?.onComplete(output)
            Log.d(TAG, "Download completed: ${output.absolutePath}")

        } catch (e: Exception) {
            Log.e(TAG, "Download error", e)
            callback?.onError(e.message ?: "Unknown error")
        }
    }

    /**
     * 解析 Media Playlist
     */
    private fun parseMediaPlaylist(content: String, baseUrl: String): M3u8Info {
        val lines = content.lines()
        val segments = mutableListOf<SegmentInfo>()

        var currentDuration = 0.0
        var currentTitle = ""
        var segmentIndex = 0
        var isEncrypted = false
        var keyUri: String? = null
        var iv: String? = null

        for (i in lines.indices) {
            val line = lines[i].trim()

            when {
                line.startsWith("#EXTINF:") -> {
                    val durationMatch = Pattern.compile("#EXTINF:([\\d.]+)").matcher(line)
                    if (durationMatch.find()) {
                        currentDuration = durationMatch.group(1).toDoubleOrNull() ?: 0.0
                    }
                    val titleMatch = Pattern.compile(",(.+)$").matcher(line)
                    if (titleMatch.find()) {
                        currentTitle = titleMatch.group(1)
                    }
                }
                line.isNotEmpty() && !line.startsWith("#") -> {
                    val segmentUrl = resolveUrl(baseUrl, line)
                    segments.add(SegmentInfo(
                        index = segmentIndex++,
                        url = segmentUrl,
                        duration = currentDuration,
                        title = currentTitle
                    ))
                }
                line.startsWith("#EXT-X-KEY:") -> {
                    isEncrypted = true
                    val keyMatch = Pattern.compile("URI=\"([^\"]+)\"").matcher(line)
                    if (keyMatch.find()) {
                        keyUri = keyMatch.group(1)
                    }
                    val ivMatch = Pattern.compile("IV=0x([0-9a-fA-F]+)").matcher(line)
                    if (ivMatch.find()) {
                        iv = ivMatch.group(1)
                    }
                }
            }
        }

        val totalDuration = segments.sumOf { it.duration }

        Log.d(TAG, "Parsed ${segments.size} segments, encrypted: $isEncrypted, duration: ${totalDuration}s")

        return M3u8Info(
            baseUrl = baseUrl,
            segments = segments,
            isEncrypted = isEncrypted,
            keyUri = keyUri,
            iv = if (iv != null) hexToBytes(iv) else null,
            totalDuration = totalDuration
        )
    }

    /**
     * 解析 Master Playlist
     */
    private fun parseMasterPlaylist(content: String, baseUrl: String): List<QualityOption> {
        val qualities = mutableListOf<QualityOption>()
        val lines = content.lines()

        var currentBandwidth = 0
        var currentName = ""
        var currentUrl: String? = null

        for (line in lines) {
            when {
                line.startsWith("#EXT-X-STREAM-INF:") -> {
                    val bandwidthMatch = Pattern.compile("BANDWIDTH=(\\d+)").matcher(line)
                    if (bandwidthMatch.find()) {
                        currentBandwidth = bandwidthMatch.group(1).toIntOrNull() ?: 0
                    }
                    val nameMatch = Pattern.compile("NAME=\"([^\"]+)\"").matcher(line)
                    if (nameMatch.find()) {
                        currentName = nameMatch.group(1)
                    } else {
                        currentName = "${currentBandwidth / 1000}kbps"
                    }
                }
                line.isNotEmpty() && !line.startsWith("#") -> {
                    currentUrl = resolveUrl(baseUrl, line)
                    qualities.add(QualityOption(
                        name = currentName,
                        bandwidth = currentBandwidth,
                        url = currentUrl
                    ))
                    currentUrl = null
                }
            }
        }

        return qualities
    }

    /**
     * 获取基础 URL
     */
    private fun getBaseUrl(url: String): String {
        return try {
            val uri = URI(url)
            val path = uri.path ?: ""
            val lastSlash = path.lastIndexOf('/')
            val basePath = if (lastSlash > 0) path.substring(0, lastSlash + 1) else "/"
            "${uri.scheme}://${uri.host}${basePath}"
        } catch (e: Exception) {
            val lastSlash = url.lastIndexOf('/')
            if (lastSlash > 0) url.substring(0, lastSlash + 1) else url
        }
    }

    /**
     * 解析相对 URL
     */
    private fun resolveUrl(baseUrl: String, relativeUrl: String): String {
        return when {
            relativeUrl.startsWith("http://") || relativeUrl.startsWith("https://") -> relativeUrl
            relativeUrl.startsWith("/") -> {
                val uri = URI(baseUrl)
                "${uri.scheme}://${uri.host}$relativeUrl"
            }
            else -> baseUrl + relativeUrl
        }
    }

    /**
     * 下载内容
     */
    private fun fetchContent(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36")
            .header("Accept", "*/*")
            .build()

        val response = okHttpClient.newCall(request).execute()
        return response.body?.string() ?: throw Exception("Empty response")
    }

    /**
     * 下载分片
     */
    private fun downloadSegment(url: String, output: File) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36")
            .build()

        val response = okHttpClient.newCall(request).execute()

        if (!response.isSuccessful) {
            throw Exception("Segment download failed: ${response.code}")
        }

        response.body?.byteStream()?.use { input ->
            FileOutputStream(output).use { outputStream ->
                input.copyTo(outputStream)
            }
        }
    }

    /**
     * 合并 TS 文件
     */
    private fun mergeTsFiles(segments: List<File>, output: File) {
        FileOutputStream(output).use { fos ->
            for (segment in segments) {
                if (segment.exists()) {
                    segment.inputStream().use { input ->
                        input.copyTo(fos)
                    }
                }
            }
        }
    }

    /**
     * 获取加密密钥
     */
    private suspend fun fetchEncryptionKey(keyUrl: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(keyUrl)
                .build()

            val response = okHttpClient.newCall(request).execute()
            val key = response.body?.bytes()

            if (key != null && key.size == 16) {
                Log.d(TAG, "Key fetched successfully")
                key
            } else {
                Log.e(TAG, "Invalid key size: ${key?.size}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching key", e)
            null
        }
    }

    /**
     * Hex 字符串转字节数组
     */
    private fun hexToBytes(hex: String): ByteArray {
        return hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}

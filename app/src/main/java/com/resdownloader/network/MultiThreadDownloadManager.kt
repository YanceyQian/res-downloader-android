package com.resdownloader.network

import android.content.Context
import android.util.Log
import com.resdownloader.data.preferences.PreferencesManager
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.RandomAccessFile
import java.net.InetSocketAddress
import java.net.Proxy
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * 多线程断点续传下载管理器
 * 支持：
 * - 多线程并行下载
 * - 断点续传
 * - 分片下载
 * - 进度回调
 * - 上游代理支持
 * - 自定义 Headers 支持
 * - 文件名处理
 */
class MultiThreadDownloadManager(
    private val context: Context,
    private val preferencesManager: PreferencesManager
) {
    companion object {
        private const val TAG = "MultiThreadDownload"
        const val DEFAULT_THREAD_COUNT = 3
        const val MIN_PART_SIZE = 1024 * 1024L // 最小分片 1MB
        const val BUFFER_SIZE = 8192
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * 创建 OkHttpClient 实例，支持动态代理配置
     */
    private fun createOkHttpClient(useProxy: Boolean): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)

        // 如果启用下载代理，配置上游代理
        if (useProxy) {
            val upstreamProxy = preferencesManager.getUpstreamProxySync()
            if (upstreamProxy.isNotEmpty()) {
                try {
                    val proxyUrl = parseProxyUrl(upstreamProxy)
                    if (proxyUrl != null) {
                        val proxy = Proxy(proxyUrl.first, InetSocketAddress(proxyUrl.second.first, proxyUrl.second.second))
                        builder.proxy(proxy)
                        Log.d(TAG, "Using upstream proxy: $upstreamProxy")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse proxy URL: $upstreamProxy", e)
                }
            }
        }

        return builder.build()
    }

    /**
     * 解析代理 URL 格式: http://host:port 或 socks5://host:port
     */
    private fun parseProxyUrl(proxyUrl: String): Pair<Proxy.Type, Pair<String, Int>>? {
        return try {
            val url = if (proxyUrl.startsWith("http://") || proxyUrl.startsWith("https://")) {
                proxyUrl
            } else {
                "http://$proxyUrl"
            }
            
            val javaNetUrl = java.net.URL(url)
            val host = javaNetUrl.host
            val port = javaNetUrl.port
            
            val proxyType = if (url.startsWith("socks5://") || url.startsWith("socks4://")) {
                Proxy.Type.SOCKS
            } else {
                Proxy.Type.HTTP
            }
            
            Pair(proxyType, Pair(host, if (port > 0) port else 8080))
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing proxy URL: $proxyUrl", e)
            null
        }
    }

    // 活跃任务
    private val activeTasks = ConcurrentHashMap<String, DownloadTask>()
    private val activeJobs = ConcurrentHashMap<String, Job>()

    // 下载任务信息
    data class DownloadTask(
        val taskId: String,
        val url: String,
        val filename: String,
        var totalSize: Long = 0L,
        var downloadedSize: AtomicLong = AtomicLong(0),
        var status: DownloadStatus = DownloadStatus.PENDING,
        val parts: MutableList<DownloadPart> = mutableListOf(),
        var error: String? = null
    )

    data class DownloadPart(
        val index: Int,
        val start: Long,
        val end: Long,
        var downloaded: Long = 0,
        var status: DownloadStatus = DownloadStatus.PENDING
    )

    enum class DownloadStatus {
        PENDING,
        DOWNLOADING,
        PAUSED,
        COMPLETED,
        FAILED
    }

    // 进度回调接口
    interface DownloadCallback {
        fun onProgress(taskId: String, progress: Int, downloaded: Long, total: Long)
        fun onComplete(taskId: String, file: File)
        fun onError(taskId: String, error: String)
        fun onPaused(taskId: String)
    }

    private var callback: DownloadCallback? = null

    fun setCallback(callback: DownloadCallback) {
        this.callback = callback
    }

    /**
     * 开始下载任务
     */
    fun startDownload(
        taskId: String,
        url: String,
        filename: String,
        decodeKey: String? = null,
        threadCount: Int = preferencesManager.getDownNumberSync()
    ) {
        Log.d(TAG, "Starting multi-thread download: $filename, threads: $threadCount")

        // 取消已存在的任务
        activeJobs[taskId]?.cancel()
        activeTasks.remove(taskId)

        // 检查是否启用下载代理
        val useDownloadProxy = preferencesManager.getDownloadProxySync()
        val okHttpClient = createOkHttpClient(useDownloadProxy)

        val job = scope.launch {
            try {
                val task = DownloadTask(taskId, url, filename)
                activeTasks[taskId] = task

                // 获取下载目录
                val downloadDir = getDownloadDirectory()
                
                // 应用文件名处理配置
                val processedFilename = processFilename(filename)
                val safeFilename = sanitizeFilename(processedFilename)
                val tempFile = File(downloadDir, "$safeFilename.tmp")
                val finalFile = File(downloadDir, safeFilename)

                // 获取文件信息
                val (totalSize, supportsRange) = fetchFileInfo(url, okHttpClient)

                if (totalSize <= 0) {
                    callback?.onError(taskId, "无法获取文件大小")
                    return@launch
                }

                task.totalSize = totalSize
                task.status = DownloadStatus.DOWNLOADING

                Log.d(TAG, "File size: $totalSize, Range supported: $supportsRange")

                // 检查断点续传
                val existingSize = if (tempFile.exists()) tempFile.length() else 0L
                Log.d(TAG, "Existing temp file size: $existingSize")

                if (supportsRange && existingSize < totalSize) {
                    // 支持断点续传，使用多线程
                    if (existingSize > 0) {
                        Log.d(TAG, "Resuming download from $existingSize bytes")
                        task.downloadedSize.set(existingSize)
                    }
                    downloadWithMultiThread(task, tempFile, threadCount, okHttpClient)
                } else {
                    // 不支持断点续传或需要重新下载
                    if (tempFile.exists()) {
                        tempFile.delete()
                    }
                    downloadWithMultiThread(task, tempFile, threadCount, okHttpClient)
                }

                // 检查是否所有分片完成
                if (task.status == DownloadStatus.DOWNLOADING) {
                    // 解密处理（如果有）
                    if (!decodeKey.isNullOrEmpty()) {
                        Log.d(TAG, "Decrypting file...")
                        decryptFile(tempFile, finalFile, decodeKey)
                    } else {
                        tempFile.renameTo(finalFile)
                    }

                    task.status = DownloadStatus.COMPLETED
                    callback?.onComplete(taskId, finalFile)
                    Log.d(TAG, "Download completed: ${finalFile.absolutePath}")
                }

            } catch (e: CancellationException) {
                Log.d(TAG, "Download cancelled: $taskId")
                activeTasks[taskId]?.status = DownloadStatus.PAUSED
                callback?.onPaused(taskId)
            } catch (e: Exception) {
                Log.e(TAG, "Download error", e)
                activeTasks[taskId]?.status = DownloadStatus.FAILED
                activeTasks[taskId]?.error = e.message
                callback?.onError(taskId, e.message ?: "Unknown error")
            } finally {
                activeJobs.remove(taskId)
            }
        }

        activeJobs[taskId] = job
    }

    /**
     * 多线程下载
     */
    private suspend fun downloadWithMultiThread(
        task: DownloadTask,
        tempFile: File,
        threadCount: Int,
        okHttpClient: OkHttpClient
    ) = withContext(Dispatchers.IO) {
        val totalSize = task.totalSize
        val partSize = maxOf(totalSize / threadCount, MIN_PART_SIZE)

        // 创建分片
        task.parts.clear()
        var start = 0L
        for (i in 0 until threadCount) {
            val end = if (i == threadCount - 1) {
                totalSize - 1
            } else {
                minOf(start + partSize - 1, totalSize - 1)
            }
            if (start <= end) {
                task.parts.add(DownloadPart(i, start, end))
            }
            start += partSize
        }

        // 创建/打开临时文件
        val raf = RandomAccessFile(tempFile, "rw")
        raf.setLength(totalSize)

        // 并行下载所有分片
        val partJobs = task.parts.map { part ->
            async {
                downloadPart(task, part, raf, okHttpClient)
            }
        }

        // 等待所有分片完成
        partJobs.awaitAll()

        raf.close()

        // 检查是否有失败的分区
        val failedParts = task.parts.filter { it.status == DownloadStatus.FAILED }
        if (failedParts.isNotEmpty()) {
            task.status = DownloadStatus.FAILED
            task.error = "Failed parts: ${failedParts.size}"
            throw Exception("Some parts failed to download")
        }

        task.status = DownloadStatus.COMPLETED
    }

    /**
     * 下载单个分片
     */
    private suspend fun downloadPart(
        task: DownloadTask,
        part: DownloadPart,
        raf: RandomAccessFile,
        okHttpClient: OkHttpClient
    ) = withContext(Dispatchers.IO) {
        try {
            part.status = DownloadStatus.DOWNLOADING
            Log.d(TAG, "Starting part ${part.index}: ${part.start}-${part.end}")

            val requestBuilder = Request.Builder()
                .url(task.url)
                .header("Range", "bytes=${part.start}-${part.end}")
                .header("Accept", "*/*")

            // 应用自定义 Headers 配置
            addCustomHeaders(requestBuilder)

            val request = requestBuilder.build()
            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful && response.code != 206) {
                Log.e(TAG, "Part ${part.index} failed: HTTP ${response.code}")
                part.status = DownloadStatus.FAILED
                return@withContext
            }

            response.body?.byteStream()?.use { inputStream ->
                raf.seek(part.start)
                val buffer = ByteArray(BUFFER_SIZE)
                var bytesRead: Int
                var totalRead = 0L

                while (isActive) {
                    bytesRead = inputStream.read(buffer)
                    if (bytesRead == -1) break

                    raf.write(buffer, 0, bytesRead)
                    totalRead += bytesRead
                    part.downloaded = totalRead

                    // 更新总进度
                    val downloaded = task.downloadedSize.addAndGet(bytesRead.toLong())
                    val progress = ((downloaded * 100) / task.totalSize).toInt()
                    callback?.onProgress(task.taskId, progress, downloaded, task.totalSize)
                }
            }

            part.status = DownloadStatus.COMPLETED
            Log.d(TAG, "Part ${part.index} completed")

        } catch (e: Exception) {
            Log.e(TAG, "Part ${part.index} error", e)
            part.status = DownloadStatus.FAILED
        }
    }

    /**
     * 根据 useHeaders 配置添加自定义 Headers
     */
    private fun addCustomHeaders(requestBuilder: Request.Builder) {
        val useHeaders = preferencesManager.getUseHeadersSync()
        
        // 始终添加 User-Agent
        val userAgent = preferencesManager.getUserAgentSync()
        if (userAgent.isNotEmpty()) {
            requestBuilder.header("User-Agent", userAgent)
        }

        // 根据配置添加其他 Headers（预留扩展）
        when (useHeaders) {
            "User-Agent,Referer" -> {
                // 预留 Referer 添加逻辑
                Log.d(TAG, "Using headers: User-Agent + Referer")
            }
            "User-Agent,Referer,Cookie" -> {
                // 预留完整 Headers 添加逻辑
                Log.d(TAG, "Using headers: User-Agent + Referer + Cookie")
            }
            else -> {
                // default: 只使用 User-Agent
                Log.d(TAG, "Using default headers: User-Agent only")
            }
        }
    }

    /**
     * 获取文件信息（大小和是否支持断点）
     */
    private fun fetchFileInfo(url: String, okHttpClient: OkHttpClient): Pair<Long, Boolean> {
        return try {
            val requestBuilder = Request.Builder()
                .url(url)
                .header("Range", "bytes=0-0") // 只请求第一个字节来检测 Range 支持
            
            // 添加自定义 Headers
            addCustomHeaders(requestBuilder)
            
            val request = requestBuilder.build()
            val response = okHttpClient.newCall(request).execute()
            val contentLength = response.header("Content-Length")?.toLongOrNull() ?: 0L
            val acceptRanges = response.header("Accept-Ranges") == "bytes"

            // 如果 Range 不被支持，需要重新获取完整大小
            val totalSize = if (response.code == 206) {
                val contentRange = response.header("Content-Range") ?: ""
                parseContentRange(contentRange, contentLength)
            } else {
                // 获取完整文件大小
                getFullFileSize(url, okHttpClient)
            }

            response.close()
            Pair(totalSize, acceptRanges)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching file info", e)
            Pair(0L, false)
        }
    }

    private fun parseContentRange(contentRange: String, partialSize: Long): Long {
        // 格式: "bytes 0-100/1000"
        val match = Regex("bytes \\d+-\\d+/(\\d+)").find(contentRange)
        return match?.groupValues?.get(1)?.toLongOrNull() ?: partialSize
    }

    private fun getFullFileSize(url: String, okHttpClient: OkHttpClient): Long {
        return try {
            val requestBuilder = Request.Builder()
                .url(url)
                .method("HEAD", null)
            
            // 添加自定义 Headers
            addCustomHeaders(requestBuilder)
            
            val request = requestBuilder.build()
            val response = okHttpClient.newCall(request).execute()
            val size = response.header("Content-Length")?.toLongOrNull() ?: 0L
            response.close()
            size
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * 暂停下载
     */
    fun pauseDownload(taskId: String) {
        activeJobs[taskId]?.cancel()
        activeTasks[taskId]?.status = DownloadStatus.PAUSED
        Log.d(TAG, "Download paused: $taskId")
    }

    /**
     * 取消下载
     */
    fun cancelDownload(taskId: String) {
        activeJobs[taskId]?.cancel()
        activeJobs.remove(taskId)
        activeTasks.remove(taskId)
        Log.d(TAG, "Download cancelled: $taskId")
    }

    /**
     * 获取下载状态
     */
    fun getDownloadStatus(taskId: String): DownloadTask? {
        return activeTasks[taskId]
    }

    /**
     * 清理资源
     */
    fun release() {
        scope.cancel()
        activeJobs.clear()
        activeTasks.clear()
    }

    private fun getDownloadDirectory(): File {
        val path = preferencesManager.getDownloadPathSync()
        val dir = File(path)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun sanitizeFilename(filename: String): String {
        var safeName = filename
        val invalidChars = listOf("<", ">", ":", "\"", "/", "\\", "|", "?", "*")
        invalidChars.forEach { char ->
            safeName = safeName.replace(char, "_")
        }
        if (!safeName.contains(".")) {
            safeName = "$safeName.mp4"
        }
        return safeName
    }

    /**
     * 处理文件名，应用用户配置
     * - filenameLen: 限制文件名长度
     * - filenameTime: 添加时间戳
     */
    private fun processFilename(filename: String): String {
        var result = filename
        
        // 应用文件名长度限制
        val maxLen = preferencesManager.getFilenameLenSync()
        if (maxLen > 0 && result.length > maxLen) {
            // 保留扩展名，截断主体部分
            val lastDot = result.lastIndexOf('.')
            if (lastDot > 0) {
                val ext = result.substring(lastDot)
                val nameWithoutExt = result.substring(0, lastDot)
                val availableLen = maxLen - ext.length
                if (availableLen > 0) {
                    result = nameWithoutExt.take(availableLen) + ext
                }
            } else {
                result = result.take(maxLen)
            }
            Log.d(TAG, "Filename truncated to $maxLen characters")
        }
        
        // 添加时间戳
        val addTime = preferencesManager.getInsertTailSync()
        if (addTime) {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
            val lastDot = result.lastIndexOf('.')
            if (lastDot > 0) {
                val ext = result.substring(lastDot)
                val nameWithoutExt = result.substring(0, lastDot)
                result = "${nameWithoutExt}_$timestamp$ext"
            } else {
                result = "${result}_$timestamp"
            }
        }
        
        return result
    }

    private suspend fun decryptFile(source: File, dest: File, key: String) {
        withContext(Dispatchers.IO) {
            try {
                val encryptedData = source.readBytes()
                val encryptedBase64 = android.util.Base64.encodeToString(encryptedData, android.util.Base64.NO_WRAP)

                val decrypted = com.resdownloader.util.AesUtils.decrypt(encryptedBase64, key)
                if (decrypted != null) {
                    val decryptedBytes = decrypted.toByteArray(charset("UTF-8"))
                    dest.writeBytes(decryptedBytes)
                    source.delete()
                    Log.d(TAG, "File decrypted successfully")
                } else {
                    source.renameTo(dest)
                    Log.w(TAG, "Decryption failed, saved original file")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Decryption error", e)
                source.renameTo(dest)
            }
        }
    }
}

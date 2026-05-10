package com.resdownloader.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.resdownloader.R
import com.resdownloader.MainActivity
import com.resdownloader.data.preferences.PreferencesManager
import com.resdownloader.network.MultiThreadDownloadManager
import com.resdownloader.network.M3u8Downloader
import com.resdownloader.network.ResourceFetcher
import com.resdownloader.util.AesUtils
import com.resdownloader.util.VideoDecryptor
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import android.net.Uri

class DownloadService : Service() {

    companion object {
        private const val TAG = "DownloadService"
        const val CHANNEL_ID = "download_channel"
        const val NOTIFICATION_ID_BASE = 1000

        const val ACTION_START = "com.resdownloader.START_DOWNLOAD"
        const val ACTION_PAUSE = "com.resdownloader.PAUSE_DOWNLOAD"
        const val ACTION_CANCEL = "com.resdownloader.CANCEL_DOWNLOAD"
        const val ACTION_START_M3U8 = "com.resdownloader.START_M3U8_DOWNLOAD"
        const val ACTION_FETCH_URL = "com.resdownloader.FETCH_URL"

        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_RESOURCE_URL = "resource_url"
        const val EXTRA_FILENAME = "filename"
        const val EXTRA_DECODE_KEY = "decode_key"
        const val EXTRA_M3U8_URL = "m3u8_url"
        const val EXTRA_M3U8_QUALITY = "m3u8_quality"

        private val activeJobs = ConcurrentHashMap<String, Job>()
        private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        private val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        // 多线程下载管理器
        private var multiThreadManager: MultiThreadDownloadManager? = null

        // M3U8 下载器
        private var m3u8Downloader: M3u8Downloader? = null

        // 资源抓取器
        private var resourceFetcher: ResourceFetcher? = null

        fun startDownload(context: Context, taskId: String, url: String, filename: String, decodeKey: String? = null) {
            try {
                Log.d(TAG, "Preparing to start download: $filename")
                val intent = Intent(context, DownloadService::class.java).apply {
                    action = ACTION_START
                    putExtra(EXTRA_TASK_ID, taskId)
                    putExtra(EXTRA_RESOURCE_URL, url)
                    putExtra(EXTRA_FILENAME, filename)
                    putExtra(EXTRA_DECODE_KEY, decodeKey)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                Log.d(TAG, "Download service started")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start download", e)
                Toast.makeText(context, "启动下载失败: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        fun startM3u8Download(context: Context, taskId: String, url: String, filename: String, quality: String? = null) {
            try {
                Log.d(TAG, "Preparing to start M3U8 download: $filename")
                val intent = Intent(context, DownloadService::class.java).apply {
                    action = ACTION_START_M3U8
                    putExtra(EXTRA_TASK_ID, taskId)
                    putExtra(EXTRA_M3U8_URL, url)
                    putExtra(EXTRA_FILENAME, filename)
                    putExtra(EXTRA_M3U8_QUALITY, quality)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                Log.d(TAG, "M3U8 download service started")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start M3U8 download", e)
                Toast.makeText(context, "启动M3U8下载失败: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        suspend fun fetchResourceUrl(context: Context, url: String): ResourceFetcher.FetchResult {
            val fetcher = ResourceFetcher()
            return fetcher.fetch(url)
        }

        fun pauseDownload(taskId: String) {
            activeJobs[taskId]?.cancel()
            activeJobs.remove(taskId)
            multiThreadManager?.pauseDownload(taskId)
        }

        fun cancelDownload(taskId: String) {
            activeJobs[taskId]?.cancel()
            activeJobs.remove(taskId)
            multiThreadManager?.cancelDownload(taskId)
        }
    }

    private lateinit var preferencesManager: PreferencesManager

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "DownloadService created")
        preferencesManager = PreferencesManager(this)
        createNotificationChannel()

        // 初始化多线程下载管理器
        multiThreadManager = MultiThreadDownloadManager(this, preferencesManager)
        multiThreadManager?.setCallback(object : MultiThreadDownloadManager.DownloadCallback {
            override fun onProgress(taskId: String, progress: Int, downloaded: Long, total: Long) {
                updateNotification(taskId, "$downloaded/$total", progress, false)
            }

            override fun onComplete(taskId: String, file: File) {
                updateNotification(taskId, file.name, 100, true)
                scope.launch(Dispatchers.Main) {
                    Toast.makeText(this@DownloadService, "下载完成: ${file.name}", Toast.LENGTH_LONG).show()
                }
            }

            override fun onError(taskId: String, error: String) {
                updateNotification(taskId, error, -1, true)
                scope.launch(Dispatchers.Main) {
                    Toast.makeText(this@DownloadService, "下载错误: $error", Toast.LENGTH_LONG).show()
                }
            }

            override fun onPaused(taskId: String) {
                Log.d(TAG, "Download paused: $taskId")
            }
        })

        // 初始化 M3U8 下载器
        m3u8Downloader = M3u8Downloader()

        // 初始化资源抓取器
        resourceFetcher = ResourceFetcher()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: ${intent?.action}")

        when (intent?.action) {
            ACTION_START -> {
                val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return START_NOT_STICKY
                val url = intent.getStringExtra(EXTRA_RESOURCE_URL) ?: return START_NOT_STICKY
                val filename = intent.getStringExtra(EXTRA_FILENAME) ?: "download.mp4"
                val decodeKey = intent.getStringExtra(EXTRA_DECODE_KEY)

                Log.d(TAG, "Starting download - URL: $url, File: $filename")

                try {
                    val safeFilename = sanitizeFilename(filename)
                    val notification = createNotification(taskId, safeFilename, 0)

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        startForeground(NOTIFICATION_ID_BASE + taskId.hashCode(), notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                    } else {
                        startForeground(NOTIFICATION_ID_BASE + taskId.hashCode(), notification)
                    }

                    Toast.makeText(this, "开始下载: $safeFilename", Toast.LENGTH_SHORT).show()

                    // 使用多线程下载管理器
                    multiThreadManager?.startDownload(taskId, url, safeFilename, decodeKey)
                        ?: startDownloadTask(taskId, url, safeFilename, decodeKey)

                } catch (e: Exception) {
                    Log.e(TAG, "Error in start download", e)
                    Toast.makeText(this, "下载启动失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }

            ACTION_START_M3U8 -> {
                val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return START_NOT_STICKY
                val m3u8Url = intent.getStringExtra(EXTRA_M3U8_URL) ?: return START_NOT_STICKY
                val filename = intent.getStringExtra(EXTRA_FILENAME) ?: "video.mp4"
                val quality = intent.getStringExtra(EXTRA_M3U8_QUALITY)

                Log.d(TAG, "Starting M3U8 download - URL: $m3u8Url, File: $filename")

                try {
                    val safeFilename = sanitizeFilename(filename)
                    val notification = createNotification(taskId, safeFilename, 0)

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        startForeground(NOTIFICATION_ID_BASE + taskId.hashCode(), notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                    } else {
                        startForeground(NOTIFICATION_ID_BASE + taskId.hashCode(), notification)
                    }

                    Toast.makeText(this, "开始M3U8下载: $safeFilename", Toast.LENGTH_SHORT).show()
                    startM3u8DownloadTask(taskId, m3u8Url, safeFilename, quality)

                } catch (e: Exception) {
                    Log.e(TAG, "Error in start M3U8 download", e)
                    Toast.makeText(this, "M3U8下载启动失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }

            ACTION_PAUSE -> {
                val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return START_NOT_STICKY
                pauseDownload(taskId)
            }
            ACTION_CANCEL -> {
                val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return START_NOT_STICKY
                cancelDownload(taskId)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "DownloadService destroyed")
        scope.cancel()
        super.onDestroy()
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
     * M3U8 下载任务
     */
    private fun startM3u8DownloadTask(taskId: String, m3u8Url: String, filename: String, quality: String? = null) {
        val job = scope.launch {
            try {
                val downloadDir = getDownloadDirectory()
                val safeFilename = filename.let {
                    if (!it.endsWith(".mp4") && !it.endsWith(".ts")) "$it.mp4" else it
                }

                Log.d(TAG, "Starting M3U8 download: $m3u8Url")

                m3u8Downloader?.setCallback(object : M3u8Downloader.DownloadCallback {
                    override fun onProgress(progress: Int, downloaded: Int, total: Int) {
                        updateNotification(taskId, "$downloaded/$total", progress, false)
                    }

                    override fun onComplete(outputFile: File) {
                        Log.d(TAG, "M3U8 download completed: ${outputFile.absolutePath}")
                        updateNotification(taskId, outputFile.name, 100, true)
                        scope.launch(Dispatchers.Main) {
                            Toast.makeText(this@DownloadService, "M3U8下载完成: ${outputFile.name}", Toast.LENGTH_LONG).show()
                        }
                        activeJobs.remove(taskId)
                        if (activeJobs.isEmpty()) stopSelf()
                    }

                    override fun onError(error: String) {
                        Log.e(TAG, "M3U8 download error: $error")
                        updateNotification(taskId, error, -1, true)
                        scope.launch(Dispatchers.Main) {
                            Toast.makeText(this@DownloadService, "M3U8下载错误: $error", Toast.LENGTH_LONG).show()
                        }
                        activeJobs.remove(taskId)
                        if (activeJobs.isEmpty()) stopSelf()
                    }
                })

                m3u8Downloader?.download(m3u8Url, downloadDir.absolutePath, safeFilename)

            } catch (e: Exception) {
                Log.e(TAG, "M3U8 download error", e)
                updateNotification(taskId, filename, -1, true)
                scope.launch(Dispatchers.Main) {
                    Toast.makeText(this@DownloadService, "M3U8下载错误: ${e.message}", Toast.LENGTH_LONG).show()
                }
                activeJobs.remove(taskId)
                if (activeJobs.isEmpty()) stopSelf()
            }
        }

        activeJobs[taskId] = job
    }

    private fun getDownloadDirectory(): File {
        return try {
            val path = preferencesManager.getDownloadPathSync()
            val dir = File(path)
            if (!dir.exists()) {
                dir.mkdirs()
            }
            Log.d(TAG, "Using download directory: $path")
            dir
        } catch (e: Exception) {
            Log.e(TAG, "Error getting download directory", e)
            val cacheDir = cacheDir
            val dir = File(cacheDir, "downloads")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            dir
        }
    }

    private fun startDownloadTask(taskId: String, url: String, filename: String, decodeKey: String? = null) {
        Log.d(TAG, "Starting download for: $filename, decodeKey: ${if (decodeKey != null) "provided" else "null"}")

        val job = scope.launch {
            try {
                val dir = getDownloadDirectory()
                val file = File(dir, filename)

                Log.d(TAG, "Downloading to: ${file.absolutePath}")

                val parsedUrl = java.net.URL(url)
                val host = parsedUrl.host
                val referer = "${parsedUrl.protocol}://$host/"

                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36")
                    .header("Referer", referer)
                    .header("Accept", "*/*")
                    .build()

                val response = okHttpClient.newCall(request).execute()

                val finalUrl = response.request.url.toString()
                Log.d(TAG, "Final URL after redirects: $finalUrl")

                val contentType = response.header("Content-Type") ?: ""
                val contentLength = response.body?.contentLength() ?: 0L

                Log.d(TAG, "Content-Type: $contentType, Content-Length: $contentLength")

                if (!response.isSuccessful) {
                    Log.e(TAG, "Download failed: ${response.code}")
                    updateNotification(taskId, filename, -1, true)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@DownloadService, "下载失败: HTTP ${response.code}", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }

                if (contentType.contains("text/html") && contentLength < 1024 * 1024) {
                    Log.e(TAG, "Received HTML page instead of video, likely a redirect page")
                    updateNotification(taskId, filename, -1, true)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@DownloadService, "下载失败: 该链接可能需要登录或不支持直接下载", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }

                val actualFilename = if (filename.contains(".")) filename else {
                    val extension = when {
                        contentType.contains("video/mp4") -> ".mp4"
                        contentType.contains("video/webm") -> ".webm"
                        contentType.contains("audio/mpeg") || contentType.contains("audio/mp3") -> ".mp3"
                        contentType.contains("audio/x-m4a") -> ".m4a"
                        contentType.contains("image/jpeg") -> ".jpg"
                        contentType.contains("image/png") -> ".png"
                        contentType.contains("application/vnd.apple.mpegurl") -> ".m3u8"
                        else -> ".mp4"
                    }
                    filename + extension
                }

                val tempFile = File(dir, "$actualFilename.tmp")
                val actualFile = File(dir, actualFilename)
                Log.d(TAG, "Using filename: ${actualFile.name}")

                val totalBytes = if (contentLength > 0) contentLength else 0L
                Log.d(TAG, "Total bytes to download: $totalBytes")

                response.body?.byteStream()?.use { inputStream ->
                    FileOutputStream(tempFile).use { outputStream ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var downloadedBytes = 0L
                        var lastUpdateTime = System.currentTimeMillis()

                        while (isActive) {
                            bytesRead = inputStream.read(buffer)
                            if (bytesRead == -1) break

                            outputStream.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead

                            val currentTime = System.currentTimeMillis()
                            if (totalBytes > 0 && currentTime - lastUpdateTime > 500) {
                                val progress = ((downloadedBytes * 100) / totalBytes).toInt()
                                updateNotification(taskId, actualFilename, progress, false)
                                lastUpdateTime = currentTime
                            }
                        }
                    }
                }

                if (!decodeKey.isNullOrEmpty()) {
                    Log.d(TAG, "Attempting to decrypt file with decodeKey")
                    try {
                        // 使用 VideoDecryptor 进行解密
                        val decryptResult = VideoDecryptor.decryptFile(
                            tempFile,
                            actualFile,
                            decodeKey,
                            VideoDecryptor.DecryptMode.WECHAT_VIDEO
                        )
                        
                        if (decryptResult.success) {
                            // 修复 MP4 头
                            VideoDecryptor.fixMp4Header(actualFile)
                            tempFile.delete()
                            Log.d(TAG, "Decryption completed successfully")
                        } else {
                            // 解密失败，尝试直接使用原文件
                            Log.w(TAG, "Decryption failed: ${decryptResult.error}, using encrypted file")
                            tempFile.renameTo(actualFile)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Decryption failed", e)
                        tempFile.renameTo(actualFile)
                    }
                } else {
                    tempFile.renameTo(actualFile)
                }

                Log.d(TAG, "Download completed to: ${actualFile.absolutePath}")
                updateNotification(taskId, actualFilename, 100, true)

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@DownloadService, "下载完成: ${actualFile.name}", Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                Log.e(TAG, "Download error", e)
                updateNotification(taskId, filename, -1, true)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@DownloadService, "下载错误: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                activeJobs.remove(taskId)
                if (activeJobs.isEmpty()) {
                    stopSelf()
                }
            }
        }

        activeJobs[taskId] = job
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Download progress notifications"
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(taskId: String, filename: String, progress: Int) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.downloading))
            .setContentText(filename)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, progress == 0)
            .setOngoing(progress in 1..99)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()

    private fun updateNotification(taskId: String, filename: String, progress: Int, completed: Boolean) {
        val notificationManager = getSystemService(NotificationManager::class.java)

        val notification = if (completed) {
            if (progress < 0) {
                NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle(getString(R.string.download_failed))
                    .setContentText(filename)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setAutoCancel(true)
                    .build()
            } else {
                NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle(getString(R.string.download_complete))
                    .setContentText(filename)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setAutoCancel(true)
                    .build()
            }
        } else {
            createNotification(taskId, filename, progress)
        }

        notificationManager.notify(NOTIFICATION_ID_BASE + taskId.hashCode(), notification)
    }
}

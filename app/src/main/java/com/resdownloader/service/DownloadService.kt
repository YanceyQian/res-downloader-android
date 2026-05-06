package com.resdownloader.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.resdownloader.R
import com.resdownloader.data.model.DownloadStatus
import com.resdownloader.data.model.DownloadTask
import com.resdownloader.data.model.ResourceInfo
import com.resdownloader.data.model.ResourceType
import com.resdownloader.ui.MainActivity
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

class DownloadService : Service() {

    companion object {
        const val CHANNEL_ID = "download_channel"
        const val NOTIFICATION_ID_BASE = 1000

        const val ACTION_START = "com.resdownloader.START_DOWNLOAD"
        const val ACTION_PAUSE = "com.resdownloader.PAUSE_DOWNLOAD"
        const val ACTION_RESUME = "com.resdownloader.RESUME_DOWNLOAD"
        const val ACTION_CANCEL = "com.resdownloader.CANCEL_DOWNLOAD"

        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_RESOURCE_URL = "resource_url"
        const val EXTRA_RESOURCE_TYPE = "resource_type"
        const val EXTRA_RESOURCE_PLATFORM = "resource_platform"
        const val EXTRA_FILENAME = "filename"
        const val EXTRA_DOWNLOAD_PATH = "download_path"

        private val activeJobs = ConcurrentHashMap<String, Job>()
        private val pausedTasks = ConcurrentHashMap<String, Long>()

        private val _downloadStates = ConcurrentHashMap<String, DownloadState>()
        val downloadStates: Map<String, DownloadState> = _downloadStates

        private var downloadDirectory = "/storage/emulated/0/Download/ResDownloader"

        fun setDownloadDirectory(path: String) {
            downloadDirectory = path
        }

        fun startDownload(context: Context, taskId: String, resourceInfo: ResourceInfo) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TASK_ID, taskId)
                putExtra(EXTRA_RESOURCE_URL, resourceInfo.url)
                putExtra(EXTRA_RESOURCE_TYPE, resourceInfo.type.name)
                putExtra(EXTRA_RESOURCE_PLATFORM, resourceInfo.platform.name)
                putExtra(EXTRA_FILENAME, resourceInfo.filename)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun pauseDownload(taskId: String) {
            activeJobs[taskId]?.cancel()
            activeJobs.remove(taskId)
            _downloadStates[taskId] = DownloadState.Paused(_downloadStates[taskId]?.progress ?: 0)
        }

        fun cancelDownload(taskId: String) {
            activeJobs[taskId]?.cancel()
            activeJobs.remove(taskId)
            _downloadStates.remove(taskId)
            pausedTasks.remove(taskId)
        }

        fun isTaskActive(taskId: String): Boolean = activeJobs.containsKey(taskId)
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return START_NOT_STICKY
                val url = intent.getStringExtra(EXTRA_RESOURCE_URL) ?: return START_NOT_STICKY
                val type = intent.getStringExtra(EXTRA_RESOURCE_TYPE) ?: "OTHER"
                val platform = intent.getStringExtra(EXTRA_RESOURCE_PLATFORM) ?: "OTHER"
                val filename = intent.getStringExtra(EXTRA_FILENAME) ?: "download"

                val notification = createNotification(taskId, filename, 0)
                startForeground(NOTIFICATION_ID_BASE + taskId.hashCode(), notification)

                startDownloadTask(taskId, url, ResourceType.valueOf(type), platform, filename)
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
        scope.cancel()
        super.onDestroy()
    }

    private fun startDownloadTask(
        taskId: String,
        url: String,
        type: ResourceType,
        platform: String,
        filename: String
    ) {
        val job = scope.launch {
            try {
                _downloadStates[taskId] = DownloadState.Downloading(0)

                val dir = File(downloadDirectory)
                if (!dir.exists()) {
                    dir.mkdirs()
                }

                val file = File(dir, filename)
                var downloadedBytes = pausedTasks.remove(taskId) ?: 0L

                val requestBuilder = Request.Builder().url(url)

                if (downloadedBytes > 0) {
                    requestBuilder.addHeader("Range", "bytes=$downloadedBytes-")
                }

                val response = okHttpClient.newCall(requestBuilder.build()).execute()

                if (!response.isSuccessful && response.code != 206) {
                    _downloadStates[taskId] = DownloadState.Failed("Download failed: ${response.code}")
                    updateNotification(taskId, filename, -1, true)
                    return@launch
                }

                val totalBytes = response.body?.contentLength()?.let { downloadedBytes + it } ?: 0L

                response.body?.byteStream()?.use { inputStream ->
                    FileOutputStream(file, true).use { outputStream ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int

                        while (isActive) {
                            bytesRead = inputStream.read(buffer)
                            if (bytesRead == -1) break

                            outputStream.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead

                            if (totalBytes > 0) {
                                val progress = ((downloadedBytes * 100) / totalBytes).toInt()
                                _downloadStates[taskId] = DownloadState.Downloading(progress)
                                updateNotification(taskId, filename, progress, false)
                            }
                        }
                    }
                }

                _downloadStates[taskId] = DownloadState.Completed(file.absolutePath)
                updateNotification(taskId, filename, 100, true)

                broadcastDownloadComplete(taskId, file.absolutePath)

            } catch (e: CancellationException) {
                pausedTasks[taskId] = getCurrentDownloadedBytes(taskId)
                _downloadStates[taskId] = DownloadState.Paused(
                    _downloadStates[taskId]?.progress ?: 0
                )
            } catch (e: Exception) {
                e.printStackTrace()
                _downloadStates[taskId] = DownloadState.Failed(e.message ?: "Unknown error")
                updateNotification(taskId, filename, -1, true)
            } finally {
                activeJobs.remove(taskId)
                if (activeJobs.isEmpty()) {
                    stopSelf()
                }
            }
        }

        activeJobs[taskId] = job
    }

    private fun getCurrentDownloadedBytes(taskId: String): Long {
        return pausedTasks[taskId] ?: 0L
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

    private fun broadcastDownloadComplete(taskId: String, filePath: String) {
        val intent = Intent("com.resdownloader.DOWNLOAD_COMPLETE")
        intent.putExtra(EXTRA_TASK_ID, taskId)
        intent.putExtra("file_path", filePath)
        sendBroadcast(intent)
    }

    sealed class DownloadState {
        data class Downloading(val progress: Int) : DownloadState()
        data class Paused(val progress: Int) : DownloadState()
        data class Completed(val filePath: String) : DownloadState()
        data class Failed(val error: String) : DownloadState()
    }
}

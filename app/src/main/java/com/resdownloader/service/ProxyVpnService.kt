package com.resdownloader.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.resdownloader.R
import com.resdownloader.ui.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetAddress
import java.nio.ByteBuffer

class ProxyVpnService : VpnService() {

    companion object {
        const val CHANNEL_ID = "proxy_service_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_START = "com.resdownloader.START_VPN"
        const val ACTION_STOP = "com.resdownloader.STOP_VPN"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        private var proxyPort = 8899
        private var serverAddress = "127.0.0.1"

        fun setConfig(port: Int) {
            proxyPort = port
        }
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var localProxyServer: LocalProxyServer? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startLocalProxyServer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopProxy()
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                startProxy()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopProxy()
        super.onDestroy()
    }

    override fun onRevoke() {
        stopProxy()
        super.onRevoke()
    }

    private fun startProxy() {
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)

        _isRunning.value = true

        try {
            vpnInterface = establishVpn()
            job = scope.launch {
                processVpnTraffic()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            stopProxy()
        }
    }

    private fun stopProxy() {
        job?.cancel()
        job = null

        vpnInterface?.close()
        vpnInterface = null

        localProxyServer?.stop()
        localProxyServer = null

        _isRunning.value = false

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun establishVpn(): ParcelFileDescriptor {
        val builder = Builder()
            .setSession("ResDownloader Proxy")
            .addAddress("10.0.0.2", 32)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("8.8.8.8")
            .addDnsServer("8.8.4.4")
            .setMtu(1500)
            .setBlocking(true)

        builder.addAddress("10.0.0.1", 32)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.addDisallowedApplication("com.resdownloader")
        }

        return builder.establish()!!
    }

    private suspend fun processVpnTraffic() {
        val vpnFd = vpnInterface ?: return
        val input = FileInputStream(vpnFd.fileDescriptor)
        val output = FileOutputStream(vpnFd.fileDescriptor)

        val packet = ByteBuffer.allocate(32767)
        val buffer = ByteBuffer.allocate(32767)

        try {
            while (isActive && vpnInterface != null) {
                packet.clear()
                val length = input.read(packet.array())

                if (length > 0) {
                    packet.limit(length)
                    processPacket(packet, buffer, output)
                } else if (length < 0) {
                    break
                }

                delay(1)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                input.close()
                output.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun processPacket(
        inputPacket: ByteBuffer,
        outputBuffer: ByteBuffer,
        output: FileOutputStream
    ) {
        if (inputPacket.remaining() < 20) return

        val version = (inputPacket.get(0).toInt() shr 4) and 0xF
        if (version != 4) return

        val protocol = inputPacket.get(9).toInt() and 0xFF

        if (protocol == 6 || protocol == 17) {
            try {
                outputBuffer.clear()
                inputPacket.rewind()
                inputPacket.get(outputBuffer.array(), 0, inputPacket.remaining())
                outputBuffer.limit(inputPacket.remaining())
                output.write(outputBuffer.array(), 0, outputBuffer.limit())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun startLocalProxyServer() {
        localProxyServer = LocalProxyServer(proxyPort) { resourceData ->
            onResourceIntercepted(resourceData)
        }
        scope.launch {
            localProxyServer?.start()
        }
    }

    private fun onResourceIntercepted(data: ResourceData) {
        val intent = Intent("com.resdownloader.RESOURCE_INTERCEPTED")
        intent.putExtra("url", data.url)
        intent.putExtra("type", data.type)
        intent.putExtra("platform", data.platform)
        intent.putExtra("size", data.size)
        sendBroadcast(intent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Proxy Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when proxy is running"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle(getString(R.string.app_name))
        .setContentText(getString(R.string.proxy_running))
        .setSmallIcon(android.R.drawable.ic_menu_share)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setOngoing(true)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        .addAction(
            android.R.drawable.ic_menu_close_clear_cancel,
            getString(R.string.stop_proxy),
            PendingIntent.getService(
                this,
                0,
                Intent(this, ProxyVpnService::class.java).apply {
                    action = ACTION_STOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        .build()

    data class ResourceData(
        val url: String,
        val type: String,
        val platform: String,
        val size: Long
    )
}

class LocalProxyServer(
    private val port: Int,
    private val onResourceIntercepted: (ProxyVpnService.ResourceData) -> Unit
) {
    private var serverSocket: java.net.ServerSocket? = null
    private var isRunning = false

    fun start() {
        isRunning = true
        try {
            serverSocket = java.net.ServerSocket(port)
            serverSocket?.soTimeout = 1000

            while (isRunning) {
                try {
                    val client = serverSocket?.accept()
                    if (client != null) {
                        handleClient(client)
                    }
                } catch (e: java.net.SocketTimeoutException) {
                    continue
                } catch (e: Exception) {
                    if (isRunning) e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun handleClient(client: java.net.Socket) {
        try {
            val buffer = ByteArray(8192)
            val bytesRead = client.getInputStream().read(buffer)

            if (bytesRead > 0) {
                val request = String(buffer, 0, bytesRead)
                val urlMatch = Regex("Host:\\s*([^\\r\\n]+)").find(request)
                val host = urlMatch?.groupValues?.get(1) ?: ""

                if (host.isNotEmpty()) {
                    val fullUrl = "https://$host"
                    val resourceData = ProxyVpnService.ResourceData(
                        url = fullUrl,
                        type = detectResourceType(fullUrl),
                        platform = detectPlatform(fullUrl),
                        size = 0
                    )
                    onResourceIntercepted(resourceData)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                client.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun detectResourceType(url: String): String {
        val lower = url.lowercase()
        return when {
            lower.contains(".m3u8") -> "m3u8"
            lower.contains(".mp4") || lower.contains(".flv") -> "video"
            lower.contains(".mp3") || lower.contains(".flac") || lower.contains(".m4a") -> "audio"
            lower.contains(".jpg") || lower.contains(".png") || lower.contains(".gif") -> "image"
            else -> "other"
        }
    }

    private fun detectPlatform(url: String): String {
        val lower = url.lowercase()
        return when {
            lower.contains("weixin.qq.com") || lower.contains("wechat.com") -> "wechat"
            lower.contains("douyin.com") -> "douyin"
            lower.contains("kuaishou.com") -> "kuaishou"
            lower.contains("xiaohongshu.com") -> "xiaohongshu"
            lower.contains("kugou.com") -> "kugou"
            lower.contains("y.qq.com") -> "qqmusic"
            else -> "other"
        }
    }
}

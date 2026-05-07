package com.resdownloader.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.resdownloader.MainActivity
import com.resdownloader.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

@AndroidEntryPoint
class ProxyVpnService : VpnService() {

    companion object {
        const val TAG = "ProxyVpnService"
        const val CHANNEL_ID = "proxy_service_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_START = "com.resdownloader.START_PROXY"
        const val ACTION_STOP = "com.resdownloader.STOP_PROXY"

        private var isRunningValue = false
        private var packetCountValue = 0L
        private var byteCountValue = 0L
        private val resourcesValue = mutableListOf<com.resdownloader.data.model.ResourceInfo>()

        // 属性访问器 - 保持向后兼容
        val isRunning: Boolean get() = isRunningValue
        
        // 函数访问器 - 用于解决某些上下文中的类型推断问题
        fun getRunning(): Boolean = isRunningValue
        fun getPacketCount(): Long = packetCountValue
        fun getByteCount(): Long = byteCountValue
        fun resetStats() {
            packetCountValue = 0
            byteCountValue = 0
        }
        
        fun addResource(resource: com.resdownloader.data.model.ResourceInfo) {
            synchronized(resourcesValue) {
                if (resourcesValue.none { it.id == resource.id }) {
                    resourcesValue.add(resource)
                }
            }
        }
        
        fun removeResource(id: String) {
            synchronized(resourcesValue) {
                resourcesValue.removeIf { it.id == id }
            }
        }
        
        fun getResources(): List<com.resdownloader.data.model.ResourceInfo> {
            synchronized(resourcesValue) {
                return resourcesValue.toList()
            }
        }
        
        // 函数访问器 - 用于解决某些上下文中的类型推断问题
        fun getResourcesList(): List<com.resdownloader.data.model.ResourceInfo> = getResources()
        fun clearResourcesList() {
            synchronized(resourcesValue) {
                resourcesValue.clear()
            }
        }
        
        fun clearResources() {
            clearResourcesList()
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var vpnInterface: ParcelFileDescriptor? = null
    private var vpnJob: Job? = null
    private var proxyServer: ServerSocket? = null
    private val isVpnRunning = AtomicBoolean(false)

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: ${intent?.action}")

        when (intent?.action) {
            ACTION_STOP -> {
                stopVpn()
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                startVpn()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    override fun onRevoke() {
        Log.d(TAG, "VPN revoked")
        stopVpn()
        super.onRevoke()
    }

    private fun startVpn() {
        if (isVpnRunning.get()) {
            Log.d(TAG, "VPN already running")
            return
        }

        serviceScope.launch {
            try {
                Log.d(TAG, "Starting VPN service")

                val builder = Builder()
                    .setSession("ResDownloader")
                    .addAddress("10.0.0.2", 32)
                    .addRoute("0.0.0.0", 0)
                    .setMtu(1500)
                    .addDnsServer("8.8.8.8")
                    .addDnsServer("114.114.114.114")

                // VPN 全流量代理模式
                // 排除本应用，确保抓取功能的完整性
                builder.addDisallowedApplication(packageName)

                val configureIntent = Intent(this@ProxyVpnService, MainActivity::class.java)
                builder.setConfigureIntent(
                    PendingIntent.getActivity(
                        this@ProxyVpnService,
                        0,
                        configureIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )

                vpnInterface = builder.establish()

                if (vpnInterface != null) {
                    isVpnRunning.set(true)
                    isRunningValue = true
                    resetStats()

                    val notification = createNotification()
                    startForeground(NOTIFICATION_ID, notification)

                    Log.d(TAG, "VPN established successfully")
                    startProxyServer()
                    startVpnProcessing()
                } else {
                    Log.e(TAG, "Failed to establish VPN interface")
                    stopSelf()
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error starting VPN", e)
                stopSelf()
            }
        }
    }

    private fun startProxyServer() {
        try {
            proxyServer = ServerSocket(8899)
            serviceScope.launch {
                while (isVpnRunning.get()) {
                    try {
                        val client = proxyServer?.accept()
                        client?.let {
                            handleProxyClient(it)
                        }
                    } catch (e: Exception) {
                        if (isVpnRunning.get()) {
                            Log.e(TAG, "Error accepting proxy client", e)
                        }
                    }
                }
            }
            Log.d(TAG, "Proxy server started on port 8899")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start proxy server", e)
        }
    }

    private suspend fun handleProxyClient(client: Socket) {
        withContext(Dispatchers.IO) {
            try {
                val input = client.getInputStream()
                val output = client.getOutputStream()

                val buffer = ByteArray(8192)
                val bytesRead = input.read(buffer)

                if (bytesRead > 0) {
                    packetCountValue++
                    byteCountValue += bytesRead.toLong()

                    val request = String(buffer, 0, bytesRead)
                    val logLen = if (request.length > 100) 100 else request.length
                    Log.d(TAG, "Proxy request: ${request.substring(0, logLen)}")

                    if (request.startsWith("CONNECT")) {
                        val firstLine = request.split("\n")[0]
                        val parts = firstLine.split(" ")
                        if (parts.size >= 2) {
                            val hostPort = parts[1]
                            val hostParts = hostPort.split(":")
                            if (hostParts.size == 2) {
                                val host = hostParts[0]
                                val portStr = hostParts[1]
                                val port = portStr.toIntOrNull()
                                if (port != null) {
                                    val targetSocket = Socket(host, port)
                                    output.write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray())
                                    output.flush()

                                    launch {
                                        val clientIn = client.getInputStream()
                                        val targetOut = targetSocket.getOutputStream()
                                        val targetIn = targetSocket.getInputStream()
                                        val clientOut = client.getOutputStream()

                                        launch {
                                            val pipeBuffer = ByteArray(8192)
                                            while (isVpnRunning.get()) {
                                                try {
                                                    val bytes = clientIn.read(pipeBuffer)
                                                    if (bytes <= 0) break
                                                    packetCountValue++
                                                    byteCountValue += bytes.toLong()
                                                    targetOut.write(pipeBuffer, 0, bytes)
                                                    targetOut.flush()
                                                } catch (e: Exception) {
                                                    break
                                                }
                                            }
                                        }

                                        launch {
                                            val pipeBuffer = ByteArray(8192)
                                            while (isVpnRunning.get()) {
                                                try {
                                                    val bytes = targetIn.read(pipeBuffer)
                                                    if (bytes <= 0) break
                                                    packetCountValue++
                                                    byteCountValue += bytes.toLong()
                                                    clientOut.write(pipeBuffer, 0, bytes)
                                                    clientOut.flush()
                                                } catch (e: Exception) {
                                                    break
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Unit
            } catch (e: Exception) {
                Log.e(TAG, "Error handling proxy client", e)
            } finally {
                try {
                    client.close()
                } catch (e: Exception) {
                }
            }
        }
    }

    private fun startVpnProcessing() {
        vpnJob = serviceScope.launch {
            val input = FileInputStream(vpnInterface!!.fileDescriptor)
            val buffer = ByteArray(32767)

            while (isVpnRunning.get()) {
                try {
                    val bytesRead = input.read(buffer)
                    if (bytesRead > 0) {
                        packetCountValue++
                        byteCountValue += bytesRead
                    } else {
                        delay(10)
                    }
                } catch (e: Exception) {
                    if (isVpnRunning.get()) {
                        Log.e(TAG, "Error reading from VPN", e)
                    }
                }
            }
        }
    }

    private fun stopVpn() {
        Log.d(TAG, "Stopping VPN service")

        isVpnRunning.set(false)
        isRunningValue = false

        vpnJob?.cancel()
        vpnJob = null

        try {
            proxyServer?.close()
            proxyServer = null
        } catch (e: Exception) {
        }

        vpnInterface?.close()
        vpnInterface = null

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.app_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "资源下载服务"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): android.app.Notification {
        val stopIntent = Intent(this, ProxyVpnService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("正在抓取资源")
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setOngoing(true)
            .setShowWhen(false)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "停止",
                stopPendingIntent
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
}
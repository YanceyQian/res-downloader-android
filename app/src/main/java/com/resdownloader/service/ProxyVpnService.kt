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
import com.resdownloader.data.model.Platform
import com.resdownloader.data.model.ResourceInfo
import com.resdownloader.data.model.ResourceType
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
        private val resourcesValue = mutableListOf<ResourceInfo>()

        val isRunning: Boolean get() = isRunningValue
        
        fun getRunning(): Boolean = isRunningValue
        fun getPacketCount(): Long = packetCountValue
        fun getByteCount(): Long = byteCountValue
        
        fun resetStats() {
            packetCountValue = 0
            byteCountValue = 0
        }
        
        fun addResource(resource: ResourceInfo) {
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
        
        fun getResources(): List<ResourceInfo> {
            synchronized(resourcesValue) {
                return resourcesValue.toList()
            }
        }
        
        fun clearResources() {
            synchronized(resourcesValue) {
                resourcesValue.clear()
            }
        }

        // 视频号相关域名
        private val wechatDomains = listOf(
            "weixin.qq.com",
            "wechat.com",
            "wxtingyun.com",
            "mp.weixin.qq.com",
            "res.wx.qq.com",
            "szshort.biz.wechat.com",
            "shoujiweservice.qq.com"
        )

        // 其他支持的平台域名
        private val supportedPlatforms = mapOf(
            "bilibili" to listOf("bilibili.com", "biliintl.com", "b23.tv"),
            "douyin" to listOf("douyin.com", "iesdouyin.com", "amemv.com"),
            "kuaishou" to listOf("kuaishou.com", "kspkg.com", "kuaishoupay.com"),
            "xiaohongshu" to listOf("xiaohongshu.com", "xhslink.com"),
            "kugou" to listOf("kugou.com", "kgimg.com"),
            "qqmusic" to listOf("y.qq.com", "music.qq.com", "imgcache.qq.com"),
            "netease" to listOf("music.163.com", "126.net"),
            "weibo" to listOf("weibo.com", "sinaimg.cn", "weibocdn.com"),
            "zhihu" to listOf("zhihu.com", "zimg.cn")
        )

        // 媒体文件扩展名
        private val mediaExtensions = listOf(
            ".mp4", ".m3u8", ".ts", ".flv", ".webm", ".mkv",
            ".mp3", ".flac", ".wav", ".aac", ".ogg", ".m4a",
            ".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp"
        )
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var vpnInterface: ParcelFileDescriptor? = null
    private var vpnJob: Job? = null
    private val isVpnRunning = AtomicBoolean(false)
    
    // 媒体类型检测
    private val ipv4HeaderSize = 20
    private val tcpHeaderSize = 20

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
                    .setBlocking(true)  // 同步模式

                // 排除本应用，避免流量循环
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

    /**
     * VPN 流量处理核心
     * 从 TUN 接口读取数据包，解析 TCP 连接，转发到目标服务器
     */
    private fun startVpnProcessing() {
        vpnJob = serviceScope.launch {
            val vpnFd = vpnInterface ?: return@launch
            val input = FileInputStream(vpnFd.fileDescriptor)
            val output = FileOutputStream(vpnFd.fileDescriptor)
            val packet = ByteBuffer.allocate(32767)
            packet.order(ByteOrder.LITTLE_ENDIAN)

            Log.d(TAG, "VPN processing started")

            while (isVpnRunning.get() && isActive) {
                try {
                    packet.clear()
                    val length = input.read(packet.array())

                    if (length > 0) {
                        packet.position(0)
                        packet.limit(length)
                        
                        packetCountValue++
                        byteCountValue += length

                        // 解析 IP 头部
                        if (length >= ipv4HeaderSize) {
                            val version = (packet.get(0).toInt() and 0xF0) shr 4
                            
                            if (version == 4) {
                                // IPv4
                                val headerLength = (packet.get(0).toInt() and 0x0F) * 4
                                val protocol = packet.get(9).toInt() and 0xFF
                                
                                if (protocol == 6) { // TCP
                                    // 获取目标 IP 和端口
                                    val destIp = "${packet.get(16).toInt() and 0xFF}.${packet.get(17).toInt() and 0xFF}.${packet.get(18).toInt() and 0xFF}.${packet.get(19).toInt() and 0xFF}"
                                    val destPort = ((packet.get(headerLength + 2).toInt() and 0xFF) shl 8) or (packet.get(headerLength + 3).toInt() and 0xFF)
                                    
                                    // 获取源 IP
                                    val srcIp = "${packet.get(12).toInt() and 0xFF}.${packet.get(13).toInt() and 0xFF}.${packet.get(14).toInt() and 0xFF}.${packet.get(15).toInt() and 0xFF}"
                                    
                                    // 解析 TCP 数据
                                    val tcpDataOffset = headerLength + tcpHeaderSize
                                    if (length > tcpDataOffset) {
                                        val tcpData = ByteArray(length - tcpDataOffset)
                                        packet.position(tcpDataOffset)
                                        packet.get(tcpData, 0, tcpData.size)
                                        
                                        // 尝试解析 HTTP/HTTPS 数据
                                        val httpData = String(tcpData, Charsets.UTF_8)
                                        processHttpData(httpData, destIp, destPort)
                                    }
                                }
                            }
                        }

                        // 将数据包写回（保持 VPN 正常工作）
                        packet.position(0)
                        packet.limit(length)
                        try {
                            output.write(packet.array(), 0, length)
                            output.flush()
                        } catch (e: Exception) {
                            // 忽略写入错误
                        }
                    } else {
                        delay(10)
                    }
                } catch (e: Exception) {
                    if (isVpnRunning.get()) {
                        Log.e(TAG, "Error processing VPN packet", e)
                        delay(100)
                    }
                }
            }
            
            Log.d(TAG, "VPN processing stopped")
        }
    }

    /**
     * 处理 HTTP/HTTPS 数据，捕获媒体资源
     */
    private fun processHttpData(data: String, destIp: String, destPort: Int) {
        try {
            // 检测 HTTP 请求行
            val lines = data.split("\r\n", "\n")
            if (lines.isEmpty()) return
            
            val firstLine = lines[0]
            
            // 检测 CONNECT 请求（HTTPS）
            if (firstLine.startsWith("CONNECT")) {
                val parts = firstLine.split(" ")
                if (parts.size >= 2) {
                    val hostPort = parts[1]
                    val hostParts = hostPort.split(":")
                    val host = hostParts[0]
                    val port = if (hostParts.size > 1) hostParts[1].toIntOrNull() ?: 443 else 443
                    
                    Log.d(TAG, "HTTPS CONNECT: $host:$port")
                    detectMediaResource(host, port, "https")
                }
            }
            
            // 检测 HTTP 请求（明文）
            if (firstLine.startsWith("GET") || firstLine.startsWith("POST")) {
                // 提取 Host
                val host = lines.find { it.startsWith("Host:") }?.substringAfter("Host:")?.trim()
                
                if (host != null && lines.size > 1) {
                    // 检测 URL 中的媒体文件
                    val urlParts = firstLine.split(" ")
                    if (urlParts.size >= 2) {
                        val path = urlParts[1]
                        detectMediaUrl("http://$host$path", host)
                    }
                }
            }
        } catch (e: Exception) {
            // 静默处理解析错误
        }
    }

    /**
     * 检测媒体 URL
     */
    private fun detectMediaUrl(url: String, host: String) {
        val lowerUrl = url.lowercase()
        
        // 检查是否为媒体文件
        val isMedia = mediaExtensions.any { lowerUrl.contains(it) }
        
        if (isMedia) {
            val platformStr = detectPlatform(host)
            val platform = platformStr.toPlatform()
            val resourceType = detectResourceType(lowerUrl)
            val filename = extractFilename(url)
            
            Log.d(TAG, "Media detected: $url (platform: $platform, type: $resourceType)")
            
            val resource = ResourceInfo(
                id = generateId(),
                url = url,
                type = resourceType,
                platform = platform,
                filename = filename,
                size = 0L,
                timestamp = System.currentTimeMillis()
            )
            
            addResource(resource)
        }
    }

    /**
     * 检测媒体资源（通过域名）
     */
    private fun detectMediaResource(host: String, port: Int, protocol: String) {
        val lowerHost = host.lowercase()
        
        // 检查是否为支持的平台
        val platform = detectPlatform(lowerHost)
        if (platform != "other") {
            Log.d(TAG, "Platform traffic detected: $host (platform: $platform)")
            // 记录平台流量，但不立即创建资源
            // 资源会在实际媒体请求时捕获
        }
    }

    /**
     * 检测平台类型
     */
    private fun detectPlatform(host: String): String {
        val lowerHost = host.lowercase()
        
        // 微信/视频号
        if (wechatDomains.any { lowerHost.contains(it) }) {
            return "wechat"
        }
        
        // 其他平台
        for ((platform, domains) in supportedPlatforms) {
            if (domains.any { lowerHost.contains(it) }) {
                return platform
            }
        }
        
        return "other"
    }

    /**
     * 将字符串转换为 Platform 枚举
     */
    private fun String.toPlatform(): Platform {
        return when (this.lowercase()) {
            "wechat" -> Platform.WECHAT
            "douyin" -> Platform.DOUYIN
            "kuaishou" -> Platform.KUAISHOU
            "xiaohongshu", "xhs" -> Platform.XIAOHONGSHU
            "kugou" -> Platform.KUGOU
            "qqmusic" -> Platform.QQMUSIC
            "bilibili", "bili" -> Platform.BILIBILI
            "wechat_mini", "miniprogram" -> Platform.WECHAT_MINI
            "qqweishi" -> Platform.QQWEISHI
            "youtube" -> Platform.YOUTUBE
            else -> Platform.OTHER
        }
    }

    /**
     * 检测资源类型
     */
    private fun detectResourceType(url: String): ResourceType {
        return when {
            url.contains(".m3u8") -> ResourceType.M3U8
            url.contains(".mp4") || url.contains(".flv") || url.contains(".ts") -> ResourceType.VIDEO
            url.contains(".mp3") || url.contains(".flac") || url.contains(".wav") || url.contains(".m4a") -> ResourceType.AUDIO
            url.contains(".jpg") || url.contains(".png") || url.contains(".gif") || url.contains(".webp") -> ResourceType.IMAGE
            else -> ResourceType.VIDEO
        }
    }

    /**
     * 从 HTTP 响应中提取视频号 decodeKey
     * 
     * 参考原项目 plugin.qq.com.go 的 decodeKey 提取逻辑
     * 
     * 视频号 API 响应格式：
     * {
     *   "ret": 0,
     *   "data": {
     *     "decodeKey": "base64_encoded_key",
     *     "direct_url": "video_url",
     *     ...
     *   }
     * }
     */
    fun extractDecodeKey(responseBody: String): String? {
        try {
            // 尝试 JSON 格式
            val jsonRegex = Regex(""""decodeKey"\s*:\s*"([^"]+)"""")
            val match = jsonRegex.find(responseBody)
            if (match != null) {
                val key = match.groupValues[1]
                Log.d(TAG, "Found decodeKey in JSON response")
                return key
            }
            
            // 尝试直接 base64 编码
            val keyRegex = Regex("decodeKey=([A-Za-z0-9+/=]+)")
            val keyMatch = keyRegex.find(responseBody)
            if (keyMatch != null) {
                val key = keyMatch.groupValues[1]
                Log.d(TAG, "Found decodeKey in query string")
                return key
            }
            
            // 尝试从 URL 参数提取
            val urlKeyRegex = Regex("""key=([^&\s]+)""")
            val urlMatch = urlKeyRegex.find(responseBody)
            if (urlMatch != null) {
                val key = urlMatch.groupValues[1]
                Log.d(TAG, "Found key in URL parameters")
                return key
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting decodeKey", e)
        }
        
        return null
    }

    /**
     * 从 HTTP 请求中提取 Cookie
     * 视频号 API 需要有效的 Cookie
     */
    fun extractCookie(headers: Map<String, String>): String? {
        return headers["Cookie"] ?: headers["cookie"]
    }

    /**
     * 从 HTTP 请求中提取 Referer
     * 用于视频号 API 请求
     */
    fun extractReferer(headers: Map<String, String>): String? {
        return headers["Referer"] ?: headers["referer"]
    }

    /**
     * 从 URL 提取文件名
     */
    private fun extractFilename(url: String): String {
        return try {
            val path = url.substringAfter("?").substringBefore("#")
            val parts = path.split("/")
            val filename = parts.lastOrNull() ?: "download"
            if (filename.contains(".") || filename.isEmpty()) filename else "$filename.mp4"
        } catch (e: Exception) {
            "download_${System.currentTimeMillis()}"
        }
    }

    /**
     * 生成唯一 ID
     */
    private fun generateId(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return (1..8).map { chars.random() }.joinToString("")
    }

    private fun stopVpn() {
        Log.d(TAG, "Stopping VPN service")

        isVpnRunning.set(false)
        isRunningValue = false

        vpnJob?.cancel()
        vpnJob = null

        try {
            vpnInterface?.close()
            vpnInterface = null
        } catch (e: Exception) {
        }

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

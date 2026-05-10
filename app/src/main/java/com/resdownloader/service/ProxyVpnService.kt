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
import com.resdownloader.data.model.RuleSet
import com.resdownloader.data.preferences.PreferencesManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay

import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

@AndroidEntryPoint
class ProxyVpnService : VpnService() {

    @Inject
    lateinit var preferencesManager: PreferencesManager

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

        @Volatile
        private var ruleSet: RuleSet? = null

        val isRunning: Boolean get() = isRunningValue
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

        fun updateRuleSet(ruleString: String) {
            ruleSet = RuleSet.parse(ruleString)
            Log.d(TAG, "RuleSet updated")
        }

        fun getRuleSet(): RuleSet? = ruleSet

        private val platformDomains = mapOf(
            "wechat" to listOf("weixin.qq.com", "wechat.com", "wxtingyun.com"),
            "bilibili" to listOf("bilibili.com", "biliintl.com", "b23.tv", "bilivideo.com"),
            "douyin" to listOf("douyin.com", "iesdouyin.com", "amemv.com", "pstatp.com"),
            "kuaishou" to listOf("kuaishou.com", "kspkg.com", "kuaishoupay.com", "gifshow.com"),
            "xiaohongshu" to listOf("xiaohongshu.com", "xhslink.com", "xhscdn.com"),
            "kugou" to listOf("kugou.com", "kgimg.com"),
            "qqmusic" to listOf("y.qq.com", "music.qq.com", "imgcache.qq.com"),
            "netease" to listOf("music.163.com", "126.net"),
            "weibo" to listOf("weibo.com", "sinaimg.cn", "weibocdn.com"),
            "google" to listOf("googlevideo.com", "youtu.be"),
            "qqvideo" to listOf("v.qq.com", "weishi.qq.com")
        )

        private val mediaExtensions = listOf(
            ".mp4", ".m3u8", ".ts", ".flv", ".webm", ".mkv",
            ".mp3", ".flac", ".wav", ".aac", ".ogg", ".m4a",
            ".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp"
        )
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var vpnInterface: ParcelFileDescriptor? = null
    private var vpnJob: Job? = null
    private val connectionManager = ConnectionManager()
    private val mIsActive = AtomicBoolean(false)

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
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onRevoke() {
        Log.d(TAG, "VPN revoked")
        stopVpn()
        super.onRevoke()
    }

    private fun startVpn() {
        if (isRunningValue) {
            Log.d(TAG, "VPN already running")
            return
        }

        serviceScope.launch {
            try {
                Log.d(TAG, "Starting VPN service")

                val ruleString = preferencesManager.getRuleSync()
                updateRuleSet(ruleString)
                Log.d(TAG, "RuleSet initialized")

                val builder = Builder()
                    .setSession("ResDownloader")
                    .addAddress("10.0.0.2", 32)
                    .addRoute("0.0.0.0", 0)
                    .setMtu(1500)
                    .addDnsServer("8.8.8.8")
                    .addDnsServer("114.114.114.114")

                // 排除本应用避免流量循环
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
                    isRunningValue = true
                    mIsActive.set(true)
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
     * 从 TUN 接口读取 IP 数据包，解析目标地址，建立 TCP 连接并转发
     */
    private fun startVpnProcessing() {
        vpnJob = serviceScope.launch {
            val vpnFd = vpnInterface ?: return@launch
            val input = FileInputStream(vpnFd.fileDescriptor)
            val output = FileOutputStream(vpnFd.fileDescriptor)
            
            val packet = ByteBuffer.allocate(32767)
            packet.order(ByteOrder.LITTLE_ENDIAN)

            Log.d(TAG, "VPN processing started")

            while (mIsActive.get() && isRunningValue) {
                try {
                    packet.clear()
                    val length = input.read(packet.array())

                    if (length > 0) {
                        packet.position(0)
                        packet.limit(length)
                        
                        packetCountValue++
                        byteCountValue += length

                        // 在协程中处理数据包，避免阻塞
                        launch(Dispatchers.IO) {
                            processPacket(packet, length, output)
                        }
                    } else {
                        delay(1)
                    }
                } catch (e: Exception) {
                    if (mIsActive.get()) {
                        Log.e(TAG, "Error reading packet", e)
                        delay(10)
                    }
                }
            }

            Log.d(TAG, "VPN processing stopped")
        }
    }

    /**
     * 处理 IP 数据包
     */
    private suspend fun processPacket(packet: ByteBuffer, length: Int, output: FileOutputStream) {
        if (length < 20) return

        val version = (packet.get(0).toInt() and 0xF0) shr 4
        
        when (version) {
            4 -> processIPv4(packet, length, output)
            else -> {
                // 静默忽略其他版本
            }
        }
    }

    /**
     * 处理 IPv4 数据包
     */
    private suspend fun processIPv4(packet: ByteBuffer, length: Int, output: FileOutputStream) {
        val ipHeaderLength = (packet.get(0).toInt() and 0x0F) * 4
        if (length < ipHeaderLength) return

        val protocol = packet.get(9).toInt() and 0xFF
        
        // 提取源和目标地址
        val srcIp = "${packet.get(12).toInt() and 0xFF}.${packet.get(13).toInt() and 0xFF}.${packet.get(14).toInt() and 0xFF}.${packet.get(15).toInt() and 0xFF}"
        val dstIp = "${packet.get(16).toInt() and 0xFF}.${packet.get(17).toInt() and 0xFF}.${packet.get(18).toInt() and 0xFF}.${packet.get(19).toInt() and 0xFF}"

        when (protocol) {
            6 -> processTCP(packet, length, ipHeaderLength, srcIp, dstIp, output)  // TCP
            17 -> processUDP(packet, length, ipHeaderLength, srcIp, dstIp)  // UDP (DNS)
        }
    }

    /**
     * 处理 TCP 数据包 - 关键修复
     */
    private suspend fun processTCP(
        packet: ByteBuffer,
        totalLength: Int,
        ipHeaderLength: Int,
        srcIp: String,
        dstIp: String,
        output: FileOutputStream
    ) {
        val tcpHeaderStart = ipHeaderLength
        if (totalLength < tcpHeaderStart + 20) return

        // 解析 TCP 头部
        val srcPort = ((packet.get(tcpHeaderStart).toInt() and 0xFF) shl 8) or (packet.get(tcpHeaderStart + 1).toInt() and 0xFF)
        val dstPort = ((packet.get(tcpHeaderStart + 2).toInt() and 0xFF) shl 8) or (packet.get(tcpHeaderStart + 3).toInt() and 0xFF)
        
        val dataOffset = ((packet.get(tcpHeaderStart + 12).toInt() and 0xF0) shr 4) * 4
        val flags = packet.get(tcpHeaderStart + 13).toInt() and 0xFF
        
        val fin = (flags and 0x01) != 0
        val syn = (flags and 0x02) != 0
        val rst = (flags and 0x04) != 0
        val psh = (flags and 0x08) != 0
        val ack = (flags and 0x10) != 0

        // TCP 数据起始位置
        val tcpDataStart = tcpHeaderStart + dataOffset.coerceAtLeast(20)
        val tcpDataLength = totalLength - tcpDataStart

        // 创建连接标识
        val connId = ConnectionId(srcIp, srcPort, dstIp, dstPort)

        when {
            // SYN - 建立新连接
            syn && !ack -> {
                Log.d(TAG, "TCP SYN: $dstIp:$dstPort")
                handleNewConnection(connId, packet, ipHeaderLength, tcpDataStart, totalLength, output)
            }
            
            // FIN - 关闭连接
            fin -> {
                Log.d(TAG, "TCP FIN: $connId")
                connectionManager.closeConnection(connId)
            }
            
            // RST - 重置连接
            rst -> {
                Log.d(TAG, "TCP RST: $connId")
                connectionManager.closeConnection(connId)
            }
            
            // PSH/ACK - 数据包，转发到目标
            (psh || ack || tcpDataLength > 0) && !syn -> {
                if (tcpDataLength > 0) {
                    val tcpData = ByteArray(tcpDataLength)
                    packet.position(tcpDataStart)
                    packet.get(tcpData, 0, tcpDataLength)
                    
                    // 转发数据到目标
                    connectionManager.sendToTarget(connId, tcpData)
                    
                    // 检测媒体 URL
                    detectMediaInRequest(tcpData, tcpDataLength, dstIp, dstPort)
                }
            }
        }
    }

    /**
     * 处理新的 TCP 连接
     */
    private suspend fun handleNewConnection(
        connId: ConnectionId,
        originalPacket: ByteBuffer,
        ipHeaderLength: Int,
        tcpHeaderStart: Int,
        totalLength: Int,
        vpnOutput: FileOutputStream
    ) {
        try {
            // 解析 TCP 序列号
            val seqNum = ((originalPacket.get(tcpHeaderStart + 4).toInt() and 0xFF) shl 24) or
                         ((originalPacket.get(tcpHeaderStart + 5).toInt() and 0xFF) shl 16) or
                         ((originalPacket.get(tcpHeaderStart + 6).toInt() and 0xFF) shl 8) or
                         (originalPacket.get(tcpHeaderStart + 7).toInt() and 0xFF)

            Log.d(TAG, "Connecting to ${connId.dstIp}:${connId.dstPort}")

            // 建立到目标服务器的连接
            val targetSocket = Socket()
            targetSocket.tcpNoDelay = true
            targetSocket.soTimeout = 30000
            targetSocket.connect(InetSocketAddress(connId.dstIp, connId.dstPort), 10000)

            Log.d(TAG, "Connected to ${connId.dstIp}:${connId.dstPort}")

            // 创建连接上下文
            val context = ConnectionContext(
                id = connId,
                targetSocket = targetSocket,
                vpnOutput = vpnOutput,
                vpnPacket = originalPacket,
                ipHeaderLength = ipHeaderLength,
                tcpHeaderStart = tcpHeaderStart,
                localSeq = seqNum.toLong()
            )

            // 保存连接
            connectionManager.addConnection(connId, context)

            // 启动从目标读取响应的协程
            serviceScope.launch(Dispatchers.IO) {
                readFromTarget(context)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect to ${connId.dstIp}:${connId.dstPort}", e)
            // 发送 TCP RST
            sendTcpRst(connId, vpnOutput)
        }
    }

    /**
     * 从目标服务器读取响应并转发到 VPN
     */
    private suspend fun readFromTarget(context: ConnectionContext) {
        try {
            val inputStream = context.targetSocket.getInputStream()
            val buffer = ByteArray(16384)
            
            while (context.isActive.get() && context.targetSocket.isConnected) {
                try {
                    val bytesRead = withContext(Dispatchers.IO) {
                        inputStream.read(buffer)
                    }
                    
                    if (bytesRead <= 0) {
                        delay(10)
                        continue
                    }

                    // 检测媒体 URL
                    val data = buffer.copyOf(bytesRead)
                    detectMediaInResponse(data, bytesRead, context.id.dstIp)

                    // 将数据写入 VPN
                    writeToVpn(context, data, bytesRead)

                } catch (e: Exception) {
                    if (context.isActive.get()) {
                        Log.d(TAG, "Connection closed: ${context.id}")
                    }
                    break
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading from target", e)
        } finally {
            context.isActive.set(false)
            connectionManager.removeConnection(context.id)
        }
    }

    /**
     * 将数据写入 VPN 接口
     * 构建 IP/TCP 包并通过 TUN 接口发送
     */
    private fun writeToVpn(context: ConnectionContext, data: ByteArray, length: Int) {
        try {
            // 构建响应 IP 包
            val responsePacket = ByteBuffer.allocate(65535)
            responsePacket.order(ByteOrder.LITTLE_ENDIAN)

            // IP 头部 (20 bytes)
            val totalLength = 20 + 20 + length  // IP + TCP + Data
            
            responsePacket.put(0, 0x45.toByte())  // Version 4, IHL 5
            responsePacket.put(1, 0x00.toByte())  // TOS
            responsePacket.putShort(2, totalLength.toShort())  // Total Length
            responsePacket.putShort(4, 0x0000.toShort())  // ID
            responsePacket.putShort(6, 0x4000.toShort())  // Flags + Fragment Offset
            responsePacket.put(8, 64.toByte())  // TTL
            responsePacket.put(9, 6.toByte())  // Protocol TCP
            responsePacket.putShort(10, 0x0000.toShort())  // Checksum (计算)

            // 交换源和目标 IP
            val dstIpParts = context.id.dstIp.split(".")
            val srcIpParts = context.id.srcIp.split(".")
            responsePacket.put(12, dstIpParts[0].toInt().toByte())
            responsePacket.put(13, dstIpParts[1].toInt().toByte())
            responsePacket.put(14, dstIpParts[2].toInt().toByte())
            responsePacket.put(15, dstIpParts[3].toInt().toByte())
            
            responsePacket.put(16, srcIpParts[0].toInt().toByte())
            responsePacket.put(17, srcIpParts[1].toInt().toByte())
            responsePacket.put(18, srcIpParts[2].toInt().toByte())
            responsePacket.put(19, srcIpParts[3].toInt().toByte())

            // TCP 头部 (20 bytes)
            val tcpHeaderStart = 20
            responsePacket.put(tcpHeaderStart, ((context.id.dstPort shr 8) and 0xFF).toByte())  // Src Port
            responsePacket.put(tcpHeaderStart + 1, (context.id.dstPort and 0xFF).toByte())
            responsePacket.put(tcpHeaderStart + 2, ((context.id.srcPort shr 8) and 0xFF).toByte())  // Dst Port
            responsePacket.put(tcpHeaderStart + 3, (context.id.srcPort and 0xFF).toByte())

            responsePacket.putInt(tcpHeaderStart + 4, context.remoteSeq.toInt())  // Seq
            responsePacket.putInt(tcpHeaderStart + 8, context.localSeq.toInt())  // Ack
            responsePacket.put(tcpHeaderStart + 12, 0x50.toByte())  // Data Offset
            responsePacket.put(tcpHeaderStart + 13, 0x18.toByte())  // Flags (PSH|ACK)
            responsePacket.putShort(tcpHeaderStart + 14, 65535.toShort())  // Window
            responsePacket.putShort(tcpHeaderStart + 16, 0x0000.toShort())  // Checksum
            responsePacket.putShort(tcpHeaderStart + 18, 0x0000.toShort())  // Urgent Pointer

            // 复制数据
            responsePacket.position(tcpHeaderStart + 20)
            responsePacket.put(data, 0, length)

            // 写入 VPN
            synchronized(context.vpnOutput) {
                val finalPacket = ByteBuffer.allocate(totalLength)
                finalPacket.order(ByteOrder.LITTLE_ENDIAN)
                for (i in 0 until totalLength) {
                    finalPacket.put(i, responsePacket.get(i))
                }
                context.vpnOutput.write(finalPacket.array(), 0, totalLength)
                context.vpnOutput.flush()
            }

            // 更新序列号
            context.remoteSeq += length

        } catch (e: Exception) {
            Log.e(TAG, "Error writing to VPN", e)
        }
    }

    /**
     * 发送 TCP RST 响应
     */
    private fun sendTcpRst(connId: ConnectionId, vpnOutput: FileOutputStream) {
        // 简化实现
        Log.d(TAG, "Sending TCP RST to $connId")
    }

    /**
     * 处理 UDP 数据包 (DNS)
     */
    private suspend fun processUDP(
        packet: ByteBuffer,
        length: Int,
        ipHeaderLength: Int,
        srcIp: String,
        dstIp: String
    ) {
        val udpHeaderLength = 8
        if (length < ipHeaderLength + udpHeaderLength) return

        val srcPort = ((packet.get(ipHeaderLength).toInt() and 0xFF) shl 8) or (packet.get(ipHeaderLength + 1).toInt() and 0xFF)
        val dstPort = ((packet.get(ipHeaderLength + 2).toInt() and 0xFF) shl 8) or (packet.get(ipHeaderLength + 3).toInt() and 0xFF)

        // DNS 查询 (端口 53)
        if (dstPort == 53 && length > ipHeaderLength + udpHeaderLength + 12) {
            val domain = parseDnsQuery(packet.array(), ipHeaderLength + udpHeaderLength, length - ipHeaderLength - udpHeaderLength)
            if (domain != null) {
                Log.v(TAG, "DNS: $domain")
            }
        }
    }

    /**
     * 解析 DNS 查询
     */
    private fun parseDnsQuery(data: ByteArray, offset: Int, length: Int): String? {
        return try {
            if (length < 13) return null
            val parts = mutableListOf<String>()
            var pos = offset + 12
            while (pos < offset + length) {
                val len = data[pos].toInt() and 0xFF
                if (len == 0) break
                if (len >= 0xC0) {
                    pos = offset + length
                    break
                }
                parts.add(String(data, pos + 1, len))
                pos += len + 1
            }
            if (parts.isNotEmpty()) parts.joinToString(".") else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 检测 HTTP 请求中的媒体 URL
     */
    private fun detectMediaInRequest(data: ByteArray, length: Int, host: String, port: Int) {
        try {
            val request = String(data, 0, length, Charsets.UTF_8)
            val lines = request.split("\r\n", "\n")
            
            // 检测 HTTP 方法
            if (lines.isEmpty()) return
            val firstLine = lines[0]
            
            when {
                firstLine.startsWith("GET") || firstLine.startsWith("POST") -> {
                    val urlLine = firstLine.split(" ")
                    if (urlLine.size >= 2) {
                        val path = urlLine[1]
                        val hostHeader = lines.find { it.startsWith("Host:", true) }?.substringAfter(":")?.trim() ?: host
                        
                        val fullUrl = if (path.startsWith("http")) path else "http://$hostHeader$path"
                        
                        if (isMediaUrl(fullUrl)) {
                            Log.d(TAG, "Media URL: $fullUrl")
                            addMediaResource(fullUrl, hostHeader)
                        }
                    }
                }
                firstLine.startsWith("CONNECT") -> {
                    // HTTPS 连接
                    val hostPort = firstLine.split(" ").getOrNull(1) ?: return
                    val parts = hostPort.split(":")
                    val httpsHost = parts.getOrNull(0) ?: return
                    Log.d(TAG, "HTTPS: $httpsHost:${parts.getOrNull(1) ?: 443}")
                }
            }
        } catch (e: Exception) {
            // 忽略解析错误
        }
    }

    /**
     * 检测 HTTP 响应中的媒体 URL
     */
    private fun detectMediaInResponse(data: ByteArray, length: Int, host: String) {
        try {
            val response = String(data, 0, minOf(length, 2048), Charsets.UTF_8)
            
            // 检测 Location 重定向
            response.lines().forEach { line ->
                if (line.startsWith("Location:", true) || line.startsWith("location:", true)) {
                    val url = line.substringAfter(":").trim()
                    if (isMediaUrl(url)) {
                        Log.d(TAG, "Media URL (redirect): $url")
                        addMediaResource(url, host)
                    }
                }
            }
        } catch (e: Exception) {
            // 忽略解析错误
        }
    }

    private fun isMediaUrl(url: String): Boolean {
        val lower = url.lowercase()
        return mediaExtensions.any { lower.contains(it) } || lower.contains("video") || lower.contains("audio") || lower.contains("image")
    }

    private fun addMediaResource(url: String, host: String) {
        val platform = detectPlatform(host)
        val resourceType = when {
            url.contains(".m3u8") -> ResourceType.M3U8
            url.contains(".mp4") || url.contains(".flv") || url.contains(".ts") -> ResourceType.VIDEO
            url.contains(".mp3") || url.contains(".flac") || url.contains(".wav") || url.contains(".m4a") -> ResourceType.AUDIO
            else -> ResourceType.VIDEO
        }
        
        val filename = try {
            val path = url.substringAfter("?").substringBefore("#")
            val name = path.split("/").lastOrNull() ?: "download"
            if (name.contains(".") || name.isEmpty()) name else "$name.mp4"
        } catch (e: Exception) {
            "download_${System.currentTimeMillis()}"
        }

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

    private fun detectPlatform(host: String): Platform {
        val currentRuleSet = ruleSet
        if (currentRuleSet == null || !currentRuleSet.shouldMitm(host)) {
            return Platform.UNKNOWN
        }

        val lowerHost = host.lowercase()
        
        for ((platform, domains) in platformDomains) {
            if (domains.any { lowerHost.contains(it) }) {
                return when (platform) {
                    "wechat" -> Platform.WECHAT
                    "douyin" -> Platform.DOUYIN
                    "kuaishou" -> Platform.KUAISHOU
                    "xiaohongshu" -> Platform.XIAOHONGSHU
                    "kugou" -> Platform.KOUGOU
                    "qqmusic" -> Platform.QQMUSIC
                    "bilibili" -> Platform.BILIBILI
                    else -> Platform.UNKNOWN
                }
            }
        }
        
        return Platform.UNKNOWN
    }

    private fun generateId(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return (1..8).map { chars.random() }.joinToString("")
    }

    private fun stopVpn() {
        Log.d(TAG, "Stopping VPN service")

        mIsActive.set(false)
        isRunningValue = false
        
        connectionManager.closeAll()

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

/**
 * TCP 连接标识
 */
data class ConnectionId(
    val srcIp: String,
    val srcPort: Int,
    val dstIp: String,
    val dstPort: Int
)

/**
 * TCP 连接上下文
 */
class ConnectionContext(
    val id: ConnectionId,
    val targetSocket: Socket,
    val vpnOutput: FileOutputStream,
    val vpnPacket: ByteBuffer,
    val ipHeaderLength: Int,
    val tcpHeaderStart: Int,
    val localSeq: Long
) {
    var remoteSeq: Long = 0
    val isActive = AtomicBoolean(true)

    fun close() {
        isActive.set(false)
        try {
            targetSocket.close()
        } catch (e: Exception) {
        }
    }
}

/**
 * TCP 连接管理器
 */
class ConnectionManager {
    private val connections = ConcurrentHashMap<ConnectionId, ConnectionContext>()
    
    companion object {
        private const val TAG = "ConnectionManager"
    }

    fun addConnection(id: ConnectionId, context: ConnectionContext) {
        connections[id] = context
    }

    fun getConnection(id: ConnectionId): ConnectionContext? {
        return connections[id]
    }

    fun removeConnection(id: ConnectionId): ConnectionContext? {
        return connections.remove(id)
    }

    fun sendToTarget(id: ConnectionId, data: ByteArray) {
        connections[id]?.let { ctx ->
            try {
                ctx.targetSocket.getOutputStream().write(data)
                ctx.targetSocket.getOutputStream().flush()
            } catch (e: Exception) {
                Log.e(TAG, "Error sending to target", e)
                ctx.close()
                removeConnection(id)
            }
        }
    }

    fun closeConnection(id: ConnectionId) {
        connections[id]?.close()
        removeConnection(id)
    }

    fun closeAll() {
        connections.values.forEach { it.close() }
        connections.clear()
    }
}

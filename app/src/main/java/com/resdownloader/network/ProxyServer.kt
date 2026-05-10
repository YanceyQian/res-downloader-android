package com.resdownloader.network

import android.util.Log
import com.resdownloader.data.model.Platform
import com.resdownloader.data.model.ResourceInfo
import com.resdownloader.data.model.ResourceType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class ProxyServer(private val port: Int = 8899) {

    companion object {
        private const val TAG = "ProxyServer"
    }

    private val serverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var serverSocket: ServerSocket? = null
    private val isRunning = AtomicBoolean(false)
    private val activeConnections = ConcurrentHashMap<String, Socket>()

    var onResourceFound: ((ResourceInfo) -> Unit)? = null

    fun start() {
        if (isRunning.get()) return

        serverScope.launch {
            try {
                Log.d(TAG, "Starting proxy server on port $port")
                serverSocket = ServerSocket(port)
                isRunning.set(true)

                while (isRunning.get()) {
                    try {
                        val clientSocket = serverSocket?.accept() ?: continue
                        val connectionId = System.currentTimeMillis().toString()
                        activeConnections[connectionId] = clientSocket

                        Log.d(TAG, "New connection accepted: $connectionId")
                        handleConnection(connectionId, clientSocket)
                    } catch (e: IOException) {
                        if (isRunning.get()) {
                            Log.e(TAG, "Error accepting connection", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Proxy server error", e)
            }
        }
    }

    fun stop() {
        Log.d(TAG, "Stopping proxy server")
        isRunning.set(false)
        serverSocket?.close()

        activeConnections.values.forEach { socket ->
            try {
                socket.close()
            } catch (e: IOException) {
                Log.e(TAG, "Error closing connection", e)
            }
        }
        activeConnections.clear()
    }

    fun isActive(): Boolean = isRunning.get()

    private fun handleConnection(connectionId: String, clientSocket: Socket) {
        serverScope.launch {
            try {
                val clientInput = clientSocket.getInputStream()
                val clientOutput = clientSocket.getOutputStream()

                val requestLine = readLine(clientInput) ?: return@launch
                Log.d(TAG, "Request line: $requestLine")

                if (!requestLine.startsWith("CONNECT")) {
                    handleHttpConnection(
                        clientSocket,
                        clientInput,
                        clientOutput,
                        requestLine
                    )
                } else {
                    handleHttpsConnection(
                        clientSocket,
                        clientInput,
                        clientOutput,
                        requestLine
                    )
                }

            } catch (e: Exception) {
                Log.e(TAG, "Connection handling error", e)
            } finally {
                try {
                    clientSocket.close()
                } catch (e: IOException) {
                    Log.e(TAG, "Error closing socket", e)
                }
                activeConnections.remove(connectionId)
            }
        }
    }

    private fun handleHttpConnection(
        clientSocket: Socket,
        clientInput: InputStream,
        clientOutput: OutputStream,
        requestLine: String
    ) {
        serverScope.launch {
            try {
                val parts = requestLine.split(" ")
                if (parts.size < 3) return@launch

                val method = parts[0]
                val url = parts[1]
                val version = parts[2]

                val headers = mutableMapOf<String, String>()
                var line: String?
                while (readLine(clientInput).also { line = it } != null && line!!.isNotEmpty()) {
                    val headerParts = line!!.split(":", limit = 2)
                    if (headerParts.size >= 2) {
                        headers[headerParts[0].trim()] = headerParts[1].trim()
                    }
                }

                val host = headers["Host"] ?: ""

                Log.d(TAG, "HTTP Request: $method $url $version")
                Log.d(TAG, "Host: $host")

                if (isMediaUrl(url)) {
                    val resourceInfo = extractResource(url, host, headers)
                    Log.d(TAG, "Media resource found: ${resourceInfo.filename}")
                    onResourceFound?.invoke(resourceInfo)
                }

                forwardHttpRequest(method, url, version, headers, clientInput, clientOutput)

            } catch (e: Exception) {
                Log.e(TAG, "HTTP connection error", e)
            }
        }
    }

    private fun handleHttpsConnection(
        clientSocket: Socket,
        clientInput: InputStream,
        clientOutput: OutputStream,
        requestLine: String
    ) {
        serverScope.launch {
            try {
                val hostPort = requestLine.split(" ")[1]
                val host = hostPort.split(":")[0]

                Log.d(TAG, "HTTPS Connection to: $host")

                clientOutput.write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray())
                clientOutput.flush()

                val targetSocket = try {
                    val portIndex = hostPort.indexOf(":")
                    val targetPort = if (portIndex > 0) hostPort.substring(portIndex + 1).toInt() else 443
                    Socket(host, targetPort)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to connect to target", e)
                    return@launch
                }

                val targetInput = targetSocket.getInputStream()
                val targetOutput = targetSocket.getOutputStream()

                val clientToTarget = launch { copyStream(clientInput, targetOutput) }
                val targetToClient = launch { copyStream(targetInput, clientOutput) }

                clientToTarget.join()
                targetToClient.cancel()

                targetSocket.close()

            } catch (e: Exception) {
                Log.e(TAG, "HTTPS connection error", e)
            }
        }
    }

    private fun forwardHttpRequest(
        method: String,
        url: String,
        version: String,
        headers: Map<String, String>,
        clientInput: InputStream,
        clientOutput: OutputStream
    ) {
        serverScope.launch {
            try {
                val host = headers["Host"] ?: return@launch
                val portIndex = host.indexOf(":")
                val targetPort = if (portIndex > 0) host.substring(portIndex + 1).toInt() else 80
                val targetHost = if (portIndex > 0) host.substring(0, portIndex) else host

                val targetSocket = Socket(targetHost, targetPort)
                val targetInput = targetSocket.getInputStream()
                val targetOutput = targetSocket.getOutputStream()

                val requestBuilder = StringBuilder()
                requestBuilder.append("$method $url $version\r\n")
                headers.forEach { (key, value) ->
                    requestBuilder.append("$key: $value\r\n")
                }
                requestBuilder.append("\r\n")

                targetOutput.write(requestBuilder.toString().toByteArray())

                val contentLength = headers["Content-Length"]?.toLongOrNull()
                if (contentLength != null && contentLength > 0) {
                    copyStream(clientInput, targetOutput, contentLength)
                }

                val response = StringBuilder()
                var responseLine: String?

                responseLine = readLine(targetInput) ?: return@launch
                response.append(responseLine).append("\r\n")

                val responseHeaders = mutableMapOf<String, String>()

                while (readLine(targetInput).also { responseLine = it } != null && responseLine!!.isNotEmpty()) {
                    response.append(responseLine).append("\r\n")
                    val headerParts = responseLine!!.split(":", limit = 2)
                    if (headerParts.size >= 2) {
                        responseHeaders[headerParts[0].trim()] = headerParts[1].trim()
                    }
                }

                response.append("\r\n")

                val contentType = responseHeaders["Content-Type"] ?: ""
                if (isMediaContentType(contentType) || isMediaUrl(url)) {
                    val resourceInfo = extractResource(url, host, headers)
                    val contentLengthValue = responseHeaders["Content-Length"]?.toLongOrNull() ?: 0
                    val completeResource = resourceInfo.copy(size = contentLengthValue)
                    Log.d(TAG, "Media response found: ${resourceInfo.filename}")
                    onResourceFound?.invoke(completeResource)
                }

                clientOutput.write(response.toString().toByteArray())

                copyStream(targetInput, clientOutput)

                targetSocket.close()

            } catch (e: Exception) {
                Log.e(TAG, "Forward request error", e)
            }
        }
    }

    private fun copyStream(input: InputStream, output: OutputStream, limit: Long? = null) {
        val buffer = ByteArray(8192)
        var bytesRead: Int
        var totalRead = 0L

        try {
            while (true) {
                val toRead = if (limit != null) {
                    val remaining = limit - totalRead
                    if (remaining <= 0) break
                    minOf(buffer.size.toLong(), remaining).toInt()
                } else {
                    buffer.size
                }

                bytesRead = input.read(buffer, 0, toRead)
                if (bytesRead == -1) break

                output.write(buffer, 0, bytesRead)
                totalRead += bytesRead
                output.flush()
            }
        } catch (e: Exception) {
        }
    }

    private fun readLine(input: InputStream): String? {
        val buffer = StringBuilder()
        var byte: Int

        try {
            while (input.read().also { byte = it } != -1) {
                if (byte == '\r'.code) {
                    continue
                }
                if (byte == '\n'.code) {
                    return buffer.toString()
                }
                buffer.append(byte.toChar())
            }
        } catch (e: IOException) {
            return null
        }

        return if (buffer.isNotEmpty()) buffer.toString() else null
    }

    private fun isMediaUrl(url: String): Boolean {
        val lowerUrl = url.lowercase()
        val extensions = listOf(
            ".mp4", ".m3u8", ".ts", ".flv", ".webm", ".mkv", ".avi", ".mov",
            ".mp3", ".flac", ".wav", ".aac", ".ogg", ".m4a", ".wma",
            ".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp", ".svg", ".ico",
            ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx",
            ".zip", ".rar", ".7z", ".tar", ".gz"
        )
        return extensions.any { lowerUrl.contains(it) }
    }

    private fun isMediaContentType(contentType: String): Boolean {
        val lowerType = contentType.lowercase()
        val mediaTypes = listOf(
            "video/", "audio/", "image/",
            "application/x-mpegurl", "application/vnd.apple.mpegurl",
            "application/pdf", "application/zip", "application/rar"
        )
        return mediaTypes.any { lowerType.contains(it) }
    }

    private fun extractResource(url: String, host: String, headers: Map<String, String>): ResourceInfo {
        val type = detectType(url)
        val platform = detectPlatform(host)
        val filename = extractFilename(url)

        return ResourceInfo(
            id = System.currentTimeMillis().toString(),
            url = url,
            type = type,
            platform = platform,
            filename = filename,
            size = 0,
            timestamp = System.currentTimeMillis()
        )
    }

    private fun detectType(url: String): ResourceType {
        val lowerUrl = url.lowercase()
        return when {
            lowerUrl.contains(".m3u8") -> ResourceType.M3U8
            lowerUrl.contains(".mp4") || lowerUrl.contains(".flv") || 
            lowerUrl.contains(".webm") || lowerUrl.contains(".mkv") ||
            lowerUrl.contains(".avi") || lowerUrl.contains(".mov") ||
            lowerUrl.contains(".ts") -> ResourceType.VIDEO
            lowerUrl.contains(".mp3") || lowerUrl.contains(".flac") || 
            lowerUrl.contains(".wav") || lowerUrl.contains(".aac") ||
            lowerUrl.contains(".ogg") || lowerUrl.contains(".m4a") ||
            lowerUrl.contains(".wma") -> ResourceType.AUDIO
            lowerUrl.contains(".jpg") || lowerUrl.contains(".jpeg") || 
            lowerUrl.contains(".png") || lowerUrl.contains(".gif") ||
            lowerUrl.contains(".webp") || lowerUrl.contains(".bmp") ||
            lowerUrl.contains(".svg") || lowerUrl.contains(".ico") -> ResourceType.IMAGE
            lowerUrl.contains(".pdf") -> ResourceType.PDF
            lowerUrl.contains(".doc") || lowerUrl.contains(".docx") -> ResourceType.DOC
            lowerUrl.contains(".xls") || lowerUrl.contains(".xlsx") -> ResourceType.XLS
            else -> ResourceType.STREAM
        }
    }

    private fun detectPlatform(host: String): Platform {
        val lowerHost = host.lowercase()
        return when {
            lowerHost.contains("weixin.qq.com") || lowerHost.contains("wechat.com") ||
            lowerHost.contains("wxtingyun.com") -> Platform.WECHAT
            lowerHost.contains("douyin.com") || lowerHost.contains("iesdouyin.com") ||
            lowerHost.contains("amemv.com") || lowerHost.contains("pstatp.com") -> Platform.DOUYIN
            lowerHost.contains("kuaishou.com") || lowerHost.contains("kspkg.com") ||
            lowerHost.contains("gifshow.com") -> Platform.KUAISHOU
            lowerHost.contains("xiaohongshu.com") || lowerHost.contains("xhslink.com") ||
            lowerHost.contains("xhscdn.com") -> Platform.XIAOHONGSHU
            lowerHost.contains("kugou.com") || lowerHost.contains("kgimg.com") -> Platform.KOUGOU
            lowerHost.contains("qq.com") && (lowerHost.contains("music") || lowerHost.contains("y.qq")) -> Platform.QQMUSIC
            else -> Platform.UNKNOWN
        }
    }

    private fun extractFilename(url: String): String {
        val path = url.split("?")[0]
        val parts = path.split("/")
        val filename = parts.lastOrNull() ?: "download"
        return if (filename.isNotEmpty() && filename.contains(".")) filename else "download"
    }
}

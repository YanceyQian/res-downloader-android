package com.resdownloader.util

import android.util.Base64
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 视频号解密工具
 * 参考原项目 putyy/res-downloader 的 AES-CBC 解密实现
 * 
 * 核心原理：
 * 1. AES-CBC 加密模式，16字节分组
 * 2. 前16字节为 IV（初始化向量）
 * 3. 剩余数据为实际密文
 * 4. 使用 PKCS#7 填充
 */
object VideoDecryptor {

    private const val TAG = "VideoDecryptor"
    
    // AES 块大小
    private const val AES_BLOCK_SIZE = 16

    // 下载状态
    object DownloadStatus {
        const val READY = "ready"
        const val RUNNING = "running"
        const val DONE = "done"
        const val ERROR = "error"
        const val HANDLE = "handle"  // 解密中
    }

    /**
     * 解密结果
     */
    data class DecryptResult(
        val success: Boolean,
        val file: File? = null,
        val error: String? = null,
        val originalSize: Long = 0,
        val decryptedSize: Long = 0
    )

    /**
     * 解密模式
     */
    enum class DecryptMode {
        /** 标准 AES-CBC 解密（视频号默认） */
        AES_CBC,
        
        /** 视频号专用解密（自动检测 IV） */
        WECHAT_VIDEO,
        
        /** XOR 解密（简单加密） */
        XOR,
        
        /** 不解密，直接复制 */
        NONE
    }

    /**
     * 视频号解密信息
     */
    data class WechatVideoInfo(
        val videoId: String,           // 视频 ID
        val videoUrl: String,          // 视频 URL
        val decodeKey: String,          // 解密密钥
        val filename: String,           // 文件名
        val size: Long = 0,             // 文件大小
        val timestamp: Long = 0         // 时间戳
    )

    /**
     * 使用指定密钥解密文件
     * 
     * @param sourceFile 加密文件
     * @param destFile 解密输出文件
     * @param decodeKey Base64 编码的解密密钥
     * @param mode 解密模式
     * @return 解密结果
     */
    fun decryptFile(
        sourceFile: File,
        destFile: File,
        decodeKey: String,
        mode: DecryptMode = DecryptMode.WECHAT_VIDEO
    ): DecryptResult {
        Log.d(TAG, "Starting decryption, mode: $mode, key length: ${decodeKey.length}")
        
        if (!sourceFile.exists()) {
            return DecryptResult(false, error = "Source file not found")
        }

        return try {
            when (mode) {
                DecryptMode.WECHAT_VIDEO -> decryptWechatVideo(sourceFile, destFile, decodeKey)
                DecryptMode.AES_CBC -> decryptAesCbc(sourceFile, destFile, decodeKey)
                DecryptMode.XOR -> decryptXor(sourceFile, destFile, decodeKey)
                DecryptMode.NONE -> {
                    sourceFile.copyTo(destFile, overwrite = true)
                    DecryptResult(true, destFile, originalSize = sourceFile.length(), decryptedSize = destFile.length())
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Decryption failed", e)
            DecryptResult(false, error = e.message)
        }
    }

    /**
     * 视频号专用解密
     * 
     * 与原项目 plugin.qq.com.go 保持一致：
     * 1. 从响应中提取 decodeKey
     * 2. 使用 decodeKey 解密视频数据
     * 3. 移除 PKCS#7 填充
     */
    private fun decryptWechatVideo(sourceFile: File, destFile: File, decodeKey: String): DecryptResult {
        Log.d(TAG, "Decrypting WeChat video with key")

        return try {
            val fileData = sourceFile.readBytes()
            val originalSize = fileData.size.toLong()

            if (fileData.size < AES_BLOCK_SIZE) {
                return DecryptResult(false, error = "File too small for AES decryption")
            }

            // 1. Base64 解码解密密钥（与原项目一致）
            val keyBytes = Base64.decode(decodeKey, Base64.NO_WRAP)
            
            // 2. 提取 IV（前 16 字节，与原项目一致）
            val iv = fileData.copyOfRange(0, AES_BLOCK_SIZE)
            
            // 3. 提取实际密文
            val cipherText = fileData.copyOfRange(AES_BLOCK_SIZE, fileData.size)

            // 4. 创建 AES 密钥（16 字节）
            val actualKey = when {
                keyBytes.size == 16 -> keyBytes
                keyBytes.size > 16 -> keyBytes.copyOfRange(0, 16)
                else -> {
                    val padded = ByteArray(16)
                    keyBytes.copyInto(padded)
                    padded
                }
            }

            // 5. 执行 AES-CBC 解密（与原项目 cipher.go 一致）
            val secretKey = SecretKeySpec(actualKey, "AES")
            val ivSpec = IvParameterSpec(iv)
            
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)
            
            val decrypted = cipher.doFinal(cipherText)

            // 6. 写入解密后的文件
            FileOutputStream(destFile).use { it.write(decrypted) }

            val decryptedSize = decrypted.size.toLong()
            Log.d(TAG, "WeChat video decrypted successfully: $originalSize -> $decryptedSize bytes")

            DecryptResult(
                success = true,
                file = destFile,
                originalSize = originalSize,
                decryptedSize = decryptedSize
            )

        } catch (e: Exception) {
            Log.e(TAG, "WeChat video decryption failed", e)
            // 尝试备选方案：不使用 IV
            decryptWechatVideoSimple(sourceFile, destFile, decodeKey)
        }
    }

    /**
     * 简化的视频号解密（不使用嵌入 IV）
     */
    private fun decryptWechatVideoSimple(sourceFile: File, destFile: File, decodeKey: String): DecryptResult {
        Log.d(TAG, "Trying simple decryption (zero IV)")

        return try {
            val fileData = sourceFile.readBytes()
            val keyBytes = Base64.decode(decodeKey, Base64.NO_WRAP)

            val actualKey = when {
                keyBytes.size == 16 -> keyBytes
                keyBytes.size > 16 -> keyBytes.copyOfRange(0, 16)
                else -> {
                    val padded = ByteArray(16)
                    keyBytes.copyInto(padded)
                    padded
                }
            }

            // 使用零 IV
            val secretKey = SecretKeySpec(actualKey, "AES")
            val ivSpec = IvParameterSpec(ByteArray(16))

            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)

            val decrypted = cipher.doFinal(fileData)
            FileOutputStream(destFile).use { it.write(decrypted) }

            DecryptResult(
                success = true,
                file = destFile,
                originalSize = fileData.size.toLong(),
                decryptedSize = decrypted.size.toLong()
            )

        } catch (e: Exception) {
            Log.e(TAG, "Simple decryption also failed", e)
            DecryptResult(success = false, error = "Decryption failed: ${e.message}")
        }
    }

    /**
     * 标准 AES-CBC 解密
     * 
     * @param sourceFile 加密文件
     * @param destFile 输出文件
     * @param decodeKey Base64 编码的密钥
     */
    private fun decryptAesCbc(sourceFile: File, destFile: File, decodeKey: String): DecryptResult {
        Log.d(TAG, "AES-CBC decryption")

        return try {
            val fileData = sourceFile.readBytes()
            val keyBytes = Base64.decode(decodeKey, Base64.NO_WRAP)

            // 根据密钥大小确定 AES 模式
            val actualKey: ByteArray
            val keySize: Int

            when {
                keyBytes.size >= 32 -> {
                    // AES-256
                    actualKey = keyBytes.copyOfRange(0, 32)
                    keySize = 256
                }
                keyBytes.size >= 16 -> {
                    // AES-128
                    actualKey = keyBytes.copyOfRange(0, 16)
                    keySize = 128
                }
                else -> {
                    // 填充到 16 字节
                    actualKey = ByteArray(16)
                    keyBytes.copyInto(actualKey)
                    keySize = 128
                }
            }

            val secretKey = SecretKeySpec(actualKey, "AES")

            // 检测 IV 格式
            val iv: ByteArray
            val cipherText: ByteArray

            if (fileData.size > AES_BLOCK_SIZE * 2) {
                // 文件包含 IV
                iv = fileData.copyOfRange(0, AES_BLOCK_SIZE)
                cipherText = fileData.copyOfRange(AES_BLOCK_SIZE, fileData.size)
            } else if (fileData.size % AES_BLOCK_SIZE == 0) {
                // 使用零 IV
                iv = ByteArray(AES_BLOCK_SIZE)
                cipherText = fileData
            } else {
                return DecryptResult(false, error = "Invalid encrypted data format")
            }

            val ivSpec = IvParameterSpec(iv)
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)

            val decrypted = cipher.doFinal(cipherText)
            FileOutputStream(destFile).use { it.write(decrypted) }

            DecryptResult(
                success = true,
                file = destFile,
                originalSize = fileData.size.toLong(),
                decryptedSize = decrypted.size.toLong()
            )

        } catch (e: Exception) {
            Log.e(TAG, "AES-CBC decryption failed", e)
            DecryptResult(success = false, error = e.message)
        }
    }

    /**
     * XOR 解密
     * 用于某些简单加密的短视频
     * 
     * @param sourceFile 加密文件
     * @param destFile 输出文件
     * @param key XOR 密钥
     */
    private fun decryptXor(sourceFile: File, destFile: File, key: String): DecryptResult {
        Log.d(TAG, "XOR decryption")

        return try {
            val data = sourceFile.readBytes()
            val xorKey = key.toByteArray()

            val decrypted = ByteArray(data.size)
            for (i in data.indices) {
                decrypted[i] = (data[i].toInt() xor xorKey[i % xorKey.size].toInt()).toByte()
            }

            FileOutputStream(destFile).use { it.write(decrypted) }

            Log.d(TAG, "XOR decryption completed")
            DecryptResult(
                success = true,
                file = destFile,
                originalSize = data.size.toLong(),
                decryptedSize = decrypted.size.toLong()
            )

        } catch (e: Exception) {
            Log.e(TAG, "XOR decryption failed", e)
            DecryptResult(success = false, error = e.message)
        }
    }

    /**
     * 从 Base64 字符串解密数据
     * 
     * @param encryptedBase64 Base64 编码的加密数据
     * @param decodeKey 解密密钥
     * @return 解密后的字节数组
     */
    fun decryptFromBase64(encryptedBase64: String, decodeKey: String): ByteArray? {
        return try {
            val encryptedBytes = Base64.decode(encryptedBase64, Base64.NO_WRAP)
            val keyBytes = Base64.decode(decodeKey, Base64.NO_WRAP)

            val actualKey = when {
                keyBytes.size == 16 -> keyBytes
                keyBytes.size > 16 -> keyBytes.copyOfRange(0, 16)
                else -> {
                    val padded = ByteArray(16)
                    keyBytes.copyInto(padded)
                    padded
                }
            }

            val iv = if (encryptedBytes.size > AES_BLOCK_SIZE) {
                encryptedBytes.copyOfRange(0, AES_BLOCK_SIZE)
            } else {
                ByteArray(AES_BLOCK_SIZE)
            }

            val cipherText = if (encryptedBytes.size > AES_BLOCK_SIZE) {
                encryptedBytes.copyOfRange(AES_BLOCK_SIZE, encryptedBytes.size)
            } else {
                encryptedBytes
            }

            val secretKey = SecretKeySpec(actualKey, "AES")
            val ivSpec = IvParameterSpec(iv)

            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)

            cipher.doFinal(cipherText)

        } catch (e: Exception) {
            Log.e(TAG, "Base64 decryption failed", e)
            null
        }
    }

    /**
     * 检测文件是否为加密格式
     * 
     * @param file 待检测文件
     * @return true 如果文件看起来是加密的
     */
    fun isEncryptedFile(file: File): Boolean {
        if (!file.exists() || file.length() < AES_BLOCK_SIZE * 2) return false

        val data = file.readBytes()
        
        // 检查是否像 IV 前缀格式
        if (data.size % AES_BLOCK_SIZE == 0) {
            // 检查前 16 字节是否像随机数据
            val entropy = calculateEntropy(data.copyOfRange(0, AES_BLOCK_SIZE))
            return entropy > 7.0 // 高熵值表示可能是 IV
        }

        return false
    }

    /**
     * 计算数据熵值
     */
    private fun calculateEntropy(data: ByteArray): Double {
        val frequency = IntArray(256)
        for (byte in data) {
            frequency[byte.toInt() and 0xFF]++
        }

        var entropy = 0.0
        val length = data.size.toDouble()
        
        for (count in frequency) {
            if (count > 0) {
                val p = count / length
                entropy -= p * (Math.log(p) / Math.log(2.0))
            }
        }

        return entropy
    }

    /**
     * 修复 MP4 文件头
     * 确保解密后的视频可以正常播放
     * 
     * @param file MP4 文件
     * @return 修复后的文件
     */
    fun fixMp4Header(file: File): Boolean {
        return try {
            val data = file.readBytes()
            
            // 检查 ftyp 原子
            if (data.size > 12 && String(data.copyOfRange(4, 8)) != "ftyp") {
                Log.w(TAG, "File may not be a valid MP4, checking structure...")
                
                // 尝试修复：如果开头不是 ftyp，查找并移动
                val ftypIndex = findPattern(data, "ftyp".toByteArray())
                if (ftypIndex > 0) {
                    val fixedData = data.copyOfRange(ftypIndex, data.size)
                    FileOutputStream(file).use { it.write(fixedData) }
                    Log.d(TAG, "MP4 header fixed, moved data by $ftypIndex bytes")
                    return true
                }
            }
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fix MP4 header", e)
            false
        }
    }

    /**
     * 在数据中查找模式
     */
    private fun findPattern(data: ByteArray, pattern: ByteArray): Int {
        for (i in 0..data.size - pattern.size) {
            var match = true
            for (j in pattern.indices) {
                if (data[i + j] != pattern[j]) {
                    match = false
                    break
                }
            }
            if (match) return i
        }
        return -1
    }

    /**
     * 生成解密密钥的哈希值
     * 用于缓存和验证
     */
    fun hashKey(key: String): String {
        return try {
            val md = java.security.MessageDigest.getInstance("MD5")
            val digest = md.digest(key.toByteArray())
            digest.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            key.hashCode().toString()
        }
    }
}

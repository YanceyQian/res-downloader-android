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
 * 支持多种加密格式：
 * - AES-128-CBC
 * - AES-256-CBC
 * - 自定义 XOR
 * - 视频号专用加密
 */
object VideoDecryptor {

    private const val TAG = "VideoDecryptor"

    // 视频号默认密钥
    private const val WX_DEFAULT_KEY = "QGYLg7aT8p9b3xN2m5k1j6h4f0d9e8c7"

    /**
     * 解密结果
     */
    data class DecryptResult(
        val success: Boolean,
        val file: File? = null,
        val error: String? = null
    )

    /**
     * 解密模式
     */
    enum class DecryptMode {
        AES_128_CBC,
        AES_256_CBC,
        WX_VIDEO,
        XOR,
        NONE
    }

    /**
     * 自动检测并解密视频文件
     */
    fun decryptFile(
        sourceFile: File,
        destFile: File,
        key: String? = null,
        mode: DecryptMode = DecryptMode.AES_128_CBC
    ): DecryptResult {
        return try {
            Log.d(TAG, "Starting decryption, mode: $mode")

            when (mode) {
                DecryptMode.WX_VIDEO -> decryptWxVideo(sourceFile, destFile, key)
                DecryptMode.XOR -> decryptXor(sourceFile, destFile, key)
                DecryptMode.AES_128_CBC -> decryptAes(sourceFile, destFile, key, 128)
                DecryptMode.AES_256_CBC -> decryptAes(sourceFile, destFile, key, 256)
                DecryptMode.NONE -> {
                    sourceFile.copyTo(destFile, overwrite = true)
                    DecryptResult(true, destFile)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Decryption failed", e)
            DecryptResult(false, error = e.message)
        }
    }

    /**
     * 解密视频号专用格式
     * 视频号视频使用特殊的加密方式：
     * 1. 前16字节为IV
     * 2. 剩余数据使用AES-128-CBC解密
     */
    private fun decryptWxVideo(source: File, dest: File, key: String?): DecryptResult {
        Log.d(TAG, "Decrypting WeChat video...")

        return try {
            val data = source.readBytes()

            if (data.size < 16) {
                return DecryptResult(false, error = "File too small")
            }

            // 提取 IV（前16字节）
            val iv = data.copyOfRange(0, 16)
            val encryptedData = data.copyOfRange(16, data.size)

            // 使用提供的密钥或默认密钥
            val actualKey = (key ?: WX_DEFAULT_KEY).toByteArray()

            // 如果密钥不是16字节，进行处理
            val keyBytes = when {
                actualKey.size == 16 -> actualKey
                actualKey.size > 16 -> actualKey.copyOf(16)
                else -> {
                    val padded = ByteArray(16)
                    actualKey.copyInto(padded)
                    padded
                }
            }

            val secretKey = SecretKeySpec(keyBytes, "AES")
            val ivSpec = IvParameterSpec(iv)

            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)

            val decrypted = cipher.doFinal(encryptedData)

            FileOutputStream(dest).use { it.write(decrypted) }

            Log.d(TAG, "WeChat video decrypted successfully")
            DecryptResult(true, dest)

        } catch (e: Exception) {
            Log.e(TAG, "WeChat video decryption failed", e)
            // 尝试不使用IV的解密
            decryptWxVideoSimple(source, dest, key)
        }
    }

    /**
     * 简化版视频号解密
     */
    private fun decryptWxVideoSimple(source: File, dest: File, key: String?): DecryptResult {
        return try {
            val data = source.readBytes()
            val actualKey = (key ?: WX_DEFAULT_KEY).toByteArray()

            val keyBytes = when {
                actualKey.size == 16 -> actualKey
                actualKey.size > 16 -> actualKey.copyOf(16)
                else -> {
                    val padded = ByteArray(16)
                    actualKey.copyInto(padded)
                    padded
                }
            }

            // 使用零IV尝试解密
            val secretKey = SecretKeySpec(keyBytes, "AES")
            val ivSpec = IvParameterSpec(ByteArray(16))

            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)

            val decrypted = cipher.doFinal(data)

            FileOutputStream(dest).use { it.write(decrypted) }

            Log.d(TAG, "Simple decryption succeeded")
            DecryptResult(true, dest)

        } catch (e: Exception) {
            Log.e(TAG, "Simple decryption failed", e)
            DecryptResult(false, error = e.message)
        }
    }

    /**
     * AES 解密
     */
    private fun decryptAes(source: File, dest: File, key: String?, keySize: Int): DecryptResult {
        Log.d(TAG, "AES decryption, keySize: $keySize")

        return try {
            val data = source.readBytes()
            val actualKey = key ?: WX_DEFAULT_KEY

            // 根据密钥大小处理
            val keyBytes = when (keySize) {
                128 -> {
                    val keyByteArray = actualKey.toByteArray()
                    when {
                        keyByteArray.size == 16 -> keyByteArray
                        keyByteArray.size > 16 -> keyByteArray.copyOf(16)
                        else -> {
                            val padded = ByteArray(16)
                            keyByteArray.copyInto(padded)
                            padded
                        }
                    }
                }
                256 -> {
                    val keyByteArray = actualKey.toByteArray()
                    when {
                        keyByteArray.size == 32 -> keyByteArray
                        keyByteArray.size > 32 -> keyByteArray.copyOf(32)
                        keyByteArray.size >= 16 -> {
                            // 扩展到32字节
                            val expanded = ByteArray(32)
                            keyByteArray.copyInto(expanded)
                            // 复制后半部分
                            keyByteArray.copyInto(expanded, 16, 0, minOf(16, keyByteArray.size))
                            expanded
                        }
                        else -> {
                            val padded = ByteArray(32)
                            keyByteArray.copyInto(padded)
                            padded
                        }
                    }
                }
                else -> throw IllegalArgumentException("Invalid key size: $keySize")
            }

            val secretKey = SecretKeySpec(keyBytes, "AES")

            // 检查是否有嵌入的IV
            val iv: ByteArray
            val encryptedData: ByteArray

            if (data.size >= 32 && looksLikeIvPrefix(data)) {
                // 从文件头部读取IV
                iv = data.copyOfRange(0, 16)
                encryptedData = data.copyOfRange(16, data.size)
                Log.d(TAG, "Using embedded IV")
            } else if (data.size % 16 == 0) {
                // 假设整个文件都是加密数据，使用零IV
                iv = ByteArray(16)
                encryptedData = data
                Log.d(TAG, "Using zero IV")
            } else {
                return DecryptResult(false, error = "Invalid encrypted data format")
            }

            val ivSpec = IvParameterSpec(iv)
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)

            val decrypted = cipher.doFinal(encryptedData)

            FileOutputStream(dest).use { it.write(decrypted) }

            Log.d(TAG, "AES decryption completed")
            DecryptResult(true, dest)

        } catch (e: Exception) {
            Log.e(TAG, "AES decryption failed", e)
            DecryptResult(false, error = e.message)
        }
    }

    /**
     * XOR 解密
     * 用于某些简单加密的短视频
     */
    private fun decryptXor(source: File, dest: File, key: String?): DecryptResult {
        Log.d(TAG, "XOR decryption")

        return try {
            val data = source.readBytes()
            val xorKey = (key ?: "tencent").toByteArray()

            val decrypted = ByteArray(data.size)
            for (i in data.indices) {
                decrypted[i] = (data[i].toInt() xor xorKey[i % xorKey.size].toInt()).toByte()
            }

            FileOutputStream(dest).use { it.write(decrypted) }

            Log.d(TAG, "XOR decryption completed")
            DecryptResult(true, dest)

        } catch (e: Exception) {
            Log.e(TAG, "XOR decryption failed", e)
            DecryptResult(false, error = e.message)
        }
    }

    /**
     * 检测是否像IV前缀格式
     */
    private fun looksLikeIvPrefix(data: ByteArray): Boolean {
        if (data.size < 32) return false

        // 检查前16字节是否像随机数据（IV应该是随机的）
        // 简单检查：如果前16字节与后16字节有明显不同，可能是IV
        var diffCount = 0
        for (i in 0 until 16) {
            if (data[i] != data[i + 16]) diffCount++
        }

        return diffCount > 8
    }

    /**
     * 从 Base64 字符串解密
     */
    fun decryptFromBase64(encryptedBase64: String, key: String): ByteArray? {
        return try {
            val encryptedBytes = Base64.decode(encryptedBase64, Base64.NO_WRAP)
            val keyBytes = key.toByteArray(charset("UTF-8"))

            val keyFinal = when {
                keyBytes.size == 16 -> keyBytes
                keyBytes.size > 16 -> keyBytes.copyOf(16)
                else -> {
                    val padded = ByteArray(16)
                    keyBytes.copyInto(padded)
                    padded
                }
            }

            val iv = if (encryptedBytes.size > 16) {
                encryptedBytes.copyOfRange(0, 16)
            } else {
                ByteArray(16)
            }

            val data = if (encryptedBytes.size > 16) {
                encryptedBytes.copyOfRange(16, encryptedBytes.size)
            } else {
                encryptedBytes
            }

            val secretKey = SecretKeySpec(keyFinal, "AES")
            val ivSpec = IvParameterSpec(iv)

            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)

            cipher.doFinal(data)

        } catch (e: Exception) {
            Log.e(TAG, "Base64 decryption failed", e)
            null
        }
    }

    /**
     * 获取视频号密钥（从配置URL）
     */
    fun fetchWxVideoKey(configUrl: String): String? {
        return null // 需要网络请求，在实际使用时实现
    }

    /**
     * 验证是否为加密视频
     */
    fun isEncryptedFile(file: File): Boolean {
        if (!file.exists() || file.length() < 32) return false

        val data = file.readBytes()
        return looksLikeIvPrefix(data) || isAesEncrypted(data)
    }

    /**
     * 简单检测是否为AES加密
     */
    private fun isAesEncrypted(data: ByteArray): Boolean {
        // AES加密的数据应该有良好的随机性
        // 简单检查：文件大小应该是16的倍数
        return data.size % 16 == 0
    }
}

package com.resdownloader.util

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object AesUtils {
    
    fun decrypt(encryptedText: String, key: String): String? {
        return try {
            val cipherTextBytes = Base64.decode(encryptedText, Base64.DEFAULT)
            val keyBytes = key.toByteArray(charset("UTF-8"))
            
            if (cipherTextBytes.size < 16) {
                return null
            }
            
            val iv = cipherTextBytes.copyOfRange(0, 16)
            val cipherBytes = cipherTextBytes.copyOfRange(16, cipherTextBytes.size)
            
            val secretKeySpec = SecretKeySpec(keyBytes, "AES")
            val ivParameterSpec = IvParameterSpec(iv)
            
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivParameterSpec)
            
            val decryptedBytes = cipher.doFinal(cipherBytes)
            val padding = decryptedBytes[decryptedBytes.size - 1].toInt()
            
            if (padding < 1 || padding > 16) {
                return String(decryptedBytes)
            }
            
            val unpadBytes = decryptedBytes.copyOfRange(0, decryptedBytes.size - padding)
            String(unpadBytes)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

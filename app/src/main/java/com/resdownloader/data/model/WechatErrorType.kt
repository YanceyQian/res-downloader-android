package com.resdownloader.data.model

/**
 * 微信视频抓取错误类型
 * 用于分类错误并提供相应的用户提示
 */
enum class WechatErrorType(
    val code: Int,
    val title: String,
    val userMessage: String,
    val isApiRelated: Boolean,
    val requiresAppUpdate: Boolean
) {
    // API 相关错误（可能需要更新 APP）
    API_RET_ERROR(1001, "API 返回错误", "微信 API 返回了错误，请更新到最新版本", true, true),
    API_NETWORK_ERROR(1002, "网络请求失败", "网络连接失败，请检查网络", false, false),
    API_TIMEOUT(1003, "请求超时", "请求超时，请重试", false, false),
    API_INVALID_RESPONSE(1004, "响应格式错误", "微信响应格式已更新，请更新到最新版本", true, true),
    API_AUTH_FAILED(1005, "认证失败", "微信认证失败，可能需要更新版本", true, true),
    API_QUOTA_EXCEEDED(1006, "配额超限", "请求过于频繁，请稍后再试", false, false),

    // 解密相关错误
    DECRYPT_KEY_MISSING(2001, "密钥缺失", "视频解密密钥获取失败，请更新到最新版本", true, true),
    DECRYPT_KEY_INVALID(2002, "密钥无效", "视频解密密钥已失效，请更新到最新版本", true, true),
    DECRYPT_FAILED(2003, "解密失败", "视频解密失败，可能是微信版本不兼容", true, true),

    // 链接解析错误
    URL_INVALID(3001, "链接无效", "视频链接格式不正确", false, false),
    URL_SHORT_RESOLVE_FAILED(3002, "短链接解析失败", "无法解析视频链接，请检查链接是否正确", false, false),
    VIDEO_ID_MISSING(3003, "视频 ID 缺失", "无法获取视频 ID，请检查链接是否正确", false, false),

    // 下载相关错误
    DOWNLOAD_FAILED(4001, "下载失败", "视频下载失败，请重试", false, false),
    DOWNLOAD_INTERRUPTED(4002, "下载中断", "下载被中断，请重试", false, false),
    STORAGE_FULL(4003, "存储空间不足", "存储空间不足，请清理后重试", false, false),

    // 未知错误
    UNKNOWN(9999, "未知错误", "发生未知错误，请更新到最新版本", true, true);

    companion object {
        /**
         * 根据异常类型和 HTTP 状态码获取错误类型
         */
        fun fromException(e: Exception, httpCode: Int? = null): WechatErrorType {
            val message = e.message?.lowercase() ?: ""

            return when {
                // 网络相关
                message.contains("timeout") || message.contains("timed out") -> API_TIMEOUT
                message.contains("network") || message.contains("connection") -> API_NETWORK_ERROR
                message.contains("socket") -> API_NETWORK_ERROR

                // HTTP 状态码相关
                httpCode == 401 || httpCode == 403 -> API_AUTH_FAILED
                httpCode == 404 -> API_INVALID_RESPONSE
                httpCode == 429 -> API_QUOTA_EXCEEDED
                httpCode in 500..599 -> API_NETWORK_ERROR

                // 解密相关
                message.contains("decrypt") || message.contains("decode") || message.contains("key") -> DECRYPT_FAILED

                // 下载相关
                message.contains("download") -> DOWNLOAD_FAILED
                message.contains("space") || message.contains("storage") -> STORAGE_FULL

                // 其他
                else -> UNKNOWN
            }
        }

        /**
         * 根据 API 响应状态码获取错误类型
         */
        fun fromApiResponse(retCode: Int, errmsg: String? = null): WechatErrorType {
            return when (retCode) {
                -1 -> API_AUTH_FAILED
                -2 -> API_INVALID_RESPONSE
                -3 -> API_QUOTA_EXCEEDED
                -4 -> API_AUTH_FAILED
                -5 -> API_INVALID_RESPONSE
                else -> {
                    if (retCode != 0) {
                        API_RET_ERROR
                    } else {
                        UNKNOWN
                    }
                }
            }
        }
    }
}

/**
 * 错误详情
 */
data class WechatErrorDetail(
    val type: WechatErrorType,
    val originalMessage: String,
    val timestamp: Long = System.currentTimeMillis(),
    val platformVersion: String? = null,
    val appVersion: String? = null
)

/**
 * 错误统计（用于上报）
 */
data class ErrorStatistics(
    val errorCount: Int = 0,
    val apiRelatedCount: Int = 0,
    val lastErrorType: WechatErrorType? = null,
    val lastErrorTime: Long = 0
)

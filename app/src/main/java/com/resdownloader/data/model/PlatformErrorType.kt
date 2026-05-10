package com.resdownloader.data.model

import com.resdownloader.data.model.Platform

/**
 * 各平台通用错误类型
 * 错误码范围：
 * - 1xxx: API 相关错误（可能需要更新应用）
 * - 2xxx: 解密相关错误
 * - 3xxx: 链接解析错误
 * - 4xxx: 下载相关错误
 * - 5xxx: 资源状态错误（已删除、版权限制等）
 * - 6xxx: 网络相关错误
 */
enum class PlatformErrorType(
    val code: Int,
    val userMessage: String,
    val isApiRelated: Boolean = false,
    val requiresAppUpdate: Boolean = false,
    val platformSpecific: Platform? = null
) {
    // ========== API 相关错误 ==========
    API_RET_ERROR(1001, "接口返回错误，请稍后重试", isApiRelated = true, requiresAppUpdate = true),
    API_NETWORK_ERROR(1002, "网络连接失败，请检查网络", isApiRelated = true),
    API_TIMEOUT(1003, "请求超时，请稍后重试", isApiRelated = true),
    API_INVALID_RESPONSE(1004, "接口响应格式已变更，请更新应用", isApiRelated = true, requiresAppUpdate = true),
    API_AUTH_FAILED(1005, "认证失败，可能需要重新登录", isApiRelated = true),
    API_QUOTA_EXCEEDED(1006, "请求次数超限，请稍后再试", isApiRelated = true),
    API_SIGNATURE_INVALID(1007, "签名验证失败，请更新应用", isApiRelated = true, requiresAppUpdate = true),
    API_VERSION_MISMATCH(1008, "接口版本不匹配，请更新应用", isApiRelated = true, requiresAppUpdate = true),

    // ========== 解密相关错误 ==========
    DECRYPT_KEY_MISSING(2001, "缺少解密密钥，请更新应用", requiresAppUpdate = true),
    DECRYPT_KEY_INVALID(2002, "解密密钥已失效，请更新应用", requiresAppUpdate = true),
    DECRYPT_FAILED(2003, "视频解密失败，请更新应用", requiresAppUpdate = true),

    // ========== 链接解析错误 ==========
    URL_INVALID(3001, "链接格式无效"),
    URL_SHORT_RESOLVE_FAILED(3002, "短链接解析失败，请稍后重试"),
    VIDEO_ID_MISSING(3003, "无法提取视频ID，请检查链接是否正确"),
    URL_EXPIRED(3004, "链接已过期"),

    // ========== 下载相关错误 ==========
    DOWNLOAD_FAILED(4001, "下载失败，请稍后重试"),
    DOWNLOAD_INTERRUPTED(4002, "下载被中断，请重试"),
    STORAGE_FULL(4003, "存储空间不足，请清理后重试"),
    DOWNLOAD_PERMISSION_DENIED(4004, "没有存储权限，请授予权限"),

    // ========== 资源状态错误 ==========
    RESOURCE_DELETED(5001, "资源已被删除"),
    RESOURCE_EXPIRED(5002, "资源已过期或失效"),
    RESOURCE_REGION_BLOCKED(5003, "该资源在当前地区不可用"),
    RESOURCE_COPYRIGHT_BLOCKED(5004, "该资源受版权保护，无法下载"),
    RESOURCE_PRIVATE(5005, "这是一条私密内容，无法下载"),
    RESOURCE_NEED_LOGIN(5006, "请登录后再试"),
    RESOURCE_VIP_ONLY(5007, "这是付费/会员专属内容"),

    // ========== 网络相关错误 ==========
    NETWORK_UNAVAILABLE(6001, "网络不可用，请检查网络连接"),
    NETWORK_SLOW(6002, "网络较慢，请切换网络后重试"),
    SSL_ERROR(6003, "SSL证书错误，请更新应用"),
    PROXY_ERROR(6004, "代理连接失败，请重启应用"),

    // ========== 未知错误 ==========
    UNKNOWN(9999, "发生未知错误，请稍后重试");

    /**
     * 获取错误分类描述
     */
    fun getCategory(): ErrorCategory {
        return when (code) {
            in 1000..1999 -> ErrorCategory.API_ERROR
            in 2000..2999 -> ErrorCategory.DECRYPT_ERROR
            in 3000..3999 -> ErrorCategory.PARSE_ERROR
            in 4000..4999 -> ErrorCategory.DOWNLOAD_ERROR
            in 5000..5999 -> ErrorCategory.RESOURCE_ERROR
            in 6000..6999 -> ErrorCategory.NETWORK_ERROR
            else -> ErrorCategory.UNKNOWN
        }
    }

    /**
     * 是否应该显示降级方案
     */
    fun shouldShowFallback(): Boolean {
        return when (getCategory()) {
            ErrorCategory.API_ERROR -> requiresAppUpdate
            ErrorCategory.RESOURCE_ERROR -> true  // 资源问题也有替代方案
            else -> false
        }
    }

    /**
     * 获取错误严重程度
     */
    fun getSeverity(): ErrorSeverity {
        return when {
            requiresAppUpdate -> ErrorSeverity.HIGH
            code in 5001..5007 -> ErrorSeverity.MEDIUM  // 资源问题
            isApiRelated -> ErrorSeverity.MEDIUM
            else -> ErrorSeverity.LOW
        }
    }

    companion object {
        /**
         * 从异常创建错误类型
         */
        fun fromException(e: Exception, httpCode: Int? = null): PlatformErrorType {
            return when {
                e.message?.contains("timeout", ignoreCase = true) == true -> API_TIMEOUT
                e.message?.contains("network", ignoreCase = true) == true -> API_NETWORK_ERROR
                e.message?.contains("certificate", ignoreCase = true) == true -> SSL_ERROR
                e.message?.contains("storage", ignoreCase = true) == true -> STORAGE_FULL
                e.message?.contains("permission", ignoreCase = true) == true -> DOWNLOAD_PERMISSION_DENIED
                httpCode == 401 -> API_AUTH_FAILED
                httpCode == 403 -> API_AUTH_FAILED
                httpCode == 404 -> RESOURCE_DELETED
                httpCode == 429 -> API_QUOTA_EXCEEDED
                httpCode in 500..599 -> API_RET_ERROR
                else -> UNKNOWN
            }
        }

        /**
         * 从 API 响应码创建错误类型
         */
        fun fromApiResponse(retCode: Int, platform: Platform): PlatformErrorType {
            return when (platform) {
                Platform.WECHAT -> fromWechatResponse(retCode)
                Platform.DOUYIN -> fromDouyinResponse(retCode)
                Platform.KUAISHOU -> fromKuaishouResponse(retCode)
                Platform.XIAOHONGSHU -> fromXiaohongshuResponse(retCode)
                Platform.BILIBILI -> fromBilibiliResponse(retCode)
                Platform.NETEASE -> fromNeteaseResponse(retCode)
                else -> UNKNOWN
            }
        }

        private fun fromWechatResponse(retCode: Int): PlatformErrorType {
            return when (retCode) {
                -1 -> API_VERSION_MISMATCH
                -2 -> API_SIGNATURE_INVALID
                -3 -> API_AUTH_FAILED
                -4 -> DECRYPT_KEY_INVALID
                -5 -> RESOURCE_DELETED
                -6 -> RESOURCE_REGION_BLOCKED
                else -> API_RET_ERROR
            }
        }

        private fun fromDouyinResponse(retCode: Int): PlatformErrorType {
            return when (retCode) {
                10001 -> API_VERSION_MISMATCH
                10002 -> API_SIGNATURE_INVALID
                10003 -> RESOURCE_DELETED
                10004 -> RESOURCE_COPYRIGHT_BLOCKED
                else -> API_RET_ERROR
            }
        }

        private fun fromKuaishouResponse(retCode: Int): PlatformErrorType {
            return when (retCode) {
                20001 -> API_VERSION_MISMATCH
                20002 -> DECRYPT_KEY_INVALID
                20003 -> RESOURCE_DELETED
                else -> API_RET_ERROR
            }
        }

        private fun fromXiaohongshuResponse(retCode: Int): PlatformErrorType {
            return when (retCode) {
                30001 -> API_VERSION_MISMATCH
                30002 -> RESOURCE_DELETED
                30003 -> RESOURCE_PRIVATE
                30004 -> RESOURCE_NEED_LOGIN
                else -> API_RET_ERROR
            }
        }

        private fun fromBilibiliResponse(retCode: Int): PlatformErrorType {
            return when (retCode) {
                40001 -> API_VERSION_MISMATCH
                40002 -> RESOURCE_COPYRIGHT_BLOCKED
                40003 -> RESOURCE_VIP_ONLY
                40004 -> RESOURCE_NEED_LOGIN
                else -> API_RET_ERROR
            }
        }

        private fun fromNeteaseResponse(retCode: Int): PlatformErrorType {
            return when (retCode) {
                50001 -> API_VERSION_MISMATCH
                50002 -> RESOURCE_COPYRIGHT_BLOCKED
                50003 -> RESOURCE_VIP_ONLY
                50004 -> RESOURCE_DELETED
                else -> API_RET_ERROR
            }
        }
    }
}

enum class ErrorCategory {
    API_ERROR,      // API 相关
    DECRYPT_ERROR,  // 解密相关
    PARSE_ERROR,    // 解析相关
    DOWNLOAD_ERROR, // 下载相关
    RESOURCE_ERROR, // 资源本身问题
    NETWORK_ERROR,  // 网络问题
    UNKNOWN         // 未知
}

enum class ErrorSeverity {
    HIGH,   // 高严重性，需要更新应用
    MEDIUM, // 中等严重性，可能有替代方案
    LOW     // 低严重性，重试即可
}

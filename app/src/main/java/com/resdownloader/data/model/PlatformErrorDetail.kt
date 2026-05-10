package com.resdownloader.data.model

/**
 * 平台错误详情
 * 包含错误类型、原始消息、上下文信息
 */
data class PlatformErrorDetail(
    val type: PlatformErrorType,
    val originalMessage: String,
    val platform: Platform? = null,
    val url: String? = null,
    val httpCode: Int? = null,
    val platformVersion: String? = null,
    val appVersion: String? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * 获取用户友好的错误消息
     */
    fun getUserMessage(): String {
        return type.userMessage
    }

    /**
     * 是否需要更新应用才能解决
     */
    fun requiresAppUpdate(): Boolean {
        return type.requiresAppUpdate
    }

    /**
     * 是否应该显示降级方案
     */
    fun shouldShowFallback(): Boolean {
        return type.shouldShowFallback()
    }

    /**
     * 获取错误严重程度
     */
    fun getSeverity(): ErrorSeverity {
        return type.getSeverity()
    }

    /**
     * 转换为可分享的错误报告
     */
    fun toReport(): String {
        return buildString {
            appendLine("=== 错误报告 ===")
            appendLine("错误类型: ${type.name} (${type.code})")
            appendLine("错误消息: $originalMessage")
            platform?.let { appendLine("平台: ${it.displayName}") }
            url?.let { appendLine("相关链接: $it") }
            httpCode?.let { appendLine("HTTP状态码: $it") }
            platformVersion?.let { appendLine("平台版本: $it") }
            appVersion?.let { appendLine("应用版本: $it") }
            appendLine("发生时间: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(timestamp))}")
            appendLine("=============")
        }
    }
}

/**
 * 平台错误统计
 */
data class PlatformErrorStatistics(
    val platform: Platform,
    val errorCount: Int = 0,
    val apiRelatedCount: Int = 0,
    val lastErrorType: PlatformErrorType? = null,
    val lastErrorTime: Long = 0,
    val consecutiveApiFailures: Int = 0  // 连续 API 失败次数
) {
    /**
     * 检查 API 是否可能已失效
     */
    fun isApiLikelyBroken(): Boolean {
        return consecutiveApiFailures >= 3
    }

    /**
     * 添加错误并更新统计
     */
    fun addError(error: PlatformErrorDetail): PlatformErrorStatistics {
        return copy(
            errorCount = errorCount + 1,
            apiRelatedCount = if (error.type.isApiRelated) apiRelatedCount + 1 else apiRelatedCount,
            lastErrorType = error.type,
            lastErrorTime = error.timestamp,
            consecutiveApiFailures = if (error.type.isApiRelated) consecutiveApiFailures + 1 else 0
        )
    }

    /**
     * 清除连续失败计数（成功时调用）
     */
    fun clearConsecutiveFailures(): PlatformErrorStatistics {
        return copy(consecutiveApiFailures = 0)
    }
}

/**
 * 平台公告（支持多平台）
 */
data class PlatformAnnouncement(
    val id: String,
    val platform: Platform,  // 针对的平台
    val title: String,
    val content: String,
    val type: PlatformAnnouncementType,
    val minAppVersion: String? = null,
    val maxAppVersion: String? = null,
    val fixedInVersion: String? = null,  // 标注在哪个版本已修复
    val startTime: Long? = null,
    val endTime: Long? = null,
    val actionUrl: String? = null,
    val actionText: String? = null
) {
    /**
     * 检查公告是否对当前版本有效
     */
    fun isActive(currentVersion: String): Boolean {
        val now = System.currentTimeMillis()

        // 检查时间范围
        if (startTime != null && now < startTime) return false
        if (endTime != null && now > endTime) return false

        // 检查版本范围
        if (minAppVersion != null && compareVersions(currentVersion, minAppVersion) < 0) {
            return false
        }
        if (maxAppVersion != null && compareVersions(currentVersion, maxAppVersion) > 0) {
            return false
        }

        return true
    }

    /**
     * 检查是否已有修复版本
     */
    fun hasFix(): Boolean = fixedInVersion != null

    /**
     * 检查当前版本是否需要更新才能修复
     */
    fun needsUpdate(currentVersion: String): Boolean {
        return fixedInVersion != null && compareVersions(currentVersion, fixedInVersion) < 0
    }

    private fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.removePrefix("v").split(".").map { it.split("-")[0].toIntOrNull() ?: 0 }
        val parts2 = v2.removePrefix("v").split(".").map { it.split("-")[0].toIntOrNull() ?: 0 }
        val maxLen = maxOf(parts1.size, parts2.size)

        for (i in 0 until maxLen) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p1 != p2) return p1.compareTo(p2)
        }
        return 0
    }
}

enum class PlatformAnnouncementType {
    INFO,       // 普通信息
    WARNING,    // 警告（如接口更新）
    URGENT,     // 紧急（如 API 完全失效）
    UPDATE      // 更新提示
}

/**
 * 各平台降级方案配置
 */
data class PlatformFallbackConfig(
    val platform: Platform,
    val primaryMessage: String,
    val alternativeMethods: List<FallbackMethod>,
    val feedbackTemplate: String,
    val documentationUrl: String? = null
)

/**
 * 降级方法
 */
data class FallbackMethod(
    val id: String,
    val title: String,
    val description: String,
    val icon: String,
    val priority: Int  // 显示优先级，数字越小优先级越高
)

/**
 * 各平台的降级方案配置
 */
object PlatformFallbackConfigs {
    
    fun getConfig(platform: Platform): PlatformFallbackConfig {
        return when (platform) {
            Platform.WECHAT -> PlatformFallbackConfig(
                platform = Platform.WECHAT,
                primaryMessage = "微信视频号接口可能已更新",
                alternativeMethods = listOf(
                    FallbackMethod("share_link", "分享链接方式", "在微信中分享视频链接，然后粘贴到应用中", "link", 1),
                    FallbackMethod("proxy_capture", "代理抓取", "开启代理后自动捕获视频号内容", "wifi_tethering", 2),
                    FallbackMethod("screen_record", "录屏保存", "使用手机录屏功能录制视频", "videocam", 3),
                    FallbackMethod("feedback", "提交反馈", "帮助开发者快速定位并修复问题", "feedback", 4)
                ),
                feedbackTemplate = """
                    ## 问题描述
                    （请描述您遇到的问题）

                    ## 操作步骤
                    1. 打开微信视频号
                    2. 点击某个视频
                    3. 点击分享 -> 复制链接
                    4. 粘贴到本应用中
                    5. 点击下载

                    ## 错误信息
                    - 错误类型: %s
                    - 错误消息: %s
                    - 应用版本: %s
                    - 微信版本: %s

                    ## 其他信息
                    （可选）提供视频链接（非必须）：
                """.trimIndent()
            )

            Platform.DOUYIN -> PlatformFallbackConfig(
                platform = Platform.DOUYIN,
                primaryMessage = "抖音接口可能已更新",
                alternativeMethods = listOf(
                    FallbackMethod("short_url", "短链接解析", "使用 v.douyin.com 短链接解析", "link", 1),
                    FallbackMethod("proxy_capture", "代理抓取", "开启代理后自动捕获抖音视频", "wifi_tethering", 2),
                    FallbackMethod("third_party", "第三方工具", "使用其他抖音下载工具", "apps", 3),
                    FallbackMethod("feedback", "提交反馈", "帮助开发者快速定位并修复问题", "feedback", 4)
                ),
                feedbackTemplate = """
                    ## 问题描述
                    （请描述您遇到的问题）

                    ## 操作步骤
                    1. 在抖音中复制视频分享链接
                    2. 粘贴到本应用中
                    3. 点击下载

                    ## 错误信息
                    - 错误类型: %s
                    - 错误消息: %s
                    - 应用版本: %s

                    ## 其他信息
                    （可选）提供视频链接（非必须）：
                """.trimIndent()
            )

            Platform.KUAISHOU -> PlatformFallbackConfig(
                platform = Platform.KUAISHOU,
                primaryMessage = "快手接口可能已更新",
                alternativeMethods = listOf(
                    FallbackMethod("short_url", "短链接解析", "使用 ksurl.cn 短链接解析", "link", 1),
                    FallbackMethod("proxy_capture", "代理抓取", "开启代理后自动捕获快手内容", "wifi_tethering", 2),
                    FallbackMethod("third_party", "第三方工具", "使用其他快手下载工具", "apps", 3),
                    FallbackMethod("feedback", "提交反馈", "帮助开发者快速定位并修复问题", "feedback", 4)
                ),
                feedbackTemplate = """
                    ## 问题描述
                    （请描述您遇到的问题）

                    ## 操作步骤
                    1. 在快手中复制视频分享链接
                    2. 粘贴到本应用中
                    3. 点击下载

                    ## 错误信息
                    - 错误类型: %s
                    - 错误消息: %s
                    - 应用版本: %s

                    ## 其他信息
                    （可选）提供视频链接（非必须）：
                """.trimIndent()
            )

            Platform.XIAOHONGSHU -> PlatformFallbackConfig(
                platform = Platform.XIAOHONGSHU,
                primaryMessage = "小红书笔记可能已删除或需要登录",
                alternativeMethods = listOf(
                    FallbackMethod("proxy_capture", "代理抓取", "开启代理自动抓取（推荐）", "wifi_tethering", 1),
                    FallbackMethod("screenshot", "截图保存", "使用截图保存图片内容", "photo_camera", 2),
                    FallbackMethod("web_version", "网页版", "尝试在浏览器中打开", "language", 3),
                    FallbackMethod("feedback", "提交反馈", "帮助开发者快速定位并修复问题", "feedback", 4)
                ),
                feedbackTemplate = """
                    ## 问题描述
                    （请描述您遇到的问题）

                    ## 操作步骤
                    1. 在小红书中复制笔记分享链接
                    2. 粘贴到本应用中
                    3. 点击下载

                    ## 错误信息
                    - 错误类型: %s
                    - 错误消息: %s
                    - 应用版本: %s

                    ## 其他信息
                    （可选）提供笔记链接（非必须）：
                """.trimIndent()
            )

            Platform.BILIBILI -> PlatformFallbackConfig(
                platform = Platform.BILIBILI,
                primaryMessage = "B站视频可能需要登录或受版权限制",
                alternativeMethods = listOf(
                    FallbackMethod("login_retry", "登录后再试", "登录后再尝试解析", "person", 1),
                    FallbackMethod("proxy_capture", "代理抓取", "开启代理后自动捕获（部分有效）", "wifi_tethering", 2),
                    FallbackMethod("screen_record", "录屏保存", "使用录屏方式保存", "videocam", 3),
                    FallbackMethod("feedback", "提交反馈", "帮助开发者快速定位并修复问题", "feedback", 4)
                ),
                feedbackTemplate = """
                    ## 问题描述
                    （请描述您遇到的问题）

                    ## 操作步骤
                    1. 在B站中复制视频链接或 BV 号
                    2. 粘贴到本应用中
                    3. 点击下载

                    ## 错误信息
                    - 错误类型: %s
                    - 错误消息: %s
                    - 应用版本: %s

                    ## 其他信息
                    （可选）提供视频链接（非必须）：
                """.trimIndent()
            )

            Platform.NETEASE -> PlatformFallbackConfig(
                platform = Platform.NETEASE,
                primaryMessage = "该资源可能受版权保护",
                alternativeMethods = listOf(
                    FallbackMethod("proxy_capture", "代理抓取", "开启代理抓取音频流", "wifi_tethering", 1),
                    FallbackMethod("screen_record", "录屏录音", "使用系统录屏录制音频", "mic", 2),
                    FallbackMethod("other_platform", "其他平台", "在其他音乐平台搜索该歌曲", "search", 3),
                    FallbackMethod("feedback", "提交反馈", "帮助开发者快速定位并修复问题", "feedback", 4)
                ),
                feedbackTemplate = """
                    ## 问题描述
                    （请描述您遇到的问题）

                    ## 操作步骤
                    1. 在网易云音乐中复制歌曲链接
                    2. 粘贴到本应用中
                    3. 点击下载

                    ## 错误信息
                    - 错误类型: %s
                    - 错误消息: %s
                    - 应用版本: %s

                    ## 其他信息
                    （可选）提供歌曲链接（非必须）：
                """.trimIndent()
            )

            Platform.QQMUSIC -> PlatformFallbackConfig(
                platform = Platform.QQMUSIC,
                primaryMessage = "QQ音乐接口可能已更新",
                alternativeMethods = listOf(
                    FallbackMethod("proxy_capture", "代理抓取", "开启代理抓取音频流（推荐）", "wifi_tethering", 1),
                    FallbackMethod("screen_record", "录屏录音", "使用系统录屏录制音频", "mic", 2),
                    FallbackMethod("other_platform", "其他平台", "尝试网易云音乐或酷狗音乐", "search", 3),
                    FallbackMethod("third_party", "第三方工具", "使用 SMM Voyager 等工具", "apps", 4),
                    FallbackMethod("feedback", "提交反馈", "帮助开发者快速定位并修复问题", "feedback", 5)
                ),
                feedbackTemplate = """
                    ## 问题描述
                    （请描述您遇到的问题）

                    ## 操作步骤
                    1. 在QQ音乐中复制歌曲链接
                    2. 粘贴到本应用中
                    3. 点击下载

                    ## 错误信息
                    - 错误类型: %s
                    - 错误消息: %s
                    - 应用版本: %s

                    ## 其他信息
                    （可选）提供歌曲链接（非必须）：
                """.trimIndent()
            )

            Platform.KOUGOU -> PlatformFallbackConfig(
                platform = Platform.KOUGOU,
                primaryMessage = "酷狗音乐接口可能已更新",
                alternativeMethods = listOf(
                    FallbackMethod("proxy_capture", "代理抓取", "开启代理抓取音频流（推荐）", "wifi_tethering", 1),
                    FallbackMethod("screen_record", "录屏录音", "使用系统录屏录制音频", "mic", 2),
                    FallbackMethod("other_platform", "其他平台", "尝试网易云音乐或QQ音乐", "search", 3),
                    FallbackMethod("third_party", "第三方工具", "使用 SMM Voyager 等工具", "apps", 4),
                    FallbackMethod("feedback", "提交反馈", "帮助开发者快速定位并修复问题", "feedback", 5)
                ),
                feedbackTemplate = """
                    ## 问题描述
                    （请描述您遇到的问题）

                    ## 操作步骤
                    1. 在酷狗音乐中复制歌曲链接
                    2. 粘贴到本应用中
                    3. 点击下载

                    ## 错误信息
                    - 错误类型: %s
                    - 错误消息: %s
                    - 应用版本: %s

                    ## 其他信息
                    （可选）提供歌曲链接（非必须）：
                """.trimIndent()
            )

            else -> PlatformFallbackConfig(
                platform = platform,
                primaryMessage = "该平台接口可能已更新",
                alternativeMethods = listOf(
                    FallbackMethod("proxy_capture", "代理抓取", "开启代理后自动捕获", "wifi_tethering", 1),
                    FallbackMethod("screen_record", "录屏保存", "使用录屏方式保存", "videocam", 2),
                    FallbackMethod("video_tools", "视频下载工具", "JDownloader2、IDM（Windows）、Folx（Mac）", "apps", 3),
                    FallbackMethod("web_service", "在线解析", "使用在线视频解析网站", "language", 4),
                    FallbackMethod("feedback", "提交反馈", "帮助开发者评估是否值得添加支持", "feedback", 5)
                ),
                feedbackTemplate = """
                    ## 问题描述
                    （请描述您遇到的问题）

                    ## 操作步骤
                    1. 复制内容链接
                    2. 粘贴到本应用中
                    3. 点击下载

                    ## 错误信息
                    - 错误类型: %s
                    - 错误消息: %s
                    - 应用版本: %s
                    - 平台: %s

                    ## 其他信息
                """.trimIndent(),
                documentationUrl = null
            )
        }
    }
}

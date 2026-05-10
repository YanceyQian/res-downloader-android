package com.resdownloader.data.repository

import android.content.Context
import android.util.Log
import com.resdownloader.BuildConfig
import com.resdownloader.data.model.WechatErrorDetail
import com.resdownloader.data.model.WechatErrorType
import com.resdownloader.data.model.ErrorStatistics
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 微信视频抓取错误处理器
 * 负责：
 * 1. 统一处理和分类错误
 * 2. 统计错误信息
 * 3. 检测 API 失效并触发更新提醒
 * 4. 本地缓存错误历史
 * 5. 提供降级方案
 * 6. 收集用户反馈
 */
@Singleton
class WechatErrorRepository @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val moshi: Moshi,
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "WechatErrorHandler"

        // 连续 API 错误次数阈值，超过则判定为 API 失效
        private const val API_FAILURE_THRESHOLD = 3

        // 错误缓存文件名
        private const val ERROR_CACHE_FILE = "wechat_errors.json"

        // 反馈文件名
        private const val FEEDBACK_CACHE_FILE = "wechat_feedback.json"

        // 公告 API（可替换为实际的公告服务）
        private const val ANNOUNCEMENT_API = "https://raw.githubusercontent.com/YanceyQian/res-downloader-android/main/announcement.json"
    }

    private val _errorStatistics = MutableStateFlow(ErrorStatistics())
    val errorStatistics: StateFlow<ErrorStatistics> = _errorStatistics.asStateFlow()

    private val _isApiLikelyBroken = MutableStateFlow(false)
    val isApiLikelyBroken: StateFlow<Boolean> = _isApiLikelyBroken.asStateFlow()

    private val _currentAnnouncement = MutableStateFlow<Announcement?>(null)
    val currentAnnouncement: StateFlow<Announcement?> = _currentAnnouncement.asStateFlow()

    // 降级方案状态
    private val _fallbackState = MutableStateFlow<FallbackState>(FallbackState.None)
    val fallbackState: StateFlow<FallbackState> = _fallbackState.asStateFlow()

    // 公告中说明的"已修复版本"，如果当前版本 >= 此版本，说明最新版本已修复
    private var fixedInVersion: String? = null

    // 最近一次 API 错误的时间
    private var lastApiErrorTime: Long = 0

    // API 错误计数器
    private var apiErrorCount: Int = 0

    // 用户是否已尝试更新但仍失败
    private var updateAttemptedAndFailed = false

    /**
     * 降级方案状态
     */
    sealed class FallbackState {
        object None : FallbackState()
        data class SuggestShareLink(val message: String) : FallbackState()
        data class SuggestAlternative(val alternatives: List<String>) : FallbackState()
        data class AllFailed(val feedbackUrl: String) : FallbackState()
    }

    /**
     * 处理错误
     */
    fun handleError(errorDetail: WechatErrorDetail) {
        Log.w(TAG, "Handling error: ${errorDetail.type} - ${errorDetail.originalMessage}")

        // 更新统计
        updateStatistics(errorDetail)

        // 如果是 API 相关错误，增加计数器
        if (errorDetail.type.isApiRelated) {
            apiErrorCount++
            lastApiErrorTime = System.currentTimeMillis()

            // 检查是否达到 API 失效阈值
            if (apiErrorCount >= API_FAILURE_THRESHOLD) {
                _isApiLikelyBroken.value = true
                updateFallbackState()
                Log.e(TAG, "API appears to be broken! Error count: $apiErrorCount")
            }
        }

        // 持久化错误到本地
        saveErrorToCache(errorDetail)
    }

    /**
     * 更新降级方案状态
     * 根据公告中的修复版本信息判断是否需要降级
     */
    private fun updateFallbackState() {
        val currentVersion = BuildConfig.VERSION_NAME

        // 检查公告中是否说明某个版本已修复
        if (fixedInVersion != null) {
            if (compareVersions(currentVersion, fixedInVersion!!) >= 0) {
                // 当前版本 >= 修复版本，说明最新版本应该已修复
                // 但用户仍然失败，可能是其他问题
                _fallbackState.value = FallbackState.SuggestAlternative(
                    listOf(
                        "请确认微信已更新到最新版本",
                        "尝试重新启动微信和本应用",
                        "清除微信缓存后重试"
                    )
                )
                return
            } else {
                // 当前版本 < 修复版本，提示用户更新
                _fallbackState.value = FallbackState.SuggestShareLink(
                    "微信视频号接口已更新，当前版本($currentVersion)可能不支持。\n" +
                    "请更新到 v$fixedInVersion 或更高版本。\n\n" +
                    "如果更新后仍有问题，可以尝试：\n" +
                    "• 通过微信分享链接方式获取视频\n" +
                    "• 使用其他视频下载工具作为临时替代"
                )
                return
            }
        }

        // 没有公告信息，根据错误次数判断
        when {
            apiErrorCount < API_FAILURE_THRESHOLD -> {
                _fallbackState.value = FallbackState.None
            }
            updateAttemptedAndFailed || _currentAnnouncement.value == null -> {
                // 用户已尝试更新但仍失败，或没有公告
                _fallbackState.value = FallbackState.AllFailed(
                    "https://github.com/YanceyQian/res-downloader-android/issues/new"
                )
            }
            else -> {
                // 还没尝试更新，先提示更新
                _fallbackState.value = FallbackState.SuggestShareLink(
                    "微信视频号接口可能已更新。\n\n" +
                    "请尝试以下方法：\n" +
                    "1. 更新本应用到最新版本\n" +
                    "2. 如果更新后仍失败，尝试分享链接方式\n\n" +
                    "如果问题持续，请通过反馈功能告知开发者。"
                )
            }
        }
    }

    /**
     * 标记用户已尝试更新但仍失败
     */
    fun markUpdateAttemptedAndFailed() {
        updateAttemptedAndFailed = true
        updateFallbackState()
    }

    /**
     * 获取当前降级提示消息
     */
    fun getFallbackMessage(): String? {
        return when (val state = _fallbackState.value) {
            is FallbackState.SuggestShareLink -> state.message
            is FallbackState.SuggestAlternative -> state.alternatives.joinToString("\n• ")
            is FallbackState.AllFailed -> """
                抱歉，微信视频号功能暂时不可用。

                可能的原因：
                • 微信版本已更新，但本应用尚未适配
                • 网络环境异常
                • 视频已被删除或设置为不可下载

                临时解决方案：
                1. 通过微信直接分享视频到其他设备
                2. 使用手机录屏功能
                3. 等待开发者发布更新版本

                如需帮助，请提交反馈。
            """.trimIndent()
            FallbackState.None -> null
        }
    }

    /**
     * 获取降级方案列表
     */
    fun getFallbackOptions(): List<FallbackOption> {
        return listOf(
            FallbackOption(
                id = "share_link",
                title = "分享链接方式",
                description = "在微信中分享视频链接，然后粘贴到应用中",
                icon = "link",
                action = "paste"
            ),
            FallbackOption(
                id = "screenshot",
                title = "录屏保存",
                description = "使用手机录屏功能录制视频",
                icon = "videocam",
                action = "hint"
            ),
            FallbackOption(
                id = "other_app",
                title = "其他下载工具",
                description = "临时使用其他支持微信视频的下载工具",
                icon = "apps",
                action = "hint"
            ),
            FallbackOption(
                id = "feedback",
                title = "提交反馈",
                description = "帮助开发者快速定位并修复问题",
                icon = "feedback",
                action = "browser"
            )
        )
    }

    /**
     * 处理异常的便捷方法
     */
    fun handleException(
        e: Exception,
        httpCode: Int? = null,
        platformVersion: String? = null
    ): WechatErrorDetail {
        val errorType = WechatErrorType.fromException(e, httpCode)
        val errorDetail = WechatErrorDetail(
            type = errorType,
            originalMessage = e.message ?: "Unknown error",
            platformVersion = platformVersion,
            appVersion = BuildConfig.VERSION_NAME
        )
        handleError(errorDetail)
        return errorDetail
    }

    /**
     * 处理 API 响应错误
     */
    fun handleApiError(retCode: Int, errmsg: String?): WechatErrorDetail {
        val errorType = WechatErrorType.fromApiResponse(retCode, errmsg)
        val errorDetail = WechatErrorDetail(
            type = errorType,
            originalMessage = errmsg ?: "API returned error code: $retCode"
        )
        handleError(errorDetail)
        return errorDetail
    }

    /**
     * 清除 API 错误计数（成功后调用）
     */
    fun clearApiErrorCount() {
        apiErrorCount = 0
        _isApiLikelyBroken.value = false
        updateAttemptedAndFailed = false
        _fallbackState.value = FallbackState.None
        Log.d(TAG, "API error count cleared")
    }

    /**
     * 更新错误统计
     */
    private fun updateStatistics(errorDetail: WechatErrorDetail) {
        val current = _errorStatistics.value
        _errorStatistics.value = current.copy(
            errorCount = current.errorCount + 1,
            apiRelatedCount = if (errorDetail.type.isApiRelated) current.apiRelatedCount + 1 else current.apiRelatedCount,
            lastErrorType = errorDetail.type,
            lastErrorTime = System.currentTimeMillis()
        )
    }

    /**
     * 获取用户友好的错误提示（考虑降级方案）
     */
    fun getUserMessage(errorDetail: WechatErrorDetail): String {
        val baseMessage = errorDetail.type.userMessage
        val fallbackMessage = getFallbackMessage()

        // 根据公告信息判断是否应该提示更新
        val shouldSuggestUpdate = if (fixedInVersion != null) {
            compareVersions(BuildConfig.VERSION_NAME, fixedInVersion!!) < 0
        } else {
            errorDetail.type.requiresAppUpdate
        }

        return if (shouldSuggestUpdate && fallbackMessage != null) {
            "$baseMessage\n\n$fallbackMessage"
        } else if (shouldSuggestUpdate) {
            "$baseMessage\n\n建议：更新到最新版本以获得更好的兼容性"
        } else {
            baseMessage
        }
    }

    /**
     * 检查是否应该显示更新提醒
     */
    fun shouldShowUpdateReminder(): Boolean {
        // 如果连续 3 次 API 错误，显示更新提醒
        return apiErrorCount >= API_FAILURE_THRESHOLD || _errorStatistics.value.lastErrorType?.requiresAppUpdate == true
    }

    /**
     * 获取错误历史（从本地缓存）
     */
    suspend fun getErrorHistory(): List<WechatErrorDetail> = withContext(Dispatchers.IO) {
        try {
            val cacheFile = File(context.filesDir, ERROR_CACHE_FILE)
            if (!cacheFile.exists()) return@withContext emptyList()

            val json = cacheFile.readText()
            val adapter = moshi.adapter<List<WechatErrorDetail>>(
                com.squareup.moshi.Types.newParameterizedType(List::class.java, WechatErrorDetail::class.java)
            )
            adapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load error history", e)
            emptyList()
        }
    }

    /**
     * 保存错误到本地缓存
     */
    private fun saveErrorToCache(errorDetail: WechatErrorDetail) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val history = kotlinx.coroutines.runBlocking { getErrorHistory() }.toMutableList()
                history.add(errorDetail)

                // 只保留最近 100 条
                val trimmedHistory = history.takeLast(100)

                val cacheFile = File(context.filesDir, ERROR_CACHE_FILE)
                val adapter = moshi.adapter<List<WechatErrorDetail>>(
                    com.squareup.moshi.Types.newParameterizedType(List::class.java, WechatErrorDetail::class.java)
                )
                cacheFile.writeText(adapter.toJson(trimmedHistory))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save error to cache", e)
            }
        }
    }

    /**
     * 清除错误历史
     */
    suspend fun clearErrorHistory() = withContext(Dispatchers.IO) {
        try {
            val cacheFile = File(context.filesDir, ERROR_CACHE_FILE)
            cacheFile.delete()
            _errorStatistics.value = ErrorStatistics()
            apiErrorCount = 0
            _isApiLikelyBroken.value = false
            _fallbackState.value = FallbackState.None
            updateAttemptedAndFailed = false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear error history", e)
        }
    }

    /**
     * 检查公告（用于通知用户微信版本更新）
     */
    suspend fun checkAnnouncement(): Announcement? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(ANNOUNCEMENT_API)
                .build()

            val response = okHttpClient.newCall(request).execute()

            if (response.isSuccessful) {
                val body = response.body?.string()
                if (body != null) {
                    val adapter = moshi.adapter(Announcement::class.java)
                    val announcement = adapter.fromJson(body)

                    // 检查公告是否针对当前版本
                    if (announcement != null && announcement.isActive()) {
                        _currentAnnouncement.value = announcement
                        // 保存修复版本信息
                        fixedInVersion = announcement.fixedInVersion
                        return@withContext announcement
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check announcement", e)
            null
        }
    }

    /**
     * 提交用户反馈
     */
    suspend fun submitFeedback(feedback: WechatUserFeedback): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // 保存反馈到本地
            saveFeedbackToCache(feedback)

            // TODO: 可以选择是否上传到服务器
            // 这里先本地保存，用户可以通过 adb pull 导出
            Log.i(TAG, "Feedback saved locally: ${feedback.title}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save feedback", e)
            Result.failure(e)
        }
    }

    /**
     * 保存反馈到本地缓存
     */
    private fun saveFeedbackToCache(feedback: WechatUserFeedback) {
        try {
            val cacheFile = File(context.filesDir, FEEDBACK_CACHE_FILE)
            val adapter = moshi.adapter(WechatUserFeedback::class.java)
            cacheFile.writeText(adapter.toJson(feedback))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save feedback to cache", e)
        }
    }

    /**
     * 生成反馈模板
     */
    fun generateFeedbackTemplate(): WechatUserFeedback {
        val errorHistory = kotlinx.coroutines.runBlocking { getErrorHistory() }
        val lastError = errorHistory.lastOrNull()

        return WechatUserFeedback(
            title = "[微信视频号] 抓取失败反馈",
            description = """
                ## 问题描述
                （请描述您遇到的问题）

                ## 操作步骤
                1. 打开微信视频号
                2. 点击某个视频
                3. 点击分享 -> 复制链接
                4. 粘贴到本应用中
                5. 点击下载

                ## 错误信息
                - 错误类型: ${lastError?.type?.name ?: "未知"}
                - 错误消息: ${lastError?.originalMessage ?: "无"}
                - 应用版本: ${BuildConfig.VERSION_NAME}
                - 微信版本: ${lastError?.platformVersion ?: "未知"}

                ## 其他信息
                （可选）提供视频链接（非必须）：
            """.trimIndent(),
            appVersion = BuildConfig.VERSION_NAME,
            wechatVersion = lastError?.platformVersion,
            errorCount = errorHistory.size,
            lastErrorType = lastError?.type?.name,
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * 重置状态
     */
    fun reset() {
        apiErrorCount = 0
        _isApiLikelyBroken.value = false
        _errorStatistics.value = ErrorStatistics()
        _fallbackState.value = FallbackState.None
        updateAttemptedAndFailed = false
        fixedInVersion = null
    }

    /**
     * 比较版本号
     */
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

/**
 * 降级方案选项
 */
data class FallbackOption(
    val id: String,
    val title: String,
    val description: String,
    val icon: String,
    val action: String  // "paste" | "hint" | "browser"
)

/**
 * 微信用户反馈数据
 */
data class WechatUserFeedback(
    val title: String,
    val description: String,
    val appVersion: String,
    val wechatVersion: String?,
    val errorCount: Int,
    val lastErrorType: String?,
    val timestamp: Long
)

/**
 * 公告数据类
 */
data class Announcement(
    val id: String,
    val title: String,
    val content: String,
    val type: AnnouncementType,
    val minAppVersion: String? = null,
    val maxAppVersion: String? = null,
    val fixedInVersion: String? = null,  // 标注在哪个版本已修复
    val startTime: Long? = null,
    val endTime: Long? = null,
    val actionUrl: String? = null,
    val actionText: String? = null
) {
    fun isActive(): Boolean {
        val now = System.currentTimeMillis()

        // 检查时间范围
        if (startTime != null && now < startTime) return false
        if (endTime != null && now > endTime) return false

        // 检查版本范围
        if (minAppVersion != null || maxAppVersion != null) {
            val currentVersion = BuildConfig.VERSION_NAME
            if (minAppVersion != null && compareVersions(currentVersion, minAppVersion) < 0) {
                return false
            }
            if (maxAppVersion != null && compareVersions(currentVersion, maxAppVersion) > 0) {
                return false
            }
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
        return fixedInVersion != null && compareVersions(currentVersion, fixedInVersion!!) < 0
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

enum class AnnouncementType {
    INFO,       // 普通信息
    WARNING,    // 警告（如微信版本更新）
    URGENT,     // 紧急（如 API 完全失效）
    UPDATE      // 更新提示
}

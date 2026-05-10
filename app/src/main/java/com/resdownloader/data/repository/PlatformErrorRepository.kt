package com.resdownloader.data.repository

import android.content.Context
import android.util.Log
import com.resdownloader.BuildConfig
import com.resdownloader.data.model.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 通用平台错误处理仓库
 * 统一处理所有平台的错误、降级方案和用户反馈
 */
@Singleton
class PlatformErrorRepository @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val moshi: Moshi,
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "PlatformErrorRepo"

        // 连续 API 错误次数阈值，超过则判定为 API 失效
        private const val API_FAILURE_THRESHOLD = 3

        // 各平台公告 API
        private const val ANNOUNCEMENT_API = "https://raw.githubusercontent.com/YanceyQian/res-downloader-android/main/announcement.json"
    }

    // 各平台的错误统计
    private val _platformStatistics = MutableStateFlow<Map<Platform, PlatformErrorStatistics>>(emptyMap())
    val platformStatistics: StateFlow<Map<Platform, PlatformErrorStatistics>> = _platformStatistics.asStateFlow()

    // 当前显示降级方案的平台
    private val _activeFallbackPlatform = MutableStateFlow<Platform?>(null)
    val activeFallbackPlatform: StateFlow<Platform?> = _activeFallbackPlatform.asStateFlow()

    // 各平台的公告
    private val _platformAnnouncements = MutableStateFlow<Map<Platform, PlatformAnnouncement>>(emptyMap())
    val platformAnnouncements: StateFlow<Map<Platform, PlatformAnnouncement>> = _platformAnnouncements.asStateFlow()

    // 用户反馈
    private val _pendingFeedback = MutableStateFlow<List<PlatformUserFeedback>>(emptyList())
    val pendingFeedback: StateFlow<List<PlatformUserFeedback>> = _pendingFeedback.asStateFlow()

    // 缓存目录
    private val cacheDir: File
        get() = File(context.filesDir, "platform_errors")

    init {
        cacheDir.mkdirs()
    }

    /**
     * 处理平台错误
     */
    fun handleError(errorDetail: PlatformErrorDetail) {
        val platform = errorDetail.platform ?: return
        Log.w(TAG, "Handling error for ${platform.displayName}: ${errorDetail.type} - ${errorDetail.originalMessage}")

        // 更新统计
        updateStatistics(platform, errorDetail)

        // 如果是 API 相关错误，检查是否达到失效阈值
        if (errorDetail.type.isApiRelated) {
            val stats = _platformStatistics.value[platform]
            if (stats != null && stats.consecutiveApiFailures >= API_FAILURE_THRESHOLD) {
                _activeFallbackPlatform.value = platform
                Log.e(TAG, "${platform.displayName} API appears to be broken!")
            }
        }

        // 持久化错误
        saveErrorToCache(errorDetail)
    }

    /**
     * 处理异常的便捷方法
     */
    fun handleException(
        platform: Platform,
        e: Exception,
        httpCode: Int? = null,
        platformVersion: String? = null,
        url: String? = null
    ): PlatformErrorDetail {
        val errorType = PlatformErrorType.fromException(e, httpCode)
        val errorDetail = PlatformErrorDetail(
            type = errorType,
            originalMessage = e.message ?: "Unknown error",
            platform = platform,
            url = url,
            httpCode = httpCode,
            platformVersion = platformVersion,
            appVersion = BuildConfig.VERSION_NAME
        )
        handleError(errorDetail)
        return errorDetail
    }

    /**
     * 处理 API 响应错误
     */
    fun handleApiError(
        platform: Platform,
        retCode: Int,
        errmsg: String? = null,
        url: String? = null
    ): PlatformErrorDetail {
        val errorType = PlatformErrorType.fromApiResponse(retCode, platform)
        val errorDetail = PlatformErrorDetail(
            type = errorType,
            originalMessage = errmsg ?: "API returned error code: $retCode",
            platform = platform,
            url = url
        )
        handleError(errorDetail)
        return errorDetail
    }

    /**
     * 更新平台错误统计
     */
    private fun updateStatistics(platform: Platform, errorDetail: PlatformErrorDetail) {
        val currentStats = _platformStatistics.value[platform] ?: PlatformErrorStatistics(platform = platform)
        val newStats = currentStats.addError(errorDetail)
        
        _platformStatistics.value = _platformStatistics.value.toMutableMap().apply {
            put(platform, newStats)
        }
    }

    /**
     * 获取平台错误统计
     */
    fun getStatistics(platform: Platform): PlatformErrorStatistics? {
        return _platformStatistics.value[platform]
    }

    /**
     * 检查平台 API 是否可能已失效
     */
    fun isApiLikelyBroken(platform: Platform): Boolean {
        return _platformStatistics.value[platform]?.isApiLikelyBroken() == true
    }

    /**
     * 获取降级方案配置
     */
    fun getFallbackConfig(platform: Platform): PlatformFallbackConfig {
        return PlatformFallbackConfigs.getConfig(platform)
    }

    /**
     * 获取降级方案选项
     */
    fun getFallbackOptions(platform: Platform): List<FallbackOptionUi> {
        val config = getFallbackConfig(platform)
        return config.alternativeMethods.mapIndexed { index, method ->
            FallbackOptionUi(
                id = method.id,
                title = method.title,
                description = method.description,
                icon = method.icon,
                isPrimary = index == 0
            )
        }
    }

    /**
     * 获取降级消息
     */
    fun getFallbackMessage(platform: Platform): String {
        val config = getFallbackConfig(platform)
        val stats = getStatistics(platform)
        val announcement = _platformAnnouncements.value[platform]
        val currentVersion = BuildConfig.VERSION_NAME

        // 检查公告中是否有修复版本
        if (announcement?.hasFix() == true) {
            if (announcement.needsUpdate(currentVersion)) {
                // 当前版本 < 修复版本
                return """
                    ${config.primaryMessage}
                    
                    请更新到 v${announcement.fixedInVersion} 或更高版本。
                    
                    如果更新后仍有问题，可以尝试以下替代方案。
                """.trimIndent()
            } else if (stats?.isApiLikelyBroken() == true) {
                // 已更新但仍失败
                return """
                    请确认${platform.displayName}已更新到最新版本。
                    
                    如仍有问题，可以尝试以下替代方案。
                """.trimIndent()
            }
        }

        // 根据错误类型生成消息
        return when (stats?.lastErrorType?.getCategory()) {
            ErrorCategory.API_ERROR -> config.primaryMessage + "\n\n请尝试以下替代方案。"
            ErrorCategory.RESOURCE_ERROR -> stats.lastErrorType.userMessage
            ErrorCategory.NETWORK_ERROR -> "网络连接不稳定，请检查网络后重试。"
            else -> "发生未知错误，请稍后重试。"
        }
    }

    /**
     * 获取用户友好的错误消息
     */
    fun getUserMessage(errorDetail: PlatformErrorDetail): String {
        val fallbackMessage = getFallbackMessage(errorDetail.platform ?: return errorDetail.getUserMessage())
        return if (errorDetail.type.requiresAppUpdate) {
            "${errorDetail.getUserMessage()}\n\n$fallbackMessage"
        } else {
            errorDetail.getUserMessage()
        }
    }

    /**
     * 清除平台错误计数（成功后调用）
     */
    fun clearErrorCount(platform: Platform) {
        val currentStats = _platformStatistics.value[platform] ?: return
        val clearedStats = currentStats.clearConsecutiveFailures()
        
        _platformStatistics.value = _platformStatistics.value.toMutableMap().apply {
            put(platform, clearedStats)
        }
        
        // 如果这个平台之前显示了降级方案，现在也隐藏
        if (_activeFallbackPlatform.value == platform) {
            _activeFallbackPlatform.value = null
        }
        
        Log.d(TAG, "${platform.displayName} error count cleared")
    }

    /**
     * 关闭降级方案对话框
     */
    fun dismissFallbackDialog() {
        _activeFallbackPlatform.value = null
    }

    /**
     * 检查平台公告
     */
    suspend fun checkAnnouncement(platform: Platform): PlatformAnnouncement? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(ANNOUNCEMENT_API)
                .build()

            val response = okHttpClient.newCall(request).execute()

            if (response.isSuccessful) {
                val body = response.body?.string()
                if (body != null) {
                    // 解析公告列表
                    val type = Types.newParameterizedType(List::class.java, PlatformAnnouncement::class.java)
                    val adapter = moshi.adapter<List<PlatformAnnouncement>>(type)
                    val announcements = adapter.fromJson(body)

                    // 找到针对指定平台的公告
                    val platformAnnouncement = announcements?.find { 
                        it.platform == platform && it.isActive(BuildConfig.VERSION_NAME)
                    }

                    if (platformAnnouncement != null) {
                        // 更新状态
                        _platformAnnouncements.value = _platformAnnouncements.value.toMutableMap().apply {
                            put(platform, platformAnnouncement)
                        }
                        return@withContext platformAnnouncement
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check announcement for $platform", e)
            null
        }
    }

    /**
     * 生成反馈模板
     */
    fun generateFeedback(platform: Platform, errorDetail: PlatformErrorDetail): PlatformUserFeedback {
        val config = getFallbackConfig(platform)
        val feedbackContent = config.feedbackTemplate.format(
            errorDetail.type.name,
            errorDetail.originalMessage,
            BuildConfig.VERSION_NAME,
            errorDetail.platformVersion ?: "未知"
        )

        return PlatformUserFeedback(
            title = "[${platform.displayName}] 抓取失败反馈",
            description = feedbackContent,
            platform = platform,
            appVersion = BuildConfig.VERSION_NAME,
            platformVersion = errorDetail.platformVersion,
            errorType = errorDetail.type.name,
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * 保存反馈
     */
    suspend fun saveFeedback(feedback: PlatformUserFeedback): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val current = _pendingFeedback.value.toMutableList()
            current.add(feedback)
            _pendingFeedback.value = current

            // 同时保存到本地
            saveFeedbackToCache(feedback)
            
            Log.i(TAG, "Feedback saved: ${feedback.title}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save feedback", e)
            Result.failure(e)
        }
    }

    /**
     * 从缓存加载反馈
     */
    suspend fun loadFeedbackFromCache(): List<PlatformUserFeedback> = withContext(Dispatchers.IO) {
        try {
            val cacheFile = File(cacheDir, "feedback.json")
            if (!cacheFile.exists()) return@withContext emptyList()

            val json = cacheFile.readText()
            val type = Types.newParameterizedType(List::class.java, PlatformUserFeedback::class.java)
            val adapter = moshi.adapter<List<PlatformUserFeedback>>(type)
            adapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load feedback from cache", e)
            emptyList()
        }
    }

    /**
     * 保存反馈到缓存
     */
    private fun saveFeedbackToCache(feedback: PlatformUserFeedback) {
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
            try {
                val cacheFile = File(cacheDir, "feedback.json")
                val current = loadFeedbackFromCache().toMutableList()
                current.add(feedback)
                
                val type = Types.newParameterizedType(List::class.java, PlatformUserFeedback::class.java)
                val adapter = moshi.adapter<List<PlatformUserFeedback>>(type)
                cacheFile.writeText(adapter.toJson(current))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save feedback to cache", e)
            }
        }
    }

    /**
     * 获取错误历史
     */
    suspend fun getErrorHistory(platform: Platform): List<PlatformErrorDetail> = withContext(Dispatchers.IO) {
        try {
            val cacheFile = File(cacheDir, "${platform.name.lowercase()}_errors.json")
            if (!cacheFile.exists()) return@withContext emptyList()

            val json = cacheFile.readText()
            val type = Types.newParameterizedType(List::class.java, PlatformErrorDetail::class.java)
            val adapter = moshi.adapter<List<PlatformErrorDetail>>(type)
            adapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load error history", e)
            emptyList()
        }
    }

    /**
     * 保存错误到缓存
     */
    private fun saveErrorToCache(errorDetail: PlatformErrorDetail) {
        val platform = errorDetail.platform ?: return
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
            try {
                val cacheFile = File(cacheDir, "${platform.name.lowercase()}_errors.json")
                val history = getErrorHistory(platform).toMutableList()
                history.add(errorDetail)

                // 只保留最近 100 条
                val trimmedHistory = history.takeLast(100)

                val type = Types.newParameterizedType(List::class.java, PlatformErrorDetail::class.java)
                val adapter = moshi.adapter<List<PlatformErrorDetail>>(type)
                cacheFile.writeText(adapter.toJson(trimmedHistory))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save error to cache", e)
            }
        }
    }

    /**
     * 清除平台错误历史
     */
    suspend fun clearErrorHistory(platform: Platform) = withContext(Dispatchers.IO) {
        try {
            val cacheFile = File(cacheDir, "${platform.name.lowercase()}_errors.json")
            cacheFile.delete()
            
            // 清除内存中的统计
            _platformStatistics.value = _platformStatistics.value.toMutableMap().apply {
                remove(platform)
            }
            _activeFallbackPlatform.value = null
            
            Log.d(TAG, "${platform.displayName} error history cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear error history", e)
        }
    }

    /**
     * 重置所有状态
     */
    fun reset() {
        _platformStatistics.value = emptyMap()
        _activeFallbackPlatform.value = null
        _platformAnnouncements.value = emptyMap()
        Log.d(TAG, "All platform error states reset")
    }
}

/**
 * 平台用户反馈数据
 */
data class PlatformUserFeedback(
    val title: String,
    val description: String,
    val platform: Platform,
    val appVersion: String,
    val platformVersion: String?,
    val errorType: String?,
    val timestamp: Long
)

/**
 * 降级方案选项 UI 模型
 */
data class FallbackOptionUi(
    val id: String,
    val title: String,
    val description: String,
    val icon: String,
    val isPrimary: Boolean = false
)

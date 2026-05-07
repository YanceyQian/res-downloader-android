package com.resdownloader.data.preferences

import android.content.Context
import com.resdownloader.data.model.MimeDefaults
import com.resdownloader.data.model.MimeInfo
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val PREFS_NAME = "settings"
        
        // 设置项 Key
        private const val KEY_LANGUAGE = "lang"
        private const val KEY_DOWNLOAD_PATH = "download_path"
        private const val KEY_PROXY_PORT = "proxy_port"
        private const val KEY_LAST_VERSION = "last_version"
        private const val KEY_CERTIFICATE_INSTALLED = "certificate_installed"
        
        // 新增设置项 Key
        private const val KEY_THEME = "theme"
        private const val KEY_HOST = "host"
        private const val KEY_QUALITY = "quality"
        private const val KEY_FILENAME_LEN = "filename_len"
        private const val KEY_FILENAME_TIME = "filename_time"
        private const val KEY_UPSTREAM_PROXY = "upstream_proxy"
        private const val KEY_OPEN_PROXY = "open_proxy"
        private const val KEY_DOWNLOAD_PROXY = "download_proxy"
        private const val KEY_AUTO_PROXY = "auto_proxy"
        private const val KEY_WX_ACTION = "wx_action"
        private const val KEY_TASK_NUMBER = "task_number"
        private const val KEY_DOWN_NUMBER = "down_number"
        private const val KEY_USER_AGENT = "user_agent"
        private const val KEY_USE_HEADERS = "use_headers"
        private const val KEY_INSERT_TAIL = "insert_tail"
        private const val KEY_RULE = "rule"
        private const val KEY_MIME_MAP = "mime_map"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    // 语言相关
    private val _language = MutableStateFlow(getLanguage())
    val language: Flow<String> = _language
    
    private val _downloadPath = MutableStateFlow(getDownloadPath())
    val downloadPath: Flow<String> = _downloadPath
    
    private val _proxyPort = MutableStateFlow(getProxyPort())
    val proxyPort: Flow<Int> = _proxyPort
    
    private val _lastVersion = MutableStateFlow(getLastVersion())
    val lastVersion: Flow<String> = _lastVersion
    
    private val _certificateInstalled = MutableStateFlow(isCertificateInstalled())
    val certificateInstalled: Flow<Boolean> = _certificateInstalled
    
    // 新增设置项
    private val _theme = MutableStateFlow(getTheme())
    val theme: Flow<String> = _theme
    
    private val _host = MutableStateFlow(getHost())
    val host: Flow<String> = _host
    
    private val _quality = MutableStateFlow(getQuality())
    val quality: Flow<Int> = _quality
    
    private val _filenameLen = MutableStateFlow(getFilenameLen())
    val filenameLen: Flow<Int> = _filenameLen
    
    private val _filenameTime = MutableStateFlow(getFilenameTime())
    val filenameTime: Flow<Boolean> = _filenameTime
    
    private val _upstreamProxy = MutableStateFlow(getUpstreamProxy())
    val upstreamProxy: Flow<String> = _upstreamProxy
    
    private val _openProxy = MutableStateFlow(getOpenProxy())
    val openProxy: Flow<Boolean> = _openProxy
    
    private val _downloadProxy = MutableStateFlow(getDownloadProxy())
    val downloadProxy: Flow<Boolean> = _downloadProxy
    
    private val _autoProxy = MutableStateFlow(getAutoProxy())
    val autoProxy: Flow<Boolean> = _autoProxy
    
    private val _wxAction = MutableStateFlow(getWxAction())
    val wxAction: Flow<Boolean> = _wxAction
    
    private val _taskNumber = MutableStateFlow(getTaskNumber())
    val taskNumber: Flow<Int> = _taskNumber
    
    private val _downNumber = MutableStateFlow(getDownNumber())
    val downNumber: Flow<Int> = _downNumber
    
    private val _userAgent = MutableStateFlow(getUserAgent())
    val userAgent: Flow<String> = _userAgent
    
    private val _useHeaders = MutableStateFlow(getUseHeaders())
    val useHeaders: Flow<String> = _useHeaders
    
    private val _insertTail = MutableStateFlow(getInsertTail())
    val insertTail: Flow<Boolean> = _insertTail
    
    private val _rule = MutableStateFlow(getRule())
    val rule: Flow<String> = _rule
    
    private val _mimeMap = MutableStateFlow(getMimeMap())
    val mimeMap: Flow<Map<String, com.resdownloader.data.model.MimeInfo>> = _mimeMap

    // 语言相关
    private fun getLanguage(): String {
        return prefs.getString(KEY_LANGUAGE, "zh") ?: "zh"
    }

    fun getLanguageSync(): String {
        return getLanguage()
    }

    suspend fun setLanguage(lang: String) {
        // 使用 commit() 同步保存，确保数据写入完成后再继续
        prefs.edit().putString(KEY_LANGUAGE, lang).commit()
        _language.value = lang
    }

    fun setLanguageSync(lang: String) {
        // 使用 commit() 同步保存，确保数据写入完成后再继续
        prefs.edit().putString(KEY_LANGUAGE, lang).commit()
        _language.value = lang
    }

    // 下载路径
    private fun getDownloadPath(): String {
        val defaultPath = try {
            context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)?.absolutePath
                ?: (context.filesDir.absolutePath + "/downloads")
        } catch (e: Exception) {
            context.filesDir.absolutePath + "/downloads"
        }
        return prefs.getString(KEY_DOWNLOAD_PATH, defaultPath) ?: defaultPath
    }

    // 同步获取下载路径
    fun getDownloadPathSync(): String {
        val defaultPath = try {
            context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)?.absolutePath
                ?: (context.filesDir.absolutePath + "/downloads")
        } catch (e: Exception) {
            context.filesDir.absolutePath + "/downloads"
        }
        return prefs.getString(KEY_DOWNLOAD_PATH, defaultPath) ?: defaultPath
    }
    
    // 同步获取代理端口
    fun getProxyPortSync(): Int {
        return prefs.getInt(KEY_PROXY_PORT, 8899)
    }
    
    // 同步获取是否添加到尾部
    fun getInsertTailSync(): Boolean {
        return prefs.getBoolean(KEY_INSERT_TAIL, true)
    }

    suspend fun setDownloadPath(path: String) {
        prefs.edit().putString(KEY_DOWNLOAD_PATH, path).apply()
        _downloadPath.value = path
    }

    // 代理端口
    private fun getProxyPort(): Int {
        return prefs.getInt(KEY_PROXY_PORT, 8899)
    }

    suspend fun setProxyPort(port: Int) {
        prefs.edit().putInt(KEY_PROXY_PORT, port).apply()
        _proxyPort.value = port
    }

    // 最后版本
    private fun getLastVersion(): String {
        return prefs.getString(KEY_LAST_VERSION, "") ?: ""
    }

    suspend fun setLastVersion(version: String) {
        prefs.edit().putString(KEY_LAST_VERSION, version).apply()
        _lastVersion.value = version
    }

    // 证书安装
    private fun isCertificateInstalled(): Boolean {
        return prefs.getBoolean(KEY_CERTIFICATE_INSTALLED, false)
    }

    suspend fun setCertificateInstalled(installed: Boolean) {
        prefs.edit().putBoolean(KEY_CERTIFICATE_INSTALLED, installed).apply()
        _certificateInstalled.value = installed
    }
    
    // 主题
    private fun getTheme(): String {
        return prefs.getString(KEY_THEME, "lightTheme") ?: "lightTheme"
    }
    
    suspend fun setTheme(theme: String) {
        prefs.edit().putString(KEY_THEME, theme).apply()
        _theme.value = theme
    }
    
    // Host
    private fun getHost(): String {
        return prefs.getString(KEY_HOST, "127.0.0.1") ?: "127.0.0.1"
    }

    fun getHostSync(): String {
        return getHost()
    }
    
    suspend fun setHost(host: String) {
        prefs.edit().putString(KEY_HOST, host).apply()
        _host.value = host
    }
    
    // 质量
    private fun getQuality(): Int {
        return prefs.getInt(KEY_QUALITY, 0)
    }
    
    suspend fun setQuality(quality: Int) {
        prefs.edit().putInt(KEY_QUALITY, quality).apply()
        _quality.value = quality
    }
    
    // 文件名长度
    private fun getFilenameLen(): Int {
        return prefs.getInt(KEY_FILENAME_LEN, 0)
    }
    
    suspend fun setFilenameLen(len: Int) {
        prefs.edit().putInt(KEY_FILENAME_LEN, len).apply()
        _filenameLen.value = len
    }
    
    // 文件名时间
    private fun getFilenameTime(): Boolean {
        return prefs.getBoolean(KEY_FILENAME_TIME, true)
    }
    
    suspend fun setFilenameTime(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FILENAME_TIME, enabled).apply()
        _filenameTime.value = enabled
    }
    
    // 上游代理
    private fun getUpstreamProxy(): String {
        return prefs.getString(KEY_UPSTREAM_PROXY, "") ?: ""
    }
    
    suspend fun setUpstreamProxy(proxy: String) {
        prefs.edit().putString(KEY_UPSTREAM_PROXY, proxy).apply()
        _upstreamProxy.value = proxy
    }
    
    // 开启代理
    private fun getOpenProxy(): Boolean {
        return prefs.getBoolean(KEY_OPEN_PROXY, false)
    }
    
    suspend fun setOpenProxy(open: Boolean) {
        prefs.edit().putBoolean(KEY_OPEN_PROXY, open).apply()
        _openProxy.value = open
    }
    
    // 下载使用代理
    private fun getDownloadProxy(): Boolean {
        return prefs.getBoolean(KEY_DOWNLOAD_PROXY, false)
    }
    
    suspend fun setDownloadProxy(open: Boolean) {
        prefs.edit().putBoolean(KEY_DOWNLOAD_PROXY, open).apply()
        _downloadProxy.value = open
    }
    
    // 自动代理
    private fun getAutoProxy(): Boolean {
        return prefs.getBoolean(KEY_AUTO_PROXY, false)
    }
    
    suspend fun setAutoProxy(open: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_PROXY, open).apply()
        _autoProxy.value = open
    }
    
    // 微信操作
    private fun getWxAction(): Boolean {
        return prefs.getBoolean(KEY_WX_ACTION, true)
    }
    
    suspend fun setWxAction(open: Boolean) {
        prefs.edit().putBoolean(KEY_WX_ACTION, open).apply()
        _wxAction.value = open
    }
    
    // 任务数
    private fun getTaskNumber(): Int {
        return prefs.getInt(KEY_TASK_NUMBER, 8)
    }
    
    suspend fun setTaskNumber(num: Int) {
        prefs.edit().putInt(KEY_TASK_NUMBER, num).apply()
        _taskNumber.value = num
    }
    
    // 下载数
    private fun getDownNumber(): Int {
        return prefs.getInt(KEY_DOWN_NUMBER, 3)
    }

    fun getDownNumberSync(): Int {
        return getDownNumber()
    }

    suspend fun setDownNumber(num: Int) {
        prefs.edit().putInt(KEY_DOWN_NUMBER, num).apply()
        _downNumber.value = num
    }
    
    // UserAgent
    private fun getUserAgent(): String {
        return prefs.getString(KEY_USER_AGENT, "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36") ?: ""
    }

    fun getUserAgentSync(): String {
        return getUserAgent()
    }

    suspend fun setUserAgent(ua: String) {
        prefs.edit().putString(KEY_USER_AGENT, ua).apply()
        _userAgent.value = ua
    }
    
    // 使用的Headers
    private fun getUseHeaders(): String {
        return prefs.getString(KEY_USE_HEADERS, "default") ?: "default"
    }
    
    suspend fun setUseHeaders(headers: String) {
        prefs.edit().putString(KEY_USE_HEADERS, headers).apply()
        _useHeaders.value = headers
    }
    
    // 插入尾部
    private fun getInsertTail(): Boolean {
        return prefs.getBoolean(KEY_INSERT_TAIL, true)
    }
    
    suspend fun setInsertTail(open: Boolean) {
        prefs.edit().putBoolean(KEY_INSERT_TAIL, open).apply()
        _insertTail.value = open
    }
    
    // 规则
    private fun getRule(): String {
        return prefs.getString(KEY_RULE, defaultRule) ?: defaultRule
    }
    
    private val defaultRule = """*
*.qq.com
video.qq.com
*.douyin.com
*.kuaishou.com
*.xiaohongshu.com
*.bilibili.com
*.kugou.com
y.qq.com

# 排除
!static.qq.com"""
    
    suspend fun setRule(rule: String) {
        prefs.edit().putString(KEY_RULE, rule).apply()
        _rule.value = rule
    }
    
    // MIME类型映射
    private fun getMimeMap(): Map<String, MimeInfo> {
        val json = prefs.getString(KEY_MIME_MAP, null)
        return if (json != null) {
            try {
                val moshi = Moshi.Builder().build()
                val type = Types.newParameterizedType(Map::class.java, String::class.java, MimeInfo::class.java)
                val adapter = moshi.adapter<Map<String, MimeInfo>>(type)
                adapter.fromJson(json) ?: MimeDefaults.defaultMimeMap
            } catch (e: Exception) {
                MimeDefaults.defaultMimeMap
            }
        } else {
            MimeDefaults.defaultMimeMap
        }
    }
    
    suspend fun setMimeMap(mimeMap: Map<String, MimeInfo>) {
        try {
            val moshi = Moshi.Builder().build()
            val type = Types.newParameterizedType(Map::class.java, String::class.java, MimeInfo::class.java)
            val adapter = moshi.adapter<Map<String, MimeInfo>>(type)
            val json = adapter.toJson(mimeMap)
            prefs.edit().putString(KEY_MIME_MAP, json).apply()
            _mimeMap.value = mimeMap
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    fun getMimeMapSync(): Map<String, MimeInfo> {
        return getMimeMap()
    }
}

package com.resdownloader.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resdownloader.BuildConfig
import com.resdownloader.data.model.AssetInfo
import com.resdownloader.data.model.MimeDefaults
import com.resdownloader.data.model.MimeInfo
import com.resdownloader.data.model.VersionInfo
import com.resdownloader.data.preferences.PreferencesManager
import com.resdownloader.data.repository.UpdateRepository
import com.resdownloader.data.repository.UpdateState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val updateRepository: UpdateRepository
) : ViewModel() {



    // 基础设置
    val downloadPath = preferencesManager.downloadPath
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val proxyPort = preferencesManager.proxyPort
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 8899)

    val language = preferencesManager.language
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "zh")

    val certificateInstalled = preferencesManager.certificateInstalled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // 新增设置项
    val theme = preferencesManager.theme
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "lightTheme")

    val host = preferencesManager.host
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "127.0.0.1")

    val quality = preferencesManager.quality
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val filenameLen = preferencesManager.filenameLen
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val filenameTime = preferencesManager.filenameTime
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val upstreamProxy = preferencesManager.upstreamProxy
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val openProxy = preferencesManager.openProxy
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val downloadProxy = preferencesManager.downloadProxy
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val autoProxy = preferencesManager.autoProxy
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val wxAction = preferencesManager.wxAction
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val taskNumber = preferencesManager.taskNumber
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 8)

    val downNumber = preferencesManager.downNumber
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 3)

    val userAgent = preferencesManager.userAgent
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36")

    val useHeaders = preferencesManager.useHeaders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "default")

    val insertTail = preferencesManager.insertTail
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val rule = preferencesManager.rule
        .stateIn(
            viewModelScope, 
            SharingStarted.WhileSubscribed(5000), 
            """*
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
        )

    val mimeMap = preferencesManager.mimeMap
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val updateState = updateRepository.updateState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UpdateState.Idle)

    val latestVersion = updateRepository.latestVersion
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val currentVersion = BuildConfig.VERSION_NAME

    private val _uiEvent = MutableSharedFlow<UiEvent>()
    val uiEvent: SharedFlow<UiEvent> = _uiEvent.asSharedFlow()

    // 基础设置方法
    fun setDownloadPath(path: String) {
        viewModelScope.launch {
            preferencesManager.setDownloadPath(path)
        }
    }

    fun setProxyPort(port: Int) {
        viewModelScope.launch {
            if (port in 1024..65535) {
                preferencesManager.setProxyPort(port)
            } else {
                _uiEvent.emit(UiEvent.Error("端口号无效，请输入 1024-65535 之间的值"))
            }
        }
    }

    fun setLanguage(lang: String) {
        viewModelScope.launch {
            preferencesManager.setLanguage(lang)
            _uiEvent.emit(UiEvent.LanguageChanged(lang))
        }
    }

    fun setCertificateInstalled(installed: Boolean) {
        viewModelScope.launch {
            preferencesManager.setCertificateInstalled(installed)
        }
    }

    // 新增设置方法
    fun setTheme(theme: String) {
        viewModelScope.launch {
            preferencesManager.setTheme(theme)
        }
    }

    fun setHost(host: String) {
        viewModelScope.launch {
            preferencesManager.setHost(host)
        }
    }

    fun setQuality(quality: Int) {
        viewModelScope.launch {
            preferencesManager.setQuality(quality)
        }
    }

    fun setFilenameLen(len: Int) {
        viewModelScope.launch {
            preferencesManager.setFilenameLen(len)
        }
    }

    fun setFilenameTime(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setFilenameTime(enabled)
        }
    }

    fun setUpstreamProxy(proxy: String) {
        viewModelScope.launch {
            preferencesManager.setUpstreamProxy(proxy)
        }
    }

    fun setOpenProxy(open: Boolean) {
        viewModelScope.launch {
            preferencesManager.setOpenProxy(open)
        }
    }

    fun setDownloadProxy(open: Boolean) {
        viewModelScope.launch {
            preferencesManager.setDownloadProxy(open)
        }
    }

    fun setAutoProxy(open: Boolean) {
        viewModelScope.launch {
            preferencesManager.setAutoProxy(open)
        }
    }

    fun setWxAction(open: Boolean) {
        viewModelScope.launch {
            preferencesManager.setWxAction(open)
        }
    }

    fun setTaskNumber(num: Int) {
        viewModelScope.launch {
            preferencesManager.setTaskNumber(num)
        }
    }

    fun setDownNumber(num: Int) {
        viewModelScope.launch {
            preferencesManager.setDownNumber(num)
        }
    }

    fun setUserAgent(ua: String) {
        viewModelScope.launch {
            preferencesManager.setUserAgent(ua)
        }
    }

    fun setUseHeaders(headers: String) {
        viewModelScope.launch {
            preferencesManager.setUseHeaders(headers)
        }
    }

    fun setInsertTail(open: Boolean) {
        viewModelScope.launch {
            preferencesManager.setInsertTail(open)
        }
    }

    fun setRule(rule: String) {
        viewModelScope.launch {
            preferencesManager.setRule(rule)
        }
    }

    fun setMimeMap(mimeMap: Map<String, MimeInfo>) {
        viewModelScope.launch {
            preferencesManager.setMimeMap(mimeMap)
        }
    }

    // 更新相关方法
    fun checkForUpdates() {
        viewModelScope.launch {
            val result = updateRepository.checkForUpdates()
            result.fold(
                onSuccess = { versionInfo ->
                    _uiEvent.emit(UiEvent.UpdateAvailable(versionInfo))
                },
                onFailure = { error ->
                    if (error.message?.contains("Already up to date") == true) {
                        _uiEvent.emit(UiEvent.NoUpdate)
                    } else {
                        _uiEvent.emit(UiEvent.Error(error.message ?: "检查更新失败"))
                    }
                }
            )
        }
    }

    fun downloadUpdate(asset: AssetInfo) {
        viewModelScope.launch {
            updateRepository.downloadUpdate(asset, onProgress = { }).fold(
                onSuccess = { filePath ->
                    _uiEvent.emit(UiEvent.UpdateDownloaded(filePath))
                },
                onFailure = { error ->
                    _uiEvent.emit(UiEvent.Error(error.message ?: "下载更新失败"))
                }
            )
        }
    }

    fun resetUpdateState() {
        updateRepository.resetState()
    }

    // ==================== 恢复默认设置方法 ====================

    /**
     * 恢复所有设置到默认值
     */
    fun resetAllSettings() {
        viewModelScope.launch {
            preferencesManager.setHost("127.0.0.1")
            preferencesManager.setProxyPort(8899)
            preferencesManager.setTheme("lightTheme")
            preferencesManager.setQuality(0)
            preferencesManager.setFilenameLen(0)
            preferencesManager.setFilenameTime(true)
            preferencesManager.setUpstreamProxy("")
            preferencesManager.setOpenProxy(false)
            preferencesManager.setDownloadProxy(false)
            preferencesManager.setAutoProxy(false)
            preferencesManager.setWxAction(true)
            preferencesManager.setTaskNumber(8)
            preferencesManager.setDownNumber(3)
            preferencesManager.setUserAgent("")
            preferencesManager.setUseHeaders("default")
            preferencesManager.setInsertTail(true)
            // 恢复规则
            setRule(getDefaultRule())
            // 恢复MIME映射
            setMimeMap(MimeDefaults.defaultMimeMap)
        }
    }

    /**
     * 仅恢复域名规则
     */
    fun resetRule() {
        viewModelScope.launch {
            setRule(getDefaultRule())
        }
    }

    /**
     * 仅恢复拦截规则（MIME映射）
     */
    fun resetMimeMap() {
        viewModelScope.launch {
            setMimeMap(MimeDefaults.defaultMimeMap)
        }
    }

    /**
     * 仅恢复代理设置
     */
    fun resetProxySettings() {
        viewModelScope.launch {
            preferencesManager.setHost("127.0.0.1")
            preferencesManager.setProxyPort(8899)
            preferencesManager.setUpstreamProxy("")
            preferencesManager.setOpenProxy(false)
            preferencesManager.setDownloadProxy(false)
            preferencesManager.setAutoProxy(false)
        }
    }

    /**
     * 获取默认域名规则
     */
    private fun getDefaultRule(): String = """*
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

    sealed class UiEvent {
        data class Error(val message: String) : UiEvent()
        data class UpdateAvailable(val versionInfo: VersionInfo) : UiEvent()
        data class UpdateDownloaded(val filePath: String) : UiEvent()
        object NoUpdate : UiEvent()
        data class LanguageChanged(val lang: String) : UiEvent()
    }
}

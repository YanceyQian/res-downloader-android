package com.resdownloader.data.preferences

import android.content.Context
import com.resdownloader.R
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LanguageManager @Inject constructor(
    private val preferencesManager: PreferencesManager
) {

    private val _currentLanguage = MutableStateFlow(preferencesManager.getLanguageSync())
    val currentLanguage: Flow<String> = _currentLanguage.asStateFlow()

    fun setLanguage(lang: String) {
        preferencesManager.setLanguageSync(lang)
        _currentLanguage.value = lang
    }

    fun getString(context: Context, resId: Int): String {
        val lang = _currentLanguage.value
        return when (lang) {
            "en" -> getEnglishString(resId) ?: context.getString(resId)
            else -> context.getString(resId)
        }
    }

    private fun getEnglishString(resId: Int): String? {
        return when (resId) {
            R.string.app_name -> "Resource Downloader"
            R.string.home -> "Home"
            R.string.download -> "Download"
            R.string.settings -> "Settings"
            R.string.theme -> "Theme"
            R.string.language -> "Language"
            R.string.download_path -> "Download Path"
            R.string.quality -> "Quality"
            R.string.filename_time -> "Filename Timestamp"
            R.string.filename_len -> "Filename Length"
            R.string.host -> "Host"
            R.string.proxy_port -> "Proxy Port"
            R.string.auto_proxy -> "Auto Proxy"
            R.string.upstream_proxy -> "Upstream Proxy"
            R.string.download_proxy -> "Download Proxy"
            R.string.full_intercept -> "Full Intercept"
            R.string.insert_tail -> "Insert Tail"
            R.string.task_number -> "Task Number"
            R.string.down_number -> "Download Number"
            R.string.user_agent -> "User Agent"
            R.string.use_headers -> "Use Headers"
            R.string.domain_rule -> "Domain Rule"
            R.string.install_certificate -> "Install Certificate"
            R.string.battery_optimization -> "Battery Optimization"
            R.string.check_update -> "Check Update"
            R.string.about -> "About"
            R.string.version -> "Version"
            R.string.basic_setting -> "Basic Settings"
            R.string.network -> "Network"
            R.string.advanced_setting -> "Advanced Settings"
            R.string.about_section -> "About"
            R.string.confirm -> "Confirm"
            R.string.cancel -> "Cancel"
            R.string.no_update -> "No updates available"
            R.string.filename_rules_tip -> "Controls filename length (0 for unlimited)"
            R.string.auto_proxy_tip -> "Auto enable proxy when app starts"
            R.string.download_proxy_tip -> "Use upstream proxy for downloads"
            R.string.full_intercept_tip -> "Full interception for WeChat video accounts"
            R.string.insert_tail_tip -> "Add new data to list tail"
            R.string.about_description -> "A network resource sniffing and high-speed download tool."
            R.string.about_support -> "Supported platforms:"
            R.string.about_application -> "WeChat Video, Douyin, Kuaishou, Xiaohongshu, Bilibili, Kugou Music, QQ Music..."
            else -> null
        }
    }
}
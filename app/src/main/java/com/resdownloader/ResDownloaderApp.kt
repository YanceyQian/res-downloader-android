package com.resdownloader

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import dagger.hilt.android.HiltAndroidApp
import java.util.Locale

@HiltAndroidApp
class ResDownloaderApp : Application() {

    companion object {
        lateinit var instance: ResDownloaderApp
            private set

        // 统一使用 settings SharedPreferences，与 PreferencesManager 保持一致
        private const val PREFS_NAME = "settings"
        private const val KEY_LANGUAGE = "lang"

        fun getLang(context: Context): String {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_LANGUAGE, "zh") ?: "zh"
        }

        fun setLang(context: Context, lang: String) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_LANGUAGE, lang).apply()
        }

        fun applyLang(context: Context): Context {
            val lang = getLang(context)
            val locale = if (lang == "en") Locale.ENGLISH else Locale.SIMPLIFIED_CHINESE
            Locale.setDefault(locale)

            val config = Configuration(context.resources.configuration)
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                config.setLocale(locale)
                context.createConfigurationContext(config)
            } else {
                @Suppress("DEPRECATION")
                config.locale = locale
                @Suppress("DEPRECATION")
                context.resources.updateConfiguration(config, context.resources.displayMetrics)
                context
            }
        }

        fun restart(context: Context) {
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            context.startActivity(intent)
            // 不再杀进程，让系统自然关闭当前 Activity
        }
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(applyLang(base))
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}

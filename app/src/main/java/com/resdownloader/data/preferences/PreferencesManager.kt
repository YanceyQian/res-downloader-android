package com.resdownloader.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore

    companion object {
        private val DOWNLOAD_PATH = stringPreferencesKey("download_path")
        private val PROXY_PORT = intPreferencesKey("proxy_port")
        private val LANGUAGE = stringPreferencesKey("language")
        private val LAST_VERSION = stringPreferencesKey("last_version")
        private val CERTIFICATE_INSTALLED = booleanPreferencesKey("certificate_installed")
    }

    val downloadPath: Flow<String> = dataStore.data.map { preferences ->
        preferences[DOWNLOAD_PATH] ?: "/storage/emulated/0/Download/ResDownloader"
    }

    val proxyPort: Flow<Int> = dataStore.data.map { preferences ->
        preferences[PROXY_PORT] ?: 8899
    }

    val language: Flow<String> = dataStore.data.map { preferences ->
        preferences[LANGUAGE] ?: "zh"
    }

    val lastVersion: Flow<String> = dataStore.data.map { preferences ->
        preferences[LAST_VERSION] ?: ""
    }

    val certificateInstalled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[CERTIFICATE_INSTALLED] ?: false
    }

    suspend fun setDownloadPath(path: String) {
        dataStore.edit { preferences ->
            preferences[DOWNLOAD_PATH] = path
        }
    }

    suspend fun setProxyPort(port: Int) {
        dataStore.edit { preferences ->
            preferences[PROXY_PORT] = port
        }
    }

    suspend fun setLanguage(lang: String) {
        dataStore.edit { preferences ->
            preferences[LANGUAGE] = lang
        }
    }

    suspend fun setLastVersion(version: String) {
        dataStore.edit { preferences ->
            preferences[LAST_VERSION] = version
        }
    }

    suspend fun setCertificateInstalled(installed: Boolean) {
        dataStore.edit { preferences ->
            preferences[CERTIFICATE_INSTALLED] = installed
        }
    }
}

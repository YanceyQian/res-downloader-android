package com.resdownloader.data.repository

import android.content.Context
import com.resdownloader.BuildConfig
import com.resdownloader.data.model.AssetInfo
import com.resdownloader.data.model.VersionInfo
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateRepository @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val moshi: Moshi,
    private val context: Context
) {
    companion object {
        private val CURRENT_VERSION: String get() = BuildConfig.VERSION_NAME
        private const val GITHUB_RELEASES_API = "https://api.github.com/repos/YanceyQian/res-downloader-android/releases/latest"
        private const val GITHUB_REPO = "https://github.com/YanceyQian/res-downloader-android"
    }

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private val _latestVersion = MutableStateFlow<VersionInfo?>(null)
    val latestVersion: StateFlow<VersionInfo?> = _latestVersion.asStateFlow()

    suspend fun checkForUpdates(): Result<VersionInfo> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(GITHUB_RELEASES_API)
                .header("Accept", "application/vnd.github.v3+json")
                .build()

            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Failed to fetch: ${response.code}"))
            }

            val body = response.body?.string()
                ?: return@withContext Result.failure(Exception("Empty response"))

            val versionInfo = parseVersionInfo(body)
            _latestVersion.value = versionInfo

            val currentVersion = normalizeVersion(CURRENT_VERSION)
            val latestVersionNorm = normalizeVersion(versionInfo.tagName)

            if (compareVersions(currentVersion, latestVersionNorm) < 0) {
                Result.success(versionInfo)
            } else {
                Result.failure(Exception("Already up to date"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadUpdate(asset: AssetInfo, onProgress: (Int) -> Unit): Result<String> = withContext(Dispatchers.IO) {
        try {
            _updateState.value = UpdateState.Downloading(0)

            val request = Request.Builder()
                .url(asset.downloadUrl)
                .build()

            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                _updateState.value = UpdateState.Error("Download failed: ${response.code}")
                return@withContext Result.failure(Exception("Download failed"))
            }

            val body = response.body
                ?: return@withContext Result.failure(Exception("Empty response"))

            val totalBytes = body.contentLength()
            var downloadedBytes = 0L

            // 使用应用缓存目录，兼容非Root设备
            val cacheDir = context.cacheDir
            val apkFile = File(cacheDir, "update.apk")

            body.byteStream().use { input ->
                apkFile.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        if (totalBytes > 0) {
                            val progress = ((downloadedBytes * 100) / totalBytes).toInt()
                            _updateState.value = UpdateState.Downloading(progress)
                            onProgress(progress)
                        }
                    }
                }
            }

            _updateState.value = UpdateState.Downloaded(apkFile.absolutePath)
            Result.success(apkFile.absolutePath)
        } catch (e: Exception) {
            _updateState.value = UpdateState.Error(e.message ?: "Download failed")
            Result.failure(e)
        }
    }

    fun resetState() {
        _updateState.value = UpdateState.Idle
    }

    private fun parseVersionInfo(json: String): VersionInfo {
        val jsonMap = moshi.adapter<Map<String, Any>>(
            Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
        ).fromJson(json) ?: emptyMap()

        val assets = (jsonMap["assets"] as? List<Map<String, Any>>)?.map { assetMap ->
            AssetInfo(
                name = assetMap["name"] as? String ?: "",
                downloadUrl = assetMap["browser_download_url"] as? String ?: "",
                size = (assetMap["size"] as? Double)?.toLong() ?: 0
            )
        }?.filter { it.name.endsWith(".apk") } ?: emptyList()

        return VersionInfo(
            tagName = jsonMap["tag_name"] as? String ?: "",
            name = jsonMap["name"] as? String ?: "",
            body = jsonMap["body"] as? String ?: "",
            htmlUrl = jsonMap["html_url"] as? String ?: GITHUB_REPO,
            assets = assets
        )
    }

    private fun normalizeVersion(version: String): String {
        return version.removePrefix("v").replace("-.*".toRegex(), "")
    }

    private fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = v2.split(".").map { it.toIntOrNull() ?: 0 }

        val maxLen = maxOf(parts1.size, parts2.size)

        for (i in 0 until maxLen) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }

            if (p1 != p2) {
                return p1.compareTo(p2)
            }
        }

        return 0
    }
}

sealed class UpdateState {
    object Idle : UpdateState()
    data class Downloading(val progress: Int) : UpdateState()
    data class Downloaded(val filePath: String) : UpdateState()
    data class Error(val message: String) : UpdateState()
}

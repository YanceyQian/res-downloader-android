package com.resdownloader.data.model

data class DownloadTask(
    val id: String,
    val resourceInfo: ResourceInfo,
    val status: DownloadStatus,
    val progress: Int,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val filePath: String? = null,
    val errorMessage: String? = null
)

enum class DownloadStatus {
    PENDING,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED
}

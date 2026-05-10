package com.resdownloader.data.model

/**
 * 资源信息数据类
 * 对应原项目 res-downloader 的 MediaInfo
 */
data class ResourceInfo(
    val id: String,
    val url: String,
    val type: ResourceType,
    val platform: Platform,
    val filename: String,
    val size: Long,
    val timestamp: Long,
    val headers: Map<String, String> = emptyMap(),
    val decodeKey: String? = null,
    val extraInfo: Map<String, String> = emptyMap()
)

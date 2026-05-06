package com.resdownloader.data.model

data class ResourceInfo(
    val id: String,
    val url: String,
    val type: ResourceType,
    val platform: Platform,
    val filename: String,
    val size: Long,
    val timestamp: Long,
    val headers: Map<String, String> = emptyMap()
)

enum class ResourceType {
    VIDEO,
    AUDIO,
    IMAGE,
    M3U8,
    OTHER
}

enum class Platform {
    WECHAT,
    DOUYIN,
    KUAISHOU,
    XIAOHONGSHU,
    KUGOU,
    QQMUSIC,
    OTHER
}

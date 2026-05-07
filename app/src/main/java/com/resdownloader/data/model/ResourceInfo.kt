package com.resdownloader.data.model

data class ResourceInfo(
    val id: String,
    val url: String,
    val type: ResourceType,
    val platform: Platform,
    val filename: String,
    val size: Long,
    val timestamp: Long,
    val headers: Map<String, String> = emptyMap(),
    val decodeKey: String? = null
)

enum class ResourceType {
    VIDEO,
    AUDIO,
    IMAGE,
    M3U8,
    LIVE,
    STREAM,
    XLS,
    DOC,
    PDF,
    FONT,
    OTHER
}

enum class Platform {
    WECHAT,          // 微信（视频号、小程序）
    DOUYIN,          // 抖音
    KUAISHOU,        // 快手
    XIAOHONGSHU,     // 小红书
    KUGOU,           // 酷狗音乐
    QQMUSIC,         // QQ音乐
    BILIBILI,        // B站
    WECHAT_MINI,     // 微信小程序
    QQWEISHI,        // QQ微视
    YOUTUBE,         // YouTube
    OTHER            // 其他平台
}

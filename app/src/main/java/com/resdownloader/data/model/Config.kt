package com.resdownloader.data.model

data class Config(
    // 基础设置
    val theme: String = "light",
    val locale: String = "zh",
    val host: String = "127.0.0.1",
    val port: Int = 8899,
    val quality: Int = 0,
    val saveDirectory: String = "",
    val filenameLen: Int = 0,
    val filenameTime: Boolean = false,
    
    // 高级设置
    val upstreamProxy: String = "",
    val openProxy: Boolean = false,
    val downloadProxy: Boolean = false,
    val autoProxy: Boolean = false,
    val wxAction: Boolean = false,
    val taskNumber: Int = 8,
    val downNumber: Int = 3,
    val userAgent: String = "",
    val useHeaders: String = "",
    val insertTail: Boolean = false,
    
    // MIME类型映射
    val mimeMap: Map<String, MimeMapItem> = emptyMap(),
    
    // 规则配置
    val rule: String = ""
)

data class MimeMapItem(
    val type: String,
    val suffix: String
)

// 下载质量枚举
enum class DownloadQuality(val value: Int, val desc: String) {
    HIGHEST(0, "最高"),
    MEDIUM(1, "中等"),
    LOWEST(2, "最低")
}

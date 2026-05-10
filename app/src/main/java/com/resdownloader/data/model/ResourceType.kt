package com.resdownloader.data.model

/**
 * 资源类型枚举
 * 对应原项目的资源分类
 */
enum class ResourceType {
    VIDEO,      // 视频
    AUDIO,      // 音频
    IMAGE,      // 图片
    M3U8,       // M3U8 播放列表
    LIVE,       // 直播流
    STREAM,     // 流数据
    XLS,        // 表格文件
    DOC,        // 文档
    PDF,        // PDF 文件
    FONT,
    OTHER;

    /**
     * 获取类型的显示名称
     */
    fun displayName(): String = when (this) {
        VIDEO -> "视频"
        AUDIO -> "音频"
        IMAGE -> "图片"
        M3U8 -> "M3U8"
        LIVE -> "直播"
        STREAM -> "流"
        XLS -> "表格"
        DOC -> "文档"
        PDF -> "PDF"
        FONT -> "字体"
        OTHER -> "其他"
    }

    /**
     * 获取类型的文件扩展名
     */
    fun defaultExtension(): String = when (this) {
        VIDEO -> "mp4"
        AUDIO -> "mp3"
        IMAGE -> "jpg"
        M3U8 -> "m3u8"
        LIVE -> "flv"
        STREAM -> "ts"
        XLS -> "xlsx"
        DOC -> "docx"
        PDF -> "pdf"
        FONT -> "ttf"
        OTHER -> "bin"
    }

    companion object {
        /**
         * 从文件扩展名推断资源类型
         */
        fun fromExtension(extension: String): ResourceType {
            return when (extension.lowercase()) {
                "mp4", "webm", "mkv", "mov", "avi", "flv", "ts" -> VIDEO
                "mp3", "wav", "flac", "aac", "m4a", "ogg" -> AUDIO
                "jpg", "jpeg", "png", "gif", "webp", "bmp" -> IMAGE
                "m3u8" -> M3U8
                "xlsx", "xls", "csv" -> XLS
                "doc", "docx" -> DOC
                "pdf" -> PDF
                "ttf", "otf", "woff", "woff2" -> FONT
                else -> OTHER
            }
        }

        /**
         * 从 MIME 类型推断资源类型
         */
        fun fromMimeType(mimeType: String): ResourceType {
            return when {
                mimeType.startsWith("video/") -> VIDEO
                mimeType.startsWith("audio/") -> AUDIO
                mimeType.startsWith("image/") -> IMAGE
                mimeType.contains("excel") || mimeType.contains("spreadsheet") -> XLS
                mimeType.contains("word") || mimeType.contains("document") -> DOC
                mimeType.contains("pdf") -> PDF
                mimeType.contains("font") -> FONT
                else -> OTHER
            }
        }
    }
}

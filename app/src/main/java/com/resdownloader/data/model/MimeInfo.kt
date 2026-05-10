package com.resdownloader.data.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class MimeInfo(
    val type: String,
    val suffix: String
)

object MimeDefaults {
    val defaultMimeMap: Map<String, MimeInfo> = mapOf(
        "image/png" to MimeInfo("image", ".png"),
        "image/webp" to MimeInfo("image", ".webp"),
        "image/jpeg" to MimeInfo("image", ".jpeg"),
        "image/jpg" to MimeInfo("image", ".jpg"),
        "image/gif" to MimeInfo("image", ".gif"),
        "image/avif" to MimeInfo("image", ".avif"),
        "image/bmp" to MimeInfo("image", ".bmp"),
        "image/tiff" to MimeInfo("image", ".tiff"),
        "image/heic" to MimeInfo("image", ".heic"),
        "image/x-icon" to MimeInfo("image", ".ico"),
        "image/svg+xml" to MimeInfo("image", ".svg"),
        "image/vnd.adobe.photoshop" to MimeInfo("image", ".psd"),
        "image/jp2" to MimeInfo("image", ".jp2"),
        "image/jpeg2000" to MimeInfo("image", ".jp2"),
        "image/apng" to MimeInfo("image", ".apng"),
        "audio/mpeg" to MimeInfo("audio", ".mp3"),
        "audio/mp3" to MimeInfo("audio", ".mp3"),
        "audio/wav" to MimeInfo("audio", ".wav"),
        "audio/aiff" to MimeInfo("audio", ".aiff"),
        "audio/x-aiff" to MimeInfo("audio", ".aiff"),
        "audio/aac" to MimeInfo("audio", ".aac"),
        "audio/ogg" to MimeInfo("audio", ".ogg"),
        "audio/flac" to MimeInfo("audio", ".flac"),
        "audio/midi" to MimeInfo("audio", ".mid"),
        "audio/x-midi" to MimeInfo("audio", ".mid"),
        "audio/x-ms-wma" to MimeInfo("audio", ".wma"),
        "audio/opus" to MimeInfo("audio", ".opus"),
        "audio/webm" to MimeInfo("audio", ".webm"),
        "audio/mp4" to MimeInfo("audio", ".m4a"),
        "audio/amr" to MimeInfo("audio", ".amr"),
        "video/mp4" to MimeInfo("video", ".mp4"),
        "video/webm" to MimeInfo("video", ".webm"),
        "video/ogg" to MimeInfo("video", ".ogv"),
        "video/x-msvideo" to MimeInfo("video", ".avi"),
        "video/mpeg" to MimeInfo("video", ".mpeg"),
        "video/quicktime" to MimeInfo("video", ".mov"),
        "video/x-ms-wmv" to MimeInfo("video", ".wmv"),
        "video/3gpp" to MimeInfo("video", ".3gp"),
        "video/x-matroska" to MimeInfo("video", ".mkv"),
        "audio/video" to MimeInfo("live", ".flv"),
        "video/x-flv" to MimeInfo("live", ".flv"),
        "application/dash+xml" to MimeInfo("live", ".mpd"),
        "application/vnd.apple.mpegurl" to MimeInfo("m3u8", ".m3u8"),
        "application/x-mpegurl" to MimeInfo("m3u8", ".m3u8"),
        "application/x-mpeg" to MimeInfo("m3u8", ".m3u8"),
        "audio/x-mpegurl" to MimeInfo("m3u8", ".m3u8"),
        "application/pdf" to MimeInfo("pdf", ".pdf"),
        "application/vnd.ms-powerpoint" to MimeInfo("ppt", ".ppt"),
        "application/vnd.openxmlformats-officedocument.presentationml.presentation" to MimeInfo("ppt", ".pptx"),
        "application/vnd.ms-excel" to MimeInfo("xls", ".xls"),
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" to MimeInfo("xls", ".xlsx"),
        "text/csv" to MimeInfo("xls", ".csv"),
        "application/msword" to MimeInfo("doc", ".doc"),
        "application/rtf" to MimeInfo("doc", ".rtf"),
        "text/rtf" to MimeInfo("doc", ".rtf"),
        "application/vnd.oasis.opendocument.text" to MimeInfo("doc", ".odt"),
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document" to MimeInfo("doc", ".docx"),
        "font/woff" to MimeInfo("font", ".woff"),
        "application/octet-stream" to MimeInfo("stream", ".default")
    )
}
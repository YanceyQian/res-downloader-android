package com.resdownloader.data.model

/**
 * 支持的平台枚举
 * 对应原项目 res-downloader 的平台支持
 */
enum class Platform(
    val displayName: String,
    val domains: List<String>,
    val urlPatterns: List<Regex>
) {
    /**
     * 微信视频号
     * 原项目: plugin.qq.com.go 中的微信视频号支持
     */
    WECHAT(
        displayName = "微信视频号",
        domains = listOf(
            "channels.weixin.qq.com",
            "mp.weixin.qq.com",
            "res.wx.qq.com",
            "finder.video.qq.com"
        ),
        urlPatterns = listOf(
            Regex("(channels\\.weixin\\.qq\\.com[^\\s]+)"),
            Regex("(mp\\.weixin\\.qq\\.com/s/[^\\s]+)"),
            Regex("(short\\.ws/[a-zA-Z0-9]+)")
        )
    ),

    /**
     * B站 (Bilibili)
     * 支持: 视频、番剧、弹幕、字幕
     */
    BILIBILI(
        displayName = "B站",
        domains = listOf(
            "bilibili.com",
            "b23.tv"
        ),
        urlPatterns = listOf(
            Regex("(b23\\.tv/[a-zA-Z0-9]+)"),
            Regex("bilibili\\.com/video/(BV[a-zA-Z0-9]+)"),
            Regex("bilibili\\.com/video/(av\\d+)")
        )
    ),

    /**
     * 抖音
     * 支持: 视频、图集
     */
    DOUYIN(
        displayName = "抖音",
        domains = listOf(
            "douyin.com",
            "iesdouyin.com",
            "amemv.com"
        ),
        urlPatterns = listOf(
            Regex("(v\\.douyin\\.com/[a-zA-Z0-9]+)"),
            Regex("(www\\.douyin\\.com/video/\\d+)")
        )
    ),

    /**
     * 快手
     * 支持: 视频、图片
     */
    KUAISHOU(
        displayName = "快手",
        domains = listOf(
            "kuaishou.com",
            "ksurl.cn"
        ),
        urlPatterns = listOf(
            Regex("(ksurl\\.cn/[a-zA-Z0-9]+)"),
            Regex("(v\\.kuaishou\\.com/[a-zA-Z0-9]+)")
        )
    ),

    /**
     * 小红书
     * 支持: 视频、图片、图文
     */
    XIAOHONGSHU(
        displayName = "小红书",
        domains = listOf(
            "xiaohongshu.com",
            "xhslink.com"
        ),
        urlPatterns = listOf(
            Regex("(xhslink\\.com/[a-zA-Z0-9]+)"),
            Regex("(xiaohongshu\\.com/explore/[a-zA-Z0-9]+)")
        )
    ),

    /**
     * 网易云音乐
     * 支持: 音频、歌词
     */
    NETEASE(
        displayName = "网易云音乐",
        domains = listOf(
            "music.163.com",
            "api.iplay.ink"
        ),
        urlPatterns = listOf(
            Regex("music\\.163\\.com/song\\?id=(\\d+)")
        )
    ),

    /**
     * QQ音乐
     * 原项目 plugin.qq.com.go 中有特殊处理
     */
    QQMUSIC(
        displayName = "QQ音乐",
        domains = listOf(
            "qq.com",
            "y.qq.com"
        ),
        urlPatterns = listOf(
            Regex("(y\\.qq\\.com/n/ryqq/songDetail/\\w+)")
        )
    ),

    /**
     * 酷狗音乐
     */
    KOUGOU(
        displayName = "酷狗音乐",
        domains = listOf(
            "kugou.com",
            "www.kugou.com"
        ),
        urlPatterns = listOf(
            Regex("kugou\\.com/yy/html/page/song\\.html\\?hash=([a-zA-Z0-9]+)")
        )
    ),

    /**
     * 通用平台
     * 支持直接链接和 M3U8
     */
    GENERAL(
        displayName = "通用",
        domains = emptyList(),
        urlPatterns = listOf(
            Regex(".*\\.(mp4|webm|mkv|mov|avi|flv)(?:\\?.*)?$", RegexOption.IGNORE_CASE),
            Regex(".*\\.(mp3|wav|flac|aac|m4a|ogg)(?:\\?.*)?$", RegexOption.IGNORE_CASE),
            Regex(".*\\.(m3u8)(?:\\?.*)?$", RegexOption.IGNORE_CASE),
            Regex(".*\\.(jpg|jpeg|png|gif|webp)(?:\\?.*)?$", RegexOption.IGNORE_CASE)
        )
    ),

    /**
     * 未知平台
     */
    UNKNOWN(
        displayName = "未知",
        domains = emptyList(),
        urlPatterns = emptyList()
    );

    /**
     * 检查 URL 是否属于此平台
     */
    fun matches(url: String): Boolean {
        val lowerUrl = url.lowercase()
        
        // 检查域名
        if (domains.isNotEmpty() && domains.any { lowerUrl.contains(it) }) {
            return true
        }
        
        // 检查 URL 模式
        return urlPatterns.any { it.containsMatchIn(url) }
    }

    companion object {
    /**
     * 从 URL 检测平台
     */
    fun detect(url: String): Platform {
        // 先检查特殊平台（按优先级排序）
        val platforms = listOf(
            WECHAT, BILIBILI, DOUYIN, KUAISHOU, 
            XIAOHONGSHU, NETEASE, QQMUSIC, KOUGOU
        )
            
            for (platform in platforms) {
                if (platform.matches(url)) {
                    return platform
                }
            }
            
            // 检查通用模式
            if (GENERAL.matches(url)) {
                return GENERAL
            }
            
            return UNKNOWN
        }

        /**
         * 获取平台图标资源名
         */
        fun getIconResource(platform: Platform): String {
            return when (platform) {
                WECHAT -> "ic_wechat"
                BILIBILI -> "ic_bilibili"
                DOUYIN -> "ic_douyin"
                KUAISHOU -> "ic_kuaishou"
                XIAOHONGSHU -> "ic_xiaohongshu"
                NETEASE -> "ic_netease"
                QQMUSIC -> "ic_qqmusic"
                KOUGOU -> "ic_kugou"
                GENERAL -> "ic_download"
                UNKNOWN -> "ic_link"
            }
        }
    }
}

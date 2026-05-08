package com.resdownloader.data.model

/**
 * 解析域名规则字符串，返回是否应该拦截该域名
 *
 * 规则格式：
 * - `*` - 匹配所有域名
 * - `*.example.com` - 匹配所有子域名
 * - `example.com` - 精确匹配
 * - `!example.com` - 排除该域名
 * - `# comment` - 注释行
 *
 * 支持的平台域名示例（原项目 detectPlatform）：
 * - 抖音系: *.douyin.com, *.iesdouyin.com, *.amemv.com
 * - 快手系: *.kuaishou.com, *.kspkg.com
 * - 小红书: *.xiaohongshu.com, *.xhslink.com
 * - 微信/视频号: weixin.qq.com, *.wechat.com, servicewechat.com
 * - QQ音乐: y.qq.com, music.qq.com
 * - 酷狗: *.kugou.com, *.kgimg.com
 *
 * 匹配逻辑：
 * 1. 按行解析规则
 * 2. 否定规则 (!) 最后处理
 * 3. 通配符规则 (*.domain) 使用 endsWith 匹配
 */
object RuleSet {
    private var rules: List<Rule> = emptyList()
    private var hasWildcard: Boolean = false

    data class Rule(
        val pattern: String,
        val isExclude: Boolean = false,
        val isWildcard: Boolean = false
    )

    /**
     * 解析规则字符串
     * @param rawRules 原始规则字符串
     */
    fun parse(rawRules: String): RuleSet {
        rules = rawRules.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { line ->
                when {
                    line.startsWith("!") -> Rule(line.substring(1), isExclude = true)
                    line.startsWith("*.") -> Rule(line.substring(2), isWildcard = true)
                    else -> Rule(line)
                }
            }
        hasWildcard = rules.any { it.pattern == "*" }
        return this
    }

    fun shouldMitm(host: String): Boolean {
        val lowerHost = host.lowercase()

        // 如果有 * 通配符，默认拦截
        if (hasWildcard) {
            // 检查是否有排除规则匹配
            val excludeMatch = rules.filter { it.isExclude }.any { rule ->
                matches(rule.pattern, lowerHost)
            }
            if (excludeMatch) return false
            return true
        }

        // 没有 * 时，精确匹配规则
        var result = false
        for (rule in rules) {
            if (matches(rule.pattern, lowerHost)) {
                result = !rule.isExclude
            }
        }
        return result
    }

    private fun matches(pattern: String, host: String): Boolean {
        return when {
            pattern == "*" -> true
            pattern.startsWith("*.") -> host.endsWith(pattern.substring(1))
            else -> host == pattern || host.endsWith(".$pattern")
        }
    }
}

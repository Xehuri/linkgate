package com.xehuri.linkgate.data

import java.net.URI

/**
 * 站点图标（favicon）地址推导与 HTML 解析（纯 JVM 逻辑，可单元测试）。
 *
 * 策略：
 * 1. 先试站点 origin 下的 `/favicon.ico`；
 * 2. 不行再抓首页 HTML，解析 `<link rel="icon|shortcut icon|apple-touch-icon">` 的 href；
 * 3. 都失败就由 UI 退回首字母色块。
 */
object FaviconLocator {

    fun originOf(url: String): String? {
        val uri = try {
            URI(url)
        } catch (e: Exception) {
            return null
        }
        val scheme = uri.scheme ?: return null
        val host = uri.host ?: return null
        if (host.isBlank()) return null
        val port = if (uri.port > 0) ":${uri.port}" else ""
        return "$scheme://$host$port"
    }

    /** 兜底候选图标地址（origin + /favicon.ico）。 */
    fun candidateUrls(siteUrl: String): List<String> {
        val origin = originOf(siteUrl) ?: return emptyList()
        return listOf("$origin/favicon.ico")
    }

    /** 从 HTML 里解析图标 href，并相对 [baseUrl] 解析成绝对地址。 */
    fun extractIconHrefs(html: String, baseUrl: String): List<String> {
        if (html.isBlank()) return emptyList()
        val result = LinkedHashSet<String>()
        val base = try {
            URI(baseUrl)
        } catch (e: Exception) {
            null
        } ?: return emptyList()

        val linkTag = Regex("<link\\b[^>]*>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val relAttr = Regex("rel\\s*=\\s*[\"']([^\"']*)[\"']", RegexOption.IGNORE_CASE)
        val hrefAttr = Regex("href\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)

        for (match in linkTag.findAll(html)) {
            val tag = match.value
            val rel = relAttr.find(tag)?.groupValues?.getOrNull(1)?.lowercase() ?: continue
            if (!rel.contains("icon")) continue
            val href = hrefAttr.find(tag)?.groupValues?.getOrNull(1)?.trim() ?: continue
            val cleaned = href.replace("&amp;", "&")
            val absolute = try {
                base.resolve(cleaned).toString()
            } catch (e: Exception) {
                continue
            }
            if (absolute.startsWith("http://", ignoreCase = true) ||
                absolute.startsWith("https://", ignoreCase = true)
            ) {
                result.add(absolute)
            }
        }
        return result.toList()
    }
}

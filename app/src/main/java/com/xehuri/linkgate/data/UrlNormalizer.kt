package com.xehuri.linkgate.data

import java.net.Inet6Address
import java.net.InetAddress
import java.net.MalformedURLException
import java.net.URL
import java.util.Locale

/**
 * 把用户随手输入的网址整理成可加载的 http/https 链接。
 *
 * 规则：
 * - 去掉首尾空白与从聊天软件粘贴时常见的引号/书名号/中文标点；
 * - 没写协议默认补 https://；
 * - 只接受 http/https，其它协议（javascript:、file:、intent:、ftp: 等）一律拒绝；
 * - 主机名必须带点、是 localhost 或 IP 字面量，避免 "abc" 这种输入变成无意义的 https://abc；
 * - 支持 IPv6 字面量：`[2408:...::1]:8080`、`2408:...::1`、`https://[2408:...::1]/path` 都能识别。
 *
 * 纯 JVM 逻辑，可在单元测试里直接验证（不依赖 Android 运行时）。
 */
object UrlNormalizer {

    /** 主机[:端口][/路径]，主机可以是域名/IPv4 或带方括号的 IPv6 字面量。 */
    private val HOST_PORT = Regex(
        "^(\\[[0-9A-Fa-f:.%A-Za-z]+\\]|[A-Za-z0-9._-]+):(\\d{1,5})(/.*)?$",
    )

    private val TRIM_CHARS = charArrayOf(
        '"', '\'', '<', '>', '，', '。', '、', '；', ',', ';', '（', '）', '【', '】', '《', '》', '`',
    )

    fun normalize(raw: String?): String? {
        if (raw == null) return null
        var text = raw.trim()
        if (text.isEmpty()) return null
        // 零宽字符是复制粘贴的常见污染源
        text = text.replace("\u200B", "").replace("\uFEFF", "").trim()
        text = text.trim(*TRIM_CHARS)
        if (text.isEmpty()) return null
        if (text.startsWith("//")) text = "https:$text"
        if (text.any { it.isWhitespace() }) return null

        val schemeEnd = text.indexOf("://")
        if (schemeEnd >= 0) {
            val scheme = text.substring(0, schemeEnd).lowercase(Locale.ROOT)
            if (scheme != "http" && scheme != "https") return null
            text = scheme + text.substring(schemeEnd)
        } else {
            text = when {
                // [2408:...::1]:8080 或 [2408:...::1]
                text.startsWith("[") && looksLikeHostWithPort(text) -> "https://$text"
                // 裸 IPv6 字面量：2408:...::1
                isBareIpv6Literal(text) -> "https://[$text]"
                // example.com:8080 形式的主机:端口
                looksLikeHostWithPort(text) -> "https://$text"
                text.contains(':') -> return null
                text.startsWith(".") -> return null
                else -> "https://$text"
            }
        }

        val url = try {
            URL(text)
        } catch (e: MalformedURLException) {
            return null
        }
        if (url.protocol != "http" && url.protocol != "https") return null
        val host = url.host
        if (host.isNullOrBlank()) return null
        if (!isAcceptableHost(host)) return null
        val port = url.port
        if (port > 65535) return null
        return url.toString()
    }

    /** 由网址推导默认标题：去掉 www. 前缀、去掉 IPv6 方括号的主机名。 */
    fun titleFromUrl(url: String): String {
        val host = try {
            URL(url).host
        } catch (e: MalformedURLException) {
            null
        }
        if (host.isNullOrBlank()) return url
        return host.removePrefix("[").removeSuffix("]").substringBefore('%').removePrefix("www.")
    }

    /** 这个 URL 的主机是不是 IPv6 字面量。 */
    fun isIpv6Host(url: String): Boolean {
        val host = try {
            URL(url).host
        } catch (e: MalformedURLException) {
            return false
        }
        return !host.isNullOrBlank() && host.startsWith("[")
    }

    /** 从 IPv6 字面量 URL 里取出纯地址（去掉方括号与 zone id），失败返回 null。 */
    fun ipv6LiteralOf(url: String): String? {
        val host = try {
            URL(url).host
        } catch (e: MalformedURLException) {
            return null
        } ?: return null
        if (!host.startsWith("[") || !host.endsWith("]")) return null
        val inner = host.substring(1, host.length - 1).substringBefore('%')
        return inner.ifBlank { null }
    }

    /** 形如 example.com:8080 或 [2408::1]:8080 的「主机:端口」输入。 */
    private fun looksLikeHostWithPort(text: String): Boolean = HOST_PORT.matches(text)

    /** 裸 IPv6 字面量（不含方括号、不含端口）。 */
    private fun isBareIpv6Literal(text: String): Boolean {
        if (!text.contains(':')) return false
        // 至少两处冒号，避免把 host:port 误判成 IPv6
        if (text.count { it == ':' } < 2) return false
        val address = try {
            InetAddress.getByName(text)
        } catch (e: Throwable) {
            return false
        }
        return address is Inet6Address
    }

    private fun isAcceptableHost(host: String): Boolean {
        if (host.startsWith(".") || host.endsWith(".")) return false
        if (host.equals("localhost", ignoreCase = true)) return true
        if (isIpv4(host)) return true
        // IPv6 字面量在 URL 里带方括号
        if (host.startsWith("[") && host.endsWith("]")) {
            val inner = host.substring(1, host.length - 1).substringBefore('%')
            if (inner.isEmpty()) return false
            return try {
                InetAddress.getByName(inner) is Inet6Address
            } catch (e: Throwable) {
                false
            }
        }
        if (!host.contains(".")) return false
        return host.split('.').none { it.isEmpty() }
    }

    private fun isIpv4(host: String): Boolean {
        val parts = host.split('.')
        if (parts.size != 4) return false
        return parts.all { part ->
            part.isNotEmpty() && part.length <= 3 && part.all { it.isDigit() } && part.toIntOrNull() in 0..255
        }
    }
}

package com.xehuri.linkgate.web

/**
 * Cookie 头的解析与回填格式（纯 JVM 逻辑，可单测）。
 *
 * 背景：WebView 里 `HttpOnly` 的会话 Cookie 在进程结束时会被丢弃（浏览器语义如此），
 * 但用户期望「关掉 App 再从列表打开网页，登录状态还在」。做法是：
 * 退到后台时用 [android.webkit.CookieManager.getCookie] 把每个站点的 Cookie 头快照下来，
 * 下次打开该站点前再用 `setCookie` 回填，并补上较长的 Max-Age 让它真正落盘。
 *
 * getCookie 返回形如 `a=1; b=2; c=3`，这里只切分 name=value，不处理属性。
 */
object CookieSnapshot {

    /** 回填时给会话 Cookie 的默认有效期：一年。 */
    const val MAX_AGE_SECONDS: Long = 31_536_000L

    fun pairs(header: String?): List<String> {
        if (header.isNullOrBlank()) return emptyList()
        return header.split(';')
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.contains('=') && !it.startsWith("=") }
    }

    /**
     * 把 `name=value` 拼成 setCookie 接受的 Set-Cookie 串。
     * secure 用于 https 站点补 Secure 属性。
     */
    fun toSetCookieValue(pair: String, secure: Boolean): String {
        val base = "$pair; Path=/; Max-Age=$MAX_AGE_SECONDS"
        return if (secure) "$base; Secure" else base
    }
}

package com.xehuri.linkgate.web

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import com.xehuri.linkgate.data.PasswordOrigin
import com.xehuri.linkgate.data.PasswordStore
import com.xehuri.linkgate.data.Site
import android.util.Log

/**
 * 「清除本站数据」：把 Cookie（真 Cookie + 快照）、本地存储、缓存、表单、HTTP Basic 凭据、
 * 已保存密码一次清干净。
 *
 * 顺序很重要：先停止加载，再清存储，最后由调用方决定是否 reload；
 * 少清任何一层都会导致「清完又变成登录态」，所以这里按层列全。
 */
object SiteDataCleaner {

    private const val TAG = "LinkGateClear"

    /** @return 清理过程中失败的分层名称（空表示全部成功） */
    fun clearSite(
        context: Context,
        site: Site,
        currentUrl: String?,
        webView: WebView?,
    ): List<String> {
        val failures = ArrayList<String>(4)
        runCatching { webView?.stopLoading() }

        // 1. 真 Cookie：对当前页面地址与站点主地址分别逐条置空
        val urls = listOfNotNull(currentUrl, site.url, site.ipv6Url.ifBlank { null })
            .filter { it.startsWith("http://", true) || it.startsWith("https://", true) }
            .distinct()
        val manager = CookieManager.getInstance()
        for (url in urls) {
            val header = runCatching { manager.getCookie(url) }.getOrNull()
            if (header.isNullOrBlank()) continue
            for (pair in CookieSnapshot.pairs(header)) {
                val name = pair.substringBefore('=').trim()
                if (name.isEmpty()) continue
                runCatching { manager.setCookie(url, "$name=; Max-Age=0; Path=/") }
            }
        }

        // 2. App 自己的 Cookie 快照（清不干净的话下次 onStop 又把 Cookie 灌回来）
        runCatching { CookieStore(context).remove(site.id) }
            .onFailure { failures.add("Cookie 快照") }

        // 3. 本地存储（localStorage / IndexedDB / WebSQL）
        val origin = runCatching { PasswordOrigin.fromUrl(site.url) }.getOrNull()
        if (!origin.isNullOrBlank()) {
            runCatching { WebStorage.getInstance().deleteOrigin(origin) }
                .onFailure { failures.add("本地存储") }
        }

        // 4. 缓存与表单
        runCatching {
            webView?.clearCache(true)
            webView?.clearFormData()
            webView?.clearHistory()
        }.onFailure { failures.add("缓存") }

        runCatching { manager.flush() }
        Log.d(TAG, "清除站点数据: ${site.title}, 地址数=${urls.size}, 失败=${failures.joinToString()}")
        return failures
    }

    /** 清除该站点已保存的密码（按 origin 匹配）。 */
    fun clearPasswords(context: Context, site: Site): Int {
        val origin = runCatching { PasswordOrigin.fromUrl(site.url) }.getOrNull() ?: return 0
        val store = PasswordStore(context)
        val targets = store.all().filter { it.origin == origin }
        targets.forEach { runCatching { store.remove(it.origin, it.username) } }
        return targets.size
    }
}

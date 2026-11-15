package com.xehuri.linkgate.web

import android.content.Context
import android.webkit.CookieManager
import com.xehuri.linkgate.data.Site

/**
 * Cookie 的持久化：把每个站点的 Cookie 头快照存到 SharedPreferences，
 * 打开站点前回填；退到后台时调用 [flushAsync] 让 WebView 把 Cookie 写盘。
 *
 * 只对用户自己添加的站点做快照，不做全局抓取；数据存应用私有目录，随卸载清除。
 *
 * 写入策略是「增量」而不是「先清空再写」：
 * 冷启动早期 [CookieManager.getCookie] 可能返回空，如果此时清空再写，
 * 这一站点的登录态就被永久抹掉了。现在只要读不到就保留旧快照，
 * 只有站点被删除时才删掉对应快照。
 */
class CookieStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 用当前站点列表增量刷新快照：读不到 Cookie 的站点保留旧值，已删除站点的快照才清理。 */
    fun saveFrom(sites: List<Site>) {
        val manager = CookieManager.getInstance()
        val editor = prefs.edit()
        val liveIds = sites.mapTo(HashSet()) { it.id }
        for (site in sites) {
            val header = runCatching { manager.getCookie(site.url) }.getOrNull()
            if (header.isNullOrBlank()) continue
            editor.putString(key(site.id), header)
        }
        for (existing in prefs.all.keys) {
            if (!existing.startsWith(PREFIX)) continue
            if (existing.removePrefix(PREFIX) !in liveIds) editor.remove(existing)
        }
        editor.apply()
    }

    /** 清除全部 Cookie 快照（「清除网页数据」时调用，避免旧快照把 Cookie 又灌回来）。 */
    fun clearAll() {
        prefs.edit().clear().apply()
    }

    /** 清除单个站点的 Cookie 快照（「清除本站数据」时调用）。 */
    fun remove(siteId: String) {
        prefs.edit().remove(key(siteId)).apply()
    }

    /** 打开某个站点前回填它的 Cookie 快照。 */
    fun restoreFor(siteId: String, url: String) {
        val header = prefs.getString(key(siteId), null) ?: return
        val secure = url.startsWith("https://", ignoreCase = true)
        val manager = CookieManager.getInstance()
        CookieSnapshot.pairs(header).forEach { pair ->
            runCatching { manager.setCookie(url, CookieSnapshot.toSetCookieValue(pair, secure)) }
        }
    }

    companion object {
        private const val PREFS_NAME = "linkgate_cookies"
        private const val PREFIX = "cookie:"

        private fun key(siteId: String) = PREFIX + siteId

        /**
         * 冷启动预热：`CookieManager` 是惰性初始化的，第一次 `getCookie` 之前必须触发一次实例化，
         * 否则进程刚起来就退到后台时拿到的全是空值。
         */
        fun warmUp() {
            runCatching { CookieManager.getInstance().setAcceptCookie(true) }
        }

        /**
         * WebView 的 Cookie 平时只在内存里，进程被杀可能丢持久 Cookie。
         * flush 会阻塞磁盘写入，放后台线程跑，避免卡 UI。
         */
        fun flushAsync() {
            val thread = Thread({
                runCatching { CookieManager.getInstance().flush() }
            }, "linkgate-cookie-flush")
            thread.isDaemon = true
            thread.start()
        }
    }
}

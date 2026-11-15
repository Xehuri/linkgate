package com.xehuri.linkgate.nav

import java.util.Locale

/** 网页界面里按下返回键时应该做什么。 */
enum class BackAction {
    /** 网页历史里还有上一级页面：goBack() */
    WEB_GO_BACK,

    /** 回到站点选择界面（App 首屏）；它再按返回就是退出应用 */
    CLOSE_WEB_SCREEN,
}

/**
 * 返回栈策略（纯逻辑，可单元测试）：
 *
 * 1. 当前已经在「入口落地页」→ 关闭网页界面，回到站点选择界面（**优先判断**）；
 * 2. 网页内还有历史 → 回上一级网页；
 * 3. 历史到头 → 同样回站点选择界面；
 * 4. 站点选择界面再按返回 → 退出应用（由 Compose/Activity 层处理）。
 *
 * 为什么必须先判断「落地页」：很多站点会把入口 URL 302 到 /login 之类的真实入口，
 * 历史里留下了「原地址 → 真实入口」。如果先判断 canGoBack，就会 goBack 到原地址、
 * 再被重定向回真实入口，表现为「返回键永远回不到选择界面」。
 *
 * landingUrl 由 [com.xehuri.linkgate.web.WebHost] 在首次加载完成时记录，而不是用户填写的原始 URL。
 */
object BackPlanner {

    fun inWebScreen(canGoBackInHistory: Boolean, currentUrl: String?, landingUrl: String): BackAction {
        if (samePage(currentUrl, landingUrl)) return BackAction.CLOSE_WEB_SCREEN
        return if (canGoBackInHistory) BackAction.WEB_GO_BACK else BackAction.CLOSE_WEB_SCREEN
    }

    /**
     * 两个 URL 是否算「同一页」：忽略锚点、查询串、末尾斜杠、大小写与 www. 前缀。
     * 只用于返回栈判断，不用于网络请求。
     */
    fun samePage(a: String?, b: String?): Boolean {
        val left = normalizeForCompare(a) ?: return false
        val right = normalizeForCompare(b) ?: return false
        return left == right
    }

    private fun normalizeForCompare(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        var text = raw.trim()
        val schemeEnd = text.indexOf("://")
        val scheme: String
        var rest: String
        if (schemeEnd > 0) {
            scheme = text.substring(0, schemeEnd).lowercase(Locale.ROOT)
            rest = text.substring(schemeEnd + 3)
        } else {
            scheme = ""
            rest = text
        }
        rest = rest.substringBefore('#').substringBefore('?')
        val slash = rest.indexOf('/')
        var host = if (slash >= 0) rest.substring(0, slash) else rest
        var path = if (slash >= 0) rest.substring(slash) else ""
        host = host.lowercase(Locale.ROOT).removePrefix("www.")
        path = path.trimEnd('/')
        if (host.isEmpty()) return null
        // 路径也统一小写：仅用于返回栈判断，宁可宽松（站点重定向常改路径大小写）
        return "$scheme://$host$path".lowercase(Locale.ROOT)
    }
}

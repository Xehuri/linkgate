package com.xehuri.linkgate.nav

import com.xehuri.linkgate.data.Site

/**
 * 决定「这次打开站点用哪个地址」的纯函数。
 *
 * 规则：
 * 1. 没开「IPv6 优先」→ 用主地址；
 * 2. 站点没配 IPv6 备用地址（或与主地址相同）→ 用主地址；
 * 3. 开了优先、配了 IPv6，但本机当下没有可用 IPv6 → 用主地址（不做无谓的等待）；
 * 4. 都满足 → 用 IPv6 地址，并把主地址记为回退地址（打不开时自动改回主地址）。
 */
object OpenTargetPlanner {

    /**
     * @param url 实际要加载的地址
     * @param usingIpv6 是否正在使用 IPv6 备用地址
     * @param fallbackUrl 打开失败时应该回退到的地址，没有则为 null
     */
    data class Plan(
        val url: String,
        val usingIpv6: Boolean,
        val fallbackUrl: String?,
    )

    fun plan(site: Site, preferIpv6: Boolean, ipv6Available: Boolean): Plan {
        val primary = Plan(url = site.url, usingIpv6 = false, fallbackUrl = null)
        if (!preferIpv6) return primary
        val ipv6 = site.ipv6Url.trim()
        if (ipv6.isEmpty() || ipv6 == site.url) return primary
        if (!ipv6Available) return primary
        return Plan(url = ipv6, usingIpv6 = true, fallbackUrl = site.url)
    }
}

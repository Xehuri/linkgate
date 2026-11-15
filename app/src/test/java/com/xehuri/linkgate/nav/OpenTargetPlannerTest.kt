package com.xehuri.linkgate.nav

import com.xehuri.linkgate.data.Site
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenTargetPlannerTest {

    private val withIpv6 = Site(
        id = "a",
        title = "站点",
        url = "https://example.com",
        ipv6Url = "https://[2408:8207:1234::1]:8443",
    )

    private val withoutIpv6 = Site(id = "b", title = "站点", url = "https://example.com")

    @Test
    fun `没开 IPv6 优先时用主地址`() {
        val plan = OpenTargetPlanner.plan(withIpv6, preferIpv6 = false, ipv6Available = true)
        assertEquals("https://example.com", plan.url)
        assertFalse(plan.usingIpv6)
        assertNull(plan.fallbackUrl)
    }

    @Test
    fun `没配 IPv6 地址时用主地址`() {
        val plan = OpenTargetPlanner.plan(withoutIpv6, preferIpv6 = true, ipv6Available = true)
        assertEquals("https://example.com", plan.url)
        assertFalse(plan.usingIpv6)
    }

    @Test
    fun `本机没有 IPv6 时用主地址`() {
        val plan = OpenTargetPlanner.plan(withIpv6, preferIpv6 = true, ipv6Available = false)
        assertEquals("https://example.com", plan.url)
        assertFalse(plan.usingIpv6)
    }

    @Test
    fun `条件都满足时优先用 IPv6 并保留回退地址`() {
        val plan = OpenTargetPlanner.plan(withIpv6, preferIpv6 = true, ipv6Available = true)
        assertEquals("https://[2408:8207:1234::1]:8443", plan.url)
        assertTrue(plan.usingIpv6)
        assertEquals("https://example.com", plan.fallbackUrl)
    }

    @Test
    fun `IPv6 地址与主地址相同时不做无意义切换`() {
        val same = withIpv6.copy(ipv6Url = "https://example.com")
        val plan = OpenTargetPlanner.plan(same, preferIpv6 = true, ipv6Available = true)
        assertEquals("https://example.com", plan.url)
        assertFalse(plan.usingIpv6)
        assertNull(plan.fallbackUrl)
    }
}

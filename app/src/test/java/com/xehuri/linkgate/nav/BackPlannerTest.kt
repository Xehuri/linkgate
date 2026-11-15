package com.xehuri.linkgate.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 返回栈契约（与用户确认过的语义）：
 * 网页上一级 → 站点选择界面（App 首屏） → 退出应用。
 * 站点自己的首页不是返回链上的独立一级。
 */
class BackPlannerTest {

    private val landing = "https://example.com/landing"

    @Test
    fun `有历史且不在入口页时回上一级网页`() {
        assertEquals(
            BackAction.WEB_GO_BACK,
            BackPlanner.inWebScreen(
                canGoBackInHistory = true,
                currentUrl = "https://example.com/other",
                landingUrl = landing,
            ),
        )
    }

    @Test
    fun `已经在入口页时有历史也优先回到选择界面`() {
        // 站点入口 URL 302 到 /login 后，历史里还留着原地址；
        // 如果先判断 canGoBack 就会在「原地址 → 重定向回入口」之间死循环。
        assertEquals(
            BackAction.CLOSE_WEB_SCREEN,
            BackPlanner.inWebScreen(canGoBackInHistory = true, currentUrl = landing, landingUrl = landing),
        )
    }

    @Test
    fun `历史到头且不在入口页时也回到选择界面`() {
        assertEquals(
            BackAction.CLOSE_WEB_SCREEN,
            BackPlanner.inWebScreen(
                canGoBackInHistory = false,
                currentUrl = "https://example.com/other",
                landingUrl = landing,
            ),
        )
    }

    @Test
    fun `已经在入口页且无历史时回到选择界面`() {
        assertEquals(
            BackAction.CLOSE_WEB_SCREEN,
            BackPlanner.inWebScreen(canGoBackInHistory = false, currentUrl = landing, landingUrl = landing),
        )
    }

    @Test
    fun `入口判定忽略末尾斜杠与锚点`() {
        assertEquals(
            BackAction.CLOSE_WEB_SCREEN,
            BackPlanner.inWebScreen(false, "https://example.com/landing/#section", "https://example.com/landing/"),
        )
    }

    @Test
    fun `入口判定忽略查询串`() {
        // 很多站点入口会带 ?from=xxx 之类参数
        assertEquals(
            BackAction.CLOSE_WEB_SCREEN,
            BackPlanner.inWebScreen(false, "https://example.com/landing?from=wx", landing),
        )
    }

    @Test
    fun `入口判定忽略 www 前缀`() {
        assertEquals(
            BackAction.CLOSE_WEB_SCREEN,
            BackPlanner.inWebScreen(false, "https://www.example.com/landing", landing),
        )
    }

    @Test
    fun `重定向后的真实入口可以正常退出`() {
        // 模拟入口 URL 302 到 /login 的场景：落地页记为 /login
        val realLanding = "https://example.com/login"
        assertEquals(
            BackAction.CLOSE_WEB_SCREEN,
            BackPlanner.inWebScreen(canGoBackInHistory = true, currentUrl = realLanding, landingUrl = realLanding),
        )
        // 还没记录落地页时，历史里还有上一级 → 先回上一级
        assertEquals(
            BackAction.WEB_GO_BACK,
            BackPlanner.inWebScreen(true, realLanding, "https://example.com"),
        )
    }

    @Test
    fun `当前地址为空时直接回到选择界面`() {
        assertEquals(BackAction.CLOSE_WEB_SCREEN, BackPlanner.inWebScreen(false, null, landing))
        assertEquals(BackAction.CLOSE_WEB_SCREEN, BackPlanner.inWebScreen(false, "  ", landing))
    }

    @Test
    fun `同一页判定忽略大小写`() {
        assertTrue(BackPlanner.samePage("HTTPS://Example.com/Path", "https://example.com/path"))
    }

    @Test
    fun `不同路径不算同一页`() {
        assertFalse(BackPlanner.samePage("https://example.com/a", "https://example.com/b"))
    }

    @Test
    fun `both null 不是同一页`() {
        assertFalse(BackPlanner.samePage(null, null))
    }
}

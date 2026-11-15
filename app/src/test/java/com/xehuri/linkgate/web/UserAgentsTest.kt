package com.xehuri.linkgate.web

import com.xehuri.linkgate.data.UserAgentMode
import com.xehuri.linkgate.data.ViewMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserAgentsTest {

    /** 模拟平板上的老 WebView（104） */
    private val defaultUa =
        "Mozilla/5.0 (Linux; Android 13; TestDevice Build/TESTBUILD; wv) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/104.0.5112.97 Safari/537.36"

    @Test
    fun `从系统 UA 里取出 Chrome 主版本`() {
        assertEquals("104", UserAgents.chromeMajor(defaultUa))
        assertEquals("120", UserAgents.chromeMajor("Mozilla/5.0 (Linux; Android 13)"))
    }

    @Test
    fun `桌面版 UA 用真实内核版本并去掉移动标记`() {
        val desktop = UserAgents.desktop(defaultUa)
        assertTrue(desktop.contains("Windows NT 10.0"))
        assertTrue(desktop.contains("Chrome/104.0.0.0"))
        assertTrue(!desktop.contains("Android"))
        assertTrue(!desktop.contains("Mobile"))
    }

    @Test
    fun `系统 UA 追加应用标识`() {
        val system = UserAgents.system(defaultUa, "1.1.0")
        assertTrue(system.startsWith(defaultUa))
        assertTrue(system.endsWith("LinkGate/1.1.0"))
    }

    @Test
    fun `跟随视图模式时桌面版用桌面 UA`() {
        val desktop = UserAgents.resolve(
            mode = UserAgentMode.FOLLOW_VIEW,
            viewMode = ViewMode.DESKTOP,
            customUa = "",
            defaultUa = defaultUa,
            appVersion = "1.1.0",
        )
        assertTrue(desktop.contains("Windows NT"))
    }

    @Test
    fun `跟随视图模式时移动版用系统 UA`() {
        val mobile = UserAgents.resolve(
            mode = UserAgentMode.FOLLOW_VIEW,
            viewMode = ViewMode.MOBILE,
            customUa = "",
            defaultUa = defaultUa,
            appVersion = "1.1.0",
        )
        assertTrue(mobile.contains("Android"))
        assertTrue(mobile.endsWith("LinkGate/1.1.0"))
    }

    @Test
    fun `自定义模式优先用自定义值`() {
        val custom = UserAgents.resolve(
            mode = UserAgentMode.CUSTOM,
            viewMode = ViewMode.DESKTOP,
            customUa = "  MyAgent/9  ",
            defaultUa = defaultUa,
            appVersion = "1.1.0",
        )
        assertEquals("MyAgent/9", custom)
    }

    @Test
    fun `自定义为空时退回系统 UA`() {
        val custom = UserAgents.resolve(
            mode = UserAgentMode.CUSTOM,
            viewMode = ViewMode.SYSTEM,
            customUa = "   ",
            defaultUa = defaultUa,
            appVersion = "1.1.0",
        )
        assertTrue(custom.endsWith("LinkGate/1.1.0"))
    }

    @Test
    fun `桌面模式不受视图模式影响`() {
        val desktop = UserAgents.resolve(
            mode = UserAgentMode.DESKTOP,
            viewMode = ViewMode.MOBILE,
            customUa = "",
            defaultUa = defaultUa,
            appVersion = "1.1.0",
        )
        assertTrue(desktop.contains("Windows NT"))
    }
}

package com.xehuri.linkgate.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsCodecTest {

    private val custom = AppSettings(
        preferIpv6 = true,
        wakeTapCount = 3,
        zoomEnabled = false,
        javaScriptEnabled = false,
        domStorageEnabled = false,
        databaseEnabled = false,
        loadImages = false,
        cacheMode = CacheMode.NO_CACHE,
        mixedContent = MixedContentLevel.BLOCK,
        textZoomPercent = 130,
        thirdPartyCookies = false,
        mediaAutoplay = false,
        viewMode = ViewMode.DESKTOP,
        hardwareAcceleration = false,
        darkMode = DarkModeSetting.ALWAYS_DARK,
        engine = BrowserEngine.WEBVIEW,
        userAgentMode = UserAgentMode.CUSTOM,
        customUserAgent = "MyAgent/1.0 (x=y)",
    )

    @Test
    fun `所有字段都能完整往返`() {
        val decoded = SettingsCodec.decode(SettingsCodec.encode(custom))
        assertEquals(custom, decoded)
    }

    @Test
    fun `空数据得到默认设置`() {
        assertEquals(AppSettings.DEFAULT, SettingsCodec.decode(null))
        assertEquals(AppSettings.DEFAULT, SettingsCodec.decode(""))
        assertEquals(AppSettings.DEFAULT, SettingsCodec.decode("随便一段文字"))
        assertEquals(AppSettings.DEFAULT, SettingsCodec.decode("LINKGATE-SETTINGS/2"))
    }

    @Test
    fun `默认值符合产品约定`() {
        val defaults = AppSettings.DEFAULT
        assertTrue("IPv6 优先默认关闭", !defaults.preferIpv6)
        assertEquals("悬浮条默认需要两次双指点击", 2, defaults.wakeTapCount)
        assertTrue("缩放默认开启", defaults.zoomEnabled)
        assertTrue("硬件加速默认开启", defaults.hardwareAcceleration)
        assertEquals(UserAgentMode.FOLLOW_VIEW, defaults.userAgentMode)
        assertEquals("默认内置 Gecko 内核", BrowserEngine.GECKO, defaults.engine)
        assertEquals(ViewMode.SYSTEM, defaults.viewMode)
        assertEquals(DarkModeSetting.SYSTEM, defaults.darkMode)
    }

    @Test
    fun `不认识的键被忽略`() {
        val raw = "LINKGATE-SETTINGS/1\nfuture_key=42\nprefer_ipv6=1"
        val decoded = SettingsCodec.decode(raw)
        assertTrue(decoded.preferIpv6)
        assertEquals(AppSettings.DEFAULT.wakeTapCount, decoded.wakeTapCount)
    }

    @Test
    fun `非法值只影响该项`() {
        val raw = buildString {
            append("LINKGATE-SETTINGS/1\n")
            append("prefer_ipv6=1\n")
            append("wake_taps=abc\n")
            append("engine=MARS\n")
            append("view_mode=MARS\n")
            append("cache_mode=NONSENSE\n")
            append("text_zoom=999\n")
        }
        val decoded = SettingsCodec.decode(raw)
        assertTrue(decoded.preferIpv6)
        assertEquals(AppSettings.DEFAULT.wakeTapCount, decoded.wakeTapCount)
        assertEquals(ViewMode.SYSTEM, decoded.viewMode)
        assertEquals("非法内核值回落默认", BrowserEngine.GECKO, decoded.engine)
        assertEquals(CacheMode.DEFAULT, decoded.cacheMode)
        // 越界的数字被夹回上限
        assertEquals(AppSettings.MAX_TEXT_ZOOM, decoded.textZoomPercent)
    }

    @Test
    fun `越界的连点次数被夹进合法区间`() {
        assertEquals(AppSettings.MIN_WAKE_TAPS, SettingsCodec.decode("LINKGATE-SETTINGS/1\nwake_taps=0").wakeTapCount)
        assertEquals(AppSettings.MAX_WAKE_TAPS, SettingsCodec.decode("LINKGATE-SETTINGS/1\nwake_taps=99").wakeTapCount)
    }

    @Test
    fun `自定义 UA 里的特殊字符不会破坏格式`() {
        val ua = "Agent/2.0 (a=b; c\\d)\nsecond line"
        val decoded = SettingsCodec.decode(SettingsCodec.encode(AppSettings.DEFAULT.copy(customUserAgent = ua)))
        assertEquals(ua, decoded.customUserAgent)
    }

    @Test
    fun `布尔值兼容多种写法`() {
        assertTrue(SettingsCodec.decode("LINKGATE-SETTINGS/1\nprefer_ipv6=true").preferIpv6)
        assertTrue(SettingsCodec.decode("LINKGATE-SETTINGS/1\nprefer_ipv6=ON").preferIpv6)
        assertTrue(!SettingsCodec.decode("LINKGATE-SETTINGS/1\nprefer_ipv6=0").preferIpv6)
        assertTrue(!SettingsCodec.decode("LINKGATE-SETTINGS/1\nprefer_ipv6=off").preferIpv6)
    }

    @Test
    fun `编码时多余的空白与超长 UA 被规范化`() {
        val encoded = SettingsCodec.encode(AppSettings.DEFAULT.copy(customUserAgent = "  " + "x".repeat(900) + "  "))
        val decoded = SettingsCodec.decode(encoded)
        assertEquals(AppSettings.MAX_UA_LENGTH, decoded.customUserAgent.length)
        assertTrue(!decoded.customUserAgent.startsWith(" "))
    }
}

package com.xehuri.linkgate.data

/** 缓存策略（对应 WebSettings.CacheMode） */
enum class CacheMode { DEFAULT, CACHE_ELSE_NETWORK, NO_CACHE }

/** 混合内容（http 资源嵌在 https 页面里）放行程度 */
enum class MixedContentLevel { ALLOW, COMPATIBILITY, BLOCK }

/** 视图模式：跟随系统 / 强制桌面版 / 强制移动版 */
enum class ViewMode { SYSTEM, DESKTOP, MOBILE }

/** 网页深浅色：跟随应用主题 / 永远浅色 / 永远深色 / 不做任何处理 */
enum class DarkModeSetting { SYSTEM, ALWAYS_LIGHT, ALWAYS_DARK, OFF }

/** 浏览器内核：内置 Gecko（不受系统 WebView 版本限制）或系统 WebView */
enum class BrowserEngine { GECKO, WEBVIEW }

/** 浏览器标识（UA）策略 */
enum class UserAgentMode { FOLLOW_VIEW, SYSTEM, DESKTOP, CUSTOM }

/**
 * 全局设置。所有值都有安全默认值，[sanitized] 负责把越界值夹回合法范围，
 * 保证 UI、编码器与 WebView 拿到的是同一份合法数据。
 *
 * 纯数据 + 纯函数，可单元测试；持久化见 [SettingsCodec] 与 [SettingsStore]。
 */
data class AppSettings(
    // ---------- 网络 ----------
    /** IPv6 优先：站点配了 IPv6 备用地址且本机有 IPv6 时，优先用 IPv6 地址打开 */
    val preferIpv6: Boolean = false,

    // ---------- 手势 ----------
    /** 悬浮控制条需要连续几次双指轻点才唤醒（1-3，默认 2） */
    val wakeTapCount: Int = 2,

    // ---------- WebView 参数 ----------
    /** 允许双指缩放 / 捏合缩放 */
    val zoomEnabled: Boolean = true,
    val javaScriptEnabled: Boolean = true,
    val domStorageEnabled: Boolean = true,
    val databaseEnabled: Boolean = true,
    /** 自动加载图片（关掉可省流量，网页会缺图） */
    val loadImages: Boolean = true,
    val cacheMode: CacheMode = CacheMode.DEFAULT,
    val mixedContent: MixedContentLevel = MixedContentLevel.ALLOW,
    /** 文本缩放百分比（50-200） */
    val textZoomPercent: Int = 100,
    val thirdPartyCookies: Boolean = true,
    /** 允许网页自动播放音视频（不要求用户手势） */
    val mediaAutoplay: Boolean = true,

    // ---------- 内核 ----------
    /** 用哪个渲染内核打开网页。默认内置 Gecko：系统 WebView 版本随厂商而定，老设备上会缺新特性 */
    val engine: BrowserEngine = BrowserEngine.GECKO,

    // ---------- 渲染 ----------
    val viewMode: ViewMode = ViewMode.SYSTEM,
    /** 硬件加速渲染；关掉改用软件渲染（个别机型上能修花屏，但更卡） */
    val hardwareAcceleration: Boolean = true,
    val darkMode: DarkModeSetting = DarkModeSetting.SYSTEM,

    // ---------- 浏览器标识 ----------
    val userAgentMode: UserAgentMode = UserAgentMode.FOLLOW_VIEW,
    /** 仅当 [userAgentMode] 为 CUSTOM 时生效 */
    val customUserAgent: String = "",
) {

    /** 是否使用桌面版布局 */
    val desktopLayout: Boolean get() = viewMode == ViewMode.DESKTOP

    fun sanitized(): AppSettings = copy(
        wakeTapCount = wakeTapCount.coerceIn(MIN_WAKE_TAPS, MAX_WAKE_TAPS),
        textZoomPercent = textZoomPercent.coerceIn(MIN_TEXT_ZOOM, MAX_TEXT_ZOOM),
        customUserAgent = customUserAgent.trim().take(MAX_UA_LENGTH),
    )

    companion object {
        const val MIN_WAKE_TAPS = 1
        const val MAX_WAKE_TAPS = 3
        const val MIN_TEXT_ZOOM = 50
        const val MAX_TEXT_ZOOM = 200
        const val MAX_UA_LENGTH = 512

        /** 出厂默认设置 */
        val DEFAULT = AppSettings()
    }
}

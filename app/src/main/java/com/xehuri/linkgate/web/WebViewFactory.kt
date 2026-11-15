package com.xehuri.linkgate.web

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.util.Log
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.xehuri.linkgate.BuildConfig
import com.xehuri.linkgate.data.AppSettings
import com.xehuri.linkgate.data.CacheMode
import com.xehuri.linkgate.data.DarkModeSetting
import com.xehuri.linkgate.data.MixedContentLevel
import com.xehuri.linkgate.data.PasswordStore
import com.xehuri.linkgate.data.ViewMode

/**
 * WebView 的创建与配置。默认配置的取舍是「让网页像在浏览器里一样能用」：
 * JS、DOM 存储、Cookie（含 third-party 与落盘）、媒体自动播放、混合内容、缩放、地理定位、
 * 文件选择、下载、外部协议跳转；用户可在设置界面里覆盖其中大部分。
 *
 * 安全边界（不随设置放开）：
 * - allowFileAccess = false，网页不能读本地文件；
 * - 证书错误一律取消（见 [LinkGateWebViewClient]）；
 * - 只允许 http/https 导航进入 WebView，其它协议交给系统。
 */
object WebViewFactory {

    private const val TAG = "LinkGateWeb"

    @Volatile
    private var cachedDefaultUserAgent: String? = null

    /** 系统 WebView 的默认 UA（解析桌面版 UA 的版本号要用它） */
    fun defaultUserAgent(context: Context): String {
        cachedDefaultUserAgent?.let { return it }
        val ua = runCatching { WebSettings.getDefaultUserAgent(context) }.getOrNull()
            ?: System.getProperty("http.agent")
            ?: "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        cachedDefaultUserAgent = ua
        return ua
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    fun create(context: Context, host: WebHost, settings: AppSettings): WebView {
        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
        val webView = WebView(context)

        // 关键修复：必须显式给 MATCH_PARENT 布局参数。
        // Compose 的 AndroidView 默认会先用 WRAP_CONTENT 量一次，WebView 以 0 高度初始化
        // 了视口，导致页面里的 100vh / 100dvh / height:100% 全部解析成 0。
        // 普通文档流页面看起来正常，但 dsh 这类整页依赖 height:100% 的 SPA 会直接白屏。
        webView.layoutParams = WebHost.matchParentParams()

        applySettings(webView, settings)

        // 打开站点前先回填该站点的 Cookie 快照（含 HttpOnly 会话 Cookie），
        // 这样关掉再打开不会掉登录态。快照按主地址存，IPv6 备用地址也共享同一份登录态。
        CookieStore(context).restoreFor(host.siteId, host.primaryUrl)

        webView.webChromeClient = LinkGateChromeClient(host)
        webView.webViewClient = LinkGateWebViewClient(host)
        webView.setDownloadListener(LinkGateDownloadListener(host))
        webView.isVerticalScrollBarEnabled = true
        webView.isHorizontalScrollBarEnabled = false

        // 触摸网页即锁定「入口落地地址」；连续两次双指轻点呼出控制条
        val wakeDetector = TwoFingerTapDetector(
            requiredTaps = settings.sanitized().wakeTapCount,
            onProgress = {},
            onTriggered = { host.onWakeGesture?.invoke() },
        )
        webView.setOnTouchListener { view, event ->
            host.lockLanding()
            wakeDetector.onTouch(view, event)
        }

        // 老系统 WebView（如 Chrome 104）缺少 AbortSignal.any 等新 API，先注入兼容垫片
        WebCompat.install(context, webView)

        // 桌面版网站模式：强制 980px 布局宽度（详见 DesktopViewport 的说明）
        if (settings.sanitized().viewMode == ViewMode.DESKTOP) {
            DesktopViewport.apply(webView)
        }

        // 密码桥：用 WebMessageListener（原生能拿到不可伪造的 sourceOrigin），
        // 页面只能查询/保存自己 origin 下的凭据。
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            try {
                WebViewCompat.addWebMessageListener(
                    webView,
                    "LinkGateBridge",
                    setOf("*"),
                    PasswordBridge(host, PasswordStore(context)),
                )
            } catch (e: Throwable) {
                Log.w(TAG, "注册密码桥失败", e)
            }
            // 下载桥：页面里的 blob:/data: 下载由此交给原生落盘（见 DownloadBridge）
            try {
                WebViewCompat.addWebMessageListener(
                    webView,
                    "LinkGateDownload",
                    setOf("*"),
                    DownloadBridge(context, host),
                )
            } catch (e: Throwable) {
                Log.w(TAG, "注册下载桥失败", e)
            }
        }

        host.webView = webView
        webView.loadUrl(host.homeUrl)
        return webView
    }

    /** 把设置应用到一个已存在的 WebView。可反复调用（幂等），供 Compose 的 update 使用。 */
    @SuppressLint("SetJavaScriptEnabled")
    fun applySettings(webView: WebView, settings: AppSettings) {
        val s = settings.sanitized()
        val context = webView.context
        webView.settings.apply {
            javaScriptEnabled = s.javaScriptEnabled
            domStorageEnabled = s.domStorageEnabled
            databaseEnabled = s.databaseEnabled
            loadsImagesAutomatically = s.loadImages
            blockNetworkImage = !s.loadImages
            cacheMode = when (s.cacheMode) {
                CacheMode.DEFAULT -> WebSettings.LOAD_DEFAULT
                CacheMode.CACHE_ELSE_NETWORK -> WebSettings.LOAD_CACHE_ELSE_NETWORK
                CacheMode.NO_CACHE -> WebSettings.LOAD_NO_CACHE
            }
            mixedContentMode = when (s.mixedContent) {
                MixedContentLevel.ALLOW -> WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                MixedContentLevel.COMPATIBILITY -> WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                MixedContentLevel.BLOCK -> WebSettings.MIXED_CONTENT_NEVER_ALLOW
            }
            textZoom = s.textZoomPercent
            mediaPlaybackRequiresUserGesture = !s.mediaAutoplay
            setSupportZoom(s.zoomEnabled)
            builtInZoomControls = s.zoomEnabled
            // 隐藏 WebView 自带的 +/- 缩放按钮，只保留双指捏合
            displayZoomControls = false
            useWideViewPort = s.viewMode != ViewMode.MOBILE
            loadWithOverviewMode = s.viewMode != ViewMode.MOBILE
            setGeolocationEnabled(true)
            javaScriptCanOpenWindowsAutomatically = true
            // 关掉多窗口：target=_blank / window.open 直接在当前 WebView 打开，避免弹窗式 WebView
            setSupportMultipleWindows(false)
            allowFileAccess = false
            allowContentAccess = true
            userAgentString = UserAgents.resolve(
                mode = s.userAgentMode,
                viewMode = s.viewMode,
                customUa = s.customUserAgent,
                defaultUa = defaultUserAgent(context),
                appVersion = BuildConfig.VERSION_NAME,
            )
        }
        runCatching {
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, s.thirdPartyCookies)
        }
        Log.d(
            TAG,
            "应用 WebView 参数: view=${s.viewMode} uaMode=${s.userAgentMode} zoom=${s.zoomEnabled} " +
                "js=${s.javaScriptEnabled} images=${s.loadImages} cache=${s.cacheMode} " +
                "mixed=${s.mixedContent} textZoom=${s.textZoomPercent} hw=${s.hardwareAcceleration} " +
                "dark=${s.darkMode} ua=${s.userAgentMode}",
        )
        // 渲染后端：默认不额外加层（走硬件加速）；关掉时强制软件渲染
        webView.setLayerType(
            if (s.hardwareAcceleration) View.LAYER_TYPE_NONE else View.LAYER_TYPE_SOFTWARE,
            null,
        )
    }

    /**
     * 网页深浅色。
     *
     * - [DarkModeSetting.SYSTEM] 跟随应用主题（默认行为）；
     * - 33+ 用「算法暗色」，29-32 用已废弃但有效的 forceDark；
     * - 厂商 WebView 不支持时静默跳过，页面自身的 prefers-color-scheme 仍然生效。
     */
    fun applyDarkMode(webView: WebView, mode: DarkModeSetting, systemDark: Boolean) {
        val dark = when (mode) {
            DarkModeSetting.SYSTEM -> systemDark
            DarkModeSetting.ALWAYS_DARK -> true
            DarkModeSetting.ALWAYS_LIGHT -> false
            DarkModeSetting.OFF -> return
        }
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                WebSettingsCompat.setAlgorithmicDarkeningAllowed(webView.settings, dark)
                return
            }
        } catch (e: Throwable) {
            Log.w(TAG, "算法暗色开关失败", e)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                webView.settings.isAlgorithmicDarkeningAllowed = dark
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                @Suppress("DEPRECATION")
                webView.settings.forceDark =
                    if (dark) WebSettings.FORCE_DARK_ON else WebSettings.FORCE_DARK_OFF
            }
        } catch (ignored: Throwable) {
            // 个别厂商 WebView 不支持该开关，忽略即可
        }
    }

}

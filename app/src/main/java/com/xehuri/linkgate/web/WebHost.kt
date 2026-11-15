package com.xehuri.linkgate.web

import android.content.Context
import android.view.ViewGroup
import android.webkit.WebView

/**
 * WebView 与 Compose 之间的桥：WebView 的回调（进度、标题、错误、文件选择等）
 * 都写到这里的 lambda 上，由 Compose 侧在 SideEffect 里刷新。
 *
 * 这里还负责记录「入口落地地址」：很多站点会把入口 URL 跳转到 /login 之类，
 * 如果返回栈拿用户填写的原始 URL 做入口判断，就会陷入「后退 → 跳转 → 回到同一页」的死循环。
 *
 * 落地地址规则：
 * - 首次加载完成后开始记录；
 * - 在锁定窗口（[LANDING_LOCK_DELAY_MS]）内，后续页面完成会继续刷新落地地址，覆盖跳转链；
 * - 用户一旦触摸网页、或窗口到期，落地地址锁定（防止正常浏览把入口改掉）。
 */
class WebHost(
    /** 用 applicationContext：权限检查、下载、外部跳转都不需要 Activity 上下文 */
    val appContext: Context,
    /** 站点 id，用于 Cookie 快照等按站点存储的数据 */
    val siteId: String,
    /** 本次实际要加载的地址（开启 IPv6 优先时可能是站点的 IPv6 备用地址） */
    val homeUrl: String,
    /** 用户配置的主地址；Cookie 快照按它回填，IPv6 打开失败也回退到它 */
    val primaryUrl: String = homeUrl,
    /** 回退地址：IPv6 备用地址打不开时改用主地址；null 表示没有回退 */
    val fallbackUrl: String? = null,
    /** 站点名，用于下载记录里标注来源 */
    val siteTitle: String = "",
) {
    var webView: WebView? = null
    var currentUrl: String? = null

    /** 首次加载完成时实际落地的地址（判断「是不是已经在入口页」用） */
    var landingUrl: String = homeUrl
        private set

    private var landingLocked = false
    private var lockScheduled = false
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    private var fallbackConsumed = false

    var onProgress: (Int) -> Unit = {}
    var onTitle: (String?) -> Unit = {}
    var onUrl: (String?) -> Unit = {}
    var onPageError: (String?) -> Unit = {}
    var onNotice: (String) -> Unit = {}
    var onPermissionHint: (List<String>) -> Unit = {}

    /** 连击唤醒控制条时的回调 */
    var onWakeGesture: (() -> Unit)? = null

    /** WebView 上报站点图标（favicon）时触发，用于首屏卡片缓存 */
    var onIcon: ((android.graphics.Bitmap) -> Unit)? = null

    /** 页面上检测到登录表单提交，请求保存密码 */
    var onPasswordSaveRequest: ((com.xehuri.linkgate.data.PasswordEntry) -> Unit)? = null

    /** 密码相关轻提示（如：已自动填充） */
    var onPasswordNotice: ((String) -> Unit)? = null

    /** 渲染进程崩溃（WebView 自己要杀掉宿主进程，必须在此处兜住） */
    var onRenderCrash: (() -> Unit)? = null

    /** 由 Compose 注入：启动系统文件选择器 */
    var chooserLauncher: ((android.content.Intent) -> Unit)? = null

    /** 网页发起的文件选择回调，等系统选择器返回后回填 */
    var fileChooserCallback: android.webkit.ValueCallback<Array<android.net.Uri>>? = null

    fun updateUrl(url: String?) {
        if (!url.isNullOrBlank()) currentUrl = url
    }

    /**
     * 主框架加载失败时调用：如果是 IPv6 备用地址且在回退范围内，返回应该改用的地址。
     * 每个站点实例只回退一次，避免主地址也失败时来回弹跳。
     */
    fun takeFallbackUrl(): String? {
        val fallback = fallbackUrl ?: return null
        if (fallbackConsumed) return null
        fallbackConsumed = true
        return fallback
    }

    /** 页面加载完成时调用：锁定窗口内持续刷新落地地址。 */
    fun captureLandingIfFirstLoad(url: String?) {
        if (landingLocked || url.isNullOrBlank()) return
        val isWeb = url.startsWith("http://", ignoreCase = true) ||
            url.startsWith("https://", ignoreCase = true)
        if (!isWeb) return
        landingUrl = url
        currentUrl = url
        if (!lockScheduled) {
            lockScheduled = true
            handler.postDelayed(
                { landingLocked = true },
                LANDING_LOCK_DELAY_MS,
            )
        }
    }

    /** 用户触摸网页：立刻锁定落地地址。 */
    fun lockLanding() {
        landingLocked = true
    }

    companion object {
        /** 首次加载完成后多久锁定落地地址（覆盖 302 / 客户端跳转链） */
        private const val LANDING_LOCK_DELAY_MS = 3_000L

        /** 创建 WebView 时必须显式给的布局参数，见 [WebViewFactory.create] 的注释 */
        fun matchParentParams(): ViewGroup.LayoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
    }
}

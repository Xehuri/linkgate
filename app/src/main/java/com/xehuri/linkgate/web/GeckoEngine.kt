package com.xehuri.linkgate.web

import android.content.Context
import android.net.Uri
import android.util.Log
import com.xehuri.linkgate.BuildConfig
import com.xehuri.linkgate.data.AppSettings
import com.xehuri.linkgate.data.PasswordOrigin
import com.xehuri.linkgate.perm.RuntimePermissions
import com.xehuri.linkgate.data.UserAgentMode
import com.xehuri.linkgate.data.ViewMode
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.WebResponse

/**
 * GeckoView 内核（内置引擎）的会话工厂。
 *
 * 为什么内置：系统 WebView 由厂商/系统决定版本（这台平板停在 104），CSS 锚点定位等新特性缺失，
 * 网页里的自定义浮层会错位；GeckoView 的引擎随 App 一起分发，不受系统限制。
 *
 * 代价：APK 体积（arm64 单 ABI 约 +85MB）、每个站点需要重新登录（Cookie 在 Gecko 自己的 profile 里）。
 *
 * 本类只做「会话 + 委托」的搭接，页面级的 UI（沉浸、控制条、返回栈）在 [com.xehuri.linkgate.ui.GeckoScreen]。
 */
object GeckoEngine {

    private const val TAG = "GeckoEngine"
    private const val MAX_DOWNLOAD_BYTES = 64L * 1024 * 1024

    @Volatile
    private var runtime: GeckoRuntime? = null

    /** GeckoRuntime 是进程级单例、初始化很重，懒加载一次。 */
    fun runtime(context: Context): GeckoRuntime {
        runtime?.let { return it }
        return synchronized(this) {
            runtime ?: run {
                val settings = GeckoRuntimeSettings.Builder()
                    .javaScriptEnabled(true)
                    .aboutConfigEnabled(false)
                    .consoleOutput(BuildConfig.DEBUG)
                    .build()
                GeckoRuntime.create(context.applicationContext, settings).also {
                    runtime = it
                    Log.i(TAG, "GeckoRuntime 就绪")
                }
            }
        }
    }

    /** 会话回调：由 UI 层注入。 */
    class Hooks(
        val onProgress: (Int) -> Unit = {},
        val onTitle: (String?) -> Unit = {},
        val onUrl: (String?) -> Unit = {},
        val onCanGoBack: (Boolean) -> Unit = {},
        val onLoaded: (String?) -> Unit = {},
        val onNotice: (String) -> Unit = {},
        val onCrash: () -> Unit = {},
    )

    fun createSession(context: Context, settings: AppSettings, hooks: Hooks): GeckoSession {
        val s = settings.sanitized()
        val sessionSettings = GeckoSessionSettings.Builder()
            .apply {
                when (s.viewMode) {
                    ViewMode.DESKTOP -> {
                        userAgentMode(GeckoSessionSettings.USER_AGENT_MODE_DESKTOP)
                        viewportMode(GeckoSessionSettings.VIEWPORT_MODE_DESKTOP)
                    }

                    ViewMode.MOBILE -> {
                        userAgentMode(GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
                        viewportMode(GeckoSessionSettings.VIEWPORT_MODE_MOBILE)
                    }

                    ViewMode.SYSTEM -> {
                        viewportMode(GeckoSessionSettings.VIEWPORT_MODE_MOBILE)
                        if (s.userAgentMode == UserAgentMode.DESKTOP) {
                            userAgentMode(GeckoSessionSettings.USER_AGENT_MODE_DESKTOP)
                        }
                    }
                }
                // 自定义 UA 优先
                if (s.userAgentMode == UserAgentMode.CUSTOM && s.customUserAgent.isNotBlank()) {
                    userAgentOverride(s.customUserAgent)
                }
                displayMode(GeckoSessionSettings.DISPLAY_MODE_FULLSCREEN)
            }
            .build()

        val session = GeckoSession(sessionSettings)

        var currentUrl: String? = null

        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onProgressChange(s: GeckoSession, progress: Int) = hooks.onProgress(progress)

            override fun onPageStart(s: GeckoSession, url: String) {
                currentUrl = url
                hooks.onUrl(url)
                hooks.onProgress(1)
            }

            override fun onPageStop(s: GeckoSession, success: Boolean) {
                hooks.onProgress(100)
                hooks.onLoaded(currentUrl)
            }
        }

        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onCanGoBack(s: GeckoSession, value: Boolean) = hooks.onCanGoBack(value)

            override fun onLoadRequest(
                s: GeckoSession,
                request: GeckoSession.NavigationDelegate.LoadRequest,
            ): GeckoResult<AllowOrDeny> {
                val url = request.uri
                // 非 http(s) 协议交给系统（与 WebView 版的外部协议策略一致）
                if (!ExternalLinks.isWebUrl(url)) {
                    ExternalLinks.open(context, url)
                    return GeckoResult.deny()
                }
                return GeckoResult.allow()
            }
        }

        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(s: GeckoSession, title: String?) = hooks.onTitle(title)

            /** 服务端给了 Content-Disposition 时，Gecko 不再内联渲染，交给这里保存。 */
            override fun onExternalResponse(s: GeckoSession, response: WebResponse) {
                handleExternalResponse(context, response, hooks)
            }

            override fun onCrash(s: GeckoSession) {
                Log.w(TAG, "Gecko 内容进程崩溃")
                hooks.onCrash()
            }
        }

        session.permissionDelegate = object : GeckoSession.PermissionDelegate {
            override fun onContentPermissionRequest(
                s: GeckoSession,
                perm: GeckoSession.PermissionDelegate.ContentPermission,
            ): GeckoResult<Int> {
                // 与 WebView 版一致：系统权限已授权就放行，否则拒绝并提示
                val needed = when (perm.permission) {
                    GeckoSession.PermissionDelegate.PERMISSION_GEOLOCATION ->
                        listOf(android.Manifest.permission.ACCESS_FINE_LOCATION)

                    // 其余（桌面通知、自动播放、持久化存储等）按「放行」处理，与 WebView 版的宽松策略一致
                    else -> emptyList()
                }
                val missing = needed.filterNot { RuntimePermissions.isGranted(context, it) }
                return if (missing.isEmpty()) {
                    GeckoResult.fromValue(GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW)
                } else {
                    GeckoResult.fromValue(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY)
                }
            }

            override fun onAndroidPermissionsRequest(
                s: GeckoSession,
                permissions: Array<out String>?,
                callback: GeckoSession.PermissionDelegate.Callback,
            ) {
                val missing = permissions.orEmpty().filterNot { RuntimePermissions.isGranted(context, it) }
                if (missing.isEmpty()) callback.grant() else callback.reject()
            }

        }

        // 必须先用 runtime 打开会话，否则 GeckoView.setSession 时运行时上下文为空（会 NPE 崩溃）
        session.open(runtime(context))
        return session
    }

    /** Content-Disposition 触发的下载：把 body 读出来交给 DownloadSaver（与 WebView 版共用下载管理器）。 */
    private fun handleExternalResponse(context: Context, response: WebResponse, hooks: Hooks) {
        val appContext = context.applicationContext
        val name = guessName(response)
        val mime = response.headers["content-type"]?.substringBefore(';')?.trim().orEmpty()
        val origin = runCatching { PasswordOrigin.fromUrl(response.uri) }.getOrNull().orEmpty()
        Thread({
            val bytes = runCatching {
                (response.body ?: throw IllegalStateException("没有响应体")).use { input ->
                    val out = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_DOWNLOAD_BYTES) throw IllegalStateException("文件超过 64MB")
                        out.write(buffer, 0, read)
                    }
                    out.toByteArray()
                }
            }.getOrNull()

            android.os.Handler(android.os.Looper.getMainLooper()).post {
                if (bytes == null) {
                    hooks.onNotice("下载失败：$name")
                    return@post
                }
                val saved = DownloadSaver.save(appContext, name, mime, bytes)
                if (saved == null) {
                    hooks.onNotice("下载保存失败：$name")
                    return@post
                }
                val record = com.xehuri.linkgate.data.DownloadRecord(
                    id = "gk:${System.currentTimeMillis()}",
                    name = saved.displayName,
                    mime = mime.ifBlank { "application/octet-stream" },
                    size = bytes.size.toLong(),
                    uri = saved.uri,
                    pageUrl = origin,
                    siteTitle = "",
                    createdAt = System.currentTimeMillis(),
                    ok = true,
                )
                DownloadCenter.publish(appContext, record)
                DownloadSaver.notifySaved(appContext, record)
                hooks.onNotice("已保存到${saved.location}")
            }
        }, "linkgate-gecko-download").start()
    }

    private fun guessName(response: WebResponse): String {
        val disposition = response.headers["content-disposition"].orEmpty()
        val star = Regex("filename\\*\\s*=\\s*[^']*''([^;]+)", RegexOption.IGNORE_CASE).find(disposition)
        val plain = Regex("filename\\s*=\\s*\"?([^\";]+)\"?", RegexOption.IGNORE_CASE).find(disposition)
        val raw = star?.groupValues?.getOrNull(1) ?: plain?.groupValues?.getOrNull(1)
        val fromUri = Uri.parse(response.uri).lastPathSegment
        val name = (raw ?: fromUri ?: "download").trim().trim('"').substringBefore('?')
        return name.replace('/', '_').take(120).ifBlank { "download" }
    }
}

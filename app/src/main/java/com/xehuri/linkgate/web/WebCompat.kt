package com.xehuri.linkgate.web

import android.content.Context
import android.util.Log
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/**
 * 老版系统 WebView 的 JS 兼容垫片注入。
 *
 * 小米平板 5 等设备的系统 WebView 可能停在 Chrome 104 左右，而 dsh 客户端插件会用到
 * `AbortSignal.any`（Chrome 116）等新 API，直接报 "TypeError: ... is not a function"，
 * 表现为「页面渲染出来了但功能不正常」。
 *
 * 做法：
 * 1. 优先用 `WebViewCompat.addDocumentStartJavaScript` 在**文档开始**就注入，早于页面脚本；
 * 2. 不支持该特性时退回 `evaluateJavascript`（页面开始加载时注入），覆盖绝大多数站点；
 * 3. 垫片本身全部 feature-detect，不影响已支持这些 API 的新 WebView。
 */
object WebCompat {

    private const val TAG = "LinkGateCompat"
    private val ASSET_NAMES = listOf("web-compat.js", "web-passwords.js", "web-downloads.js")

    @Volatile
    private var cachedScript: String? = null

    fun script(context: Context): String {
        cachedScript?.let { return it }
        synchronized(this) {
            cachedScript?.let { return it }
            val text = try {
                ASSET_NAMES.joinToString("\n;\n") { name ->
                    context.applicationContext.assets.open(name)
                        .bufferedReader(Charsets.UTF_8)
                        .use { it.readText() }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "读取兼容脚本失败", e)
                ""
            }
            cachedScript = text
            return text
        }
    }

    /** 创建 WebView 后、加载页面前调用。 */
    fun install(context: Context, webView: WebView) {
        val script = script(context)
        if (script.isEmpty()) return
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            try {
                WebViewCompat.addDocumentStartJavaScript(webView, script, setOf("*"))
                Log.d(TAG, "document-start 注入已启用")
                return
            } catch (e: Throwable) {
                Log.w(TAG, "document-start 注入失败，回退到页面开始注入", e)
            }
        }
        // 兜底：立即注入一次，并依赖 onPageStarted 的重复注入
        webView.evaluateJavascript(script, null)
    }

    /** 每次主框架开始加载时兜底注入（文档开始脚本不支持时才有实际作用）。 */
    fun injectOnPageStarted(webView: WebView?) {
        val script = cachedScript ?: return
        if (script.isEmpty()) return
        try {
            webView?.evaluateJavascript(script, null)
        } catch (e: Throwable) {
            Log.w(TAG, "页面开始注入失败", e)
        }
    }
}

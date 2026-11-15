package com.xehuri.linkgate.web

import android.graphics.Bitmap
import android.net.http.SslError
import android.util.Log
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * 页面导航与错误上报。主框架错误会转成 Compose 里的「重试 / 首屏 / 站点列表」面板，
 * 而不是把 WebView 自带的灰色错误页暴露给用户。
 *
 * 这里同时维护「当前地址」与「入口落地地址」：落地地址只取第一次成功加载完成时的 URL，
 * 用来解决站点 302 到 /login 后返回键死循环的问题。
 */
class LinkGateWebViewClient(private val host: WebHost) : WebViewClient() {

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val url = request?.url?.toString() ?: return false
        if (ExternalLinks.isWebUrl(url)) return false
        return ExternalLinks.open(host.appContext, url)
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        Log.d(TAG, "onPageStarted $url")
        WebCompat.injectOnPageStarted(view)
        host.onPageError(null)
        host.updateUrl(url)
        host.onUrl(url)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        Log.d(TAG, "onPageFinished $url")
        host.captureLandingIfFirstLoad(url)
        host.updateUrl(url)
        host.onUrl(url)
    }

    override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
        super.doUpdateVisitedHistory(view, url, isReload)
        host.updateUrl(url)
        host.onUrl(url)
    }

    /**
     * 渲染进程崩溃。这里必须返回 true 表示「已处理」：否则系统会认定宿主没有能力恢复，
     * 直接把整个 App 进程杀掉，用户看到的就是闪退。返回 true 后由 Compose 侧重建议 WebView。
     */
    override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
        Log.w(TAG, "WebView 渲染进程崩溃，didCrash=${detail?.didCrash()}")
        host.onRenderCrash?.invoke()
        return true
    }

    override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: WebResourceError?,
    ) {
        if (request?.isForMainFrame == true) {
            Log.w(
                TAG,
                "主框架加载失败 url=${request.url} code=${error?.errorCode} desc=${error?.description}",
            )
            host.onPageError(describeError(error))
        }
    }

    override fun onReceivedHttpError(
        view: WebView?,
        request: WebResourceRequest?,
        errorResponse: WebResourceResponse?,
    ) {
        if (request?.isForMainFrame == true && errorResponse != null && errorResponse.statusCode >= 400) {
            host.onPageError("HTTP ${errorResponse.statusCode}")
        }
    }

    override fun onReceivedSslError(
        view: WebView?,
        handler: SslErrorHandler?,
        error: SslError?,
    ) {
        // 证书有问题一律取消，不做「继续访问」的降级
        handler?.cancel()
        host.onPageError("TLS 证书错误（${error?.primaryError ?: "unknown"}）")
    }

    private companion object {
        const val TAG = "LinkGateWebView"
    }

    private fun describeError(error: WebResourceError?): String {
        if (error == null) return "网络错误"
        val description = error.description?.toString().orEmpty()
        return if (description.isBlank()) "网络错误（code ${error.errorCode}）" else description
    }
}

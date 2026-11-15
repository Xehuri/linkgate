package com.xehuri.linkgate.web

import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import com.xehuri.linkgate.BuildConfig
import com.xehuri.linkgate.perm.PermNames
import com.xehuri.linkgate.perm.RuntimePermissions

/**
 * WebView 的「浏览器外壳」部分，核心是自动放行网页的权限请求：
 *
 * - h5 请求麦克风 / 摄像头（getUserMedia）：只要 App 已经拿到对应系统权限，就直接 grant，
 *   不再逐站点弹「是否允许」；
 * - h5 请求定位：只要 App 有定位权限就直接允许；
 * - 网页 <input type=file> 唤起系统文件选择器。
 *
 * 如果系统权限被用户拒绝，则 deny 并让 Compose 侧提示去授权 —— 因为 WebView 无法绕过系统权限。
 */
class LinkGateChromeClient(private val host: WebHost) : WebChromeClient() {

    override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
        if (icon != null) host.onIcon?.invoke(icon)
    }

    override fun onProgressChanged(view: WebView?, newProgress: Int) {
        if (newProgress in 0..100) host.onProgress(newProgress)
    }

    override fun onReceivedTitle(view: WebView?, title: String?) {
        host.onTitle(title)
    }

    override fun onPermissionRequest(request: PermissionRequest?) {
        if (request == null) return
        val resources = request.resources
        val missing = resources
            .mapNotNull { requiredSystemPermission(it) }
            .distinct()
            .filterNot { RuntimePermissions.isGranted(host.appContext, it) }

        if (missing.isEmpty()) {
            request.grant(resources)
            Log.d(TAG, "onPermissionRequest granted: ${resources.joinToString()}")
        } else {
            request.deny()
            Log.w(TAG, "onPermissionRequest denied, missing OS permissions: ${missing.joinToString()}")
            host.onPermissionHint(missing)
        }
    }

    override fun onPermissionRequestCanceled(request: PermissionRequest?) {
        Log.d(TAG, "onPermissionRequestCanceled")
    }

    override fun onGeolocationPermissionsShowPrompt(
        origin: String?,
        callback: GeolocationPermissions.Callback?,
    ) {
        if (origin == null || callback == null) return
        val granted = RuntimePermissions.isGranted(host.appContext, PermNames.ACCESS_FINE_LOCATION) ||
            RuntimePermissions.isGranted(host.appContext, PermNames.ACCESS_COARSE_LOCATION)
        callback.invoke(origin, granted, false)
        if (!granted) host.onPermissionHint(listOf(PermNames.ACCESS_FINE_LOCATION))
    }

    override fun onShowFileChooser(
        webView: WebView?,
        filePathCallback: ValueCallback<Array<Uri>>?,
        fileChooserParams: FileChooserParams?,
    ): Boolean {
        if (filePathCallback == null || fileChooserParams == null) return false
        // 取消上一次还没回填的选择，避免回调泄漏
        host.fileChooserCallback?.onReceiveValue(null)
        host.fileChooserCallback = filePathCallback

        val launcher = host.chooserLauncher ?: run {
            host.fileChooserCallback = null
            return false
        }
        val intent = try {
            fileChooserParams.createIntent()
        } catch (e: Exception) {
            host.fileChooserCallback = null
            return false
        }
        return try {
            launcher(intent)
            true
        } catch (e: Exception) {
            host.fileChooserCallback?.onReceiveValue(null)
            host.fileChooserCallback = null
            false
        }
    }

    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
        // 只在调试包打日志：这在 WebView→Java 的 JNI 回调里执行，正式包每打一条都要走一次 logd，
        // 页面控制台输出多时（例如 dsh 这种重度 SPA）会明显增加主线程负担。
        if (BuildConfig.DEBUG && consoleMessage != null) {
            Log.d(TAG, "[web] ${consoleMessage.message()} @${consoleMessage.lineNumber()}")
        }
        return true
    }

    private fun requiredSystemPermission(resource: String): String? = when (resource) {
        PermissionRequest.RESOURCE_AUDIO_CAPTURE -> PermNames.RECORD_AUDIO
        PermissionRequest.RESOURCE_VIDEO_CAPTURE -> PermNames.CAMERA
        else -> null
    }

    companion object {
        private const val TAG = "LinkGateWeb"
    }
}

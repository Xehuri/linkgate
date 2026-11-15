package com.xehuri.linkgate.web

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.webkit.CookieManager
import android.webkit.DownloadListener
import com.xehuri.linkgate.data.DataUrl
import java.net.URLDecoder

/**
 * 网页里 http(s) 下载链接交给系统 DownloadManager：下载完成会发通知，
 * 文件落到公共「下载」目录；公共目录写入失败时退回应用私有目录。
 *
 * `data:` 链接由 [DataUrl] 在本地解码后交给 [DownloadSaver]（DownloadManager 处理不了 data:）。
 * `blob:` 链接由页面里的 `web-downloads.js` + [DownloadBridge] 负责，这里只做兜底提示。
 */
class LinkGateDownloadListener(private val host: WebHost) : DownloadListener {

    override fun onDownloadStart(
        url: String?,
        userAgent: String?,
        contentDisposition: String?,
        mimetype: String?,
        contentLength: Long,
    ) {
        if (url.isNullOrBlank()) return

        if (url.startsWith("data:", ignoreCase = true)) {
            saveDataUrl(url)
            return
        }
        if (url.startsWith("blob:", ignoreCase = true)) {
            host.onNotice("这个链接的内容只在页面内存里，点页面上的下载按钮才能保存")
            return
        }

        val manager = host.appContext.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
        if (manager == null) {
            host.onNotice("系统下载服务不可用")
            return
        }
        val fileName = guessFileName(url, contentDisposition)
        val enqueued = runCatching {
            manager.enqueue(buildRequest(url, userAgent, mimetype, fileName, publicDir = true))
            true
        }.getOrElse {
            runCatching {
                manager.enqueue(buildRequest(url, userAgent, mimetype, fileName, publicDir = false))
                true
            }.getOrDefault(false)
        }
        host.onNotice(if (enqueued) "已开始下载：$fileName" else "下载失败，可长按链接复制后用浏览器下载")
    }

    private fun saveDataUrl(url: String) {
        val decoded = DataUrl.decode(url)
        if (decoded == null) {
            host.onNotice("这个内嵌文件无法解析，下载已取消")
            return
        }
        val appContext = host.appContext
        Thread({
            val saved = DownloadSaver.save(appContext, decoded.fileName, decoded.mime, decoded.bytes)
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                if (saved == null) {
                    host.onNotice("内嵌文件保存失败")
                } else {
                    host.onNotice("已保存到${saved.location}")
                    DownloadSaver.notifySaved(appContext, saved)
                }
            }
        }, "linkgate-download-data").start()
    }

    private fun buildRequest(
        url: String,
        userAgent: String?,
        mimetype: String?,
        fileName: String,
        publicDir: Boolean,
    ): DownloadManager.Request {
        val request = DownloadManager.Request(Uri.parse(url))
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        request.setTitle(fileName)
        request.setDescription("LinkGate 下载")
        if (!mimetype.isNullOrBlank()) request.setMimeType(mimetype)
        request.setAllowedOverMetered(true)
        request.setAllowedOverRoaming(true)
        if (publicDir) {
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        } else {
            request.setDestinationInExternalFilesDir(host.appContext, Environment.DIRECTORY_DOWNLOADS, fileName)
        }
        if (!userAgent.isNullOrBlank()) request.addRequestHeader("User-Agent", userAgent)
        CookieManager.getInstance().getCookie(url)?.let { request.addRequestHeader("Cookie", it) }
        // 有些站点会校验 Referer 才允许下载
        host.currentUrl?.let { request.addRequestHeader("Referer", it) }
        return request
    }

    private fun guessFileName(url: String, contentDisposition: String?): String {
        val fromHeader = contentDisposition?.let { header ->
            FILENAME_STAR.find(header)?.groupValues?.getOrNull(1)
                ?: FILENAME.find(header)?.groupValues?.getOrNull(1)
        }?.let { clean(fileName = it) }
        val fromUrl = Uri.parse(url).lastPathSegment
            ?.substringBefore('?')
            ?.let { clean(fileName = it) }
        val name = (fromHeader ?: fromUrl)?.takeIf { it.isNotBlank() } ?: "download"
        return name.replace('/', '_').replace('\\', '_').take(120)
    }

    private fun clean(fileName: String): String {
        val trimmed = fileName.trim().trim('"')
        return try {
            URLDecoder.decode(trimmed, "UTF-8")
        } catch (e: Exception) {
            trimmed
        }
    }

    companion object {
        private const val TAG = "LinkGateDownload"
        private val FILENAME_STAR = Regex("filename\\*\\s*=\\s*[^']*''([^;]+)", RegexOption.IGNORE_CASE)
        private val FILENAME = Regex("filename\\s*=\\s*\"?([^\";]+)\"?", RegexOption.IGNORE_CASE)
    }
}

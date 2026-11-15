package com.xehuri.linkgate.web

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.WebView
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.Base64

/**
 * 页面里的 blob:/data: 下载 → 原生写盘。
 *
 * 协议（JS → 原生，全部 JSON 字符串，分批传输避免单条消息过大）：
 * - `{"type":"start","id":"dl-1","name":"报告.pdf","mime":"application/pdf","size":12345}`
 * - `{"type":"chunk","id":"dl-1","seq":0,"data":"<标准 base64>"}`（可多次）
 * - `{"type":"end","id":"dl-1"}`  → 触发落盘 + 通知
 * - `{"type":"error","id":"dl-1","message":"..."}` → 只提示，不落盘
 *
 * 安全边界：
 * - 只处理主框架消息（`isMainFrame`），不理会 iframe；
 * - 只接受 `blob:`/`data:` 来源的转交（JS 侧限制），原生不再信任页面给的其它协议；
 * - 单文件上限 [MAX_BYTES]，超限直接丢弃并提示，避免被页面拖垮内存。
 */
class DownloadBridge(
    private val context: Context,
    private val host: WebHost,
) : WebViewCompat.WebMessageListener {

    private val pending = HashMap<String, Pending>()
    private val main = Handler(Looper.getMainLooper())

    private class Pending(val name: String, val mime: String) {
        val buffer = ByteArrayOutputStream()
    }

    override fun onPostMessage(
        view: WebView,
        message: WebMessageCompat,
        sourceOrigin: android.net.Uri,
        isMainFrame: Boolean,
        replyProxy: JavaScriptReplyProxy,
    ) {
        if (!isMainFrame) return
        val payload = try {
            JSONObject(message.data ?: return)
        } catch (e: Throwable) {
            return
        }
        val id = payload.optString("id").take(64)
        when (payload.optString("type")) {
            "start" -> {
                if (id.isEmpty()) return
                if (pending.size >= MAX_CONCURRENT) {
                    Log.w(TAG, "并发下载过多，忽略 $id")
                    return
                }
                pending[id] = Pending(
                    name = payload.optString("name").take(160).ifBlank { "download" },
                    mime = payload.optString("mime").take(120).ifBlank { "application/octet-stream" },
                )
            }

            "chunk" -> {
                val item = pending[id] ?: return
                val data = payload.optString("data")
                if (data.isEmpty()) return
                val bytes = try {
                    Base64.getDecoder().decode(data)
                } catch (e: Throwable) {
                    Log.w(TAG, "base64 解码失败: $id", e)
                    pending.remove(id)
                    return
                }
                if (item.buffer.size() + bytes.size > MAX_BYTES) {
                    pending.remove(id)
                    host.onNotice("文件过大，已取消下载（上限 ${MAX_BYTES / (1024 * 1024)}MB）")
                    return
                }
                item.buffer.write(bytes)
            }

            "end" -> finish(id)

            "error" -> {
                pending.remove(id)
                val detail = payload.optString("message").take(120)
                host.onNotice(if (detail.isBlank()) "网页下载失败" else "网页下载失败：$detail")
            }
        }
    }

    private fun finish(id: String) {
        val item = pending.remove(id) ?: return
        val bytes = item.buffer.toByteArray()
        if (bytes.isEmpty()) {
            host.onNotice("下载内容为空，已取消")
            return
        }
        Log.d(TAG, "接收完成 ${item.name} (${bytes.size} 字节)")
        val appContext = context.applicationContext
        Thread({
            val saved = DownloadSaver.save(appContext, item.name, item.mime, bytes)
            main.post {
                if (saved == null) {
                    host.onNotice("下载保存失败：${item.name}")
                } else {
                    host.onNotice("已保存到${saved.location}")
                    DownloadSaver.notifySaved(appContext, saved)
                }
            }
        }, "linkgate-download-save").start()
    }

    companion object {
        private const val TAG = "LinkGateDownload"
        private const val MAX_BYTES = 64L * 1024 * 1024
        private const val MAX_CONCURRENT = 4
    }
}

package com.xehuri.linkgate.web

import android.net.Uri
import android.webkit.WebView
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import com.xehuri.linkgate.data.PasswordEntry
import com.xehuri.linkgate.data.PasswordOrigin
import com.xehuri.linkgate.data.PasswordStore
import org.json.JSONArray
import org.json.JSONObject

/**
 * 密码管理器与页面之间的安全桥。
 *
 * 用 `WebViewCompat.addWebMessageListener` 而不是 `addJavascriptInterface`：
 * 原生侧能拿到 WebView 提供的 **sourceOrigin**（不可被页面伪造），
 * 因此凭据只会在「页面自己的 origin」下查询/保存，不会跨站串数据。
 *
 * 协议（JS ⇄ 原生，全部 JSON 字符串）：
 * - JS → 原生 `{"type":"save","username":"...","password":"..."}`：表单提交后请求保存
 * - JS → 原生 `{"type":"query"}`：询问当前 origin 已保存的凭据
 * - 原生 → JS `{"type":"credentials","entries":[{"username","password"}]}`：回填数据
 * - JS → 原生 `{"type":"filled"}`：已自动填充（原生弹轻提示）
 */
class PasswordBridge(
    private val host: WebHost,
    private val store: PasswordStore,
) : WebViewCompat.WebMessageListener {

    override fun onPostMessage(
        view: WebView,
        message: WebMessageCompat,
        sourceOrigin: Uri,
        isMainFrame: Boolean,
        replyProxy: JavaScriptReplyProxy,
    ) {
        if (!isMainFrame) return
        val origin = PasswordOrigin.fromUri(java.net.URI(sourceOrigin.toString())) ?: return
        val payload = try {
            JSONObject(message.data ?: return)
        } catch (e: Throwable) {
            return
        }
        when (payload.optString("type")) {
            "save" -> {
                val password = payload.optString("password")
                if (password.isEmpty()) return
                val username = payload.optString("username").take(256)
                host.onPasswordSaveRequest?.invoke(
                    PasswordEntry(
                        origin = origin,
                        username = username,
                        password = password,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
            }

            "query" -> {
                val entries = store.find(origin)
                replyProxy.postMessage(buildCredentialsJson(entries))
            }

            "filled" -> host.onPasswordNotice?.invoke("已自动填入已保存的密码")
        }
    }

    private fun buildCredentialsJson(entries: List<PasswordEntry>): String {
        val root = JSONObject()
        root.put("type", "credentials")
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject().apply {
                    put("username", entry.username)
                    put("password", entry.password)
                },
            )
        }
        root.put("entries", array)
        return root.toString()
    }
}

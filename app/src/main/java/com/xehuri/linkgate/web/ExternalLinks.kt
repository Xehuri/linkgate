package com.xehuri.linkgate.web

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * WebView 内部能处理的协议交给 WebView；其它协议（intent://、mailto:、tel:、应用深链）
 * 交给系统应用处理。处理不了就返回 false，让 WebView 保持原状而不是崩掉。
 */
object ExternalLinks {

    private val WEB_SCHEMES = listOf(
        "http://", "https://", "file://", "about:", "data:", "blob:", "javascript:",
    )

    fun isWebUrl(url: String): Boolean = WEB_SCHEMES.any { url.startsWith(it, ignoreCase = true) }

    fun open(context: Context, url: String): Boolean {
        if (url.isBlank()) return false
        return try {
            val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun openInBrowser(context: Context, url: String): Boolean {
        if (url.isBlank()) return false
        return try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
            true
        } catch (e: Exception) {
            false
        }
    }
}

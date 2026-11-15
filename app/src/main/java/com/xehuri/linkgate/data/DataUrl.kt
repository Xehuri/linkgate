package com.xehuri.linkgate.data

import java.net.URLDecoder
import java.util.Base64

/**
 * `data:` URL 的解析（纯 JVM，可单测）。
 *
 * 格式：`data:[<mediatype>][;base64],<data>`
 * - 带 `;base64` 时数据段先做 URL 解码再做 Base64 解码；
 * - 否则按百分号编码解码成字节。
 *
 * 网页里「导出 CSV / 下载小图片」常用 `data:` 链接，WebView 的 DownloadListener
 * 收到的就是这种 URL，需要原生自己解码才能落盘。
 */
object DataUrl {

    data class Decoded(
        val mime: String,
        val fileName: String,
        val bytes: ByteArray,
    )

    private const val MAX_BYTES = 32 * 1024 * 1024

    fun decode(url: String?): Decoded? {
        if (url == null) return null
        if (!url.startsWith("data:", ignoreCase = true)) return null
        val body = url.substring(5)
        val comma = body.indexOf(',')
        if (comma < 0) return null
        val meta = body.substring(0, comma)
        val payload = body.substring(comma + 1)

        val isBase64 = meta.lowercase().split(';').any { it.trim() == "base64" }
        val mime = meta.split(';').firstOrNull()?.trim()
            ?.takeIf { it.isNotEmpty() && it.contains('/') }
            ?: "application/octet-stream"

        val bytes = try {
            if (isBase64) {
                val cleaned = percentDecode(payload).filterNot { it.isWhitespace() }
                Base64.getDecoder().decode(cleaned)
            } else {
                percentDecode(payload).toByteArray(Charsets.UTF_8)
            }
        } catch (e: Throwable) {
            return null
        }
        if (bytes.isEmpty() || bytes.size > MAX_BYTES) return null
        return Decoded(mime = mime, fileName = "download" + extensionFor(mime), bytes = bytes)
    }

    private fun percentDecode(text: String): String = try {
        URLDecoder.decode(text, "UTF-8")
    } catch (e: Throwable) {
        text
    }

    fun extensionFor(mime: String): String = when (mime.substringBefore(';').trim().lowercase()) {
        "text/plain" -> ".txt"
        "text/csv" -> ".csv"
        "text/html" -> ".html"
        "application/json" -> ".json"
        "application/pdf" -> ".pdf"
        "application/zip" -> ".zip"
        "image/png" -> ".png"
        "image/jpeg" -> ".jpg"
        "image/gif" -> ".gif"
        "image/webp" -> ".webp"
        "image/svg+xml" -> ".svg"
        "audio/mpeg" -> ".mp3"
        "video/mp4" -> ".mp4"
        else -> ".bin"
    }
}

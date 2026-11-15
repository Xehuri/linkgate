package com.xehuri.linkgate.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * 站点图标缓存：
 * - 网页加载时 WebView 上报的图标（[save]）会压缩成 PNG 存到应用私有目录；
 * - 从未访问过的站点，用 [fetchAndSave] 主动抓 `/favicon.ico` 或首页里的 `<link rel=icon>`；
 * - 全都失败时 UI 用首字母色块兜底。
 *
 * 只对用户自己添加的站点发请求，且每个站点一次失败后本次运行不再重试。
 */
class IconStore(context: Context) {

    private val appContext = context.applicationContext
    private val dir: File = File(appContext.filesDir, "site_icons").apply { mkdirs() }

    fun iconFile(siteId: String): File = File(dir, safeName(siteId) + ".png")

    fun has(siteId: String): Boolean {
        val file = iconFile(siteId)
        return file.isFile && file.length() > 0L
    }

    /** 删除已缓存的图标（重新抓取前调用）。 */
    fun delete(siteId: String): Boolean {
        val file = iconFile(siteId)
        return try {
            !file.exists() || file.delete()
        } catch (e: Throwable) {
            Log.w(TAG, "删除图标失败: $siteId", e)
            false
        }
    }

    /** 读取并解码图标（限制最大边 [maxPx]），失败返回 null。 */
    fun load(siteId: String, maxPx: Int = 128): Bitmap? {
        val file = iconFile(siteId)
        if (!file.isFile || file.length() == 0L) return null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            var largest = maxOf(bounds.outWidth, bounds.outHeight)
            while (largest / 2 >= maxPx) {
                largest /= 2
                sample *= 2
            }
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeFile(file.absolutePath, options)
        } catch (e: Throwable) {
            Log.w(TAG, "读取图标失败: $siteId", e)
            null
        }
    }

    /** 保存 WebView 上报的图标（压缩到最大 128px 的 PNG，原子写入）。 */
    fun save(siteId: String, bitmap: Bitmap): Boolean {
        return try {
            val scaled = scaleDown(bitmap, MAX_STORED_PX)
            val target = iconFile(siteId)
            val tmp = File(target.parentFile, target.name + ".tmp")
            FileOutputStream(tmp).use { output ->
                scaled.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
            if (scaled !== bitmap) scaled.recycle()
            if (target.exists()) target.delete()
            val ok = tmp.renameTo(target)
            if (!ok) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
            true
        } catch (e: Throwable) {
            Log.w(TAG, "保存图标失败: $siteId", e)
            false
        }
    }

    /**
     * 主动抓取图标。阻塞调用，请在后台线程执行。
     * 顺序：/favicon.ico → 首页 HTML 中声明的 icon。
     */
    fun fetchAndSave(siteId: String, siteUrl: String): Boolean {
        if (has(siteId)) return true

        for (url in FaviconLocator.candidateUrls(siteUrl)) {
            decodeIcon(download(url, maxBytes = ICON_MAX_BYTES))?.let { bitmap ->
                if (save(siteId, bitmap)) return true
            }
        }

        val html = downloadText(siteUrl, maxBytes = HTML_MAX_BYTES) ?: return false
        for (url in FaviconLocator.extractIconHrefs(html, siteUrl)) {
            decodeIcon(download(url, maxBytes = ICON_MAX_BYTES))?.let { bitmap ->
                if (save(siteId, bitmap)) return true
            }
        }
        return false
    }

    private fun decodeIcon(bytes: ByteArray?): Bitmap? {
        if (bytes == null || bytes.isEmpty()) return null
        val bitmap = try {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Throwable) {
            null
        } ?: return null
        if (bitmap.width < 8 || bitmap.height < 8) {
            bitmap.recycle()
            return null
        }
        return bitmap
    }

    private fun download(url: String, maxBytes: Int): ByteArray? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                instanceFollowRedirects = true
                requestMethod = "GET"
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "image/*,*/*;q=0.8")
            }
            if (connection.responseCode !in 200..299) return null
            val contentType = connection.contentType.orEmpty()
            if (contentType.isNotEmpty() && !contentType.startsWith("image/") && !url.endsWith(".ico", true)) {
                return null
            }
            connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8 * 1024)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > maxBytes) return null
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            }
        } catch (e: Throwable) {
            null
        } finally {
            try {
                connection?.disconnect()
            } catch (ignored: Throwable) {
            }
        }
    }

    private fun downloadText(url: String, maxBytes: Int): String? {
        val bytes = download(url, maxBytes) ?: return null
        return try {
            String(bytes, Charsets.UTF_8)
        } catch (e: Throwable) {
            null
        }
    }

    private fun scaleDown(bitmap: Bitmap, maxPx: Int): Bitmap {
        val largest = maxOf(bitmap.width, bitmap.height)
        if (largest <= maxPx) return bitmap
        val ratio = maxPx.toFloat() / largest
        return Bitmap.createScaledBitmap(
            bitmap,
            maxOf(1, (bitmap.width * ratio).toInt()),
            maxOf(1, (bitmap.height * ratio).toInt()),
            true,
        )
    }

    private fun safeName(siteId: String): String =
        siteId.replace(Regex("[^A-Za-z0-9_-]"), "_").take(64).ifBlank { "site" }

    companion object {
        private const val TAG = "LinkGateIcon"
        private const val MAX_STORED_PX = 128
        private const val ICON_MAX_BYTES = 512 * 1024
        private const val HTML_MAX_BYTES = 256 * 1024
        private const val TIMEOUT_MS = 6_000
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) LinkGateFavicon/1.0"
    }
}

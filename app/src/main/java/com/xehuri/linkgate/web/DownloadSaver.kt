package com.xehuri.linkgate.web

import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.io.File
import java.io.FileOutputStream

/**
 * 把「页面里的 blob:/data: 下载」落盘到系统「下载」目录。
 *
 * 为什么需要单独一条通路：`WebView` 的 `DownloadListener` 只对 http(s) 响应生效，
 * 而现代网页大量用 `URL.createObjectURL(blob)` + `<a download>` 造下载链接 ——
 * 这类 URL 在 Chromium 内部才有数据，原生拿不到，必须在 JS 侧读取字节再交给原生写盘。
 *
 * 保存位置与可见性：
 * - Android 10+：走 MediaStore.Downloads（自动出现在「下载」应用与文件管理器里，无需存储权限）；
 * - Android 8/9：写公共 Downloads 目录（需要 WRITE_EXTERNAL_STORAGE，被拒则退回应用私有目录）；
 * - 写完发一条带「打开下载目录」的完成通知（无通知权限时静默，仅 toast）。
 */
object DownloadSaver {

    private const val TAG = "LinkGateDownload"
    private const val CHANNEL_ID = "linkgate_downloads"
    private const val CHANNEL_NAME = "下载完成"

    data class Saved(
        val displayName: String,
        val location: String,
    )

    /** 阻塞调用，请在后台线程执行。 */
    fun save(context: Context, rawName: String, mime: String, bytes: ByteArray): Saved? {
        val name = sanitizeName(rawName)
        val type = mime.ifBlank { "application/octet-stream" }
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveViaMediaStore(context, name, type, bytes)
            } else {
                saveLegacy(context, name, bytes) ?: saveViaMediaStore(context, name, type, bytes)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "保存下载失败: $name", e)
            null
        }
    }

    private fun saveViaMediaStore(context: Context, name: String, mime: String, bytes: ByteArray): Saved? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
        }
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Downloads.EXTERNAL_CONTENT_URI
        }
        val uri: Uri = resolver.insert(collection, values) ?: return null
        resolver.openOutputStream(uri)?.use { it.write(bytes) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        return Saved(displayName = name, location = "下载目录/$name")
    }

    private fun saveLegacy(context: Context, name: String, bytes: ByteArray): Saved? {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val target = File(dir, uniqueName(dir, name))
        return try {
            dir.mkdirs()
            FileOutputStream(target).use { it.write(bytes) }
            Saved(displayName = target.name, location = target.absolutePath)
        } catch (e: Throwable) {
            Log.w(TAG, "写公共下载目录失败，改用应用私有目录", e)
            val fallbackDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "")
            runCatching { fallbackDir.mkdirs() }
            val fallback = File(fallbackDir, uniqueName(fallbackDir, name))
            runCatching {
                FileOutputStream(fallback).use { it.write(bytes) }
                Saved(displayName = fallback.name, location = fallback.absolutePath)
            }.getOrNull()
        }
    }

    private fun uniqueName(dir: File, name: String): String {
        if (!File(dir, name).exists()) return name
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        for (index in 1..999) {
            val candidate = "$base ($index)$ext"
            if (!File(dir, candidate).exists()) return candidate
        }
        return "$base-${System.currentTimeMillis()}$ext"
    }

    private fun sanitizeName(raw: String): String {
        val cleaned = raw.trim()
            .replace('/', '_')
            .replace('\\', '_')
            .replace("\u0000", "")
            .take(120)
        return cleaned.ifBlank { "download-${System.currentTimeMillis()}" }
    }

    /** 下载完成通知：点击打开系统「下载」界面（避免 file:// 的 FileUriExposedException）。 */
    fun notifySaved(context: Context, saved: Saved) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        runCatching {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
        val openDownloads = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pending = PendingIntent.getActivity(
            context,
            saved.displayName.hashCode(),
            openDownloads,
            flags,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("下载完成")
            .setContentText(saved.displayName)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(saved.displayName.hashCode(), notification)
        }.onFailure { Log.w(TAG, "发送下载通知失败（可能没有通知权限）", it) }
    }

    /** 打开系统「下载」界面。 */
    fun openDownloadsUi(context: Context): Boolean = try {
        context.startActivity(
            Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    } catch (e: Throwable) {
        Log.w(TAG, "打开下载目录失败", e)
        false
    }
}

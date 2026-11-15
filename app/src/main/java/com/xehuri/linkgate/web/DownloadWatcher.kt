package com.xehuri.linkgate.web

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.core.content.ContextCompat
import com.xehuri.linkgate.data.DownloadRecord

/**
 * 系统 DownloadManager 的完成监听：下载完成后补一条历史并通知前台弹「是否打开」。
 *
 * 为什么需要它：http(s) 下载交给系统后，完成时我们的进程可能在后台甚至已被回收。
 * 前台时用这个动态广播收结果；后台/重启后由 [DownloadCenter.syncFromDownloadManager] 补齐。
 */
class DownloadWatcher(private val context: Context) {

    private var receiver: BroadcastReceiver? = null

    fun start(onFinished: (DownloadRecord) -> Unit) {
        if (receiver != null) return
        val target = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (id <= 0L) return
                runCatching { handle(id, onFinished) }
                    .onFailure { Log.w(TAG, "处理下载完成失败: $id", it) }
            }
        }
        receiver = target
        runCatching {
            ContextCompat.registerReceiver(
                context,
                target,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                ContextCompat.RECEIVER_EXPORTED,
            )
        }.onFailure { Log.w(TAG, "注册下载完成广播失败", it) }
    }

    fun stop() {
        receiver?.let { runCatching { context.unregisterReceiver(it) } }
        receiver = null
    }

    private fun handle(downloadId: Long, onFinished: (DownloadRecord) -> Unit) {
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager ?: return
        val cursor = manager.query(DownloadManager.Query().setFilterById(downloadId)) ?: return
        cursor.use { c ->
            if (!c.moveToFirst()) return
            val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val name = c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE))
                ?: "download"
            val mime = c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_MEDIA_TYPE)).orEmpty()
            val size = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            val (pageUrl, siteTitle) = DownloadCenter.takeDmContext(downloadId)
            val ok = status == DownloadManager.STATUS_SUCCESSFUL
            // 不能读 COLUMN_LOCAL_URI / COLUMN_LOCAL_FILENAME（Android 10+ 抛 SecurityException），
            // 统一按文件名去 MediaStore 反查 content uri
            val uri = if (ok) DownloadCenter.resolveContentUri(context, name) else ""
            val record = DownloadRecord(
                id = "dm:$downloadId",
                name = name,
                mime = mime,
                size = size.coerceAtLeast(0L),
                uri = uri,
                pageUrl = pageUrl,
                siteTitle = siteTitle,
                createdAt = System.currentTimeMillis(),
                ok = ok,
            )
            DownloadCenter.publish(context, record, notifyUi = ok)
            if (ok) DownloadSaver.notifySaved(context, record)
        }
    }

    private companion object {
        const val TAG = "LinkGateDownload"
    }
}

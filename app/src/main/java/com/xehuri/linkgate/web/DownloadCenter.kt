package com.xehuri.linkgate.web

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.xehuri.linkgate.data.DownloadRecord
import com.xehuri.linkgate.data.DownloadStore
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 下载记录中心：统一负责「记一条历史」「通知前台弹是否打开」「打开文件」。
 *
 * 三条下载通路（DownloadManager / blob 消息桥 / data: 解码）都从这里落记录，
 * UI 侧只需要订阅 [addListener] 就能在下载完成时弹窗。
 */
object DownloadCenter {

    private const val TAG = "LinkGateDownload"

    private val listeners = CopyOnWriteArrayList<(DownloadRecord) -> Unit>()

    /** 系统 DownloadManager 任务的上下文（页面地址 / 站点名），完成回调时补进记录 */
    private val dmContext = HashMap<Long, Pair<String, String>>()

    fun addListener(listener: (DownloadRecord) -> Unit) {
        listeners.addIfAbsent(listener)
    }

    fun removeListener(listener: (DownloadRecord) -> Unit) {
        listeners.remove(listener)
    }

    fun rememberDmContext(downloadId: Long, pageUrl: String, siteTitle: String) {
        synchronized(dmContext) { dmContext[downloadId] = pageUrl to siteTitle }
    }

    fun takeDmContext(downloadId: Long): Pair<String, String> = synchronized(dmContext) {
        dmContext.remove(downloadId) ?: ("" to "")
    }

    /**
     * 保存记录并通知前台。
     * @param notifyUi true 时通知已注册的监听者（前台据此弹「是否打开」）
     */
    fun publish(context: Context, record: DownloadRecord, notifyUi: Boolean = true) {
        val stored = runCatching { DownloadStore(context).add(record) }.getOrNull() ?: return
        Log.d(TAG, "下载记录: ${record.name} (${record.size} B) ok=${record.ok} 共 ${stored.size} 条")
        if (!notifyUi) return
        listeners.forEach { listener ->
            runCatching { listener(record) }.onFailure { Log.w(TAG, "下载回调失败", it) }
        }
    }

    /** 打开下载好的文件；uri 失效或没有可处理的应用时退回系统「下载」界面。 */
    fun open(context: Context, record: DownloadRecord): Boolean {
        val intent = buildOpenIntent(context, record)
        if (intent != null) {
            val ok = runCatching { context.startActivity(intent) }.isSuccess
            if (ok) return true
        }
        return DownloadSaver.openDownloadsUi(context)
    }

    fun buildOpenIntent(context: Context, record: DownloadRecord): Intent? {
        val uri = runCatching { Uri.parse(record.uri) }.getOrNull() ?: return null
        if (uri.scheme.isNullOrBlank()) return null
        val mime = record.mime.ifBlank { "*/*" }
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * 按文件名在 MediaStore 的「下载」集合里反查可打开的 `content://` 地址。
     * DownloadManager 的 cursor 上不能直接读本地路径（Q+ 抛 SecurityException），这是唯一稳妥的桥。
     */
    fun resolveContentUri(context: Context, displayName: String): String {
        if (displayName.isBlank()) return ""
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Downloads.EXTERNAL_CONTENT_URI
        }
        return runCatching {
            context.contentResolver.query(
                collection,
                arrayOf(MediaStore.Downloads._ID),
                "${MediaStore.Downloads.DISPLAY_NAME} = ?",
                arrayOf(displayName),
                "${MediaStore.Downloads.DATE_ADDED} DESC",
            )?.use { c ->
                if (c.moveToFirst()) {
                    return@use Uri.withAppendedPath(collection, c.getLong(0).toString()).toString()
                }
                ""
            }.orEmpty()
        }.getOrDefault("")
    }

    /** 删除记录（可选同时删文件）。 */
    fun remove(context: Context, record: DownloadRecord, deleteFile: Boolean): List<DownloadRecord> {
        if (deleteFile) {
            runCatching {
                context.contentResolver.delete(Uri.parse(record.uri), null, null)
            }.onFailure { Log.w(TAG, "删除文件失败: ${record.name}", it) }
        }
        return runCatching {
            val store = DownloadStore(context)
            if (record.id.startsWith("dm:")) store.hideDmIds(listOf(record.id))
            store.remove(record.id)
        }.getOrDefault(emptyList())
    }

    /**
     * 把系统 DownloadManager 里「本应用发起」的已完成任务补进历史。
     * 用于「App 被系统杀掉后，下载在后台完成」的场景，保证下载管理器里能看到完整记录。
     */
    fun syncFromDownloadManager(context: Context) {
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager ?: return
        val store = runCatching { DownloadStore(context) }.getOrNull() ?: return
        val known = runCatching { store.all().map { it.id }.toSet() }.getOrDefault(emptySet())
        val hidden = runCatching { store.hiddenDmIds() }.getOrDefault(emptySet())
        val cursor = runCatching {
            manager.query(DownloadManager.Query().setFilterByStatus(DownloadManager.STATUS_SUCCESSFUL))
        }.getOrNull() ?: return
        cursor.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_ID))
                val recordId = "dm:$id"
                if (recordId in known || recordId in hidden) continue
                val name = runCatching {
                    c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE))
                }.getOrNull()?.takeIf { it.isNotBlank() }
                    ?: runCatching {
                        c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_DESCRIPTION))
                    }.getOrNull().orEmpty()
                val safeName = name.ifBlank { "download" }
                val mime = runCatching {
                    c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_MEDIA_TYPE))
                }.getOrNull().orEmpty()
                val size = runCatching {
                    c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                }.getOrDefault(0L)
                // 注意：COLUMN_LOCAL_URI / COLUMN_LOCAL_FILENAME 在 Android 10+ 读取会抛
                // SecurityException（框架已弃用），只能靠文件名去 MediaStore 反查可打开的 uri。
                val uri = resolveContentUri(context, safeName)
                publish(
                    context,
                    DownloadRecord(
                        id = recordId,
                        name = safeName,
                        mime = mime,
                        size = size.coerceAtLeast(0),
                        uri = uri,
                        pageUrl = "",
                        siteTitle = "",
                        createdAt = System.currentTimeMillis(),
                        ok = true,
                    ),
                    notifyUi = false,
                )
            }
        }
    }
}

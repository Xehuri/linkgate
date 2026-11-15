package com.xehuri.linkgate.data

/**
 * 一条下载历史。
 *
 * @param id 记录 id：系统 DownloadManager 的任务用 `dm:<downloadId>`，其余用时间戳随机串
 * @param name 文件显示名（含扩展名）
 * @param mime 保存时使用的 MIME
 * @param size 字节数
 * @param uri 打开用的地址：`content://`（MediaStore）或 `file://`（旧系统兜底）
 * @param pageUrl 触发下载的页面地址
 * @param siteTitle 触发下载的站点名（用于列表里区分来源）
 * @param createdAt 完成时间戳
 * @param ok 是否成功（失败的也留一条，便于排查）
 */
data class DownloadRecord(
    val id: String,
    val name: String,
    val mime: String,
    val size: Long,
    val uri: String,
    val pageUrl: String = "",
    val siteTitle: String = "",
    val createdAt: Long = 0L,
    val ok: Boolean = true,
) {
    /** 列表里显示的副标题：站点名 + 大小 */
    fun summary(): String = buildString {
        if (siteTitle.isNotBlank()) {
            append(siteTitle)
            append(" · ")
        }
        append(formatSize(size))
        if (!ok) append(" · 未完成")
    }

    /** 文件类型分类，用于列表图标 */
    fun kind(): Kind = when {
        mime.startsWith("image/") -> Kind.IMAGE
        mime.startsWith("video/") -> Kind.VIDEO
        mime.startsWith("audio/") -> Kind.AUDIO
        mime == "application/pdf" || name.endsWith(".pdf", true) -> Kind.PDF
        mime.contains("zip") || mime.contains("compressed") ||
            name.endsWith(".apk", true) || name.endsWith(".7z", true) || name.endsWith(".rar", true) -> Kind.ARCHIVE
        else -> Kind.FILE
    }

    enum class Kind { IMAGE, VIDEO, AUDIO, PDF, ARCHIVE, FILE }

    companion object {
        fun formatSize(bytes: Long): String {
            if (bytes <= 0) return "0 B"
            if (bytes < 1024) return "$bytes B"
            val kb = bytes / 1024.0
            if (kb < 1024) return String.format(java.util.Locale.ROOT, "%.1f KB", kb)
            val mb = kb / 1024.0
            if (mb < 1024) return String.format(java.util.Locale.ROOT, "%.1f MB", mb)
            return String.format(java.util.Locale.ROOT, "%.2f GB", mb / 1024.0)
        }
    }
}

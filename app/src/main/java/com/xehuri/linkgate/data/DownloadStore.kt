package com.xehuri.linkgate.data

import android.content.Context

/**
 * 下载历史的本地持久化（与站点/设置分开的 prefs 文件）。
 *
 * 只保留最近 [MAX_RECORDS] 条：下载历史是「方便找回」，不是归档系统，
 * 无限增长只会让 SharedPreferences 越来越大。
 */
class DownloadStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun all(): List<DownloadRecord> = DownloadCodec.decode(prefs.getString(KEY_RECORDS, null))

    /** 新记录放最前面；同 id 覆盖（重复回调不会产生两条）。 */
    fun add(record: DownloadRecord): List<DownloadRecord> {
        val current = all().filterNot { it.id == record.id }
        val next = (listOf(record) + current).take(MAX_RECORDS)
        save(next)
        return next
    }

    fun remove(id: String): List<DownloadRecord> {
        val next = all().filterNot { it.id == id }
        save(next)
        return next
    }

    fun clear(): List<DownloadRecord> {
        save(emptyList())
        return emptyList()
    }

    /**
     * 用户主动删掉的「系统下载任务」记录 id 集合。
     * 有了它，[com.xehuri.linkgate.web.DownloadCenter.syncFromDownloadManager] 才不会把
     * 刚被用户清掉的记录又补回来。
     */
    fun hiddenDmIds(): Set<String> = prefs.getStringSet(KEY_HIDDEN, emptySet()).orEmpty()

    fun hideDmIds(ids: Collection<String>) {
        if (ids.isEmpty()) return
        val next = (hiddenDmIds().toList() + ids).takeLast(MAX_HIDDEN).toSet()
        prefs.edit().putStringSet(KEY_HIDDEN, next).apply()
    }

    fun clearHidden() {
        prefs.edit().remove(KEY_HIDDEN).apply()
    }

    private fun save(records: List<DownloadRecord>) {
        prefs.edit().putString(KEY_RECORDS, DownloadCodec.encode(records)).apply()
    }

    companion object {
        const val MAX_RECORDS = 200
        private const val PREFS_NAME = "linkgate_downloads"
        private const val KEY_RECORDS = "records_v1"
        private const val KEY_HIDDEN = "hidden_dm_v1"
        private const val MAX_HIDDEN = 300
    }
}

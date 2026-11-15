package com.xehuri.linkgate.data

import java.util.UUID

/**
 * 站点列表的导入合并（纯函数）。
 *
 * 规则：
 * - 已存在相同地址的站点不重复导入（地址比较忽略大小写与首尾空白）；
 * - 导入的 id 若与现有 id 冲突（或为空），重新生成，避免覆盖用户已有站点；
 * - 导入项追加在现有列表后面，保持各自原有顺序。
 */
object SiteImporter {

    fun merge(existing: List<Site>, imported: List<Site>): List<Site> {
        if (imported.isEmpty()) return existing
        val urls = existing.mapTo(HashSet()) { normalizeKey(it.url) }
        val ids = existing.mapTo(HashSet()) { it.id }
        val out = ArrayList<Site>(existing.size + imported.size)
        out.addAll(existing)
        for (candidate in imported) {
            val key = normalizeKey(candidate.url)
            if (key.isEmpty() || key in urls) continue
            urls.add(key)
            val id = candidate.id.trim().ifBlank { UUID.randomUUID().toString() }
                .let { if (it in ids) UUID.randomUUID().toString() else it }
            ids.add(id)
            out.add(candidate.copy(id = id))
        }
        return out
    }

    /** 统计一次导入会新增多少条（给 UI 提示用） */
    fun countNew(existing: List<Site>, imported: List<Site>): Int = merge(existing, imported).size - existing.size

    private fun normalizeKey(url: String): String = url.trim().trimEnd('/').lowercase()
}

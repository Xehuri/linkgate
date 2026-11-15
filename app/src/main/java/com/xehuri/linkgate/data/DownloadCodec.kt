package com.xehuri.linkgate.data

import java.util.Base64

/**
 * 下载历史的持久化编码。与站点/设置一样的「纯文本 + Base64 字段」，方便排障，也能在 JVM 单测里跑。
 *
 * ```
 * LINKGATE-DOWNLOADS/1
 * <base64(id)>\t<base64(name)>\t<base64(mime)>\t<size>\t<base64(uri)>\t<base64(pageUrl)>\t<base64(siteTitle)>\t<createdAt>\t<0|1>
 * ```
 *
 * 解码容忍脏数据：字段数不对、数字非法、id 为空的行直接跳过，不会让整份历史失效。
 */
object DownloadCodec {

    private const val HEADER = "LINKGATE-DOWNLOADS/1"
    private const val FIELDS = 9

    fun encode(records: List<DownloadRecord>): String {
        if (records.isEmpty()) return HEADER
        val sb = StringBuilder(HEADER)
        for (record in records) {
            sb.append('\n')
                .append(enc(record.id)).append('\t')
                .append(enc(record.name)).append('\t')
                .append(enc(record.mime)).append('\t')
                .append(record.size).append('\t')
                .append(enc(record.uri)).append('\t')
                .append(enc(record.pageUrl)).append('\t')
                .append(enc(record.siteTitle)).append('\t')
                .append(record.createdAt).append('\t')
                .append(if (record.ok) 1 else 0)
        }
        return sb.toString()
    }

    fun decode(raw: String?): List<DownloadRecord> {
        if (raw.isNullOrBlank()) return emptyList()
        val lines = raw.split('\n')
        if (lines.firstOrNull()?.trim() != HEADER) return emptyList()
        val out = ArrayList<DownloadRecord>(lines.size)
        for (line in lines.drop(1)) {
            if (line.isBlank()) continue
            val parts = line.split('\t')
            if (parts.size != FIELDS) continue
            val id = dec(parts[0])?.trim().orEmpty()
            if (id.isEmpty()) continue
            val size = parts[3].toLongOrNull() ?: continue
            val createdAt = parts[7].toLongOrNull() ?: 0L
            out.add(
                DownloadRecord(
                    id = id,
                    name = dec(parts[1]).orEmpty().ifBlank { "download" },
                    mime = dec(parts[2]).orEmpty(),
                    size = size,
                    uri = dec(parts[4]).orEmpty(),
                    pageUrl = dec(parts[5]).orEmpty(),
                    siteTitle = dec(parts[6]).orEmpty(),
                    createdAt = createdAt,
                    ok = parts[8].trim() != "0",
                ),
            )
        }
        return out
    }

    private fun enc(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))

    private fun dec(value: String): String? = try {
        String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)
    } catch (e: IllegalArgumentException) {
        null
    }
}

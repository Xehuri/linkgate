package com.xehuri.linkgate.data

import java.util.Base64

/**
 * 站点列表的持久化编码。用纯文本 + Base64 字段，方便排障，也不依赖 org.json
 * （unit test 里没有 Android 的 org.json 实现）。
 *
 * 格式：
 * ```
 * LINKGATE-SITES/2
 * <base64(id)>\t<base64(title)>\t<base64(url)>\t<base64(description)>\t<base64(ipv6Url)>
 * ...
 * ```
 *
 * 兼容：仍然能读 1.0.x 写下的 `LINKGATE-SITES/1`（每行 3 个字段），读出来 description/ipv6Url 为空；
 * 写入一律用 /2。这样老用户升级不会丢站点，也不需要单独的迁移步骤。
 */
object SiteCodec {

    private const val HEADER_V2 = "LINKGATE-SITES/2"
    private const val HEADER_V1 = "LINKGATE-SITES/1"

    private const val FIELDS_V1 = 3

    fun encode(sites: List<Site>): String {
        if (sites.isEmpty()) return HEADER_V2
        val sb = StringBuilder(HEADER_V2)
        for (site in sites) {
            sb.append('\n')
                .append(enc(site.id)).append('\t')
                .append(enc(site.title)).append('\t')
                .append(enc(site.url)).append('\t')
                .append(enc(site.description)).append('\t')
                .append(enc(site.ipv6Url))
        }
        return sb.toString()
    }

    fun decode(raw: String?): List<Site> {
        if (raw.isNullOrBlank()) return emptyList()
        val lines = raw.split('\n')
        val header = lines.firstOrNull()?.trim()
        if (header != HEADER_V2 && header != HEADER_V1) return emptyList()
        val out = ArrayList<Site>(lines.size)
        for (line in lines.drop(1)) {
            if (line.isBlank()) continue
            val parts = line.split('\t')
            // 容忍字段数不一致的行：按能读到的部分解析，多余的字段忽略
            if (parts.size < FIELDS_V1) continue
            val id = dec(parts[0])?.trim().orEmpty()
            val title = dec(parts.getOrNull(1)).orEmpty()
            val storedUrl = dec(parts.getOrNull(2))?.trim().orEmpty()
            if (id.isEmpty() || storedUrl.isEmpty()) continue
            // 损坏的行（Base64 乱码等）会解码出垃圾字符串：只有能重新规范成 http(s) 的才收下，
            // 避免坏数据变成打不开的站点混进列表。
            val url = UrlNormalizer.normalize(storedUrl) ?: continue
            // 老格式（每行 3 字段）没有这两个字段，按空处理
            val description = if (parts.size > 3) dec(parts[3]).orEmpty().trim() else ""
            val ipv6Raw = if (parts.size > 4) dec(parts[4]).orEmpty().trim() else ""
            // IPv6 备用地址坏掉不应该连累整个站点：规范化失败就当没配
            val ipv6Url = if (ipv6Raw.isEmpty()) "" else (UrlNormalizer.normalize(ipv6Raw) ?: "")
            out.add(
                Site(
                    id = id,
                    title = title.ifBlank { UrlNormalizer.titleFromUrl(url) },
                    url = url,
                    description = description,
                    ipv6Url = ipv6Url,
                ),
            )
        }
        return out
    }

    private fun enc(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))

    private fun dec(value: String?): String? {
        if (value.isNullOrEmpty()) return ""
        return try {
            String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}

package com.xehuri.linkgate.data

import java.net.URI
import java.util.Base64

/**
 * 一条已保存的登录凭据。origin 形如 `https://dsh.xehuri.top`（含端口），
 * 只在该 origin 下才会被回填，避免跨站泄露。
 */
data class PasswordEntry(
    val origin: String,
    val username: String,
    val password: String,
    val updatedAt: Long,
)

/** origin 归一化：scheme://host[:port]，小写，去掉路径/查询/锚点。 */
object PasswordOrigin {

    fun fromUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return try {
            fromUri(URI(url))
        } catch (e: Exception) {
            null
        }
    }

    fun fromUri(uri: URI?): String? {
        if (uri == null) return null
        val scheme = uri.scheme?.lowercase() ?: return null
        val host = uri.host?.lowercase() ?: return null
        if (host.isBlank()) return null
        if (scheme != "http" && scheme != "https") return null
        val port = if (uri.port > 0) ":${uri.port}" else ""
        return "$scheme://$host$port"
    }
}

/**
 * 凭据列表的本地编码格式（UTF-8 + Base64 字段），与 [SiteCodec] 同样的思路：
 * 纯文本可排障，且不依赖 org.json，便于单元测试。
 *
 * ```
 * LINKGATE-PASSWORDS/1
 * <base64(origin)>\t<base64(username)>\t<base64(password)>\t<updatedAt>
 * ```
 */
object PasswordCodec {

    private const val HEADER = "LINKGATE-PASSWORDS/1"

    fun encode(entries: List<PasswordEntry>): String {
        val sb = StringBuilder(HEADER)
        for (entry in entries) {
            sb.append('\n')
                .append(enc(entry.origin)).append('\t')
                .append(enc(entry.username)).append('\t')
                .append(enc(entry.password)).append('\t')
                .append(entry.updatedAt)
        }
        return sb.toString()
    }

    fun decode(raw: String?): List<PasswordEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        val lines = raw.split('\n')
        if (lines.firstOrNull()?.trim() != HEADER) return emptyList()
        val result = ArrayList<PasswordEntry>()
        for (line in lines.drop(1)) {
            if (line.isBlank()) continue
            val parts = line.split('\t')
            if (parts.size != 4) continue
            val origin = dec(parts[0])?.trim().orEmpty()
            val username = dec(parts[1]).orEmpty()
            val password = dec(parts[2]).orEmpty()
            val updatedAt = parts[3].trim().toLongOrNull() ?: 0L
            if (origin.isEmpty() || password.isEmpty()) continue
            // origin 必须能归一化回自身（防止 Base64 乱码被当成合法站点）
            val normalized = PasswordOrigin.fromUrl(origin) ?: continue
            if (!normalized.equals(origin, ignoreCase = true)) continue
            result.add(PasswordEntry(normalized, username, password, updatedAt))
        }
        return result
    }

    private fun enc(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))

    private fun dec(value: String): String? = try {
        String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)
    } catch (e: IllegalArgumentException) {
        null
    }
}

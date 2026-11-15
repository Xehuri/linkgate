package com.xehuri.linkgate.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteCodecTest {

    private val samples = listOf(
        Site(id = "id-1", title = "哔哩哔哩", url = "https://www.bilibili.com"),
        Site(id = "id-2", title = "带\t制表符和\n换行的标题", url = "https://example.com/a?b=c&d=e"),
        Site(id = "id-3", title = "GitHub", url = "https://github.com/xehuri"),
        Site(
            id = "id-4",
            title = "家里的 NAS",
            url = "https://nas.example.com:8443",
            description = "内网 8443 端口",
            ipv6Url = "https://[2408:8207:1234::1]:8443",
        ),
    )

    @Test
    fun `编码后可完整解码回来`() {
        val encoded = SiteCodec.encode(samples)
        assertEquals(samples, SiteCodec.decode(encoded))
    }

    @Test
    fun `顺序在编解码后保持不变`() {
        val decoded = SiteCodec.decode(SiteCodec.encode(samples))
        assertEquals(listOf("id-1", "id-2", "id-3", "id-4"), decoded.map { it.id })
    }

    @Test
    fun `描述与 IPv6 地址能持久化`() {
        val decoded = SiteCodec.decode(SiteCodec.encode(samples)).single { it.id == "id-4" }
        assertEquals("内网 8443 端口", decoded.description)
        assertEquals("https://[2408:8207:1234::1]:8443", decoded.ipv6Url)
        assertTrue(decoded.hasIpv6)
    }

    @Test
    fun `空列表编码解码都安全`() {
        val encoded = SiteCodec.encode(emptyList())
        assertTrue(encoded.startsWith("LINKGATE-SITES/2"))
        assertTrue(SiteCodec.decode(encoded).isEmpty())
    }

    @Test
    fun `旧版三字段格式仍然能读`() {
        val legacy = buildString {
            append("LINKGATE-SITES/1\n")
            append(enc("old-1")).append('\t')
            append(enc("老站点")).append('\t')
            append(enc("https://old.example.com"))
        }
        val decoded = SiteCodec.decode(legacy)
        assertEquals(1, decoded.size)
        assertEquals("old-1", decoded.single().id)
        assertEquals("老站点", decoded.single().title)
        assertEquals("https://old.example.com", decoded.single().url)
        assertEquals("", decoded.single().description)
        assertEquals("", decoded.single().ipv6Url)
    }

    @Test
    fun `未知头部返回空列表`() {
        assertTrue(SiteCodec.decode(null).isEmpty())
        assertTrue(SiteCodec.decode("").isEmpty())
        assertTrue(SiteCodec.decode("不是我们的格式").isEmpty())
        assertTrue(SiteCodec.decode("LINKGATE-SITES/9\nabc\tdef\tghi").isEmpty())
    }

    @Test
    fun `损坏的行被跳过而不是整个列表失败`() {
        val good = SiteCodec.encode(samples.take(1))
        val broken = "$good\n坏行没有制表符\nbad\tbase64\tbase64"
        val decoded = SiteCodec.decode(broken)
        assertEquals(1, decoded.size)
        assertEquals("id-1", decoded.first().id)
    }

    @Test
    fun `IPv6 字段坏掉不影响站点本身`() {
        val raw = buildString {
            append("LINKGATE-SITES/2\n")
            append(enc("x")).append('\t')
            append(enc("坏 IPv6")).append('\t')
            append(enc("https://example.com")).append('\t')
            append(enc("描述")).append('\t')
            append(enc("不是链接"))
        }
        val decoded = SiteCodec.decode(raw).single()
        assertEquals("https://example.com", decoded.url)
        assertEquals("描述", decoded.description)
        assertEquals("", decoded.ipv6Url)
    }

    @Test
    fun `空标题回退为域名`() {
        val encrypted = SiteCodec.encode(listOf(Site(id = "x", title = "", url = "https://www.example.com/page")))
        val decoded = SiteCodec.decode(encrypted)
        assertEquals("example.com", decoded.single().title)
    }

    @Test
    fun `副标题优先用描述`() {
        assertEquals("描述文字", Site(id = "a", title = "t", url = "https://x.example.com", description = "描述文字").subtitleText())
        assertEquals("x.example.com", Site(id = "a", title = "t", url = "https://x.example.com").subtitleText())
    }

    private fun enc(value: String): String =
        java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))
}

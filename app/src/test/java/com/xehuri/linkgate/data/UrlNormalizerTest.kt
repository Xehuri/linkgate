package com.xehuri.linkgate.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlNormalizerTest {

    @Test
    fun `裸域名自动补 https`() {
        assertEquals("https://example.com", UrlNormalizer.normalize("example.com"))
    }

    @Test
    fun `带路径与查询参数的地址原样保留`() {
        assertEquals(
            "https://example.com/a/b?q=1",
            UrlNormalizer.normalize("https://example.com/a/b?q=1"),
        )
    }

    @Test
    fun `首尾空白和粘贴引号会被去掉`() {
        assertEquals("https://example.com", UrlNormalizer.normalize("  \"example.com\"  "))
        assertEquals("https://example.com", UrlNormalizer.normalize("《example.com》"))
    }

    @Test
    fun `协议相对地址补 https`() {
        assertEquals("https://cdn.example.com/a", UrlNormalizer.normalize("//cdn.example.com/a"))
    }

    @Test
    fun `http 协议保留`() {
        assertEquals("http://example.com", UrlNormalizer.normalize("http://example.com"))
    }

    @Test
    fun `主机加端口可识别`() {
        assertEquals("https://localhost:8080", UrlNormalizer.normalize("localhost:8080"))
        assertEquals("https://example.com:8080/path", UrlNormalizer.normalize("example.com:8080/path"))
    }

    @Test
    fun `非 http 协议一律拒绝`() {
        assertNull(UrlNormalizer.normalize("javascript:alert(1)"))
        assertNull(UrlNormalizer.normalize("file:///etc/passwd"))
        assertNull(UrlNormalizer.normalize("ftp://example.com"))
        assertNull(UrlNormalizer.normalize("intent://scan/#Intent;scheme=zxing;end"))
    }

    @Test
    fun `空输入与空白输入返回 null`() {
        assertNull(UrlNormalizer.normalize(null))
        assertNull(UrlNormalizer.normalize(""))
        assertNull(UrlNormalizer.normalize("    "))
        assertNull(UrlNormalizer.normalize("\u200B"))
    }

    @Test
    fun `没有点的主机名被拒绝`() {
        assertNull(UrlNormalizer.normalize("example"))
        assertNull(UrlNormalizer.normalize("https://"))
        assertNull(UrlNormalizer.normalize("a..b"))
    }

    @Test
    fun `中间带空格的输入被拒绝`() {
        assertNull(UrlNormalizer.normalize("example .com"))
        assertNull(UrlNormalizer.normalize("https://example.com/a b"))
    }

    @Test
    fun `非法端口被拒绝`() {
        assertNull(UrlNormalizer.normalize("example.com:abc"))
    }

    @Test
    fun `标题默认取域名并去掉 www 前缀`() {
        assertEquals("example.com", UrlNormalizer.titleFromUrl("https://www.example.com/some/path?x=1"))
        assertEquals("bilibili.com", UrlNormalizer.titleFromUrl("https://bilibili.com"))
    }
    @Test
    fun `规范化后的地址总是可解析的 https 或 http`() {
        val input = listOf("example.com", "baidu.com/s?wd=x", "HTTP://Example.com/x")
        input.forEach { raw ->
            val url = UrlNormalizer.normalize(raw)
            assertNotNull("应该能规范化: $raw", url)
            assertTrue("必须是 http(s): $url", url!!.startsWith("http"))
        }
    }

    // ---------------- IPv6 ----------------

    @Test
    fun `带方括号与端口的 IPv6 地址可识别`() {
        assertEquals(
            "https://[2408:8207:1234::1]:8080",
            UrlNormalizer.normalize("[2408:8207:1234::1]:8080"),
        )
        assertEquals(
            "http://[2408:8207:1234::1]:8080/path?q=1",
            UrlNormalizer.normalize("http://[2408:8207:1234::1]:8080/path?q=1"),
        )
    }

    @Test
    fun `裸 IPv6 地址自动补协议与方括号`() {
        assertEquals("https://[2408:8207:1234::1]", UrlNormalizer.normalize("2408:8207:1234::1"))
        assertEquals("https://[::1]", UrlNormalizer.normalize("::1"))
        assertEquals("https://[fe80::1]", UrlNormalizer.normalize("fe80::1"))
    }

    @Test
    fun `IPv6 的标题去掉方括号`() {
        assertEquals("2408:8207:1234::1", UrlNormalizer.titleFromUrl("https://[2408:8207:1234::1]:8080/x"))
    }

    @Test
    fun `IPv6 主机判定`() {
        assertTrue(UrlNormalizer.isIpv6Host("https://[2408:8207:1234::1]:8080"))
        assertTrue(!UrlNormalizer.isIpv6Host("https://example.com"))
        assertEquals("2408:8207:1234::1", UrlNormalizer.ipv6LiteralOf("https://[2408:8207:1234::1]:8080"))
        assertNull(UrlNormalizer.ipv6LiteralOf("https://example.com"))
    }

    @Test
    fun `非法的 IPv6 字面量被拒绝`() {
        assertNull(UrlNormalizer.normalize("https://[2408:8207:zzzz::1]"))
        assertNull(UrlNormalizer.normalize("[::1"))
        assertNull(UrlNormalizer.normalize("2408:8207:1234::1:8080/path"))
    }

    @Test
    fun `IPv6 与主机端口不会互相误判`() {
        assertEquals("https://example.com:8080/path", UrlNormalizer.normalize("example.com:8080/path"))
        assertEquals("https://[::1]:8080", UrlNormalizer.normalize("[::1]:8080"))
    }
}

package com.xehuri.linkgate.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FaviconLocatorTest {

    @Test
    fun `origin 去掉路径与查询串`() {
        assertEquals("https://example.com", FaviconLocator.originOf("https://example.com/a/b?x=1#y"))
    }

    @Test
    fun `origin 保留非默认端口`() {
        assertEquals("http://127.0.0.1:18081", FaviconLocator.originOf("http://127.0.0.1:18081/me"))
    }

    @Test
    fun `非法地址返回 null`() {
        assertEquals(null, FaviconLocator.originOf("not a url"))
        assertEquals(null, FaviconLocator.originOf(""))
    }

    @Test
    fun `兜底候选是 origin 下的 favicon`() {
        assertEquals(
            listOf("https://example.com/favicon.ico"),
            FaviconLocator.candidateUrls("https://example.com/plugins/client.js"),
        )
    }

    @Test
    fun `解析相对路径 icon`() {
        val html = """<html><head><link rel="icon" href="/static/fav.png"></head></html>"""
        assertEquals(
            listOf("https://example.com/static/fav.png"),
            FaviconLocator.extractIconHrefs(html, "https://example.com/page"),
        )
    }

    @Test
    fun `解析协议相对地址`() {
        val html = """<link rel="shortcut icon" href="//cdn.example.com/i.ico">"""
        assertEquals(
            listOf("https://cdn.example.com/i.ico"),
            FaviconLocator.extractIconHrefs(html, "https://example.com/"),
        )
    }

    @Test
    fun `支持 apple-touch-icon 与相对目录`() {
        val html = """<link rel="apple-touch-icon" sizes="180x180" href="touch.png">"""
        assertEquals(
            listOf("https://example.com/dir/touch.png"),
            FaviconLocator.extractIconHrefs(html, "https://example.com/dir/page"),
        )
    }

    @Test
    fun `非图标 link 与 data URL 被忽略`() {
        val html = """
            <link rel="stylesheet" href="/a.css">
            <link rel="icon" href="data:image/png;base64,AAAA">
            <link rel="icon" href="https://cdn.example.com/ok.png">
        """.trimIndent()
        assertEquals(
            listOf("https://cdn.example.com/ok.png"),
            FaviconLocator.extractIconHrefs(html, "https://example.com/"),
        )
    }

    @Test
    fun `空 HTML 返回空列表`() {
        assertTrue(FaviconLocator.extractIconHrefs("", "https://example.com").isEmpty())
        assertTrue(FaviconLocator.extractIconHrefs("<html></html>", "https://example.com").isEmpty())
    }
}

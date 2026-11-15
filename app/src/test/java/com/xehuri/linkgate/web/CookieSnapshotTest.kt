package com.xehuri.linkgate.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CookieSnapshotTest {

    @Test
    fun `解析 Cookie 头为 name=value 列表`() {
        assertEquals(
            listOf("a=1", "b=2", "c=3"),
            CookieSnapshot.pairs("a=1; b=2;c=3"),
        )
    }

    @Test
    fun `空头与只有分号时返回空列表`() {
        assertTrue(CookieSnapshot.pairs(null).isEmpty())
        assertTrue(CookieSnapshot.pairs("").isEmpty())
        assertTrue(CookieSnapshot.pairs("   ").isEmpty())
        assertTrue(CookieSnapshot.pairs("; ;").isEmpty())
    }

    @Test
    fun `畸形片段被跳过`() {
        assertEquals(listOf("a=1"), CookieSnapshot.pairs("novalue; =x; a=1; ="))
    }

    @Test
    fun `值里包含等号也能保留`() {
        assertEquals(listOf("token=abc==def"), CookieSnapshot.pairs("token=abc==def"))
    }

    @Test
    fun `回填字符串带 Path 与 Max-Age`() {
        val value = CookieSnapshot.toSetCookieValue("sess=1", secure = false)
        assertTrue(value.startsWith("sess=1"))
        assertTrue(value.contains("Path=/"))
        assertTrue(value.contains("Max-Age=${CookieSnapshot.MAX_AGE_SECONDS}"))
        assertFalse(value.contains("Secure"))
    }

    @Test
    fun `https 站点回填带 Secure`() {
        val value = CookieSnapshot.toSetCookieValue("sess=1", secure = true)
        assertTrue(value.contains("Secure"))
    }
}

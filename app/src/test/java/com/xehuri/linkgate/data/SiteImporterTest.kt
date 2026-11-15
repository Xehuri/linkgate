package com.xehuri.linkgate.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteImporterTest {

    private val existing = listOf(
        Site(id = "id-1", title = "A", url = "https://a.example.com"),
        Site(id = "id-2", title = "B", url = "https://b.example.com"),
    )

    @Test
    fun `新站点追加在末尾并保持顺序`() {
        val imported = listOf(
            Site(id = "n1", title = "C", url = "https://c.example.com"),
            Site(id = "n2", title = "D", url = "https://d.example.com"),
        )
        val merged = SiteImporter.merge(existing, imported)
        assertEquals(listOf("id-1", "id-2", "n1", "n2"), merged.map { it.id })
    }

    @Test
    fun `重复地址不会再导入一次`() {
        val imported = listOf(
            Site(id = "n1", title = "A2", url = "https://a.example.com"),
            Site(id = "n2", title = "A3", url = "https://a.example.com/"),
            Site(id = "n3", title = "E", url = "https://e.example.com"),
        )
        val merged = SiteImporter.merge(existing, imported)
        assertEquals(3, merged.size)
        assertEquals("E", merged.last().title)
        assertEquals(1, SiteImporter.countNew(existing, imported))
    }

    @Test
    fun `id 冲突时重新生成避免覆盖`() {
        val imported = listOf(Site(id = "id-1", title = "冲突", url = "https://conflict.example.com"))
        val merged = SiteImporter.merge(existing, imported)
        assertEquals(3, merged.size)
        val added = merged.last()
        assertEquals("冲突", added.title)
        assertTrue("id 必须被替换", added.id != "id-1")
        assertEquals("原有站点不能被改", "A", merged.first().title)
    }

    @Test
    fun `空 id 会被补齐`() {
        val merged = SiteImporter.merge(existing, listOf(Site(id = "", title = "X", url = "https://x.example.com")))
        assertTrue(merged.last().id.isNotBlank())
    }

    @Test
    fun `导入空列表保持原样`() {
        assertEquals(existing, SiteImporter.merge(existing, emptyList()))
        assertEquals(0, SiteImporter.countNew(existing, emptyList()))
    }

    @Test
    fun `描述与 IPv6 字段随导入保留`() {
        val imported = Site(
            id = "n1",
            title = "带备注",
            url = "https://note.example.com",
            description = "备注内容",
            ipv6Url = "http://[2408::1]:8080",
        )
        val merged = SiteImporter.merge(existing, listOf(imported))
        assertEquals("备注内容", merged.last().description)
        assertEquals("http://[2408::1]:8080", merged.last().ipv6Url)
    }
}

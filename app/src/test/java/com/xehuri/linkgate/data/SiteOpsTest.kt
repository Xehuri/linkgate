package com.xehuri.linkgate.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class SiteOpsTest {

    private val a = Site("a", "A", "https://a.com")
    private val b = Site("b", "B", "https://b.com")
    private val c = Site("c", "C", "https://c.com")
    private val list = listOf(a, b, c)

    @Test
    fun `添加追加到末尾`() {
        val d = Site("d", "D", "https://d.com")
        assertEquals(listOf("a", "b", "c", "d"), SiteOps.add(list, d).map { it.id })
    }

    @Test
    fun `编辑只替换同一 id`() {
        val renamed = b.copy(title = "B2", url = "https://b2.com")
        val result = SiteOps.update(list, renamed)
        assertEquals("B2", result[1].title)
        assertEquals("A", result[0].title)
        assertEquals(3, result.size)
    }

    @Test
    fun `删除移除对应项`() {
        assertEquals(listOf("a", "c"), SiteOps.remove(list, "b").map { it.id })
    }

    @Test
    fun `删除不存在的 id 不改变列表`() {
        assertEquals(list, SiteOps.remove(list, "zzz"))
    }

    @Test
    fun `置顶把目标移到最后一项到最前`() {
        assertEquals(listOf("c", "a", "b"), SiteOps.moveToTop(list, "c").map { it.id })
    }

    @Test
    fun `已经在最前时置顶不改变列表`() {
        assertSame(list, SiteOps.moveToTop(list, "a"))
    }

    @Test
    fun `置顶不存在的 id 不改变列表`() {
        assertSame(list, SiteOps.moveToTop(list, "zzz"))
    }

    @Test
    fun `上移交换相邻两项`() {
        assertEquals(listOf("b", "a", "c"), SiteOps.moveUp(list, "b").map { it.id })
    }

    @Test
    fun `第一项上移不改变列表`() {
        assertSame(list, SiteOps.moveUp(list, "a"))
    }

    @Test
    fun `下移交换相邻两项且末项不动`() {
        assertEquals(listOf("a", "c", "b"), SiteOps.moveDown(list, "b").map { it.id })
        assertSame(list, SiteOps.moveDown(list, "c"))
    }
}

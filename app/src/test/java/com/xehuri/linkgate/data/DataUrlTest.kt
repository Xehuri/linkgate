package com.xehuri.linkgate.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DataUrlTest {

    @Test
    fun `base64 数据能解码出字节与扩展名`() {
        val decoded = DataUrl.decode("data:text/plain;base64,SGVsbG8gTGluayBHYXRl")
        assertEquals("Hello Link Gate", String(decoded!!.bytes, Charsets.UTF_8))
        assertEquals("text/plain", decoded.mime)
        assertTrue(decoded.fileName.endsWith(".txt"))
    }

    @Test
    fun `百分号编码的数据能解码`() {
        val decoded = DataUrl.decode("data:text/csv,%E5%90%8D%E5%AD%97%2C%E5%B9%B4%E9%BE%84")
        assertEquals("名字,年龄", String(decoded!!.bytes, Charsets.UTF_8))
        assertTrue(decoded.fileName.endsWith(".csv"))
    }

    @Test
    fun `图片类型推导扩展名`() {
        val decoded = DataUrl.decode("data:image/png;base64,iVBORw0KGgo=")
        assertEquals(".png", decoded!!.fileName.takeLast(4))
        assertEquals("image/png", decoded.mime)
    }

    @Test
    fun `没有 mime 时按二进制处理`() {
        val decoded = DataUrl.decode("data:,just-text")
        assertEquals("just-text", String(decoded!!.bytes, Charsets.UTF_8))
        assertEquals("application/octet-stream", decoded.mime)
        assertTrue(decoded.fileName.endsWith(".bin"))
    }

    @Test
    fun `非法输入返回 null`() {
        assertNull(DataUrl.decode(null))
        assertNull(DataUrl.decode(""))
        assertNull(DataUrl.decode("https://example.com/a.txt"))
        assertNull(DataUrl.decode("data:text/plain;base64,%%%not-base64%%%"))
        assertNull(DataUrl.decode("data:text/plain"))
    }
}

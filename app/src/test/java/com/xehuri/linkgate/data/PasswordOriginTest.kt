package com.xehuri.linkgate.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PasswordOriginTest {

    @Test
    fun `去掉路径查询与锚点`() {
        assertEquals("https://dsh.xehuri.top", PasswordOrigin.fromUrl("https://dsh.xehuri.top/login?next=/x#y"))
    }

    @Test
    fun `保留端口并小写主机`() {
        assertEquals("http://127.0.0.1:18081", PasswordOrigin.fromUrl("http://127.0.0.1:18081/login-form"))
        assertEquals("https://example.com", PasswordOrigin.fromUrl("HTTPS://Example.COM/a"))
    }

    @Test
    fun `非 http 协议返回 null`() {
        assertNull(PasswordOrigin.fromUrl("file:///etc/passwd"))
        assertNull(PasswordOrigin.fromUrl("ftp://example.com/x"))
        assertNull(PasswordOrigin.fromUrl("javascript:alert(1)"))
        assertNull(PasswordOrigin.fromUrl(""))
        assertNull(PasswordOrigin.fromUrl(null))
    }

    @Test
    fun `不同 origin 不相等`() {
        val first = PasswordOrigin.fromUrl("https://a.example.com/login")
        val second = PasswordOrigin.fromUrl("https://b.example.com/login")
        assertEquals("https://a.example.com", first)
        assertEquals("https://b.example.com", second)
    }
}

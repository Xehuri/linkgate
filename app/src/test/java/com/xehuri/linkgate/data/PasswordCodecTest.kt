package com.xehuri.linkgate.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordCodecTest {

    private val samples = listOf(
        PasswordEntry("https://dsh.xehuri.top", "axeuh", "p@ss\tword", 1_700_000_000_000L),
        PasswordEntry("http://127.0.0.1:18081", "", "simple", 1_700_000_000_001L),
    )

    @Test
    fun `编码后可完整解码`() {
        assertEquals(samples, PasswordCodec.decode(PasswordCodec.encode(samples)))
    }

    @Test
    fun `空列表与垃圾数据返回空`() {
        assertTrue(PasswordCodec.decode(PasswordCodec.encode(emptyList())).isEmpty())
        assertTrue(PasswordCodec.decode(null).isEmpty())
        assertTrue(PasswordCodec.decode("").isEmpty())
        assertTrue(PasswordCodec.decode("garbage").isEmpty())
        assertTrue(PasswordCodec.decode("LINKGATE-PASSWORDS/2\nx\ty\tz\t1").isEmpty())
    }

    @Test
    fun `空密码的记录被丢弃`() {
        val encoded = PasswordCodec.encode(
            listOf(PasswordEntry("https://a.com", "u", "", 1L)),
        )
        assertTrue(PasswordCodec.decode(encoded).isEmpty())
    }

    @Test
    fun `损坏行不影响其它记录`() {
        val good = PasswordCodec.encode(samples.take(1))
        val broken = good + "\n坏行没有制表符\nnot\tbase64\tbase64\tnot-a-number"
        val decoded = PasswordCodec.decode(broken)
        assertEquals(1, decoded.size)
        assertEquals("axeuh", decoded.first().username)
    }
}

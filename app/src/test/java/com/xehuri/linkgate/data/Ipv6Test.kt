package com.xehuri.linkgate.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Ipv6Test {

    @Test
    fun `全球单播与唯一本地地址可用`() {
        assertTrue(Ipv6.isUsableLiteral("2408:8207:1234::1"))
        assertTrue(Ipv6.isUsableLiteral("fd00:0:0:0:0:0:0:1"))
        assertTrue(Ipv6.isUsableLiteral("[2408:8207:1234::1]"))
        assertTrue(Ipv6.isUsableLiteral("2408:8207:1234::1%wlan0"))
    }

    @Test
    fun `回环链路本地与组播不可用`() {
        assertFalse(Ipv6.isUsableLiteral("::1"))
        assertFalse(Ipv6.isUsableLiteral("::"))
        assertFalse(Ipv6.isUsableLiteral("fe80::1"))
        assertFalse(Ipv6.isUsableLiteral("ff02::1"))
    }

    @Test
    fun `不是 IPv6 的输入一律不可用`() {
        assertFalse(Ipv6.isUsableLiteral(""))
        assertFalse(Ipv6.isUsableLiteral("127.0.0.1"))
        assertFalse(Ipv6.isUsableLiteral("example.com"))
        assertFalse(Ipv6.isUsableLiteral("https://[2408:8207:1234::1]"))
        assertFalse(Ipv6.isUsableLiteral("2408:8207:zzzz::1"))
    }

    @Test
    fun `本机 IPv6 判定不会抛异常`() {
        // 结果取决于运行环境，这里只要求不崩（设备分支在真机上另有验证）
        Ipv6.isAvailableOnDevice()
        Ipv6.firstDeviceAddress()
    }
}

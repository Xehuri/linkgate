package com.xehuri.linkgate.data

import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface

/**
 * IPv6 相关的判定。分两层：
 * - [isUsableLiteral] 纯字符串/地址判定，可单元测试；
 * - [isAvailableOnDevice] 枚举本机网卡，判断是否真的拿到了可路由的 IPv6 地址。
 */
object Ipv6 {

    /**
     * 是不是「可用来访问外网」的 IPv6 字面量。
     * 排除：回环（::1）、未指定（::）、链路本地（fe80::/10）、组播（ff00::/8）。
     * 保留：全球单播与唯一本地地址（fc00::/7，家宽/内网也用得上）。
     */
    fun isUsableLiteral(text: String): Boolean {
        var value = text.trim()
        if (value.isEmpty()) return false
        if (value.startsWith("[") && value.endsWith("]")) {
            value = value.substring(1, value.length - 1)
        }
        value = value.substringBefore('%')
        if (!value.contains(':')) return false
        val address = try {
            InetAddress.getByName(value)
        } catch (e: Throwable) {
            return false
        }
        if (address !is Inet6Address) return false
        return !address.isLoopbackAddress &&
            !address.isAnyLocalAddress &&
            !address.isLinkLocalAddress &&
            !address.isMulticastAddress
    }

    /**
     * 本机当前是否有可用的 IPv6 地址。
     * 只看已启用、非回环的网卡，且地址必须是可路由的 IPv6。
     */
    fun isAvailableOnDevice(): Boolean {
        return try {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty().any { nif ->
                try {
                    if (!nif.isUp || nif.isLoopback) return@any false
                } catch (e: Throwable) {
                    return@any false
                }
                nif.inetAddresses.toList().any { address ->
                    address is Inet6Address &&
                        !address.isLoopbackAddress &&
                        !address.isAnyLocalAddress &&
                        !address.isLinkLocalAddress &&
                        !address.isMulticastAddress
                }
            }
        } catch (e: Throwable) {
            false
        }
    }

    /** 当前第一个可用的 IPv6 地址文本（用于设置页展示），没有则 null。 */
    fun firstDeviceAddress(): String? {
        return try {
            for (nif in NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()) {
                try {
                    if (!nif.isUp || nif.isLoopback) continue
                } catch (e: Throwable) {
                    continue
                }
                for (address in nif.inetAddresses.toList()) {
                    if (address is Inet6Address &&
                        !address.isLoopbackAddress &&
                        !address.isAnyLocalAddress &&
                        !address.isLinkLocalAddress &&
                        !address.isMulticastAddress
                    ) {
                        return address.hostAddress?.substringBefore('%')
                    }
                }
            }
            null
        } catch (e: Throwable) {
            null
        }
    }
}

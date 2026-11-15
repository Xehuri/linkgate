package com.xehuri.linkgate.data

/**
 * 一个可快捷打开的网页。列表按存储顺序展示，索引 0 在最上面。
 *
 * @param id 稳定标识，用于编辑/删除/排序时不依赖标题唯一性
 * @param title 展示名；用户没填时用域名兜底
 * @param url 已规范化的 http/https 链接（主地址）
 * @param description 备注信息，卡片上小字显示，可留空
 * @param ipv6Url 可选的 IPv6 备用地址；开启「IPv6 优先」且本机有 IPv6 时优先用它打开，
 *                打不开会自动回退到主地址。可留空
 */
data class Site(
    val id: String,
    val title: String,
    val url: String,
    val description: String = "",
    val ipv6Url: String = "",
) {
    /** 是否配置了 IPv6 备用地址 */
    val hasIpv6: Boolean get() = ipv6Url.isNotBlank()

    /** 卡片副标题：优先显示描述，没有描述时显示域名 */
    fun subtitleText(): String =
        description.trim().ifBlank { UrlNormalizer.titleFromUrl(url) }
}

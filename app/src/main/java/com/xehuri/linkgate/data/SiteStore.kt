package com.xehuri.linkgate.data

import android.content.Context

/**
 * 站点列表的本地持久化（SharedPreferences 单 key 存编码后的纯文本）。
 * 数据量很小（几十个站点），不需要数据库。
 */
class SiteStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): List<Site> = SiteCodec.decode(prefs.getString(KEY_SITES, null))

    fun save(sites: List<Site>) {
        prefs.edit().putString(KEY_SITES, SiteCodec.encode(sites)).apply()
    }

    /**
     * 上一次「自动批量申请权限」时待申请权限集合的签名。签名不变就不再重复弹窗，
     * 权限集合变化（例如系统升级带来的新权限）时会再次自动申请。
     */
    fun autoRequestSignature(): String = prefs.getString(KEY_PERMISSION_SIGNATURE, "").orEmpty()

    fun setAutoRequestSignature(signature: String) {
        prefs.edit().putString(KEY_PERMISSION_SIGNATURE, signature).apply()
    }

    companion object {
        private const val PREFS_NAME = "linkgate_prefs"
        private const val KEY_SITES = "sites_v1"
        private const val KEY_PERMISSION_SIGNATURE = "permission_signature_v1"
    }
}

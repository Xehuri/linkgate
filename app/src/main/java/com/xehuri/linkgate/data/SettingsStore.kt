package com.xehuri.linkgate.data

import android.content.Context

/**
 * 全局设置的本地持久化。与站点数据分开存（独立 prefs 文件），
 * 这样清空站点数据不会顺手把设置也清掉。
 */
class SettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): AppSettings = SettingsCodec.decode(prefs.getString(KEY_SETTINGS, null))

    fun save(settings: AppSettings) {
        prefs.edit().putString(KEY_SETTINGS, SettingsCodec.encode(settings)).apply()
    }

    private companion object {
        const val PREFS_NAME = "linkgate_settings"
        const val KEY_SETTINGS = "settings_v1"
    }
}

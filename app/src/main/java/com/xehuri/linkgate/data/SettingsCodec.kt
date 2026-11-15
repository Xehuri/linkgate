package com.xehuri.linkgate.data

/**
 * 设置的持久化编码：纯文本 `key=value`，一行一项。
 *
 * ```
 * LINKGATE-SETTINGS/1
 * prefer_ipv6=1
 * wake_taps=2
 * view_mode=SYSTEM
 * ```
 *
 * 解码规则（保证升级/降级都不会让应用崩或丢设置）：
 * - 头部不认识 → 全部用默认值；
 * - 不认识的 key → 忽略；
 * - 值非法 → 该项用默认值，其余项照常读取。
 */
object SettingsCodec {

    private const val HEADER = "LINKGATE-SETTINGS/1"

    private const val KEY_PREFER_IPV6 = "prefer_ipv6"
    private const val KEY_WAKE_TAPS = "wake_taps"
    private const val KEY_ZOOM = "zoom_enabled"
    private const val KEY_JS = "js_enabled"
    private const val KEY_DOM = "dom_storage"
    private const val KEY_DATABASE = "database"
    private const val KEY_IMAGES = "images"
    private const val KEY_CACHE_MODE = "cache_mode"
    private const val KEY_MIXED_CONTENT = "mixed_content"
    private const val KEY_TEXT_ZOOM = "text_zoom"
    private const val KEY_THIRD_PARTY_COOKIES = "third_party_cookies"
    private const val KEY_MEDIA_AUTOPLAY = "media_autoplay"
    private const val KEY_ENGINE = "engine"
    private const val KEY_VIEW_MODE = "view_mode"
    private const val KEY_HARDWARE_ACCEL = "hardware_accel"
    private const val KEY_DARK_MODE = "dark_mode"
    private const val KEY_UA_MODE = "ua_mode"
    private const val KEY_UA_CUSTOM = "ua_custom"

    fun encode(settings: AppSettings): String {
        val s = settings.sanitized()
        return buildString {
            append(HEADER).append('\n')
            append(KEY_PREFER_IPV6).append('=').append(bool(s.preferIpv6)).append('\n')
            append(KEY_WAKE_TAPS).append('=').append(s.wakeTapCount).append('\n')
            append(KEY_ZOOM).append('=').append(bool(s.zoomEnabled)).append('\n')
            append(KEY_JS).append('=').append(bool(s.javaScriptEnabled)).append('\n')
            append(KEY_DOM).append('=').append(bool(s.domStorageEnabled)).append('\n')
            append(KEY_DATABASE).append('=').append(bool(s.databaseEnabled)).append('\n')
            append(KEY_IMAGES).append('=').append(bool(s.loadImages)).append('\n')
            append(KEY_CACHE_MODE).append('=').append(s.cacheMode.name).append('\n')
            append(KEY_MIXED_CONTENT).append('=').append(s.mixedContent.name).append('\n')
            append(KEY_TEXT_ZOOM).append('=').append(s.textZoomPercent).append('\n')
            append(KEY_THIRD_PARTY_COOKIES).append('=').append(bool(s.thirdPartyCookies)).append('\n')
            append(KEY_MEDIA_AUTOPLAY).append('=').append(bool(s.mediaAutoplay)).append('\n')
            append(KEY_ENGINE).append('=').append(s.engine.name).append('\n')
            append(KEY_VIEW_MODE).append('=').append(s.viewMode.name).append('\n')
            append(KEY_HARDWARE_ACCEL).append('=').append(bool(s.hardwareAcceleration)).append('\n')
            append(KEY_DARK_MODE).append('=').append(s.darkMode.name).append('\n')
            append(KEY_UA_MODE).append('=').append(s.userAgentMode.name).append('\n')
            append(KEY_UA_CUSTOM).append('=').append(escape(s.customUserAgent)).append('\n')
        }
    }

    fun decode(raw: String?): AppSettings {
        val defaults = AppSettings.DEFAULT
        if (raw.isNullOrBlank()) return defaults
        val lines = raw.split('\n')
        if (lines.firstOrNull()?.trim() != HEADER) return defaults
        val map = HashMap<String, String>(32)
        for (line in lines.drop(1)) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
            val eq = trimmed.indexOf('=')
            if (eq <= 0) continue
            map[trimmed.substring(0, eq)] = trimmed.substring(eq + 1)
        }
        return AppSettings(
            preferIpv6 = map.bool(KEY_PREFER_IPV6, defaults.preferIpv6),
            wakeTapCount = map.int(KEY_WAKE_TAPS, defaults.wakeTapCount),
            zoomEnabled = map.bool(KEY_ZOOM, defaults.zoomEnabled),
            javaScriptEnabled = map.bool(KEY_JS, defaults.javaScriptEnabled),
            domStorageEnabled = map.bool(KEY_DOM, defaults.domStorageEnabled),
            databaseEnabled = map.bool(KEY_DATABASE, defaults.databaseEnabled),
            loadImages = map.bool(KEY_IMAGES, defaults.loadImages),
            cacheMode = map.enum(KEY_CACHE_MODE, defaults.cacheMode),
            mixedContent = map.enum(KEY_MIXED_CONTENT, defaults.mixedContent),
            textZoomPercent = map.int(KEY_TEXT_ZOOM, defaults.textZoomPercent),
            thirdPartyCookies = map.bool(KEY_THIRD_PARTY_COOKIES, defaults.thirdPartyCookies),
            mediaAutoplay = map.bool(KEY_MEDIA_AUTOPLAY, defaults.mediaAutoplay),
            engine = map.enum(KEY_ENGINE, defaults.engine),
            viewMode = map.enum(KEY_VIEW_MODE, defaults.viewMode),
            hardwareAcceleration = map.bool(KEY_HARDWARE_ACCEL, defaults.hardwareAcceleration),
            darkMode = map.enum(KEY_DARK_MODE, defaults.darkMode),
            userAgentMode = map.enum(KEY_UA_MODE, defaults.userAgentMode),
            customUserAgent = unescape(map[KEY_UA_CUSTOM].orEmpty()),
        ).sanitized()
    }

    private fun bool(value: Boolean): String = if (value) "1" else "0"

    private fun HashMap<String, String>.bool(key: String, fallback: Boolean): Boolean =
        when (this[key]?.lowercase()) {
            "1", "true", "yes", "on" -> true
            "0", "false", "no", "off" -> false
            else -> fallback
        }

    private fun HashMap<String, String>.int(key: String, fallback: Int): Int =
        this[key]?.toIntOrNull() ?: fallback

    private inline fun <reified T : Enum<T>> HashMap<String, String>.enum(key: String, fallback: T): T {
        val raw = this[key] ?: return fallback
        return enumValues<T>().firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: fallback
    }

    /** 自定义 UA 里可能出现换行等字符，编码时转义，避免破坏行结构 */
    private fun escape(value: String): String = buildString {
        for (ch in value) {
            when (ch) {
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '=' -> append("\\e")
                else -> append(ch)
            }
        }
    }

    private fun unescape(value: String): String = buildString {
        var index = 0
        while (index < value.length) {
            val ch = value[index]
            if (ch == '\\' && index + 1 < value.length) {
                when (val next = value[index + 1]) {
                    'n' -> append('\n')
                    'r' -> append('\r')
                    'e' -> append('=')
                    '\\' -> append('\\')
                    else -> {
                        append(ch)
                        append(next)
                    }
                }
                index += 2
            } else {
                append(ch)
                index++
            }
        }
    }
}

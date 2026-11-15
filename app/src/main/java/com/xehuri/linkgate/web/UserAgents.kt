package com.xehuri.linkgate.web

import com.xehuri.linkgate.data.UserAgentMode
import com.xehuri.linkgate.data.ViewMode

/**
 * 浏览器标识（UA）的组装。纯函数，方便单元测试。
 *
 * 只做两件事：从系统 WebView 的 UA 里取出 Chrome 主版本号，然后按模式拼出目标 UA。
 * 不用固定死的版本号：老平板的 WebView 是 104，系统 UA 里就带 104，桌面版 UA 也跟着 104，
 * 这样站点看到的版本与真实渲染引擎一致，不会出现「声称支持但实际不支持」的错配。
 */
object UserAgents {

    private val CHROME_MAJOR = Regex("Chrome/(\\d+)")

    /** 桌面版 Chrome UA 模板 */
    private const val DESKTOP_TEMPLATE =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/%s.0.0.0 Safari/537.36"

    /** 从系统 UA 里取 Chrome 主版本号；取不到时退回一个保守值 */
    fun chromeMajor(defaultUa: String): String =
        CHROME_MAJOR.find(defaultUa)?.groupValues?.getOrNull(1) ?: "120"

    /** 桌面版 UA */
    fun desktop(defaultUa: String): String =
        DESKTOP_TEMPLATE.format(chromeMajor(defaultUa))

    /** 系统 UA + LinkGate 标记（方便站点侧识别，也不影响识别为 Chrome） */
    fun system(defaultUa: String, appVersion: String): String =
        "$defaultUa LinkGate/$appVersion"

    /**
     * 按设置解析最终 UA。
     *
     * @param mode UA 策略
     * @param viewMode 视图模式（FOLLOW_VIEW 时决定是否用桌面 UA）
     * @param customUa 自定义 UA（CUSTOM 时生效）
     * @param defaultUa 系统 WebView 的默认 UA
     * @param appVersion 应用版本号
     */
    fun resolve(
        mode: UserAgentMode,
        viewMode: ViewMode,
        customUa: String,
        defaultUa: String,
        appVersion: String,
    ): String = when (mode) {
        UserAgentMode.FOLLOW_VIEW ->
            if (viewMode == ViewMode.DESKTOP) desktop(defaultUa) else system(defaultUa, appVersion)
        UserAgentMode.SYSTEM -> system(defaultUa, appVersion)
        UserAgentMode.DESKTOP -> desktop(defaultUa)
        UserAgentMode.CUSTOM -> customUa.trim().ifEmpty { system(defaultUa, appVersion) }
    }
}

// LinkGate 顶层构建脚本：只声明插件版本，不在这里配置模块。
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

package com.xehuri.linkgate.perm

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 交叉校验：代码里要申请的运行时权限，必须在 AndroidManifest.xml 里真的声明过。
 * 少声明一条，请求就会静默失败（系统直接返回 denied），这类问题在纯逻辑测试里发现不了。
 */
class ManifestPermissionsTest {

    private val manifestText: String by lazy {
        val candidates = listOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml"),
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: throw IllegalStateException("找不到 AndroidManifest.xml（工作目录=${File(".").absolutePath}）")
        file.readText()
    }

    @Test
    fun `所有运行时权限都在清单中声明`() {
        val permissions = listOf(
            PermNames.RECORD_AUDIO,
            PermNames.CAMERA,
            PermNames.ACCESS_FINE_LOCATION,
            PermNames.ACCESS_COARSE_LOCATION,
            PermNames.POST_NOTIFICATIONS,
            PermNames.READ_EXTERNAL_STORAGE,
            PermNames.WRITE_EXTERNAL_STORAGE,
            PermNames.READ_MEDIA_IMAGES,
            PermNames.READ_MEDIA_VIDEO,
            PermNames.READ_MEDIA_AUDIO,
        )
        permissions.forEach { permission ->
            assertTrue("AndroidManifest.xml 缺少 $permission", manifestText.contains(permission))
        }
    }

    @Test
    fun `旧存储权限带上了 maxSdkVersion 上限`() {
        assertTrue(
            "READ_EXTERNAL_STORAGE 应有 maxSdkVersion=32",
            manifestText.contains("android.permission.READ_EXTERNAL_STORAGE") &&
                Regex(
                    "READ_EXTERNAL_STORAGE\"[^>]*maxSdkVersion=\"32\"",
                    setOf(RegexOption.DOT_MATCHES_ALL),
                ).containsMatchIn(manifestText),
        )
        assertTrue(
            "WRITE_EXTERNAL_STORAGE 应有 maxSdkVersion=28",
            Regex(
                "WRITE_EXTERNAL_STORAGE\"[^>]*maxSdkVersion=\"28\"",
                setOf(RegexOption.DOT_MATCHES_ALL),
            ).containsMatchIn(manifestText),
        )
    }

    @Test
    fun `返回键接管与明文流量开关已配置`() {
        assertTrue(
            "应开启 enableOnBackInvokedCallback 以便 Android 13+ 走统一返回分发",
            manifestText.contains("android:enableOnBackInvokedCallback=\"true\""),
        )
        assertTrue(
            "应允许明文 http，避免 http 站点直接打不开",
            manifestText.contains("android:usesCleartextTraffic=\"true\""),
        )
    }
}

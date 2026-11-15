package com.xehuri.linkgate.perm

/**
 * 权限字符串集中定义。刻意不用 Manifest.permission.* 常量：
 * 这样权限规划逻辑可以放在纯 JVM 单元测试里跑（android.jar 是桩实现）。
 * 这里的字符串必须与 AndroidManifest.xml 中声明的一致。
 */
object PermNames {
    const val RECORD_AUDIO = "android.permission.RECORD_AUDIO"
    const val CAMERA = "android.permission.CAMERA"
    const val ACCESS_FINE_LOCATION = "android.permission.ACCESS_FINE_LOCATION"
    const val ACCESS_COARSE_LOCATION = "android.permission.ACCESS_COARSE_LOCATION"
    const val POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS"
    const val READ_EXTERNAL_STORAGE = "android.permission.READ_EXTERNAL_STORAGE"
    const val WRITE_EXTERNAL_STORAGE = "android.permission.WRITE_EXTERNAL_STORAGE"
    const val READ_MEDIA_IMAGES = "android.permission.READ_MEDIA_IMAGES"
    const val READ_MEDIA_VIDEO = "android.permission.READ_MEDIA_VIDEO"
    const val READ_MEDIA_AUDIO = "android.permission.READ_MEDIA_AUDIO"
}

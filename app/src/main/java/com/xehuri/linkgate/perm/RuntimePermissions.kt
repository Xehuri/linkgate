package com.xehuri.linkgate.perm

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

/** Android 运行时权限的查询与跳转。 */
object RuntimePermissions {

    fun isGranted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** 当前系统版本下、清单里这些运行时权限中已经授予的集合。 */
    fun granted(context: Context): Set<String> =
        PermissionPlanner.items(Build.VERSION.SDK_INT)
            .map { it.permission }
            .filter { isGranted(context, it) }
            .toSet()

    fun missing(context: Context): List<String> =
        PermissionPlanner.pending(Build.VERSION.SDK_INT, granted(context))

    fun openAppSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }
}

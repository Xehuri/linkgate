package com.xehuri.linkgate.perm

/**
 * 浏览器可能用到的运行时权限清单，以及「按系统版本该申请哪些」的纯逻辑。
 *
 * 按 API 级别过滤是必须的：READ_MEDIA_* 只在 33+ 存在，WRITE_EXTERNAL_STORAGE 只在 28- 才有意义，
 * 在错误的版本上申请会直接抛 SecurityException 或被系统静默忽略。
 */
object PermissionPlanner {

    data class Item(
        val permission: String,
        val title: String,
        val description: String,
    )

    /** 需要向系统申请的运行时权限（顺序即弹窗顺序，麦克风/摄像头放最前面）。 */
    fun items(sdkInt: Int): List<Item> {
        val result = ArrayList<Item>(10)
        result.add(Item(PermNames.RECORD_AUDIO, "麦克风", "网页语音输入、语音通话、录音"))
        result.add(Item(PermNames.CAMERA, "摄像头", "网页拍照、扫码、视频通话"))
        result.add(Item(PermNames.ACCESS_FINE_LOCATION, "精确位置", "地图、附近服务、定位类网页"))
        result.add(Item(PermNames.ACCESS_COARSE_LOCATION, "大致位置", "粗略定位与天气类网页"))
        if (sdkInt >= 33) {
            result.add(Item(PermNames.POST_NOTIFICATIONS, "通知", "下载完成提醒、网页通知"))
            result.add(Item(PermNames.READ_MEDIA_IMAGES, "照片和图片", "网页上传图片"))
            result.add(Item(PermNames.READ_MEDIA_VIDEO, "视频", "网页上传视频"))
            result.add(Item(PermNames.READ_MEDIA_AUDIO, "音乐和音频", "网页上传音频文件"))
        } else {
            result.add(Item(PermNames.READ_EXTERNAL_STORAGE, "读取存储", "网页上传文件、下载管理"))
            if (sdkInt <= 28) {
                result.add(Item(PermNames.WRITE_EXTERNAL_STORAGE, "写入存储", "把下载文件保存到公共目录"))
            }
        }
        return result
    }

    /** 仍未授予的权限（用于批量预申请）。 */
    fun pending(sdkInt: Int, granted: Set<String>): List<String> =
        items(sdkInt).map { it.permission }.distinct().filter { it !in granted }

    /** 待申请集合的签名：用于「同一批权限不重复弹窗」。 */
    fun signature(pending: List<String>): String = pending.sorted().joinToString("|")
}

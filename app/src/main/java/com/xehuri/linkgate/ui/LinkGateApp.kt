package com.xehuri.linkgate.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewDatabase
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.xehuri.linkgate.BuildConfig
import com.xehuri.linkgate.data.AppSettings
import com.xehuri.linkgate.data.DownloadRecord
import com.xehuri.linkgate.data.DownloadStore
import com.xehuri.linkgate.data.IconStore
import com.xehuri.linkgate.data.Ipv6
import com.xehuri.linkgate.data.PasswordEntry
import com.xehuri.linkgate.data.PasswordStore
import com.xehuri.linkgate.data.SettingsStore
import com.xehuri.linkgate.data.Site
import com.xehuri.linkgate.data.SiteCodec
import com.xehuri.linkgate.data.SiteImporter
import com.xehuri.linkgate.data.SiteOps
import com.xehuri.linkgate.data.SiteStore
import com.xehuri.linkgate.nav.OpenTargetPlanner
import com.xehuri.linkgate.perm.PermissionPlanner
import com.xehuri.linkgate.perm.RuntimePermissions
import com.xehuri.linkgate.web.CookieStore
import com.xehuri.linkgate.web.DownloadCenter
import com.xehuri.linkgate.web.DownloadWatcher
import com.xehuri.linkgate.web.DownloadSaver
import com.xehuri.linkgate.web.SiteDataCleaner
import com.xehuri.linkgate.web.WebViewFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** favicon 抓取并发度：站点多时避免一次性开几十条连接 */
private const val ICON_FETCH_PARALLELISM = 3

/**
 * 应用根组件：三种界面（站点列表首屏 / 网页 / 设置），外加权限批量预申请、密码库与权限对话框。
 *
 * 返回键总策略：
 * - 设置界面：回站点列表
 * - 网页界面：网页历史 → 站点列表首屏（见 [com.xehuri.linkgate.nav.BackPlanner]）
 * - 列表界面：直接退出应用
 */
@Composable
fun LinkGateApp(store: SiteStore, settingsStore: SettingsStore, onExit: () -> Unit) {
    val context = LocalContext.current
    val darkTheme = isSystemInDarkTheme()

    var sites by remember { mutableStateOf(store.load()) }
    var settings by remember { mutableStateOf(settingsStore.load()) }
    var openSiteId by rememberSaveable { mutableStateOf<String?>(null) }
    var openPlan by remember { mutableStateOf<OpenTargetPlanner.Plan?>(null) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showDownloads by rememberSaveable { mutableStateOf(false) }
    var downloadRecords by remember { mutableStateOf(DownloadStore(context).all()) }
    var pendingDownload by remember { mutableStateOf<DownloadRecord?>(null) }
    var granted by remember { mutableStateOf(RuntimePermissions.granted(context)) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var iconVersion by remember { mutableIntStateOf(0) }
    val iconFetchAttempted = remember { mutableSetOf<String>() }
    var passwordEntries by remember { mutableStateOf<List<PasswordEntry>>(emptyList()) }
    var showPasswordManager by remember { mutableStateOf(false) }

    val clipboard = remember(context) {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    }
    val webViewVersion = remember(context) {
        runCatching { WebView.getCurrentWebViewPackage()?.versionName }.getOrNull() ?: "未知"
    }
    val defaultUserAgent = remember(context) { WebViewFactory.defaultUserAgent(context) }

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted = RuntimePermissions.granted(context) }

    val requestPermissions: (List<String>) -> Unit = { permissions ->
        val pending = permissions.filter { it.isNotBlank() }.distinct()
        if (pending.isEmpty()) {
            granted = RuntimePermissions.granted(context)
        } else {
            permissionLauncher.launch(pending.toTypedArray())
        }
    }

    // 预先申请：首次启动（以及权限集合变化时）把清单里所有运行时权限一次性批量申请。
    // 用签名去重，避免每次回到应用都重复弹窗。
    LaunchedEffect(Unit) {
        val missing = RuntimePermissions.missing(context)
        if (missing.isEmpty()) {
            store.setAutoRequestSignature("")
        } else {
            val signature = PermissionPlanner.signature(missing)
            if (store.autoRequestSignature() != signature) {
                store.setAutoRequestSignature(signature)
                permissionLauncher.launch(missing.toTypedArray())
            }
        }
    }

    // 进/出网页界面时刷新权限快照（用户可能刚在系统设置里改过）；
    // 从网页返回列表时刷新图标（WebView 可能上报了站点 favicon）
    LaunchedEffect(openSiteId) {
        granted = RuntimePermissions.granted(context)
        if (openSiteId == null) iconVersion++
    }

    // 没有缓存图标的站点，后台抓一次 favicon。
    // 注意 effect 的 key 不能包含 iconVersion：成功回写 iconVersion 会让 effect 重启，
    // 在飞的协程被取消，而该站点已经被记入 attempted，于是永远只剩首字母色块。
    LaunchedEffect(sites) {
        val iconStore = IconStore(context)
        val targets = sites.filter { site ->
            !iconStore.has(site.id) && iconFetchAttempted.add(site.id)
        }
        if (targets.isEmpty()) return@LaunchedEffect
        var anySaved = false
        // 并发度限制为 3，避免站点多时一次性开一堆 TCP 连接
        targets.chunked(ICON_FETCH_PARALLELISM).forEach { chunk ->
            chunk.map { site ->
                async(Dispatchers.IO) {
                    val saved = iconStore.fetchAndSave(site.id, site.url)
                    if (!saved) {
                        // 失败（超时/无图标）不永久拉黑，下次列表变化时还能重试
                        iconFetchAttempted.remove(site.id)
                    }
                    saved
                }
            }.forEach { job ->
                if (job.await()) anySaved = true
            }
        }
        if (anySaved) withContext(Dispatchers.Main) { iconVersion++ }
    }

    // 下载完成 → 刷新历史 + 弹「是否打开」；同时监听系统 DownloadManager 的完成广播
    DisposableEffect(Unit) {
        val listener: (DownloadRecord) -> Unit = { record ->
            downloadRecords = DownloadStore(context).all()
            if (record.ok) pendingDownload = record
        }
        DownloadCenter.addListener(listener)
        val watcher = DownloadWatcher(context.applicationContext)
        watcher.start { }
        onDispose {
            DownloadCenter.removeListener(listener)
            watcher.stop()
        }
    }

    LinkGateTheme(darkTheme = darkTheme) {
        val activeSite = sites.firstOrNull { it.id == openSiteId }

        fun updateSettings(next: AppSettings) {
            val sanitized = next.sanitized()
            settingsStore.save(sanitized)
            settings = sanitized
        }

        fun clearCache() {
            runCatching {
                val temp = WebView(context)
                temp.clearCache(true)
                temp.clearFormData()
                temp.destroy()
            }
            toast("网页缓存已清除")
        }

        fun clearWebData() {
            runCatching {
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
                WebStorage.getInstance().deleteAllData()
                val database = WebViewDatabase.getInstance(context)
                database.clearFormData()
                database.clearHttpAuthUsernamePassword()
            }
            CookieStore(context).clearAll()
            toast("Cookie 与网页数据已清除（密码库不受影响）")
        }

        fun exportSites() {
            if (sites.isEmpty()) {
                toast("还没有站点可导出")
                return
            }
            clipboard?.setPrimaryClip(ClipData.newPlainText("LinkGate", SiteCodec.encode(sites)))
            toast("已复制 ${sites.size} 个站点配置到剪贴板")
        }

        fun importSites() {
            val text = clipboard?.primaryClip?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
            val imported = SiteCodec.decode(text)
            if (imported.isEmpty()) {
                toast("剪贴板里没有可识别的站点配置")
                return
            }
            val merged = SiteImporter.merge(sites, imported)
            val added = merged.size - sites.size
            if (added > 0) {
                store.save(merged)
                sites = merged
                toast("已导入 $added 个站点")
            } else {
                toast("这些站点都已经在列表里了")
            }
        }

        when {
            showDownloads -> DownloadManagerScreen(
                records = downloadRecords,
                onBack = { showDownloads = false },
                onOpen = { record ->
                    if (!DownloadCenter.open(context, record)) {
                        toast("没有能打开该文件的应用")
                    }
                },
                onDelete = { record, deleteFile ->
                    downloadRecords = DownloadCenter.remove(context, record, deleteFile)
                    toast(if (deleteFile) "已删除记录和文件" else "已删除记录")
                },
                onClearAll = {
                    val dmIds = downloadRecords.filter { it.id.startsWith("dm:") }.map { it.id }
                    val store = DownloadStore(context)
                    store.hideDmIds(dmIds)
                    downloadRecords = store.clear()
                    toast("下载记录已清空")
                },
                onOpenSystemDownloads = {
                    if (!DownloadSaver.openDownloadsUi(context)) {
                        toast("没有找到可用的文件管理器")
                    }
                },
            )

            showSettings -> SettingsScreen(
                settings = settings,
                onChange = { updateSettings(it) },
                onBack = { showSettings = false },
                deviceIpv6Available = remember(showSettings) { Ipv6.isAvailableOnDevice() },
                deviceIpv6Address = remember(showSettings) { Ipv6.firstDeviceAddress() },
                webViewVersion = webViewVersion,
                defaultUserAgent = defaultUserAgent,
                // 版本号是编译期内联常量：加 versionCode 一起显示，顺便强制本文件重编译
                appVersion = BuildConfig.VERSION_NAME + "（" + BuildConfig.VERSION_CODE + "）",
                siteCount = sites.size,
                onOpenSystemSettings = { RuntimePermissions.openAppSettings(context) },
                onOpenPermissions = {
                    granted = RuntimePermissions.granted(context)
                    showPermissionDialog = true
                },
                onClearCache = { clearCache() },
                onClearWebData = { clearWebData() },
                onExportSites = { exportSites() },
                onImportSites = { importSites() },
                onOpenDownloads = {
                    DownloadCenter.syncFromDownloadManager(context)
                    downloadRecords = DownloadStore(context).all()
                    showSettings = false
                    showDownloads = true
                },
            )

            activeSite == null -> HomeScreen(
                sites = sites,
                iconVersion = iconVersion,
                permissionMissing = PermissionPlanner.pending(Build.VERSION.SDK_INT, granted).size,
                onOpenSite = { site ->
                    openPlan = OpenTargetPlanner.plan(
                        site = site,
                        preferIpv6 = settings.preferIpv6,
                        ipv6Available = Ipv6.isAvailableOnDevice(),
                    )
                    openSiteId = site.id
                },
                onAddSite = { site ->
                    val next = SiteOps.add(sites, site)
                    store.save(next)
                    sites = next
                },
                onEditSite = { site ->
                    val next = SiteOps.update(sites, site)
                    store.save(next)
                    sites = next
                },
                onDeleteSite = { site ->
                    val next = SiteOps.remove(sites, site.id)
                    store.save(next)
                    sites = next
                    runCatching { IconStore(context).delete(site.id) }
                },
                onMoveTop = { site ->
                    val next = SiteOps.moveToTop(sites, site.id)
                    store.save(next)
                    sites = next
                },
                onMoveUp = { site ->
                    val next = SiteOps.moveUp(sites, site.id)
                    store.save(next)
                    sites = next
                },
                onMoveDown = { site ->
                    val next = SiteOps.moveDown(sites, site.id)
                    store.save(next)
                    sites = next
                },
                onRefreshIcon = { site ->
                    runCatching { IconStore(context).delete(site.id) }
                    iconFetchAttempted.remove(site.id)
                    iconVersion++
                    toast("已重新抓取图标")
                },
                onClearSiteData = { site ->
                    val failures = SiteDataCleaner.clearSite(
                        context = context,
                        site = site,
                        currentUrl = null,
                        webView = null,
                    )
                    val removed = SiteDataCleaner.clearPasswords(context, site)
                    val message = when {
                        failures.isNotEmpty() -> "部分数据未能清除：${failures.joinToString("、")}"
                        removed > 0 -> "已清除「${site.title}」的数据与 $removed 条密码"
                        else -> "已清除「${site.title}」的数据"
                    }
                    toast(message)
                    // 图标不用清：它只是展示用途，清掉反而让卡片变回顾色块
                },
                onCopyLink = { site ->
                    clipboard?.setPrimaryClip(ClipData.newPlainText("LinkGate", site.url))
                    toast("已复制链接")
                },
                onOpenPermissions = {
                    granted = RuntimePermissions.granted(context)
                    showPermissionDialog = true
                },
                onOpenPasswordManager = {
                    passwordEntries = PasswordStore(context).all()
                    showPasswordManager = true
                },
                onOpenDownloads = {
                    DownloadCenter.syncFromDownloadManager(context)
                    downloadRecords = DownloadStore(context).all()
                    showDownloads = true
                },
                onOpenSettings = {
                    granted = RuntimePermissions.granted(context)
                    showSettings = true
                },
                onExit = onExit,
            )

            else -> {
                val plan = remember(activeSite.id, settings.preferIpv6, openPlan) {
                    openPlan ?: OpenTargetPlanner.plan(activeSite, settings.preferIpv6, Ipv6.isAvailableOnDevice())
                }
                WebScreen(
                    site = activeSite,
                    plan = plan,
                    settings = settings,
                    dark = darkTheme,
                    onClose = {
                        openSiteId = null
                        openPlan = null
                    },
                )
            }
        }

        pendingDownload?.let { record ->
            AlertDialog(
                onDismissRequest = { pendingDownload = null },
                title = { Text("下载完成") },
                text = {
                    Column {
                        Text(record.name, style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = record.summary(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("要现在打开吗？", style = MaterialTheme.typography.bodyMedium)
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        pendingDownload = null
                        if (!DownloadCenter.open(context, record)) {
                            toast("没有能打开该文件的应用")
                        }
                    }) { Text("打开") }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = {
                            pendingDownload = null
                            showDownloads = true
                        }) { Text("下载管理") }
                        TextButton(onClick = { pendingDownload = null }) {
                            Text("稍后")
                        }
                    }
                },
            )
        }

        if (showPasswordManager) {
            PasswordManagerDialog(
                entries = passwordEntries,
                onDismiss = { showPasswordManager = false },
                onDelete = { entry ->
                    PasswordStore(context).remove(entry.origin, entry.username)
                    passwordEntries = PasswordStore(context).all()
                },
            )
        }

        if (showPermissionDialog) {
            PermissionDialog(
                items = PermissionPlanner.items(Build.VERSION.SDK_INT),
                granted = granted,
                onDismiss = { showPermissionDialog = false },
                onRequest = { permissions ->
                    showPermissionDialog = false
                    requestPermissions(permissions)
                },
                onOpenSettings = {
                    showPermissionDialog = false
                    RuntimePermissions.openAppSettings(context)
                },
            )
        }
    }
}

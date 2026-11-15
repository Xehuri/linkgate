package com.xehuri.linkgate.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.xehuri.linkgate.data.AppSettings
import com.xehuri.linkgate.data.IconStore
import com.xehuri.linkgate.data.PasswordEntry
import com.xehuri.linkgate.data.PasswordStore
import com.xehuri.linkgate.data.Site
import com.xehuri.linkgate.nav.BackAction
import com.xehuri.linkgate.nav.BackPlanner
import com.xehuri.linkgate.nav.OpenTargetPlanner
import com.xehuri.linkgate.web.ExternalLinks
import com.xehuri.linkgate.web.DownloadSaver
import com.xehuri.linkgate.web.SiteDataCleaner
import com.xehuri.linkgate.web.WebHost
import com.xehuri.linkgate.web.WebViewFactory
import kotlinx.coroutines.delay

/** 每个进程只提示一次「双指连点呼出控制条」 */
private var hintShown = false

/** 渲染进程崩溃后最多自动重建几次，超过就交给用户决定 */
private const val MAX_AUTO_RECOVER = 2

/**
 * 网页界面：沉浸式全屏，网页铺满整块屏幕（不保留 App 顶栏，隐藏系统状态栏与导航栏）。
 *
 * 悬浮控制条默认隐藏（不遮挡网页），按设置需要连续 N 次双指轻点才呼出（默认 2 次），
 * 4.5 秒无操作自动收起。控制条里的按钮：返回 / 前进 / 刷新 / 站点列表 / 更多。
 * 单指操作完全留给网页，返回键由系统手势或控制条触发。
 *
 * 返回键语义见 [BackPlanner]：入口落地页 → 回站点列表；否则回网页上一级。
 */
@Composable
fun WebScreen(
    site: Site,
    plan: OpenTargetPlanner.Plan,
    settings: AppSettings,
    dark: Boolean,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val composeView = LocalView.current
    val activity = context as? Activity
    val host = remember(site.id, plan.url) {
        WebHost(
            appContext = context.applicationContext,
            siteId = site.id,
            homeUrl = plan.url,
            primaryUrl = site.url,
            fallbackUrl = plan.fallbackUrl,
        )
    }

    var progress by remember { mutableIntStateOf(0) }
    var pageTitle by remember { mutableStateOf(site.title) }
    var currentUrl by remember { mutableStateOf(plan.url) }
    var pageError by remember { mutableStateOf<String?>(null) }
    var controlsVisible by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var systemBarsVisible by remember { mutableStateOf(false) }
    var pendingPasswordSave by remember { mutableStateOf<PasswordEntry?>(null) }
    var showClearSiteData by remember { mutableStateOf(false) }
    // 渲染进程崩溃后 +1，用 key() 强制重建 WebView 实例
    var webViewGeneration by remember { mutableIntStateOf(0) }
    var crashCount by remember { mutableIntStateOf(0) }
    // 已应用过的设置签名：避免每次进度刷新都重复写一遍 WebSettings
    var appliedSignature by remember(webViewGeneration) { mutableStateOf<Any?>(null) }

    val wakeTaps = settings.sanitized().wakeTapCount

    // 进入网页界面即隐藏系统栏：真正铺满屏幕
    DisposableEffect(Unit) {
        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, composeView) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }
    LaunchedEffect(systemBarsVisible) {
        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, composeView) }
            ?: return@LaunchedEffect
        if (systemBarsVisible) controller.show(WindowInsetsCompat.Type.systemBars())
        else controller.hide(WindowInsetsCompat.Type.systemBars())
    }
    // 退到后台时暂停页面的 JS 定时器与媒体，避免后台耗电、发热、外放
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            val webView = host.webView ?: return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    runCatching {
                        webView.onPause()
                        webView.pauseTimers()
                    }
                }
                Lifecycle.Event.ON_RESUME -> {
                    runCatching {
                        webView.onResume()
                        webView.resumeTimers()
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            runCatching { host.webView?.resumeTimers() }
        }
    }

    // 控制条 4.5 秒无操作自动收起；「更多」菜单展开期间暂停计时，避免菜单被一起吞掉
    LaunchedEffect(controlsVisible, menuExpanded) {
        if (controlsVisible && !menuExpanded) {
            delay(4_500)
            controlsVisible = false
        }
    }
    LaunchedEffect(Unit) {
        if (!hintShown) {
            hintShown = true
            val gesture = if (wakeTaps <= 1) "双指轻点屏幕" else "连续 ${wakeTaps} 次双指轻点屏幕"
            Toast.makeText(context, "$gesture，可呼出控制条（返回 / 刷新 / 列表）", Toast.LENGTH_LONG).show()
        }
    }

    // 网页 <input type=file> 的文件选择器
    val fileChooserLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val callback = host.fileChooserCallback
        host.fileChooserCallback = null
        if (callback == null) return@rememberLauncherForActivityResult
        if (result.resultCode != Activity.RESULT_OK) {
            callback.onReceiveValue(null)
            return@rememberLauncherForActivityResult
        }
        val data = result.data
        if (data == null) {
            callback.onReceiveValue(null)
            return@rememberLauncherForActivityResult
        }
        val uris = ArrayList<Uri>()
        data.data?.let { uris.add(it) }
        data.clipData?.let { clip ->
            for (index in 0 until clip.itemCount) uris.add(clip.getItemAt(index).uri)
        }
        callback.onReceiveValue(if (uris.isEmpty()) null else uris.toTypedArray())
    }

    SideEffect {
        host.onProgress = { value -> progress = value }
        host.onTitle = { title -> if (!title.isNullOrBlank()) pageTitle = title }
        host.onUrl = { url -> if (!url.isNullOrBlank()) currentUrl = url }
        host.onPageError = { message ->
            // message 为 null 表示「开始加载新页面，清掉之前的错误」，绝不能当成加载失败
            if (message == null) {
                pageError = null
            } else {
                // 用 IPv6 备用地址真的打不开时，自动回退到主地址（每个站点只回退一次）
                val fallback = host.takeFallbackUrl()
                if (fallback != null) {
                    android.util.Log.w("LinkGateWeb", "IPv6 回退：$message → $fallback")
                    Toast.makeText(context, "IPv6 地址打不开，已改用主地址", Toast.LENGTH_LONG).show()
                    pageError = null
                    host.webView?.loadUrl(fallback)
                } else {
                    pageError = message
                }
            }
        }
        host.onNotice = { message -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
        host.onPermissionHint = { missing ->
            Toast.makeText(
                context,
                "网页需要的权限还没授权：${missing.joinToString("、")}。请回列表进入设置授权。",
                Toast.LENGTH_LONG,
            ).show()
        }
        host.onWakeGesture = { controlsVisible = true }
        host.onRenderCrash = {
            if (crashCount < MAX_AUTO_RECOVER) {
                crashCount++
                pageError = null
                Toast.makeText(context, "网页渲染进程崩溃，已自动重建页面", Toast.LENGTH_SHORT).show()
                webViewGeneration++
            } else {
                pageError = "网页渲染进程反复崩溃，已停止自动重试。可以重试或换个站点。"
            }
        }
        host.onPasswordSaveRequest = { entry -> pendingPasswordSave = entry }
        host.onPasswordNotice = { message -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
        host.onIcon = { icon ->
            // 存盘是 IO，放到后台线程；回列表时会刷新图标版本
            val iconStore = IconStore(context)
            Thread { iconStore.save(site.id, icon) }.start()
        }
        host.chooserLauncher = { intent -> fileChooserLauncher.launch(intent) }
    }

    fun handleBack() {
        val action = BackPlanner.inWebScreen(
            canGoBackInHistory = host.webView?.canGoBack() == true,
            currentUrl = host.currentUrl ?: currentUrl,
            landingUrl = host.landingUrl,
        )
        when (action) {
            BackAction.WEB_GO_BACK -> host.webView?.goBack()
            BackAction.CLOSE_WEB_SCREEN -> onClose()
        }
    }

    BackHandler { handleBack() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // edge-to-edge 下 adjustResize 不生效，必须自己处理 IME insets，
            // 否则网页里的输入框与登录按钮会被软键盘挡住。
            .imePadding(),
    ) {
        key(webViewGeneration) {
            AndroidView(
                factory = { ctx -> WebViewFactory.create(ctx, host, settings) },
                update = { webView ->
                    val signature = settings to dark
                    if (appliedSignature != signature) {
                        appliedSignature = signature
                        WebViewFactory.applySettings(webView, settings)
                        WebViewFactory.applyDarkMode(webView, settings.darkMode, dark)
                    }
                },
                onRelease = { webView ->
                    host.webView = null
                    webView.stopLoading()
                    webView.loadUrl("about:blank")
                    webView.destroy()
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (progress in 1..99) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.fillMaxWidth().height(2.dp).align(Alignment.TopCenter),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Transparent,
            )
        }

        pageError?.let { message ->
            ErrorPane(
                message = message,
                url = host.currentUrl ?: currentUrl,
                onRetry = {
                    pageError = null
                    host.webView?.reload()
                },
                onCopyLink = {
                    val url = host.currentUrl ?: currentUrl
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("LinkGate", url))
                    Toast.makeText(context, "已复制链接", Toast.LENGTH_SHORT).show()
                },
                onOpenBrowser = {
                    val url = host.currentUrl ?: currentUrl
                    if (!ExternalLinks.openInBrowser(context, url)) {
                        Toast.makeText(context, "没有找到可用的浏览器", Toast.LENGTH_SHORT).show()
                    }
                },
                onClose = onClose,
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (controlsVisible && pageError == null) {
            // 点空白处收起控制条；网页本身此时不接收点击，避免误触
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { controlsVisible = false }
                    .clearAndSetSemantics { },
            )
            Surface(
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 30.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.62f),
                contentColor = Color.White,
                shadowElevation = 8.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ControlButton(Icons.AutoMirrored.Filled.ArrowBack, "返回") {
                        controlsVisible = false
                        handleBack()
                    }
                    ControlButton(Icons.AutoMirrored.Filled.ArrowForward, "前进") {
                        if (host.webView?.canGoForward() == true) host.webView?.goForward()
                    }
                    ControlButton(Icons.Filled.Refresh, "刷新") {
                        controlsVisible = false
                        pageError = null
                        host.webView?.reload()
                    }
                    ControlButton(Icons.AutoMirrored.Filled.List, "站点列表") {
                        controlsVisible = false
                        onClose()
                    }
                    Box {
                        ControlButton(Icons.Filled.MoreVert, "更多") { menuExpanded = true }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (systemBarsVisible) "隐藏系统栏" else "显示系统栏") },
                                onClick = {
                                    menuExpanded = false
                                    controlsVisible = false
                                    systemBarsVisible = !systemBarsVisible
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("复制当前链接") },
                                onClick = {
                                    menuExpanded = false
                                    controlsVisible = false
                                    val url = host.currentUrl ?: currentUrl
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("LinkGate", url))
                                    Toast.makeText(context, "已复制当前链接", Toast.LENGTH_SHORT).show()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("清除本站数据") },
                                onClick = {
                                    menuExpanded = false
                                    controlsVisible = false
                                    showClearSiteData = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("打开下载目录") },
                                onClick = {
                                    menuExpanded = false
                                    controlsVisible = false
                                    if (!DownloadSaver.openDownloadsUi(context)) {
                                        Toast.makeText(context, "没有找到可用的文件管理器", Toast.LENGTH_SHORT).show()
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("用系统浏览器打开") },
                                onClick = {
                                    menuExpanded = false
                                    controlsVisible = false
                                    val url = host.currentUrl ?: currentUrl
                                    if (!ExternalLinks.openInBrowser(context, url)) {
                                        Toast.makeText(context, "没有找到可用的浏览器", Toast.LENGTH_SHORT).show()
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showClearSiteData) {
        AlertDialog(
            onDismissRequest = { showClearSiteData = false },
            title = { Text("清除「${site.title}」的站点数据？") },
            text = {
                Text(
                    "会清除该站点的 Cookie、本地存储、缓存以及已保存的密码，" +
                        "之后需要重新登录。站点本身仍保留在列表里。",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showClearSiteData = false
                    val failures = SiteDataCleaner.clearSite(
                        context = context,
                        site = site,
                        currentUrl = host.currentUrl ?: currentUrl,
                        webView = host.webView,
                    )
                    val removedPasswords = SiteDataCleaner.clearPasswords(context, site)
                    val message = when {
                        failures.isNotEmpty() -> "部分数据未能清除：${failures.joinToString("、")}"
                        removedPasswords > 0 -> "已清除站点数据与 $removedPasswords 条密码"
                        else -> "已清除站点数据"
                    }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    host.webView?.reload()
                }) { Text("清除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearSiteData = false }) { Text("取消") }
            },
        )
    }

    pendingPasswordSave?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingPasswordSave = null },
            title = { Text("保存这个密码？") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("网站：${entry.origin}", style = MaterialTheme.typography.bodySmall)
                    Text("用户名：${entry.username.ifBlank { "(未填写)" }}")
                    Text(
                        "密码会加密保存在本机，只在 ${entry.origin} 下自动填充。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val ok = runCatching { PasswordStore(context).save(entry) }.isSuccess
                    Toast.makeText(context, if (ok) "密码已保存" else "保存失败", Toast.LENGTH_SHORT).show()
                    pendingPasswordSave = null
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { pendingPasswordSave = null }) { Text("不保存") }
            },
        )
    }
}

@Composable
private fun ControlButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = label, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun ErrorPane(
    message: String,
    url: String,
    onRetry: () -> Unit,
    onCopyLink: () -> Unit,
    onOpenBrowser: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(40.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text("网页没能打开", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (url.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onRetry) { Text("重试") }
                OutlinedButton(onClick = onClose) { Text("回到站点列表") }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onCopyLink) { Text("复制链接") }
                TextButton(onClick = onOpenBrowser) { Text("用浏览器打开") }
            }
        }
    }
}

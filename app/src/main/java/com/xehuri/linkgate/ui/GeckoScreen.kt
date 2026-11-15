package com.xehuri.linkgate.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.xehuri.linkgate.data.AppSettings
import com.xehuri.linkgate.data.Site
import com.xehuri.linkgate.nav.BackAction
import com.xehuri.linkgate.nav.BackPlanner
import com.xehuri.linkgate.web.DownloadSaver
import com.xehuri.linkgate.web.ExternalLinks
import com.xehuri.linkgate.web.GeckoEngine
import com.xehuri.linkgate.web.TwoFingerTapDetector
import org.mozilla.geckoview.GeckoView
import kotlinx.coroutines.delay

/** 每个进程只提示一次「双指连点呼出控制条」 */
private var geckoHintShown = false

/**
 * 内置 Gecko 内核的网页界面。
 *
 * 与 [WebScreen]（系统 WebView 版）保持同一套交互：沉浸全屏、控制条默认隐藏、
 * 连续 N 次双指轻点唤出、返回键按 [BackPlanner] 逐级回退。
 *
 * 差异：内核由 App 自带（GeckoView），因此不受系统 WebView 版本限制；Cookie 存在 Gecko 自己的
 * profile 里，每个站点首次进入需要重新登录。
 */
@Composable
fun GeckoScreen(
    site: Site,
    settings: AppSettings,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val composeView = LocalView.current
    val activity = context as? Activity
    val wakeTaps = settings.sanitized().wakeTapCount

    var progress by remember { mutableIntStateOf(0) }
    var pageTitle by remember { mutableStateOf(site.title) }
    var currentUrl by remember { mutableStateOf(site.url) }
    var landingUrl by remember { mutableStateOf<String?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var crashed by remember { mutableStateOf(false) }

    val hooks = remember(site.id) {
        GeckoEngine.Hooks(
            onProgress = { value -> progress = value },
            onTitle = { title -> if (!title.isNullOrBlank()) pageTitle = title },
            onUrl = { url -> if (!url.isNullOrBlank()) currentUrl = url },
            onCanGoBack = { value -> canGoBack = value },
            onLoaded = { url ->
                if (!url.isNullOrBlank()) {
                    currentUrl = url
                    if (landingUrl == null) landingUrl = url
                }
            },
            onNotice = { message -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show() },
            onCrash = { crashed = true },
        )
    }

    val session = remember(site.id, settings.viewMode, settings.userAgentMode) {
        GeckoEngine.createSession(context, settings, hooks).apply { loadUri(site.url) }
    }
    val tapDetector = remember(session, wakeTaps) {
        TwoFingerTapDetector(
            requiredTaps = wakeTaps,
            onTriggered = { controlsVisible = true },
        )
    }

    DisposableEffect(Unit) {
        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, composeView) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
    LaunchedEffect(controlsVisible, menuExpanded) {
        if (controlsVisible && !menuExpanded) {
            delay(4_500)
            controlsVisible = false
        }
    }
    LaunchedEffect(Unit) {
        if (!geckoHintShown) {
            geckoHintShown = true
            val gesture = if (wakeTaps <= 1) "双指轻点屏幕" else "连续 $wakeTaps 次双指轻点屏幕"
            Toast.makeText(context, "$gesture，可呼出控制条", Toast.LENGTH_LONG).show()
        }
    }
    DisposableEffect(session) {
        onDispose {
            runCatching { session.close() }
        }
    }

    fun handleBack() {
        val action = BackPlanner.inWebScreen(
            canGoBackInHistory = canGoBack,
            currentUrl = currentUrl,
            landingUrl = landingUrl ?: site.url,
        )
        when (action) {
            BackAction.WEB_GO_BACK -> session.goBack()
            BackAction.CLOSE_WEB_SCREEN -> onClose()
        }
    }

    BackHandler { handleBack() }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AndroidView(
            factory = { ctx ->
                GeckoView(ctx).apply {
                    setSession(session)
                    setOnTouchListener { view, event -> tapDetector.onTouch(view, event) }
                }
            },
            onRelease = { view ->
                runCatching { view.releaseSession() }
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (progress in 1..99) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.fillMaxWidth().height(2.dp).align(Alignment.TopCenter),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Transparent,
            )
        }

        if (crashed) {
            ErrorPane(
                message = "内置内核的渲染进程崩溃了，可以重试或换个内核打开。",
                onRetry = {
                    crashed = false
                    session.reload()
                },
                onClose = onClose,
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (controlsVisible && !crashed) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { controlsVisible = false },
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
                    ControlButton(Icons.Filled.Refresh, "刷新") {
                        controlsVisible = false
                        session.reload()
                    }
                    ControlButton(Icons.AutoMirrored.Filled.List, "站点列表") {
                        controlsVisible = false
                        onClose()
                    }
                    Box {
                        ControlButton(Icons.Filled.MoreVert, "更多") { menuExpanded = true }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("复制当前链接") },
                                onClick = {
                                    menuExpanded = false
                                    controlsVisible = false
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("LinkGate", currentUrl))
                                    Toast.makeText(context, "已复制当前链接", Toast.LENGTH_SHORT).show()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("用系统浏览器打开") },
                                onClick = {
                                    menuExpanded = false
                                    controlsVisible = false
                                    if (!ExternalLinks.openInBrowser(context, currentUrl)) {
                                        Toast.makeText(context, "没有找到可用的浏览器", Toast.LENGTH_SHORT).show()
                                    }
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
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ControlButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(48.dp).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = label, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun ErrorPane(
    message: String,
    onRetry: () -> Unit,
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
            Spacer(modifier = Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onRetry) { Text("重试") }
                OutlinedButton(onClick = onClose) { Text("回到站点列表") }
            }
        }
    }
}

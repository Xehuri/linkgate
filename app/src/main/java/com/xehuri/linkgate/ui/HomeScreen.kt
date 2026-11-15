package com.xehuri.linkgate.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import com.xehuri.linkgate.R
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.xehuri.linkgate.data.Site
import kotlin.math.roundToInt

/** 长按浮窗的目标：哪个站点 + 手指在根坐标系里的位置（浮窗就弹在这里） */
private data class CardMenuTarget(val site: Site, val anchor: Offset)

/**
 * 站点列表首屏：网格展示、搜索过滤、长按卡片在**手指位置**弹出操作浮窗、
 * 右下角添加按钮。在这个界面按返回键 = 退出应用。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    sites: List<Site>,
    iconVersion: Int,
    permissionMissing: Int,
    onOpenSite: (Site) -> Unit,
    onAddSite: (Site) -> Unit,
    onEditSite: (Site) -> Unit,
    onDeleteSite: (Site) -> Unit,
    onMoveTop: (Site) -> Unit,
    onMoveUp: (Site) -> Unit,
    onMoveDown: (Site) -> Unit,
    onRefreshIcon: (Site) -> Unit,
    onCopyLink: (Site) -> Unit,
    onClearSiteData: (Site) -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenPasswordManager: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenSettings: () -> Unit,
    onExit: () -> Unit,
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingSite by remember { mutableStateOf<Site?>(null) }
    var deletingSite by remember { mutableStateOf<Site?>(null) }
    var clearingSite by remember { mutableStateOf<Site?>(null) }
    var menuTarget by remember { mutableStateOf<CardMenuTarget?>(null) }
    var query by remember { mutableStateOf("") }

    BackHandler { onExit() }

    // 站点多的时候才显示搜索框，少的时候留白更清爽
    val showSearch = sites.size >= 6
    val visibleSites = remember(sites, query) {
        val keyword = query.trim()
        if (keyword.isEmpty()) {
            sites
        } else {
            sites.filter { site ->
                site.title.contains(keyword, ignoreCase = true) ||
                    site.description.contains(keyword, ignoreCase = true) ||
                    site.url.contains(keyword, ignoreCase = true)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "LinkGate",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                actions = {
                    IconButton(onClick = onOpenDownloads) {
                        Icon(
                            painter = painterResource(R.drawable.ic_download),
                            contentDescription = "下载管理",
                        )
                    }
                    IconButton(onClick = onOpenPasswordManager) {
                        Icon(Icons.Filled.Person, contentDescription = "已保存的密码")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "设置")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("添加网页") },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (permissionMissing > 0) {
                PermissionBanner(missing = permissionMissing, onClick = onOpenPermissions)
            }
            if (showSearch) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("搜索站点") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Filled.Clear, contentDescription = "清空搜索")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Search,
                    ),
                    keyboardActions = KeyboardActions(onSearch = {}),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            when {
                sites.isEmpty() -> EmptyState(onAdd = { showAddDialog = true }, modifier = Modifier.weight(1f))
                visibleSites.isEmpty() -> NoMatchState(modifier = Modifier.weight(1f))
                else -> BoxWithConstraints(modifier = Modifier.weight(1f)) {
                    // 首屏自适应：手机 2 列，平板/横屏按可用宽度增加列数，避免大屏上卡片被压得过小
                    val horizontalPadding = if (maxWidth >= 820.dp) 28.dp else 16.dp
                    val usableWidth = maxWidth - horizontalPadding * 2
                    val columns = (usableWidth.value / 220f).toInt().coerceIn(2, 6)
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = horizontalPadding,
                            top = 8.dp,
                            end = horizontalPadding,
                            bottom = 96.dp,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(visibleSites, key = { it.id }) { site ->
                            SiteCard(
                                site = site,
                                iconVersion = iconVersion,
                                onOpen = { onOpenSite(site) },
                                onLongPress = { anchor -> menuTarget = CardMenuTarget(site, anchor) },
                            )
                        }
                    }
                }
            }
        }
    }

    // 长按浮窗：整屏只保留一个，弹在手指按下的位置（超出屏幕时自动往里收）
    menuTarget?.let { target ->
        SiteCardMenu(
            target = target,
            onDismiss = { menuTarget = null },
            actions = listOf(
                MenuAction("置顶", Icons.Filled.Star) { onMoveTop(target.site) },
                MenuAction("上移", Icons.Filled.KeyboardArrowUp) { onMoveUp(target.site) },
                MenuAction("下移", Icons.Filled.KeyboardArrowDown) { onMoveDown(target.site) },
                MenuAction("编辑", Icons.Filled.Edit) { editingSite = target.site },
                MenuAction("复制链接", Icons.Filled.Share) { onCopyLink(target.site) },
                MenuAction("刷新图标", Icons.Filled.Refresh) { onRefreshIcon(target.site) },
                MenuAction("清除本站数据", Icons.Filled.Clear) { clearingSite = target.site },
                MenuAction("删除", Icons.Filled.Delete) { deletingSite = target.site },
            ),
        )
    }

    if (showAddDialog) {
        AddSiteDialog(
            initial = null,
            onDismiss = { showAddDialog = false },
            onConfirm = { site ->
                showAddDialog = false
                onAddSite(site)
            },
        )
    }

    editingSite?.let { site ->
        AddSiteDialog(
            initial = site,
            onDismiss = { editingSite = null },
            onConfirm = { updated ->
                editingSite = null
                onEditSite(updated)
            },
        )
    }

    clearingSite?.let { site ->
        ClearSiteDataDialog(
            site = site,
            onDismiss = { clearingSite = null },
            onConfirm = {
                clearingSite = null
                onClearSiteData(site)
            },
        )
    }

    deletingSite?.let { site ->
        DeleteSiteDialog(
            site = site,
            onDismiss = { deletingSite = null },
            onConfirm = {
                deletingSite = null
                onDeleteSite(site)
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SiteCard(
    site: Site,
    iconVersion: Int,
    onOpen: () -> Unit,
    onLongPress: (Offset) -> Unit,
) {
    val subtitle = remember(site.url, site.description) { site.subtitleText() }
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var pressPosition by remember { mutableStateOf(Offset.Zero) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 92.dp)
            .onGloballyPositioned { coordinates = it }
            // 只观测按下位置，不消费事件：点击/长按仍由 combinedClickable 处理（保留水波纹）
            .recordPressPosition { position -> pressPosition = position }
            .combinedClickable(
                onClick = onOpen,
                onLongClick = {
                    // 用「窗口坐标」而不是 composition 根坐标：Popup 的 offset 就是相对窗口原点的，
                    // 两者混用会让浮窗整体偏上/偏下（状态栏高度那一点点）。
                    val anchor = coordinates?.localToWindow(pressPosition) ?: pressPosition
                    onLongPress(anchor)
                },
            ),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 网站 favicon；拿不到则首字母色块兜底
            SiteIcon(site = site, iconVersion = iconVersion, size = 30.dp)
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = site.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    maxLines = if (site.description.isBlank()) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (site.hasIpv6) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "IPv6",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

/** 按下位置观测器：Initial 阶段不消费事件，只记录触点 */
private fun Modifier.recordPressPosition(onPress: (Offset) -> Unit): Modifier =
    this.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type == PointerEventType.Press) {
                    event.changes.firstOrNull()?.let { onPress(it.position) }
                }
            }
        }
    }

private data class MenuAction(val label: String, val icon: ImageVector, val onClick: () -> Unit)

/**
 * 长按操作浮窗：以手指位置为左上角弹出（和 Chrome 的长按菜单一致），
 * 靠近屏幕边缘时自动往里收，保证完整可见。
 */
@Composable
private fun SiteCardMenu(
    target: CardMenuTarget,
    onDismiss: () -> Unit,
    actions: List<MenuAction>,
) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val menuWidth = 196.dp
    val itemHeight = 44.dp
    val padding = 4.dp
    val estimatedHeight = itemHeight * actions.size + padding * 2

    // 屏幕像素尺寸：只用于「别把浮窗顶出屏幕」的裁剪
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.roundToPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.roundToPx() }
    val menuWidthPx = with(density) { menuWidth.roundToPx() }
    val menuHeightPx = with(density) { estimatedHeight.roundToPx() }
    val marginPx = with(density) { 8.dp.roundToPx() }

    // 和 Chrome 一样：默认以触点作为浮窗左上角；右边/下面放不下时翻到左侧/上方，
    // 让浮窗始终「贴着手」，而不是被挤到屏幕角落。
    val anchorX = target.anchor.x.roundToInt()
    val anchorY = target.anchor.y.roundToInt()
    val maxX = (screenWidthPx - menuWidthPx - marginPx).coerceAtLeast(marginPx)
    val maxY = (screenHeightPx - menuHeightPx - marginPx).coerceAtLeast(marginPx)
    var x = anchorX
    if (x + menuWidthPx + marginPx > screenWidthPx) x = anchorX - menuWidthPx
    x = x.coerceIn(marginPx, maxX)
    var y = anchorY
    if (y + menuHeightPx + marginPx > screenHeightPx) y = anchorY - menuHeightPx
    y = y.coerceIn(marginPx, maxY)

    android.util.Log.d(
        "LinkGateMenu",
        "anchor=${target.anchor} screen=${screenWidthPx}x${screenHeightPx} menu=${menuWidthPx}x${menuHeightPx} -> ($x, $y)",
    )
    Popup(
        offset = IntOffset(x, y),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Surface(
            modifier = Modifier.width(menuWidth),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            shadowElevation = 10.dp,
        ) {
            Column(modifier = Modifier.padding(vertical = padding)) {
                actions.forEach { action ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onDismiss()
                                action.onClick()
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = action.icon,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = if (action.label == "删除") MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = action.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (action.label == "删除") MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionBanner(missing: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "还有 $missing 项浏览器权限未授权，点此批量申请",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun ClearSiteDataDialog(
    site: Site,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("清除「${site.title}」的数据？") },
        text = {
            Text(
                "会清除该站点的 Cookie、本地存储、缓存与已保存的密码，之后需要重新登录。" +
                    "站点本身仍保留在列表里。",
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("清除", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun NoMatchState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("没有匹配的站点", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "换个关键词试试，或清空搜索框。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyState(onAdd: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.List,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(34.dp),
            )
        }
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = "还没有网页",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "添加一个网址，之后打开 LinkGate 点一下就能直达。\n长按卡片可以置顶、上移、编辑或删除。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onAdd, shape = RoundedCornerShape(14.dp)) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("添加第一个网页")
        }
    }
}

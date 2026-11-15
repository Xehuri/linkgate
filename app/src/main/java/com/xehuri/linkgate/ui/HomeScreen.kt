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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xehuri.linkgate.data.Site
import com.xehuri.linkgate.data.UrlNormalizer

/**
 * 站点列表首屏：网格展示、搜索过滤、长按卡片操作（置顶/上移/编辑/复制链接/刷新图标/删除）、
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
    onOpenPermissions: () -> Unit,
    onOpenPasswordManager: () -> Unit,
    onOpenSettings: () -> Unit,
    onExit: () -> Unit,
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingSite by remember { mutableStateOf<Site?>(null) }
    var deletingSite by remember { mutableStateOf<Site?>(null) }
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
                                onMoveTop = { onMoveTop(site) },
                                onMoveUp = { onMoveUp(site) },
                                onMoveDown = { onMoveDown(site) },
                                onEdit = { editingSite = site },
                                onCopyLink = { onCopyLink(site) },
                                onRefreshIcon = { onRefreshIcon(site) },
                                onDelete = { deletingSite = site },
                            )
                        }
                    }
                }
            }
        }
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
    onMoveTop: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onEdit: () -> Unit,
    onCopyLink: () -> Unit,
    onRefreshIcon: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val subtitle = remember(site.url, site.description) { site.subtitleText() }
    val host = remember(site.url) { UrlNormalizer.titleFromUrl(site.url) }

    Box {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 92.dp)
                .combinedClickable(onClick = onOpen, onLongClick = { menuOpen = true }),
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

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text("置顶") },
                leadingIcon = { Icon(Icons.Filled.Star, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    onMoveTop()
                },
            )
            DropdownMenuItem(
                text = { Text("上移") },
                leadingIcon = { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    onMoveUp()
                },
            )
            DropdownMenuItem(
                text = { Text("下移") },
                leadingIcon = { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    onMoveDown()
                },
            )
            DropdownMenuItem(
                text = { Text("编辑") },
                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    onEdit()
                },
            )
            DropdownMenuItem(
                text = { Text("复制链接") },
                leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    onCopyLink()
                },
            )
            DropdownMenuItem(
                text = { Text("刷新图标") },
                leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    onRefreshIcon()
                },
            )
            DropdownMenuItem(
                text = { Text("删除") },
                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    onDelete()
                },
            )
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

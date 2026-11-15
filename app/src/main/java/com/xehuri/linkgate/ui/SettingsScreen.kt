package com.xehuri.linkgate.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.xehuri.linkgate.data.AppSettings
import com.xehuri.linkgate.data.CacheMode
import com.xehuri.linkgate.data.DarkModeSetting
import com.xehuri.linkgate.data.MixedContentLevel
import com.xehuri.linkgate.data.UserAgentMode
import com.xehuri.linkgate.data.ViewMode
import com.xehuri.linkgate.web.UserAgents

/**
 * 设置界面：网络 / 手势 / 网页 / 渲染 / 浏览器标识 / 数据 / 系统 / 关于。
 *
 * 全部是「本地生效」的开关，改完立即写盘，不需要保存按钮；返回键回站点列表。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onChange: (AppSettings) -> Unit,
    onBack: () -> Unit,
    deviceIpv6Available: Boolean,
    deviceIpv6Address: String?,
    webViewVersion: String,
    defaultUserAgent: String,
    appVersion: String,
    siteCount: Int,
    onOpenSystemSettings: () -> Unit,
    onOpenPermissions: () -> Unit,
    onClearCache: () -> Unit,
    onClearWebData: () -> Unit,
    onExportSites: () -> Unit,
    onImportSites: () -> Unit,
) {
    BackHandler { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 4.dp,
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ---------------- 网络 ----------------
            item { SectionTitle("网络") }
            item {
                SettingsCard {
                    SwitchRow(
                        title = "IPv6 优先",
                        summary = "站点填了 IPv6 地址时，优先走 IPv6 直连；打不开会自动改用主地址",
                        checked = settings.preferIpv6,
                        onCheckedChange = { onChange(settings.copy(preferIpv6 = it)) },
                    )
                    Divider()
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                        Text("本机 IPv6 状态", style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = when {
                                deviceIpv6Available && deviceIpv6Address != null -> "可用：$deviceIpv6Address"
                                deviceIpv6Available -> "可用"
                                else -> "当前网络没有 IPv6"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // ---------------- 手势 ----------------
            item { SectionTitle("手势") }
            item {
                SettingsCard {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                        Text("悬浮控制条唤醒方式", style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "需要连续几次双指轻点才呼出控制条。次数越多越不容易误触。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (taps in AppSettings.MIN_WAKE_TAPS..AppSettings.MAX_WAKE_TAPS) {
                                ChoiceChip(
                                    text = "双指连点 $taps 次",
                                    selected = settings.wakeTapCount == taps,
                                    onClick = { onChange(settings.copy(wakeTapCount = taps)) },
                                )
                            }
                        }
                    }
                }
            }

            // ---------------- 网页 ----------------
            item { SectionTitle("网页") }
            item {
                SettingsCard {
                    SwitchRow(
                        title = "允许双指缩放",
                        summary = "关闭后网页不能用捏合手势放大缩小（单指滚动不受影响）",
                        checked = settings.zoomEnabled,
                        onCheckedChange = { onChange(settings.copy(zoomEnabled = it)) },
                    )
                    Divider()
                    SwitchRow(
                        title = "JavaScript",
                        summary = "关掉后大部分现代网页无法正常显示",
                        checked = settings.javaScriptEnabled,
                        onCheckedChange = { onChange(settings.copy(javaScriptEnabled = it)) },
                    )
                    Divider()
                    SwitchRow(
                        title = "DOM 存储",
                        summary = "本地存储、IndexedDB 依赖它，关掉会丢失登录态",
                        checked = settings.domStorageEnabled,
                        onCheckedChange = { onChange(settings.copy(domStorageEnabled = it)) },
                    )
                    Divider()
                    SwitchRow(
                        title = "数据库",
                        summary = "老站点可能用到 WebSQL",
                        checked = settings.databaseEnabled,
                        onCheckedChange = { onChange(settings.copy(databaseEnabled = it)) },
                    )
                    Divider()
                    SwitchRow(
                        title = "自动加载图片",
                        summary = "关掉可省流量，网页里图片会显示为占位",
                        checked = settings.loadImages,
                        onCheckedChange = { onChange(settings.copy(loadImages = it)) },
                    )
                    Divider()
                    SwitchRow(
                        title = "第三方 Cookie",
                        summary = "关掉后跨站登录（如单点登录）可能失效",
                        checked = settings.thirdPartyCookies,
                        onCheckedChange = { onChange(settings.copy(thirdPartyCookies = it)) },
                    )
                    Divider()
                    SwitchRow(
                        title = "媒体自动播放",
                        summary = "允许网页不点播放就开始放音视频",
                        checked = settings.mediaAutoplay,
                        onCheckedChange = { onChange(settings.copy(mediaAutoplay = it)) },
                    )
                    Divider()
                    OptionRow(
                        title = "缓存策略",
                        options = listOf(
                            CacheMode.DEFAULT to "按需（推荐）",
                            CacheMode.CACHE_ELSE_NETWORK to "缓存优先",
                            CacheMode.NO_CACHE to "不用缓存",
                        ),
                        selected = settings.cacheMode,
                        onSelect = { onChange(settings.copy(cacheMode = it)) },
                    )
                    Divider()
                    OptionRow(
                        title = "混合内容",
                        options = listOf(
                            MixedContentLevel.ALLOW to "全部允许",
                            MixedContentLevel.COMPATIBILITY to "兼容模式",
                            MixedContentLevel.BLOCK to "全部拦截",
                        ),
                        selected = settings.mixedContent,
                        onSelect = { onChange(settings.copy(mixedContent = it)) },
                        summary = "https 页面里加载 http 资源时的处理方式",
                    )
                    Divider()
                    StepRow(
                        title = "文本缩放",
                        valueText = "${settings.textZoomPercent}%",
                        onDecrease = {
                            onChange(
                                settings.copy(
                                    textZoomPercent = (settings.textZoomPercent - 10).coerceAtLeast(AppSettings.MIN_TEXT_ZOOM),
                                ),
                            )
                        },
                        onIncrease = {
                            onChange(
                                settings.copy(
                                    textZoomPercent = (settings.textZoomPercent + 10).coerceAtMost(AppSettings.MAX_TEXT_ZOOM),
                                ),
                            )
                        },
                    )
                }
            }

            // ---------------- 渲染 ----------------
            item { SectionTitle("渲染") }
            item {
                SettingsCard {
                    OptionRow(
                        title = "视图模式",
                        options = listOf(
                            ViewMode.SYSTEM to "跟随网页",
                            ViewMode.DESKTOP to "桌面版网站",
                            ViewMode.MOBILE to "移动版网站",
                        ),
                        selected = settings.viewMode,
                        onSelect = { onChange(settings.copy(viewMode = it)) },
                        summary = "桌面版会用 980px 布局宽度，适合排版在手机上挤成一团的站点",
                    )
                    Divider()
                    SwitchRow(
                        title = "硬件加速渲染",
                        summary = "关闭后改用软件渲染，更卡但能绕过个别机型的花屏/白块",
                        checked = settings.hardwareAcceleration,
                        onCheckedChange = { onChange(settings.copy(hardwareAcceleration = it)) },
                    )
                    Divider()
                    OptionRow(
                        title = "网页深浅色",
                        options = listOf(
                            DarkModeSetting.SYSTEM to "跟随应用",
                            DarkModeSetting.ALWAYS_DARK to "总是深色",
                            DarkModeSetting.ALWAYS_LIGHT to "总是浅色",
                            DarkModeSetting.OFF to "由网页决定",
                        ),
                        selected = settings.darkMode,
                        onSelect = { onChange(settings.copy(darkMode = it)) },
                    )
                }
            }

            // ---------------- 浏览器标识 ----------------
            item { SectionTitle("浏览器标识（UA）") }
            item {
                SettingsCard {
                    OptionRow(
                        title = "UA 模式",
                        options = listOf(
                            UserAgentMode.FOLLOW_VIEW to "跟随视图模式",
                            UserAgentMode.SYSTEM to "系统 WebView 默认",
                            UserAgentMode.DESKTOP to "桌面版 Chrome",
                            UserAgentMode.CUSTOM to "自定义",
                        ),
                        selected = settings.userAgentMode,
                        onSelect = { onChange(settings.copy(userAgentMode = it)) },
                    )
                    if (settings.userAgentMode == UserAgentMode.CUSTOM) {
                        Divider()
                        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            OutlinedTextField(
                                value = settings.customUserAgent,
                                onValueChange = {
                                    onChange(settings.copy(customUserAgent = it.take(AppSettings.MAX_UA_LENGTH)))
                                },
                                label = { Text("自定义 UA") },
                                placeholder = { Text("Mozilla/5.0 (...)") },
                                singleLine = false,
                                maxLines = 3,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Uri,
                                    imeAction = ImeAction.Done,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "留空时使用系统默认 UA",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Divider()
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Text(
                            text = "当前生效的 UA",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = UserAgents.resolve(
                                mode = settings.userAgentMode,
                                viewMode = settings.viewMode,
                                customUa = settings.customUserAgent,
                                defaultUa = defaultUserAgent,
                                appVersion = appVersion,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // ---------------- 数据 ----------------
            item { SectionTitle("数据") }
            item {
                SettingsCard {
                    ActionRow(
                        title = "清除缓存",
                        summary = "只清网页缓存，站点列表与 Cookie 保留",
                        onClick = onClearCache,
                    )
                    Divider()
                    ActionRow(
                        title = "清除网页数据",
                        summary = "清掉全部 Cookie、本地存储与已保存的登录态",
                        onClick = onClearWebData,
                    )
                    Divider()
                    ActionRow(
                        title = "导出站点配置",
                        summary = "把 $siteCount 个站点复制到剪贴板，便于备份或迁移到别的设备",
                        enabled = siteCount > 0,
                        onClick = onExportSites,
                    )
                    Divider()
                    ActionRow(
                        title = "从剪贴板导入站点",
                        summary = "自动跳过已存在的地址，不会覆盖现有站点",
                        onClick = onImportSites,
                    )
                }
            }

            // ---------------- 系统 ----------------
            item { SectionTitle("系统") }
            item {
                SettingsCard {
                    ActionRow(
                        title = "权限总览",
                        summary = "逐项查看浏览器可能用到的权限，一键补申请",
                        onClick = onOpenPermissions,
                    )
                    Divider()
                    ActionRow(
                        title = "打开系统应用设置",
                        summary = "授权/撤销权限、通知、后台限制都在这里",
                        onClick = onOpenSystemSettings,
                    )
                }
            }

            // ---------------- 关于 ----------------
            item { SectionTitle("关于") }
            item {
                SettingsCard {
                    InfoRow(title = "版本", value = appVersion)
                    Divider()
                    InfoRow(title = "WebView 版本", value = webViewVersion)
                    Divider()
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Text(
                            text = "网页数据保存在本机应用私有目录；密码用 Android Keystore 加密，" +
                                "只在对应站点自动填充。应用不联网上报任何数据。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Start,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 2.dp),
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp,
    ) {
        Column { content() }
    }
}

@Composable
private fun Divider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
    )
}

@Composable
private fun SwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (summary.isNotBlank()) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun InfoRow(title: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun ActionRow(
    title: String,
    summary: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (summary.isNotBlank()) {
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun <T> OptionRow(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    summary: String = "",
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selected }?.second ?: ""
    Box {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(
                    text = selectedLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (summary.isNotBlank()) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    trailingIcon = {
                        if (value == selected) {
                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelect(value)
                    },
                )
            }
        }
    }
}

@Composable
private fun StepRow(
    title: String,
    valueText: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "当前 $valueText",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onDecrease) { Text("减小") }
        TextButton(onClick = onIncrease) { Text("增大") }
    }
}

@Composable
private fun ChoiceChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

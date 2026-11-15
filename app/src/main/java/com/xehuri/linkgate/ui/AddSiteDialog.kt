package com.xehuri.linkgate.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.xehuri.linkgate.data.Site
import com.xehuri.linkgate.data.UrlNormalizer
import java.util.UUID

/**
 * 添加 / 编辑网页的对话框。
 *
 * 网址会先过 [UrlNormalizer]：没写协议自动补 https，非法或非 http(s) 协议直接报错，
 * 避免存进一个打不开的站点。IPv6 地址是可选字段，只在「IPv6 优先」开启时使用；
 * 描述信息也是可选的，会显示在首屏卡片上。
 */
@Composable
fun AddSiteDialog(
    initial: Site?,
    onDismiss: () -> Unit,
    onConfirm: (Site) -> Unit,
) {
    var urlText by remember { mutableStateOf(initial?.url ?: "") }
    var titleText by remember { mutableStateOf(initial?.title ?: "") }
    var ipv6Text by remember { mutableStateOf(initial?.ipv6Url ?: "") }
    var descriptionText by remember { mutableStateOf(initial?.description ?: "") }
    var errorText by remember { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "添加网页" else "编辑网页") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = urlText,
                    onValueChange = { value ->
                        urlText = value
                        errorText = null
                    },
                    label = { Text("网址") },
                    placeholder = { Text("example.com 或 https://example.com") },
                    singleLine = true,
                    isError = errorText != null,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = {
                        val pasted = clipboard.getText()?.text
                        if (!pasted.isNullOrBlank()) {
                            urlText = pasted.trim()
                            errorText = null
                        }
                    }) { Text("粘贴") }
                    TextButton(onClick = {
                        urlText = ""
                        titleText = ""
                        ipv6Text = ""
                        descriptionText = ""
                        errorText = null
                    }) { Text("清空") }
                }
                errorText?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                OutlinedTextField(
                    value = titleText,
                    onValueChange = { titleText = it },
                    label = { Text("名称（可留空）") },
                    placeholder = { Text("留空则显示域名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = ipv6Text,
                    onValueChange = {
                        ipv6Text = it
                        errorText = null
                    },
                    label = { Text("IPv6 地址（可选）") },
                    placeholder = { Text("http://[2408:...::1]:8080") },
                    supportingText = {
                        if (ipv6Text.isNotBlank()) {
                            Text(
                                text = "填了它并开启「IPv6 优先」时，会优先用这个地址打开；打不开自动回退主地址。",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = descriptionText,
                    onValueChange = { value -> descriptionText = value.take(200) },
                    label = { Text("描述（可选）") },
                    placeholder = { Text("例如：公司内网、家里 NAS") },
                    maxLines = 3,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val normalized = UrlNormalizer.normalize(urlText)
                if (normalized == null) {
                    errorText = "网址不合法。请输入 example.com 或 https://example.com 这样的 http/https 链接。"
                    return@TextButton
                }
                val ipv6Raw = ipv6Text.trim()
                val normalizedIpv6 = if (ipv6Raw.isEmpty()) {
                    ""
                } else {
                    val value = UrlNormalizer.normalize(ipv6Raw)
                    if (value == null) {
                        errorText = "IPv6 地址不合法。请输入 http://[2408:...]:8080 这样的 http/https 链接，或留空。"
                        return@TextButton
                    }
                    value
                }
                val finalTitle = titleText.trim().ifBlank { UrlNormalizer.titleFromUrl(normalized) }
                onConfirm(
                    Site(
                        id = initial?.id ?: UUID.randomUUID().toString(),
                        title = finalTitle,
                        url = normalized,
                        description = descriptionText.trim(),
                        ipv6Url = normalizedIpv6,
                    ),
                )
            }) {
                Text(if (initial == null) "添加" else "保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
fun DeleteSiteDialog(
    site: Site,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除「${site.title}」？") },
        text = {
            Column(modifier = Modifier.padding(top = 0.dp)) {
                Text("只从 LinkGate 列表里移除，网页本身不受影响。")
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("删除", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

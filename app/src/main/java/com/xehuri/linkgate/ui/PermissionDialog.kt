package com.xehuri.linkgate.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xehuri.linkgate.perm.PermissionPlanner

/**
 * 权限总览/申请入口：列出浏览器可能用到的所有权限及当前状态，
 * 可以一次性补申请所有未授予项，或跳到系统设置页。
 */
@Composable
fun PermissionDialog(
    items: List<PermissionPlanner.Item>,
    granted: Set<String>,
    onDismiss: () -> Unit,
    onRequest: (List<String>) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val missing = items.filter { it.permission !in granted }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("浏览器权限") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "LinkGate 会在启动时批量申请这些权限；网页里请求麦克风、摄像头、定位时会自动放行，不再逐站点询问。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider()
                items.forEach { item ->
                    val isGranted = item.permission in granted
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isGranted) Icons.Filled.Check else Icons.Filled.Close,
                            contentDescription = null,
                            tint = if (isGranted) Color(0xFF2E9E5B) else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = item.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (missing.isEmpty()) onDismiss() else onRequest(missing.map { it.permission })
            }) {
                Text(if (missing.isEmpty()) "全部已授权" else "申请剩余 ${missing.size} 项")
            }
        },
        dismissButton = {
            TextButton(onClick = onOpenSettings) { Text("系统设置") }
        },
    )
}

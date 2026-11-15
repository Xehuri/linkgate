package com.xehuri.linkgate.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xehuri.linkgate.data.IconStore
import com.xehuri.linkgate.data.Site
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 站点图标：优先显示缓存的 site icon（favicon），没有就退回「首字母 + 色块」。
 *
 * [iconVersion] 变化时重新读盘，用于「访问过网站拿到 WebView 上报图标」或「后台抓到图标」后刷新。
 */
@Composable
fun SiteIcon(
    site: Site,
    iconVersion: Int,
    size: Dp = 30.dp,
) {
    val context = LocalContext.current
    val accent = remember(site.id) {
        val hue = ((site.title.hashCode() * 31 + site.url.hashCode()) and 0x7FFFFFFF) % 360
        Color.hsl(hue.toFloat(), 0.55f, 0.45f)
    }
    var bitmap by remember(site.id, iconVersion) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(site.id, iconVersion) {
        bitmap = withContext(Dispatchers.IO) { IconStore(context).load(site.id) }
    }

    Box(
        modifier = Modifier.size(size).clip(CircleShape).background(accent),
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                text = site.title.take(1).uppercase().ifBlank { "?" },
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

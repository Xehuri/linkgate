package com.xehuri.linkgate

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.xehuri.linkgate.data.SettingsStore
import com.xehuri.linkgate.data.SiteStore
import com.xehuri.linkgate.ui.LinkGateApp
import com.xehuri.linkgate.web.CookieStore

class MainActivity : ComponentActivity() {

    private lateinit var store: SiteStore
    private lateinit var settingsStore: SettingsStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 全面屏：允许网页内容延伸到刘海/挖孔区域
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        // 预热 CookieManager：它是惰性初始化的，第一次 getCookie 之前必须实例化一次，
        // 否则进程刚起来就退到后台时会读到空 Cookie，把登录态快照抹掉。
        CookieStore.warmUp()
        store = SiteStore(applicationContext)
        settingsStore = SettingsStore(applicationContext)
        setContent {
            LinkGateApp(store = store, settingsStore = settingsStore, onExit = { finish() })
        }
    }

    override fun onStop() {
        super.onStop()
        // 退到后台时把 Cookie 快照存下来并让 WebView 落盘：
        // HttpOnly 会话 Cookie 在进程结束后不会自己保留，必须由 App 兜底。
        runCatching { CookieStore(applicationContext).saveFrom(store.load()) }
        CookieStore.flushAsync()
    }
}

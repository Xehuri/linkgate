# LinkGate

安卓网页快捷启动器：启动就是站点列表，点一下直达网页；网页**沉浸式全屏**，悬浮控件**默认隐藏**（两指轻点呼出）；
返回键按「网页上一级 → 站点选择界面（首屏）→ 退出应用」回退；Cookie 会落盘并按站点恢复；内置密码管理器（保存 + 自动填充）。

- 应用名：**LinkGate**
- 包名：`com.xehuri.linkgate`
- 版本：1.0.4（versionCode 5）
- minSdk 26（Android 8.0）/ targetSdk 34 / compileSdk 36
- 安装包：`dist/LinkGate-1.0.4.apk`（release，已用专用密钥签名）

## 功能

### 1. 站点列表首屏（手机 / 平板自适应）

- 首次启动是空白引导页，只有「添加第一个网页」；
- **卡片图标**：优先显示网站 favicon（网页加载时由 WebView 上报，或后台抓 `/favicon.ico`、解析 `<link rel=icon>`），
  拿不到时回退「首字母 + 色块」；图标与标题**同一行**，不单独占一行；
- **自适应列数**：按「卡片目标宽度 220dp」反推列数——手机 2 列、平板竖屏 3 列、平板横屏 4–5 列，避免大屏卡片被压小/拉大；
- 标题最多两行，长名字不再被截断；显示域名；
- 右下角「添加网页」：输入网址（自动补 `https://`）与可选名称，支持粘贴剪贴板；
- 长按卡片：**置顶** / **上移** / **编辑** / **删除**（带确认）；
- 右上角两个入口：**已保存的密码**、**权限**；
- 夜间模式跟随系统。

### 2. 网页界面（全屏 + 悬浮控件默认隐藏）

- **沉浸式全屏**：隐藏状态栏与导航栏，网页铺满整块屏幕（含刘海区域）；
- **悬浮控制条默认隐藏**：**两指轻点屏幕**呼出，4.5 秒无操作自动收起，点空白处也可收起；
- 控制条按钮：返回 / 刷新 / 回到站点列表 / 更多（系统栏开关、复制链接、用系统浏览器打开）；
- 单指点击、滑动、缩放完全留给网页；
- 已开启：JavaScript、DOM Storage、Cookie 与第三方 Cookie、媒体自动播放、缩放、定位、文件选择、下载、混合内容；
- 网页里的 `intent://`、`mailto:`、`tel:` 交给系统；证书错误一律拒绝；
- 主框架错误显示「重试 / 回到站点列表」面板。

### 3. 返回键语义

| 当前状态 | 按返回键的结果 |
| --- | --- |
| 当前页就是站点入口落地页 | 回到站点选择界面（App 首屏） |
| 网页内有历史，且不在入口页 | 回上一级网页 |
| 网页历史到头（不在入口页） | 回到站点选择界面 |
| 站点选择界面 | 退出应用 |

「入口落地页」由 WebView 首次加载完成时记录，而不是用户填写的原始 URL，所以站点 302 到 `/login` 这类情况不会出现返回死循环。

### 4. Cookie 持久化

1. 退到后台时调用 `CookieManager.flush()`（后台线程），把持久 Cookie 写盘；
2. 按站点快照 Cookie 头，下次打开该站点前回填并补 `Max-Age`，**HttpOnly 会话 Cookie** 也能恢复。

真机验证：dsh 登录后 `am force-stop` → 重开仍直接进入已登录工作区。
代价：回填的 Cookie 无法还原 `HttpOnly` 属性（WebView API 不暴露该属性）。

### 5. 密码管理器（参考 Chrome）

- **保存**：页面上检测到登录表单提交（或点击登录按钮）时，弹出「保存这个密码？」对话框（显示网站与用户名），点「保存」即存入；
- **自动填充**：再次打开该网站，出现密码框时自动填入用户名 + 密码（只填空字段，会派发 `input/change` 事件，
  兼容 React/Vue 受控组件）；自动填充后 5 秒内不会误弹保存提示；
- **管理**：首屏右上角「人像」图标 → 已保存的密码列表（按 origin + 用户名），可单条删除；
- **安全**：
  - 使用 `WebViewCompat.addWebMessageListener` 与页面通信，原生侧拿到的是 **WebView 提供的、不可伪造的 sourceOrigin**，
    凭据只在该 origin 下查询/保存，跨站不串数据；不使用 `addJavascriptInterface`；
  - 凭据用 **Android Keystore AES-256-GCM** 密钥加密后存 SharedPreferences（真机验证 prefs 中只有密文，无明文密码）；
  - 无主密码、无云同步；设备解锁后 App 内可直接使用。

### 6. 权限：预先声明 + 启动批量预申请 + 网页内自动放行

Manifest 预先声明：`RECORD_AUDIO`、`CAMERA`、`ACCESS_FINE/COARSE_LOCATION`、`POST_NOTIFICATIONS`（33+）、
`READ_MEDIA_IMAGES/VIDEO/AUDIO`（33+）、`READ_EXTERNAL_STORAGE`（≤32）、`WRITE_EXTERNAL_STORAGE`（≤28）、
`INTERNET`、`ACCESS_NETWORK_STATE`、`ACCESS_WIFI_STATE`、`MODIFY_AUDIO_SETTINGS`、`VIBRATE`、`WAKE_LOCK`。

- 首次启动批量预申请（按权限集合签名去重，不重复弹窗）；
- 网页内 `onPermissionRequest` 只要 App 已有系统权限就直接 `grant`；定位自动允许；
- 被拒时列表页顶部横幅提示，可补申请或跳系统设置。

### 7. 老版系统 WebView 兼容垫片

部分设备（如小米平板 5，WebView **104**）缺少 dsh 客户端插件使用的现代 API，会报
`TypeError: AbortSignal.any is not a function` —— 页面渲染出来但功能不可用。

LinkGate 在**文档开始**（`WebViewCompat.addDocumentStartJavaScript`）注入 `assets/web-compat.js`，
feature-detect 后补齐：`AbortSignal.any/timeout`、`Promise.withResolvers/try`、`Object/Map.groupBy`、
`Array.prototype.toSorted/toReversed/toSpliced/with`、`Array.fromAsync`、TypedArray 非破坏性方法、
`Set` 集合运算、`URL.parse/canParse`、`Element.checkVisibility`、`scheduler.postTask/yield`、
`String.isWellFormed/toWellFormed` 等。新 WebView 上全部原样跳过。

真机验证（平板 WebView 104）：补丁后 `typeof AbortSignal.any === 'function'`，dsh 重载**零 JS 异常/零错误日志**，
工作区正常显示且可交互。

注意：CSS 层的 `:has()`、`@container`、`color-mix()`、CSS 嵌套等无法运行时补齐，老 WebView 上部分样式会降级。

## 构建

- JDK 21、Gradle 8.9（wrapper）、AGP 8.6.1、Kotlin 2.0.0、Compose BOM 2024.12.01、androidx.webkit 1.13.0
- Android SDK：platform `android-36`、build-tools `34.0.0`/`35.0.0`
- `local.properties` 的 `sdk.dir` 指向本机 SDK

```bash
cd linkgate
./gradlew :app:assembleDebug          # 调试包
./gradlew :app:testDebugUnitTest      # 纯 JVM 单测（82 个）
./gradlew :app:lintDebug              # 静态检查（0 errors）
./gradlew :app:assembleRelease        # 未签名 release
```

> `debug` 构建也用 `keystore/linkgate-release.jks` 签名（`app/build.gradle.kts` 的 `signingConfigs`），
> 便于直接覆盖安装到已装正式包的设备上排查且保留数据。

签名：

```bash
BT=$ANDROID_HOME/build-tools/35.0.0
PASS=$(cat keystore/.storepass)
$BT/zipalign -p -f 4 app/build/outputs/apk/release/app-release-unsigned.apk /tmp/LinkGate-aligned.apk
$BT/apksigner sign --ks keystore/linkgate-release.jks --ks-key-alias linkgate \
  --ks-pass "pass:$PASS" --key-pass "pass:$PASS" \
  --out dist/LinkGate-1.0.4.apk /tmp/LinkGate-aligned.apk
```

> 换签名密钥会导致无法覆盖升级。请保留 `keystore/linkgate-release.jks` 与 `.storepass`。

## 安装

```bash
adb install -r dist/LinkGate-1.0.4.apk
```

首次启动会连续弹出系统权限对话框，全部允许即可。

## 目录结构

```
app/src/main/java/com/xehuri/linkgate/
├── MainActivity.kt              # 单 Activity；退后台快照 Cookie + flush
├── data/                        # Site/编解码/排序、Cookie 快照、favicon 定位与缓存、密码编码/加密存储
├── nav/BackPlanner.kt           # 返回栈策略（纯函数）
├── perm/                        # 权限清单与运行时查询
├── ui/                          # Compose：列表首屏、站点图标、添加/编辑/删除、权限、密码管理、全屏网页
└── web/                         # WebView 配置、ChromeClient、WebViewClient、Cookie、下载、外部协议、
                                 # 兼容垫片注入、密码桥（WebMessageListener）
app/src/main/assets/             # web-compat.js（现代 API 垫片）、web-passwords.js（表单保存/填充脚本）
app/src/test/java/...            # 82 个 JVM 单测
```

## 版本记录

- 1.0.1：返回栈改为「入口落地页优先」；网页界面沉浸式全屏；Cookie flush + 快照恢复；卡片标题两行。
- 1.0.2：修复 **Compose `AndroidView` 承载 WebView 导致视口高度为 0**（显式 `MATCH_PARENT`，
  否则 dsh 这类依赖 `height:100%` 的 SPA 白屏）；悬浮控件默认隐藏 + 两指轻点呼出。
- 1.0.3/1.0.4：平板首屏自适应列数；卡片 favicon 图标（内联，不独占一行）；老 WebView 兼容垫片；
  密码管理器（保存提示 + 自动填充 + 加密存储 + 管理页）。

## 已知限制

- WebView 版本由系统/厂商提供；老 WebView 上 CSS 新特性（`:has()`、`@container` 等）会降级，JS 新 API 由垫片补齐。
- 未实现网页屏幕共享（`getDisplayMedia` 需要 MediaProjection 前台服务）。
- `blob:`/`data:` 下载不支持。
- 不支持多窗口/弹窗式 WebView，`window.open` 与 `target=_blank` 在当前窗口打开。
- 密码库无主密码、无云同步；恢复的 Cookie 不带 `HttpOnly`。
- 数据只存本机 SharedPreferences / filesDir，无导入导出。

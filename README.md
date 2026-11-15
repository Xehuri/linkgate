# LinkGate

安卓网页快捷启动器：启动就是站点列表，点一下直达网页；网页**沉浸式全屏**，悬浮控件**默认隐藏**
（按设置需要连续 N 次双指轻点才呼出，默认 2 次）；返回键按「网页上一级 → 站点选择界面（首屏）→ 退出应用」回退；
Cookie 会落盘并按站点恢复；内置密码管理器（保存 + 自动填充）与完整设置界面（IPv6 优先、缩放、渲染、UA、WebView 参数）。

- 应用名：**LinkGate**
- 包名：`com.xehuri.linkgate`
- 版本：1.1.4（versionCode 10）
- minSdk 26（Android 8.0）/ targetSdk 34 / compileSdk 36
- 安装包：`dist/LinkGate-1.1.4.apk`（release，已用专用密钥签名）

## 功能

### 1. 站点列表首屏（手机 / 平板自适应）

- 首次启动是空白引导页，只有「添加第一个网页」；
- **卡片图标**：优先显示网站 favicon（网页加载时由 WebView 上报，或后台抓 `/favicon.ico`、解析 `<link rel=icon>`），
  有图标时**不再垫色块底**（避免和品牌色冲突）；拿不到图标时回退「首字母 + 色块」；图标与标题**同一行**；
- **自适应列数**：按「卡片目标宽度 220dp」反推列数——手机 2 列、平板竖屏 3 列、平板横屏 4–5 列；
- 标题最多两行；**副标题优先显示描述**（没填描述时显示域名）；配了 IPv6 地址的站点带 `IPv6` 标记；
- **搜索过滤**：站点 ≥ 6 个时顶部出现搜索框，按标题/描述/网址过滤，无结果有独立空态；
- 右下角「添加网页」：网址（自动补 `https://`）、名称、**IPv6 地址（可选）**、**描述（可选）**，支持粘贴剪贴板；
- 长按卡片：**置顶 / 上移 / 下移 / 编辑 / 复制链接 / 刷新图标 / 清除本站数据 / 删除**，
  浮窗**弹在手指按下的位置**（和 Chrome 一致：右下角放不下时自动翻到左上），不再固定挤在屏幕角落；
- 右上角两个入口：**已保存的密码**、**设置**；
- 夜间模式跟随系统。

### 2. 网页界面（全屏 + 悬浮控件默认隐藏）

- **沉浸式全屏**：隐藏状态栏与导航栏，网页铺满整块屏幕（含刘海区域）；
- **悬浮控制条默认隐藏**：设置里可选「双指连点 1/2/3 次」唤醒（**默认 2 次**，降低误触），
  4.5 秒无操作自动收起，点空白处也可收起；每次点中给一次轻触感反馈，凑满次数时给更强的反馈；
- 控制条按钮：返回 / **前进** / 刷新 / 回到站点列表 / 更多（系统栏开关、复制链接、**清除本站数据**、用系统浏览器打开）；
- 单指点击、滑动、缩放完全留给网页；「更多」菜单展开期间暂停自动收起，不会点着点着菜单消失；
- **键盘避让**：edge-to-edge 下自己处理 IME insets，网页里的输入框不会被软键盘挡住；
- **退到后台自动暂停**页面的 JS 定时器与媒体，避免后台耗电/发热/外放；
- 网页里的 `intent://`、`mailto:`、`tel:` 交给系统；证书错误一律拒绝；
- 主框架错误显示增强面板：错误信息 + 当前网址 + 「重试 / 回到站点列表 / 复制链接 / 用浏览器打开」。

### 3. 设置界面（首屏右上角齿轮）

全部本地生效、改完即存，无需保存按钮；分区分组如下。

| 分区 | 项目 |
| --- | --- |
| 网络 | **IPv6 优先**开关；实时显示本机 IPv6 状态（可用地址 / 当前网络没有 IPv6） |
| 手势 | 悬浮控制条唤醒次数：双指连点 1 / 2 / 3 次（默认 2） |
| 网页 | 允许双指缩放、JavaScript、DOM 存储、数据库、自动加载图片、第三方 Cookie、媒体自动播放、缓存策略（按需/缓存优先/不用缓存）、混合内容（全部允许/兼容/拦截）、文本缩放（50%–200%，10% 步进） |
| 渲染 | 视图模式（跟随网页 / 桌面版网站 / 移动版网站）、硬件加速渲染、网页深浅色（跟随应用/总是深色/总是浅色/由网页决定） |
| 浏览器标识 | UA 模式（跟随视图模式 / 系统默认 / 桌面版 Chrome / 自定义）+ 自定义 UA 输入 + 当前生效 UA 预览 |
| 数据 | 清除缓存、清除网页数据、导出站点配置（到剪贴板）、从剪贴板导入站点 |
| 系统 | 权限总览（逐项查看 + 补申请）、打开系统应用设置 |
| 关于 | 版本、WebView 版本、数据存储说明 |

### 4. 站点字段：描述 + IPv6 地址

- **描述**：卡片副标题显示，也可以只在编辑时当备忘；搜索会匹配描述；
- **IPv6 地址**：可选。填了它并开启「IPv6 优先」后，App 会先检测本机是否真的拿到可路由的 IPv6，
  满足条件才用该地址打开，否则直接用主地址（不做无谓等待）；
- **自动回退**：IPv6 地址打不开（超时/拒绝连接等）时，自动改用主地址并提示一次，不会让用户卡在错误页；
- 地址规范化支持 IPv6 字面量：`[2408:...::1]:8080`、`2408:...::1`、`https://[2408:...::1]/path` 都能识别。

真机验证：站点 IPv6 地址指到同一局域网内的 IPv6 主机 → 页面标题「IPv6 直连成功」，客户端地址为该手机的 IPv6；
换成不可达地址 `[::2]:18081` → 日志出现 `IPv6 回退：net::ERR_CONNECTION_TIMED_OUT → 主地址`，页面回落到主地址。

### 5. 返回键语义

| 当前状态 | 按返回键的结果 |
| --- | --- |
| 当前页就是站点入口落地页 | 回到站点选择界面（App 首屏） |
| 网页内有历史，且不在入口页 | 回上一级网页 |
| 网页历史到头（不在入口页） | 回到站点选择界面 |
| 设置界面 | 回站点选择界面 |
| 站点选择界面 | 退出应用 |

「入口落地页」由 WebView 首次加载完成时记录，而不是用户填写的原始 URL，所以站点 302 到 `/login` 这类情况不会出现返回死循环。

### 6. Cookie 持久化

1. 退到后台时调用 `CookieManager.flush()`（后台线程），把持久 Cookie 写盘；
2. 按站点**增量**快照 Cookie 头（读不到就保留旧值，绝不清空），下次打开该站点前回填并补 `Max-Age`，
   **HttpOnly 会话 Cookie** 也能恢复；
3. 启动时预热 `CookieManager`，避免冷启动早期读到空 Cookie 把登录态抹掉。

真机验证：登录后 `am force-stop` → 重开仍直接进入已登录工作区；「清除本站数据」后再打开，
服务端收到的 Cookie 头为 `(空)`。

代价：回填的 Cookie 无法还原 `HttpOnly` 属性（WebView API 不暴露该属性）。

### 7. 密码管理器（参考 Chrome）

- **保存**：页面上检测到登录表单提交（或点击登录按钮）时，弹出「保存这个密码？」对话框（显示网站与用户名），点「保存」即存入；
- **自动填充**：再次打开该网站，出现密码框时自动填入用户名 + 密码（只填空字段，会派发 `input/change` 事件，
  兼容 React/Vue 受控组件）；自动填充后 5 秒内不会误弹保存提示；
- **管理**：首屏右上角「人像」图标 → 已保存的密码列表（按 origin + 用户名），可单条删除；
- **安全**：
  - 使用 `WebViewCompat.addWebMessageListener` 与页面通信，原生侧拿到的是 **WebView 提供的、不可伪造的 sourceOrigin**，
    凭据只在该 origin 下查询/保存，跨站不串数据；不使用 `addJavascriptInterface`；
  - 凭据用 **Android Keystore AES-256-GCM** 密钥加密后存 SharedPreferences（真机验证 prefs 中只有密文，无明文密码）；
  - 无主密码、无云同步；设备解锁后 App 内可直接使用。

### 8. 权限：预先声明 + 启动批量预申请 + 网页内自动放行

Manifest 预先声明：`RECORD_AUDIO`、`CAMERA`、`ACCESS_FINE/COARSE_LOCATION`、`POST_NOTIFICATIONS`（33+）、
`READ_MEDIA_IMAGES/VIDEO/AUDIO`（33+）、`READ_EXTERNAL_STORAGE`（≤32）、`WRITE_EXTERNAL_STORAGE`（≤28）、
`INTERNET`、`ACCESS_NETWORK_STATE`、`ACCESS_WIFI_STATE`、`MODIFY_AUDIO_SETTINGS`、`VIBRATE`、`WAKE_LOCK`。

- 首次启动批量预申请（按权限集合签名去重，不重复弹窗）；
- 网页内 `onPermissionRequest` 只要 App 已有系统权限就直接 `grant`；定位自动允许；
- 被拒时列表页顶部横幅提示，可补申请或跳系统设置。

### 9. 文件下载

网页里的下载分三条通路，都落到系统「下载」目录：

| 通路 | 触发场景 | 实现 |
| --- | --- | --- |
| 普通附件 | 服务端返回 `Content-Disposition: attachment` | 交给系统 `DownloadManager`（带 Cookie / UA / Referer，失败回退应用私有目录） |
| `blob:` | 现代网页 `URL.createObjectURL(blob)` + `<a download>`、`window.open(blob)` | 注入 `assets/web-downloads.js` 读成字节 → 分片 base64 → `LinkGateDownload` 消息桥 → 原生写盘 |
| `data:` | `<a download href="data:...">` 内嵌文件 | 页面内由同一脚本接管；直接导航到 data: 时由 `DataUrl` 原生解码 |

- 保存：Android 10+ 走 MediaStore（出现在「下载」应用/文件管理器，无需存储权限），8/9 写公共 Downloads 目录；
- **完成即问**：应用在前台时弹「下载完成 —— 要现在打开吗？」（打开 / 稍后 / 下载管理）；
  后台完成则发「下载完成」通知，点通知直接打开文件，附「打开下载目录」按钮；
- **下载管理器**（首屏右上角下载图标，或设置 → 数据 → 下载管理）：历史列表带类型色块、站点名、大小、时间；
  点一条直接打开；条目菜单可「只删记录」或「删除记录和文件」；顶栏菜单可「打开系统下载目录 / 清空记录」；
- 历史合并了系统 DownloadManager 的任务：App 被杀之后在后台完成的下载，重新打开管理器时会自动补录；
  被用户删掉的记录会记进 hidden 集合，不会被再次补回来；
- 入口：首屏图标、设置 → 数据 → 下载管理，网页控制条「更多」→ 打开下载目录；
- 限制：单文件 64MB（内存中分片转发）、`blob:` 仅拦截带 `download` 属性的链接（没有该属性的 blob 链接是「页面内预览」，不接管）；
  DownloadManager 的 cursor 在 Android 10+ 不允许读本地路径，打开文件统一走「文件名 → MediaStore 反查 content uri」。

真机验证（Android 13 / WebView 124）：`report.txt`（DownloadManager）、`导出数据.csv`（blob，32 字节含 BOM）、
`内嵌文件.txt`（data:, 18 字节）三者都出现在 `/sdcard/Download/` 且内容正确；
三种通路完成时都弹了「是否打开」，点「打开」能拉起系统选择器；下载管理器里能看到、能打开、能删（只删记录时文件仍在）。

### 10. 稳定性与数据安全

- **渲染进程崩溃兜底**：`WebViewClient.onRenderProcessGone` 返回 true 并重建 WebView（最多自动重试 2 次），
  避免系统把整个 App 杀掉（以前表现为闪退）；
- **备份排除敏感数据**：`backup_rules.xml` / `data_extraction_rules.xml` 把 Cookie 快照（明文凭据）、
  密码库（Keystore 密钥不可迁移）、favicon 缓存排除在云备份与换机迁移之外，只备份站点列表与设置；
- **站点导出/导入**：编码格式稳定（`LINKGATE-SITES/2`，兼容读取 `/1`），导出的文本只含标题/网址/描述/IPv6 地址，
  不含任何 Cookie 与密码；导入按网址去重、id 冲突自动重生成，不会覆盖已有站点；
- **清除数据**：全局（设置页）与单站点（长按菜单 / 网页控制条）两个层级，清 Cookie、快照、本地存储、缓存、表单与已存密码。

### 11. 老版系统 WebView 兼容垫片

部分设备（系统 WebView 停在 **104**）缺少现代网页用到的 API，会报
`TypeError: AbortSignal.any is not a function` —— 页面渲染出来但功能不可用。

LinkGate 在**文档开始**（`WebViewCompat.addDocumentStartJavaScript`）注入 `assets/web-compat.js`，
feature-detect 后补齐：`AbortSignal.any/timeout`、`Promise.withResolvers/try`、`Object/Map.groupBy`、
`Array.prototype.toSorted/toReversed/toSpliced/with`、`Array.fromAsync`、TypedArray 非破坏性方法、
`Set` 集合运算、`URL.parse/canParse`、`Element.checkVisibility`、`scheduler.postTask/yield`、
`String.isWellFormed/toWellFormed` 等。新 WebView 上全部原样跳过。

桌面版视图另注入 `DesktopViewport`：把页面 viewport meta 强制改写为 `width=980`，
解决「换了桌面 UA 但页面自带 `width=device-width`，布局仍是手机宽度」的问题。

真机验证（平板 WebView 104）：补丁后 `typeof AbortSignal.any === 'function'`，页面重载**零 JS 异常/零错误日志**。
手机（WebView 124）：桌面版模式下 `window.innerWidth = 980`，UA 为 `Windows NT 10.0 ... Chrome/124.0.0.0`。

注意：CSS 层的 `:has()`、`@container`、`color-mix()`、CSS 嵌套等无法运行时补齐，老 WebView 上部分样式会降级。

## 构建

- JDK 21、Gradle 8.9（wrapper）、AGP 8.6.1、Kotlin 2.0.0、Compose BOM 2024.12.01、androidx.webkit 1.13.0
- Android SDK：platform `android-36`、build-tools `34.0.0`/`35.0.0`
- `local.properties` 的 `sdk.dir` 指向本机 SDK

```bash
cd linkgate
./gradlew :app:assembleDebug          # 调试包
./gradlew :app:testDebugUnitTest      # 纯 JVM 单测（141 个）
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
  --out dist/LinkGate-1.1.4.apk /tmp/LinkGate-aligned.apk
```

> 换签名密钥会导致无法覆盖升级。请保留 `keystore/linkgate-release.jks` 与 `.storepass`（均在 `.gitignore` 内）。

## 安装

```bash
adb install -r dist/LinkGate-1.1.4.apk
```

首次启动会连续弹出系统权限对话框，全部允许即可。

## 目录结构

```
app/src/main/java/com/xehuri/linkgate/
├── MainActivity.kt              # 单 Activity；Cookie 预热、退后台快照 + flush
├── data/                        # Site/编解码(v2)/排序/导入合并、AppSettings+编解码、IPv6 判定、
│                                # favicon 定位与缓存、密码编码/加密存储
├── nav/                         # BackPlanner（返回栈，纯函数）、OpenTargetPlanner（IPv6 选路，纯函数）
├── perm/                        # 权限清单与运行时查询
├── ui/                          # Compose：列表首屏、设置页、站点图标、增删改、权限、密码管理、全屏网页
└── web/                         # WebView 配置与参数应用、UA 组装、桌面视口注入、ChromeClient、
                                 # WebViewClient（含崩溃兜底）、Cookie、下载、外部协议、清除站点数据、
                                 # 两指连点状态机、兼容垫片注入、密码桥（WebMessageListener）
app/src/main/assets/             # web-compat.js（现代 API 垫片）、web-passwords.js（表单保存/填充脚本）
app/src/main/res/xml/            # backup_rules.xml、data_extraction_rules.xml（备份排除敏感数据）
app/src/test/java/...            # 141 个 JVM 单测
```

## 版本记录

- 1.0.1：返回栈改为「入口落地页优先」；网页界面沉浸式全屏；Cookie flush + 快照恢复；卡片标题两行。
- 1.0.2：修复 **Compose `AndroidView` 承载 WebView 导致视口高度为 0**（显式 `MATCH_PARENT`，
  否则依赖 `height:100%` 的 SPA 会白屏）；悬浮控件默认隐藏 + 两指轻点呼出。
- 1.0.3/1.0.4：平板首屏自适应列数；卡片 favicon 图标（内联，不独占一行）；老 WebView 兼容垫片；
  密码管理器（保存提示 + 自动填充 + 加密存储 + 管理页）。
- 1.1.0：站点**描述**与**IPv6 地址**字段；**IPv6 优先**（含不可达自动回退）；**设置界面**
  （网页参数 / 渲染模式 / UA / 手势 / 数据 / 系统）；**双指连点唤醒**（默认 2 次，可配 1–3 次）；
  桌面版视口强制 980px；站点搜索、下移、复制链接、刷新图标、清除本站数据；导出/导入站点；
  渲染进程崩溃恢复；Cookie 快照改增量写；键盘避让；后台暂停页面；备份排除 Cookie 与密码；有图标不再垫色块底。
- 1.1.4：修「关于」里的版本号显示（`BuildConfig.VERSION_NAME` 是编译期内联常量，增量编译后旧值会残留），
  现在显示 `版本（versionCode）`。
- 1.1.3：**重页面（dsh 这类 SPA）主线程优化**：键盘避让改用「动画目标 insets」，避免键盘动画期间逐帧缩放
  WebView；`onConsoleMessage` 只在调试包打日志（JNI 回调里写 logd 很贵）；密码扫描的 MutationObserver
  去抖 500ms（原来每次 DOM 变动都整篇 `querySelectorAll`）。平板实测：键盘/点击场景 janky 4.63%→0.94%、
  滚动 janky 2.17%→0.50%。
- 1.1.2：**下载完成提示**（应用内「是否打开」弹窗 + 通知点击直达文件）；**下载管理器**（历史列表、
  打开文件、删记录/删文件、清空、合并系统 DownloadManager 任务并记住已删除项）；修复 Android 10+ 读
  `COLUMN_LOCAL_FILENAME` 抛 `SecurityException` 导致「下载管理」闪退；改用「文件名 → MediaStore 反查」打开文件。
- 1.1.1：**文件下载支持**（DownloadManager + blob 消息桥 + data: 原生解码，落系统下载目录 + 完成通知 + 下载目录入口）；
  首屏长按浮窗改为**在手指位置弹出**（放不下时自动翻转），不再固定屏幕角落。

## 已知限制

- WebView 版本由系统/厂商提供；老 WebView 上 CSS 新特性（`:has()`、`@container` 等）会降级，JS 新 API 由垫片补齐。
- 未实现网页屏幕共享（`getDisplayMedia` 需要 MediaProjection 前台服务）。
- 不支持多窗口/弹窗式 WebView，`window.open` 与 `target=_blank` 在当前窗口打开。
- 密码库无主密码、无云同步；恢复的 Cookie 不带 `HttpOnly`。
- 「IPv6 优先」只对**显式配置了 IPv6 地址的站点**生效；没配的站点走系统 DNS 解析（由 Chromium 自行选择协议）。
- 桌面版视图会强制 980px 布局，个别站点可能出现横向滚动或元素错位，可切回「跟随网页」。
- **CSS 锚点定位**（`anchor-name` / `position-anchor` / `anchor()` / `position-area`）需要 Chrome 125+（WebView ≥125）。
  系统 WebView 124 上，依赖它的网页自定义浮窗（例如某些网页应用的自定义长按菜单）会退回到角落位置 —— 这不是 LinkGate 的视图承载问题，
  在「设置 → 应用 → Android System WebView」升级到 125 以上即可恢复（本机 Chrome 已是 147，但未注册为 WebView 提供者）。
- 站点数据只存本机；换机请用「导出站点配置 → 从剪贴板导入」。

## 许可证

[Apache License 2.0](LICENSE)，版权归 Xehuri 所有（见 [NOTICE](NOTICE)）。

```
Copyright 2026 Xehuri

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0
```

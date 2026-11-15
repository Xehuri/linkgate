package com.xehuri.linkgate.web

import android.util.Log
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/**
 * 「桌面版网站」模式下把页面布局宽度强制成 980px。
 *
 * 为什么需要：`WebSettings.useWideViewPort` 只在页面**没有**声明 `<meta name="viewport">` 时
 * 才会用宽视口；而绝大多数现代站点都声明了 `width=device-width`，于是即便换了桌面 UA，
 * 布局宽度仍然是手机宽度（如 393px），页面照样是窄条。
 *
 * 做法：在 document-start 注入一段脚本，把页面的 viewport meta 改写为 `width=980`，
 * 并用 MutationObserver 盯着页面自己插进来的 meta（很多站点的 meta 是框架动态插入的），
 * 一出现就改写，保证最终生效的是 980。
 */
object DesktopViewport {

    private const val TAG = "LinkGateDesktop"

    private const val SCRIPT = """
(function () {
  if (window.__linkgateDesktopViewport) return;
  window.__linkgateDesktopViewport = true;
  var CONTENT = 'width=980';

  function apply() {
    try {
      var head = document.head || document.documentElement;
      if (!head) return;
      var metas = document.querySelectorAll('meta[name="viewport" i]');
      if (metas.length === 0) {
        var created = document.createElement('meta');
        created.setAttribute('name', 'viewport');
        created.setAttribute('content', CONTENT);
        head.appendChild(created);
      } else {
        for (var i = 0; i < metas.length; i++) {
          if (metas[i].getAttribute('content') !== CONTENT) {
            metas[i].setAttribute('content', CONTENT);
          }
        }
      }
    } catch (e) {}
  }

  apply();
  document.addEventListener('DOMContentLoaded', apply);
  window.addEventListener('load', apply);
  try {
    var observer = new MutationObserver(function () { apply(); });
    observer.observe(document.documentElement, { childList: true, subtree: true });
    setTimeout(function () { observer.disconnect(); }, 5000);
  } catch (e) {}
})();
"""

    /** 只在桌面版模式下调用，且每个 WebView 只调用一次。 */
    fun apply(webView: WebView) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            Log.w(TAG, "当前 WebView 不支持 document-start 脚本，桌面视口注入跳过")
            return
        }
        runCatching {
            WebViewCompat.addDocumentStartJavaScript(webView, SCRIPT, setOf("*"))
        }.onFailure { Log.w(TAG, "桌面视口注入失败", it) }
    }
}

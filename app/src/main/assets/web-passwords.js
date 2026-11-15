/*
 * LinkGate 密码自动填充/保存脚本（document-start 注入）。
 *
 * 与原生 PasswordBridge（WebMessageListener）配合：
 * - 页面出现密码框 → 向原生查询当前 origin 的凭据 → 只填「空字段」，并派发 input/change 事件；
 * - 检测到表单提交 / 登录按钮点击 → 把用户名+密码交给原生弹「保存密码？」；
 * - 回填后 5 秒内不重复弹保存提示（避免把自动填充当成用户输入再存一遍）。
 *
 * 安全：不做任何跨 origin 请求；是否返回凭据由原生按 WebView 提供的 sourceOrigin 决定。
 */
(function () {
  'use strict';
  if (window.__linkgatePasswordInit) return;
  window.__linkgatePasswordInit = true;

  var origin = location.origin;
  if (!origin || origin === 'null') return;

  var suppressUntil = 0;
  var lastSentKey = '';
  var queryPending = false;
  var lastQueryAt = 0;
  var observer = null;

  function bridge() {
    return window.LinkGateBridge || null;
  }

  function post(payload) {
    var b = bridge();
    if (!b || typeof b.postMessage !== 'function') return false;
    try {
      b.postMessage(JSON.stringify(payload));
      return true;
    } catch (e) {
      return false;
    }
  }

  function installReplyHandler() {
    var b = bridge();
    if (!b) return false;
    var handler = function (event) {
      var payload;
      try { payload = JSON.parse(event.data); } catch (e) { return; }
      if (payload && payload.type === 'credentials') fill(payload.entries || []);
    };
    try {
      if (typeof b.addEventListener === 'function') b.addEventListener('message', handler);
      else b.onmessage = handler;
      return true;
    } catch (e) {
      return false;
    }
  }

  function waitForBridge(attempt) {
    if (installReplyHandler()) {
      requestFill();
      return;
    }
    if (attempt > 50) return;
    setTimeout(function () { waitForBridge(attempt + 1); }, 200);
  }

  function isVisible(el) {
    if (!el || el.disabled || el.readOnly) return false;
    if (!el.getClientRects || el.getClientRects().length === 0) return false;
    var rect = el.getBoundingClientRect();
    return rect.width > 0 && rect.height > 0;
  }

  function passwordFields() {
    var nodes = document.querySelectorAll('input[type="password"]');
    var list = [];
    for (var i = 0; i < nodes.length; i++) {
      if (isVisible(nodes[i])) list.push(nodes[i]);
    }
    return list;
  }

  function looksLikeUsername(el) {
    if (!el || el.tagName !== 'INPUT') return false;
    var type = (el.type || 'text').toLowerCase();
    if (type !== 'text' && type !== 'email' && type !== 'tel') return false;
    var hint = ((el.name || '') + ' ' + (el.id || '') + ' ' + (el.getAttribute('autocomplete') || '') + ' ' +
      (el.getAttribute('placeholder') || '')).toLowerCase();
    return /user|login|email|account|phone|mail|账号|用户|邮箱|手机/.test(hint);
  }

  function usernameFieldFor(passwordField) {
    var form = passwordField.form;
    var scope = form || document;
    var candidates = scope.querySelectorAll('input');
    var best = null;
    for (var i = 0; i < candidates.length; i++) {
      var el = candidates[i];
      if (el === passwordField || !isVisible(el)) continue;
      if (looksLikeUsername(el)) {
        best = el;
        if (el.compareDocumentPosition(passwordField) & Node.DOCUMENT_POSITION_FOLLOWING) break;
      }
    }
    if (!best && form) {
      for (var j = 0; j < candidates.length; j++) {
        var item = candidates[j];
        if (item !== passwordField && isVisible(item) && (item.type === 'text' || item.type === 'email')) {
          best = item;
        }
      }
    }
    return best;
  }

  function setNativeValue(el, value) {
    try {
      var descriptor = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value');
      if (descriptor && descriptor.set) descriptor.set.call(el, value);
      else el.value = value;
    } catch (e) {
      el.value = value;
    }
    try {
      el.dispatchEvent(new Event('input', { bubbles: true }));
      el.dispatchEvent(new Event('change', { bubbles: true }));
    } catch (e) { /* ignore */ }
  }

  function fill(entries) {
    if (!entries || !entries.length) return;
    var fields = passwordFields();
    if (!fields.length) return;
    var filledAny = false;
    for (var i = 0; i < fields.length; i++) {
      var passwordField = fields[i];
      var userField = usernameFieldFor(passwordField);
      var entry = entries[0];
      if (userField && userField.value) {
        for (var k = 0; k < entries.length; k++) {
          if ((entries[k].username || '').toLowerCase() === userField.value.toLowerCase()) {
            entry = entries[k];
            break;
          }
        }
      }
      if (userField && !userField.value && entry.username) {
        setNativeValue(userField, entry.username);
        filledAny = true;
      }
      if (!passwordField.value && entry.password) {
        setNativeValue(passwordField, entry.password);
        filledAny = true;
      }
    }
    if (filledAny) {
      suppressUntil = Date.now() + 5000;
      post({ type: 'filled' });
    }
  }

  function requestFill() {
    if (queryPending || Date.now() - lastQueryAt < 800) return;
    if (!passwordFields().length) return;
    queryPending = true;
    lastQueryAt = Date.now();
    if (!post({ type: 'query' })) queryPending = false;
    setTimeout(function () { queryPending = false; }, 1000);
  }

  function captureFrom(root) {
    if (Date.now() < suppressUntil) return;
    var fields = [];
    try {
      fields = (root || document).querySelectorAll('input[type="password"]');
    } catch (e) {
      return;
    }
    for (var i = 0; i < fields.length; i++) {
      var passwordField = fields[i];
      if (!isVisible(passwordField) || !passwordField.value) continue;
      var userField = usernameFieldFor(passwordField);
      var username = userField ? userField.value : '';
      var key = username + '\u0000' + passwordField.value;
      if (key === lastSentKey) continue;
      lastSentKey = key;
      post({ type: 'save', username: username, password: passwordField.value });
      return;
    }
  }

  function boot() {
    if (!document.documentElement) return;
    waitForBridge(0);

    document.addEventListener('focusin', function (event) {
      var el = event.target;
      if (!el || el.tagName !== 'INPUT') return;
      if (el.type === 'password' || looksLikeUsername(el)) requestFill();
    }, true);

    document.addEventListener('submit', function (event) {
      captureFrom(event.target || document);
    }, true);

    document.addEventListener('click', function (event) {
      var target = event.target;
      if (!target) return;
      if (target.tagName !== 'BUTTON' && target.tagName !== 'INPUT' && target.tagName !== 'A') return;
      setTimeout(function () {
        captureFrom(document);
      }, 450);
    }, true);

    window.addEventListener('pagehide', function () {
      captureFrom(document);
    }, true);

    try {
      // 去抖：重度 SPA（聊天流式输出）每秒可能产生成百上千次 DOM 变动，
      // 每次都整篇 querySelectorAll 会把渲染线程拖满。这里合并成最多每 500ms 查一次。
      var fillTimer = null;
      observer = new MutationObserver(function () {
        if (fillTimer !== null) return;
        fillTimer = setTimeout(function () {
          fillTimer = null;
          if (passwordFields().length) requestFill();
        }, 500);
      });
      observer.observe(document.documentElement, { childList: true, subtree: true });
    } catch (e) { /* ignore */ }
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', boot);
  } else {
    boot();
  }
})();

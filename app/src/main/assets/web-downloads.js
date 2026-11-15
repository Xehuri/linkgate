/*
 * LinkGate 下载支持（注入到每个页面的 document-start 脚本）。
 *
 * 背景：WebView 的 DownloadListener 只对 http(s) 响应生效，而现代网页大量用
 * `URL.createObjectURL(blob)` / `data:` + `<a download>` 造下载链接，这些字节只在
 * 渲染进程内部，原生拿不到 —— 表现为点了「下载」什么也没发生。
 *
 * 做法：只在 **链接带 download 属性且地址是 blob:/data:** 时接管，读成字节后
 * 分片 base64 发给原生（window.LinkGateDownload，由 DownloadBridge 处理）。
 * 没有 download 属性的 blob 链接不拦截（那是「在页面里预览」，不是下载）。
 */
(function () {
  if (window.__linkgateDownloads) return;
  if (!window.LinkGateDownload || typeof window.LinkGateDownload.postMessage !== 'function') return;
  window.__linkgateDownloads = true;

  var CHUNK_BYTES = 256 * 1024;
  var MAX_BYTES = 64 * 1024 * 1024;
  var counter = 0;

  var EXT_BY_MIME = {
    'application/pdf': '.pdf',
    'application/zip': '.zip',
    'application/json': '.json',
    'application/xml': '.xml',
    'application/octet-stream': '.bin',
    'application/vnd.ms-excel': '.xls',
    'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet': '.xlsx',
    'application/msword': '.doc',
    'application/vnd.openxmlformats-officedocument.wordprocessingml.document': '.docx',
    'application/vnd.openxmlformats-officedocument.presentationml.presentation': '.pptx',
    'text/plain': '.txt',
    'text/csv': '.csv',
    'text/html': '.html',
    'image/png': '.png',
    'image/jpeg': '.jpg',
    'image/gif': '.gif',
    'image/webp': '.webp',
    'image/svg+xml': '.svg',
    'audio/mpeg': '.mp3',
    'video/mp4': '.mp4'
  };

  function post(obj) {
    try {
      window.LinkGateDownload.postMessage(JSON.stringify(obj));
      return true;
    } catch (e) {
      return false;
    }
  }

  function extensionFor(mime) {
    if (!mime) return '.bin';
    var key = String(mime).split(';')[0].trim().toLowerCase();
    if (EXT_BY_MIME[key]) return EXT_BY_MIME[key];
    if (key.indexOf('text/') === 0) return '.txt';
    if (key.indexOf('image/') === 0) return '.' + key.slice(6).replace('jpeg', 'jpg');
    if (key.indexOf('audio/') === 0) return '.' + key.slice(6);
    if (key.indexOf('video/') === 0) return '.' + key.slice(6);
    return '.bin';
  }

  function nameFor(anchor) {
    var raw = (anchor.getAttribute('download') || '').trim();
    if (raw) return raw;
    return 'download-' + Date.now();
  }

  function ensureExtension(name, mime) {
    if (/\.[a-z0-9]{1,8}$/i.test(name)) return name;
    return name + extensionFor(mime);
  }

  function toBase64(bytes) {
    var out = '';
    var step = 0x8000;
    for (var i = 0; i < bytes.length; i += step) {
      out += String.fromCharCode.apply(null, bytes.subarray(i, i + step));
    }
    return btoa(out);
  }

  function sendBytes(id, name, mime, bytes) {
    if (!post({ type: 'start', id: id, name: name, mime: mime || 'application/octet-stream', size: bytes.length })) {
      return;
    }
    var seq = 0;
    for (var offset = 0; offset < bytes.length; offset += CHUNK_BYTES) {
      var slice = bytes.subarray(offset, offset + CHUNK_BYTES);
      if (!post({ type: 'chunk', id: id, seq: seq, data: toBase64(slice) })) {
        post({ type: 'error', id: id, message: '与客户端通信失败' });
        return;
      }
      seq += 1;
    }
    post({ type: 'end', id: id });
  }

  function download(url, name) {
    var id = 'dl-' + Date.now() + '-' + (counter++);
    fetch(url)
      .then(function (response) { return response.blob(); })
      .then(function (blob) {
        var mime = blob.type || 'application/octet-stream';
        var finalName = ensureExtension(name || ('download-' + Date.now()), mime);
        if (blob.size > MAX_BYTES) {
          post({ type: 'error', id: id, message: '文件超过 64MB' });
          return;
        }
        blob.arrayBuffer().then(function (buffer) {
          sendBytes(id, finalName, mime, new Uint8Array(buffer));
        }).catch(function () {
          post({ type: 'error', id: id, message: '读取文件失败' });
        });
      })
      .catch(function () {
        post({ type: 'error', id: id, message: '浏览器拒绝读取该地址' });
      });
  }

  function hrefOf(anchor) {
    var raw = anchor.getAttribute('href') || '';
    if (!raw) return '';
    try {
      return new URL(raw, location.href).href;
    } catch (e) {
      return raw;
    }
  }

  // 捕获阶段拦截，早于页面自己的点击处理（含 React/Vue 的合成事件）
  document.addEventListener('click', function (event) {
    var target = event.target;
    if (!target || !target.closest) return;
    var anchor = target.closest('a[href]');
    if (!anchor) return;
    if (!anchor.hasAttribute('download')) return;
    var url = hrefOf(anchor);
    if (!/^(blob:|data:)/i.test(url)) return;
    event.preventDefault();
    event.stopPropagation();
    download(url, nameFor(anchor));
  }, true);

  // window.open(blob) 的场景：页面用一个隐藏链接触发新窗口下载
  var rawOpen = window.open;
  if (typeof rawOpen === 'function') {
    window.open = function (url) {
      if (typeof url === 'string' && /^(blob:|data:)/i.test(url)) {
        download(url, 'download-' + Date.now());
        return null;
      }
      return rawOpen.apply(window, arguments);
    };
  }
})();

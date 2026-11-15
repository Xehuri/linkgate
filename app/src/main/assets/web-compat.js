/*
 * LinkGate WebView 兼容垫片（document-start 注入）。
 *
 * 背景：部分设备的系统 WebView 较旧（例如小米平板 5 自带 WebView 104），
 * 而 dsh 的客户端插件用到了 Chrome 105~125 才有的现代 API，会直接抛
 * "TypeError: AbortSignal.any is not a function"，导致界面渲染出来但功能不可用。
 *
 * 原则：
 * 1. 只补"缺失"的 API，已有实现一律不动；
 * 2. 每个补丁都做 feature detect，异常吞掉，绝不影响正常页面；
 * 3. 只补 ECMAScript/Web API，不碰 CSS（:has()/@container 这类无法运行时补齐）。
 */
(function () {
  'use strict';
  var g = typeof globalThis !== 'undefined' ? globalThis : self;
  if (!g) return;

  function define(target, name, value) {
    if (!target) return;
    var exists = false;
    try { exists = typeof target[name] === 'function'; } catch (e) { exists = false; }
    if (exists) return;
    try {
      Object.defineProperty(target, name, {
        value: value,
        configurable: true,
        enumerable: false,
        writable: true
      });
    } catch (e) {
      try { target[name] = value; } catch (e2) { /* ignore */ }
    }
  }

  // ---- AbortSignal.any / abort / timeout（Chrome 116 / 103）----
  if (g.AbortSignal) {
    define(g.AbortSignal, 'any', function (signals) {
      var controller = new AbortController();
      var list = Array.from(signals || []);
      var listeners = [];
      function cleanup() {
        while (listeners.length) {
          var item = listeners.pop();
          try { item.signal.removeEventListener('abort', item.handler); } catch (e) { /* ignore */ }
        }
      }
      function abortWith(signal) {
        cleanup();
        controller.abort(signal.reason);
      }
      for (var i = 0; i < list.length; i++) {
        (function (signal) {
          if (!signal) return;
          if (signal.aborted) { abortWith(signal); return; }
          var handler = function () { abortWith(signal); };
          try {
            signal.addEventListener('abort', handler, { once: true });
            listeners.push({ signal: signal, handler: handler });
          } catch (e) { /* ignore */ }
        })(list[i]);
      }
      return controller.signal;
    });

    define(g.AbortSignal, 'timeout', function (milliseconds) {
      var controller = new AbortController();
      var timer = setTimeout(function () {
        var reason;
        try { reason = new DOMException('signal timed out', 'TimeoutError'); } catch (e) { reason = new Error('timeout'); }
        controller.abort(reason);
      }, milliseconds);
      try {
        if (typeof timer === 'object' && timer && typeof timer.unref === 'function') timer.unref();
      } catch (e) { /* ignore */ }
      return controller.signal;
    });
  }

  // ---- Promise.withResolvers / try（Chrome 119 / 128）----
  if (g.Promise) {
    define(g.Promise, 'withResolvers', function () {
      var resolve, reject;
      var promise = new g.Promise(function (res, rej) { resolve = res; reject = rej; });
      return { promise: promise, resolve: resolve, reject: reject };
    });
    define(g.Promise, 'try', function (callback) {
      return new g.Promise(function (resolve) { resolve(callback()); });
    });
  }

  // ---- Object.groupBy / Map.groupBy（Chrome 117）----
  if (g.Object) {
    define(g.Object, 'groupBy', function (items, callback) {
      var result = Object.create(null);
      var index = 0;
      if (items === null || items === undefined) return result;
      for (var item of items) {
        var key = callback(item, index++);
        if (typeof key === 'symbol') {
          if (!Object.prototype.hasOwnProperty.call(result, key)) result[key] = [];
          result[key].push(item);
        } else {
          key = String(key);
          if (!Object.prototype.hasOwnProperty.call(result, key)) result[key] = [];
          result[key].push(item);
        }
      }
      return result;
    });
  }
  if (g.Map) {
    define(g.Map, 'groupBy', function (items, callback) {
      var result = new Map();
      var index = 0;
      if (items === null || items === undefined) return result;
      for (var item of items) {
        var key = callback(item, index++);
        if (!result.has(key)) result.set(key, []);
        result.get(key).push(item);
      }
      return result;
    });
  }

  // ---- Array 非破坏性方法（Chrome 110）----
  define(Array.prototype, 'toSorted', function (compareFn) {
    return Array.prototype.slice.call(this).sort(compareFn);
  });
  define(Array.prototype, 'toReversed', function () {
    return Array.prototype.slice.call(this).reverse();
  });
  define(Array.prototype, 'toSpliced', function () {
    var copy = Array.prototype.slice.call(this);
    copy.splice.apply(copy, Array.prototype.slice.call(arguments));
    return copy;
  });
  define(Array.prototype, 'with', function (index, value) {
    var length = this.length >>> 0;
    var normalized = index < 0 ? length + index : index;
    var copy = Array.prototype.slice.call(this);
    copy[normalized] = value;
    return copy;
  });
  define(Array, 'fromAsync', function (iterable, mapFn) {
    return (async function () {
      var result = [];
      var index = 0;
      for await (var item of iterable) {
        result.push(mapFn ? await mapFn(item, index++) : item);
      }
      return result;
    })();
  });

  // ---- TypedArray 非破坏性方法 ----
  [
    'Int8Array', 'Uint8Array', 'Uint8ClampedArray', 'Int16Array', 'Uint16Array',
    'Int32Array', 'Uint32Array', 'Float32Array', 'Float64Array', 'BigInt64Array', 'BigUint64Array'
  ].forEach(function (name) {
    var Ctor = g[name];
    if (!Ctor || !Ctor.prototype) return;
    define(Ctor.prototype, 'toSorted', function (compareFn) {
      var copy = new Ctor(this);
      copy.sort(compareFn);
      return copy;
    });
    define(Ctor.prototype, 'toReversed', function () {
      return new Ctor(this).reverse();
    });
  });

  // ---- Set 集合运算（Chrome 122）----
  if (g.Set && g.Set.prototype) {
    var sp = g.Set.prototype;
    define(sp, 'union', function (other) {
      var result = new Set(this);
      for (var value of other) result.add(value);
      return result;
    });
    define(sp, 'intersection', function (other) {
      var result = new Set();
      for (var value of other) if (this.has(value)) result.add(value);
      return result;
    });
    define(sp, 'difference', function (other) {
      var result = new Set(this);
      for (var value of other) result.delete(value);
      return result;
    });
    define(sp, 'symmetricDifference', function (other) {
      var result = new Set(this);
      for (var value of other) {
        if (result.has(value)) result.delete(value); else result.add(value);
      }
      return result;
    });
    define(sp, 'isSubsetOf', function (other) {
      for (var value of this) if (!other.has(value)) return false;
      return true;
    });
    define(sp, 'isSupersetOf', function (other) {
      for (var value of other) if (!this.has(value)) return false;
      return true;
    });
    define(sp, 'isDisjointFrom', function (other) {
      for (var value of this) if (other.has(value)) return false;
      return true;
    });
  }

  // ---- URL.parse / URL.canParse（Chrome 126 / 120）----
  if (g.URL) {
    define(g.URL, 'parse', function (url, base) {
      try { return new g.URL(url, base); } catch (e) { return null; }
    });
    define(g.URL, 'canParse', function (url, base) {
      try { new g.URL(url, base); return true; } catch (e) { return false; }
    });
  }

  // ---- Element.checkVisibility（Chrome 105）----
  if (g.Element && g.Element.prototype) {
    define(g.Element.prototype, 'checkVisibility', function () {
      if (!this.isConnected) return false;
      var node = this;
      while (node && node.nodeType === 1) {
        var style = g.getComputedStyle(node);
        if (!style) break;
        if (style.display === 'none' || style.visibility === 'hidden' || style.visibility === 'collapse') return false;
        node = node.parentElement;
      }
      return true;
    });
  }

  // ---- scheduler.postTask / scheduler.yield（Chrome 94 / 129）----
  if (typeof g.scheduler === 'undefined') {
    try { g.scheduler = {}; } catch (e) { /* ignore */ }
  }
  if (g.scheduler) {
    define(g.scheduler, 'postTask', function (callback, options) {
      var delay = (options && options.delay) || 0;
      return new g.Promise(function (resolve, reject) {
        setTimeout(function () {
          try { resolve(callback()); } catch (error) { reject(error); }
        }, delay);
      });
    });
    define(g.scheduler, 'yield', function () {
      return new g.Promise(function (resolve) { setTimeout(resolve, 0); });
    });
  }

  // ---- String 规范化辅助（Chrome 111）----
  if (g.String && g.String.prototype) {
    define(g.String.prototype, 'isWellFormed', function () {
      return !/[\uD800-\uDBFF](?![\uDC00-\uDFFF])|(?<![\uD800-\uDBFF])[\uDC00-\uDFFF]/.test(String(this));
    });
    define(g.String.prototype, 'toWellFormed', function () {
      var input = String(this);
      var output = '';
      for (var i = 0; i < input.length; i++) {
        var code = input.charCodeAt(i);
        if (code >= 0xD800 && code <= 0xDBFF) {
          var next = input.charCodeAt(i + 1);
          if (next >= 0xDC00 && next <= 0xDFFF) { output += input[i] + input[i + 1]; i++; }
          else { output += '\uFFFD'; }
        } else if (code >= 0xDC00 && code <= 0xDFFF) {
          output += '\uFFFD';
        } else {
          output += input[i];
        }
      }
      return output;
    });
  }

  try { g.__linkgateCompatLoaded = true; } catch (e) { /* ignore */ }
})();

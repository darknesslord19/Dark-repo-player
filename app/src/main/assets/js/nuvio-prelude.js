/*
 * Nuvio eklentileri için çalışma ortamı.
 * Nuvio'nun sağladıklarını taklit eder: fetch (Android üzerinden, CORS yok),
 * require('cheerio-without-node-native' | 'cheerio' | 'react-native-cheerio' | 'crypto-js' | 'axios'),
 * module.exports, TMDB_API_KEY, console.
 */
(function () {
  'use strict';
  var G = globalThis;
  // WebView'da window/self zaten var ve salt-okunur; yalnızca eksikse tanımla
  G.global = G;
  if (typeof G.window === 'undefined') G.window = G;
  if (typeof G.self === 'undefined') G.self = G;
  G.process = G.process || { env: {}, platform: 'android', version: 'v18.0.0', nextTick: function (f) { Promise.resolve().then(f); } };

  // ---------- console → Android günlüğü ----------
  var fmt = function (args) {
    return Array.prototype.map.call(args, function (a) {
      if (a instanceof Error) return a.message;
      if (typeof a === 'object') { try { return JSON.stringify(a); } catch (e) { return String(a); } }
      return String(a);
    }).join(' ');
  };
  G.console = {
    log: function () { NativeBridge.log('I', fmt(arguments)); },
    info: function () { NativeBridge.log('I', fmt(arguments)); },
    debug: function () {},
    warn: function () { NativeBridge.log('W', fmt(arguments)); },
    error: function () { NativeBridge.log('E', fmt(arguments)); }
  };

  // ---------- fetch ----------
  var pending = {};
  var seq = 0;

  function makeHeaders(obj) {
    var m = {};
    Object.keys(obj || {}).forEach(function (k) { m[k.toLowerCase()] = obj[k]; });
    var h = {
      get: function (n) { var v = m[String(n).toLowerCase()]; return v === undefined ? null : v; },
      has: function (n) { return String(n).toLowerCase() in m; },
      forEach: function (cb) { Object.keys(m).forEach(function (k) { cb(m[k], k); }); },
      entries: function () { return Object.entries(m)[Symbol.iterator](); },
      keys: function () { return Object.keys(m)[Symbol.iterator](); },
      raw: function () { return m; }
    };
    h[Symbol.iterator] = h.entries;
    return h;
  }

  function makeResponse(r) {
    var body = r.body || '';
    return {
      ok: r.status >= 200 && r.status < 300,
      status: r.status,
      statusText: r.statusText || '',
      url: r.url,
      redirected: !!r.redirected,
      headers: makeHeaders(r.headers),
      text: function () { return Promise.resolve(body); },
      json: function () { return Promise.resolve().then(function () { return JSON.parse(body); }); },
      arrayBuffer: function () { return Promise.resolve(new TextEncoder().encode(body).buffer); },
      clone: function () { return makeResponse(r); }
    };
  }

  function normHeaders(h) {
    if (!h) return {};
    var o = {};
    if (Array.isArray(h)) { h.forEach(function (p) { o[p[0]] = String(p[1]); }); return o; }
    if (typeof h.forEach === 'function' && typeof h.get === 'function') { h.forEach(function (v, k) { o[k] = String(v); }); return o; }
    Object.keys(h).forEach(function (k) { if (h[k] != null) o[k] = String(h[k]); });
    return o;
  }

  G.__fetchDone = function (id, res) {
    var p = pending[id];
    if (!p) return;
    delete pending[id];
    if (res.error) p.reject(new TypeError(res.error));
    else p.resolve(makeResponse(res));
  };

  G.fetch = function (input, opts) {
    opts = opts || {};
    var url = typeof input === 'string' ? input : (input && input.url) || String(input);
    var body = opts.body;
    if (body != null && typeof body !== 'string') {
      body = (typeof URLSearchParams !== 'undefined' && body instanceof URLSearchParams) ? body.toString() : JSON.stringify(body);
    }
    var id = ++seq;
    return new Promise(function (resolve, reject) {
      pending[id] = { resolve: resolve, reject: reject };
      if (opts.signal && opts.signal.addEventListener) {
        opts.signal.addEventListener('abort', function () {
          if (pending[id]) { delete pending[id]; var e = new Error('Aborted'); e.name = 'AbortError'; reject(e); }
        });
      }
      NativeBridge.fetch(id, url, String(opts.method || 'GET').toUpperCase(),
        JSON.stringify(normHeaders(opts.headers)), body == null ? null : String(body), opts.redirect !== 'manual');
    });
  };

  // ---------- axios (küçük uyumluluk katmanı) ----------
  function axiosReq(cfg) {
    var url = cfg.url;
    if (cfg.params) {
      var q = new URLSearchParams(cfg.params).toString();
      if (q) url += (url.indexOf('?') >= 0 ? '&' : '?') + q;
    }
    var headers = Object.assign({}, cfg.headers || {});
    var data = cfg.data;
    if (data != null && typeof data === 'object' && !(data instanceof URLSearchParams)) {
      data = JSON.stringify(data);
      if (!headers['Content-Type'] && !headers['content-type']) headers['Content-Type'] = 'application/json';
    }
    return fetch(url, { method: cfg.method || 'GET', headers: headers, body: data }).then(function (r) {
      return r.text().then(function (t) {
        var d = t;
        if (cfg.responseType !== 'text') { try { d = JSON.parse(t); } catch (e) { /* düz metin */ } }
        var res = { data: d, status: r.status, statusText: r.statusText, headers: r.headers.raw(), config: cfg };
        if (r.status >= 400 && !(cfg.validateStatus && cfg.validateStatus(r.status))) {
          var err = new Error('Request failed with status code ' + r.status); err.response = res; throw err;
        }
        return res;
      });
    });
  }
  var axios = function (a, b) { return typeof a === 'string' ? axiosReq(Object.assign({ url: a }, b || {})) : axiosReq(a); };
  ['get', 'delete', 'head'].forEach(function (m) { axios[m] = function (u, c) { return axiosReq(Object.assign({ url: u, method: m.toUpperCase() }, c || {})); }; });
  ['post', 'put', 'patch'].forEach(function (m) { axios[m] = function (u, d, c) { return axiosReq(Object.assign({ url: u, data: d, method: m.toUpperCase() }, c || {})); }; });
  axios.create = function (base) {
    var inst = function (a, b) { return axios(Object.assign({}, base, typeof a === 'string' ? Object.assign({ url: a }, b) : a)); };
    Object.keys(axios).forEach(function (k) { inst[k] = axios[k]; });
    return inst;
  };
  axios.default = axios;

  // ---------- cheerio (DOMParser üzerinde) ----------
  if (typeof Element !== 'undefined') {
    Object.defineProperty(Element.prototype, 'attribs', {
      configurable: true,
      get: function () { var o = {}; for (var i = 0; i < this.attributes.length; i++) o[this.attributes[i].name] = this.attributes[i].value; return o; }
    });
    Object.defineProperty(Element.prototype, 'name', { configurable: true, get: function () { return this.tagName.toLowerCase(); } });
    Object.defineProperty(Node.prototype, 'data', { configurable: true, get: function () { return this.nodeType === 3 || this.nodeType === 8 ? this.nodeValue : undefined; } });
  }

  // jQuery'ye özel seçicileri (:contains, :eq, :first, :last, :gt, :lt) destekleyen querySelectorAll
  var PSEUDO = /:(contains|eq|gt|lt|first|last|has)(\((?:"[^"]*"|'[^']*'|[^()]*)\))?/;
  function qsaOne(root, sel) {
    var m = sel.match(PSEUDO);
    if (!m) { try { return Array.prototype.slice.call(root.querySelectorAll(sel)); } catch (e) { return []; } }
    var before = sel.slice(0, m.index).trim() || '*';
    var after = sel.slice(m.index + m[0].length);
    var arg = m[2] ? m[2].slice(1, -1).replace(/^["']|["']$/g, '') : '';
    var list = qsaOne(root, before);
    switch (m[1]) {
      case 'contains': list = list.filter(function (el) { return el.textContent.indexOf(arg) >= 0; }); break;
      case 'eq': list = list[+arg] ? [list[+arg]] : []; break;
      case 'gt': list = list.slice(+arg + 1); break;
      case 'lt': list = list.slice(0, +arg); break;
      case 'first': list = list.slice(0, 1); break;
      case 'last': list = list.slice(-1); break;
      case 'has': list = list.filter(function (el) { return qsaOne(el, arg).length > 0; }); break;
    }
    after = after.trim();
    if (!after) return list;
    var out = [];
    var direct = after[0] === '>';
    var rest = direct ? after.slice(1).trim() : after;
    list.forEach(function (el) {
      qsaOne(el, rest).forEach(function (x) { if (!direct || x.parentNode === el) out.push(x); });
    });
    return out;
  }
  function splitSel(sel) {
    var parts = [], depth = 0, q = null, cur = '';
    for (var i = 0; i < sel.length; i++) {
      var c = sel[i];
      if (q) { if (c === q) q = null; cur += c; continue; }
      if (c === '"' || c === "'") { q = c; cur += c; continue; }
      if (c === '(' || c === '[') depth++;
      if (c === ')' || c === ']') depth--;
      if (c === ',' && depth === 0) { parts.push(cur); cur = ''; continue; }
      cur += c;
    }
    parts.push(cur);
    return parts.map(function (s) { return s.trim(); }).filter(Boolean);
  }
  function qsa(roots, sel) {
    var seen = new Set(), out = [];
    splitSel(sel).forEach(function (part) {
      roots.forEach(function (r) {
        if (!r || !r.querySelectorAll) return;
        qsaOne(r, part).forEach(function (el) { if (!seen.has(el)) { seen.add(el); out.push(el); } });
      });
    });
    return out;
  }
  function matches(el, sel) {
    if (!el || el.nodeType !== 1) return false;
    if (!PSEUDO.test(sel)) { try { return el.matches(sel); } catch (e) { return false; } }
    var root = el.ownerDocument || document;
    return qsa([root], sel).indexOf(el) >= 0;
  }

  function Wrap(nodes) {
    nodes = nodes.filter(function (n) { return n != null; });
    this.nodes = nodes;
    this.length = nodes.length;
    for (var i = 0; i < nodes.length; i++) this[i] = nodes[i];
  }
  var W = Wrap.prototype;
  var w = function (n) { return new Wrap(n); };
  W.each = function (fn) { for (var i = 0; i < this.nodes.length; i++) { if (fn.call(this.nodes[i], i, this.nodes[i]) === false) break; } return this; };
  W.map = function (fn) {
    var out = [];
    this.nodes.forEach(function (n, i) { var r = fn.call(n, i, n); if (r != null) { if (Array.isArray(r)) out.push.apply(out, r); else out.push(r); } });
    var res = new Wrap([]); res.nodes = out; res.length = out.length; out.forEach(function (v, i) { res[i] = v; });
    return res;
  };
  W.get = function (i) { return i === undefined ? this.nodes.slice() : this.nodes[i < 0 ? this.nodes.length + i : i]; };
  W.toArray = function () { return this.nodes.slice(); };
  W.find = function (sel) { return w(qsa(this.nodes, sel)); };
  W.first = function () { return w(this.nodes.slice(0, 1)); };
  W.last = function () { return w(this.nodes.slice(-1)); };
  W.eq = function (i) { return w([this.nodes[i < 0 ? this.nodes.length + i : i]]); };
  W.slice = function (a, b) { return w(this.nodes.slice(a, b)); };
  W.attr = function (name, val) {
    if (val !== undefined) { this.nodes.forEach(function (n) { if (n.setAttribute) n.setAttribute(name, val); }); return this; }
    var n = this.nodes[0];
    if (!n || !n.getAttribute) return undefined;
    if (name === undefined) return n.attribs;
    var v = n.getAttribute(name);
    return v === null ? undefined : v;
  };
  W.data = function (name) {
    var n = this.nodes[0];
    if (!n || !n.getAttribute) return undefined;
    if (name === undefined) { var o = {}; Object.keys(n.dataset || {}).forEach(function (k) { o[k] = n.dataset[k]; }); return o; }
    var v = n.getAttribute('data-' + name);
    if (v == null) return undefined;
    try { return JSON.parse(v); } catch (e) { return v; }
  };
  W.prop = function (name) { var n = this.nodes[0]; return n ? n[name] : undefined; };
  W.val = function () { var n = this.nodes[0]; return n ? (n.value !== undefined ? n.value : n.getAttribute('value')) : undefined; };
  W.text = function (t) {
    if (t !== undefined) { this.nodes.forEach(function (n) { n.textContent = t; }); return this; }
    return this.nodes.map(function (n) { return n.textContent || ''; }).join('');
  };
  W.html = function (h) {
    if (h !== undefined) { this.nodes.forEach(function (n) { n.innerHTML = h; }); return this; }
    var n = this.nodes[0];
    if (!n) return null;
    return n.nodeType === 9 ? n.documentElement.outerHTML : (n.innerHTML !== undefined ? n.innerHTML : n.textContent);
  };
  W.outerHTML = function () { var n = this.nodes[0]; return n ? n.outerHTML : null; };
  W.children = function (sel) {
    var out = [];
    this.nodes.forEach(function (n) { Array.prototype.forEach.call(n.children || [], function (c) { if (!sel || matches(c, sel)) out.push(c); }); });
    return w(out);
  };
  W.contents = function () { var out = []; this.nodes.forEach(function (n) { out.push.apply(out, n.childNodes || []); }); return w(out); };
  W.parent = function (sel) { var out = []; this.nodes.forEach(function (n) { var p = n.parentElement; if (p && out.indexOf(p) < 0 && (!sel || matches(p, sel))) out.push(p); }); return w(out); };
  W.parents = function (sel) { var out = []; this.nodes.forEach(function (n) { var p = n.parentElement; while (p) { if ((!sel || matches(p, sel)) && out.indexOf(p) < 0) out.push(p); p = p.parentElement; } }); return w(out); };
  W.closest = function (sel) { var out = []; this.nodes.forEach(function (n) { var p = n; while (p && p.nodeType === 1) { if (matches(p, sel)) { if (out.indexOf(p) < 0) out.push(p); break; } p = p.parentElement; } }); return w(out); };
  W.next = function (sel) { return w(this.nodes.map(function (n) { var x = n.nextElementSibling; return x && (!sel || matches(x, sel)) ? x : null; })); };
  W.prev = function (sel) { return w(this.nodes.map(function (n) { var x = n.previousElementSibling; return x && (!sel || matches(x, sel)) ? x : null; })); };
  W.nextAll = function (sel) { var out = []; this.nodes.forEach(function (n) { var x = n.nextElementSibling; while (x) { if (!sel || matches(x, sel)) out.push(x); x = x.nextElementSibling; } }); return w(out); };
  W.prevAll = function (sel) { var out = []; this.nodes.forEach(function (n) { var x = n.previousElementSibling; while (x) { if (!sel || matches(x, sel)) out.push(x); x = x.previousElementSibling; } }); return w(out); };
  W.siblings = function (sel) { var out = []; this.nodes.forEach(function (n) { Array.prototype.forEach.call((n.parentElement || {}).children || [], function (c) { if (c !== n && (!sel || matches(c, sel))) out.push(c); }); }); return w(out); };
  W.filter = function (f) { return w(this.nodes.filter(function (n, i) { return typeof f === 'function' ? f.call(n, i, n) : matches(n, f); })); };
  W.not = function (f) { return w(this.nodes.filter(function (n, i) { return !(typeof f === 'function' ? f.call(n, i, n) : matches(n, f)); })); };
  W.is = function (sel) { return this.nodes.some(function (n) { return matches(n, sel); }); };
  W.has = function (sel) { return w(this.nodes.filter(function (n) { return qsa([n], sel).length > 0; })); };
  W.hasClass = function (c) { return this.nodes.some(function (n) { return n.classList && n.classList.contains(c); }); };
  W.remove = function () { this.nodes.forEach(function (n) { if (n.parentNode) n.parentNode.removeChild(n); }); return this; };
  W.add = function (other) { return w(this.nodes.concat(other instanceof Wrap ? other.nodes : [other])); };
  W.end = function () { return this; };
  W.toString = function () { return this.nodes.map(function (n) { return n.outerHTML || n.textContent; }).join(''); };
  W[Symbol.iterator] = function () { return this.nodes[Symbol.iterator](); };

  function load(html) {
    var doc = new DOMParser().parseFromString(String(html == null ? '' : html), 'text/html');
    var $ = function (sel, ctx) {
      if (sel instanceof Wrap) return sel;
      if (sel == null) return w([]);
      if (sel.nodeType) return w([sel]);
      if (Array.isArray(sel)) return w(sel);
      if (typeof sel === 'string') {
        var t = sel.trim();
        if (t[0] === '<') return w(Array.prototype.slice.call(new DOMParser().parseFromString(t, 'text/html').body.childNodes));
        var roots = ctx ? $(ctx).nodes : [doc];
        return w(qsa(roots, t));
      }
      return w([]);
    };
    $.html = function (x) { return x ? $(x).outerHTML() : doc.documentElement.outerHTML; };
    $.text = function (x) { return x ? $(x).text() : (doc.body ? doc.body.textContent : ''); };
    $.root = function () { return w([doc]); };
    $.contains = function (a, b) { return a !== b && a.contains(b); };
    $.fn = W;
    return $;
  }
  var cheerio = { load: load, html: function (x) { return x instanceof Wrap ? x.outerHTML() : String(x); } };
  cheerio.default = cheerio;

  // ---------- require ----------
  G.__require = function (name) {
    switch (name) {
      case 'cheerio-without-node-native':
      case 'cheerio':
      case 'react-native-cheerio':
        return cheerio;
      case 'crypto-js':
        if (!G.CryptoJS) throw new Error('crypto-js yüklenemedi');
        return G.CryptoJS;
      case 'axios':
        return axios;
      default:
        throw new Error("Desteklenmeyen modül: " + name);
    }
  };
  G.require = G.__require;

  // ---------- eklenti tanımla / çalıştır ----------
  G.__scrapers = {};
  G.__define = function (id, code) {
    var module = { exports: {} };
    try { delete G.getStreams; } catch (e) { G.getStreams = undefined; }
    var fn = new Function('module', 'exports', 'require', 'global', 'TMDB_API_KEY', 'SCRAPER_ID',
      code + '\n;return (typeof getStreams !== "undefined") ? getStreams : undefined;');
    var local = fn(module, module.exports, G.__require, G, G.TMDB_API_KEY, id);
    var ex = module.exports;
    var gs = (ex && ex.getStreams) || (ex && ex.default && ex.default.getStreams) ||
      (typeof ex === 'function' ? ex : null) || local || G.getStreams;
    if (typeof gs !== 'function') throw new Error('getStreams bulunamadı');
    G.__scrapers[id] = gs;
    return 'ok';
  };
  G.__run = function (callId, id, tmdbId, type, season, episode) {
    var p;
    try {
      p = Promise.resolve(G.__scrapers[id](String(tmdbId), type,
        season == null ? undefined : season, episode == null ? undefined : episode));
    } catch (e) { p = Promise.reject(e); }
    p.then(function (r) {
      NativeBridge.done(callId, JSON.stringify(Array.isArray(r) ? r : (r && Array.isArray(r.streams) ? r.streams : [])));
    }).catch(function (e) {
      NativeBridge.fail(callId, String((e && e.message) || e));
    });
  };
  NativeBridge.log('I', 'Nuvio çalışma ortamı hazır');
})();

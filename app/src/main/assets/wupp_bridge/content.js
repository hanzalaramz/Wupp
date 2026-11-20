(function () {
  "use strict";

  var isXray = typeof window !== "undefined" && typeof window.wrappedJSObject === "object" && window.wrappedJSObject !== null;
  var pageWin = isXray ? window.wrappedJSObject : window;
  var hasExport = isXray && typeof exportFunction === "function";
  var hasClone = isXray && typeof cloneInto === "function";

  function unwrap(el) {
    return (el && el.wrappedJSObject) ? el.wrappedJSObject : el;
  }

  function toPageFn(fn) {
    return hasExport ? exportFunction(fn, pageWin) : fn;
  }

  function toPageObj(obj) {
    if (!hasClone) return obj;
    try {
      return cloneInto(obj, pageWin, { cloneFunctions: true });
    } catch (e) {
      return cloneInto(obj, pageWin);
    }
  }

  function definePageGetter(obj, prop, getterFn) {
    if (!obj) return false;
    try {
      var desc = toPageObj({
        configurable: true,
        enumerable: true,
        get: toPageFn(getterFn)
      });
      pageWin.Object.defineProperty(obj, prop, desc);
      return true;
    } catch (e) {
      return false;
    }
  }

  var permState = { mic: false, cam: false };

  function applyDesktopEnvironment() {
    if (pageWin.__wuppEnvApplied) return;
    try {
      pageWin.__wuppEnvApplied = true;
    } catch (e) {}

    var navProto = (pageWin.Navigator && pageWin.Navigator.prototype) || pageWin.navigator;
    if (!definePageGetter(navProto, "platform", function () { return "Win32"; })) {
      definePageGetter(pageWin.navigator, "platform", function () { return "Win32"; });
    }
    if (!definePageGetter(navProto, "oscpu", function () { return "Windows NT 10.0; Win64; x64"; })) {
      definePageGetter(pageWin.navigator, "oscpu", function () { return "Windows NT 10.0; Win64; x64"; });
    }
    if (!definePageGetter(navProto, "maxTouchPoints", function () { return 0; })) {
      definePageGetter(pageWin.navigator, "maxTouchPoints", function () { return 0; });
    }

    try {
      delete pageWin.ontouchstart;
      if (pageWin.Window && pageWin.Window.prototype) delete pageWin.Window.prototype.ontouchstart;
      if (pageWin.Document && pageWin.Document.prototype) delete pageWin.Document.prototype.ontouchstart;
      if (pageWin.HTMLElement && pageWin.HTMLElement.prototype) delete pageWin.HTMLElement.prototype.ontouchstart;
    } catch (e) {}

    try {
      var scrProto = (pageWin.Screen && pageWin.Screen.prototype) || pageWin.screen;
      definePageGetter(scrProto, "width", function () { return 1366; });
      definePageGetter(scrProto, "height", function () { return 768; });
      definePageGetter(scrProto, "availWidth", function () { return 1366; });
      definePageGetter(scrProto, "availHeight", function () { return 728; });
      definePageGetter(scrProto, "colorDepth", function () { return 24; });
      definePageGetter(scrProto, "pixelDepth", function () { return 24; });
    } catch (e) {}

    try {
      if (typeof pageWin.matchMedia === "function") {
        var origMatchMedia = pageWin.matchMedia.bind(pageWin);
        var wrappedMatchMedia = function (q) {
          var res = origMatchMedia(q);
          if (typeof q === "string") {
            var ql = q.toLowerCase();
            var forced = null;
            if (ql.indexOf("pointer: fine") !== -1 || ql.indexOf("any-pointer: fine") !== -1 ||
                ql.indexOf("hover: hover") !== -1 || ql.indexOf("any-hover: hover") !== -1) {
              forced = true;
            } else if (ql.indexOf("pointer: coarse") !== -1 || ql.indexOf("any-pointer: coarse") !== -1 ||
                ql.indexOf("hover: none") !== -1 || ql.indexOf("any-hover: none") !== -1) {
              forced = false;
            }
            if (forced !== null) {
              var mql = toPageObj({
                matches: forced,
                media: q,
                onchange: null
              });
              mql.addListener = toPageFn(function () {});
              mql.removeListener = toPageFn(function () {});
              mql.addEventListener = toPageFn(function () {});
              mql.removeEventListener = toPageFn(function () {});
              mql.dispatchEvent = toPageFn(function () { return false; });
              return mql;
            }
          }
          return res;
        };
        pageWin.matchMedia = toPageFn(wrappedMatchMedia);
      }
    } catch (e) {}

    try {
      var permsObj = pageWin.navigator && pageWin.navigator.permissions;
      if (permsObj && typeof permsObj.query === "function") {
        var origPermQuery = permsObj.query.bind(permsObj);
        var makePermStatus = function (stateVal, nameVal) {
          var st = toPageObj({ state: stateVal, name: nameVal, onchange: null });
          st.addEventListener = toPageFn(function () {});
          st.removeEventListener = toPageFn(function () {});
          st.dispatchEvent = toPageFn(function () { return false; });
          return st;
        };
        var wrappedQuery = function (desc) {
          var nm = (desc && desc.name ? String(desc.name) : "").toLowerCase();
          if (nm === "microphone" || nm === "camera") {
            var hasNative = nm === "camera" ? !!permState.cam : !!permState.mic;
            return new pageWin.Promise(toPageFn(function (resolve) {
              origPermQuery(desc).then(toPageFn(function (status) {
                if (status && status.state === "granted") {
                  resolve(status);
                } else if (hasNative) {
                  resolve(makePermStatus("granted", nm));
                } else if (status && status.state === "denied") {
                  resolve(makePermStatus("prompt", nm));
                } else {
                  resolve(status || makePermStatus("prompt", nm));
                }
              }), toPageFn(function () {
                resolve(makePermStatus(hasNative ? "granted" : "prompt", nm));
              }));
            }));
          }
          return origPermQuery(desc);
        };
        permsObj.query = toPageFn(wrappedQuery);
      }
    } catch (e) {}
  }

  function enforceDesktopViewport() {
    try {
      var vp = document.querySelector('meta[name="viewport"]');
      if (vp) {
        if ((vp.getAttribute("content") || "") !== "width=1024") {
          vp.setAttribute("content", "width=1024");
        }
      } else if (document.head) {
        var m = document.createElement("meta");
        m.name = "viewport";
        m.content = "width=1024";
        document.head.appendChild(m);
      }
    } catch (e) {}
  }

  applyDesktopEnvironment();
  enforceDesktopViewport();
  try {
    var vpObs = new MutationObserver(function () { enforceDesktopViewport(); });
    vpObs.observe(document.documentElement || document, {
      childList: true,
      subtree: true,
      attributes: true,
      attributeFilter: ["content", "name"]
    });
    if (document.readyState === "loading") {
      document.addEventListener("DOMContentLoaded", enforceDesktopViewport);
    }
  } catch (e) {}

  if (window !== window.top) {
    return;
  }

  function newPageObj() {
    return new pageWin.Object();
  }

  function newPageArr(items) {
    var arr = new pageWin.Array();
    if (items && items.length) {
      for (var i = 0; i < items.length; i++) arr.push(items[i]);
    }
    return arr;
  }

  var waModCache = {};
  var waModulesMapScanned = false;

  function requireWa(id) {
    try {
      if (typeof pageWin.require === "function") {
        return pageWin.require(id);
      }
    } catch (e) {}
    return null;
  }

  function findWaModule(cacheKey, candidateIds, predicate) {
    if (waModCache[cacheKey]) return waModCache[cacheKey];
    if (candidateIds && candidateIds.length) {
      for (var i = 0; i < candidateIds.length; i++) {
        var mod = requireWa(candidateIds[i]);
        if (mod && (!predicate || predicate(mod))) {
          waModCache[cacheKey] = mod;
          return mod;
        }
      }
    }
    try {
      var dbg = pageWin.__debug;
      var map = dbg && dbg.modulesMap;
      if (map && typeof map === "object") {
        var keys = Object.keys(map);
        for (var k = 0; k < keys.length; k++) {
          var id = keys[k];
          if (id.indexOf("WAWeb") !== 0 && id.indexOf("useWAWeb") !== 0) continue;
          if (!/(?:Call|Chat|Wid|Cmd|Voip|QueryExist|FindChat)/i.test(id)) continue;
          var m = requireWa(id);
          if (m && predicate && predicate(m)) {
            waModCache[cacheKey] = m;
            return m;
          }
        }
        waModulesMapScanned = true;
      }
    } catch (e) {}
    return null;
  }

  function resolveCallCollection() {
    var m = findWaModule("CallCollection", ["WAWebCallCollection"], function (mod) {
      var col = (mod && (mod.CallCollection || mod.default || mod));
      return !!(col && typeof col === "object" && ("activeCall" in col) && typeof col.getModelsArray === "function");
    });
    return m ? (m.CallCollection || m.default || m) : null;
  }

  function resolveChatCollection() {
    var m = findWaModule("ChatCollection", ["WAWebChatCollection"], function (mod) {
      var col = (mod && (mod.ChatCollection || mod.default || mod));
      return !!(col && typeof col === "object" && typeof col.get === "function" && typeof col.getActive === "function");
    });
    return m ? (m.ChatCollection || m.default || m) : null;
  }

  function resolveCmd() {
    var m = findWaModule("Cmd", ["WAWebCmd"], function (mod) {
      var c = (mod && (mod.Cmd || mod.default || mod));
      return !!(c && typeof c.openChatBottom === "function");
    });
    return m ? (m.Cmd || m.default || m) : null;
  }

  function resolveWidFactory() {
    return findWaModule("WidFactory", ["WAWebWidFactory"], function (mod) {
      return !!(mod && typeof mod.createWid === "function");
    });
  }

  function resolveVoipStackSync() {
    var m = findWaModule("VoipStackInterface", ["WAWebVoipStackInterface"], function (mod) {
      return !!(mod && (typeof mod.getCachedVoipStackInterface === "function" || typeof mod.getVoipStackInterface === "function"));
    });
    if (!m) return null;
    try {
      if (typeof m.getCachedVoipStackInterface === "function") {
        return m.getCachedVoipStackInterface();
      }
    } catch (e) {}
    return null;
  }

  function getVoipStackAsync(cb) {
    var m = findWaModule("VoipStackInterface", ["WAWebVoipStackInterface"], function (mod) {
      return !!(mod && typeof mod.getVoipStackInterface === "function");
    });
    if (!m) { cb(null); return; }
    try {
      var cached = typeof m.getCachedVoipStackInterface === "function" ? m.getCachedVoipStackInterface() : null;
      if (cached) { cb(cached); return; }
      var pr = m.getVoipStackInterface();
      if (pr && typeof pr.then === "function") {
        pr.then(toPageFn(function (stack) { cb(stack || null); }), toPageFn(function () { cb(null); }));
      } else {
        cb(pr || null);
      }
    } catch (e) {
      cb(null);
    }
  }

  function ensureVoipBundlesLoaded(cb) {
    var startMod = findWaModule("VoipStartCall", ["WAWebVoipStartCall"], function (mod) {
      return !!(mod && typeof mod.startWAWebVoipCall === "function");
    });
    var stackMod = findWaModule("VoipStackInterface", ["WAWebVoipStackInterface"], function (mod) {
      return !!(mod && typeof mod.getVoipStackInterface === "function");
    });
    if (startMod && stackMod) {
      cb();
      return;
    }
    var bl = requireWa("Bootloader");
    if (!bl || typeof bl.loadModules !== "function") {
      cb();
      return;
    }
    var done = false;
    var finish = function () {
      if (done) return;
      done = true;
      cb();
    };
    setTimeout(finish, 2600);
    try {
      var comps = [];
      try {
        var cmap = bl.__debug && bl.__debug.componentMap;
        var baseComps = ["WAWebCallButtons.react", "WAWebVoipCallsTabDrawerFlow.react", "WAWebVoipStackInterfaceImpl"];
        for (var i = 0; i < baseComps.length; i++) {
          if (!cmap || cmap[baseComps[i]]) comps.push(baseComps[i]);
        }
        if (cmap && comps.length < 3) {
          var cKeys = Object.keys(cmap);
          for (var j = 0; j < cKeys.length && comps.length < 6; j++) {
            if (/CallButtons|VoipCallsTab|VoipStackInterface/i.test(cKeys[j]) && comps.indexOf(cKeys[j]) === -1) {
              comps.push(cKeys[j]);
            }
          }
        }
      } catch (e2) {
        comps = ["WAWebCallButtons.react", "WAWebVoipCallsTabDrawerFlow.react", "WAWebVoipStackInterfaceImpl"];
      }
      if (!comps.length) { finish(); return; }
      bl.loadModules(newPageArr(comps), toPageFn(finish), "wupp");
    } catch (e) {
      finish();
    }
  }

  var nativePort = null;
  var pendingOutbox = [];
  var ringMuted = false;
  var videoCssActive = false;

  function sendToApp(payload) {
    try {
      if (nativePort) {
        nativePort.postMessage(payload);
        return;
      }
    } catch (e) {
      nativePort = null;
    }
    if (pendingOutbox.length < 32) {
      pendingOutbox.push(payload);
    }
  }

  function flushOutbox() {
    if (!nativePort) return;
    while (pendingOutbox.length > 0) {
      var item = pendingOutbox.shift();
      try {
        nativePort.postMessage(item);
      } catch (e) {
        pendingOutbox.unshift(item);
        nativePort = null;
        break;
      }
    }
  }

  function classifySelfVideo(v) {
    try {
      if (v.getAttribute("data-wupp-self") === "1") return true;
      var pv = unwrap(v);
      var so = pv && pv.srcObject;
      if (so && typeof so.getVideoTracks === "function") {
        var tracks = so.getVideoTracks();
        for (var i = 0; i < tracks.length; i++) {
          var tr = tracks[i];
          var lbl = String((tr && tr.label) || "").toLowerCase();
          if (/camera|front|back|rear|facing/.test(lbl)) return true;
          try {
            var st = typeof tr.getSettings === "function" ? tr.getSettings() : null;
            if (st && (st.facingMode === "user" || st.facingMode === "environment")) {
              return true;
            }
          } catch (e2) {}
        }
      }
      var cs = window.getComputedStyle ? window.getComputedStyle(v) : null;
      var tf = cs && cs.transform;
      if (tf && tf.indexOf("matrix(-") === 0) return true;
    } catch (e) {}
    return false;
  }

  function syncVideoLayout() {
    var videos = document.querySelectorAll("video");
    var activeVideos = [];
    var playingAny = false;
    var remotePlaying = false;
    for (var i = 0; i < videos.length; i++) {
      var v = videos[i];
      var pv = unwrap(v);
      var hasMedia = !!(v.readyState >= 1 || v.videoWidth > 0 || (pv && pv.srcObject));
      if (hasMedia) {
        activeVideos.push(v);
        if (v.readyState >= 2 && v.videoWidth > 0) {
          playingAny = true;
        }
      }
      if (videoCssActive && hasMedia) {
        var p = v.parentElement;
        while (p && p !== document.documentElement) {
          if (!p.hasAttribute("data-wupp-vpath")) {
            p.setAttribute("data-wupp-vpath", "1");
          }
          p = p.parentElement;
        }
      }
    }

    if (!videoCssActive || !activeVideos.length) {
      var oldPaths = document.querySelectorAll("[data-wupp-vpath]");
      for (var op = 0; op < oldPaths.length; op++) {
        oldPaths[op].removeAttribute("data-wupp-vpath");
      }
      for (var ov = 0; ov < videos.length; ov++) {
        videos[ov].removeAttribute("data-wupp-role");
      }
      if (activeVideos.length === 1 && !classifySelfVideo(activeVideos[0]) && activeVideos[0].videoWidth > 0) {
        remotePlaying = true;
      } else if (activeVideos.length >= 2) {
        remotePlaying = playingAny;
      }
      return { count: videos.length, activeVid: playingAny, remoteVid: remotePlaying };
    }

    if (activeVideos.length === 1) {
      var sole = activeVideos[0];
      if (classifySelfVideo(sole)) {
        sole.setAttribute("data-wupp-self", "1");
      } else if (sole.readyState >= 2 && sole.videoWidth > 0) {
        remotePlaying = true;
      }
      if (sole.getAttribute("data-wupp-role") !== "main") {
        sole.setAttribute("data-wupp-role", "main");
      }
    } else {
      var selfIdx = -1;
      for (var s = 0; s < activeVideos.length; s++) {
        if (classifySelfVideo(activeVideos[s])) {
          selfIdx = s;
          break;
        }
      }
      if (selfIdx === -1) {
        for (var m = activeVideos.length - 1; m >= 0; m--) {
          if (activeVideos[m].muted) {
            selfIdx = m;
            break;
          }
        }
      }
      if (selfIdx === -1) selfIdx = activeVideos.length - 1;
      for (var r = 0; r < activeVideos.length; r++) {
        var targetRole = (r === selfIdx) ? "pip" : "main";
        if (r === selfIdx) {
          activeVideos[r].setAttribute("data-wupp-self", "1");
        } else if (activeVideos[r].readyState >= 2 && activeVideos[r].videoWidth > 0) {
          remotePlaying = true;
        }
        if (activeVideos[r].getAttribute("data-wupp-role") !== targetRole) {
          activeVideos[r].setAttribute("data-wupp-role", targetRole);
        }
      }
    }
    return { count: videos.length, activeVid: playingAny, remoteVid: remotePlaying };
  }

  function applyVideoCss(active) {
    videoCssActive = !!active;
    var rootEl = document.documentElement;
    if (rootEl) {
      if (videoCssActive) rootEl.classList.add("wupp-video-active");
      else rootEl.classList.remove("wupp-video-active");
    }
    var id = "wupp-call-video-css";
    var st = document.getElementById(id);
    if (!videoCssActive) {
      if (st) st.remove();
      syncVideoLayout();
      return;
    }
    if (!st) {
      st = document.createElement("style");
      st.id = id;
      (document.head || document.documentElement).appendChild(st);
    }
    st.textContent =
      "html.wupp-video-active, html.wupp-video-active body { background: #000 !important; overflow: hidden !important; margin: 0 !important; padding: 0 !important; }" +
      "html.wupp-video-active body *:not(video) { visibility: hidden !important; pointer-events: none !important; }" +
      "html.wupp-video-active [data-wupp-vpath=\"1\"] { transform: none !important; filter: none !important; backdrop-filter: none !important; perspective: none !important; contain: none !important; clip-path: none !important; clip: auto !important; overflow: visible !important; }" +
      "html.wupp-video-active video { visibility: visible !important; opacity: 1 !important; display: block !important; }" +
      "html.wupp-video-active video[data-wupp-role=\"main\"] { position: fixed !important; inset: 0 !important; top: 0 !important; left: 0 !important; right: 0 !important; bottom: 0 !important; width: 100vw !important; height: 100vh !important; max-width: none !important; max-height: none !important; border-radius: 0 !important; object-fit: cover !important; z-index: 9998 !important; background: #000 !important; }" +
      "html.wupp-video-active video[data-wupp-role=\"pip\"] { position: fixed !important; top: 88px !important; left: auto !important; right: 16px !important; bottom: auto !important; width: 108px !important; height: 156px !important; max-width: none !important; max-height: none !important; border-radius: 18px !important; object-fit: cover !important; z-index: 9999 !important; box-shadow: 0 8px 24px rgba(0,0,0,0.45) !important; }";
    syncVideoLayout();
  }

  function applyRingMute(muted) {
    ringMuted = !!muted;
    try {
      var audios = document.querySelectorAll("audio");
      for (var i = 0; i < audios.length; i++) {
        audios[i].muted = ringMuted;
      }
    } catch (e) {}
  }

  function textOf(el) {
    return ((el && (el.innerText || el.textContent)) || "").trim();
  }

  function hasIcon(name) {
    return !!document.querySelector('[data-icon="' + name + '"]');
  }

  function findBtnByLabel(re) {
    var els = document.querySelectorAll("button,[role=\"button\"],[aria-label]");
    for (var i = 0; i < els.length; i++) {
      var al = els[i].getAttribute("aria-label") || "";
      if (re.test(al) || re.test(textOf(els[i]))) return els[i];
    }
    return null;
  }

  function readPairingCode() {
    var attrEl = document.querySelector("[data-link-code]");
    if (attrEl) {
      var rawAttr = (attrEl.getAttribute("data-link-code") || "").replace(/[^A-Z0-9]/gi, "").toUpperCase();
      if (/^[A-Z0-9]{8}$/.test(rawAttr)) {
        return rawAttr.slice(0, 4) + "-" + rawAttr.slice(4, 8);
      }
    }
    var boxes = document.querySelectorAll('[data-testid*="link-device-phone-number-code"], [aria-details*="link-device-phone-number-code"], [aria-label*="Enter code" i], [aria-label*="pairing code" i], [data-ref]');
    for (var b = 0; b < boxes.length; b++) {
      var raw = textOf(boxes[b]).replace(/[^A-Z0-9-]/gi, "").toUpperCase();
      var m = raw.match(/([A-Z0-9]{4}-?[A-Z0-9]{4})/);
      if (m && m[1] && !/LINK|CODE|WHAT|PHON|NUMB/.test(m[1])) {
        var c = m[1].replace(/-/g, "");
        if (c.length === 8) return c.slice(0, 4) + "-" + c.slice(4, 8);
      }
    }
    var singleEls = document.querySelectorAll("span, div, strong, b");
    var bucket = [];
    var keys = [];
    for (var j = 0; j < singleEls.length; j++) {
      var el = singleEls[j];
      if (el.children.length > 0) continue;
      var t = textOf(el).toUpperCase();
      if (!/^[A-Z0-9]{1}$/.test(t)) continue;
      var parent = (el.parentElement && el.parentElement.parentElement) || el.parentElement;
      if (!parent) continue;
      var idx = keys.indexOf(parent);
      if (idx === -1) {
        idx = keys.length;
        keys.push(parent);
        bucket.push("");
      }
      bucket[idx] += t;
    }
    for (var k = 0; k < bucket.length; k++) {
      if (/^[A-Z0-9]{8}$/.test(bucket[k])) {
        return bucket[k].slice(0, 4) + "-" + bucket[k].slice(4, 8);
      }
    }
    return "";
  }

  function isSyncingChatsScreen() {
    if (document.querySelector("canvas[aria-label]")) return false;
    var bodyTxt = norm(textOf(document.body));
    if (/loading your chats|downloading messages|organizing messages|optimizing|synchronizing/i.test(bodyTxt)) {
      return true;
    }
    return !!findBtnByLabel(/^log out$/i);
  }

  function readNativeActiveCallState() {
    try {
      var callStore = resolveCallCollection();
      var ac = callStore && callStore.activeCall;
      if (!ac) return null;
      var stNum = typeof ac.getState === "function" ? ac.getState() : ac.state;
      var mapped = "";
      // WAWebVoipWaCallEnums.CallState
      if (stNum === 3 || stNum === 8) {
        mapped = "INCOMING_RINGING";
      } else if (stNum === 1 || stNum === 12 || stNum === 14) {
        mapped = "CALLING";
      } else if (stNum === 2 || stNum === 11) {
        mapped = "RINGING";
      } else if (stNum === 4 || stNum === 5 || stNum === 9) {
        mapped = "CONNECTING";
      } else if (stNum === 6) {
        mapped = "ACTIVE";
      } else {
        return null;
      }
      var peerHint = "";
      try {
        peerHint = (ac.peerJid && (ac.peerJid.user || (ac.peerJid.toString && ac.peerJid.toString()))) || "";
      } catch (e2) {}
      return {
        state: mapped,
        isVideo: !!ac.isVideo,
        peerHint: String(peerHint || ""),
        canAnswer: mapped === "INCOMING_RINGING"
      };
    } catch (e) {
      return null;
    }
  }

  var lastAuth = "";
  var lastCode = "";
  var lastCallSig = "";
  var lastCallState = "IDLE";
  var powerMode = "sleep";
  var dialInFlight = false;
  var domObs = null;
  var domObsActive = false;
  var fastPollId = null;
  var slowPollId = null;
  var hookedCallCol = null;

  function applyIdleSleepCss(sleeping) {
    var rootEl = document.documentElement;
    if (rootEl) {
      if (sleeping) rootEl.classList.add("wupp-idle-sleep");
      else rootEl.classList.remove("wupp-idle-sleep");
    }
    var id = "wupp-idle-sleep-css";
    var st = document.getElementById(id);
    if (!sleeping) {
      if (st) st.remove();
      return;
    }
    if (!st) {
      st = document.createElement("style");
      st.id = id;
      (document.head || document.documentElement).appendChild(st);
    }
    st.textContent =
      "html.wupp-idle-sleep #pane-side, html.wupp-idle-sleep #main { display: none !important; }" +
      "html.wupp-idle-sleep * { animation: none !important; transition: none !important; }";
  }

  function ensureCallCollectionHooked() {
    try {
      var callCol = resolveCallCollection();
      if (!callCol || hookedCallCol === callCol) return !!hookedCallCol;
      if (typeof callCol.on === "function") {
        hookedCallCol = callCol;
        var handler = toPageFn(function () {
          try { inspectDom(); } catch (e) {}
        });
        callCol.on("change add remove change:activeCall", handler);
        return true;
      }
    } catch (e) {}
    return !!hookedCallCol;
  }

  function syncPowerUiState() {
    var shouldSleep = powerMode === "sleep" &&
      lastAuth === "AUTHENTICATED" &&
      lastCallState === "IDLE" &&
      !dialInFlight &&
      !videoCssActive;
    applyIdleSleepCss(shouldSleep);
    updateObserverActivity();
  }

  function updateObserverActivity() {
    var root = document.documentElement || document.body;
    var needFast = (lastAuth !== "AUTHENTICATED") ||
      (powerMode !== "sleep") ||
      (lastCallState !== "IDLE") ||
      dialInFlight;

    ensureCallCollectionHooked();

    if (needFast) {
      if (slowPollId) {
        clearInterval(slowPollId);
        slowPollId = null;
      }
      if (root && domObs && !domObsActive) {
        try {
          domObs.observe(root, {
            childList: true,
            subtree: true,
            attributes: true,
            attributeFilter: ["aria-label", "data-icon", "src"]
          });
          domObsActive = true;
        } catch (e) {}
      }
      if (!fastPollId) {
        fastPollId = setInterval(function () {
          try { inspectDom(); } catch (e) {}
        }, 350);
      }
    } else {
      if (fastPollId) {
        clearInterval(fastPollId);
        fastPollId = null;
      }
      if (domObs && domObsActive) {
        try { domObs.disconnect(); } catch (e) {}
        domObsActive = false;
      }
      var fallbackMs = hookedCallCol ? 30000 : 8000;
      if (!slowPollId) {
        slowPollId = setInterval(function () {
          ensureCallCollectionHooked();
          try { inspectDom(); } catch (e) {}
        }, fallbackMs);
      }
    }
  }

  function inspectDom() {
    var prevAuth = lastAuth;
    var cmdObj = resolveCmd();
    var authenticated = !!(
      document.querySelector("#pane-side") ||
      document.querySelector('[data-testid="chat-list-search"]') ||
      document.querySelector('[aria-label*="Search input" i]') ||
      (cmdObj && cmdObj.isMainLoaded === true)
    );
    if (authenticated) {
      if (lastAuth !== "AUTHENTICATED") {
        lastAuth = "AUTHENTICATED";
        lastCode = "";
        sendToApp({ type: "linkUpdate", state: "AUTHENTICATED", code: "" });
      }
    } else {
      var code = readPairingCode();
      if (code) {
        if (lastAuth !== "PAIRING_CODE_READY" || lastCode !== code) {
          lastAuth = "PAIRING_CODE_READY";
          lastCode = code;
          sendToApp({ type: "linkUpdate", state: "PAIRING_CODE_READY", code: code });
        }
      } else if (document.querySelector("canvas[aria-label]") || findBtnByLabel(/link with phone/i)) {
        if (lastAuth !== "NEEDS_PAIRING_CODE") {
          lastAuth = "NEEDS_PAIRING_CODE";
          sendToApp({ type: "linkUpdate", state: "NEEDS_PAIRING_CODE", code: "" });
        }
      } else if (isSyncingChatsScreen()) {
        if (lastAuth !== "SYNCING_CHATS") {
          lastAuth = "SYNCING_CHATS";
          lastCode = "";
          sendToApp({ type: "linkUpdate", state: "LOADING", code: "" });
          sendToApp({ type: "pairingStatus", status: "Phone linked! Syncing WhatsApp Web chats…" });
        }
      }
    }

    var nativeCall = readNativeActiveCallState();
    var acceptBtn = findBtnByLabel(/^(accept|answer|join call)/i) ||
      (hasIcon("call-accept") ? document.querySelector('[data-icon="call-accept"]') : null);
    var endBtn = findBtnByLabel(/^(end call|hang up|leave call)/i) ||
      document.querySelector('[data-icon="call-end"],[data-icon="end-call"]');
    var vidInfo = syncVideoLayout();
    var activeVid = vidInfo.remoteVid;
    var isVideo = (nativeCall && nativeCall.isVideo) || vidInfo.activeVid || vidInfo.count > 0 || !!findBtnByLabel(/turn off camera|turn on camera|flip camera/i);
    var state = "IDLE";
    var canAnswer = false;
    if (nativeCall) {
      state = nativeCall.state;
      canAnswer = nativeCall.canAnswer || !!acceptBtn;
    } else if (acceptBtn) {
      state = "INCOMING_RINGING";
      canAnswer = true;
    } else if (endBtn) {
      var bodyTxt = textOf(document.body);
      var callEl = document.querySelector('[role="dialog"], [aria-label*="call" i]');
      var callTxt = callEl ? textOf(callEl) : bodyTxt;
      if (activeVid || /\b\d{1,2}:\d{2}\b/.test(callTxt)) {
        state = "ACTIVE";
      } else if (/\bringing\b/i.test(callTxt)) {
        state = "RINGING";
      } else if (/\b(calling|waiting|dialing)\b/i.test(callTxt)) {
        state = "CALLING";
      } else {
        state = "CONNECTING";
      }
    }
    var callerHint = (nativeCall && nativeCall.peerHint) || "";
    var dialog = document.querySelector('[role="dialog"], [aria-label*="call" i]');
    if (dialog) {
      var heading = dialog.querySelector('h1,h2,span[title],span[dir="auto"]');
      var hTxt = textOf(heading);
      if (hTxt) callerHint = hTxt;
    }
    var prevCallState = lastCallState;
    lastCallState = state;
    var sig = state + "|" + callerHint + "|" + isVideo + "|" + canAnswer + "|" + activeVid;
    if (sig !== lastCallSig) {
      lastCallSig = sig;
      sendToApp({
        type: "webCallEvent",
        state: state,
        callerHint: callerHint,
        isVideo: isVideo,
        canAnswer: canAnswer,
        hasActiveVideo: activeVid
      });
    }
    if (ringMuted) {
      applyRingMute(true);
    }
    if (lastAuth !== prevAuth) {
      reportEnv();
    }
    if (lastAuth !== prevAuth || lastCallState !== prevCallState) {
      syncPowerUiState();
    }
  }

  var observerStarted = false;
  function startDomObserver() {
    if (observerStarted) return;
    observerStarted = true;
    var root = document.documentElement || document.body;
    if (!root) {
      document.addEventListener("DOMContentLoaded", function () {
        observerStarted = false;
        startDomObserver();
      }, { once: true });
      return;
    }
    var pending = false;
    domObs = new MutationObserver(function () {
      if (pending) return;
      pending = true;
      setTimeout(function () {
        pending = false;
        try { inspectDom(); } catch (e) {}
      }, 140);
    });
    updateObserverActivity();
    inspectDom();
  }

  function readWaModulesDiag() {
    var out = "";
    try {
      if (typeof pageWin.require === "function") {
        try {
          var uaMod = pageWin.require("WAWebUA");
          var u = uaMod && (uaMod.UA || uaMod.default || uaMod);
          if (u) {
            out += " waOS=" + u.os + "/br=" + u.browser;
          }
        } catch (e) {}
        try {
          var gMod = pageWin.require("WAWebVoipGatingUtils");
          if (gMod) {
            var unSup = typeof gMod.getUnsupportedBrowserReason === "function" ? gMod.getUnsupportedBrowserReason() : "?";
            var dl = typeof gMod.isVoipDownloadEnabled === "function" ? gMod.isVoipDownloadEnabled() : "?";
            var en = typeof gMod.isCallingEnabled === "function" ? gMod.isCallingEnabled() : "?";
            var grp = typeof gMod.isWebGroupCallingUsable === "function" ? gMod.isWebGroupCallingUsable() : "?";
            out += " voip=[unSup:" + String(unSup) + ",dl:" + String(dl) + ",en:" + String(en) + ",grp:" + String(grp) + "]";
          }
        } catch (e) {}
        try {
          var wfOk = !!resolveWidFactory();
          var cmdOk = !!resolveCmd();
          var ccOk = !!resolveCallCollection();
          var vsOk = !!findWaModule("VoipStartCall", ["WAWebVoipStartCall"], function (m) {
            return !!(m && typeof m.startWAWebVoipCall === "function");
          });
          out += " rte=" + (wfOk ? 1 : 0) + (cmdOk ? 1 : 0) + (ccOk ? 1 : 0) + (vsOk ? 1 : 0);
        } catch (e) {}
      }
    } catch (e) {}
    return out;
  }

  function readDisabledCallTooltipDiag() {
    try {
      var hdr = document.querySelector('#main header, [data-testid="conversation-header"]');
      if (!hdr) return "";
      var btns = hdr.querySelectorAll('button, [role="button"], [tabindex]');
      var res = [];
      for (var i = 0; i < btns.length; i++) {
        var b = btns[i];
        var al = (b.getAttribute("aria-label") || b.getAttribute("title") || "").trim();
        var cs = window.getComputedStyle ? window.getComputedStyle(b) : null;
        var dis = !!b.disabled || b.getAttribute("aria-disabled") === "true" || (cs && parseFloat(cs.opacity || "1") < 0.7);
        if (dis || /call|video|voice/i.test(al)) {
          res.push((al || "?") + (dis ? "(dis)" : "(ok)"));
        }
      }
      return res.length ? (" btns=[" + res.join(";") + "]") : "";
    } catch (e) {
      return "";
    }
  }

  var sabPmChecked = false;
  var sabPmOk = false;
  function checkSabPostMessage() {
    if (sabPmChecked) return sabPmOk;
    sabPmChecked = true;
    try {
      if (typeof pageWin.MessageChannel === "function" && typeof pageWin.SharedArrayBuffer === "function") {
        var mc = new pageWin.MessageChannel();
        mc.port1.postMessage(new pageWin.SharedArrayBuffer(8));
        mc.port1.close();
        mc.port2.close();
        sabPmOk = true;
      }
    } catch (e) {
      sabPmOk = false;
    }
    return sabPmOk;
  }

  var lastEnvStr = "";
  function reportEnv() {
    try {
      var pm = checkSabPostMessage() ? "pm1" : "pm0";
      var coi = pageWin.crossOriginIsolated ? "1(nat)" : "0";
      var sab = typeof pageWin.SharedArrayBuffer === "function" ? ("nat/" + pm) : ("0/" + pm);
      var apis = "eng=gecko,RTC=" + (typeof pageWin.RTCPeerConnection === "function" ? 1 : 0) +
        ",AW=" + (typeof pageWin.AudioWorkletNode === "function" ? 1 : 0) +
        ",coi=" + coi +
        ",sab=" + sab;
      var nav = pageWin.navigator;
      var base = "plt=" + ((nav && nav.platform) || "-") +
        " | touch=" + (nav ? nav.maxTouchPoints : "?") +
        " | vw=" + window.innerWidth + "x" + window.innerHeight +
        " | " + apis +
        readWaModulesDiag() +
        readDisabledCallTooltipDiag();
      var micSt = "?";
      var devInfo = "?";
      var emit = function () {
        var full = base + " | mic=" + micSt + " | devs=" + devInfo;
        if (full !== lastEnvStr) {
          lastEnvStr = full;
          sendToApp({ type: "envDiag", diag: full });
        }
      };
      var pagePerms = pageWin.navigator && pageWin.navigator.permissions;
      var p1 = (pagePerms && pagePerms.query)
        ? pagePerms.query(toPageObj({ name: "microphone" })).then(toPageFn(function (s) { micSt = s ? s.state : "?"; }), toPageFn(function () { micSt = permState.mic ? "granted" : "err"; }))
        : Promise.resolve();
      var p2 = (navigator.mediaDevices && navigator.mediaDevices.enumerateDevices)
        ? navigator.mediaDevices.enumerateDevices().then(function (ds) {
            if (!ds) { devInfo = "0"; return; }
            var ai = 0, vi = 0, ao = 0;
            for (var d = 0; d < ds.length; d++) {
              if (ds[d].kind === "audioinput") ai++;
              else if (ds[d].kind === "videoinput") vi++;
              else if (ds[d].kind === "audiooutput") ao++;
            }
            devInfo = ai + "a/" + vi + "v/" + ao + "o";
          }).catch(function () { devInfo = "err"; })
        : Promise.resolve();
      Promise.all([p1, p2]).then(emit, emit);
    } catch (e) {}
  }

  function norm(s) {
    return (s || "").replace(/\s+/g, " ").trim();
  }

  function normLower(s) {
    return norm(s).toLowerCase();
  }

  function digitsOnly(s) {
    return (s || "").replace(/\D/g, "");
  }

  function isElementUsable(el) {
    if (!el || el.disabled) return false;
    try {
      var cs = window.getComputedStyle(el);
      if (!cs || cs.display === "none" || cs.visibility === "hidden" || cs.opacity === "0") return false;
      var r = el.getBoundingClientRect();
      return r.width > 4 && r.height > 4;
    } catch (e) {
      return true;
    }
  }

  function invokeSingleReactHandler(nodes) {
    for (var n = 0; n < nodes.length; n++) {
      var node = unwrap(nodes[n]);
      if (!node) continue;
      try {
        var keys = Object.keys(node);
        for (var k = 0; k < keys.length; k++) {
          if (keys[k].indexOf("__reactProps$") === 0) {
            var rp = node[keys[k]];
            var fn = rp && (rp.onClick || rp.onPress || rp.onMouseDown || rp.onPointerUp);
            if (typeof fn === "function") {
              fn(toPageObj({
                type: "click",
                target: node,
                currentTarget: node,
                preventDefault: toPageFn(function () {}),
                stopPropagation: toPageFn(function () {}),
                nativeEvent: new pageWin.MouseEvent("click", toPageObj({ bubbles: true, cancelable: true }))
              }));
              return true;
            }
          }
          if (keys[k].indexOf("__reactFiber$") === 0) {
            var fb = node[keys[k]];
            var mp = fb && fb.memoizedProps;
            var ffn = mp && (mp.onClick || mp.onPress);
            if (typeof ffn === "function") {
              ffn(toPageObj({
                type: "click",
                target: node,
                currentTarget: node,
                preventDefault: toPageFn(function () {}),
                stopPropagation: toPageFn(function () {}),
                nativeEvent: new pageWin.MouseEvent("click", toPageObj({ bubbles: true, cancelable: true }))
              }));
              return true;
            }
          }
        }
      } catch (e) {}
    }
    return false;
  }

  function activateDomElement(el, withReactFallback) {
    if (!el) return;
    try {
      if (el.scrollIntoView) el.scrollIntoView({ block: "center", inline: "center" });
    } catch (e) {}
    try { el.focus(); } catch (e) {}
    var r = el.getBoundingClientRect ? el.getBoundingClientRect() : null;
    var cx = (r && r.width > 0) ? (r.left + r.width / 2) : 200;
    var cy = (r && r.height > 0) ? (r.top + r.height / 2) : 200;
    var evOpts = { bubbles: true, cancelable: true, view: pageWin, clientX: cx, clientY: cy, button: 0, buttons: 1 };
    var pEl = unwrap(el);
    try { pEl.dispatchEvent(new pageWin.PointerEvent("pointerdown", toPageObj(evOpts))); } catch (e) {}
    try { pEl.dispatchEvent(new pageWin.MouseEvent("mousedown", toPageObj(evOpts))); } catch (e) {}
    try { pEl.dispatchEvent(new pageWin.PointerEvent("pointerup", toPageObj(evOpts))); } catch (e) {}
    try { pEl.dispatchEvent(new pageWin.MouseEvent("mouseup", toPageObj(evOpts))); } catch (e) {}
    try { el.click(); } catch (e) {
      try { pEl.dispatchEvent(new pageWin.MouseEvent("click", toPageObj(evOpts))); } catch (e2) {}
    }
    if (withReactFallback) {
      invokeSingleReactHandler([el, el.parentElement]);
    }
  }

  function requestNativeTapEl(el) {
    if (!el) return false;
    try {
      if (el.scrollIntoView) el.scrollIntoView({ block: "center", inline: "center" });
    } catch (e) {}
    try {
      var r = el.getBoundingClientRect();
      if (r && r.width > 0 && r.height > 0 && window.innerWidth > 0 && window.innerHeight > 0) {
        var xr = (r.left + r.width / 2) / window.innerWidth;
        var yr = (r.top + r.height / 2) / window.innerHeight;
        if (xr > 0 && xr < 1 && yr > 0 && yr < 1) {
          sendToApp({ type: "nativeTap", xRatio: xr, yRatio: yr });
          return true;
        }
      }
    } catch (e) {}
    return false;
  }

  function runPairingAutomation(digits) {
    var cleanDigits = digitsOnly(digits);
    if (!cleanDigits) return;
    var myGen = (window.__wuppPairGen || 0) + 1;
    window.__wuppPairGen = myGen;
    var attempts = 0;
    var submitted = false;
    var submittedAt = 0;
    var lastStatus = "";
    var lastAltClickAt = 0;
    var altClickCount = 0;
    var hasClickedAltLink = false;
    var lastClickedDesc = "none";

    function reportStatus(msg) {
      if (msg && msg !== lastStatus) {
        lastStatus = msg;
        sendToApp({ type: "pairingStatus", status: msg });
      }
    }

    function isBlockedText(s) {
      return /terms|privacy|policy|download|get the app|windows|mac|microsoft|help/i.test(s || "");
    }

    function findVisiblePhoneInput() {
      var selectors = [
        'input[aria-label*="phone" i]',
        'input[aria-label*="number" i]',
        'input[type="tel"]',
        'input[inputmode="tel"]',
        '[data-testid*="phone"] input',
        'input[type="text"]',
        'input:not([type="hidden"]):not([type="checkbox"]):not([type="radio"])'
      ];
      for (var s = 0; s < selectors.length; s++) {
        var list = document.querySelectorAll(selectors[s]);
        for (var i = 0; i < list.length; i++) {
          var el = list[i];
          var t = (el.type || "").toLowerCase();
          if (t === "checkbox" || t === "radio" || t === "hidden") continue;
          var ph = (el.getAttribute("placeholder") || el.getAttribute("aria-label") || "").toLowerCase();
          if (ph.indexOf("search") !== -1 || ph.indexOf("country") !== -1) continue;
          if (isElementUsable(el)) return el;
        }
      }
      return null;
    }

    function findAltLinkingElement() {
      var phoneRe = /link with phone number|log in with phone number|use phone number|phone number instead/i;
      if (document.body && document.createTreeWalker) {
        var walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, null, false);
        var node;
        while ((node = walker.nextNode())) {
          var t = norm(node.nodeValue || "");
          if (t.length < 12 || t.length > 60) continue;
          if (isBlockedText(t)) continue;
          if (!phoneRe.test(t)) continue;
          var el = node.parentElement;
          if (!el) continue;
          var cur = el;
          var bestClickable = el;
          var depth = 0;
          while (cur && cur !== document.body && depth < 5) {
            var cTxt = norm(textOf(cur));
            if (cTxt.length > 65 || isBlockedText(cTxt)) break;
            var href = cur.getAttribute ? (cur.getAttribute("href") || "") : "";
            if (href && href !== "#" && href.indexOf("web.whatsapp.com") === -1) break;
            bestClickable = cur;
            var role = cur.getAttribute ? (cur.getAttribute("role") || "") : "";
            var tag = (cur.tagName || "").toLowerCase();
            if (role === "button" || tag === "button" || tag === "a" || cur.hasAttribute("tabindex")) {
              return cur;
            }
            cur = cur.parentElement;
            depth++;
          }
          return bestClickable;
        }
      }
      return null;
    }

    function setReactInputValue(input, val) {
      input.focus();
      var pInput = unwrap(input);
      try {
        var prevVal = pInput.value;
        var tracker = pInput._valueTracker;
        if (tracker) tracker.setValue("");
        document.execCommand("selectAll", false, null);
        document.execCommand("insertText", false, val);
        if (pInput.value !== val) {
          var setter = pageWin.Object.getOwnPropertyDescriptor(pageWin.HTMLInputElement.prototype, "value").set;
          setter.call(pInput, val);
          if (tracker) tracker.setValue(prevVal);
          pInput.dispatchEvent(new pageWin.InputEvent("input", toPageObj({ bubbles: true, data: val, inputType: "insertText" })));
          pInput.dispatchEvent(new pageWin.Event("change", toPageObj({ bubbles: true })));
        }
      } catch (e) {
        try {
          var setter2 = pageWin.Object.getOwnPropertyDescriptor(pageWin.HTMLInputElement.prototype, "value").set;
          setter2.call(pInput, val);
          pInput.dispatchEvent(new pageWin.Event("input", toPageObj({ bubbles: true })));
          pInput.dispatchEvent(new pageWin.Event("change", toPageObj({ bubbles: true })));
        } catch (e2) {}
      }
      if ((pInput.value || "").indexOf(val) === -1) {
        try {
          var keys = Object.keys(pInput);
          for (var k = 0; k < keys.length; k++) {
            if (keys[k].indexOf("__reactProps$") === 0) {
              var rp = pInput[keys[k]];
              if (rp && typeof rp.onChange === "function") {
                rp.onChange(toPageObj({
                  target: pInput,
                  currentTarget: pInput,
                  preventDefault: toPageFn(function () {}),
                  stopPropagation: toPageFn(function () {})
                }));
              }
            }
          }
        } catch (e) {}
      }
    }

    function clickNextButton(input) {
      var nextBtn = document.querySelector('[data-testid*="link-device-phone-number-code-entry-next"]');
      if (nextBtn && isElementUsable(nextBtn)) {
        activateDomElement(nextBtn, true);
        lastClickedDesc = "next-testid";
        return true;
      }
      var candidates = document.querySelectorAll('button, [role="button"], a, span, div');
      var best = null;
      var bestLen = 99;
      for (var i = 0; i < candidates.length; i++) {
        var el = candidates[i];
        if (!isElementUsable(el)) continue;
        var al = (el.getAttribute("aria-label") || "").trim();
        var txt = norm(textOf(el));
        var targetStr = txt || al;
        if (targetStr.length > 0 && targetStr.length <= 20 &&
            (/^(next|continue|confirm|done|link)$/i.test(txt) || /^(next|continue|confirm|done|link)$/i.test(al))) {
          if (targetStr.length <= bestLen) {
            bestLen = targetStr.length;
            best = el;
          }
        }
      }
      if (best) {
        activateDomElement(best, true);
        lastClickedDesc = "next:" + (norm(textOf(best)) || best.getAttribute("aria-label") || "btn").slice(0, 16);
      }
      try {
        var pInput = unwrap(input);
        pInput.dispatchEvent(new pageWin.KeyboardEvent("keydown", toPageObj({ key: "Enter", code: "Enter", keyCode: 13, which: 13, bubbles: true })));
        pInput.dispatchEvent(new pageWin.KeyboardEvent("keyup", toPageObj({ key: "Enter", code: "Enter", keyCode: 13, which: 13, bubbles: true })));
      } catch (e) {}
      return !!best;
    }

    function step() {
      if (window.__wuppPairGen !== myGen) return;
      attempts++;

      var cmdObj = resolveCmd();
      var authenticated = !!(
        document.querySelector("#pane-side") ||
        document.querySelector('[data-testid="chat-list-search"]') ||
        (cmdObj && cmdObj.isMainLoaded === true)
      );
      if (authenticated) {
        sendToApp({ type: "linkUpdate", state: "AUTHENTICATED", code: "" });
        return;
      }

      if (isSyncingChatsScreen()) {
        submitted = true;
        reportStatus("Phone linked! Syncing WhatsApp Web chats…");
        sendToApp({ type: "linkUpdate", state: "LOADING", code: "" });
        if (attempts < 120) {
          setTimeout(step, 800);
        }
        return;
      }

      var code = readPairingCode();
      if (code) {
        sendToApp({ type: "linkUpdate", state: "PAIRING_CODE_READY", code: code });
        return;
      }

      var input = findVisiblePhoneInput();
      if (input) {
        if (!submitted || (Date.now() - submittedAt >= 6000)) {
          submitted = true;
          submittedAt = Date.now();
          reportStatus("Requesting 8-digit pairing code… (" + attempts + ")");
          var prefillDigits = digitsOnly(input.value || "");
          activateDomElement(input, true);
          input.focus();
          setReactInputValue(input, "+" + cleanDigits);
          var afterDigits = digitsOnly(input.value || "");
          if (prefillDigits.length > 0 && prefillDigits.length <= 4 && afterDigits === (prefillDigits + cleanDigits)) {
            setReactInputValue(input, cleanDigits.slice(prefillDigits.length));
          }
          var tail = cleanDigits.slice(-7);
          if (digitsOnly(input.value || "").indexOf(tail) === -1) {
            sendToApp({ type: "nativeKeyText", text: "+" + cleanDigits, sendEnter: false });
          }
          setTimeout(function () {
            if (window.__wuppPairGen !== myGen) return;
            clickNextButton(input);
          }, 550);
        }
      } else {
        var altEl = findAltLinkingElement();
        if (altEl) {
          hasClickedAltLink = true;
          reportStatus("Opening phone linking screen… (" + attempts + ")");
          var now = Date.now();
          if (now - lastAltClickAt >= 2800) {
            lastAltClickAt = now;
            altClickCount++;
            lastClickedDesc = "alt:" + (altEl.tagName || "") + ":" + norm(textOf(altEl)).slice(0, 28);
            activateDomElement(altEl, altClickCount >= 2);
          }
        } else if (!hasClickedAltLink) {
          reportStatus("Waiting for WhatsApp Web pairing page… (" + attempts + ")");
        } else {
          reportStatus("Opening phone linking screen… (" + attempts + ")");
        }
      }

      if (attempts < 55) {
        setTimeout(step, 650);
      } else {
        var inputCnt = document.querySelectorAll("input").length;
        var snippet = document.body ? norm(textOf(document.body)).slice(0, 90) : "";
        reportStatus("Timed out (inputs=" + inputCnt + ", clicked=" + lastClickedDesc + "): " + snippet);
        sendToApp({ type: "linkUpdate", state: "ERROR", code: "" });
      }
    }

    setTimeout(step, 250);
  }

  function runDialContact(targetWidStr, peerJidStr, digits, contactName, isVideo, isGroup, d1, d2) {
    var cleanDigits = digitsOnly(digits);
    var myGen = (window.__wuppDialGen || 0) + 1;
    window.__wuppDialGen = myGen;
    dialInFlight = true;
    syncPowerUiState();

    function emitDialResult(dispatched, reason) {
      if (window.__wuppDialGen === myGen) {
        dialInFlight = false;
        if (!dispatched && lastCallState === "IDLE" && powerMode === "call") {
          powerMode = "sleep";
        }
        syncPowerUiState();
      }
      sendToApp({ type: "dialResult", dispatched: !!dispatched, reason: reason || "" });
    }

    function findSearchBox() {
      var sels = [
        '[data-testid="chat-list-search"] [contenteditable="true"]',
        '[aria-label*="Search input" i][contenteditable="true"]',
        '#side [contenteditable="true"][role="textbox"]',
        '#side [role="textbox"]',
        '#side input[type="text"]',
        'input[aria-label*="Search" i]'
      ];
      for (var i = 0; i < sels.length; i++) {
        var el = document.querySelector(sels[i]);
        if (el && isElementUsable(el)) return el;
      }
      return null;
    }

    function readBoxText(box) {
      if (!box) return "";
      return norm(box.value !== undefined ? box.value : box.textContent);
    }

    function setSearchQuery(box, query) {
      try {
        var clearBtn = document.querySelector('#side [aria-label*="Cancel search" i], #side [data-icon="x-alt"], #side button[aria-label*="Clear" i]');
        if (clearBtn && isElementUsable(clearBtn)) {
          clearBtn.click();
        }
      } catch (e) {}
      requestNativeTapEl(box);
      box.focus();
      try {
        var sel = window.getSelection && window.getSelection();
        if (sel && document.createRange) {
          var range = document.createRange();
          range.selectNodeContents(box);
          sel.removeAllRanges();
          sel.addRange(range);
        } else {
          document.execCommand("selectAll", false, null);
        }
        document.execCommand("delete", false, null);
        document.execCommand("insertText", false, query);
      } catch (e) {}
      if (readBoxText(box) !== norm(query)) {
        setTimeout(function () {
          if (window.__wuppDialGen !== myGen) return;
          box.focus();
          sendToApp({ type: "nativeKeyText", text: "__CLEAR__", sendEnter: false });
          sendToApp({ type: "nativeKeyText", text: query, sendEnter: false });
        }, 140);
      }
    }

    function getValidChatRows() {
      var all = document.querySelectorAll('#pane-side [role="listitem"], #pane-side [role="row"]');
      var valid = [];
      for (var i = 0; i < all.length; i++) {
        var row = all[i];
        try {
          var r = row.getBoundingClientRect();
          if (!r || r.height < 40 || r.width < 80) continue;
        } catch (e) {}
        var txt = norm(textOf(row));
        if (!txt || txt.length < 2) continue;
        if (/^(chats|contacts|messages|groups|unread|favorites|archived|locked chats|searching|looking for)/i.test(txt)) continue;
        if (row.querySelector('[data-testid*="cell-frame-container"], [role="gridcell"], [tabindex], span[title], img')) {
          valid.push(row);
        }
      }
      return valid;
    }

    function rowTitleText(row) {
      var spans = row ? row.querySelectorAll("span[title]") : [];
      var out = "";
      for (var i = 0; i < spans.length; i++) {
        var t = norm(spans[i].getAttribute("title") || spans[i].innerText || "");
        if (t) out += " " + t;
      }
      return norm(out);
    }

    function pickBestChatRow(rows, allowBodyAndFallback, preSearchRows) {
      if (!rows.length) return null;
      var tail7 = cleanDigits.length >= 7 ? cleanDigits.slice(-7) : cleanDigits;
      var cleanName = normLower(contactName);
      var listFiltered = rows.length <= 5 || (preSearchRows > 5 && rows.length < preSearchRows);

      for (var i = 0; i < rows.length; i++) {
        var row = rows[i];
        var titleTxt = rowTitleText(row);
        var titleDigits = digitsOnly(titleTxt);
        if (tail7 && titleDigits.indexOf(tail7) !== -1) return row;
        if (listFiltered && cleanName.length >= 2 && normLower(titleTxt).indexOf(cleanName) !== -1) return row;
      }
      if (allowBodyAndFallback && listFiltered) {
        for (var j = 0; j < rows.length; j++) {
          var rowB = rows[j];
          var txt = norm(textOf(rowB));
          var rowDigits = digitsOnly(txt);
          if (tail7 && rowDigits.indexOf(tail7) !== -1) return rowB;
          if (cleanName.length >= 2 && normLower(txt).indexOf(cleanName) !== -1) return rowB;
        }
        if (rows.length <= 3) return rows[0];
      }
      return null;
    }

    function findMainHeader() {
      return document.querySelector('#main header, [data-testid="conversation-header"], [data-testid="conversation-panel-wrapper"] header, #main [role="banner"]');
    }

    function hasCallEndControl() {
      if (document.querySelector('[data-icon="call-end"], [data-icon="end-call"]')) return true;
      var btns = document.querySelectorAll("button, [role=\"button\"], [aria-label]");
      for (var i = 0; i < btns.length; i++) {
        var al = norm(btns[i].getAttribute("aria-label") || "");
        if (/^(end call|hang up|leave call)/i.test(al)) return true;
      }
      return false;
    }

    function findHeaderButtons() {
      var hdr = findMainHeader();
      return hdr ? hdr.querySelectorAll("button, [role=\"button\"], [tabindex]") : [];
    }

    function isButtonDisabled(el) {
      if (!el) return true;
      if (el.disabled || el.getAttribute("aria-disabled") === "true") return true;
      try {
        var cs = window.getComputedStyle(el);
        if (cs && parseFloat(cs.opacity || "1") < 0.6) return true;
        if (cs && cs.pointerEvents === "none") return true;
      } catch (e) {}
      return false;
    }

    function buttonLabelAndIcon(b) {
      var al = norm(b.getAttribute("aria-label") || b.getAttribute("title") || b.innerText || "");
      if (!al) {
        var svgTitle = b.querySelector("svg title");
        if (svgTitle) al = norm(svgTitle.textContent || "");
      }
      var icEl = b.querySelector("[data-icon]");
      var ic = icEl ? (icEl.getAttribute("data-icon") || "").toLowerCase() : "";
      return { al: al, ic: ic };
    }

    function findHeaderCallButton() {
      var btns = findHeaderButtons();
      if (!btns || !btns.length) return null;
      var specificLabelRe = isVideo ? /video\s*call|start\s*video|video/i : /voice\s*call|audio\s*call|phone\s*call/i;
      var specificIconSel = isVideo
        ? '[data-icon="video-call"],[data-icon="video-call-refreshed"],[data-icon="video"],[data-icon*="video-call"],[data-icon*="videocam"]'
        : '[data-icon="voice-call"],[data-icon="voice-call-refreshed"],[data-icon="audio-call"],[data-icon="call"],[data-icon="ic-call"],[data-icon="call-refreshed"],[data-icon="phone"],[data-icon*="voice"],[data-icon*="audio"]';

      var disabledMatch = null;
      for (var i = 0; i < btns.length; i++) {
        var b = btns[i];
        try {
          var r = b.getBoundingClientRect();
          if (!r || r.width < 4 || r.height < 4) continue;
        } catch (e) {}
        var info = buttonLabelAndIcon(b);
        if (/^(search|menu|more options|profile|group info|close|back|attach|mute)/i.test(info.al)) continue;
        var matchesDirect = specificLabelRe.test(info.al) || !!b.querySelector(specificIconSel);
        if (!isVideo && /video/i.test(info.al) && !/voice|audio/i.test(info.al)) matchesDirect = false;
        if (matchesDirect) {
          if (!isButtonDisabled(b)) return { el: b, kind: "direct", disabled: false };
          disabledMatch = { el: b, kind: "direct", disabled: true };
        }
      }

      for (var j = 0; j < btns.length; j++) {
        var u = btns[j];
        try {
          var r2 = u.getBoundingClientRect();
          if (!r2 || r2.width < 4 || r2.height < 4) continue;
        } catch (e) {}
        var uInfo = buttonLabelAndIcon(u);
        if (/^(search|menu|more options|profile|group info|close|back|attach|mute)/i.test(uInfo.al)) continue;
        var matchesUnified = /^(call|calls|start( a)? call|make( a)? call|voice or video|video or voice)$/i.test(uInfo.al) ||
          /\bcall\b/i.test(uInfo.al) ||
          ((uInfo.ic.indexOf("call") !== -1 || uInfo.ic.indexOf("phone") !== -1 || uInfo.ic.indexOf("video") !== -1) &&
            uInfo.ic.indexOf("missed") === -1 && uInfo.ic.indexOf("history") === -1);
        if (matchesUnified) {
          if (!isButtonDisabled(u)) return { el: u, kind: "unified", disabled: false };
          if (!disabledMatch) disabledMatch = { el: u, kind: "unified", disabled: true };
        }
      }
      return disabledMatch;
    }

    function findCallSubmenuOrConfirmOption(excludeBtn) {
      var nodes = document.querySelectorAll('[role="menu"] [role="menuitem"], [role="menu"] button, [role="menu"] [role="button"], [role="application"] [role="button"], [role="dialog"] button, [role="dialog"] [role="button"], div[role="menuitem"], li[role="menuitem"]');
      var modeRe = isVideo ? /video\s*call|^video$/i : /voice\s*call|audio\s*call|^voice$|^audio$/i;
      var iconSel = isVideo ? '[data-icon*="video"]' : '[data-icon*="voice"],[data-icon*="audio"],[data-icon="call"],[data-icon*="phone"]';
      for (var i = 0; i < nodes.length; i++) {
        var n = nodes[i];
        if (n === excludeBtn || (excludeBtn && excludeBtn.contains(n))) continue;
        if (!isElementUsable(n)) continue;
        var txt = norm(textOf(n) || n.getAttribute("aria-label") || "");
        if (modeRe.test(txt) || n.querySelector(iconSel)) return n;
      }
      for (var j = 0; j < nodes.length; j++) {
        var d = nodes[j];
        if (d === excludeBtn || (excludeBtn && excludeBtn.contains(d))) continue;
        if (!isElementUsable(d)) continue;
        if (!d.closest('[role="dialog"]')) continue;
        var dt = norm(textOf(d) || d.getAttribute("aria-label") || "");
        if (/^(start call|call|continue|allow|ok)$/i.test(dt)) return d;
      }
      return null;
    }

    function readUnsupportedDialogReason() {
      var dlgs = document.querySelectorAll('[role="dialog"]');
      for (var i = 0; i < dlgs.length; i++) {
        if (!isElementUsable(dlgs[i])) continue;
        var txt = norm(textOf(dlgs[i]));
        if (/download whatsapp|get the app|get whatsapp|browser isn.?t supported|not available on whatsapp web/i.test(txt)) {
          return txt.slice(0, 120);
        }
      }
      return "";
    }

    function buildHeaderDiag(rowCount) {
      var hdr = findMainHeader();
      var btns = findHeaderButtons();
      var list = [];
      for (var i = 0; i < btns.length && i < 8; i++) {
        var info = buttonLabelAndIcon(btns[i]);
        var dis = isButtonDisabled(btns[i]) ? "(dis)" : "";
        list.push((info.al || "-") + "@" + (info.ic || "-") + dis);
      }
      return "w=" + window.innerWidth + "x" + window.innerHeight +
        ",plt=" + (pageWin.navigator.platform || "-") +
        ",main=" + (!!hdr) + ",rows=" + rowCount + ",hdr=[" + list.join(";") + "]";
    }

    function matchesTargetChat() {
      var tail7 = cleanDigits.length >= 7 ? cleanDigits.slice(-7) : cleanDigits;
      var cleanName = normLower(contactName);
      var targetUser = (targetWidStr || peerJidStr || "").split("@")[0].split(":")[0];

      // 1. Check active ChatModel in ChatCollection (authoritative when present)
      try {
        var chatCol = resolveChatCollection();
        var activeChat = chatCol && typeof chatCol.getActive === "function" ? chatCol.getActive() : null;
        if (activeChat && activeChat.id) {
          var acUser = String(activeChat.id.user || "");
          var acSer = String(activeChat.id._serialized || (activeChat.id.toString && activeChat.id.toString()) || "");
          if (targetWidStr && acSer === targetWidStr) return true;
          if (isGroup && targetUser && acUser === targetUser) return true;
          if (tail7 && digitsOnly(acUser).indexOf(tail7) !== -1) return true;
          // Active chat exists in store and is definitely SOMEONE ELSE!
          var acTitle = normLower(activeChat.formattedTitle || activeChat.name || "");
          if (cleanName.length >= 2 && acTitle && acTitle.indexOf(cleanName) !== -1) return true;
          if (acSer) return false;
        }
      } catch (e) {}

      // 2. Fallback: verify #main header text matches target digits or contact name
      var hdr = findMainHeader();
      if (!hdr || !isElementUsable(hdr)) return false;
      var hdrTxt = norm(textOf(hdr));
      if (!hdrTxt) return false;
      var hdrDigits = digitsOnly(hdrTxt);
      if (tail7 && hdrDigits.indexOf(tail7) !== -1) return true;
      if (cleanName.length >= 2 && normLower(hdrTxt).indexOf(cleanName) !== -1) return true;
      return false;
    }

    function resolveTargetChatViaStore(widObj, onChat) {
      try {
        var chatCol = resolveChatCollection();
        var existing = chatCol && typeof chatCol.get === "function" ? chatCol.get(widObj) : null;
        if (existing) { onChat(existing); return; }

        var findMod = findWaModule("FindChatAction", ["WAWebFindChatAction"], function (m) {
          return !!(m && typeof m.findOrCreateLatestChat === "function");
        });
        if (!findMod) { onChat(null); return; }
        var pr = findMod.findOrCreateLatestChat(widObj, "createChat");
        if (pr && typeof pr.then === "function") {
          pr.then(toPageFn(function (res) {
            var ch = (res && res.chat && res.chat.id && chatCol) ? chatCol.get(res.chat.id) : (res && res.chat);
            onChat(ch || null);
          }), toPageFn(function () {
            onChat(null);
          }));
        } else {
          onChat(null);
        }
      } catch (e) {
        onChat(null);
      }
    }

    function resolveExactUserWid(rawWidObj, onWid) {
      try {
        var qMod = findWaModule("QueryWidExists", ["WAWebSendQueryExists", "WAWebQueryExistJob", "WAWebExistsJob"], function (m) {
          return !!(m && typeof m.queryWidExists === "function");
        });
        if (qMod) {
          var pr = qMod.queryWidExists(rawWidObj);
          if (pr && typeof pr.then === "function") {
            var settled = false;
            setTimeout(function () {
              if (settled) return;
              settled = true;
              onWid(rawWidObj);
            }, 2200);
            pr.then(toPageFn(function (res) {
              if (settled) return;
              settled = true;
              onWid((res && (res.lid || res.wid)) || rawWidObj);
            }), toPageFn(function () {
              if (settled) return;
              settled = true;
              onWid(rawWidObj);
            }));
            return;
          }
        }
      } catch (e) {}
      onWid(rawWidObj);
    }

    function openChatViaCmdRouter(widObj, onDone) {
      var cmd = resolveCmd();
      if (!cmd || typeof cmd.openChatBottom !== "function") { onDone(false); return; }
      resolveTargetChatViaStore(widObj, function (chat) {
        if (!chat) { onDone(false); return; }
        try {
          var arg = newPageObj();
          arg.chat = chat;
          var p = cmd.openChatBottom(arg);
          if (p && typeof p.then === "function") {
            p.then(toPageFn(function () { onDone(true); }), toPageFn(function () {
              try { cmd.openChatBottom(chat); onDone(true); } catch (e2) { onDone(false); }
            }));
          } else {
            onDone(true);
          }
        } catch (e) {
          try { cmd.openChatBottom(chat); onDone(true); } catch (e2) { onDone(false); }
        }
      });
    }

    function hasNativeOutgoingCall() {
      var nc = readNativeActiveCallState();
      return !!(nc && nc.state !== "INCOMING_RINGING");
    }

    function tryDirectNativeVoipCall(onFallback) {
      var wf = resolveWidFactory();
      var rawTarget = targetWidStr || (isGroup ? peerJidStr : (cleanDigits ? cleanDigits + "@c.us" : peerJidStr));
      if (!wf || !rawTarget) {
        onFallback(null, "no-wid-factory");
        return;
      }
      var baseWid = null;
      try {
        baseWid = wf.createWid(rawTarget);
      } catch (e) {
        baseWid = null;
      }
      if (!baseWid) {
        onFallback(null, "bad-wid:" + rawTarget);
        return;
      }

      ensureVoipBundlesLoaded(function () {
        if (window.__wuppDialGen !== myGen) return;
        var startMod = findWaModule("VoipStartCall", ["WAWebVoipStartCall"], function (m) {
          return !!(m && typeof m.startWAWebVoipCall === "function");
        });
        if (!startMod) {
          onFallback(baseWid, "no-start-mod");
          return;
        }

        function watchDirectCallConfirm(tag) {
          var checks = 0;
          function checkLoop() {
            if (window.__wuppDialGen !== myGen) return;
            checks++;
            if (hasNativeOutgoingCall() || hasCallEndControl()) {
              emitDialResult(true, tag);
              return;
            }
            if (checks >= 16) {
              onFallback(baseWid, tag + "-no-active");
              return;
            }
            setTimeout(checkLoop, 180);
          }
          setTimeout(checkLoop, 160);
        }

        if (isGroup && typeof startMod.startWAWebVoipGroupCallFromChat === "function") {
          resolveTargetChatViaStore(baseWid, function (chat) {
            if (window.__wuppDialGen !== myGen) return;
            if (!chat) {
              onFallback(baseWid, "grp-chat-missing");
              return;
            }
            try {
              var idMod = findWaModule("VoipCallIdProvider", ["WAWebVoipCallIdProvider"], function (m) {
                return !!(m && typeof m.generateAndStoreNewCallId === "function");
              });
              if (idMod) idMod.generateAndStoreNewCallId();
              var gp = startMod.startWAWebVoipGroupCallFromChat(chat, !!isVideo, 8, 5);
              if (gp && typeof gp.then === "function") {
                gp.then(toPageFn(function () {
                  watchDirectCallConfirm("route=direct-group");
                }), toPageFn(function () {
                  onFallback(baseWid, "grp-offer-err");
                }));
              } else {
                watchDirectCallConfirm("route=direct-group");
              }
            } catch (e) {
              onFallback(baseWid, "grp-exc");
            }
          });
          return;
        }

        if (!isGroup && typeof startMod.startWAWebVoipCall === "function") {
          resolveExactUserWid(baseWid, function (resolvedWid) {
            if (window.__wuppDialGen !== myGen) return;
            try {
              var opts = newPageObj();
              opts.entryTrust = "user_gesture";
              var cp = startMod.startWAWebVoipCall(resolvedWid, !!isVideo, 8, 5, null, opts);
              if (cp && typeof cp.then === "function") {
                cp.then(toPageFn(function () {
                  watchDirectCallConfirm("route=direct-voip");
                }), toPageFn(function () {
                  onFallback(resolvedWid, "voip-offer-err");
                }));
              } else {
                watchDirectCallConfirm("route=direct-voip");
              }
            } catch (e) {
              onFallback(baseWid, "voip-exc");
            }
          });
          return;
        }

        onFallback(baseWid, "no-start-fn");
      });
    }

    var queries = [];
    if (cleanDigits) queries.push("+" + cleanDigits);
    if (cleanDigits.length > 10) queries.push(cleanDigits.slice(-10));
    if (cleanDigits) queries.push(cleanDigits);
    if (contactName && queries.indexOf(contactName) === -1) queries.push(contactName);
    var uniqueQueries = [];
    for (var q = 0; q < queries.length; q++) {
      if (queries[q] && uniqueQueries.indexOf(queries[q]) === -1) uniqueQueries.push(queries[q]);
    }

    var queryIdx = 0;
    var phase = "DIRECT_ROUTE";
    var phaseTicks = 0;
    var rowClicked = false;
    var initialRowCount = getValidChatRows().length;
    var lastRowCount = initialRowCount;
    var matchedQueryPrefix = "";
    var clickedCallInfo = null;
    var callBtnRetries = 0;
    var submenuClicked = false;
    var routeReason = "";

    function runSearchAttempt(box) {
      initialRowCount = Math.max(initialRowCount, getValidChatRows().length);
      matchedQueryPrefix = normLower(uniqueQueries[queryIdx]).slice(0, 4);
      setSearchQuery(box, uniqueQueries[queryIdx]);
      phase = "WAIT_ROW";
      phaseTicks = 0;
    }

    function openChatRow(row) {
      var target = row.querySelector('[data-testid*="cell-frame-container"], [role="gridcell"], [tabindex="-1"], [tabindex="0"]') || row;
      activateDomElement(target, true);
      requestNativeTapEl(target);
    }

    function startUiRouterFallback(widObj, failTag) {
      routeReason = failTag || "";
      if (window.__wuppDialGen !== myGen) return;
      if (widObj) {
        openChatViaCmdRouter(widObj, function (opened) {
          if (window.__wuppDialGen !== myGen) return;
          if (opened) {
            phase = "WAIT_HEADER_AND_BTN";
            phaseTicks = 0;
            setTimeout(tick, 320);
            return;
          }
          if (!uniqueQueries.length) {
            emitDialResult(false, "Chat unavailable in WhatsApp Web (" + routeReason + ")");
            return;
          }
          phase = "WAIT_SEARCH_BOX";
          phaseTicks = 0;
          setTimeout(tick, 120);
        });
        return;
      }
      if (!uniqueQueries.length) {
        emitDialResult(false, "No phone number or group JID available for call");
        return;
      }
      phase = "WAIT_SEARCH_BOX";
      phaseTicks = 0;
      setTimeout(tick, 120);
    }

    function tick() {
      if (window.__wuppDialGen !== myGen) return;
      phaseTicks++;

      if (hasNativeOutgoingCall() || hasCallEndControl()) {
        emitDialResult(true, routeReason);
        return;
      }

      var unsupported = readUnsupportedDialogReason();
      if (unsupported) {
        emitDialResult(false, "WhatsApp Web calling unavailable: " + unsupported);
        return;
      }

      if (phase === "WAIT_SEARCH_BOX") {
        var box = findSearchBox();
        if (box) {
          runSearchAttempt(box);
          setTimeout(tick, Math.max(d1 || 450, 750));
          return;
        }
        if (phaseTicks >= 16) {
          emitDialResult(false, "WhatsApp Web session not ready yet");
          return;
        }
        setTimeout(tick, 250);
        return;
      }

      if (phase === "WAIT_ROW") {
        var curBox = findSearchBox();
        var boxText = normLower(readBoxText(curBox));
        var boxHasQuery = !!matchedQueryPrefix && boxText.indexOf(matchedQueryPrefix) !== -1;
        var rows = getValidChatRows();
        lastRowCount = rows.length;
        var settled = boxHasQuery && (phaseTicks >= 2 || rows.length <= 5);
        var row = settled ? pickBestChatRow(rows, boxHasQuery, initialRowCount) : null;
        if (row) {
          openChatRow(row);
          rowClicked = true;
          phase = "WAIT_HEADER_AND_BTN";
          phaseTicks = 0;
          setTimeout(tick, Math.max(d2 || 500, 650));
          return;
        }
        if (phaseTicks >= 10) {
          if (queryIdx + 1 < uniqueQueries.length) {
            queryIdx++;
            if (curBox) {
              runSearchAttempt(curBox);
              setTimeout(tick, 700);
              return;
            }
          }
          emitDialResult(false, "Contact not found in WhatsApp Web (" + buildHeaderDiag(lastRowCount) + ")");
          return;
        }
        setTimeout(tick, 300);
        return;
      }

      if (phase === "WAIT_HEADER_AND_BTN") {
        if (!matchesTargetChat()) {
          if (phaseTicks % 3 === 1 && rowClicked) {
            var curBox2 = findSearchBox();
            var boxOk2 = !!matchedQueryPrefix && normLower(readBoxText(curBox2)).indexOf(matchedQueryPrefix) !== -1;
            var rows2 = getValidChatRows();
            var row2 = pickBestChatRow(rows2, boxOk2, initialRowCount);
            if (row2) openChatRow(row2);
          }
          if (phaseTicks >= 14) {
            emitDialResult(false, "Refused call: target chat did not open in WhatsApp Web (" + buildHeaderDiag(lastRowCount) + ")");
            return;
          }
          setTimeout(tick, 300);
          return;
        }

        var hdr = findMainHeader();
        if (!hdr || !isElementUsable(hdr)) {
          if (phaseTicks >= 16) {
            emitDialResult(false, "Call button unavailable in WhatsApp Web (" + buildHeaderDiag(lastRowCount) + ")");
            return;
          }
          setTimeout(tick, 280);
          return;
        }

        var found = findHeaderCallButton();
        if (found && found.el && !found.disabled) {
          clickedCallInfo = found;
          activateDomElement(found.el, true);
          phase = "WAIT_POST_CLICK";
          phaseTicks = 0;
          setTimeout(tick, 340);
          return;
        }

        if (found && found.disabled && phaseTicks >= 6) {
          emitDialResult(false, "Call button disabled in WhatsApp Web (" + buildHeaderDiag(lastRowCount) + ")");
          return;
        }

        if (phaseTicks >= 16) {
          emitDialResult(false, "Call button unavailable in WhatsApp Web (" + buildHeaderDiag(lastRowCount) + ")");
          return;
        }
        setTimeout(tick, 280);
        return;
      }

      if (phase === "WAIT_POST_CLICK") {
        if (!submenuClicked) {
          var subOpt = findCallSubmenuOrConfirmOption(clickedCallInfo ? clickedCallInfo.el : null);
          if (subOpt) {
            submenuClicked = true;
            activateDomElement(subOpt, true);
            setTimeout(tick, 350);
            return;
          }
        } else {
          var confirmOpt = findCallSubmenuOrConfirmOption(clickedCallInfo ? clickedCallInfo.el : null);
          if (confirmOpt && confirmOpt.closest('[role="dialog"]')) {
            activateDomElement(confirmOpt, true);
            setTimeout(tick, 350);
            return;
          }
        }

        if (phaseTicks === 3 && !submenuClicked && callBtnRetries === 0 && clickedCallInfo && clickedCallInfo.el) {
          callBtnRetries++;
          activateDomElement(clickedCallInfo.el, true);
        }

        if (phaseTicks >= 9) {
          if (clickedCallInfo && clickedCallInfo.kind === "unified" && !submenuClicked) {
            emitDialResult(false, "Call menu option unavailable (" + buildHeaderDiag(lastRowCount) + ")");
          } else {
            emitDialResult(true, routeReason);
          }
          return;
        }
        setTimeout(tick, 280);
      }
    }

    tryDirectNativeVoipCall(startUiRouterFallback);
  }

  function handleAppCommand(msg) {
    if (!msg || typeof msg !== "object") return;
    var cmd = msg.cmd;
    if (cmd === "powerMode") {
      powerMode = msg.mode || "sleep";
      syncPowerUiState();
      if (powerMode === "preview") {
        reportEnv();
      }
      if (powerMode !== "sleep") {
        try { inspectDom(); } catch (e) {}
      }
    } else if (cmd === "requestEnv") {
      reportEnv();
    } else if (cmd === "syncPermissions") {
      permState.mic = !!msg.mic;
      permState.cam = !!msg.cam;
    } else if (cmd === "pairing") {
      powerMode = "preview";
      syncPowerUiState();
      runPairingAutomation(msg.phoneDigits || "");
    } else if (cmd === "dial") {
      powerMode = "call";
      syncPowerUiState();
      runDialContact(
        msg.targetWid || "",
        msg.peerJid || "",
        msg.digits || "",
        msg.contactName || "",
        !!msg.isVideo,
        !!msg.isGroup,
        msg.delay1 || 450,
        msg.delay2 || 550
      );
    } else if (cmd === "answer") {
      powerMode = "call";
      syncPowerUiState();
      getVoipStackAsync(function (stack) {
        var ac = resolveCallCollection() && resolveCallCollection().activeCall;
        if (stack && typeof stack.acceptCall === "function" && ac) {
          try {
            stack.acceptCall(true, !msg.audioOnly && !!ac.isVideo);
            return;
          } catch (e) {}
        }
        if (msg.audioOnly) {
          var camBtn = document.querySelector('[aria-label*="camera" i]');
          if (camBtn && /turn off/i.test(camBtn.getAttribute("aria-label") || "")) camBtn.click();
        }
        var accept = findBtnByLabel(/^(accept|answer|join call)/i) || document.querySelector('[data-icon="call-accept"]');
        if (accept) activateDomElement(accept, true);
      });
    } else if (cmd === "end") {
      getVoipStackAsync(function (stack) {
        var callCol = resolveCallCollection();
        var ac = callCol && callCol.activeCall;
        var st = ac && (typeof ac.getState === "function" ? ac.getState() : ac.state);
        if (stack && ac) {
          try {
            ac.userEndedCall = true;
            if ((st === 3 || st === 8) && typeof stack.rejectCallWithEndReason === "function") {
              stack.rejectCallWithEndReason(2);
              return;
            }
            if ((st === 3 || st === 8) && typeof stack.rejectCall === "function") {
              stack.rejectCall();
              return;
            }
            if (typeof stack.endCall === "function") {
              stack.endCall(2, true);
              return;
            }
          } catch (e) {}
        }
        var end = findBtnByLabel(/^(end call|hang up|leave call|decline|reject|ignore)/i) ||
          document.querySelector('[data-icon="call-end"],[data-icon="end-call"]');
        if (end) activateDomElement(end, true);
      });
    } else if (cmd === "mute") {
      var wantMuted = !!msg.muted;
      var stSync = resolveVoipStackSync();
      if (stSync && typeof stSync.setCallMute === "function") {
        try { stSync.setCallMute(wantMuted); return; } catch (e) {}
      }
      var els = document.querySelectorAll('button,[role="button"],[aria-label]');
      for (var i = 0; i < els.length; i++) {
        var al = els[i].getAttribute("aria-label") || "";
        if (wantMuted && /^mute/i.test(al) && !/^unmute/i.test(al)) { activateDomElement(els[i], true); break; }
        if (!wantMuted && /^unmute/i.test(al)) { activateDomElement(els[i], true); break; }
      }
    } else if (cmd === "camera") {
      var wantOff = !!msg.camOff;
      var stSync2 = resolveVoipStackSync();
      if (stSync2 && typeof stSync2.setCallVideoMute === "function") {
        try { stSync2.setCallVideoMute(wantOff); return; } catch (e) {}
      }
      var els2 = document.querySelectorAll('button,[role="button"],[aria-label]');
      for (var j = 0; j < els2.length; j++) {
        var al2 = els2[j].getAttribute("aria-label") || "";
        if (wantOff && /turn off camera|stop video/i.test(al2)) { activateDomElement(els2[j], true); break; }
        if (!wantOff && /turn on camera|start video/i.test(al2)) { activateDomElement(els2[j], true); break; }
      }
    } else if (cmd === "flip") {
      var flipBtn = findBtnByLabel(/switch camera|flip camera/i);
      if (flipBtn) activateDomElement(flipBtn, true);
    } else if (cmd === "upgradeVideo") {
      var stSync3 = resolveVoipStackSync();
      if (stSync3 && typeof stSync3.requestVideoUpgrade === "function") {
        try { stSync3.requestVideoUpgrade(); return; } catch (e) {}
      }
      var els3 = document.querySelectorAll('button,[role="button"],[aria-label]');
      for (var k = 0; k < els3.length; k++) {
        var al3 = els3[k].getAttribute("aria-label") || "";
        if (/switch to video|turn on camera|video/i.test(al3) && !/turn off/i.test(al3)) {
          activateDomElement(els3[k], true);
          break;
        }
      }
    } else if (cmd === "videoCss") {
      applyVideoCss(!!msg.active);
    } else if (cmd === "ringMute") {
      applyRingMute(!!msg.muted);
    } else if (cmd === "neutral") {
      try {
        var cmdObj = resolveCmd();
        var chatCol = resolveChatCollection();
        var active = chatCol && typeof chatCol.getActive === "function" ? chatCol.getActive() : null;
        if (cmdObj && active && typeof cmdObj.closeChat === "function") {
          cmdObj.closeChat(active);
        }
      } catch (e) {}
      try {
        var clearBtn = document.querySelector('[aria-label="Cancel search"], [data-icon="x-alt"]');
        if (clearBtn) clearBtn.click();
        unwrap(document).dispatchEvent(new pageWin.KeyboardEvent("keydown", toPageObj({ key: "Escape", code: "Escape", keyCode: 27, bubbles: true })));
      } catch (e) {}
    } else if (cmd === "scrollPairing") {
      try {
        var t = document.querySelector('input:not([type="hidden"]), [data-link-code], canvas, [role="button"]');
        if (t && t.scrollIntoView) t.scrollIntoView({ block: "center", inline: "center" });
      } catch (e) {}
    } else if (cmd === "forceReloadClean") {
      try {
        if (navigator.serviceWorker && navigator.serviceWorker.getRegistrations) {
          navigator.serviceWorker.getRegistrations().then(function (regs) {
            Promise.all(regs.map(function (r) { return r.unregister(); })).finally(function () {
              location.reload(true);
            });
          }).catch(function () { location.reload(true); });
          return;
        }
      } catch (e) {}
      location.reload(true);
    }
  }

  function connectBridge() {
    if (nativePort) return;
    try {
      var p = browser.runtime.connectNative("wuppBridge");
      nativePort = p;
      p.onMessage.addListener(handleAppCommand);
      p.onDisconnect.addListener(function () {
        if (nativePort === p) nativePort = null;
        setTimeout(connectBridge, 600);
      });
      flushOutbox();
    } catch (e) {
      nativePort = null;
      setTimeout(connectBridge, 600);
    }
  }

  connectBridge();
  startDomObserver();
  setTimeout(reportEnv, 900);
})();

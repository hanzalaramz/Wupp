package com.wa.beastclient.core.calling

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.ContextCompat
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject
import kotlin.random.Random

/**
 * Isolated hidden System WebView engine powering `web.whatsapp.com` WebRTC calls.
 * Preserves authentic Chromium fingerprints, avoids JS bundle/prototype tampering,
 * and communicates strictly via read-only ARIA/data-icon DOM observation.
 */
class WebCallEngine(
    context: Context,
    private val listener: Listener
) {
    interface Listener {
        fun onLinkStateChanged(state: WebLinkState, pairingCode: String = "")
        fun onWebCallEvent(
            state: String,
            callerHint: String,
            isVideo: Boolean,
            canAnswer: Boolean,
            hasActiveVideo: Boolean
        )
        fun onWebRtcMediaCapture(hasVideo: Boolean)
        fun onDialAttemptFinished(dispatched: Boolean, reason: String)
    }

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var webView: WebView? = null
    private var hostContainer: ViewGroup? = null
    private var allowTouchThrough = false
    private var videoOverlayCssActive = false
    private var lastTeardownMs = 0L
    private var pendingWarmRunnable: Runnable? = null

    val isAttachedAndLoaded: Boolean
        get() = webView != null

    /**
     * Ensures the hidden WebView is created, hardened, and attached into [container]
     * at full layout size (never 1x1dp, never GONE) so Chromium reports authentic viewport telemetry.
     */
    fun attachToContainer(container: ViewGroup, showVideoSurface: Boolean) {
        mainHandler.post {
            hostContainer = container
            allowTouchThrough = showVideoSurface
            val wv = ensureCreatedLocked() ?: return@post
            val currentParent = wv.parent as? ViewGroup
            if (currentParent != null && currentParent !== container) {
                currentParent.removeView(wv)
            }
            if (wv.parent == null) {
                container.addView(
                    wv,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
            }
            wv.alpha = if (showVideoSurface) 1f else 0f
            wv.isClickable = showVideoSurface
            wv.isFocusable = false
            wv.isFocusableInTouchMode = false
            wv.onResume()
            wv.resumeTimers()
            setVideoCssActive(showVideoSurface)
        }
    }

    fun detachIfHostedBy(container: ViewGroup) {
        mainHandler.post {
            if (hostContainer === container) {
                hostContainer = null
            }
            val wv = webView ?: return@post
            if (wv.parent === container) {
                container.removeView(wv)
            }
        }
    }

    fun ensureWarm(respectCooldown: Boolean = true) {
        mainHandler.post {
            pendingWarmRunnable?.let { mainHandler.removeCallbacks(it) }
            pendingWarmRunnable = null
            val elapsed = System.currentTimeMillis() - lastTeardownMs
            if (respectCooldown && webView == null && lastTeardownMs > 0L && elapsed < MIN_RECONNECT_COOLDOWN_MS) {
                val delayMs = MIN_RECONNECT_COOLDOWN_MS - elapsed
                val task = Runnable { ensureCreatedLocked() }
                pendingWarmRunnable = task
                mainHandler.postDelayed(task, delayMs)
                return@post
            }
            ensureCreatedLocked()
        }
    }

    fun setHighPriorityRenderer(highPriority: Boolean) {
        mainHandler.post {
            val wv = webView ?: return@post
            val policy = if (highPriority) {
                WebView.RENDERER_PRIORITY_IMPORTANT
            } else {
                WebView.RENDERER_PRIORITY_WAIVED
            }
            runCatching { wv.setRendererPriorityPolicy(policy, false) }
        }
    }

    fun setVideoCssActive(active: Boolean) {
        mainHandler.post {
            videoOverlayCssActive = active
            val wv = webView ?: return@post
            wv.alpha = if (active) 1f else 0f
            val js = if (active) JS_ENABLE_VIDEO_CSS else JS_DISABLE_VIDEO_CSS
            wv.evaluateJavascript(js, null)
        }
    }

    fun setWebRingingMuted(muted: Boolean) {
        mainHandler.post {
            val wv = webView ?: return@post
            val js = "try{document.querySelectorAll('audio').forEach(function(a){a.muted=${if (muted) "true" else "false"};});}catch(e){}"
            wv.evaluateJavascript(js, null)
        }
    }

    fun requestPhonePairingCode(phoneDigits: String) {
        mainHandler.post {
            val wv = ensureCreatedLocked() ?: return@post
            setHighPriorityRenderer(true)
            val safeDigits = JSONObject.quote(phoneDigits.filter { it.isDigit() })
            val js = JS_REQUEST_PAIRING_CODE_TEMPLATE.replace("__PHONE_DIGITS__", safeDigits)
            wv.evaluateJavascript(js, null)
        }
    }

    fun dialContactInWeb(phoneDigits: String, fallbackName: String, isVideo: Boolean) {
        mainHandler.post {
            val wv = ensureCreatedLocked()
            if (wv == null) {
                listener.onDialAttemptFinished(false, "Calling WebView unavailable")
                return@post
            }
            setHighPriorityRenderer(true)
            val stepDelay1 = Random.nextLong(380L, 640L)
            val stepDelay2 = Random.nextLong(420L, 740L)
            val safeDigits = JSONObject.quote(phoneDigits.filter { it.isDigit() })
            val safeName = JSONObject.quote(fallbackName.trim())
            val js = JS_DIAL_CONTACT_TEMPLATE
                .replace("__PHONE_DIGITS__", safeDigits)
                .replace("__CONTACT_NAME__", safeName)
                .replace("__IS_VIDEO__", if (isVideo) "true" else "false")
                .replace("__DELAY_1__", stepDelay1.toString())
                .replace("__DELAY_2__", stepDelay2.toString())
            wv.evaluateJavascript(js, null)
        }
    }

    fun answerIncomingCall(audioOnly: Boolean = false) {
        mainHandler.post {
            setHighPriorityRenderer(true)
            val js = JS_ANSWER_CALL_TEMPLATE.replace("__AUDIO_ONLY__", if (audioOnly) "true" else "false")
            webView?.evaluateJavascript(js, null)
        }
    }

    fun declineOrEndCall() {
        mainHandler.post {
            webView?.evaluateJavascript(JS_END_OR_DECLINE_CALL, null)
            returnToNeutralLandingAfterDelay()
        }
    }

    fun toggleMuteInWeb(targetMuted: Boolean) {
        mainHandler.post {
            val js = JS_TOGGLE_MUTE_TEMPLATE.replace("__MUTED__", if (targetMuted) "true" else "false")
            webView?.evaluateJavascript(js, null)
        }
    }

    fun toggleCameraInWeb(cameraOff: Boolean) {
        mainHandler.post {
            val js = JS_TOGGLE_CAMERA_TEMPLATE.replace("__CAM_OFF__", if (cameraOff) "true" else "false")
            webView?.evaluateJavascript(js, null)
        }
    }

    fun flipCameraInWeb() {
        mainHandler.post {
            webView?.evaluateJavascript(JS_FLIP_CAMERA, null)
        }
    }

    fun upgradeAudioCallToVideo() {
        mainHandler.post {
            webView?.evaluateJavascript(JS_UPGRADE_TO_VIDEO, null)
        }
    }

    /**
     * Navigates back to the neutral landing pane with a jittered 450-850ms delay so no chat
     * thread stays open (preventing accidental read receipts from the browser companion).
     */
    fun returnToNeutralLandingAfterDelay() {
        val delayMs = Random.nextLong(450L, 850L)
        mainHandler.postDelayed({
            webView?.evaluateJavascript(JS_RETURN_NEUTRAL_LANDING, null)
        }, delayMs)
    }

    fun teardownImmediate(clearSessionData: Boolean = false) {
        mainHandler.post {
            pendingWarmRunnable?.let { mainHandler.removeCallbacks(it) }
            pendingWarmRunnable = null
            val wv = webView
            webView = null
            lastTeardownMs = System.currentTimeMillis()
            if (wv != null) {
                runCatching {
                    (wv.parent as? ViewGroup)?.removeView(wv)
                    wv.stopLoading()
                    wv.removeJavascriptInterface("WuppCallBridge")
                    if (clearSessionData) {
                        wv.clearCache(true)
                        wv.clearHistory()
                    }
                    wv.loadUrl("about:blank")
                    wv.destroy()
                }
            }
            if (clearSessionData) {
                runCatching {
                    CookieManager.getInstance().removeAllCookies(null)
                    CookieManager.getInstance().flush()
                    android.webkit.WebStorage.getInstance().deleteAllData()
                }
            }
            listener.onLinkStateChanged(WebLinkState.UNINITIALIZED, "")
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    private fun ensureCreatedLocked(): WebView? {
        webView?.let { existing ->
            hostContainer?.let { container ->
                if (existing.parent == null) {
                    container.addView(
                        existing,
                        ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    )
                }
            }
            return existing
        }

        val wv = runCatching {
            object : WebView(appContext) {
                override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
                    return if (allowTouchThrough) super.dispatchTouchEvent(ev) else false
                }
            }
        }.getOrNull() ?: return null

        wv.setBackgroundColor(Color.TRANSPARENT)
        wv.alpha = if (videoOverlayCssActive) 1f else 0f
        wv.isClickable = false
        wv.isFocusable = false
        wv.isFocusableInTouchMode = false

        val settings = wv.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.mediaPlaybackRequiresUserGesture = false
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.setSupportZoom(false)
        settings.builtInZoomControls = false
        settings.displayZoomControls = false
        @Suppress("DEPRECATION")
        settings.saveFormData = false
        settings.setGeolocationEnabled(false)
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.userAgentString = buildChromiumMatchedDesktopUa(WebSettings.getDefaultUserAgent(appContext))

        if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
            runCatching {
                WebSettingsCompat.setRequestedWithHeaderOriginAllowList(settings, emptySet())
            }
        }

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)
        runCatching { wv.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_WAIVED, false) }

        wv.addJavascriptInterface(WuppCallJsBridge(), "WuppCallBridge")
        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                view?.evaluateJavascript(JS_INSTALL_PASSIVE_OBSERVER, null)
                if (videoOverlayCssActive) {
                    view?.evaluateJavascript(JS_ENABLE_VIDEO_CSS, null)
                }
            }
        }
        wv.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest?) {
                val req = request ?: return
                val originOk = isAllowedWhatsAppPermissionOrigin(req.origin?.toString())
                if (!originOk) {
                    req.deny()
                    return
                }
                val hasMic = ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
                val hasCam = ContextCompat.checkSelfPermission(appContext, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
                val granted = req.resources.filter { res ->
                    when (res) {
                        PermissionRequest.RESOURCE_AUDIO_CAPTURE -> hasMic
                        PermissionRequest.RESOURCE_VIDEO_CAPTURE -> hasCam
                        else -> false
                    }
                }.toTypedArray()

                if (granted.isEmpty()) {
                    req.deny()
                } else {
                    req.grant(granted)
                    listener.onWebRtcMediaCapture(PermissionRequest.RESOURCE_VIDEO_CAPTURE in granted)
                }
            }
        }

        webView = wv
        hostContainer?.let { container ->
            container.addView(
                wv,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }
        listener.onLinkStateChanged(WebLinkState.LOADING, "")
        wv.loadUrl(WA_WEB_URL)
        return wv
    }

    private inner class WuppCallJsBridge {
        @JavascriptInterface
        fun onLinkUpdate(state: String, code: String) {
            val parsed = when (state.uppercase()) {
                "AUTHENTICATED" -> WebLinkState.AUTHENTICATED
                "PAIRING_CODE_READY" -> WebLinkState.PAIRING_CODE_READY
                "NEEDS_PAIRING_CODE" -> WebLinkState.NEEDS_PAIRING_CODE
                "ERROR" -> WebLinkState.ERROR
                else -> WebLinkState.LOADING
            }
            mainHandler.post { listener.onLinkStateChanged(parsed, code.trim()) }
        }

        @JavascriptInterface
        fun onWebCallEvent(
            state: String,
            callerHint: String,
            isVideo: Boolean,
            canAnswer: Boolean,
            hasActiveVideo: Boolean
        ) {
            mainHandler.post {
                listener.onWebCallEvent(state, callerHint, isVideo, canAnswer, hasActiveVideo)
            }
        }

        @JavascriptInterface
        fun onDialResult(dispatched: Boolean, reason: String) {
            mainHandler.post {
                listener.onDialAttemptFinished(dispatched, reason)
            }
        }
    }

    companion object {
        private const val WA_WEB_URL = "https://web.whatsapp.com/"
        const val MIN_RECONNECT_COOLDOWN_MS = 45_000L

        private val JS_INSTALL_PASSIVE_OBSERVER = """
            (function() {
              if (window.__wuppObserverInstalled) { return; }
              window.__wuppObserverInstalled = true;
              var lastAuth = '';
              var lastCode = '';
              var lastCallSig = '';

              function textOf(el) {
                return (el && (el.innerText || el.textContent) || '').trim();
              }
              function hasIcon(name) {
                return !!document.querySelector('[data-icon="' + name + '"]');
              }
              function findBtnByLabel(re) {
                var els = document.querySelectorAll('button,[role="button"],[aria-label]');
                for (var i = 0; i < els.length; i++) {
                  var al = els[i].getAttribute('aria-label') || '';
                  if (re.test(al) || re.test(textOf(els[i]))) return els[i];
                }
                return null;
              }
              function readPairingCode() {
                var container = document.querySelector('[data-link-code]') || document.querySelector('[aria-label*="Enter code" i]') || document.querySelector('[data-ref]');
                var raw = container ? (container.getAttribute('data-link-code') || textOf(container)) : '';
                var compact = raw.replace(/[^A-Z0-9-]/gi, '').toUpperCase();
                var m = compact.match(/([A-Z0-9]{4}-?[A-Z0-9]{4})/);
                if (m && m[1] && !/LINK|CODE|WHAT/.test(m[1])) {
                  var c = m[1].replace(/-/g, '');
                  return c.slice(0, 4) + '-' + c.slice(4, 8);
                }
                var spans = document.querySelectorAll('span[dir="auto"], div[dir="ltr"] span');
                var joined = '';
                for (var j = 0; j < spans.length; j++) {
                  var t = textOf(spans[j]);
                  if (/^[A-Z0-9]{1}$/.test(t)) joined += t;
                }
                if (joined.length === 8) {
                  return joined.slice(0, 4) + '-' + joined.slice(4, 8);
                }
                return '';
              }

              function inspectDom() {
                var authenticated = !!(document.querySelector('#pane-side') || document.querySelector('[data-testid="chat-list-search"]') || document.querySelector('[aria-label*="Search input" i]'));
                if (authenticated) {
                  if (lastAuth !== 'AUTHENTICATED') {
                    lastAuth = 'AUTHENTICATED';
                    lastCode = '';
                    WuppCallBridge.onLinkUpdate('AUTHENTICATED', '');
                  }
                } else {
                  var code = readPairingCode();
                  if (code) {
                    if (lastAuth !== 'PAIRING_CODE_READY' || lastCode !== code) {
                      lastAuth = 'PAIRING_CODE_READY';
                      lastCode = code;
                      WuppCallBridge.onLinkUpdate('PAIRING_CODE_READY', code);
                    }
                  } else if (document.querySelector('canvas[aria-label]') || findBtnByLabel(/link with phone/i)) {
                    if (lastAuth !== 'NEEDS_PAIRING_CODE') {
                      lastAuth = 'NEEDS_PAIRING_CODE';
                      WuppCallBridge.onLinkUpdate('NEEDS_PAIRING_CODE', '');
                    }
                  }
                }

                var acceptBtn = findBtnByLabel(/^(accept|answer|join call)/i) || (hasIcon('call-accept') ? document.querySelector('[data-icon="call-accept"]') : null);
                var declineBtn = findBtnByLabel(/^(decline|reject|ignore)/i);
                var endBtn = findBtnByLabel(/^(end call|hang up|leave call)/i) || document.querySelector('[data-icon="call-end"],[data-icon="end-call"]');
                var videos = document.querySelectorAll('video');
                var activeVid = false;
                for (var v = 0; v < videos.length; v++) {
                  if (!videos[v].muted && videos[v].readyState >= 2 && videos[v].videoWidth > 0) {
                    activeVid = true;
                  }
                  if (videos[v].muted) {
                    videos[v].setAttribute('data-wupp-self', '1');
                  } else {
                    videos[v].removeAttribute('data-wupp-self');
                  }
                }
                var isVideo = activeVid || videos.length > 0 || !!findBtnByLabel(/turn off camera|turn on camera|flip camera/i);
                var state = 'IDLE';
                if (acceptBtn) {
                  state = 'INCOMING_RINGING';
                } else if (endBtn) {
                  var bodyTxt = textOf(document.body);
                  if (/ringing|calling/i.test(bodyTxt) && !activeVid) {
                    state = 'DIALING';
                  } else if (/connecting|reconnecting/i.test(bodyTxt) && !activeVid) {
                    state = 'CONNECTING';
                  } else {
                    state = 'ACTIVE';
                  }
                }
                var callerHint = '';
                var dialog = document.querySelector('[role="dialog"], [aria-label*="call" i]');
                if (dialog) {
                  var heading = dialog.querySelector('h1,h2,span[title],span[dir="auto"]');
                  callerHint = textOf(heading);
                }
                var sig = state + '|' + callerHint + '|' + isVideo + '|' + !!acceptBtn + '|' + activeVid;
                if (sig !== lastCallSig) {
                  lastCallSig = sig;
                  WuppCallBridge.onWebCallEvent(state, callerHint, isVideo, !!acceptBtn, activeVid);
                }
              }

              var pending = false;
              var obs = new MutationObserver(function() {
                if (pending) return;
                pending = true;
                setTimeout(function() {
                  pending = false;
                  try { inspectDom(); } catch (e) {}
                }, 140);
              });
              obs.observe(document.documentElement || document.body, { childList: true, subtree: true, attributes: true, attributeFilter: ['aria-label', 'data-icon', 'src'] });
              setInterval(function() { try { inspectDom(); } catch (e) {} }, 900);
              inspectDom();
            })();
        """.trimIndent()

        private val JS_ENABLE_VIDEO_CSS = """
            (function() {
              var id = 'wupp-call-video-css';
              var st = document.getElementById(id);
              if (!st) {
                st = document.createElement('style');
                st.id = id;
                document.head.appendChild(st);
              }
              st.textContent =
                'html, body { background: #000 !important; overflow: hidden !important; }' +
                '#side, header, footer, [data-testid="chat-list-search"] { opacity: 0 !important; pointer-events: none !important; }' +
                'button, [role="button"] { opacity: 0 !important; pointer-events: none !important; }' +
                'video:not([data-wupp-self="1"]):not([muted]) { position: fixed !important; inset: 0 !important; width: 100vw !important; height: 100vh !important; object-fit: cover !important; z-index: 9998 !important; background: #000 !important; }' +
                'video[data-wupp-self="1"], video[muted] { position: fixed !important; top: 72px !important; right: 16px !important; width: 108px !important; height: 156px !important; border-radius: 18px !important; object-fit: cover !important; z-index: 9999 !important; box-shadow: 0 8px 24px rgba(0,0,0,0.45) !important; }';
            })();
        """.trimIndent()

        private val JS_DISABLE_VIDEO_CSS = """
            (function() {
              var st = document.getElementById('wupp-call-video-css');
              if (st) st.remove();
            })();
        """.trimIndent()

        private val JS_REQUEST_PAIRING_CODE_TEMPLATE = """
            (function() {
              var digits = __PHONE_DIGITS__;
              function clickByRe(re) {
                var els = document.querySelectorAll('button,[role="button"],span[role="button"]');
                for (var i = 0; i < els.length; i++) {
                  var txt = (els[i].getAttribute('aria-label') || els[i].innerText || '').trim();
                  if (re.test(txt)) { els[i].click(); return true; }
                }
                return false;
              }
              function setNativeValue(input, val) {
                var setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
                setter.call(input, val);
                input.dispatchEvent(new Event('input', { bubbles: true }));
                input.dispatchEvent(new Event('change', { bubbles: true }));
              }
              clickByRe(/link with phone number/i);
              setTimeout(function() {
                var input = document.querySelector('input[type="tel"], input[aria-label*="phone" i]');
                if (input && digits) {
                  input.focus();
                  setNativeValue(input, '+' + digits);
                  setTimeout(function() {
                    clickByRe(/^(next|continue)$/i);
                  }, 350);
                }
              }, 700);
            })();
        """.trimIndent()

        private val JS_DIAL_CONTACT_TEMPLATE = """
            (function() {
              var digits = __PHONE_DIGITS__;
              var contactName = __CONTACT_NAME__;
              var isVideo = __IS_VIDEO__;
              var d1 = __DELAY_1__;
              var d2 = __DELAY_2__;
              var query = digits ? ('+' + digits) : contactName;
              if (!query) {
                WuppCallBridge.onDialResult(false, 'No phone number available for this contact');
                return;
              }
              var box = document.querySelector('[data-testid="chat-list-search"] [contenteditable="true"], [aria-label*="Search input" i][contenteditable="true"], #side [role="textbox"]');
              if (!box) {
                WuppCallBridge.onDialResult(false, 'WhatsApp Web session not ready yet');
                return;
              }
              box.focus();
              try {
                document.execCommand('selectAll', false, null);
                document.execCommand('insertText', false, query);
              } catch (e) {
                box.textContent = query;
                box.dispatchEvent(new InputEvent('input', { bubbles: true }));
              }
              setTimeout(function() {
                var rows = document.querySelectorAll('#pane-side [role="listitem"], #pane-side [role="row"]');
                if (!rows.length) {
                  WuppCallBridge.onDialResult(false, 'Contact not found in WhatsApp Web');
                  return;
                }
                rows[0].click();
                setTimeout(function() {
                  var iconSel = isVideo ? '[data-icon="video-call"],[data-icon="video"]' : '[data-icon="call"],[data-icon="audio-call"]';
                  var labelRe = isVideo ? /video call/i : /voice call|audio call/i;
                  var btn = null;
                  var headerBtns = document.querySelectorAll('#main header button, #main header [role="button"], header button, header [role="button"]');
                  for (var i = 0; i < headerBtns.length; i++) {
                    var al = headerBtns[i].getAttribute('aria-label') || '';
                    if (labelRe.test(al) || headerBtns[i].querySelector(iconSel)) {
                      btn = headerBtns[i];
                      break;
                    }
                  }
                  if (!btn) {
                    var all = document.querySelectorAll(iconSel);
                    if (all.length) btn = all[0].closest('button,[role="button"]') || all[0];
                  }
                  if (!btn) {
                    WuppCallBridge.onDialResult(false, 'Call button unavailable in WhatsApp Web');
                    return;
                  }
                  btn.click();
                  setTimeout(function() {
                    var confirmBtns = document.querySelectorAll('[role="dialog"] button,[role="dialog"] [role="button"]');
                    for (var c = 0; c < confirmBtns.length; c++) {
                      if (/call|start|continue/i.test(confirmBtns[c].innerText || '')) {
                        confirmBtns[c].click();
                        break;
                      }
                    }
                    WuppCallBridge.onDialResult(true, '');
                  }, 300);
                }, d2);
              }, d1);
            })();
        """.trimIndent()

        private val JS_ANSWER_CALL_TEMPLATE = """
            (function() {
              var audioOnly = __AUDIO_ONLY__;
              if (audioOnly) {
                var camBtn = document.querySelector('[aria-label*="camera" i]');
                if (camBtn && /turn off/i.test(camBtn.getAttribute('aria-label') || '')) camBtn.click();
              }
              var els = document.querySelectorAll('button,[role="button"],[aria-label]');
              for (var i = 0; i < els.length; i++) {
                var al = els[i].getAttribute('aria-label') || els[i].innerText || '';
                if (/^(accept|answer|join call)/i.test(al.trim())) { els[i].click(); return; }
              }
              var ic = document.querySelector('[data-icon="call-accept"]');
              if (ic) ic.click();
            })();
        """.trimIndent()

        private val JS_END_OR_DECLINE_CALL = """
            (function() {
              var els = document.querySelectorAll('button,[role="button"],[aria-label]');
              for (var i = 0; i < els.length; i++) {
                var al = (els[i].getAttribute('aria-label') || els[i].innerText || '').trim();
                if (/^(end call|hang up|leave call|decline|reject|ignore)/i.test(al)) {
                  els[i].click();
                  return;
                }
              }
              var ic = document.querySelector('[data-icon="call-end"],[data-icon="end-call"]');
              if (ic) ic.click();
            })();
        """.trimIndent()

        private val JS_TOGGLE_MUTE_TEMPLATE = """
            (function() {
              var wantMuted = __MUTED__;
              var els = document.querySelectorAll('button,[role="button"],[aria-label]');
              for (var i = 0; i < els.length; i++) {
                var al = els[i].getAttribute('aria-label') || '';
                if (wantMuted && /^mute/i.test(al) && !/^unmute/i.test(al)) { els[i].click(); return; }
                if (!wantMuted && /^unmute/i.test(al)) { els[i].click(); return; }
              }
            })();
        """.trimIndent()

        private val JS_TOGGLE_CAMERA_TEMPLATE = """
            (function() {
              var wantOff = __CAM_OFF__;
              var els = document.querySelectorAll('button,[role="button"],[aria-label]');
              for (var i = 0; i < els.length; i++) {
                var al = els[i].getAttribute('aria-label') || '';
                if (wantOff && /turn off camera|stop video/i.test(al)) { els[i].click(); return; }
                if (!wantOff && /turn on camera|start video/i.test(al)) { els[i].click(); return; }
              }
            })();
        """.trimIndent()

        private val JS_FLIP_CAMERA = """
            (function() {
              var els = document.querySelectorAll('button,[role="button"],[aria-label]');
              for (var i = 0; i < els.length; i++) {
                var al = els[i].getAttribute('aria-label') || '';
                if (/switch camera|flip camera/i.test(al)) { els[i].click(); return; }
              }
            })();
        """.trimIndent()

        private val JS_UPGRADE_TO_VIDEO = """
            (function() {
              var els = document.querySelectorAll('button,[role="button"],[aria-label]');
              for (var i = 0; i < els.length; i++) {
                var al = els[i].getAttribute('aria-label') || '';
                if (/switch to video|turn on camera|video/i.test(al) && !/turn off/i.test(al)) { els[i].click(); return; }
              }
            })();
        """.trimIndent()

        private val JS_RETURN_NEUTRAL_LANDING = """
            (function() {
              try {
                var clearBtn = document.querySelector('[aria-label="Cancel search"], [data-icon="x-alt"]');
                if (clearBtn) clearBtn.click();
                document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', code: 'Escape', keyCode: 27, bubbles: true }));
              } catch (e) {}
            })();
        """.trimIndent()
    }
}

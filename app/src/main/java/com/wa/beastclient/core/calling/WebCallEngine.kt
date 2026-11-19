package com.wa.beastclient.core.calling

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
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
        fun onPairingStatusChanged(status: String) {}
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
    private var webView: WuppWebView? = null
    private var hostContainer: ViewGroup? = null
    private var backgroundHostContainer: ViewGroup? = null
    private var allowTouchThrough = false
    private var videoOverlayCssActive = false
    private var lastTeardownMs = 0L
    private var pendingWarmRunnable: Runnable? = null
    @Volatile
    private var pendingPairingPhoneDigits: String = ""

    val isAttachedAndLoaded: Boolean
        get() = webView != null

    /**
     * Ensures the hidden WebView is created, hardened, and attached into [container]
     * at full layout size (never 1x1dp, never GONE) so Chromium reports authentic viewport telemetry.
     */
    fun attachToContainer(
        container: ViewGroup,
        showVideoSurface: Boolean,
        showPairingSurface: Boolean = false
    ) {
        mainHandler.post {
            hostContainer = container
            if (!showVideoSurface && !showPairingSurface) {
                backgroundHostContainer = container
            }
            val visible = showVideoSurface || showPairingSurface
            allowTouchThrough = visible
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
            wv.setBackgroundColor(if (visible) Color.WHITE else Color.TRANSPARENT)
            wv.isClickable = visible
            wv.isFocusable = true
            wv.isFocusableInTouchMode = true
            wv.onResume()
            wv.resumeTimers()
            setVideoCssActive(showVideoSurface)
            wv.alpha = if (visible) 1f else 0.01f
            wv.requestLayout()
            wv.invalidate()
            if (showPairingSurface) {
                wv.postDelayed({
                    runCatching {
                        wv.evaluateJavascript(
                            """
                            (function() {
                              var t = document.querySelector('input:not([type="hidden"]), [data-link-code], canvas, [role="button"]');
                              if (t && t.scrollIntoView) {
                                t.scrollIntoView({ block: 'center', inline: 'center' });
                              }
                            })();
                            """.trimIndent(),
                            null
                        )
                    }
                }, 250L)
            }
        }
    }

    fun detachIfHostedBy(container: ViewGroup) {
        mainHandler.post {
            if (hostContainer === container) {
                hostContainer = null
            }
            if (backgroundHostContainer === container) {
                backgroundHostContainer = null
            }
            val wv = webView ?: return@post
            if (wv.parent === container) {
                container.removeView(wv)
                val bg = backgroundHostContainer
                if (bg != null && bg !== container && bg.isAttachedToWindow) {
                    attachToContainer(bg, showVideoSurface = false, showPairingSurface = false)
                }
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
            wv.alpha = if (active || allowTouchThrough) 1f else 0.01f
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
        val cleanDigits = phoneDigits.filter { it.isDigit() }
        pendingPairingPhoneDigits = cleanDigits
        mainHandler.post {
            val wv = ensureCreatedLocked() ?: return@post
            setHighPriorityRenderer(true)
            listener.onPairingStatusChanged("Loading WhatsApp Web engine…")
            val currentUrl = wv.url.orEmpty()
            if (currentUrl.startsWith("https://web.whatsapp.com") && cleanDigits.isNotEmpty()) {
                injectPairingAutomation(wv, cleanDigits)
            }
        }
    }

    private fun injectPairingAutomation(wv: WebView, cleanDigits: String) {
        val safeDigits = JSONObject.quote(cleanDigits)
        wv.evaluateJavascript(JS_INSTALL_PASSIVE_OBSERVER, null)
        val js = JS_REQUEST_PAIRING_CODE_TEMPLATE.replace("__PHONE_DIGITS__", safeDigits)
        wv.evaluateJavascript(js, null)
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
        pendingPairingPhoneDigits = ""
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

    private inner class WuppWebView(context: Context) : WebView(context) {
        override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
            return if (allowTouchThrough) super.dispatchTouchEvent(ev) else false
        }

        fun dispatchSyntheticTap(xRatio: Float, yRatio: Float) {
            val w = width
            val h = height
            if (w <= 0 || h <= 0) return
            val px = xRatio.coerceIn(0.01f, 0.99f) * w
            val py = yRatio.coerceIn(0.01f, 0.99f) * h
            val downTime = SystemClock.uptimeMillis()
            val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, px, py, 0).apply {
                source = InputDevice.SOURCE_TOUCHSCREEN
            }
            super.dispatchTouchEvent(down)
            down.recycle()

            mainHandler.postDelayed({
                val upTime = SystemClock.uptimeMillis()
                val up = MotionEvent.obtain(downTime, upTime, MotionEvent.ACTION_UP, px, py, 0).apply {
                    source = InputDevice.SOURCE_TOUCHSCREEN
                }
                super.dispatchTouchEvent(up)
                up.recycle()
            }, 50L)
        }

        fun dispatchSyntheticKeyText(text: String, sendEnter: Boolean) {
            requestFocus()
            val kcm = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD)
            val chars = text.toCharArray()
            if (chars.isNotEmpty()) {
                kcm.getEvents(chars)?.forEach { ev ->
                    super.dispatchKeyEvent(ev)
                }
            }
            if (sendEnter) {
                mainHandler.postDelayed({
                    val now = SystemClock.uptimeMillis()
                    super.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER, 0))
                    super.dispatchKeyEvent(KeyEvent(now, now + 35L, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER, 0))
                }, 120L)
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    private fun ensureCreatedLocked(): WuppWebView? {
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

        val viewContext = hostContainer?.context ?: backgroundHostContainer?.context ?: appContext
        val wv = runCatching { WuppWebView(viewContext) }.getOrNull() ?: return null

        wv.setBackgroundColor(if (allowTouchThrough) Color.WHITE else Color.TRANSPARENT)
        wv.alpha = if (allowTouchThrough || videoOverlayCssActive) 1f else 0.01f
        wv.isClickable = allowTouchThrough
        wv.isFocusable = true
        wv.isFocusableInTouchMode = true

        val settings = wv.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.mediaPlaybackRequiresUserGesture = false
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
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
        wv.setDownloadListener { _, _, _, _, _ -> }
        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url ?: return false
                val schemeOk = url.scheme.equals("https", ignoreCase = true)
                val hostOk = url.host?.lowercase() == "web.whatsapp.com"
                return !(schemeOk && hostOk)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                val loadedUrl = url.orEmpty()
                if (!loadedUrl.startsWith("https://web.whatsapp.com")) return
                view?.evaluateJavascript(JS_INSTALL_PASSIVE_OBSERVER, null)
                if (videoOverlayCssActive) {
                    view?.evaluateJavascript(JS_ENABLE_VIDEO_CSS, null)
                }
                val digits = pendingPairingPhoneDigits
                if (digits.isNotEmpty() && view != null) {
                    setHighPriorityRenderer(true)
                    injectPairingAutomation(view, digits)
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
            if (parsed == WebLinkState.AUTHENTICATED || parsed == WebLinkState.PAIRING_CODE_READY || parsed == WebLinkState.ERROR) {
                pendingPairingPhoneDigits = ""
            }
            mainHandler.post { listener.onLinkStateChanged(parsed, code.trim()) }
        }

        @JavascriptInterface
        fun onPairingStatus(status: String) {
            mainHandler.post { listener.onPairingStatusChanged(status.trim()) }
        }

        @JavascriptInterface
        fun requestNativeTap(xRatio: Double, yRatio: Double) {
            mainHandler.post {
                webView?.dispatchSyntheticTap(xRatio.toFloat(), yRatio.toFloat())
            }
        }

        @JavascriptInterface
        fun requestNativeKeyText(text: String, sendEnter: Boolean) {
            mainHandler.post {
                webView?.dispatchSyntheticKeyText(text, sendEnter)
            }
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
                var attrEl = document.querySelector('[data-link-code]');
                if (attrEl) {
                  var rawAttr = (attrEl.getAttribute('data-link-code') || '').replace(/[^A-Z0-9]/gi, '').toUpperCase();
                  if (/^[A-Z0-9]{8}$/.test(rawAttr)) {
                    return rawAttr.slice(0, 4) + '-' + rawAttr.slice(4, 8);
                  }
                }
                var boxes = document.querySelectorAll('[data-testid*="link-device-phone-number-code"], [aria-details*="link-device-phone-number-code"], [aria-label*="Enter code" i], [aria-label*="pairing code" i], [data-ref]');
                for (var b = 0; b < boxes.length; b++) {
                  var raw = textOf(boxes[b]).replace(/[^A-Z0-9-]/gi, '').toUpperCase();
                  var m = raw.match(/([A-Z0-9]{4}-?[A-Z0-9]{4})/);
                  if (m && m[1] && !/LINK|CODE|WHAT|PHON|NUMB/.test(m[1])) {
                    var c = m[1].replace(/-/g, '');
                    if (c.length === 8) return c.slice(0, 4) + '-' + c.slice(4, 8);
                  }
                }
                var singleEls = document.querySelectorAll('span, div, strong, b');
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
                    bucket.push('');
                  }
                  bucket[idx] += t;
                }
                for (var k = 0; k < bucket.length; k++) {
                  if (/^[A-Z0-9]{8}$/.test(bucket[k])) {
                    return bucket[k].slice(0, 4) + '-' + bucket[k].slice(4, 8);
                  }
                }
                return '';
              }
              window.__wuppReadPairingCode = readPairingCode;

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
              var myGen = (window.__wuppPairGen || 0) + 1;
              window.__wuppPairGen = myGen;
              var attempts = 0;
              var submitted = false;
              var submittedAt = 0;
              var lastStatus = '';
              var lastAltClickAt = 0;
              var altClickCount = 0;
              var hasClickedAltLink = false;
              var lastClickedDesc = 'none';

              function reportStatus(msg) {
                if (msg && msg !== lastStatus) {
                  lastStatus = msg;
                  try { WuppCallBridge.onPairingStatus(msg); } catch (e) {}
                }
              }

              function normText(el) {
                return ((el && (el.innerText || el.textContent)) || '').replace(/\s+/g, ' ').trim();
              }

              function isBlockedText(s) {
                return /terms|privacy|policy|download|get the app|windows|mac|microsoft|help/i.test(s || '');
              }

              function isElementUsable(el) {
                if (!el || el.disabled) return false;
                try {
                  var cs = window.getComputedStyle(el);
                  if (!cs || cs.display === 'none' || cs.visibility === 'hidden') return false;
                  var r = el.getBoundingClientRect();
                  return r.width > 10 && r.height > 10;
                } catch (e) {
                  return true;
                }
              }

              function readCodeFallback() {
                if (typeof window.__wuppReadPairingCode === 'function') {
                  var fromObs = window.__wuppReadPairingCode();
                  if (fromObs) return fromObs;
                }
                var attrEl = document.querySelector('[data-link-code]');
                if (attrEl) {
                  var rawAttr = (attrEl.getAttribute('data-link-code') || '').replace(/[^A-Z0-9]/gi, '').toUpperCase();
                  if (/^[A-Z0-9]{8}$/.test(rawAttr)) {
                    return rawAttr.slice(0, 4) + '-' + rawAttr.slice(4, 8);
                  }
                }
                return '';
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
                    var t = (el.type || '').toLowerCase();
                    if (t === 'checkbox' || t === 'radio' || t === 'hidden') continue;
                    var ph = (el.getAttribute('placeholder') || el.getAttribute('aria-label') || '').toLowerCase();
                    if (ph.indexOf('search') !== -1 || ph.indexOf('country') !== -1) continue;
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
                    var t = (node.nodeValue || '').replace(/\s+/g, ' ').trim();
                    if (t.length < 12 || t.length > 60) continue;
                    if (isBlockedText(t)) continue;
                    if (!phoneRe.test(t)) continue;
                    var el = node.parentElement;
                    if (!el) continue;
                    var cur = el;
                    var bestClickable = el;
                    var depth = 0;
                    while (cur && cur !== document.body && depth < 5) {
                      var cTxt = normText(cur);
                      if (cTxt.length > 65 || isBlockedText(cTxt)) break;
                      var href = cur.getAttribute ? (cur.getAttribute('href') || '') : '';
                      if (href && href !== '#' && href.indexOf('web.whatsapp.com') === -1) break;
                      bestClickable = cur;
                      var role = cur.getAttribute ? (cur.getAttribute('role') || '') : '';
                      var tag = (cur.tagName || '').toLowerCase();
                      if (role === 'button' || tag === 'button' || tag === 'a' || cur.hasAttribute('tabindex')) {
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

              function invokeSingleReactHandler(nodes) {
                for (var n = 0; n < nodes.length; n++) {
                  var node = nodes[n];
                  if (!node) continue;
                  try {
                    var keys = Object.keys(node);
                    for (var k = 0; k < keys.length; k++) {
                      if (keys[k].indexOf('__reactProps$') === 0) {
                        var rp = node[keys[k]];
                        var fn = rp && (rp.onClick || rp.onPress || rp.onPointerUp);
                        if (typeof fn === 'function') {
                          fn({
                            type: 'click',
                            target: node,
                            currentTarget: node,
                            preventDefault: function() {},
                            stopPropagation: function() {},
                            nativeEvent: new MouseEvent('click', { bubbles: true, cancelable: true })
                          });
                          return true;
                        }
                      }
                      if (keys[k].indexOf('__reactFiber$') === 0) {
                        var fb = node[keys[k]];
                        var mp = fb && fb.memoizedProps;
                        var ffn = mp && (mp.onClick || mp.onPress);
                        if (typeof ffn === 'function') {
                          ffn({
                            type: 'click',
                            target: node,
                            currentTarget: node,
                            preventDefault: function() {},
                            stopPropagation: function() {},
                            nativeEvent: new MouseEvent('click', { bubbles: true, cancelable: true })
                          });
                          return true;
                        }
                      }
                    }
                  } catch (e) {}
                }
                return false;
              }

              function activateElement(el, attemptNum) {
                if (!el) return;
                var tgt = (el.closest && el.closest('[role="button"],button,a,[tabindex]')) || el;
                var tgtTxt = normText(tgt);
                if (tgtTxt.length > 75 || isBlockedText(tgtTxt)) {
                  tgt = el;
                }
                try {
                  if (tgt.scrollIntoView) {
                    tgt.scrollIntoView({ block: 'center', inline: 'center' });
                  }
                } catch (e) {}
                try { tgt.focus(); } catch (e) {}
                var r = tgt.getBoundingClientRect ? tgt.getBoundingClientRect() : null;
                var cx = (r && r.width > 0) ? (r.left + r.width / 2) : 200;
                var cy = (r && r.height > 0) ? (r.top + r.height / 2) : 200;
                var evOpts = { bubbles: true, cancelable: true, view: window, clientX: cx, clientY: cy, button: 0, buttons: 1 };
                try { tgt.dispatchEvent(new PointerEvent('pointerdown', evOpts)); } catch (e) {}
                try { tgt.dispatchEvent(new MouseEvent('mousedown', evOpts)); } catch (e) {}
                try { tgt.dispatchEvent(new PointerEvent('pointerup', evOpts)); } catch (e) {}
                try { tgt.dispatchEvent(new MouseEvent('mouseup', evOpts)); } catch (e) {}
                try { tgt.click(); } catch (e) {
                  try { tgt.dispatchEvent(new MouseEvent('click', evOpts)); } catch (e2) {}
                }
                if ((attemptNum || 1) >= 2) {
                  invokeSingleReactHandler([tgt, el, tgt.parentElement]);
                  try {
                    tgt.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', code: 'Enter', keyCode: 13, which: 13, bubbles: true, cancelable: true }));
                    tgt.dispatchEvent(new KeyboardEvent('keyup', { key: 'Enter', code: 'Enter', keyCode: 13, which: 13, bubbles: true, cancelable: true }));
                    tgt.dispatchEvent(new KeyboardEvent('keydown', { key: ' ', code: 'Space', keyCode: 32, which: 32, bubbles: true, cancelable: true }));
                    tgt.dispatchEvent(new KeyboardEvent('keyup', { key: ' ', code: 'Space', keyCode: 32, which: 32, bubbles: true, cancelable: true }));
                  } catch (e) {}
                }
              }

              function setReactInputValue(input, val) {
                input.focus();
                try {
                  var prevVal = input.value;
                  var tracker = input._valueTracker;
                  if (tracker) { tracker.setValue(''); }
                  document.execCommand('selectAll', false, null);
                  document.execCommand('insertText', false, val);
                  if (input.value !== val) {
                    var setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
                    setter.call(input, val);
                    if (tracker) { tracker.setValue(prevVal); }
                    input.dispatchEvent(new InputEvent('input', { bubbles: true, data: val, inputType: 'insertText' }));
                    input.dispatchEvent(new Event('change', { bubbles: true }));
                  }
                } catch (e) {
                  var setter2 = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
                  setter2.call(input, val);
                  input.dispatchEvent(new Event('input', { bubbles: true }));
                  input.dispatchEvent(new Event('change', { bubbles: true }));
                }
                if ((input.value || '').indexOf(val) === -1) {
                  try {
                    var keys = Object.keys(input);
                    for (var k = 0; k < keys.length; k++) {
                      if (keys[k].indexOf('__reactProps$') === 0) {
                        var rp = input[keys[k]];
                        if (rp && typeof rp.onChange === 'function') {
                          rp.onChange({
                            target: input,
                            currentTarget: input,
                            preventDefault: function() {},
                            stopPropagation: function() {}
                          });
                        }
                      }
                    }
                  } catch (e) {}
                }
              }

              function clickNextButton(input) {
                var nextBtn = document.querySelector('[data-testid*="link-device-phone-number-code-entry-next"]');
                if (nextBtn && isElementUsable(nextBtn)) {
                  activateElement(nextBtn);
                  lastClickedDesc = 'next-testid';
                  return true;
                }
                var candidates = document.querySelectorAll('button, [role="button"], a, span, div');
                var best = null;
                var bestLen = 99;
                for (var i = 0; i < candidates.length; i++) {
                  var el = candidates[i];
                  if (!isElementUsable(el)) continue;
                  var al = (el.getAttribute('aria-label') || '').trim();
                  var txt = normText(el);
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
                  activateElement(best);
                  lastClickedDesc = 'next:' + (normText(best) || best.getAttribute('aria-label') || 'btn').slice(0, 16);
                }
                try {
                  input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', code: 'Enter', keyCode: 13, which: 13, bubbles: true }));
                  input.dispatchEvent(new KeyboardEvent('keyup', { key: 'Enter', code: 'Enter', keyCode: 13, which: 13, bubbles: true }));
                } catch (e) {}
                return !!best;
              }

              function step() {
                if (window.__wuppPairGen !== myGen) return;
                attempts++;

                var authenticated = !!(document.querySelector('#pane-side') || document.querySelector('[data-testid="chat-list-search"]'));
                if (authenticated) {
                  WuppCallBridge.onLinkUpdate('AUTHENTICATED', '');
                  return;
                }

                var code = readCodeFallback();
                if (code) {
                  WuppCallBridge.onLinkUpdate('PAIRING_CODE_READY', code);
                  return;
                }

                var input = findVisiblePhoneInput();
                if (input && digits) {
                  if (!submitted || (Date.now() - submittedAt >= 6000)) {
                    submitted = true;
                    submittedAt = Date.now();
                    reportStatus('Requesting 8-digit pairing code… (' + attempts + ')');
                    var prefillDigits = (input.value || '').replace(/\D/g, '');
                    activateElement(input);
                    input.focus();
                    setReactInputValue(input, '+' + digits);
                    var afterDigits = (input.value || '').replace(/\D/g, '');
                    if (prefillDigits.length > 0 && prefillDigits.length <= 4 && afterDigits === (prefillDigits + digits)) {
                      setReactInputValue(input, digits.slice(prefillDigits.length));
                    }
                    var tail = digits.slice(-7);
                    if ((input.value || '').replace(/\D/g, '').indexOf(tail) === -1) {
                      try { WuppCallBridge.requestNativeKeyText('+' + digits, false); } catch (e) {}
                    }
                    setTimeout(function() {
                      if (window.__wuppPairGen !== myGen) return;
                      clickNextButton(input);
                    }, 550);
                  }
                } else {
                  var altEl = findAltLinkingElement();
                  if (altEl) {
                    hasClickedAltLink = true;
                    reportStatus('Opening phone linking screen… (' + attempts + ')');
                    var now = Date.now();
                    if (now - lastAltClickAt >= 2800) {
                      lastAltClickAt = now;
                      altClickCount++;
                      lastClickedDesc = 'alt:' + (altEl.tagName || '') + ':' + normText(altEl).slice(0, 28);
                      activateElement(altEl, altClickCount);
                    }
                  } else if (!hasClickedAltLink) {
                    reportStatus('Waiting for WhatsApp Web pairing page… (' + attempts + ')');
                  } else {
                    reportStatus('Opening phone linking screen… (' + attempts + ')');
                  }
                }

                if (attempts < 45) {
                  setTimeout(step, 650);
                } else {
                  var inputCnt = document.querySelectorAll('input').length;
                  var snippet = document.body ? (document.body.innerText || '').replace(/\s+/g, ' ').trim().slice(0, 90) : '';
                  reportStatus('Timed out (inputs=' + inputCnt + ', clicked=' + lastClickedDesc + '): ' + snippet);
                  WuppCallBridge.onLinkUpdate('ERROR', '');
                }
              }

              setTimeout(step, 250);
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

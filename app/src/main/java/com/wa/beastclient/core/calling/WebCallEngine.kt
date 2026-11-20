package com.wa.beastclient.core.calling

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.os.Message
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
import androidx.webkit.UserAgentMetadata
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
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
        fun onEnvDiagnostics(diag: String) {}
    }

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var webView: WuppWebView? = null
    private var popoutWebView: WuppWebView? = null
    private var hostContainer: ViewGroup? = null
    private var backgroundHostContainer: ViewGroup? = null
    private var allowTouchThrough = false
    private var videoOverlayCssActive = false
    private var lastTeardownMs = 0L
    private var pendingWarmRunnable: Runnable? = null
    @Volatile
    private var pendingPairingPhoneDigits: String = ""
    @Volatile
    private var pendingSwRefreshOnUpgrade: Boolean = false

    val isAttachedAndLoaded: Boolean
        get() = webView != null

    private fun activeCallWebView(): WuppWebView? = popoutWebView ?: webView

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
            attachChildView(wv, container, visible)
            popoutWebView?.let { popup ->
                attachChildView(popup, container, visible)
            }
            setVideoCssActive(showVideoSurface)
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

    private fun attachChildView(wv: WuppWebView, container: ViewGroup, visible: Boolean) {
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
        wv.isClickable = true
        wv.isFocusable = true
        wv.isFocusableInTouchMode = true
        wv.onResume()
        wv.resumeTimers()
        wv.alpha = if (visible) 1f else 0.01f
        wv.requestLayout()
        wv.invalidate()
    }

    fun detachIfHostedBy(container: ViewGroup) {
        mainHandler.post {
            popoutWebView?.let { popup ->
                if (popup.parent === container) {
                    container.removeView(popup)
                }
            }
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
            val wv = activeCallWebView() ?: return@post
            wv.alpha = if (active || allowTouchThrough) 1f else 0.01f
            val js = if (active) JS_ENABLE_VIDEO_CSS else JS_DISABLE_VIDEO_CSS
            wv.evaluateJavascript(js, null)
            if (popoutWebView != null && webView != null) {
                webView?.evaluateJavascript(JS_DISABLE_VIDEO_CSS, null)
            }
        }
    }

    fun setWebRingingMuted(muted: Boolean) {
        mainHandler.post {
            val js = "try{document.querySelectorAll('audio').forEach(function(a){a.muted=${if (muted) "true" else "false"};});}catch(e){}"
            webView?.evaluateJavascript(js, null)
            popoutWebView?.evaluateJavascript(js, null)
        }
    }

    fun reloadCleanPreservingLogin() {
        mainHandler.post {
            destroyPopoutWindow()
            val wv = ensureCreatedLocked() ?: return@post
            listener.onLinkStateChanged(WebLinkState.LOADING, "")
            wv.clearCache(false)
            val unregisterAndReload = """
                (function() {
                  try {
                    if (navigator.serviceWorker && navigator.serviceWorker.getRegistrations) {
                      navigator.serviceWorker.getRegistrations().then(function(regs) {
                        for (var i = 0; i < regs.length; i++) {
                          try { regs[i].unregister(); } catch (e) {}
                        }
                        location.reload(true);
                      }).catch(function() { location.reload(true); });
                      return;
                    }
                  } catch (e) {}
                  location.reload(true);
                })();
            """.trimIndent()
            wv.evaluateJavascript(unregisterAndReload, null)
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
            activeCallWebView()?.evaluateJavascript(js, null)
        }
    }

    fun declineOrEndCall() {
        mainHandler.post {
            activeCallWebView()?.evaluateJavascript(JS_END_OR_DECLINE_CALL, null)
            mainHandler.postDelayed({ destroyPopoutWindow() }, 500L)
            returnToNeutralLandingAfterDelay()
        }
    }

    fun toggleMuteInWeb(targetMuted: Boolean) {
        mainHandler.post {
            val js = JS_TOGGLE_MUTE_TEMPLATE.replace("__MUTED__", if (targetMuted) "true" else "false")
            activeCallWebView()?.evaluateJavascript(js, null)
        }
    }

    fun toggleCameraInWeb(cameraOff: Boolean) {
        mainHandler.post {
            val js = JS_TOGGLE_CAMERA_TEMPLATE.replace("__CAM_OFF__", if (cameraOff) "true" else "false")
            activeCallWebView()?.evaluateJavascript(js, null)
        }
    }

    fun flipCameraInWeb() {
        mainHandler.post {
            activeCallWebView()?.evaluateJavascript(JS_FLIP_CAMERA, null)
        }
    }

    fun upgradeAudioCallToVideo() {
        mainHandler.post {
            activeCallWebView()?.evaluateJavascript(JS_UPGRADE_TO_VIDEO, null)
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

    private fun destroyPopoutWindow() {
        val popup = popoutWebView ?: return
        popoutWebView = null
        runCatching {
            (popup.parent as? ViewGroup)?.removeView(popup)
            popup.stopLoading()
            popup.removeJavascriptInterface("WuppCallBridge")
            popup.loadUrl("about:blank")
            popup.destroy()
        }
    }

    fun teardownImmediate(clearSessionData: Boolean = false) {
        pendingPairingPhoneDigits = ""
        mainHandler.post {
            pendingWarmRunnable?.let { mainHandler.removeCallbacks(it) }
            pendingWarmRunnable = null
            destroyPopoutWindow()
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
            if (text == "__CLEAR__") {
                val now = SystemClock.uptimeMillis()
                super.dispatchKeyEvent(
                    KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A, 0, KeyEvent.META_CTRL_ON)
                )
                super.dispatchKeyEvent(
                    KeyEvent(now, now + 20L, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_A, 0, KeyEvent.META_CTRL_ON)
                )
                super.dispatchKeyEvent(KeyEvent(now + 30L, now + 30L, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL, 0))
                super.dispatchKeyEvent(KeyEvent(now + 30L, now + 50L, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL, 0))
                return
            }
            val kcm = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD)
            val chars = text.toCharArray()
            if (chars.isNotEmpty()) {
                val events = kcm.getEvents(chars)
                if (events != null) {
                    events.forEach { ev -> super.dispatchKeyEvent(ev) }
                } else {
                    chars.forEach { ch ->
                        kcm.getEvents(charArrayOf(ch))?.forEach { ev -> super.dispatchKeyEvent(ev) }
                    }
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
        val prefs = appContext.getSharedPreferences(PREFS_CALL_ENGINE, Context.MODE_PRIVATE)
        val savedRev = prefs.getInt(KEY_ENV_REV, 0)
        if (savedRev < CURRENT_ENV_REV) {
            runCatching { wv.clearCache(false) }
            prefs.edit().putInt(KEY_ENV_REV, CURRENT_ENV_REV).apply()
            pendingSwRefreshOnUpgrade = true
        }
        configureDesktopWebView(wv, isPopout = false)

        webView = wv
        hostContainer?.let { container ->
            attachChildView(wv, container, allowTouchThrough || videoOverlayCssActive)
        }
        listener.onLinkStateChanged(WebLinkState.LOADING, "")
        wv.loadUrl(WA_WEB_URL)
        return wv
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    private fun configureDesktopWebView(wv: WuppWebView, isPopout: Boolean) {
        wv.setBackgroundColor(if (allowTouchThrough) Color.WHITE else Color.TRANSPARENT)
        wv.alpha = if (allowTouchThrough || videoOverlayCssActive) 1f else 0.01f
        wv.isClickable = true
        wv.isFocusable = true
        wv.isFocusableInTouchMode = true

        val settings = wv.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        @Suppress("DEPRECATION")
        settings.databaseEnabled = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.mediaPlaybackRequiresUserGesture = false
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.setSupportMultipleWindows(true)
        settings.javaScriptCanOpenWindowsAutomatically = true
        @Suppress("DEPRECATION")
        settings.saveFormData = false
        settings.setGeolocationEnabled(false)
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.userAgentString = buildChromiumMatchedDesktopUa(WebSettings.getDefaultUserAgent(appContext))

        if (WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)) {
            runCatching {
                val currentMeta = WebSettingsCompat.getUserAgentMetadata(settings)
                val desktopBrands = currentMeta.brandVersionList.map { bv ->
                    if (bv.brand.contains("WebView", ignoreCase = true)) {
                        UserAgentMetadata.BrandVersion.Builder(bv)
                            .setBrand("Google Chrome")
                            .build()
                    } else {
                        bv
                    }
                }
                val desktopMeta = UserAgentMetadata.Builder(currentMeta)
                    .setBrandVersionList(desktopBrands)
                    .setPlatform("Windows")
                    .setPlatformVersion("10.0.0")
                    .setArchitecture("x86")
                    .setBitness(64)
                    .setModel("")
                    .setMobile(false)
                    .setWow64(false)
                    .build()
                WebSettingsCompat.setUserAgentMetadata(settings, desktopMeta)
            }
        }

        if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
            runCatching {
                WebSettingsCompat.setRequestedWithHeaderOriginAllowList(settings, emptySet())
            }
        }

        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            runCatching {
                WebViewCompat.addDocumentStartJavaScript(
                    wv,
                    JS_DESKTOP_ENV_DOCUMENT_START,
                    setOf("https://web.whatsapp.com")
                )
            }
        }

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)
        val priority = if (isPopout) WebView.RENDERER_PRIORITY_IMPORTANT else WebView.RENDERER_PRIORITY_WAIVED
        runCatching { wv.setRendererPriorityPolicy(priority, false) }

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
                if (!isPopout && pendingSwRefreshOnUpgrade) {
                    pendingSwRefreshOnUpgrade = false
                    view?.evaluateJavascript(
                        """
                        (function() {
                          try {
                            if (navigator.serviceWorker && navigator.serviceWorker.getRegistrations) {
                              navigator.serviceWorker.getRegistrations().then(function(regs) {
                                if (regs && regs.length > 0) {
                                  Promise.all(regs.map(function(r) { return r.unregister(); })).then(function() {
                                    location.reload(true);
                                  });
                                }
                              });
                            }
                          } catch (e) {}
                        })();
                        """.trimIndent(),
                        null
                    )
                }
                view?.evaluateJavascript(JS_DESKTOP_ENV_DOCUMENT_START, null)
                view?.evaluateJavascript(JS_INSTALL_PASSIVE_OBSERVER, null)
                if (videoOverlayCssActive) {
                    view?.evaluateJavascript(JS_ENABLE_VIDEO_CSS, null)
                }
                if (!isPopout) {
                    val digits = pendingPairingPhoneDigits
                    if (digits.isNotEmpty() && view != null) {
                        setHighPriorityRenderer(true)
                        injectPairingAutomation(view, digits)
                    }
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

            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?
            ): Boolean {
                val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                destroyPopoutWindow()
                val ctx = hostContainer?.context ?: backgroundHostContainer?.context ?: appContext
                val popup = runCatching { WuppWebView(ctx) }.getOrNull() ?: return false
                configureDesktopWebView(popup, isPopout = true)
                popoutWebView = popup
                hostContainer?.let { container ->
                    attachChildView(popup, container, allowTouchThrough || videoOverlayCssActive)
                }
                transport.webView = popup
                resultMsg.sendToTarget()
                return true
            }

            override fun onCloseWindow(window: WebView?) {
                if (window === popoutWebView) {
                    destroyPopoutWindow()
                }
            }
        }
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

        @JavascriptInterface
        fun hasMediaPermission(kind: String): Boolean {
            val perm = when (kind.lowercase()) {
                "camera", "video" -> Manifest.permission.CAMERA
                else -> Manifest.permission.RECORD_AUDIO
            }
            return ContextCompat.checkSelfPermission(appContext, perm) == PackageManager.PERMISSION_GRANTED
        }

        @JavascriptInterface
        fun onEnvDiagnostics(diag: String) {
            mainHandler.post {
                listener.onEnvDiagnostics(diag)
            }
        }
    }

    companion object {
        private const val WA_WEB_URL = "https://web.whatsapp.com/"
        const val MIN_RECONNECT_COOLDOWN_MS = 45_000L
        private const val PREFS_CALL_ENGINE = "wupp_call_engine_prefs"
        private const val KEY_ENV_REV = "calling_web_env_rev"
        private const val CURRENT_ENV_REV = 14

        private val JS_DESKTOP_ENV_DOCUMENT_START = """
            (function() {
              if (window.__wuppEnvApplied) return;
              window.__wuppEnvApplied = true;

              function safeDefine(obj, prop, getter) {
                try {
                  Object.defineProperty(obj, prop, {
                    configurable: true,
                    enumerable: true,
                    get: getter
                  });
                  return true;
                } catch (e) {
                  return false;
                }
              }

              var navProto = (typeof Navigator !== 'undefined' && Navigator.prototype) || navigator;
              if (!safeDefine(navProto, 'platform', function() { return 'Win32'; })) {
                safeDefine(navigator, 'platform', function() { return 'Win32'; });
              }
              if (!safeDefine(navProto, 'maxTouchPoints', function() { return 0; })) {
                safeDefine(navigator, 'maxTouchPoints', function() { return 0; });
              }
              try {
                delete window.ontouchstart;
                if (typeof Window !== 'undefined' && Window.prototype) delete Window.prototype.ontouchstart;
                if (typeof Document !== 'undefined' && Document.prototype) delete Document.prototype.ontouchstart;
                if (typeof HTMLElement !== 'undefined' && HTMLElement.prototype) delete HTMLElement.prototype.ontouchstart;
              } catch (e) {}

              try {
                var uad = navigator.userAgentData;
                if (!uad || uad.mobile || uad.platform !== 'Windows') {
                  var m = (navigator.userAgent || '').match(/Chrome\/(\d+)(?:\.(\d+\.\d+\.\d+))?/);
                  var major = (m && m[1]) ? m[1] : '131';
                  var fullVer = (m && m[2]) ? (major + '.' + m[2]) : (major + '.0.0.0');
                  var brands = [
                    { brand: 'Not-A.Brand', version: '99' },
                    { brand: 'Google Chrome', version: major },
                    { brand: 'Chromium', version: major }
                  ];
                  var fullBrands = [
                    { brand: 'Not-A.Brand', version: '99.0.0.0' },
                    { brand: 'Google Chrome', version: fullVer },
                    { brand: 'Chromium', version: fullVer }
                  ];
                  var pcUad = {
                    brands: brands,
                    mobile: false,
                    platform: 'Windows',
                    getHighEntropyValues: function(hints) {
                      return Promise.resolve({
                        brands: brands,
                        mobile: false,
                        platform: 'Windows',
                        platformVersion: '10.0.0',
                        architecture: 'x86',
                        bitness: '64',
                        model: '',
                        uaFullVersion: fullVer,
                        fullVersionList: fullBrands,
                        wow64: false
                      });
                    },
                    toJSON: function() {
                      return { brands: brands, mobile: false, platform: 'Windows' };
                    }
                  };
                  if (!safeDefine(navProto, 'userAgentData', function() { return pcUad; })) {
                    safeDefine(navigator, 'userAgentData', function() { return pcUad; });
                  }
                }
              } catch (e) {}

              try {
                var scrProto = (typeof Screen !== 'undefined' && Screen.prototype) || window.screen;
                safeDefine(scrProto, 'width', function() { return 1366; });
                safeDefine(scrProto, 'height', function() { return 768; });
                safeDefine(scrProto, 'availWidth', function() { return 1366; });
                safeDefine(scrProto, 'availHeight', function() { return 728; });
                safeDefine(scrProto, 'colorDepth', function() { return 24; });
                safeDefine(scrProto, 'pixelDepth', function() { return 24; });
              } catch (e) {}

              try {
                var origMatchMedia = window.matchMedia && window.matchMedia.bind(window);
                if (origMatchMedia) {
                  window.matchMedia = function(q) {
                    var res = origMatchMedia(q);
                    if (typeof q === 'string') {
                      var ql = q.toLowerCase();
                      var forced = null;
                      if (ql.indexOf('pointer: fine') !== -1 || ql.indexOf('any-pointer: fine') !== -1 ||
                          ql.indexOf('hover: hover') !== -1 || ql.indexOf('any-hover: hover') !== -1) {
                        forced = true;
                      } else if (ql.indexOf('pointer: coarse') !== -1 || ql.indexOf('any-pointer: coarse') !== -1 ||
                          ql.indexOf('hover: none') !== -1 || ql.indexOf('any-hover: none') !== -1) {
                        forced = false;
                      }
                      if (forced !== null) {
                        return {
                          matches: forced,
                          media: q,
                          onchange: null,
                          addListener: function() {},
                          removeListener: function() {},
                          addEventListener: function() {},
                          removeEventListener: function() {},
                          dispatchEvent: function() { return false; }
                        };
                      }
                    }
                    return res;
                  };
                }
              } catch (e) {}

              try {
                if (navigator.permissions && typeof navigator.permissions.query === 'function') {
                  var origPermQuery = navigator.permissions.query.bind(navigator.permissions);
                  navigator.permissions.query = function(desc) {
                    var nm = (desc && desc.name ? String(desc.name) : '').toLowerCase();
                    if (nm === 'microphone' || nm === 'camera') {
                      var hasNative = false;
                      try { hasNative = !!(window.WuppCallBridge && WuppCallBridge.hasMediaPermission(nm)); } catch (e) {}
                      return origPermQuery(desc).then(function(status) {
                        if (status && status.state === 'granted') return status;
                        if (hasNative) {
                          return {
                            state: 'granted',
                            name: nm,
                            onchange: null,
                            addEventListener: function() {},
                            removeEventListener: function() {},
                            dispatchEvent: function() { return false; }
                          };
                        }
                        if (status && status.state === 'denied') {
                          return {
                            state: 'prompt',
                            name: nm,
                            onchange: null,
                            addEventListener: function() {},
                            removeEventListener: function() {},
                            dispatchEvent: function() { return false; }
                          };
                        }
                        return status;
                      }).catch(function() {
                        return {
                          state: hasNative ? 'granted' : 'prompt',
                          name: nm,
                          onchange: null,
                          addEventListener: function() {},
                          removeEventListener: function() {},
                          dispatchEvent: function() { return false; }
                        };
                      });
                    }
                    return origPermQuery(desc);
                  };
                }
              } catch (e) {}

              function enforceDesktopViewport() {
                try {
                  var vp = document.querySelector('meta[name="viewport"]');
                  if (vp) {
                    var cur = vp.getAttribute('content') || '';
                    if (cur !== 'width=1024') {
                      vp.setAttribute('content', 'width=1024');
                    }
                  } else if (document.head) {
                    var m = document.createElement('meta');
                    m.name = 'viewport';
                    m.content = 'width=1024';
                    document.head.appendChild(m);
                  }
                } catch (e) {}
              }

              enforceDesktopViewport();
              try {
                var vpObs = new MutationObserver(function() { enforceDesktopViewport(); });
                vpObs.observe(document.documentElement || document, {
                  childList: true,
                  subtree: true,
                  attributes: true,
                  attributeFilter: ['content', 'name']
                });
                if (document.readyState === 'loading') {
                  document.addEventListener('DOMContentLoaded', enforceDesktopViewport);
                }
              } catch (e) {}
            })();
        """.trimIndent()

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

              function readWaModulesDiag() {
                var out = '';
                try {
                  if (typeof window.require === 'function') {
                    try {
                      var uaMod = window.require('WAWebUA');
                      var u = uaMod && (uaMod.UA || uaMod.default || uaMod);
                      if (u) {
                        out += ' waOS=' + u.os + '/br=' + u.browser + '/mob=' + (u.isMobile !== undefined ? u.isMobile : u.device);
                      }
                    } catch (e) {}
                    try {
                      var gMod = window.require('WAWebVoipGatingUtils');
                      if (gMod) {
                        var falseFns = [];
                        var gKeys = Object.keys(gMod);
                        for (var i = 0; i < gKeys.length && falseFns.length < 8; i++) {
                          var k = gKeys[i];
                          try {
                            if (typeof gMod[k] === 'function' && gMod[k].length === 0) {
                              var val = gMod[k]();
                              if (val === false) falseFns.push(k);
                            }
                          } catch (e2) {}
                        }
                        if (falseFns.length) {
                          out += ' gateFalse=[' + falseFns.join(',') + ']';
                        } else {
                          out += ' gateKeys=' + gKeys.length;
                        }
                      }
                    } catch (e) {}
                  }
                } catch (e) {}
                return out;
              }

              function readDisabledCallTooltipDiag() {
                try {
                  var hdr = document.querySelector('#main header, [data-testid="conversation-header"]');
                  if (!hdr) return '';
                  var btns = hdr.querySelectorAll('button, [role="button"], [tabindex]');
                  var res = [];
                  for (var i = 0; i < btns.length; i++) {
                    var b = btns[i];
                    var al = (b.getAttribute('aria-label') || b.getAttribute('title') || '').trim();
                    var cs = window.getComputedStyle ? window.getComputedStyle(b) : null;
                    var dis = !!b.disabled || b.getAttribute('aria-disabled') === 'true' || (cs && parseFloat(cs.opacity || '1') < 0.7);
                    var extra = '';
                    try {
                      var fKey = Object.keys(b).find(function(k) { return k.indexOf('__reactFiber$') === 0; });
                      var cur = fKey ? b[fKey] : null;
                      var depth = 0;
                      while (cur && depth < 10) {
                        var mp = cur.memoizedProps;
                        if (mp) {
                          if (mp.tooltip || mp.tooltipContent || mp.disabledReason || mp.label) {
                            var tip = mp.tooltipContent || mp.tooltip || mp.disabledReason || mp.label;
                            if (typeof tip === 'string' && tip.length > 0 && tip.length < 80) {
                              extra += ' tip=' + tip;
                              break;
                            }
                          }
                        }
                        cur = cur.return;
                        depth++;
                      }
                    } catch (e2) {}
                    if (dis || /call|video|voice/i.test(al)) {
                      res.push((al || '?') + (dis ? '(dis)' : '(ok)') + extra);
                    }
                  }
                  return res.length ? (' btns=[' + res.join(';') + ']') : '';
                } catch (e) {
                  return '';
                }
              }

              var cachedWorkerInfo = '';
              function probeWorkerOnce(cb) {
                if (cachedWorkerInfo) { cb(cachedWorkerInfo); return; }
                try {
                  if (typeof Worker !== 'function' || typeof Blob !== 'function' || !window.URL || !URL.createObjectURL) {
                    cachedWorkerInfo = 'no-worker';
                    cb(cachedWorkerInfo);
                    return;
                  }
                  var code = "var ua=navigator.userAgentData?(navigator.userAgentData.mobile+'/'+navigator.userAgentData.platform):'none';postMessage((navigator.platform||'-')+'|'+ua);close();";
                  var url = URL.createObjectURL(new Blob([code], { type: 'application/javascript' }));
                  var wk = new Worker(url);
                  var done = false;
                  wk.onmessage = function(ev) {
                    if (done) return;
                    done = true;
                    cachedWorkerInfo = String(ev.data || '?');
                    try { URL.revokeObjectURL(url); } catch (e) {}
                    cb(cachedWorkerInfo);
                  };
                  wk.onerror = function() {
                    if (done) return;
                    done = true;
                    cachedWorkerInfo = 'wk-err';
                    cb(cachedWorkerInfo);
                  };
                  setTimeout(function() {
                    if (!done) {
                      done = true;
                      cachedWorkerInfo = 'wk-to';
                      cb(cachedWorkerInfo);
                    }
                  }, 500);
                } catch (e) {
                  cachedWorkerInfo = 'wk-ex';
                  cb(cachedWorkerInfo);
                }
              }

              var lastEnvStr = '';
              function reportEnv() {
                try {
                  var uad = navigator.userAgentData ? (navigator.userAgentData.mobile + '/' + navigator.userAgentData.platform) : 'none';
                  var apis = 'RTC=' + (typeof RTCPeerConnection === 'function' ? 1 : 0) +
                    ',VF=' + (typeof VideoFrame === 'function' ? 1 : 0) +
                    ',VE=' + (typeof VideoEncoder === 'function' ? 1 : 0) +
                    ',AW=' + (typeof AudioWorkletNode === 'function' ? 1 : 0) +
                    ',SWk=' + (typeof SharedWorker !== 'undefined' ? 1 : 0) +
                    ',GPU=' + (navigator.gpu ? 1 : 0) +
                    ',ots=' + (('ontouchstart' in window) ? 1 : 0);
                  var base = 'plt=' + (navigator.platform || '-') +
                    ' | ua=' + uad +
                    ' | touch=' + navigator.maxTouchPoints +
                    ' | vw=' + window.innerWidth + 'x' + window.innerHeight +
                    ' | ' + apis +
                    readWaModulesDiag() +
                    readDisabledCallTooltipDiag();
                  var micSt = '?';
                  var devInfo = '?';
                  var wkInfo = '?';
                  var emit = function() {
                    var full = base + ' | wk=' + wkInfo + ' | mic=' + micSt + ' | devs=' + devInfo;
                    if (full !== lastEnvStr) {
                      lastEnvStr = full;
                      try { WuppCallBridge.onEnvDiagnostics(full); } catch (e) {}
                    }
                  };
                  var p1 = (navigator.permissions && navigator.permissions.query)
                    ? navigator.permissions.query({ name: 'microphone' }).then(function(s) { micSt = s ? s.state : '?'; }).catch(function() { micSt = 'err'; })
                    : Promise.resolve();
                  var p2 = (navigator.mediaDevices && navigator.mediaDevices.enumerateDevices)
                    ? navigator.mediaDevices.enumerateDevices().then(function(ds) {
                        if (!ds) { devInfo = '0'; return; }
                        var ai = 0, vi = 0, ao = 0;
                        for (var d = 0; d < ds.length; d++) {
                          if (ds[d].kind === 'audioinput') ai++;
                          else if (ds[d].kind === 'videoinput') vi++;
                          else if (ds[d].kind === 'audiooutput') ao++;
                        }
                        devInfo = ai + 'a/' + vi + 'v/' + ao + 'o';
                      }).catch(function() { devInfo = 'err'; })
                    : Promise.resolve();
                  var p3 = new Promise(function(resolve) {
                    probeWorkerOnce(function(wi) { wkInfo = wi; resolve(); });
                  });
                  Promise.all([p1, p2, p3]).then(emit, emit);
                } catch (e) {}
              }
              setTimeout(reportEnv, 800);
              setInterval(reportEnv, 2500);
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
              var myGen = (window.__wuppDialGen || 0) + 1;
              window.__wuppDialGen = myGen;

              function norm(s) {
                return (s || '').replace(/\s+/g, ' ').trim();
              }

              function normLower(s) {
                return norm(s).toLowerCase();
              }

              function digitsOnly(s) {
                return (s || '').replace(/\D/g, '');
              }

              function isVisible(el) {
                if (!el || el.disabled) return false;
                try {
                  var cs = window.getComputedStyle(el);
                  if (!cs || cs.display === 'none' || cs.visibility === 'hidden' || cs.opacity === '0') return false;
                  var r = el.getBoundingClientRect();
                  return r.width > 4 && r.height > 4;
                } catch (e) {
                  return true;
                }
              }

              function invokeReactHandler(nodes) {
                for (var n = 0; n < nodes.length; n++) {
                  var node = nodes[n];
                  if (!node) continue;
                  try {
                    var keys = Object.keys(node);
                    for (var k = 0; k < keys.length; k++) {
                      if (keys[k].indexOf('__reactProps$') === 0) {
                        var rp = node[keys[k]];
                        var fn = rp && (rp.onClick || rp.onPress || rp.onMouseDown);
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

              function clickSingle(el, withReactFallback) {
                if (!el) return;
                try { if (el.scrollIntoView) el.scrollIntoView({ block: 'center', inline: 'center' }); } catch (e) {}
                try { el.focus(); } catch (e) {}
                var r = el.getBoundingClientRect ? el.getBoundingClientRect() : null;
                var cx = (r && r.width > 0) ? (r.left + r.width / 2) : 200;
                var cy = (r && r.height > 0) ? (r.top + r.height / 2) : 200;
                var evOpts = { bubbles: true, cancelable: true, view: window, clientX: cx, clientY: cy, button: 0, buttons: 1 };
                try { el.dispatchEvent(new PointerEvent('pointerdown', evOpts)); } catch (e) {}
                try { el.dispatchEvent(new MouseEvent('mousedown', evOpts)); } catch (e) {}
                try { el.dispatchEvent(new PointerEvent('pointerup', evOpts)); } catch (e) {}
                try { el.dispatchEvent(new MouseEvent('mouseup', evOpts)); } catch (e) {}
                try { el.click(); } catch (e) {
                  try { el.dispatchEvent(new MouseEvent('click', evOpts)); } catch (e2) {}
                }
                if (withReactFallback) {
                  invokeReactHandler([el, el.parentElement]);
                }
              }

              function nativeTapEl(el) {
                if (!el) return false;
                try { if (el.scrollIntoView) el.scrollIntoView({ block: 'center', inline: 'center' }); } catch (e) {}
                try {
                  var r = el.getBoundingClientRect();
                  if (r && r.width > 0 && r.height > 0 && window.innerWidth > 0 && window.innerHeight > 0) {
                    var xr = (r.left + r.width / 2) / window.innerWidth;
                    var yr = (r.top + r.height / 2) / window.innerHeight;
                    if (xr > 0 && xr < 1 && yr > 0 && yr < 1) {
                      WuppCallBridge.requestNativeTap(xr, yr);
                      return true;
                    }
                  }
                } catch (e) {}
                return false;
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
                  if (el && isVisible(el)) return el;
                }
                return null;
              }

              function readBoxText(box) {
                if (!box) return '';
                return norm(box.value !== undefined ? box.value : box.textContent);
              }

              function setSearchQuery(box, query) {
                try {
                  var clearBtn = document.querySelector('#side [aria-label*="Cancel search" i], #side [data-icon="x-alt"], #side button[aria-label*="Clear" i]');
                  if (clearBtn && isVisible(clearBtn)) {
                    clearBtn.click();
                  }
                } catch (e) {}
                nativeTapEl(box);
                box.focus();
                try {
                  var sel = window.getSelection && window.getSelection();
                  if (sel && document.createRange) {
                    var range = document.createRange();
                    range.selectNodeContents(box);
                    sel.removeAllRanges();
                    sel.addRange(range);
                  } else {
                    document.execCommand('selectAll', false, null);
                  }
                  document.execCommand('delete', false, null);
                  document.execCommand('insertText', false, query);
                } catch (e) {}
                var cur = readBoxText(box);
                if (cur !== norm(query)) {
                  setTimeout(function() {
                    if (window.__wuppDialGen !== myGen) return;
                    box.focus();
                    try { WuppCallBridge.requestNativeKeyText('__CLEAR__', false); } catch (e) {}
                    try { WuppCallBridge.requestNativeKeyText(query, false); } catch (e) {}
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
                  var txt = norm(row.innerText || row.textContent);
                  if (!txt || txt.length < 2) continue;
                  if (/^(chats|contacts|messages|groups|unread|favorites|archived|locked chats|searching|looking for)/i.test(txt)) continue;
                  if (row.querySelector('[data-testid*="cell-frame-container"], [role="gridcell"], [tabindex], span[title], img')) {
                    valid.push(row);
                  }
                }
                return valid;
              }

              function rowTitleText(row) {
                var spans = row ? row.querySelectorAll('span[title]') : [];
                var out = '';
                for (var i = 0; i < spans.length; i++) {
                  var t = norm(spans[i].getAttribute('title') || spans[i].innerText || '');
                  if (t) out += ' ' + t;
                }
                return norm(out);
              }

              function pickBestChatRow(rows, allowBodyAndFallback, preSearchRows) {
                if (!rows.length) return null;
                var tail7 = digits.length >= 7 ? digits.slice(-7) : digits;
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
                    var txt = norm(rowB.innerText || rowB.textContent);
                    var rowDigits = digitsOnly(txt);
                    if (tail7 && rowDigits.indexOf(tail7) !== -1) return rowB;
                    if (cleanName.length >= 2 && normLower(txt).indexOf(cleanName) !== -1) return rowB;
                  }
                  if (rows.length <= 3) {
                    return rows[0];
                  }
                }
                return null;
              }

              function findMainHeader() {
                return document.querySelector('#main header, [data-testid="conversation-header"], [data-testid="conversation-panel-wrapper"] header, #main [role="banner"]');
              }

              function hasCallEndControl() {
                if (document.querySelector('[data-icon="call-end"], [data-icon="end-call"]')) return true;
                var btns = document.querySelectorAll('button, [role="button"], [aria-label]');
                for (var i = 0; i < btns.length; i++) {
                  var al = norm(btns[i].getAttribute('aria-label') || '');
                  if (/^(end call|hang up|leave call)/i.test(al)) return true;
                }
                return false;
              }

              function findHeaderButtons() {
                var hdr = findMainHeader();
                if (!hdr) return [];
                return hdr.querySelectorAll('button, [role="button"], [tabindex]');
              }

              function isButtonDisabled(el) {
                if (!el) return true;
                if (el.disabled || el.getAttribute('aria-disabled') === 'true') return true;
                try {
                  var cs = window.getComputedStyle(el);
                  if (cs && parseFloat(cs.opacity || '1') < 0.6) return true;
                  var pe = cs ? cs.pointerEvents : '';
                  if (pe === 'none') return true;
                } catch (e) {}
                return false;
              }

              function buttonLabelAndIcon(b) {
                var al = norm(b.getAttribute('aria-label') || b.getAttribute('title') || b.innerText || '');
                if (!al) {
                  var svgTitle = b.querySelector('svg title');
                  if (svgTitle) al = norm(svgTitle.textContent || '');
                }
                var icEl = b.querySelector('[data-icon]');
                var ic = icEl ? (icEl.getAttribute('data-icon') || '').toLowerCase() : '';
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
                    if (!isButtonDisabled(b)) return { el: b, kind: 'direct', disabled: false };
                    disabledMatch = { el: b, kind: 'direct', disabled: true };
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
                    ((uInfo.ic.indexOf('call') !== -1 || uInfo.ic.indexOf('phone') !== -1 || uInfo.ic.indexOf('video') !== -1) &&
                      uInfo.ic.indexOf('missed') === -1 && uInfo.ic.indexOf('history') === -1);
                  if (matchesUnified) {
                    if (!isButtonDisabled(u)) return { el: u, kind: 'unified', disabled: false };
                    if (!disabledMatch) disabledMatch = { el: u, kind: 'unified', disabled: true };
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
                  if (!isVisible(n)) continue;
                  var txt = norm(n.innerText || n.textContent || n.getAttribute('aria-label') || '');
                  if (modeRe.test(txt) || n.querySelector(iconSel)) {
                    return n;
                  }
                }
                for (var j = 0; j < nodes.length; j++) {
                  var d = nodes[j];
                  if (d === excludeBtn || (excludeBtn && excludeBtn.contains(d))) continue;
                  if (!isVisible(d)) continue;
                  if (!d.closest('[role="dialog"]')) continue;
                  var dt = norm(d.innerText || d.textContent || d.getAttribute('aria-label') || '');
                  if (/^(start call|call|continue|allow|ok)$/i.test(dt)) {
                    return d;
                  }
                }
                return null;
              }

              function readUnsupportedDialogReason() {
                var dlgs = document.querySelectorAll('[role="dialog"]');
                for (var i = 0; i < dlgs.length; i++) {
                  if (!isVisible(dlgs[i])) continue;
                  var txt = norm(dlgs[i].innerText || dlgs[i].textContent);
                  if (/download whatsapp|get the app|get whatsapp|browser isn.?t supported|not available on whatsapp web/i.test(txt)) {
                    return txt.slice(0, 120);
                  }
                }
                return '';
              }

              function buildHeaderDiag(rowCount) {
                var hdr = findMainHeader();
                var mainAny = !!document.querySelector('#main, [data-testid="conversation-panel-wrapper"], [role="main"]');
                var btns = findHeaderButtons();
                var list = [];
                for (var i = 0; i < btns.length && i < 8; i++) {
                  var b = btns[i];
                  var info = buttonLabelAndIcon(b);
                  var tx = norm(b.innerText || '').slice(0, 16);
                  var dis = isButtonDisabled(b) ? '(dis)' : '';
                  list.push((info.al || tx || '-') + '@' + (info.ic || '-') + dis);
                }
                var box = findSearchBox();
                var boxVal = readBoxText(box).slice(0, 18);
                var dlgs = document.querySelectorAll('[role="dialog"]').length;
                var ua = navigator.userAgentData ? (navigator.userAgentData.mobile + '/' + navigator.userAgentData.platform) : 'none';
                return 'w=' + window.innerWidth + 'x' + window.innerHeight +
                  ',ua=' + ua + ',plt=' + (navigator.platform || '-') +
                  ',main=' + (!!hdr) + '/' + mainAny + ',rows=' + rowCount + ',box=' + boxVal + ',dlgs=' + dlgs + ',hdr=[' + list.join(';') + ']';
              }

              var queries = [];
              if (digits) queries.push('+' + digits);
              if (digits.length > 10) queries.push(digits.slice(-10));
              if (digits) queries.push(digits);
              if (contactName && queries.indexOf(contactName) === -1) queries.push(contactName);
              var uniqueQueries = [];
              for (var q = 0; q < queries.length; q++) {
                if (queries[q] && uniqueQueries.indexOf(queries[q]) === -1) uniqueQueries.push(queries[q]);
              }
              if (!uniqueQueries.length) {
                WuppCallBridge.onDialResult(false, 'No phone number available for this contact');
                return;
              }

              var queryIdx = 0;
              var phase = 'WAIT_SEARCH_BOX';
              var phaseTicks = 0;
              var rowClicked = false;
              var initialRowCount = getValidChatRows().length;
              var lastRowCount = initialRowCount;
              var matchedQueryPrefix = '';
              var clickedCallInfo = null;
              var callBtnRetries = 0;
              var submenuClicked = false;

              function runSearchAttempt(box) {
                initialRowCount = Math.max(initialRowCount, getValidChatRows().length);
                matchedQueryPrefix = normLower(uniqueQueries[queryIdx]).slice(0, 4);
                setSearchQuery(box, uniqueQueries[queryIdx]);
                phase = 'WAIT_ROW';
                phaseTicks = 0;
              }

              function openChatRow(row, useNative) {
                var clickTarget = row.querySelector('[data-testid*="cell-frame-container"], [role="gridcell"], [tabindex="-1"], [tabindex="0"]') || row;
                clickSingle(clickTarget, true);
                if (useNative) {
                  nativeTapEl(clickTarget);
                }
              }

              function tick() {
                if (window.__wuppDialGen !== myGen) return;
                phaseTicks++;

                if (hasCallEndControl()) {
                  WuppCallBridge.onDialResult(true, '');
                  return;
                }

                var unsupported = readUnsupportedDialogReason();
                if (unsupported) {
                  WuppCallBridge.onDialResult(false, 'WhatsApp Web calling unavailable: ' + unsupported);
                  return;
                }

                if (phase === 'WAIT_SEARCH_BOX') {
                  var box = findSearchBox();
                  if (box) {
                    runSearchAttempt(box);
                    setTimeout(tick, Math.max(d1, 850));
                    return;
                  }
                  if (phaseTicks >= 16) {
                    WuppCallBridge.onDialResult(false, 'WhatsApp Web session not ready yet');
                    return;
                  }
                  setTimeout(tick, 250);
                  return;
                }

                if (phase === 'WAIT_ROW') {
                  var curBox = findSearchBox();
                  var boxText = normLower(readBoxText(curBox));
                  var boxHasQuery = !!matchedQueryPrefix && boxText.indexOf(matchedQueryPrefix) !== -1;
                  var rows = getValidChatRows();
                  lastRowCount = rows.length;
                  var settled = boxHasQuery && (phaseTicks >= 2 || rows.length <= 5);
                  var row = settled ? pickBestChatRow(rows, boxHasQuery, initialRowCount) : null;
                  if (row) {
                    openChatRow(row, true);
                    rowClicked = true;
                    phase = 'WAIT_HEADER_AND_BTN';
                    phaseTicks = 0;
                    setTimeout(tick, Math.max(d2, 650));
                    return;
                  }
                  if (phaseTicks >= 10) {
                    if (queryIdx + 1 < uniqueQueries.length) {
                      queryIdx++;
                      if (curBox) {
                        runSearchAttempt(curBox);
                        setTimeout(tick, 750);
                        return;
                      }
                    }
                    WuppCallBridge.onDialResult(false, 'Contact not found in WhatsApp Web (' + buildHeaderDiag(lastRowCount) + ')');
                    return;
                  }
                  setTimeout(tick, 300);
                  return;
                }

                if (phase === 'WAIT_HEADER_AND_BTN') {
                  var hdr = findMainHeader();
                  if (!hdr || !isVisible(hdr)) {
                    if (phaseTicks % 3 === 1 && rowClicked) {
                      var curBox2 = findSearchBox();
                      var boxOk2 = !!matchedQueryPrefix && normLower(readBoxText(curBox2)).indexOf(matchedQueryPrefix) !== -1;
                      var rows2 = getValidChatRows();
                      var row2 = pickBestChatRow(rows2, boxOk2, initialRowCount);
                      if (row2) {
                        openChatRow(row2, true);
                      }
                    }
                    if (phaseTicks >= 18) {
                      WuppCallBridge.onDialResult(false, 'Call button unavailable in WhatsApp Web (' + buildHeaderDiag(lastRowCount) + ')');
                      return;
                    }
                    setTimeout(tick, 300);
                    return;
                  }

                  var found = findHeaderCallButton();
                  if (found && found.el && !found.disabled) {
                    clickedCallInfo = found;
                    if (!nativeTapEl(found.el)) {
                      clickSingle(found.el, false);
                    }
                    phase = 'WAIT_POST_CLICK';
                    phaseTicks = 0;
                    setTimeout(tick, 340);
                    return;
                  }

                  if (found && found.disabled && phaseTicks >= 6) {
                    WuppCallBridge.onDialResult(false, 'WhatsApp Web disabled calling in Android WebView (' + buildHeaderDiag(lastRowCount) + ')');
                    return;
                  }

                  if (phaseTicks >= 16) {
                    WuppCallBridge.onDialResult(false, 'Call button unavailable in WhatsApp Web (' + buildHeaderDiag(lastRowCount) + ')');
                    return;
                  }
                  setTimeout(tick, 280);
                  return;
                }

                if (phase === 'WAIT_POST_CLICK') {
                  if (!submenuClicked) {
                    var subOpt = findCallSubmenuOrConfirmOption(clickedCallInfo ? clickedCallInfo.el : null);
                    if (subOpt) {
                      submenuClicked = true;
                      clickSingle(subOpt, true);
                      setTimeout(tick, 350);
                      return;
                    }
                  } else {
                    var confirmOpt = findCallSubmenuOrConfirmOption(clickedCallInfo ? clickedCallInfo.el : null);
                    if (confirmOpt && confirmOpt.closest('[role="dialog"]')) {
                      clickSingle(confirmOpt, true);
                      setTimeout(tick, 350);
                      return;
                    }
                  }

                  if (phaseTicks === 3 && !submenuClicked && callBtnRetries === 0 && clickedCallInfo && clickedCallInfo.el) {
                    callBtnRetries++;
                    clickSingle(clickedCallInfo.el, true);
                  }

                  if (phaseTicks >= 9) {
                    if (clickedCallInfo && clickedCallInfo.kind === 'unified' && !submenuClicked) {
                      WuppCallBridge.onDialResult(false, 'Call menu option unavailable in WhatsApp Web (' + buildHeaderDiag(lastRowCount) + ')');
                    } else {
                      WuppCallBridge.onDialResult(true, '');
                    }
                    return;
                  }
                  setTimeout(tick, 280);
                }
              }

              setTimeout(tick, 120);
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

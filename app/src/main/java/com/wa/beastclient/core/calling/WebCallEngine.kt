package com.wa.beastclient.core.calling

import android.Manifest
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
import androidx.core.content.ContextCompat
import org.json.JSONObject
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.StorageController
import org.mozilla.geckoview.WebExtension
import java.io.File
import java.net.URI
import java.util.Locale
import kotlin.random.Random

/**
 * Isolated Mozilla GeckoView engine powering `web.whatsapp.com` WebRTC calls with
 * native Cross-Origin Isolation (`COOP: same-origin` + `COEP: require-corp`),
 * multi-threaded `SharedArrayBuffer` / Emscripten PThreads support, and built-in
 * WebExtension native-port observation & call control.
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

    private var mainSession: GeckoSession? = null
    private var popoutSession: GeckoSession? = null
    private var mainView: WuppGeckoView? = null
    private var popoutView: WuppGeckoView? = null
    private var mainPort: WebExtension.Port? = null
    private var popoutPort: WebExtension.Port? = null
    private val pendingMainCommands = mutableListOf<JSONObject>()
    private val pendingPopoutCommands = mutableListOf<JSONObject>()

    private var hostContainer: ViewGroup? = null
    private var backgroundHostContainer: ViewGroup? = null
    private var allowTouchThrough = false
    private var videoOverlayCssActive = false
    private var webRingingMuted = false
    private var lastTeardownMs = 0L
    private var pendingWarmRunnable: Runnable? = null
    @Volatile
    private var pendingPairingPhoneDigits: String = ""

    val isAttachedAndLoaded: Boolean
        get() = mainSession != null

    /**
     * Ensures the GeckoSession is created and attached into [container] at full viewport size
     * so WhatsApp Web maintains desktop layout and active WebRTC pipelines.
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
            val session = ensureSessionCreatedLocked() ?: return@post

            attachSessionToContainer(
                session = session,
                isPopout = false,
                container = container,
                visible = visible
            )
            popoutSession?.let { popup ->
                attachSessionToContainer(
                    session = popup,
                    isPopout = true,
                    container = container,
                    visible = visible
                )
            }
            setVideoCssActive(showVideoSurface)
            if (showPairingSurface) {
                mainHandler.postDelayed({
                    sendCommand(isPopout = false, JSONObject().put("cmd", "scrollPairing"))
                }, 250L)
            }
        }
    }

    private fun attachSessionToContainer(
        session: GeckoSession,
        isPopout: Boolean,
        container: ViewGroup,
        visible: Boolean
    ) {
        val currentView = if (isPopout) popoutView else mainView
        if (currentView != null && currentView.parent !== container) {
            runCatching {
                if (currentView.session != null) {
                    currentView.releaseSession()
                }
            }
            (currentView.parent as? ViewGroup)?.removeView(currentView)
        }

        val targetView = if (currentView != null && currentView.parent === container) {
            currentView
        } else {
            val viewContext = container.context
            WuppGeckoView(viewContext).apply {
                setViewBackend(GeckoView.BACKEND_TEXTURE_VIEW)
                isClickable = true
                isFocusable = true
                isFocusableInTouchMode = true
            }.also { gv ->
                container.addView(
                    gv,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
                if (isPopout) {
                    popoutView = gv
                } else {
                    mainView = gv
                }
            }
        }

        if (targetView.session !== session) {
            if (targetView.session != null) {
                runCatching { targetView.releaseSession() }
            }
            targetView.setSession(session)
        }
        targetView.setBackgroundColor(if (visible) Color.BLACK else Color.TRANSPARENT)
        targetView.alpha = if (visible) 1f else 0.01f
        runCatching { session.setActive(true) }
        targetView.requestLayout()
        targetView.invalidate()
    }

    fun detachIfHostedBy(container: ViewGroup) {
        mainHandler.post {
            popoutView?.let { pv ->
                if (pv.parent === container) {
                    runCatching { if (pv.session != null) pv.releaseSession() }
                    container.removeView(pv)
                    popoutView = null
                }
            }
            if (hostContainer === container) {
                hostContainer = null
            }
            if (backgroundHostContainer === container) {
                backgroundHostContainer = null
            }
            val mv = mainView ?: return@post
            if (mv.parent === container) {
                runCatching { if (mv.session != null) mv.releaseSession() }
                container.removeView(mv)
                mainView = null
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
            if (respectCooldown && mainSession == null && lastTeardownMs > 0L && elapsed < MIN_RECONNECT_COOLDOWN_MS) {
                val delayMs = MIN_RECONNECT_COOLDOWN_MS - elapsed
                val task = Runnable { ensureSessionCreatedLocked() }
                pendingWarmRunnable = task
                mainHandler.postDelayed(task, delayMs)
                return@post
            }
            ensureSessionCreatedLocked()
        }
    }

    fun setHighPriorityRenderer(highPriority: Boolean) {
        mainHandler.post {
            val priority = if (highPriority) GeckoSession.PRIORITY_HIGH else GeckoSession.PRIORITY_DEFAULT
            runCatching { mainSession?.setPriorityHint(priority) }
            runCatching { popoutSession?.setPriorityHint(priority) }
            if (highPriority) {
                runCatching { mainSession?.setActive(true) }
                runCatching { popoutSession?.setActive(true) }
            }
        }
    }

    fun setVideoCssActive(active: Boolean) {
        mainHandler.post {
            videoOverlayCssActive = active
            val activeIsPopout = popoutSession != null
            val targetView = if (activeIsPopout) popoutView else mainView
            targetView?.alpha = if (active || allowTouchThrough) 1f else 0.01f
            sendCommand(
                isPopout = activeIsPopout,
                JSONObject().put("cmd", "videoCss").put("active", active)
            )
            if (activeIsPopout) {
                sendCommand(
                    isPopout = false,
                    JSONObject().put("cmd", "videoCss").put("active", false)
                )
            }
        }
    }

    fun setWebRingingMuted(muted: Boolean) {
        mainHandler.post {
            webRingingMuted = muted
            val payload = JSONObject().put("cmd", "ringMute").put("muted", muted)
            sendCommand(isPopout = false, payload)
            if (popoutSession != null) {
                sendCommand(isPopout = true, payload)
            }
        }
    }

    fun reloadCleanPreservingLogin() {
        mainHandler.post {
            destroyPopoutSession()
            val session = ensureSessionCreatedLocked() ?: return@post
            listener.onLinkStateChanged(WebLinkState.LOADING, "")
            sendCommand(isPopout = false, JSONObject().put("cmd", "forceReloadClean"))
            runCatching { session.reload(GeckoSession.LOAD_FLAGS_BYPASS_CACHE) }
        }
    }

    fun requestPhonePairingCode(phoneDigits: String) {
        val cleanDigits = phoneDigits.filter { it.isDigit() }
        pendingPairingPhoneDigits = cleanDigits
        mainHandler.post {
            ensureSessionCreatedLocked() ?: return@post
            setHighPriorityRenderer(true)
            listener.onPairingStatusChanged("Loading WhatsApp Web engine…")
            if (cleanDigits.isNotEmpty()) {
                sendCommand(
                    isPopout = false,
                    JSONObject().put("cmd", "pairing").put("phoneDigits", cleanDigits)
                )
            }
        }
    }

    fun dialContactInWeb(phoneDigits: String, fallbackName: String, isVideo: Boolean) {
        mainHandler.post {
            val session = ensureSessionCreatedLocked()
            if (session == null) {
                listener.onDialAttemptFinished(false, "Calling GeckoView session unavailable")
                return@post
            }
            setHighPriorityRenderer(true)
            syncPermissionsToExtension(isPopout = false)
            val stepDelay1 = Random.nextLong(380L, 640L)
            val stepDelay2 = Random.nextLong(420L, 740L)
            val cmd = JSONObject()
                .put("cmd", "dial")
                .put("digits", phoneDigits.filter { it.isDigit() })
                .put("contactName", fallbackName.trim())
                .put("isVideo", isVideo)
                .put("delay1", stepDelay1)
                .put("delay2", stepDelay2)
            sendCommand(isPopout = false, cmd)
        }
    }

    fun answerIncomingCall(audioOnly: Boolean = false) {
        mainHandler.post {
            setHighPriorityRenderer(true)
            syncPermissionsToExtension(isPopout = false)
            val activeIsPopout = popoutSession != null
            if (activeIsPopout) syncPermissionsToExtension(isPopout = true)
            sendCommand(
                isPopout = activeIsPopout,
                JSONObject().put("cmd", "answer").put("audioOnly", audioOnly)
            )
        }
    }

    fun declineOrEndCall() {
        mainHandler.post {
            val activeIsPopout = popoutSession != null
            sendCommand(isPopout = activeIsPopout, JSONObject().put("cmd", "end"))
            mainHandler.postDelayed({ destroyPopoutSession() }, 500L)
            returnToNeutralLandingAfterDelay()
        }
    }

    fun toggleMuteInWeb(targetMuted: Boolean) {
        mainHandler.post {
            sendCommand(
                isPopout = popoutSession != null,
                JSONObject().put("cmd", "mute").put("muted", targetMuted)
            )
        }
    }

    fun toggleCameraInWeb(cameraOff: Boolean) {
        mainHandler.post {
            sendCommand(
                isPopout = popoutSession != null,
                JSONObject().put("cmd", "camera").put("camOff", cameraOff)
            )
        }
    }

    fun flipCameraInWeb() {
        mainHandler.post {
            sendCommand(isPopout = popoutSession != null, JSONObject().put("cmd", "flip"))
        }
    }

    fun upgradeAudioCallToVideo() {
        mainHandler.post {
            sendCommand(isPopout = popoutSession != null, JSONObject().put("cmd", "upgradeVideo"))
        }
    }

    /**
     * Navigates back to the neutral landing pane with a jittered 450-850ms delay so no chat
     * thread stays open (preventing accidental read receipts from the browser companion).
     */
    fun returnToNeutralLandingAfterDelay() {
        val delayMs = Random.nextLong(450L, 850L)
        mainHandler.postDelayed({
            sendCommand(isPopout = false, JSONObject().put("cmd", "neutral"))
        }, delayMs)
    }

    private fun destroyPopoutSession() {
        val popup = popoutSession ?: return
        popoutSession = null
        popoutPort = null
        pendingPopoutCommands.clear()
        popoutView?.let { pv ->
            popoutView = null
            runCatching {
                if (pv.session != null) pv.releaseSession()
                (pv.parent as? ViewGroup)?.removeView(pv)
            }
        }
        runCatching {
            popup.close()
        }
    }

    fun teardownImmediate(clearSessionData: Boolean = false) {
        pendingPairingPhoneDigits = ""
        mainHandler.post {
            pendingWarmRunnable?.let { mainHandler.removeCallbacks(it) }
            pendingWarmRunnable = null
            destroyPopoutSession()
            val session = mainSession
            mainSession = null
            mainPort = null
            pendingMainCommands.clear()
            lastTeardownMs = System.currentTimeMillis()
            mainView?.let { mv ->
                mainView = null
                runCatching {
                    if (mv.session != null) mv.releaseSession()
                    (mv.parent as? ViewGroup)?.removeView(mv)
                }
            }
            if (session != null) {
                runCatching { session.close() }
            }
            if (clearSessionData) {
                runCatching {
                    sharedRuntime?.storageController?.clearDataFromBaseDomain(
                        "whatsapp.com",
                        StorageController.ClearFlags.ALL
                    )
                }
            }
            listener.onLinkStateChanged(WebLinkState.UNINITIALIZED, "")
        }
    }

    private fun ensureRuntimeAndExtension(): GeckoRuntime? {
        val existing = sharedRuntime
        val runtime = existing ?: synchronized(runtimeLock) {
            sharedRuntime ?: createRuntimeInternal().also { sharedRuntime = it }
        }
        if (runtime != null && installedExtension == null && !extensionInstallStarted) {
            extensionInstallStarted = true
            runCatching {
                runtime.webExtensionController
                    .ensureBuiltIn("resource://android/assets/wupp_bridge/", "wupp-call-bridge@wupp.app")
                    .accept(
                        { ext ->
                            mainHandler.post {
                                installedExtension = ext
                                if (ext != null) {
                                    mainSession?.let { bindExtensionToSession(ext, it, isPopout = false) }
                                    popoutSession?.let { bindExtensionToSession(ext, it, isPopout = true) }
                                }
                            }
                        },
                        { err ->
                            extensionInstallStarted = false
                            mainHandler.post {
                                listener.onEnvDiagnostics("eng=gecko ext_err=${err?.message ?: "unknown"}")
                            }
                        }
                    )
            }.onFailure {
                extensionInstallStarted = false
            }
        }
        return runtime
    }

    private fun createRuntimeInternal(): GeckoRuntime? {
        return runCatching {
            val desktopUa = buildDesktopFirefoxUa(GeckoSession.getDefaultUserAgent())
            val configFile = File(appContext.filesDir, "wupp_geckoview_config.yaml")
            val yaml = buildString {
                appendLine("prefs:")
                appendLine("  general.useragent.override: \"${desktopUa.replace("\"", "\\\"")}\"")
                appendLine("  general.platform.override: \"Win32\"")
                appendLine("  general.oscpu.override: \"Windows NT 10.0; Win64; x64\"")
                appendLine("  dom.w3c_touch_events.enabled: 0")
                appendLine("  media.autoplay.default: 0")
                appendLine("  media.autoplay.blocking_policy: 0")
                appendLine("  media.setsinkid.enabled: true")
            }
            configFile.writeText(yaml, Charsets.UTF_8)

            val settings = GeckoRuntimeSettings.Builder()
                .configFilePath(configFile.absolutePath)
                .javaScriptEnabled(true)
                .webFontsEnabled(true)
                .automaticFontSizeAdjustment(false)
                .inputAutoZoomEnabled(false)
                .doubleTapZoomingEnabled(false)
                .consoleOutput(false)
                .build()
            GeckoRuntime.create(appContext, settings)
        }.getOrNull()
    }

    private fun ensureSessionCreatedLocked(): GeckoSession? {
        mainSession?.let { existing ->
            hostContainer?.let { container ->
                val mv = mainView
                if (mv == null || mv.parent !== container) {
                    attachSessionToContainer(
                        session = existing,
                        isPopout = false,
                        container = container,
                        visible = allowTouchThrough || videoOverlayCssActive
                    )
                }
            }
            return existing
        }

        val runtime = ensureRuntimeAndExtension() ?: return null
        val session = createConfiguredSession(runtime = runtime, isPopout = false)
        mainSession = session
        hostContainer?.let { container ->
            attachSessionToContainer(
                session = session,
                isPopout = false,
                container = container,
                visible = allowTouchThrough || videoOverlayCssActive
            )
        }
        listener.onLinkStateChanged(WebLinkState.LOADING, "")
        session.loadUri(WA_WEB_URL)
        return session
    }

    private fun createConfiguredSession(runtime: GeckoRuntime, isPopout: Boolean): GeckoSession {
        val desktopUa = buildDesktopFirefoxUa(GeckoSession.getDefaultUserAgent())
        val settings = GeckoSessionSettings.Builder()
            .userAgentMode(GeckoSessionSettings.USER_AGENT_MODE_DESKTOP)
            .userAgentOverride(desktopUa)
            .viewportMode(GeckoSessionSettings.VIEWPORT_MODE_DESKTOP)
            .suspendMediaWhenInactive(false)
            .useTrackingProtection(false)
            .allowJavascript(true)
            .build()

        val session = GeckoSession(settings)
        configureSessionDelegates(session, runtime, isPopout)
        session.open(runtime)
        session.setPriorityHint(if (isPopout) GeckoSession.PRIORITY_HIGH else GeckoSession.PRIORITY_DEFAULT)
        session.setActive(true)
        installedExtension?.let { ext ->
            bindExtensionToSession(ext, session, isPopout)
        }
        return session
    }

    private fun bindExtensionToSession(ext: WebExtension, session: GeckoSession, isPopout: Boolean) {
        val msgDelegate = object : WebExtension.MessageDelegate {
            override fun onConnect(port: WebExtension.Port) {
                mainHandler.post {
                    if (isPopout) {
                        popoutPort = port
                    } else {
                        mainPort = port
                    }
                    port.setDelegate(createPortDelegate(isPopout))
                    syncPermissionsToExtension(isPopout)
                    flushPendingCommands(isPopout)
                }
            }
        }
        session.webExtensionController.setMessageDelegate(ext, msgDelegate, NATIVE_PORT_APP_ID)
    }

    private fun createPortDelegate(isPopout: Boolean): WebExtension.PortDelegate {
        return object : WebExtension.PortDelegate {
            override fun onPortMessage(message: Any, port: WebExtension.Port) {
                val json = message as? JSONObject ?: return
                handleExtensionMessage(json, isPopout)
            }

            override fun onDisconnect(port: WebExtension.Port) {
                mainHandler.post {
                    if (isPopout && popoutPort === port) {
                        popoutPort = null
                    } else if (!isPopout && mainPort === port) {
                        mainPort = null
                    }
                }
            }
        }
    }

    private fun handleExtensionMessage(json: JSONObject, isPopout: Boolean) {
        val type = json.optString("type")
        mainHandler.post {
            when (type) {
                "linkUpdate" -> {
                    val stateName = json.optString("state")
                    val code = json.optString("code")
                    val parsed = when (stateName) {
                        "NEEDS_PAIRING_CODE" -> WebLinkState.NEEDS_PAIRING_CODE
                        "PAIRING_CODE_READY" -> WebLinkState.PAIRING_CODE_READY
                        "AUTHENTICATED" -> WebLinkState.AUTHENTICATED
                        "ERROR" -> WebLinkState.ERROR
                        else -> WebLinkState.LOADING
                    }
                    if (parsed == WebLinkState.AUTHENTICATED) {
                        pendingPairingPhoneDigits = ""
                    }
                    listener.onLinkStateChanged(parsed, code)
                }
                "pairingStatus" -> {
                    val status = json.optString("status")
                    if (status.isNotBlank()) listener.onPairingStatusChanged(status)
                }
                "webCallEvent" -> {
                    listener.onWebCallEvent(
                        state = json.optString("state"),
                        callerHint = json.optString("callerHint"),
                        isVideo = json.optBoolean("isVideo"),
                        canAnswer = json.optBoolean("canAnswer"),
                        hasActiveVideo = json.optBoolean("hasActiveVideo")
                    )
                }
                "dialResult" -> {
                    listener.onDialAttemptFinished(
                        dispatched = json.optBoolean("dispatched"),
                        reason = json.optString("reason")
                    )
                }
                "envDiag" -> {
                    val diag = json.optString("diag")
                    if (diag.isNotBlank()) listener.onEnvDiagnostics(diag)
                }
                "nativeTap" -> {
                    val view = if (isPopout) popoutView else mainView
                    view?.dispatchSyntheticTap(
                        json.optDouble("xRatio", 0.5).toFloat(),
                        json.optDouble("yRatio", 0.5).toFloat()
                    )
                }
                "nativeKeyText" -> {
                    val view = if (isPopout) popoutView else mainView
                    val text = json.optString("text")
                    val sendEnter = json.optBoolean("sendEnter")
                    view?.dispatchSyntheticKeyText(text, sendEnter)
                }
            }
        }
    }

    private fun sendCommand(isPopout: Boolean, cmd: JSONObject) {
        val port = if (isPopout) popoutPort else mainPort
        if (port != null) {
            runCatching { port.postMessage(cmd) }.onFailure {
                queueCommand(isPopout, cmd)
            }
        } else {
            queueCommand(isPopout, cmd)
        }
    }

    private fun queueCommand(isPopout: Boolean, cmd: JSONObject) {
        val queue = if (isPopout) pendingPopoutCommands else pendingMainCommands
        if (queue.size < 16) {
            queue.add(cmd)
        }
    }

    private fun flushPendingCommands(isPopout: Boolean) {
        val port = (if (isPopout) popoutPort else mainPort) ?: return
        val queue = if (isPopout) pendingPopoutCommands else pendingMainCommands
        if (queue.isEmpty()) return
        val copy = queue.toList()
        queue.clear()
        copy.forEach { cmd ->
            runCatching { port.postMessage(cmd) }
        }
    }

    private fun syncPermissionsToExtension(isPopout: Boolean) {
        val hasMic = ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        val hasCam = ContextCompat.checkSelfPermission(appContext, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        sendCommand(
            isPopout = isPopout,
            JSONObject()
                .put("cmd", "syncPermissions")
                .put("mic", hasMic)
                .put("cam", hasCam)
        )
    }

    private fun configureSessionDelegates(session: GeckoSession, runtime: GeckoRuntime, isPopout: Boolean) {
        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLoadRequest(
                sess: GeckoSession,
                request: GeckoSession.NavigationDelegate.LoadRequest
            ): GeckoResult<AllowOrDeny> {
                val parsed = runCatching { URI(request.uri) }.getOrNull()
                val schemeOk = parsed?.scheme.equals("https", ignoreCase = true)
                val hostOk = parsed?.host?.lowercase(Locale.ROOT)?.trimEnd('.') == "web.whatsapp.com"
                return if (schemeOk && hostOk) GeckoResult.allow() else GeckoResult.deny()
            }

            override fun onNewSession(sess: GeckoSession, uri: String): GeckoResult<GeckoSession> {
                val originOk = isAllowedWhatsAppPermissionOrigin(uri) || uri == "about:blank" || uri.isBlank()
                if (!originOk) {
                    return GeckoResult.fromValue(null)
                }
                destroyPopoutSession()
                val popup = createConfiguredSession(runtime = runtime, isPopout = true)
                popoutSession = popup
                val targetContainer = hostContainer ?: backgroundHostContainer
                if (targetContainer != null) {
                    attachSessionToContainer(
                        session = popup,
                        isPopout = true,
                        container = targetContainer,
                        visible = allowTouchThrough || videoOverlayCssActive
                    )
                }
                return GeckoResult.fromValue(popup)
            }
        }

        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStop(sess: GeckoSession, success: Boolean) {
                if (!success) return
                syncPermissionsToExtension(isPopout)
                if (videoOverlayCssActive) {
                    sendCommand(isPopout, JSONObject().put("cmd", "videoCss").put("active", true))
                }
                if (webRingingMuted) {
                    sendCommand(isPopout, JSONObject().put("cmd", "ringMute").put("muted", true))
                }
                if (!isPopout) {
                    val digits = pendingPairingPhoneDigits
                    if (digits.isNotEmpty()) {
                        setHighPriorityRenderer(true)
                        sendCommand(
                            isPopout = false,
                            JSONObject().put("cmd", "pairing").put("phoneDigits", digits)
                        )
                    }
                }
            }
        }

        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onCloseRequest(sess: GeckoSession) {
                if (isPopout && popoutSession === sess) {
                    destroyPopoutSession()
                }
            }

            override fun onCrash(sess: GeckoSession) {
                if (isPopout) {
                    destroyPopoutSession()
                } else {
                    mainSession = null
                    mainPort = null
                }
            }

            override fun onKill(sess: GeckoSession) {
                if (isPopout) {
                    destroyPopoutSession()
                } else {
                    mainSession = null
                    mainPort = null
                }
            }
        }

        session.permissionDelegate = object : GeckoSession.PermissionDelegate {
            override fun onAndroidPermissionsRequest(
                sess: GeckoSession,
                permissions: Array<out String>?,
                callback: GeckoSession.PermissionDelegate.Callback
            ) {
                val perms = permissions.orEmpty()
                val allGranted = perms.all { p ->
                    ContextCompat.checkSelfPermission(appContext, p) == PackageManager.PERMISSION_GRANTED
                }
                if (allGranted) callback.grant() else callback.reject()
            }

            override fun onContentPermissionRequest(
                sess: GeckoSession,
                perm: GeckoSession.PermissionDelegate.ContentPermission
            ): GeckoResult<Int> {
                val originOk = isAllowedWhatsAppPermissionOrigin(perm.uri)
                val allow = originOk && when (perm.permission) {
                    GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_AUDIBLE,
                    GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE,
                    GeckoSession.PermissionDelegate.PERMISSION_PERSISTENT_STORAGE,
                    GeckoSession.PermissionDelegate.PERMISSION_MEDIA_KEY_SYSTEM_ACCESS -> true
                    else -> false
                }
                return GeckoResult.fromValue(
                    if (allow) {
                        GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW
                    } else {
                        GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY
                    }
                )
            }

            override fun onMediaPermissionRequest(
                sess: GeckoSession,
                uri: String,
                video: Array<out GeckoSession.PermissionDelegate.MediaSource>?,
                audio: Array<out GeckoSession.PermissionDelegate.MediaSource>?,
                callback: GeckoSession.PermissionDelegate.MediaCallback
            ) {
                if (!isAllowedWhatsAppPermissionOrigin(uri)) {
                    callback.reject()
                    return
                }
                val hasMic = ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
                val hasCam = ContextCompat.checkSelfPermission(appContext, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED

                val selectedAudio = if (hasMic && !audio.isNullOrEmpty()) {
                    audio.firstOrNull { it.source == GeckoSession.PermissionDelegate.MediaSource.SOURCE_MICROPHONE }
                        ?: audio.first()
                } else {
                    null
                }
                val selectedVideo = if (hasCam && !video.isNullOrEmpty()) {
                    video.firstOrNull {
                        it.source == GeckoSession.PermissionDelegate.MediaSource.SOURCE_CAMERA &&
                            it.name?.contains("front", ignoreCase = true) == true
                    } ?: video.firstOrNull {
                        it.source == GeckoSession.PermissionDelegate.MediaSource.SOURCE_CAMERA
                    } ?: video.first()
                } else {
                    null
                }

                if (selectedAudio == null && selectedVideo == null) {
                    callback.reject()
                    return
                }
                callback.grant(selectedVideo, selectedAudio)
                listener.onWebRtcMediaCapture(selectedVideo != null)
            }
        }
    }

    private inner class WuppGeckoView(context: Context) : GeckoView(context) {
        @Volatile
        private var allowSyntheticTouch: Boolean = false

        override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
            return if (allowTouchThrough || allowSyntheticTouch) super.dispatchTouchEvent(ev) else false
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
            allowSyntheticTouch = true
            runCatching { super.dispatchTouchEvent(down) }
            allowSyntheticTouch = false
            down.recycle()

            mainHandler.postDelayed({
                val upTime = SystemClock.uptimeMillis()
                val up = MotionEvent.obtain(downTime, upTime, MotionEvent.ACTION_UP, px, py, 0).apply {
                    source = InputDevice.SOURCE_TOUCHSCREEN
                }
                allowSyntheticTouch = true
                runCatching { super.dispatchTouchEvent(up) }
                allowSyntheticTouch = false
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

    companion object {
        private const val WA_WEB_URL = "https://web.whatsapp.com/"
        private const val NATIVE_PORT_APP_ID = "wuppBridge"
        const val MIN_RECONNECT_COOLDOWN_MS = 45_000L

        private val runtimeLock = Any()
        @Volatile
        private var sharedRuntime: GeckoRuntime? = null
        @Volatile
        private var installedExtension: WebExtension? = null
        @Volatile
        private var extensionInstallStarted: Boolean = false
    }
}

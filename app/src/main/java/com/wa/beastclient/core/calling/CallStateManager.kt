package com.wa.beastclient.core.calling

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import com.wa.beastclient.core.service.WaRealtimeService
import com.wa.beastclient.domain.stealth.CallBackendMode
import com.wa.beastclient.domain.stealth.GlobalStealthConfig
import com.wa.beastclient.domain.stealth.StealthMatrixManager
import com.wa.beastclient.ui.calling.CallActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Deterministic single source of truth for Voice & Video call state,
 * lifecycle throttling (OFF / SMART_WARM / ALWAYS_READY), and Go/WebView coordination.
 */
object CallStateManager {
    private const val SMART_WARM_IDLE_DESTROY_MS = 10 * 60 * 1000L
    private const val DIAL_TIMEOUT_MS = 15_000L
    private const val TERMINATION_GRACE_MS = 320L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _uiState = MutableStateFlow(CallUiState())
    val uiState: StateFlow<CallUiState> = _uiState.asStateFlow()

    private val _linkState = MutableStateFlow(WebLinkState.UNINITIALIZED)
    val linkState: StateFlow<WebLinkState> = _linkState.asStateFlow()

    private val _pairingCode = MutableStateFlow("")
    val pairingCode: StateFlow<String> = _pairingCode.asStateFlow()

    private val _pairingStatus = MutableStateFlow("")
    val pairingStatus: StateFlow<String> = _pairingStatus.asStateFlow()

    @Volatile
    private var pairingInProgress: Boolean = false
    @Volatile
    private var appContext: Context? = null
    @Volatile
    private var engine: WebCallEngine? = null
    @Volatile
    private var audioController: CallAudioController? = null

    private var smartWarmDestroyJob: Job? = null
    private var dialTimeoutJob: Job? = null
    private var durationTickerJob: Job? = null
    private var pendingDialRunnable: Job? = null
    private var appInForeground: Boolean = false
    private var lastWebCallState: String = "IDLE"

    fun init(context: Context) {
        val ctx = context.applicationContext
        if (appContext != null) return
        synchronized(this) {
            if (appContext != null) return
            appContext = ctx
            audioController = CallAudioController(
                context = ctx,
                onRoutesChanged = { available, active ->
                    _uiState.update { it.copy(availableRoutes = available, audioRoute = active) }
                },
                onVoicePlaybackStarted = {
                    onVoiceAudioPlaybackConfirmed()
                }
            )
            engine = WebCallEngine(
                context = ctx,
                listener = object : WebCallEngine.Listener {
                    override fun onLinkStateChanged(state: WebLinkState, pairingCode: String) {
                        _linkState.value = state
                        if (pairingCode.isNotBlank()) {
                            _pairingCode.value = pairingCode
                            _pairingStatus.value = ""
                        } else if (state == WebLinkState.AUTHENTICATED) {
                            pairingInProgress = false
                            _pairingCode.value = ""
                            _pairingStatus.value = ""
                            if (!StealthMatrixManager.config.value.callingLinked) {
                                StealthMatrixManager.updateConfig { cfg ->
                                    val nextMode = if (cfg.callBackendMode == CallBackendMode.OFF) {
                                        CallBackendMode.ALWAYS_READY
                                    } else {
                                        cfg.callBackendMode
                                    }
                                    cfg.copy(callingLinked = true, callBackendMode = nextMode)
                                }
                            }
                        } else if (state == WebLinkState.ERROR) {
                            pairingInProgress = false
                        }
                    }

                    override fun onPairingStatusChanged(status: String) {
                        if (status.isNotBlank()) {
                            _pairingStatus.value = status
                        }
                    }

                    override fun onWebCallEvent(
                        state: String,
                        callerHint: String,
                        isVideo: Boolean,
                        canAnswer: Boolean,
                        hasActiveVideo: Boolean
                    ) {
                        handleWebCallStateChange(state, callerHint, isVideo, canAnswer, hasActiveVideo)
                    }

                    override fun onWebRtcMediaCapture(hasVideo: Boolean) {
                        if (_uiState.value.phase == CallPhase.DIALING || _uiState.value.phase == CallPhase.INCOMING_RINGING) {
                            _uiState.update {
                                it.copy(
                                    phase = CallPhase.CONNECTING,
                                    isVideo = it.isVideo || hasVideo,
                                    statusText = "Connecting…"
                                )
                            }
                        }
                    }

                    override fun onDialAttemptFinished(dispatched: Boolean, reason: String) {
                        if (!dispatched && _uiState.value.phase == CallPhase.DIALING) {
                            failCall(reason.ifBlank { "Couldn't place call in WhatsApp Web" })
                        }
                    }
                }
            )
        }
    }

    fun attachWebViewHost(container: ViewGroup, showVideoSurface: Boolean) {
        init(container.context)
        engine?.attachToContainer(container, showVideoSurface)
    }

    fun detachWebViewHost(container: ViewGroup) {
        engine?.detachIfHostedBy(container)
    }

    fun onConfigChanged(config: GlobalStealthConfig) {
        if (config.offlineMode || config.callBackendMode == CallBackendMode.OFF) {
            pairingInProgress = false
            _pairingStatus.value = ""
            if (_uiState.value.phase == CallPhase.IDLE) {
                smartWarmDestroyJob?.cancel()
                engine?.teardownImmediate(clearSessionData = false)
            }
            return
        }
        if (!config.callingLinked) {
            if (pairingInProgress) {
                smartWarmDestroyJob?.cancel()
                engine?.ensureWarm(respectCooldown = false)
            } else if (_uiState.value.phase == CallPhase.IDLE) {
                smartWarmDestroyJob?.cancel()
                engine?.teardownImmediate(clearSessionData = false)
            }
            return
        }
        when (config.callBackendMode) {
            CallBackendMode.ALWAYS_READY -> {
                smartWarmDestroyJob?.cancel()
                engine?.ensureWarm(respectCooldown = true)
            }
            CallBackendMode.SMART_WARM -> {
                if (appInForeground) {
                    smartWarmDestroyJob?.cancel()
                    engine?.ensureWarm(respectCooldown = true)
                }
            }
            CallBackendMode.OFF -> Unit
        }
    }

    fun onAppForegroundChanged(inForeground: Boolean) {
        appInForeground = inForeground
        val cfg = StealthMatrixManager.config.value
        if (cfg.offlineMode || !cfg.callingLinked || cfg.callBackendMode == CallBackendMode.OFF) return

        if (inForeground) {
            smartWarmDestroyJob?.cancel()
            engine?.ensureWarm(respectCooldown = true)
        } else if (cfg.callBackendMode == CallBackendMode.SMART_WARM && _uiState.value.phase == CallPhase.IDLE) {
            smartWarmDestroyJob?.cancel()
            smartWarmDestroyJob = scope.launch {
                delay(SMART_WARM_IDLE_DESTROY_MS)
                if (!appInForeground &&
                    _uiState.value.phase == CallPhase.IDLE &&
                    StealthMatrixManager.config.value.callBackendMode == CallBackendMode.SMART_WARM
                ) {
                    engine?.teardownImmediate(clearSessionData = false)
                }
            }
        }
    }

    fun requestPhonePairingCode(phoneDigits: String) {
        appContext?.let { init(it) }
        pairingInProgress = true
        _pairingCode.value = ""
        _pairingStatus.value = "Loading WhatsApp Web engine…"
        _linkState.value = WebLinkState.LOADING
        engine?.requestPhonePairingCode(phoneDigits)
    }

    fun unlinkCallingEngine() {
        smartWarmDestroyJob?.cancel()
        pairingInProgress = false
        _pairingCode.value = ""
        _pairingStatus.value = ""
        StealthMatrixManager.updateConfig {
            it.copy(callingLinked = false, callBackendMode = CallBackendMode.OFF)
        }
        engine?.teardownImmediate(clearSessionData = true)
    }

    fun startOutgoingCall(
        context: Context,
        peerJid: String,
        peerName: String,
        peerAvatarPath: String?,
        phoneDigits: String,
        isVideo: Boolean,
        isGroup: Boolean
    ) {
        val ctx = context.applicationContext
        init(ctx)
        if (StealthMatrixManager.config.value.offlineMode) return
        if (_uiState.value.phase != CallPhase.IDLE) {
            launchCallActivity(ctx)
            return
        }

        smartWarmDestroyJob?.cancel()
        engine?.setHighPriorityRenderer(true)
        engine?.setWebRingingMuted(true)
        audioController?.startInCallAudio(isVideo)

        _uiState.value = CallUiState(
            phase = CallPhase.DIALING,
            peerJid = peerJid,
            peerFromJid = peerJid,
            peerPhoneDigits = phoneDigits,
            peerName = peerName.ifBlank { peerJid.substringBefore("@") },
            peerAvatarPath = peerAvatarPath,
            direction = CallDirection.OUTGOING,
            isVideo = isVideo,
            isGroup = isGroup,
            canAnswerInApp = false,
            statusText = "Calling…"
        )

        CallForegroundService.sync(ctx, _uiState.value)
        launchCallActivity(ctx)

        pendingDialRunnable?.cancel()
        pendingDialRunnable = scope.launch {
            engine?.ensureWarm(respectCooldown = false)
            var waited = 0L
            while (waited < 8000L && _linkState.value != WebLinkState.AUTHENTICATED) {
                delay(250L)
                waited += 250L
            }
            if (_uiState.value.phase != CallPhase.DIALING) return@launch
            if (_linkState.value != WebLinkState.AUTHENTICATED) {
                failCall("WhatsApp Web session not linked or still loading")
                return@launch
            }
            engine?.dialContactInWeb(phoneDigits, peerName, isVideo)
        }

        dialTimeoutJob?.cancel()
        dialTimeoutJob = scope.launch {
            delay(DIAL_TIMEOUT_MS)
            if (_uiState.value.phase == CallPhase.DIALING) {
                failCall("Couldn't connect call")
            }
        }
    }

    fun onGoIncomingCall(
        callId: String,
        fromJid: String,
        callerJid: String,
        callerAltJid: String,
        peerName: String,
        peerAvatarPath: String?,
        isVideo: Boolean,
        isGroup: Boolean,
        isNotice: Boolean
    ) {
        val ctx = appContext ?: return
        val cfg = StealthMatrixManager.config.value
        if (cfg.offlineMode || !cfg.callingLinked || cfg.callBackendMode == CallBackendMode.OFF) {
            return
        }
        val cur = _uiState.value
        if (cur.phase != CallPhase.IDLE) return

        val dialableDigits = extractDialablePhoneDigits(callerJid, callerAltJid)
        val canAnswer = engine?.isAttachedAndLoaded == true && _linkState.value == WebLinkState.AUTHENTICATED
        if (!canAnswer && isNotice) {
            engine?.ensureWarm(respectCooldown = false)
        }

        smartWarmDestroyJob?.cancel()
        engine?.setHighPriorityRenderer(true)
        engine?.setWebRingingMuted(true)
        audioController?.refreshRoutes(isVideo)
        audioController?.startRinging()

        _uiState.value = CallUiState(
            phase = CallPhase.INCOMING_RINGING,
            callId = callId,
            peerJid = callerJid.ifBlank { fromJid },
            peerFromJid = fromJid.ifBlank { callerJid },
            peerPhoneDigits = dialableDigits,
            peerName = peerName.ifBlank { dialableDigits.ifBlank { callerJid.substringBefore("@") } },
            peerAvatarPath = peerAvatarPath,
            direction = CallDirection.INCOMING,
            isVideo = isVideo,
            isGroup = isGroup,
            canAnswerInApp = canAnswer,
            statusText = if (isVideo) "Incoming video call…" else "Incoming voice call…"
        )

        CallForegroundService.sync(ctx, _uiState.value)
        launchCallActivity(ctx)
    }

    fun onGoCallAcceptedElsewhere(callId: String) {
        val cur = _uiState.value
        if (cur.phase == CallPhase.INCOMING_RINGING && shouldDismissCallFromGoSignal(cur, callId)) {
            mainHandler.postDelayed({
                if (_uiState.value.phase == CallPhase.INCOMING_RINGING && lastWebCallState != "ACTIVE") {
                    finishCallInternal()
                }
            }, TERMINATION_GRACE_MS)
        }
    }

    fun onGoCallTerminated(callId: String, reason: String) {
        val cur = _uiState.value
        if (!shouldDismissCallFromGoSignal(cur, callId)) return
        if (cur.phase == CallPhase.INCOMING_RINGING || cur.phase == CallPhase.DIALING) {
            mainHandler.postDelayed({
                if (lastWebCallState == "IDLE" || _uiState.value.phase == CallPhase.INCOMING_RINGING) {
                    finishCallInternal()
                }
            }, TERMINATION_GRACE_MS)
        }
    }

    fun answerIncomingCall(audioOnly: Boolean = false) {
        val cur = _uiState.value
        if (cur.phase != CallPhase.INCOMING_RINGING) return
        val useVideo = cur.isVideo && !audioOnly
        if (!cur.canAnswerInApp && engine?.isAttachedAndLoaded != true) {
            val ctx = appContext ?: return
            openOfficialWhatsApp(ctx, cur.peerPhoneDigits)
            finishCallInternal()
            return
        }
        audioController?.startInCallAudio(useVideo)
        engine?.setHighPriorityRenderer(true)
        engine?.answerIncomingCall(audioOnly)
        _uiState.update {
            it.copy(
                phase = CallPhase.CONNECTING,
                isVideo = useVideo,
                statusText = "Connecting…"
            )
        }
        engine?.setVideoCssActive(useVideo)
        appContext?.let { CallForegroundService.sync(it, _uiState.value) }
    }

    fun declineOrEndCall() {
        val cur = _uiState.value
        if (cur.phase == CallPhase.IDLE) return

        if (cur.phase == CallPhase.INCOMING_RINGING && cur.callId.isNotBlank() && !cur.canAnswerInApp) {
            val targetFrom = cur.peerFromJid.ifBlank { cur.peerJid }
            WaRealtimeService.rejectCallViaGoBridge(targetFrom, cur.callId)
        } else {
            engine?.declineOrEndCall()
        }
        finishCallInternal()
    }

    fun toggleMute() {
        val next = !_uiState.value.isMuted
        _uiState.update { it.copy(isMuted = next) }
        engine?.toggleMuteInWeb(next)
    }

    fun toggleCamera() {
        val cur = _uiState.value
        if (!cur.isVideo) {
            upgradeToVideo()
            return
        }
        val nextOff = !cur.isCameraOff
        _uiState.update { it.copy(isCameraOff = nextOff) }
        engine?.toggleCameraInWeb(nextOff)
    }

    fun flipCamera() {
        engine?.flipCameraInWeb()
    }

    fun upgradeToVideo() {
        _uiState.update { it.copy(isVideo = true, isCameraOff = false) }
        audioController?.updateVideoState(true)
        engine?.upgradeAudioCallToVideo()
        if (_uiState.value.phase == CallPhase.ACTIVE) {
            engine?.setVideoCssActive(true)
        }
        appContext?.let { CallForegroundService.sync(it, _uiState.value) }
    }

    fun setAudioRoute(route: CallAudioRoute) {
        audioController?.setAudioRoute(route)
    }

    fun dismissFailedScreen() {
        finishCallInternal()
    }

    fun openOfficialWhatsAppFallback() {
        val ctx = appContext ?: return
        val digits = _uiState.value.peerPhoneDigits
        finishCallInternal()
        openOfficialWhatsApp(ctx, digits)
    }

    private fun handleWebCallStateChange(
        webState: String,
        callerHint: String,
        isVideo: Boolean,
        canAnswer: Boolean,
        hasActiveVideo: Boolean
    ) {
        lastWebCallState = webState
        val cur = _uiState.value
        when (webState) {
            "INCOMING_RINGING" -> {
                if (cur.phase == CallPhase.IDLE) {
                    val ctx = appContext ?: return
                    val cfg = StealthMatrixManager.config.value
                    if (cfg.offlineMode || !cfg.callingLinked || cfg.callBackendMode == CallBackendMode.OFF) {
                        return
                    }
                    audioController?.refreshRoutes(isVideo)
                    audioController?.startRinging()
                    engine?.setWebRingingMuted(true)
                    engine?.setHighPriorityRenderer(true)
                    _uiState.value = CallUiState(
                        phase = CallPhase.INCOMING_RINGING,
                        peerName = callerHint.ifBlank { "WhatsApp Call" },
                        direction = CallDirection.INCOMING,
                        isVideo = isVideo,
                        canAnswerInApp = canAnswer,
                        statusText = if (isVideo) "Incoming video call…" else "Incoming voice call…"
                    )
                    CallForegroundService.sync(ctx, _uiState.value)
                    launchCallActivity(ctx)
                } else if (cur.phase == CallPhase.INCOMING_RINGING) {
                    _uiState.update {
                        it.copy(
                            canAnswerInApp = canAnswer || it.canAnswerInApp,
                            peerName = it.peerName.ifBlank { callerHint }
                        )
                    }
                }
            }

            "CONNECTING" -> {
                dialTimeoutJob?.cancel()
                if (cur.phase == CallPhase.DIALING || cur.phase == CallPhase.INCOMING_RINGING) {
                    _uiState.update {
                        it.copy(
                            phase = CallPhase.CONNECTING,
                            isVideo = it.isVideo || isVideo || hasActiveVideo,
                            statusText = "Connecting…"
                        )
                    }
                    appContext?.let { CallForegroundService.sync(it, _uiState.value) }
                }
            }

            "ACTIVE" -> {
                dialTimeoutJob?.cancel()
                if (cur.phase == CallPhase.DIALING || cur.phase == CallPhase.CONNECTING) {
                    markCallActive(cur.isVideo || hasActiveVideo)
                } else if (cur.phase == CallPhase.ACTIVE && (hasActiveVideo && !cur.isVideo)) {
                    _uiState.update { it.copy(isVideo = true) }
                    engine?.setVideoCssActive(true)
                }
            }

            "IDLE" -> {
                if (cur.phase == CallPhase.CONNECTING || cur.phase == CallPhase.ACTIVE) {
                    finishCallInternal()
                }
            }
        }
    }

    private fun onVoiceAudioPlaybackConfirmed() {
        val cur = _uiState.value
        if (cur.phase == CallPhase.DIALING || cur.phase == CallPhase.CONNECTING) {
            markCallActive(cur.isVideo)
        }
    }

    private fun markCallActive(isVideo: Boolean) {
        dialTimeoutJob?.cancel()
        val now = System.currentTimeMillis()
        _uiState.update {
            it.copy(
                phase = CallPhase.ACTIVE,
                isVideo = isVideo,
                connectedAtEpochMs = if (it.connectedAtEpochMs > 0L) it.connectedAtEpochMs else now,
                statusText = formatCallTimer(0)
            )
        }
        engine?.setVideoCssActive(isVideo)
        audioController?.updateVideoState(isVideo)
        startDurationTicker()
        appContext?.let { CallForegroundService.sync(it, _uiState.value) }
    }

    private fun startDurationTicker() {
        durationTickerJob?.cancel()
        durationTickerJob = scope.launch {
            while (_uiState.value.phase == CallPhase.ACTIVE) {
                val start = _uiState.value.connectedAtEpochMs
                val sec = if (start > 0L) ((System.currentTimeMillis() - start) / 1000L).toInt().coerceAtLeast(0) else 0
                _uiState.update {
                    it.copy(durationSec = sec, statusText = formatCallTimer(sec))
                }
                delay(1000L)
            }
        }
    }

    private fun failCall(reason: String) {
        pendingDialRunnable?.cancel()
        dialTimeoutJob?.cancel()
        durationTickerJob?.cancel()
        audioController?.stopAll()
        engine?.setVideoCssActive(false)
        engine?.setHighPriorityRenderer(false)
        engine?.returnToNeutralLandingAfterDelay()
        _uiState.update {
            it.copy(
                phase = CallPhase.FAILED,
                statusText = "Call failed",
                errorReason = reason
            )
        }
        appContext?.let { CallForegroundService.stop(it) }
    }

    private fun finishCallInternal() {
        pendingDialRunnable?.cancel()
        dialTimeoutJob?.cancel()
        durationTickerJob?.cancel()
        lastWebCallState = "IDLE"
        audioController?.stopAll()
        engine?.setVideoCssActive(false)
        engine?.setWebRingingMuted(false)
        engine?.setHighPriorityRenderer(false)
        engine?.returnToNeutralLandingAfterDelay()
        _uiState.value = CallUiState(phase = CallPhase.IDLE)
        appContext?.let { ctx ->
            CallForegroundService.stop(ctx)
            onConfigChanged(StealthMatrixManager.config.value)
        }
    }

    fun launchCallActivity(context: Context) {
        val intent = Intent(context, CallActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            )
        }
        runCatching { context.startActivity(intent) }
    }

    fun openOfficialWhatsApp(context: Context, phoneDigits: String = "") {
        val clean = phoneDigits.filter { it.isDigit() }
        val waIntent = if (clean.isNotBlank()) {
            Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$clean"))
        } else {
            context.packageManager.getLaunchIntentForPackage("com.whatsapp")
                ?: context.packageManager.getLaunchIntentForPackage("com.whatsapp.w4b")
                ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/"))
        }
        waIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(waIntent) }
    }
}

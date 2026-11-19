package com.wa.beastclient.core.calling

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import java.util.concurrent.Executor

/**
 * Coordinates Android telecom audio focus, communication-mode hardware AEC,
 * earpiece/speaker/Bluetooth routing, proximity screen-off wake locks, and native ringtones.
 */
class CallAudioController(
    context: Context,
    private val onRoutesChanged: (available: Set<CallAudioRoute>, active: CallAudioRoute) -> Unit,
    private val onVoicePlaybackStarted: () -> Unit
) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val mainExecutor = Executor { runnable -> mainHandler.post(runnable) }

    private var focusRequest: AudioFocusRequest? = null
    private var playbackCallback: AudioManager.AudioPlaybackCallback? = null
    private var commDeviceListener: Any? = null
    private var ringtone: Ringtone? = null
    private var proximityWakeLock: PowerManager.WakeLock? = null

    private var activeRoute: CallAudioRoute = CallAudioRoute.EARPIECE
    private var inCallAudioActive: Boolean = false
    private var currentIsVideo: Boolean = false
    private var ringingActive: Boolean = false

    fun refreshRoutes(isVideoCall: Boolean = currentIsVideo): Pair<Set<CallAudioRoute>, CallAudioRoute> {
        val available = queryAvailableRoutes()
        val chosen = if (activeRoute in available) activeRoute else chooseDefaultRoute(available, isVideoCall)
        activeRoute = chosen
        onRoutesChanged(available, chosen)
        return available to chosen
    }

    fun startRinging() {
        mainHandler.post {
            if (ringingActive) return@post
            ringingActive = true
            val ringerMode = audioManager.ringerMode
            if (ringerMode == AudioManager.RINGER_MODE_NORMAL) {
                runCatching {
                    val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                    val rt = RingtoneManager.getRingtone(appContext, uri)
                    rt?.audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        rt?.isLooping = true
                    }
                    rt?.play()
                    ringtone = rt
                }
            }
            if (ringerMode != AudioManager.RINGER_MODE_SILENT) {
                startRingVibration()
            }
        }
    }

    fun stopRinging() {
        mainHandler.post {
            ringingActive = false
            runCatching { ringtone?.stop() }
            ringtone = null
            stopRingVibration()
        }
    }

    fun startInCallAudio(isVideo: Boolean) {
        mainHandler.post {
            stopRinging()
            currentIsVideo = isVideo
            inCallAudioActive = true
            requestVoiceAudioFocus()
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            registerPlaybackWatcher()
            registerCommDeviceWatcher()

            val available = queryAvailableRoutes()
            val route = chooseDefaultRoute(available, isVideo)
            applyAudioRouteInternal(route, available)
            updateProximityLock()
        }
    }

    fun updateVideoState(isVideo: Boolean) {
        mainHandler.post {
            currentIsVideo = isVideo
            if (inCallAudioActive) {
                val available = queryAvailableRoutes()
                if (isVideo && activeRoute == CallAudioRoute.EARPIECE && CallAudioRoute.SPEAKER in available) {
                    applyAudioRouteInternal(CallAudioRoute.SPEAKER, available)
                }
                updateProximityLock()
            }
        }
    }

    fun setAudioRoute(route: CallAudioRoute) {
        mainHandler.post {
            val available = queryAvailableRoutes()
            val target = if (route in available) route else chooseDefaultRoute(available, currentIsVideo)
            applyAudioRouteInternal(target, available)
            updateProximityLock()
        }
    }

    fun stopAll() {
        mainHandler.post {
            stopRinging()
            inCallAudioActive = false
            currentIsVideo = false
            releaseProximityLock()
            unregisterPlaybackWatcher()
            unregisterCommDeviceWatcher()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                runCatching { audioManager.clearCommunicationDevice() }
            } else {
                @Suppress("DEPRECATION")
                runCatching {
                    audioManager.stopBluetoothSco()
                    audioManager.isBluetoothScoOn = false
                    audioManager.isSpeakerphoneOn = false
                }
            }
            runCatching { audioManager.mode = AudioManager.MODE_NORMAL }
            abandonVoiceAudioFocus()
            activeRoute = CallAudioRoute.EARPIECE
            onRoutesChanged(setOf(CallAudioRoute.EARPIECE, CallAudioRoute.SPEAKER), CallAudioRoute.EARPIECE)
        }
    }

    private fun queryAvailableRoutes(): Set<CallAudioRoute> {
        val routes = linkedSetOf(CallAudioRoute.EARPIECE, CallAudioRoute.SPEAKER)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val devices = runCatching { audioManager.availableCommunicationDevices }.getOrDefault(emptyList())
            for (d in devices) {
                when (d.type) {
                    AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                    AudioDeviceInfo.TYPE_BLE_HEADSET -> routes.add(CallAudioRoute.BLUETOOTH)
                    AudioDeviceInfo.TYPE_WIRED_HEADSET,
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                    AudioDeviceInfo.TYPE_USB_HEADSET -> routes.add(CallAudioRoute.WIRED_HEADSET)
                }
            }
        } else {
            val outputs = runCatching { audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS) }.getOrNull().orEmpty()
            for (d in outputs) {
                when (d.type) {
                    AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> routes.add(CallAudioRoute.BLUETOOTH)
                    AudioDeviceInfo.TYPE_WIRED_HEADSET,
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                    AudioDeviceInfo.TYPE_USB_HEADSET -> routes.add(CallAudioRoute.WIRED_HEADSET)
                }
            }
        }
        return routes
    }

    private fun chooseDefaultRoute(available: Set<CallAudioRoute>, isVideo: Boolean): CallAudioRoute = when {
        CallAudioRoute.BLUETOOTH in available -> CallAudioRoute.BLUETOOTH
        CallAudioRoute.WIRED_HEADSET in available -> CallAudioRoute.WIRED_HEADSET
        isVideo && CallAudioRoute.SPEAKER in available -> CallAudioRoute.SPEAKER
        else -> CallAudioRoute.EARPIECE
    }

    private fun applyAudioRouteInternal(route: CallAudioRoute, available: Set<CallAudioRoute>) {
        activeRoute = route
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val commDevices = runCatching { audioManager.availableCommunicationDevices }.getOrDefault(emptyList())
            val matching = commDevices.firstOrNull { dev ->
                when (route) {
                    CallAudioRoute.BLUETOOTH -> dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || dev.type == AudioDeviceInfo.TYPE_BLE_HEADSET
                    CallAudioRoute.WIRED_HEADSET -> dev.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                        dev.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                        dev.type == AudioDeviceInfo.TYPE_USB_HEADSET
                    CallAudioRoute.SPEAKER -> dev.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                    CallAudioRoute.EARPIECE -> dev.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
                }
            }
            if (matching != null) {
                runCatching { audioManager.setCommunicationDevice(matching) }
            } else if (route == CallAudioRoute.EARPIECE) {
                runCatching { audioManager.clearCommunicationDevice() }
            }
        } else {
            @Suppress("DEPRECATION")
            when (route) {
                CallAudioRoute.BLUETOOTH -> {
                    audioManager.isSpeakerphoneOn = false
                    runCatching {
                        audioManager.startBluetoothSco()
                        audioManager.isBluetoothScoOn = true
                    }
                }
                CallAudioRoute.WIRED_HEADSET, CallAudioRoute.EARPIECE -> {
                    runCatching {
                        audioManager.stopBluetoothSco()
                        audioManager.isBluetoothScoOn = false
                    }
                    audioManager.isSpeakerphoneOn = false
                }
                CallAudioRoute.SPEAKER -> {
                    runCatching {
                        audioManager.stopBluetoothSco()
                        audioManager.isBluetoothScoOn = false
                    }
                    audioManager.isSpeakerphoneOn = true
                }
            }
        }
        onRoutesChanged(available, route)
    }

    private fun updateProximityLock() {
        val shouldHold = inCallAudioActive && !currentIsVideo && activeRoute == CallAudioRoute.EARPIECE
        if (shouldHold) {
            acquireProximityLock()
        } else {
            releaseProximityLock()
        }
    }

    private fun acquireProximityLock() {
        val lock = proximityWakeLock ?: run {
            val pm = powerManager ?: return
            @Suppress("DEPRECATION")
            val created = if (pm.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
                pm.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "Wupp:CallProximityLock")
            } else null
            proximityWakeLock = created
            created
        } ?: return
        if (!lock.isHeld) {
            runCatching { lock.acquire(2 * 60 * 60 * 1000L) }
        }
    }

    private fun releaseProximityLock() {
        val lock = proximityWakeLock ?: return
        if (lock.isHeld) {
            runCatching {
                @Suppress("DEPRECATION")
                lock.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY)
            }
        }
    }

    private fun requestVoiceAudioFocus() {
        if (focusRequest != null) return
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(attrs)
            .setWillPauseWhenDucked(false)
            .build()
        focusRequest = req
        runCatching { audioManager.requestAudioFocus(req) }
    }

    private fun abandonVoiceAudioFocus() {
        val req = focusRequest ?: return
        focusRequest = null
        runCatching { audioManager.abandonAudioFocusRequest(req) }
    }

    private fun registerPlaybackWatcher() {
        if (playbackCallback != null) return
        val cb = object : AudioManager.AudioPlaybackCallback() {
            override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>) {
                val hasVoiceStream = configs.any { cfg ->
                    val usage = cfg.audioAttributes.usage
                    val content = cfg.audioAttributes.contentType
                    usage == AudioAttributes.USAGE_VOICE_COMMUNICATION ||
                        usage == AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING ||
                        content == AudioAttributes.CONTENT_TYPE_SPEECH
                }
                if (hasVoiceStream) {
                    onVoicePlaybackStarted()
                }
            }
        }
        playbackCallback = cb
        runCatching { audioManager.registerAudioPlaybackCallback(cb, mainHandler) }
    }

    private fun unregisterPlaybackWatcher() {
        val cb = playbackCallback ?: return
        playbackCallback = null
        runCatching { audioManager.unregisterAudioPlaybackCallback(cb) }
    }

    private fun registerCommDeviceWatcher() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || commDeviceListener != null) return
        val listener = AudioManager.OnCommunicationDeviceChangedListener {
            refreshRoutes(currentIsVideo)
            updateProximityLock()
        }
        commDeviceListener = listener
        runCatching { audioManager.addOnCommunicationDeviceChangedListener(mainExecutor, listener) }
    }

    private fun unregisterCommDeviceWatcher() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val listener = commDeviceListener as? AudioManager.OnCommunicationDeviceChangedListener ?: return
        commDeviceListener = null
        runCatching { audioManager.removeOnCommunicationDeviceChangedListener(listener) }
    }

    private fun activeVibrator(): Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private fun startRingVibration() {
        runCatching {
            val vib = activeVibrator() ?: return
            if (!vib.hasVibrator()) return
            val pattern = longArrayOf(0L, 800L, 1200L)
            vib.vibrate(VibrationEffect.createWaveform(pattern, 0))
        }
    }

    private fun stopRingVibration() {
        runCatching { activeVibrator()?.cancel() }
    }
}

package com.wa.beastclient.core.calling

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.display.DisplayManager
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Display
import android.view.Surface
import com.wa.bridge.whatsbridge.Whatsbridge
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Hardware MediaCodec H.264 Annex-B video encoder (Camera2 -> GL upright portrait relay ->
 * Rust VoIP) and decoder (Rust VoIP -> remote Surface) supporting front/back switching and PLI.
 */
class NativeVoipVideoPipeline(
    private val context: Context,
    private val callId: String,
    private val onRemoteOrientationDegrees: (Int) -> Unit = {},
    private val onRemoteVideoSizeChanged: (Int, Int) -> Unit = { _, _ -> },
    private val onRemoteFirstFrameDecoded: () -> Unit = {}
) {
    companion object {
        private const val TAG = "NativeVoipVideo"
        private const val MIME_AVC = MediaFormat.MIMETYPE_VIDEO_AVC
        private const val CAM_WIDTH = 640
        private const val CAM_HEIGHT = 480
        private const val ENC_WIDTH = 480
        private const val ENC_HEIGHT = 640
        private const val BITRATE = 800_000
        private const val FPS = 24
        private const val I_FRAME_INTERVAL_SEC = 2
    }

    private val running = AtomicBoolean(false)
    private val sendEnabled = AtomicBoolean(false)
    private val useFrontCamera = AtomicBoolean(true)

    private val localSurfaceRef = AtomicReference<Surface?>(null)
    private val remoteSurfaceRef = AtomicReference<Surface?>(null)

    private var cameraThread: HandlerThread? = null
    private var cameraHandler: Handler? = null
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null

    private var encoder: MediaCodec? = null
    private var encoderInputSurface: Surface? = null
    private var glRelay: CameraVideoGlRelay? = null
    private var glCameraSurface: Surface? = null
    private var encoderDrainThread: Thread? = null
    private var decoderThread: Thread? = null

    private var codecConfigAnnexB: ByteArray? = null
    private val cameraLock = Any()

    fun start(enableSendCamera: Boolean) {
        if (!running.compareAndSet(false, true)) return
        sendEnabled.set(enableSendCamera)

        decoderThread = Thread({ runDecoderLoop() }, "wupp-voip-vdec").apply {
            isDaemon = true
            start()
        }

        if (enableSendCamera) {
            startCameraAndEncoder()
        }
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        sendEnabled.set(false)
        stopCameraAndEncoder()
        decoderThread = null
    }

    fun setLocalPreviewSurface(surface: Surface?) {
        val prev = localSurfaceRef.getAndSet(surface)
        if (prev !== surface && running.get() && sendEnabled.get()) {
            restartCaptureSessionOnly()
        }
    }

    fun setRemoteSurface(surface: Surface?) {
        remoteSurfaceRef.set(surface)
        if (surface != null && running.get()) {
            Whatsbridge.requestPeerKeyframe(callId)
        }
    }

    fun setCameraEnabled(enabled: Boolean) {
        val was = sendEnabled.getAndSet(enabled)
        if (!running.get() || was == enabled) return
        if (enabled) {
            startCameraAndEncoder()
        } else {
            stopCameraAndEncoder()
        }
    }

    fun switchCamera() {
        useFrontCamera.set(!useFrontCamera.get())
        if (running.get() && sendEnabled.get()) {
            stopCameraAndEncoder()
            startCameraAndEncoder()
        }
    }

    fun requestSyncKeyframe() {
        val enc = encoder ?: return
        runCatching {
            val params = Bundle().apply {
                putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0)
            }
            enc.setParameters(params)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startCameraAndEncoder() {
        synchronized(cameraLock) {
            if (cameraDevice != null) return
            try {
                val format = MediaFormat.createVideoFormat(MIME_AVC, ENC_WIDTH, ENC_HEIGHT).apply {
                    setInteger(
                        MediaFormat.KEY_COLOR_FORMAT,
                        MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
                    )
                    setInteger(MediaFormat.KEY_BIT_RATE, BITRATE)
                    setInteger(MediaFormat.KEY_FRAME_RATE, FPS)
                    setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL_SEC)
                    setInteger(
                        MediaFormat.KEY_BITRATE_MODE,
                        MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR
                    )
                }
                val enc = MediaCodec.createEncoderByType(MIME_AVC)
                enc.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                val inputSurf = enc.createInputSurface()
                enc.start()
                encoder = enc
                encoderInputSurface = inputSurf
                codecConfigAnnexB = null

                val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                val isFront = useFrontCamera.get()
                val targetFacing = if (isFront) {
                    CameraCharacteristics.LENS_FACING_FRONT
                } else {
                    CameraCharacteristics.LENS_FACING_BACK
                }
                val camId = cm.cameraIdList.firstOrNull { id ->
                    cm.getCameraCharacteristics(id)
                        .get(CameraCharacteristics.LENS_FACING) == targetFacing
                } ?: cm.cameraIdList.firstOrNull() ?: return

                val sensorDeg = cm.getCameraCharacteristics(camId)
                    .get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
                val relay = CameraVideoGlRelay(
                    encWidth = ENC_WIDTH,
                    encHeight = ENC_HEIGHT,
                    camWidth = CAM_WIDTH,
                    camHeight = CAM_HEIGHT,
                    encoderInputSurface = inputSurf,
                    rotationDegreesProvider = {
                        val displayDeg = currentDisplayRotationDegrees()
                        if (isFront) {
                            (sensorDeg + displayDeg) % 360
                        } else {
                            (sensorDeg - displayDeg + 360) % 360
                        }
                    }
                )
                val relaySurface = relay.startAndGetCameraSurface()
                if (relaySurface == null) {
                    relay.stop()
                    stopCameraAndEncoder()
                    return
                }
                glRelay = relay
                glCameraSurface = relaySurface

                // Frames are already rotated to upright portrait before H.264 encoding
                runCatching {
                    Whatsbridge.setCallVideoOrientation(callId, 0)
                }

                encoderDrainThread = Thread({ runEncoderDrainLoop(enc) }, "wupp-voip-venc").apply {
                    isDaemon = true
                    start()
                }

                val thread = HandlerThread("wupp-voip-cam").apply { start() }
                val handler = Handler(thread.looper)
                cameraThread = thread
                cameraHandler = handler

                cm.openCamera(camId, object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        synchronized(cameraLock) {
                            if (!running.get() || !sendEnabled.get()) {
                                camera.close()
                                return
                            }
                            cameraDevice = camera
                            createCaptureSessionLocked(camera)
                        }
                    }

                    override fun onDisconnected(camera: CameraDevice) {
                        runCatching { camera.close() }
                    }

                    override fun onError(camera: CameraDevice, error: Int) {
                        Log.w(TAG, "CameraDevice error=$error")
                        runCatching { camera.close() }
                    }
                }, handler)
            } catch (t: Throwable) {
                Log.e(TAG, "Failed starting Camera2/H264 encoder: ${t.message}", t)
                stopCameraAndEncoder()
            }
        }
    }

    private fun restartCaptureSessionOnly() {
        synchronized(cameraLock) {
            val cam = cameraDevice ?: return
            runCatching { captureSession?.close() }
            captureSession = null
            createCaptureSessionLocked(cam)
        }
    }

    private fun currentDisplayRotationDegrees(): Int {
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        val rot = dm?.getDisplay(Display.DEFAULT_DISPLAY)?.rotation ?: Surface.ROTATION_0
        return when (rot) {
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
    }

    @Suppress("DEPRECATION")
    private fun createCaptureSessionLocked(camera: CameraDevice) {
        val encSurf = glCameraSurface ?: encoderInputSurface ?: return
        val targets = buildList {
            add(encSurf)
            localSurfaceRef.get()?.takeIf { it.isValid }?.let { add(it) }
        }
        val handler = cameraHandler
        runCatching {
            camera.createCaptureSession(
                targets,
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        synchronized(cameraLock) {
                            if (!running.get() || !sendEnabled.get()) {
                                runCatching { session.close() }
                                return
                            }
                            captureSession = session
                            val req = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                                targets.forEach { addTarget(it) }
                                set(
                                    CaptureRequest.CONTROL_MODE,
                                    CaptureRequest.CONTROL_MODE_AUTO
                                )
                            }
                            session.setRepeatingRequest(req.build(), null, handler)
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Log.w(TAG, "Camera capture session configure failed")
                    }
                },
                handler
            )
        }.onFailure {
            Log.w(TAG, "createCaptureSession failed: ${it.message}")
        }
    }

    private fun stopCameraAndEncoder() {
        synchronized(cameraLock) {
            runCatching { captureSession?.close() }
            captureSession = null
            runCatching { cameraDevice?.close() }
            cameraDevice = null
            runCatching { cameraThread?.quitSafely() }
            cameraThread = null
            cameraHandler = null
            runCatching { glRelay?.stop() }
            glRelay = null
            glCameraSurface = null
            runCatching { encoder?.signalEndOfInputStream() }
            runCatching { encoder?.stop() }
            runCatching { encoder?.release() }
            encoder = null
            runCatching { encoderInputSurface?.release() }
            encoderInputSurface = null
            codecConfigAnnexB = null
            encoderDrainThread = null
        }
    }

    private fun runEncoderDrainLoop(enc: MediaCodec) {
        val info = MediaCodec.BufferInfo()
        val startNs = System.nanoTime()
        while (running.get() && sendEnabled.get()) {
            val outIdx = runCatching { enc.dequeueOutputBuffer(info, 25_000) }.getOrElse { break }
            if (outIdx < 0) continue
            try {
                val buf = enc.getOutputBuffer(outIdx) ?: continue
                if (info.size > 0) {
                    val data = ByteArray(info.size)
                    buf.position(info.offset)
                    buf.limit(info.offset + info.size)
                    buf.get(data)

                    if ((info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        codecConfigAnnexB = data
                    } else {
                        val isKey = (info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0
                        val au = if (isKey && codecConfigAnnexB != null) {
                            codecConfigAnnexB!! + data
                        } else {
                            data
                        }
                        val elapsedNs = (System.nanoTime() - startNs).coerceAtLeast(0L)
                        val ts90k = ((elapsedNs * 90L) / 1_000_000L) and 0xFFFF_FFFFL
                        Whatsbridge.pushVoipVideoAu(callId, au, ts90k)
                    }
                }
            } finally {
                runCatching { enc.releaseOutputBuffer(outIdx, false) }
            }
        }
    }

    private fun runDecoderLoop() {
        var decoder: MediaCodec? = null
        var configuredSurface: Surface? = null
        var cachedSpsPps: ByteArray? = null
        var lastOrientation = -1
        var firstFrameReported = false
        val info = MediaCodec.BufferInfo()

        try {
            while (running.get()) {
                val packet = Whatsbridge.pullVoipVideoFrame(callId) ?: continue
                if (packet.size <= 8) continue

                val flags = packet[0].toInt() and 0xFF
                val isKey = (flags and 1) != 0
                val orientation = packet[1].toInt() and 0x03
                if (orientation != lastOrientation) {
                    lastOrientation = orientation
                    onRemoteOrientationDegrees(whatsappOrientationToClockwiseDegrees(orientation))
                }

                val ts90k = ByteBuffer.wrap(packet, 4, 4)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .int.toLong() and 0xFFFF_FFFFL
                val payload = packet.copyOfRange(8, packet.size)

                extractParameterSetsAnnexB(payload)?.let { cachedSpsPps = it }

                val targetSurface = remoteSurfaceRef.get()
                if (targetSurface == null || !targetSurface.isValid) {
                    if (decoder != null) {
                        runCatching { decoder.stop() }
                        runCatching { decoder.release() }
                        decoder = null
                        configuredSurface = null
                    }
                    continue
                }

                if (decoder == null || configuredSurface !== targetSurface) {
                    if (decoder != null) {
                        runCatching { decoder.stop() }
                        runCatching { decoder.release() }
                        decoder = null
                    }
                    val initCfg = cachedSpsPps
                    if (initCfg == null && !isKey) {
                        Whatsbridge.requestPeerKeyframe(callId)
                        continue
                    }
                    val fmt = MediaFormat.createVideoFormat(MIME_AVC, CAM_WIDTH, CAM_HEIGHT)
                    if (initCfg != null) {
                        fmt.setByteBuffer("csd-0", ByteBuffer.wrap(initCfg))
                    }
                    decoder = runCatching {
                        MediaCodec.createDecoderByType(MIME_AVC).apply {
                            configure(fmt, targetSurface, null, 0)
                            start()
                        }
                    }.getOrNull()
                    if (decoder != null) {
                        configuredSurface = targetSurface
                    } else {
                        continue
                    }
                }

                val dec = decoder
                val inIdx = runCatching { dec.dequeueInputBuffer(15_000) }.getOrElse { -1 }
                if (inIdx >= 0) {
                    val inBuf = dec.getInputBuffer(inIdx)
                    if (inBuf != null) {
                        inBuf.clear()
                        inBuf.put(payload, 0, payload.size.coerceAtMost(inBuf.remaining()))
                        val ptsUs = (ts90k * 1_000L) / 90L
                        val decFlags = if (isKey) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                        dec.queueInputBuffer(inIdx, 0, payload.size, ptsUs, decFlags)
                    }
                }

                while (true) {
                    val outIdx = runCatching { dec.dequeueOutputBuffer(info, 0) }.getOrElse { break }
                    if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        runCatching {
                            val outFmt = dec.outputFormat
                            val w = if (outFmt.containsKey("crop-right") && outFmt.containsKey("crop-left")) {
                                outFmt.getInteger("crop-right") - outFmt.getInteger("crop-left") + 1
                            } else if (outFmt.containsKey(MediaFormat.KEY_WIDTH)) {
                                outFmt.getInteger(MediaFormat.KEY_WIDTH)
                            } else {
                                0
                            }
                            val h = if (outFmt.containsKey("crop-bottom") && outFmt.containsKey("crop-top")) {
                                outFmt.getInteger("crop-bottom") - outFmt.getInteger("crop-top") + 1
                            } else if (outFmt.containsKey(MediaFormat.KEY_HEIGHT)) {
                                outFmt.getInteger(MediaFormat.KEY_HEIGHT)
                            } else {
                                0
                            }
                            if (w > 0 && h > 0) {
                                onRemoteVideoSizeChanged(w, h)
                            }
                        }
                    } else if (outIdx >= 0) {
                        dec.releaseOutputBuffer(outIdx, true)
                        if (!firstFrameReported) {
                            firstFrameReported = true
                            onRemoteFirstFrameDecoded()
                        }
                    } else {
                        break
                    }
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Video decoder loop error: ${t.message}", t)
        } finally {
            runCatching { decoder?.stop() }
            runCatching { decoder?.release() }
        }
    }

    /**
     * Scans Annex-B byte stream and extracts concatenated SPS (type 7) + PPS (type 8) NAL units
     * with 4-byte start codes whenever present in an IDR access unit.
     */
    private fun extractParameterSetsAnnexB(data: ByteArray): ByteArray? {
        val nals = ArrayList<ByteArray>(2)
        var i = 0
        val len = data.size
        while (i + 4 < len) {
            val startCodeLen = when {
                data[i] == 0.toByte() && data[i + 1] == 0.toByte() && data[i + 2] == 0.toByte() && data[i + 3] == 1.toByte() -> 4
                data[i] == 0.toByte() && data[i + 1] == 0.toByte() && data[i + 2] == 1.toByte() -> 3
                else -> {
                    i++
                    continue
                }
            }
            val nalStart = i + startCodeLen
            var next = nalStart
            while (next + 3 < len) {
                if (data[next] == 0.toByte() && data[next + 1] == 0.toByte() &&
                    (data[next + 2] == 1.toByte() || (next + 4 < len && data[next + 2] == 0.toByte() && data[next + 3] == 1.toByte()))
                ) {
                    break
                }
                next++
            }
            if (next + 3 >= len) next = len
            if (nalStart < next) {
                val nalType = data[nalStart].toInt() and 0x1F
                if (nalType == 7 || nalType == 8) {
                    nals.add(byteArrayOf(0, 0, 0, 1) + data.copyOfRange(nalStart, next))
                }
            }
            i = next
        }
        if (nals.isEmpty()) return null
        val total = nals.sumOf { it.size }
        val merged = ByteArray(total)
        var pos = 0
        for (nal in nals) {
            nal.copyInto(merged, pos)
            pos += nal.size
        }
        return merged
    }
}

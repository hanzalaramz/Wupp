package com.wa.beastclient.core.calling

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

internal data class RelayTransformParams(
    val cwRotationDeg: Int,
    val effectiveSourceWidth: Int,
    val effectiveSourceHeight: Int
)

/**
 * Computes OpenGL quad scales for center-crop (aspect-fill) output so the camera texture
 * fills the encoder viewport edge-to-edge without baking black letterbox or pillarbox bars.
 */
internal fun computeRelayCenterCropScales(srcAspect: Float, encAspect: Float): Pair<Float, Float> {
    if (srcAspect <= 0f || encAspect <= 0f) return 1f to 1f
    if (kotlin.math.abs(srcAspect - encAspect) <= 0.01f) return 1f to 1f
    return if (srcAspect > encAspect) {
        (srcAspect / encAspect) to 1f
    } else {
        1f to (encAspect / srcAspect)
    }
}

/**
 * Determines the remaining clockwise rotation and effective source dimensions for [CameraVideoGlRelay].
 *
 * On most Android Camera2 devices, [SurfaceTexture.getTransformMatrix] already includes the camera
 * sensor's 90/270-degree orientation transform (meaning the off-diagonal terms `stMatrix[1]` and
 * `stMatrix[4]` are non-zero while `stMatrix[0]` and `stMatrix[5]` are ~0). When that happens, we
 * must NOT apply [sensorDeg] a second time — only compensate for [displayDeg] and swap the
 * effective source dimensions to match the already-rotated texture.
 */
internal fun resolveCameraRelayTransform(
    camWidth: Int,
    camHeight: Int,
    sensorDeg: Int,
    displayDeg: Int,
    isFrontCamera: Boolean,
    stMatrix: FloatArray
): RelayTransformParams {
    val normalizedDisplay = ((displayDeg % 360) + 360) % 360
    val stHasQuarterTurn = stMatrix.size >= 6 &&
        kotlin.math.abs(stMatrix[0]) < 0.5f &&
        kotlin.math.abs(stMatrix[5]) < 0.5f &&
        kotlin.math.abs(stMatrix[1]) > 0.5f &&
        kotlin.math.abs(stMatrix[4]) > 0.5f

    return if (stHasQuarterTurn) {
        RelayTransformParams(
            cwRotationDeg = (360 - normalizedDisplay) % 360,
            effectiveSourceWidth = camHeight,
            effectiveSourceHeight = camWidth
        )
    } else {
        val cwRot = if (isFrontCamera) {
            (sensorDeg + normalizedDisplay) % 360
        } else {
            (sensorDeg - normalizedDisplay + 360) % 360
        }
        RelayTransformParams(
            cwRotationDeg = cwRot,
            effectiveSourceWidth = camWidth,
            effectiveSourceHeight = camHeight
        )
    }
}

/**
 * Offscreen OpenGL ES 2.0 relay that converts Camera2 external texture frames into upright
 * portrait frames for [MediaCodec.createInputSurface], applying sensor + display rotation
 * without stretching or double-rotating when [SurfaceTexture] already carries sensor transform.
 */
internal class CameraVideoGlRelay(
    private val encWidth: Int,
    private val encHeight: Int,
    private val camWidth: Int,
    private val camHeight: Int,
    private val sensorDeg: Int,
    private val isFrontCamera: Boolean,
    private val encoderInputSurface: Surface,
    private val displayRotationProvider: () -> Int
) {
    companion object {
        private const val TAG = "CameraVideoGlRelay"
        private const val EGL_RECORDABLE_ANDROID = 0x3142

        private const val VERTEX_SHADER = """
            attribute vec4 aPosition;
            attribute vec4 aTexCoord;
            uniform mat4 uMvpMatrix;
            uniform mat4 uTexMatrix;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = uMvpMatrix * aPosition;
                vTexCoord = (uTexMatrix * aTexCoord).xy;
            }
        """

        private const val FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vTexCoord;
            uniform samplerExternalOES sTexture;
            void main() {
                gl_FragColor = texture2D(sTexture, vTexCoord);
            }
        """

        private val QUAD_COORDS = floatArrayOf(
            // x,    y,    u,   v
            -1f, -1f, 0f, 0f,
            1f, -1f, 1f, 0f,
            -1f, 1f, 0f, 1f,
            1f, 1f, 1f, 1f
        )
    }

    private val glThread = HandlerThread("wupp-voip-gl").apply { start() }
    private val glHandler = Handler(glThread.looper)
    private val released = AtomicBoolean(false)

    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var program = 0
    private var oesTextureId = 0
    private var surfaceTexture: SurfaceTexture? = null
    private var inputSurface: Surface? = null

    private var aPositionLoc = -1
    private var aTexCoordLoc = -1
    private var uMvpMatrixLoc = -1
    private var uTexMatrixLoc = -1

    private val vertexBuffer: FloatBuffer = ByteBuffer
        .allocateDirect(QUAD_COORDS.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(QUAD_COORDS)
            position(0)
        }

    private val stMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    @Volatile
    private var initError: Throwable? = null

    fun startAndGetCameraSurface(): Surface? {
        val latch = CountDownLatch(1)
        glHandler.post {
            try {
                initEglAndGl()
            } catch (t: Throwable) {
                initError = t
                Log.e(TAG, "EGL init failed: ${t.message}", t)
                releaseGlLocked()
            } finally {
                latch.countDown()
            }
        }
        latch.await(2, TimeUnit.SECONDS)
        if (initError != null) return null
        return inputSurface
    }

    fun stop() {
        if (!released.compareAndSet(false, true)) return
        val latch = CountDownLatch(1)
        glHandler.post {
            releaseGlLocked()
            latch.countDown()
        }
        latch.await(1500, TimeUnit.MILLISECONDS)
        runCatching { glThread.quitSafely() }
    }

    private fun initEglAndGl() {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(eglDisplay != EGL14.EGL_NO_DISPLAY) { "eglGetDisplay failed" }

        val version = IntArray(2)
        check(EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) { "eglInitialize failed" }

        val attribList = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL_RECORDABLE_ANDROID, 1,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        check(EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, 1, numConfigs, 0) && numConfigs[0] > 0) {
            "eglChooseConfig failed"
        }

        val ctxAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
        eglContext = EGL14.eglCreateContext(eglDisplay, configs[0], EGL14.EGL_NO_CONTEXT, ctxAttribs, 0)
        check(eglContext != EGL14.EGL_NO_CONTEXT) { "eglCreateContext failed" }

        val surfAttribs = intArrayOf(EGL14.EGL_NONE)
        eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, configs[0], encoderInputSurface, surfAttribs, 0)
        check(eglSurface != EGL14.EGL_NO_SURFACE) { "eglCreateWindowSurface failed" }

        check(EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            "eglMakeCurrent failed"
        }

        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        aPositionLoc = GLES20.glGetAttribLocation(program, "aPosition")
        aTexCoordLoc = GLES20.glGetAttribLocation(program, "aTexCoord")
        uMvpMatrixLoc = GLES20.glGetUniformLocation(program, "uMvpMatrix")
        uTexMatrixLoc = GLES20.glGetUniformLocation(program, "uTexMatrix")

        val texIds = IntArray(1)
        GLES20.glGenTextures(1, texIds, 0)
        oesTextureId = texIds[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        surfaceTexture = SurfaceTexture(oesTextureId).apply {
            setDefaultBufferSize(camWidth, camHeight)
            setOnFrameAvailableListener({ st ->
                if (!released.get()) {
                    drawFrame(st)
                }
            }, glHandler)
        }
        inputSurface = Surface(surfaceTexture)
    }

    private fun drawFrame(st: SurfaceTexture) {
        if (released.get() || eglDisplay == EGL14.EGL_NO_DISPLAY) return
        runCatching {
            st.updateTexImage()
            st.getTransformMatrix(stMatrix)

            val transform = resolveCameraRelayTransform(
                camWidth = camWidth,
                camHeight = camHeight,
                sensorDeg = sensorDeg,
                displayDeg = displayRotationProvider(),
                isFrontCamera = isFrontCamera,
                stMatrix = stMatrix
            )
            val cwRot = transform.cwRotationDeg
            val rotatedW = if (cwRot == 90 || cwRot == 270) {
                transform.effectiveSourceHeight.toFloat()
            } else {
                transform.effectiveSourceWidth.toFloat()
            }
            val rotatedH = if (cwRot == 90 || cwRot == 270) {
                transform.effectiveSourceWidth.toFloat()
            } else {
                transform.effectiveSourceHeight.toFloat()
            }

            Matrix.setIdentityM(mvpMatrix, 0)
            // Center-crop fill so the encoded frame never ships black letterbox/pillarbox bars to peer
            val srcAspect = rotatedW / rotatedH.coerceAtLeast(1f)
            val encAspect = encWidth.toFloat() / encHeight.toFloat().coerceAtLeast(1f)
            val (cropScaleX, cropScaleY) = computeRelayCenterCropScales(srcAspect, encAspect)
            if (cropScaleX != 1f || cropScaleY != 1f) {
                Matrix.scaleM(mvpMatrix, 0, cropScaleX, cropScaleY, 1f)
            }
            if (cwRot != 0) {
                Matrix.rotateM(mvpMatrix, 0, -cwRot.toFloat(), 0f, 0f, 1f)
            }

            GLES20.glViewport(0, 0, encWidth, encHeight)
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

            GLES20.glUseProgram(program)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId)

            vertexBuffer.position(0)
            GLES20.glEnableVertexAttribArray(aPositionLoc)
            GLES20.glVertexAttribPointer(aPositionLoc, 2, GLES20.GL_FLOAT, false, 16, vertexBuffer)

            vertexBuffer.position(2)
            GLES20.glEnableVertexAttribArray(aTexCoordLoc)
            GLES20.glVertexAttribPointer(aTexCoordLoc, 2, GLES20.GL_FLOAT, false, 16, vertexBuffer)

            GLES20.glUniformMatrix4fv(uMvpMatrixLoc, 1, false, mvpMatrix, 0)
            GLES20.glUniformMatrix4fv(uTexMatrixLoc, 1, false, stMatrix, 0)

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

            EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, st.timestamp)
            EGL14.eglSwapBuffers(eglDisplay, eglSurface)
        }
    }

    private fun releaseGlLocked() {
        runCatching { surfaceTexture?.setOnFrameAvailableListener(null) }
        runCatching { inputSurface?.release() }
        inputSurface = null
        runCatching { surfaceTexture?.release() }
        surfaceTexture = null

        if (oesTextureId != 0) {
            runCatching { GLES20.glDeleteTextures(1, intArrayOf(oesTextureId), 0) }
            oesTextureId = 0
        }
        if (program != 0) {
            runCatching { GLES20.glDeleteProgram(program) }
            program = 0
        }
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            runCatching {
                EGL14.eglMakeCurrent(
                    eglDisplay,
                    EGL14.EGL_NO_SURFACE,
                    EGL14.EGL_NO_SURFACE,
                    EGL14.EGL_NO_CONTEXT
                )
            }
            if (eglSurface != EGL14.EGL_NO_SURFACE) {
                runCatching { EGL14.eglDestroySurface(eglDisplay, eglSurface) }
                eglSurface = EGL14.EGL_NO_SURFACE
            }
            if (eglContext != EGL14.EGL_NO_CONTEXT) {
                runCatching { EGL14.eglDestroyContext(eglDisplay, eglContext) }
                eglContext = EGL14.EGL_NO_CONTEXT
            }
            runCatching { EGL14.eglTerminate(eglDisplay) }
            eglDisplay = EGL14.EGL_NO_DISPLAY
        }
    }

    private fun createProgram(vertexSrc: String, fragmentSrc: String): Int {
        val vs = compileShader(GLES20.GL_VERTEX_SHADER, vertexSrc)
        val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSrc)
        val prog = GLES20.glCreateProgram()
        GLES20.glAttachShader(prog, vs)
        GLES20.glAttachShader(prog, fs)
        GLES20.glLinkProgram(prog)
        val status = IntArray(1)
        GLES20.glGetProgramiv(prog, GLES20.GL_LINK_STATUS, status, 0)
        GLES20.glDeleteShader(vs)
        GLES20.glDeleteShader(fs)
        check(status[0] == GLES20.GL_TRUE) {
            "glLinkProgram failed: ${GLES20.glGetProgramInfoLog(prog)}"
        }
        return prog
    }

    private fun compileShader(type: Int, src: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, src)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        check(status[0] == GLES20.GL_TRUE) {
            "glCompileShader failed: ${GLES20.glGetShaderInfoLog(shader)}"
        }
        return shader
    }
}

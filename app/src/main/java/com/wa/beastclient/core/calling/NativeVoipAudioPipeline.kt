package com.wa.beastclient.core.calling

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Process
import android.util.Log
import com.wa.bridge.whatsbridge.Whatsbridge
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Full-duplex 16 kHz mono PCM pipeline bridging Android AudioRecord/AudioTrack to
 * Rust whatsapp-rust VoIP (which internally encodes/decodes pure-Rust MLow frames).
 */
class NativeVoipAudioPipeline(
    private val callId: String
) {
    companion object {
        private const val TAG = "NativeVoipAudio"
        private const val SAMPLE_RATE = 16_000
        private const val FRAME_SAMPLES = 960 // 60 ms @ 16 kHz mono
    }

    private val running = AtomicBoolean(false)
    private val muted = AtomicBoolean(false)
    private var captureThread: Thread? = null
    private var playoutThread: Thread? = null

    fun setMuted(isMuted: Boolean) {
        muted.set(isMuted)
    }

    fun start() {
        if (!running.compareAndSet(false, true)) return
        captureThread = Thread({ runCaptureLoop() }, "wupp-voip-mic").apply {
            isDaemon = true
            start()
        }
        playoutThread = Thread({ runPlayoutLoop() }, "wupp-voip-spk").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        captureThread = null
        playoutThread = null
    }

    private fun runCaptureLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        val minBufBytes = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(FRAME_SAMPLES * 2)
        val recBufBytes = (minBufBytes * 2).coerceAtLeast(FRAME_SAMPLES * 2 * 4)

        var recorder: AudioRecord? = null
        var aec: AcousticEchoCanceler? = null
        var ns: NoiseSuppressor? = null
        try {
            recorder = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                recBufBytes
            )
            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                Log.w(TAG, "AudioRecord failed to initialize")
                return
            }

            val sessionId = recorder.audioSessionId
            if (AcousticEchoCanceler.isAvailable()) {
                aec = runCatching {
                    AcousticEchoCanceler.create(sessionId)?.apply { enabled = true }
                }.getOrNull()
            }
            if (NoiseSuppressor.isAvailable()) {
                ns = runCatching {
                    NoiseSuppressor.create(sessionId)?.apply { enabled = true }
                }.getOrNull()
            }

            recorder.startRecording()
            val frame = ShortArray(FRAME_SAMPLES)
            while (running.get()) {
                var offset = 0
                while (offset < FRAME_SAMPLES && running.get()) {
                    val read = recorder.read(
                        frame,
                        offset,
                        FRAME_SAMPLES - offset,
                        AudioRecord.READ_BLOCKING
                    )
                    if (read <= 0) {
                        break
                    }
                    offset += read
                }
                if (!running.get()) break
                if (offset == FRAME_SAMPLES) {
                    if (muted.get()) {
                        frame.fill(0)
                    }
                    val res = Whatsbridge.pushVoipAudioCapture(callId, frame)
                    if (res < 0) break
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Capture loop error: ${t.message}", t)
        } finally {
            runCatching { aec?.release() }
            runCatching { ns?.release() }
            runCatching { recorder?.stop() }
            runCatching { recorder?.release() }
        }
    }

    private fun runPlayoutLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        val minBufBytes = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(FRAME_SAMPLES * 2)
        val trackBufBytes = (minBufBytes * 2).coerceAtLeast(FRAME_SAMPLES * 2 * 4)

        var track: AudioTrack? = null
        try {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            val format = AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build()
            track = AudioTrack.Builder()
                .setAudioAttributes(attrs)
                .setAudioFormat(format)
                .setBufferSizeInBytes(trackBufBytes)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            track.play()
            val frame = ShortArray(FRAME_SAMPLES)
            while (running.get()) {
                val n = Whatsbridge.pullVoipAudioPlayout(callId, frame)
                if (n < 0) break
                if (n > 0) {
                    track.write(frame, 0, n, AudioTrack.WRITE_BLOCKING)
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Playout loop error: ${t.message}", t)
        } finally {
            runCatching { track?.stop() }
            runCatching { track?.release() }
        }
    }
}

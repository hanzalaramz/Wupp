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
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

internal fun mixCallPcmSample(localSample: Short, remoteSample: Short): Short =
    (localSample.toInt() + remoteSample.toInt())
        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
        .toShort()

/**
 * Thread-safe dual-stream 16 kHz mono WAV call recording writer that blends local mic frames
 * and remote playout frames cleanly on the device with valid RIFF/WAVE header finalization.
 */
internal class CallRecordingWriter(
    val outputFile: File,
    private val sampleRateHz: Int = 16_000
) {
    private val lock = Any()
    private val micSamples = ArrayDeque<Short>(4096)
    private val remoteSamples = ArrayDeque<Short>(4096)
    private var outStream: BufferedOutputStream? = null
    private var dataBytesWritten: Long = 0L
    private var closed = false

    init {
        outputFile.parentFile?.mkdirs()
        outStream = BufferedOutputStream(FileOutputStream(outputFile), 8192).apply {
            write(buildWavHeader(0L))
        }
    }

    fun appendLocalFrame(samples: ShortArray, count: Int = samples.size) {
        if (count <= 0) return
        synchronized(lock) {
            if (closed) return
            for (i in 0 until count.coerceAtMost(samples.size)) {
                micSamples.addLast(samples[i])
            }
            trimOverflowLocked(micSamples)
            drainMixLocked(flushAll = false)
        }
    }

    fun appendRemoteFrame(samples: ShortArray, count: Int = samples.size) {
        if (count <= 0) return
        synchronized(lock) {
            if (closed) return
            for (i in 0 until count.coerceAtMost(samples.size)) {
                remoteSamples.addLast(samples[i])
            }
            trimOverflowLocked(remoteSamples)
            drainMixLocked(flushAll = false)
        }
    }

    fun finish(): File? {
        synchronized(lock) {
            if (closed) return outputFile.takeIf { it.exists() && it.length() > 44L }
            closed = true
            drainMixLocked(flushAll = true)
            runCatching { outStream?.flush() }
            runCatching { outStream?.close() }
            outStream = null
            if (dataBytesWritten <= 0L) {
                runCatching { outputFile.delete() }
                return null
            }
            patchWavHeaderSizes(outputFile, dataBytesWritten)
            return outputFile.takeIf { it.exists() && it.length() > 44L }
        }
    }

    private fun trimOverflowLocked(queue: ArrayDeque<Short>) {
        // Cap drift backlog at 2 seconds so skewed producer/consumer rates stay bounded.
        val maxSamples = sampleRateHz * 2
        while (queue.size > maxSamples) {
            queue.removeFirst()
        }
    }

    private fun drainMixLocked(flushAll: Boolean) {
        val stream = outStream ?: return
        val maxSkewSamples = (sampleRateHz * 180) / 1000
        while (true) {
            val paired = minOf(micSamples.size, remoteSamples.size)
            val count = when {
                paired >= 320 -> paired
                flushAll && (micSamples.isNotEmpty() || remoteSamples.isNotEmpty()) ->
                    maxOf(micSamples.size, remoteSamples.size)
                micSamples.size > maxSkewSamples -> micSamples.size - maxSkewSamples
                remoteSamples.size > maxSkewSamples -> remoteSamples.size - maxSkewSamples
                else -> 0
            }
            if (count <= 0) break

            val bytes = ByteArray(count * 2)
            for (i in 0 until count) {
                val local = if (micSamples.isNotEmpty()) micSamples.removeFirst() else 0
                val remote = if (remoteSamples.isNotEmpty()) remoteSamples.removeFirst() else 0
                val mixed = mixCallPcmSample(local, remote).toInt()
                bytes[i * 2] = (mixed and 0xFF).toByte()
                bytes[i * 2 + 1] = ((mixed ushr 8) and 0xFF).toByte()
            }
            stream.write(bytes)
            dataBytesWritten += bytes.size
        }
    }

    private fun buildWavHeader(dataLen: Long): ByteArray {
        val safeLen = dataLen.coerceAtMost(Int.MAX_VALUE.toLong() - 36L).toInt()
        return ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray())
            putInt(36 + safeLen)
            put("WAVE".toByteArray())
            put("fmt ".toByteArray())
            putInt(16)
            putShort(1)
            putShort(1)
            putInt(sampleRateHz)
            putInt(sampleRateHz * 2)
            putShort(2)
            putShort(16)
            put("data".toByteArray())
            putInt(safeLen)
        }.array()
    }

    private fun patchWavHeaderSizes(file: File, dataLen: Long) {
        runCatching {
            val safeLen = dataLen.coerceAtMost(Int.MAX_VALUE.toLong() - 36L).toInt()
            val le4 = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
            RandomAccessFile(file, "rw").use { raf ->
                le4.clear()
                le4.putInt(36 + safeLen)
                raf.seek(4L)
                raf.write(le4.array())
                le4.clear()
                le4.putInt(safeLen)
                raf.seek(40L)
                raf.write(le4.array())
            }
        }
    }
}

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
    @Volatile
    private var recordingWriter: CallRecordingWriter? = null

    fun setMuted(isMuted: Boolean) {
        muted.set(isMuted)
    }

    fun isRecording(): Boolean = recordingWriter != null

    fun startRecording(outputFile: File): Boolean {
        if (!running.get()) return false
        if (recordingWriter != null) return true
        return runCatching {
            recordingWriter = CallRecordingWriter(outputFile, SAMPLE_RATE)
            true
        }.getOrDefault(false)
    }

    fun stopRecording(): File? {
        val writer = recordingWriter ?: return null
        recordingWriter = null
        return writer.finish()
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
        stopRecording()
        captureThread = null
        playoutThread = null
    }

    private fun openAudioRecordWithRetry(recBufBytes: Int): AudioRecord? {
        val sources = intArrayOf(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.MIC
        )
        repeat(8) { attempt ->
            if (!running.get()) return null
            val source = sources[if (attempt < 5) 0 else 1]
            val candidate = runCatching {
                AudioRecord(
                    source,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    recBufBytes
                )
            }.getOrNull()
            if (candidate != null && candidate.state == AudioRecord.STATE_INITIALIZED) {
                return candidate
            }
            runCatching { candidate?.release() }
            Log.w(TAG, "AudioRecord init attempt ${attempt + 1} failed (source=$source), retrying…")
            runCatching { Thread.sleep(120L) }
        }
        return null
    }

    private fun openAudioTrackWithRetry(trackBufBytes: Int): AudioTrack? {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val format = AudioFormat.Builder()
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .build()
        repeat(8) { attempt ->
            if (!running.get()) return null
            val candidate = runCatching {
                AudioTrack.Builder()
                    .setAudioAttributes(attrs)
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(trackBufBytes)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
            }.getOrNull()
            if (candidate != null && candidate.state == AudioTrack.STATE_INITIALIZED) {
                return candidate
            }
            runCatching { candidate?.release() }
            Log.w(TAG, "AudioTrack init attempt ${attempt + 1} failed, retrying…")
            runCatching { Thread.sleep(120L) }
        }
        return null
    }

    private fun runCaptureLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        val minBufBytes = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(FRAME_SAMPLES * 2)
        val recBufBytes = minBufBytes.coerceAtLeast(FRAME_SAMPLES * 2 * 2)

        var recorder: AudioRecord? = null
        var aec: AcousticEchoCanceler? = null
        var ns: NoiseSuppressor? = null
        try {
            recorder = openAudioRecordWithRetry(recBufBytes)
            if (recorder == null) {
                Log.w(TAG, "AudioRecord failed to initialize after retries")
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
                    recordingWriter?.appendLocalFrame(frame, offset)
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
        val trackBufBytes = minBufBytes.coerceAtLeast(FRAME_SAMPLES * 2 * 2)

        var track: AudioTrack? = null
        try {
            track = openAudioTrackWithRetry(trackBufBytes)
            if (track == null) {
                Log.w(TAG, "AudioTrack failed to initialize after retries")
                return
            }

            track.play()
            val frame = ShortArray(FRAME_SAMPLES)
            while (running.get()) {
                val n = Whatsbridge.pullVoipAudioPlayout(callId, frame)
                if (n < 0) break
                if (n > 0) {
                    recordingWriter?.appendRemoteFrame(frame, n)
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

package com.wa.beastclient.data.media

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaRecorder
import android.os.Build
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Pure RFC 7845 / RFC 3533 OggOpus container writer with unreflected Ogg CRC-32 (0x04C11DB7).
 */
internal class OggOpusMuxer(
    private val output: OutputStream,
    private val sampleRateHz: Int = 48_000,
    private val channelCount: Int = 1,
    private val preSkipSamples: Int = 312,
    serial: Int? = null
) {
    private val streamSerial: Int = serial ?: SecureRandom().nextInt().let { if (it == 0) 0x57555050 else it }
    private var pageSequence: Int = 0
    private var totalGranule48k: Long = 0L
    private var headersWritten: Boolean = false
    private var finished: Boolean = false

    private val pendingPackets = ArrayList<ByteArray>(MAX_PACKETS_PER_PAGE)
    private var pendingGranuleDelta: Long = 0L
    private var pendingSegmentCount: Int = 0

    fun writeHeaders() {
        if (headersWritten) return
        headersWritten = true
        val opusHead = buildOpusHeadPacket(
            channelCount = channelCount,
            preSkipSamples = preSkipSamples,
            inputSampleRateHz = sampleRateHz
        )
        writePage(
            headerType = FLAG_BOS,
            granulePos = 0L,
            packets = listOf(opusHead)
        )
        val opusTags = buildOpusTagsPacket(vendor = "Wupp")
        writePage(
            headerType = FLAG_NONE,
            granulePos = 0L,
            packets = listOf(opusTags)
        )
    }

    fun writeOpusPacket(packet: ByteArray) {
        if (finished || packet.isEmpty() || isCodecConfigHeaderPacket(packet)) return
        writeHeaders()

        val packetCopy = packet.copyOf()
        val segsForPacket = (packetCopy.size / 255) + 1
        if (pendingPackets.isNotEmpty() &&
            (pendingSegmentCount + segsForPacket > 255 || pendingPackets.size >= MAX_PACKETS_PER_PAGE)
        ) {
            flushPendingAudioPage(isEos = false)
        }

        pendingPackets.add(packetCopy)
        pendingSegmentCount += segsForPacket
        pendingGranuleDelta += opusPacketSampleCount48k(packetCopy).toLong()
    }

    fun finish() {
        if (finished) return
        finished = true
        writeHeaders()
        if (pendingPackets.isNotEmpty()) {
            flushPendingAudioPage(isEos = true)
        } else {
            writePage(
                headerType = FLAG_EOS,
                granulePos = totalGranule48k,
                packets = emptyList()
            )
        }
        output.flush()
    }

    private fun flushPendingAudioPage(isEos: Boolean) {
        if (pendingPackets.isEmpty()) return
        totalGranule48k += pendingGranuleDelta
        writePage(
            headerType = if (isEos) FLAG_EOS else FLAG_NONE,
            granulePos = totalGranule48k,
            packets = pendingPackets
        )
        pendingPackets.clear()
        pendingGranuleDelta = 0L
        pendingSegmentCount = 0
    }

    private fun writePage(headerType: Int, granulePos: Long, packets: List<ByteArray>) {
        val segTable = ByteArrayOutputStream()
        var payloadSize = 0
        for (pkt in packets) {
            var remaining = pkt.size
            while (remaining >= 255) {
                segTable.write(255)
                remaining -= 255
            }
            segTable.write(remaining)
            payloadSize += pkt.size
        }
        val segBytes = segTable.toByteArray()
        val headerSize = 27 + segBytes.size
        val pageBytes = ByteArray(headerSize + payloadSize)
        val buf = ByteBuffer.wrap(pageBytes).order(ByteOrder.LITTLE_ENDIAN)

        buf.put('O'.code.toByte())
        buf.put('g'.code.toByte())
        buf.put('g'.code.toByte())
        buf.put('S'.code.toByte())
        buf.put(0) // stream structure version
        buf.put(headerType.toByte())
        buf.putLong(granulePos)
        buf.putInt(streamSerial)
        buf.putInt(pageSequence++)
        buf.putInt(0) // placeholder CRC-32
        buf.put(segBytes.size.toByte())
        buf.put(segBytes)
        for (pkt in packets) {
            buf.put(pkt)
        }

        val crc = computeOggCrc32(pageBytes)
        ByteBuffer.wrap(pageBytes, 22, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(crc)
        output.write(pageBytes)
    }

    companion object {
        private const val FLAG_NONE = 0x00
        private const val FLAG_BOS = 0x02
        private const val FLAG_EOS = 0x04
        private const val MAX_PACKETS_PER_PAGE = 25

        private val OGG_CRC_TABLE = IntArray(256).also { table ->
            for (i in 0 until 256) {
                var r = i shl 24
                for (bit in 0 until 8) {
                    r = if ((r and Int.MIN_VALUE) != 0) {
                        (r shl 1) xor 0x04C11DB7
                    } else {
                        r shl 1
                    }
                }
                table[i] = r
            }
        }

        internal fun computeOggCrc32(data: ByteArray): Int {
            var crc = 0
            for (b in data) {
                val idx = ((crc ushr 24) xor (b.toInt() and 0xFF)) and 0xFF
                crc = (crc shl 8) xor OGG_CRC_TABLE[idx]
            }
            return crc
        }

        internal fun buildOpusHeadPacket(
            channelCount: Int = 1,
            preSkipSamples: Int = 312,
            inputSampleRateHz: Int = 48_000
        ): ByteArray {
            val out = ByteArray(19)
            val buf = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN)
            buf.put("OpusHead".toByteArray(Charsets.US_ASCII))
            buf.put(1) // version
            buf.put(channelCount.toByte())
            buf.putShort(preSkipSamples.toShort())
            buf.putInt(inputSampleRateHz)
            buf.putShort(0) // output gain (0 dB)
            buf.put(0) // channel mapping family 0 (mono/stereo)
            return out
        }

        internal fun buildOpusTagsPacket(vendor: String = "Wupp"): ByteArray {
            val vendorBytes = vendor.toByteArray(Charsets.UTF_8)
            val out = ByteArray(16 + vendorBytes.size)
            val buf = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN)
            buf.put("OpusTags".toByteArray(Charsets.US_ASCII))
            buf.putInt(vendorBytes.size)
            buf.put(vendorBytes)
            buf.putInt(0) // user comment list length
            return out
        }

        internal fun isCodecConfigHeaderPacket(packet: ByteArray): Boolean {
            if (packet.size >= 8) {
                val prefix = String(packet, 0, 8, Charsets.US_ASCII)
                if (prefix == "OpusHead" || prefix == "OpusTags" || prefix == "AOPUSHDR") {
                    return true
                }
            }
            return false
        }

        /**
         * Parses an RFC 6716 Opus TOC byte (and optional frame-count byte for code 3)
         * to compute exact decoded sample count at 48 kHz.
         */
        internal fun opusPacketSampleCount48k(packet: ByteArray): Int {
            if (packet.isEmpty()) return 960
            val toc = packet[0].toInt() and 0xFF
            val config = (toc shr 3) and 0x1F
            val code = toc and 0x03

            val frameSamples48k = when {
                config < 12 -> { // SILK-only
                    when (config and 0x03) {
                        0 -> 480   // 10ms
                        1 -> 960   // 20ms
                        2 -> 1920  // 40ms
                        else -> 2880 // 60ms
                    }
                }
                config < 16 -> { // Hybrid
                    if ((config and 0x01) == 0) 480 else 960
                }
                else -> { // CELT-only
                    when (config and 0x03) {
                        0 -> 120  // 2.5ms
                        1 -> 240  // 5ms
                        2 -> 480  // 10ms
                        else -> 960 // 20ms
                    }
                }
            }

            val frameCount = when (code) {
                0 -> 1
                1, 2 -> 2
                else -> if (packet.size >= 2) (packet[1].toInt() and 0x3F).coerceAtLeast(1) else 1
            }
            return frameSamples48k * frameCount
        }
    }
}

internal fun compute64BarWaveformFromPcmLevels(levels: List<Int>): IntArray {
    if (levels.isEmpty()) {
        return IntArray(64) { idx -> 18 + ((idx * 7) % 35) }
    }
    return IntArray(64) { bin ->
        val start = (bin * levels.size) / 64
        val end = (((bin + 1) * levels.size) / 64).coerceAtLeast(start + 1).coerceAtMost(levels.size)
        var peak = 0
        for (i in start until end) {
            if (levels[i] > peak) peak = levels[i]
        }
        peak.coerceIn(6, 100)
    }
}

/**
 * Records 48 kHz mono PCM via AudioRecord, encodes directly with Android's MediaCodec ("audio/opus"),
 * and writes an RFC 7845 OggOpus (.ogg) file compatible with native WhatsApp PTT playback.
 */
internal class OpusOggVoiceRecorder(
    val outputFile: File,
    private val sampleRateHz: Int = 48_000,
    private val bitRate: Int = 32_000
) {
    private var audioRecord: AudioRecord? = null
    private var codec: MediaCodec? = null
    private var workerThread: Thread? = null

    @Volatile
    private var running = false

    @Volatile
    private var paused = false

    @Volatile
    private var failed = false

    private val rmsBuckets = ArrayList<Int>(512)
    private val lock = Any()
    private val pauseLock = ReentrantLock()
    private val pauseCondition = pauseLock.newCondition()

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return false
        }
        var rec: AudioRecord? = null
        var enc: MediaCodec? = null
        return try {
            val frameSamples = (sampleRateHz / 50) // 20ms = 960 samples @ 48kHz
            val frameBytes = frameSamples * 2
            val minBuf = AudioRecord.getMinBufferSize(
                sampleRateHz,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBuf <= 0) return false
            val recBufSize = max(minBuf * 2, frameBytes * 8)

            rec = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRateHz,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                recBufSize
            )
            if (rec.state != AudioRecord.STATE_INITIALIZED) {
                rec.release()
                return false
            }

            val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_OPUS, sampleRateHz, 1).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, frameBytes * 2)
            }
            enc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS)
            enc.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            enc.start()
            rec.startRecording()

            audioRecord = rec
            codec = enc
            running = true
            failed = false

            workerThread = Thread({
                encodeLoop(rec, enc, frameBytes)
            }, "wupp-opus-rec").apply { start() }

            true
        } catch (_: Throwable) {
            running = false
            runCatching { rec?.stop() }
            runCatching { rec?.release() }
            runCatching { enc?.stop() }
            runCatching { enc?.release() }
            audioRecord = null
            codec = null
            false
        }
    }

    fun isPaused(): Boolean = paused

    fun pause() {
        if (!running || paused) return
        paused = true
        runCatching { audioRecord?.stop() }
    }

    fun resume() {
        if (!running || !paused) return
        pauseLock.withLock {
            runCatching { audioRecord?.startRecording() }
            paused = false
            pauseCondition.signalAll()
        }
    }

    fun stopAndFinalize(): Boolean {
        running = false
        pauseLock.withLock {
            paused = false
            pauseCondition.signalAll()
        }
        runCatching { workerThread?.join(5_000L) }
        workerThread = null
        runCatching { audioRecord?.stop() }
        runCatching { audioRecord?.release() }
        audioRecord = null
        runCatching { codec?.stop() }
        runCatching { codec?.release() }
        codec = null
        return !failed && outputFile.exists() && outputFile.length() > 128L
    }

    fun cancelAndDelete() {
        running = false
        pauseLock.withLock {
            paused = false
            pauseCondition.signalAll()
        }
        runCatching { workerThread?.join(2_000L) }
        workerThread = null
        runCatching { audioRecord?.stop() }
        runCatching { audioRecord?.release() }
        audioRecord = null
        runCatching { codec?.stop() }
        runCatching { codec?.release() }
        codec = null
        runCatching { outputFile.delete() }
    }

    fun waveformCsv(): String {
        val snapshot = synchronized(lock) { rmsBuckets.toList() }
        return compute64BarWaveformFromPcmLevels(snapshot).joinToString(",")
    }

    private fun encodeLoop(rec: AudioRecord, enc: MediaCodec, frameBytes: Int) {
        val pcmBuf = ByteArray(frameBytes)
        val outInfo = MediaCodec.BufferInfo()
        var totalSamples = 0L
        var eosQueued = false
        var eosSeen = false

        var fos: FileOutputStream? = null
        try {
            fos = FileOutputStream(outputFile)
            val muxer = OggOpusMuxer(
                output = fos,
                sampleRateHz = sampleRateHz,
                channelCount = 1,
                preSkipSamples = 312
            )
            muxer.writeHeaders()

            while (!eosSeen) {
                if (running && paused) {
                    pauseLock.withLock {
                        while (running && paused) {
                            runCatching { pauseCondition.await(250L, TimeUnit.MILLISECONDS) }
                        }
                    }
                    continue
                }
                if (!eosQueued) {
                    val inIdx = enc.dequeueInputBuffer(10_000L)
                    if (inIdx >= 0) {
                        val inBuf = enc.getInputBuffer(inIdx)
                        if (inBuf != null) {
                            inBuf.clear()
                            if (running) {
                                var offset = 0
                                while (running && !paused && offset < frameBytes) {
                                    val n = rec.read(pcmBuf, offset, frameBytes - offset)
                                    if (n <= 0) break
                                    offset += n
                                }
                                if (paused && offset == 0) {
                                    enc.queueInputBuffer(inIdx, 0, 0, (totalSamples * 1_000_000L) / sampleRateHz, 0)
                                } else if (offset > 0) {
                                    val evenBytes = offset and 1.inv()
                                    if (evenBytes > 0) {
                                        recordRmsSample(pcmBuf, evenBytes)
                                        inBuf.put(pcmBuf, 0, evenBytes)
                                        val samples = evenBytes / 2
                                        val ptsUs = (totalSamples * 1_000_000L) / sampleRateHz
                                        totalSamples += samples
                                        enc.queueInputBuffer(inIdx, 0, evenBytes, ptsUs, 0)
                                    } else {
                                        enc.queueInputBuffer(inIdx, 0, 0, (totalSamples * 1_000_000L) / sampleRateHz, 0)
                                    }
                                } else {
                                    val ptsUs = (totalSamples * 1_000_000L) / sampleRateHz
                                    enc.queueInputBuffer(inIdx, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                    eosQueued = true
                                }
                            } else {
                                val ptsUs = (totalSamples * 1_000_000L) / sampleRateHz
                                enc.queueInputBuffer(inIdx, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                eosQueued = true
                            }
                        }
                    }
                }

                var outIdx = enc.dequeueOutputBuffer(outInfo, if (eosQueued) 20_000L else 0L)
                if (outIdx == MediaCodec.INFO_TRY_AGAIN_LATER && eosQueued && !running) {
                    outIdx = enc.dequeueOutputBuffer(outInfo, 30_000L)
                    if (outIdx == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        eosSeen = true
                    }
                }
                if (outIdx >= 0) {
                    if (drainOutputBuffer(enc, outIdx, outInfo, muxer)) {
                        eosSeen = true
                    }
                }
            }

            muxer.finish()
        } catch (_: Throwable) {
            failed = true
        } finally {
            runCatching { fos?.close() }
        }
    }

    private fun drainOutputBuffer(
        enc: MediaCodec,
        outIdx: Int,
        outInfo: MediaCodec.BufferInfo,
        muxer: OggOpusMuxer
    ): Boolean {
        val outBuf = enc.getOutputBuffer(outIdx)
        val isConfig = (outInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
        if (outBuf != null && outInfo.size > 0 && !isConfig) {
            val packet = ByteArray(outInfo.size)
            outBuf.position(outInfo.offset)
            outBuf.limit(outInfo.offset + outInfo.size)
            outBuf.get(packet)
            muxer.writeOpusPacket(packet)
        }
        val isEos = (outInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
        enc.releaseOutputBuffer(outIdx, false)
        return isEos
    }

    private fun recordRmsSample(pcm: ByteArray, evenByteLen: Int) {
        var sumSq = 0.0
        val sampleCount = evenByteLen / 2
        var idx = 0
        while (idx + 1 < evenByteLen) {
            val lo = pcm[idx].toInt() and 0xFF
            val hi = pcm[idx + 1].toInt()
            val sample = (hi shl 8) or lo
            val norm = sample.toDouble() / 32768.0
            sumSq += norm * norm
            idx += 2
        }
        val rms = if (sampleCount > 0) sqrt(sumSq / sampleCount) else 0.0
        val bar = min(100, max(6, (rms * 240.0).roundToInt()))
        synchronized(lock) {
            rmsBuckets.add(bar)
        }
    }
}

internal fun hasExtractableAudioTrack(file: File): Boolean {
    if (!file.exists() || file.length() <= 128L) return false
    val extractor = MediaExtractor()
    return try {
        extractor.setDataSource(file.absolutePath)
        for (i in 0 until extractor.trackCount) {
            val fmt = extractor.getTrackFormat(i)
            val mime = fmt.getString(MediaFormat.KEY_MIME).orEmpty()
            if (mime.startsWith("audio/", ignoreCase = true)) {
                return true
            }
        }
        false
    } catch (_: Throwable) {
        false
    } finally {
        runCatching { extractor.release() }
    }
}

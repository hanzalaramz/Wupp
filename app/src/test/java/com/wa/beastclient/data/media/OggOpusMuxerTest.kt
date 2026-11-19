package com.wa.beastclient.data.media

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class OggOpusMuxerTest {

    @Test
    fun `muxer writes valid RFC 7845 OggOpus BOS, Tags, audio CRC-32, and EOS`() {
        val baos = ByteArrayOutputStream()
        val muxer = OggOpusMuxer(
            output = baos,
            sampleRateHz = 48_000,
            channelCount = 1,
            preSkipSamples = 312,
            serial = 0x12345678
        )

        // Ensure Android codec-config headers are stripped cleanly
        muxer.writeOpusPacket("AOPUSHDRextra".toByteArray(Charsets.US_ASCII))
        muxer.writeOpusPacket("OpusHeadextra".toByteArray(Charsets.US_ASCII))

        // Write two 20ms SILK/Hybrid/CELT packets (config=16 -> 20ms CELT/FB = 960 samples @ 48kHz? Wait: config=19 is 20ms CELT)
        val pkt1 = byteArrayOf((19 shl 3).toByte(), 0x11, 0x22, 0x33)
        // Also verify multi-segment (>255 bytes) packet lacing works cleanly
        val pkt2 = ByteArray(300) { idx -> if (idx == 0) (19 shl 3).toByte() else idx.toByte() }
        muxer.writeOpusPacket(pkt1)
        muxer.writeOpusPacket(pkt2)
        muxer.finish()

        val bytes = baos.toByteArray()
        val pages = parseOggPages(bytes)
        assertEquals(3, pages.size)

        // Page 0: BOS OpusHead
        val p0 = pages[0]
        assertEquals(0x02, p0.headerType)
        assertEquals(0L, p0.granulePos)
        assertEquals(0x12345678, p0.serial)
        assertEquals(0, p0.sequence)
        assertTrue(p0.crcValid)
        assertEquals("OpusHead", String(p0.payload, 0, 8, Charsets.US_ASCII))
        assertEquals(19, p0.payload.size)
        assertEquals(1, p0.payload[8].toInt()) // version 1
        assertEquals(1, p0.payload[9].toInt()) // 1 mono channel
        val headBuf = ByteBuffer.wrap(p0.payload).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(312.toShort(), headBuf.getShort(10))
        assertEquals(48_000, headBuf.getInt(12))

        // Page 1: OpusTags
        val p1 = pages[1]
        assertEquals(0x00, p1.headerType)
        assertEquals(0L, p1.granulePos)
        assertEquals(1, p1.sequence)
        assertTrue(p1.crcValid)
        assertEquals("OpusTags", String(p1.payload, 0, 8, Charsets.US_ASCII))

        // Page 2: EOS audio page with cumulative 1920 samples (2 x 20ms @ 48kHz)
        val p2 = pages[2]
        assertEquals(0x04, p2.headerType)
        assertEquals(1920L, p2.granulePos)
        assertEquals(2, p2.sequence)
        assertTrue(p2.crcValid)
        assertArrayEquals(pkt1 + pkt2, p2.payload)
    }

    @Test
    fun `opusPacketSampleCount48k parses TOC configs and frame counts`() {
        // config 1 -> 20ms SILK, code 0 -> 1 frame = 960 samples
        assertEquals(960, OggOpusMuxer.opusPacketSampleCount48k(byteArrayOf((1 shl 3).toByte(), 0x01)))
        // config 1 -> 20ms SILK, code 1 -> 2 frames = 1920 samples
        assertEquals(1920, OggOpusMuxer.opusPacketSampleCount48k(byteArrayOf(((1 shl 3) or 1).toByte(), 0x01)))
        // config 19 -> 20ms CELT, code 3 with 3 frames = 2880 samples
        assertEquals(2880, OggOpusMuxer.opusPacketSampleCount48k(byteArrayOf(((19 shl 3) or 3).toByte(), 0x03)))
        assertFalse(OggOpusMuxer.isCodecConfigHeaderPacket(byteArrayOf((19 shl 3).toByte(), 0x01)))
    }

    @Test
    fun `compute64BarWaveformFromPcmLevels always outputs 64 clamped bars`() {
        val bars = compute64BarWaveformFromPcmLevels(listOf(0, 50, 120, 30, 85))
        assertEquals(64, bars.size)
        assertTrue(bars.all { it in 6..100 })
    }

    private data class ParsedOggPage(
        val headerType: Int,
        val granulePos: Long,
        val serial: Int,
        val sequence: Int,
        val payload: ByteArray,
        val crcValid: Boolean
    )

    private fun parseOggPages(data: ByteArray): List<ParsedOggPage> {
        val result = ArrayList<ParsedOggPage>()
        var offset = 0
        while (offset < data.size) {
            assertEquals("OggS", String(data, offset, 4, Charsets.US_ASCII))
            val headerType = data[offset + 5].toInt() and 0xFF
            val buf = ByteBuffer.wrap(data, offset, 27).slice().order(ByteOrder.LITTLE_ENDIAN)
            val granule = buf.getLong(6)
            val serial = buf.getInt(14)
            val seq = buf.getInt(18)
            val expectedCrc = buf.getInt(22)
            val segCount = data[offset + 26].toInt() and 0xFF
            var bodyLen = 0
            for (s in 0 until segCount) {
                bodyLen += data[offset + 27 + s].toInt() and 0xFF
            }
            val totalPageLen = 27 + segCount + bodyLen
            val pageCopy = data.copyOfRange(offset, offset + totalPageLen)
            pageCopy[22] = 0
            pageCopy[23] = 0
            pageCopy[24] = 0
            pageCopy[25] = 0
            val actualCrc = OggOpusMuxer.computeOggCrc32(pageCopy)
            val payload = data.copyOfRange(offset + 27 + segCount, offset + totalPageLen)
            result.add(ParsedOggPage(headerType, granule, serial, seq, payload, actualCrc == expectedCrc))
            offset += totalPageLen
        }
        return result
    }
}

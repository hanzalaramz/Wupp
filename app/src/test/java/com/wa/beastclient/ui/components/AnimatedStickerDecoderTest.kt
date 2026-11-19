package com.wa.beastclient.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class AnimatedStickerDecoderTest {

    @Test
    fun detectStickerContainerFormatIdentifiesZipJsonAndWebpHeaders() {
        val zipHeader = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
        assertEquals(StickerContainerFormat.LOTTIE_WAS_ZIP, detectStickerContainerFormat(zipHeader))

        val jsonHeader = " \n {\"v\":\"5.7.4\"}".toByteArray(Charsets.UTF_8)
        assertEquals(StickerContainerFormat.LOTTIE_JSON, detectStickerContainerFormat(jsonHeader))

        val webpHeader = "RIFF\u0020\u0000\u0000\u0000WEBPVP8X".toByteArray(Charsets.ISO_8859_1)
        assertEquals(StickerContainerFormat.WEBP_OR_RASTER, detectStickerContainerFormat(webpHeader))
    }

    @Test
    fun extractLottieJsonFromZipStreamExtractsPreferredAnimationJson() {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("meta.json"))
            zos.write("""{"meta":1}""".toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("animation.json"))
            zos.write("""{"v":"5.9.0","fr":30,"layers":[]}""".toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
        val extracted = extractLottieJsonFromZipStream(baos.toByteArray())
        assertNotNull(extracted)
        assertTrue(extracted!!.contains("layers"))
    }

    @Test
    fun extractLottieJsonFromInvalidZipReturnsNull() {
        assertNull(extractLottieJsonFromZipStream("not a zip".toByteArray(Charsets.UTF_8)))
    }
}

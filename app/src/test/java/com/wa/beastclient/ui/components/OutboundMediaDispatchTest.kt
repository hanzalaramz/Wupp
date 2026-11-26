package com.wa.beastclient.ui.components

import com.wa.beastclient.core.service.formatOutboundMediaBody
import com.wa.beastclient.core.service.resolveOutboundMediaDispatch
import com.wa.beastclient.data.db.MessageEntity
import com.wa.beastclient.data.media.FavoriteStickerRecord
import com.wa.beastclient.data.media.OutboundQualityMode
import com.wa.beastclient.data.media.isStickerMatchedByFavoriteKeys
import com.wa.beastclient.ui.resolveFavoriteStickerEntities
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OutboundMediaDispatchTest {

    @Test
    fun resolveOutboundMediaDispatch_keepsVideosAsVideoEvenInOriginalMode() {
        assertEquals(
            "VIDEO" to "hd",
            resolveOutboundMediaDispatch("video/mp4", OutboundQualityMode.ORIGINAL, isVoiceNote = false)
        )
        assertEquals(
            "VIDEO" to "standard",
            resolveOutboundMediaDispatch("video/quicktime", OutboundQualityMode.STANDARD, isVoiceNote = false)
        )
    }

    @Test
    fun resolveOutboundMediaDispatch_keepsPhotosAsImageEvenInOriginalMode() {
        assertEquals(
            "IMAGE" to "original",
            resolveOutboundMediaDispatch("image/png", OutboundQualityMode.ORIGINAL, isVoiceNote = false)
        )
        assertEquals(
            "IMAGE" to "original",
            resolveOutboundMediaDispatch("image/jpeg", OutboundQualityMode.ORIGINAL, isVoiceNote = false)
        )
        assertEquals(
            "IMAGE" to "hd",
            resolveOutboundMediaDispatch("image/jpeg", OutboundQualityMode.HD, isVoiceNote = false)
        )
        assertEquals(
            "DOCUMENT" to "hd",
            resolveOutboundMediaDispatch("application/pdf", OutboundQualityMode.HD, isVoiceNote = false)
        )
        assertEquals(
            "DOCUMENT" to "original",
            resolveOutboundMediaDispatch("application/octet-stream", OutboundQualityMode.ORIGINAL, isVoiceNote = false)
        )
        assertEquals(
            "PTT" to "standard",
            resolveOutboundMediaDispatch("audio/ogg", OutboundQualityMode.STANDARD, isVoiceNote = true)
        )
        assertEquals(
            "STICKER" to "sticker",
            resolveOutboundMediaDispatch("image/webp", OutboundQualityMode.STICKER, isVoiceNote = false)
        )
        assertEquals(
            "VIDEO" to "gif",
            resolveOutboundMediaDispatch("video/mp4", OutboundQualityMode.GIF, isVoiceNote = false)
        )
    }

    @Test
    fun formatOutboundMediaBody_preservesImageCaptionsAndDocumentFilenames() {
        assertEquals(
            "Look at this photo",
            formatOutboundMediaBody("IMAGE", "Look at this photo", "orig_123.jpg")
        )
        assertEquals(
            "report.pdf\nQuarterly numbers",
            formatOutboundMediaBody("DOCUMENT", "Quarterly numbers", "report.pdf")
        )
        assertEquals(
            "report.pdf",
            formatOutboundMediaBody("DOCUMENT", "", "report.pdf")
        )
    }

    @Test
    fun resolveDocumentExtensionLabel_mapsImageMimeToImageLabelInsteadOfFile() {
        assertEquals("JPG", resolveDocumentExtensionLabel("image/jpeg", "photo"))
        assertEquals("PNG", resolveDocumentExtensionLabel("image/png", "capture"))
        assertEquals("PDF", resolveDocumentExtensionLabel("application/pdf", "invoice.pdf"))
    }

    @Test
    fun favoriteStickers_persistBeyondRecentWindowAndIgnoreBlankSha() {
        assertFalse(
            isStickerMatchedByFavoriteKeys(
                sha256 = "",
                localPath = "/tmp/s1.webp",
                messageId = "m1",
                favoriteKeys = setOf("")
            )
        )
        assertTrue(
            isStickerMatchedByFavoriteKeys(
                sha256 = "",
                localPath = "/tmp/s1.webp",
                messageId = "m1",
                favoriteKeys = setOf("/tmp/s1.webp")
            )
        )

        val persisted = FavoriteStickerRecord(
            key = "/tmp/fav_1.webp",
            sha256 = "abc123",
            filePath = "/tmp/favorites/fav_1.webp",
            originalPath = "/tmp/old_deleted_chat_sticker.webp",
            messageId = "old_msg_999",
            addedAt = 5000L
        )
        val dbFallback = MessageEntity(
            id = "m_db_2",
            chatJid = "123@s.whatsapp.net",
            senderJid = "123@s.whatsapp.net",
            timestamp = 3000L,
            msgType = "STICKER",
            mimeType = "image/webp",
            localMediaPath = "/tmp/db_sticker_2.webp",
            mediaSha256 = ""
        )
        val resolved = resolveFavoriteStickerEntities(
            allLocalStickers = listOf(dbFallback),
            favoriteRecords = listOf(persisted),
            favoriteKeys = setOf("/tmp/db_sticker_2.webp"),
            fileExists = { path -> path == "/tmp/favorites/fav_1.webp" || path == "/tmp/db_sticker_2.webp" }
        )
        assertEquals(2, resolved.size)
        assertEquals("/tmp/favorites/fav_1.webp", resolved[0].localMediaPath)
        assertEquals("/tmp/db_sticker_2.webp", resolved[1].localMediaPath)
    }

    @Test
    fun computeVideoDisplayRatio_accountsForRotationAndPixelAspectRatio() {
        // Landscape 1920x1080 -> 16:9
        assertEquals(16f / 9f, computeVideoDisplayRatio(1920, 1080)!!, 0.001f)
        // Rotated portrait 1920x1080 at 90 degrees -> 9:16
        assertEquals(9f / 16f, computeVideoDisplayRatio(1920, 1080, rotationDegrees = 90)!!, 0.001f)
        assertEquals(9f / 16f, computeVideoDisplayRatio(1920, 1080, rotationDegrees = 270)!!, 0.001f)
        // Non-square pixel SAR
        assertEquals(1.5f, computeVideoDisplayRatio(720, 720, rotationDegrees = 0, pixelWidthHeightRatio = 1.5f)!!, 0.001f)
        // Invalid dimensions
        assertNull(computeVideoDisplayRatio(0, 1080))
        assertNull(computeVideoDisplayRatio(1920, -1))
    }
}

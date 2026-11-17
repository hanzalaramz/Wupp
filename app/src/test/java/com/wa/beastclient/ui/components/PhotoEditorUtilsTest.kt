package com.wa.beastclient.ui.components

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoEditorUtilsTest {

    @Test
    fun normalizeRotationAngle_handlesPositiveNegativeAndFullTurns() {
        assertEquals(0, normalizeRotationAngle(0))
        assertEquals(90, normalizeRotationAngle(90))
        assertEquals(180, normalizeRotationAngle(180))
        assertEquals(270, normalizeRotationAngle(270))
        assertEquals(0, normalizeRotationAngle(360))
        assertEquals(90, normalizeRotationAngle(450))
        assertEquals(270, normalizeRotationAngle(-90))
        assertEquals(180, normalizeRotationAngle(-180))
        assertEquals(90, normalizeRotationAngle(-270))
        assertEquals(0, normalizeRotationAngle(-360))
    }

    @Test
    fun photoFilterPresets_haveValidMatrixDimensionsAndDistinctLabels() {
        assertNull(PhotoFilterPreset.NORMAL.matrixArray)

        val nonNormalPresets = PhotoFilterPreset.entries.filter { it != PhotoFilterPreset.NORMAL }
        for (preset in nonNormalPresets) {
            val matrix = preset.matrixArray
            org.junit.Assert.assertNotNull(matrix)
            assertEquals("Matrix for ${preset.name} must have 20 float entries (4x5 ColorMatrix)", 20, matrix!!.size)
        }

        val labels = PhotoFilterPreset.entries.map { it.label }
        assertEquals(labels.size, labels.toSet().size)
    }

    @Test
    fun applyUndoAction_revertsStrokesTextAndStickersCorrectly() {
        val strokes = mutableListOf<DoodleStroke>()
        val texts = mutableListOf<TextOverlayItem>()
        val stickers = mutableListOf<StickerOverlayItem>()

        val stroke1 = DoodleStroke(listOf(NormPoint(0.1f, 0.1f), NormPoint(0.2f, 0.2f)), Color.Red)
        val stroke2 = DoodleStroke(listOf(NormPoint(0.3f, 0.3f), NormPoint(0.4f, 0.4f)), Color.Blue)
        strokes.add(stroke1)
        strokes.add(stroke2)

        val text1 = TextOverlayItem(id = "txt-1", text = "Hello", color = Color.White, normX = 0.5f, normY = 0.5f)
        texts.add(text1)

        val sticker1 = StickerOverlayItem(id = "stk-1", emoji = "🔥", normX = 0.2f, normY = 0.3f)
        stickers.add(sticker1)

        // 1. Undo Sticker
        applyUndoAction(UndoAction.AddSticker(sticker1), strokes, texts, stickers)
        assertTrue(stickers.isEmpty())

        // 2. Undo Text
        applyUndoAction(UndoAction.AddText(text1), strokes, texts, stickers)
        assertTrue(texts.isEmpty())

        // 3. Undo Stroke 2
        applyUndoAction(UndoAction.AddStroke(stroke2), strokes, texts, stickers)
        assertEquals(1, strokes.size)
        assertEquals(stroke1, strokes.first())

        // 4. Undo Stroke 1
        applyUndoAction(UndoAction.AddStroke(stroke1), strokes, texts, stickers)
        assertTrue(strokes.isEmpty())
    }

    @Test
    fun whatsappPaletteAndEmojis_containExpectedElements() {
        assertTrue(WHATSAPP_PALETTE_COLORS.isNotEmpty())
        // Contains WhatsApp brand green #25D366
        assertTrue(WHATSAPP_PALETTE_COLORS.any { it == Color(0xFF25D366) })

        assertTrue(POPULAR_STICKER_EMOJIS.contains("❤️"))
        assertTrue(POPULAR_STICKER_EMOJIS.contains("🔥"))
        assertTrue(POPULAR_STICKER_EMOJIS.contains("😂"))
    }
}

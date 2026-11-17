package com.wa.beastclient.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class BuiltinSticker(
    val id: String,
    val name: String,
    val file: File
)

/**
 * Manages built-in WhatsApp-style sticker packs (e.g. "Cuppy") rendered into
 * crisp 512x512 transparent WebP stickers matching official WhatsApp stickers.
 */
object CuppyPackManager {
    const val PACK_NAME = "Cuppy"

    suspend fun getCuppyStickers(context: Context): List<BuiltinSticker> = withContext(Dispatchers.IO) {
        val packDir = File(context.filesDir, "stickers/cuppy").apply { mkdirs() }
        val items = mutableListOf<BuiltinSticker>()

        val stickerTypes = listOf(
            "happy" to "Cuppy Happy",
            "laugh" to "Cuppy Laughing",
            "love" to "Cuppy Love",
            "broken_heart" to "Cuppy Heartbreak",
            "crying" to "Cuppy Crying",
            "bawling" to "Cuppy Bawling",
            "sleeping" to "Cuppy Sleeping",
            "cool" to "Cuppy Cool",
            "shock" to "Cuppy Shock"
        )

        for ((key, title) in stickerTypes) {
            val file = File(packDir, "cuppy_$key.webp")
            if (!file.exists() || file.length() == 0L) {
                renderCuppyToWebp(key, file)
            }
            if (file.exists() && file.length() > 0L) {
                items.add(BuiltinSticker("builtin_cuppy_$key", title, file))
            }
        }
        items
    }

    private fun renderCuppyToWebp(type: String, destFile: File) {
        val size = 512
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(44, 30, 20)
            style = Paint.Style.STROKE
            strokeWidth = 14f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        val cupFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 252, 245)
            style = Paint.Style.FILL
        }

        val coffeeFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(112, 66, 32)
            style = Paint.Style.FILL
        }

        val foamFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(238, 218, 195)
            style = Paint.Style.FILL
        }

        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(235, 75, 75)
            style = Paint.Style.FILL
        }

        val tearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(70, 160, 245)
            style = Paint.Style.FILL
        }

        // Draw Saucer
        val saucerRect = RectF(90f, 410f, 422f, 455f)
        canvas.drawOval(saucerRect, cupFill)
        canvas.drawOval(saucerRect, strokePaint)

        // Draw Cup Handle
        val handlePath = Path().apply {
            moveTo(380f, 220f)
            cubicTo(490f, 220f, 490f, 350f, 370f, 360f)
        }
        val handleStroke = Paint(strokePaint).apply { strokeWidth = 32f }
        canvas.drawPath(handlePath, handleStroke)
        val handleInner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 252, 245)
            style = Paint.Style.STROKE
            strokeWidth = 16f
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawPath(handlePath, handleInner)

        // Draw Cup Body
        val cupPath = Path().apply {
            moveTo(110f, 170f)
            cubicTo(100f, 380f, 150f, 420f, 256f, 420f)
            cubicTo(362f, 420f, 412f, 380f, 402f, 170f)
            close()
        }
        canvas.drawPath(cupPath, cupFill)
        canvas.drawPath(cupPath, strokePaint)

        // Cup Rim & Coffee Oval
        val rimRect = RectF(108f, 130f, 404f, 210f)
        canvas.drawOval(rimRect, cupFill)
        canvas.drawOval(rimRect, strokePaint)

        val coffeeRect = RectF(126f, 142f, 386f, 198f)
        canvas.drawOval(coffeeRect, coffeeFill)
        canvas.drawOval(RectF(146f, 150f, 366f, 190f), foamFill)

        // Expressions & Decorations
        when (type) {
            "happy" -> {
                // Happy arc eyes
                val eyeL = Path().apply {
                    moveTo(180f, 280f)
                    quadTo(205f, 255f, 230f, 280f)
                }
                val eyeR = Path().apply {
                    moveTo(282f, 280f)
                    quadTo(307f, 255f, 332f, 280f)
                }
                canvas.drawPath(eyeL, strokePaint)
                canvas.drawPath(eyeR, strokePaint)

                // Open smile
                val mouth = Path().apply {
                    moveTo(225f, 310f)
                    cubicTo(225f, 365f, 287f, 365f, 287f, 310f)
                    close()
                }
                canvas.drawPath(mouth, accentPaint)
                canvas.drawPath(mouth, strokePaint)

                // Pink cheeks
                val cheekPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(120, 255, 140, 150)
                }
                canvas.drawCircle(170f, 315f, 18f, cheekPaint)
                canvas.drawCircle(342f, 315f, 18f, cheekPaint)
            }
            "love" -> {
                // Heart in foam
                val heartPath = Path().apply {
                    moveTo(256f, 180f)
                    cubicTo(220f, 150f, 220f, 195f, 256f, 210f)
                    cubicTo(292f, 195f, 292f, 150f, 256f, 180f)
                    close()
                }
                canvas.drawPath(heartPath, accentPaint)

                // Heart eyes
                drawMiniHeart(canvas, 205f, 275f, 22f, accentPaint)
                drawMiniHeart(canvas, 307f, 275f, 22f, accentPaint)

                // Cute smile
                val mouth = Path().apply {
                    moveTo(236f, 320f)
                    quadTo(256f, 345f, 276f, 320f)
                }
                canvas.drawPath(mouth, strokePaint)
            }
            "broken_heart" -> {
                // Broken heart foam
                drawMiniHeart(canvas, 256f, 180f, 28f, accentPaint)
                val breakLine = Path().apply {
                    moveTo(256f, 160f)
                    lineTo(250f, 175f)
                    lineTo(262f, 190f)
                    lineTo(256f, 208f)
                }
                val breakPaint = Paint(strokePaint).apply { strokeWidth = 5f; color = Color.WHITE }
                canvas.drawPath(breakLine, breakPaint)

                // Sad eyes
                canvas.drawCircle(200f, 285f, 12f, strokePaint)
                canvas.drawCircle(312f, 285f, 12f, strokePaint)

                // Wobbly sad mouth
                val mouth = Path().apply {
                    moveTo(230f, 340f)
                    quadTo(256f, 320f, 282f, 340f)
                }
                canvas.drawPath(mouth, strokePaint)
            }
            "laugh" -> {
                // Squint laugh eyes "> <"
                val eyeL = Path().apply {
                    moveTo(180f, 260f); lineTo(215f, 275f); lineTo(180f, 290f)
                }
                val eyeR = Path().apply {
                    moveTo(332f, 260f); lineTo(297f, 275f); lineTo(332f, 290f)
                }
                canvas.drawPath(eyeL, strokePaint)
                canvas.drawPath(eyeR, strokePaint)

                // Wide laughing open mouth
                val mouth = Path().apply {
                    moveTo(215f, 305f)
                    cubicTo(215f, 375f, 297f, 375f, 297f, 305f)
                    close()
                }
                canvas.drawPath(mouth, accentPaint)
                canvas.drawPath(mouth, strokePaint)

                // Tears of joy
                canvas.drawOval(RectF(150f, 270f, 170f, 305f), tearPaint)
                canvas.drawOval(RectF(342f, 270f, 362f, 305f), tearPaint)
            }
            "crying" -> {
                // Sad eyes
                canvas.drawCircle(205f, 280f, 12f, strokePaint)
                canvas.drawCircle(307f, 280f, 12f, strokePaint)

                // Blue streams of tears
                val tearStreamL = Path().apply {
                    moveTo(195f, 290f); lineTo(205f, 365f); lineTo(215f, 290f); close()
                }
                val tearStreamR = Path().apply {
                    moveTo(297f, 290f); lineTo(307f, 365f); lineTo(317f, 290f); close()
                }
                canvas.drawPath(tearStreamL, tearPaint)
                canvas.drawPath(tearStreamR, tearPaint)

                // Sad mouth
                val mouth = Path().apply {
                    moveTo(230f, 335f)
                    quadTo(256f, 315f, 282f, 335f)
                }
                canvas.drawPath(mouth, strokePaint)
            }
            "bawling" -> {
                // Bawling closed eyes
                val eyeL = Path().apply { moveTo(180f, 280f); lineTo(225f, 280f) }
                val eyeR = Path().apply { moveTo(287f, 280f); lineTo(332f, 280f) }
                canvas.drawPath(eyeL, strokePaint)
                canvas.drawPath(eyeR, strokePaint)

                // Huge tear fountains
                val fountL = Path().apply {
                    moveTo(180f, 280f); cubicTo(130f, 270f, 120f, 360f, 160f, 390f); close()
                }
                val fountR = Path().apply {
                    moveTo(332f, 280f); cubicTo(382f, 270f, 392f, 360f, 352f, 390f); close()
                }
                canvas.drawPath(fountL, tearPaint)
                canvas.drawPath(fountR, tearPaint)

                // Open howling mouth
                val mouth = Path().apply {
                    moveTo(225f, 310f); quadTo(256f, 360f, 287f, 310f); close()
                }
                canvas.drawPath(mouth, accentPaint)
                canvas.drawPath(mouth, strokePaint)
            }
            "sleeping" -> {
                // Closed line eyes
                val eyeL = Path().apply { moveTo(185f, 280f); lineTo(225f, 280f) }
                val eyeR = Path().apply { moveTo(287f, 280f); lineTo(327f, 280f) }
                canvas.drawPath(eyeL, strokePaint)
                canvas.drawPath(eyeR, strokePaint)

                // Small "o" mouth
                canvas.drawCircle(256f, 325f, 10f, strokePaint)

                // Floating ZZZ
                val zPaint = Paint(strokePaint).apply {
                    textSize = 48f
                    style = Paint.Style.FILL_AND_STROKE
                    strokeWidth = 3f
                    color = Color.rgb(65, 125, 230)
                }
                canvas.drawText("Z", 360f, 190f, zPaint)
                zPaint.textSize = 34f
                canvas.drawText("z", 385f, 150f, zPaint)
                zPaint.textSize = 24f
                canvas.drawText("z", 405f, 125f, zPaint)
            }
            "cool" -> {
                // Sunglasses
                val glasses = Path().apply {
                    moveTo(165f, 260f)
                    lineTo(347f, 260f)
                    cubicTo(350f, 305f, 290f, 305f, 285f, 275f)
                    cubicTo(280f, 265f, 232f, 265f, 227f, 275f)
                    cubicTo(222f, 305f, 162f, 305f, 165f, 260f)
                    close()
                }
                val darkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(25, 30, 35) }
                canvas.drawPath(glasses, darkPaint)
                canvas.drawPath(glasses, strokePaint)

                // Smirk
                val smirk = Path().apply {
                    moveTo(235f, 330f)
                    quadTo(260f, 335f, 285f, 320f)
                }
                canvas.drawPath(smirk, strokePaint)

                // Cookie beside cup
                val cookiePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(205, 140, 75) }
                canvas.drawCircle(130f, 410f, 32f, cookiePaint)
                canvas.drawCircle(130f, 410f, 32f, strokePaint)
                val chipPaint = Paint(darkPaint)
                canvas.drawCircle(120f, 400f, 5f, chipPaint)
                canvas.drawCircle(142f, 415f, 4f, chipPaint)
                canvas.drawCircle(128f, 425f, 5f, chipPaint)
            }
            "shock" -> {
                // Huge round eyes
                canvas.drawCircle(200f, 275f, 22f, cupFill)
                canvas.drawCircle(200f, 275f, 22f, strokePaint)
                canvas.drawCircle(200f, 275f, 8f, strokePaint)

                canvas.drawCircle(312f, 275f, 22f, cupFill)
                canvas.drawCircle(312f, 275f, 22f, strokePaint)
                canvas.drawCircle(312f, 275f, 8f, strokePaint)

                // Round open shock mouth
                canvas.drawOval(RectF(236f, 310f, 276f, 355f), strokePaint)
            }
        }

        FileOutputStream(destFile).use { out ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                bmp.compress(Bitmap.CompressFormat.WEBP_LOSSY, 90, out)
            } else {
                @Suppress("DEPRECATION")
                bmp.compress(Bitmap.CompressFormat.WEBP, 90, out)
            }
        }
        bmp.recycle()
    }

    private fun drawMiniHeart(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        val path = Path().apply {
            moveTo(cx, cy + size * 0.7f)
            cubicTo(cx - size, cy - size * 0.5f, cx - size * 0.4f, cy - size, cx, cy - size * 0.4f)
            cubicTo(cx + size * 0.4f, cy - size, cx + size, cy - size * 0.5f, cx, cy + size * 0.7f)
            close()
        }
        canvas.drawPath(path, paint)
    }
}

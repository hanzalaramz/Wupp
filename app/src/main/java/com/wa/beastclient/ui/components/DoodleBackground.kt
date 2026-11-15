package com.wa.beastclient.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import com.wa.beastclient.ui.theme.LocalWaColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Renders a crisp fine-line WhatsApp doodle wallpaper tiled with GPU ImageShader repeat tiling.
 */
@Composable
fun DoodleBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val colors = LocalWaColors.current

    val shaderBrush = remember(colors.doodleColor) {
        val tilePx = 360
        val bmp = Bitmap.createBitmap(tilePx, tilePx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.0f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = colors.doodleColor.toArgb()
        }
        drawDoodleTile(c, paint)
        ShaderBrush(ImageShader(bmp.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
    }

    val tileAlpha = colors.doodleTintAlpha
    Box(modifier = modifier.background(colors.chatCanvas)) {
        ComposeCanvas(modifier = Modifier.fillMaxSize()) {
            drawRect(brush = shaderBrush, alpha = tileAlpha)
        }
        content()
    }
}

private fun drawDoodleTile(c: Canvas, p: Paint) {
    fun withTransform(x: Float, y: Float, deg: Float, scale: Float = 1f, block: () -> Unit) {
        c.save()
        c.translate(x, y)
        c.rotate(deg)
        c.scale(scale, scale)
        block()
        c.restore()
    }

    // 1. Speech bubble + lines
    withTransform(46f, 44f, -14f, 0.95f) {
        c.drawRoundRect(RectF(-18f, -13f, 18f, 11f), 7f, 7f, p)
        c.drawLine(-9f, -3f, 9f, -3f, p)
        c.drawLine(-9f, 3f, 4f, 3f, p)
    }

    // 2. Ringed planet
    withTransform(138f, 36f, -20f, 1.0f) {
        c.drawCircle(0f, 0f, 11f, p)
        c.drawArc(RectF(-22f, -6f, 22f, 6f), -25f, 230f, false, p)
    }

    // 3. Crescent moon + star
    withTransform(232f, 42f, 18f, 0.95f) {
        val path = Path().apply {
            addArc(RectF(-13f, -13f, 13f, 13f), -70f, 220f)
            arcTo(RectF(-5f, -11f, 15f, 11f), 150f, -180f, false)
        }
        c.drawPath(path, p)
        c.drawPoint(14f, -8f, p)
    }

    // 4. Coffee mug
    withTransform(316f, 48f, -10f, 0.95f) {
        c.drawRoundRect(RectF(-12f, -9f, 10f, 13f), 4f, 4f, p)
        c.drawArc(RectF(6f, -4f, 17f, 7f), -90f, 180f, false, p)
        c.drawLine(-4f, -14f, -2f, -19f, p)
        c.drawLine(3f, -14f, 5f, -19f, p)
    }

    // 5. Camera
    withTransform(38f, 134f, 12f, 0.95f) {
        c.drawRoundRect(RectF(-17f, -11f, 17f, 13f), 4f, 4f, p)
        c.drawRoundRect(RectF(-7f, -16f, 7f, -11f), 2f, 2f, p)
        c.drawCircle(0f, 1f, 6f, p)
        c.drawPoint(11f, -5f, p)
    }

    // 6. Paper plane
    withTransform(128f, 118f, -24f, 1.0f) {
        val plane = Path().apply {
            moveTo(-16f, 2f)
            lineTo(17f, -10f)
            lineTo(4f, 14f)
            lineTo(-3f, 4f)
            close()
        }
        c.drawPath(plane, p)
        c.drawLine(-3f, 4f, 17f, -10f, p)
    }

    // 7. Phone handset
    withTransform(214f, 128f, 22f, 0.95f) {
        val phone = Path().apply {
            moveTo(-10f, -12f)
            quadTo(-14f, 0f, -6f, 10f)
            quadTo(4f, 16f, 12f, 12f)
        }
        c.drawPath(phone, p)
        c.drawCircle(-10f, -12f, 3.5f, p)
        c.drawCircle(12f, 12f, 3.5f, p)
    }

    // 8. Smiley face
    withTransform(306f, 132f, -8f, 1.0f) {
        c.drawCircle(0f, 0f, 14f, p)
        c.drawCircle(-5f, -4f, 1.4f, p)
        c.drawCircle(5f, -4f, 1.4f, p)
        c.drawArc(RectF(-7f, -2f, 7f, 8f), 15f, 150f, false, p)
    }

    // 9. Music notes
    withTransform(58f, 224f, -12f, 0.95f) {
        c.drawCircle(-8f, 10f, 4.5f, p)
        c.drawCircle(9f, 7f, 4.5f, p)
        c.drawLine(-4f, 10f, -4f, -10f, p)
        c.drawLine(13f, 7f, 13f, -13f, p)
        c.drawLine(-4f, -10f, 13f, -13f, p)
    }

    // 10. Heart
    withTransform(150f, 214f, 16f, 0.95f) {
        val heart = Path().apply {
            moveTo(0f, 13f)
            cubicTo(-16f, 3f, -16f, -13f, 0f, -5f)
            cubicTo(16f, -13f, 16f, 3f, 0f, 13f)
        }
        c.drawPath(heart, p)
    }

    // 11. Envelope
    withTransform(246f, 218f, -14f, 0.95f) {
        c.drawRoundRect(RectF(-17f, -11f, 17f, 11f), 3f, 3f, p)
        val flap = Path().apply {
            moveTo(-16f, -9f)
            lineTo(0f, 3f)
            lineTo(16f, -9f)
        }
        c.drawPath(flap, p)
    }

    // 12. Microphone
    withTransform(326f, 228f, 10f, 0.95f) {
        c.drawRoundRect(RectF(-6f, -14f, 6f, 4f), 6f, 6f, p)
        c.drawArc(RectF(-11f, -6f, 11f, 10f), 0f, 180f, false, p)
        c.drawLine(0f, 10f, 0f, 16f, p)
        c.drawLine(-6f, 16f, 6f, 16f, p)
    }

    // 13. Clock
    withTransform(48f, 314f, 8f, 0.95f) {
        c.drawCircle(0f, 0f, 14f, p)
        c.drawLine(0f, 0f, 0f, -8f, p)
        c.drawLine(0f, 0f, 6f, 3f, p)
    }

    // 14. 5-point Star
    withTransform(142f, 312f, 14f, 1.0f) {
        val star = Path()
        for (i in 0 until 10) {
            val r = if (i % 2 == 0) 14f else 6f
            val angle = (-90.0 + i * 36.0) * PI / 180.0
            val sx = (r * cos(angle)).toFloat()
            val sy = (r * sin(angle)).toFloat()
            if (i == 0) star.moveTo(sx, sy) else star.lineTo(sx, sy)
        }
        star.close()
        c.drawPath(star, p)
    }

    // 15. Padlock
    withTransform(234f, 316f, -10f, 0.95f) {
        c.drawRoundRect(RectF(-11f, -3f, 11f, 13f), 4f, 4f, p)
        c.drawArc(RectF(-7f, -14f, 7f, 1f), 180f, 180f, false, p)
        c.drawCircle(0f, 5f, 2f, p)
    }

    // 16. Sparkle / Four-point star
    withTransform(318f, 314f, 20f, 0.95f) {
        val sp = Path().apply {
            moveTo(0f, -13f)
            quadTo(0f, 0f, 13f, 0f)
            quadTo(0f, 0f, 0f, 13f)
            quadTo(0f, 0f, -13f, 0f)
            quadTo(0f, 0f, 0f, -13f)
        }
        c.drawPath(sp, p)
    }
}

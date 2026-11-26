package com.wa.beastclient.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Custom WhatsApp bubble shape supporting a compact 6.dp curved tail on the leading cluster item
 * and rounder 16.dp corners matching Official WhatsApp proportions.
 */
class WhatsAppBubbleShape(
    private val isFromMe: Boolean,
    private val showTail: Boolean,
    private val cornerRadiusDp: Int = 15
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val maxR = minOf(size.width, size.height) / 2f
        val r = minOf(with(density) { cornerRadiusDp.coerceIn(4, 24).dp.toPx() }, maxR)
        if (!showTail) {
            return Outline.Rounded(
                RoundRect(
                    rect = Rect(0f, 0f, size.width, size.height),
                    cornerRadius = CornerRadius(r)
                )
            )
        }

        val tailW = with(density) { 5.5.dp.toPx() }
        val tailRise = with(density) { 11.dp.toPx() }.coerceAtMost(size.height * 0.55f)
        val w = size.width
        val h = size.height

        val path = Path().apply {
            if (isFromMe) {
                val bodyRight = (w - tailW).coerceAtLeast(r * 1.5f)
                moveTo(r, 0f)
                lineTo(bodyRight - r, 0f)
                quadraticTo(bodyRight, 0f, bodyRight, r)
                lineTo(bodyRight, h - tailRise)
                quadraticTo(bodyRight, h - 3f, w - 1f, h - 1.5f)
                quadraticTo(w + 0.5f, h, w - 3.5f, h)
                lineTo(r, h)
                quadraticTo(0f, h, 0f, h - r)
                lineTo(0f, r)
                quadraticTo(0f, 0f, r, 0f)
                close()
            } else {
                val bodyLeft = tailW.coerceAtMost(w * 0.25f)
                moveTo(bodyLeft + r, 0f)
                lineTo(w - r, 0f)
                quadraticTo(w, 0f, w, r)
                lineTo(w, h - r)
                quadraticTo(w, h, w - r, h)
                lineTo(3.5f, h)
                quadraticTo(-0.5f, h, 1f, h - 1.5f)
                quadraticTo(bodyLeft, h - 3f, bodyLeft, h - tailRise)
                lineTo(bodyLeft, r)
                quadraticTo(bodyLeft, 0f, bodyLeft + r, 0f)
                close()
            }
        }
        return Outline.Generic(path)
    }
}

val WhatsAppClusterInnerShape = RoundedCornerShape(15.dp)

fun whatsAppClusterInnerShape(cornerRadiusDp: Int): RoundedCornerShape =
    RoundedCornerShape(cornerRadiusDp.coerceIn(4, 24).dp)

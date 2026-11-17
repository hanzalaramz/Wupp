package com.wa.beastclient.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.ColorMatrixColorFilter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.roundToInt

data class NormPoint(val x: Float, val y: Float)

data class DoodleStroke(
    val points: List<NormPoint>,
    val color: Color,
    val normWidth: Float = 0.012f
)

data class TextOverlayItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val color: Color,
    var normX: Float,
    var normY: Float,
    val normSize: Float = 0.06f
)

data class StickerOverlayItem(
    val id: String = UUID.randomUUID().toString(),
    val emoji: String,
    var normX: Float,
    var normY: Float,
    val normSize: Float = 0.11f
)

enum class PhotoFilterPreset(val label: String, val matrixArray: FloatArray?) {
    NORMAL("Normal", null),
    VIVID(
        "Vivid",
        floatArrayOf(
            1.25f, 0f, 0f, 0f, 0f,
            0f, 1.25f, 0f, 0f, 0f,
            0f, 0f, 1.25f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
    ),
    BW(
        "B&W",
        floatArrayOf(
            0.33f, 0.59f, 0.11f, 0f, 0f,
            0.33f, 0.59f, 0.11f, 0f, 0f,
            0.33f, 0.59f, 0.11f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
    ),
    COOL(
        "Cool",
        floatArrayOf(
            0.9f, 0f, 0f, 0f, 0f,
            0f, 1.0f, 0f, 0f, 0f,
            0f, 0f, 1.3f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
    ),
    WARM(
        "Warm",
        floatArrayOf(
            1.25f, 0f, 0f, 0f, 0f,
            0f, 1.08f, 0f, 0f, 0f,
            0f, 0f, 0.82f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
    ),
    VINTAGE(
        "Vintage",
        floatArrayOf(
            0.393f, 0.769f, 0.189f, 0f, 0f,
            0.349f, 0.686f, 0.168f, 0f, 0f,
            0.272f, 0.534f, 0.131f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
    )
}

val WHATSAPP_PALETTE_COLORS = listOf(
    Color(0xFF25D366), // WhatsApp Green
    Color(0xFFFFCC00), // Yellow
    Color(0xFFFF9500), // Orange
    Color(0xFFFF3B30), // Red
    Color(0xFFFF2D55), // Pink
    Color(0xFFAF52DE), // Purple
    Color(0xFF007AFF), // Blue
    Color(0xFF5AC8FA), // Cyan
    Color(0xFFFFFFFF), // White
    Color(0xFF1F2C34)  // Dark
)

val POPULAR_STICKER_EMOJIS = listOf(
    "❤️", "😍", "😂", "🔥", "👍", "👏", "🎉", "💯",
    "✨", "😎", "🥳", "🥺", "🥰", "🙌", "🤩", "🤔",
    "👀", "💖", "🌟", "🎈", "🎂", "🍕", "☕", "🍺",
    "⚽", "🚀", "🌸", "🐶", "🐱", "🏖️", "🚗", "💪"
)

sealed interface UndoAction {
    data class AddStroke(val stroke: DoodleStroke) : UndoAction
    data class AddText(val item: TextOverlayItem) : UndoAction
    data class AddSticker(val item: StickerOverlayItem) : UndoAction
}

fun normalizeRotationAngle(angle: Int): Int = (angle % 360 + 360) % 360

fun applyUndoAction(
    action: UndoAction,
    strokes: MutableList<DoodleStroke>,
    textOverlays: MutableList<TextOverlayItem>,
    stickerOverlays: MutableList<StickerOverlayItem>
) {
    when (action) {
        is UndoAction.AddStroke -> strokes.remove(action.stroke)
        is UndoAction.AddText -> textOverlays.remove(action.item)
        is UndoAction.AddSticker -> stickerOverlays.remove(action.item)
    }
}

/**
 * Interactive canvas layer supporting real-time photo rotation, color matrix filters,
 * smooth finger doodle drawing, and draggable text & sticker overlays.
 */
@Composable
fun PhotoEditorCanvas(
    bitmap: ImageBitmap,
    rotationAngle: Int,
    filter: PhotoFilterPreset,
    isPenActive: Boolean,
    activeColor: Color,
    strokes: List<DoodleStroke>,
    onAddStroke: (DoodleStroke) -> Unit,
    textOverlays: List<TextOverlayItem>,
    onUpdateTextPosition: (TextOverlayItem, Float, Float) -> Unit,
    onDeleteText: (TextOverlayItem) -> Unit,
    stickerOverlays: List<StickerOverlayItem>,
    onUpdateStickerPosition: (StickerOverlayItem, Float, Float) -> Unit,
    onDeleteSticker: (StickerOverlayItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var canvasWidth by remember { mutableStateOf(1f) }
    var canvasHeight by remember { mutableStateOf(1f) }
    val currentPoints = remember { mutableStateListOf<NormPoint>() }

    val colorFilter = remember(filter) {
        filter.matrixArray?.let { ColorFilter.colorMatrix(ColorMatrix(it)) }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                canvasWidth = coordinates.size.width.toFloat().coerceAtLeast(1f)
                canvasHeight = coordinates.size.height.toFloat().coerceAtLeast(1f)
            },
        contentAlignment = Alignment.Center
    ) {
        // 1. Base Image with rotation and filter
        androidx.compose.foundation.Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            colorFilter = colorFilter,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationZ = rotationAngle.toFloat()
                }
        )

        // 2. Doodle Strokes Drawing Layer
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (isPenActive) {
                        Modifier.pointerInput(canvasWidth, canvasHeight, activeColor) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    currentPoints.clear()
                                    currentPoints.add(NormPoint(offset.x / canvasWidth, offset.y / canvasHeight))
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    currentPoints.add(NormPoint(change.position.x / canvasWidth, change.position.y / canvasHeight))
                                },
                                onDragEnd = {
                                    if (currentPoints.isNotEmpty()) {
                                        onAddStroke(
                                            DoodleStroke(
                                                points = currentPoints.toList(),
                                                color = activeColor
                                            )
                                        )
                                        currentPoints.clear()
                                    }
                                },
                                onDragCancel = {
                                    currentPoints.clear()
                                }
                            )
                        }
                    } else Modifier
                )
        ) {
            // Draw finalized strokes
            strokes.forEach { stroke ->
                if (stroke.points.size > 1) {
                    val path = Path().apply {
                        val first = stroke.points.first()
                        moveTo(first.x * size.width, first.y * size.height)
                        for (i in 1 until stroke.points.size) {
                            val pt = stroke.points[i]
                            lineTo(pt.x * size.width, pt.y * size.height)
                        }
                    }
                    drawPath(
                        path = path,
                        color = stroke.color,
                        style = Stroke(
                            width = stroke.normWidth * minOf(size.width, size.height),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
            }

            // Draw active stroke currently being drawn
            if (currentPoints.size > 1) {
                val path = Path().apply {
                    val first = currentPoints.first()
                    moveTo(first.x * size.width, first.y * size.height)
                    for (i in 1 until currentPoints.size) {
                        val pt = currentPoints[i]
                        lineTo(pt.x * size.width, pt.y * size.height)
                    }
                }
                drawPath(
                    path = path,
                    color = activeColor,
                    style = Stroke(
                        width = 0.012f * minOf(size.width, size.height),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }

        // 3. Draggable Sticker Overlays
        stickerOverlays.forEach { item ->
            key(item.id) {
                DraggableStickerItem(
                    item = item,
                    canvasWidth = canvasWidth,
                    canvasHeight = canvasHeight,
                    isPenActive = isPenActive,
                    onUpdatePosition = onUpdateStickerPosition
                )
            }
        }

        // 4. Draggable Text Overlays
        textOverlays.forEach { item ->
            key(item.id) {
                DraggableTextItem(
                    item = item,
                    canvasWidth = canvasWidth,
                    canvasHeight = canvasHeight,
                    isPenActive = isPenActive,
                    onUpdatePosition = onUpdateTextPosition
                )
            }
        }
    }
}

@Composable
private fun DraggableStickerItem(
    item: StickerOverlayItem,
    canvasWidth: Float,
    canvasHeight: Float,
    isPenActive: Boolean,
    onUpdatePosition: (StickerOverlayItem, Float, Float) -> Unit
) {
    var offsetX by remember(item.id) { mutableStateOf(item.normX * canvasWidth) }
    var offsetY by remember(item.id) { mutableStateOf(item.normY * canvasHeight) }

    Box(
        modifier = Modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .pointerInput(item.id, isPenActive) {
                if (!isPenActive) {
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            offsetX = (offsetX + dragAmount.x).coerceIn(0f, canvasWidth - 48.dp.toPx())
                            offsetY = (offsetY + dragAmount.y).coerceIn(0f, canvasHeight - 48.dp.toPx())
                            onUpdatePosition(item, offsetX / canvasWidth, offsetY / canvasHeight)
                        }
                    )
                }
            }
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = item.emoji,
            fontSize = 44.sp
        )
    }
}

@Composable
private fun DraggableTextItem(
    item: TextOverlayItem,
    canvasWidth: Float,
    canvasHeight: Float,
    isPenActive: Boolean,
    onUpdatePosition: (TextOverlayItem, Float, Float) -> Unit
) {
    var offsetX by remember(item.id) { mutableStateOf(item.normX * canvasWidth) }
    var offsetY by remember(item.id) { mutableStateOf(item.normY * canvasHeight) }

    Box(
        modifier = Modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .pointerInput(item.id, isPenActive) {
                if (!isPenActive) {
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            offsetX = (offsetX + dragAmount.x).coerceIn(0f, canvasWidth - 100.dp.toPx())
                            offsetY = (offsetY + dragAmount.y).coerceIn(0f, canvasHeight - 40.dp.toPx())
                            onUpdatePosition(item, offsetX / canvasWidth, offsetY / canvasHeight)
                        }
                    )
                }
            }
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = item.text,
            color = item.color,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Vertical WhatsApp-style color slider bar on the right side.
 */
@Composable
fun PhotoEditorColorPalette(
    selectedColor: Color,
    onColorSelected: (Color) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Black.copy(alpha = 0.65f))
            .padding(vertical = 8.dp, horizontal = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        WHATSAPP_PALETTE_COLORS.forEach { color ->
            val isSelected = color == selectedColor
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(
                        width = if (isSelected) 2.5.dp else 1.dp,
                        color = if (isSelected) Color.White else Color.Black.copy(alpha = 0.4f),
                        shape = CircleShape
                    )
                    .clickable { onColorSelected(color) }
            )
        }
    }
}

/**
 * Horizontal filter carousel bar.
 */
@Composable
fun PhotoEditorFilterBar(
    selectedFilter: PhotoFilterPreset,
    onFilterSelected: (PhotoFilterPreset) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PhotoFilterPreset.entries.forEach { filter ->
            val isSelected = filter == selectedFilter
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (isSelected) Color(0xFF25D366).copy(alpha = 0.28f)
                        else Color.White.copy(alpha = 0.16f)
                    )
                    .border(
                        width = 1.2.dp,
                        color = if (isSelected) Color(0xFF25D366) else Color.White.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .clickable { onFilterSelected(filter) }
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Text(
                    text = filter.label,
                    color = if (isSelected) Color(0xFF25D366) else Color.White,
                    fontSize = 12.5.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

/**
 * Text Overlay Input Modal.
 */
@Composable
fun PhotoEditorTextInputDialog(
    initialText: String = "",
    initialColor: Color = Color.White,
    onDismiss: () -> Unit,
    onConfirm: (text: String, color: Color) -> Unit
) {
    var text by remember { mutableStateOf(initialText) }
    var selectedColor by remember { mutableStateOf(initialColor) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, "Cancel", tint = Color.White)
                    }
                    IconButton(
                        onClick = {
                            if (text.isNotBlank()) {
                                onConfirm(text.trim(), selectedColor)
                            }
                        },
                        enabled = text.isNotBlank()
                    ) {
                        Icon(
                            Icons.Rounded.Check,
                            "Done",
                            tint = if (text.isNotBlank()) Color(0xFF25D366) else Color.Gray
                        )
                    }
                }

                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = TextStyle(
                        color = selectedColor,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.Center) {
                            if (text.isEmpty()) {
                                Text(
                                    text = "Type text...",
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            inner()
                        }
                    }
                )

                // Color choices row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.Center
                ) {
                    WHATSAPP_PALETTE_COLORS.forEach { color ->
                        val isSel = color == selectedColor
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 6.dp)
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (isSel) 2.5.dp else 1.dp,
                                    color = if (isSel) Color.White else Color.Black.copy(alpha = 0.35f),
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = color }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Sticker / Emoji Picker Bottom Sheet.
 */
@Composable
fun PhotoEditorEmojiSheet(
    onDismiss: () -> Unit,
    onEmojiSelected: (String) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(Color(0xFF1F2C34))
                    .clickable(enabled = false) {}
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Add Sticker",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, "Close", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                LazyVerticalGrid(
                    columns = GridCells.Fixed(8),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(POPULAR_STICKER_EMOJIS) { emoji ->
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .clickable {
                                    onEmojiSelected(emoji)
                                    onDismiss()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = emoji, fontSize = 24.sp)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Render all photo edits (rotation, color filters, doodle strokes, text, and stickers)
 * into a high-quality JPEG file ready for sending.
 */
suspend fun renderEditedPhotoToFile(
    context: Context,
    sourceFile: File,
    rotationAngle: Int,
    filter: PhotoFilterPreset,
    strokes: List<DoodleStroke>,
    textOverlays: List<TextOverlayItem>,
    stickerOverlays: List<StickerOverlayItem>
): File = withContext(Dispatchers.IO) {
    val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(sourceFile.absolutePath, boundsOpts)
    val origW = boundsOpts.outWidth
    val origH = boundsOpts.outHeight
    if (origW <= 0 || origH <= 0) return@withContext sourceFile

    var sampleSize = 1
    val maxDim = maxOf(origW, origH)
    while (maxDim / (sampleSize * 2) >= 2048) {
        sampleSize *= 2
    }
    val decodeOpts = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inMutable = true
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    var baseBmp = BitmapFactory.decodeFile(sourceFile.absolutePath, decodeOpts) ?: return@withContext sourceFile

    // 1. Rotation
    val normAngle = normalizeRotationAngle(rotationAngle)
    if (normAngle != 0) {
        val matrix = Matrix().apply { postRotate(normAngle.toFloat()) }
        val rotated = Bitmap.createBitmap(baseBmp, 0, 0, baseBmp.width, baseBmp.height, matrix, true)
        if (rotated != baseBmp) {
            baseBmp.recycle()
            baseBmp = rotated
        }
    }

    val width = baseBmp.width
    val height = baseBmp.height
    val bmpW = width.toFloat()
    val bmpH = height.toFloat()

    val resultBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(resultBitmap)

    // 2. Base image with Color Matrix filter
    val imgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    filter.matrixArray?.let {
        imgPaint.colorFilter = ColorMatrixColorFilter(it)
    }
    canvas.drawBitmap(baseBmp, 0f, 0f, imgPaint)
    baseBmp.recycle()

    // 3. Doodle strokes
    strokes.forEach { stroke ->
        if (stroke.points.size > 1) {
            val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = stroke.color.toArgb()
                style = Paint.Style.STROKE
                strokeWidth = stroke.normWidth * minOf(bmpW, bmpH)
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            val path = android.graphics.Path()
            val first = stroke.points.first()
            path.moveTo(first.x * bmpW, first.y * bmpH)
            for (i in 1 until stroke.points.size) {
                val pt = stroke.points[i]
                path.lineTo(pt.x * bmpW, pt.y * bmpH)
            }
            canvas.drawPath(path, strokePaint)
        }
    }

    // 4. Text overlays
    textOverlays.forEach { textItem ->
        if (textItem.text.isNotBlank()) {
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textItem.color.toArgb()
                textSize = textItem.normSize * minOf(bmpW, bmpH)
                typeface = Typeface.DEFAULT_BOLD
                setShadowLayer(8f, 2f, 2f, android.graphics.Color.BLACK)
            }
            canvas.drawText(
                textItem.text,
                textItem.normX * bmpW,
                textItem.normY * bmpH + textPaint.textSize,
                textPaint
            )
        }
    }

    // 5. Sticker / Emoji overlays
    stickerOverlays.forEach { stickerItem ->
        if (stickerItem.emoji.isNotBlank()) {
            val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = stickerItem.normSize * minOf(bmpW, bmpH)
            }
            canvas.drawText(
                stickerItem.emoji,
                stickerItem.normX * bmpW,
                stickerItem.normY * bmpH + emojiPaint.textSize,
                emojiPaint
            )
        }
    }

    val outputFile = File(context.cacheDir, "wupp_outbound_${System.currentTimeMillis()}.jpg")
    FileOutputStream(outputFile).use { out ->
        resultBitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
    }
    resultBitmap.recycle()
    outputFile
}

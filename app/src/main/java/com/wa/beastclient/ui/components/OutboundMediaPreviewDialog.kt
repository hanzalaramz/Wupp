package com.wa.beastclient.ui.components

import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.CropRotate
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.wa.beastclient.R
import com.wa.beastclient.data.media.OutboundQualityMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class StagedOutboundMedia(
    val file: File,
    val mimeType: String,
    val isVideo: Boolean,
    val durationSec: Int,
    val fileSize: Long
)

/**
 * Full-screen AMOLED media pre-send editor:
 * - Left circular X close button; right uniform circular toolbar (Download, HD, Crop/Rotate, Stickers, Aa, Pen)
 * - Clean centered overlay status text when toggling View Once or HD quality
 * - Subtle floating Filters pill above the caption bar for photos
 * - Bottom dark caption capsule with integrated crisp-white (1) View-Once toggle on the right
 * - Recipient pill bottom-left + circular green Send FAB bottom-right
 */
@Composable
fun OutboundMediaPreviewDialog(
    stagedItems: List<StagedOutboundMedia>,
    recipientLabel: String,
    initialCaption: String,
    initialQualityMode: OutboundQualityMode,
    onDismiss: () -> Unit,
    onSendBatch: (items: List<Pair<StagedOutboundMedia, String>>, qualityMode: OutboundQualityMode, isViewOnce: Boolean) -> Unit
) {
    if (stagedItems.isEmpty()) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isRendering by remember { mutableStateOf(false) }

    val activeItems = remember(stagedItems) { stagedItems.toMutableStateList() }
    var selectedIndex by remember(stagedItems) { mutableIntStateOf(0) }
    val safeIndex = selectedIndex.coerceIn(0, (activeItems.size - 1).coerceAtLeast(0))
    val staged = activeItems.getOrElse(safeIndex) { stagedItems.first() }
    val captionsByPath = remember(stagedItems) {
        mutableStateMapOf<String, String>().apply {
            if (stagedItems.isNotEmpty() && initialCaption.isNotBlank()) {
                put(stagedItems.first().file.absolutePath, initialCaption)
            }
        }
    }
    val caption = captionsByPath[staged.file.absolutePath].orEmpty()
    var isViewOnce by remember(stagedItems) { mutableStateOf(false) }
    var qualityMode by remember(stagedItems) {
        mutableStateOf(
            if (staged.isVideo && initialQualityMode == OutboundQualityMode.ORIGINAL) {
                OutboundQualityMode.HD
            } else {
                initialQualityMode
            }
        )
    }

    // Transient centered status notice (e.g. "Media set to view once", "HD quality enabled")
    var statusNoticeText by remember(staged.file.absolutePath) { mutableStateOf<String?>(null) }
    var statusNoticeSeq by remember(staged.file.absolutePath) { mutableIntStateOf(0) }
    LaunchedEffect(statusNoticeSeq) {
        if (statusNoticeSeq > 0) {
            delay(2_200L)
            statusNoticeText = null
        }
    }
    val showStatusNotice: (String) -> Unit = { msg ->
        statusNoticeText = msg
        statusNoticeSeq += 1
    }

    // Photo Editing States
    var rotationAngle by remember(staged.file.absolutePath) { mutableIntStateOf(0) }
    var activeFilter by remember(staged.file.absolutePath) { mutableStateOf(PhotoFilterPreset.NORMAL) }
    var isPenActive by remember(staged.file.absolutePath) { mutableStateOf(false) }
    var activeColor by remember(staged.file.absolutePath) { mutableStateOf(WHATSAPP_PALETTE_COLORS.first()) }
    val strokes = remember(staged.file.absolutePath) { mutableStateListOf<DoodleStroke>() }
    val textOverlays = remember(staged.file.absolutePath) { mutableStateListOf<TextOverlayItem>() }
    val stickerOverlays = remember(staged.file.absolutePath) { mutableStateListOf<StickerOverlayItem>() }
    val undoStack = remember(staged.file.absolutePath) { mutableStateListOf<UndoAction>() }

    var showFilterBar by remember(staged.file.absolutePath) { mutableStateOf(false) }
    var showTextInputDialog by remember(staged.file.absolutePath) { mutableStateOf(false) }
    var showEmojiSheet by remember(staged.file.absolutePath) { mutableStateOf(false) }

    val previewBitmap = remember(staged.file.absolutePath, staged.isVideo) {
        if (!staged.isVideo) {
            runCatching { BitmapFactory.decodeFile(staged.file.absolutePath)?.asImageBitmap() }.getOrNull()
        } else {
            null
        }
    }

    val player = remember(staged.file.absolutePath, staged.isVideo) {
        if (staged.isVideo) {
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(Uri.fromFile(staged.file)))
                repeatMode = Player.REPEAT_MODE_ONE
                prepare()
                playWhenReady = true
            }
        } else {
            null
        }
    }
    var isPlaying by remember(player) { mutableStateOf(true) }

    DisposableEffect(player) {
        if (player != null) {
            val listener = object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }
            }
            player.addListener(listener)
            onDispose {
                player.removeListener(listener)
                player.release()
            }
        } else {
            onDispose { }
        }
    }

    val hasPhotoEdits = !staged.isVideo && (
        rotationAngle % 360 != 0 ||
            activeFilter != PhotoFilterPreset.NORMAL ||
            strokes.isNotEmpty() ||
            textOverlays.isNotEmpty() ||
            stickerOverlays.isNotEmpty()
        )

    val handleSendAction: () -> Unit = {
        if (!isRendering && activeItems.isNotEmpty()) {
            if (hasPhotoEdits) {
                isRendering = true
                scope.launch {
                    val editedFile = runCatching {
                        renderEditedPhotoToFile(
                            context = context,
                            sourceFile = staged.file,
                            rotationAngle = rotationAngle,
                            filter = activeFilter,
                            strokes = strokes.toList(),
                            textOverlays = textOverlays.toList(),
                            stickerOverlays = stickerOverlays.toList()
                        )
                    }.getOrDefault(staged.file)
                    isRendering = false
                    val payload = activeItems.mapIndexed { idx, item ->
                        val itemCaption = captionsByPath[item.file.absolutePath].orEmpty().trim()
                        val finalItem = if (idx == safeIndex && editedFile != item.file) {
                            item.copy(file = editedFile, fileSize = editedFile.length())
                        } else {
                            item
                        }
                        finalItem to itemCaption
                    }
                    onSendBatch(payload, qualityMode, isViewOnce)
                }
            } else {
                val payload = activeItems.map { item ->
                    item to captionsByPath[item.file.absolutePath].orEmpty().trim()
                }
                onSendBatch(payload, qualityMode, isViewOnce)
            }
        }
    }

    val handleSaveCopy: () -> Unit = {
        if (!isRendering) {
            scope.launch {
                val targetFile = if (hasPhotoEdits) {
                    isRendering = true
                    val rendered = runCatching {
                        renderEditedPhotoToFile(
                            context = context,
                            sourceFile = staged.file,
                            rotationAngle = rotationAngle,
                            filter = activeFilter,
                            strokes = strokes.toList(),
                            textOverlays = textOverlays.toList(),
                            stickerOverlays = stickerOverlays.toList()
                        )
                    }.getOrDefault(staged.file)
                    isRendering = false
                    rendered
                } else {
                    staged.file
                }
                val saved = saveStagedPreviewToGallery(context, targetFile, staged.mimeType, staged.isVideo)
                showStatusNotice(if (saved) "Saved to gallery" else "Could not save copy")
            }
        }
    }

    val darkOrbBg = Color(0xFF14171A)
    val darkOrbBorder = Color.White.copy(alpha = 0.08f)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            // 1. Center Stage (Video Player or Interactive Photo Canvas)
            if (staged.isVideo && player != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 62.dp, bottom = 122.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            if (player.isPlaying) player.pause() else player.play()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AspectFitVideoSurface(
                        player = player,
                        file = staged.file,
                        modifier = Modifier.fillMaxSize()
                    )
                    AnimatedVisibility(
                        visible = !isPlaying,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(66.dp)
                                .clip(CircleShape)
                                .background(Color(0xB8101419))
                                .border(0.75.dp, Color.White.copy(alpha = 0.20f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = "Play preview",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                }
            } else if (previewBitmap != null) {
                PhotoEditorCanvas(
                    bitmap = previewBitmap,
                    rotationAngle = rotationAngle,
                    filter = activeFilter,
                    isPenActive = isPenActive,
                    activeColor = activeColor,
                    strokes = strokes,
                    onAddStroke = { stroke ->
                        strokes.add(stroke)
                        undoStack.add(UndoAction.AddStroke(stroke))
                    },
                    textOverlays = textOverlays,
                    onUpdateTextPosition = { item, x, y ->
                        item.normX = x
                        item.normY = y
                    },
                    onDeleteText = { item ->
                        textOverlays.remove(item)
                        undoStack.removeAll { it is UndoAction.AddText && it.item.id == item.id }
                    },
                    stickerOverlays = stickerOverlays,
                    onUpdateStickerPosition = { item, x, y ->
                        item.normX = x
                        item.normY = y
                    },
                    onDeleteSticker = { item ->
                        stickerOverlays.remove(item)
                        undoStack.removeAll { it is UndoAction.AddSticker && it.item.id == item.id }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 62.dp, bottom = 122.dp)
                )
            }

            // 2. Vertical Floating Color Palette (When Pen mode is active)
            if (isPenActive && !staged.isVideo) {
                PhotoEditorColorPalette(
                    selectedColor = activeColor,
                    onColorSelected = { activeColor = it },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 12.dp)
                )
            }

            // 3. Subtle Floating Filters Pill / Filter Bar above caption capsule (for photos)
            if (!staged.isVideo) {
                if (showFilterBar) {
                    PhotoEditorFilterBar(
                        selectedFilter = activeFilter,
                        onFilterSelected = { activeFilter = it },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 132.dp)
                    )
                } else {
                    val filterInteraction = remember { MutableInteractionSource() }
                    val filterActive = activeFilter != PhotoFilterPreset.NORMAL
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 128.dp)
                            .expressivePressScale(filterInteraction, pressedScale = 0.94f)
                            .clip(CircleShape)
                            .background(Color(0x99161A1E))
                            .border(
                                width = 0.65.dp,
                                color = if (filterActive) Color.White.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.14f),
                                shape = CircleShape
                            )
                            .clickable(
                                interactionSource = filterInteraction,
                                indication = null
                            ) { showFilterBar = true }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = "Filters",
                            tint = Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = if (filterActive) "Filter · ${activeFilter.label}" else "Filters",
                            color = Color.White,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // 4. Clean Centered Overlay Notice ("Media set to view once", "HD quality enabled")
            AnimatedVisibility(
                visible = statusNoticeText != null,
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(240)),
                modifier = Modifier.align(Alignment.Center)
            ) {
                statusNoticeText?.let { notice ->
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.42f))
                            .padding(horizontal = 16.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = notice,
                            color = Color.White.copy(alpha = 0.94f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            style = TextStyle(
                                shadow = Shadow(
                                    color = Color.Black.copy(alpha = 0.65f),
                                    offset = Offset(0f, 1.5f),
                                    blurRadius = 6f
                                )
                            )
                        )
                    }
                }
            }

            // 5. Top Action Toolbar: Left X + Right 6 Uniform Circular Orbs (Download, HD, Rotate, Stickers, Aa, Pen)
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.80f), Color.Transparent)
                        )
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PreviewGlassOrbButton(
                    onClick = onDismiss,
                    bgColor = darkOrbBg,
                    borderColor = darkOrbBorder
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier.size(21.dp)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    // Undo button (only appears when there are photo edits)
                    if (!staged.isVideo && undoStack.isNotEmpty()) {
                        PreviewGlassOrbButton(
                            onClick = {
                                val lastAction = undoStack.removeLastOrNull()
                                if (lastAction != null) {
                                    applyUndoAction(lastAction, strokes, textOverlays, stickerOverlays)
                                }
                            },
                            bgColor = darkOrbBg,
                            borderColor = darkOrbBorder
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Undo,
                                contentDescription = "Undo",
                                tint = Color.White,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }

                    // Download / Save copy button (matches ↓ icon in top toolbar)
                    PreviewGlassOrbButton(
                        onClick = handleSaveCopy,
                        bgColor = darkOrbBg,
                        borderColor = darkOrbBorder
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = "Save copy",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Monochrome Boxed HD / RAW / SD Quality Orb
                    val isHdActive = qualityMode != OutboundQualityMode.STANDARD
                    val qualityBadgeLabel = when {
                        staged.isVideo -> "HD"
                        qualityMode == OutboundQualityMode.ORIGINAL -> "RAW"
                        else -> "HD"
                    }
                    val hdColor = if (isHdActive) Color.White else Color.White.copy(alpha = 0.48f)
                    PreviewGlassOrbButton(
                        onClick = {
                            if (!staged.isVideo) {
                                qualityMode = when (qualityMode) {
                                    OutboundQualityMode.STANDARD -> OutboundQualityMode.HD
                                    OutboundQualityMode.HD -> if (isViewOnce) OutboundQualityMode.STANDARD else OutboundQualityMode.ORIGINAL
                                    OutboundQualityMode.ORIGINAL,
                                    OutboundQualityMode.STICKER,
                                    OutboundQualityMode.GIF -> OutboundQualityMode.STANDARD
                                }
                                showStatusNotice(
                                    when (qualityMode) {
                                        OutboundQualityMode.HD -> "Photo set to HD quality"
                                        OutboundQualityMode.ORIGINAL -> "Photo set to original quality"
                                        else -> "Photo set to standard quality"
                                    }
                                )
                            }
                        },
                        enabled = !staged.isVideo,
                        bgColor = darkOrbBg,
                        borderColor = if (isHdActive) Color.White.copy(alpha = 0.24f) else darkOrbBorder
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .border(
                                    width = 1.25.dp,
                                    color = hdColor,
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 3.5.dp, vertical = 1.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = qualityBadgeLabel,
                                color = hdColor,
                                fontSize = if (qualityBadgeLabel.length > 2) 8.sp else 9.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (-0.2).sp
                            )
                        }
                    }

                    if (!staged.isVideo) {
                        // Crop / Rotate 90°
                        val isRotated = rotationAngle % 360 != 0
                        PreviewGlassOrbButton(
                            onClick = { rotationAngle = (rotationAngle + 90) % 360 },
                            bgColor = darkOrbBg,
                            borderColor = if (isRotated) Color.White.copy(alpha = 0.28f) else darkOrbBorder
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CropRotate,
                                contentDescription = "Crop and rotate",
                                tint = Color.White,
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        // Stickers / Emoji Tool
                        val hasStickers = stickerOverlays.isNotEmpty()
                        PreviewGlassOrbButton(
                            onClick = { showEmojiSheet = true },
                            bgColor = darkOrbBg,
                            borderColor = if (hasStickers) Color.White.copy(alpha = 0.28f) else darkOrbBorder
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.EmojiEmotions,
                                contentDescription = "Stickers",
                                tint = Color.White,
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        // Text Overlay Tool (Aa)
                        val hasText = textOverlays.isNotEmpty()
                        PreviewGlassOrbButton(
                            onClick = { showTextInputDialog = true },
                            bgColor = darkOrbBg,
                            borderColor = if (hasText) Color.White.copy(alpha = 0.28f) else darkOrbBorder
                        ) {
                            Text(
                                text = "Aa",
                                color = Color.White,
                                fontSize = 15.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = (-0.3).sp
                            )
                        }

                        // Pen / Doodle Drawing Tool
                        PreviewGlassOrbButton(
                            onClick = {
                                isPenActive = !isPenActive
                                if (isPenActive) showFilterBar = false
                            },
                            bgColor = if (isPenActive) Color.White else darkOrbBg,
                            borderColor = if (isPenActive) Color.White else darkOrbBorder
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Edit,
                                contentDescription = "Draw",
                                tint = if (isPenActive) Color.Black else Color.White,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }
            }

            // 6. Bottom 2-Row Controls:
            //    Row 1: Full-width dark AMOLED caption capsule with crisp white (1) View-Once button inside right edge
            //    Row 2: Subtle dark recipient pill on left + circular green Send button on right
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.92f))
                        )
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Row 1: Caption capsule + integrated (1) View Once button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp, max = 112.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(Color(0xFF121518))
                        .border(
                            width = 0.6.dp,
                            color = Color.White.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(26.dp)
                        )
                        .padding(start = 14.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Collections,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.42f),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (caption.isEmpty()) {
                            Text(
                                text = stringResource(R.string.chat_media_add_caption),
                                color = Color(0xFFA0A8B0),
                                fontSize = 15.5.sp
                            )
                        }
                        BasicTextField(
                            value = caption,
                            onValueChange = { captionsByPath[staged.file.absolutePath] = it },
                            textStyle = TextStyle(
                                color = Color.White,
                                fontSize = 15.5.sp,
                                lineHeight = 20.sp
                            ),
                            cursorBrush = SolidColor(Color(0xFF25D366)),
                            maxLines = 4,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))

                    // Integrated View-Once (1) button inside right end of caption capsule
                    val voInteraction = remember { MutableInteractionSource() }
                    val voBg by animateColorAsState(
                        targetValue = if (isViewOnce) Color.White else Color.Transparent,
                        animationSpec = tween(160),
                        label = "view_once_bg"
                    )
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .expressivePressScale(voInteraction, pressedScale = 0.88f)
                            .clip(CircleShape)
                            .background(voBg)
                            .clickable(
                                interactionSource = voInteraction,
                                indication = null
                            ) {
                                isViewOnce = !isViewOnce
                                if (isViewOnce && qualityMode == OutboundQualityMode.ORIGINAL) {
                                    qualityMode = OutboundQualityMode.HD
                                }
                                showStatusNotice(
                                    if (isViewOnce) "Media set to view once" else "View once turned off"
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isViewOnce) {
                            Text(
                                text = "1",
                                color = Color(0xFF0A0D10),
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        } else {
                            Icon(
                                painter = painterResource(R.drawable.vec_ic_view_once),
                                contentDescription = "View once",
                                tint = Color.White.copy(alpha = 0.86f),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                if (activeItems.size > 1) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp)
                    ) {
                        itemsIndexed(activeItems, key = { idx, item -> "${item.file.absolutePath}_$idx" }) { idx, item ->
                            val isSelected = idx == safeIndex
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .border(
                                        width = if (isSelected) 2.dp else 0.75.dp,
                                        color = if (isSelected) Color(0xFF25D366) else Color.White.copy(alpha = 0.25f),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable { selectedIndex = idx }
                            ) {
                                AsyncImage(
                                    model = item.file,
                                    contentDescription = "Selected media ${idx + 1}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                if (isSelected && activeItems.size > 1) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(2.dp)
                                            .size(18.dp)
                                            .clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.72f))
                                            .clickable {
                                                activeItems.removeAt(idx)
                                                selectedIndex = selectedIndex.coerceIn(0, (activeItems.size - 1).coerceAtLeast(0))
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = "Remove item",
                                            tint = Color.White,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Row 2: Recipient Pill (left) + Circular Vibrant Green Send Button (right)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (recipientLabel.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color(0xFF14171A))
                                .padding(horizontal = 15.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = recipientLabel,
                                color = Color(0xFF8C969E),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    val sendInteraction = remember { MutableInteractionSource() }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (activeItems.size > 1) {
                            Text(
                                text = "${activeItems.size} items",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .expressivePressScale(sendInteraction, pressedScale = 0.90f)
                                .clip(CircleShape)
                                .background(Color(0xFF25D366))
                                .clickable(
                                    interactionSource = sendInteraction,
                                    indication = null
                                ) { handleSendAction() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Send,
                                contentDescription = "Send",
                                tint = Color(0xFF07130C),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }

            // 7. Text Overlay Input Modal
            if (showTextInputDialog) {
                PhotoEditorTextInputDialog(
                    onDismiss = { showTextInputDialog = false },
                    onConfirm = { txt, col ->
                        val item = TextOverlayItem(text = txt, color = col, normX = 0.25f, normY = 0.45f)
                        textOverlays.add(item)
                        undoStack.add(UndoAction.AddText(item))
                        showTextInputDialog = false
                    }
                )
            }

            // 8. Sticker / Emoji Picker Sheet
            if (showEmojiSheet) {
                PhotoEditorEmojiSheet(
                    onDismiss = { showEmojiSheet = false },
                    onEmojiSelected = { emoji ->
                        val item = StickerOverlayItem(emoji = emoji, normX = 0.45f, normY = 0.45f)
                        stickerOverlays.add(item)
                        undoStack.add(UndoAction.AddSticker(item))
                    }
                )
            }

            // 9. Background Rendering Indicator
            if (isRendering) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.68f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFF25D366),
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
        }
    }
}

private suspend fun saveStagedPreviewToGallery(
    context: Context,
    file: File,
    mimeType: String,
    isVideo: Boolean
): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        val ext = file.extension.ifBlank { if (isVideo) "mp4" else "jpg" }
        val displayName = "WUPP_${System.currentTimeMillis()}.$ext"
        val resolvedMime = mimeType.ifBlank { if (isVideo) "video/mp4" else "image/jpeg" }
        val collection = if (isVideo) {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, resolvedMime)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    if (isVideo) "${Environment.DIRECTORY_MOVIES}/Wupp" else "${Environment.DIRECTORY_PICTURES}/Wupp"
                )
            }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(collection, values) ?: return@runCatching false
        resolver.openOutputStream(uri)?.use { out ->
            file.inputStream().use { input -> input.copyTo(out) }
        } ?: return@runCatching false
        true
    }.getOrDefault(false)
}

@Composable
private fun PreviewGlassOrbButton(
    onClick: () -> Unit,
    bgColor: Color,
    borderColor: Color,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(41.dp)
            .expressivePressScale(interactionSource, pressedScale = 0.88f)
            .clip(CircleShape)
            .background(bgColor)
            .border(0.6.dp, borderColor, CircleShape)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

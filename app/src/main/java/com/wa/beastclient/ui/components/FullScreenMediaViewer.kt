package com.wa.beastclient.ui.components

import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.wa.beastclient.data.db.MessageEntity
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FullScreenMediaViewer(
    msg: MessageEntity,
    senderDisplayName: String,
    onDismiss: () -> Unit,
    onSaveToGallery: () -> Unit,
    onReply: (() -> Unit)? = null,
    onQuickReaction: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val mediaFile = remember(msg.localMediaPath) {
        msg.localMediaPath?.takeIf { it.isNotBlank() }?.let { path ->
            File(path).takeIf { it.exists() && it.length() > 0L }
        }
    }
    val isVideo = msg.msgType.uppercase() == "VIDEO" || msg.mimeType.startsWith("video/", ignoreCase = true)
    val isAudio = msg.msgType.uppercase() == "PTT" || msg.msgType.uppercase() == "AUDIO" ||
        msg.mimeType.startsWith("audio/", ignoreCase = true)
    val bitmap = remember(mediaFile, isVideo, isAudio) {
        if (!isVideo && !isAudio && mediaFile != null) {
            runCatching { BitmapFactory.decodeFile(mediaFile.absolutePath)?.asImageBitmap() }.getOrNull()
        } else {
            null
        }
    }

    var showChrome by remember { mutableStateOf(true) }
    var showOverflow by remember { mutableStateOf(false) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    val formattedTime = remember(msg.timestamp) {
        if (msg.timestamp > 0L) {
            SimpleDateFormat("MMMM d, h:mm a", Locale.getDefault()).format(Date(msg.timestamp))
        } else {
            ""
        }
    }
    val headerTitle = if (msg.isFromMe) "You" else senderDisplayName.ifBlank { msg.senderName.ifBlank { "Contact" } }

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
        ) {
            if (isAudio && mediaFile != null) {
                AudioLightboxStage(
                    file = mediaFile,
                    durationSec = msg.durationSec,
                    onToggleChrome = { showChrome = !showChrome }
                )
            } else if (isVideo && mediaFile != null) {
                VideoLightboxStage(
                    file = mediaFile,
                    showChrome = showChrome,
                    onToggleChrome = { showChrome = !showChrome }
                )
            } else if (bitmap != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(msg.id) {
                            detectTapGestures(
                                onTap = { showChrome = !showChrome },
                                onDoubleTap = {
                                    if (scale > 1.2f) {
                                        scale = 1f
                                        offsetX = 0f
                                        offsetY = 0f
                                    } else {
                                        scale = 2.4f
                                    }
                                }
                            )
                        }
                        .pointerInput(msg.id) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val nextScale = (scale * zoom).coerceIn(1f, 4f)
                                scale = nextScale
                                if (nextScale > 1.02f) {
                                    val maxOffset = (nextScale - 1f) * 650f
                                    offsetX = (offsetX + pan.x).coerceIn(-maxOffset, maxOffset)
                                    offsetY = (offsetY + pan.y).coerceIn(-maxOffset, maxOffset)
                                } else {
                                    offsetX = 0f
                                    offsetY = 0f
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = msg.body,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offsetX,
                                translationY = offsetY
                            )
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { showChrome = !showChrome },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text(
                            text = if (msg.localMediaPath.isNullOrBlank()) "Downloading media..." else "Unable to open media file",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                        if (msg.body.isNotBlank()) {
                            Text(
                                text = msg.body,
                                color = Color(0xFFB8C2C7),
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            // Top bar overlay (matches original WhatsApp media lightbox)
            AnimatedVisibility(
                visible = showChrome,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.72f), Color.Transparent)
                            )
                        )
                        .statusBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = headerTitle,
                                color = Color.White,
                                fontSize = 16.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (msg.isViewOnce) {
                                Text(
                                    text = "View Once Vault",
                                    color = Color(0xFF25D366),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier
                                        .background(Color(0xFF0E2C1F), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        if (formattedTime.isNotBlank()) {
                            Text(
                                text = formattedTime,
                                color = Color(0xFFD1D7DB),
                                fontSize = 12.sp
                            )
                        }
                    }
                    if (mediaFile != null) {
                        IconButton(onClick = onSaveToGallery) {
                            Icon(
                                imageVector = Icons.Rounded.Download,
                                contentDescription = "Save to Gallery",
                                tint = Color.White
                            )
                        }
                    }
                    if (onReply != null) {
                        IconButton(
                            onClick = {
                                onReply()
                                onDismiss()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Reply,
                                contentDescription = "Reply",
                                tint = Color.White
                            )
                        }
                    }
                    Box {
                        IconButton(onClick = { showOverflow = true }) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = "More",
                                tint = Color.White
                            )
                        }
                        DropdownMenu(
                            expanded = showOverflow,
                            onDismissRequest = { showOverflow = false },
                            containerColor = Color(0xFF233138)
                        ) {
                            if (mediaFile != null) {
                                DropdownMenuItem(
                                    text = { Text("Save to gallery", color = Color.White) },
                                    onClick = {
                                        showOverflow = false
                                        onSaveToGallery()
                                    }
                                )
                            }
                            if (onReply != null) {
                                DropdownMenuItem(
                                    text = { Text("Reply", color = Color.White) },
                                    onClick = {
                                        showOverflow = false
                                        onReply()
                                        onDismiss()
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Bottom caption + Reply pill + Quick emoji reactions
            AnimatedVisibility(
                visible = showChrome,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))
                            )
                        )
                        .navigationBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (msg.body.isNotBlank()) {
                        Text(
                            text = msg.body,
                            color = Color.White,
                            fontSize = 14.5.sp,
                            lineHeight = 20.sp
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .clip(RoundedCornerShape(23.dp))
                                .background(Color(0xFF202C33).copy(alpha = 0.92f))
                                .clickable {
                                    if (onReply != null) {
                                        onReply()
                                        onDismiss()
                                    }
                                }
                                .padding(horizontal = 18.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = "Reply",
                                color = Color(0xFFD1D7DB),
                                fontSize = 15.sp
                            )
                        }
                        listOf("❤️", "🙏", "👍", "😊").forEach { emoji ->
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF202C33).copy(alpha = 0.92f))
                                    .clickable {
                                        if (onQuickReaction != null) {
                                            onQuickReaction(emoji)
                                        } else {
                                            Toast.makeText(context, "Reacted $emoji", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = emoji, fontSize = 18.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoLightboxStage(
    file: File,
    showChrome: Boolean = true,
    onToggleChrome: () -> Unit
) {
    val context = LocalContext.current
    val player = remember(file.absolutePath) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            prepare()
            playWhenReady = true
        }
    }
    var isPlaying by remember { mutableStateOf(true) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    isPlaying = false
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(player, isPlaying) {
        while (true) {
            durationMs = player.duration.coerceAtLeast(0L)
            positionMs = player.currentPosition.coerceAtLeast(0L)
            delay(200L)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggleChrome
            ),
        contentAlignment = Alignment.Center
    ) {
        AspectFitVideoSurface(
            player = player,
            file = file,
            modifier = Modifier.fillMaxSize()
        )
        AnimatedVisibility(
            visible = showChrome || !isPlaying,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .size(62.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.56f))
                    .clickable {
                        if (player.playbackState == Player.STATE_ENDED) {
                            player.seekTo(0L)
                            player.play()
                        } else if (player.isPlaying) {
                            player.pause()
                        } else {
                            player.play()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(34.dp)
                )
            }
        }
        AnimatedVisibility(
            visible = durationMs > 0L && (showChrome || !isPlaying),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 92.dp)
            ) {
                Slider(
                    value = (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f),
                    onValueChange = { frac ->
                        player.seekTo((frac * durationMs).toLong())
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF25D366),
                        activeTrackColor = Color(0xFF25D366),
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                    )
                )
            }
        }
    }
}

@Composable
private fun AudioLightboxStage(
    file: File,
    durationSec: Int,
    onToggleChrome: () -> Unit
) {
    val context = LocalContext.current
    val player = remember(file.absolutePath) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            prepare()
            playWhenReady = true
        }
    }
    var isPlaying by remember { mutableStateOf(true) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf((durationSec.coerceAtLeast(0) * 1000L)) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    isPlaying = false
                    positionMs = 0L
                    player.seekTo(0L)
                    player.playWhenReady = false
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(player) {
        while (true) {
            val dur = player.duration
            if (dur > 0L) durationMs = dur
            positionMs = player.currentPosition.coerceAtLeast(0L)
            delay(100L)
        }
    }

    fun formatSec(ms: Long): String {
        val total = (ms / 1000L).coerceAtLeast(0L)
        return "%d:%02d".format(total / 60L, total % 60L)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onToggleChrome),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(116.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1F2C34)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = "Voice message",
                    tint = Color(0xFF25D366),
                    modifier = Modifier.size(56.dp)
                )
            }
            Text(
                text = "Voice message · View once",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF25D366))
                    .clickable {
                        if (player.playbackState == Player.STATE_ENDED) {
                            player.seekTo(0L)
                            player.play()
                        } else if (player.isPlaying) {
                            player.pause()
                        } else {
                            player.play()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color(0xFF0B141A),
                    modifier = Modifier.size(36.dp)
                )
            }
            val safeDur = durationMs.coerceAtLeast(1L)
            Slider(
                value = (positionMs.toFloat() / safeDur.toFloat()).coerceIn(0f, 1f),
                onValueChange = { frac ->
                    player.seekTo((frac * safeDur).toLong())
                },
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF25D366),
                    activeTrackColor = Color(0xFF25D366),
                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                )
            )
            Text(
                text = "${formatSec(positionMs)} / ${formatSec(durationMs)}",
                color = Color(0xFFD1D7DB),
                fontSize = 13.sp
            )
        }
    }
}

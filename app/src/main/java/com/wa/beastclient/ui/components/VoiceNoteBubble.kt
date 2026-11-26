package com.wa.beastclient.ui.components

import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wa.beastclient.R
import com.wa.beastclient.data.db.MessageEntity
import com.wa.beastclient.data.media.VoiceNotePlaybackManager
import com.wa.beastclient.ui.screens.WhatsAppAvatar
import com.wa.beastclient.ui.theme.LocalWaColors
import com.wa.beastclient.ui.theme.LocalWaMonoFamily
import com.wa.beastclient.ui.theme.LocalWuppShowSeconds
import com.wa.beastclient.ui.theme.LocalWuppUse24Hour
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

private const val VOICE_WAVEFORM_BARS = 42

private fun resampleNormalizedWaveform(samples: List<Int>, targetBars: Int): List<Float> {
    if (samples.isEmpty()) return emptyList()
    val raw = FloatArray(targetBars) { idx ->
        val startPos = (idx.toFloat() / targetBars.toFloat()) * samples.size.toFloat()
        val endPos = ((idx + 1).toFloat() / targetBars.toFloat()) * samples.size.toFloat()
        val lo = startPos.toInt().coerceIn(0, samples.lastIndex)
        val hi = max(lo, (endPos - 0.001f).toInt().coerceIn(0, samples.lastIndex))
        var peak = 0
        for (i in lo..hi) {
            if (samples[i] > peak) peak = samples[i]
        }
        peak.toFloat()
    }
    val minV = raw.minOrNull() ?: 0f
    val maxV = raw.maxOrNull() ?: 1f
    val span = maxV - minV
    return if (span <= 6f) {
        emptyList()
    } else {
        raw.map { v ->
            val norm = ((v - minV) / span).coerceIn(0f, 1f)
            norm.toDouble().pow(0.82).toFloat()
        }
    }
}

private fun extractVbrPacketWaveform(path: String, targetBars: Int): List<Float>? {
    val file = File(path)
    if (!file.exists() || file.length() <= 64L) return null
    val extractor = MediaExtractor()
    return try {
        extractor.setDataSource(file.absolutePath)
        var audioTrack = -1
        var durationUs = 0L
        for (i in 0 until extractor.trackCount) {
            val fmt = extractor.getTrackFormat(i)
            if (fmt.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                audioTrack = i
                if (fmt.containsKey(MediaFormat.KEY_DURATION)) {
                    durationUs = fmt.getLong(MediaFormat.KEY_DURATION)
                }
                break
            }
        }
        if (audioTrack < 0) return null
        extractor.selectTrack(audioTrack)
        val packets = ArrayList<Pair<Long, Int>>(256)
        var maxTs = durationUs
        var count = 0
        while (count < 4000) {
            val sz = extractor.sampleSize
            if (sz <= 0L) break
            val ts = extractor.sampleTime.coerceAtLeast(0L)
            if (ts > maxTs) maxTs = ts
            packets.add(ts to sz.toInt())
            count++
            if (!extractor.advance()) break
        }
        if (packets.size < 4) return null
        val sums = FloatArray(targetBars)
        val hits = IntArray(targetBars)
        val useTime = maxTs > 0L
        val spanUs = maxTs.coerceAtLeast(1L)
        packets.forEachIndexed { idx, (ts, sz) ->
            val b = if (useTime) {
                ((ts.toDouble() / spanUs.toDouble()) * (targetBars - 0.001)).toInt().coerceIn(0, targetBars - 1)
            } else {
                ((idx.toDouble() / packets.size.toDouble()) * (targetBars - 0.001)).toInt().coerceIn(0, targetBars - 1)
            }
            sums[b] += sz.toFloat()
            hits[b]++
        }
        val avgs = FloatArray(targetBars) { i -> if (hits[i] > 0) sums[i] / hits[i] else 0f }
        val nonZero = avgs.filter { it > 0f }
        val minV = nonZero.minOrNull() ?: 0f
        val maxV = nonZero.maxOrNull() ?: 0f
        val span = maxV - minV
        if (span <= 2f) return null
        avgs.map { v ->
            if (v <= 0f) 0f
            else (((v - minV) / span).coerceIn(0f, 1f)).toDouble().pow(0.84).toFloat()
        }
    } catch (_: Throwable) {
        null
    } finally {
        runCatching { extractor.release() }
    }
}

private fun syntheticSpeechWaveform(seedKey: String, targetBars: Int): List<Float> {
    val seed = abs(seedKey.hashCode())
    val phaseA = (seed % 31) * 0.21f
    val phaseB = ((seed / 31) % 29) * 0.27f
    return List(targetBars) { i ->
        val t = i.toFloat() / (targetBars - 1).coerceAtLeast(1).toFloat()
        val edgeTaper = (min(i, targetBars - 1 - i).coerceAtMost(3) / 3f)
        val phrase = 0.5f + 0.5f * sin(t * 11.5f + phaseA)
        val syllable = 0.5f + 0.5f * sin(t * 31f + phaseB) * cos(t * 17f - phaseA)
        val raw = (phrase * 0.62f + syllable * 0.38f) * (0.18f + 0.82f * edgeTaper)
        if (i == 0 || i == targetBars - 1) 0.04f else raw.coerceIn(0.03f, 0.96f)
    }
}

/**
 * Interactive Voice Note (PTT) bubble with mirrored incoming/outgoing layout:
 * - Mirrored layout: outgoing places Avatar/Speed on the left; incoming places Play on the left & Avatar/Speed on the right
 * - Avatar smoothly morphs into the 1×/1.5×/2× speed pill during playback so the 42-bar waveform stays wide and uncluttered
 * - Tap & drag waveform scrubbing with active thumb dot + Ghost Audio receipt guard
 */
@Composable
fun VoiceNoteBubble(
    msg: MessageEntity,
    ghostPlayedActive: Boolean,
    onManualSendPlayedReceipt: () -> Unit,
    onRequestDownload: () -> Unit = {},
    isDownloadingMedia: Boolean = false,
    mediaDownloadError: String? = null,
    modifier: Modifier = Modifier,
    avatarPath: String? = null,
    onClickAvatar: (() -> Unit)? = null
) {
    val colors = LocalWaColors.current
    val mono = LocalWaMonoFamily.current
    val context = LocalContext.current

    val csvNormalized = remember(msg.waveformCsv) {
        val parsed = msg.waveformCsv.split(",").mapNotNull { it.trim().toIntOrNull() }
        val minV = parsed.minOrNull() ?: 0
        val maxV = parsed.maxOrNull() ?: 0
        val looksLikeFlatByteEntropy = parsed.size >= 8 && (maxV - minV) <= 14 && minV >= 32
        if (parsed.size >= 8 && !looksLikeFlatByteEntropy) {
            resampleNormalizedWaveform(parsed, VOICE_WAVEFORM_BARS).takeIf { it.isNotEmpty() }
        } else {
            null
        }
    }

    val mediaFileExists = remember(msg.localMediaPath) {
        !msg.localMediaPath.isNullOrBlank() &&
            File(msg.localMediaPath).let { it.exists() && it.length() > 0L }
    }

    val peaks by produceState(
        initialValue = csvNormalized ?: syntheticSpeechWaveform(msg.id, VOICE_WAVEFORM_BARS),
        key1 = csvNormalized,
        key2 = msg.localMediaPath,
        key3 = mediaFileExists
    ) {
        value = when {
            csvNormalized != null -> csvNormalized
            mediaFileExists && !msg.localMediaPath.isNullOrBlank() -> {
                withContext(Dispatchers.IO) {
                    extractVbrPacketWaveform(msg.localMediaPath, VOICE_WAVEFORM_BARS)
                } ?: syntheticSpeechWaveform(msg.id, VOICE_WAVEFORM_BARS)
            }
            else -> syntheticSpeechWaveform(msg.id, VOICE_WAVEFORM_BARS)
        }
    }

    val playbackState by VoiceNotePlaybackManager.state.collectAsState()
    val isThisActive = playbackState.activeMessageId == msg.id
    val isPlaying = isThisActive && playbackState.isPlaying

    var isScrubbing by remember(msg.id) { mutableStateOf(false) }
    var scrubFraction by remember(msg.id) { mutableFloatStateOf(0f) }
    var isDownloading by remember(msg.id) { mutableStateOf(false) }
    var autoPlayOnDownload by remember(msg.id) { mutableStateOf(false) }
    val speedIndex = playbackState.speedIndex

    val senderLabel = remember(msg.isFromMe, msg.senderName, msg.senderJid) {
        if (msg.isFromMe) "You"
        else msg.senderName.ifBlank { msg.senderJid.substringBefore("@") }
    }

    LaunchedEffect(mediaFileExists, autoPlayOnDownload) {
        if (mediaFileExists) {
            isDownloading = false
            if (autoPlayOnDownload && !msg.localMediaPath.isNullOrBlank()) {
                autoPlayOnDownload = false
                VoiceNotePlaybackManager.play(
                    context = context,
                    messageId = msg.id,
                    chatJid = msg.chatJid,
                    senderLabel = senderLabel,
                    filePath = msg.localMediaPath,
                    durationHintSec = msg.durationSec,
                    initialSeekFraction = scrubFraction
                )
                if (!msg.isFromMe && msg.deliveryStatus != "PLAYED" && !ghostPlayedActive) {
                    onManualSendPlayedReceipt()
                }
            }
        }
    }

    val effectiveDownloading = !mediaFileExists && (isDownloading || isDownloadingMedia)

    LaunchedEffect(mediaDownloadError, isDownloadingMedia) {
        if (!mediaDownloadError.isNullOrBlank() && !isDownloadingMedia) {
            isDownloading = false
            autoPlayOnDownload = false
        }
    }

    LaunchedEffect(isDownloading, mediaFileExists) {
        if (isDownloading && !mediaFileExists) {
            delay(15_000L)
            isDownloading = false
            autoPlayOnDownload = false
        }
    }

    val totalDurationSec = remember(msg.durationSec, isThisActive, playbackState.durationMs) {
        val playerSec = if (isThisActive && playbackState.durationMs > 0L) {
            (playbackState.durationMs / 1000L).toInt()
        } else 0
        max(1, if (playerSec > 0) playerSec else msg.durationSec)
    }

    val playbackFraction = if (isScrubbing) {
        scrubFraction
    } else if (isThisActive) {
        val durMs = playbackState.durationMs.takeIf { it > 0L } ?: (totalDurationSec * 1000L)
        VoiceNotePlaybackManager.computePlaybackFraction(playbackState.currentPositionMs, durMs)
    } else {
        scrubFraction
    }

    val currentSec = if (isScrubbing) {
        (totalDurationSec * scrubFraction).toInt()
    } else if (isThisActive) {
        (playbackState.currentPositionMs / 1000L).toInt()
    } else {
        (totalDurationSec * scrubFraction).toInt()
    }

    LaunchedEffect(isThisActive, playbackState.isEnded) {
        if (isThisActive && playbackState.isEnded) {
            scrubFraction = 0f
        }
    }

    val showSeconds = LocalWuppShowSeconds.current
    val bubbleSurfaceColor = if (msg.isFromMe) colors.outgoingBubble else colors.incomingBubble
    val micTint = if (msg.deliveryStatus == "PLAYED") colors.blueTick else colors.brightGreen
    val showSpeedPill = isPlaying || isScrubbing || playbackFraction > 0.005f

    val avatarOrSpeedSlot: @Composable () -> Unit = {
        Box(
            modifier = Modifier.size(48.dp),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = showSpeedPill,
                transitionSpec = {
                    (fadeIn(tween(150)) + scaleIn(initialScale = 0.85f, animationSpec = tween(150))) togetherWith
                        (fadeOut(tween(130)) + scaleOut(targetScale = 0.85f, animationSpec = tween(130)))
                },
                label = "ptt_avatar_speed_morph"
            ) { showingSpeed ->
                if (showingSpeed) {
                    val speedInteraction = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .expressivePressScale(speedInteraction, pressedScale = 0.90f)
                            .clip(CircleShape)
                            .background(if (colors.isDark) Color.Black.copy(alpha = 0.34f) else Color.Black.copy(alpha = 0.12f))
                            .border(0.6.dp, Color.White.copy(alpha = if (colors.isDark) 0.14f else 0.08f), CircleShape)
                            .height(27.dp)
                            .width(44.dp)
                            .clickable(
                                interactionSource = speedInteraction,
                                indication = null
                            ) {
                                VoiceNotePlaybackManager.cycleSpeed()
                            }
                            .semantics { contentDescription = "Playback speed. Tap to change." },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = when (speedIndex) {
                                1 -> "1.5×"
                                2 -> "2×"
                                else -> "1×"
                            },
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = mono,
                            color = colors.textPrimary
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .then(
                                if (onClickAvatar != null) {
                                    Modifier.clickable(onClick = onClickAvatar)
                                } else {
                                    Modifier
                                }
                            )
                    ) {
                        val initials = msg.senderName.ifBlank { msg.senderJid.substringBefore("@") }
                            .trim()
                            .take(1)
                            .uppercase()
                            .ifBlank { null }
                        WhatsAppAvatar(
                            jid = if (msg.isFromMe) "" else msg.senderJid,
                            avatarPath = avatarPath,
                            isGroup = false,
                            size = 46.dp,
                            initialChar = initials,
                            modifier = Modifier.align(Alignment.Center)
                        )
                        Box(
                            modifier = Modifier
                                .align(if (msg.isFromMe) Alignment.BottomEnd else Alignment.BottomStart)
                                .offset(x = if (msg.isFromMe) 2.dp else (-3).dp, y = 1.dp)
                                .size(19.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(R.drawable.msg_status_mic),
                                contentDescription = null,
                                colorFilter = ColorFilter.tint(bubbleSurfaceColor),
                                modifier = Modifier.size(18.dp)
                            )
                            Image(
                                painter = painterResource(R.drawable.msg_status_mic),
                                contentDescription = null,
                                colorFilter = ColorFilter.tint(micTint),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    val playButtonSlot: @Composable () -> Unit = {
        val playInteraction = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .size(36.dp)
                .expressivePressScale(playInteraction, pressedScale = 0.86f)
                .clip(CircleShape)
                .clickable(
                    interactionSource = playInteraction,
                    indication = null
                ) {
                    if (mediaFileExists && !msg.localMediaPath.isNullOrBlank()) {
                        VoiceNotePlaybackManager.togglePlayPause(
                            context = context,
                            messageId = msg.id,
                            chatJid = msg.chatJid,
                            senderLabel = senderLabel,
                            filePath = msg.localMediaPath,
                            durationHintSec = msg.durationSec,
                            initialSeekFraction = scrubFraction,
                            onStartedPlaying = {
                                if (!msg.isFromMe && msg.deliveryStatus != "PLAYED" && !ghostPlayedActive) {
                                    onManualSendPlayedReceipt()
                                }
                            }
                        )
                    } else {
                        isDownloading = true
                        autoPlayOnDownload = true
                        onRequestDownload()
                    }
                }
                .semantics {
                    contentDescription = when {
                        effectiveDownloading -> "Downloading voice message"
                        isPlaying -> "Pause voice message"
                        !mediaFileExists -> "Download voice message"
                        else -> "Play voice message"
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (effectiveDownloading) {
                @OptIn(ExperimentalMaterial3ExpressiveApi::class)
                LoadingIndicator(
                    modifier = Modifier.size(22.dp),
                    color = colors.textPrimary
                )
            } else {
                Icon(
                    imageVector = when {
                        isPlaying -> Icons.Rounded.Pause
                        !mediaFileExists -> Icons.Rounded.Download
                        else -> Icons.Rounded.PlayArrow
                    },
                    contentDescription = null,
                    tint = if (msg.isFromMe && colors.isDark) {
                        Color.White.copy(alpha = 0.90f)
                    } else {
                        colors.textSecondary.copy(alpha = 0.95f)
                    },
                    modifier = Modifier.size(if (!mediaFileExists) 22.dp else 33.dp)
                )
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (msg.isFromMe) {
                avatarOrSpeedSlot()
                Spacer(modifier = Modifier.width(6.dp))
                playButtonSlot()
                Spacer(modifier = Modifier.width(5.dp))
            } else {
                playButtonSlot()
                Spacer(modifier = Modifier.width(5.dp))
            }

            // Wide 42-Bar Organic Speech Waveform + Scrubber Thumb Dot + Duration/Meta Row
            Column(modifier = Modifier.weight(1f)) {
                val activeBarColor = if (msg.deliveryStatus == "PLAYED" && !msg.isFromMe) {
                    colors.blueTick
                } else if (msg.isFromMe) {
                    Color.White.copy(alpha = 0.94f)
                } else {
                    colors.brightGreen
                }
                val inactiveBarColor = if (msg.isFromMe) {
                    Color.White.copy(alpha = 0.36f)
                } else if (colors.isDark) {
                    Color(0xFF6B747C)
                } else {
                    colors.textSecondary.copy(alpha = 0.38f)
                }
                val thumbColor = if (msg.deliveryStatus == "PLAYED" && !msg.isFromMe) {
                    colors.blueTick
                } else if (msg.isFromMe) {
                    colors.blueTick
                } else {
                    colors.brightGreen
                }

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(31.dp)
                        .semantics {
                            contentDescription = "Voice message playback position"
                            progressBarRangeInfo = ProgressBarRangeInfo(playbackFraction, 0f..1f)
                            setProgress { target ->
                                if (mediaFileExists) {
                                    val fraction = target.coerceIn(0f, 1f)
                                    scrubFraction = fraction
                                    if (isThisActive) {
                                        VoiceNotePlaybackManager.seekToFraction(msg.id, fraction)
                                    }
                                    true
                                } else false
                            }
                        }
                        .pointerInput(msg.id, isThisActive, peaks, totalDurationSec) {
                            detectTapGestures { offset ->
                                val frac = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                scrubFraction = frac
                                if (isThisActive) {
                                    VoiceNotePlaybackManager.seekToFraction(msg.id, frac)
                                }
                            }
                        }
                        .pointerInput(msg.id, isThisActive, peaks, totalDurationSec) {
                            detectHorizontalDragGestures(
                                onDragStart = { offset ->
                                    isScrubbing = true
                                    scrubFraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                },
                                onHorizontalDrag = { change, _ ->
                                    change.consume()
                                    scrubFraction = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                                },
                                onDragEnd = {
                                    if (isThisActive) {
                                        VoiceNotePlaybackManager.seekToFraction(msg.id, scrubFraction)
                                    }
                                    isScrubbing = false
                                },
                                onDragCancel = {
                                    isScrubbing = false
                                }
                            )
                        }
                ) {
                    val thumbRadius = (if (isScrubbing) 6.dp else 5.2.dp).toPx()
                    val padH = thumbRadius
                    val trackW = (size.width - padH * 2f).coerceAtLeast(1f)
                    val barCount = peaks.size.coerceAtLeast(1)
                    val slotW = trackW / barCount
                    val barW = (slotW * 0.52f).coerceIn(1.9.dp.toPx(), 2.6.dp.toPx())
                    val minBarH = 2.6.dp.toPx()
                    val maxBarH = (size.height * 0.84f).coerceAtLeast(minBarH)
                    val centerY = size.height / 2f

                    peaks.forEachIndexed { idx, normAmp ->
                        val ratio = (idx + 0.5f) / barCount.toFloat()
                        val clamped = normAmp.coerceIn(0f, 1f)
                        val barH = minBarH + clamped * (maxBarH - minBarH)
                        val x = padH + idx * slotW + (slotW - barW) / 2f
                        val active = ratio <= playbackFraction && (playbackFraction > 0f || isPlaying || isScrubbing)
                        drawRoundRect(
                            color = if (active) activeBarColor else inactiveBarColor,
                            topLeft = Offset(x, centerY - barH / 2f),
                            size = Size(barW, barH),
                            cornerRadius = CornerRadius(barW / 2f, barW / 2f)
                        )
                    }

                    // Active Scrubber Thumb Dot riding cleanly on the waveform
                    val thumbX = (padH + trackW * playbackFraction).coerceIn(padH, size.width - padH)
                    drawCircle(
                        color = bubbleSurfaceColor,
                        radius = thumbRadius + 1.dp.toPx(),
                        center = Offset(thumbX, centerY)
                    )
                    drawCircle(
                        color = thumbColor,
                        radius = thumbRadius,
                        center = Offset(thumbX, centerY)
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val shownSec = if (isPlaying || isScrubbing || currentSec > 0) currentSec else totalDurationSec
                    val durationText = "%d:%02d".format(shownSec / 60, shownSec % 60)
                    Text(
                        text = durationText,
                        fontSize = 12.5.sp,
                        color = if (isScrubbing) {
                            colors.brightGreen
                        } else if (msg.isFromMe && colors.isDark) {
                            Color.White.copy(alpha = 0.78f)
                        } else {
                            colors.textSecondary
                        },
                        fontWeight = if (isScrubbing) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    MessageMetaCluster(
                        timeText = formatMessageClock(
                            msg.timestamp,
                            includeSeconds = showSeconds,
                            use24Hour = LocalWuppUse24Hour.current
                        ),
                        isEdited = msg.isEdited,
                        isEphemeral = msg.isEphemeral,
                        isFromMe = msg.isFromMe,
                        deliveryStatus = msg.deliveryStatus
                    )
                }
            }

            if (!msg.isFromMe) {
                Spacer(modifier = Modifier.width(8.dp))
                avatarOrSpeedSlot()
            }
        }
    }
}

package com.wa.beastclient.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wa.beastclient.data.db.GroupReceiptEntity
import com.wa.beastclient.data.db.MessageEntity
import com.wa.beastclient.data.db.WaDao
import com.wa.beastclient.ui.theme.WaColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun MessageInfoSheet(
    msg: MessageEntity,
    isGroup: Boolean,
    groupReceipts: List<GroupReceiptEntity>,
    colors: WaColors,
    mono: FontFamily,
    showSeconds: Boolean,
    use24Hour: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    val dateClock = formatMessageDateTime(msg.timestamp, showSeconds = true, use24Hour = use24Hour)
    val rank = WaDao.deliveryRank(msg.deliveryStatus)
    val isVoiceOrAudio = msg.msgType.uppercase() in setOf("PTT", "AUDIO")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.elevatedBar
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = null,
                    tint = colors.accentGreen,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Message info",
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    fontSize = 18.sp
                )
            }

            // Message Summary Card
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = colors.appSurface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val senderLabel = if (msg.isFromMe) {
                            "You"
                        } else {
                            msg.senderName.ifBlank { msg.senderJid.substringBefore("@") }
                        }
                        Text(
                            text = senderLabel,
                            color = colors.accentGreen,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = msg.msgType.uppercase(),
                            color = colors.textSecondary,
                            fontFamily = mono,
                            fontSize = 11.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(colors.textSecondary.copy(alpha = 0.14f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    val previewBody = msg.body.trim().ifBlank { WaDao.previewTextFor(msg) }
                    if (previewBody.isNotBlank()) {
                        Text(
                            text = previewBody,
                            color = colors.textPrimary,
                            fontSize = 14.sp,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    val badges = buildList {
                        if (msg.isEdited) add("Edited")
                        if (msg.isPinned) add("Pinned")
                        if (msg.isViewOnce) add("View once")
                        if (msg.isEphemeral && msg.ephemeralDuration > 0) {
                            add("Disappearing (${formatEphemeralDurationShort(msg.ephemeralDuration)})")
                        }
                        if (msg.isDeletedBySender) add("Deleted by sender")
                        if (msg.durationSec > 0) {
                            add("Duration ${"%d:%02d".format(msg.durationSec / 60, msg.durationSec % 60)}")
                        }
                        if (msg.fileSize > 0L) {
                            add(formatCompactByteSize(msg.fileSize))
                        }
                    }
                    if (badges.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            badges.forEach { badge ->
                                Text(
                                    text = badge,
                                    color = colors.textSecondary,
                                    fontSize = 11.sp,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(colors.textSecondary.copy(alpha = 0.12f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Delivery & Read Status Card
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = colors.appSurface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (!isGroup) {
                        if (msg.isFromMe) {
                            val deliveredClock = formatReceiptStatusValue(
                                timestampMs = msg.deliveredAt,
                                isReached = rank >= 2,
                                reachedFallback = "Delivered",
                                pendingLabel = "Waiting for device",
                                showSeconds = true,
                                use24Hour = use24Hour
                            )
                            val readClock = formatReceiptStatusValue(
                                timestampMs = msg.readAt,
                                isReached = rank >= 3,
                                reachedFallback = "Read",
                                pendingLabel = "Not read yet",
                                showSeconds = true,
                                use24Hour = use24Hour
                            )
                            val playedClock = formatReceiptStatusValue(
                                timestampMs = msg.playedAt,
                                isReached = rank >= 4,
                                reachedFallback = "Played",
                                pendingLabel = "Not played yet",
                                showSeconds = true,
                                use24Hour = use24Hour
                            )
                            MessageReceiptRow(
                                icon = if (rank >= 1) Icons.Rounded.Done else Icons.Rounded.Schedule,
                                iconTint = if (rank >= 1) colors.brightGreen else colors.textSecondary,
                                label = "Sent",
                                value = dateClock.ifBlank { "Queued" },
                                colors = colors,
                                mono = mono
                            )
                            HorizontalDivider(color = colors.textSecondary.copy(alpha = 0.12f))
                            MessageReceiptRow(
                                icon = Icons.Rounded.DoneAll,
                                iconTint = if (rank >= 2) colors.textPrimary else colors.textSecondary.copy(alpha = 0.45f),
                                label = "Delivered",
                                value = deliveredClock,
                                colors = colors,
                                mono = mono
                            )
                            HorizontalDivider(color = colors.textSecondary.copy(alpha = 0.12f))
                            MessageReceiptRow(
                                icon = Icons.Rounded.DoneAll,
                                iconTint = if (rank >= 3) colors.blueTick else colors.textSecondary.copy(alpha = 0.45f),
                                label = "Read",
                                value = readClock,
                                colors = colors,
                                mono = mono
                            )
                            if (isVoiceOrAudio) {
                                HorizontalDivider(color = colors.textSecondary.copy(alpha = 0.12f))
                                MessageReceiptRow(
                                    icon = Icons.Rounded.DoneAll,
                                    iconTint = if (rank >= 4) colors.blueTick else colors.textSecondary.copy(alpha = 0.45f),
                                    label = "Played",
                                    value = playedClock,
                                    colors = colors,
                                    mono = mono
                                )
                            }
                        } else {
                            MessageReceiptRow(
                                icon = Icons.Rounded.Done,
                                iconTint = colors.brightGreen,
                                label = "Received",
                                value = dateClock.ifBlank { "Unknown" },
                                colors = colors,
                                mono = mono
                            )
                            HorizontalDivider(color = colors.textSecondary.copy(alpha = 0.12f))
                            MessageReceiptRow(
                                icon = Icons.Rounded.DoneAll,
                                iconTint = if (rank >= 3) colors.blueTick else colors.textSecondary,
                                label = "Status",
                                value = msg.deliveryStatus.lowercase()
                                    .replaceFirstChar { it.uppercase() },
                                colors = colors,
                                mono = mono
                            )
                        }
                    } else {
                        MessageReceiptRow(
                            icon = Icons.Rounded.Done,
                            iconTint = colors.brightGreen,
                            label = if (msg.isFromMe) "Sent" else "Received",
                            value = dateClock.ifBlank { "Unknown" },
                            colors = colors,
                            mono = mono
                        )
                        HorizontalDivider(color = colors.textSecondary.copy(alpha = 0.12f))
                        Text(
                            text = "Group Per-Participant Receipts",
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary,
                            fontSize = 13.5.sp
                        )
                        if (groupReceipts.isEmpty()) {
                            Text(
                                text = "No individual receipts recorded for this message yet.",
                                color = colors.textSecondary,
                                fontSize = 13.sp
                            )
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 220.dp)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                groupReceipts.forEach { r ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = r.participantName.ifBlank { r.participantJid.substringBefore("@") },
                                            color = colors.textPrimary,
                                            fontSize = 13.5.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        val rRank = WaDao.deliveryRank(r.status)
                                        Text(
                                            text = "${r.status} · ${formatMessageClock(r.timestamp, includeSeconds = showSeconds, use24Hour = use24Hour)}",
                                            color = if (rRank >= 3) colors.blueTick else colors.textSecondary,
                                            fontFamily = mono,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Technical Metadata Card
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = colors.appSurface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Message ID",
                            color = colors.textSecondary,
                            fontSize = 11.sp
                        )
                        Text(
                            text = msg.id,
                            color = colors.textPrimary,
                            fontFamily = mono,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (msg.mimeType.isNotBlank()) {
                            Text(
                                text = msg.mimeType,
                                color = colors.textSecondary,
                                fontFamily = mono,
                                fontSize = 11.sp
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(msg.id))
                            Toast.makeText(context, "Message ID copied", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = "Copy Message ID",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

@Composable
private fun MessageReceiptRow(
    icon: ImageVector,
    iconTint: Color,
    label: String,
    value: String,
    colors: WaColors,
    mono: FontFamily
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                color = colors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Text(
            text = value,
            color = colors.textSecondary,
            fontFamily = mono,
            fontSize = 12.5.sp
        )
    }
}

internal fun formatReceiptStatusValue(
    timestampMs: Long,
    isReached: Boolean,
    reachedFallback: String,
    pendingLabel: String,
    showSeconds: Boolean,
    use24Hour: Boolean
): String {
    if (timestampMs > 0L) {
        return formatMessageDateTime(timestampMs, showSeconds = showSeconds, use24Hour = use24Hour)
    }
    return if (isReached) reachedFallback else pendingLabel
}

internal fun formatMessageDateTime(
    epochMs: Long,
    showSeconds: Boolean,
    use24Hour: Boolean
): String {
    if (epochMs <= 0L) return ""
    val normalizedMs = if (epochMs < 10_000_000_000L) epochMs * 1000L else epochMs
    val dateStr = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(normalizedMs))
    val clockStr = formatMessageClock(normalizedMs, includeSeconds = showSeconds, use24Hour = use24Hour)
    return if (clockStr.isNotBlank()) "$dateStr · $clockStr" else dateStr
}

private fun formatEphemeralDurationShort(seconds: Int): String = when {
    seconds >= 86400 -> "${seconds / 86400}d"
    seconds >= 3600 -> "${seconds / 3600}h"
    seconds >= 60 -> "${seconds / 60}m"
    else -> "${seconds}s"
}

private fun formatCompactByteSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024f * 1024f))
    bytes >= 1024L -> "${bytes / 1024L} KB"
    else -> "$bytes B"
}

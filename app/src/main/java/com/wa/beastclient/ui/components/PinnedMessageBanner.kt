package com.wa.beastclient.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wa.beastclient.R
import com.wa.beastclient.data.db.MessageEntity
import com.wa.beastclient.data.db.WaDao
import com.wa.beastclient.ui.theme.LocalWaColors
import com.wa.beastclient.ui.theme.WaColors

/**
 * WhatsApp-matching pinned message header banner displayed directly beneath the chat top bar.
 * Tapping smoothly navigates and pulses the target message. If multiple messages are pinned,
 * tapping cycles across the pinned set.
 */
@Composable
fun PinnedMessageBanner(
    pinnedMessages: List<MessageEntity>,
    colors: WaColors = LocalWaColors.current,
    onMessageClick: (MessageEntity) -> Unit,
    onUnpinClick: (MessageEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    if (pinnedMessages.isEmpty()) return

    var activeIndex by remember(pinnedMessages.size) { mutableIntStateOf(0) }
    val safeIndex = activeIndex.coerceIn(0, pinnedMessages.lastIndex)
    val current = pinnedMessages.getOrNull(safeIndex) ?: pinnedMessages.first()

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = colors.elevatedBar.copy(alpha = 0.94f),
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    onMessageClick(current)
                    if (pinnedMessages.size > 1) {
                        activeIndex = (safeIndex + 1) % pinnedMessages.size
                    }
                }
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.PushPin,
                contentDescription = stringResource(R.string.pinned_message_banner_label),
                tint = colors.accentGreen,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val headerText = when {
                        current.isFromMe -> "You"
                        current.senderName.isNotBlank() -> current.senderName
                        else -> stringResource(R.string.pinned_message_banner_label)
                    }
                    Text(
                        text = headerText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.accentGreen,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (pinnedMessages.size > 1) {
                        Text(
                            text = "· ${safeIndex + 1}/${pinnedMessages.size}",
                            fontSize = 11.sp,
                            color = colors.textSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                AnimatedContent(
                    targetState = current,
                    transitionSpec = {
                        (fadeIn() + slideInVertically { it / 2 }) togetherWith
                            (fadeOut() + slideOutVertically { -it / 2 })
                    },
                    label = "pinned_preview_anim"
                ) { targetMsg ->
                    val preview = WaDao.previewTextFor(targetMsg).ifBlank { targetMsg.body }
                    Text(
                        text = preview,
                        fontSize = 13.sp,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(
                onClick = { onUnpinClick(current) },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.unpin_message),
                    tint = colors.textSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * Native WhatsApp duration selection dialog for pinning a message.
 * Options: 24 hours, 7 days (default), 30 days.
 */
@Composable
fun PinDurationDialog(
    onDismiss: () -> Unit,
    onConfirm: (durationSec: Long) -> Unit,
    colors: WaColors = LocalWaColors.current
) {
    val options = listOf(
        Pair(stringResource(R.string.pin_duration_24h), 86400L),
        Pair(stringResource(R.string.pin_duration_7d), 604800L),
        Pair(stringResource(R.string.pin_duration_30d), 2592000L)
    )
    var selectedDuration by remember { mutableLongStateOf(604800L) } // 7 days is WhatsApp default

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.elevatedBar,
        titleContentColor = colors.textPrimary,
        textContentColor = colors.textSecondary,
        title = {
            Text(
                text = stringResource(R.string.pin_message_title),
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp
            )
        },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.pin_message_subtitle),
                    fontSize = 14.sp,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                options.forEach { (label, durationSec) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedDuration = durationSec }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedDuration == durationSec,
                            onClick = { selectedDuration = durationSec },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = colors.accentGreen,
                                unselectedColor = colors.textSecondary
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = label,
                            fontSize = 15.sp,
                            color = colors.textPrimary
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedDuration) }) {
                Text(
                    text = stringResource(R.string.pin_message),
                    color = colors.accentGreen,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Cancel",
                    color = colors.textSecondary
                )
            }
        }
    )
}
